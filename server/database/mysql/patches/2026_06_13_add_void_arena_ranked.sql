CREATE TABLE IF NOT EXISTS `voidarena_ranked_stats`
(
    `ID`               int(10) unsigned NOT NULL AUTO_INCREMENT,
    `seasonID`         varchar(16)      NOT NULL DEFAULT 'global',
    `playerID`         int(10) unsigned NOT NULL,
    `rating`           int(10)          NOT NULL DEFAULT 1200,
    `wins`             int(10) unsigned NOT NULL DEFAULT 0,
    `losses`           int(10) unsigned NOT NULL DEFAULT 0,
    `disconnectLosses` int(10) unsigned NOT NULL DEFAULT 0,
    `resetCount`       int(10) unsigned NOT NULL DEFAULT 0,
    `updatedAt`        bigint(20)       NOT NULL DEFAULT 0,
    PRIMARY KEY (`ID`),
    UNIQUE KEY `voidarena_ranked_player_season` (`seasonID`, `playerID`),
    KEY `voidarena_ranked_rating` (`seasonID`, `rating`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
