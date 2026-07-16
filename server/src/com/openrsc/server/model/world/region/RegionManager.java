package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class RegionManager {
	private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>> regions;

	private final World world;

	public RegionManager(final World world) {
		this.world = world;
		this.regions = new ConcurrentHashMap<>();
	}

	public void load() {
		// TODO: The WorldLoader.loadWorld() should accept a RegionManager as an argument and place regions there.
		getWorld().getWorldLoader().loadWorld();
	}

	public void unload() {
		for (final ConcurrentHashMap<Integer, Region> yRegionList : regions.values()) {
			for (final Region region : yRegionList.values()) {
				region.unload();
			}
		}
		regions.clear();
	}

	/**
	 * Gets the local players around an entity.
	 *
	 * @param entity The entity.
	 * @return The collection of local players.
	 */
	public Collection<Player> getLocalPlayers(final Entity entity) {
		// Nobody online means the scan below is provably empty; skip the region walk and
		// set churn entirely (every aggressive NPC calls this each tick). Callers only
		// iterate the result — the returned set must not be mutated.
		if (getWorld().getPlayers().size() == 0) {
			return Collections.emptySet();
		}
		final LinkedHashSet<Player> localPlayers = new LinkedHashSet<Player>();
		final Point viewLocation = getViewLocation(entity);
		for (final Region region : getVisibleRegions(viewLocation)) {
			for (final Player player : region.getPlayers()) {
				// voidscape instancing: players only see players sharing their phase (id 0 == overworld).
				if (player.getLocation().withinRange(viewLocation, (getWorld().getServer().getConfig().VIEW_DISTANCE * 8) - 1)
						&& entity.getInstanceId() == player.getInstanceId()) {
					localPlayers.add(player);
				}
			}
		}
		return localPlayers;
	}

	/**
	 * Gets the local NPCs around an entity.
	 *
	 * @param entity The entity.
	 * @return The collection of local NPCs.
	 */
	public Collection<Npc> getLocalNpcs(final Entity entity) {
		final LinkedHashSet<Npc> localNpcs = new LinkedHashSet<>();
		final Point viewLocation = getViewLocation(entity);
		for (final Region region : getVisibleRegions(viewLocation)) {
			for (final Npc npc : region.getNpcs()) {
				// voidscape instancing: only NPCs sharing the observer's phase are visible/interactable.
				if (npc.getLocation().withinRange(viewLocation, (getWorld().getServer().getConfig().VIEW_DISTANCE * 8) - 1)
						&& entity.getInstanceId() == npc.getInstanceId()) {
					localNpcs.add(npc);
				}
			}
		}
		return localNpcs;
	}

	public Collection<GameObject> getLocalObjects(final Mob entity) {
		LinkedHashSet<GameObject> localObjects = new LinkedHashSet<GameObject>();
		final Point viewLocation = getViewLocation(entity);
		for (final Iterator<Region> region = getVisibleRegions(viewLocation).iterator(); region.hasNext(); ) {
			Collection<GameObject> objects = region.next().getGameObjects();
			synchronized (objects) {
				for (final Iterator<GameObject> o = objects.iterator(); o.hasNext(); ) {
					final GameObject gameObject = o.next();
					if (gameObject
						.getLocation()
						.withinGridRange(
							viewLocation,
							getWorld().getServer().getConfig().VIEW_DISTANCE
						)
					) {
						localObjects.add(gameObject);
					}
				}
			}
		}
		return localObjects;
	}

	public Collection<GroundItem> getLocalGroundItems(final Mob entity) {
		final LinkedHashSet<GroundItem> localItems = new LinkedHashSet<GroundItem>();
		final Point viewLocation = getViewLocation(entity);
		for (final Region region : getVisibleRegions(viewLocation)) {
			for (final GroundItem o : region.getGroundItems()) {
				// voidscape instancing: ground items (e.g. a boss instance's loot) are phase-private.
				if (o.getLocation().withinGridRange(viewLocation, getWorld().getServer().getConfig().VIEW_DISTANCE)
						&& entity.getInstanceId() == o.getInstanceId()) {
					localItems.add(o);
				}
			}
		}
		return localItems;
	}

	/**
	 * Returns a defensive snapshot of ground items in the supplied world-space bounds.
	 */
	public Collection<GroundItem> getGroundItemsInBounds(final int minX, final int minY,
											 final int maxX, final int maxY, final int instanceId) {
		final LinkedHashSet<GroundItem> items = new LinkedHashSet<>();
		final int minRegionX = minX / Constants.REGION_SIZE;
		final int maxRegionX = maxX / Constants.REGION_SIZE;
		final int minRegionY = minY / Constants.REGION_SIZE;
		final int maxRegionY = maxY / Constants.REGION_SIZE;
		for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
			for (int regionY = minRegionY; regionY <= maxRegionY; regionY++) {
				for (final GroundItem item : getRegionFromSectorCoordinates(regionX, regionY).getGroundItems()) {
					final Point location = item.getLocation();
					if (!item.isRemoved() && location != null && item.getInstanceId() == instanceId
						&& location.inBounds(minX, minY, maxX, maxY)) {
						items.add(item);
					}
				}
			}
		}
		return items;
	}

	private Point getViewLocation(final Entity entity) {
		if (entity instanceof Player) {
			return ((Player) entity).getViewLocation();
		}
		return entity.getLocation();
	}

	/**
	 * Gets regions within range of the given location
	 * @param location location
	 * @return regions within range of the given location
	 */
	public LinkedHashSet<Region> getVisibleRegions(final Point location) {
		// View distance is in multiples of 8
		final int viewDistance = getWorld().getServer().getConfig().VIEW_DISTANCE << 3;

		final int regionX = location.getX() / Constants.REGION_SIZE;
		final int regionY = location.getY() / Constants.REGION_SIZE;

		final int offsetX = location.getX() % Constants.REGION_SIZE;
		final int offsetY = location.getY() % Constants.REGION_SIZE;

		// Primitive mods instead of boxed lists; visit order matches the original
		// (0,0) -> (0,yMod) -> (xMod,0) -> (xMod,yMod) since region order determines
		// downstream entity iteration order.
		final int xMod = offsetX <= viewDistance ? -1
			: (Constants.REGION_SIZE - offsetX <= viewDistance ? 1 : 0);
		final int yMod = offsetY <= viewDistance ? -1
			: (Constants.REGION_SIZE - offsetY <= viewDistance ? 1 : 0);

		final LinkedHashSet<Region> visible = new LinkedHashSet<>();
		visible.add(getRegionFromSectorCoordinates(regionX, regionY));
		if (yMod != 0) {
			visible.add(getRegionFromSectorCoordinates(regionX, regionY + yMod));
		}
		if (xMod != 0) {
			visible.add(getRegionFromSectorCoordinates(regionX + xMod, regionY));
			if (yMod != 0) {
				visible.add(getRegionFromSectorCoordinates(regionX + xMod, regionY + yMod));
			}
		}

		return visible;
	}

	/**
	 * Gets the regions surrounding a location.
	 *
	 * @param location The location.
	 * @return The regions surrounding the location.
	 */
	public LinkedHashSet<Region> getSurroundingRegions(final Point location) {
		final int regionX = location.getX() / Constants.REGION_SIZE;
		final int regionY = location.getY() / Constants.REGION_SIZE;

		final LinkedHashSet<Region> surrounding = new LinkedHashSet<Region>();
		surrounding.add(getRegionFromSectorCoordinates(regionX, regionY));
		final int[] xMod = {-1, +1, -1, 0, +1, 0, -1, +1};
		final int[] yMod = {-1, +1, 0, -1, 0, +1, +1, -1};
		for (int i = 0; i < xMod.length; i++) {
			final Region tmpRegion = getRegionFromSectorCoordinates(regionX + xMod[i], regionY + yMod[i]);
			if (tmpRegion != null) {
				surrounding.add(tmpRegion);
			}
		}
		return surrounding;
	}

	private Region getRegionFromSectorCoordinates(final int regionX, final int regionY) {
		// Hit path is two lock-free gets; computeIfAbsent only on first touch of a sector
		// (also closes the old check-then-act race that could clobber a freshly made row).
		ConcurrentHashMap<Integer, Region> row = regions.get(regionX);
		if (row == null) {
			row = regions.computeIfAbsent(regionX, k -> new ConcurrentHashMap<>());
		}
		Region region = row.get(regionY);
		if (region == null) {
			region = row.computeIfAbsent(regionY, k -> new Region(this, regionX, regionY));
		}
		return region;
	}

	public Region getRegion(final int x, final int y) {
		final int regionX = x / Constants.REGION_SIZE;
		final int regionY = y / Constants.REGION_SIZE;
		return getRegionFromSectorCoordinates(regionX, regionY);
	}

	public Region getRegion(final Point objectCoordinates) {
		return getRegion(objectCoordinates.getX(), objectCoordinates.getY());
	}

	/**
	 * Are the given coords within the world boundaries
	 */
	public boolean withinWorld(final int x, final int y) {
		return x >= 0 && x < Constants.MAX_WIDTH && y >= 0 && y < Constants.MAX_HEIGHT;
	}

	public TileValue getTile(final int x, final int y) {
		if (!withinWorld(x, y)) {
			return null;
		}

		return getRegion(x, y).getTileValue(x % Constants.REGION_SIZE, y % Constants.REGION_SIZE);
	}

	public TileValue getTile(final Point point) {
		return getTile(point.getX(), point.getY());
	}

	// originally private, set to public to access for reset event
	public ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>> getRegions() {
		return regions;
	}

	public World getWorld() {
		return world;
	}
}
