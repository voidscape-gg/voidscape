CREATE TABLE IF NOT EXISTS `_PREFIX_duel_proof_attempts`
(
	`proof_id`          VARCHAR(32) NOT NULL PRIMARY KEY,
	`duel_id`           INTEGER          DEFAULT NULL UNIQUE,
	`protocol_version`  INTEGER NOT NULL,
	`rng_version`       INTEGER NOT NULL,
	`formula_version`   INTEGER NOT NULL,
	`context_version`   INTEGER NOT NULL,
	`status`            VARCHAR(24) NOT NULL,
	`created_at_ms`     INTEGER NOT NULL,
	`updated_at_ms`     INTEGER NOT NULL,
	`locked_at_ms`      INTEGER          DEFAULT NULL,
	`started_at_ms`     INTEGER          DEFAULT NULL,
	`finished_at_ms`    INTEGER          DEFAULT NULL,
	`abort_reason`      VARCHAR(32)      DEFAULT NULL,
	`context_bytes`     BLOB    NOT NULL,
	`context_hash32`      BLOB    NOT NULL,
	`server_commitment32` BLOB    NOT NULL,
	`server_seed32`       BLOB    NOT NULL,
	`final_lock_hash32`   BLOB             DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS `_PREFIX_duel_proof_attempt_lifecycle`
	ON `_PREFIX_duel_proof_attempts` (`status`, `updated_at_ms`);

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_proof_attempt_participants`
(
	`proof_id`          VARCHAR(32) NOT NULL,
	`canonical_ordinal` INTEGER NOT NULL,
	`player_id`         INTEGER NOT NULL,
	`username`          VARCHAR(12) NOT NULL,
	`client_commitment32` BLOB DEFAULT NULL,
	`client_seed32`       BLOB DEFAULT NULL,
	`lock_ack32`          BLOB DEFAULT NULL,
	PRIMARY KEY (`proof_id`, `canonical_ordinal`),
	UNIQUE (`proof_id`, `player_id`)
);

CREATE INDEX IF NOT EXISTS `_PREFIX_duel_proof_attempt_participant_history`
	ON `_PREFIX_duel_proof_attempt_participants` (`player_id`, `proof_id`);
