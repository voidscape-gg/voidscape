CREATE TABLE IF NOT EXISTS "_PREFIX_world_achievement_records"
(
	"season_id"       TEXT    NOT NULL,
	"record_key"      TEXT    NOT NULL,
	"record_type"     TEXT    NOT NULL,
	"player_id"       INTEGER NOT NULL,
	"player_name"     TEXT    NOT NULL,
	"subject_id"      INTEGER NOT NULL DEFAULT 0,
	"value"           INTEGER NOT NULL DEFAULT 0,
	"source"          TEXT    NOT NULL,
	"source_event_key" TEXT            DEFAULT NULL,
	"claimed_at_ms"   INTEGER NOT NULL,
	"detail"          TEXT    NOT NULL DEFAULT '',
	PRIMARY KEY ("season_id", "record_key"),
	UNIQUE ("season_id", "source", "source_event_key", "record_type")
);

CREATE INDEX IF NOT EXISTS "_PREFIX_world_achievement_type_time"
	ON "_PREFIX_world_achievement_records" ("season_id", "record_type", "claimed_at_ms");
CREATE INDEX IF NOT EXISTS "_PREFIX_world_achievement_player_time"
	ON "_PREFIX_world_achievement_records" ("season_id", "player_id", "claimed_at_ms");

CREATE TABLE IF NOT EXISTS "_PREFIX_world_pk_events"
(
	"death_id"            TEXT    NOT NULL PRIMARY KEY,
	"season_id"           TEXT    NOT NULL,
	"killer_player_id"    INTEGER NOT NULL,
	"killer_account_id"   INTEGER          DEFAULT NULL,
	"killer_name"         TEXT    NOT NULL,
	"victim_player_id"    INTEGER NOT NULL,
	"victim_account_id"   INTEGER          DEFAULT NULL,
	"victim_name"         TEXT    NOT NULL,
	"pair_low_player_id"  INTEGER NOT NULL,
	"pair_high_player_id" INTEGER NOT NULL,
	"qualified"           INTEGER NOT NULL DEFAULT 0,
	"reject_reason"       TEXT    NOT NULL DEFAULT '',
	"victim_was_skulled"  INTEGER NOT NULL DEFAULT 0,
	"victim_damage"       INTEGER NOT NULL DEFAULT 0,
	"loot_value"          INTEGER NOT NULL DEFAULT 0,
	"streak_after"        INTEGER NOT NULL DEFAULT 0,
	"ended_streak"        INTEGER NOT NULL DEFAULT 0,
	"wilderness_level"    INTEGER NOT NULL DEFAULT 0,
	"occurred_at_ms"      INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS "_PREFIX_world_pk_pair_qualified_time"
	ON "_PREFIX_world_pk_events" ("season_id", "pair_low_player_id", "pair_high_player_id", "qualified", "occurred_at_ms");
CREATE INDEX IF NOT EXISTS "_PREFIX_world_pk_qualified_time"
	ON "_PREFIX_world_pk_events" ("season_id", "qualified", "occurred_at_ms");
CREATE INDEX IF NOT EXISTS "_PREFIX_world_pk_killer_time"
	ON "_PREFIX_world_pk_events" ("season_id", "killer_player_id", "occurred_at_ms");
CREATE INDEX IF NOT EXISTS "_PREFIX_world_pk_victim_time"
	ON "_PREFIX_world_pk_events" ("season_id", "victim_player_id", "occurred_at_ms");

CREATE TABLE IF NOT EXISTS "_PREFIX_world_pk_streaks"
(
	"season_id"            TEXT    NOT NULL,
	"player_id"            INTEGER NOT NULL,
	"player_name"          TEXT    NOT NULL,
	"current_streak"       INTEGER NOT NULL DEFAULT 0,
	"best_streak"          INTEGER NOT NULL DEFAULT 0,
	"qualified_kills"      INTEGER NOT NULL DEFAULT 0,
	"last_qualified_at_ms" INTEGER NOT NULL DEFAULT 0,
	"updated_at_ms"        INTEGER NOT NULL,
	PRIMARY KEY ("season_id", "player_id")
);

CREATE INDEX IF NOT EXISTS "_PREFIX_world_pk_streak_leaders"
	ON "_PREFIX_world_pk_streaks" ("season_id", "best_streak", "qualified_kills", "updated_at_ms", "player_id");
