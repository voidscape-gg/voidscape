#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
port = (root / "Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java").read_text()
bridge = (root / "Web_Client_TeaVM/src/main/java/orsc/WebCrackerCampaignSmokeBridge.java").read_text()
index = (root / "Web_Client_TeaVM/src/main/webapp/index.html").read_text()
smoke = (root / "scripts/smoke-web-teavm-cracker-campaign.sh").read_text()
config = (root / "Client_Base/src/orsc/Config.java").read_text()

assert "public static final int CLIENT_VERSION = 10132;" in config
for token in (
    "publishCrackerCampaignHudState();",
    "client.workbenchCrackerCampaignRemaining()",
    "client.workbenchCrackerCampaignHudVisible()",
    "client.workbenchCrackerCampaignHudLabel()",
    "crackerCampaignEnvelopeLeakCount()",
    "if (!isWebDiagnosticsEnabled())",
    "window.__voidscapeCrackerCampaignHudState",
    "window.__voidscapeCrackerCampaignSmokeEnvelope",
):
    assert token in port, token

def jsbody_for(method_name):
    method = port.index(method_name)
    start = port.rfind("@JSBody", 0, method)
    assert start >= 0
    return port[start:method]

envelope_gate = jsbody_for("hasWebSmokeCrackerCampaignEnvelope();")
command_gate = jsbody_for("hasWebSmokeCrackerCampaignCommand();")
for gate, label in ((envelope_gate, "envelope"), (command_gate, "command")):
    assert "window.__voidscapeDiagnosticsEnabled" in gate, f"{label} must require diagnostics"
    for host in ("127.0.0.1", "localhost", "::1"):
        assert f"window.location.hostname === '{host}'" in gate, f"{label} must require loopback"
assert "Number.isInteger(value)" in command_gate
assert "value >= 0" in command_gate and "value <= 1000000" in command_gate

publish_start = port.index("private void publishCrackerCampaignHudState()")
publish_end = port.index("private int crackerCampaignEnvelopeLeakCount()", publish_start)
publish = port[publish_start:publish_end]
assert publish.index("if (!isWebDiagnosticsEnabled())") < publish.index("crackerCampaignEnvelopeLeakCount()"), \
    "chat-history scan must remain diagnostics-only"

assert "client.handleVoidscapeCrackerCampaignMessage(envelope)" in bridge
assert "client.sendCommandString(\"cracker \" + remaining)" in bridge
assert "Config.isWeb()" in bridge
assert "crackerCampaignHud: cloneForDiagnostics" in index
assert "crackerCampaignSmokeInjection: cloneForDiagnostics" in index

for token in (
    "--allow-campaign-mutation",
    "clientVersion === 10132",
    "server login snapshot",
    "source: 'login-snapshot'",
    "owner-command-broadcast",
    "observedLocalEnvelopeInjectionSequence",
    "localCompiledParserProof",
    "requestedServerRemaining: 0",
    "best-effort failure cleanup zero",
    "mobile: '1'",
    "isMobile: true",
    "hasTouch: true",
    "iPhone",
    "await page.waitForTimeout(1200)",
    "welcome-${dialogHeight}",
    "!document.body.classList.contains('dialog-open')",
    "async function framePoint(page, x, y)",
    "x * rect.width / canvas.width",
    "y * rect.height / canvas.height",
    "snapshot.canvas && snapshot.canvas.width",
    "snapshot.canvas && snapshot.canvas.height",
    "chatLeakCount === 0",
    "b.x + b.width <= f.width",
):
    assert token in smoke, token

network = smoke.index("// Network proof:")
injection = smoke.index("// Parser proof is explicitly separate")
assert network < smoke.index("const loginSnapshot", network) < injection
assert smoke.index("networkProofs.push", network) < injection
assert smoke.index("networkInjectionBaseline", network) < smoke.index("const loginSnapshot", network)
assert smoke.index("networkInjectionFinal", network) < injection
assert smoke.index("injectCompiledEnvelope(page", injection) > injection
assert "const finalVisible = await sendCrackerCount" not in smoke
assert "const finalZero = await sendCrackerCount" not in smoke
assert "if (page && !serverPoolZeroObserved)" in smoke
assert "failure.json" in smoke
assert "desktop: '1'" not in smoke

print("TeaVM cracker campaign HUD static contract passed.")
PY

bash -n "$ROOT/scripts/smoke-web-teavm-cracker-campaign.sh"
