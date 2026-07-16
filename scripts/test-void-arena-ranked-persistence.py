#!/usr/bin/env python3
"""Deterministic SQLite contract tests for ranked Void Arena persistence."""

from pathlib import Path
import sqlite3
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SQLITE_PATCHES = ROOT / "server" / "database" / "sqlite" / "patches"
BASE_PATCHES = (
    SQLITE_PATCHES / "2026_06_13_add_void_arena_ranked.sql",
    SQLITE_PATCHES / "2026_06_14_add_void_arena_match_ledger.sql",
)
HARDENING_PATCH = SQLITE_PATCHES / "2026_07_15_harden_void_arena_ranked_sessions.sql"
MYSQL_CORE = ROOT / "server" / "database" / "mysql" / "core.sql"
MYSQL_SNAPSHOT_PATCH = (
    ROOT
    / "server"
    / "database"
    / "mysql"
    / "patches"
    / "2026_07_15_expand_player_cache_for_void_arena_snapshot.sql"
)
MYSQL_BASE_PATCHES = (
    ROOT
    / "server"
    / "database"
    / "mysql"
    / "patches"
    / "2026_06_13_add_void_arena_ranked.sql",
    ROOT
    / "server"
    / "database"
    / "mysql"
    / "patches"
    / "2026_06_14_add_void_arena_match_ledger.sql",
)
SQLITE_CORE = ROOT / "server" / "database" / "sqlite" / "core.sqlite"
VOID_ARENA_SOURCE = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "content"
    / "voidarena"
    / "VoidArena.java"
)
AUGUST_2026_UTC_MS = 1_785_542_400_000

SESSION_COLUMNS = (
    "match_id",
    "season_id",
    "status",
    "result_reason",
    "player_a_id",
    "player_b_id",
    "winner_id",
    "loser_id",
    "player_a_rating_before",
    "player_a_rating_after",
    "player_b_rating_before",
    "player_b_rating_after",
    "rating_delta",
    "rating_applied",
    "same_ip",
    "same_ip_local_exempt",
    "prior_rated_results_30m",
    "prior_decisive_results_day",
    "slot_index",
    "started_at_ms",
    "ended_at_ms",
)

SETTLE_SQL = """
UPDATE voidarena_ranked_match_sessions
SET status = ?, result_reason = ?, winner_id = ?, loser_id = ?,
    player_a_rating_after = ?, player_b_rating_after = ?, rating_delta = ?,
    rating_applied = ?, ended_at_ms = ?
WHERE match_id = ? AND status = ? AND season_id = ?
  AND player_a_id = ? AND player_b_id = ?
  AND player_a_rating_before = ? AND player_b_rating_before = ?
  AND same_ip = ? AND same_ip_local_exempt = ?
  AND prior_rated_results_30m = ? AND prior_decisive_results_day = ?
  AND slot_index = ? AND started_at_ms = ?
"""

PAIR_AUDIT_SQL = """
SELECT MAX(CASE WHEN ended_at_ms >= ? THEN ended_at_ms ELSE NULL END)
           AS last_rated_result_at_ms,
       COALESCE(SUM(CASE WHEN ended_at_ms >= ? THEN 1 ELSE 0 END), 0)
           AS decisive_results_utc_day
FROM voidarena_ranked_match_sessions
WHERE player_a_id = ? AND player_b_id = ?
  AND status = 'SETTLED' AND result_reason IN ('DEATH', 'FORFEIT')
"""

RECONCILE_SQL = """
UPDATE voidarena_ranked_match_sessions
SET status = 'SETTLED', result_reason = ?, winner_id = NULL, loser_id = NULL,
    player_a_rating_after = player_a_rating_before,
    player_b_rating_after = player_b_rating_before,
    rating_delta = 0, rating_applied = 0,
    ended_at_ms = CASE WHEN started_at_ms > ? THEN started_at_ms ELSE ? END
WHERE status = 'ACTIVE'
"""


def patch_sql(path):
    """Model the server patch loader with an empty configured table prefix."""
    sql = path.read_text(encoding="utf-8").replace("_PREFIX_", "")
    if "_PREFIX_" in sql:
        raise AssertionError(f"unexpanded database prefix in {path}")
    return sql


class RankedPersistenceTests(unittest.TestCase):
    def setUp(self):
        self.tempdir = tempfile.TemporaryDirectory(prefix="void-arena-ranked-")
        self.db_path = Path(self.tempdir.name) / "ranked.db"
        self.connection = self.open_database(self.db_path)
        self.next_match_number = 1

    def tearDown(self):
        self.connection.close()
        self.tempdir.cleanup()

    @staticmethod
    def open_database(path, harden=True):
        connection = sqlite3.connect(path)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        for patch in BASE_PATCHES:
            connection.executescript(patch_sql(patch))
        if harden:
            connection.executescript(patch_sql(HARDENING_PATCH))
        return connection

    def match_id(self):
        match_id = f"00000000-0000-0000-0000-{self.next_match_number:012d}"
        self.next_match_number += 1
        return match_id

    def active_record(self, **overrides):
        record = {
            "match_id": self.match_id(),
            "season_id": "2026-07",
            "status": "ACTIVE",
            "result_reason": None,
            "player_a_id": 101,
            "player_b_id": 202,
            "winner_id": None,
            "loser_id": None,
            "player_a_rating_before": 1200,
            "player_a_rating_after": None,
            "player_b_rating_before": 1200,
            "player_b_rating_after": None,
            "rating_delta": 0,
            "rating_applied": 0,
            "same_ip": 0,
            "same_ip_local_exempt": 0,
            "prior_rated_results_30m": 0,
            "prior_decisive_results_day": 0,
            "slot_index": 0,
            "started_at_ms": 1_785_000_000_000,
            "ended_at_ms": None,
        }
        record.update(overrides)
        return record

    def insert_active(self, **overrides):
        record = self.active_record(**overrides)
        placeholders = ", ".join("?" for _ in SESSION_COLUMNS)
        columns = ", ".join(SESSION_COLUMNS)
        self.connection.execute(
            f"INSERT INTO voidarena_ranked_match_sessions ({columns}) VALUES ({placeholders})",
            tuple(record[column] for column in SESSION_COLUMNS),
        )
        return record

    def settle(
        self,
        active,
        *,
        reason,
        winner_id,
        loser_id,
        player_a_after,
        player_b_after,
        rating_delta,
        rating_applied,
        ended_at_ms=None,
        expected_overrides=None,
    ):
        expected = dict(active)
        expected.update(expected_overrides or {})
        ended_at_ms = ended_at_ms or active["started_at_ms"] + 60_000
        parameters = (
            "SETTLED",
            reason,
            winner_id,
            loser_id,
            player_a_after,
            player_b_after,
            rating_delta,
            int(rating_applied),
            ended_at_ms,
            expected["match_id"],
            "ACTIVE",
            expected["season_id"],
            expected["player_a_id"],
            expected["player_b_id"],
            expected["player_a_rating_before"],
            expected["player_b_rating_before"],
            expected["same_ip"],
            expected["same_ip_local_exempt"],
            expected["prior_rated_results_30m"],
            expected["prior_decisive_results_day"],
            expected["slot_index"],
            expected["started_at_ms"],
        )
        return self.connection.execute(SETTLE_SQL, parameters).rowcount

    def settle_a_win(self, active, delta=16, reason="DEATH", ended_at_ms=None):
        return self.settle(
            active,
            reason=reason,
            winner_id=active["player_a_id"],
            loser_id=active["player_b_id"],
            player_a_after=active["player_a_rating_before"] + delta,
            player_b_after=active["player_b_rating_before"] - delta,
            rating_delta=delta,
            rating_applied=True,
            ended_at_ms=ended_at_ms,
        )

    def pair_audit(self, player_a_id, player_b_id, rolling_cutoff_ms, utc_day_start_ms):
        row = self.connection.execute(
            PAIR_AUDIT_SQL,
            (rolling_cutoff_ms, utc_day_start_ms, player_a_id, player_b_id),
        ).fetchone()
        return row["last_rated_result_at_ms"], row["decisive_results_utc_day"]

    def assert_integrity_error(self, callback):
        with self.assertRaises(sqlite3.IntegrityError):
            callback()

    def test_snapshot_cache_schema_and_champion_keys_fit_production_limits(self):
        mysql_core = MYSQL_CORE.read_text(encoding="utf-8").lower()
        mysql_patch = MYSQL_SNAPSHOT_PATCH.read_text(encoding="utf-8").lower()
        sqlite_core = SQLITE_CORE.read_text(encoding="utf-8").lower()
        arena_source = VOID_ARENA_SOURCE.read_text(encoding="utf-8")

        self.assertIn("`value`    mediumtext", mysql_core)
        self.assertIn("modify column `value` mediumtext", mysql_patch)
        self.assertIn("`value`    text", sqlite_core)

        champion_keys = (
            "void_arena_last_champion_season",
            "void_arena_champ_p_202607",
            "void_arena_champ_r_202607",
            "void_arena_champ_t_202607",
        )
        for key in champion_keys:
            with self.subTest(key=key):
                self.assertLessEqual(len(key), 32)
        self.assertNotIn("void_arena_current_champion_", arena_source)

    def test_mysql_ranked_base_patches_honor_configured_table_prefix(self):
        expected_tables = (
            "`_PREFIX_voidarena_ranked_stats`",
            "`_PREFIX_voidarena_ranked_matches`",
        )
        for patch, expected_table in zip(MYSQL_BASE_PATCHES, expected_tables):
            with self.subTest(patch=patch.name):
                sql = patch.read_text(encoding="utf-8")
                self.assertIn(f"CREATE TABLE IF NOT EXISTS {expected_table}", sql)
                expanded = sql.replace("_PREFIX_", "vs_")
                self.assertIn(expected_table.replace("_PREFIX_", "vs_"), expanded)
                self.assertNotIn("_PREFIX_", expanded)

    def test_patch_migrates_global_rows_to_legacy_without_touching_named_seasons(self):
        legacy_path = Path(self.tempdir.name) / "legacy.db"
        connection = self.open_database(legacy_path, harden=False)
        try:
            connection.execute(
                "INSERT INTO voidarena_ranked_stats (seasonID, playerID) VALUES ('global', 1)"
            )
            connection.execute(
                "INSERT INTO voidarena_ranked_stats (seasonID, playerID) VALUES ('2026-06', 2)"
            )
            connection.execute(
                """
                INSERT INTO voidarena_ranked_matches
                    (seasonID, winnerID, loserID, winnerRatingBefore, winnerRatingAfter,
                     loserRatingBefore, loserRatingAfter, ratingDelta)
                VALUES ('global', 1, 2, 1200, 1216, 1200, 1184, 16)
                """
            )
            connection.executescript(patch_sql(HARDENING_PATCH))

            stat_seasons = [
                row[0]
                for row in connection.execute(
                    "SELECT seasonID FROM voidarena_ranked_stats ORDER BY playerID"
                )
            ]
            match_season = connection.execute(
                "SELECT seasonID FROM voidarena_ranked_matches"
            ).fetchone()[0]
            self.assertEqual(["LEGACY", "2026-06"], stat_seasons)
            self.assertEqual("LEGACY", match_season)
        finally:
            connection.close()

    def test_active_insert_enforces_identity_snapshot_and_antifarm_constraints(self):
        valid = self.insert_active(same_ip=1, same_ip_local_exempt=1, slot_index=7)
        self.assertEqual("ACTIVE", self.connection.execute(
            "SELECT status FROM voidarena_ranked_match_sessions WHERE match_id = ?",
            (valid["match_id"],),
        ).fetchone()[0])

        invalid_overrides = (
            {"match_id": "too-short"},
            {"season_id": ""},
            {"season_id": "x" * 17},
            {"status": "BROKEN"},
            {"result_reason": "DEATH"},
            {"player_a_id": 0},
            {"player_a_id": 202, "player_b_id": 101},
            {"player_a_id": 101, "player_b_id": 101},
            {"player_a_rating_before": 0},
            {"player_b_rating_before": 0},
            {"same_ip": 1, "same_ip_local_exempt": 0},
            {"same_ip": 0, "same_ip_local_exempt": 1},
            {"same_ip": 2, "same_ip_local_exempt": 2},
            {"prior_rated_results_30m": 1},
            {"prior_decisive_results_day": -1},
            {"prior_decisive_results_day": 3},
            {"started_at_ms": 0},
            {"winner_id": 101},
            {"player_a_rating_after": 1200},
            {"rating_delta": 1},
            {"rating_applied": 1},
            {"ended_at_ms": 1_785_000_000_001},
        )
        for overrides in invalid_overrides:
            with self.subTest(overrides=overrides):
                self.assert_integrity_error(lambda overrides=overrides: self.insert_active(**overrides))

        self.assert_integrity_error(lambda: self.insert_active(match_id=valid["match_id"]))

    def test_active_slot_index_must_be_nonnegative(self):
        self.assert_integrity_error(lambda: self.insert_active(slot_index=-1))

    def test_settlement_claims_active_snapshot_exactly_once(self):
        active = self.insert_active(slot_index=4)

        wrong_snapshot = self.settle(
            active,
            reason="DEATH",
            winner_id=active["player_a_id"],
            loser_id=active["player_b_id"],
            player_a_after=1216,
            player_b_after=1184,
            rating_delta=16,
            rating_applied=True,
            expected_overrides={"slot_index": 5},
        )
        self.assertEqual(0, wrong_snapshot)
        self.assertEqual(1, self.settle_a_win(active))
        self.assertEqual(0, self.settle_a_win(active))
        self.assertEqual(
            0,
            self.settle(
                active,
                reason="FORFEIT",
                winner_id=active["player_b_id"],
                loser_id=active["player_a_id"],
                player_a_after=1184,
                player_b_after=1216,
                rating_delta=-16,
                rating_applied=True,
            ),
        )

        row = self.connection.execute(
            "SELECT * FROM voidarena_ranked_match_sessions WHERE match_id = ?",
            (active["match_id"],),
        ).fetchone()
        self.assertEqual("SETTLED", row["status"])
        self.assertEqual("DEATH", row["result_reason"])
        self.assertEqual(1216, row["player_a_rating_after"])
        self.assertEqual(1184, row["player_b_rating_after"])

    def test_terminal_row_and_both_stat_updates_commit_or_rollback_as_one_unit(self):
        def insert_stats(season_id, player_id):
            self.connection.execute(
                """
                INSERT INTO voidarena_ranked_stats
                    (seasonID, playerID, rating, wins, losses, disconnectLosses,
                     resetCount, updatedAt)
                VALUES (?, ?, 1200, 0, 0, 0, 0, 0)
                """,
                (season_id, player_id),
            )

        def update_stats(season_id, player_id, rating, wins, losses, updated_at):
            changed = self.connection.execute(
                """
                UPDATE voidarena_ranked_stats
                SET rating = ?, wins = ?, losses = ?, updatedAt = ?
                WHERE seasonID = ? AND playerID = ?
                """,
                (rating, wins, losses, updated_at, season_id, player_id),
            ).rowcount
            self.assertEqual(1, changed)

        committed = self.insert_active(season_id="2026-09")
        insert_stats("2026-09", committed["player_a_id"])
        insert_stats("2026-09", committed["player_b_id"])
        self.connection.commit()

        ended_at = committed["started_at_ms"] + 60_000
        with self.connection:
            self.assertEqual(1, self.settle_a_win(committed, ended_at_ms=ended_at))
            update_stats("2026-09", committed["player_a_id"], 1216, 1, 0, ended_at)
            update_stats("2026-09", committed["player_b_id"], 1184, 0, 1, ended_at)

        committed_row = self.connection.execute(
            "SELECT status FROM voidarena_ranked_match_sessions WHERE match_id = ?",
            (committed["match_id"],),
        ).fetchone()
        committed_stats = self.connection.execute(
            """
            SELECT playerID, rating, wins, losses
            FROM voidarena_ranked_stats WHERE seasonID = '2026-09'
            ORDER BY playerID
            """
        ).fetchall()
        self.assertEqual("SETTLED", committed_row["status"])
        self.assertEqual(
            [(101, 1216, 1, 0), (202, 1184, 0, 1)],
            [tuple(row) for row in committed_stats],
        )

        rolled_back = self.insert_active(
            season_id="2026-10", player_a_id=303, player_b_id=404
        )
        insert_stats("2026-10", rolled_back["player_a_id"])
        insert_stats("2026-10", rolled_back["player_b_id"])
        self.connection.commit()

        with self.assertRaisesRegex(RuntimeError, "injected second-stat failure"):
            with self.connection:
                self.assertEqual(1, self.settle_a_win(rolled_back, ended_at_ms=ended_at))
                update_stats("2026-10", rolled_back["player_a_id"], 1216, 1, 0, ended_at)
                raise RuntimeError("injected second-stat failure")

        rolled_back_row = self.connection.execute(
            "SELECT status FROM voidarena_ranked_match_sessions WHERE match_id = ?",
            (rolled_back["match_id"],),
        ).fetchone()
        rolled_back_stats = self.connection.execute(
            """
            SELECT rating, wins, losses FROM voidarena_ranked_stats
            WHERE seasonID = '2026-10' ORDER BY playerID
            """
        ).fetchall()
        self.assertEqual("ACTIVE", rolled_back_row["status"])
        self.assertEqual([(1200, 0, 0), (1200, 0, 0)], [tuple(row) for row in rolled_back_stats])

    def test_decisive_results_are_strictly_zero_sum_in_both_directions(self):
        player_a_wins = self.insert_active()
        self.assertEqual(1, self.settle_a_win(player_a_wins, delta=24))

        player_b_wins = self.insert_active()
        self.assertEqual(
            1,
            self.settle(
                player_b_wins,
                reason="FORFEIT",
                winner_id=player_b_wins["player_b_id"],
                loser_id=player_b_wins["player_a_id"],
                player_a_after=1176,
                player_b_after=1224,
                rating_delta=-24,
                rating_applied=True,
            ),
        )

        non_zero_sum = self.insert_active()
        self.assert_integrity_error(
            lambda: self.settle(
                non_zero_sum,
                reason="DEATH",
                winner_id=non_zero_sum["player_a_id"],
                loser_id=non_zero_sum["player_b_id"],
                player_a_after=1216,
                player_b_after=1185,
                rating_delta=16,
                rating_applied=True,
            )
        )

        wrong_direction = self.insert_active()
        self.assert_integrity_error(
            lambda: self.settle(
                wrong_direction,
                reason="DEATH",
                winner_id=wrong_direction["player_a_id"],
                loser_id=wrong_direction["player_b_id"],
                player_a_after=1184,
                player_b_after=1216,
                rating_delta=-16,
                rating_applied=True,
            )
        )

    def test_decisive_zero_delta_is_valid_at_the_rating_floor(self):
        active = self.insert_active(
            player_a_rating_before=1600,
            player_b_rating_before=1,
        )
        self.assertEqual(1, self.settle_a_win(active, delta=0))
        row = self.connection.execute(
            "SELECT * FROM voidarena_ranked_match_sessions WHERE match_id = ?",
            (active["match_id"],),
        ).fetchone()
        self.assertEqual(1, row["rating_applied"])
        self.assertEqual(0, row["rating_delta"])
        self.assertEqual(1601, row["player_a_rating_after"] + row["player_b_rating_after"])

        below_floor = self.insert_active(player_b_rating_before=1)
        self.assert_integrity_error(
            lambda: self.settle(
                below_floor,
                reason="DEATH",
                winner_id=below_floor["player_a_id"],
                loser_id=below_floor["player_b_id"],
                player_a_after=1201,
                player_b_after=0,
                rating_delta=1,
                rating_applied=True,
            )
        )

    def test_decisive_results_require_both_canonical_participants(self):
        for winner_id, loser_id in ((None, 202), (101, None), (303, 202), (101, 303)):
            active = self.insert_active()
            with self.subTest(winner_id=winner_id, loser_id=loser_id):
                self.assert_integrity_error(
                    lambda active=active, winner_id=winner_id, loser_id=loser_id: self.settle(
                        active,
                        reason="DEATH",
                        winner_id=winner_id,
                        loser_id=loser_id,
                        player_a_after=1216,
                        player_b_after=1184,
                        rating_delta=16,
                        rating_applied=True,
                    )
                )

    def test_neutral_results_preserve_ratings_and_have_no_winner(self):
        neutral_reasons = (
            "TIMEOUT_DRAW",
            "SERVER_SHUTDOWN_NO_CONTEST",
            "SERVER_RESTART_NO_CONTEST",
        )
        for reason in neutral_reasons:
            active = self.insert_active()
            with self.subTest(reason=reason):
                self.assertEqual(
                    1,
                    self.settle(
                        active,
                        reason=reason,
                        winner_id=None,
                        loser_id=None,
                        player_a_after=1200,
                        player_b_after=1200,
                        rating_delta=0,
                        rating_applied=False,
                    ),
                )

        invalid_cases = (
            {"winner_id": 101},
            {"player_a_after": 1201},
            {"player_a_after": 1201, "player_b_after": 1199, "rating_delta": 1},
            {"rating_applied": True},
        )
        for invalid in invalid_cases:
            active = self.insert_active()
            values = {
                "winner_id": None,
                "loser_id": None,
                "player_a_after": 1200,
                "player_b_after": 1200,
                "rating_delta": 0,
                "rating_applied": False,
            }
            values.update(invalid)
            with self.subTest(invalid=invalid):
                self.assert_integrity_error(
                    lambda active=active, values=values: self.settle(
                        active,
                        reason="TIMEOUT_DRAW",
                        **values,
                    )
                )

        active = self.insert_active()
        self.assert_integrity_error(
            lambda: self.settle(
                active,
                reason="TIMEOUT_DRAW",
                winner_id=None,
                loser_id=None,
                player_a_after=1200,
                player_b_after=1200,
                rating_delta=0,
                rating_applied=False,
                ended_at_ms=active["started_at_ms"] - 1,
            )
        )

    def test_pair_audit_enforces_rolling_cooldown_across_monthly_rollover(self):
        month_boundary = AUGUST_2026_UTC_MS
        now = month_boundary + 5 * 60_000
        previous_end = month_boundary - 5 * 60_000
        previous = self.insert_active(
            season_id="2026-07",
            started_at_ms=previous_end - 60_000,
        )
        self.assertEqual(1, self.settle_a_win(previous, ended_at_ms=previous_end))

        timeout = self.insert_active(
            season_id="2026-08",
            started_at_ms=month_boundary + 60_000,
        )
        self.assertEqual(
            1,
            self.settle(
                timeout,
                reason="TIMEOUT_DRAW",
                winner_id=None,
                loser_id=None,
                player_a_after=1200,
                player_b_after=1200,
                rating_delta=0,
                rating_applied=False,
                ended_at_ms=month_boundary + 2 * 60_000,
            ),
        )

        last_result, daily_count = self.pair_audit(
            101,
            202,
            now - 30 * 60_000,
            month_boundary,
        )
        self.assertEqual(previous_end, last_result)
        self.assertEqual(0, daily_count)

    def test_pair_audit_counts_three_decisive_results_and_ignores_neutral_rows(self):
        day_start = AUGUST_2026_UTC_MS
        now = day_start + 20 * 60 * 60_000
        for prior_count, hour in enumerate((1, 3, 5)):
            ended_at = day_start + hour * 60 * 60_000
            active = self.insert_active(
                prior_decisive_results_day=prior_count,
                started_at_ms=ended_at - 60_000,
            )
            self.assertEqual(1, self.settle_a_win(active, ended_at_ms=ended_at))

        neutral = self.insert_active(started_at_ms=day_start + 6 * 60 * 60_000)
        self.assertEqual(
            1,
            self.settle(
                neutral,
                reason="TIMEOUT_DRAW",
                winner_id=None,
                loser_id=None,
                player_a_after=1200,
                player_b_after=1200,
                rating_delta=0,
                rating_applied=False,
                ended_at_ms=day_start + 7 * 60 * 60_000,
            ),
        )

        last_result, daily_count = self.pair_audit(
            101,
            202,
            now - 30 * 60_000,
            day_start,
        )
        self.assertIsNone(last_result)
        self.assertEqual(3, daily_count)
        self.assert_integrity_error(
            lambda: self.insert_active(prior_decisive_results_day=3)
        )

        cutoff = now - 30 * 60_000
        boundary = self.insert_active(
            player_a_id=303,
            player_b_id=404,
            started_at_ms=cutoff - 60_000,
        )
        self.assertEqual(1, self.settle_a_win(boundary, ended_at_ms=cutoff))
        self.assertEqual((cutoff, 1), self.pair_audit(303, 404, cutoff, day_start))
        self.assertEqual((None, 0), self.pair_audit(404, 303, cutoff, day_start))

    def test_startup_reconciliation_neutralizes_only_active_rows_once(self):
        reconcile_at = 1_785_600_000_000
        active_a = self.insert_active(
            season_id="2026-07",
            started_at_ms=reconcile_at - 5_000,
        )
        active_b = self.insert_active(
            season_id="2026-08",
            player_a_rating_before=1400,
            player_b_rating_before=1000,
            started_at_ms=reconcile_at - 4_000,
        )
        active_future = self.insert_active(
            season_id="2026-08",
            player_a_id=303,
            player_b_id=404,
            started_at_ms=reconcile_at + 30_000,
        )
        settled = self.insert_active(started_at_ms=reconcile_at - 3_000)
        self.assertEqual(1, self.settle_a_win(settled, ended_at_ms=reconcile_at - 1_000))

        changed = self.connection.execute(
            RECONCILE_SQL,
            ("SERVER_RESTART_NO_CONTEST", reconcile_at, reconcile_at),
        ).rowcount
        self.assertEqual(3, changed)
        self.assertEqual(
            0,
            self.connection.execute(
                RECONCILE_SQL,
                ("SERVER_RESTART_NO_CONTEST", reconcile_at + 1, reconcile_at + 1),
            ).rowcount,
        )

        rows = {
            row["match_id"]: row
            for row in self.connection.execute(
                "SELECT * FROM voidarena_ranked_match_sessions"
            )
        }
        for active in (active_a, active_b, active_future):
            row = rows[active["match_id"]]
            self.assertEqual("SETTLED", row["status"])
            self.assertEqual("SERVER_RESTART_NO_CONTEST", row["result_reason"])
            self.assertIsNone(row["winner_id"])
            self.assertIsNone(row["loser_id"])
            self.assertEqual(active["player_a_rating_before"], row["player_a_rating_after"])
            self.assertEqual(active["player_b_rating_before"], row["player_b_rating_after"])
            self.assertEqual(0, row["rating_delta"])
            self.assertEqual(0, row["rating_applied"])
            self.assertEqual(max(reconcile_at, active["started_at_ms"]), row["ended_at_ms"])

        terminal = rows[settled["match_id"]]
        self.assertEqual("DEATH", terminal["result_reason"])
        self.assertEqual(1216, terminal["player_a_rating_after"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
