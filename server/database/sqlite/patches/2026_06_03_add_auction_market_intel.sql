-- voidscape: record completed auction house purchases for market intel.

CREATE TABLE IF NOT EXISTS "_PREFIX_auction_sales"
(
    "sale_id"         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "auction_id"      INTEGER NOT NULL,
    "item_id"         INTEGER NOT NULL,
    "amount"          INTEGER NOT NULL,
    "unit_price"      INTEGER NOT NULL,
    "total_price"     INTEGER NOT NULL,
    "tax"             INTEGER NOT NULL,
    "seller"          INTEGER NOT NULL,
    "seller_username" TEXT    NOT NULL,
    "buyer"           INTEGER NOT NULL,
    "buyer_username"  TEXT    NOT NULL,
    "sold_at"         INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS "idx_auction_sales_auction" ON "_PREFIX_auction_sales" ("auction_id");
CREATE INDEX IF NOT EXISTS "idx_auction_sales_item_time" ON "_PREFIX_auction_sales" ("item_id", "sold_at");
CREATE INDEX IF NOT EXISTS "idx_auction_sales_seller_time" ON "_PREFIX_auction_sales" ("seller", "sold_at");
CREATE INDEX IF NOT EXISTS "idx_auction_sales_buyer_time" ON "_PREFIX_auction_sales" ("buyer", "sold_at");
CREATE INDEX IF NOT EXISTS "idx_auction_sales_time" ON "_PREFIX_auction_sales" ("sold_at");
