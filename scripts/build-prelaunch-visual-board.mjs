#!/usr/bin/env node
import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import process from "node:process";
import { pathToFileURL } from "node:url";

const root = path.resolve(new URL("..", import.meta.url).pathname);
const require = createRequire(import.meta.url);

const args = process.argv.slice(2);
let outDir = path.join(root, "tmp/prelaunch-visual-board");
let chromePath = process.env.CHROME_PATH || process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || "";
let playwrightDir = process.env.PLAYWRIGHT_CORE_DIR || "";
let skipOverviewScreenshots = process.env.VOIDSCAPE_SKIP_VISUAL_BOARD_REVIEW_SHOTS === "1";

for (let i = 0; i < args.length; i += 1) {
  const arg = args[i];
  if (arg === "--out") {
    outDir = path.resolve(args[i + 1] || "");
    i += 1;
  } else if (arg === "--chrome") {
    chromePath = args[i + 1] || "";
    i += 1;
  } else if (arg === "--playwright-core") {
    playwrightDir = args[i + 1] || "";
    i += 1;
  } else if (arg === "--skip-overview-screenshots") {
    skipOverviewScreenshots = true;
  } else if (arg === "-h" || arg === "--help") {
    console.log(`Usage: scripts/build-prelaunch-visual-board.mjs [--out DIR]

Generates a local HTML gallery from the latest prelaunch screenshot artifacts.
The report links images in place; it does not copy or mutate smoke output.

Options:
  --out DIR                    Output directory.
  --chrome PATH                Chrome/Chromium executable for overview PNGs.
  --playwright-core DIR        Directory for playwright or playwright-core.
  --skip-overview-screenshots  Do not render board-review PNGs.`);
    process.exit(0);
  } else {
    console.error(`Unknown argument: ${arg}`);
    process.exit(2);
  }
}

const fromRoot = (...parts) => path.join(root, ...parts);

const globalReviewFocus = [
  "Start with portal/account on desktop and mobile: CTA placement, signup form visibility, account manager, character roster, starter-card state, and security panel.",
  "Compare client entry points: browser /play, desktop launcher, desktop Java, iPhone Simulator, and Android APK should all point players toward the portal-first account flow.",
  "Treat Android and iPhone physical devices as manual release gates. The screenshots here are emulator/Simulator evidence, not permission to publish direct APK links.",
  "After any final hosted file sync or service restart, run VERIFY-STAGING.sh from that exact synced bundle before player-facing links go live.",
];

const manualGates = [
  "Confirm Android APK channel. Current direction is APK visible in the post-launch chooser when the APK artifact exists.",
  "Run physical Android QA on a real phone, especially portrait gameplay framing, settings-panel spacing, touch targets, and keyboard return.",
  "Run physical iPhone Safari and Home Screen QA. Simulator screenshots do not prove keyboard, browser chrome, or PWA behavior.",
  "Run the exact synced bundle's VERIFY-STAGING.sh after final host sync/restart.",
  "If a later bundle is deployed, refill and archive that bundle's RELEASE-HANDOFF.md with backup paths and source-disclosure confirmation.",
];

const sections = [
  {
    title: "Portal and Account Funnel",
    intent: "Landing page, signup success, account manager, character roster, starter card state, security, and mobile navigation.",
    summary: "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/summary.json",
    reviewFocus: [
      "Reserve, Sign In, and Features CTAs are visible and not competing with each other.",
      "Live click-map smoke proves visible landing targets for Reserve, Features, footer Home, policy links, and the AGPL source link.",
      "Pre-sync live smoke caught stale footer controls targeting a missing Features section and a hidden Whitepaper section; the hosted footer fix is now deployed.",
      "Signup success leads naturally into account management with the first linked character visible.",
      "Live authenticated account-manager proof uses the existing disposable QA account without creating a new account, character, starter card, or recovery codes.",
      "Dashboard quick actions and the character manager create-flow are now screenshot-proven, including a second game login and 2 / 10 account slot state.",
      "Starter-card waiting state is readable and matches the public prelaunch promise.",
      "Mobile landing and account nav have no horizontal overflow and keep the signup path obvious.",
    ],
    tiles: [
      ["Live desktop landing", "tmp/prelaunch-portal-live-source-link-v1/01-desktop-landing.png", "Current public landing and prelaunch signup position."],
      ["Live mobile landing", "tmp/prelaunch-portal-live-source-link-v1/08-mobile-landing.png", "Current public mobile layout and no horizontal overflow."],
      ["Live CTA click map", "tmp/prelaunch-portal-live-link-clicks-v2/02-desktop-click-map.png", "Live proof that visible landing CTAs and footer links reach real visible sections."],
      ["Signup success", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/03-desktop-signup-success.png", "Launch-signup account and first character creation success state."],
      ["Dashboard", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/04-desktop-dashboard.png", "Account overview, slots, starter-card promise, and quick-action starting point."],
      ["Characters", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/05-desktop-characters.png", "Roster and first linked OpenRSC character."],
      ["Second character created", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/05b-desktop-character-created.png", "Character manager creates an additional game login and shows 2 account characters."],
      ["Subscription", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/06-desktop-subscription.png", "Free starter subscription card waiting state after account-manager actions."],
      ["Security", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/07-desktop-security.png", "Recovery-code/password security surface plus the support fallback for lost recovery codes."],
      ["Mobile account", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/09-mobile-dashboard.png", "Signed-in account view on mobile width."],
      ["Mobile nav", "tmp/prelaunch-portal-account-manager-recovery-fallback-v1/smoke/10-mobile-nav-open.png", "Mobile account navigation drawer."],
      ["Live account dashboard", "tmp/prelaunch-portal-live-account-manager-login-v1/03-desktop-existing-login-dashboard.png", "Non-mutating live proof that an existing launch-signup account can sign in and see its slot/card state."],
      ["Live account security", "tmp/prelaunch-portal-live-account-manager-login-v1/06-desktop-existing-security.png", "Non-mutating live proof of the security panel and recovery-code support fallback."],
      ["Live mobile account nav", "tmp/prelaunch-portal-live-account-manager-login-v1/10-mobile-nav-open.png", "Non-mutating live proof that signed-in mobile account navigation opens cleanly."],
    ],
  },
  {
    title: "Countdown Takeover",
    intent: "The previous landing page state after the launch countdown expires.",
    summary: "tmp/prelaunch-portal-open-visual-v2/smoke/summary.json",
    reviewFocus: [
      "Post-countdown copy says the world is open and no stale reserve-only state remains.",
      "Play, download, and account-management CTAs are visible without burying the next section.",
    ],
    tiles: [
      ["Launch-open desktop", "tmp/prelaunch-portal-open-visual-v2/smoke/01-desktop-launch-open.png", "Countdown handoff with play/download/account CTAs visible."],
      ["Launch-open mobile", "tmp/prelaunch-portal-open-visual-v2/smoke/08-mobile-landing.png", "Mobile post-countdown landing."],
    ],
  },
  {
    title: "Hosted Web Client",
    intent: "Live /play browser client on desktop and phone-sized Safari profiles.",
    summary: "tmp/web-teavm-hosted-prelaunch-mobile-login-fields-smoke-v2/summary.json",
    reviewFocus: [
      "Desktop and phone-sized /play views keep account creation and recovery routed through the portal.",
      "Hosted phone portrait and landscape captures reach the Design Your Character flow, not only the login shell.",
      "Desktop authenticated proof reaches the first-login subscription/invite dialog over the live game canvas.",
      "Diagnostics screenshots show the intended WSS endpoint, portal handoff URLs, and clean mobile handoff state.",
    ],
    tiles: [
      ["Desktop web first-login", "tmp/prelaunch-client-qa/web-desktop-portal-created-v2/desktop-mouse-smoke.png", "Desktop TeaVM login into a portal-created character, resource controls, keyboard path, and first-login subscription/invite dialog."],
      ["iPhone character design", "tmp/web-teavm-hosted-prelaunch-mobile-login-fields-smoke-v2/iphone-portrait-clean.png", "Phone portrait reaches Design Your Character with mobile panel buttons and resource controls visible."],
      ["iPhone login diagnostics", "tmp/web-teavm-hosted-prelaunch-mobile-login-fields-smoke-v2/iphone-login-diagnostics.png", "Mobile login, endpoint, portal handoff, and diagnostic export state."],
      ["iPhone landscape character design", "tmp/web-teavm-hosted-prelaunch-mobile-login-fields-smoke-v2/iphone-landscape-clean.png", "Phone landscape reaches Design Your Character with the Accept action and resource controls visible."],
      ["iPhone landscape diagnostics", "tmp/web-teavm-hosted-prelaunch-mobile-login-fields-smoke-v2/iphone-landscape-diagnostics.png", "Landscape diagnostic control reachability and layout."],
    ],
  },
  {
    title: "Mobile Safari Simulator",
    intent: "Simulator-only Safari orientation proof for the live web client; physical iPhone remains a manual gate.",
    reviewFocus: [
      "Use these only as orientation and layout evidence.",
      "Do not replace physical iPhone Safari and Home Screen QA with Simulator proof.",
    ],
    tiles: [
      ["Safari portrait", "tmp/prelaunch-client-qa/iphone-simulator-live-play-v1/simulator-safari-portrait.png", "Live /play in portrait Safari."],
      ["Safari raw landscape", "tmp/prelaunch-client-qa/iphone-simulator-live-play-v1/simulator-safari-landscape-right.png", "Raw landscape capture from Simulator."],
      ["Safari upright landscape", "tmp/prelaunch-client-qa/iphone-simulator-live-play-v1/simulator-safari-landscape-right-upright.png", "Rotated landscape review image."],
    ],
  },
  {
    title: "Desktop Launcher",
    intent: "Staging launcher jar renders correctly and points at the launch host/portal.",
    summary: "tmp/prelaunch-client-qa/launcher-staging-jar-visual-v2/manifest.json",
    reviewFocus: [
      "Launcher should show the Voidscape identity, public endpoint, and portal/download copy clearly.",
      "Packaged-config proof should match the same host and portal used by the launch bundle.",
    ],
    tiles: [
      ["Packaged launcher", "tmp/prelaunch-client-qa/launcher-staging-jar-visual-v2/launcher.png", "Ready-to-play launcher, endpoint, and portal copy."],
      ["Live-style launcher", "tmp/prelaunch-client-qa/launcher-live-visual-v1/launcher.png", "Direct live-style launcher override proof."],
    ],
  },
  {
    title: "Desktop Java Client",
    intent: "Workbench-captured Java client login screen, local authenticated panels, and live-host authenticated panels.",
    reviewFocus: [
      "Login and authenticated panels should show a fresh player, not stale local cache credentials.",
      "Inventory, stats, map, and account captures should be readable at the classic client size.",
      "Live-host captures should target voidscape.gg:43596 for the disposable QA account.",
    ],
    manifestCaptures: [
      "tmp/prelaunch-client-qa/pc-client-login-screen-visual-v2/manifest.json",
      "tmp/prelaunch-client-qa/pc-client-auth-visual-v3/manifest.json",
      "tmp/prelaunch-client-qa/pc-client-live-credential-v1/manifest.json",
    ],
  },
  {
    title: "Android APK",
    intent: "Emulator-backed APK login, gameplay, lifecycle/resume, settings logout, and keyboard return.",
    reviewFocus: [
      "Current public-channel default is post-launch APK visibility; still use physical Android QA before broad promotion.",
      "Portrait gameplay has visible unused vertical space in the current emulator proof, so real-phone QA should inspect framing before public APK distribution.",
      "Refreshed lifecycle proof shows the Game settings tab readable in portrait after the heading/first-row collision fix.",
      "Keyboard return after logout and portal handoff remain core Android acceptance checks.",
    ],
    tiles: [
      ["Login home", "tmp/prelaunch-client-qa/android-auth-login-v4/00-auth-login-home.png", "Branded APK login home."],
      ["Existing user keyboard", "tmp/prelaunch-client-qa/android-auth-login-v4/01-auth-existing-user-keyboard.png", "Existing-user tap opens keyboard."],
      ["Credentials entered", "tmp/prelaunch-client-qa/android-auth-login-v4/02-auth-credentials-entered.png", "Calibrated credential entry."],
      ["Post login", "tmp/prelaunch-client-qa/android-auth-login-v4/03-auth-post-login.png", "Authenticated first-login/invite/subscription panel."],
      ["Lifecycle game HUD", "tmp/prelaunch-client-qa/android-auth-lifecycle-v15/03-lifecycle-game-hud.png", "Returning-player in-game HUD."],
      ["After resume", "tmp/prelaunch-client-qa/android-auth-lifecycle-v15/04-lifecycle-after-resume.png", "App resume preserves play state."],
      ["Duplicate relaunch", "tmp/prelaunch-client-qa/android-auth-lifecycle-v15/05-lifecycle-after-duplicate-relaunch.png", "Launcher relaunch returns to the running client."],
      ["Settings open", "tmp/prelaunch-client-qa/android-auth-lifecycle-v15/06-lifecycle-settings-open.png", "Game settings tab is readable in portrait and the logout row remains reachable."],
      ["Logged out", "tmp/prelaunch-client-qa/android-auth-lifecycle-v15/07-lifecycle-after-logout-login-home.png", "Logout returns to branded login home."],
      ["Post-logout keyboard", "tmp/prelaunch-client-qa/android-auth-lifecycle-v15/08-lifecycle-after-logout-keyboard.png", "Existing-user keyboard still opens after logout."],
    ],
  },
];

function readJson(relativePath) {
  if (!relativePath) return null;
  const absolutePath = fromRoot(relativePath);
  if (!fs.existsSync(absolutePath)) return null;
  try {
    return JSON.parse(fs.readFileSync(absolutePath, "utf8"));
  } catch (error) {
    return { parseError: error.message };
  }
}

function htmlEscape(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function slugId(value) {
  return String(value)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function listHtml(items, className = "") {
  if (!items || items.length === 0) return "";
  const classAttr = className ? ` class="${htmlEscape(className)}"` : "";
  return `<ul${classAttr}>${items.map((item) => `<li>${htmlEscape(item)}</li>`).join("")}</ul>`;
}

function hrefFor(absolutePath) {
  return path.relative(outDir, absolutePath).split(path.sep).join("/");
}

function normalizeTile(tile) {
  if (Array.isArray(tile)) {
    const [title, relativePath, proof] = tile;
    return {
      title,
      relativePath,
      proof,
      absolutePath: fromRoot(relativePath),
    };
  }
  return {
    ...tile,
    absolutePath: path.isAbsolute(tile.path) ? tile.path : fromRoot(tile.path),
    relativePath: tile.relativePath || tile.path,
  };
}

function firstExistingDirectory(candidates) {
  for (const candidate of candidates) {
    if (candidate && fs.existsSync(candidate) && fs.statSync(candidate).isDirectory()) {
      return candidate;
    }
  }
  return "";
}

function firstExecutable(candidates) {
  for (const candidate of candidates) {
    if (!candidate) continue;
    try {
      fs.accessSync(candidate, fs.constants.X_OK);
      return candidate;
    } catch {
      // Keep looking.
    }
  }
  return "";
}

function resolvePlaywrightDir() {
  if (playwrightDir) return playwrightDir;
  return firstExistingDirectory([
    path.join(root, "node_modules/playwright-core"),
    path.join(root, "node_modules/playwright"),
    "/tmp/voidscape-playwright-smoke/node_modules/playwright-core",
    "/tmp/voidscape-playwright-smoke/node_modules/playwright",
    "/Users/s/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core",
    "/Users/s/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright",
  ]);
}

function resolveChromePath() {
  if (chromePath) return chromePath;
  return firstExecutable([
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
  ]);
}

async function waitForBoardImages(page) {
  await page.evaluate(async () => {
    const images = Array.from(document.images);
    await Promise.all(images.map(async (image) => {
      image.loading = "eager";
      if (image.decode) {
        await image.decode().catch(() => {});
        return;
      }
      if (image.complete) return;
      await new Promise((resolve) => {
        image.addEventListener("load", resolve, { once: true });
        image.addEventListener("error", resolve, { once: true });
      });
    }));
  });
  await page.waitForFunction(() => Array.from(document.images).every((image) => {
    return image.complete && image.naturalWidth > 0 && image.naturalHeight > 0;
  }), null, { timeout: 15000 });
}

async function renderOverviewScreenshots(indexPath) {
  if (skipOverviewScreenshots) {
    return { screenshots: [], warning: "overview screenshot rendering skipped by option" };
  }

  const resolvedPlaywrightDir = resolvePlaywrightDir();
  const resolvedChromePath = resolveChromePath();
  if (!resolvedPlaywrightDir || !resolvedChromePath) {
    return {
      screenshots: [],
      warning: "overview screenshot rendering skipped; Chrome/Playwright was not found",
    };
  }

  let chromium;
  try {
    ({ chromium } = require(resolvedPlaywrightDir));
  } catch (error) {
    return {
      screenshots: [],
      warning: `overview screenshot rendering skipped; could not load Playwright: ${error.message}`,
    };
  }

  const browser = await chromium.launch({
    executablePath: resolvedChromePath,
    headless: true,
  });
  const fileUrl = pathToFileURL(indexPath).href;
  const screenshots = [];
  try {
    for (const viewport of [
      { name: "desktop", width: 1440, height: 1200, file: "board-review.png" },
      { name: "mobile", width: 390, height: 900, file: "board-review-mobile.png" },
    ]) {
      const page = await browser.newPage({
        viewport: { width: viewport.width, height: viewport.height },
        deviceScaleFactor: 1,
      });
      await page.goto(fileUrl, { waitUntil: "load", timeout: 30000 });
      await waitForBoardImages(page);
      await page.screenshot({
        path: path.join(outDir, viewport.file),
        fullPage: true,
      });
      await page.close();
      screenshots.push({
        name: viewport.name,
        width: viewport.width,
        height: viewport.height,
        path: path.relative(root, path.join(outDir, viewport.file)),
      });
    }
  } finally {
    await browser.close();
  }
  return { screenshots, warning: "" };
}

function manifestTiles(section) {
  const captures = [];
  for (const manifestPath of section.manifestCaptures || []) {
    const manifest = readJson(manifestPath);
    if (!manifest || manifest.parseError) {
      captures.push({
        title: path.basename(path.dirname(manifestPath)),
        relativePath: manifestPath,
        proof: manifest?.parseError || "Manifest missing.",
        absolutePath: fromRoot(manifestPath),
        missing: true,
      });
      continue;
    }
    for (const capture of manifest.captures || []) {
      captures.push({
        title: capture.label,
        proof: `${path.basename(path.dirname(manifestPath))} (${capture.width}x${capture.height})`,
        relativePath: path.relative(root, capture.pngPath),
        absolutePath: capture.pngPath,
      });
    }
  }
  return captures;
}

function sectionSummary(section) {
  const summary = readJson(section.summary);
  if (!section.summary) return { status: "info", label: "artifact images" };
  if (!summary) return { status: "missing", label: "summary missing" };
  if (summary.parseError) return { status: "warn", label: "summary parse error" };
  if (summary.ok === false || (Array.isArray(summary.failures) && summary.failures.length > 0)) {
    return { status: "fail", label: "summary has failures" };
  }
  return { status: "pass", label: "summary pass" };
}

const builtSections = sections.map((section) => {
  const tiles = [
    ...(section.tiles || []).map(normalizeTile),
    ...manifestTiles(section),
  ].map((tile) => ({
    ...tile,
    exists: Boolean(tile.absolutePath && fs.existsSync(tile.absolutePath)),
  }));
  const missing = tiles.filter((tile) => !tile.exists);
  const summary = sectionSummary(section);
  const status = missing.length > 0 ? "missing" : summary.status;
  return {
    ...section,
    id: slugId(section.title),
    tiles,
    missing,
    summaryStatus: summary,
    status,
  };
});

const totals = builtSections.reduce(
  (acc, section) => {
    acc.sections += 1;
    acc.tiles += section.tiles.length;
    acc.missing += section.missing.length;
    if (section.status === "pass" || section.status === "info") acc.ready += 1;
    return acc;
  },
  { sections: 0, tiles: 0, missing: 0, ready: 0 },
);

fs.mkdirSync(outDir, { recursive: true });

const summary = {
  generatedAt: new Date().toISOString(),
  root,
  outDir,
  totals,
  overviewScreenshots: [],
  overviewWarning: "",
  sections: builtSections.map((section) => ({
    title: section.title,
    id: section.id,
    status: section.status,
    summary: section.summary || "",
    summaryStatus: section.summaryStatus,
    tileCount: section.tiles.length,
    missing: section.missing.map((tile) => tile.relativePath),
    reviewFocus: section.reviewFocus || [],
  })),
  reviewFocus: globalReviewFocus,
  manualGates,
};

const sectionHtml = builtSections
  .map((section) => {
    const status = section.status.toUpperCase();
    const summaryLink = section.summary
      ? `<a class="summary-link" href="${htmlEscape(hrefFor(fromRoot(section.summary)))}">summary</a>`
      : "";
    const tiles = section.tiles
      .map((tile) => {
        const href = tile.exists ? hrefFor(tile.absolutePath) : "";
        const image = tile.exists
          ? `<a href="${htmlEscape(href)}"><img src="${htmlEscape(href)}" alt="${htmlEscape(tile.title)}" loading="eager" decoding="sync"></a>`
          : `<div class="missing-box">MISSING<br><code>${htmlEscape(tile.relativePath)}</code></div>`;
        return `<article class="tile ${tile.exists ? "" : "tile-missing"}">
          ${image}
          <div class="tile-copy">
            <h3>${htmlEscape(tile.title)}</h3>
            <p>${htmlEscape(tile.proof || "")}</p>
            ${tile.exists ? `<a href="${htmlEscape(href)}">${htmlEscape(tile.relativePath)}</a>` : ""}
          </div>
        </article>`;
      })
      .join("\n");
    const focusHtml = section.reviewFocus?.length
      ? `<div class="focus-box"><h3>Review Focus</h3>${listHtml(section.reviewFocus)}</div>`
      : "";
    return `<section class="section" id="${htmlEscape(section.id)}">
      <div class="section-head">
        <div>
          <h2>${htmlEscape(section.title)}</h2>
          <p>${htmlEscape(section.intent)}</p>
        </div>
        <div class="section-meta">
          <span class="pill ${htmlEscape(section.status)}">${htmlEscape(status)}</span>
          ${summaryLink}
        </div>
      </div>
      ${focusHtml}
      <div class="grid">${tiles}</div>
    </section>`;
  })
  .join("\n");

const quickLinksHtml = builtSections
  .map((section) => `<a class="quick-link" href="#${htmlEscape(section.id)}">${htmlEscape(section.title)}</a>`)
  .join("");

const overviewHtml = `<section class="overview">
    <div class="overview-col">
      <h2>QC Focus</h2>
      ${listHtml(globalReviewFocus, "review-list")}
    </div>
    <div class="overview-col">
      <h2>Manual Gates</h2>
      ${listHtml(manualGates, "review-list gate-list")}
    </div>
  </section>
  <nav class="quick-links" aria-label="Visual evidence sections">${quickLinksHtml}</nav>`;

const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Voidscape Prelaunch Visual Board</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #111312;
      --panel: #181c1b;
      --panel-2: #202522;
      --text: #f3efe5;
      --muted: #b9b1a2;
      --line: #383d38;
      --accent: #79b8a8;
      --warn: #e5be65;
      --bad: #ef7b72;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 1.45;
    }
    header {
      padding: 28px clamp(16px, 4vw, 48px) 14px;
      border-bottom: 1px solid var(--line);
      background: #151816;
    }
    h1, h2, h3, p { margin: 0; }
    h1 { font-size: clamp(24px, 3vw, 40px); letter-spacing: 0; }
    header p { margin-top: 8px; max-width: 920px; color: var(--muted); }
    .stats {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 16px;
    }
    .stat, .pill, .summary-link {
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 6px 9px;
      background: var(--panel-2);
      color: var(--text);
      font-size: 13px;
      text-decoration: none;
    }
    main { padding: 22px clamp(16px, 4vw, 48px) 48px; }
    .overview {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
      margin-bottom: 20px;
    }
    .overview-col, .focus-box {
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      padding: 14px 16px;
    }
    .overview h2, .focus-box h3 {
      font-size: 15px;
      letter-spacing: 0;
      margin: 0 0 8px;
    }
    .review-list, .focus-box ul {
      margin: 0;
      padding-left: 18px;
      color: var(--muted);
      font-size: 13px;
    }
    .review-list li, .focus-box li { margin: 5px 0; }
    .gate-list li { color: #e7d39c; }
    .quick-links {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      margin: 0 0 20px;
    }
    .quick-link {
      border: 1px solid var(--line);
      border-radius: 6px;
      padding: 7px 9px;
      background: var(--panel-2);
      color: #a9d7cd;
      font-size: 13px;
      text-decoration: none;
    }
    .section {
      margin-top: 22px;
      border-top: 1px solid var(--line);
      padding-top: 18px;
    }
    .section-head {
      display: flex;
      align-items: start;
      justify-content: space-between;
      gap: 14px;
      margin-bottom: 14px;
    }
    .focus-box {
      margin: -2px 0 14px;
      background: #161a19;
    }
    .section-head h2 { font-size: 18px; letter-spacing: 0; }
    .section-head p { color: var(--muted); max-width: 900px; margin-top: 4px; }
    .section-meta {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: flex-end;
    }
    .pill.pass, .pill.info { border-color: color-mix(in srgb, var(--accent) 65%, var(--line)); color: #bde5d8; }
    .pill.warn, .pill.missing { border-color: color-mix(in srgb, var(--warn) 65%, var(--line)); color: #f1d89a; }
    .pill.fail { border-color: color-mix(in srgb, var(--bad) 65%, var(--line)); color: #f0aaa4; }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 12px;
    }
    .tile {
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      overflow: hidden;
      min-width: 0;
    }
    .tile img {
      display: block;
      width: 100%;
      aspect-ratio: 16 / 10;
      object-fit: contain;
      background: #050605;
      border-bottom: 1px solid var(--line);
    }
    .missing-box {
      display: grid;
      place-content: center;
      gap: 8px;
      min-height: 180px;
      padding: 16px;
      color: #f1d89a;
      background: #221d12;
      text-align: center;
      border-bottom: 1px solid var(--line);
    }
    .missing-box code {
      color: #eadbb8;
      overflow-wrap: anywhere;
      font-size: 12px;
    }
    .tile-copy {
      padding: 10px 11px 12px;
      min-width: 0;
    }
    .tile-copy h3 { font-size: 14px; letter-spacing: 0; }
    .tile-copy p {
      margin-top: 4px;
      min-height: 40px;
      color: var(--muted);
      font-size: 13px;
    }
    .tile-copy a {
      display: block;
      margin-top: 8px;
      color: #a9d7cd;
      font-size: 12px;
      overflow-wrap: anywhere;
      text-decoration: none;
    }
    @media (max-width: 680px) {
      .overview { grid-template-columns: 1fr; }
      .section-head { display: block; }
      .section-meta { justify-content: flex-start; margin-top: 10px; }
      .grid { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <header>
    <h1>Voidscape Prelaunch Visual Board</h1>
    <p>Generated from local prelaunch smoke artifacts. Each tile links to the raw screenshot so QC can inspect the actual rendered portal, launcher, desktop client, web client, iPhone Simulator, and Android APK evidence.</p>
    <div class="stats">
      <span class="stat">Generated ${htmlEscape(summary.generatedAt)}</span>
      <span class="stat">${totals.sections} sections</span>
      <span class="stat">${totals.tiles} screenshots</span>
      <span class="stat">${totals.missing} missing</span>
      <a class="stat" href="summary.json">summary.json</a>
    </div>
  </header>
  <main>
    ${overviewHtml}
    ${sectionHtml}
  </main>
</body>
</html>
`;

const indexPath = path.join(outDir, "index.html");
fs.writeFileSync(indexPath, html);

const overviewResult = await renderOverviewScreenshots(indexPath);
summary.overviewScreenshots = overviewResult.screenshots;
summary.overviewWarning = overviewResult.warning;

fs.writeFileSync(path.join(outDir, "summary.json"), `${JSON.stringify(summary, null, 2)}\n`);

console.log(JSON.stringify({
  ok: totals.missing === 0,
  index: indexPath,
  summary: path.join(outDir, "summary.json"),
  overviewScreenshots: summary.overviewScreenshots,
  overviewWarning: summary.overviewWarning,
  totals,
}, null, 2));
