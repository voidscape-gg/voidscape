CREATE TABLE IF NOT EXISTS `_PREFIX_duel_proof_attempts`
(
	`proof_id`          char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`duel_id`           bigint(20) unsigned                              DEFAULT NULL,
	`protocol_version`  smallint(5) unsigned                    NOT NULL,
	`rng_version`       smallint(5) unsigned                    NOT NULL,
	`formula_version`   smallint(5) unsigned                    NOT NULL,
	`context_version`   smallint(5) unsigned                    NOT NULL,
	`status`            varchar(24)                             NOT NULL,
	`created_at_ms`     bigint(20)                              NOT NULL,
	`updated_at_ms`     bigint(20)                              NOT NULL,
	`locked_at_ms`      bigint(20)                                       DEFAULT NULL,
	`started_at_ms`     bigint(20)                                       DEFAULT NULL,
	`finished_at_ms`    bigint(20)                                       DEFAULT NULL,
	`abort_reason`      varchar(32)                                      DEFAULT NULL,
	`context_bytes`     blob                                    NOT NULL,
	`context_hash32`      binary(32)                              NOT NULL,
	`server_commitment32` binary(32)                              NOT NULL,
	`server_seed32`       binary(32)                              NOT NULL,
	`final_lock_hash32`   binary(32)                                       DEFAULT NULL,
	PRIMARY KEY (`proof_id`),
	UNIQUE KEY `duel_proof_attempt_duel` (`duel_id`),
	KEY `duel_proof_attempt_lifecycle` (`status`, `updated_at_ms`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;

CREATE TABLE IF NOT EXISTS `_PREFIX_duel_proof_attempt_participants`
(
	`proof_id`           char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`canonical_ordinal`  tinyint(3) unsigned                            NOT NULL,
	`player_id`           int(10) unsigned                               NOT NULL,
	`username`            varchar(12)                                    NOT NULL,
	`client_commitment32` binary(32)                                              DEFAULT NULL,
	`client_seed32`       binary(32)                                              DEFAULT NULL,
	`lock_ack32`          binary(32)                                              DEFAULT NULL,
	PRIMARY KEY (`proof_id`, `canonical_ordinal`),
	UNIQUE KEY `duel_proof_attempt_player` (`proof_id`, `player_id`),
	KEY `duel_proof_attempt_participant_history` (`player_id`, `proof_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
