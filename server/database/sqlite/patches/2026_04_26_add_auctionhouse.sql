-- voidscape: enable auction house schema (was previously gated behind manual addon SQL)
-- Idempotent: safe to apply on databases that already had the tables created via the addon.

CREATE TABLE IF NOT EXISTS "_PREFIX_expired_auctions"
(
    "playerID"    INTEGER NOT NULL,
    "claim_id"    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "item_id"     INTEGER NOT NULL,
    "item_amount" INTEGER NOT NULL,
    "time"        TEXT    NOT NULL,
    "claim_time"  TEXT    NOT NULL DEFAULT '0',
    "claimed"     INTEGER NOT NULL DEFAULT 0,
    "explanation" TEXT    NOT NULL DEFAULT ' '
);

CREATE TABLE IF NOT EXISTS "_PREFIX_auctions"
(
    "auctionID"       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "itemID"          INTEGER NOT NULL,
    "amount"          INTEGER NOT NULL,
    "amount_left"     INTEGER NOT NULL,
    "price"           INTEGER NOT NULL,
    "seller"          INTEGER NOT NULL,
    "seller_username" TEXT    NOT NULL,
    "buyer_info"      TEXT    NOT NULL,
    "sold-out"        INTEGER NOT NULL DEFAULT 0,
    "time"            TEXT    NOT NULL DEFAULT '0',
    "was_cancel"      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS "idx_auctions_itemid" ON "_PREFIX_auctions" ("itemID");
CREATE INDEX IF NOT EXISTS "idx_auctions_seller" ON "_PREFIX_auctions" ("seller_username");
CREATE INDEX IF NOT EXISTS "idx_auctions_time"   ON "_PREFIX_auctions" ("time");
