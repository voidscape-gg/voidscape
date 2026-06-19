CREATE TABLE IF NOT EXISTS `item_provenance_events`
(
	`eventID`         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	`itemID`          INTEGER NOT NULL DEFAULT 0,
	`catalogID`       INTEGER NOT NULL,
	`amount`          INTEGER NOT NULL DEFAULT 1,
	`noted`           INTEGER NOT NULL DEFAULT 0,
	`actorID`         INTEGER NOT NULL DEFAULT 0,
	`actor_username`  TEXT    NOT NULL DEFAULT '',
	`targetID`        INTEGER NOT NULL DEFAULT 0,
	`target_username` TEXT    NOT NULL DEFAULT '',
	`event_type`      TEXT    NOT NULL,
	`source`          TEXT    NOT NULL,
	`destination`     TEXT    NOT NULL DEFAULT '',
	`command`         TEXT    NOT NULL DEFAULT '',
	`x`               INTEGER NOT NULL DEFAULT 0,
	`y`               INTEGER NOT NULL DEFAULT 0,
	`time`            INTEGER NOT NULL,
	`extra`           TEXT    NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS `item_provenance_time_type`
	ON `item_provenance_events` (`time`, `event_type`);

CREATE INDEX IF NOT EXISTS `item_provenance_catalog_time`
	ON `item_provenance_events` (`catalogID`, `time`);

CREATE INDEX IF NOT EXISTS `item_provenance_actor_time`
	ON `item_provenance_events` (`actorID`, `time`);

CREATE INDEX IF NOT EXISTS `item_provenance_target_time`
	ON `item_provenance_events` (`targetID`, `time`);
