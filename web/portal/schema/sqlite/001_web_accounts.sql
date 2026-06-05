-- voidscape portal account-management schema for SQLite.
-- This is portal-owned reference SQL and is not auto-applied by the OpenRSC server.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS web_accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email_canonical TEXT NOT NULL,
    email_display TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    CHECK (length(email_canonical) > 3),
    CHECK (status IN ('active', 'locked', 'review', 'deleted'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_accounts_email
    ON web_accounts (email_canonical);

CREATE TABLE IF NOT EXISTS web_account_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    session_token_hash TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    expires_at INTEGER NOT NULL,
    last_seen_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ip_hash TEXT,
    user_agent_hash TEXT,
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_account_sessions_token
    ON web_account_sessions (session_token_hash);

CREATE INDEX IF NOT EXISTS idx_web_account_sessions_account
    ON web_account_sessions (account_id, expires_at);

CREATE TABLE IF NOT EXISTS web_recovery_codes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    code_hash TEXT NOT NULL,
    code_hint TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    used_at TEXT,
    revoked_at TEXT,
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE,
    CHECK (status IN ('active', 'used', 'revoked'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_recovery_codes_hash
    ON web_recovery_codes (code_hash);

CREATE INDEX IF NOT EXISTS idx_web_recovery_codes_account
    ON web_recovery_codes (account_id, status);

CREATE TABLE IF NOT EXISTS web_account_characters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    player_id INTEGER NOT NULL,
    display_username TEXT NOT NULL,
    normalized_username TEXT NOT NULL,
    slot INTEGER NOT NULL,
    is_primary INTEGER NOT NULL DEFAULT 0,
    link_status TEXT NOT NULL DEFAULT 'linked',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE,
    CHECK (slot BETWEEN 0 AND 9),
    CHECK (is_primary IN (0, 1)),
    CHECK (link_status IN ('linked', 'pending', 'revoked'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_account_characters_player
    ON web_account_characters (player_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_account_characters_slot
    ON web_account_characters (account_id, slot);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_account_characters_name
    ON web_account_characters (account_id, normalized_username);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_account_characters_primary
    ON web_account_characters (account_id)
    WHERE is_primary = 1;

CREATE TABLE IF NOT EXISTS web_character_link_challenges (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    player_id INTEGER,
    display_username TEXT NOT NULL,
    normalized_username TEXT NOT NULL,
    code_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    expires_at INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    verified_at TEXT,
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE,
    CHECK (status IN ('pending', 'verified', 'expired', 'revoked'))
);

CREATE INDEX IF NOT EXISTS idx_web_character_link_challenges_account
    ON web_character_link_challenges (account_id, status, expires_at);

CREATE INDEX IF NOT EXISTS idx_web_character_link_challenges_name
    ON web_character_link_challenges (normalized_username, status);

CREATE TABLE IF NOT EXISTS web_entitlements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'granted',
    source TEXT NOT NULL,
    code_hint TEXT,
    starts_at INTEGER,
    expires_at INTEGER,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    consumed_at TEXT,
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE,
    CHECK (status IN ('granted', 'consumed', 'revoked', 'expired'))
);

CREATE INDEX IF NOT EXISTS idx_web_entitlements_account
    ON web_entitlements (account_id, status, type);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_founder_free_subscription
    ON web_entitlements (account_id, type)
    WHERE type = 'founder_free_subscription'
      AND status IN ('granted', 'consumed');

CREATE TABLE IF NOT EXISTS web_founder_reservations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    username TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    email_canonical TEXT NOT NULL,
    email_display TEXT NOT NULL,
    founder_code TEXT NOT NULL,
    credited_referrals INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'pending',
    expires_at INTEGER,
    verified_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE SET NULL,
    CHECK (credited_referrals >= 0),
    CHECK (status IN ('pending', 'verified', 'dev_verified', 'expired', 'released', 'blocked'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_founder_code
    ON web_founder_reservations (founder_code);

CREATE INDEX IF NOT EXISTS idx_web_founder_reservations_email
    ON web_founder_reservations (email_canonical, status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_founder_active_name
    ON web_founder_reservations (normalized_name)
    WHERE status IN ('pending', 'verified', 'dev_verified');

CREATE TABLE IF NOT EXISTS web_founder_referrals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    referrer_reservation_id INTEGER NOT NULL,
    referred_reservation_id INTEGER NOT NULL,
    referrer_code TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'signup_started',
    risk_score INTEGER NOT NULL DEFAULT 0,
    credited_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (referrer_reservation_id) REFERENCES web_founder_reservations (id) ON DELETE CASCADE,
    FOREIGN KEY (referred_reservation_id) REFERENCES web_founder_reservations (id) ON DELETE CASCADE,
    CHECK (referrer_reservation_id <> referred_reservation_id),
    CHECK (risk_score BETWEEN 0 AND 100),
    CHECK (status IN ('clicked', 'signup_started', 'email_verified', 'qualified_pending_age', 'credited', 'rejected', 'pending_review'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_web_founder_referrals_pair
    ON web_founder_referrals (referrer_reservation_id, referred_reservation_id);

CREATE INDEX IF NOT EXISTS idx_web_founder_referrals_code
    ON web_founder_referrals (referrer_code, status);

CREATE TABLE IF NOT EXISTS web_audit_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    actor_type TEXT NOT NULL DEFAULT 'system',
    actor_id TEXT,
    event_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE SET NULL,
    CHECK (actor_type IN ('system', 'account', 'admin'))
);

CREATE INDEX IF NOT EXISTS idx_web_audit_events_account
    ON web_audit_events (account_id, created_at);

CREATE INDEX IF NOT EXISTS idx_web_audit_events_entity
    ON web_audit_events (entity_type, entity_id, created_at);

CREATE TABLE IF NOT EXISTS web_abuse_signals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER,
    founder_reservation_id INTEGER,
    signal_type TEXT NOT NULL,
    signal_hash TEXT NOT NULL,
    bucket TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    expires_at INTEGER,
    FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE SET NULL,
    FOREIGN KEY (founder_reservation_id) REFERENCES web_founder_reservations (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_web_abuse_signals_hash
    ON web_abuse_signals (signal_type, signal_hash, created_at);

CREATE INDEX IF NOT EXISTS idx_web_abuse_signals_account
    ON web_abuse_signals (account_id, created_at);
