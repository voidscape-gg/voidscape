package com.openrsc.server.util;

import com.openrsc.server.model.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class PlayerList extends EntityList<Player> {
    private final Map<Long, Player> playerHashIndex = new HashMap<>();

    public PlayerList(int capacity) {
        super(capacity);
    }

    @Override
    public synchronized boolean add(Player entity) {
		if (entity == null || playerHashIndex.containsKey(entity.getUsernameHash()) || super.contains(entity)) {
			return false;
		}
		if (!super.add(entity)) {
			return false;
		}
		playerHashIndex.put(entity.getUsernameHash(), entity);
		return true;
    }

	@Override
	public synchronized void clear() {
		super.clear();
		playerHashIndex.clear();
	}

    @Override
    public synchronized Player remove(int index) {
        Player player = super.remove(index);
        if(player != null && playerHashIndex.get(player.getUsernameHash()) == player) {
            playerHashIndex.remove(player.getUsernameHash());
        }
        return player;
    }

	@Override
	public synchronized void remove(Player player) {
		removeIfCurrent(player);
	}

	/** Returns whether this exact Player incarnation owns both list indexes. */
	public synchronized boolean isCurrent(Player player) {
		if (player == null || playerHashIndex.get(player.getUsernameHash()) != player) {
			return false;
		}
		final int index = player.getIndex();
		return index >= 0 && get(index) == player;
	}

	/** Atomically removes only the expected Player incarnation. */
	public synchronized boolean removeIfCurrent(Player player) {
		if (!isCurrent(player)) {
			return false;
		}
		remove(player.getIndex());
		return true;
	}

    /**
     * Gets a player by their username hash
     * @param hash username hash
     * @return the player associated with this hash
     */
    public synchronized Player getPlayerByHash(long hash) {
        return playerHashIndex.get(hash);
    }
	/**
	 * Remove a player by their username hash
	 * @param hash username hash
	 * @return the player associated with this hash
	 */
	public synchronized Player removePlayerByHash(long hash) {
		final Player player = playerHashIndex.get(hash);
		return player != null && removeIfCurrent(player) ? player : null;
	}
}
