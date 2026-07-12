# Voidscape Portal Schema

These SQL files define the website/account-management data model. The `001` web-account schema is portal-owned and is not auto-applied by the game server. The `002` commerce schema has matching OpenRSC SQLite/MySQL patches so the portal and game claim path share one restricted ledger.

Use them when standing up the production portal database or a local web-account integration test:

```bash
scripts/test-portal-schema.sh
```

## Tables

- `web_accounts` - email/password identity for the website, including account-wide subscription expiry and native-backfill claim state.
- `web_account_identities` - external login identities such as Google OpenID Connect subjects.
- `web_account_sessions` - hashed bearer/session tokens.
- `web_recovery_codes` - hashed one-time account recovery codes; the local API consumes one code during password recovery, rotates the password hash, and revokes old sessions.
- `web_password_reset_tokens` - hashed, expiring, one-use email reset tokens with request metadata and explicit pending/used/revoked/expired states.
- `web_legacy_account_claims` - hashed, expiring, one-use email claims that bind a proved native character to a new verified portal email and website-password hash.
- `web_account_characters` - links one web account to up to 10 existing `players.id` values.
- `web_character_link_challenges` - proof-of-control flow for linking an existing game character.
- `web_entitlements` - weekly subscription cards and one-time starter subscription-card grants.
- `web_founder_reservations` - early-access username/email reservations and founder codes.
- `web_founder_referrals` - referral qualification state.
- `web_founder_referral_reward_codes` - one subscription-card signup code issued per credited beta referral.
- `web_audit_events` - append-only audit trail for account, founder, reward-code, entitlement, and admin actions.
- `web_abuse_signals` - privacy-aware hashes/buckets used for starter-card rate limits, recovery-failure tracking, and referral review.
- `portal_commerce_checkout_intents` - expiring opaque account/package/price expectations and their Tebex basket IDs.
- `portal_commerce_webhook_events` - deduplicated webhook IDs and payload hashes with retryable processing status; raw bodies are never stored.
- `portal_commerce_payments` - exact provider transaction facts after package/quantity/one-off/currency verification, with expected catalog, product-paid, transaction-paid, and signed-total-minus-product `tax_and_adjustments` minor-unit fields kept distinct.
- `portal_commerce_entitlements` - one card unit per verified payment line, moving through pending/claimed/revoked/frozen/review states.

The commerce tables intentionally contain no customer email, username, IP address, or raw webhook payload. The exact Tebex transaction ID is retained only in this restricted ledger; later public item provenance should expose a hash instead.

The game login model still uses one `players` row per character, but public registration should be portal-first. Website-password recovery accepts either a hashed recovery code or a one-time token delivered to the account email; both revoke existing portal sessions and other recovery credentials. Older native accounts can be claimed only after the current game password and latest `web_account_id` marker match, followed by possession of the new email. Character game-password changes are a separate authenticated action and do not alter the website password.
