#!/usr/bin/env python3
"""Post a Discord message as the VoidBot application.

This script is intentionally stdlib-only and never prints the bot token. It is
the canonical way for local agents/operators to post release notes or bug-feed
fix summaries without using a personal Discord browser session.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request


API_BASE = "https://discord.com/api/v10"
GUILD_ID = "1499807816553205831"

CHANNELS = {
    "bug-feed": "1515901664773804093",
    "announcements": "1499811862529970327",
}

TOKEN_ENV_NAMES = (
    "VOIDBOT_DISCORD_TOKEN",
    "PORTAL_DISCORD_BOT_TOKEN",
    "DISCORD_BOT_TOKEN",
)

KEYCHAIN_SERVICES = (
    "voidscape-voidbot-discord-token",
    "voidscape-discord-gate",
    "VoidBot",
)


def read_content(args: argparse.Namespace) -> str:
    sources = [bool(args.content), bool(args.file), args.stdin]
    if sum(1 for source in sources if source) != 1:
        raise SystemExit("Provide exactly one of --content, --file, or --stdin.")
    if args.content:
        return args.content
    if args.file:
        with open(args.file, "r", encoding="utf-8") as handle:
            return handle.read()
    return sys.stdin.read()


def token_from_keychain(service: str, account: str | None) -> str | None:
    command = ["security", "find-generic-password"]
    if account:
        command += ["-a", account]
    command += ["-s", service, "-w"]
    try:
        result = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0:
        return None
    token = result.stdout.strip()
    return token or None


def load_env_files(paths: list[str] | None) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in paths or []:
        try:
            with open(path, "r", encoding="utf-8") as handle:
                lines = handle.readlines()
        except FileNotFoundError:
            raise SystemExit(f"Env file not found: {path}")
        for raw_line in lines:
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[len("export "):].strip()
            if "=" not in line:
                continue
            name, value = line.split("=", 1)
            name = name.strip()
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
                value = value[1:-1]
            if name:
                values[name] = value
    return values


def load_token(args: argparse.Namespace) -> tuple[str, str]:
    env_file_values = load_env_files(args.env_file)

    for name in TOKEN_ENV_NAMES:
        token = os.environ.get(name)
        if token:
            return token, f"env:{name}"
        token = env_file_values.get(name)
        if token:
            return token, f"env-file:{name}"

    services = args.keychain_service or list(KEYCHAIN_SERVICES)
    account = args.keychain_account
    for service in services:
        token = token_from_keychain(service, account)
        if token:
            return token, f"keychain:{service}"

    raise SystemExit(
        "Missing VoidBot token. Set VOIDBOT_DISCORD_TOKEN, PORTAL_DISCORD_BOT_TOKEN, "
        "or DISCORD_BOT_TOKEN, or store it in Keychain service "
        "'voidscape-voidbot-discord-token'."
    )


def channel_id_for(value: str) -> str:
    return CHANNELS.get(value, value)


def post_message(token: str, channel_id: str, content: str, allow_mentions: bool) -> dict:
    return send_message_request(token, "POST", f"/channels/{channel_id}/messages", content, allow_mentions)


def edit_message(token: str, channel_id: str, message_id: str, content: str, allow_mentions: bool) -> dict:
    return send_message_request(
        token,
        "PATCH",
        f"/channels/{channel_id}/messages/{message_id}",
        content,
        allow_mentions,
    )


def send_message_request(token: str, method: str, route: str, content: str, allow_mentions: bool) -> dict:
    payload = {
        "content": content,
        "allowed_mentions": {"parse": ["users", "roles", "everyone"] if allow_mentions else []},
    }
    request = urllib.request.Request(
        f"{API_BASE}{route}",
        data=json.dumps(payload).encode("utf-8"),
        method=method,
        headers={
            "Authorization": f"Bot {token}",
            "Content-Type": "application/json",
            "User-Agent": "VoidscapeVoidBotPoster/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")
        raise RuntimeError(f"Discord HTTP {exc.code}: {detail}") from exc


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Post a Discord message as VoidBot. Use --dry-run first; actual sends require --yes."
    )
    parser.add_argument("--channel", default="bug-feed", help="Named channel or raw Discord channel id.")
    parser.add_argument("--content", help="Message content.")
    parser.add_argument("--file", help="Read message content from this UTF-8 file.")
    parser.add_argument("--stdin", action="store_true", help="Read message content from stdin.")
    parser.add_argument("--dry-run", action="store_true", help="Print the planned request without posting.")
    parser.add_argument("--yes", action="store_true", help="Actually send the message.")
    parser.add_argument("--edit-message-id", help="Edit an existing bot-authored message instead of posting a new one.")
    parser.add_argument("--allow-mentions", action="store_true", help="Allow Discord mentions to parse.")
    parser.add_argument(
        "--env-file",
        action="append",
        help="Read token environment variables from this KEY=VALUE file. Can be repeated.",
    )
    parser.add_argument(
        "--keychain-service",
        action="append",
        help="Keychain service to try for the bot token. Can be repeated.",
    )
    parser.add_argument("--keychain-account", help="Optional Keychain account filter.")
    args = parser.parse_args()

    content = read_content(args).strip()
    if not content:
        raise SystemExit("Message content is empty.")
    if len(content) > 2000:
        raise SystemExit(f"Discord message content is {len(content)} chars; max is 2000.")

    channel_id = channel_id_for(args.channel)
    preview = {
        "channel": args.channel,
        "channelId": channel_id,
        "guildUrl": f"https://discord.com/channels/{GUILD_ID}/{channel_id}",
        "mentionsAllowed": bool(args.allow_mentions),
        "editMessageId": args.edit_message_id,
        "content": content,
    }

    if args.dry_run:
        print(json.dumps(preview, indent=2))
        return 0
    if not args.yes:
        print(json.dumps(preview, indent=2))
        raise SystemExit("Refusing to post without --yes. Use --dry-run to preview.")

    token, source = load_token(args)
    if args.edit_message_id:
        message = edit_message(token, channel_id, args.edit_message_id, content, args.allow_mentions)
    else:
        message = post_message(token, channel_id, content, args.allow_mentions)
    print(json.dumps({
        "ok": True,
        "action": "edit" if args.edit_message_id else "post",
        "channelId": channel_id,
        "messageId": message.get("id"),
        "tokenSource": source,
        "url": f"https://discord.com/channels/{GUILD_ID}/{channel_id}/{message.get('id')}",
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
