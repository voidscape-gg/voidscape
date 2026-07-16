#!/usr/bin/env node
import { createHash } from "node:crypto";
import { writeFileSync } from "node:fs";
import http from "node:http";

const [portFile, adminStatusText, clientVersion] = process.argv.slice(2);
if (!portFile || !adminStatusText || !clientVersion) {
	throw new Error("usage: launch-staging-guard-server.mjs PORT_FILE ADMIN_STATUS CLIENT_VERSION");
}
const adminStatus = Number(adminStatusText);
const artifactPaths = [
	"Open_RSC_Client.jar",
	"MD5.SUM",
	"video/Authentic_Sprites.orsc",
	"video/Custom_Landscape.orsc",
	"video/models.orsc",
	"worldmap/plane-0.png",
	"worldmap/plane-1.png",
	"worldmap/plane-2.png",
	"worldmap/plane-3.png"
];
const artifacts = new Map(artifactPaths.map((path) => [path, Buffer.from(`fixture:${path}`)]));
const launcher = Buffer.from("fixture:launcher");
let baseUrl = "";

const server = http.createServer(async (request, response) => {
	const url = new URL(request.url || "/", baseUrl);
	if (url.pathname === "/api/health") {
		return json(response, 200, {
			ok: true,
			publicMode: true,
			launchSignupMode: true,
			storage: { durable: true, tempDirOverride: false },
			openRscDb: { configured: true },
			config: {
				publicReady: true,
				abuseHashSaltConfigured: true,
				email: { verificationRequired: true, configured: true }
			}
		});
	}
	if (url.pathname === "/api/public") {
		return json(response, 200, {
			publicMode: true,
			launchSignupMode: true,
			launch: { openAt: "2026-07-18T18:00:00.000Z" },
			worldRules: {
				registration: "hybrid",
				webRegistration: "portal-first",
				desktopRegistration: "packet",
				nativeAndroidRegistration: "portal-first",
				packetRegistration: true,
				communityTermsVersion: "2026-07-16",
				communityTermsUrl: "/community-rules",
				memberWorld: true,
				subscriptionGrantsMembers: false
			},
			status: { playersOnline: 0 },
			oauth: { google: { enabled: false } },
			downloads: [{ slug: "android-apk", available: false }]
		});
	}
	if (url.pathname === "/api/launcher/manifest.properties") {
		return text(response, 200, launcherManifest(), "text/plain");
	}
	if (url.pathname.startsWith("/artifacts/")) {
		const path = decodeURIComponent(url.pathname.slice("/artifacts/".length));
		const bytes = artifacts.get(path);
		if (!bytes) return text(response, 404, "missing");
		return bytesResponse(request, response, bytes);
	}
	if (url.pathname === "/launcher.jar") return bytesResponse(request, response, launcher);
	if (url.pathname === "/api/payments/subscription-cards/checkout") {
		return json(response, 404, { error: "not_available_during_prelaunch" });
	}
	if (url.pathname === "/api/oauth/google/nonce") {
		return json(response, 503, { error: "google_oauth_not_configured" });
	}
	if (url.pathname.startsWith("/api/admin/")) {
		return json(response, adminStatus, { error: adminStatus === 404 ? "not_found" : "forbidden" });
	}
	if (url.pathname === "/api/accounts/register" && request.method === "POST") {
		const body = await readJson(request);
		if (body.termsAccepted !== true || body.termsVersion !== "2026-07-16") {
			return json(response, 400, { error: "terms_acceptance_required" });
		}
		if (!/^[A-Za-z0-9]{8,20}$/.test(String(body.password || ""))) {
			return json(response, 400, { error: "invalid_game_password" });
		}
		return json(response, 202, {
			verificationRequired: true,
			email: body.email,
			username: body.username,
			expiresAt: "2026-07-19T18:00:00.000Z"
		});
	}
	if (url.pathname === "/api/accounts/login" && request.method === "POST") {
		const body = await readJson(request);
		if (String(body.email || "").includes("pending")) {
			return json(response, 401, { error: "invalid_credentials" });
		}
		return json(response, 200, accountPayload(body.email, body.password, String(body.email || "").includes("unverified")));
	}
	if (url.pathname === "/api/account") {
		return json(response, 200, accountPayload("exact@example.com", "Launchpass1", false));
	}
	return json(response, 404, { error: "not_found" });
});

server.listen(0, "127.0.0.1", () => {
	const address = server.address();
	baseUrl = `http://127.0.0.1:${address.port}/`;
	writeFileSync(portFile, String(address.port));
});

function launcherManifest() {
	const lines = [
		"version=2",
		`clientVersion=${clientVersion}`,
		`baseUrl=${baseUrl}artifacts/`,
		`launcher.sha256=${sha256(launcher)}`,
		`launcher.size=${launcher.length}`,
		`launcher.url=${baseUrl}launcher.jar`
	];
	artifactPaths.forEach((path, index) => {
		const bytes = artifacts.get(path);
		const key = `file.${index + 1}`;
		lines.push(`${key}.path=${path}`, `${key}.sha256=${sha256(bytes)}`, `${key}.size=${bytes.length}`);
	});
	return `${lines.join("\n")}\n`;
}

function accountPayload(email, password, unverified) {
	const username = email === "exact@example.com" ? "ExactQa" : email.split("@")[0].replace(/[^A-Za-z0-9 ]/g, "").slice(0, 12);
	return {
		token: "fixture-session-token",
		account: { email, status: "active" },
		security: { emailVerified: !unverified },
		characters: [{
			name: username,
			playerId: 42,
			linkStatus: "linked",
			source: "openrsc-sqlite-created"
		}],
		passwordEchoForFixtureOnly: Boolean(password)
	};
}

function sha256(bytes) {
	return createHash("sha256").update(bytes).digest("hex");
}

async function readJson(request) {
	const chunks = [];
	for await (const chunk of request) chunks.push(chunk);
	return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
}

function json(response, status, body) {
	return text(response, status, JSON.stringify(body), "application/json");
}

function text(response, status, body, contentType = "text/plain") {
	const bytes = Buffer.from(body);
	response.writeHead(status, { "content-type": contentType, "content-length": bytes.length });
	response.end(bytes);
}

function bytesResponse(request, response, bytes) {
	response.writeHead(200, { "content-type": "application/octet-stream", "content-length": bytes.length });
	response.end(request.method === "HEAD" ? undefined : bytes);
}
