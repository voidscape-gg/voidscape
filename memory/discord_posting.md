---
name: Discord posting
description: Canonical VoidBot posting path for Discord updates
type: reference
---

Use `scripts/post-voidbot-discord.py` for Discord messages that must come from VoidBot, including `#bug-feed` fix summaries. Do not post these through the user's Chrome/Discord account.

Default bug-feed flow:

```bash
scripts/post-voidbot-discord.py --channel bug-feed --dry-run --stdin < /tmp/fixes.txt
scripts/post-voidbot-discord.py --channel bug-feed --yes --stdin < /tmp/fixes.txt
```

The script reads the bot token from `VOIDBOT_DISCORD_TOKEN`, `PORTAL_DISCORD_BOT_TOKEN`, `DISCORD_BOT_TOKEN`, or macOS Keychain service `voidscape-voidbot-discord-token`. Mentions are disabled unless `--allow-mentions` is explicitly passed.

Use `--edit-message-id <message-id>` to correct an existing VoidBot-authored post in place instead of creating duplicate fix-list messages.

On the live host, use the bug-triage service env file instead of personal Chrome/Discord:

```bash
/opt/voidscape/scripts/post-voidbot-discord.py --channel bug-feed --env-file /etc/voidscape/discord-bug-triage.env --dry-run --stdin < /tmp/fixes.txt
/opt/voidscape/scripts/post-voidbot-discord.py --channel bug-feed --env-file /etc/voidscape/discord-bug-triage.env --yes --stdin < /tmp/fixes.txt
```
