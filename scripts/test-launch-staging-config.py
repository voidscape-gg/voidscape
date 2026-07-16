#!/usr/bin/env python3
"""Focused regression tests for the launch config/package safety contract."""

from __future__ import annotations

import importlib.util
import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR_PATH = ROOT / "scripts" / "generate-launch-server-config.py"
sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("launch_config_generator", GENERATOR_PATH)
assert SPEC and SPEC.loader
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


REVIEWED_VOIDSCAPE_PROFILE = {
    "aggro_range": "4",
    "avatar_generator": "true",
    "batch_progression": "true",
    "combat_exp_rate": "10",
    "custom_landscape": "true",
    "db_name": "voidscape",
    "enforce_custom_client_version": "true",
    "experience_drops_toggle": "true",
    "fog_toggle": "true",
    "game_tick": "640",
    "idle_timer": "600000",
    "idle_timer_subscriber": "900000",
    "inventory_count_toggle": "true",
    "launch_subscription_card_until": "2026-07-19T18:00:00Z",
    "melee_gives_xp_hit": "true",
    "member_world": "true",
    "milliseconds_between_casts": "1900",
    "more_shafts_per_better_log": "true",
    "perf_telemetry": "true",
    "perf_telemetry_interval_seconds": "30",
    "perf_telemetry_window_ticks": "512",
    "production_command_lockdown": "true",
    "ranged_gives_xp_hit": "true",
    "restrict_item_id": "9999",
    "right_click_bank": "true",
    "right_click_trade": "true",
    "server_name": "Voidscape",
    "server_name_welcome": "Voidscape",
    "show_roof_toggle": "true",
    "skilling_exp_rate": "1.5",
    "spawn_auction_npcs": "true",
    "void_arena_allow_ambiguous_proxy_ranked": "false",
    "want_bank_notes": "true",
    "want_bank_presets": "true",
    "want_beta_onboarding_guide": "false",
    "want_cert_as_notes": "true",
    "want_custom_banks": "false",
    "want_custom_ui": "true",
    "want_drop_x": "true",
    "want_email": "false",
    "want_fatigue": "false",
    "want_feature_websockets": "true",
    "want_global_chat": "true",
    "want_global_chat_country_flags": "true",
    "want_global_friend": "false",
    "want_keyboard_shortcuts": "2",
    "want_leftclick_webs": "true",
    "want_packet_register": "true",
    "want_pcap_logging": "false",
    "want_void_colossus": "false",
    "want_void_dungeon": "false",
    "want_void_enclave": "true",
    "want_world_announcements": "true",
    "want_world_milestone_announcements": "true",
    "want_world_new_player_announcements": "true",
    "want_world_skulled_pk_announcements": "true",
    "welcome_text": "Welcome to Voidscape.",
    "wilderness_npc_blocking": "0",
    "wilderness_spawn_multiplier": "1.5",
}


class LaunchStagingConfigTest(unittest.TestCase):
    def test_contract_freezes_reviewed_voidscape_profile(self):
        contract = GENERATOR.load_contract()
        self.assertEqual(REVIEWED_VOIDSCAPE_PROFILE, contract)

    def test_preservation_base_renders_exact_launch_contract(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "local.launch-staging.conf"
            expected = GENERATOR.render_config(
                ROOT / "server" / "preservation.conf",
                target,
                "10138",
                "43596",
                "43496",
            )
            text = target.read_text(encoding="utf-8")
            for key, value in expected.items():
                matches = list(GENERATOR.key_pattern(key).finditer(text))
                self.assertEqual(1, len(matches), key)
                self.assertEqual(value, matches[0].group("value").strip(), key)

    def test_duplicate_release_key_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "duplicate.conf"
            source.write_text("db_name: one\ndb_name: two\n", encoding="utf-8")
            with self.assertRaisesRegex(GENERATOR.LaunchConfigError, "duplicate.*db_name"):
                GENERATOR.render_config(
                    source,
                    directory / "out.conf",
                    "10138",
                    "43596",
                    "43496",
                )

    def test_sensitive_base_preset_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "secret.conf"
            source.write_text(
                "db_name: preservation\n"
                "discord_global_chat_webhook_url: https://example.invalid/private\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                GENERATOR.LaunchConfigError, "sensitive setting.*webhook_url"
            ):
                GENERATOR.render_config(
                    source,
                    directory / "out.conf",
                    "10138",
                    "43596",
                    "43496",
                )

    def test_hosted_verifier_checks_configs_without_network_access(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            server_config = directory / "local.conf"
            connections_config = directory / "connections.conf"
            GENERATOR.render_config(
                ROOT / "server" / "preservation.conf",
                server_config,
                "10138",
                "43596",
                "43496",
            )
            connections_config.write_text("db_type: sqlite\n", encoding="utf-8")

            output = directory / "pass"
            command = [
                "node",
                "scripts/verify-launch-staging.mjs",
                "--server-config",
                str(server_config),
                "--connections-config",
                str(connections_config),
                "--expected-client-version",
                "10138",
                "--server-config-only",
                "--out",
                str(output),
            ]
            passed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(0, passed.returncode, passed.stdout + passed.stderr)
            summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
            self.assertTrue(summary["serverConfigOnly"])
            self.assertEqual([], summary["failures"])

            server_config.write_text(
                server_config.read_text(encoding="utf-8") + "\ndb_name: other\n",
                encoding="utf-8",
            )
            failed_output = directory / "duplicate"
            command[-1] = str(failed_output)
            failed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(1, failed.returncode, failed.stdout + failed.stderr)
            failed_summary = json.loads(
                (failed_output / "summary.json").read_text(encoding="utf-8")
            )
            duplicate_check = next(
                check
                for check in failed_summary["checks"]
                if check["name"] == "server config db_name unique"
            )
            self.assertEqual("fail", duplicate_check["status"])

    def test_bundle_database_paths_and_verifier_are_fail_closed(self):
        package = (ROOT / "scripts" / "package-launch-staging.sh").read_text(
            encoding="utf-8"
        )
        verifier = (ROOT / "scripts" / "verify-launch-staging.mjs").read_text(
            encoding="utf-8"
        )
        database = "/opt/voidscape/server/inc/sqlite/voidscape.db"
        self.assertGreaterEqual(package.count(database), 4)
        self.assertNotIn("/opt/voidscape/server/inc/sqlite/preservation.db", package)
        self.assertIn("VOIDSCAPE_DEPLOYED_SERVER_CONFIG", package)
        self.assertIn("VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG", package)
        self.assertIn("VOIDSCAPE_VERIFY_RUN_SIGNUP", package)
        self.assertIn(
            "install -d -o voidscape -g voidscape -m 0750 /opt/voidscape/server/avatars",
            package,
        )
        self.assertIn("SIGNUP_MODE=(--skip-signup)", package)
        self.assertNotIn("VOIDSCAPE_REPO_ROOT", package)
        self.assertIn(
            'verify_exact_sha256_tree "\\$BUNDLE_DIR/scripts" "\\$BUNDLE_DIR/scripts.SHA256"',
            package,
        )
        self.assertIn('"\\$BUNDLE_DIR/scripts/verify-launch-staging.mjs"', package)
        self.assertNotIn("VOIDSCAPE_SKIP_DEPLOYED_BYTE_CHECK", package)
        self.assertIn("\\`VOIDSCAPE_VERIFY_RUN_SIGNUP=1\\`", package)
        self.assertIn("dev-server.mjs --initialize-store", package)
        self.assertIn("runuser -u voidscape -- env PORTAL_DATA_DIR", package)
        self.assertIn("portal-state-prelaunch-\\$stamp.json", package)
        self.assertIn("state-pair-prelaunch-\\$stamp.sha256", package)
        self.assertIn("sqlite3 /opt/voidscape/server/inc/sqlite/voidscape.db", package)
        self.assertNotIn(
            "install -m 600 /opt/voidscape/server/inc/sqlite/voidscape.db",
            package,
        )
        self.assertIn('assertCheck("portal store ready"', verifier)
        self.assertNotIn(
            '--server-config "\\$BUNDLE_DIR/server/local.launch-staging.conf"',
            package,
        )

    def test_portal_public_boundary_packaging_and_handoff_are_fail_closed(self):
        package = (ROOT / "scripts" / "package-launch-staging.sh").read_text(
            encoding="utf-8"
        )
        verifier = (ROOT / "scripts" / "verify-launch-staging.mjs").read_text(
            encoding="utf-8"
        )

        self.assertIn('python3 "$ROOT/scripts/package-portal-runtime.py"', package)
        self.assertIn(
            'location ~ "^/(?!\\.well-known(?:/|$))(?:.*/)?\\."', package
        )
        self.assertIn('location ~* "^/[^/]+\\.mjs$"', package)
        self.assertIn('location ~* "^/[^/]+\\.md$"', package)
        snippet = package.split(
            'cat > "$OUTPUT_DIR/ops/nginx/'
            'voidscape-admin-public-block.location.conf"',
            1,
        )[1].split("\nEOF", 1)[0]
        nginx_regexes = re.findall(
            r'location ~\*? "([^"]+)" \{ return 404; \}', snippet
        )
        self.assertNotIn(
            "location = /api/launcher/manifest.properties", snippet
        )
        self.assertNotIn("location ^~ /api/launcher/", snippet)
        self.assertNotIn("location /api/launcher/", snippet)
        self.assertTrue(
            any(re.match(pattern, "/dev-server.mjs") for pattern in nginx_regexes)
        )
        self.assertTrue(
            any(re.match(pattern, "/commerce-smoke.mjs") for pattern in nginx_regexes)
        )
        for public_path in (
            "/api/launcher/manifest.properties",
            "/styles.css",
            "/landing.js",
            "/assets/loot-editor-data.json",
        ):
            self.assertFalse(
                any(re.match(pattern, public_path) for pattern in nginx_regexes),
                public_path,
            )
        self.assertGreaterEqual(package.count('--public-origin "https://'), 3)
        self.assertGreaterEqual(
            package.count("install -o root -g voidscape -m 0600"), 2
        )
        self.assertGreaterEqual(
            package.count(
                "include /etc/nginx/snippets/"
                "voidscape-admin-public-block.location.conf;"
            ),
            3,
        )
        self.assertGreaterEqual(
            package.count("nginx -t && systemctl reload nginx"), 3
        )
        self.assertIn(
            "const adminPublicInputs = [args.portalUrl.href, ...args.adminPublicUrls];",
            verifier,
        )
        self.assertGreaterEqual(
            verifier.count("for (const publicBase of args.adminPublicUrls)"), 2
        )
        self.assertIn("requestStatusOnly(path, publicBase)", verifier)
        self.assertIn('case "--static-boundary-only":', verifier)


if __name__ == "__main__":
    unittest.main(verbosity=2)
