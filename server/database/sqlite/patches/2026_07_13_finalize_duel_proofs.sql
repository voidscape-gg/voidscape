CREATE TABLE IF NOT EXISTS `_PREFIX_duel_proof_witnesses`
(
	`proof_id`         VARCHAR(32) NOT NULL PRIMARY KEY,
	`witness_version`  INTEGER     NOT NULL,
	`witness_bytes`    BLOB        NOT NULL,
	`witness_hash32`   BLOB        NOT NULL,
	`starter_ordinal`  INTEGER     NOT NULL,
	`swing_count`      INTEGER     NOT NULL,
	`winner_player_id` INTEGER     NOT NULL,
	`terminal_cause`   VARCHAR(32) NOT NULL,
	`finished_at_ms`   INTEGER     NOT NULL
);
