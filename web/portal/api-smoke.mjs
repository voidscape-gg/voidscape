const port = Number(process.env.PORT || 8788);
const baseUrl = `http://127.0.0.1:${port}`;

let token = "";

const initialPublic = await api("/api/public");
assert(initialPublic.status.online === true, "public endpoint should expose online state");
assert(initialPublic.rates.baseCombat === 7, "public endpoint should expose base combat rate");
assert(initialPublic.rates.subscribedSkill === 6, "public endpoint should expose subscribed skill rate");
assert(Array.isArray(initialPublic.news) && initialPublic.news.length >= 3, "public endpoint should expose news");
assert(Array.isArray(initialPublic.market) && initialPublic.market.length >= 6, "public endpoint should expose market rows");
assert(Array.isArray(initialPublic.highscores) && initialPublic.highscores.length >= 6, "public endpoint should expose highscores");
assert(Array.isArray(initialPublic.activity) && initialPublic.activity.length >= 6, "public endpoint should expose activity");
const pcDownload = initialPublic.downloads.find((row) => row.label === "PC client");
assert(pcDownload && typeof pcDownload.available === "boolean", "public endpoint should expose PC client download availability");
if (pcDownload.available) {
	const downloadResponse = await fetch(`${baseUrl}${pcDownload.url}`);
	assert(downloadResponse.ok, "available PC client download should be served");
	assert((downloadResponse.headers.get("content-disposition") || "").includes(".jar"), "download should include jar attachment disposition");
	assert(Number(downloadResponse.headers.get("content-length") || 0) > 1024, "download should include a non-empty artifact");
}

const snapshot = await api("/api/openrsc/characters/SmokeHero");
assert(snapshot.character.source === "openrsc-sqlite", "snapshot endpoint should report the OpenRSC source");
assert(snapshot.character.name === "SmokeHero", "snapshot endpoint should return the player username");
assert(snapshot.character.combat === 87, "snapshot endpoint should return combat level");
assert(snapshot.character.total === "1194", "snapshot endpoint should return total level");
assert(snapshot.character.status === "Lumbridge", "snapshot endpoint should summarize location");
assert(snapshot.character.title === "Crownless Conqueror", "snapshot endpoint should resolve the active title");
assert(snapshot.character.subscriptionState.active === true, "snapshot endpoint should resolve subscription state");
assert(snapshot.character.subscriptionState.combatXpRate === 10, "snapshot endpoint should include subscribed combat XP rate");
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

const secondReferral = await api("/api/founder/reservations", {
	method: "POST",
	body: {
		username: "SmokePal2",
		email: "friend-two@example.com",
		referrerCode: founder.founder.code
	}
});
assert(secondReferral.founder.referredBy.username === "SmokeHero", "referred reservations should expose the referrer username");

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
assert(registered.founder.freeSubscriptionUnlocked === true, "two real referrals should unlock the founder reward");
assert(registered.rewards.founderFreeSubscriptions === 1, "founder reward should appear in account reward state");
assert(registered.characters[0].appearanceData.topColour === 4, "starter characters should expose default appearance data");

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
			path: i % 3 === 0 ? "forager" : i % 2 === 0 ? "arcanist" : "warrior"
		}
	});
	assert(roster.characters.length === i, `roster should contain ${i} characters`);
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

const founderRewardRedeemed = await api("/api/subscriptions/redeem-founder", {
	method: "POST"
});
assert(founderRewardRedeemed.account.subscription.active === true, "founder reward should activate subscription state");
assert(founderRewardRedeemed.rewards.founderFreeSubscriptions === 0, "redeeming founder reward should consume the free card");

const founderRewardAgain = await api("/api/subscriptions/redeem-founder", {
	method: "POST",
	expectStatus: 404
});
assert(founderRewardAgain.error === "founder_reward_not_available", "founder reward should only be consumed once");

const redeemed = await api("/api/subscriptions/redeem", {
	method: "POST",
	body: { code: "VOID-WEEK-SMOKE" }
});
assert(redeemed.account.subscription.active === true, "redeeming a card should activate subscription state");
assert(redeemed.account.subscription.combatXpRate === 10, "subscribed combat XP rate should be 10x");
assert(redeemed.account.subscription.skillXpRate === 6, "subscribed skill XP rate should be 6x");

const account = await api("/api/account");
assert(account.characters.length === 10, "account endpoint should return the full roster");

const updatedPublic = await api("/api/public");
assert(updatedPublic.founderStats.reservations >= 1, "public endpoint should include reservation count");
assert(updatedPublic.founderStats.freeSubCardsUnlocked >= 1, "public endpoint should include founder reward unlock count");
assert(updatedPublic.activity[0].time === "Now", "public endpoint should include dynamic activity");

console.log(JSON.stringify({
	ok: true,
	account: account.account.email,
	characters: account.characters.length,
	founderReward: account.founder.freeSubscriptionUnlocked,
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
			...(token ? { authorization: `Bearer ${token}` } : {})
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
