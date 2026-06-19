CREATE TABLE IF NOT EXISTS `bug_reports`
(
	`id`             INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	`report_id`      TEXT    NOT NULL,
	`time`           INTEGER NOT NULL,
	`reporter_id`    INTEGER NOT NULL DEFAULT 0,
	`reporter`       TEXT    NOT NULL,
	`server_name`    TEXT    NOT NULL,
	`x`              INTEGER NOT NULL DEFAULT 0,
	`y`              INTEGER NOT NULL DEFAULT 0,
	`floor`          INTEGER NOT NULL DEFAULT 0,
	`client_version` INTEGER NOT NULL DEFAULT 0,
	`category`       TEXT    NOT NULL DEFAULT 'bug',
	`private_report` INTEGER NOT NULL DEFAULT 0,
	`status`         TEXT    NOT NULL DEFAULT 'new',
	`message`        TEXT    NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `bug_reports_report_id`
	ON `bug_reports` (`report_id`);

CREATE INDEX IF NOT EXISTS `bug_reports_status_time`
	ON `bug_reports` (`status`, `time`);

CREATE INDEX IF NOT EXISTS `bug_reports_reporter_time`
	ON `bug_reports` (`reporter_id`, `time`);
