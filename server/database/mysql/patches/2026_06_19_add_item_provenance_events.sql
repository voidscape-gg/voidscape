CREATE TABLE IF NOT EXISTS `item_provenance_events`
(
	`eventID`         bigint(20) unsigned NOT NULL AUTO_INCREMENT,
	`itemID`          BIGINT              NOT NULL DEFAULT 0,
	`catalogID`       int(10) unsigned    NOT NULL,
	`amount`          int(10) unsigned    NOT NULL DEFAULT 1,
	`noted`           tinyint(1) unsigned NOT NULL DEFAULT 0,
	`actorID`         int(10) unsigned    NOT NULL DEFAULT 0,
	`actor_username`  varchar(12)         NOT NULL DEFAULT '',
	`targetID`        int(10) unsigned    NOT NULL DEFAULT 0,
	`target_username` varchar(12)         NOT NULL DEFAULT '',
	`event_type`      varchar(32)         NOT NULL,
	`source`          varchar(32)         NOT NULL,
	`destination`     varchar(32)         NOT NULL DEFAULT '',
	`command`         varchar(32)         NOT NULL DEFAULT '',
	`x`               int(10)             NOT NULL DEFAULT 0,
	`y`               int(10)             NOT NULL DEFAULT 0,
	`time`            bigint(20)          NOT NULL,
	`extra`           varchar(255)        NOT NULL DEFAULT '',
	PRIMARY KEY (`eventID`),
	KEY `item_provenance_time_type` (`time`, `event_type`),
	KEY `item_provenance_catalog_time` (`catalogID`, `time`),
	KEY `item_provenance_actor_time` (`actorID`, `time`),
	KEY `item_provenance_target_time` (`targetID`, `time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
