CREATE TABLE IF NOT EXISTS `_PREFIX_voidarena_ranked_matches`
(
    `ID`                  int(10) unsigned NOT NULL AUTO_INCREMENT,
    `seasonID`            varchar(16)      NOT NULL DEFAULT 'global',
    `winnerID`            int(10) unsigned NOT NULL,
    `loserID`             int(10) unsigned NOT NULL,
    `winnerRatingBefore`  int(10)          NOT NULL,
    `winnerRatingAfter`   int(10)          NOT NULL,
    `loserRatingBefore`   int(10)          NOT NULL,
    `loserRatingAfter`    int(10)          NOT NULL,
    `ratingDelta`         int(10)          NOT NULL,
    `disconnectLoss`      tinyint(1)       NOT NULL DEFAULT 0,
    `slotIndex`           int(10)          NOT NULL DEFAULT -1,
    `startedAt`           bigint(20)       NOT NULL DEFAULT 0,
    `endedAt`             bigint(20)       NOT NULL DEFAULT 0,
    PRIMARY KEY (`ID`),
    KEY `voidarena_ranked_matches_season_time` (`seasonID`, `endedAt`),
    KEY `voidarena_ranked_matches_winner` (`winnerID`, `endedAt`),
    KEY `voidarena_ranked_matches_loser` (`loserID`, `endedAt`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
