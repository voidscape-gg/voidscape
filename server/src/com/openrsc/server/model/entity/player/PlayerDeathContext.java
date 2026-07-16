package com.openrsc.server.model.entity.player;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerDeathContext {
	private final UUID deathId;
	private final Point deathPoint;
	private final int deathInstanceId;
	private final Mob killer;
	private final UUID killerId;
	private final List<GroundItem> groundItems;
	private final AtomicBoolean persistenceRequested = new AtomicBoolean(false);

	public PlayerDeathContext(Point deathPoint, int deathInstanceId, Mob killer,
						  List<GroundItem> groundItems) {
		this.deathId = UUID.randomUUID();
		this.deathPoint = Point.location(
			Objects.requireNonNull(deathPoint, "deathPoint").getX(), deathPoint.getY());
		this.deathInstanceId = deathInstanceId;
		this.killer = killer;
		this.killerId = killer == null ? null : killer.getUUID();
		this.groundItems = Collections.unmodifiableList(new ArrayList<>(
			Objects.requireNonNull(groundItems, "groundItems")));
	}

	public UUID getDeathId() {
		return deathId;
	}

	public Point getDeathPoint() {
		return Point.location(deathPoint.getX(), deathPoint.getY());
	}

	public int getDeathInstanceId() {
		return deathInstanceId;
	}

	public Mob getKiller() {
		return killer;
	}

	public UUID getKillerId() {
		return killerId;
	}

	public List<GroundItem> getGroundItems() {
		return groundItems;
	}

	public void requestPersistence() {
		persistenceRequested.set(true);
	}

	public boolean isPersistenceRequested() {
		return persistenceRequested.get();
	}
}
