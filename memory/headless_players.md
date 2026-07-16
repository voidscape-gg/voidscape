---
name: Headless players
description: Ordinary-account fleet lifecycle, credential, routing, and deployment invariants
type: project
---

# Headless players

- The fleet is ten ordinary registered accounts from `tools/headless_players/roster.json`; there is no synthetic flag, special database ID, server-side actor service, protocol marker, or alternate social handler.
- A fresh account's first live session must receive the stock join announcement, submit appearance, choose `Skip the intro`, choose `Forager's Path`, and walk from Lumbridge. Never proof-login it during provisioning.
- The controller never chats or accepts/reciprocates trade or duel requests. Other players still use normal visibility, follow, friend, message, trade, and duel request pathways.
- After a successful gather, the controller persists a closed rotation gate, sends a one-tile ordinary walk to cancel the old batch, excludes the successful node, and requires a fresh matching route response plus a real coordinate change before another object action. Rejected/stalled routes retry through closed-gate backoff. Woodcutting, Mining, and Fishing recheck interruption after their opening delay and before any reward. Failed gathers do not advance rotation.
- Ch0p stages at Falador bank and mines Rimmington. Ultraz instead sells the starter sword, sails Port Sarim to Karamja, gathers food, makes the protected shielded crossing, sails to Ardougne, then walks to Catherby.
- Provisioning is deliberately SQLite-only and fails closed before registration for another database type, an unavailable canonical password helper, duplicate/online/mismatched rows, stale receipts, or unsafe credential permissions.
- Production uses ten static `voidscape-headless-<profile>` system users, one hardened `voidscape-headless@.service` per account, the shared controller service, and `voidscape-headless.target`; retain `LimitCORE=0`.
- `/etc/voidscape/headless-players` is root-owned mode 0700 with mode-0600 password and receipt files. Back it up only as an encrypted off-host artifact paired with the corresponding account database backup; never package plaintext secrets.
- For an isolated private world, root first verifies the real world config and mirrors only `right_click_bank`, `want_fatigue`, and `want_bank_notes` into root:`voidscape` mode-0640 `/etc/voidscape/headless-runtime.conf`. Keep the `/etc/voidscape` parent root:`voidscape` mode 0710 so services can traverse the known path without listing the directory; the credential subtree stays root:root 0700 and unrelated secrets stay 0600.
- systemd `LoadCredential=` appears to the service as a protected read-only `0440` file under `CREDENTIALS_DIRECTORY`. Voidbot accepts that exact read-only systemd view while continuing to reject ordinary group-readable password files.
- A valid paired backup is taken only after stopping the target and passing `check-sqlite-roster-offline`; its decrypted member list must be exactly one password and one receipt for every roster profile (20 files total).
- Local first-login observation uses `scripts/headless-demo.sh`: a pinned disposable SQLite copy on loopback `43606`/`43506`, isolated fleet state and controls, an online `wbtest` gate, and a persistent one-shot marker. Run `reset` for another replay; never login a fleet account or open the pinned seed directly.
- The authoritative runbook is `docs/subsystems/headless-players.md`; deployment and restore procedures are in `docs/OPERATIONS.md`.
