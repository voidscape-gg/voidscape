#!/usr/bin/env bash
# Visual smoke for the prelaunch portal and account-management flow.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORTAL_URL="${PORTAL_VISUAL_URL:-http://127.0.0.1:8788/}"
OUT_DIR="${PORTAL_VISUAL_OUT:-$ROOT/tmp/portal-prelaunch-visual-smoke}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
SIGNUP_USERNAME="${PORTAL_VISUAL_USERNAME:-}"
SIGNUP_EMAIL="${PORTAL_VISUAL_EMAIL:-}"
SIGNUP_PASSWORD="${PORTAL_VISUAL_PASSWORD:-Visualpass1}"
LOGIN_EMAIL="${PORTAL_VISUAL_LOGIN_EMAIL:-}"
LOGIN_PASSWORD="${PORTAL_VISUAL_LOGIN_PASSWORD:-}"
LOGIN_PASSWORD_FILE="${PORTAL_VISUAL_LOGIN_PASSWORD_FILE:-}"
SKIP_SIGNUP=0
EXPECT_LAUNCH_OPEN=0
IGNORE_HTTPS_ERRORS=0

usage() {
	cat <<'EOF'
Usage: scripts/smoke-portal-prelaunch-visual.sh [options]

Options:
  --portal-url URL        Portal URL. Default: http://127.0.0.1:8788/
  --out DIR               Output directory. Default: tmp/portal-prelaunch-visual-smoke.
  --username NAME         Signup username. Default: generated Vis* name.
  --email EMAIL           Signup email. Default: visual+<name>@voidscape.gg.
  --password PASSWORD     Signup/game password. Default: Visualpass1.
  --login-email EMAIL     Sign in to an existing portal account instead of signing up.
  --login-password PASS   Existing portal password. Prefer --login-password-file for live QA.
  --login-password-file FILE
                           Read an existing portal password from FILE.
  --skip-signup           Screenshot/assert the landing only, without creating an account.
  --expect-launch-open    Require the countdown takeover/open-launch surface.
  --chrome PATH           Chrome/Chromium executable path.
  --playwright-core DIR   Directory for playwright or playwright-core package.
  --ignore-https-errors   Ignore HTTPS certificate errors.
  -h, --help              Show this help.

The script captures desktop and mobile screenshots, checks mobile horizontal
overflow, optionally creates a real launch-signup account, and verifies the
dashboard, character roster, subscription, security, and signed-in mobile nav.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--portal-url)
			PORTAL_URL="${2:-}"
			shift 2
			;;
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--username)
			SIGNUP_USERNAME="${2:-}"
			shift 2
			;;
		--email)
			SIGNUP_EMAIL="${2:-}"
			shift 2
			;;
		--password)
			SIGNUP_PASSWORD="${2:-}"
			shift 2
			;;
		--login-email)
			LOGIN_EMAIL="${2:-}"
			shift 2
			;;
		--login-password)
			LOGIN_PASSWORD="${2:-}"
			shift 2
			;;
		--login-password-file)
			LOGIN_PASSWORD_FILE="${2:-}"
			shift 2
			;;
		--skip-signup)
			SKIP_SIGNUP=1
			shift
			;;
		--expect-launch-open)
			EXPECT_LAUNCH_OPEN=1
			shift
			;;
		--chrome)
			CHROME_PATH="${2:-}"
			shift 2
			;;
		--playwright-core)
			PLAYWRIGHT_CORE_DIR="${2:-}"
			shift 2
			;;
		--ignore-https-errors)
			IGNORE_HTTPS_ERRORS=1
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "Unknown option: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

if [[ -z "$PLAYWRIGHT_CORE_DIR" ]]; then
	for candidate in \
		"$ROOT/node_modules/playwright-core" \
		"$ROOT/node_modules/playwright" \
		"/tmp/voidscape-playwright-smoke/node_modules/playwright-core" \
		"/tmp/voidscape-playwright-smoke/node_modules/playwright" \
		"/Users/s/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core" \
		"/Users/s/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright"; do
		if [[ -d "$candidate" ]]; then
			PLAYWRIGHT_CORE_DIR="$candidate"
			break
		fi
	done
fi

if [[ -z "$PLAYWRIGHT_CORE_DIR" || ! -d "$PLAYWRIGHT_CORE_DIR" ]]; then
	cat >&2 <<'EOF'
ERROR: playwright/playwright-core was not found.

Install it outside the repo or point PLAYWRIGHT_CORE_DIR at an existing package:
  mkdir -p /tmp/voidscape-playwright-smoke
  cd /tmp/voidscape-playwright-smoke
  npm init -y
  npm install playwright-core
EOF
	exit 1
fi

if [[ -z "$CHROME_PATH" ]]; then
	for candidate in \
		"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
		"/Applications/Chromium.app/Contents/MacOS/Chromium" \
		"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"; do
		if [[ -x "$candidate" ]]; then
			CHROME_PATH="$candidate"
			break
		fi
	done
fi

if [[ -z "$CHROME_PATH" || ! -x "$CHROME_PATH" ]]; then
	echo "ERROR: Chrome/Chromium executable not found. Pass --chrome PATH or set CHROME_PATH." >&2
	exit 1
fi

if [[ -n "$LOGIN_PASSWORD_FILE" ]]; then
	if [[ ! -f "$LOGIN_PASSWORD_FILE" ]]; then
		echo "ERROR: --login-password-file does not exist: $LOGIN_PASSWORD_FILE" >&2
		exit 1
	fi
	LOGIN_PASSWORD="$(tr -d '\r\n' < "$LOGIN_PASSWORD_FILE")"
fi

if [[ -n "$LOGIN_EMAIL" && -z "$LOGIN_PASSWORD" ]]; then
	echo "ERROR: --login-email requires --login-password-file or --login-password." >&2
	exit 1
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

if [[ -z "$SIGNUP_USERNAME" ]]; then
	SIGNUP_USERNAME="Vis$(date +%s | tail -c 9)"
	SIGNUP_USERNAME="${SIGNUP_USERNAME:0:12}"
fi
if [[ -z "$SIGNUP_EMAIL" ]]; then
	SIGNUP_EMAIL="visual-$(printf '%s' "$SIGNUP_USERNAME" | tr '[:upper:]' '[:lower:]')@voidscape.gg"
fi

export PORTAL_URL OUT_DIR CHROME_PATH PLAYWRIGHT_CORE_DIR
export SIGNUP_USERNAME SIGNUP_EMAIL SIGNUP_PASSWORD SKIP_SIGNUP EXPECT_LAUNCH_OPEN IGNORE_HTTPS_ERRORS
export LOGIN_EMAIL LOGIN_PASSWORD

node <<'NODE'
const fs = require("fs");
const path = require("path");
const { chromium } = require(process.env.PLAYWRIGHT_CORE_DIR);

const outDir = process.env.OUT_DIR;
const portalUrl = normalizeBaseUrl(process.env.PORTAL_URL);
const signup = {
	username: process.env.SIGNUP_USERNAME,
	email: process.env.SIGNUP_EMAIL,
	password: process.env.SIGNUP_PASSWORD
};
const login = {
	email: process.env.LOGIN_EMAIL || "",
	password: process.env.LOGIN_PASSWORD || ""
};
const secondaryCharacter = {
	username: process.env.PORTAL_VISUAL_CHARACTER || secondaryCharacterName(signup.username),
	password: process.env.PORTAL_VISUAL_CHARACTER_PASSWORD || signup.password
};
const skipSignup = process.env.SKIP_SIGNUP === "1";
const loginOnly = Boolean(login.email);
const accountFlowActive = !skipSignup || loginOnly;
const expectLaunchOpen = process.env.EXPECT_LAUNCH_OPEN === "1";
const ignoreHTTPSErrors = process.env.IGNORE_HTTPS_ERRORS === "1";

const checks = [];
const screenshots = [];
const failures = [];

function record(status, name, detail = "") {
	checks.push({ status, name, detail });
	if (status === "fail") failures.push({ name, detail });
}

function assertCheck(name, condition, detail = "") {
	record(condition ? "pass" : "fail", name, detail);
}

async function screenshot(page, name, options = {}) {
	const file = path.join(outDir, `${name}.png`);
	await page.screenshot({ path: file, fullPage: Boolean(options.fullPage) });
	screenshots.push({ name, file });
	return file;
}

async function gotoPortal(page) {
	await page.goto(portalUrl, { waitUntil: "domcontentloaded", timeout: 30000 });
	await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
	await page.waitForFunction(() => {
		const message = document.querySelector("#founder-message");
		const countdown = document.querySelector("#beta-countdown-days");
		return document.body.classList.contains("public-mode")
			|| (message && message.textContent && message.textContent.trim().length > 0)
			|| (countdown && countdown.textContent !== "--");
	}, null, { timeout: 12000 }).catch(() => {});
	await page.waitForTimeout(500);
}

async function assertNoHorizontalOverflow(page, label) {
	const metrics = await page.evaluate(() => {
		const doc = document.documentElement;
		const overflow = [];
		for (const element of Array.from(document.body.querySelectorAll("*"))) {
			const style = window.getComputedStyle(element);
			if (style.position === "fixed" && style.pointerEvents === "none") continue;
			const rect = element.getBoundingClientRect();
			if (!rect.width || !rect.height) continue;
			if (rect.right > window.innerWidth + 2 || rect.left < -2) {
				overflow.push({
					tag: element.tagName.toLowerCase(),
					id: element.id || "",
					className: String(element.className || "").slice(0, 120),
					left: Math.round(rect.left),
					right: Math.round(rect.right),
					width: Math.round(rect.width)
				});
			}
			if (overflow.length >= 8) break;
		}
		return {
			innerWidth: window.innerWidth,
			scrollWidth: doc.scrollWidth,
			overflow
		};
	});
	assertCheck(
		`${label} has no horizontal page overflow`,
		metrics.scrollWidth <= metrics.innerWidth + 2,
		JSON.stringify(metrics)
	);
}

async function assertPortalWiring(page, label) {
	const wiring = await page.evaluate(() => {
		const accountView = document.querySelector("#account");
		const textFor = (element) => String(element.textContent || element.getAttribute("aria-label") || "")
			.replace(/\s+/g, " ")
			.trim()
			.slice(0, 100);
		const rowsFor = (selector, attr) => Array.from(document.querySelectorAll(selector)).map((element) => {
			const id = element.getAttribute(attr) || "";
			const target = id ? document.getElementById(id) : null;
			const elementStyle = window.getComputedStyle(element);
			const elementRect = element.getBoundingClientRect();
			const targetStyle = target ? window.getComputedStyle(target) : null;
			const targetRect = target ? target.getBoundingClientRect() : null;
			return {
				id,
				label: textFor(element),
				tag: element.tagName.toLowerCase(),
				targetExists: Boolean(target),
				inLanding: Boolean(target && accountView && accountView.contains(target)),
				isView: Boolean(target && target.classList && target.classList.contains("view")),
				sourceVisible: Boolean(elementRect.width && elementRect.height && elementStyle.display !== "none" && elementStyle.visibility !== "hidden"),
				targetHasBox: Boolean(targetRect && targetRect.width && targetRect.height && targetStyle.display !== "none" && targetStyle.visibility !== "hidden")
			};
		});
		const landingScrollTargets = rowsFor("[data-landing-scroll]", "data-landing-scroll");
		const whitepaperTargets = rowsFor("[data-whitepaper-jump]", "data-whitepaper-jump");
		const viewTargets = rowsFor("[data-view-link]", "data-view-link");
		const links = Array.from(document.querySelectorAll("a[href]")).map((link) => ({
			href: link.getAttribute("href") || "",
			text: textFor(link)
		}));
		return {
			brokenLandingScrollTargets: landingScrollTargets.filter((row) => !row.targetExists || !row.inLanding || (row.sourceVisible && !row.targetHasBox)),
			brokenWhitepaperTargets: whitepaperTargets.filter((row) => !row.targetExists || !row.inLanding),
			brokenViewTargets: viewTargets.filter((row) => !row.targetExists || (!row.isView && !row.inLanding)),
			policyLinks: links.filter((row) => ["/privacy", "/data-deletion", "/transparency"].includes(row.href)),
			sourceLinks: links.filter((row) => /github\.com\/voidscape-gg\/voidscape/i.test(row.href))
		};
	});
	assertCheck(`${label} landing button targets exist`, wiring.brokenLandingScrollTargets.length === 0, JSON.stringify(wiring.brokenLandingScrollTargets));
	assertCheck(`${label} whitepaper button targets exist`, wiring.brokenWhitepaperTargets.length === 0, JSON.stringify(wiring.brokenWhitepaperTargets));
	assertCheck(`${label} account view targets exist`, wiring.brokenViewTargets.length === 0, JSON.stringify(wiring.brokenViewTargets));
	assertCheck(`${label} policy links are present`, wiring.policyLinks.length >= 3, JSON.stringify(wiring.policyLinks));
	assertCheck(`${label} AGPL source link is present`, wiring.sourceLinks.length >= 1, JSON.stringify(wiring.sourceLinks));
}

async function assertPolicyPagesReachable(context, label) {
	for (const pagePath of ["/privacy", "/data-deletion", "/transparency"]) {
		const target = new URL(pagePath, portalUrl).href;
		try {
			const response = await context.request.get(target, { timeout: 15000, failOnStatusCode: false });
			assertCheck(`${label} ${pagePath} returns OK`, response.status() >= 200 && response.status() < 400, `${target} -> ${response.status()}`);
		} catch (error) {
			record("fail", `${label} ${pagePath} returns OK`, error && error.message ? error.message : String(error));
		}
	}
}

async function clickFirstVisible(page, selector, label) {
	const locator = page.locator(selector);
	const count = await locator.count();
	for (let index = 0; index < count; index += 1) {
		const candidate = locator.nth(index);
		if (!(await candidate.isVisible().catch(() => false))) continue;
		await candidate.scrollIntoViewIfNeeded().catch(() => {});
		await candidate.click();
		return true;
	}
	record("fail", `${label} clickable`, `No visible element matched ${selector}`);
	return false;
}

async function clickLandingTarget(page, selector, label, expectedHash) {
	if (!(await clickFirstVisible(page, selector, label))) return;
	await page.waitForFunction((hash) => window.location.hash === hash, `#${expectedHash}`, { timeout: 2500 }).catch(() => {});
	await page.waitForFunction((id) => {
		const target = document.getElementById(id);
		if (!target) return false;
		const rect = target.getBoundingClientRect();
		return rect.height > 0 && rect.bottom > 0 && rect.top < window.innerHeight;
	}, expectedHash, { timeout: 3500 }).catch(() => {});
	await page.waitForTimeout(700);
	const state = await page.evaluate((id) => {
		const target = document.getElementById(id);
		const rect = target ? target.getBoundingClientRect() : null;
		return {
			hash: window.location.hash,
			targetExists: Boolean(target),
			top: rect ? Math.round(rect.top) : null,
			bottom: rect ? Math.round(rect.bottom) : null,
			height: rect ? Math.round(rect.height) : null,
			visibleInViewport: Boolean(rect && rect.height > 0 && rect.bottom > 0 && rect.top < window.innerHeight),
			viewportHeight: window.innerHeight
		};
	}, expectedHash);
	assertCheck(`${label} reaches visible #${expectedHash}`, state.hash === `#${expectedHash}` && state.visibleInViewport, JSON.stringify(state));
}

async function assertSignInCta(page) {
	if (!(await clickFirstVisible(page, "[data-prelaunch-auth-cta]", "sign in CTA"))) return;
	await page.waitForFunction(() => window.location.hash === "#founder-form", null, { timeout: 2500 }).catch(() => {});
	const state = await page.evaluate(() => ({
		hash: window.location.hash,
		title: document.querySelector("#founder-title")?.textContent?.trim() || "",
		signInTabActive: Boolean(document.querySelector('[data-account-mode="signin"].is-active')),
		authTabsHidden: Boolean(document.querySelector(".prelaunch-auth-tabs")?.hidden),
		nameRowHidden: Boolean(document.querySelector("#founder-name-row")?.hidden),
		emailFocused: document.activeElement && document.activeElement.id === "founder-email"
	}));
	assertCheck("sign in CTA reaches account form", state.hash === "#founder-form", JSON.stringify(state));
	if (!state.authTabsHidden) {
		assertCheck("sign in CTA switches auth mode", /sign in/i.test(state.title) && state.signInTabActive && state.nameRowHidden, JSON.stringify(state));
	}
}

async function runLandingClickMap(page, context) {
	await assertPortalWiring(page, "desktop");
	await assertPolicyPagesReachable(context, "desktop");
	await clickLandingTarget(page, "#landing-hero-primary-cta", "hero primary CTA", "founder-form");
	await clickLandingTarget(page, '.landing-nav-links [data-landing-scroll="gameplay"]', "top Features CTA", "gameplay");
	await clickLandingTarget(page, '.landing-final [data-landing-scroll="gameplay"]', "final Features CTA", "gameplay");
	await clickLandingTarget(page, '.landing-footer [data-landing-scroll="gameplay"]', "footer Features CTA", "gameplay");
	await clickLandingTarget(page, '.landing-footer [data-landing-scroll="home"]', "footer Home CTA", "home");
	await assertSignInCta(page);
	await screenshot(page, "02-desktop-click-map");
}

async function assertLaunchOpen(page, label) {
	const state = await page.evaluate(() => ({
		launchOpen: document.body.classList.contains("launch-open"),
		hero: document.querySelector("#landing-hero-title")?.textContent?.trim() || "",
		countdownLabel: document.querySelector("#beta-countdown-label")?.textContent?.trim() || "",
		platformTitle: document.querySelector("#landing-platform-title")?.textContent?.trim() || "",
		launchProofHidden: document.querySelector("#landing-launch-proof")?.hidden,
		downloadsHidden: document.querySelector("#download-actions")?.hidden,
		downloads: Array.from(document.querySelectorAll("#download-actions .download-action")).map((action) => ({
			tag: action.tagName.toLowerCase(),
			text: String(action.textContent || "").replace(/\s+/g, " ").trim(),
			href: action.getAttribute("href") || "",
			disabled: Boolean(action.disabled)
		}))
	}));
	assertCheck(`${label} launch-open class`, state.launchOpen === true, JSON.stringify(state));
	assertCheck(`${label} hero says open`, /open/i.test(state.hero), JSON.stringify(state));
	assertCheck(`${label} countdown status label`, /Launch status/i.test(state.countdownLabel), JSON.stringify(state));
	assertCheck(`${label} client chooser copy`, /Choose your client/i.test(state.platformTitle), JSON.stringify(state));
	assertCheck(`${label} launch proof visible`, state.launchProofHidden === false, JSON.stringify(state));
	assertCheck(`${label} download actions visible`, state.downloadsHidden === false && state.downloads.length > 0, JSON.stringify(state));
	assertCheck(`${label} web client action links to /play`, state.downloads.some((row) => row.tag === "a" && /\/play\/?/.test(row.href)), JSON.stringify(state.downloads));
	assertCheck(`${label} public Android action is not a debug APK`, state.downloads.every((row) => !/android/i.test(row.text) || !/debug/i.test(`${row.text} ${row.href}`)), JSON.stringify(state.downloads));
}

async function activateView(page, view) {
	await page.evaluate((id) => {
		const links = Array.from(document.querySelectorAll(`[data-view-link="${id}"]`));
		const visible = links.find((link) => {
			const rect = link.getBoundingClientRect();
			return rect.width > 0 && rect.height > 0;
		});
		(visible || links[0])?.click();
	}, view);
	await page.waitForTimeout(500);
}

async function assertActiveView(page, expected, label) {
	const state = await page.evaluate((id) => {
		const active = document.querySelector(".view.is-active");
		return {
			activeId: active?.id || "",
			hash: window.location.hash,
			title: document.querySelector("#view-title")?.textContent?.trim() || "",
			expected: id
		};
	}, expected);
	assertCheck(label, state.activeId === expected || state.hash === `#${expected}`, JSON.stringify(state));
}

async function assertDashboardQuickActions(page) {
	await activateView(page, "dashboard");
	await clickFirstVisible(page, '.account-quick-grid [data-view-link="characters"]', "dashboard Characters quick action");
	await page.waitForTimeout(400);
	await assertActiveView(page, "characters", "dashboard Characters quick action opens Characters");
	await activateView(page, "dashboard");
	await clickFirstVisible(page, '.account-quick-grid [data-view-link="subscription"]', "dashboard Subscription quick action");
	await page.waitForTimeout(400);
	await assertActiveView(page, "subscription", "dashboard Subscription quick action opens Subscription");
	await activateView(page, "dashboard");
	await clickFirstVisible(page, '.account-quick-grid [data-view-link="security"]', "dashboard Security quick action");
	await page.waitForTimeout(400);
	await assertActiveView(page, "security", "dashboard Security quick action opens Security");
	await activateView(page, "dashboard");
	await clickLandingTarget(page, '.account-quick-grid [data-landing-scroll="gameplay"]', "dashboard Features quick action", "gameplay");
}

async function createAdditionalCharacter(page) {
	await activateView(page, "characters");
	await page.locator("#character-name").fill(secondaryCharacter.username);
	await page.locator("#character-password").fill(secondaryCharacter.password);
	const responsePromise = page.waitForResponse((response) => response.url().includes("/api/characters") && response.request().method() === "POST", { timeout: 15000 }).catch(() => null);
	await page.locator("#queue-character").click();
	const response = await responsePromise;
	if (response) {
		assertCheck("additional character API status", response.status() === 201, `HTTP ${response.status()}`);
	}
	await page.waitForFunction((name) => {
		const cards = Array.from(document.querySelectorAll("#character-cards .character-card")).map((card) => card.textContent || "").join("\n");
		const message = document.querySelector("#character-message")?.textContent || "";
		return cards.includes(name) && /created|added/i.test(message);
	}, secondaryCharacter.username, { timeout: 15000 });
	await screenshot(page, "05b-desktop-character-created");
	const characters = await page.evaluate((name) => ({
		title: document.querySelector("#roster-title")?.textContent?.trim() || "",
		message: document.querySelector("#character-message")?.textContent?.trim() || "",
		cards: Array.from(document.querySelectorAll("#character-cards .character-card")).map((card) => card.textContent || "").join("\n"),
		cardCount: document.querySelectorAll("#character-cards .character-card").length,
		name
	}), secondaryCharacter.username);
	assertCheck("additional character roster count", /2 characters/i.test(characters.title) || characters.cardCount >= 2, JSON.stringify(characters));
	assertCheck("additional character card visible", characters.cards.includes(secondaryCharacter.username), JSON.stringify(characters));
	assertCheck("additional character created message", /created|added/i.test(characters.message), JSON.stringify(characters));
	await activateView(page, "dashboard");
	await page.waitForFunction(() => /2\s*\/\s*10/.test(document.querySelector("#dashboard-character-count")?.textContent || ""), null, { timeout: 8000 });
	const dashboard = await page.evaluate(() => ({
		characters: document.querySelector("#dashboard-character-count")?.textContent?.trim() || "",
		card: document.querySelector("#dashboard-card-state")?.textContent?.trim() || ""
	}));
	assertCheck("dashboard character count after additional creation", /2\s*\/\s*10/.test(dashboard.characters), JSON.stringify(dashboard));
	assertCheck("starter card still waiting after additional creation", /card|waiting|reserved/i.test(dashboard.card), JSON.stringify(dashboard));
}

async function runSignupFlow(page) {
	await page.locator("#founder-name").fill(signup.username);
	await page.locator("#founder-email").fill(signup.email);
	await page.locator("#founder-password").fill(signup.password);
	await Promise.all([
		page.waitForResponse((response) => response.url().includes("/api/accounts/register") && response.request().method() === "POST", { timeout: 15000 }).catch(() => null),
		page.locator("#founder-submit").click()
	]);
	await page.locator("#prelaunch-success:not([hidden])").waitFor({ timeout: 15000 });
	await screenshot(page, "03-desktop-signup-success");
	const state = await page.evaluate(() => ({
		account: document.querySelector("#prelaunch-success-account")?.textContent?.trim() || "",
		name: document.querySelector("#prelaunch-success-name")?.textContent?.trim() || "",
		status: document.querySelector("#prelaunch-success-status")?.textContent?.trim() || "",
		code: document.querySelector("#prelaunch-success-code")?.textContent?.trim() || "",
		message: document.querySelector("#founder-message")?.textContent?.trim() || ""
	}));
	assertCheck("signup success email shown", state.account === signup.email, JSON.stringify(state));
	assertCheck("signup success character shown", state.name === signup.username, JSON.stringify(state));
	assertCheck("signup success uses one slot", /1\s*\/\s*10/.test(state.status), JSON.stringify(state));
	assertCheck("launch-signup hides bearer code", state.code === "-" || state.code === "", JSON.stringify(state));

	await activateView(page, "dashboard");
	await page.waitForFunction((email) => document.querySelector("#dashboard-account-email")?.textContent?.includes(email), signup.email, { timeout: 12000 });
	await screenshot(page, "04-desktop-dashboard");
	const dashboard = await page.evaluate(() => ({
		email: document.querySelector("#dashboard-account-email")?.textContent?.trim() || "",
		characters: document.querySelector("#dashboard-character-count")?.textContent?.trim() || "",
		card: document.querySelector("#dashboard-card-state")?.textContent?.trim() || "",
		security: document.querySelector("#dashboard-security-state")?.textContent?.trim() || ""
	}));
	assertCheck("dashboard email", dashboard.email === signup.email, JSON.stringify(dashboard));
	assertCheck("dashboard character count", /1\s*\/\s*10/.test(dashboard.characters), JSON.stringify(dashboard));
	assertCheck("dashboard starter card visible", /card|waiting|reserved/i.test(dashboard.card), JSON.stringify(dashboard));
	assertCheck("dashboard security state loaded", !/not signed in/i.test(dashboard.security), JSON.stringify(dashboard));

	await assertDashboardQuickActions(page);

	await activateView(page, "characters");
	await page.waitForTimeout(1000);
	await screenshot(page, "05-desktop-characters");
	const characters = await page.evaluate(() => ({
		title: document.querySelector("#roster-title")?.textContent?.trim() || "",
		cards: Array.from(document.querySelectorAll("#character-cards .character-card")).map((card) => card.textContent || "").join("\n")
	}));
	assertCheck("characters view has one roster card", /1 character/i.test(characters.title) || characters.cards.includes(signup.username), JSON.stringify(characters));
	assertCheck("characters view includes signup name", characters.cards.includes(signup.username), JSON.stringify(characters));

	await createAdditionalCharacter(page);

	await activateView(page, "subscription");
	await page.waitForTimeout(500);
	await screenshot(page, "06-desktop-subscription");
	const subscription = await page.evaluate(() => ({
		status: document.querySelector("#sub-status")?.textContent?.trim() || "",
		reward: document.querySelector("#founder-reward-count")?.textContent?.trim() || "",
		state: document.querySelector("#redeem-state")?.textContent?.trim() || ""
	}));
	assertCheck("subscription starter card visible", /card|waiting|reserved/i.test(`${subscription.reward} ${subscription.state}`), JSON.stringify(subscription));

	await activateView(page, "security");
	await page.waitForTimeout(500);
	await screenshot(page, "07-desktop-security");
	const security = await page.evaluate(() => ({
		score: document.querySelector("#security-score")?.textContent?.trim() || "",
		message: document.querySelector("#security-message")?.textContent?.trim() || "",
		recoverySupport: document.querySelector("#recovery-support-note")?.textContent?.trim() || "",
		recoverySupportHref: document.querySelector("#recovery-support-note a")?.getAttribute("href") || "",
		emailChecked: document.querySelector("#security-email-check")?.checked,
		passwordChecked: document.querySelector("#security-password-check")?.checked
	}));
	assertCheck("security score visible", /^\d+/.test(security.score), JSON.stringify(security));
	assertCheck("security password state visible", security.passwordChecked === true, JSON.stringify(security));
	assertCheck("security recovery fallback visible", /lost your recovery codes/i.test(security.recoverySupport) && /character names/i.test(security.recoverySupport), JSON.stringify(security));
	assertCheck("security recovery fallback mailto", security.recoverySupportHref === "mailto:support@voidscape.gg", JSON.stringify(security));
}

async function runLoginAccountFlow(page) {
	await activateView(page, "dashboard");
	await page.waitForTimeout(400);
	await page.locator('[data-portal-auth-mode="login"]').click();
	await page.locator("#portal-auth-email").fill(login.email);
	await page.locator("#portal-auth-password").fill(login.password);
	const responsePromise = page.waitForResponse((response) => response.url().includes("/api/accounts/login") && response.request().method() === "POST", { timeout: 15000 }).catch(() => null);
	await page.locator("#portal-auth-submit").click();
	const response = await responsePromise;
	if (response) {
		assertCheck("existing account login API status", response.status() === 200, `HTTP ${response.status()}`);
	}
	await page.waitForFunction((email) => document.querySelector("#dashboard-account-email")?.textContent?.includes(email), login.email, { timeout: 15000 });
	await screenshot(page, "03-desktop-existing-login-dashboard");
	const dashboard = await page.evaluate(() => ({
		email: document.querySelector("#dashboard-account-email")?.textContent?.trim() || "",
		characters: document.querySelector("#dashboard-character-count")?.textContent?.trim() || "",
		card: document.querySelector("#dashboard-card-state")?.textContent?.trim() || "",
		security: document.querySelector("#dashboard-security-state")?.textContent?.trim() || ""
	}));
	assertCheck("existing account dashboard email", dashboard.email === login.email, JSON.stringify(dashboard));
	assertCheck("existing account character count loaded", /\d+\s*\/\s*10/.test(dashboard.characters), JSON.stringify(dashboard));
	assertCheck("existing account starter card visible", /card|waiting|reserved|claimed|used/i.test(dashboard.card), JSON.stringify(dashboard));
	assertCheck("existing account security state loaded", !/not signed in/i.test(dashboard.security), JSON.stringify(dashboard));

	await assertDashboardQuickActions(page);

	await activateView(page, "characters");
	await page.waitForTimeout(1000);
	await screenshot(page, "04-desktop-existing-characters");
	const characters = await page.evaluate(() => ({
		title: document.querySelector("#roster-title")?.textContent?.trim() || "",
		cardCount: document.querySelectorAll("#character-cards .character-card").length,
		cards: Array.from(document.querySelectorAll("#character-cards .character-card")).map((card) => card.textContent || "").join("\n")
	}));
	assertCheck("existing account roster loaded", characters.cardCount >= 1 || /\d+ character/i.test(characters.title), JSON.stringify(characters));

	await activateView(page, "subscription");
	await page.waitForTimeout(500);
	await screenshot(page, "05-desktop-existing-subscription");
	const subscription = await page.evaluate(() => ({
		reward: document.querySelector("#founder-reward-count")?.textContent?.trim() || "",
		state: document.querySelector("#redeem-state")?.textContent?.trim() || ""
	}));
	assertCheck("existing account subscription state loaded", /card|waiting|reserved|claimed|used|no subscription/i.test(`${subscription.reward} ${subscription.state}`), JSON.stringify(subscription));

	await activateView(page, "security");
	await page.waitForTimeout(500);
	await screenshot(page, "06-desktop-existing-security");
	const security = await page.evaluate(() => ({
		score: document.querySelector("#security-score")?.textContent?.trim() || "",
		message: document.querySelector("#security-message")?.textContent?.trim() || "",
		recoverySupport: document.querySelector("#recovery-support-note")?.textContent?.trim() || "",
		recoverySupportHref: document.querySelector("#recovery-support-note a")?.getAttribute("href") || "",
		passwordChecked: document.querySelector("#security-password-check")?.checked
	}));
	assertCheck("existing account security score visible", /^\d+/.test(security.score), JSON.stringify(security));
	assertCheck("existing account password state visible", security.passwordChecked === true, JSON.stringify(security));
	assertCheck("existing account recovery fallback visible", /lost your recovery codes/i.test(security.recoverySupport) && /character names/i.test(security.recoverySupport), JSON.stringify(security));
	assertCheck("existing account recovery fallback mailto", security.recoverySupportHref === "mailto:support@voidscape.gg", JSON.stringify(security));
}

async function runMobilePass(page) {
	await page.setViewportSize({ width: 390, height: 844 });
	await gotoPortal(page);
	await assertPortalWiring(page, "mobile");
	await assertNoHorizontalOverflow(page, "mobile landing");
	await screenshot(page, "08-mobile-landing");
	if (expectLaunchOpen) await assertLaunchOpen(page, "mobile");
	if (accountFlowActive) {
		const authText = await page.locator("[data-prelaunch-auth-cta]").first().textContent().catch(() => "");
		assertCheck("mobile signed-in CTA switches to manage", /manage account/i.test(authText || ""), authText || "");
		await activateView(page, "dashboard");
		await page.waitForTimeout(300);
		await screenshot(page, "09-mobile-dashboard");
		const toggleVisible = await page.locator("#menu-toggle").isVisible().catch(() => false);
		assertCheck("mobile dashboard nav toggle visible", toggleVisible === true, String(toggleVisible));
		if (!toggleVisible) return;
		await page.locator("#menu-toggle").click();
		await page.waitForTimeout(300);
		await screenshot(page, "10-mobile-nav-open");
		const navOpen = await page.evaluate(() => document.querySelector(".portal-shell")?.classList.contains("nav-open"));
		assertCheck("mobile nav opens", navOpen === true, String(navOpen));
	}
}

async function main() {
	const browser = await chromium.launch({
		executablePath: process.env.CHROME_PATH,
		headless: true
	});
	try {
		const context = await browser.newContext({
			viewport: { width: 1440, height: 1000 },
			deviceScaleFactor: 1,
			ignoreHTTPSErrors
		});
		const page = await context.newPage();
		await gotoPortal(page);
		await assertNoHorizontalOverflow(page, "desktop landing");
		await screenshot(page, expectLaunchOpen ? "01-desktop-launch-open" : "01-desktop-landing");
		if (expectLaunchOpen) await assertLaunchOpen(page, "desktop");
		await runLandingClickMap(page, context);
		await gotoPortal(page);
		if (loginOnly) {
			await runLoginAccountFlow(page);
		} else if (!skipSignup) {
			await runSignupFlow(page);
		}
		await runMobilePass(page);
		await context.close();
	} finally {
		await browser.close();
	}
}

function normalizeBaseUrl(value) {
	const url = new URL(value);
	if (!url.pathname.endsWith("/")) url.pathname += "/";
	return url.href;
}

function secondaryCharacterName(primaryName) {
	const suffix = String(primaryName || "")
		.replace(/[^a-z0-9]/gi, "")
		.slice(-8) || String(Date.now()).slice(-8);
	return `Alt${suffix}`.slice(0, 12);
}

main().catch((error) => {
	record("fail", "fatal", error && error.stack ? error.stack : String(error));
}).finally(() => {
	const summary = {
		startedUrl: portalUrl,
		finishedAt: new Date().toISOString(),
		expectLaunchOpen,
		skipSignup,
		login: loginOnly ? { email: login.email } : null,
		signup: skipSignup || loginOnly ? null : {
			username: signup.username,
			email: signup.email,
			additionalCharacter: secondaryCharacter.username
		},
		checks,
		failures,
		screenshots
	};
	fs.writeFileSync(path.join(outDir, "summary.json"), JSON.stringify(summary, null, 2));
	if (failures.length) {
		console.error(`Portal visual smoke failed with ${failures.length} failure(s).`);
		for (const failure of failures) console.error(`- ${failure.name}: ${failure.detail}`);
		process.exit(1);
	}
	console.log("Portal visual smoke passed.");
	console.log(`Artifacts: ${outDir}`);
});
NODE
