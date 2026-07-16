CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipts`
(
	`duel_id`         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	`started_at_ms`   INTEGER NOT NULL,
	`completed_at_ms` INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS `_PREFIX_duel_receipts_completed`
	ON `_PREFIX_duel_receipts` (`completed_at_ms`, `duel_id`);

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipt_participants`
(
	`duel_id`         INTEGER NOT NULL,
	`player_id`       INTEGER NOT NULL,
	`player_username` TEXT    NOT NULL,
	`won`             INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY (`duel_id`, `player_id`)
);

CREATE INDEX IF NOT EXISTS `_PREFIX_duel_receipt_participant_history`
	ON `_PREFIX_duel_receipt_participants` (`player_id`, `duel_id`);

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipt_stakes`
(
	`duel_id`         INTEGER NOT NULL,
	`owner_player_id` INTEGER NOT NULL,
	`slot_index`      INTEGER NOT NULL,
	`catalog_id`      INTEGER NOT NULL,
	`amount`          INTEGER NOT NULL,
	`noted`           INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY (`duel_id`, `owner_player_id`, `slot_index`)
);

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_receipt_swings`
(
	`duel_id`         INTEGER NOT NULL,
	`actor_player_id` INTEGER NOT NULL,
	`swing_number`    INTEGER NOT NULL,
	`combat_style`    INTEGER NOT NULL,
	`did_hit`         INTEGER NOT NULL DEFAULT 0,
	`damage`          INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY (`duel_id`, `actor_player_id`, `swing_number`)
);
