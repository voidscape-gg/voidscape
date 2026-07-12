#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-schema.XXXXXX")"
db_path="$tmp_dir/portal-schema.db"

cleanup() {
	rm -rf "$tmp_dir"
}
trap cleanup EXIT

sqlite3 "$db_path" < web/portal/schema/sqlite/001_web_accounts.sql
sqlite3 "$db_path" < web/portal/schema/sqlite/002_tebex_commerce.sql

sqlite3 "$db_path" <<'SQL'
PRAGMA foreign_keys = ON;

INSERT INTO web_accounts (id, email_canonical, email_display, password_hash)
VALUES (1, 'schema@example.com', 'schema@example.com', 'scrypt$fixture');

INSERT INTO web_account_sessions (account_id, session_token_hash, expires_at, ip_hash, user_agent_hash)
VALUES (1, 'session-hash-fixture', 9999999999999, 'ip-hash', 'ua-hash');

INSERT INTO web_account_identities (
	account_id, provider, provider_subject, email_canonical, email_display, display_name, email_verified
) VALUES (1, 'google', 'google-subject-fixture', 'schema@example.com', 'schema@example.com', 'Schema Hero', 1);

INSERT INTO web_recovery_codes (account_id, code_hash, code_hint, status)
VALUES (1, 'recovery-hash-fixture', '1234', 'active');

INSERT INTO web_password_reset_tokens (
	account_id, token_hash, request_ip_hash, identifier_type, status, expires_at
) VALUES (1, 'reset-hash-fixture', 'reset-ip-hash', 'username', 'pending', 9999999999999);

INSERT INTO web_legacy_account_claims (
	account_id, character_id, player_id, normalized_username,
	target_email_canonical, target_email_display, password_hash,
	credential_fingerprint, ownership_marker, token_hash, request_ip_hash,
	status, expires_at
) VALUES (
	1, 1, 77, 'schemahero',
	'claim@example.com', 'claim@example.com', 'scrypt$pending-fixture',
	'credential-fingerprint-fixture', '1', 'legacy-claim-token-hash-fixture', 'claim-ip-hash',
	'pending', 9999999999999
);

INSERT INTO web_founder_reservations (
	id, account_id, username, normalized_name, email_canonical, email_display,
	founder_code, credited_referrals, status
) VALUES
	(1, 1, 'SchemaHero', 'schemahero', 'schema@example.com', 'schema@example.com', 'SCHEMA-A1', 2, 'verified'),
	(2, NULL, 'FriendOne', 'friendone', 'friend@example.com', 'friend@example.com', 'FRIEND-A1', 0, 'verified');

INSERT INTO web_founder_referrals (referrer_reservation_id, referred_reservation_id, referrer_code, status, risk_score)
VALUES (1, 2, 'SCHEMA-A1', 'credited', 3);

INSERT INTO web_founder_referral_reward_codes (
	referral_id, referrer_reservation_id, referred_reservation_id, code, code_normalized, status
) VALUES (1, 1, 2, 'VOID-SCHM-AA22', 'VOIDSCHMAA22', 'issued');

INSERT INTO web_entitlements (account_id, type, status, source, code_hint, starts_at, expires_at)
VALUES (1, 'starter_free_subscription', 'granted', 'prelaunch_signup', 'SCHEMA', 1780539000000, 1781143800000);

INSERT INTO web_account_characters (
	account_id, player_id, display_username, normalized_username, slot, is_primary
) VALUES
	(1, 77, 'SchemaHero', 'schemahero', 0, 1),
	(1, 78, 'SchemaAlt', 'schemaalt', 9, 0);

INSERT INTO web_character_link_challenges (
	account_id, player_id, display_username, normalized_username, code_hash, status, expires_at
) VALUES (1, 79, 'SchemaLink', 'schemalink', 'challenge-hash', 'pending', 9999999999999);

INSERT INTO web_audit_events (account_id, actor_type, actor_id, event_type, entity_type, entity_id, metadata_json)
VALUES (1, 'system', 'schema-test', 'schema_smoke', 'web_account', '1', '{"ok":true}');

INSERT INTO web_abuse_signals (account_id, founder_reservation_id, signal_type, signal_hash, bucket, metadata_json, expires_at)
VALUES (1, 1, 'ip', 'ip-hash', '127.0.0.0/24', '{"source":"schema-test"}', 9999999999999);

INSERT INTO portal_commerce_checkout_intents (
	intent_id, account_id, package_id, expected_currency, expected_amount_minor,
	basket_id, status, created_at_ms, expires_at_ms, updated_at_ms
) VALUES ('intent-schema-fixture', 1, '6276316', 'USD', 500, 'basket-schema-fixture', 'completed', 1000, 2000, 1500);

INSERT INTO portal_commerce_webhook_events (
	webhook_id, event_type, payload_sha256, transaction_id, status,
	attempt_count, created_at_ms, processed_at_ms, updated_at_ms
) VALUES ('webhook-schema-fixture', 'payment.completed', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
	'tbx-schema-fixture', 'processed', 1, 1500, 1500, 1500);

INSERT INTO portal_commerce_payments (
	provider, transaction_id, intent_id, account_id, basket_id, package_id,
	quantity, payment_sequence, currency, expected_amount_minor,
	product_paid_amount_minor, transaction_paid_amount_minor, tax_and_adjustments_minor,
	status, completed_at_ms, updated_at_ms
) VALUES ('tebex', 'tbx-schema-fixture', 'intent-schema-fixture', 1, 'basket-schema-fixture',
	'6276316', 1, 'oneoff', 'USD', 500, 450, 495, 45, 'complete', 1500, 1500);

INSERT INTO portal_commerce_entitlements (
	payment_id, account_id, provider, transaction_id, line_key, package_id,
	unit_index, state, created_at_ms, updated_at_ms
) VALUES (1, 1, 'tebex', 'tbx-schema-fixture', 'package:6276316:unit:1', '6276316', 1, 'pending', 1500, 1500);
SQL

foreign_key_violations="$(sqlite3 "$db_path" "PRAGMA foreign_key_check;")"
if [[ -n "$foreign_key_violations" ]]; then
	echo "$foreign_key_violations"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_account_characters (account_id, player_id, display_username, normalized_username, slot) VALUES (1, 80, 'BadSlot', 'badslot', 10);" >/dev/null 2>&1; then
	echo "expected slot 10 to violate the 10-character slot constraint"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_account_characters (account_id, player_id, display_username, normalized_username, slot) VALUES (1, 77, 'DuplicatePlayer', 'duplicateplayer', 2);" >/dev/null 2>&1; then
	echo "expected duplicate player_id to violate character ownership uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_founder_reservations (username, normalized_name, email_canonical, email_display, founder_code, status) VALUES ('SchemaHero2', 'schemahero', 'other@example.com', 'other@example.com', 'OTHER-A1', 'pending');" >/dev/null 2>&1; then
	echo "expected duplicate active founder name to violate reservation uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_founder_referrals (referrer_reservation_id, referred_reservation_id, referrer_code, status, risk_score) VALUES (1, 2, 'SCHEMA-A1', 'credited', 0);" >/dev/null 2>&1; then
	echo "expected duplicate founder referral pair to violate referral uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_founder_referrals (referrer_reservation_id, referred_reservation_id, referrer_code, status, risk_score) VALUES (1, 1, 'SCHEMA-A1', 'credited', 0);" >/dev/null 2>&1; then
	echo "expected founder self-referral to violate referral distinctness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_founder_referral_reward_codes (referral_id, referrer_reservation_id, referred_reservation_id, code, code_normalized, status) VALUES (1, 1, 2, 'VOID-DUPE-AA22', 'VOIDDUPEAA22', 'issued');" >/dev/null 2>&1; then
	echo "expected duplicate referral reward referral_id to violate reward uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_founder_referral_reward_codes (referral_id, referrer_reservation_id, referred_reservation_id, code, code_normalized, status) VALUES (999, 1, 2, 'VOID-SCHM-AA22', 'VOIDSCHMAA22', 'issued');" >/dev/null 2>&1; then
	echo "expected duplicate referral reward code to violate code uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_account_identities (account_id, provider, provider_subject, email_canonical, email_display) VALUES (1, 'google', 'another-google-subject', 'schema@example.com', 'schema@example.com');" >/dev/null 2>&1; then
	echo "expected duplicate provider per account to violate identity uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_account_identities (account_id, provider, provider_subject, email_canonical, email_display) VALUES (1, 'google', 'google-subject-fixture', 'schema@example.com', 'schema@example.com');" >/dev/null 2>&1; then
	echo "expected duplicate google subject to violate identity uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_password_reset_tokens (account_id, token_hash, identifier_type, status, expires_at) VALUES (1, 'reset-hash-fixture', 'email', 'pending', 9999999999999);" >/dev/null 2>&1; then
	echo "expected duplicate reset token hash to violate token uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_password_reset_tokens (account_id, token_hash, identifier_type, status, expires_at) VALUES (1, 'reset-hash-invalid', 'player-name', 'pending', 9999999999999);" >/dev/null 2>&1; then
	echo "expected invalid reset identifier type to violate the identifier constraint"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_password_reset_tokens (account_id, token_hash, identifier_type, status, expires_at) VALUES (1, 'reset-hash-invalid-status', 'email', 'active', 9999999999999);" >/dev/null 2>&1; then
	echo "expected invalid reset token status to violate the status constraint"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_legacy_account_claims (account_id, character_id, player_id, normalized_username, target_email_canonical, target_email_display, ownership_marker, token_hash, status, expires_at) VALUES (1, 1, 77, 'schemahero', 'other@example.com', 'other@example.com', '1', 'legacy-claim-token-hash-fixture', 'pending', 9999999999999);" >/dev/null 2>&1; then
	echo "expected duplicate legacy claim token hash to violate token uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO web_legacy_account_claims (account_id, character_id, player_id, normalized_username, target_email_canonical, target_email_display, ownership_marker, token_hash, status, expires_at) VALUES (1, 1, 77, 'schemahero', 'other@example.com', 'other@example.com', '1', 'legacy-claim-invalid-status', 'active', 9999999999999);" >/dev/null 2>&1; then
	echo "expected invalid legacy claim status to violate the status constraint"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO portal_commerce_webhook_events (webhook_id, event_type, payload_sha256, status, attempt_count, created_at_ms, updated_at_ms) VALUES ('webhook-payload-dupe', 'payment.completed', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'processed', 1, 1, 1);" >/dev/null 2>&1; then
	echo "expected duplicate webhook payload to violate event uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO portal_commerce_payments (provider, transaction_id, intent_id, account_id, basket_id, package_id, quantity, payment_sequence, currency, expected_amount_minor, product_paid_amount_minor, transaction_paid_amount_minor, tax_and_adjustments_minor, status, completed_at_ms, updated_at_ms) VALUES ('tebex', 'tbx-schema-fixture', 'other-intent', 1, 'other-basket', '6276316', 1, 'oneoff', 'USD', 500, 500, 550, 50, 'complete', 1, 1);" >/dev/null 2>&1; then
	echo "expected duplicate provider transaction to violate payment uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO portal_commerce_entitlements (payment_id, account_id, provider, transaction_id, line_key, package_id, unit_index, state, created_at_ms, updated_at_ms) VALUES (1, 1, 'tebex', 'tbx-schema-fixture', 'package:6276316:unit:1', '6276316', 1, 'pending', 1, 1);" >/dev/null 2>&1; then
	echo "expected duplicate provider transaction line to violate entitlement uniqueness"
	exit 1
fi

if sqlite3 "$db_path" "INSERT INTO portal_commerce_entitlements (payment_id, account_id, provider, transaction_id, line_key, package_id, unit_index, state, created_at_ms, updated_at_ms) VALUES (1, 1, 'tebex', 'tbx-other', 'other-line', '6276316', 1, 'available', 1, 1);" >/dev/null 2>&1; then
	echo "expected invalid commerce entitlement state to violate the state constraint"
	exit 1
fi

if sqlite3 -cmd 'PRAGMA foreign_keys=ON;' "$db_path" "INSERT INTO portal_commerce_payments (provider, transaction_id, intent_id, account_id, basket_id, package_id, quantity, payment_sequence, currency, expected_amount_minor, product_paid_amount_minor, transaction_paid_amount_minor, tax_and_adjustments_minor, status, completed_at_ms, updated_at_ms) VALUES ('tebex', 'tbx-orphan', 'missing-intent', 1, 'missing-basket', '6276316', 1, 'oneoff', 'USD', 500, 500, 540, 40, 'complete', 1, 1);" >/dev/null 2>&1; then
	echo "fresh commerce connections must enforce payment-to-intent foreign keys"
	exit 1
fi

if sqlite3 -cmd 'PRAGMA foreign_keys=ON;' "$db_path" "INSERT INTO portal_commerce_entitlements (payment_id, account_id, provider, transaction_id, line_key, package_id, unit_index, state, created_at_ms, updated_at_ms) VALUES (999, 1, 'tebex', 'tbx-orphan-entitlement', 'orphan-line', '6276316', 1, 'pending', 1, 1);" >/dev/null 2>&1; then
	echo "fresh commerce connections must enforce entitlement-to-payment foreign keys"
	exit 1
fi

commerce_pii_columns="$(sqlite3 "$db_path" "SELECT name FROM pragma_table_info('portal_commerce_checkout_intents') UNION ALL SELECT name FROM pragma_table_info('portal_commerce_webhook_events') UNION ALL SELECT name FROM pragma_table_info('portal_commerce_payments') UNION ALL SELECT name FROM pragma_table_info('portal_commerce_entitlements');" | rg -i '(^|_)(email|username|ip|raw_body|customer)(_|$)' || true)"
if [[ -n "$commerce_pii_columns" ]]; then
	echo "commerce tables must not contain customer PII columns: $commerce_pii_columns"
	exit 1
fi

sqlite3 "$db_path" "INSERT INTO web_founder_reservations (username, normalized_name, email_canonical, email_display, founder_code, status) VALUES ('SchemaHero2', 'schemahero', 'other@example.com', 'other@example.com', 'OTHER-A2', 'released');"

sqlite3 -json "$db_path" <<'SQL'
SELECT
	(SELECT COUNT(*) FROM web_accounts) AS accounts,
	(SELECT COUNT(*) FROM web_account_identities) AS identities,
	(SELECT COUNT(*) FROM web_recovery_codes) AS recovery_codes,
	(SELECT COUNT(*) FROM web_password_reset_tokens) AS password_reset_tokens,
	(SELECT COUNT(*) FROM web_legacy_account_claims) AS legacy_account_claims,
	(SELECT COUNT(*) FROM web_account_characters) AS character_links,
	(SELECT COUNT(*) FROM web_founder_reservations) AS founder_reservations,
	(SELECT COUNT(*) FROM web_founder_referrals) AS founder_referrals,
	(SELECT COUNT(*) FROM web_founder_referral_reward_codes) AS referral_reward_codes,
	(SELECT COUNT(*) FROM web_entitlements) AS entitlements,
	(SELECT COUNT(*) FROM web_audit_events) AS audit_events,
	(SELECT COUNT(*) FROM web_abuse_signals) AS abuse_signals,
	(SELECT COUNT(*) FROM portal_commerce_checkout_intents) AS commerce_intents,
	(SELECT COUNT(*) FROM portal_commerce_webhook_events) AS commerce_events,
	(SELECT COUNT(*) FROM portal_commerce_payments) AS commerce_payments,
	(SELECT COUNT(*) FROM portal_commerce_entitlements) AS commerce_entitlements;
SQL

game_patch_db="$tmp_dir/game-commerce-patch.db"
sqlite3 "$game_patch_db" < server/database/sqlite/patches/2026_07_11_add_portal_commerce.sql
game_patch_tables="$(sqlite3 "$game_patch_db" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE 'portal_commerce_%';")"
if [[ "$game_patch_tables" != "4" ]]; then
	echo "game SQLite commerce patch should create all four commerce tables"
	exit 1
fi
