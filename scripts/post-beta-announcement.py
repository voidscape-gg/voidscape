#!/usr/bin/env python3
"""Post the public beta launch announcement to Discord.

This is intentionally stdlib-only so it can run from a systemd timer on the VPS
without installing Python packages. It is idempotent: once a Discord message is
created, the marker file prevents retry pings.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import socket
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


ANNOUNCEMENTS_CHANNEL_ID = "1499811862529970327"
DEFAULT_MARKER = "/var/lib/voidscape-portal/beta-announcement-posted.json"
DEFAULT_IMAGES = [
    "/opt/voidscape/assets/discord/beta-open-announcement-1920x1080.jpg",
    "/opt/voidscape/assets/discord/beta-rewards-and-testing-1600x900.jpg",
    "/opt/voidscape/assets/discord/beta-test-checklist-1600x900.jpg",
]

CONTENT = """@everyone **BETA IS OPEN.**

Voidscape public beta is live. This is the real release-valid test window: play hard, break things, and stack 1-week subscription cards for release.

**Start here:** https://voidscape.gg
- Download the launcher or Android APK. No Discord login required for downloads.
- Join with Discord to get the beta role and access the tester hub.

**Stack sub cards**
Invite friends. When they make a character, they put your in-game name in **Invited by**.
You earn a real 1-week sub-card reward code. Use `::codes` in game to check your rewards.

**Test everything**
New-player flow, Void Island, `::beta`, Void Arena, Void Knight/Colossus, drops, banks, launcher, Android, weird edges.

**Report bugs**
Use `::bug <what happened>` in game. Add coords with `::coords` when you can.

See you in the Void."""


def wait_for_port(host: str, port: int, timeout_seconds: int) -> None:
    deadline = time.time() + timeout_seconds
    last_error: OSError | None = None
    while time.time() < deadline:
        try:
            with socket.create_connection((host, port), timeout=2):
                return
        except OSError as exc:
            last_error = exc
            time.sleep(2)
    raise RuntimeError(f"Timed out waiting for {host}:{port}: {last_error}")


def multipart_body(payload: dict, image_paths: list[Path]) -> tuple[bytes, str]:
    boundary = f"voidscape-{uuid.uuid4().hex}"
    chunks: list[bytes] = []

    def add_part(headers: list[str], body: bytes) -> None:
        chunks.append(f"--{boundary}\r\n".encode("utf-8"))
        for header in headers:
            chunks.append(f"{header}\r\n".encode("utf-8"))
        chunks.append(b"\r\n")
        chunks.append(body)
        chunks.append(b"\r\n")

    add_part(
        [
            'Content-Disposition: form-data; name="payload_json"',
            "Content-Type: application/json",
        ],
        json.dumps(payload).encode("utf-8"),
    )

    for index, path in enumerate(image_paths):
        content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        add_part(
            [
                f'Content-Disposition: form-data; name="files[{index}]"; filename="{path.name}"',
                f"Content-Type: {content_type}",
            ],
            path.read_bytes(),
        )

    chunks.append(f"--{boundary}--\r\n".encode("utf-8"))
    return b"".join(chunks), boundary


def post_message(token: str, channel_id: str, image_paths: list[Path]) -> dict:
    payload = {
        "content": CONTENT,
        "allowed_mentions": {"parse": ["everyone"]},
        "attachments": [
            {
                "id": index,
                "filename": path.name,
                "description": description,
            }
            for index, (path, description) in enumerate(
                zip(
                    image_paths,
                    [
                        "Voidscape public beta is open launch art.",
                        "Referral reward instructions for stacking subscription cards.",
                        "Current build gameplay checklist for beta testers.",
                    ],
                )
            )
        ],
    }
    body, boundary = multipart_body(payload, image_paths)
    request = urllib.request.Request(
        f"https://discord.com/api/v10/channels/{channel_id}/messages",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bot {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "User-Agent": "VoidscapeBetaAnnouncement/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")
        raise RuntimeError(f"Discord HTTP {exc.code}: {detail}") from exc


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--skip-wait", action="store_true")
    args = parser.parse_args()

    token = os.environ.get("PORTAL_DISCORD_BOT_TOKEN") or os.environ.get("DISCORD_BOT_TOKEN")
    channel_id = os.environ.get("DISCORD_ANNOUNCEMENT_CHANNEL_ID", ANNOUNCEMENTS_CHANNEL_ID)
    marker_path = Path(os.environ.get("VOIDSCAPE_BETA_ANNOUNCEMENT_MARKER", DEFAULT_MARKER))
    wait_host = os.environ.get("VOIDSCAPE_BETA_WAIT_HOST", "127.0.0.1")
    wait_port = int(os.environ.get("VOIDSCAPE_BETA_WAIT_PORT", "43596"))
    wait_timeout = int(os.environ.get("VOIDSCAPE_BETA_WAIT_TIMEOUT_SECONDS", "180"))
    image_paths = [Path(path) for path in os.environ.get("VOIDSCAPE_BETA_ANNOUNCEMENT_IMAGES", ":".join(DEFAULT_IMAGES)).split(":") if path]

    missing_images = [str(path) for path in image_paths if not path.exists()]
    if missing_images:
        raise FileNotFoundError(f"Missing announcement images: {', '.join(missing_images)}")

    if marker_path.exists():
        print(f"Announcement already posted: {marker_path}")
        return 0

    if args.dry_run:
        print(json.dumps({
            "channelId": channel_id,
            "mentionsEveryone": "@everyone" in CONTENT,
            "imagePaths": [str(path) for path in image_paths],
            "content": CONTENT,
        }, indent=2))
        return 0

    if not token:
        raise RuntimeError("Missing PORTAL_DISCORD_BOT_TOKEN or DISCORD_BOT_TOKEN")

    if not args.skip_wait:
        wait_for_port(wait_host, wait_port, wait_timeout)

    message = post_message(token, channel_id, image_paths)
    marker_path.parent.mkdir(parents=True, exist_ok=True)
    marker_path.write_text(json.dumps({
        "messageId": message.get("id"),
        "channelId": channel_id,
        "postedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }, indent=2) + "\n", encoding="utf-8")
    print(f"Posted beta announcement message {message.get('id')} to channel {channel_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
