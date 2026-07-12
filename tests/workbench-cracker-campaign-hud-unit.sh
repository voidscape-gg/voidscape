#!/usr/bin/env bash
# Source contract for the Slice 4C Cracker campaign HUD Workbench proof.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

python3 - "$REPO" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1])
workbench = (root / "PC_Client/src/orsc/WorkbenchServer.java").read_text()
mudclient = (root / "Client_Base/src/orsc/mudclient.java").read_text()

for route in (
    'createContext("/fixture/cracker-campaign-hud"',
    'createContext("/scenario/cracker-campaign-hud"',
):
    assert route in workbench, route

for method in (
    "workbenchSetCrackerCampaignRemaining",
    "workbenchCrackerCampaignRemaining",
    "workbenchCrackerCampaignHudVisible",
    "workbenchCrackerCampaignHudLabel",
    "workbenchCrackerCampaignHudX",
    "workbenchCrackerCampaignHudY",
    "workbenchCrackerCampaignHudWidth",
    "workbenchCrackerCampaignHudHeight",
    "workbenchCrackerCampaignTopTabsX",
    "workbenchCrackerCampaignTopTabsY",
    "workbenchCrackerCampaignTopTabsWidth",
    "workbenchCrackerCampaignTopTabsHeight",
    "workbenchCrackerCampaignLocationX",
    "workbenchCrackerCampaignLocationY",
    "workbenchCrackerCampaignLocationWidth",
    "workbenchCrackerCampaignLocationHeight",
    "workbenchCrackerCampaignKillFeedBaseY",
):
    assert method in workbench, f"Workbench does not consume {method}"
    assert f"public " in mudclient[mudclient.index(method) - 40:mudclient.index(method)], \
        f"mudclient Workbench API is not public: {method}"

fixture_start = workbench.index("private static String crackerCampaignHudFixtureJson")
fixture_end = workbench.index("private static String crackerCampaignHudScenarioJson", fixture_start)
fixture = workbench[fixture_start:fixture_end]
for fragment in (
    "requireLoggedIn();",
    'requiredInt(fields, "remaining")',
    "remaining < 0 || remaining > 1_000_000",
    '"true".equalsIgnoreCase(fields.get("capture"))',
    "prepareCrackerCampaignHudProof(client)",
    "setCrackerCampaignHudRemaining(client, remaining)",
    "validateCrackerCampaignHudSnapshot",
):
    assert fragment in fixture, f"fixture contract missing: {fragment}"

scenario_start = workbench.index("private static String crackerCampaignHudScenarioJson")
scenario_end = workbench.index("private static void prepareCrackerCampaignHudProof", scenario_start)
scenario = workbench[scenario_start:scenario_end]
for fragment in (
    "final int originalViewportIndex = ScaledWindow.getViewportPresetIndex()",
    "final int originalRemaining = client.workbenchCrackerCampaignRemaining()",
    "final int[] viewportIndexes = {4, 5}",
    "final int[][] captureSizes = {{1024, 768}, {512, 346}}",
    "final int[][] gameSizes = {{1024, 756}, {512, 334}}",
    "final int[] remainingValues = {1000, 999, 1, 0}",
    "captureOnce(\"scenario-cracker-campaign-hud-\"",
    "captures.size() != 8 || proofs.size() != 8",
    "finally {",
    "cleanupCrackerCampaignHudScenario(",
    "originalViewportIndex, originalRemaining,",
    "originalGameWidth, originalGameHeight",
):
    assert fragment in scenario, f"scenario contract missing: {fragment}"

state_start = workbench.index("private static void appendCrackerCampaignHud")
state_end = workbench.index("private static void appendSubscriptionShop", state_start)
state = workbench[state_start:state_end]
for field in (
    "crackerCampaignHud",
    "remaining",
    "visible",
    "label",
    "bounds",
    "topTabs",
    "locationPlaque",
    "killFeedBaseY",
):
    assert field in state, f"structured state missing {field}"

validation_start = workbench.index("private static void validateCrackerCampaignHudSnapshot")
validation_end = workbench.index("private static String expectedCrackerCampaignHudLabel", validation_start)
validation = workbench[validation_start:validation_end]
for fragment in (
    "expectedLabel.equals(snapshot.label)",
    "snapshot.visible != expectedVisible",
    "snapshot.bounds.inFrame(frameWidth, frameHeight)",
    "snapshot.bounds.overlaps(snapshot.topTabs)",
    "snapshot.bounds.overlaps(snapshot.locationPlaque)",
    "snapshot.killFeedBaseY <= snapshot.bounds.bottom()",
):
    assert fragment in validation, f"geometry/grammar assertion missing: {fragment}"

label_start = workbench.index("private static String expectedCrackerCampaignHudLabel")
label_end = workbench.index("private static void appendCrackerCampaignHudProof", label_start)
label = workbench[label_start:label_end]
assert 'if (remaining <= 0) return "";' in label
assert '"%,d cracker%s available"' in label
assert 'remaining == 1 ? "" : "s"' in label

assert "interfaces" in workbench and "appendCrackerCampaignHud(json, client)" in workbench
assert "android" not in scenario.lower(), "desktop Workbench scenario must not claim Android proof"
print("Cracker campaign HUD Workbench source contract passed.")
PY
