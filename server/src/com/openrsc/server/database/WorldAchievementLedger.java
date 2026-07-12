package com.openrsc.server.database;

import com.openrsc.server.database.struct.WorldAchievementRecord;
import com.openrsc.server.database.struct.WorldPkEvent;
import com.openrsc.server.database.struct.WorldPkStreak;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

/**
 * Shared MySQL/SQLite SQL contract for immutable world records and PK events plus
 * their mutable streak projection. Policy and normalization belong to the caller;
 * this class only rejects values that cannot safely satisfy the storage contract.
 */
public final class WorldAchievementLedger {
	private static final int MAX_PREFIX_LENGTH = 32;
	private static final int MAX_SEASON_ID_LENGTH = 32;
	private static final int MAX_RECORD_KEY_LENGTH = 96;
	private static final int MAX_RECORD_TYPE_LENGTH = 32;
	private static final int MAX_PLAYER_NAME_LENGTH = 12;
	private static final int MAX_SOURCE_LENGTH = 32;
	private static final int MAX_SOURCE_EVENT_KEY_LENGTH = 96;
	private static final int MAX_REJECT_REASON_LENGTH = 64;
	private static final int MAX_DETAIL_LENGTH = 255;

	private static final String RECORD_COLUMNS = "`season_id`,`record_key`,`record_type`,`player_id`,"
		+ "`player_name`,`subject_id`,`value`,`source`,`source_event_key`,"
		+ "`claimed_at_ms`,`detail`";
	private static final String EVENT_COLUMNS = "`death_id`,`season_id`,`killer_player_id`,"
		+ "`killer_account_id`,`killer_name`,`victim_player_id`,`victim_account_id`,"
		+ "`victim_name`,`pair_low_player_id`,`pair_high_player_id`,`qualified`,"
		+ "`reject_reason`,`victim_was_skulled`,`victim_damage`,`loot_value`,"
		+ "`streak_after`,`ended_streak`,`wilderness_level`,`occurred_at_ms`";
	private static final String STREAK_COLUMNS = "`season_id`,`player_id`,`player_name`,"
		+ "`current_streak`,`best_streak`,`qualified_kills`,`last_qualified_at_ms`,`updated_at_ms`";

	private WorldAchievementLedger() {
	}

	public static WorldAchievementRecord loadRecord(JDBCDatabaseConnection connection,
		String tablePrefix, String seasonId, String recordKey) {
		final String table = table(tablePrefix, "world_achievement_records");
		if (!validKey(seasonId, MAX_SEASON_ID_LENGTH)
			|| !validKey(recordKey, MAX_RECORD_KEY_LENGTH)) {
			return null;
		}
		final String sql = "SELECT " + RECORD_COLUMNS + " FROM " + table
			+ " WHERE `season_id`=? AND `record_key`=? LIMIT 1";
		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			statement.setString(1, seasonId);
			statement.setString(2, recordKey);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? readRecord(result) : null;
			}
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	/** Returns one for the first claim and zero for an existing key or source event. */
	public static int insertRecord(JDBCDatabaseConnection connection, String tablePrefix,
		WorldAchievementRecord record) {
		final String table = table(tablePrefix, "world_achievement_records");
		if (!validRecord(record)) return 0;

		String sql = "INSERT INTO " + table + " (" + RECORD_COLUMNS + ")"
			+ " SELECT ?,?,?,?,?,?,?,?,?,?,?"
			+ " WHERE NOT EXISTS (SELECT 1 FROM " + table
			+ " WHERE `season_id`=? AND `record_key`=?)";
		if (record.sourceEventKey != null) {
			sql += " AND NOT EXISTS (SELECT 1 FROM " + table
				+ " WHERE `season_id`=? AND `source`=? AND `source_event_key`=?"
				+ " AND `record_type`=?)";
		}

		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			int index = bindRecord(statement, record);
			statement.setString(index++, record.seasonId);
			statement.setString(index++, record.recordKey);
			if (record.sourceEventKey != null) {
				statement.setString(index++, record.seasonId);
				statement.setString(index++, record.source);
				statement.setString(index++, record.sourceEventKey);
				statement.setString(index, record.recordType);
			}
			return statement.executeUpdate();
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	public static WorldPkEvent loadPkEvent(JDBCDatabaseConnection connection,
		String tablePrefix, String deathId) {
		final String table = table(tablePrefix, "world_pk_events");
		if (!validDeathId(deathId)) return null;
		final String sql = "SELECT " + EVENT_COLUMNS + " FROM " + table
			+ " WHERE `death_id`=? LIMIT 1";
		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			statement.setString(1, deathId);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? readPkEvent(result) : null;
			}
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	/** Returns one for a new death id and zero for a replay. */
	public static int insertPkEvent(JDBCDatabaseConnection connection, String tablePrefix,
		WorldPkEvent event) {
		final String table = table(tablePrefix, "world_pk_events");
		if (!validPkEvent(event)) return 0;
		final String sql = "INSERT INTO " + table + " (" + EVENT_COLUMNS + ")"
			+ " SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?"
			+ " WHERE NOT EXISTS (SELECT 1 FROM " + table + " WHERE `death_id`=?)";
		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			int index = bindPkEvent(statement, event);
			statement.setString(index, event.deathId);
			return statement.executeUpdate();
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	public static Long loadLastQualifiedPairTime(JDBCDatabaseConnection connection,
		String tablePrefix, String seasonId, int pairLowPlayerId, int pairHighPlayerId) {
		final String table = table(tablePrefix, "world_pk_events");
		if (!validKey(seasonId, MAX_SEASON_ID_LENGTH)
			|| pairLowPlayerId <= 0 || pairHighPlayerId <= pairLowPlayerId) {
			return null;
		}
		final String sql = "SELECT `occurred_at_ms` FROM " + table
			+ " WHERE `season_id`=? AND `pair_low_player_id`=? AND `pair_high_player_id`=?"
			+ " AND `qualified`=? ORDER BY `occurred_at_ms` DESC LIMIT 1";
		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			statement.setString(1, seasonId);
			statement.setInt(2, pairLowPlayerId);
			statement.setInt(3, pairHighPlayerId);
			statement.setBoolean(4, true);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? result.getLong("occurred_at_ms") : null;
			}
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	public static WorldPkStreak loadPkStreak(JDBCDatabaseConnection connection,
		String tablePrefix, String seasonId, int playerId) {
		final String table = table(tablePrefix, "world_pk_streaks");
		if (!validKey(seasonId, MAX_SEASON_ID_LENGTH) || playerId <= 0) return null;
		final String sql = "SELECT " + STREAK_COLUMNS + " FROM " + table
			+ " WHERE `season_id`=? AND `player_id`=? LIMIT 1";
		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			statement.setString(1, seasonId);
			statement.setInt(2, playerId);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? readPkStreak(result) : null;
			}
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	/** Returns one for a new season/player row and zero when the projection exists. */
	public static int insertPkStreak(JDBCDatabaseConnection connection, String tablePrefix,
		WorldPkStreak streak) {
		final String table = table(tablePrefix, "world_pk_streaks");
		if (!validPkStreak(streak)) return 0;
		final String sql = "INSERT INTO " + table + " (" + STREAK_COLUMNS + ")"
			+ " SELECT ?,?,?,?,?,?,?,?"
			+ " WHERE NOT EXISTS (SELECT 1 FROM " + table
			+ " WHERE `season_id`=? AND `player_id`=?)";
		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			int index = bindPkStreak(statement, streak);
			statement.setString(index++, streak.seasonId);
			statement.setInt(index, streak.playerId);
			return statement.executeUpdate();
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	/** Returns one only when the exact season/player projection already exists. */
	public static int updatePkStreak(JDBCDatabaseConnection connection, String tablePrefix,
		WorldPkStreak streak) {
		final String table = table(tablePrefix, "world_pk_streaks");
		if (!validPkStreak(streak)) return 0;
		final String sql = "UPDATE " + table
			+ " SET `player_name`=?,`current_streak`=?,`best_streak`=?,`qualified_kills`=?,"
			+ "`last_qualified_at_ms`=?,`updated_at_ms`=?"
			+ " WHERE `season_id`=? AND `player_id`=?";
		try (PreparedStatement statement = required(connection).prepareStatement(sql)) {
			statement.setString(1, streak.playerName);
			statement.setInt(2, streak.currentStreak);
			statement.setInt(3, streak.bestStreak);
			statement.setLong(4, streak.qualifiedKills);
			statement.setLong(5, streak.lastQualifiedAtMs);
			statement.setLong(6, streak.updatedAtMs);
			statement.setString(7, streak.seasonId);
			statement.setInt(8, streak.playerId);
			return statement.executeUpdate();
		} catch (SQLException ex) {
			throw failure(ex);
		}
	}

	private static int bindRecord(PreparedStatement statement, WorldAchievementRecord record)
		throws SQLException {
		int index = 1;
		statement.setString(index++, record.seasonId);
		statement.setString(index++, record.recordKey);
		statement.setString(index++, record.recordType);
		statement.setInt(index++, record.playerId);
		statement.setString(index++, record.playerName);
		statement.setInt(index++, record.subjectId);
		statement.setLong(index++, record.value);
		statement.setString(index++, record.source);
		if (record.sourceEventKey == null) {
			statement.setNull(index++, Types.VARCHAR);
		} else {
			statement.setString(index++, record.sourceEventKey);
		}
		statement.setLong(index++, record.claimedAtMs);
		statement.setString(index++, record.detail);
		return index;
	}

	private static int bindPkEvent(PreparedStatement statement, WorldPkEvent event)
		throws SQLException {
		int index = 1;
		statement.setString(index++, event.deathId);
		statement.setString(index++, event.seasonId);
		statement.setInt(index++, event.killerPlayerId);
		setNullableLong(statement, index++, event.killerAccountId);
		statement.setString(index++, event.killerName);
		statement.setInt(index++, event.victimPlayerId);
		setNullableLong(statement, index++, event.victimAccountId);
		statement.setString(index++, event.victimName);
		statement.setInt(index++, event.pairLowPlayerId);
		statement.setInt(index++, event.pairHighPlayerId);
		statement.setBoolean(index++, event.qualified);
		statement.setString(index++, event.rejectReason);
		statement.setBoolean(index++, event.victimWasSkulled);
		statement.setInt(index++, event.victimDamage);
		statement.setLong(index++, event.lootValue);
		statement.setInt(index++, event.streakAfter);
		statement.setInt(index++, event.endedStreak);
		statement.setInt(index++, event.wildernessLevel);
		statement.setLong(index++, event.occurredAtMs);
		return index;
	}

	private static int bindPkStreak(PreparedStatement statement, WorldPkStreak streak)
		throws SQLException {
		int index = 1;
		statement.setString(index++, streak.seasonId);
		statement.setInt(index++, streak.playerId);
		statement.setString(index++, streak.playerName);
		statement.setInt(index++, streak.currentStreak);
		statement.setInt(index++, streak.bestStreak);
		statement.setLong(index++, streak.qualifiedKills);
		statement.setLong(index++, streak.lastQualifiedAtMs);
		statement.setLong(index++, streak.updatedAtMs);
		return index;
	}

	private static WorldAchievementRecord readRecord(ResultSet result) throws SQLException {
		WorldAchievementRecord record = new WorldAchievementRecord();
		record.seasonId = result.getString("season_id");
		record.recordKey = result.getString("record_key");
		record.recordType = result.getString("record_type");
		record.playerId = result.getInt("player_id");
		record.playerName = result.getString("player_name");
		record.subjectId = result.getInt("subject_id");
		record.value = result.getLong("value");
		record.source = result.getString("source");
		record.sourceEventKey = result.getString("source_event_key");
		record.claimedAtMs = result.getLong("claimed_at_ms");
		record.detail = result.getString("detail");
		return record;
	}

	private static WorldPkEvent readPkEvent(ResultSet result) throws SQLException {
		WorldPkEvent event = new WorldPkEvent();
		event.deathId = result.getString("death_id");
		event.seasonId = result.getString("season_id");
		event.killerPlayerId = result.getInt("killer_player_id");
		event.killerAccountId = nullableLong(result, "killer_account_id");
		event.killerName = result.getString("killer_name");
		event.victimPlayerId = result.getInt("victim_player_id");
		event.victimAccountId = nullableLong(result, "victim_account_id");
		event.victimName = result.getString("victim_name");
		event.pairLowPlayerId = result.getInt("pair_low_player_id");
		event.pairHighPlayerId = result.getInt("pair_high_player_id");
		event.qualified = result.getBoolean("qualified");
		event.rejectReason = result.getString("reject_reason");
		event.victimWasSkulled = result.getBoolean("victim_was_skulled");
		event.victimDamage = result.getInt("victim_damage");
		event.lootValue = result.getLong("loot_value");
		event.streakAfter = result.getInt("streak_after");
		event.endedStreak = result.getInt("ended_streak");
		event.wildernessLevel = result.getInt("wilderness_level");
		event.occurredAtMs = result.getLong("occurred_at_ms");
		return event;
	}

	private static WorldPkStreak readPkStreak(ResultSet result) throws SQLException {
		WorldPkStreak streak = new WorldPkStreak();
		streak.seasonId = result.getString("season_id");
		streak.playerId = result.getInt("player_id");
		streak.playerName = result.getString("player_name");
		streak.currentStreak = result.getInt("current_streak");
		streak.bestStreak = result.getInt("best_streak");
		streak.qualifiedKills = result.getLong("qualified_kills");
		streak.lastQualifiedAtMs = result.getLong("last_qualified_at_ms");
		streak.updatedAtMs = result.getLong("updated_at_ms");
		return streak;
	}

	private static boolean validRecord(WorldAchievementRecord record) {
		return record != null
			&& validKey(record.seasonId, MAX_SEASON_ID_LENGTH)
			&& validKey(record.recordKey, MAX_RECORD_KEY_LENGTH)
			&& validKey(record.recordType, MAX_RECORD_TYPE_LENGTH)
			&& record.playerId > 0 && record.subjectId >= 0
			&& validKey(record.playerName, MAX_PLAYER_NAME_LENGTH)
			&& validKey(record.source, MAX_SOURCE_LENGTH)
			&& (record.sourceEventKey == null
				|| validKey(record.sourceEventKey, MAX_SOURCE_EVENT_KEY_LENGTH))
			&& record.claimedAtMs > 0L
			&& validText(record.detail, MAX_DETAIL_LENGTH);
	}

	private static boolean validPkEvent(WorldPkEvent event) {
		if (event == null || !validDeathId(event.deathId)
			|| !validKey(event.seasonId, MAX_SEASON_ID_LENGTH)
			|| event.killerPlayerId <= 0 || event.victimPlayerId <= 0
			|| event.killerPlayerId == event.victimPlayerId
			|| (event.killerAccountId != null && event.killerAccountId <= 0L)
			|| (event.victimAccountId != null && event.victimAccountId <= 0L)
			|| !validKey(event.killerName, MAX_PLAYER_NAME_LENGTH)
			|| !validKey(event.victimName, MAX_PLAYER_NAME_LENGTH)
			|| !validText(event.rejectReason, MAX_REJECT_REASON_LENGTH)
			|| event.victimDamage < 0 || event.lootValue < 0L
			|| event.streakAfter < 0 || event.endedStreak < 0
			|| event.wildernessLevel < 0 || event.occurredAtMs <= 0L) {
			return false;
		}
		return event.pairLowPlayerId == Math.min(event.killerPlayerId, event.victimPlayerId)
			&& event.pairHighPlayerId == Math.max(event.killerPlayerId, event.victimPlayerId);
	}

	private static boolean validPkStreak(WorldPkStreak streak) {
		return streak != null
			&& validKey(streak.seasonId, MAX_SEASON_ID_LENGTH)
			&& streak.playerId > 0
			&& validKey(streak.playerName, MAX_PLAYER_NAME_LENGTH)
			&& streak.currentStreak >= 0 && streak.bestStreak >= 0
			&& streak.qualifiedKills >= 0L && streak.lastQualifiedAtMs >= 0L
			&& streak.updatedAtMs > 0L;
	}

	private static boolean validKey(String value, int maxLength) {
		return value != null && !value.isEmpty() && value.length() <= maxLength;
	}

	private static boolean validText(String value, int maxLength) {
		return value != null && value.length() <= maxLength;
	}

	private static void setNullableLong(PreparedStatement statement, int index, Long value)
		throws SQLException {
		if (value == null) statement.setNull(index, Types.BIGINT);
		else statement.setLong(index, value);
	}

	private static Long nullableLong(ResultSet result, String column) throws SQLException {
		long value = result.getLong(column);
		return result.wasNull() ? null : value;
	}

	private static boolean validDeathId(String deathId) {
		if (deathId == null || deathId.length() != 36) return false;
		try {
			return UUID.fromString(deathId).toString().equals(deathId);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static JDBCDatabaseConnection required(JDBCDatabaseConnection connection) {
		if (connection == null) {
			throw new IllegalArgumentException("World achievement database connection is required");
		}
		return connection;
	}

	private static String table(String tablePrefix, String baseName) {
		if (tablePrefix == null || tablePrefix.length() > MAX_PREFIX_LENGTH) {
			throw new IllegalArgumentException("Invalid world achievement table prefix");
		}
		for (int index = 0; index < tablePrefix.length(); index++) {
			char character = tablePrefix.charAt(index);
			if ((character < 'a' || character > 'z')
				&& (character < 'A' || character > 'Z')
				&& (character < '0' || character > '9')
				&& character != '_') {
				throw new IllegalArgumentException("Invalid world achievement table prefix");
			}
		}
		return "`" + tablePrefix + baseName + "`";
	}

	private static GameDatabaseException failure(SQLException exception) {
		return new GameDatabaseException(WorldAchievementLedger.class, exception.getMessage());
	}
}
