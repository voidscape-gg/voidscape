CREATE TABLE IF NOT EXISTS `_PREFIX_world_achievement_records`
(
	`season_id`      varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`record_key`     varchar(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`record_type`    varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`player_id`      int(10) unsigned    NOT NULL,
	`player_name`    varchar(12)         NOT NULL,
	`subject_id`     int(10)             NOT NULL DEFAULT 0,
	`value`          bigint(20)          NOT NULL DEFAULT 0,
	`source`         varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`source_event_key` varchar(96) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
	`claimed_at_ms`  bigint(20) unsigned NOT NULL,
	`detail`         varchar(255)        NOT NULL DEFAULT '',
	PRIMARY KEY (`season_id`, `record_key`),
	UNIQUE KEY `world_achievement_source_event` (`season_id`, `source`, `source_event_key`, `record_type`),
	KEY `world_achievement_type_time` (`season_id`, `record_type`, `claimed_at_ms`),
	KEY `world_achievement_player_time` (`season_id`, `player_id`, `claimed_at_ms`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `_PREFIX_world_pk_events`
(
	`death_id`             char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`season_id`            varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`killer_player_id`     int(10) unsigned    NOT NULL,
	`killer_account_id`    bigint(20) unsigned          DEFAULT NULL,
	`killer_name`          varchar(12)         NOT NULL,
	`victim_player_id`     int(10) unsigned    NOT NULL,
	`victim_account_id`    bigint(20) unsigned          DEFAULT NULL,
	`victim_name`          varchar(12)         NOT NULL,
	`pair_low_player_id`   int(10) unsigned    NOT NULL,
	`pair_high_player_id`  int(10) unsigned    NOT NULL,
	`qualified`            tinyint(1) unsigned NOT NULL DEFAULT 0,
	`reject_reason`        varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
	`victim_was_skulled`   tinyint(1) unsigned NOT NULL DEFAULT 0,
	`victim_damage`        int(10) unsigned    NOT NULL DEFAULT 0,
	`loot_value`           bigint(20) unsigned NOT NULL DEFAULT 0,
	`streak_after`         int(10) unsigned    NOT NULL DEFAULT 0,
	`ended_streak`         int(10) unsigned    NOT NULL DEFAULT 0,
	`wilderness_level`     int(10) unsigned    NOT NULL DEFAULT 0,
	`occurred_at_ms`       bigint(20) unsigned NOT NULL,
	PRIMARY KEY (`death_id`),
	KEY `world_pk_pair_qualified_time` (`season_id`, `pair_low_player_id`, `pair_high_player_id`, `qualified`, `occurred_at_ms`),
	KEY `world_pk_qualified_time` (`season_id`, `qualified`, `occurred_at_ms`),
	KEY `world_pk_killer_time` (`season_id`, `killer_player_id`, `occurred_at_ms`),
	KEY `world_pk_victim_time` (`season_id`, `victim_player_id`, `occurred_at_ms`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `_PREFIX_world_pk_streaks`
(
	`season_id`            varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`player_id`            int(10) unsigned    NOT NULL,
	`player_name`          varchar(12)         NOT NULL,
	`current_streak`       int(10) unsigned    NOT NULL DEFAULT 0,
	`best_streak`          int(10) unsigned    NOT NULL DEFAULT 0,
	`qualified_kills`      bigint(20) unsigned NOT NULL DEFAULT 0,
	`last_qualified_at_ms` bigint(20) unsigned NOT NULL DEFAULT 0,
	`updated_at_ms`        bigint(20) unsigned NOT NULL,
	PRIMARY KEY (`season_id`, `player_id`),
	KEY `world_pk_streak_leaders` (`season_id`, `best_streak`, `qualified_kills`, `updated_at_ms`, `player_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
