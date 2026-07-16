UPDATE `_PREFIX_voidarena_ranked_stats`
SET `seasonID` = 'LEGACY'
WHERE `seasonID` = 'global';

UPDATE `_PREFIX_voidarena_ranked_matches`
SET `seasonID` = 'LEGACY'
WHERE `seasonID` = 'global';

CREATE TABLE IF NOT EXISTS `_PREFIX_voidarena_ranked_match_sessions`
(
    `match_id`                    VARCHAR(36) NOT NULL,
    `season_id`                   VARCHAR(16) NOT NULL,
    `status`                      VARCHAR(8)  NOT NULL,
    `result_reason`               VARCHAR(32)          DEFAULT NULL,
    `player_a_id`                 INTEGER     NOT NULL,
    `player_b_id`                 INTEGER     NOT NULL,
    `winner_id`                   INTEGER              DEFAULT NULL,
    `loser_id`                    INTEGER              DEFAULT NULL,
    `player_a_rating_before`      INTEGER     NOT NULL,
    `player_a_rating_after`       INTEGER              DEFAULT NULL,
    `player_b_rating_before`      INTEGER     NOT NULL,
    `player_b_rating_after`       INTEGER              DEFAULT NULL,
    `rating_delta`                INTEGER     NOT NULL DEFAULT 0,
    `rating_applied`              INTEGER     NOT NULL DEFAULT 0,
    `same_ip`                     INTEGER     NOT NULL DEFAULT 0,
    `same_ip_local_exempt`        INTEGER     NOT NULL DEFAULT 0,
    `prior_rated_results_30m`     INTEGER     NOT NULL DEFAULT 0,
    `prior_decisive_results_day`  INTEGER     NOT NULL DEFAULT 0,
    `slot_index`                  INTEGER     NOT NULL,
    `started_at_ms`               INTEGER     NOT NULL,
    `ended_at_ms`                 INTEGER              DEFAULT NULL,
    PRIMARY KEY (`match_id`),
    CHECK (length(`match_id`) = 36),
    CHECK (length(`season_id`) BETWEEN 1 AND 16),
    CHECK (`player_a_id` > 0 AND `player_a_id` < `player_b_id`),
    CHECK (`player_a_rating_before` >= 1 AND `player_b_rating_before` >= 1),
    CHECK ((`player_a_rating_after` IS NULL OR `player_a_rating_after` >= 1)
        AND (`player_b_rating_after` IS NULL OR `player_b_rating_after` >= 1)),
    CHECK (`rating_applied` IN (0, 1)),
    CHECK (`same_ip` IN (0, 1) AND `same_ip_local_exempt` IN (0, 1)
        AND `same_ip` = `same_ip_local_exempt`),
    CHECK (`prior_rated_results_30m` = 0),
    CHECK (`prior_decisive_results_day` BETWEEN 0 AND 2),
    CHECK (`slot_index` >= 0),
    CHECK (`started_at_ms` > 0),
    CHECK (
        (`status` = 'ACTIVE'
            AND `result_reason` IS NULL AND `winner_id` IS NULL AND `loser_id` IS NULL
            AND `player_a_rating_after` IS NULL AND `player_b_rating_after` IS NULL
            AND `rating_delta` = 0 AND `rating_applied` = 0 AND `ended_at_ms` IS NULL)
        OR
        (`status` = 'SETTLED'
            AND `result_reason` IN ('DEATH', 'FORFEIT', 'TIMEOUT_DRAW',
                'SERVER_SHUTDOWN_NO_CONTEST', 'SERVER_RESTART_NO_CONTEST')
            AND `player_a_rating_after` IS NOT NULL AND `player_b_rating_after` IS NOT NULL
            AND `ended_at_ms` IS NOT NULL AND `ended_at_ms` >= `started_at_ms`)
    ),
    CHECK (
        `status` = 'ACTIVE'
        OR (`result_reason` IN ('DEATH', 'FORFEIT') AND `rating_applied` = 1
            AND `winner_id` IS NOT NULL AND `loser_id` IS NOT NULL
            AND ((`winner_id` = `player_a_id` AND `loser_id` = `player_b_id` AND `rating_delta` >= 0)
                OR (`winner_id` = `player_b_id` AND `loser_id` = `player_a_id` AND `rating_delta` <= 0)))
        OR (`result_reason` IN ('TIMEOUT_DRAW', 'SERVER_SHUTDOWN_NO_CONTEST',
                'SERVER_RESTART_NO_CONTEST')
            AND `winner_id` IS NULL AND `loser_id` IS NULL AND `rating_applied` = 0
            AND `rating_delta` = 0
            AND `player_a_rating_after` = `player_a_rating_before`
            AND `player_b_rating_after` = `player_b_rating_before`)
    ),
    CHECK (`status` = 'ACTIVE'
        OR (`player_a_rating_after` = `player_a_rating_before` + `rating_delta`
            AND `player_b_rating_after` = `player_b_rating_before` - `rating_delta`))
);

CREATE INDEX IF NOT EXISTS `_PREFIX_voidarena_sessions_season_end`
    ON `_PREFIX_voidarena_ranked_match_sessions` (`season_id`, `ended_at_ms`, `match_id`);
CREATE INDEX IF NOT EXISTS `_PREFIX_voidarena_sessions_status_start`
    ON `_PREFIX_voidarena_ranked_match_sessions` (`status`, `started_at_ms`);
CREATE INDEX IF NOT EXISTS `_PREFIX_voidarena_sessions_pair_end`
    ON `_PREFIX_voidarena_ranked_match_sessions` (`player_a_id`, `player_b_id`, `ended_at_ms`);
CREATE INDEX IF NOT EXISTS `_PREFIX_voidarena_sessions_player_a`
    ON `_PREFIX_voidarena_ranked_match_sessions` (`player_a_id`, `season_id`, `ended_at_ms`);
CREATE INDEX IF NOT EXISTS `_PREFIX_voidarena_sessions_player_b`
    ON `_PREFIX_voidarena_ranked_match_sessions` (`player_b_id`, `season_id`, `ended_at_ms`);
