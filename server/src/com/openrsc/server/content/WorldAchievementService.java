package com.openrsc.server.content;

import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.struct.WorldAchievementRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Durable coordinator for server-first progression records.
 *
 * This service deliberately owns no player mutation or presentation callback. Callers
 * may publish the highest newly claimed level only after this method returns a committed
 * result. Existing records, rollbacks, and unresolved outcomes always return no claims.
 */
public final class WorldAchievementService {
	private static final Logger LOGGER = LogManager.getLogger(WorldAchievementService.class);
	private static final int[] FIRST_SKILL_LEVELS = {80, 90, 99};
	private static final int MAX_SEASON_ID_LENGTH = 32;
	private static final int MAX_PLAYER_NAME_LENGTH = 12;
	private static final String FIRST_SKILL_TYPE = "first_skill";
	private static final String SKILL_LEVEL_SOURCE = "skill_level";

	private final RecordPort port;
	private final boolean enabled;
	private final String seasonId;
	private final TimeSource timeSource;

	public WorldAchievementService(GameDatabase database, boolean enabled, String seasonId) {
		this(new DatabaseRecordPort(database), enabled, seasonId, System::currentTimeMillis);
	}

	WorldAchievementService(RecordPort port, boolean enabled, String seasonId,
		TimeSource timeSource) {
		if (port == null) {
			throw new IllegalArgumentException("World achievement record port is required");
		}
		if (timeSource == null) {
			throw new IllegalArgumentException("World achievement time source is required");
		}
		this.port = port;
		this.seasonId = normalizeSeasonId(seasonId);
		this.enabled = enabled && !this.seasonId.isEmpty();
		this.timeSource = timeSource;
		if (enabled && this.seasonId.isEmpty()) {
			LOGGER.error("World achievements disabled because the season id is invalid");
		}
	}

	/**
	 * Claims every launch first-skill threshold crossed by one ordinary player.
	 * Thresholds are attempted in ascending order inside one settled transaction.
	 */
	public SkillClaimResult claimFirstSkillLevels(int playerId, String playerName,
		boolean elevated, int skill, int oldLevel, int newLevel) {
		final String normalizedName = normalizePlayerName(playerName);
		if (!enabled || elevated || playerId <= 0 || normalizedName.isEmpty()
			|| skill < 0 || oldLevel >= newLevel) {
			return SkillClaimResult.none();
		}

		final List<Integer> crossedLevels = crossedFirstSkillLevels(oldLevel, newLevel);
		if (crossedLevels.isEmpty()) {
			return SkillClaimResult.none();
		}

		final long claimedAtMs = timeSource.currentTimeMillis();
		if (claimedAtMs <= 0L) {
			LOGGER.error("World first-skill claim rejected because the clock was not positive");
			return SkillClaimResult.none();
		}

		final List<WorldAchievementRecord> inserted = new ArrayList<>();
		final AtomicTransactionOutcome outcome;
		try {
			outcome = port.atomically(() -> {
				for (int level : crossedLevels) {
					final WorldAchievementRecord record = firstSkillRecord(
						playerId, normalizedName, skill, level, claimedAtMs);
					final int insertCount = port.insertRecord(record);
					if (insertCount == 1) {
						inserted.add(record);
					} else if (insertCount != 0) {
						throw new IllegalStateException(
							"World achievement insert returned " + insertCount);
					}
				}
			}, () -> verifyInsertedRecords(inserted));
		} catch (RuntimeException ex) {
			LOGGER.error("Unable to settle first-skill claims for player " + playerId, ex);
			return SkillClaimResult.none();
		}

		if (outcome != AtomicTransactionOutcome.COMMITTED) {
			if (outcome == AtomicTransactionOutcome.ROLLED_BACK) {
				LOGGER.warn("First-skill claim transaction rolled back for player {}", playerId);
			} else {
				LOGGER.error("First-skill claim transaction has unresolved outcome for player {}",
					playerId);
			}
			return SkillClaimResult.none();
		}

		if (inserted.isEmpty()) {
			return SkillClaimResult.none();
		}
		final List<Integer> claimedLevels = new ArrayList<>(inserted.size());
		for (WorldAchievementRecord record : inserted) {
			claimedLevels.add((int) record.value);
		}
		return new SkillClaimResult(claimedLevels);
	}

	private AtomicTransactionOutcome verifyInsertedRecords(
		List<WorldAchievementRecord> inserted) throws Exception {
		if (inserted.isEmpty()) {
			return AtomicTransactionOutcome.ROLLED_BACK;
		}

		int exactMatches = 0;
		int absent = 0;
		for (WorldAchievementRecord expected : inserted) {
			final WorldAchievementRecord durable =
				port.loadRecord(expected.seasonId, expected.recordKey);
			if (durable == null) {
				absent++;
			} else if (sameRecord(expected, durable)) {
				exactMatches++;
			} else {
				return AtomicTransactionOutcome.UNKNOWN;
			}
		}
		if (exactMatches == inserted.size()) {
			return AtomicTransactionOutcome.COMMITTED;
		}
		if (absent == inserted.size()) {
			return AtomicTransactionOutcome.ROLLED_BACK;
		}
		return AtomicTransactionOutcome.UNKNOWN;
	}

	private WorldAchievementRecord firstSkillRecord(int playerId, String playerName,
		int skill, int level, long claimedAtMs) {
		final WorldAchievementRecord record = new WorldAchievementRecord();
		record.seasonId = seasonId;
		record.recordKey = "first:skill:" + skill + ":" + level;
		record.recordType = FIRST_SKILL_TYPE;
		record.playerId = playerId;
		record.playerName = playerName;
		record.subjectId = skill;
		record.value = level;
		record.source = SKILL_LEVEL_SOURCE;
		record.sourceEventKey = null;
		record.claimedAtMs = claimedAtMs;
		record.detail = "skill=" + skill + " level=" + level;
		return record;
	}

	private static boolean sameRecord(WorldAchievementRecord expected,
		WorldAchievementRecord actual) {
		return same(expected.seasonId, actual.seasonId)
			&& same(expected.recordKey, actual.recordKey)
			&& same(expected.recordType, actual.recordType)
			&& expected.playerId == actual.playerId
			&& same(expected.playerName, actual.playerName)
			&& expected.subjectId == actual.subjectId
			&& expected.value == actual.value
			&& same(expected.source, actual.source)
			&& same(expected.sourceEventKey, actual.sourceEventKey)
			&& expected.claimedAtMs == actual.claimedAtMs
			&& same(expected.detail, actual.detail);
	}

	private static boolean same(Object first, Object second) {
		return first == null ? second == null : first.equals(second);
	}

	private static List<Integer> crossedFirstSkillLevels(int oldLevel, int newLevel) {
		final List<Integer> crossed = new ArrayList<>(FIRST_SKILL_LEVELS.length);
		for (int level : FIRST_SKILL_LEVELS) {
			if (oldLevel < level && newLevel >= level) {
				crossed.add(level);
			}
		}
		return crossed;
	}

	private static String normalizeSeasonId(String rawSeasonId) {
		if (rawSeasonId == null) {
			return "";
		}
		final String normalized = rawSeasonId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || normalized.length() > MAX_SEASON_ID_LENGTH) {
			return "";
		}
		for (int index = 0; index < normalized.length(); index++) {
			final char character = normalized.charAt(index);
			if ((character < 'a' || character > 'z')
				&& (character < '0' || character > '9')
				&& character != '-' && character != '_') {
				return "";
			}
		}
		return normalized;
	}

	private static String normalizePlayerName(String playerName) {
		if (playerName == null) {
			return "";
		}
		final String normalized = playerName.trim();
		return normalized.isEmpty() || normalized.length() > MAX_PLAYER_NAME_LENGTH
			? "" : normalized;
	}

	@FunctionalInterface
	interface TransactionBody {
		void run() throws Exception;
	}

	@FunctionalInterface
	interface TransactionVerifier {
		AtomicTransactionOutcome verify() throws Exception;
	}

	interface RecordPort {
		AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier);
		int insertRecord(WorldAchievementRecord record) throws Exception;
		WorldAchievementRecord loadRecord(String seasonId, String recordKey) throws Exception;
	}

	@FunctionalInterface
	interface TimeSource {
		long currentTimeMillis();
	}

	private static final class DatabaseRecordPort implements RecordPort {
		private final GameDatabase database;

		private DatabaseRecordPort(GameDatabase database) {
			if (database == null) {
				throw new IllegalArgumentException("World achievement database is required");
			}
			this.database = database;
		}

		@Override
		public AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier) {
			return database.atomicallySettled(body::run, verifier::verify);
		}

		@Override
		public int insertRecord(WorldAchievementRecord record) throws Exception {
			return database.queryInsertWorldAchievementRecord(record);
		}

		@Override
		public WorldAchievementRecord loadRecord(String seasonId, String recordKey)
			throws Exception {
			return database.queryLoadWorldAchievementRecord(seasonId, recordKey);
		}
	}

	public static final class SkillClaimResult {
		private static final SkillClaimResult NONE =
			new SkillClaimResult(Collections.emptyList());

		private final List<Integer> claimedLevels;
		private final int highestClaimedLevel;

		private SkillClaimResult(List<Integer> claimedLevels) {
			final List<Integer> copy = new ArrayList<>(claimedLevels);
			this.claimedLevels = Collections.unmodifiableList(copy);
			this.highestClaimedLevel = copy.isEmpty() ? 0 : copy.get(copy.size() - 1);
		}

		private static SkillClaimResult none() {
			return NONE;
		}

		public List<Integer> getClaimedLevels() {
			return claimedLevels;
		}

		/** Returns zero when this call won no record. */
		public int getHighestClaimedLevel() {
			return highestClaimedLevel;
		}

		public boolean hasClaims() {
			return !claimedLevels.isEmpty();
		}
	}
}
