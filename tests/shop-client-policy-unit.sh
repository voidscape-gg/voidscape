#!/usr/bin/env bash
# Static cross-client guardrails for the launch storefront entry point.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT="$ROOT/Client_Base/src/orsc/mudclient.java"
PC_CONFIG="$ROOT/PC_Client/src/orsc/osConfig.java"
ANDROID_CONFIG="$ROOT/Android_Client/Open RSC Android Client/src/main/java/orsc/osConfig.java"
WEB_CONFIG="$ROOT/Web_Client_TeaVM/src/main/java/orsc/osConfig.java"
WEB_INDEX="$ROOT/Web_Client_TeaVM/src/main/webapp/index.html"
WEB_PORT="$ROOT/Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java"
PC_PORT="$ROOT/PC_Client/src/orsc/ORSCApplet.java"

rg -Fq 'VOIDSCAPE_PORTAL_SHOP_URL = "https://voidscape.gg/portal#subscription-buy"' "$PC_CONFIG"
rg -Fq 'VOIDSCAPE_PORTAL_SHOP_URL = "voidscape:shop"' "$WEB_CONFIG"
rg -Fq 'VOIDSCAPE_PORTAL_SHOP_URL = ""' "$ANDROID_CONFIG"

# Native Android keeps its keyboard action and has no in-app purchase target.
rg -Fq 'return index == VOIDSCAPE_CHAT_TAB_COUNT - 1 && isNativeAndroidClient();' "$CLIENT"
rg -Fq '"Report a player", false' "$CLIENT"

# Desktop/web open an in-canvas catalog from SHOP. Only its explicit Buy action
# may cross the secure-checkout boundary; the shared client embeds no URL.
rg -Fq 'openVoidscapeSubscriptionShop();' "$CLIENT"
rg -Fq 'drawVoidscapeSubscriptionShop();' "$CLIENT"
rg -Fq 'voidscapeSubscriptionShopHit(voidscapeSubscriptionShopBuyRect(), x, y)' "$CLIENT"
rg -Fq 'openVoidscapeSubscriptionShopCheckout();' "$CLIENT"
rg -Fq 'if (this.voidscapeSubscriptionShopOpen) return "subscriptionShop";' "$CLIENT"
rg -Fq 'if (!this.showDialogChristmasCracker && !this.voidscapeSubscriptionShopOpen) {' "$CLIENT"
rg -Fq 'if (voidscapeSubscriptionShopBlockingSurfaceOpen()) {' "$CLIENT"
rg -Fq 'if (voidscapeSubscriptionShopCoveredByHigherModal()) {' "$CLIENT"
rg -Fq '&& voidscapeSubscriptionShopBlockingSurfaceOpen()) {' "$CLIENT"
rg -Fq '&& client.consumeVoidscapeSubscriptionShopPointerAt(client.mouseX, client.mouseY,' "$WEB_PORT"
rg -Fq 'resetVoidscapeProfileSnapshot();' "$CLIENT"
rg -Fq 'return Utils.openWebpage(url.trim());' "$PC_PORT"
if [[ "$(rg -Fc 'openConfiguredUrl(osConfig.VOIDSCAPE_PORTAL_SHOP_URL)' "$CLIENT")" -ne 1 ]]; then
	echo "Only the subscription modal Buy action may open checkout" >&2
	exit 1
fi
if rg -q 'https?://[^" ]+.*subscription' "$CLIENT"; then
	echo "Shared client must not embed a storefront URL" >&2
	exit 1
fi

# The TeaVM bridge binds SHOP to the same-origin portal path, independent of
# account/recovery query overrides, and Report remains prominent in Options.
rg -Fq "var parsed = new URL('/portal', window.location.origin);" "$WEB_INDEX"
rg -Fq "parsed.hash = 'subscription-buy';" "$WEB_INDEX"
rg -Fq "text === 'voidscape:shop'" "$WEB_INDEX"
rg -Fq 'Report Abuse: @red@Open' "$CLIENT"

echo "Shop client policy checks passed."
