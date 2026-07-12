package com.openrsc.server.database;

import com.openrsc.server.database.struct.PortalCommerceEntitlement;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Exact SQL contract shared by MySQL and SQLite for game-side paid-card pickup.
 * These tables deliberately do not use the legacy game table prefix.
 */
public final class PortalCommerceLedger {
	private static final String COLUMNS = "`id`,`payment_id`,`account_id`,`provider`,`transaction_id`,"
		+ "`line_key`,`package_id`,`unit_index`,`state`,`claimed_player_id`,`claimed_item_id`,"
		+ "`claimed_at_ms`,`created_at_ms`,`updated_at_ms`";
	private static final String TABLE = "`portal_commerce_entitlements`";

	private PortalCommerceLedger() {
	}

	public static PortalCommerceEntitlement loadOldestPending(JDBCDatabaseConnection connection,
		int accountId) {
		if (accountId <= 0) return null;
		final String sql = "SELECT " + COLUMNS + " FROM " + TABLE
			+ " WHERE `account_id`=? AND `state`='pending'"
			+ " ORDER BY `created_at_ms` ASC, `id` ASC LIMIT 1";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, accountId);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? read(result) : null;
			}
		} catch (SQLException ex) {
			throw new GameDatabaseException(PortalCommerceLedger.class, ex.getMessage());
		}
	}

	public static PortalCommerceEntitlement loadById(JDBCDatabaseConnection connection,
		long entitlementId) {
		if (entitlementId <= 0L) return null;
		final String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE `id`=? LIMIT 1";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, entitlementId);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? read(result) : null;
			}
		} catch (SQLException ex) {
			throw new GameDatabaseException(PortalCommerceLedger.class, ex.getMessage());
		}
	}

	/** Returns one only when this exact account still owns this exact pending row. */
	public static int claimPending(JDBCDatabaseConnection connection, long entitlementId,
		int accountId, int playerId, long catalogItemId, long claimedAtMs) {
		if (entitlementId <= 0L || accountId <= 0 || playerId <= 0
			|| catalogItemId <= 0L || claimedAtMs <= 0L) {
			return 0;
		}
		final String sql = "UPDATE " + TABLE
			+ " SET `state`='claimed',`claimed_player_id`=?,`claimed_item_id`=?,"
			+ "`claimed_at_ms`=?,`updated_at_ms`=?"
			+ " WHERE `id`=? AND `account_id`=? AND `state`='pending'"
			+ " AND `claimed_player_id` IS NULL AND `claimed_item_id` IS NULL"
			+ " AND `claimed_at_ms` IS NULL";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, playerId);
			statement.setLong(2, catalogItemId);
			statement.setLong(3, claimedAtMs);
			statement.setLong(4, claimedAtMs);
			statement.setLong(5, entitlementId);
			statement.setInt(6, accountId);
			return statement.executeUpdate();
		} catch (SQLException ex) {
			throw new GameDatabaseException(PortalCommerceLedger.class, ex.getMessage());
		}
	}

	private static PortalCommerceEntitlement read(ResultSet result) throws SQLException {
		final PortalCommerceEntitlement entitlement = new PortalCommerceEntitlement();
		entitlement.id = result.getLong("id");
		entitlement.paymentId = result.getLong("payment_id");
		entitlement.accountId = result.getInt("account_id");
		entitlement.provider = result.getString("provider");
		entitlement.transactionId = result.getString("transaction_id");
		entitlement.lineKey = result.getString("line_key");
		entitlement.packageId = result.getString("package_id");
		entitlement.unitIndex = result.getInt("unit_index");
		entitlement.state = result.getString("state");
		entitlement.claimedPlayerId = result.getInt("claimed_player_id");
		entitlement.claimedItemId = result.getLong("claimed_item_id");
		entitlement.claimedAtMs = result.getLong("claimed_at_ms");
		entitlement.createdAtMs = result.getLong("created_at_ms");
		entitlement.updatedAtMs = result.getLong("updated_at_ms");
		return entitlement;
	}
}
