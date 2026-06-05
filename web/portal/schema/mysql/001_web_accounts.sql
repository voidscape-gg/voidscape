-- voidscape portal account-management schema for MySQL/MariaDB.
-- This is portal-owned reference SQL and is not auto-applied by the OpenRSC server.

CREATE TABLE IF NOT EXISTS web_accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email_canonical VARCHAR(255) NOT NULL,
    email_display VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    status VARCHAR(24) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_accounts_email (email_canonical),
    KEY idx_web_accounts_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_account_identities (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(191) NOT NULL,
    email_canonical VARCHAR(255) NOT NULL,
    email_display VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    avatar_url VARCHAR(512),
    email_verified TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    last_login_at DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_account_identities_provider_subject (provider, provider_subject),
    UNIQUE KEY uq_web_account_identities_account_provider (account_id, provider),
    KEY idx_web_account_identities_email (email_canonical),
    CONSTRAINT fk_web_account_identities_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_web_account_identities_provider CHECK (provider IN ('google')),
    CONSTRAINT chk_web_account_identities_verified CHECK (email_verified IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_account_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    session_token_hash VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at BIGINT UNSIGNED NOT NULL,
    last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ip_hash VARCHAR(128),
    user_agent_hash VARCHAR(128),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_account_sessions_token (session_token_hash),
    KEY idx_web_account_sessions_account (account_id, expires_at),
    CONSTRAINT fk_web_account_sessions_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_recovery_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    code_hint VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    used_at DATETIME(3),
    revoked_at DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_recovery_codes_hash (code_hash),
    KEY idx_web_recovery_codes_account (account_id, status),
    CONSTRAINT fk_web_recovery_codes_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_web_recovery_codes_status CHECK (status IN ('active', 'used', 'revoked'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_account_characters (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    player_id BIGINT UNSIGNED NOT NULL,
    display_username VARCHAR(12) NOT NULL,
    normalized_username VARCHAR(12) NOT NULL,
    slot TINYINT UNSIGNED NOT NULL,
    is_primary TINYINT(1) NOT NULL DEFAULT 0,
    primary_account_id BIGINT UNSIGNED AS (CASE WHEN is_primary = 1 THEN account_id ELSE NULL END) STORED,
    link_status VARCHAR(24) NOT NULL DEFAULT 'linked',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_account_characters_player (player_id),
    UNIQUE KEY uq_web_account_characters_slot (account_id, slot),
    UNIQUE KEY uq_web_account_characters_name (account_id, normalized_username),
    UNIQUE KEY uq_web_account_characters_primary (primary_account_id),
    KEY idx_web_account_characters_account (account_id, link_status),
    CONSTRAINT fk_web_account_characters_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_web_account_characters_slot CHECK (slot <= 9),
    CONSTRAINT chk_web_account_characters_primary CHECK (is_primary IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_character_link_challenges (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    player_id BIGINT UNSIGNED,
    display_username VARCHAR(12) NOT NULL,
    normalized_username VARCHAR(12) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'pending',
    expires_at BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    verified_at DATETIME(3),
    PRIMARY KEY (id),
    KEY idx_web_character_link_challenges_account (account_id, status, expires_at),
    KEY idx_web_character_link_challenges_name (normalized_username, status),
    CONSTRAINT fk_web_character_link_challenges_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_entitlements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'granted',
    source VARCHAR(64) NOT NULL,
    code_hint VARCHAR(32),
    starts_at BIGINT UNSIGNED,
    expires_at BIGINT UNSIGNED,
    active_founder_free_subscription_account BIGINT UNSIGNED AS (
        CASE
            WHEN type = 'founder_free_subscription' AND status IN ('granted', 'consumed') THEN account_id
            ELSE NULL
        END
    ) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    consumed_at DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_founder_free_subscription (active_founder_free_subscription_account),
    KEY idx_web_entitlements_account (account_id, status, type),
    CONSTRAINT fk_web_entitlements_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_founder_reservations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED,
    username VARCHAR(12) NOT NULL,
    normalized_name VARCHAR(12) NOT NULL,
    active_normalized_name VARCHAR(12) AS (
        CASE
            WHEN status IN ('pending', 'verified', 'dev_verified') THEN normalized_name
            ELSE NULL
        END
    ) STORED,
    email_canonical VARCHAR(255) NOT NULL,
    email_display VARCHAR(255) NOT NULL,
    founder_code VARCHAR(24) NOT NULL,
    credited_referrals INT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'pending',
    expires_at BIGINT UNSIGNED,
    verified_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_founder_code (founder_code),
    UNIQUE KEY uq_web_founder_active_name (active_normalized_name),
    KEY idx_web_founder_reservations_email (email_canonical, status),
    CONSTRAINT fk_web_founder_reservations_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_founder_referrals (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    referrer_reservation_id BIGINT UNSIGNED NOT NULL,
    referred_reservation_id BIGINT UNSIGNED NOT NULL,
    referrer_code VARCHAR(24) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'signup_started',
    risk_score INT UNSIGNED NOT NULL DEFAULT 0,
    credited_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_web_founder_referrals_pair (referrer_reservation_id, referred_reservation_id),
    KEY idx_web_founder_referrals_code (referrer_code, status),
    CONSTRAINT fk_web_founder_referrals_referrer
        FOREIGN KEY (referrer_reservation_id) REFERENCES web_founder_reservations (id) ON DELETE CASCADE,
    CONSTRAINT fk_web_founder_referrals_referred
        FOREIGN KEY (referred_reservation_id) REFERENCES web_founder_reservations (id) ON DELETE CASCADE,
    CONSTRAINT chk_web_founder_referrals_distinct CHECK (referrer_reservation_id <> referred_reservation_id),
    CONSTRAINT chk_web_founder_referrals_risk CHECK (risk_score <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_audit_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED,
    actor_type VARCHAR(24) NOT NULL DEFAULT 'system',
    actor_id VARCHAR(64),
    event_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64),
    metadata_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_web_audit_events_account (account_id, created_at),
    KEY idx_web_audit_events_entity (entity_type, entity_id, created_at),
    CONSTRAINT fk_web_audit_events_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS web_abuse_signals (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED,
    founder_reservation_id BIGINT UNSIGNED,
    signal_type VARCHAR(48) NOT NULL,
    signal_hash VARCHAR(128) NOT NULL,
    bucket VARCHAR(64),
    metadata_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at BIGINT UNSIGNED,
    PRIMARY KEY (id),
    KEY idx_web_abuse_signals_hash (signal_type, signal_hash, created_at),
    KEY idx_web_abuse_signals_account (account_id, created_at),
    CONSTRAINT fk_web_abuse_signals_account
        FOREIGN KEY (account_id) REFERENCES web_accounts (id) ON DELETE SET NULL,
    CONSTRAINT fk_web_abuse_signals_founder
        FOREIGN KEY (founder_reservation_id) REFERENCES web_founder_reservations (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
