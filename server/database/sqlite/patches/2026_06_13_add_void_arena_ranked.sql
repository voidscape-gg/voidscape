CREATE TABLE IF NOT EXISTS `voidarena_ranked_stats`
(
    `ID`               INTEGER PRIMARY KEY AUTOINCREMENT,
    `seasonID`         varchar(16) NOT NULL DEFAULT 'global',
    `playerID`         INTEGER     NOT NULL,
    `rating`           INTEGER     NOT NULL DEFAULT 1200,
    `wins`             INTEGER     NOT NULL DEFAULT 0,
    `losses`           INTEGER     NOT NULL DEFAULT 0,
    `disconnectLosses` INTEGER     NOT NULL DEFAULT 0,
    `resetCount`       INTEGER     NOT NULL DEFAULT 0,
    `updatedAt`        INTEGER     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS `voidarena_ranked_player_season`
    ON `voidarena_ranked_stats` (`seasonID`, `playerID`);

CREATE INDEX IF NOT EXISTS `voidarena_ranked_rating`
    ON `voidarena_ranked_stats` (`seasonID`, `rating`);
