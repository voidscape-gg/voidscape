CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipts`
(
	`duel_id`         bigint(20) unsigned NOT NULL AUTO_INCREMENT,
	`started_at_ms`   bigint(20)          NOT NULL,
	`completed_at_ms` bigint(20)          NOT NULL,
	PRIMARY KEY (`duel_id`),
	KEY `duel_receipts_completed` (`completed_at_ms`, `duel_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipt_participants`
(
	`duel_id`        bigint(20) unsigned NOT NULL,
	`player_id`      int(10) unsigned    NOT NULL,
	`player_username` varchar(12)         NOT NULL,
	`won`            tinyint(1) unsigned NOT NULL DEFAULT 0,
	PRIMARY KEY (`duel_id`, `player_id`),
	KEY `duel_receipt_participant_history` (`player_id`, `duel_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipt_stakes`
(
	`duel_id`         bigint(20) unsigned NOT NULL,
	`owner_player_id` int(10) unsigned    NOT NULL,
	`slot_index`      int(10) unsigned    NOT NULL,
	`catalog_id`      int(10) unsigned    NOT NULL,
	`amount`          int(10) unsigned    NOT NULL,
	`noted`           tinyint(1) unsigned NOT NULL DEFAULT 0,
	PRIMARY KEY (`duel_id`, `owner_player_id`, `slot_index`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipt_swings`
(
	`duel_id`        bigint(20) unsigned NOT NULL,
	`actor_player_id` int(10) unsigned    NOT NULL,
	`swing_number`   int(10) unsigned    NOT NULL,
	`combat_style`   tinyint(3) unsigned NOT NULL,
	`did_hit`        tinyint(1) unsigned NOT NULL DEFAULT 0,
	`damage`         int(10) unsigned    NOT NULL DEFAULT 0,
	PRIMARY KEY (`duel_id`, `actor_player_id`, `swing_number`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
