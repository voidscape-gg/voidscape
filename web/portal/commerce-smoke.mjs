import { createServer } from "node:http";
import { createHash, createHmac } from "node:crypto";
import { execFileSync, spawn } from "node:child_process";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { createServer as createNetServer } from "node:net";

const repoRoot = resolve(new URL("../..", import.meta.url).pathname);
const webhookSecret = "commerce-webhook-secret-fixture";
const publicToken = "commerce-public-token-fixture";
const privateKey = "commerce-private-key-fixture";
const packageId = "6276316";
const expectedAuthorization = `Basic ${Buffer.from(`${publicToken}:${privateKey}`).toString("base64")}`;
const sessionToken = "commerce-session-token-fixture";
const adminToken = "commerce-admin-token-fixture-1234567890";
const tmp = await mkdtemp(join(tmpdir(), "voidscape-commerce-smoke."));
const dbPath = join(tmp, "openrsc.db");
const dataDir = join(tmp, "portal-data");
const portalLog = join(tmp, "portal.log");
let portalProcess;
let mockServer;

function check(condition, message) {
	if (!condition) throw new Error(message);
}

function sqlite(sql, json = false) {
	const args = json ? ["-json", dbPath, sql] : [dbPath, sql];
	const output = execFileSync("sqlite3", args, { encoding: "utf8" }).trim();
	return json && output ? JSON.parse(output) : output;
}

async function freePort() {
	return new Promise((resolvePort, reject) => {
		const server = createNetServer();
		server.once("error", reject);
		server.listen(0, "127.0.0.1", () => {
			const address = server.address();
			server.close(() => resolvePort(address.port));
		});
	});
}

async function jsonRequest(url, options = {}, expectedStatus = 200) {
	const response = await fetch(url, options);
	const text = await response.text();
	let body = {};
	try {
		body = text ? JSON.parse(text) : {};
	} catch (error) {
		throw new Error(`non-JSON response ${response.status}: ${text.slice(0, 200)}`);
	}
	check(response.status === expectedStatus, `expected ${expectedStatus} from ${url}, got ${response.status}: ${text}`);
	return body;
}

function signedWebhook(payload) {
	const raw = JSON.stringify(payload);
	const bodyHash = createHash("sha256").update(raw).digest("hex");
	const signature = createHmac("sha256", webhookSecret).update(bodyHash).digest("hex");
	return { raw, signature };
}

async function sendWebhook(base, payload, status = 200, signatureOverride = "") {
	const signed = signedWebhook(payload);
	return jsonRequest(`${base}/api/payments/tebex/webhook`, {
		method: "POST",
		headers: {
			"content-type": "application/json",
			"x-signature": signatureOverride || signed.signature
		},
		body: signed.raw
	}, status);
}

function paymentPayload({
	webhookId,
	type = "payment.completed",
	transactionId,
	intent,
	basket,
	createdAtMs,
	customShape = "subject",
	productId = packageId,
	baseAmount = 5,
	productPaidAmount = 5,
	transactionPaidAmount = 5.4
}) {
	const product = {
		id: Number(productId),
		name: "1-week subscription card",
		quantity: 1,
		base_price: { amount: baseAmount, currency: "USD" },
		paid_price: { amount: productPaidAmount, currency: "USD" },
		custom: customShape === "product" ? { voidscape_intent: intent, voidscape_basket: basket } : null
	};
	const subject = {
		transaction_id: transactionId,
		payment_sequence: "oneoff",
		created_at: new Date(createdAtMs).toISOString(),
		price_paid: { amount: transactionPaidAmount, currency: "USD" },
		fees: { tax: { amount: Math.max(0, Number((transactionPaidAmount - productPaidAmount).toFixed(2))), currency: "USD" } },
		products: [product]
	};
	if (customShape === "subject") {
		subject.custom = { voidscape_intent: intent };
		subject.basket = { ident: basket };
	}
	return {
		id: webhookId,
		type,
		date: new Date(createdAtMs + 1000).toISOString(),
		subject
	};
}

function intentRow(intentId) {
	return sqlite(`SELECT * FROM portal_commerce_checkout_intents WHERE intent_id='${intentId.replaceAll("'", "''")}'`, true)[0];
}

async function createCheckout(base) {
	const result = await jsonRequest(`${base}/api/payments/subscription-cards/checkout`, {
		method: "POST",
		headers: { authorization: `Bearer ${sessionToken}`, "content-type": "application/json" },
		body: "{}"
	}, 201);
	check(result.status === "checkout_ready", "checkout should be ready");
	check(/^http:\/\/127\.0\.0\.1:\d+\/checkout\/basket-/.test(result.links && result.links.checkout || ""), "checkout should return only the mock Tebex checkout link");
	const rows = sqlite("SELECT * FROM portal_commerce_checkout_intents ORDER BY created_at_ms DESC, intent_id DESC LIMIT 1", true);
	check(rows.length === 1 && rows[0].status === "basket_created", "checkout should persist a basket-created intent");
	return rows[0];
}

async function probeCommerceHealth(openRscDb, overrides = {}) {
	const probePort = await freePort();
	const probeBase = `http://127.0.0.1:${probePort}`;
	const probeDataDir = join(tmp, `health-${probePort}`);
	await mkdir(probeDataDir, { recursive: true });
	const child = spawn(process.execPath, [join(repoRoot, "web/portal/dev-server.mjs")], {
		cwd: repoRoot,
		env: {
			...process.env,
			PORT: String(probePort),
			PORTAL_BIND_HOST: "127.0.0.1",
			PORTAL_DATA_DIR: probeDataDir,
			PORTAL_OPENRSC_DB: openRscDb,
			PORTAL_PUBLIC_MODE: "1",
			PORTAL_LAUNCH_SIGNUP_MODE: "1",
			PORTAL_PUBLIC_ORIGIN: probeBase,
			PORTAL_ABUSE_HASH_SALT: "commerce-health-abuse-salt-1234567890",
			PORTAL_ENABLE_TEST_FAULTS: "1",
			PORTAL_TEBEX_API_BASE: `http://127.0.0.1:${mockServer.address().port}/api`,
			PORTAL_TEBEX_PUBLIC_TOKEN: publicToken,
			PORTAL_TEBEX_PRIVATE_KEY: privateKey,
			PORTAL_TEBEX_WEBHOOK_SECRET: webhookSecret,
			PORTAL_TEBEX_SUBSCRIPTION_CARD_PACKAGE_ID: packageId,
			PORTAL_TEBEX_EXPECTED_CURRENCY: "USD",
			PORTAL_TEBEX_EXPECTED_PRICE_MINOR: "500",
			...overrides
		},
		stdio: "ignore"
	});
	try {
		for (let attempt = 0; attempt < 100; attempt += 1) {
			try {
				return await jsonRequest(`${probeBase}/api/health`);
			} catch (error) {
				await new Promise((resolveWait) => setTimeout(resolveWait, 25));
			}
		}
		throw new Error("health probe portal did not start");
	} finally {
		if (child.exitCode === null && child.signalCode === null) {
			const exited = new Promise((resolveExit) => child.once("exit", resolveExit));
			child.kill("SIGTERM");
			await exited;
		}
	}
}

try {
	await mkdir(dataDir, { recursive: true });
	execFileSync("sqlite3", [dbPath], {
		input: `
			CREATE TABLE player_cache (
				playerID INTEGER NOT NULL,
				type INTEGER NOT NULL,
				key TEXT NOT NULL,
				value TEXT NOT NULL,
				dbid INTEGER PRIMARY KEY AUTOINCREMENT
			);
		`,
		encoding: "utf8"
	});
	execFileSync("sqlite3", [dbPath], {
		input: await readFile(join(repoRoot, "server/database/sqlite/patches/2026_07_11_add_portal_commerce.sql"), "utf8"),
		encoding: "utf8"
	});
	await writeFile(join(dataDir, "dev-store.json"), `${JSON.stringify({
		nextIds: { account: 2, session: 2 },
		accounts: [{
			id: 1,
			emailCanonical: "commerce@example.com",
			emailDisplay: "commerce@example.com",
			displayName: "Commerce Fixture",
			passwordHash: null,
			status: "active",
			subscriptionExpiresAt: 0,
			createdAt: new Date().toISOString(),
			updatedAt: new Date().toISOString()
		}],
		sessions: [{
			id: 1,
			accountId: 1,
			tokenHash: createHash("sha256").update(sessionToken).digest("base64url"),
			createdAt: new Date().toISOString(),
			expiresAt: Date.now() + 60 * 60 * 1000,
			lastSeenAt: new Date().toISOString()
		}]
	}, null, 2)}\n`);

	let basketCounter = 0;
	let packageControlsLocked = false;
	mockServer = createServer(async (request, response) => {
		try {
			check(request.headers.authorization === expectedAuthorization, "Tebex requests must use public-token/private-key Basic auth");
			const url = new URL(request.url, "http://mock.invalid");
			if (request.method === "GET" && url.pathname === `/api/accounts/${publicToken}/packages/${packageId}`) {
				response.writeHead(200, { "content-type": "application/json" });
				response.end(JSON.stringify({ data: {
					id: Number(packageId),
					type: "single",
					currency: "USD",
					base_price: 5,
					total_price: 5.4,
					disable_quantity: packageControlsLocked,
					disable_gifting: packageControlsLocked
				} }));
				return;
			}
			const chunks = [];
			for await (const chunk of request) chunks.push(chunk);
			const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString("utf8")) : {};
			if (request.method === "POST" && url.pathname === `/api/accounts/${publicToken}/baskets`) {
				check(Object.keys(body.custom || {}).join(",") === "voidscape_intent", "basket custom data must contain only the opaque intent");
				check(typeof body.custom.voidscape_intent === "string" && body.custom.voidscape_intent.length >= 40, "basket intent should be opaque");
				check(body.ip_address === "127.0.0.1", "backend basket creation must pass the customer's required IPv4 address");
				check(!("username" in body) && !("email" in body) && !("ip" in body), "checkout must keep account PII out of basket custom fields");
				basketCounter += 1;
				const ident = `basket-${basketCounter}`;
				response.writeHead(200, { "content-type": "application/json" });
				response.end(JSON.stringify({ data: { ident, custom: body.custom, packages: [], links: { checkout: `http://127.0.0.1:${mockServer.address().port}/checkout/${ident}` } } }));
				return;
			}
			const addMatch = /^\/api\/baskets\/(basket-\d+)\/packages$/.exec(url.pathname);
			if (request.method === "POST" && addMatch) {
				check(body.package_id === Number(packageId) && body.quantity === 1 && Object.keys(body).sort().join(",") === "custom,package_id,quantity", "basket must add exactly one allowlisted package at quantity one");
				check(body.custom && body.custom.voidscape_basket === addMatch[1] && typeof body.custom.voidscape_intent === "string", "package custom must bind the known basket and opaque intent");
				const intent = sqlite(`SELECT intent_id FROM portal_commerce_checkout_intents WHERE basket_id='${addMatch[1]}'`, true)[0].intent_id;
				response.writeHead(200, { "content-type": "application/json" });
				response.end(JSON.stringify({ data: {
					ident: addMatch[1],
					currency: "USD",
					base_price: 5,
					sales_tax: 0.4,
					total_price: 5.4,
					custom: { voidscape_intent: intent },
					packages: [{ id: Number(packageId), in_basket: { quantity: 1 }, type: "single" }],
					links: { checkout: `http://127.0.0.1:${mockServer.address().port}/checkout/${addMatch[1]}` }
				} }));
				return;
			}
			response.writeHead(404, { "content-type": "application/json" });
			response.end(JSON.stringify({ error: "mock_not_found" }));
		} catch (error) {
			response.writeHead(500, { "content-type": "application/json" });
			response.end(JSON.stringify({ error: error.message }));
		}
	});
	await new Promise((resolveListen) => mockServer.listen(0, "127.0.0.1", resolveListen));

	const portalPort = await freePort();
	const portalBase = `http://127.0.0.1:${portalPort}`;
	portalProcess = spawn(process.execPath, [join(repoRoot, "web/portal/dev-server.mjs")], {
		cwd: repoRoot,
		env: {
			...process.env,
			PORT: String(portalPort),
			PORTAL_BIND_HOST: "127.0.0.1",
			PORTAL_DATA_DIR: dataDir,
			PORTAL_OPENRSC_DB: dbPath,
			PORTAL_PUBLIC_MODE: "1",
			PORTAL_LAUNCH_SIGNUP_MODE: "1",
			PORTAL_PUBLIC_ORIGIN: portalBase,
			PORTAL_ADMIN_TOKEN: adminToken,
			PORTAL_ABUSE_HASH_SALT: "commerce-abuse-salt-fixture-1234567890",
			PORTAL_ENABLE_TEST_FAULTS: "1",
			PORTAL_TEST_TEBEX_WEBHOOK_FAIL_ONCE: "1",
			PORTAL_TEBEX_API_BASE: `http://127.0.0.1:${mockServer.address().port}/api`,
			PORTAL_TEBEX_PUBLIC_TOKEN: publicToken,
			PORTAL_TEBEX_PRIVATE_KEY: privateKey,
			PORTAL_TEBEX_WEBHOOK_SECRET: webhookSecret,
			PORTAL_TEBEX_SUBSCRIPTION_CARD_PACKAGE_ID: packageId,
			PORTAL_TEBEX_EXPECTED_CURRENCY: "USD",
			PORTAL_TEBEX_EXPECTED_PRICE_MINOR: "500"
		},
		stdio: ["ignore", "pipe", "pipe"]
	});
	let portalOutput = "";
	portalProcess.stdout.on("data", (chunk) => { portalOutput += chunk; });
	portalProcess.stderr.on("data", (chunk) => { portalOutput += chunk; });
	let health;
	for (let attempt = 0; attempt < 100; attempt += 1) {
		try {
			health = await jsonRequest(`${portalBase}/api/health`);
			break;
		} catch (error) {
			await new Promise((resolveWait) => setTimeout(resolveWait, 50));
		}
	}
	if (!health) throw new Error(`portal did not start:\n${portalOutput}`);
	check(health.config.commerce.configured === true, "health should report configured commerce");
	check(Object.values(health.config.commerce).every((value) => typeof value === "boolean"), "commerce health must expose booleans only");
	check(!JSON.stringify(health).includes(privateKey) && !JSON.stringify(health).includes(packageId), "health must not leak commerce values");
	check(health.config.commerce.operational === true && health.config.commerce.schemaReady === true, "health should prove the commerce schema is operational");

	await sendWebhook(portalBase, { id: "validation-fixture", type: "validation.webhook", subject: {} });
	const badSignaturePayload = paymentPayload({ webhookId: "bad-signature", transactionId: "tbx-bad-signature", intent: "none", basket: "none", createdAtMs: Date.now() });
	await sendWebhook(portalBase, badSignaturePayload, 401, "0".repeat(64));
	check(sqlite("SELECT COUNT(*) FROM portal_commerce_webhook_events WHERE webhook_id='bad-signature'") === "0", "bad signatures must never enter the ledger");

	await jsonRequest(`${portalBase}/api/payments/subscription-cards/checkout`, {
		method: "POST",
		headers: { authorization: `Bearer ${sessionToken}`, "content-type": "application/json" },
		body: "{}"
	}, 502);
	check(sqlite("SELECT COUNT(*) FROM portal_commerce_checkout_intents") === "0", "unlocked quantity/gifting controls must reject checkout before creating an intent");
	packageControlsLocked = true;
	const firstIntent = await createCheckout(portalBase);
	const firstPaidAt = Number(firstIntent.created_at_ms) + 1000;
	const completed = paymentPayload({
		webhookId: "completed-fixture",
		transactionId: "tbx-completed-fixture",
		intent: firstIntent.intent_id,
		basket: firstIntent.basket_id,
		createdAtMs: firstPaidAt,
		customShape: "subject"
	});
	await sendWebhook(portalBase, completed, 503);
	check(sqlite("SELECT status FROM portal_commerce_webhook_events WHERE webhook_id='completed-fixture'") === "failed", "transient processing failure should be durable");
	check(sqlite("SELECT COUNT(*) FROM portal_commerce_entitlements") === "0", "failed processing must not create an entitlement");
	await sendWebhook(portalBase, completed, 200);
	await sendWebhook(portalBase, completed, 200);
	check(sqlite("SELECT COUNT(*) FROM portal_commerce_payments WHERE transaction_id='tbx-completed-fixture'") === "1", "webhook replay must create one payment");
	check(sqlite("SELECT COUNT(*) FROM portal_commerce_entitlements WHERE transaction_id='tbx-completed-fixture'") === "1", "webhook replay must create one card entitlement");
	const taxedAmounts = sqlite("SELECT expected_amount_minor, product_paid_amount_minor, transaction_paid_amount_minor, tax_and_adjustments_minor FROM portal_commerce_payments WHERE transaction_id='tbx-completed-fixture'", true)[0];
	check(taxedAmounts.expected_amount_minor === 500 && taxedAmounts.product_paid_amount_minor === 500 && taxedAmounts.transaction_paid_amount_minor === 540 && taxedAmounts.tax_and_adjustments_minor === 40, "transaction adjustments must be stored without being mislabeled as configured product price or customer tax");

	const mismatchPackage = paymentPayload({
		webhookId: "package-mismatch",
		transactionId: "tbx-package-mismatch",
		intent: firstIntent.intent_id,
		basket: firstIntent.basket_id,
		createdAtMs: firstPaidAt,
		productId: "999999"
	});
	await sendWebhook(portalBase, mismatchPackage, 422);
	const mismatchPrice = paymentPayload({
		webhookId: "price-mismatch",
		transactionId: "tbx-price-mismatch",
		intent: firstIntent.intent_id,
		basket: firstIntent.basket_id,
		createdAtMs: firstPaidAt,
		baseAmount: 4.99,
		productPaidAmount: 4.99,
		transactionPaidAmount: 5.39
	});
	await sendWebhook(portalBase, mismatchPrice, 422);
	const missingCustom = paymentPayload({
		webhookId: "custom-missing",
		transactionId: "tbx-custom-missing",
		intent: firstIntent.intent_id,
		basket: firstIntent.basket_id,
		createdAtMs: firstPaidAt,
		customShape: "none"
	});
	await sendWebhook(portalBase, missingCustom, 422);
	check(sqlite("SELECT COUNT(*) FROM portal_commerce_entitlements") === "1", "mismatched payment facts must not create cards");

	const expiredIntent = await createCheckout(portalBase);
	sqlite(`UPDATE portal_commerce_checkout_intents SET expires_at_ms=created_at_ms+100 WHERE intent_id='${expiredIntent.intent_id}'`);
	const expired = paymentPayload({
		webhookId: "expired-intent",
		transactionId: "tbx-expired-intent",
		intent: expiredIntent.intent_id,
		basket: expiredIntent.basket_id,
		createdAtMs: Number(expiredIntent.created_at_ms) + 1000
	});
	await sendWebhook(portalBase, expired, 200);
	check(intentRow(expiredIntent.intent_id).status === "completed" && intentRow(expiredIntent.intent_id).last_error_code === "late_signed_settlement", "exact signed late payment should settle with an admin-visible late marker");
	check(sqlite("SELECT state FROM portal_commerce_entitlements WHERE transaction_id='tbx-expired-intent'") === "pending", "a charged exact late payment must remain claimable");

	for (const [id, type, expectedState] of [
		["dispute-open-pending", "payment.dispute.opened", "frozen"],
		["dispute-won-pending", "payment.dispute.won", "pending"],
		["dispute-closed-after-won", "payment.dispute.closed", "pending"],
		["refund-pending", "payment.refunded", "revoked"]
	]) {
		await sendWebhook(portalBase, paymentPayload({
			webhookId: id,
			type,
			transactionId: "tbx-completed-fixture",
			intent: firstIntent.intent_id,
			basket: firstIntent.basket_id,
			createdAtMs: firstPaidAt
		}));
		check(sqlite("SELECT state FROM portal_commerce_entitlements WHERE transaction_id='tbx-completed-fixture'") === expectedState, `${type} should move pending entitlement to ${expectedState}`);
	}
	await sendWebhook(portalBase, paymentPayload({
		webhookId: "dispute-won-after-refund",
		type: "payment.dispute.won",
		transactionId: "tbx-completed-fixture",
		intent: firstIntent.intent_id,
		basket: firstIntent.basket_id,
		createdAtMs: firstPaidAt
	}));
	check(sqlite("SELECT status FROM portal_commerce_payments WHERE transaction_id='tbx-completed-fixture'") === "refunded", "dispute won must not reopen a terminal refund");
	check(sqlite("SELECT state FROM portal_commerce_entitlements WHERE transaction_id='tbx-completed-fixture'") === "revoked", "dispute won must not restore a refunded pending card");

	await sendWebhook(portalBase, paymentPayload({
		webhookId: "dispute-lost-late-settlement",
		type: "payment.dispute.lost",
		transactionId: "tbx-expired-intent",
		intent: expiredIntent.intent_id,
		basket: expiredIntent.basket_id,
		createdAtMs: Number(expiredIntent.created_at_ms) + 1000
	}));
	await sendWebhook(portalBase, paymentPayload({
		webhookId: "dispute-won-after-lost",
		type: "payment.dispute.won",
		transactionId: "tbx-expired-intent",
		intent: expiredIntent.intent_id,
		basket: expiredIntent.basket_id,
		createdAtMs: Number(expiredIntent.created_at_ms) + 1000
	}));
	check(sqlite("SELECT status FROM portal_commerce_payments WHERE transaction_id='tbx-expired-intent'") === "dispute_lost", "dispute won must not reopen a terminal lost dispute");
	check(sqlite("SELECT state FROM portal_commerce_entitlements WHERE transaction_id='tbx-expired-intent'") === "revoked", "dispute won must not restore a lost-dispute card");

	const claimedIntent = await createCheckout(portalBase);
	const claimedPaidAt = Number(claimedIntent.created_at_ms) + 1000;
	const productCustomCompleted = paymentPayload({
		webhookId: "product-custom-completed",
		transactionId: "tbx-product-custom",
		intent: claimedIntent.intent_id,
		basket: claimedIntent.basket_id,
		createdAtMs: claimedPaidAt,
		customShape: "product",
		productPaidAmount: 4.5,
		transactionPaidAmount: 4.95
	});
	await sendWebhook(portalBase, productCustomCompleted);
	sqlite("UPDATE portal_commerce_entitlements SET state='claimed', claimed_player_id=77, claimed_item_id=123456, claimed_at_ms=2000 WHERE transaction_id='tbx-product-custom'");
	await sendWebhook(portalBase, paymentPayload({
		webhookId: "dispute-open-claimed",
		type: "payment.dispute.opened",
		transactionId: "tbx-product-custom",
		intent: claimedIntent.intent_id,
		basket: claimedIntent.basket_id,
		createdAtMs: claimedPaidAt,
		customShape: "product",
		productPaidAmount: 4.5,
		transactionPaidAmount: 4.95
	}));
	check(sqlite("SELECT state FROM portal_commerce_entitlements WHERE transaction_id='tbx-product-custom'") === "review", "claimed disputed cards should go to review without clawback");
	check(sqlite("SELECT claimed_item_id FROM portal_commerce_entitlements WHERE transaction_id='tbx-product-custom'") === "123456", "dispute must preserve claimed inventory provenance");
	for (const [id, type] of [
		["dispute-closed-before-won-claimed", "payment.dispute.closed"],
		["dispute-won-after-closed-claimed", "payment.dispute.won"]
	]) {
		await sendWebhook(portalBase, paymentPayload({
			webhookId: id,
			type,
			transactionId: "tbx-product-custom",
			intent: claimedIntent.intent_id,
			basket: claimedIntent.basket_id,
			createdAtMs: claimedPaidAt,
			customShape: "product",
			productPaidAmount: 4.5,
			transactionPaidAmount: 4.95
		}));
	}
	check(sqlite("SELECT state FROM portal_commerce_entitlements WHERE transaction_id='tbx-product-custom'") === "claimed", "won after closed should restore claimed provenance instead of leaving an inconsistent review");
	await sendWebhook(portalBase, paymentPayload({
		webhookId: "dispute-reopen-claimed",
		type: "payment.dispute.opened",
		transactionId: "tbx-product-custom",
		intent: claimedIntent.intent_id,
		basket: claimedIntent.basket_id,
		createdAtMs: claimedPaidAt,
		customShape: "product",
		productPaidAmount: 4.5,
		transactionPaidAmount: 4.95
	}));
	await sendWebhook(portalBase, paymentPayload({
		webhookId: "refund-after-claimed-dispute",
		type: "payment.refunded",
		transactionId: "tbx-product-custom",
		intent: claimedIntent.intent_id,
		basket: claimedIntent.basket_id,
		createdAtMs: claimedPaidAt,
		customShape: "product",
		productPaidAmount: 4.5,
		transactionPaidAmount: 4.95
	}));
	await sendWebhook(portalBase, paymentPayload({
		webhookId: "dispute-won-after-claimed-refund",
		type: "payment.dispute.won",
		transactionId: "tbx-product-custom",
		intent: claimedIntent.intent_id,
		basket: claimedIntent.basket_id,
		createdAtMs: claimedPaidAt,
		customShape: "product",
		productPaidAmount: 4.5,
		transactionPaidAmount: 4.95
	}));
	check(sqlite("SELECT status FROM portal_commerce_payments WHERE transaction_id='tbx-product-custom'") === "refunded", "refund must remain terminal after a later dispute-won event");
	check(sqlite("SELECT state FROM portal_commerce_entitlements WHERE transaction_id='tbx-product-custom'") === "review", "a claimed refunded card must remain in review without clawback");
	check(sqlite("SELECT review_reason FROM portal_commerce_entitlements WHERE transaction_id='tbx-product-custom'") === "refund_after_claim", "terminal refund reason must replace a reversible dispute reason");
	const discountedAmounts = sqlite("SELECT expected_amount_minor, product_paid_amount_minor, transaction_paid_amount_minor, tax_and_adjustments_minor FROM portal_commerce_payments WHERE transaction_id='tbx-product-custom'", true)[0];
	check(discountedAmounts.expected_amount_minor === 500 && discountedAmounts.product_paid_amount_minor === 450 && discountedAmounts.transaction_paid_amount_minor === 495 && discountedAmounts.tax_and_adjustments_minor === 45, "creator-authorized discount and transaction adjustments should be explicit in the ledger");

	const account = await jsonRequest(`${portalBase}/api/account`, { headers: { authorization: `Bearer ${sessionToken}` } });
	const paid = account.rewards && account.rewards.paidSubscriptionCards;
	check(paid && paid.pending === 0 && paid.claimed === 0 && paid.review === 1 && paid.revoked === 2, "account API should expose paid pending/claimed/review counts");
	await jsonRequest(`${portalBase}/api/payments/subscription-cards/checkout`, {
		method: "POST",
		headers: { authorization: `Bearer ${sessionToken}`, "content-type": "application/json" },
		body: JSON.stringify({ unexpected: true })
	}, 400);
	await jsonRequest(`${portalBase}/api/payments/subscription-cards/checkout`, {
		method: "POST",
		headers: { authorization: `Bearer ${sessionToken}`, "content-type": "application/json" },
		body: JSON.stringify({ padding: "x".repeat(1024 * 1024) })
	}, 413);
	await createCheckout(portalBase);
	await jsonRequest(`${portalBase}/api/payments/subscription-cards/checkout`, {
		method: "POST",
		headers: { authorization: `Bearer ${sessionToken}`, "content-type": "application/json" },
		body: "{}"
	}, 429);
	check(sqlite("SELECT COUNT(*) FROM portal_commerce_checkout_intents") === "4", "checkout rate limiting should stop provider/intent exhaustion after five attempts");
	await jsonRequest(`${portalBase}/api/admin/commerce`, {}, 403);
	const admin = await jsonRequest(`${portalBase}/api/admin/commerce`, { headers: { "x-portal-admin-token": adminToken } });
	check(admin.summary.payments === 3 && admin.summary.entitlements === 3, "admin commerce list should expose restricted ledger totals");
	const reconcileOne = await jsonRequest(`${portalBase}/api/admin/commerce/reconcile`, { method: "POST", headers: { "x-portal-admin-token": adminToken } });
	const reconcileTwo = await jsonRequest(`${portalBase}/api/admin/commerce/reconcile`, { method: "POST", headers: { "x-portal-admin-token": adminToken } });
	check(JSON.stringify(reconcileOne) === JSON.stringify(reconcileTwo), "commerce reconcile report should be deterministic");
	check(reconcileOne.mutated === false, "commerce reconcile endpoint must not fulfill or mutate cards");
	check(reconcileOne.anomalies.unsafeEntitlements.length === 0 && reconcileOne.anomalies.ledgerMismatches.length === 0, "terminal reversal sequences should remain internally reconcilable");

	const missingSchemaDb = join(tmp, "missing-commerce-schema.db");
	execFileSync("sqlite3", [missingSchemaDb, "CREATE TABLE placeholder (id INTEGER PRIMARY KEY);"]);
	const missingSchemaHealth = await probeCommerceHealth(missingSchemaDb);
	check(missingSchemaHealth.config.commerce.configured === true, "full env config should remain distinguishable from runtime readiness");
	check(missingSchemaHealth.config.commerce.schemaReady === false && missingSchemaHealth.config.commerce.operational === false, "health must fail closed when commerce tables are missing");
	check(missingSchemaHealth.config.publicReady === false && missingSchemaHealth.config.issues.includes("tebex_commerce_not_operational"), "missing commerce schema must fail the public-ready gate");
	const missingPathHealth = await probeCommerceHealth(join(tmp, "does-not-exist", "openrsc.db"));
	check(missingPathHealth.config.commerce.databaseReadable === false && missingPathHealth.config.commerce.operational === false, "health must fail closed for an unreadable game DB path");
	const weakSecretHealth = await probeCommerceHealth(dbPath, {
		PORTAL_TEBEX_PRIVATE_KEY: "test",
		PORTAL_TEBEX_WEBHOOK_SECRET: "changeme"
	});
	check(weakSecretHealth.config.commerce.privateKeyConfigured === false && weakSecretHealth.config.commerce.webhookSecretConfigured === false, "placeholder Tebex secrets must never be considered configured");
	check(weakSecretHealth.config.publicReady === false, "placeholder Tebex secrets must fail the public-ready gate");

	await writeFile(portalLog, portalOutput);
	process.stdout.write("portal commerce smoke: ok\n");
} finally {
	if (portalProcess) {
		if (portalProcess.exitCode === null && portalProcess.signalCode === null) {
			const exited = new Promise((resolveExit) => portalProcess.once("exit", resolveExit));
			portalProcess.kill("SIGTERM");
			await exited;
		}
	}
	if (mockServer) await new Promise((resolveClose) => mockServer.close(resolveClose));
	await rm(tmp, { recursive: true, force: true });
}
