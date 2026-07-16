#!/usr/bin/env node

import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const portalServer = join(repoRoot, "web/portal/dev-server.mjs");
const tempRoot = await mkdtemp(join(tmpdir(), "voidscape-loot-editor-static."));
const mutationPaths = [
	"/api/loot-editor",
	"/api/loot-editor/drafts",
	"/api/loot-workshop",
	"/api/loot-workshop/drafts",
	"/api/loot",
	"/api/loot-tables",
	"/api/admin/loot-editor",
	"/api/admin/loot-workshop",
	"/api/admin/loot",
	"/api/admin/loot-tables"
];
const mutationMethods = ["POST", "PUT", "PATCH", "DELETE"];
const guideScreenshotPaths = [
	"/assets/loot-editor-guide/01-choose-table.jpg",
	"/assets/loot-editor-guide/02-edit-drop.jpg",
	"/assets/loot-editor-guide/03-add-item.jpg",
	"/assets/loot-editor-guide/04-export.jpg"
];

let activeServer = null;

try {
	const publicPortal = await startPortal("public", true);
	await assertEditorStaticFiles(publicPortal.baseUrl);
	await assertNoMutationApi(publicPortal.baseUrl, "public mode");
	await stopPortal();

	const localPortal = await startPortal("local", false);
	await assertNoMutationApi(localPortal.baseUrl, "local mode");

	console.log("Loot editor static smoke OK");
	console.log("  /loot-editor, its picture guide, and all static assets load through dev-server.mjs");
	console.log("  loot mutation namespaces return 404 in public and local modes");
} finally {
	await stopPortal();
	await rm(tempRoot, { recursive: true, force: true });
}

async function startPortal(label, publicMode) {
	const port = await availablePort();
	const output = [];
	const child = spawn(process.execPath, [portalServer], {
		cwd: repoRoot,
		env: {
			...process.env,
			PORT: String(port),
			PORTAL_BIND_HOST: "127.0.0.1",
			PORTAL_DATA_DIR: join(tempRoot, label),
			PORTAL_PUBLIC_MODE: publicMode ? "1" : "0",
			PORTAL_LAUNCH_SIGNUP_MODE: "0",
			PORTAL_BETA_MODE: "0",
			PORTAL_PUBLIC_BETA: "0",
			PORTAL_ADMIN_TOKEN: "loot-editor-static-smoke-admin-token-1234567890",
			PORTAL_ABUSE_HASH_SALT: "loot-editor-static-smoke-abuse-salt-1234567890"
		},
		stdio: ["ignore", "pipe", "pipe"]
	});
	child.stdout.on("data", (chunk) => output.push(String(chunk)));
	child.stderr.on("data", (chunk) => output.push(String(chunk)));
	activeServer = { child, output };

	const baseUrl = `http://127.0.0.1:${port}`;
	for (let attempt = 0; attempt < 100; attempt += 1) {
		if (child.exitCode !== null) {
			throw new Error(`${label} portal exited during startup:\n${output.join("")}`);
		}
		try {
			const health = await fetch(`${baseUrl}/api/health`);
			if (health.status === 200) return { baseUrl };
		} catch (error) {
			// The listener is not ready yet.
		}
		await delay(50);
	}
	throw new Error(`${label} portal did not become ready:\n${output.join("")}`);
}

async function stopPortal() {
	if (!activeServer) return;
	const { child } = activeServer;
	activeServer = null;
	if (child.exitCode !== null) return;
	child.kill("SIGTERM");
	await Promise.race([
		new Promise((resolveExit) => child.once("exit", resolveExit)),
		delay(2000).then(() => {
			if (child.exitCode === null) child.kill("SIGKILL");
		})
	]);
}

async function assertEditorStaticFiles(baseUrl) {
	const html = await expectStatic(baseUrl, "/loot-editor", "text/html");
	assert(html.includes("loot-editor.css"), "loot editor HTML must load loot-editor.css");
	assert(html.includes("loot-editor.js"), "loot editor HTML must load loot-editor.js");
	assert(
		html.includes('data-source-url="assets/loot-editor-data.json"'),
		"loot editor HTML must point the client at the generated data asset"
	);
	assert(html.includes('href="/loot-editor-guide"'), "loot editor must link to its simple guide");

	const slashHtml = await expectStatic(baseUrl, "/loot-editor/", "text/html");
	assert(slashHtml === html, "/loot-editor/ must resolve to the same page as /loot-editor");

	const css = await expectStatic(baseUrl, "/loot-editor.css", "text/css");
	assert(css.trim().length > 0, "loot-editor.css must not be empty");

	const guideHtml = await expectStatic(baseUrl, "/loot-editor-guide", "text/html");
	const slashGuideHtml = await expectStatic(baseUrl, "/loot-editor-guide/", "text/html");
	assert(slashGuideHtml === guideHtml, "/loot-editor-guide/ must resolve to the same page as /loot-editor-guide");
	assert(guideHtml.includes('href="/loot-editor"'), "loot editor guide must link back to the editor");
	assert(guideHtml.includes("loot-editor-guide.css"), "loot editor guide must load its stylesheet");
	assert(!guideHtml.includes("<script"), "loot editor guide must remain a no-JavaScript static page");
	assert(!guideHtml.includes("/api/"), "loot editor guide must not reference portal APIs");
	for (const screenshotPath of guideScreenshotPaths) {
		assert(guideHtml.includes(screenshotPath.slice(1)), `loot editor guide must show ${screenshotPath}`);
		const bytes = await expectBinaryStatic(baseUrl, screenshotPath, "image/jpeg");
		assert(
			bytes.length > 4 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[bytes.length - 2] === 0xff && bytes[bytes.length - 1] === 0xd9,
			`${screenshotPath} must contain valid JPEG framing`
		);
	}

	const guideCss = await expectStatic(baseUrl, "/loot-editor-guide.css", "text/css");
	assert(guideCss.trim().length > 0, "loot-editor-guide.css must not be empty");

	const javascript = await expectStatic(baseUrl, "/loot-editor.js", "application/javascript");
	assert(javascript.trim().length > 0, "loot-editor.js must not be empty");
	assert((javascript.match(/\bfetch\s*\(/g) || []).length === 1, "loot editor client must make exactly one fetch");
	assert(javascript.includes("fetch(DATA_URL"), "loot editor client fetch must load only its static data URL");
	assert(!javascript.includes("/api/"), "loot editor client must not reference portal APIs");
	for (const method of mutationMethods) {
		assert(!javascript.includes(`method: \"${method}\"`), `loot editor client must not issue ${method} requests`);
	}

	const dataText = await expectStatic(baseUrl, "/assets/loot-editor-data.json", "application/json");
	let data;
	try {
		data = JSON.parse(dataText);
	} catch (error) {
		throw new Error(`loot editor data must be valid JSON: ${error.message}`);
	}
	assert(data !== null && typeof data === "object" && !Array.isArray(data), "loot editor data must be a JSON object");
	assert(data.schemaVersion === 1, "loot editor data must use schemaVersion 1");
	assert(/^sha256:[0-9a-f]{64}$/.test(data.source?.fingerprint || ""), "loot editor data must have a SHA-256 source fingerprint");
	assert(Array.isArray(data.items) && data.items.length > 0, "loot editor data must include an item catalog");
	assert(Array.isArray(data.groups) && data.groups.length > 0, "loot editor data must include NPC groups");
	assert(Array.isArray(data.voidChest?.rows) && data.voidChest.rows.length > 0, "loot editor data must include Void Chest rows");
	assertGeneratedDataContract(data);
}

function assertGeneratedDataContract(data) {
	const itemIds = new Set(data.items.map((item) => item.id));
	const rowIds = new Set();
	let nothingRows = 0;
	for (const group of data.groups) {
		assert(Array.isArray(group.rows), `${group.groupId || "NPC group"} must include rows`);
		const serializedWeight = group.rows.reduce((total, row) => total + row.weight, 0);
		assert(
			serializedWeight === group.denominator,
			`${group.groupId} rows must preserve the full fixed denominator (${serializedWeight} !== ${group.denominator})`
		);
		for (const row of group.rows) {
			assert(typeof row.rowId === "string" && row.rowId.length > 0, `${group.groupId} has a row without a rowId`);
			assert(!rowIds.has(row.rowId), `duplicate generated rowId ${row.rowId}`);
			rowIds.add(row.rowId);
			assert(typeof row.source?.file === "string" && row.source.file.length > 0, `${row.rowId} must include its source file`);
			assert(Number.isInteger(row.source?.line) && row.source.line > 0, `${row.rowId} must include its source line`);
			if (row.kind === "item") assert(itemIds.has(row.itemId), `${row.rowId} references unknown item ${row.itemId}`);
			if (row.kind === "nothing") {
				nothingRows += 1;
				assert(typeof row.derived === "boolean", `${row.rowId} must identify whether its Nothing weight is derived`);
			}
		}
	}
	assert(nothingRows > 0, "generated NPC tables must retain their locked Nothing outcomes");
	for (const row of data.voidChest.rows) {
		assert(typeof row.rowId === "string" && row.rowId.length > 0, "Void Chest rows must include rowIds");
		assert(!rowIds.has(row.rowId), `duplicate generated rowId ${row.rowId}`);
		rowIds.add(row.rowId);
		assert(itemIds.has(row.itemId), `${row.rowId} references unknown item ${row.itemId}`);
		assert(typeof row.source?.file === "string" && row.source.file.length > 0, `${row.rowId} must include its source file`);
		assert(Number.isInteger(row.source?.line) && row.source.line > 0, `${row.rowId} must include its source line`);
	}
	const voidWeight = data.voidChest.rows.reduce((total, row) => total + row.weight, 0);
	assert(voidWeight === data.voidChest.totalWeight, "Void Chest rows must preserve the generated fixed total");
}

async function expectStatic(baseUrl, path, expectedContentType) {
	const response = await fetch(`${baseUrl}${path}`);
	assert(response.status === 200, `${path} must return 200, got ${response.status}`);
	assert(
		(response.headers.get("content-type") || "").startsWith(expectedContentType),
		`${path} must use ${expectedContentType}, got ${response.headers.get("content-type") || "none"}`
	);
	assert(response.headers.get("x-content-type-options") === "nosniff", `${path} must set nosniff`);
	const csp = response.headers.get("content-security-policy") || "";
	assert(csp.includes("default-src 'self'"), `${path} must retain the portal CSP`);
	assert(csp.includes("connect-src 'self'"), `${path} CSP must allow same-origin editor data loading`);
	return response.text();
}

async function expectBinaryStatic(baseUrl, path, expectedContentType) {
	const response = await fetch(`${baseUrl}${path}`);
	assert(response.status === 200, `${path} must return 200, got ${response.status}`);
	assert(
		(response.headers.get("content-type") || "").startsWith(expectedContentType),
		`${path} must use ${expectedContentType}, got ${response.headers.get("content-type") || "none"}`
	);
	assert(response.headers.get("x-content-type-options") === "nosniff", `${path} must set nosniff`);
	return new Uint8Array(await response.arrayBuffer());
}

async function assertNoMutationApi(baseUrl, modeLabel) {
	for (const path of mutationPaths) {
		for (const method of mutationMethods) {
			const response = await fetch(`${baseUrl}${path}`, {
				method,
				headers: { "content-type": "application/json" },
				body: "{}"
			});
			assert(
				response.status === 404,
				`${modeLabel} ${method} ${path} must remain unavailable, got ${response.status}`
			);
		}
	}
}

async function availablePort() {
	return new Promise((resolvePort, reject) => {
		const probe = createServer();
		probe.once("error", reject);
		probe.listen(0, "127.0.0.1", () => {
			const address = probe.address();
			const port = typeof address === "object" && address ? address.port : 0;
			probe.close((error) => error ? reject(error) : resolvePort(port));
		});
	});
}

function assert(condition, message) {
	if (!condition) throw new Error(message);
}

function delay(milliseconds) {
	return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}
