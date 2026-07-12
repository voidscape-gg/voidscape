PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS portal_commerce_checkout_intents (
    intent_id TEXT NOT NULL PRIMARY KEY,
    account_id INTEGER NOT NULL CHECK (account_id > 0),
    provider TEXT NOT NULL DEFAULT 'tebex' CHECK (provider = 'tebex'),
    package_id TEXT NOT NULL,
    expected_currency TEXT NOT NULL CHECK (length(expected_currency) = 3),
    expected_amount_minor INTEGER NOT NULL CHECK (expected_amount_minor > 0),
    basket_id TEXT UNIQUE,
    status TEXT NOT NULL DEFAULT 'created'
        CHECK (status IN ('created', 'basket_created', 'completed', 'failed', 'expired')),
    last_error_code TEXT,
    created_at_ms INTEGER NOT NULL,
    expires_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_portal_commerce_intents_account_status
    ON portal_commerce_checkout_intents (account_id, status, created_at_ms);

CREATE TABLE IF NOT EXISTS portal_commerce_webhook_events (
    webhook_id TEXT NOT NULL PRIMARY KEY,
    provider TEXT NOT NULL DEFAULT 'tebex' CHECK (provider = 'tebex'),
    event_type TEXT NOT NULL,
    payload_sha256 TEXT NOT NULL UNIQUE,
    transaction_id TEXT,
    status TEXT NOT NULL DEFAULT 'received'
        CHECK (status IN ('received', 'processed', 'failed', 'ignored')),
    attempt_count INTEGER NOT NULL DEFAULT 1 CHECK (attempt_count > 0),
    last_error_code TEXT,
    created_at_ms INTEGER NOT NULL,
    processed_at_ms INTEGER,
    updated_at_ms INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_portal_commerce_events_transaction
    ON portal_commerce_webhook_events (transaction_id, event_type, status);

CREATE TABLE IF NOT EXISTS portal_commerce_payments (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    provider TEXT NOT NULL DEFAULT 'tebex' CHECK (provider = 'tebex'),
    transaction_id TEXT NOT NULL,
    intent_id TEXT NOT NULL UNIQUE,
    account_id INTEGER NOT NULL CHECK (account_id > 0),
    basket_id TEXT NOT NULL,
    package_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity = 1),
    payment_sequence TEXT NOT NULL CHECK (payment_sequence = 'oneoff'),
    currency TEXT NOT NULL CHECK (length(currency) = 3),
    expected_amount_minor INTEGER NOT NULL CHECK (expected_amount_minor > 0),
    product_paid_amount_minor INTEGER NOT NULL CHECK (product_paid_amount_minor >= 0),
    transaction_paid_amount_minor INTEGER NOT NULL CHECK (transaction_paid_amount_minor >= 0),
    tax_and_adjustments_minor INTEGER NOT NULL,
    status TEXT NOT NULL
        CHECK (status IN ('complete', 'refunded', 'disputed', 'dispute_lost', 'restored', 'review')),
    completed_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    UNIQUE (provider, transaction_id),
    FOREIGN KEY (intent_id) REFERENCES portal_commerce_checkout_intents (intent_id)
);
CREATE INDEX IF NOT EXISTS idx_portal_commerce_payments_account_status
    ON portal_commerce_payments (account_id, status, completed_at_ms);

CREATE TABLE IF NOT EXISTS portal_commerce_entitlements (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    payment_id INTEGER NOT NULL,
    account_id INTEGER NOT NULL CHECK (account_id > 0),
    provider TEXT NOT NULL DEFAULT 'tebex' CHECK (provider = 'tebex'),
    transaction_id TEXT NOT NULL,
    line_key TEXT NOT NULL,
    package_id TEXT NOT NULL,
    unit_index INTEGER NOT NULL DEFAULT 1 CHECK (unit_index = 1),
    state TEXT NOT NULL DEFAULT 'pending'
        CHECK (state IN ('pending', 'claimed', 'revoked', 'frozen', 'review')),
    claimed_player_id INTEGER,
    claimed_item_id INTEGER,
    claimed_at_ms INTEGER,
    review_reason TEXT,
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    UNIQUE (provider, transaction_id, line_key, unit_index),
    FOREIGN KEY (payment_id) REFERENCES portal_commerce_payments (id)
);
CREATE INDEX IF NOT EXISTS idx_portal_commerce_entitlements_account_state
    ON portal_commerce_entitlements (account_id, state, created_at_ms);
