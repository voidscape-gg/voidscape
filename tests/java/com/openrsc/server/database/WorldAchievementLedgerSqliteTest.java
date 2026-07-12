package com.openrsc.server.database;

import com.openrsc.server.database.struct.WorldAchievementRecord;
import com.openrsc.server.database.struct.WorldPkEvent;
import com.openrsc.server.database.struct.WorldPkStreak;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Exercises the production world-achievement SQL against the checked-in SQLite migration. */
public final class WorldAchievementLedgerSqliteTest {
	private static final String PREFIX = "qa_";
	private static final String SEASON = "launch-2026";
	private static final String NEXT_SEASON = "season-2";
	private static final String DEATH_ONE = "00000000-0000-0000-0000-000000000101";
	private static final String DEATH_TWO = "00000000-0000-0000-0000-000000000102";
	private static final String DEATH_THREE = "00000000-0000-0000-0000-000000000103";
	private static final String DEATH_ATOMIC_ROLLBACK = "00000000-0000-0000-0000-000000000201";
	private static final String DEATH_ATOMIC_COMMIT = "00000000-0000-0000-0000-000000000202";
	private static final long FIRST_TIME = 1_700_000_000_100L;

	private WorldAchievementLedgerSqliteTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected SQLite world-achievement migration path");
		}
		Class.forName("org.sqlite.JDBC");
		RealSqliteConnection connection = new RealSqliteConnection();
		try {
			applyMigration(connection, Paths.get(args[0]), PREFIX);
			testPrefixedSchemaAndNoRawNetworkIdentity(connection);
			testFirstRecordWinsAndSourceEventIsUnique(connection);
			testRecordTransactionRollback(connection);
			testPkEventReplayPairTimeAndLargeLoot(connection);
			testPkEventAndStreakMutationsSettleTogether(connection);
			testStreakInsertUpdateRollbackAndSeasonIsolation(connection);
			testInvalidStorageKeysFailSafely(connection);
		} finally {
			connection.close();
		}
		System.out.println("World achievement SQLite ledger tests passed.");
	}

	private static void testPrefixedSchemaAndNoRawNetworkIdentity(RealSqliteConnection connection)
		throws SQLException {
		for (String table : Arrays.asList("world_achievement_records", "world_pk_events", "world_pk_streaks")) {
			check(schemaObjectExists(connection, "table", PREFIX + table),
				"migration creates prefixed table " + table);
			check(!schemaObjectExists(connection, "table", table),
				"migration does not create an unprefixed table " + table);
			for (String column : tableColumns(connection, PREFIX + table)) {
				String normalized = column.toLowerCase();
				check(!normalized.equals("ip") && !normalized.equals("ip_address")
					&& !normalized.endsWith("_ip") && !normalized.contains("ip_address"),
					"world achievement tables never retain raw IP columns");
			}
		}
		for (String index : Arrays.asList(
			"world_achievement_type_time", "world_achievement_player_time",
			"world_pk_pair_qualified_time", "world_pk_qualified_time",
			"world_pk_killer_time", "world_pk_victim_time", "world_pk_streak_leaders")) {
			check(schemaObjectExists(connection, "index", PREFIX + index),
				"migration creates prefixed index " + index);
		}
	}

	private static void testFirstRecordWinsAndSourceEventIsUnique(RealSqliteConnection connection) {
		WorldAchievementRecord winner = record(
			SEASON, "first:item:795", 10, "Winner", 795, "origin:1");
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, winner) == 1,
			"first record claimant inserts exactly one row");
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, winner) == 0,
			"exact first-record replay is idempotent");

		WorldAchievementRecord competitor = record(
			SEASON, "first:item:795", 11, "Rival", 795, "origin:2");
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, competitor) == 0,
			"competitor cannot overwrite an existing record key");
		WorldAchievementRecord stored = WorldAchievementLedger.loadRecord(
			connection, PREFIX, SEASON, "first:item:795");
		check(stored != null && stored.playerId == 10 && "Winner".equals(stored.playerName)
			&& "origin:1".equals(stored.sourceEventKey),
			"immutable record retains the original winner and source event");

		WorldAchievementRecord reusedSource = record(
			SEASON, "first:item:594", 12, "Other", 594, "origin:1");
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, reusedSource) == 0,
			"exact source/type/event tuple cannot claim two records in one season");
		check(WorldAchievementLedger.loadRecord(connection, PREFIX, SEASON, "first:item:594") == null,
			"rejected source-event reuse leaves no partial row");

		WorldAchievementRecord differentSource = record(
			SEASON, "first:item:593", 12, "Other", 593, "origin:1");
		differentSource.source = "cracker_open";
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, differentSource) == 1,
			"same event key remains independent across source namespaces");

		WorldAchievementRecord differentType = record(
			SEASON, "first:skill:0:80", 13, "Skiller", 0, "origin:1");
		differentType.source = winner.source;
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, differentType) == 1,
			"same source/event key remains independent across record types");

		WorldAchievementRecord nextSeason = record(
			NEXT_SEASON, "first:item:795", 11, "Rival", 795, "origin:1");
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, nextSeason) == 1,
			"record keys and source-event uniqueness are isolated by season");

		WorldAchievementRecord noSourceOne = record(SEASON, "first:skill:1:80", 13, "Skiller", 1, null);
		WorldAchievementRecord noSourceTwo = record(SEASON, "first:skill:2:80", 13, "Skiller", 2, null);
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, noSourceOne) == 1
			&& WorldAchievementLedger.insertRecord(connection, PREFIX, noSourceTwo) == 1,
			"nullable source-event uniqueness permits unrelated claims without source rows");
	}

	private static void testRecordTransactionRollback(RealSqliteConnection connection) throws Exception {
		connection.getConnectionLock().lock();
		try {
			connection.execute("BEGIN TRANSACTION");
			WorldAchievementRecord tentative = record(
				SEASON, "first:item:1346", 14, "Rollback", 1346, "origin:44");
			check(WorldAchievementLedger.insertRecord(connection, PREFIX, tentative) == 1,
				"tentative record is inserted inside its owning transaction");
			check(WorldAchievementLedger.loadRecord(connection, PREFIX, SEASON, tentative.recordKey) != null,
				"tentative record is visible to its owning transaction");
			connection.execute("ROLLBACK");
		} finally {
			connection.getConnectionLock().unlock();
		}
		check(WorldAchievementLedger.loadRecord(connection, PREFIX, SEASON, "first:item:1346") == null,
			"record rollback removes the tentative claim");
	}

	private static void testPkEventReplayPairTimeAndLargeLoot(RealSqliteConnection connection) {
		WorldPkEvent first = pkEvent(DEATH_ONE, SEASON, 10, 20, true, FIRST_TIME);
		first.lootValue = 5_000_000_000L;
		first.streakAfter = 3;
		check(WorldAchievementLedger.insertPkEvent(connection, PREFIX, first) == 1,
			"first death id inserts one immutable PK event");

		WorldPkEvent replay = pkEvent(DEATH_ONE, SEASON, 10, 20, false, FIRST_TIME + 99L);
		replay.rejectReason = "replay";
		replay.lootValue = 1L;
		check(WorldAchievementLedger.insertPkEvent(connection, PREFIX, replay) == 0,
			"death-id replay cannot insert or overwrite an event");
		WorldPkEvent stored = WorldAchievementLedger.loadPkEvent(connection, PREFIX, DEATH_ONE);
		check(stored != null && stored.qualified && stored.lootValue == 5_000_000_000L
			&& stored.streakAfter == 3 && stored.killerAccountId == 5_000_000_000L
			&& stored.victimAccountId == null,
			"PK event preserves nullable/64-bit accounts and loot values larger than 32 bits");

		WorldPkEvent rejectedLater = pkEvent(DEATH_TWO, SEASON, 10, 20, false, FIRST_TIME + 1_000L);
		rejectedLater.rejectReason = "repeat_pair";
		check(WorldAchievementLedger.insertPkEvent(connection, PREFIX, rejectedLater) == 1,
			"rejected PK event remains available for audit");
		check(Long.valueOf(FIRST_TIME).equals(WorldAchievementLedger.loadLastQualifiedPairTime(
			connection, PREFIX, SEASON, 10, 20)),
			"pair cooldown query ignores later rejected events");

		WorldPkEvent qualifiedLater = pkEvent(DEATH_THREE, SEASON, 20, 10, true, FIRST_TIME + 2_000L);
		check(WorldAchievementLedger.insertPkEvent(connection, PREFIX, qualifiedLater) == 1,
			"ordered pair accepts either killer/victim direction");
		check(Long.valueOf(FIRST_TIME + 2_000L).equals(
			WorldAchievementLedger.loadLastQualifiedPairTime(connection, PREFIX, SEASON, 10, 20)),
			"pair query returns the most recent qualified event");
		check(WorldAchievementLedger.loadLastQualifiedPairTime(
			connection, PREFIX, NEXT_SEASON, 10, 20) == null,
			"pair timestamp query is isolated by season");
	}

	private static void testPkEventAndStreakMutationsSettleTogether(
		RealSqliteConnection connection) throws Exception {
		String season = "atomic-pk";
		WorldPkStreak killer = streak(season, 30, "Killer", 1, 1, 1L, FIRST_TIME);
		WorldPkStreak victim = streak(season, 40, "Victim", 0, 4, 9L, FIRST_TIME);

		connection.getConnectionLock().lock();
		try {
			connection.execute("BEGIN TRANSACTION");
			check(WorldAchievementLedger.insertPkEvent(connection, PREFIX,
				pkEvent(DEATH_ATOMIC_ROLLBACK, season, 30, 40, true, FIRST_TIME)) == 1,
				"atomic rollback fixture inserts its immutable event");
			check(WorldAchievementLedger.insertPkStreak(connection, PREFIX, killer) == 1
				&& WorldAchievementLedger.insertPkStreak(connection, PREFIX, victim) == 1,
				"atomic rollback fixture inserts both streak projections");
			connection.execute("ROLLBACK");
		} finally {
			connection.getConnectionLock().unlock();
		}
		check(WorldAchievementLedger.loadPkEvent(connection, PREFIX, DEATH_ATOMIC_ROLLBACK) == null
			&& WorldAchievementLedger.loadPkStreak(connection, PREFIX, season, 30) == null
			&& WorldAchievementLedger.loadPkStreak(connection, PREFIX, season, 40) == null,
			"event and both streak mutations roll back together");

		connection.getConnectionLock().lock();
		try {
			connection.execute("BEGIN TRANSACTION");
			check(WorldAchievementLedger.insertPkEvent(connection, PREFIX,
				pkEvent(DEATH_ATOMIC_COMMIT, season, 30, 40, true, FIRST_TIME + 1L)) == 1,
				"atomic commit fixture inserts its immutable event");
			check(WorldAchievementLedger.insertPkStreak(connection, PREFIX, killer) == 1
				&& WorldAchievementLedger.insertPkStreak(connection, PREFIX, victim) == 1,
				"atomic commit fixture inserts both streak projections");
			connection.execute("COMMIT");
		} finally {
			connection.getConnectionLock().unlock();
		}
		check(WorldAchievementLedger.loadPkEvent(connection, PREFIX, DEATH_ATOMIC_COMMIT) != null
			&& WorldAchievementLedger.loadPkStreak(connection, PREFIX, season, 30) != null
			&& WorldAchievementLedger.loadPkStreak(connection, PREFIX, season, 40) != null,
			"event and both streak mutations commit together");

		int replayInserted = WorldAchievementLedger.insertPkEvent(connection, PREFIX,
			pkEvent(DEATH_ATOMIC_COMMIT, season, 30, 40, true, FIRST_TIME + 2L));
		if (replayInserted == 1) {
			killer.currentStreak = 99;
			WorldAchievementLedger.updatePkStreak(connection, PREFIX, killer);
		}
		check(replayInserted == 0
			&& WorldAchievementLedger.loadPkStreak(connection, PREFIX, season, 30).currentStreak == 1,
			"replay event returns zero before the coordinator pattern mutates streaks");
	}

	private static void testStreakInsertUpdateRollbackAndSeasonIsolation(
		RealSqliteConnection connection) throws Exception {
		WorldPkStreak streak = streak(SEASON, 10, "Winner", 1, 1, 1L, FIRST_TIME);
		check(WorldAchievementLedger.insertPkStreak(connection, PREFIX, streak) == 1,
			"first season/player streak projection inserts");
		WorldPkStreak duplicate = streak(SEASON, 10, "Rival", 99, 99, 99L, FIRST_TIME + 1L);
		check(WorldAchievementLedger.insertPkStreak(connection, PREFIX, duplicate) == 0,
			"streak insert cannot overwrite an existing projection");
		check("Winner".equals(WorldAchievementLedger.loadPkStreak(connection, PREFIX, SEASON, 10).playerName),
			"duplicate streak insert leaves the original projection intact");

		streak.currentStreak = 3;
		streak.bestStreak = 3;
		streak.qualifiedKills = 3L;
		streak.lastQualifiedAtMs = FIRST_TIME + 2_000L;
		streak.updatedAtMs = FIRST_TIME + 2_000L;
		check(WorldAchievementLedger.updatePkStreak(connection, PREFIX, streak) == 1,
			"existing streak projection updates with an affected-row result");
		check(WorldAchievementLedger.loadPkStreak(connection, PREFIX, SEASON, 10).currentStreak == 3,
			"committed streak update is durable");

		connection.getConnectionLock().lock();
		try {
			connection.execute("BEGIN TRANSACTION");
			streak.currentStreak = 8;
			streak.bestStreak = 8;
			streak.qualifiedKills = 8L;
			streak.updatedAtMs = FIRST_TIME + 3_000L;
			check(WorldAchievementLedger.updatePkStreak(connection, PREFIX, streak) == 1,
				"streak update participates in the caller's transaction");
			connection.execute("ROLLBACK");
		} finally {
			connection.getConnectionLock().unlock();
		}
		WorldPkStreak rolledBack = WorldAchievementLedger.loadPkStreak(connection, PREFIX, SEASON, 10);
		check(rolledBack.currentStreak == 3 && rolledBack.bestStreak == 3
			&& rolledBack.qualifiedKills == 3L,
			"streak rollback restores the prior projection");

		WorldPkStreak nextSeason = streak(NEXT_SEASON, 10, "Winner", 0, 4, 7L, FIRST_TIME + 4_000L);
		check(WorldAchievementLedger.insertPkStreak(connection, PREFIX, nextSeason) == 1,
			"same player has an independent projection in another season");
		check(WorldAchievementLedger.loadPkStreak(connection, PREFIX, NEXT_SEASON, 10).bestStreak == 4
			&& WorldAchievementLedger.loadPkStreak(connection, PREFIX, SEASON, 10).bestStreak == 3,
			"streak loads remain season-isolated");
		WorldPkStreak missing = streak(SEASON, 999, "Missing", 1, 1, 1L, FIRST_TIME);
		check(WorldAchievementLedger.updatePkStreak(connection, PREFIX, missing) == 0,
			"update reports zero when the streak projection does not exist");
	}

	private static void testInvalidStorageKeysFailSafely(RealSqliteConnection connection) {
		check(WorldAchievementLedger.loadRecord(connection, PREFIX, "", "first:item:1") == null,
			"empty season id is rejected without querying");
		WorldAchievementRecord invalidPlayer = record(SEASON, "first:item:1", 0, "Nobody", 1, null);
		check(WorldAchievementLedger.insertRecord(connection, PREFIX, invalidPlayer) == 0,
			"nonpositive record player id is rejected without mutation");
		check(WorldAchievementLedger.loadPkEvent(connection, PREFIX,
			"not-a-canonical-uuid") == null,
			"malformed death id is rejected without querying");
		WorldPkEvent malformed = pkEvent(DEATH_ONE.toUpperCase(), NEXT_SEASON, 30, 40, true, FIRST_TIME);
		check(WorldAchievementLedger.insertPkEvent(connection, PREFIX, malformed) == 0,
			"noncanonical death UUID is rejected without mutation");
		malformed.deathId = DEATH_ONE + "0";
		check(WorldAchievementLedger.insertPkEvent(connection, PREFIX, malformed) == 0,
			"oversize death UUID is rejected without mutation");

		for (String invalidPrefix : Arrays.asList(null, "bad-prefix", "bad prefix", "bad`prefix",
			"prefix_that_is_more_than_thirty_two_chars_long")) {
			boolean rejected = false;
			try {
				WorldAchievementLedger.loadRecord(connection, invalidPrefix, SEASON, "first:item:1");
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}
			check(rejected, "unsafe table prefix is rejected before SQL construction");
		}
	}

	private static WorldAchievementRecord record(String seasonId, String key, int playerId,
		String playerName, int subjectId, String sourceEventKey) {
		WorldAchievementRecord record = new WorldAchievementRecord();
		record.seasonId = seasonId;
		record.recordKey = key;
		record.recordType = key.startsWith("first:skill:") ? "first_skill" : "first_item";
		record.playerId = playerId;
		record.playerName = playerName;
		record.subjectId = subjectId;
		record.value = key.startsWith("first:skill:") ? 80L : 1L;
		record.source = key.startsWith("first:skill:") ? "skill_level" : "npc_drop";
		record.sourceEventKey = sourceEventKey;
		record.claimedAtMs = FIRST_TIME + playerId;
		record.detail = "fixture=true";
		return record;
	}

	private static WorldPkEvent pkEvent(String deathId, String seasonId, int killerId,
		int victimId, boolean qualified, long occurredAtMs) {
		WorldPkEvent event = new WorldPkEvent();
		event.deathId = deathId;
		event.seasonId = seasonId;
		event.killerPlayerId = killerId;
		event.killerAccountId = killerId == 10 ? 5_000_000_000L : (long) killerId + 1_000L;
		event.killerName = killerId == 10 ? "Winner" : "Killer";
		event.victimPlayerId = victimId;
		event.victimAccountId = victimId == 20 ? null : (long) victimId + 1_000L;
		event.victimName = victimId == 20 ? "Victim" : "Target";
		event.pairLowPlayerId = Math.min(killerId, victimId);
		event.pairHighPlayerId = Math.max(killerId, victimId);
		event.qualified = qualified;
		event.rejectReason = qualified ? "" : "not_qualified";
		event.victimWasSkulled = true;
		event.victimDamage = 20;
		event.lootValue = 50_000L;
		event.streakAfter = qualified ? 1 : 0;
		event.endedStreak = 0;
		event.wildernessLevel = 20;
		event.occurredAtMs = occurredAtMs;
		return event;
	}

	private static WorldPkStreak streak(String seasonId, int playerId, String playerName,
		int current, int best, long kills, long updatedAtMs) {
		WorldPkStreak streak = new WorldPkStreak();
		streak.seasonId = seasonId;
		streak.playerId = playerId;
		streak.playerName = playerName;
		streak.currentStreak = current;
		streak.bestStreak = best;
		streak.qualifiedKills = kills;
		streak.lastQualifiedAtMs = current == 0 ? 0L : updatedAtMs;
		streak.updatedAtMs = updatedAtMs;
		return streak;
	}

	private static void applyMigration(RealSqliteConnection connection, Path migration,
		String prefix) throws Exception {
		String sql = new String(Files.readAllBytes(migration), StandardCharsets.UTF_8)
			.replace("_PREFIX_", prefix);
		for (String statement : sql.split(";")) {
			String trimmed = statement.trim();
			if (!trimmed.isEmpty()) connection.execute(trimmed);
		}
	}

	private static boolean schemaObjectExists(RealSqliteConnection connection,
		String type, String name) throws SQLException {
		try (PreparedStatement statement = connection.getConnection().prepareStatement(
			"SELECT COUNT(*) FROM sqlite_master WHERE type=? AND name=?")) {
			statement.setString(1, type);
			statement.setString(2, name);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() && result.getInt(1) == 1;
			}
		}
	}

	private static Set<String> tableColumns(RealSqliteConnection connection, String table)
		throws SQLException {
		Set<String> columns = new HashSet<>();
		try (Statement statement = connection.getConnection().createStatement();
			 ResultSet result = statement.executeQuery("PRAGMA table_info(`" + table + "`)")) {
			while (result.next()) columns.add(result.getString("name"));
		}
		return columns;
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static final class RealSqliteConnection extends JDBCDatabaseConnection {
		private final Connection connection;

		private RealSqliteConnection() throws SQLException {
			connection = DriverManager.getConnection("jdbc:sqlite::memory:");
		}

		@Override
		protected Statement getStatement() {
			try {
				return connection.createStatement();
			} catch (SQLException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public Connection getConnection() {
			return connection;
		}

		@Override
		protected boolean checkConnection() {
			return isConnected();
		}

		@Override
		public boolean isConnected() {
			try {
				return !connection.isClosed();
			} catch (SQLException ex) {
				return false;
			}
		}

		@Override
		public boolean open() {
			return isConnected();
		}

		@Override
		public void close() {
			try {
				connection.close();
			} catch (SQLException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public DatabaseType getDatabaseType() {
			return DatabaseType.SQLITE;
		}
	}
}
