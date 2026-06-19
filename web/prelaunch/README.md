# Voidscape Founder Pass Prototype

Static one-page founder-pass prototype for reserving a username and showing the 2-referral free launch subscription offer.

Open `index.html` directly in a browser, or serve the folder with any static server:

```bash
cd web/prelaunch
python3 -m http.server 8787
```

Then open `http://127.0.0.1:8787/`.

## Current Scope

- Single-screen Voidscape prelaunch gate.
- Username/email form behind the reservation button.
- Founder invite link and 0/2 referral progress state.
- Local prototype persistence through `localStorage`.
- No backend, no real email verification, and no production name lock.

## Production Plan

Before this collects real signups, replace the local browser state in `script.js` with an API-backed registration system. The planned architecture, anti-abuse model, and go-live flow are documented in `docs/PRELAUNCH-FOUNDER-PASS.md`.
