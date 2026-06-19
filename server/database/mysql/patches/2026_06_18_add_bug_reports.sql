CREATE TABLE IF NOT EXISTS `bug_reports`
(
	`id`             int(10) unsigned NOT NULL AUTO_INCREMENT,
	`report_id`      varchar(16)      NOT NULL,
	`time`           bigint(20)       NOT NULL,
	`reporter_id`    int(10) unsigned NOT NULL DEFAULT 0,
	`reporter`       varchar(12)      NOT NULL,
	`server_name`    varchar(64)      NOT NULL,
	`x`              int(10)          NOT NULL DEFAULT 0,
	`y`              int(10)          NOT NULL DEFAULT 0,
	`floor`          int(10)          NOT NULL DEFAULT 0,
	`client_version` int(10)          NOT NULL DEFAULT 0,
	`category`       varchar(32)      NOT NULL DEFAULT 'bug',
	`private_report` tinyint(1)       NOT NULL DEFAULT 0,
	`status`         varchar(32)      NOT NULL DEFAULT 'new',
	`message`        text             NOT NULL,
	PRIMARY KEY (`id`),
	UNIQUE KEY `bug_reports_report_id` (`report_id`),
	KEY `bug_reports_status_time` (`status`, `time`),
	KEY `bug_reports_reporter_time` (`reporter_id`, `time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = latin1;
