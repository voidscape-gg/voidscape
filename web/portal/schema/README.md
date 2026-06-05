# Voidscape Portal Schema

These SQL files define the future website/account-management data model. They are not auto-applied by the OpenRSC game server.

Use them when standing up the production portal database or a local web-account integration test:

```bash
scripts/test-portal-schema.sh
```

## Tables

- `web_accounts` - email/password identity for the website.
- `web_account_sessions` - hashed bearer/session tokens.
- `web_recovery_codes` - hashed one-time account recovery codes.
- `web_account_characters` - links one web account to up to 10 existing `players.id` values.
- `web_character_link_challenges` - proof-of-control flow for linking an existing game character.
- `web_entitlements` - weekly subscription cards and founder rewards.
- `web_founder_reservations` - early-access username/email reservations and founder codes.
- `web_founder_referrals` - referral qualification state.
- `web_audit_events` - append-only audit trail for account, founder, entitlement, and admin actions.
- `web_abuse_signals` - privacy-aware hashes/buckets used for rate limits and referral review.

The game login model remains unchanged. A production link flow should verify character ownership before inserting `web_account_characters` rows, and production recovery should verify a hashed `web_recovery_codes` row before allowing a password reset.
