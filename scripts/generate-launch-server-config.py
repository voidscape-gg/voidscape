#!/usr/bin/env python3
"""Render the public launch server config from a safe base preset."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = ROOT / "scripts" / "launch-config-contract.json"
CONFIG_ROW = re.compile(
    r"^[ \t]*(?P<key>[A-Za-z0-9_]+)[ \t]*:[ \t]*(?P<value>[^#\r\n]*)",
    re.MULTILINE,
)
SENSITIVE_KEY = re.compile(
    r"(?:^db_pass$|(?:password|secret|token)$|_webhook_url$)",
    re.IGNORECASE,
)


class LaunchConfigError(ValueError):
    pass


def load_contract(path: Path = CONTRACT_PATH) -> dict[str, str]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict) or not raw:
        raise LaunchConfigError(f"launch config contract is not a non-empty object: {path}")
    contract: dict[str, str] = {}
    for key, value in raw.items():
        if not isinstance(key, str) or not re.fullmatch(r"[a-z0-9_]+", key):
            raise LaunchConfigError(f"invalid launch config key: {key!r}")
        if not isinstance(value, str) or not value:
            raise LaunchConfigError(f"invalid launch config value for {key}: {value!r}")
        contract[key] = value
    return contract


def key_pattern(key: str) -> re.Pattern[str]:
    return re.compile(
        rf"^(?P<prefix>[ \t]*{re.escape(key)}[ \t]*:[ \t]*)"
        r"(?P<value>[^#\r\n]*?)(?P<comment>[ \t]*#.*)?$",
        re.MULTILINE,
    )


def reject_embedded_secrets(text: str) -> None:
    for match in CONFIG_ROW.finditer(text):
        key = match.group("key")
        value = match.group("value").strip()
        if SENSITIVE_KEY.search(key) and value.lower() not in {"", "null", "change_me"}:
            raise LaunchConfigError(
                f"refusing to package non-empty sensitive setting {key!r}; "
                "use a secret-free base preset"
            )


def render_config(
    source: Path,
    target: Path,
    client_version: str,
    server_port: str,
    ws_port: str,
    contract_path: Path = CONTRACT_PATH,
) -> dict[str, str]:
    values = load_contract(contract_path)
    values.update(
        {
            "client_version": client_version,
            "server_port": server_port,
            "ws_server_port": ws_port,
        }
    )
    text = source.read_text(encoding="utf-8")
    reject_embedded_secrets(text)

    for key, value in values.items():
        pattern = key_pattern(key)
        matches = list(pattern.finditer(text))
        if len(matches) > 1:
            raise LaunchConfigError(
                f"source preset has duplicate release-critical key {key!r}"
            )
        if matches:
            def replace(match: re.Match[str], replacement: str = value) -> str:
                comment = match.group("comment") or ""
                if comment.startswith("#"):
                    comment = " " + comment
                return f"{match.group('prefix')}{replacement}{comment}"

            text = pattern.sub(replace, text, count=1)
        else:
            if text and not text.endswith("\n"):
                text += "\n"
            text += f"\n\t{key}: {value}\n"

    for key, expected in values.items():
        matches = list(key_pattern(key).finditer(text))
        actual = matches[0].group("value").strip() if len(matches) == 1 else ""
        if len(matches) != 1 or actual != expected:
            raise LaunchConfigError(
                f"generated config violates {key}: expected {expected!r}, "
                f"found {actual!r} in {len(matches)} rows"
            )

    reject_embedded_secrets(text)

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    return values


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--client-version", required=True)
    parser.add_argument("--server-port", required=True)
    parser.add_argument("--ws-port", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        render_config(
            args.source,
            args.target,
            args.client_version,
            args.server_port,
            args.ws_port,
        )
    except (OSError, json.JSONDecodeError, LaunchConfigError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
