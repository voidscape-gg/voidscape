CREATE TABLE IF NOT EXISTS `_PREFIX_duel_proof_witnesses`
(
	`proof_id`         char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
	`witness_version`  smallint(5) unsigned                           NOT NULL,
	`witness_bytes`    mediumblob                                     NOT NULL,
	`witness_hash32`   binary(32)                                     NOT NULL,
	`starter_ordinal`  tinyint(3) unsigned                            NOT NULL,
	`swing_count`      int(10) unsigned                               NOT NULL,
	`winner_player_id` int(10) unsigned                               NOT NULL,
	`terminal_cause`   varchar(32)                                    NOT NULL,
	`finished_at_ms`   bigint(20)                                     NOT NULL,
	PRIMARY KEY (`proof_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
