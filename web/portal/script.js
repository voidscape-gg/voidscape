(function () {
	"use strict";

	var shell = document.querySelector(".portal-shell");
	var title = document.getElementById("view-title");
	var menuToggle = document.getElementById("menu-toggle");
	var workspace = document.querySelector(".workspace");
	var views = Array.prototype.slice.call(document.querySelectorAll(".view"));
	var viewLinks = Array.prototype.slice.call(document.querySelectorAll("[data-view-link]"));
	var landingScrollButtons = Array.prototype.slice.call(document.querySelectorAll("[data-landing-scroll]"));
	var prelaunchAuthButtons = Array.prototype.slice.call(document.querySelectorAll("[data-prelaunch-auth-cta]"));
	var discordLoginButtons = Array.prototype.slice.call(document.querySelectorAll("[data-discord-login]"));
	var whitepaperJumpButtons = Array.prototype.slice.call(document.querySelectorAll("[data-whitepaper-jump]"));
	var trustTabs = Array.prototype.slice.call(document.querySelectorAll("[data-trust-tab]"));
	var trustPanels = Array.prototype.slice.call(document.querySelectorAll("[data-trust-panel]"));
	var integrityUpdated = document.getElementById("integrity-updated");
	var integrityStaffTotal = document.getElementById("integrity-staff-total");
	var integrityStaffDetail = document.getElementById("integrity-staff-detail");
	var integrityBlockedTotal = document.getElementById("integrity-blocked-total");
	var integrityBlockedDetail = document.getElementById("integrity-blocked-detail");
	var integrityBuildStatus = document.getElementById("integrity-build-status");
	var integrityBuildDetail = document.getElementById("integrity-build-detail");
	var integrityItemsStatus = document.getElementById("integrity-items-status");
	var integrityItemsDetail = document.getElementById("integrity-items-detail");
	var trustPasswordsStatus = document.getElementById("trust-passwords-status");
	var trustPasswordsList = document.getElementById("trust-passwords-list");
	var trustSourceStatus = document.getElementById("trust-source-status");
	var trustSourceList = document.getElementById("trust-source-list");
	var trustSourceBoard = document.getElementById("trust-source-board");
	var trustSourceRepo = document.getElementById("trust-source-repo");
	var trustSourceCommit = document.getElementById("trust-source-commit");
	var trustSourceManifest = document.getElementById("trust-source-manifest");
	var trustBuildArtifacts = document.getElementById("trust-build-artifacts");
	var trustStaffStatus = document.getElementById("trust-staff-status");
	var trustStaffList = document.getElementById("trust-staff-list");
	var trustItemsStatus = document.getElementById("trust-items-status");
	var trustItemsList = document.getElementById("trust-items-list");
	var trustItemsBoard = document.getElementById("trust-items-board");
	var trustItemsBoardTotal = document.getElementById("trust-items-board-total");
	var trustItemsSources = document.getElementById("trust-items-sources");
	var trustItemsRecent = document.getElementById("trust-items-recent");
	var trustScansStatus = document.getElementById("trust-scans-status");
	var trustScansList = document.getElementById("trust-scans-list");
	var whitepaperLightbox = document.getElementById("whitepaper-lightbox");
	var whitepaperLightboxImage = document.getElementById("whitepaper-lightbox-image");
	var whitepaperLightboxCaption = document.getElementById("whitepaper-lightbox-caption");
	var whitepaperLightboxClose = document.getElementById("whitepaper-lightbox-close");
	var characterCards = document.getElementById("character-cards");
	var rankTable = document.getElementById("rank-table");
	var marketTable = document.getElementById("market-table");
	var worldFeed = document.getElementById("world-feed");
	var activeCharacterTitle = document.getElementById("active-character-title");
	var activeCharacterImage = document.getElementById("active-character-image");
	var activeCharacterDoll = document.getElementById("active-character-doll");
	var activeCharacterSource = document.getElementById("active-character-source");
	var activeCharacterCombat = document.getElementById("active-character-combat");
	var activeCharacterTotal = document.getElementById("active-character-total");
	var activeCharacterQuest = document.getElementById("active-character-quest");
	var activeCharacterKills = document.getElementById("active-character-kills");
	var activeCharacterLocation = document.getElementById("active-character-location");
	var activeCharacterSubscription = document.getElementById("active-character-subscription");
	var activeCharacterLastLogin = document.getElementById("active-character-last-login");
	var activeCharacterGear = document.getElementById("active-character-gear");
	var characterMessage = document.getElementById("character-message");
	var queueCharacter = document.getElementById("queue-character");
	var characterName = document.getElementById("character-name");
	var characterPassword = document.getElementById("character-password");
	var snapshotName = document.getElementById("snapshot-name");
	var loadSnapshot = document.getElementById("load-snapshot");
	var linkCharacterName = document.getElementById("link-character-name");
	var startLink = document.getElementById("start-link");
	var linkProof = document.getElementById("link-proof");
	var linkCommand = document.getElementById("link-command");
	var copyLinkCommand = document.getElementById("copy-link-command");
	var simulateLink = document.getElementById("simulate-link");
	var linkExpiry = document.getElementById("link-expiry");
	var redeemForm = document.getElementById("redeem-form");
	var redeemState = document.getElementById("redeem-state");
	var founderRewardCard = document.getElementById("founder-reward-card");
	var founderRewardCount = document.getElementById("founder-reward-count");
	var useFounderReward = document.getElementById("use-founder-reward");
	var subTitle = document.getElementById("sub-title");
	var subDays = document.getElementById("sub-days");
	var subMeterFill = document.getElementById("sub-meter-fill");
	var subCombatRate = document.getElementById("sub-combat-rate");
	var subSkillRate = document.getElementById("sub-skill-rate");
	var sidebarSubTime = document.getElementById("sidebar-sub-time");
	var rosterTitle = document.getElementById("roster-title");
	var slotBadge = document.getElementById("slot-badge");
	var founderForm = document.getElementById("founder-form");
	var founderTitle = document.getElementById("founder-title");
	var founderName = document.getElementById("founder-name");
	var founderNameRow = document.getElementById("founder-name-row");
	var founderEmail = document.getElementById("founder-email");
	var founderPassword = document.getElementById("founder-password");
	var founderSubmit = document.getElementById("founder-submit");
	var founderMessage = document.getElementById("founder-message");
	var prelaunchGoogle = document.getElementById("prelaunch-google");
	var googleSignupButton = document.getElementById("google-signup-button");
	var googleSignupMessage = document.getElementById("google-signup-message");
	var prelaunchSuccess = document.getElementById("prelaunch-success");
	var prelaunchSuccessAccount = document.getElementById("prelaunch-success-account");
	var prelaunchSuccessName = document.getElementById("prelaunch-success-name");
	var prelaunchSuccessStatus = document.getElementById("prelaunch-success-status");
	var prelaunchGameOnboarding = document.getElementById("prelaunch-game-onboarding");
	var prelaunchGamePassword = document.getElementById("prelaunch-game-password");
	var prelaunchCreateGameLogin = document.getElementById("prelaunch-create-game-login");
	var prelaunchDownload = document.getElementById("prelaunch-download");
	var accountModeButtons = Array.prototype.slice.call(document.querySelectorAll("[data-account-mode]"));
	var prelaunchAuthTabs = document.querySelector(".prelaunch-auth-tabs");
	var prelaunchSuccessActions = document.getElementById("prelaunch-success-actions");
	var prelaunchSuccessCode = document.getElementById("prelaunch-success-code");
	var signupCodeBlock = document.getElementById("signup-code-block");
	var signupCodeHelp = document.getElementById("signup-code-help");
	var copySignupCode = document.getElementById("copy-signup-code");
	var betaCountdown = document.getElementById("beta-countdown");
	var betaCountdownDays = document.getElementById("beta-countdown-days");
	var betaCountdownHours = document.getElementById("beta-countdown-hours");
	var betaCountdownMinutes = document.getElementById("beta-countdown-minutes");
	var betaCountdownSeconds = document.getElementById("beta-countdown-seconds");
	var betaSignupCounter = document.getElementById("beta-signup-counter");
	var betaSignupCount = document.getElementById("beta-signup-count");
	var betaCountdownNote = document.getElementById("beta-countdown-note");
	var founderProgressLabel = document.getElementById("founder-progress-label");
	var founderRewardLabel = document.getElementById("founder-reward-label");
	var founderProgressFill = document.getElementById("founder-progress-fill");
	var referralRewardCodes = document.getElementById("referral-reward-codes");
	var referralRewardCodeList = document.getElementById("referral-reward-code-list");
	var founderLink = document.getElementById("founder-link");
	var copyFounderLink = document.getElementById("copy-founder-link");
	var simulateReferral = document.getElementById("simulate-referral");
	var referralNotice = document.getElementById("referral-notice");
	var referralCodeLabel = document.getElementById("referral-code");
	var clearReferral = document.getElementById("clear-referral");
	var accountName = document.getElementById("account-name");
	var accountEmail = document.getElementById("account-email");
	var serverWorldLabel = document.getElementById("server-world-label");
	var serverOnlineCount = document.getElementById("server-online-count");
	var patchChip = document.getElementById("patch-chip");
	var landingWorldState = document.getElementById("landing-world-state");
	var landingWorldDetail = document.getElementById("landing-world-detail");
	var landingLiveStatus = document.getElementById("landing-live-status");
	var landingLivePlayers = document.getElementById("landing-live-players");
	var landingLiveBuild = document.getElementById("landing-live-build");
	var landingLiveBuildDetail = document.getElementById("landing-live-build-detail");
	var landingLiveUpdated = document.getElementById("landing-live-updated");
	var landingLiveUpdatedDetail = document.getElementById("landing-live-updated-detail");
	var landingXpRates = document.getElementById("landing-xp-rates");
	var landingSubRates = document.getElementById("landing-sub-rates");
	var landingPrizeState = document.getElementById("landing-prize-state");
	var landingPrizeDetail = document.getElementById("landing-prize-detail");
	var landingNewsList = document.getElementById("landing-news-list");
	var publicNewsList = document.getElementById("public-news-list");
	var downloadActions = document.getElementById("download-actions");
	var dashboardActiveTitle = document.getElementById("dashboard-active-title");
	var dashboardWorldState = document.getElementById("dashboard-world-state");
	var dashboardWorldSave = document.getElementById("dashboard-world-save");
	var dashboardXpRate = document.getElementById("dashboard-xp-rate");
	var dashboardAccountName = document.getElementById("dashboard-account-name");
	var dashboardAccountEmail = document.getElementById("dashboard-account-email");
	var dashboardSubscriptionState = document.getElementById("dashboard-subscription-state");
	var dashboardCharacterCount = document.getElementById("dashboard-character-count");
	var dashboardCardState = document.getElementById("dashboard-card-state");
	var dashboardSecurityState = document.getElementById("dashboard-security-state");
	var portalAuthForm = document.getElementById("portal-auth-form");
	var portalAuthUsernameRow = document.getElementById("portal-auth-username-row");
	var portalAuthUsername = document.getElementById("portal-auth-username");
	var portalAuthEmail = document.getElementById("portal-auth-email");
	var portalAuthPassword = document.getElementById("portal-auth-password");
	var portalAuthSubmit = document.getElementById("portal-auth-submit");
	var portalAuthMessage = document.getElementById("portal-auth-message");
	var portalAuthModeButtons = Array.prototype.slice.call(document.querySelectorAll("[data-portal-auth-mode]"));
	var securityScore = document.getElementById("security-score");
	var securityEmailCheck = document.getElementById("security-email-check");
	var securityRecoveryCheck = document.getElementById("security-recovery-check");
	var securityPasswordCheck = document.getElementById("security-password-check");
	var securitySessionCheck = document.getElementById("security-session-check");
	var generateRecovery = document.getElementById("generate-recovery");
	var recoveryCodes = document.getElementById("recovery-codes");
	var passwordForm = document.getElementById("password-form");
	var currentPassword = document.getElementById("current-password");
	var newPassword = document.getElementById("new-password");
	var securityMessage = document.getElementById("security-message");
	var sessionTable = document.getElementById("session-table");
	var endOtherSessions = document.getElementById("end-other-sessions");
	var adminSearchForm = document.getElementById("admin-search-form");
	var adminTokenInput = document.getElementById("admin-token");
	var adminSearchEmail = document.getElementById("admin-search-email");
	var adminSearchButton = document.getElementById("admin-search-button");
	var adminMessage = document.getElementById("admin-message");
	var adminStatusTile = document.getElementById("admin-status-tile");
	var adminStatusNote = document.getElementById("admin-status-note");
	var adminSubscriptionTile = document.getElementById("admin-subscription-tile");
	var adminSubscriptionNote = document.getElementById("admin-subscription-note");
	var adminCharacterTile = document.getElementById("admin-character-tile");
	var adminStarterTile = document.getElementById("admin-starter-tile");
	var adminStarterNote = document.getElementById("admin-starter-note");
	var adminAccountTitle = document.getElementById("admin-account-title");
	var adminAccountId = document.getElementById("admin-account-id");
	var adminAccountTable = document.getElementById("admin-account-table");
	var adminStatusSelect = document.getElementById("admin-status-select");
	var adminStatusNoteInput = document.getElementById("admin-status-note-input");
	var adminApplyStatus = document.getElementById("admin-apply-status");
	var adminSubDays = document.getElementById("admin-sub-days");
	var adminGrantSub = document.getElementById("admin-grant-sub");
	var adminClearSub = document.getElementById("admin-clear-sub");
	var adminGrantStarter = document.getElementById("admin-grant-starter");
	var adminRevokeStarter = document.getElementById("admin-revoke-starter");
	var adminRevokeSessions = document.getElementById("admin-revoke-sessions");
	var adminCharacterTable = document.getElementById("admin-character-table");
	var adminSignalTable = document.getElementById("admin-signal-table");
	var adminAuditTable = document.getElementById("admin-audit-table");
	var betaHub = document.getElementById("beta-hub");
	var betaDiscordName = document.getElementById("beta-discord-name");
	var betaCode = document.getElementById("beta-code");
	var copyBetaCode = document.getElementById("copy-beta-code");
	var betaCodeHelp = document.getElementById("beta-code-help");
	var betaDownloads = document.getElementById("beta-downloads");
	var betaReferenceSearch = document.getElementById("beta-reference-search");
	var betaReferenceCount = document.getElementById("beta-reference-count");
	var betaCommandList = document.getElementById("beta-command-list");
	var betaChecklist = document.getElementById("beta-checklist");
	var betaCoords = document.getElementById("beta-coords");
	var betaItems = document.getElementById("beta-items");
	var betaPolicy = document.getElementById("beta-policy");

	var maxCharacters = 10;
	var rosterKey = "voidscape.portal.roster";
	var selectedKey = "voidscape.portal.selectedCharacter";
	var founderKey = "voidscape.portal.founder";
	var referralKey = "voidscape.portal.referralCode";
	var sessionKey = "voidscape.portal.sessionToken";
	var adminTokenKey = "voidscape.portal.adminToken";
	var sessionToken = localStorage.getItem(sessionKey) || "";
	var adminToken = localStorage.getItem(adminTokenKey) || "";
	var accountMode = "reserve";
	var portalAuthMode = "register";
	var publicModeActive = false;
	var launchSignupModeActive = false;
	var publicModeViews = {
		account: true
	};
	var launchSignupModeViews = {
		account: true,
		dashboard: true,
		characters: true,
		subscription: true,
		security: true
	};
	var lastCharacterRefreshAt = 0;
	var activeReferralCode = captureReferralFromLocation();
	var pendingLinkChallenge = null;
	var adminAccount = null;
	var betaResources = null;
	var betaAccount = null;
	var betaDownloadRows = [];
	var launchCountdownState = null;
	var launchCountdownTimer = 0;
	var googleClientId = "";
	var googleNonce = "";
	var googleButtonRendered = false;
	var googleScriptPromise = null;
	var retiredViews = {
		landing: "account",
		highscores: "account",
		market: "account",
		activity: "account",
		public: "account"
	};

	var defaultCharacterKit = {
		path: "Game login",
		image: "assets/rsc-knight.png",
		gear: [],
		appearance: "Appearance chosen in-game",
		appearanceData: { hairColour: 2, topColour: 4, trouserColour: 8, skinColour: 1 }
	};

	var characters = loadRoster();
	var selectedCharacter = localStorage.getItem(selectedKey) || (characters[0] && characters[0].name) || "";
	if (adminTokenInput) adminTokenInput.value = adminToken;

	var ranks = [
		["#1", "Maeve", "99", "13.1m"],
		["#2", "Zamak42", "87", "8.7m"],
		["#3", "WildRanger", "61", "3.2m"],
		["#4", "VoidMage", "54", "2.4m"],
		["#5", "DarkWarden", "51", "2.1m"],
		["#6", "CoinMule17", "43", "1.2m"]
	];

	var marketRows = [
		["Rune 2h sword", "48,220 gp", "+8.1%", "High"],
		["Dragonstone amulet", "138,000 gp", "+3.6%", "Thin"],
		["Subscription card", "Reserved", "0.0%", "Pickup"],
		["Law rune", "312 gp", "-2.4%", "Active"],
		["Raw lobster", "88 gp", "+1.2%", "Stable"],
		["Void scimitar", "Rare", "No sales", "Watch"]
	];

	var feedRows = [
		["rare", "Maeve received Dragonstone amulet from the rare table.", "2m"],
		["kill", "VoidSeeker defeated DarkWarden in level 32 Wilderness.", "8m"],
		["title", "Zamak42 equipped The Conqueror.", "14m"],
		["market", "Rune 2h sword buy orders rose above 48,000 gp.", "26m"],
		["rare", "WildRanger found Void scimitar at the Void Knight.", "41m"],
		["kill", "GraveTax escaped with 19 lobsters and a skull.", "1h"]
	];

	renderAll();
	activateView((window.location.hash || "#account").replace("#", "") || "account");
	window.setTimeout(function () {
		activateView((window.location.hash || "#account").replace("#", "") || "account");
	}, 0);
	hydrateFromApi();

	viewLinks.forEach(function (link) {
		link.addEventListener("click", function (event) {
			event.preventDefault();
			activateView(link.getAttribute("data-view-link"));
			if (shell) shell.classList.remove("nav-open");
		});
	});

	landingScrollButtons.forEach(function (button) {
		button.addEventListener("click", function (event) {
			event.preventDefault();
			scrollToLandingTarget(button.getAttribute("data-landing-scroll"));
			if (shell) shell.classList.remove("nav-open");
		});
	});

	prelaunchAuthButtons.forEach(function (button) {
		button.addEventListener("click", function (event) {
			event.preventDefault();
			handlePrelaunchAuthCta();
			if (shell) shell.classList.remove("nav-open");
		});
	});

	discordLoginButtons.forEach(function (button) {
		button.addEventListener("click", function (event) {
			event.preventDefault();
			startDiscordRewardFlow(button);
		});
	});

	whitepaperJumpButtons.forEach(function (button) {
		button.addEventListener("click", function (event) {
			event.preventDefault();
			scrollToLandingTarget(button.getAttribute("data-whitepaper-jump"));
		});
	});

	trustTabs.forEach(function (button) {
		button.addEventListener("click", function () {
			setTrustPanel(button.getAttribute("data-trust-tab"));
		});
	});
	if (trustTabs.length) setTrustPanel(trustTabs[0].getAttribute("data-trust-tab"));

	setupWhitepaperLightbox();
	setupFunnelTracking();

	window.addEventListener("hashchange", function () {
		activateView((window.location.hash || "#account").replace("#", "") || "account");
	});

	window.addEventListener("focus", function () {
		refreshCharactersFromApi(false);
	});

	document.addEventListener("visibilitychange", function () {
		if (!document.hidden) refreshCharactersFromApi(false);
	});

	if (menuToggle) {
		menuToggle.addEventListener("click", function () {
			shell.classList.toggle("nav-open");
		});
	}

	if (queueCharacter) {
		queueCharacter.addEventListener("click", async function () {
			var name = normalizeName(characterName.value || "");
			if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
				characterMessage.textContent = "Choose a 2-12 character name.";
				characterName.focus();
				return;
			}
			var gamePassword = characterPassword ? characterPassword.value : "";
			if (!isGamePassword(gamePassword, 4)) {
				characterMessage.textContent = "Game password must be 4-20 letters and numbers.";
				if (characterPassword) characterPassword.focus();
				return;
			}
			if (!sessionToken) {
				characterMessage.textContent = "Sign in on the Account page before creating a game login.";
				activateView("dashboard");
				return;
			}
			queueCharacter.disabled = true;
			characterMessage.textContent = "Creating game login...";
			try {
				var state = await apiRequest("/api/characters", {
					method: "POST",
					body: {
						name: name,
						gamePassword: gamePassword
					}
				});
				applyAccountState(state);
				selectedCharacter = name;
				renderCharacters();
				renderSelectedCharacter();
				if (characterPassword) characterPassword.value = "";
				var created = characters.find(function (entry) { return entry.name === name; });
				characterMessage.textContent = name + (created && created.source === "openrsc-sqlite-created"
					? " game login created."
					: " added to this portal account.");
			} catch (error) {
				if (error.status === 401) {
					clearSession();
					characterMessage.textContent = "Session expired. Sign in again before creating a game login.";
				} else if (error.status === 409) {
					characterMessage.textContent = error.code === "character_limit_reached"
						? "Roster is full. Web accounts are capped at 10 characters."
						: "That character name is already taken.";
				} else if (error.status === 400 && error.code === "invalid_game_password") {
					characterMessage.textContent = "Enter a 4-20 letter and number game password for this character.";
					if (characterPassword) characterPassword.focus();
				} else if (error.status === 404 && error.code === "not_available_during_prelaunch") {
					characterMessage.textContent = "Public prelaunch mode only accepts reservations. Character creation opens with the account portal.";
				} else {
					characterMessage.textContent = "Character creation failed: " + (error.code || "api_error") + ".";
				}
			} finally {
				queueCharacter.disabled = characters.length >= maxCharacters || !sessionToken;
			}
		});
	}

	if (loadSnapshot) {
		loadSnapshot.addEventListener("click", async function () {
			var name = normalizeName((snapshotName && snapshotName.value) || (characterName && characterName.value) || "");
			if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
				characterMessage.textContent = "Enter a 2-12 character name to load from OpenRSC.";
				if (snapshotName) snapshotName.focus();
				return;
			}
			loadSnapshot.disabled = true;
			characterMessage.textContent = "Loading saved OpenRSC character state...";
			try {
				var result = await apiRequest("/api/openrsc/characters/" + encodeURIComponent(name));
				upsertSnapshotCharacter(result.character);
				characterMessage.textContent = result.character.name + " loaded from the configured OpenRSC SQLite database.";
			} catch (error) {
				if (error.status === 503 && error.code === "openrsc_db_not_configured") {
					characterMessage.textContent = "Start the portal with PORTAL_OPENRSC_DB=/path/to/voidscape.db to load saved characters.";
				} else if (error.status === 404) {
					characterMessage.textContent = "No OpenRSC character named " + name + " was found.";
				} else if (error.status === 409) {
					characterMessage.textContent = "Roster is full. Open a slot before loading another saved character.";
				} else {
					characterMessage.textContent = "Saved character lookup failed: " + error.code + ".";
				}
			} finally {
				loadSnapshot.disabled = false;
			}
		});
	}

	if (startLink) {
		startLink.addEventListener("click", async function () {
			var name = normalizeName((linkCharacterName && linkCharacterName.value) || (snapshotName && snapshotName.value) || selectedCharacter || "");
			if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
				characterMessage.textContent = "Enter a 2-12 character name to link.";
				if (linkCharacterName) linkCharacterName.focus();
				return;
			}
			if (!sessionToken) {
				characterMessage.textContent = "Create or load a portal account before linking a saved character.";
				founderName.focus();
				return;
			}
			startLink.disabled = true;
			characterMessage.textContent = "Starting character link challenge...";
			try {
				var result = await apiRequest("/api/character-links/start", {
					method: "POST",
					body: { username: name }
				});
				pendingLinkChallenge = result.challenge;
				renderLinkChallenge();
				characterMessage.textContent = "Link challenge started for " + result.challenge.username + ".";
			} catch (error) {
				if (error.status === 401) {
					clearSession();
					characterMessage.textContent = "Session expired. Sign in again before linking a character.";
				} else if (error.status === 503 && error.code === "openrsc_db_not_configured") {
					characterMessage.textContent = "Start the portal with PORTAL_OPENRSC_DB=/path/to/voidscape.db to link saved characters.";
				} else if (error.status === 404) {
					characterMessage.textContent = "No OpenRSC character named " + name + " was found.";
				} else if (error.status === 409) {
					characterMessage.textContent = error.code === "character_limit_reached"
						? "Roster is full. Open a slot before linking another character."
						: "That saved character is already linked.";
				} else {
					characterMessage.textContent = "Character link failed: " + error.code + ".";
				}
			} finally {
				startLink.disabled = false;
			}
		});
	}

	if (copyLinkCommand) {
		copyLinkCommand.addEventListener("click", function () {
			if (!linkCommand.value) return;
			copyText(linkCommand.value, linkCommand);
			copyLinkCommand.textContent = "Copied";
			window.setTimeout(function () {
				copyLinkCommand.textContent = "Copy";
			}, 1400);
		});
	}

	if (simulateLink) {
		simulateLink.addEventListener("click", async function () {
			if (!pendingLinkChallenge || !pendingLinkChallenge.code) {
				characterMessage.textContent = "Start a link challenge first.";
				return;
			}
			simulateLink.disabled = true;
			characterMessage.textContent = "Verifying link challenge...";
			try {
				var state = await apiRequest("/api/character-links/simulate-verify", {
					method: "POST",
					body: {
						challengeId: pendingLinkChallenge.id,
						code: pendingLinkChallenge.code
					}
				});
				applyAccountState(state);
				var linked = characters.find(function (character) {
					return character.linkStatus === "linked" && character.name.toLowerCase() === pendingLinkChallenge.username.toLowerCase();
				});
				if (linked) selectedCharacter = linked.name;
				pendingLinkChallenge = null;
				renderLinkChallenge();
				renderCharacters();
				renderSelectedCharacter();
				characterMessage.textContent = "Saved character linked to this portal account.";
			} catch (error) {
				characterMessage.textContent = "Verification failed: " + error.code + ".";
			} finally {
				simulateLink.disabled = false;
			}
		});
	}

	if (redeemForm) {
		redeemForm.addEventListener("submit", async function (event) {
			event.preventDefault();
			if (sessionToken) {
				try {
					var state = await apiRequest("/api/subscriptions/redeem", {
						method: "POST",
						body: {
							code: document.getElementById("redeem-code").value
						}
					});
					applyAccountState(state);
					redeemState.textContent = "Subscription card redeemed through the local portal API.";
					return;
				} catch (error) {
					if (error.status === 401) clearSession();
				}
			}
			redeemState.textContent = "Preview redeemed. Subscription would extend by 7 days after backend validation.";
			subDays.textContent = "13 days 21 hours";
			sidebarSubTime.textContent = "13 days 21 hours";
			if (subTitle) subTitle.textContent = "Subscribed";
			if (subMeterFill) subMeterFill.style.width = "100%";
			if (subCombatRate) subCombatRate.textContent = "11x";
			if (subSkillRate) subSkillRate.textContent = "3x";
		});
	}

	if (useFounderReward) {
		useFounderReward.addEventListener("click", function () {
			redeemState.textContent = "Starter subscription cards are claimed from the Subscription Vendor in Lumbridge.";
		});
	}

	if (generateRecovery) {
		generateRecovery.addEventListener("click", async function () {
			if (!sessionToken) {
				securityMessage.textContent = "Sign in before generating recovery codes.";
				return;
			}
			generateRecovery.disabled = true;
			securityMessage.textContent = "Generating recovery codes...";
			try {
				var result = await apiRequest("/api/security/recovery-codes", { method: "POST" });
				applyAccountState(result);
				renderRecoveryCodes(result.codes || []);
				securityMessage.textContent = "Recovery codes generated. Store them before leaving this page.";
			} catch (error) {
				if (error.status === 401) clearSession();
				securityMessage.textContent = "Recovery code rotation failed: " + error.code + ".";
			} finally {
				generateRecovery.disabled = false;
			}
		});
	}

	if (passwordForm) {
		passwordForm.addEventListener("submit", async function (event) {
			event.preventDefault();
			if (!sessionToken) {
				securityMessage.textContent = "Sign in before changing your password.";
				return;
			}
			if ((newPassword.value || "").length < 8) {
				securityMessage.textContent = "New password must be at least 8 characters.";
				newPassword.focus();
				return;
			}
			try {
				var state = await apiRequest("/api/security/password", {
					method: "POST",
					body: {
						currentPassword: currentPassword.value,
						newPassword: newPassword.value
					}
				});
				applyAccountState(state);
				currentPassword.value = "";
				newPassword.value = "";
				securityMessage.textContent = "Password updated for this portal account.";
			} catch (error) {
				if (error.status === 401 && error.code !== "invalid_current_password") clearSession();
				securityMessage.textContent = error.code === "invalid_current_password"
					? "Current password did not match."
					: "Password update failed: " + error.code + ".";
			}
		});
	}

	if (endOtherSessions) {
		endOtherSessions.addEventListener("click", async function () {
			if (!sessionToken) {
				securityMessage.textContent = "Sign in before ending other sessions.";
				return;
			}
			endOtherSessions.disabled = true;
			try {
				var state = await apiRequest("/api/security/sessions/revoke-others", { method: "POST" });
				applyAccountState(state);
				securityMessage.textContent = "Other portal sessions ended.";
			} catch (error) {
				if (error.status === 401) clearSession();
				securityMessage.textContent = "Session cleanup failed: " + error.code + ".";
			} finally {
				endOtherSessions.disabled = false;
			}
		});
	}

	if (adminSearchForm) {
		adminSearchForm.addEventListener("submit", async function (event) {
			event.preventDefault();
			await searchAdminAccount();
		});
	}

	if (adminTokenInput) {
		adminTokenInput.addEventListener("change", saveAdminTokenFromInput);
	}

	if (adminApplyStatus) {
		adminApplyStatus.addEventListener("click", async function () {
			if (!adminAccount || !adminAccount.account) return setAdminMessage("Load an account first.");
			await runAdminAction("/api/admin/accounts/" + adminAccount.account.id + "/status", {
				status: adminStatusSelect ? adminStatusSelect.value : "active",
				note: adminStatusNoteInput ? adminStatusNoteInput.value : ""
			}, "Account status updated.");
		});
	}

	if (adminGrantSub) {
		adminGrantSub.addEventListener("click", async function () {
			if (!adminAccount || !adminAccount.account) return setAdminMessage("Load an account first.");
			var days = Number(adminSubDays && adminSubDays.value || 0);
			if (!Number.isFinite(days) || days <= 0 || days > 366) {
				setAdminMessage("Choose 1-366 subscription days.");
				if (adminSubDays) adminSubDays.focus();
				return;
			}
			await runAdminAction("/api/admin/accounts/" + adminAccount.account.id + "/subscription", { days: days }, "Subscription updated.");
		});
	}

	if (adminClearSub) {
		adminClearSub.addEventListener("click", async function () {
			if (!adminAccount || !adminAccount.account) return setAdminMessage("Load an account first.");
			await runAdminAction("/api/admin/accounts/" + adminAccount.account.id + "/subscription", { action: "clear" }, "Subscription cleared.");
		});
	}

	if (adminGrantStarter) {
		adminGrantStarter.addEventListener("click", async function () {
			if (!adminAccount || !adminAccount.account) return setAdminMessage("Load an account first.");
			await runAdminAction("/api/admin/accounts/" + adminAccount.account.id + "/starter-card", { action: "grant" }, "Starter card granted.");
		});
	}

	if (adminRevokeStarter) {
		adminRevokeStarter.addEventListener("click", async function () {
			if (!adminAccount || !adminAccount.account) return setAdminMessage("Load an account first.");
			await runAdminAction("/api/admin/accounts/" + adminAccount.account.id + "/starter-card", { action: "revoke" }, "Starter card revoked.");
		});
	}

	if (adminRevokeSessions) {
		adminRevokeSessions.addEventListener("click", async function () {
			if (!adminAccount || !adminAccount.account) return setAdminMessage("Load an account first.");
			await runAdminAction("/api/admin/accounts/" + adminAccount.account.id + "/sessions", {}, "Sessions revoked.");
		});
	}

	if (copySignupCode) {
		copySignupCode.addEventListener("click", function () {
			if (!prelaunchSuccessCode || !prelaunchSuccessCode.textContent || prelaunchSuccessCode.textContent === "-") return;
			copyText(prelaunchSuccessCode.textContent);
			copySignupCode.textContent = "Copied";
			setTimeout(function () { copySignupCode.textContent = "Copy"; }, 1500);
		});
	}

	if (copyBetaCode) {
		copyBetaCode.addEventListener("click", function () {
			if (!betaCode || !betaCode.textContent || betaCode.textContent === "-") return;
			copyText(betaCode.textContent);
			copyBetaCode.textContent = "Copied";
			setTimeout(function () { copyBetaCode.textContent = "Copy"; }, 1500);
		});
	}

	if (betaReferenceSearch) {
		betaReferenceSearch.addEventListener("input", function () {
			renderBetaHub();
		});
	}

	if (prelaunchCreateGameLogin) {
		prelaunchCreateGameLogin.addEventListener("click", createPrelaunchGameLogin);
	}

	if (founderForm) {
		accountModeButtons.forEach(function (button) {
			button.addEventListener("click", function () {
				setAccountMode(button.getAttribute("data-account-mode") || "reserve");
			});
		});

		founderForm.addEventListener("submit", async function (event) {
			event.preventDefault();
			handlePrelaunchSignup();
		});
	}

	if (portalAuthForm) {
		portalAuthModeButtons.forEach(function (button) {
			button.addEventListener("click", function () {
				setPortalAuthMode(button.getAttribute("data-portal-auth-mode") || "register");
			});
		});
		portalAuthForm.addEventListener("submit", async function (event) {
			event.preventDefault();
			handlePortalAuth();
		});
		setPortalAuthMode(portalAuthMode);
	}

	function handlePrelaunchAuthCta() {
		if (sessionToken) {
			activateView("dashboard");
			return;
		}
		setAccountMode("signin");
		scrollToLandingTarget("founder-form");
		window.setTimeout(function () {
			if (founderEmail && !founderEmail.value) founderEmail.focus();
		}, 120);
	}

	function setAccountMode(mode) {
		document.body.classList.remove("prelaunch-claimed");
		accountMode = mode === "signin" && launchSignupModeActive ? "signin" : "reserve";
		accountModeButtons.forEach(function (button) {
			button.classList.toggle("is-active", button.getAttribute("data-account-mode") === accountMode);
		});
		if (founderForm) {
			founderForm.classList.remove("is-claimed", "needs-game-login", "has-game-login");
		}
		if (prelaunchSuccess) prelaunchSuccess.hidden = true;
		if (signupCodeBlock) signupCodeBlock.hidden = true;
		if (prelaunchGameOnboarding) prelaunchGameOnboarding.hidden = true;
		updateLaunchSignupCopy();
	}

	function setPortalAuthMode(mode) {
		portalAuthMode = mode === "login" ? "login" : "register";
		portalAuthModeButtons.forEach(function (button) {
			button.classList.toggle("is-active", button.getAttribute("data-portal-auth-mode") === portalAuthMode);
		});
		if (portalAuthUsernameRow) portalAuthUsernameRow.hidden = portalAuthMode === "login";
		if (portalAuthPassword) {
			portalAuthPassword.setAttribute("autocomplete", portalAuthMode === "login" ? "current-password" : "new-password");
		}
		if (portalAuthSubmit) portalAuthSubmit.textContent = portalAuthMode === "login" ? "Sign in" : "Create portal account";
		if (portalAuthMessage) {
			portalAuthMessage.textContent = portalAuthMode === "login"
				? "Sign in to manage your roster, subscription status, and security settings."
				: "Create the web account first. Game logins still use their own 4-20 character password.";
		}
	}

	async function handlePortalAuth() {
		if (window.location.protocol === "file:") {
			if (portalAuthMessage) portalAuthMessage.textContent = "Run scripts/run-portal.sh before using portal account tools.";
			return;
		}
		var email = (portalAuthEmail && portalAuthEmail.value || "").trim();
		var password = portalAuthPassword ? portalAuthPassword.value : "";
		var username = normalizeName(portalAuthUsername && portalAuthUsername.value || "");
		if (portalAuthMode === "register" && !/^[a-zA-Z0-9 ]{2,12}$/.test(username)) {
			if (portalAuthMessage) portalAuthMessage.textContent = "Choose a 2-12 character username.";
			if (portalAuthUsername) portalAuthUsername.focus();
			return;
		}
		if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
			if (portalAuthMessage) portalAuthMessage.textContent = "Enter a valid email address.";
			if (portalAuthEmail) portalAuthEmail.focus();
			return;
		}
		if (password.length < 8) {
			if (portalAuthMessage) portalAuthMessage.textContent = "Portal password must be at least 8 characters.";
			if (portalAuthPassword) portalAuthPassword.focus();
			return;
		}

		if (portalAuthSubmit) portalAuthSubmit.disabled = true;
		if (portalAuthMessage) portalAuthMessage.textContent = portalAuthMode === "login" ? "Signing in..." : "Creating portal account...";
		try {
			var path = portalAuthMode === "login" ? "/api/accounts/login" : "/api/accounts/register";
			var body = portalAuthMode === "login"
				? { email: email, password: password }
				: { username: username, email: email, password: password };
			var state = await apiRequest(path, {
				method: "POST",
				body: body
			});
			applyAccountState(state);
			if (portalAuthPassword) portalAuthPassword.value = "";
			if (portalAuthMessage) {
				portalAuthMessage.textContent = portalAuthMode === "login"
					? "Signed in. Character tools are ready."
					: "Portal account created. Add game logins from the Characters page.";
			}
			if (portalAuthForm) portalAuthForm.classList.add("is-authenticated");
		} catch (error) {
			if (error.status === 404 && error.code === "not_available_during_prelaunch") {
				if (portalAuthMessage) portalAuthMessage.textContent = "Public prelaunch mode only accepts reservations. Full account tools stay closed until release.";
			} else if (error.status === 409 && error.code === "account_exists") {
				setPortalAuthMode("login");
				if (portalAuthMessage) portalAuthMessage.textContent = "That email already has an account. Sign in instead.";
			} else if (error.status === 409 && (error.code === "username_taken" || error.code === "username_reserved")) {
				if (portalAuthMessage) portalAuthMessage.textContent = "That username is already reserved.";
			} else if (error.status === 401) {
				if (portalAuthMessage) portalAuthMessage.textContent = "Email or password did not match.";
			} else {
				if (portalAuthMessage) portalAuthMessage.textContent = "Portal account request failed: " + (error.code || "api_error") + ".";
			}
		} finally {
			if (portalAuthSubmit) portalAuthSubmit.disabled = false;
		}
	}

	function showPrelaunchClaimSuccess(state) {
		if (!founderForm || !prelaunchSuccess) return;
		var rewards = state && state.rewards ? state.rewards : null;
		var hasCard = rewards && rewards.starterSubscriptionCards > 0;
		if (!launchSignupModeActive && !hasCard && !(state && state.founder && state.founder.starterCardUnlocked)) return;
		document.body.classList.add("prelaunch-claimed");
		var firstCharacter = state && Array.isArray(state.characters) && state.characters.length ? state.characters[0] : null;
		var founderUsername = state && state.founder ? state.founder.username : firstCharacter ? firstCharacter.name : founderName.value;
		var character = prelaunchReservedCharacter(state, founderUsername);
		var needsGameLogin = Boolean(character && character.source === "founder-reserved");
		var usedSlots = state && Array.isArray(state.characters) ? state.characters.length : (character ? 1 : 0);

		founderForm.classList.add("is-claimed");
		founderForm.classList.toggle("needs-game-login", needsGameLogin);
		founderForm.classList.toggle("has-game-login", !needsGameLogin);
		if (prelaunchGoogle) prelaunchGoogle.hidden = true;
		prelaunchSuccess.hidden = false;
		if (prelaunchSuccessAccount) {
			prelaunchSuccessAccount.textContent = (state.account && state.account.email) || (state.founder && state.founder.email) || founderEmail.value || "-";
		}
		if (prelaunchSuccessName) {
			prelaunchSuccessName.textContent = founderUsername || "-";
		}
		if (prelaunchSuccessStatus) {
			prelaunchSuccessStatus.textContent = needsGameLogin ? "Game password needed" : usedSlots + " / " + maxCharacters + " slots used";
		}
		var successTitle = prelaunchSuccess.querySelector("h2");
		var successCopy = prelaunchSuccess.querySelector("p");
		if (successTitle) {
			successTitle.textContent = needsGameLogin ? "Set your game password" : "Account ready for launch";
		}
		if (successCopy) {
			successCopy.textContent = needsGameLogin
				? "Your web account and name reservation are saved. Create the playable login with a separate 4-20 character game password."
				: "Your first character uses this username and your starter card is reserved for launch day.";
		}
		if (signupCodeBlock) signupCodeBlock.hidden = true;
		if (signupCodeHelp) {
			signupCodeHelp.textContent = hasCard
				? "Your starter subscription card is reserved at the Lumbridge Subscription Vendor."
				: "Your account is active. If the starter card needs review, staff can release it without blocking play.";
		}
		if (prelaunchGameOnboarding) {
			prelaunchGameOnboarding.hidden = !needsGameLogin;
		}
		if (prelaunchDownload) {
			prelaunchDownload.hidden = needsGameLogin;
		}
		if (prelaunchGamePassword && needsGameLogin) {
			prelaunchGamePassword.value = "";
			prelaunchGamePassword.focus();
		}
	}

	function prelaunchReservedCharacter(state, username) {
		var normalized = normalizeName(username || "").toLowerCase();
		if (!normalized || !state || !Array.isArray(state.characters)) return null;
		return state.characters.find(function (character) {
			return normalizeName(character.name || "").toLowerCase() === normalized;
		}) || null;
	}

	async function createPrelaunchGameLogin() {
		if (!sessionToken) {
			founderMessage.textContent = "Sign in to the portal first.";
			return;
		}
		var name = (prelaunchSuccessName && prelaunchSuccessName.textContent) || founderName.value || "";
		name = normalizeName(name);
		var gamePassword = prelaunchGamePassword ? prelaunchGamePassword.value : "";
		if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
			founderMessage.textContent = "Reserve a username before creating the game login.";
			return;
		}
		if (!isGamePassword(gamePassword, 4)) {
			founderMessage.textContent = "Game password must be 4-20 letters and numbers.";
			if (prelaunchGamePassword) prelaunchGamePassword.focus();
			return;
		}

		prelaunchCreateGameLogin.disabled = true;
		founderMessage.textContent = "Creating your game login...";
		try {
			var state = await apiRequest("/api/characters", {
				method: "POST",
				body: {
					name: name,
					gamePassword: gamePassword
				}
			});
			applyAccountState(state);
			selectedCharacter = name;
			localStorage.setItem(selectedKey, selectedCharacter);
			renderCharacters();
			renderSelectedCharacter();
			if (prelaunchGamePassword) prelaunchGamePassword.value = "";
			showPrelaunchClaimSuccess(state);
			founderMessage.textContent = "Game login created. Download the launcher and log in as " + name + ".";
		} catch (error) {
			if (error.status === 401) {
				clearSession();
				founderMessage.textContent = "Sign in to the portal again to continue.";
			} else if (error.status === 400 && error.code === "invalid_game_password") {
				founderMessage.textContent = "Game password must be 4-20 letters and numbers.";
				if (prelaunchGamePassword) prelaunchGamePassword.focus();
			} else if (error.status === 409) {
				founderMessage.textContent = "That username is already taken. Open Manage characters to choose another.";
			} else if (error.status === 503 && error.code === "openrsc_db_not_configured") {
				founderMessage.textContent = "Start the portal with PORTAL_OPENRSC_DB before creating game logins.";
			} else {
				founderMessage.textContent = "Game login creation failed: " + (error.code || "unknown_error") + ".";
			}
		} finally {
			prelaunchCreateGameLogin.disabled = false;
		}
	}

	async function handlePrelaunchSignup() {
		var name = normalizeName(founderName.value || "");
		var email = (founderEmail && founderEmail.value || "").trim();
		var webPassword = founderPassword ? founderPassword.value : "";
		if (launchSignupModeActive && accountMode === "signin") {
			if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
				founderMessage.textContent = "Enter the email for your Voidscape account.";
				if (founderEmail) founderEmail.focus();
				return;
			}
			if (webPassword.length < 8 || webPassword.length > 20) {
				founderMessage.textContent = "Password must be 8-20 characters.";
				if (founderPassword) founderPassword.focus();
				return;
			}
			if (founderSubmit) founderSubmit.disabled = true;
			founderMessage.textContent = "Signing in...";
			try {
				var loginState = await apiRequest("/api/accounts/login", {
					method: "POST",
					body: {
						email: email,
						password: webPassword
					}
				});
				applyAccountState(loginState);
				if (founderPassword) founderPassword.value = "";
				if (launchSignupModeActive) showPrelaunchClaimSuccess(loginState);
				founderMessage.textContent = "Signed in. Account management is ready.";
				activateView("dashboard");
			} catch (error) {
				if (error.status === 401) {
					founderMessage.textContent = "Email or password did not match.";
				} else if (error.status === 404 && error.code === "not_available_during_prelaunch") {
					founderMessage.textContent = "Account sign-in opens with the launch signup flow.";
				} else {
					founderMessage.textContent = "Sign-in is unavailable right now. Please try again soon.";
				}
			} finally {
				if (founderSubmit) founderSubmit.disabled = false;
			}
			return;
		}
		if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
			founderMessage.textContent = "Choose a 2-12 character username to reserve.";
			founderName.focus();
			return;
		}
		if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
			founderMessage.textContent = launchSignupModeActive
				? "Enter a valid email address for your web account."
				: "Enter a valid email address. Your code is tied to it.";
			if (founderEmail) founderEmail.focus();
			return;
		}
		if (launchSignupModeActive && !isGamePassword(webPassword, 8)) {
			founderMessage.textContent = "Password must be 8-20 letters and numbers for your web account and first game login.";
			if (founderPassword) founderPassword.focus();
			return;
		}
		if (founderSubmit) founderSubmit.disabled = true;
		founderMessage.textContent = launchSignupModeActive ? "Reserving your name and starter card..." : "Reserving " + name + "...";
		try {
			if (launchSignupModeActive) {
				var accountState = await apiRequest("/api/accounts/register", {
					method: "POST",
					body: {
						username: name,
						email: email,
						password: webPassword,
						referrerCode: currentReferralCode() || undefined
					}
				});
				applyAccountState(accountState);
				if (founderPassword) founderPassword.value = "";
				if (characterName) characterName.value = name;
				showPrelaunchClaimSuccess(accountState);
				founderMessage.textContent = "Account and first character ready. Your starter card is waiting for launch.";
				return;
			}
			var result = await apiRequest("/api/founder/reservations", {
				method: "POST",
				body: {
					username: name,
					email: email,
					referrerCode: currentReferralCode() || undefined
				}
			});
			saveFounder({
				username: result.founder.username,
				email: result.founder.email,
				code: result.founder.code,
				invites: result.founder.creditedReferrals || 0,
				rewardCodes: result.founder.referralRewardCodes || []
			});
			showSignupCodeSuccess(result);
		} catch (error) {
			if (error.code === "referrer_not_found" || error.code === "self_referral_not_allowed") {
				founderMessage.textContent = referralErrorMessage(error);
			} else if (error.status === 400 && error.code === "invalid_username") {
				founderMessage.textContent = "Choose a 2-12 character username to reserve.";
			} else if (error.status === 400 && error.code === "invalid_email") {
				founderMessage.textContent = launchSignupModeActive
					? "Enter a valid email address for your web account."
					: "Enter a valid email address. Your code is tied to it.";
			} else if (launchSignupModeActive && error.status === 400 && error.code === "invalid_password") {
				founderMessage.textContent = "Password must be 8-20 letters and numbers for your web account and first game login.";
			} else if (launchSignupModeActive && error.status === 400 && error.code === "invalid_game_password") {
				founderMessage.textContent = "Password must be 8-20 letters and numbers for your first game login.";
			} else if (error.status === 409 && (error.code === "username_taken" || error.code === "username_reserved")) {
				founderMessage.textContent = "That username is already reserved by someone else.";
			} else if (error.status === 429) {
				founderMessage.textContent = "Too many signups from your network today. Try again tomorrow.";
			} else if (launchSignupModeActive && error.status === 409 && error.code === "account_exists") {
				try {
					var signedIn = await apiRequest("/api/accounts/login", {
						method: "POST",
						body: {
							email: email,
							password: webPassword
						}
					});
					applyAccountState(signedIn);
					showPrelaunchClaimSuccess(signedIn);
					founderMessage.textContent = "Signed in. Continue with your reserved name and game login.";
				} catch (loginError) {
					founderMessage.textContent = "That email already has an account. Use the Account page to sign in.";
				}
			} else {
				founderMessage.textContent = "Signup is unavailable right now. Please try again soon.";
			}
		} finally {
			if (founderSubmit) founderSubmit.disabled = false;
		}
	}

	function showSignupCodeSuccess(result) {
		if (!founderForm || !prelaunchSuccess || !result || !result.founder) return;
		var signup = result.signup || null;
		document.body.classList.add("prelaunch-claimed");
		founderForm.classList.add("is-claimed");
		founderForm.classList.remove("needs-game-login");
		founderForm.classList.add("has-game-login");
		prelaunchSuccess.hidden = false;
		var successTitle = prelaunchSuccess.querySelector("h2");
		var successCopy = prelaunchSuccess.querySelector("p");
		if (successTitle) successTitle.textContent = "Your username is reserved";
		if (successCopy) {
			successCopy.textContent = signup
				? "Keep your code safe - you'll also get it back any time by signing up again with the same email."
				: "Your username is reserved.";
		}
		if (prelaunchSuccessAccount) prelaunchSuccessAccount.textContent = result.founder.email || "-";
		if (prelaunchSuccessName) prelaunchSuccessName.textContent = result.founder.username || "-";
		if (prelaunchSuccessStatus) prelaunchSuccessStatus.textContent = signup ? "Code issued" : "Reserved";
		if (signupCodeBlock) signupCodeBlock.hidden = !signup;
		if (signup) {
			if (prelaunchSuccessCode) prelaunchSuccessCode.textContent = signup.code;
			if (signupCodeHelp && signup.redeemHint) signupCodeHelp.textContent = signup.redeemHint;
		}
		if (prelaunchGameOnboarding) prelaunchGameOnboarding.hidden = true;
		if (prelaunchDownload) prelaunchDownload.hidden = true;
		if (prelaunchSuccessActions) prelaunchSuccessActions.hidden = true;
		founderMessage.textContent = "You're on the launch list. We'll see you in Lumbridge.";
		renderFounder();
	}

	if (copyFounderLink) {
		copyFounderLink.addEventListener("click", function () {
			if (!founderLink.value) return;
			copyText(founderLink.value, founderLink);
			copyFounderLink.textContent = "Copied";
			window.setTimeout(function () {
				copyFounderLink.textContent = "Copy";
			}, 1400);
		});
	}

	if (referralRewardCodeList) {
		referralRewardCodeList.addEventListener("click", function (event) {
			var button = event.target.closest("[data-copy-referral-reward]");
			if (!button) return;
			copyText(button.getAttribute("data-copy-referral-reward") || "");
			button.textContent = "Copied";
			window.setTimeout(function () {
				button.textContent = "Copy";
			}, 1400);
		});
	}

	if (clearReferral) {
		clearReferral.addEventListener("click", function () {
			activeReferralCode = "";
			localStorage.removeItem(referralKey);
			renderReferralNotice();
			founderMessage.textContent = "Invite code cleared.";
		});
	}

	if (simulateReferral) {
		simulateReferral.addEventListener("click", async function () {
			var founder = loadFounder();
			if (!founder) {
				founderMessage.textContent = "Save an account first.";
				return;
			}
			try {
				var state = await apiRequest("/api/founder/simulate-referral", {
					method: "POST",
					body: {
						code: founder.code
					}
				});
				applyFounderState(state.founder);
				var rewardCount = state.founder.referralRewardCodeCount || 0;
				founderMessage.textContent = rewardCount > 0
					? "Invite credited. Referral sub code earned."
					: "Invite credited through the local API.";
				return;
			} catch (error) {
				// Static-file mode falls back to localStorage below.
			}
			founder.invites = (founder.invites || 0) + 1;
			saveFounder(founder);
			founderMessage.textContent = "Invite credited locally. API mode will mint the reward code.";
			renderFounder();
		});
	}

	Array.prototype.slice.call(document.querySelectorAll(".segmented button")).forEach(function (button) {
		button.addEventListener("click", function () {
			Array.prototype.slice.call(button.parentNode.querySelectorAll("button")).forEach(function (other) {
				other.classList.toggle("is-active", other === button);
			});
		});
	});

	function renderAll() {
		renderFounder();
		renderCharacters();
		renderSelectedCharacter();
		updateDashboardHome();
		renderPortalAuthState();
		renderRankTable();
		renderMarketTable();
		renderWorldFeed();
		renderBetaHub();
	}

	async function hydrateFromApi() {
		try {
			var returnedState = consumeDiscordReturnState();
			if (returnedState) {
				applyAccountState(returnedState);
				if (founderMessage) founderMessage.textContent = "Discord connected. Your reward code is ready.";
			}
			await apiRequest("/api/health");
			var publicState = await apiRequest("/api/public");
			applyPublicState(publicState);
			if (!sessionToken) {
				founderMessage.textContent = launchSignupModeActive
					? "Reserve your account name, create your first character, and keep your free starter card waiting."
					: "Reserve your launch username and keep your subscription card code.";
				return;
			}
			var state = await apiRequest("/api/account");
			applyAccountState(state);
			if (launchSignupModeActive) {
				showPrelaunchClaimSuccess(state);
			}
			founderMessage.textContent = "Signed in. Manage your account or reserve another character from the dashboard.";
		} catch (error) {
			if (error && error.status === 401) {
				clearSession();
				updateLaunchSignupCopy();
			}
			// Opening index.html directly or serving it statically keeps using localStorage.
		}
	}

	function applyPublicState(state) {
		if (!state) return;
		launchSignupModeActive = Boolean(state.launchSignupMode);
		document.body.classList.toggle("launch-signup-mode", launchSignupModeActive);
		googleClientId = state.oauth && state.oauth.google && state.oauth.google.enabled ? String(state.oauth.google.clientId || "") : "";
		updateLaunchSignupCopy();
		updateGoogleSignupButton();
		if (state.publicMode && !publicModeActive) {
			publicModeActive = true;
			document.body.classList.add("public-mode");
			activateView((window.location.hash || "#account").replace("#", "") || "account");
		}
		applyLaunchState(state.launch || (state.beta && state.beta.schedule) || null, state.founderStats || null);
		if (state.status) {
			if (serverWorldLabel) serverWorldLabel.textContent = state.status.world || "World 1";
			if (serverOnlineCount) serverOnlineCount.textContent = (state.status.playersOnline || 0) + " online";
			if (patchChip) patchChip.textContent = "Patch " + (state.status.patch || "0.8.7");
			if (landingWorldState) landingWorldState.textContent = state.status.online ? "Online" : "Offline";
			if (landingWorldDetail) landingWorldDetail.textContent = state.status.world || "Launch status";
			if (landingLiveStatus) landingLiveStatus.textContent = state.status.online ? "Online" : (state.status.world || "Prelaunch");
			if (landingLivePlayers) landingLivePlayers.textContent = state.status.online ? (state.status.playersOnline || 0) + " players online" : "Reservations open";
			if (dashboardWorldState) dashboardWorldState.textContent = state.status.online ? "Online" : "Offline";
			if (dashboardWorldSave) dashboardWorldSave.textContent = "Last save " + (state.status.lastSave || "recently");
		}
		if (state.rates) {
			if (landingXpRates) landingXpRates.textContent = state.rates.baseCombat + "x / " + state.rates.baseSkill + "x";
			if (landingSubRates) landingSubRates.textContent = state.rates.subscribedCombat + "x / " + state.rates.subscribedSkill + "x subscribed";
			if (dashboardXpRate) dashboardXpRate.textContent = state.rates.subscribedCombat + "x / " + state.rates.subscribedSkill + "x";
		}
		if (state.founderStats) {
			if (landingPrizeState) {
				landingPrizeState.textContent = state.founderStats.referralCodesIssued
					? state.founderStats.referralCodesIssued + " referral codes"
					: (state.founderStats.starterCardsUnlocked || 0) + " unlocked";
			}
			if (landingPrizeDetail) landingPrizeDetail.textContent = (state.founderStats.reservations || 0) + " founder reservations";
		}
		if (Array.isArray(state.highscores)) {
			ranks = state.highscores.map(function (row) {
				return [row.rank, row.player, String(row.combat), row.xp];
			});
			renderRankTable();
		}
		if (Array.isArray(state.market)) {
			marketRows = state.market.map(function (row) {
				return [row.item, row.average, row.movement, row.depth];
			});
			renderMarketTable();
		}
		if (Array.isArray(state.activity)) {
			feedRows = state.activity.map(function (row) {
				return [row.type, row.text, row.time];
			});
			renderWorldFeed();
		}
		if (Array.isArray(state.news)) {
			renderNews(landingNewsList, state.news);
			renderNews(publicNewsList, state.news);
		}
		if (Array.isArray(state.downloads)) {
			betaDownloadRows = state.downloads.slice();
			renderDownloads(state.downloads);
			renderBetaHub();
			renderLiveBuildBasics(state);
		}
		if (state.integrity) {
			renderIntegrity(state.integrity);
			renderLiveBuildBasics(state);
		}
		if (state.beta) {
			betaResources = normalizeBetaResources(state.beta);
			renderBetaHub();
		}
	}

	function applyLaunchState(schedule, founderStats) {
		if (betaCountdown) betaCountdown.hidden = false;
		var reservations = Number(founderStats && (founderStats.betaSignupCounter || founderStats.reservations || 0));
		if (betaSignupCounter && betaSignupCount) {
			betaSignupCounter.hidden = reservations <= 0;
			betaSignupCount.textContent = formatCompactNumber(reservations);
		}
		launchCountdownState = schedule && schedule.openAt ? schedule : null;
		if (!launchCountdownState) {
			setCountdownValue("--", "--", "--", "--");
			if (betaCountdownNote) {
				betaCountdownNote.textContent = "Launch date is being finalized. Reservations and codes are open now.";
			}
			if (launchCountdownTimer) {
				window.clearInterval(launchCountdownTimer);
				launchCountdownTimer = 0;
			}
			return;
		}
		updateLaunchCountdown();
		if (!launchCountdownTimer) {
			launchCountdownTimer = window.setInterval(updateLaunchCountdown, 1000);
		}
	}

	function updateLaunchSignupCopy() {
		var signInMode = launchSignupModeActive && accountMode === "signin";
		if (prelaunchAuthTabs) prelaunchAuthTabs.hidden = !launchSignupModeActive;
		if (founderTitle) {
			founderTitle.textContent = signInMode
				? "Sign in to account management"
				: launchSignupModeActive ? "Reserve your account name" : "Reserve your username";
		}
		if (founderSubmit) {
			founderSubmit.textContent = signInMode
				? "Sign in"
				: launchSignupModeActive ? "Reserve name + free card" : "Reserve & get my code";
		}
		if (founderPassword) {
			if (launchSignupModeActive) {
				founderPassword.setAttribute("type", "password");
				founderPassword.setAttribute("autocomplete", signInMode ? "current-password" : "new-password");
			} else {
				founderPassword.setAttribute("type", "hidden");
			}
		}
		var founderPasswordRow = document.getElementById("founder-password-row");
		if (founderPasswordRow) founderPasswordRow.hidden = !launchSignupModeActive;
		if (founderNameRow) founderNameRow.hidden = signInMode;
		if (founderMessage && founderForm && !founderForm.classList.contains("is-claimed")) {
			founderMessage.textContent = signInMode
				? "Use your email and password to manage characters, recovery, and launch rewards."
				: launchSignupModeActive
				? "Reserve your account name, create your first character, and get one free 1-week subscription card."
				: "Reserve your launch username and keep your subscription card code.";
		}
		updatePrelaunchAuthCtas();
	}

	function updatePrelaunchAuthCtas() {
		var signedIn = Boolean(sessionToken);
		prelaunchAuthButtons.forEach(function (button) {
			button.textContent = signedIn ? "Manage Account" : "Sign in";
			button.classList.toggle("is-authenticated", signedIn);
			button.setAttribute("aria-label", signedIn ? "Manage your Voidscape account" : "Sign in to account management");
		});
	}

	function updateGoogleSignupButton() {
		if (!prelaunchGoogle || !googleSignupButton) return;
		var enabled = Boolean(launchSignupModeActive && googleClientId && !(founderForm && founderForm.classList.contains("is-claimed")));
		prelaunchGoogle.hidden = !enabled;
		if (!enabled || googleButtonRendered) return;
		renderGoogleSignupButton();
	}

	async function renderGoogleSignupButton() {
		if (!googleSignupButton || !googleClientId) return;
		try {
			var nonceState = await apiRequest("/api/oauth/google/nonce", { method: "POST", body: {} });
			googleNonce = nonceState.nonce || "";
			await ensureGoogleScript();
			if (!window.google || !window.google.accounts || !window.google.accounts.id) {
				throw new Error("google_identity_unavailable");
			}
			window.google.accounts.id.initialize({
				client_id: googleClientId,
				callback: handleGoogleCredential,
				nonce: googleNonce
			});
			googleSignupButton.innerHTML = "";
			window.google.accounts.id.renderButton(googleSignupButton, {
				theme: "outline",
				size: "large",
				text: "continue_with",
				shape: "rectangular",
				width: Math.min(360, googleSignupButton.clientWidth || 360)
			});
			googleButtonRendered = true;
			if (googleSignupMessage) {
				googleSignupMessage.textContent = "Google creates the web login; the password above becomes the first character login.";
			}
		} catch (error) {
			googleButtonRendered = false;
			if (googleSignupMessage) {
				googleSignupMessage.textContent = "Google sign-in is not available right now. Use email and password to reserve.";
			}
		}
	}

	function ensureGoogleScript() {
		if (window.google && window.google.accounts && window.google.accounts.id) return Promise.resolve();
		if (googleScriptPromise) return googleScriptPromise;
		googleScriptPromise = new Promise(function (resolve, reject) {
			var existing = document.querySelector('script[src="https://accounts.google.com/gsi/client"]');
			if (existing) {
				existing.addEventListener("load", resolve, { once: true });
				existing.addEventListener("error", reject, { once: true });
				return;
			}
			var script = document.createElement("script");
			script.src = "https://accounts.google.com/gsi/client";
			script.async = true;
			script.defer = true;
			script.addEventListener("load", resolve, { once: true });
			script.addEventListener("error", reject, { once: true });
			document.head.appendChild(script);
		});
		return googleScriptPromise;
	}

	async function handleGoogleCredential(response) {
		var credential = response && response.credential ? response.credential : "";
		var name = normalizeName(founderName.value || "");
		var gamePassword = founderPassword ? founderPassword.value : "";
		if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
			founderMessage.textContent = "Choose a 2-12 character username to reserve.";
			founderName.focus();
			return;
		}
		if (!isGamePassword(gamePassword, 8)) {
			founderMessage.textContent = "Password must be 8-20 letters and numbers for your first game login.";
			if (founderPassword) founderPassword.focus();
			return;
		}
		if (!credential || !googleNonce) {
			founderMessage.textContent = "Google sign-in did not return a usable login. Try again or use email.";
			googleButtonRendered = false;
			renderGoogleSignupButton();
			return;
		}
		founderMessage.textContent = "Creating your account and first character with Google...";
		try {
			var state = await apiRequest("/api/accounts/google", {
				method: "POST",
				body: {
					credential: credential,
					nonce: googleNonce,
					username: name,
					gamePassword: gamePassword,
					referrerCode: currentReferralCode() || undefined
				}
			});
			applyAccountState(state);
			if (founderPassword) founderPassword.value = "";
			if (characterName) characterName.value = name;
			showPrelaunchClaimSuccess(state);
			founderMessage.textContent = "Account and first character ready. Your starter card is waiting for launch.";
			googleButtonRendered = false;
			googleNonce = "";
		} catch (error) {
			if (error.status === 400 && error.code === "google_nonce_invalid") {
				founderMessage.textContent = "Google sign-in timed out. Try the Google button again.";
				googleButtonRendered = false;
				renderGoogleSignupButton();
			} else if (error.status === 401) {
				founderMessage.textContent = "Google sign-in could not be verified. Try again or use email.";
			} else if (error.status === 409 && (error.code === "username_taken" || error.code === "username_reserved" || error.code === "character_name_taken")) {
				founderMessage.textContent = "That username is already taken.";
			} else if (error.status === 503 && error.code === "openrsc_db_not_configured") {
				founderMessage.textContent = "Launch signup needs the game database before creating characters.";
			} else {
				founderMessage.textContent = "Google signup is unavailable right now. Use email and password to reserve.";
			}
		}
	}


	function updateLaunchCountdown() {
		if (!launchCountdownState || !launchCountdownState.openAt) return;
		var openAtMs = Date.parse(launchCountdownState.openAt);
		if (!Number.isFinite(openAtMs)) {
			setCountdownValue("--", "--", "--", "--");
			return;
		}
		var remaining = Math.max(0, openAtMs - Date.now());
		var totalSeconds = Math.floor(remaining / 1000);
		var days = Math.floor(totalSeconds / 86400);
		var hours = Math.floor((totalSeconds % 86400) / 3600);
		var minutes = Math.floor((totalSeconds % 3600) / 60);
		var seconds = totalSeconds % 60;
		setCountdownValue(days, pad2(hours), pad2(minutes), pad2(seconds));
		if (betaCountdownNote) {
			betaCountdownNote.textContent = remaining > 0
				? (launchSignupModeActive
						? "Reserve your account name now. Your starter card is reserved for the Lumbridge vendor on launch day."
					: "Reserve your username now. Your code is shown on screen and can be synced to the game database for launch day.")
				: "Launch window is here. Check the launcher and Discord for final world-open status.";
		}
	}

	function setCountdownValue(days, hours, minutes, seconds) {
		if (betaCountdownDays) betaCountdownDays.textContent = String(days);
		if (betaCountdownHours) betaCountdownHours.textContent = String(hours);
		if (betaCountdownMinutes) betaCountdownMinutes.textContent = String(minutes);
		if (betaCountdownSeconds) betaCountdownSeconds.textContent = String(seconds);
	}

	function pad2(value) {
		return String(value).padStart(2, "0");
	}

	async function apiRequest(path, options) {
		options = options || {};
		if (window.location.protocol === "file:") {
			throw apiError(0, "api_unavailable");
		}
		var response = await fetch(path, {
			method: options.method || "GET",
			headers: {
				"content-type": "application/json",
				...(sessionToken ? { "authorization": "Bearer " + sessionToken } : {}),
				...(options.headers || {})
			},
			body: options.body ? JSON.stringify(options.body) : undefined
		});
		var payload = {};
		try {
			payload = await response.json();
		} catch (error) {
			throw apiError(response.status, "api_unavailable");
		}
		if (!response.ok) {
			throw apiError(response.status, payload.error || "api_error");
		}
		return payload;
	}

	function apiError(status, code) {
		var error = new Error(code);
		error.status = status;
		error.code = code;
		return error;
	}

	function saveAdminTokenFromInput() {
		adminToken = adminTokenInput ? adminTokenInput.value.trim() : adminToken;
		if (adminToken) {
			localStorage.setItem(adminTokenKey, adminToken);
		} else {
			localStorage.removeItem(adminTokenKey);
		}
	}

	function adminHeaders() {
		saveAdminTokenFromInput();
		return adminToken ? { "x-portal-admin-token": adminToken } : {};
	}

	async function adminRequest(path, options) {
		options = options || {};
		options.headers = Object.assign({}, options.headers || {}, adminHeaders());
		return apiRequest(path, options);
	}

	async function searchAdminAccount() {
		var email = adminSearchEmail ? adminSearchEmail.value.trim() : "";
		if (!adminTokenInput || !adminTokenInput.value.trim()) {
			setAdminMessage("Enter the local staff token.");
			if (adminTokenInput) adminTokenInput.focus();
			return;
		}
		if (!email) {
			setAdminMessage("Enter an account email.");
			if (adminSearchEmail) adminSearchEmail.focus();
			return;
		}
		if (adminSearchButton) adminSearchButton.disabled = true;
		setAdminMessage("Searching account...");
		try {
			var result = await adminRequest("/api/admin/accounts?email=" + encodeURIComponent(email));
			var account = result.accounts && result.accounts[0];
			if (!account) {
				adminAccount = null;
				renderAdminAccount(null);
				setAdminMessage("No account found for that email.");
				return;
			}
			adminAccount = account;
			renderAdminAccount(account);
			setAdminMessage("Account loaded.");
		} catch (error) {
			adminAccount = null;
			renderAdminAccount(null);
			setAdminMessage(adminErrorMessage(error));
		} finally {
			if (adminSearchButton) adminSearchButton.disabled = false;
		}
	}

	async function runAdminAction(path, body, successMessage) {
		setAdminMessage("Applying staff action...");
		try {
			adminAccount = await adminRequest(path, {
				method: "POST",
				body: body || {}
			});
			renderAdminAccount(adminAccount);
			setAdminMessage(successMessage);
		} catch (error) {
			setAdminMessage(adminErrorMessage(error));
		}
	}

	function adminErrorMessage(error) {
		if (error.status === 403) return "Admin token was rejected.";
		if (error.status === 503 && error.code === "admin_not_configured") return "Admin API is not configured on this portal server.";
		if (error.status === 404) return "Account was not found.";
		return "Staff request failed: " + (error.code || "api_error") + ".";
	}

	function setAdminMessage(message) {
		if (adminMessage) adminMessage.textContent = message;
	}

	function applyAccountState(state) {
		if (state.token) {
			sessionToken = state.token;
			localStorage.setItem(sessionKey, sessionToken);
		}
		if (state.account) {
			betaAccount = state;
			accountName.textContent = state.account.displayName || "Voidscape";
			accountEmail.textContent = state.account.email || "";
			if (dashboardAccountName) dashboardAccountName.textContent = state.account.displayName || "Voidscape account";
			if (dashboardAccountEmail) dashboardAccountEmail.textContent = state.account.email || "";
			if (state.account.subscription) {
				subDays.textContent = state.account.subscription.label;
				sidebarSubTime.textContent = state.account.subscription.label;
				if (subTitle) subTitle.textContent = state.account.subscription.active ? "Subscribed" : "Unsubscribed";
				if (subMeterFill) subMeterFill.style.width = state.account.subscription.active ? "100%" : "0%";
				if (subCombatRate) subCombatRate.textContent = state.account.subscription.combatXpRate + "x";
				if (subSkillRate) subSkillRate.textContent = state.account.subscription.skillXpRate + "x";
				if (dashboardSubscriptionState) {
					dashboardSubscriptionState.textContent = state.account.subscription.active
						? state.account.subscription.label
						: "Unsubscribed";
				}
			}
		}
		if (state.founder) {
			applyFounderState(state.founder);
		}
		if (state.beta) {
			betaResources = normalizeBetaResources(state.beta);
		}
		if (Array.isArray(state.downloads)) {
			betaDownloadRows = state.downloads.slice();
		}
		if (Array.isArray(state.characters)) {
			characters = state.characters.slice(0, maxCharacters);
			if (!characters.length) {
				selectedCharacter = "";
				localStorage.removeItem(selectedKey);
			} else if (!characters.some(function (character) { return character.name === selectedCharacter; })) {
				selectedCharacter = characters[0].name;
			}
			saveRoster();
			renderCharacters();
			renderSelectedCharacter();
		}
		if (Array.isArray(state.linkChallenges) && state.linkChallenges.length && !pendingLinkChallenge) {
			pendingLinkChallenge = state.linkChallenges[0];
			renderLinkChallenge();
		}
		if (state.security) {
			renderSecurity(state.security);
		}
		renderRewards(state.rewards || null);
		updateDashboardHome(state);
		renderPortalAuthState(state);
		updatePrelaunchAuthCtas();
		renderBetaHub();
	}

	function normalizeBetaResources(beta) {
		if (!beta) return null;
		if (beta.resources) return beta.resources;
		return beta;
	}

	function renderBetaHub() {
		if (!betaHub) return;
		var resources = betaResources || {};
		var hasResources = Array.isArray(resources.commands) || Array.isArray(resources.checklist) || Array.isArray(resources.coords) || Array.isArray(resources.items);
		var isTester = Boolean(betaAccount && betaAccount.beta && betaAccount.beta.tester);
		betaHub.hidden = !hasResources && !isTester;
		if (betaHub.hidden) return;

		var query = betaReferenceSearch ? betaReferenceSearch.value.trim().toLowerCase() : "";
		var account = betaAccount && betaAccount.account ? betaAccount.account : null;
		var signup = betaAccount && betaAccount.signup ? betaAccount.signup : null;
		var beta = betaAccount && betaAccount.beta ? betaAccount.beta : null;
		var discord = beta && beta.discord ? beta.discord : null;
		var displayName = (discord && discord.displayName) || (account && account.displayName) || "Beta resources";
		if (betaDiscordName) betaDiscordName.textContent = isTester ? displayName : "Beta resources";

		if (betaCode) betaCode.textContent = signup && signup.code ? signup.code : "-";
		if (copyBetaCode) copyBetaCode.disabled = !(signup && signup.code);
		if (betaCodeHelp) {
			betaCodeHelp.textContent = signup && signup.code
				? (signup.redeemHint || "Talk to the Void Subscription Vendor in Lumbridge and enter this code.")
				: "Sign in with Discord to see your release-valid subscription card code.";
		}

		renderBetaDownloads(betaDownloadRows);
		renderBetaList(betaCommandList, filteredBetaRows(resources.commands || [], query), betaCommandHtml);
		renderBetaList(betaChecklist, filteredBetaRows(resources.checklist || [], query), betaChecklistHtml);
		renderBetaList(betaCoords, filteredBetaRows(resources.coords || [], query), betaCoordHtml);
		renderBetaList(betaItems, filteredBetaRows(resources.items || [], query), betaItemHtml);
		updateBetaReferenceCount(resources, query);

		if (betaPolicy) {
			var policies = resources.policies || {};
			var progress = (beta && beta.progressPolicy) || policies.progress || "Public beta character/world progress may be wiped before launch.";
			var codes = (beta && beta.codePolicy) || policies.codes || "Discord beta codes are release-valid and preserved for release.";
			betaPolicy.textContent = progress + " " + codes;
		}
	}

	function renderBetaDownloads(rows) {
		if (!betaDownloads) return;
		var displayRows = (Array.isArray(rows) ? rows : []).filter(function (row) {
			return isPublicDownloadRow(row);
		});
		if (!displayRows.length) displayRows = Array.isArray(rows) ? rows : [];
		if (!displayRows.length) {
			betaDownloads.innerHTML = '<div class="beta-empty">Downloads are updating. Use the launcher link above if you already have it.</div>';
			return;
		}
		betaDownloads.innerHTML = displayRows.map(function (row) {
			var available = Boolean(row.available && row.url && row.url !== "#");
			var tag = available ? "a" : "button";
			var attrs = available ? linkAttributes(row, false) : ' type="button" disabled';
			return [
				"<" + tag + ' class="' + downloadActionClass(row, available) + '"' + attrs + ">",
				"<strong>" + escapeHtml(row.label || "") + "</strong>",
				"<span>" + escapeHtml(row.state || "") + "</span>",
				"</" + tag + ">"
			].join("");
		}).join("");
	}

	function renderBetaList(target, rows, formatter) {
		if (!target) return;
		target.innerHTML = rows.length
			? rows.map(formatter).join("")
			: '<li class="beta-empty">No matching entries.</li>';
	}

	function filteredBetaRows(rows, query) {
		if (!query) return rows;
		return rows.filter(function (row) {
			return betaSearchText(row).indexOf(query) !== -1;
		});
	}

	function betaSearchText(row) {
		if (typeof row === "string") return row.toLowerCase();
		return Object.keys(row || {}).map(function (key) {
			return String(row[key] || "");
		}).join(" ").toLowerCase();
	}

	function updateBetaReferenceCount(resources, query) {
		if (!betaReferenceCount) return;
		var rows = []
			.concat(resources.commands || [])
			.concat(resources.checklist || [])
			.concat(resources.coords || [])
			.concat(resources.items || []);
		var count = filteredBetaRows(rows, query).length;
		betaReferenceCount.textContent = count + " entr" + (count === 1 ? "y" : "ies");
	}

	function betaCommandHtml(row) {
		return [
			"<li>",
			'<div class="beta-row-title"><strong>' + escapeHtml(row.command || "") + "</strong>" + betaBadge(row.group) + betaBadge(row.access) + "</div>",
			"<span>" + escapeHtml(row.note || "") + "</span>",
			"</li>"
		].join("");
	}

	function betaChecklistHtml(row) {
		return "<li><strong>Test</strong><span>" + escapeHtml(row || "") + "</span></li>";
	}

	function betaCoordHtml(row) {
		return [
			"<li>",
			'<div class="beta-row-title"><strong>' + escapeHtml(row.label || "") + "</strong>" + betaBadge(row.group) + "</div>",
			'<span><code>::goto ' + escapeHtml(row.value || "") + "</code> - " + escapeHtml(row.note || "") + "</span>",
			"</li>"
		].join("");
	}

	function betaItemHtml(row) {
		return [
			"<li>",
			'<div class="beta-row-title"><strong>' + escapeHtml(row.name || "") + "</strong>" + betaBadge(row.group) + "</div>",
			'<span><code>::item ' + escapeHtml(row.id || "") + "</code> - " + escapeHtml(row.note || "") + "</span>",
			"</li>"
		].join("");
	}

	function betaBadge(value) {
		return value ? '<em class="beta-ref-badge">' + escapeHtml(value) + "</em>" : "";
	}

	function renderRewards(rewards) {
		if (!founderRewardCard || !founderRewardCount || !useFounderReward) return;
		var count = rewards && rewards.starterSubscriptionCards ? rewards.starterSubscriptionCards : 0;
		var claimed = Boolean(rewards && (rewards.starterCardStatus === "claimed" || rewards.starterSubscriptionCardsClaimed > 0));
		founderRewardCard.hidden = count <= 0 && !claimed;
		founderRewardCard.classList.toggle("is-ready", count > 0);
		founderRewardCard.classList.toggle("is-claimed", claimed);
		founderRewardCount.textContent = count > 0
			? count + " card reserved in Lumbridge"
			: claimed
				? "Starter card claimed in game"
			: "No reserved cards";
		useFounderReward.textContent = count > 0 ? "Claim in Lumbridge" : claimed ? "Claimed in game" : "No card";
		useFounderReward.disabled = true;
		if (redeemState) {
			redeemState.textContent = count > 0
				? "You have a subscription card waiting at the Lumbridge Subscription Vendor."
				: claimed
					? "Your starter subscription card has been claimed in game."
				: "No subscription card is waiting right now.";
		}
		if (dashboardCardState) {
			dashboardCardState.textContent = count > 0
				? count + " waiting"
				: claimed
					? "Claimed in game"
				: "No card waiting";
		}
	}

	function updateDashboardHome(state) {
		if (dashboardCharacterCount) {
			dashboardCharacterCount.textContent = characters.length + " / " + maxCharacters;
		}
		if (dashboardAccountName && accountName) {
			dashboardAccountName.textContent = accountName.textContent || "Voidscape account";
		}
		if (dashboardAccountEmail && accountEmail) {
			dashboardAccountEmail.textContent = accountEmail.textContent || "Sign in to manage game logins and launch rewards.";
		}
		if (dashboardSubscriptionState && subDays) {
			dashboardSubscriptionState.textContent = subDays.textContent || "Unsubscribed";
		}
		if (dashboardSecurityState && state && state.security) {
			var auth = state.security.auth || {};
			var recoveryCount = state.security.recoveryCodes ? state.security.recoveryCodes.activeCount : 0;
			dashboardSecurityState.textContent = recoveryCount > 0
				? "Recovery ready"
				: auth.googleConnected ? "Google connected" : "Local account";
		} else if (dashboardSecurityState && !sessionToken) {
			dashboardSecurityState.textContent = "Not signed in";
		}
	}

	function renderPortalAuthState(state) {
		var account = state && state.account ? state.account : betaAccount && betaAccount.account ? betaAccount.account : null;
		if (portalAuthForm) {
			portalAuthForm.classList.toggle("is-authenticated", Boolean(account && sessionToken));
		}
		if (portalAuthMessage) {
			if (account && sessionToken) {
				portalAuthMessage.textContent = "Signed in as " + (account.email || account.displayName || "this account") + ". Character tools are ready.";
			} else if (!sessionToken && (!portalAuthForm || !portalAuthForm.classList.contains("is-authenticated"))) {
				portalAuthMessage.textContent = portalAuthMode === "login"
					? "Sign in to manage your roster, subscription status, and security settings."
					: "Create the web account first. Game logins still use their own 4-20 character password.";
			}
		}
		if (queueCharacter) {
			queueCharacter.disabled = !sessionToken || characters.length >= maxCharacters;
		}
		if (characterMessage && !sessionToken) {
			characterMessage.textContent = "Sign in on the Account page first. Then create game logins here with their own in-game passwords.";
		}
	}

	function applyFounderState(apiFounder) {
		var founder = {
			username: apiFounder.username,
			email: apiFounder.email,
			code: apiFounder.code,
			invites: apiFounder.creditedReferrals || 0,
			rewardCodes: apiFounder.referralRewardCodes || [],
			referredBy: apiFounder.referredBy || null
		};
		saveFounder(founder);
		renderFounder();
	}

	function clearSession() {
		sessionToken = "";
		localStorage.removeItem(sessionKey);
		betaAccount = null;
		renderPortalAuthState();
		updatePrelaunchAuthCtas();
		renderCharacters();
	}

	function createLocalCharacter(name) {
		if (characters.length >= maxCharacters) {
			characterMessage.textContent = "Roster is full. Web accounts are capped at 10 characters.";
			return;
		}
		if (characters.some(function (character) { return character.name.toLowerCase() === name.toLowerCase(); })) {
			characterMessage.textContent = "That name is already in this prototype roster.";
			return;
		}

		var kit = defaultCharacterKit;
		var character = {
			name: name,
			path: kit.path,
			image: kit.image,
			combat: 3,
			total: "34",
			quest: 0,
			kills: 0,
			status: "Void Island",
			title: "No title equipped",
			subscription: "Account shared",
			lastLogin: "New",
			appearance: kit.appearance,
			gear: kit.gear.slice(),
			appearanceData: kit.appearanceData || null,
			equipment: []
		};
		characters.push(character);
		selectedCharacter = character.name;
		saveRoster();
		renderCharacters();
		renderSelectedCharacter();
		characterMessage.textContent = name + " added locally. Start the portal API to create a real game login.";
	}

	function upsertSnapshotCharacter(character) {
		var existingIndex = characters.findIndex(function (entry) {
			return entry.name.toLowerCase() === character.name.toLowerCase();
		});
		if (existingIndex === -1 && characters.length >= maxCharacters) {
			throw apiError(409, "character_limit_reached");
		}
		var normalized = {
			id: character.id,
			name: character.name,
			path: character.path || "OpenRSC save",
			image: character.image || "assets/rsc-knight.png",
			combat: character.combat || 3,
			total: character.total || "27",
			quest: character.quest || 0,
			kills: character.kills || 0,
			status: character.status || "Saved location",
			title: character.title || "No title equipped",
			subscription: character.subscription || "Unsubscribed",
			lastLogin: character.lastLogin || "Never",
			appearance: character.appearance || "Saved appearance fields unavailable",
			gear: Array.isArray(character.gear) && character.gear.length ? character.gear : ["No wielded equipment saved"],
			appearanceData: character.appearanceData || null,
			equipment: Array.isArray(character.equipment) ? character.equipment : [],
			source: character.source,
			playerId: character.playerId || null,
			linkStatus: character.linkStatus || "snapshot"
		};
		if (existingIndex >= 0) {
			characters.splice(existingIndex, 1, normalized);
		} else {
			characters.push(normalized);
		}
		selectedCharacter = normalized.name;
		saveRoster();
		renderCharacters();
		renderSelectedCharacter();
	}

	function activateView(id) {
		var landingTarget = landingAnchorFor(id);
		if (landingTarget) id = "account";
		var allowedPublicViews = launchSignupModeActive ? launchSignupModeViews : publicModeViews;
		if (publicModeActive && !allowedPublicViews[id]) id = "account";
		id = retiredViews[id] || id;
		var next = views.find(function (view) {
			return view.id === id;
		}) || document.getElementById("account");

		views.forEach(function (view) {
			view.classList.toggle("is-active", view === next);
		});

		viewLinks.forEach(function (link) {
			link.classList.toggle("is-active", link.getAttribute("data-view-link") === next.id);
		});

		var isLanding = next.id === "account";
		if (shell) shell.classList.toggle("landing-shell", isLanding);
		document.body.classList.toggle("prelaunch-mode", isLanding);
		title.textContent = next.getAttribute("data-title") || "Voidscape";
		if (workspace) workspace.scrollTop = 0;
		if (landingTarget) {
			window.setTimeout(function () {
				landingTarget.scrollIntoView({ behavior: "smooth", block: "start" });
			}, 40);
		}
		var hashId = landingTarget ? landingTarget.id : next.id;
		if (window.location.hash !== "#" + hashId) {
			window.history.replaceState(null, "", "#" + hashId);
		}
		if (next.id === "characters") {
			refreshCharactersFromApi(false);
		}
	}

	function landingAnchorFor(id) {
		if (!id || id === "account") return null;
		var target = document.getElementById(id);
		if (!target) return null;
		var accountView = document.getElementById("account");
		return accountView && accountView.contains(target) ? target : null;
	}

	function scrollToLandingTarget(id) {
		var target = landingAnchorFor(id);
		activateView("account");
		if (!target) return;
		window.setTimeout(function () {
			target.scrollIntoView({ behavior: "smooth", block: "start" });
			window.history.replaceState(null, "", "#" + target.id);
		}, 40);
	}

	function setTrustPanel(key) {
		if (!key) return;
		trustTabs.forEach(function (button) {
			var active = button.getAttribute("data-trust-tab") === key;
			button.classList.toggle("is-active", active);
			button.setAttribute("aria-selected", active ? "true" : "false");
		});
		trustPanels.forEach(function (panel) {
			var active = panel.getAttribute("data-trust-panel") === key;
			panel.classList.toggle("is-active", active);
			panel.hidden = !active;
		});
	}

	function startDiscordRewardFlow(button) {
		if (window.location.protocol === "file:") {
			if (founderMessage) founderMessage.textContent = "Run scripts/run-portal.sh before connecting Discord rewards.";
			return;
		}
		if (button) {
			button.disabled = true;
			button.setAttribute("aria-busy", "true");
		}
		window.location.assign("/api/oauth/discord/start?returnTo=" + encodeURIComponent("/#account"));
	}

	function consumeDiscordReturnState() {
		try {
			var text = sessionStorage.getItem("voidscape.portal.lastAccountState");
			if (!text) return null;
			sessionStorage.removeItem("voidscape.portal.lastAccountState");
			return JSON.parse(text);
		} catch (error) {
			return null;
		}
	}

	function setupWhitepaperLightbox() {
		if (!whitepaperLightbox || !whitepaperLightboxImage) return;
		var whitepaperImages = Array.prototype.slice.call(document.querySelectorAll(".whitepaper-remix img"));

		whitepaperImages.forEach(function (image) {
			image.loading = image.closest(".whitepaper-remix-hero") ? "eager" : "lazy";
			image.decoding = "async";
			image.tabIndex = 0;
			image.setAttribute("role", "button");
			image.setAttribute("aria-label", "Expand image: " + (image.alt || "Voidscape screenshot"));
			image.addEventListener("click", function () {
				openWhitepaperLightbox(image);
			});
			image.addEventListener("keydown", function (event) {
				if (event.key !== "Enter" && event.key !== " ") return;
				event.preventDefault();
				openWhitepaperLightbox(image);
			});
		});

		if (whitepaperLightboxClose) {
			whitepaperLightboxClose.addEventListener("click", closeWhitepaperLightbox);
		}
		whitepaperLightbox.addEventListener("click", function (event) {
			if (event.target === whitepaperLightbox) closeWhitepaperLightbox();
		});
		document.addEventListener("keydown", function (event) {
			if (event.key === "Escape" && !whitepaperLightbox.hidden) closeWhitepaperLightbox();
		});
	}

	function openWhitepaperLightbox(image) {
		if (!whitepaperLightbox || !whitepaperLightboxImage) return;
		whitepaperLightboxImage.src = image.currentSrc || image.src;
		whitepaperLightboxImage.alt = image.alt || "";
		if (whitepaperLightboxCaption) {
			whitepaperLightboxCaption.textContent = whitepaperImageCaption(image);
		}
		whitepaperLightbox.hidden = false;
		whitepaperLightbox.setAttribute("aria-hidden", "false");
		if (whitepaperLightboxClose) whitepaperLightboxClose.focus();
	}

	function closeWhitepaperLightbox() {
		if (!whitepaperLightbox || !whitepaperLightboxImage) return;
		whitepaperLightbox.hidden = true;
		whitepaperLightbox.setAttribute("aria-hidden", "true");
		whitepaperLightboxImage.removeAttribute("src");
	}

	function whitepaperImageCaption(image) {
		var figure = image.closest("figure");
		var figcaption = figure ? figure.querySelector("figcaption") : null;
		var heading = image.parentElement ? image.parentElement.querySelector("h3") : null;
		var cardHeading = image.closest(".vision-card") ? image.closest(".vision-card").querySelector("h3") : null;
		if (figcaption) return figcaption.textContent.trim();
		if (cardHeading) return cardHeading.textContent.trim();
		if (heading) return heading.textContent.trim();
		return image.alt || "Voidscape screenshot";
	}

	async function refreshCharactersFromApi(force) {
		if (!sessionToken || window.location.protocol === "file:") return;
		if (!document.getElementById("characters").classList.contains("is-active")) return;
		var nowMs = Date.now();
		if (!force && nowMs - lastCharacterRefreshAt < 5000) return;
		lastCharacterRefreshAt = nowMs;
		try {
			var state = await apiRequest("/api/account");
			applyAccountState(state);
		} catch (error) {
			if (error.status === 401) clearSession();
		}
	}

	function renderFounder() {
		renderReferralNotice();
		var founder = loadFounder();
		if (!founder) {
			founderProgressLabel.textContent = "0 invites credited";
			founderRewardLabel.textContent = "Invite friends for codes";
			founderProgressFill.style.width = "0%";
			founderLink.value = "";
			accountName.textContent = "Voidscape";
			accountEmail.textContent = "Reserve before launch";
			renderReferralRewardCodes([]);
			return;
		}
		founderName.value = founder.username || "";
		founderEmail.value = founder.email || "";
		accountName.textContent = founder.username || "Voidscape";
		accountEmail.textContent = founder.email || "Reserve before launch";
		var invites = founder.invites || 0;
		var rewardCodes = Array.isArray(founder.rewardCodes) ? founder.rewardCodes : [];
		founderProgressLabel.textContent = invites + " invite" + (invites === 1 ? "" : "s") + " credited";
		founderRewardLabel.textContent = rewardCodes.length > 0
			? rewardCodes.length + " sub code" + (rewardCodes.length === 1 ? "" : "s") + " earned"
			: "Invite friends for codes";
		founderProgressFill.style.width = invites > 0 ? "100%" : "0%";
		founderLink.value = makeInviteLink(founder.code);
		renderReferralRewardCodes(rewardCodes);
	}

	function renderReferralRewardCodes(rewardCodes) {
		if (!referralRewardCodes || !referralRewardCodeList) return;
		var rows = Array.isArray(rewardCodes) ? rewardCodes : [];
		referralRewardCodes.hidden = rows.length === 0;
		referralRewardCodeList.innerHTML = rows.map(function (reward) {
			return [
				'<div class="referral-reward-code-row">',
				'<strong>' + escapeHtml(reward.code || "") + '</strong>',
				'<span>' + titleCase(reward.status || "issued") + '</span>',
				'<button class="ghost-button" type="button" data-copy-referral-reward="' + escapeAttr(reward.code || "") + '">Copy</button>',
				'</div>'
			].join("");
		}).join("");
	}

	function renderReferralNotice() {
		if (!referralNotice || !referralCodeLabel) return;
		var code = currentReferralCode();
		referralNotice.hidden = !code;
		referralCodeLabel.textContent = code || "";
	}

	function renderCharacters() {
		rosterTitle.textContent = characters.length + " character" + (characters.length === 1 ? "" : "s");
		if (slotBadge) slotBadge.textContent = Math.max(0, maxCharacters - characters.length) + " open";
		queueCharacter.disabled = characters.length >= maxCharacters || !sessionToken;
		if (!characters.length) {
			characterCards.innerHTML = [
				'<div class="empty-roster">',
				"<strong>No game logins yet</strong>",
				"<span>Sign in, then create up to 10 playable character logins for this web account.</span>",
				"</div>"
			].join("");
			updateDashboardHome();
			return;
		}
		characterCards.innerHTML = characters.map(function (character) {
			return [
				'<button class="character-card character-card-simple' + (character.name === selectedCharacter ? " is-selected" : "") + '" type="button" data-character="' + escapeAttr(character.name) + '">',
				renderCharacterAvatar(character),
				'<span class="character-card-copy">',
				'<strong>' + escapeHtml(character.name) + '</strong>',
				'<small>' + escapeHtml(characterLevelLabel(character)) + '</small>',
				'<span class="' + characterSubscriptionClass(character) + '">' + escapeHtml(characterSubscriptionLabel(character)) + '</span>',
				'</span>',
				"</button>"
			].join("");
		}).join("");

		Array.prototype.slice.call(characterCards.querySelectorAll(".character-card")).forEach(function (card) {
			card.addEventListener("click", function () {
				selectedCharacter = card.getAttribute("data-character");
				localStorage.setItem(selectedKey, selectedCharacter);
				renderCharacters();
				renderSelectedCharacter();
			});
		});
		updateDashboardHome();
	}

	function renderSelectedCharacter() {
		var character = characters.find(function (entry) {
			return entry.name === selectedCharacter;
		}) || characters[0];
		if (!character) return;
		var gear = Array.isArray(character.gear) ? character.gear : [];
		if (characterName && character.source === "founder-reserved" && (!characterName.value || characterName.value === "Voidborn")) {
			characterName.value = character.name;
		}

		if (activeCharacterTitle) activeCharacterTitle.textContent = character.name;
		if (activeCharacterImage) {
			activeCharacterImage.src = character.image;
			activeCharacterImage.alt = character.name + " character render";
		}
		if (activeCharacterSource) activeCharacterSource.textContent = characterSourceLabel(character);
		if (activeCharacterDoll) {
			var visual = characterVisualState(character);
			activeCharacterDoll.className = "paper-doll " + visual.weaponClass + (visual.hasCape ? " has-cape" : "");
			Object.keys(visual.colors).forEach(function (key) {
				activeCharacterDoll.style.setProperty("--" + key, visual.colors[key]);
			});
			activeCharacterDoll.innerHTML = paperDollMarkup();
		}
		if (activeCharacterCombat) activeCharacterCombat.textContent = character.combat;
		if (activeCharacterTotal) activeCharacterTotal.textContent = character.total;
		if (activeCharacterQuest) activeCharacterQuest.textContent = character.quest;
		if (activeCharacterKills) activeCharacterKills.textContent = character.kills;
		if (activeCharacterLocation) activeCharacterLocation.textContent = character.status;
		if (activeCharacterSubscription) activeCharacterSubscription.textContent = character.subscription || "Unsubscribed";
		if (activeCharacterLastLogin) activeCharacterLastLogin.textContent = character.lastLogin || "Never";
		if (dashboardActiveTitle) dashboardActiveTitle.textContent = character.title || "No title equipped";
		if (activeCharacterGear) {
			activeCharacterGear.innerHTML = [
				'<strong>Equipped / appearance</strong>',
				'<span>' + escapeHtml(character.appearance) + '</span>',
				'<div class="loadout-grid">' + gear.map(renderGearToken).join("") + '</div>'
			].join("");
		}
		localStorage.setItem(selectedKey, character.name);
	}

	function renderCharacterAvatar(character) {
		var image = character.image || "assets/rsc-ranger.png";
		return [
			'<span class="character-avatar-frame" aria-hidden="true">',
			'<img src="' + escapeAttr(image) + '" alt="">',
			'</span>'
		].join("");
	}

	function characterLevelLabel(character) {
		if (character.source === "founder-reserved") return "Reserved";
		return "Level " + String(character.combat || 3);
	}

	function characterSubscriptionLabel(character) {
		var label = String(character.subscription || "Unsubscribed").trim();
		if (!label || label === "Account shared") return "Unsubscribed";
		return label;
	}

	function characterSubscriptionClass(character) {
		return "character-subscription-label" + (/^unsubscribed$/i.test(characterSubscriptionLabel(character)) ? "" : " is-subscribed");
	}

	function paperDollMarkup() {
		return [
			'<span class="doll-shadow"></span>',
			'<span class="doll-cape"></span>',
			'<span class="doll-neck"></span>',
			'<span class="doll-head"></span>',
			'<span class="doll-hair"></span>',
			'<span class="doll-body"></span>',
			'<span class="doll-arm left"></span>',
			'<span class="doll-arm right"></span>',
			'<span class="doll-hand left"></span>',
			'<span class="doll-hand right"></span>',
			'<span class="doll-leg left"></span>',
			'<span class="doll-leg right"></span>',
			'<span class="doll-boot left"></span>',
			'<span class="doll-boot right"></span>',
			'<span class="doll-weapon"></span>'
		].join("");
	}

	function characterVisualState(character) {
		var names = characterGearNames(character);
		var equipmentText = names.join(" ").toLowerCase();
		var appearance = character.appearanceData || {};
		var colors = {
			skin: pickColor(["#d7a27c", "#c98962", "#9f684f", "#e1bb96"], appearance.skinColour, "#c98962"),
			hair: pickColor(["#2f1d12", "#654326", "#a86f35", "#d2b76a", "#151515", "#7a2e2e"], appearance.hairColour, "#2f1d12"),
			top: pickColor(["#9d3f3f", "#465f9f", "#2f6f54", "#7451a5", "#8e704e", "#b0a083", "#263047", "#527047", "#6f4d3a", "#284b7a", "#3956b0"], appearance.topColour, "#8e704e"),
			legs: pickColor(["#49382d", "#314263", "#2e553a", "#40345f", "#736049", "#888888", "#202433", "#3e5939", "#5b4635"], appearance.trouserColour, "#49382d"),
			trim: "#d6bd7c",
			weapon: "#d8d8d1"
		};
		applyGearTint(colors, equipmentText, character.path || "");
		return {
			colors,
			hasCape: /cape/.test(equipmentText),
			weaponClass: paperWeaponClass(names)
		};
	}

	function characterGearNames(character) {
		var names = [];
		if (Array.isArray(character.equipment)) {
			character.equipment.forEach(function (item) {
				if (item && item.name) names.push(item.name);
			});
		}
		if (Array.isArray(character.gear)) {
			character.gear.forEach(function (item) {
				if (item) names.push(item);
			});
		}
		return names;
	}

	function applyGearTint(colors, text, path) {
		var lowerPath = String(path || "").toLowerCase();
		if (/wizard|robe/.test(text) || /arcanist/.test(lowerPath)) {
			colors.top = "#334fa4";
			colors.legs = "#26305d";
			colors.trim = "#b7caff";
		}
		if (/leather|forager|ranger/.test(text) || /forager/.test(lowerPath)) {
			colors.top = "#49653e";
			colors.legs = "#4b3a2d";
			colors.trim = "#9dcf84";
		}
		if (/bronze|plate|helm|helmet/.test(text)) {
			colors.top = "#8d7356";
			colors.trim = "#c99a66";
		}
		if (/rune/.test(text)) colors.weapon = "#9eb3bc";
		if (/iron/.test(text)) colors.weapon = "#d7d7cf";
		if (/bronze/.test(text)) colors.weapon = "#c68f61";
		if (/bow|maple/.test(text)) colors.weapon = "#9a6739";
		if (/void/.test(text)) colors.weapon = "#8658dd";
	}

	function paperWeaponClass(names) {
		var weapon = names.find(function (item) {
			return /sword|2h|scimitar|axe|mace|bow|staff/i.test(item);
		}) || "";
		if (!weapon) return "weapon-none";
		var category = gearCategory(weapon);
		if (category === "range") return "weapon-range";
		if (category === "magic") return "weapon-magic";
		return "weapon-weapon";
	}

	function pickColor(palette, value, fallback) {
		var index = Number(value);
		if (!Number.isFinite(index)) return fallback;
		return palette[Math.abs(index) % palette.length] || fallback;
	}

	function renderGearToken(item, compact) {
		var category = gearCategory(item);
		return [
			'<em class="gear-token gear-' + category + '" title="' + escapeAttr(item) + '">',
			'<span>' + gearIconLabel(category) + '</span>',
			'<b>' + escapeHtml(compact ? shortGearName(item) : item) + '</b>',
			'</em>'
		].join("");
	}

	function shortGearName(item) {
		return String(item || "")
			.replace("Bronze plate body", "Bronze body")
			.replace("Bronze medium helm", "Bronze helm")
			.replace("Amulet of strength", "Str amulet")
			.replace("Amulet of magic", "Magic amulet")
			.replace("Maple shortbow", "Maple bow")
			.replace("Iron 2h sword", "Iron 2h")
			.replace("Rune 2h sword", "Rune 2h")
			.replace("Leather body", "Leather")
			.replace("Bronze arrows", "Arrows");
	}

	function gearCategory(item) {
		var value = String(item || "").toLowerCase();
		if (/bow|shortbow|arrow/.test(value)) return "range";
		if (/sword|2h|scimitar|axe|mace/.test(value)) return "weapon";
		if (/rune|law|air|magic/.test(value)) return "magic";
		if (/body|plate|robe|helm|helmet|legs|cape|leather/.test(value)) return "armor";
		if (/amulet/.test(value)) return "trinket";
		if (/net|tinderbox|bait|tool/.test(value)) return "tool";
		if (/gp|coin|gold/.test(value)) return "coin";
		if (/meat|food/.test(value)) return "food";
		return "item";
	}

	function gearIconLabel(category) {
		var labels = {
			armor: "AR",
			coin: "GP",
			food: "FD",
			item: "IT",
			magic: "MG",
			range: "RG",
			tool: "TL",
			trinket: "AM",
			weapon: "WP"
		};
		return labels[category] || labels.item;
	}

	function characterStateLabel(character) {
		if (character.source === "founder-reserved") return "Reserved name";
		if (character.source === "openrsc-sqlite-created") return "Created save";
		if (character.linkStatus === "linked") return "Linked save";
		if (character.source === "openrsc-sqlite") return "Saved state";
		if (character.linkStatus === "pending") return "Pending link";
		return "Preview";
	}

	function characterStateClass(character) {
		if (character.source === "founder-reserved") return "pending";
		if (character.source === "openrsc-sqlite-created") return "linked";
		if (character.linkStatus === "linked") return "linked";
		if (character.source === "openrsc-sqlite") return "snapshot";
		if (character.linkStatus === "pending") return "pending";
		return "preview";
	}

	function characterSourceLabel(character) {
		if (character.source === "founder-reserved") return "free card reserved";
		if (character.source === "openrsc-sqlite-created") return "created game save";
		if (character.linkStatus === "linked") return "linked to game save";
		if (character.source === "openrsc-sqlite") return "saved snapshot";
		if (character.linkStatus === "pending") return "proof pending";
		return "starter preview";
	}

	function renderLinkChallenge() {
		if (!linkProof) return;
		if (!pendingLinkChallenge || !pendingLinkChallenge.command) {
			linkProof.hidden = true;
			if (linkCommand) linkCommand.value = "";
			return;
		}
		linkProof.hidden = false;
		linkCommand.value = pendingLinkChallenge.command;
		linkExpiry.textContent = (pendingLinkChallenge.minutesRemaining || 15) + " min";
	}

	function renderAdminAccount(state) {
		var account = state && state.account ? state.account : null;
		var admin = state && state.admin ? state.admin : {};
		var security = state && state.security ? state.security : {};
		var auth = state && state.auth ? state.auth : security.auth || {};
		var abuse = state && state.abuse ? state.abuse : {};
		var starterCard = abuse.starterCard || {};
		var rewards = state && state.rewards ? state.rewards : {};
		var characters = state && Array.isArray(state.characters) ? state.characters : [];
		var subscription = account && account.subscription ? account.subscription : {};

		if (adminStatusTile) adminStatusTile.textContent = account ? titleCase(account.status || admin.status || "active") : "-";
		if (adminStatusNote) adminStatusNote.textContent = account ? (admin.note || "No staff note") : "No account loaded";
		if (adminSubscriptionTile) adminSubscriptionTile.textContent = account ? (subscription.active ? subscription.label : "Unsubscribed") : "-";
		if (adminSubscriptionNote) adminSubscriptionNote.textContent = account ? (subscription.active ? "Active account sub" : "No active sub") : "Account-wide";
		if (adminCharacterTile) adminCharacterTile.textContent = String(characters.length || 0);
		var rewardStatus = rewards.starterCardStatus || (rewards.starterSubscriptionCards ? "waiting" : "");
		if (adminStarterTile) adminStarterTile.textContent = account ? titleCase(starterCard.status || rewardStatus || "none") : "-";
		if (adminStarterNote) {
			adminStarterNote.textContent = account
				? rewardStatus === "claimed"
					? "Claimed in game"
					: rewards.starterSubscriptionCards ? rewards.starterSubscriptionCards + " card waiting" : adminStarterReasons(starterCard)
				: "Review state";
		}
		if (adminAccountTitle) adminAccountTitle.textContent = account ? (account.displayName || account.email || "Account") : "No account loaded";
		if (adminAccountId) adminAccountId.textContent = account ? "ID " + account.id : "ID -";
		if (adminStatusSelect && account) adminStatusSelect.value = account.status || admin.status || "active";
		if (adminStatusNoteInput && account) adminStatusNoteInput.value = admin.note || "";

		if (adminAccountTable) {
			adminAccountTable.innerHTML = [
				'<div class="table-row table-head"><span>Field</span><span>Value</span></div>',
				accountTableRow("Email", account ? account.email : "-"),
				accountTableRow("Status", account ? titleCase(account.status || admin.status || "active") : "-"),
				accountTableRow("Google", auth.googleConnected ? "Connected" : "Not connected"),
				accountTableRow("Password", auth.passwordEnabled ? "Enabled" : "Google only"),
				accountTableRow("Recovery codes", security.recoveryCodes ? String(security.recoveryCodes.activeCount || 0) : "0"),
				accountTableRow("Sessions", Array.isArray(security.sessions) ? String(security.sessions.length) : "0")
			].join("");
		}

		if (adminCharacterTable) {
			adminCharacterTable.innerHTML = '<div class="table-row table-head"><span>Name</span><span>State</span><span>Sub</span></div>' +
				(characters.length ? characters.map(function (character) {
					return [
						'<div class="table-row">',
						"<span>" + escapeHtml(character.name || "") + "</span>",
						"<span>" + escapeHtml(characterStateLabel(character)) + "</span>",
						"<span>" + escapeHtml(character.subscription || subscription.label || "Unsubscribed") + "</span>",
						"</div>"
					].join("");
				}).join("") : '<div class="table-row"><span>No characters</span><span></span><span></span></div>');
		}

		renderAdminSignals(admin.abuseSignals || []);
		renderAdminAudit(admin.recentAudit || []);
	}

	function accountTableRow(label, value) {
		return '<div class="table-row"><span>' + escapeHtml(label) + '</span><span>' + escapeHtml(value || "-") + '</span></div>';
	}

	function renderAdminSignals(signals) {
		if (!adminSignalTable) return;
		adminSignalTable.innerHTML = '<div class="table-row table-head"><span>Type</span><span>Bucket</span><span>When</span></div>' +
			(signals.length ? signals.map(function (signal) {
				return [
					'<div class="table-row">',
					"<span>" + escapeHtml(signal.signalType || "") + "</span>",
					"<span>" + escapeHtml(signal.bucket || "") + "</span>",
					"<span>" + escapeHtml(formatSessionDate(signal.createdAt)) + "</span>",
					"</div>"
				].join("");
			}).join("") : '<div class="table-row"><span>No recent signals</span><span></span><span></span></div>');
	}

	function renderAdminAudit(events) {
		if (!adminAuditTable) return;
		adminAuditTable.innerHTML = '<div class="table-row table-head"><span>Event</span><span>Metadata</span><span>When</span></div>' +
			(events.length ? events.map(function (event) {
				return [
					'<div class="table-row">',
					"<span>" + escapeHtml(event.type || "") + "</span>",
					"<span>" + escapeHtml(adminMetadataSummary(event.metadata || {})) + "</span>",
					"<span>" + escapeHtml(formatSessionDate(event.createdAt)) + "</span>",
					"</div>"
				].join("");
			}).join("") : '<div class="table-row"><span>No recent events</span><span></span><span></span></div>');
	}

	function adminMetadataSummary(metadata) {
		return Object.keys(metadata).slice(0, 4).map(function (key) {
			return key + "=" + metadata[key];
		}).join(", ") || "-";
	}

	function adminStarterReasons(starterCard) {
		var reasons = starterCard && Array.isArray(starterCard.reasons) ? starterCard.reasons : [];
		return reasons.length ? reasons.join(", ") : "No card waiting";
	}

	function titleCase(value) {
		return String(value || "").replace(/[_-]+/g, " ").replace(/\b\w/g, function (letter) {
			return letter.toUpperCase();
		});
	}

	function renderSecurity(security) {
		if (!security || !securityScore) return;
		var auth = security.auth || {};
		var passwordEnabled = Boolean(auth.passwordEnabled);
		securityScore.textContent = security.score;
		securityEmailCheck.checked = Boolean(security.emailVerified);
		securityRecoveryCheck.checked = Boolean(security.recoveryCodes && security.recoveryCodes.activeCount > 0);
		securityPasswordCheck.checked = Boolean(passwordEnabled || auth.googleConnected);
		securitySessionCheck.checked = Array.isArray(security.sessions) && security.sessions.length <= 2;
		generateRecovery.textContent = security.recoveryCodes && security.recoveryCodes.activeCount
			? "Rotate codes"
			: "New codes";
		if (passwordForm) passwordForm.hidden = !passwordEnabled;
		if (securityMessage && !passwordEnabled && auth.googleConnected) {
			securityMessage.textContent = "Google sign-in is active. Password controls are hidden for this account.";
		}
		renderSessions(security.sessions || []);
	}

	function renderRecoveryCodes(codes) {
		if (!recoveryCodes) return;
		if (!codes.length) {
			recoveryCodes.hidden = true;
			recoveryCodes.innerHTML = "";
			return;
		}
		recoveryCodes.hidden = false;
		recoveryCodes.innerHTML = codes.map(function (code) {
			return "<code>" + escapeHtml(code) + "</code>";
		}).join("");
	}

	function renderSessions(sessions) {
		if (!sessionTable) return;
		var rows = sessions.length ? sessions.map(function (session) {
			return [
				'<div class="table-row">',
				"<span>" + escapeHtml(formatSessionDate(session.lastSeenAt || session.createdAt)) + "</span>",
				"<span>" + escapeHtml(session.location || "Local dev") + "</span>",
				"<span>" + escapeHtml(session.client || "Portal API") + "</span>",
				'<span class="' + (session.current ? "good" : "") + '">' + (session.current ? "Current" : "Active") + "</span>",
				"</div>"
			].join("");
		}).join("") : '<div class="table-row"><span>No active sessions</span><span></span><span></span><span></span></div>';
		sessionTable.innerHTML = '<div class="table-row table-head"><span>Date</span><span>Location</span><span>Client</span><span>Result</span></div>' + rows;
	}

	function renderRankTable() {
		if (!rankTable) return;
		rankTable.innerHTML = '<div class="table-row table-head"><span>Rank</span><span>Player</span><span>Combat</span><span>XP</span></div>' +
			ranks.map(function (row) {
				return '<div class="table-row"><span>' + escapeHtml(row[0]) + '</span><span>' + escapeHtml(row[1]) + '</span><span>' + escapeHtml(row[2]) + '</span><span>' + escapeHtml(row[3]) + '</span></div>';
			}).join("");
	}

	function renderMarketTable() {
		if (!marketTable) return;
		marketTable.innerHTML = '<div class="table-row table-head"><span>Item</span><span>Average</span><span>24h</span><span>Depth</span></div>' +
			marketRows.map(function (row) {
				var movement = row[2].charAt(0) === "+" ? "good" : row[2].charAt(0) === "-" ? "bad" : "";
				return '<div class="table-row"><span>' + escapeHtml(row[0]) + '</span><span>' + escapeHtml(row[1]) + '</span><span class="' + movement + '">' + escapeHtml(row[2]) + '</span><span>' + escapeHtml(row[3]) + '</span></div>';
			}).join("");
	}

	function renderWorldFeed() {
		if (!worldFeed) return;
		worldFeed.innerHTML = feedRows.map(function (row) {
			return '<div class="event-row ' + escapeAttr(row[0]) + '"><span></span><p>' + escapeHtml(row[1]) + '</p><time>' + escapeHtml(row[2]) + '</time></div>';
		}).join("");
	}

	function renderNews(target, rows) {
		if (!target) return;
		target.innerHTML = rows.map(function (row) {
			return [
				'<div class="event-row ' + escapeAttr(row.type || "title") + '">',
				"<span></span>",
				"<p>" + escapeHtml(row.text || "") + "</p>",
				"<time>" + escapeHtml(row.time || "") + "</time>",
				"</div>"
			].join("");
		}).join("");
	}

	function renderIntegrity(integrity) {
		if (!integrity) return;
		var staff = integrity.staffCommands || {};
		var build = integrity.build || {};
		var items = integrity.itemProvenance || {};
		var scans = integrity.economyScans || {};
		var accounts = integrity.accountIntegrity || {};
		var categories = Array.isArray(staff.categories) ? staff.categories : [];
		var topCategory = categories.length ? categories[0] : null;
		var total = Number(staff.total24h || 0);
		var blocked = Number(staff.blocked24h || 0);
		var itemReceipts = Number(items.total24h || 0);
		var generatedAt = integrity.generatedAt || "";

		setText(integrityUpdated, generatedAt ? "Updated " + relativeTime(generatedAt) : "Waiting for snapshot");
		setText(integrityStaffTotal, formatCompactNumber(total));
		setText(integrityStaffDetail, staffStatusDetail(staff.status, total));
		setText(integrityBlockedTotal, formatCompactNumber(blocked));
		setText(integrityBlockedDetail, blocked === 1 ? "attempt stopped" : "attempts stopped");
		setText(integrityBuildStatus, titleStatus(build.status || "available"));
		setText(integrityBuildDetail, build.evidence || "Launcher manifest hashes");
		setText(integrityItemsStatus, formatCompactNumber(itemReceipts));
		setText(integrityItemsDetail, itemReceipts === 1 ? "receipt 24h" : "receipts 24h");

		setText(trustPasswordsStatus, "Now");
		renderTrustList(trustPasswordsList, [
			"Download first. Discord stays optional.",
			"Use a fresh game password.",
			"Public pages do not expose private account data."
		]);

		setText(trustSourceStatus, titleStatus(build.status || "available"));
		renderTrustList(trustSourceList, sourceListItems(build, integrity, staff));
		renderSourceBuildBoard(build);

		setText(trustStaffStatus, titleStatus(staff.status || "waiting_for_game_snapshot"));
		renderTrustList(trustStaffList, staffListItems(staff, topCategory));

		setText(trustItemsStatus, titleStatus(itemTrustStatus(items)));
		renderTrustList(trustItemsList, itemReceiptListItems(items));
		renderItemReceiptBoard(items);

		setText(trustScansStatus, titleStatus(scanTrustStatus(scans, accounts)));
		renderTrustList(trustScansList, scanListItems(scans, accounts));
	}

	function scanListItems(scans, accounts) {
		var status = titleStatus(scanTrustStatus(scans, accounts)).toLowerCase();
		var economyFlagged = Number(scans.flagged || 0);
		var accountFlagged = Number(accounts.flagged || 0);
		var flagged = economyFlagged + accountFlagged;
		var high = Number(scans.highSeverity || 0) + Number(accounts.highSeverity || 0);
		var review = Number(accounts.review || 0);
		var trackedItems = Number(scans.trackedItems || 0);
		var checkedPlayers = Math.max(Number(scans.checkedPlayers || 0), Number(accounts.checkedPlayers || 0));
		var watchedCacheRows = Number(accounts.watchedCacheRows || 0);
		var privileged = Number(accounts.privilegedAccounts || 0);
		var sensitive = Number(accounts.recentSensitiveCommands24h || 0);
		var lastScanAt = scans.lastScanAt || accounts.lastScanAt || "";
		var rows = [
			lastScanAt ? "Last scan " + relativeTime(lastScanAt) + "." : "Nightly checks are waiting for the first snapshot.",
			"Checked " + formatCompactNumber(trackedItems) + " item rows across " + formatCompactNumber(checkedPlayers) + " players.",
			"Account review: " + formatCompactNumber(privileged) + " privileged, " + formatCompactNumber(watchedCacheRows) + " watched cache rows, " + formatCompactNumber(sensitive) + " sensitive staff commands 24h.",
			"Flagged: " + formatCompactNumber(flagged) + ". High priority: " + formatCompactNumber(high) + ". Review: " + formatCompactNumber(review) + "."
		];
		if (flagged > 0) {
			var categories = (Array.isArray(scans.categories) ? scans.categories : [])
				.concat(Array.isArray(accounts.categories) ? accounts.categories : []);
			var top = categories.length ? categories[0] : null;
			rows.push(top ? "Top finding: " + titleStatus(top.category) + " (" + formatCompactNumber(top.count) + ")." : "Private findings are available to staff.");
		} else if (lastScanAt) {
			rows.push("No impossible stats, broken item refs, invalid account flags, or impossible stacks found.");
		} else {
			rows.push("Scanner status: " + status + ".");
		}
		return rows;
	}

	function scanTrustStatus(scans, accounts) {
		var scanStatus = String(scans.status || "");
		var accountStatus = String(accounts.status || "");
		if (scanStatus === "flagged" || accountStatus === "flagged") return "flagged";
		if (scans.lastScanAt || accounts.lastScanAt) return "clean";
		return scanStatus || accountStatus || "planned";
	}

	function itemReceiptListItems(items) {
		var status = titleStatus(items.status || "not_recording").toLowerCase();
		var staffMints = Number(items.staffMints24h || 0);
		var npcDrops = Number(items.npcDrops24h || 0);
		var transfers = Number(items.transfers24h || 0);
		var tracked = Number(items.trackedItems || items.totalEvents || 0);
		var lines = [
			"Item receipts are " + status + ".",
			"24h: " + formatCompactNumber(npcDrops) + " NPC drops, " + formatCompactNumber(transfers) + " player moves, " + formatCompactNumber(staffMints) + " staff mints.",
			"Tracked receipts: " + formatCompactNumber(tracked) + "."
		];
		if (items.reason === "missing_item_provenance_table") {
			lines.push("The game database is waiting for the item provenance migration.");
		} else {
			lines.push("Covered: drops, pickups, deaths, trades, shops, and auction-house moves.");
		}
		return lines;
	}

	function renderSourceBuildBoard(build) {
		if (!trustSourceBoard || !trustBuildArtifacts) return;
		var artifacts = Array.isArray(build.artifacts) ? build.artifacts.filter(function (artifact) {
			return artifact.publicDownload !== false;
		}).slice(0, 4) : [];
		var manifest = build.manifest || {};
		var source = build.source || {};
		var hasProof = artifacts.length > 0 || source.repositoryUrl || manifest.url;
		trustSourceBoard.hidden = !hasProof;
		if (!hasProof) {
			trustBuildArtifacts.innerHTML = "";
			return;
		}
		if (trustSourceRepo) {
			trustSourceRepo.href = source.repositoryUrl || "https://github.com/voidscape-gg/voidscape";
			trustSourceRepo.textContent = source.repositoryUrl ? "View AGPL source" : titleStatus(source.status || "Source pending");
		}
		if (trustSourceCommit) {
			var commitText = source.shortCommit
				? (source.dirty ? "Baseline " : "Commit ") + source.shortCommit + (source.dirty ? " - live patch pending" : "")
				: "Commit pending";
			trustSourceCommit.textContent = commitText;
			trustSourceCommit.title = source.commit || commitText;
		}
		if (trustSourceManifest) {
			var manifestText = manifest.status === "available"
				? formatCompactNumber(manifest.fileCount || 0) + " manifest hashes"
				: titleStatus(manifest.status || "manifest pending");
			trustSourceManifest.textContent = manifestText;
			trustSourceManifest.title = manifest.clientSha256 || manifestText;
		}
		trustBuildArtifacts.innerHTML = artifacts.map(function (artifact) {
			var available = artifact.available === true && artifact.sha256;
			var hash = available ? shortHash(artifact.sha256) : "Not built";
			var updated = artifact.updatedAt ? relativeTime(artifact.updatedAt) : "";
			return [
				'<a class="build-proof-tile' + (available ? " is-ready" : "") + '" href="' + escapeAttr(artifact.url || "#") + '" title="' + escapeAttr(artifact.sha256 || "Hash pending") + '"' + (available ? " download" : ' aria-disabled="true"') + ">",
				"<b>" + escapeHtml(artifact.label || "Download") + "</b>",
				"<code>" + escapeHtml(hash) + "</code>",
				"<span>" + escapeHtml(available ? ((artifact.state || formatBytesLabel(artifact.sizeBytes)) + (updated ? " - " + updated : "")) : "Hash pending") + "</span>",
				"</a>"
			].join("");
		}).join("");
	}

	function sourceListItems(build, integrity, staff) {
		var source = build.source || {};
		var manifest = build.manifest || {};
		var rows = ["Voidscape stays AGPL source-available."];
		if (source.shortCommit && source.dirty) {
			rows.push("Live files are based on commit " + source.shortCommit + "; newer deployed patches are marked until published.");
		} else if (source.shortCommit) {
			rows.push("Live source commit: " + source.shortCommit + ".");
		} else {
			rows.push("Source commit metadata is waiting for the next publish.");
		}
		rows.push(build.evidence || "Download hashes are generated from served files.");
		if (manifest.status === "available") {
			rows.push("Launcher manifest lists " + formatCompactNumber(manifest.fileCount || 0) + " hashed files.");
		} else {
			rows.push("Integrity source: " + sourceLabel(integrity.source || staff.source || "snapshot") + ".");
		}
		return rows;
	}

	function shortHash(value) {
		var hash = String(value || "");
		if (hash.length <= 24) return hash || "Hash pending";
		return hash.slice(0, 12) + "..." + hash.slice(-8);
	}

	function formatBytesLabel(value) {
		var bytes = Number(value || 0);
		if (!Number.isFinite(bytes) || bytes <= 0) return "Built artifact";
		var mb = bytes / (1024 * 1024);
		return "Built " + (mb >= 10 ? mb.toFixed(1) : mb.toFixed(2)) + " MB";
	}

	function itemTrustStatus(items) {
		var status = String(items.status || "designing");
		var tracked = Number(items.trackedItems || items.totalEvents || 0);
		if (tracked > 0 && status.indexOf("recording") === 0) return "recording";
		return status;
	}

	function renderItemReceiptBoard(items) {
		if (!trustItemsBoard || !trustItemsSources || !trustItemsRecent) return;
		var sources = Array.isArray(items.sources) ? items.sources.slice(0, 5) : [];
		var recent = Array.isArray(items.recent) ? items.recent.slice(0, 4) : [];
		var total = Number(items.total7d || items.totalEvents || items.trackedItems || 0);
		var maxCount = sources.reduce(function (max, row) {
			return Math.max(max, Number(row.count || 0));
		}, 0);
		var hasReceipts = sources.length > 0 || recent.length > 0;
		trustItemsBoard.hidden = !hasReceipts;
		setText(trustItemsBoardTotal, formatCompactNumber(total) + (total === 1 ? " receipt" : " receipts"));
		if (!hasReceipts) {
			trustItemsSources.innerHTML = "";
			trustItemsRecent.innerHTML = "";
			return;
		}
		trustItemsSources.innerHTML = sources.map(function (row) {
			var count = Number(row.count || 0);
			var width = maxCount > 0 ? Math.max(8, Math.round((count / maxCount) * 100)) : 0;
			return [
				'<div class="receipt-source-row" style="--receipt-width: ' + width + '%">',
				"<b>" + escapeHtml(sourceLabel(row.category || "unknown")) + "</b>",
				'<div class="receipt-source-meter"><i></i></div>',
				"<em>" + escapeHtml(formatCompactNumber(count)) + "</em>",
				"</div>"
			].join("");
		}).join("");
		trustItemsRecent.innerHTML = recent.map(function (row) {
			var amount = Number(row.amount || 0);
			var itemLabel = "Item " + formatCompactNumber(row.catalogId || 0);
			if (amount > 1) itemLabel += " x" + formatCompactNumber(amount);
			return [
				'<div class="receipt-event">',
				"<b>" + escapeHtml(titleStatus(row.eventType || "item_event")) + "</b>",
				"<small>" + escapeHtml(relativeTime(row.at)) + "</small>",
				"<em>" + escapeHtml(sourceLabel(row.source || "unknown") + " -> " + sourceLabel(row.destination || "unknown")) + "</em>",
				"<strong>" + escapeHtml(itemLabel) + "</strong>",
				"</div>"
			].join("");
		}).join("");
	}

	function staffListItems(staff, topCategory) {
		var total = Number(staff.total24h || 0);
		var blocked = Number(staff.blocked24h || 0);
		if (!total) {
			return [
				staff.status === "waiting_for_game_snapshot" ? "Waiting for the live game snapshot." : "No recent staff command attempts recorded.",
				"Public summaries hide staff names, IPs, player names, and raw command args.",
				"Blocked beta-admin attempts will appear here after the exporter runs."
			];
		}
		var rows = [
			formatCompactNumber(total) + " staff command attempts tracked in the last 24 hours.",
			formatCompactNumber(blocked) + " beta-guard block" + (blocked === 1 ? "" : "s") + " recorded."
		];
		if (topCategory) {
			rows.push("Top category: " + titleStatus(topCategory.category) + " (" + formatCompactNumber(topCategory.count) + ").");
		}
		rows.push("Public summaries hide staff names, IPs, player names, and raw command args.");
		return rows;
	}

	function renderTrustList(target, rows) {
		if (!target) return;
		target.innerHTML = rows.map(function (row) {
			return "<li>" + escapeHtml(row) + "</li>";
		}).join("");
	}

	function setText(target, value) {
		if (target) target.textContent = value;
	}

	function staffStatusDetail(status, total) {
		if (status === "waiting_for_game_snapshot") return "Waiting for snapshot";
		if (status === "missing_staff_log_table") return "No staff log table";
		if (status === "openrsc_unavailable") return "Game DB unavailable";
		if (!total) return "Quiet last 24h";
		return "last 24h";
	}

	function scanDetail(scans) {
		if (scans.lastScanAt) return "Last scan " + relativeTime(scans.lastScanAt);
		return "Economy checks";
	}

	function sourceLabel(value) {
		return String(value || "snapshot").replace(/[_-]+/g, " ");
	}

	function titleStatus(value) {
		return String(value || "unknown").replace(/[_-]+/g, " ").replace(/\b\w/g, function (letter) {
			return letter.toUpperCase();
		});
	}

	function formatCompactNumber(value) {
		var number = Number(value || 0);
		if (!Number.isFinite(number)) number = 0;
		return Math.floor(number).toLocaleString();
	}

	function relativeTime(value) {
		var timestamp = Date.parse(value || "");
		if (!Number.isFinite(timestamp)) return "soon";
		var seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
		if (seconds < 60) return "just now";
		var minutes = Math.floor(seconds / 60);
		if (minutes < 60) return minutes + "m ago";
		var hours = Math.floor(minutes / 60);
		if (hours < 48) return hours + "h ago";
		var days = Math.floor(hours / 24);
		return days + "d ago";
	}

	function renderDownloads(rows) {
		if (!downloadActions) return;
		var publicRows = rows.filter(function (row) {
			return isPublicDownloadRow(row);
		});
		var displayRows = publicRows.length ? publicRows : rows;
		var launcherRow = rows.find(function (row) {
			return isLauncherDownloadRow(row) && row.available && row.url && row.url !== "#";
		});
		if (launcherRow && prelaunchDownload) {
			prelaunchDownload.href = launcherRow.url;
		}
		downloadActions.innerHTML = displayRows.map(function (row) {
			var available = Boolean(row.available && row.url && row.url !== "#");
			var tag = available ? "a" : "button";
			var attrs = available ? linkAttributes(row, true) : ' type="button" disabled';
			return [
				"<" + tag + ' class="' + downloadActionClass(row, available) + '"' + attrs + ">",
				"<strong>" + escapeHtml(row.label || "") + "</strong>",
				"<span>" + escapeHtml(row.state || "") + "</span>",
				"</" + tag + ">"
			].join("");
		}).join("");
	}

	function downloadActionClass(row, available) {
		var classes = ["ghost-button", "download-action"];
		if (available) classes.push("is-ready");
		if (isWebClientRow(row)) classes.push("web-client-action");
		return classes.join(" ");
	}

	function linkAttributes(row, withFunnel) {
		var attrs = ' href="' + escapeAttr(row.url || "#") + '"';
		if (!isWebClientRow(row) && !row.external) attrs += " download";
		if (withFunnel) attrs += ' data-funnel-event="' + escapeAttr(downloadFunnelEvent(row)) + '"';
		if (row.external) attrs += ' rel="noopener"';
		return attrs;
	}

	function renderLiveBuildBasics(state) {
		var downloads = Array.isArray(state.downloads) ? state.downloads : [];
		var launcher = downloads.find(function (row) {
			return isLauncherDownloadRow(row);
		}) || downloads[0] || {};
		var integrityGeneratedAt = state.integrity && state.integrity.generatedAt;
		var build = state.integrity && state.integrity.build || {};
		var manifest = build.manifest || {};
		var patch = state.status && state.status.patch ? String(state.status.patch) : "beta";
		if (landingLiveBuild) {
			landingLiveBuild.textContent = patch === "prelaunch" || patch === "countdown"
				? "Launch build"
				: patch === "beta" ? "Beta build" : "Build " + patch;
		}
		if (landingLiveBuildDetail) {
			if (manifest.version) {
				landingLiveBuildDetail.textContent = "Manifest " + String(manifest.version).slice(0, 12);
			} else if (launcher.updatedAt) {
				landingLiveBuildDetail.textContent = "Launcher updated " + relativeTime(launcher.updatedAt);
			} else {
				landingLiveBuildDetail.textContent = launcher.available ? "Launcher ready" : "Build waiting";
			}
		}
		if (landingLiveUpdated) {
			landingLiveUpdated.textContent = integrityGeneratedAt ? relativeTime(integrityGeneratedAt) : "Checking";
		}
		if (landingLiveUpdatedDetail) {
			landingLiveUpdatedDetail.textContent = integrityGeneratedAt ? "Integrity snapshot" : "Status snapshot";
		}
	}

	function downloadFunnelEvent(row) {
		if (!row) return "download";
		if (isWebClientRow(row)) return "play_web";
		if (row.slug === "android-apk") return "download_android";
		if (row.slug === "launcher" || isLauncherDownloadRow(row)) return "download_launcher";
		return "download";
	}

	function setupFunnelTracking() {
		document.addEventListener("click", function (event) {
			var target = event.target && event.target.closest ? event.target.closest("[data-funnel-event]") : null;
			if (!target) return;
			trackFunnelEvent(target.getAttribute("data-funnel-event"), target);
		});
	}

	function trackFunnelEvent(eventName, target) {
		if (window.location.protocol === "file:") return;
		var payload = JSON.stringify({
			event: eventName || "click",
			target: targetLabel(target),
			href: target && target.getAttribute ? target.getAttribute("href") || "" : "",
			page: window.location.pathname + window.location.search + window.location.hash,
			referrer: document.referrer || "",
			utm: utmPayload()
		});
		try {
			if (navigator.sendBeacon) {
				navigator.sendBeacon("/api/funnel/click", new Blob([payload], { type: "application/json" }));
				return;
			}
		} catch (error) {
			// Fall through to fetch.
		}
		fetch("/api/funnel/click", {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: payload,
			keepalive: true
		}).catch(function () {});
	}

	function targetLabel(target) {
		if (!target) return "";
		var text = String(target.textContent || "").replace(/\s+/g, " ").trim();
		return text.slice(0, 80);
	}

	function utmPayload() {
		var params = new URLSearchParams(window.location.search || "");
		var payload = {};
		["utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term"].forEach(function (key) {
			var value = params.get(key);
			if (value) payload[key] = value.slice(0, 80);
		});
		return payload;
	}

	function isPublicDownloadRow(row) {
		if (!row) return false;
		var url = String(row.url || "").toLowerCase();
		return row.publicDownload === true
			|| isWebClientRow(row)
			|| row.slug === "launcher"
			|| row.slug === "android-apk"
			|| url.indexOf("/downloads/launcher") !== -1
			|| url.indexOf("/downloads/android-apk") !== -1;
	}

	function isWebClientRow(row) {
		if (!row) return false;
		var url = String(row.url || "").toLowerCase();
		return row.slug === "web-client" || url.indexOf("/play") !== -1 || url.indexOf("voidscape.gg/play") !== -1;
	}

	function isLauncherDownloadRow(row) {
		if (!row) return false;
		var label = String(row.label || "").toLowerCase();
		var url = String(row.url || "").toLowerCase();
		return row.slug === "launcher" || url.indexOf("/downloads/launcher") !== -1 || label.indexOf("launcher") !== -1;
	}

	function defaultCharacters() {
		return [
			{
				name: "Zamak42",
				path: "Warrior",
				image: "assets/rsc-knight.png",
				combat: 87,
				total: "1,194",
				quest: 47,
				kills: 28,
				status: "Lumbridge",
				title: "The Conqueror",
				subscription: "6 days 21 hours",
				lastLogin: "2 min ago",
				appearance: "Male, dark hair, bronze-and-rune melee kit",
				gear: ["Rune 2h sword", "Bronze plate body", "Red cape", "Amulet of strength"],
				appearanceData: { hairColour: 2, topColour: 4, trouserColour: 8, skinColour: 1 },
				equipment: []
			},
			{
				name: "VoidMage",
				path: "Arcanist",
				image: "assets/rsc-mage.png",
				combat: 54,
				total: "767",
				quest: 22,
				kills: 4,
				status: "Varrock",
				title: "Void-Touched",
				subscription: "6 days 21 hours",
				lastLogin: "1h ago",
				appearance: "Blue robe, staffless starter look, spell pouch",
				gear: ["Wizard robe", "Amulet of magic", "Law runes", "Air runes"],
				appearanceData: { hairColour: 5, topColour: 10, trouserColour: 6, skinColour: 0 },
				equipment: []
			},
			{
				name: "WildRanger",
				path: "Forager",
				image: "assets/rsc-ranger.png",
				combat: 61,
				total: "823",
				quest: 19,
				kills: 13,
				status: "Edgeville",
				title: "Red Revenant",
				subscription: "6 days 21 hours",
				lastLogin: "14m ago",
				appearance: "Green field gear, bow, wilderness-ready pack",
				gear: ["Maple shortbow", "Leather body", "Bronze arrows", "Fishing net"],
				appearanceData: { hairColour: 3, topColour: 7, trouserColour: 3, skinColour: 2 },
				equipment: []
			}
		];
	}

	function loadRoster() {
		try {
			var roster = JSON.parse(localStorage.getItem(rosterKey));
			return Array.isArray(roster) ? roster.slice(0, maxCharacters) : [];
		} catch (error) {
			return [];
		}
	}

	function saveRoster() {
		localStorage.setItem(rosterKey, JSON.stringify(characters.slice(0, maxCharacters)));
	}

	function loadFounder() {
		try {
			return JSON.parse(localStorage.getItem(founderKey));
		} catch (error) {
			return null;
		}
	}

	function saveFounder(founder) {
		localStorage.setItem(founderKey, JSON.stringify(founder));
	}

	function captureReferralFromLocation() {
		var params = new URLSearchParams(window.location.search || "");
		var code = normalizeReferralCode(params.get("ref") || params.get("invite") || "");
		if (code) {
			localStorage.setItem(referralKey, code);
			return code;
		}
		return normalizeReferralCode(localStorage.getItem(referralKey) || "");
	}

	function currentReferralCode() {
		activeReferralCode = normalizeReferralCode(activeReferralCode || localStorage.getItem(referralKey) || "");
		return activeReferralCode;
	}

	function normalizeName(value) {
		return value.trim().replace(/\s+/g, " ");
	}

	function isGamePassword(value, minLength) {
		var text = String(value || "");
		var min = minLength || 4;
		return text.length >= min && text.length <= 20 && /^[a-zA-Z0-9]+$/.test(text);
	}

	function normalizeReferralCode(value) {
		return String(value || "").trim().toUpperCase().replace(/[^A-Z0-9-]/g, "").slice(0, 18);
	}

	function makeCode(name) {
		var stem = name.toUpperCase().replace(/[^A-Z0-9]+/g, "").slice(0, 7) || "VOID";
		var suffix = Math.random().toString(36).slice(2, 6).toUpperCase();
		return stem + "-" + suffix;
	}

	function makeInviteLink(code) {
		var url = new URL(window.location.href);
		url.hash = "account";
		url.search = "";
		url.searchParams.set("ref", code);
		return url.toString();
	}

	function referralCreditMessage(founder, fallback) {
		if (founder && founder.referredBy && founder.referredBy.code) {
			return "Account saved. Invite credited to " + (founder.referredBy.username || founder.referredBy.code) + ".";
		}
		return fallback;
	}

	function referralErrorMessage(error) {
		if (error.code === "self_referral_not_allowed") {
			return "That invite belongs to this reservation. Clear it or use another founder's link.";
		}
		return "Invite code was not found. Clear it or use a newer invite link.";
	}

	function copyText(text, fallbackInput) {
		if (navigator.clipboard && window.isSecureContext) {
			navigator.clipboard.writeText(text);
			return;
		}
		if (fallbackInput) {
			fallbackInput.select();
			document.execCommand("copy");
			return;
		}
		var scratch = document.createElement("textarea");
		scratch.value = text;
		document.body.appendChild(scratch);
		scratch.select();
		document.execCommand("copy");
		scratch.remove();
	}

	function formatSessionDate(value) {
		var date = new Date(value);
		if (Number.isNaN(date.getTime())) return "Now";
		return date.toLocaleString("en-US", {
			month: "short",
			day: "numeric",
			hour: "numeric",
			minute: "2-digit"
		});
	}

	function escapeHtml(value) {
		return String(value).replace(/[&<>"']/g, function (character) {
			return {
				"&": "&amp;",
				"<": "&lt;",
				">": "&gt;",
				"\"": "&quot;",
				"'": "&#39;"
			}[character];
		});
	}

	function escapeAttr(value) {
		return escapeHtml(value);
	}
}());
