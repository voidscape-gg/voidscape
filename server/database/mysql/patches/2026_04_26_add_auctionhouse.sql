-- voidscape: enable auction house schema (was previously gated behind manual addon SQL)
-- Idempotent: safe to apply on databases that already had the tables created via the addon.

CREATE TABLE IF NOT EXISTS `_PREFIX_expired_auctions`
(
    `playerID`    int(10) UNSIGNED NOT NULL,
    `claim_id`    int(11)          NOT NULL AUTO_INCREMENT,
    `item_id`     int(11)          NOT NULL,
    `item_amount` int(11)          NOT NULL,
    `time`        varchar(255)     NOT NULL,
    `claim_time`  varchar(255)     NOT NULL DEFAULT '0',
    `claimed`     tinyint(1)       NOT NULL DEFAULT 0,
    `explanation` varchar(255)     NOT NULL DEFAULT ' ',
    PRIMARY KEY (`claim_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;

CREATE TABLE IF NOT EXISTS `_PREFIX_auctions`
(
    `auctionID`       bigint(20)       NOT NULL AUTO_INCREMENT,
    `itemID`          int(11)          NOT NULL,
    `amount`          int(11)          NOT NULL,
    `amount_left`     int(11)          NOT NULL,
    `price`           int(11)          NOT NULL,
    `seller`          int(10) UNSIGNED NOT NULL,
    `seller_username` varchar(12)      NOT NULL,
    `buyer_info`      text             NOT NULL,
    `sold-out`        tinyint(4)       NOT NULL DEFAULT 0,
    `time`            varchar(255)     NOT NULL DEFAULT '0',
    `was_cancel`      tinyint(1)       NOT NULL DEFAULT 0,
    PRIMARY KEY (`auctionID`),
    KEY `itemID` (`itemID`),
    KEY `seller_username` (`seller_username`),
    KEY `time` (`time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
