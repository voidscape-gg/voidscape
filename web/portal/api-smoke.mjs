const port = Number(process.env.PORT || 8788);
const baseUrl = `http://127.0.0.1:${port}`;
const adminToken = process.env.PORTAL_ADMIN_TOKEN || "dev-admin";

let token = "";

const initialPublic = await api("/api/public");
assert(initialPublic.status.online === true, "public endpoint should expose online state");
assert(initialPublic.rates.baseCombat === 10, "public endpoint should expose base combat rate");
assert(initialPublic.rates.subscribedSkill === 3, "public endpoint should expose subscribed skill rate");
assert(Array.isArray(initialPublic.news) && initialPublic.news.length >= 3, "public endpoint should expose news");
assert(Array.isArray(initialPublic.market) && initialPublic.market.length >= 6, "public endpoint should expose market rows");
assert(Array.isArray(initialPublic.highscores) && initialPublic.highscores.length >= 6, "public endpoint should expose highscores");
assert(Array.isArray(initialPublic.activity) && initialPublic.activity.length >= 6, "public endpoint should expose activity");
assert(initialPublic.integrity && initialPublic.integrity.staffCommands, "public endpoint should expose integrity summary");
assert(typeof initialPublic.integrity.staffCommands.total24h === "number", "integrity summary should expose staff command totals");
assert(initialPublic.integrity.staffCommands.total24h >= 2, "integrity summary should count fixture staff commands");
assert(initialPublic.integrity.staffCommands.blocked24h >= 1, "integrity summary should count blocked fixture staff commands");
assert(initialPublic.integrity.itemProvenance && initialPublic.integrity.itemProvenance.staffMints24h >= 2, "integrity summary should count staff item mint receipts");
assert(initialPublic.integrity.itemProvenance.npcDrops24h >= 1, "integrity summary should count npc drop origin receipts");
assert(initialPublic.integrity.itemProvenance.transfers24h >= 1, "integrity summary should count movement receipts");
assert(initialPublic.integrity.itemProvenance.amount24h >= 6, "integrity summary should sum staff item mint amounts");
assert(initialPublic.integrity.economyScans && initialPublic.integrity.economyScans.flagged >= 1, "integrity summary should expose economy scan findings");
assert(initialPublic.integrity.privacy.includes("excludes IP addresses"), "integrity summary should describe public privacy limits");
assert(Array.isArray(initialPublic.integrity.build.artifacts), "integrity summary should expose build artifact proof");
assert(initialPublic.integrity.build.manifest && initialPublic.integrity.build.manifest.url === "/api/launcher/manifest.properties", "integrity summary should expose launcher manifest proof");
const launcherDownload = initialPublic.downloads.find((row) => row.slug === "launcher");
assert(launcherDownload && typeof launcherDownload.available === "boolean", "public endpoint should expose launcher download availability");
if (launcherDownload.available) {
	assert(/^[0-9a-f]{64}$/.test(launcherDownload.sha256 || ""), "available launcher download should expose a SHA-256 hash");
	const downloadResponse = await fetch(`${baseUrl}${launcherDownload.url}`);
	assert(downloadResponse.ok, "available launcher download should be served");
	assert((downloadResponse.headers.get("content-disposition") || "").includes(".jar"), "launcher download should include jar attachment disposition");
	assert(Number(downloadResponse.headers.get("content-length") || 0) > 1024, "launcher download should include a non-empty artifact");
}

const androidDownload = initialPublic.downloads.find((row) => row.slug === "android-apk");
assert(androidDownload && typeof androidDownload.available === "boolean", "public endpoint should expose Android APK download availability");
if (androidDownload.available) {
	assert(/^[0-9a-f]{64}$/.test(androidDownload.sha256 || ""), "available Android download should expose a SHA-256 hash");
	const downloadResponse = await fetch(`${baseUrl}${androidDownload.url}`);
	assert(downloadResponse.ok, "available Android APK download should be served");
	assert((downloadResponse.headers.get("content-disposition") || "").includes(".apk"), "Android download should include APK attachment disposition");
	assert(Number(downloadResponse.headers.get("content-length") || 0) > 1024, "Android download should include a non-empty artifact");
}

const clientRuntimeDownload = initialPublic.downloads.find((row) => row.slug === "client-runtime");
assert(clientRuntimeDownload && clientRuntimeDownload.publicDownload === false, "local public payload should expose private launcher runtime metadata");
if (clientRuntimeDownload.available) {
	assert(/^[0-9a-f]{64}$/.test(clientRuntimeDownload.sha256 || ""), "available client runtime should expose a SHA-256 hash");
	const downloadResponse = await fetch(`${baseUrl}${clientRuntimeDownload.url}`);
	assert(downloadResponse.ok, "available client runtime download should be served");
	assert((downloadResponse.headers.get("content-disposition") || "").includes(".jar"), "client runtime should include jar attachment disposition");
	assert(Number(downloadResponse.headers.get("content-length") || 0) > 1024, "client runtime should include a non-empty artifact");
	const manifestHead = await fetch(`${baseUrl}/api/launcher/manifest.properties`, { method: "HEAD" });
	assert(manifestHead.ok, "available client runtime should expose launcher manifest HEAD");
	const manifestResponse = await fetch(`${baseUrl}/api/launcher/manifest.properties`);
	assert(manifestResponse.ok, "available client runtime should expose launcher manifest");
	const manifest = await manifestResponse.text();
	assert(/^version=.+$/m.test(manifest), "launcher manifest should include a version");
	assert(/^file\.\d+\.path=VoidscapeClient\.jar$/m.test(manifest), "launcher manifest should include the client runtime jar path");
	assert(/^file\.\d+\.sha256=[0-9a-f]{64}$/m.test(manifest), "launcher manifest should include SHA-256 hashes");
	assert(manifest.includes(`${baseUrl}/downloads/client-runtime`), "launcher manifest should point at the client runtime download");
}

const googleOauthStub = await api("/api/oauth/google/start", { expectStatus: 501 });
assert(googleOauthStub.error === "google_oauth_not_configured", "production Google OAuth stub should be explicit");
const paymentStub = await api("/api/payments/subscription-cards/checkout", {
	method: "POST",
	expectStatus: 501
});
assert(paymentStub.error === "payments_not_configured", "production payment stub should be explicit");

const integrity = await api("/api/integrity");
assert(integrity.staffCommands && typeof integrity.staffCommands.total24h === "number", "integrity endpoint should expose staff command totals");
assert(integrity.staffCommands.total24h >= 2, "integrity endpoint should count fixture staff commands");
assert(integrity.staffCommands.blocked24h >= 1, "integrity endpoint should count blocked fixture staff commands");
assert(Array.isArray(integrity.staffCommands.categories), "integrity endpoint should expose staff command categories");
assert(Array.isArray(integrity.staffCommands.recent), "integrity endpoint should expose a sanitized recent list");
assert(integrity.itemProvenance && integrity.itemProvenance.staffMints24h >= 2, "integrity endpoint should expose staff item mint receipts");
assert(integrity.itemProvenance.npcDrops24h >= 1, "integrity endpoint should expose npc drop origin receipts");
assert(integrity.itemProvenance.transfers24h >= 1, "integrity endpoint should expose movement receipts");
assert(Array.isArray(integrity.itemProvenance.recent), "integrity endpoint should expose sanitized item receipt rows");
assert(integrity.economyScans && integrity.economyScans.flagged >= 1, "integrity endpoint should expose economy scan findings");
assert(integrity.build.status, "integrity endpoint should expose build evidence status");
assert(Array.isArray(integrity.build.artifacts), "integrity endpoint should expose build artifact hashes");
assert(integrity.build.source && integrity.build.source.repositoryUrl, "integrity endpoint should expose source metadata");

const snapshot = await api("/api/openrsc/characters/SmokeHero");
assert(snapshot.character.source === "openrsc-sqlite", "snapshot endpoint should report the OpenRSC source");
assert(snapshot.character.name === "SmokeHero", "snapshot endpoint should return the player username");
assert(snapshot.character.combat === 87, "snapshot endpoint should return combat level");
assert(snapshot.character.total === "1194", "snapshot endpoint should return total level");
assert(snapshot.character.status === "Varrock", "snapshot endpoint should summarize location");
assert(snapshot.character.title === "Crownless Conqueror", "snapshot endpoint should resolve the active title");
assert(snapshot.character.subscriptionState.active === false, "unlinked snapshot should not inherit subscription state");
assert(snapshot.character.subscriptionState.combatXpRate === 10, "unlinked snapshot should use base combat XP rate");
assert(Array.isArray(snapshot.character.equipment) && snapshot.character.equipment.length === 2, "snapshot endpoint should return wielded gear");
assert(snapshot.character.gear.includes("Iron 2-handed Sword"), "snapshot endpoint should resolve item definition names");

const founder = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokeHero",
		email: "smoke@example.com"
	}
});
assert(founder.founder.code, "founder reservation should return a code");
assert(founder.signup && /^VOID-[A-HJ-NP-TV-Z2-9]{4}-[A-HJ-NP-TV-Z2-9]{4}$/.test(founder.signup.code), "founder reservation should mint a VOID-XXXX-XXXX signup code");
assert(typeof founder.signup.redeemHint === "string" && founder.signup.redeemHint.includes("Void Subscription Vendor"), "signup state should explain in-game redemption at the vendor");
assert(founder.signup.syncedToGame === true, "signup code should sync to the configured game DB");

const sameEmailAgain = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokeHero",
		email: "smoke+resub@example.com"
	}
});
assert(sameEmailAgain.signup.code === founder.signup.code, "re-signing up with the same email should return the same signup code");

const takenName = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokeHero",
		email: "squatter@example.com"
	},
	expectStatus: 409
});
assert(takenName.error === "username_reserved", "a name reserved by another email should 409");

const invalidReferral = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "NoCode",
		email: "nocode@example.com",
		referrerCode: "VOID-NOPE"
	},
	expectStatus: 404
});
assert(invalidReferral.error === "referrer_not_found", "unknown founder referral codes should be rejected");

const selfReferral = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokeHero",
		email: "smoke@example.com",
		referrerCode: founder.founder.code
	},
	expectStatus: 400
});
assert(selfReferral.error === "self_referral_not_allowed", "a founder should not be able to credit their own invite");

const firstReferral = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokePal1",
		email: "friend-one@example.com",
		referrerCode: founder.founder.code
	}
});
assert(firstReferral.founder.referredBy.code === founder.founder.code, "referred reservations should remember the referrer code");
const referrerAfterFirst = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokeHero",
		email: "smoke@example.com"
	}
});
assert(referrerAfterFirst.founder.referralRewardCodes.length === 1, "one credited referral should mint one reward code for the referrer");
assert(/^VOID-[A-HJ-NP-TV-Z2-9]{4}-[A-HJ-NP-TV-Z2-9]{4}$/.test(referrerAfterFirst.founder.referralRewardCodes[0].code), "referral rewards should use the public signup-code format");
assert(referrerAfterFirst.founder.referralRewardCodes[0].syncedToGame === true, "referral reward codes should sync to the configured game DB");

const secondReferral = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokePal2",
		email: "friend-two@example.com",
		referrerCode: founder.founder.code
	}
});
assert(secondReferral.founder.referredBy.username === "SmokeHero", "referred reservations should expose the referrer username");
const referrerAfterSecond = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokeHero",
		email: "smoke@example.com"
	}
});
const referralRewardCodes = referrerAfterSecond.founder.referralRewardCodes.map((reward) => reward.code);
assert(referralRewardCodes.length === 2, "two credited referrals should mint two reward codes for the referrer");
assert(new Set(referralRewardCodes).size === referralRewardCodes.length, "referral reward codes should be unique");

const registered = await api("/api/accounts/register", {
	method: "POST",
	body: {
		username: "SmokeHero",
		email: "smoke@example.com",
		password: "correct-horse-battery",
		path: "warrior"
	}
});
token = registered.token;
assert(token, "registration should return a session token");
assert(registered.characters.length === 1, "registration should create the first character");
assert(registered.founder.starterCardUnlocked === true, "prelaunch signup should reserve the starter subscription card");
assert(registered.rewards.starterSubscriptionCards === 1, "reserved starter card should appear in account reward state");
assert(registered.characters[0].appearanceData.topColour === 4, "starter characters should expose default appearance data");

const heldReservation = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "HeldName",
		email: "held-by@example.com"
	}
});
assert(heldReservation.signup.code, "founder-only signups should still mint a code");
const heldNameBlocked = await api("/api/characters", {
	method: "POST",
	body: {
		name: "HeldName",
		gamePassword: "smokepass"
	},
	expectStatus: 409
});
assert(heldNameBlocked.error === "username_reserved", "character creation should respect founder reservations held by another email");

const originalToken = token;
const secondLogin = await api("/api/accounts/login", {
	method: "POST",
	body: {
		email: "smoke@example.com",
		password: "correct-horse-battery"
	}
});
const secondToken = secondLogin.token;
assert(secondToken && secondToken !== originalToken, "login should create a second session token");
token = originalToken;

const googleLinked = await api("/api/accounts/google/dev", {
	method: "POST",
	body: {
		email: "smoke@example.com",
		displayName: "Smoke Hero",
		subject: "google-smoke-subject",
		username: "SmokeHero"
	}
});
const googleToken = googleLinked.token;
assert(googleToken && googleToken !== originalToken, "Google dev sign-in should return a separate session token");
assert(googleLinked.auth.googleConnected === true, "Google dev sign-in should link a Google identity");
assert(googleLinked.auth.passwordEnabled === true, "linking Google should preserve existing password login");
assert(googleLinked.auth.providers.some((provider) => provider.provider === "google"), "account state should list Google provider");
token = googleToken;
const googleSessionAccount = await api("/api/account");
assert(googleSessionAccount.account.email === "smoke@example.com", "Google session should authenticate the same portal account");
assert(googleSessionAccount.characters.length === 1, "Google sign-in should not duplicate the existing character roster");
token = originalToken;

const invalidGoogleUsername = await api("/api/accounts/google/dev", {
	method: "POST",
	body: {
		username: "!",
		displayName: "Bad Google Name",
		subject: "google-invalid-name"
	},
	expectStatus: 400
});
assert(invalidGoogleUsername.error === "invalid_username", "Google reservation should reject invalid usernames");

const googleReserved = await api("/api/accounts/google/dev", {
	method: "POST",
	body: {
		username: "GoogHero",
		displayName: "Goog Hero",
		subject: "google-reserved-hero",
		path: "arcanist"
	}
});
const reservedToken = googleReserved.token;
const reservedCard = googleReserved.characters.find((character) => character.name === "GoogHero");
assert(reservedToken, "new Google reservation should return a session token");
assert(googleReserved.rewards.starterSubscriptionCards === 1, "new Google reservation should reserve the in-game starter card");
assert(reservedCard && reservedCard.source === "founder-reserved", "new Google reservation should create a reserved-name roster card");

const duplicateGoogleReservation = await api("/api/accounts/google/dev", {
	method: "POST",
	body: {
		username: "GoogHero",
		email: "other-googhero@example.com",
		displayName: "Other Goog Hero",
		subject: "google-other-googhero"
	},
	expectStatus: 409
});
assert(duplicateGoogleReservation.error === "username_reserved", "Google reservation should protect reserved names across accounts");

token = reservedToken;
const createdReserved = await api("/api/characters", {
	method: "POST",
	body: {
		name: "GoogHero",
		path: "arcanist",
		gamePassword: "GoogPass1"
	}
});
const createdReservedCard = createdReserved.characters.find((character) => character.name === "GoogHero");
assert(createdReserved.characters.length === 1, "creating a reserved Google name should replace the preview instead of adding a duplicate");
assert(createdReservedCard && createdReservedCard.id === reservedCard.id, "reserved Google character should keep the same portal roster id");
assert(createdReservedCard.source === "openrsc-sqlite-created", "reserved Google character should become a real OpenRSC-created save");
assert(createdReservedCard.linkStatus === "linked", "reserved Google character should link to the created game save");
token = originalToken;

const recovery = await api("/api/security/recovery-codes", { method: "POST" });
assert(Array.isArray(recovery.codes) && recovery.codes.length === 8, "security endpoint should return one-time recovery codes");
assert(recovery.security.recoveryCodes.activeCount === 8, "security state should count active recovery codes");

const badPasswordChange = await api("/api/security/password", {
	method: "POST",
	body: {
		currentPassword: "wrong-horse-battery",
		newPassword: "better-horse-battery"
	},
	expectStatus: 401
});
assert(badPasswordChange.error === "invalid_current_password", "password change should require the current password");

const passwordChanged = await api("/api/security/password", {
	method: "POST",
	body: {
		currentPassword: "correct-horse-battery",
		newPassword: "better-horse-battery"
	}
});
assert(passwordChanged.security.passwordChangedAt, "security state should expose password rotation time");

const revokedSessions = await api("/api/security/sessions/revoke-others", { method: "POST" });
assert(revokedSessions.security.sessions.length === 1, "ending other sessions should leave only the current session");
assert(revokedSessions.security.sessions[0].current === true, "remaining session should be the current session");

token = secondToken;
const revokedTokenCheck = await api("/api/account", { expectStatus: 401 });
assert(revokedTokenCheck.error === "invalid_session", "revoked session token should no longer authenticate");
token = originalToken;

const badRecovery = await api("/api/accounts/recover-password", {
	method: "POST",
	body: {
		email: "smoke@example.com",
		code: "VOID-BAD-BAD",
		newPassword: "recover-horse-battery"
	},
	expectStatus: 401
});
assert(badRecovery.error === "invalid_recovery_code", "password recovery should reject invalid recovery codes");

const recovered = await api("/api/accounts/recover-password", {
	method: "POST",
	body: {
		email: "smoke@example.com",
		code: recovery.codes[0],
		newPassword: "recover-horse-battery"
	}
});
const recoveredToken = recovered.token;
assert(recoveredToken && recoveredToken !== originalToken, "password recovery should issue a new session");
assert(recovered.security.recoveryCodes.activeCount === 7, "password recovery should consume exactly one recovery code");

token = originalToken;
const oldRecoverySession = await api("/api/account", { expectStatus: 401 });
assert(oldRecoverySession.error === "invalid_session", "password recovery should revoke existing sessions");
token = recoveredToken;

const linkStart = await api("/api/character-links/start", {
	method: "POST",
	body: { username: "SmokeHero" },
	expectStatus: 201
});
assert(linkStart.challenge.code, "link challenge should return a one-time code");
assert(linkStart.challenge.command.startsWith("::link "), "link challenge should return the in-game command");
assert(linkStart.character.id === 77, "link challenge should snapshot the existing OpenRSC player");

const badLink = await api("/api/character-links/simulate-verify", {
	method: "POST",
	body: {
		challengeId: linkStart.challenge.id,
		code: "VLINK-BADBAD"
	},
	expectStatus: 400
});
assert(badLink.error === "invalid_link_code", "link verification should reject an invalid code");

const linkedState = await api("/api/character-links/simulate-verify", {
	method: "POST",
	body: {
		challengeId: linkStart.challenge.id,
		code: linkStart.challenge.code
	}
});
const linkedCharacter = linkedState.characters.find((character) => character.name === "SmokeHero");
assert(linkedCharacter, "verified link should keep SmokeHero in the roster");
assert(linkedCharacter.linkStatus === "linked", "verified link should mark the roster character as linked");
assert(linkedCharacter.playerId === 77, "verified link should persist the OpenRSC player id");
assert(linkedCharacter.combat === 87, "verified link should replace preview combat with saved combat");
assert(linkedCharacter.title === "Crownless Conqueror", "verified link should persist saved title state");
assert(linkedCharacter.appearanceData.topColour === 8, "verified link should persist saved appearance data");
assert(Array.isArray(linkedCharacter.equipment) && linkedCharacter.equipment.length === 2, "verified link should persist wielded equipment state");

for (let i = 2; i <= 10; i += 1) {
	const roster = await api("/api/characters", {
		method: "POST",
		body: {
			name: `Smoke${i}`,
			path: i % 3 === 0 ? "forager" : i % 2 === 0 ? "arcanist" : "warrior",
			gamePassword: `SmokePass${i}`
		}
	});
	assert(roster.characters.length === i, `roster should contain ${i} characters`);
	const created = roster.characters.find((character) => character.name === `Smoke${i}`);
	assert(created && created.linkStatus === "linked", `Smoke${i} should be linked to a real game row`);
	assert(created.source === "openrsc-sqlite-created", `Smoke${i} should report the OpenRSC creation source`);
	assert(created.playerId > 77, `Smoke${i} should expose the OpenRSC player id`);
}

const overflow = await api("/api/characters", {
	method: "POST",
	body: {
		name: "Smoke11",
		path: "warrior"
	},
	expectStatus: 409
});
assert(overflow.error === "character_limit_reached", "API should enforce the 10-character cap");

const starterRewardWebRedeem = await api("/api/subscriptions/redeem-starter", {
	method: "POST",
	expectStatus: 409
});
assert(starterRewardWebRedeem.error === "claim_subscription_card_in_game", "starter card should be claimed in-game instead of redeemed on the portal");

const redeemed = await api("/api/subscriptions/redeem", {
	method: "POST",
	body: { code: "VOID-WEEK-SMOKE" }
});
assert(redeemed.account.subscription.active === true, "redeeming a card should activate subscription state");
assert(redeemed.account.subscription.combatXpRate === 11, "subscribed combat XP rate should be 11x");
assert(redeemed.account.subscription.skillXpRate === 3, "subscribed skill XP rate should be 3x");

const account = await api("/api/account");
assert(account.characters.length === 10, "account endpoint should return the full roster");

const adminLookup = await api("/api/admin/accounts?email=smoke@example.com", {
	headers: { "x-portal-admin-token": adminToken }
});
assert(adminLookup.accounts.length === 1, "admin account search should find SmokeHero by email");
assert(adminLookup.accounts[0].admin.abuseSignals.length >= 1, "admin account search should include abuse-signal audit context");

const adminGrant = await api("/api/admin/accounts/1/subscription", {
	method: "POST",
	headers: { "x-portal-admin-token": adminToken },
	body: { days: 1 }
});
assert(adminGrant.account.subscription.active === true, "admin subscription grant should keep the account subscribed");

const adminStarterGrant = await api("/api/admin/accounts/1/starter-card", {
	method: "POST",
	headers: { "x-portal-admin-token": adminToken },
	body: { action: "grant" }
});
assert(adminStarterGrant.rewards.starterSubscriptionCards >= 1, "admin starter-card grant should preserve or restore the starter card");

const abuseIp = "198.51.100.77";
const abuseOne = await api("/api/accounts/register", {
	method: "POST",
	headers: { "x-forwarded-for": abuseIp },
	body: {
		username: "AbuseOne",
		email: "abuse-one@example.com",
		password: "abuse-horse-battery"
	}
});
const abuseTwo = await api("/api/accounts/register", {
	method: "POST",
	headers: { "x-forwarded-for": abuseIp },
	body: {
		username: "AbuseTwo",
		email: "abuse-two@example.com",
		password: "abuse-horse-battery"
	}
});
const abuseThree = await api("/api/accounts/register", {
	method: "POST",
	headers: { "x-forwarded-for": abuseIp },
	body: {
		username: "AbuseThree",
		email: "abuse-three@example.com",
		password: "abuse-horse-battery"
	}
});
assert(abuseOne.rewards.starterSubscriptionCards === 1, "first account from an IP should keep the starter card");
assert(abuseTwo.rewards.starterSubscriptionCards === 1, "second account within the configured IP limit should keep the starter card");
assert(abuseThree.rewards.starterSubscriptionCards === 0, "account creation should continue but withhold the starter card after the IP limit");
assert(abuseThree.abuse.starterCard.status === "review", "withheld starter cards should be visible as review state");

const signupIp = "203.0.113.99";
const signupLimit = Math.max(1, Number(process.env.PORTAL_SIGNUP_IP_DAILY_LIMIT || 10));
for (let i = 1; i <= signupLimit; i += 1) {
	const limited = await api("/api/founder/reservations", {
		method: "POST",
		headers: { "x-forwarded-for": signupIp },
		body: {
			username: `RateGuy${i}`,
			email: `rate-guy-${i}@example.com`
		}
	});
	assert(limited.signup && limited.signup.code, `signup ${i} within the IP limit should mint a code`);
}
const overLimit = await api("/api/founder/reservations", {
	method: "POST",
	headers: { "x-forwarded-for": signupIp },
	body: {
		username: "RateGuyOver",
		email: "rate-guy-over@example.com"
	},
	expectStatus: 429
});
assert(overLimit.error === "rate_limited", "signups past the per-IP daily limit should be rate limited");
const loopbackAfterLimit = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "LocalAfter",
		email: "local-after-limit@example.com"
	}
});
assert(loopbackAfterLimit.signup && loopbackAfterLimit.signup.code, "loopback signups should be exempt from the IP limit");

const updatedPublic = await api("/api/public");
assert(updatedPublic.founderStats.reservations >= 1, "public endpoint should include reservation count");
assert(updatedPublic.founderStats.starterCardsUnlocked >= 1, "public endpoint should include starter-card unlock count");
assert(updatedPublic.activity[0].time === "Now", "public endpoint should include dynamic activity");

console.log(JSON.stringify({
	ok: true,
	account: account.account.email,
	characters: account.characters.length,
	starterReward: account.founder.starterCardUnlocked,
	snapshot: snapshot.character.name,
	linked: linkedCharacter.name,
	subscription: account.account.subscription.label,
	publicActivity: updatedPublic.activity.length
}, null, 2));

async function api(path, options = {}) {
	const response = await fetch(`${baseUrl}${path}`, {
		method: options.method || "GET",
		headers: {
			"content-type": "application/json",
			...(token ? { authorization: `Bearer ${token}` } : {}),
			...(options.headers || {})
		},
		body: options.body ? JSON.stringify(options.body) : undefined
	});
	const json = await response.json();
	const expected = options.expectStatus || (options.method === "POST" ? [200, 201] : [200]);
	const expectedStatuses = Array.isArray(expected) ? expected : [expected];
	if (!expectedStatuses.includes(response.status)) {
		throw new Error(`${path} returned ${response.status}: ${JSON.stringify(json)}`);
	}
	return json;
}

function assert(condition, message) {
	if (!condition) throw new Error(message);
}
