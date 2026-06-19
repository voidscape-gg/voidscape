-- voidscape: record completed auction house purchases for market intel.

CREATE TABLE IF NOT EXISTS `_PREFIX_auction_sales`
(
    `sale_id`         bigint(20)       NOT NULL AUTO_INCREMENT,
    `auction_id`      bigint(20)       NOT NULL,
    `item_id`         int(11)          NOT NULL,
    `amount`          int(11)          NOT NULL,
    `unit_price`      int(11)          NOT NULL,
    `total_price`     int(11)          NOT NULL,
    `tax`             int(11)          NOT NULL,
    `seller`          int(10) UNSIGNED NOT NULL,
    `seller_username` varchar(12)      NOT NULL,
    `buyer`           int(10) UNSIGNED NOT NULL,
    `buyer_username`  varchar(12)      NOT NULL,
    `sold_at`         bigint(20)       NOT NULL,
    PRIMARY KEY (`sale_id`),
    KEY `auction_id` (`auction_id`),
    KEY `item_sold_at` (`item_id`, `sold_at`),
    KEY `seller_sold_at` (`seller`, `sold_at`),
    KEY `buyer_sold_at` (`buyer`, `sold_at`),
    KEY `sold_at` (`sold_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8;
