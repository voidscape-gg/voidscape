# Prelaunch QA Handoff

Last updated: 2026-07-12

This is the current evidence map for prelaunch readiness. It is intentionally short; the full gate list remains in `docs/RELEASE-CHECKLIST.md`.

## Green Evidence

- Portal/account visual gate:
  - Local launch-signup/account-manager proof: `tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke` proves signup, dashboard quick actions, second game-login creation, 2 / 10 slot state, starter-card preservation, recovery-code support fallback, security, and signed-in mobile nav.
  - Local non-mutating link-click proof: `tmp/prelaunch-portal-local-link-clicks-v1` proves visible Reserve/Features/footer CTAs, policy routes, and AGPL source link wiring from the patched worktree.
  - Live post-sync link-click proof: `tmp/prelaunch-portal-live-link-clicks-v2` proves the same visible CTA/link wiring on `https://voidscape.gg/`.
  - Launch-open countdown takeover proof: `tmp/prelaunch-portal-open-visual-v2/smoke`
  - Live non-mutating proof: `tmp/prelaunch-portal-live-android-gate-v1`
  - Live authenticated account-manager proof: `tmp/prelaunch-portal-live-account-manager-login-v1` reuses the disposable QA account, creates no new account/character/card, and proves dashboard, characters, subscription, security recovery fallback, and signed-in mobile nav.
- Portal/API hardening:
  - `scripts/test-portal-api.sh` now includes a launch-signup second-character regression: the extra game login creates and links a real OpenRSC save while the visible starter-card entitlement stays exactly one and the game DB starter-card marker count does not increase.
  - The same API smoke now covers launch-signup recovery-code password reset: eight one-time codes are generated, invalid codes are rejected, one valid code is consumed, old launch sessions are revoked, and the recovered password becomes the only working password.
  - `scripts/test-portal-schema.sh`
  - hosted/local launch verifier rehearsals recorded in `memory/prelaunch_readiness.md`
  - Live source-disclosure proof: `/`, `/privacy`, `/data-deletion`, and `/api/integrity` point at `https://github.com/voidscape-gg/voidscape`; visual proof `tmp/prelaunch-portal-live-source-link-v1`
  - Superseded config-health evidence: the June 27 proof recorded `adminTokenConfigured=true`. The launch requirement is now `PORTAL_ADMIN_TOKEN` unset during normal operation plus external `404` responses for `/api/admin/*` on `voidscape.gg`, `www.voidscape.gg`, and the legacy sslip origin. All three remain mandatory verifier targets through launch; removing the legacy alias is a post-launch contract change. Rerun and archive the hardened hosted verifier before launch.
  - Live footer/CTA fix proof: backup `/opt/voidscape/backups/portal-pre-footer-cta-fix-20260627T110009Z.tgz`, live link-click smoke `tmp/prelaunch-portal-live-link-clicks-v2`, and non-mutating hosted verifier `tmp/launch-staging-live-nonmutating-footer-cta-fix-v1/summary.json`.
  - Live recovery-support fallback proof: backup `/opt/voidscape/backups/portal-pre-recovery-fallback-20260627T122024Z.tgz`, HTTPS HTML check confirms `recovery-support-note`, live non-mutating portal visual smoke `tmp/prelaunch-portal-live-recovery-fallback-v1`, and hosted verifier `tmp/launch-staging-live-nonmutating-recovery-fallback-v1/summary.json`.
- Desktop web `/play`:
  - Live deployed package proof: `tmp/web-teavm-deployment-verify-prelaunch-mobile-login-fields-smoke-v1`
  - Live signup + hosted web smoke proof: `tmp/launch-staging-live-signup-websmoke-v1/summary.json` created disposable account `Qa094648`, one linked OpenRSC save, waiting starter card, and passed `/play` login smoke.
  - Local desktop web authenticated proof: `tmp/prelaunch-client-qa/web-desktop-portal-created-v2`
- Android APK:
  - Public-channel decision: restore the APK to the post-launch download chooser when the APK artifact exists.
  - Auth login proof: `tmp/prelaunch-client-qa/android-auth-login-v4`
  - Auth lifecycle/logout proof: `tmp/prelaunch-client-qa/android-auth-lifecycle-v15`
  - Android-enabled staging debug package: `tmp/prelaunch-client-qa/launch-staging-package-v3-debug-android`
  - Release signing guard: `tmp/prelaunch-client-qa/android-release-signing-guard-v1`
  - Public portal APK links are served from the configured APK artifact when one exists. Set `PORTAL_ANDROID_APK` when the public APK lives outside the default Android build output.
  - For broad production promotion, prefer upload signing, a release APK package, and passing physical Android QA even though the post-launch chooser can expose an available APK artifact.
- Desktop Java client:
  - Login screen proof: `tmp/prelaunch-client-qa/pc-client-login-screen-visual-v2`
  - Authenticated fresh-cache proof: `tmp/prelaunch-client-qa/pc-client-auth-visual-v3`
  - Live-host authenticated proof: `tmp/prelaunch-client-qa/pc-client-live-credential-v1`
- Desktop launcher:
  - Live-style launcher screenshot proof: `tmp/prelaunch-client-qa/launcher-live-visual-v1`
  - Packaged staging launcher proof: `tmp/prelaunch-client-qa/launcher-staging-jar-visual-v2`
  - Bundle with embedded/sidecar launcher properties verified: `tmp/prelaunch-client-qa/launch-staging-package-v2`
- iPhone web:
  - Live Mobile Safari Simulator orientation proof: `tmp/prelaunch-client-qa/iphone-simulator-live-play-v1`
  - Live physical iPhone QA packet/template: `tmp/iphone-web-qa-live-prelaunch-v1/iphone-safari-qa-report.md` is prefilled with the current live `/play` URL, WSS endpoint, portal URLs, and hosted deployment verifier summary; real-device diagnostics still need to be pasted by the tester.
- Aggregate non-device report:
  - Latest local report proof: `tmp/prelaunch-readiness-full-live-credential-v1/summary.md` (11 pass, 0 warn, 0 fail)
  - Latest focused board refresh: `tmp/prelaunch-readiness-live-account-board-v1/summary.md` (7 pass, 4 expected warn, 0 fail) with the live authenticated account-manager tiles added.
  - Latest live hosted verifier: `tmp/launch-staging-live-signup-websmoke-v1/summary.json`; this includes deployed server-config proof, real launch signup, starter-card state, and hosted web smoke.
  - Generated rollback/source-disclosure handoff: `tmp/prelaunch-readiness-full-live-credential-v1/launch-staging-package/RELEASE-HANDOFF.md`
  - Filled compact handoff archive: `tmp/prelaunch-release-handoff-current-v1.tgz`
- Visual game/client review board:
  - Latest board: `tmp/prelaunch-readiness-full-live-credential-v1/visual-board/index.html`
  - Summary: `tmp/prelaunch-readiness-full-live-credential-v1/visual-board/summary.json` reports 7 sections, 47 screenshots, 0 missing.
  - One-glance desktop overview PNG: `tmp/prelaunch-readiness-full-live-credential-v1/visual-board/board-review.png`
  - One-glance mobile overview PNG: `tmp/prelaunch-readiness-full-live-credential-v1/visual-board/board-review-mobile.png`
  - Board now includes global QC focus notes, per-section review focus, manual gate callouts, hosted web mobile Design Your Character proof, account-manager second-character proof, live non-mutating account-manager proof, and the recovery-code support fallback proof.

## Rerun Commands

The visual board is the fastest QC entry point for the game-facing surfaces. It
collects the live portal/account screenshots, countdown takeover, hosted web
client, iPhone Simulator, desktop launcher, desktop Java client, and Android APK
evidence into one browser-reviewable page, with manual-gate warnings and
section-specific review focus notes. It also renders desktop and mobile
overview PNGs for quick visual triage. The aggregate `--visual-board` gate now
requires those two overview PNGs in addition to the 47 curated screenshot tiles.

```bash
scripts/check-prelaunch-readiness.sh \
  --out tmp/prelaunch-readiness \
  --visual-board \
  --live-portal-visual \
  --android-release-guard

scripts/check-prelaunch-readiness.sh \
  --out tmp/prelaunch-readiness-final-with-devices \
  --visual-board \
  --live-portal-visual \
  --android-release-guard \
  --android-device-report tmp/android-device-qa-current/android-device-qa-report.md \
  --iphone-qa-report tmp/iphone-web-qa/iphone-safari-qa-report.md \
  --iphone-local-preflight tmp/web-teavm-iphone-release-preflight/summary.json \
  --iphone-package-dir dist/web-teavm

scripts/smoke-portal-prelaunch-visual.sh --portal-url https://voidscape.gg/ --skip-signup --out tmp/prelaunch-portal-live-visual

scripts/smoke-portal-prelaunch-visual.sh \
  --portal-url https://voidscape.gg/ \
  --skip-signup \
  --out tmp/prelaunch-portal-live-link-clicks

scripts/smoke-portal-prelaunch-visual.sh \
  --portal-url https://voidscape.gg/ \
  --skip-signup \
  --login-email staging-qa094648@voidscape.gg \
  --login-password-file tmp/live-qa-credential-v1/password.txt \
  --out tmp/prelaunch-portal-live-account-manager-login \
  --ignore-https-errors

scripts/smoke-launcher-prelaunch.sh \
  --jar tmp/prelaunch-client-qa/launch-staging-package-v2/launcher/VoidscapeLauncher-staging.jar \
  --use-packaged-config \
  --host voidscape.gg \
  --port 43596 \
  --portal-url https://voidscape.gg \
  --out tmp/launcher-prelaunch

scripts/smoke-pc-client-prelaunch.sh \
  --user <qa-user> \
  --pass <qa-pass> \
  --out tmp/pc-client-prelaunch

scripts/package-launch-staging.sh \
  --host voidscape.gg \
  --portal-url https://voidscape.gg/ \
  --web-url https://voidscape.gg/play/ \
  --ws-url wss://voidscape.gg/play/ws/ \
  --skip-build \
  --skip-web-build \
  --skip-android \
  --allow-dirty-staging \
  --output-dir tmp/launch-staging-package

scripts/build-prelaunch-visual-board.mjs \
  --out tmp/prelaunch-visual-board

scripts/run-android-device-qa.sh \
  --apk <release-apk-or-url> \
  --out tmp/android-device-qa-current

scripts/validate-android-device-qa-report.py \
  tmp/android-device-qa-current/android-device-qa-report.md

scripts/run-web-teavm-iphone-qa.sh \
  --base-url https://voidscape.gg/play/ \
  --ws wss://voidscape.gg/play/ws/ \
  --portal https://voidscape.gg/ \
  --deployment-summary tmp/launch-staging-live-signup-websmoke-v1/web-deployment/summary.json \
  --out tmp/iphone-web-qa

scripts/check-web-teavm-iphone-final-release.py \
  --qa-report tmp/iphone-web-qa/iphone-safari-qa-report.md \
  --local-preflight tmp/web-teavm-iphone-release-preflight/summary.json \
  --package-dir dist/web-teavm
```

The skipped-build launch package above is deliberately rehearsal-only. Its
`MANIFEST.txt` must report `promotable=false` with reused-build blockers; it is
useful for launcher/layout QA, not release-ready evidence.

The aggregate runner refuses to start when the output filesystem has less than
512 MiB free, because launch bundles and screenshots can fail halfway on a full
disk. Clean superseded `tmp/prelaunch-*` artifacts first, while keeping the
latest evidence paths listed above.

Current Android public-channel decision is to show the APK in the post-launch chooser when the APK artifact exists. For production promotion, prefer upload signing, physical Android QA, and `--android-release` in the package command.

## Remaining Gates

- Run physical Android QA on at least one real phone before broad public promotion of the APK, with extra eyes on portrait gameplay framing, settings-panel spacing, touch targets, and keyboard return. Archive a passing `scripts/validate-android-device-qa-report.py` result.
- Run physical iPhone Safari and Home Screen QA; Simulator proof is not enough for keyboard, address-bar, and PWA behavior. Archive a passing `scripts/check-web-teavm-iphone-final-release.py` result for the exact uploaded package.
- For final signoff after device QA, rerun `scripts/check-prelaunch-readiness.sh` with `--android-device-report` and `--iphone-qa-report` so the physical gates appear as pass/fail rows in the main readiness report.
- After syncing the final bundle to the host and restarting services, run `VERIFY-STAGING.sh` from that exact bundle.
- If any later bundle is synced after this handoff, refill/archive that bundle's generated `RELEASE-HANDOFF.md` with the new backup paths and source-disclosure/AGPL confirmation.
