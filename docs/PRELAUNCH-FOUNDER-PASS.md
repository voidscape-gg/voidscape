# Prelaunch Founder Pass

Voidscape has a static founder-pass prototype in `web/prelaunch/`. It is intentionally not production registration. The prototype proves the page, copy, referral progress UI, and name-reservation interaction, but the current state lives in browser `localStorage`.

Do not use the prototype to collect real signups until the production flow below exists.

## Goals

- Let one real person reserve one launch username.
- Let verified users invite friends before launch.
- Unlock a free launch subscription after 2 legitimate referred users.
- Prevent fake invite loops, disposable mass signups, and name squatting.
- Keep an audit trail for every reservation, referral credit, entitlement, and admin change.

## Recommended Stack

| Layer | Choice | Notes |
|---|---|---|
| Frontend | Static page or site app | Reuse the current `web/prelaunch/` visuals and replace `localStorage` calls with API calls. |
| API | TypeScript with Fastify, or Next.js API routes | Keep the API small, typed, and isolated from the game server at first. |
| Database | Postgres | Strong uniqueness constraints and transactional name claims matter. |
| Rate limiting | Redis or Upstash Redis | Per-IP, per-email, per-name, per-referrer, and per-subnet throttles. |
| Bot protection | Cloudflare Turnstile + Cloudflare WAF | Required before reservation submit and referral signup. |
| Email | Postmark, Resend, or SES | Magic-link verification and launch notices. |
| Game integration | Internal signed API or launch-time export | Game account creation must check the same reserved-name source of truth. |

## Production Flow

### 1. Start Reservation

The user submits:

- desired username
- email
- optional referral code from `?ref=...`
- Turnstile token

Server behavior:

1. Verify Turnstile server-side.
2. Normalize username with the exact game rules.
3. Reject invalid, reserved, banned, staff-like, impersonation, or too-similar names.
4. Apply rate limits before writing.
5. Create or refresh a pending reservation with an expiry.
6. Send a magic-link email.

Names are not truly reserved until email verification succeeds.

### 2. Verify Email

The user opens the magic link.

Server behavior:

1. Validate the signed token and compare it to a stored token hash.
2. In a transaction, lock the normalized username row.
3. Promote the pending reservation to `verified`.
4. Create the user founder code if it does not already exist.
5. Record the referral relationship as `pending_review` or `qualified_pending_age`.
6. Return the founder-pass state to the frontend.

Only a verified user can receive an invite link.

### 3. Credit Referral

Referral credit is not granted when someone merely opens a link.

A referral counts only when:

- the referred account verifies email
- the referred account reserves a unique valid name
- referrer and referred user are different users
- abuse checks pass
- the referral has not already been credited

Recommended status flow:

```
clicked -> signup_started -> email_verified -> qualified_pending_age -> credited
                                      └──────-> rejected
                                      └──────-> pending_review
```

For prelaunch, use conservative rules. A referral can wait 12-24 hours before crediting, or be credited immediately only if its risk score is clean.

### 4. Unlock Founder Reward

When a user reaches 2 credited referrals:

1. Insert one `founder_entitlements` row for `free_launch_subscription`.
2. Mark it `granted`, not consumed.
3. Show the unlocked state in the founder-pass UI.
4. Consume the entitlement only after the real billing/subscription system exists.

The unlock status must always come from the backend. The browser only renders it.

## Data Model

Suggested tables:

### `prelaunch_users`

- `id`
- `email_canonical`
- `email_display`
- `email_verified_at`
- `founder_code`
- `status`: `active`, `blocked`, `review`
- `created_at`
- `updated_at`

Constraints:

- unique `email_canonical`
- unique `founder_code`

### `name_reservations`

- `id`
- `user_id`
- `display_name`
- `normalized_name`
- `status`: `pending`, `verified`, `expired`, `released`, `blocked`
- `expires_at`
- `verified_at`
- `created_at`
- `updated_at`

Constraints:

- unique `normalized_name` where status is `pending` or `verified`
- unique `user_id` where status is `verified`

### `referrals`

- `id`
- `referrer_user_id`
- `referred_user_id`
- `referrer_code`
- `status`: `clicked`, `signup_started`, `qualified_pending_age`, `credited`, `rejected`, `pending_review`
- `risk_score`
- `credited_at`
- `created_at`
- `updated_at`

Constraints:

- unique `(referrer_user_id, referred_user_id)`
- reject `referrer_user_id = referred_user_id`

### `founder_entitlements`

- `id`
- `user_id`
- `type`: `free_launch_subscription`
- `status`: `granted`, `consumed`, `revoked`
- `source`: `referral_2_verified`
- `created_at`
- `consumed_at`

Constraints:

- unique `(user_id, type)` while status is `granted` or `consumed`

### `audit_events`

- `id`
- `actor_type`: `system`, `user`, `admin`
- `actor_id`
- `event_type`
- `entity_type`
- `entity_id`
- `metadata_json`
- `created_at`

Audit these events at minimum:

- reservation created
- reservation verified
- reservation expired/released
- referral credited/rejected/reversed
- entitlement granted/revoked/consumed
- admin override

### `abuse_signals`

Store privacy-aware hashes, not raw long-term fingerprints:

- email domain
- hashed IP with rotating salt
- /24 or /48 network bucket
- user agent hash
- Turnstile result metadata
- referral velocity counters

## API Shape

Minimum endpoints:

- `POST /api/prelaunch/reservations`
  - input: username, email, referral code, Turnstile token
  - output: generic success message, never leaks whether an email already exists

- `GET /api/prelaunch/verify?token=...`
  - verifies email and finalizes name reservation
  - redirects to the founder-pass page with a short-lived session

- `GET /api/prelaunch/me`
  - returns current reservation, invite link, referral count, entitlement state

- `POST /api/prelaunch/resend-verification`
  - rate-limited resend flow

- `POST /api/prelaunch/release-name`
  - optional, lets a verified user release or change the reserved name before a cutoff

Admin endpoints should be separate, authenticated, and fully audited.

## Abuse Controls

Use layered controls. No single check is enough.

- Turnstile before every reservation attempt.
- Strict per-IP and per-subnet signup throttles.
- Strict per-email and per-normalized-name throttles.
- One verified reservation per canonical email.
- One verified reservation per user.
- Pending reservations expire automatically.
- Referral credit requires verified referred account.
- Referral credit is reversible.
- Block self-referrals.
- Flag matching IP/device/subnet patterns between referrer and referred accounts.
- Flag bursts from one referrer.
- Flag disposable email domains.
- Flag many signups sharing the same password later at game launch.
- Put suspicious referrals into review instead of silently crediting them.
- Never expose exact rejection reasons to attackers.

## Username Rules

The reservation service must share the game server's username normalization rules.

Recommended policy:

- lowercase normalized name
- trim and collapse spaces
- allow only the same characters the game login supports
- reserve a list of staff/system names
- reserve known impersonation targets
- reject confusing variants if needed
- enforce final uniqueness in Postgres, not only in application code

The game account creation flow must check `name_reservations.normalized_name` before allowing a username at launch.

## Go-Live Checklist

Before collecting real signups:

1. Deploy the prelaunch API with Postgres migrations.
2. Configure Redis rate limits.
3. Configure Turnstile and verify tokens server-side.
4. Configure email sender domain, DKIM, SPF, and DMARC.
5. Replace `web/prelaunch/script.js` localStorage helpers with API calls.
6. Add a privacy policy note for email, referral, and anti-abuse data.
7. Add admin-only review tooling for flagged referrals and names.
8. Add monitoring for signup volume, mail delivery, verification rate, and referral spikes.
9. Run a staging test with at least:
   - normal reservation
   - duplicate username
   - duplicate email
   - expired pending reservation
   - valid referral unlock
   - self-referral rejection
   - suspicious referral review
10. Freeze launch username rules and document the cutoff time for changes.
11. Back up the production database before public announcement.

At launch:

1. Lock final name changes at the announced cutoff.
2. Export verified reservations or expose them through an internal signed API.
3. Make game account registration consult the reservation source of truth.
4. Allow only the verified reservation owner to claim the reserved name.
5. Create or connect the subscription system.
6. Convert granted founder entitlements into launch subscription credits.
7. Keep the audit log immutable after launch.

## Prototype Migration Notes

Current files:

- `web/prelaunch/index.html`
- `web/prelaunch/styles.css`
- `web/prelaunch/script.js`
- `web/prelaunch/assets/`

The migration point is `web/prelaunch/script.js`. Replace these local functions with API calls:

- `loadAccount`
- `saveAccount`
- `loadLedger`
- `saveLedger`
- `creditReferral`
- `inviteCount`

The frontend should become a renderer for backend state:

- `pending_email_verification`
- `reserved_name`
- `invite_link`
- `credited_referrals`
- `required_referrals`
- `free_subscription_unlocked`
