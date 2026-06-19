CREATE TABLE IF NOT EXISTS `voidarena_ranked_matches`
(
    `ID`                  INTEGER PRIMARY KEY AUTOINCREMENT,
    `seasonID`            varchar(16) NOT NULL DEFAULT 'global',
    `winnerID`            INTEGER     NOT NULL,
    `loserID`             INTEGER     NOT NULL,
    `winnerRatingBefore`  INTEGER     NOT NULL,
    `winnerRatingAfter`   INTEGER     NOT NULL,
    `loserRatingBefore`   INTEGER     NOT NULL,
    `loserRatingAfter`    INTEGER     NOT NULL,
    `ratingDelta`         INTEGER     NOT NULL,
    `disconnectLoss`      INTEGER     NOT NULL DEFAULT 0,
    `slotIndex`           INTEGER     NOT NULL DEFAULT -1,
    `startedAt`           INTEGER     NOT NULL DEFAULT 0,
    `endedAt`             INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS `voidarena_ranked_matches_season_time`
    ON `voidarena_ranked_matches` (`seasonID`, `endedAt`);

CREATE INDEX IF NOT EXISTS `voidarena_ranked_matches_winner`
    ON `voidarena_ranked_matches` (`winnerID`, `endedAt`);

CREATE INDEX IF NOT EXISTS `voidarena_ranked_matches_loser`
    ON `voidarena_ranked_matches` (`loserID`, `endedAt`);
