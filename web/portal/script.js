(function () {
	"use strict";

	var shell = document.querySelector(".portal-shell");
	var title = document.getElementById("view-title");
	var menuToggle = document.getElementById("menu-toggle");
	var workspace = document.querySelector(".workspace");
	var views = Array.prototype.slice.call(document.querySelectorAll(".view"));
	var viewLinks = Array.prototype.slice.call(document.querySelectorAll("[data-view-link]"));
	var characterCards = document.getElementById("character-cards");
	var rankTable = document.getElementById("rank-table");
	var marketTable = document.getElementById("market-table");
	var worldFeed = document.getElementById("world-feed");
	var slotTrack = document.getElementById("slot-track");
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
	var newCharacterButton = document.getElementById("new-character-button");
	var characterName = document.getElementById("character-name");
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
	var accountModeButtons = Array.prototype.slice.call(document.querySelectorAll("[data-account-mode]"));
	var googleAuthButton = document.getElementById("google-auth-button");
	var googleAuthLabel = document.getElementById("google-auth-label");
	var founderProgressLabel = document.getElementById("founder-progress-label");
	var founderRewardLabel = document.getElementById("founder-reward-label");
	var founderProgressFill = document.getElementById("founder-progress-fill");
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

	var maxCharacters = 10;
	var rosterKey = "voidscape.portal.roster";
	var selectedKey = "voidscape.portal.selectedCharacter";
	var founderKey = "voidscape.portal.founder";
	var referralKey = "voidscape.portal.referralCode";
	var sessionKey = "voidscape.portal.sessionToken";
	var sessionToken = localStorage.getItem(sessionKey) || "";
	var selectedClass = "warrior";
	var accountMode = "reserve";
	var activeReferralCode = captureReferralFromLocation();
	var pendingLinkChallenge = null;
	var retiredViews = {
		landing: "account",
		dashboard: "account",
		highscores: "account",
		market: "account",
		activity: "account",
		admin: "account",
		public: "account"
	};

	var classKits = {
		warrior: {
			path: "Warrior",
			image: "assets/rsc-knight.png",
			gear: ["Iron 2h sword", "Bronze plate body", "Bronze medium helm", "Bronze legs"],
			appearance: "Male, bronze plate set, iron two-hander",
			appearanceData: { hairColour: 2, topColour: 4, trouserColour: 8, skinColour: 1 },
			start: "Iron 2h, 10 cooked meat, bronze armour"
		},
		arcanist: {
			path: "Arcanist",
			image: "assets/rsc-mage.png",
			gear: ["Shortbow", "Blue wizard robe", "50 bronze arrows", "Air runes"],
			appearance: "Bright robe top, light gear, bow and arrows",
			appearanceData: { hairColour: 5, topColour: 10, trouserColour: 6, skinColour: 0 },
			start: "Runes, bow, 50 bronze arrows, robes"
		},
		forager: {
			path: "Forager",
			image: "assets/rsc-ranger.png",
			gear: ["Bronze axe", "Tinderbox", "Small fishing net", "100 gp"],
			appearance: "Field kit, tools, light travelling clothes",
			appearanceData: { hairColour: 3, topColour: 7, trouserColour: 3, skinColour: 2 },
			start: "Tools, bait, food, 100 gp"
		}
	};

	var characters = loadRoster();
	var selectedCharacter = localStorage.getItem(selectedKey) || characters[0].name;

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
		["Subscription card", "10,000 gp", "0.0%", "Vendor"],
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

	window.addEventListener("hashchange", function () {
		activateView((window.location.hash || "#account").replace("#", "") || "account");
	});

	if (menuToggle) {
		menuToggle.addEventListener("click", function () {
			shell.classList.toggle("nav-open");
		});
	}

	Array.prototype.slice.call(document.querySelectorAll(".class-card")).forEach(function (button) {
		button.addEventListener("click", function () {
			selectedClass = button.getAttribute("data-class");
			Array.prototype.slice.call(document.querySelectorAll(".class-card")).forEach(function (other) {
				other.classList.toggle("is-selected", other === button);
			});
			characterMessage.textContent = classKits[selectedClass].path + " selected. Starter kit: " + classKits[selectedClass].start + ".";
		});
	});

	if (queueCharacter) {
		queueCharacter.addEventListener("click", async function () {
			var name = normalizeName(characterName.value || "");
			if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
				characterMessage.textContent = "Choose a 2-12 character name.";
				characterName.focus();
				return;
			}
			if (sessionToken) {
				try {
					var state = await apiRequest("/api/characters", {
						method: "POST",
						body: {
							name: name,
							path: selectedClass
						}
					});
					applyAccountState(state);
					selectedCharacter = name;
					renderCharacters();
					renderSelectedCharacter();
					characterMessage.textContent = name + " created through the local portal API.";
					return;
				} catch (error) {
					if (error.status === 401) {
						clearSession();
					} else if (error.status === 409) {
						characterMessage.textContent = error.code === "character_limit_reached"
							? "Roster is full. Web accounts are capped at 10 characters."
							: "That character name is already taken.";
						return;
					}
				}
			}
			createLocalCharacter(name);
		});
	}

	if (newCharacterButton) {
		newCharacterButton.addEventListener("click", function () {
			characterName.focus();
			characterMessage.textContent = "Choose a path and preview a character. Current cap: 10 per web account.";
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
		});
	}

	if (useFounderReward) {
		useFounderReward.addEventListener("click", async function () {
			if (!sessionToken) {
				redeemState.textContent = "Create or sign into a portal account before using founder rewards.";
				return;
			}
			useFounderReward.disabled = true;
			try {
				var state = await apiRequest("/api/subscriptions/redeem-founder", { method: "POST" });
				applyAccountState(state);
				redeemState.textContent = "Founder reward used. Subscription extended by 7 days.";
			} catch (error) {
				if (error.status === 401) clearSession();
				redeemState.textContent = error.code === "founder_reward_not_available"
					? "No founder reward card is available on this account."
					: "Founder reward failed: " + error.code + ".";
				renderRewards(null);
			}
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

	if (googleAuthButton) {
		googleAuthButton.addEventListener("click", handleGoogleAuth);
	}

	if (founderForm) {
		accountModeButtons.forEach(function (button) {
			button.addEventListener("click", function () {
				setAccountMode(button.getAttribute("data-account-mode") || "reserve");
			});
		});

		founderForm.addEventListener("submit", async function (event) {
			event.preventDefault();
			var name = normalizeName(founderName.value || "");
			var email = (founderEmail.value || "").trim();
			var password = founderPassword ? founderPassword.value : "";
			if (accountMode === "signin") {
				if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
					founderMessage.textContent = "Enter the email on your portal account.";
					founderEmail.focus();
					return;
				}
				if (!password || password.length < 8) {
					founderMessage.textContent = "Enter your portal password.";
					founderPassword.focus();
					return;
				}
				try {
					var loggedIn = await apiRequest("/api/accounts/login", {
						method: "POST",
						body: {
							email: email,
							password: password
						}
					});
					applyAccountState(loggedIn);
					founderMessage.textContent = "Portal account loaded through the local API.";
					founderPassword.value = "";
					return;
				} catch (loginError) {
					if (loginError.status === 401) {
						founderMessage.textContent = "Email or password did not match.";
						return;
					}
					founderMessage.textContent = "API login failed: " + loginError.code + ".";
					return;
				}
			}
			if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
				founderMessage.textContent = "Choose a 2-12 character username.";
				founderName.focus();
				return;
			}
			if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
				founderMessage.textContent = "Enter a valid email.";
				founderEmail.focus();
				return;
			}
			if (password && password.length < 8) {
				founderMessage.textContent = "Portal password must be at least 8 characters.";
				founderPassword.focus();
				return;
			}
			if (password) {
				try {
					var registered = await apiRequest("/api/accounts/register", {
						method: "POST",
						body: {
							username: name,
							email: email,
							password: password,
							path: selectedClass,
							referrerCode: currentReferralCode() || undefined
						}
					});
					applyAccountState(registered);
					founderMessage.textContent = referralCreditMessage(registered.founder, "Portal account created through the local API.");
					founderPassword.value = "";
					return;
				} catch (error) {
					if (error.status === 409 && error.code === "account_exists") {
						try {
							var loggedIn = await apiRequest("/api/accounts/login", {
								method: "POST",
								body: {
									email: email,
									password: password
								}
							});
							applyAccountState(loggedIn);
							founderMessage.textContent = "Portal account loaded through the local API.";
							founderPassword.value = "";
							return;
						} catch (loginError) {
							founderMessage.textContent = "That account exists, but the password did not match.";
							return;
						}
					}
					if (error.code === "referrer_not_found" || error.code === "self_referral_not_allowed") {
						founderMessage.textContent = referralErrorMessage(error);
						return;
					}
					if (error.status && error.status !== 404) {
						founderMessage.textContent = "API signup failed: " + error.code + ".";
						return;
					}
				}
			}
			try {
				var reservation = await apiRequest("/api/founder/reservations", {
					method: "POST",
					body: {
						username: name,
						email: email,
						referrerCode: currentReferralCode() || undefined
					}
				});
				applyFounderState(reservation.founder);
					founderMessage.textContent = referralCreditMessage(reservation.founder, "Account saved through the local API.");
				return;
			} catch (error) {
				if (error.code === "referrer_not_found" || error.code === "self_referral_not_allowed") {
					founderMessage.textContent = referralErrorMessage(error);
					return;
				}
				// Static-file mode falls back to localStorage below.
			}
			var existing = loadFounder();
			var founder = {
				username: name,
				email: email,
				code: existing && existing.code ? existing.code : makeCode(name),
				invites: existing && existing.invites ? existing.invites : 0,
				referredBy: currentReferralCode() ? { code: currentReferralCode(), username: "" } : null
			};
			saveFounder(founder);
			founderMessage.textContent = "Account saved locally. Production signup still needs API and email verification.";
			renderFounder();
		});
	}

	function setAccountMode(mode) {
		accountMode = mode === "signin" ? "signin" : "reserve";
		accountModeButtons.forEach(function (button) {
			button.classList.toggle("is-active", button.getAttribute("data-account-mode") === accountMode);
		});
		if (founderNameRow) founderNameRow.hidden = accountMode === "signin";
		if (founderTitle) founderTitle.textContent = accountMode === "signin" ? "Sign in" : "Create account";
		if (founderSubmit) founderSubmit.textContent = accountMode === "signin" ? "Sign in" : "Save account";
		if (googleAuthLabel) googleAuthLabel.textContent = accountMode === "signin" ? "Sign in with Google" : "Continue with Google";
		if (founderPassword) founderPassword.setAttribute("autocomplete", accountMode === "signin" ? "current-password" : "new-password");
		if (founderMessage) {
			founderMessage.textContent = accountMode === "signin"
				? "Returning players can load their portal account."
				: "Create a portal account, then add characters and subscriptions.";
		}
	}

	async function handleGoogleAuth() {
		var email = (founderEmail.value || "").trim();
		var name = normalizeName(founderName.value || "");
		if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
			founderMessage.textContent = "Enter the email you want to use with Google.";
			founderEmail.focus();
			return;
		}
		googleAuthButton.disabled = true;
		try {
			var state = await apiRequest("/api/accounts/google/dev", {
				method: "POST",
				body: {
					email: email,
					displayName: name || email.split("@")[0],
					username: name || undefined,
					path: selectedClass,
					referrerCode: currentReferralCode() || undefined
				}
			});
			applyAccountState(state);
			setAccountMode("signin");
			founderPassword.value = "";
			founderMessage.textContent = "Google sign-in simulated through the local API. Production will use Google's browser OAuth flow.";
		} catch (error) {
			if (error.status === 409 && error.code === "google_identity_conflict") {
				founderMessage.textContent = "This portal account is already linked to another Google identity.";
			} else if (error.code === "referrer_not_found" || error.code === "self_referral_not_allowed") {
				founderMessage.textContent = referralErrorMessage(error);
			} else if (error.status && error.status !== 404) {
				founderMessage.textContent = "Google sign-in failed: " + error.code + ".";
			} else {
				founderMessage.textContent = "Start the local portal API to test Google sign-in.";
			}
		} finally {
			googleAuthButton.disabled = false;
		}
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
				founderMessage.textContent = state.founder.freeSubscriptionUnlocked
					? "Free weekly subscription card unlocked through the local API."
					: "Invite credited through the local API.";
				return;
			} catch (error) {
				// Static-file mode falls back to localStorage below.
			}
			founder.invites = Math.min(2, (founder.invites || 0) + 1);
			saveFounder(founder);
			founderMessage.textContent = founder.invites >= 2
				? "Free weekly subscription card unlocked in this local prototype."
				: "Invite credited locally. One more verified invite unlocks the card.";
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
		renderRankTable();
		renderMarketTable();
		renderWorldFeed();
	}

	async function hydrateFromApi() {
		try {
			await apiRequest("/api/health");
			var publicState = await apiRequest("/api/public");
			applyPublicState(publicState);
			if (!sessionToken) {
				founderMessage.textContent = "Local API online. Add a portal password or use Google to create an account session.";
				return;
			}
			var state = await apiRequest("/api/account");
			applyAccountState(state);
			founderMessage.textContent = "Portal account loaded from the local API.";
		} catch (error) {
			// Opening index.html directly or serving it statically keeps using localStorage.
		}
	}

	function applyPublicState(state) {
		if (!state) return;
		if (state.status) {
			if (serverWorldLabel) serverWorldLabel.textContent = state.status.world || "World 1";
			if (serverOnlineCount) serverOnlineCount.textContent = (state.status.playersOnline || 0) + " online";
			if (patchChip) patchChip.textContent = "Patch " + (state.status.patch || "0.8.7");
			if (landingWorldState) landingWorldState.textContent = state.status.online ? "Online" : "Offline";
			if (landingWorldDetail) landingWorldDetail.textContent = (state.status.playersOnline || 0) + " players online";
			if (dashboardWorldState) dashboardWorldState.textContent = state.status.online ? "Online" : "Offline";
			if (dashboardWorldSave) dashboardWorldSave.textContent = "Last save " + (state.status.lastSave || "recently");
		}
		if (state.rates) {
			if (landingXpRates) landingXpRates.textContent = state.rates.baseCombat + "x / " + state.rates.baseSkill + "x";
			if (landingSubRates) landingSubRates.textContent = state.rates.subscribedCombat + "x / " + state.rates.subscribedSkill + "x subscribed";
			if (dashboardXpRate) dashboardXpRate.textContent = state.rates.subscribedCombat + "x / " + state.rates.subscribedSkill + "x";
		}
		if (state.founderStats) {
			if (landingPrizeState) landingPrizeState.textContent = (state.founderStats.freeSubCardsUnlocked || 0) + " unlocked";
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
			renderDownloads(state.downloads);
		}
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
				...(sessionToken ? { "authorization": "Bearer " + sessionToken } : {})
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

	function applyAccountState(state) {
		if (state.token) {
			sessionToken = state.token;
			localStorage.setItem(sessionKey, sessionToken);
		}
		if (state.account) {
			accountName.textContent = state.account.displayName || "Voidscape";
			accountEmail.textContent = state.account.email || "";
			if (state.account.subscription) {
				subDays.textContent = state.account.subscription.label;
				sidebarSubTime.textContent = state.account.subscription.label;
				if (subTitle) subTitle.textContent = state.account.subscription.active ? "Subscribed" : "Unsubscribed";
				if (subMeterFill) subMeterFill.style.width = state.account.subscription.active ? "100%" : "0%";
			}
		}
		if (state.founder) {
			applyFounderState(state.founder);
		}
		if (Array.isArray(state.characters) && state.characters.length) {
			characters = state.characters.slice(0, maxCharacters);
			if (!characters.some(function (character) { return character.name === selectedCharacter; })) {
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
	}

	function renderRewards(rewards) {
		if (!founderRewardCard || !founderRewardCount || !useFounderReward) return;
		var count = rewards && rewards.founderFreeSubscriptions ? rewards.founderFreeSubscriptions : 0;
		founderRewardCard.classList.toggle("is-ready", count > 0);
		founderRewardCount.textContent = count > 0
			? count + " free weekly card" + (count === 1 ? "" : "s")
			: "No free cards";
		useFounderReward.disabled = !sessionToken || count < 1;
	}

	function applyFounderState(apiFounder) {
		var founder = {
			username: apiFounder.username,
			email: apiFounder.email,
			code: apiFounder.code,
			invites: apiFounder.creditedReferrals || 0,
			referredBy: apiFounder.referredBy || null
		};
		saveFounder(founder);
		renderFounder();
	}

	function clearSession() {
		sessionToken = "";
		localStorage.removeItem(sessionKey);
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

		var kit = classKits[selectedClass];
		var character = {
			name: name,
			path: kit.path,
			image: kit.image,
			combat: selectedClass === "warrior" ? 8 : 5,
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
		characterMessage.textContent = name + " added locally as " + kit.path + ". Backend creation is not connected in static-file mode.";
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

		title.textContent = next.getAttribute("data-title") || "Voidscape";
		if (workspace) workspace.scrollTop = 0;
		if (window.location.hash !== "#" + next.id) {
			window.history.replaceState(null, "", "#" + next.id);
		}
	}

	function renderFounder() {
		renderReferralNotice();
		var founder = loadFounder();
		if (!founder) {
			founderProgressLabel.textContent = "0 / 2 invites";
			founderRewardLabel.textContent = "Reward locked";
			founderProgressFill.style.width = "0%";
			founderLink.value = "";
			return;
		}
		founderName.value = founder.username || "";
		founderEmail.value = founder.email || "";
		accountName.textContent = founder.username || "Zamak42";
		accountEmail.textContent = founder.email || "void@example.com";
		var invites = Math.min(2, founder.invites || 0);
		founderProgressLabel.textContent = invites >= 2 ? "Unlocked" : invites + " / 2 invites";
		founderRewardLabel.textContent = invites >= 2 ? "Free weekly sub card" : "Reward locked";
		founderProgressFill.style.width = (invites / 2 * 100) + "%";
		founderLink.value = makeInviteLink(founder.code);
	}

	function renderReferralNotice() {
		if (!referralNotice || !referralCodeLabel) return;
		var code = currentReferralCode();
		referralNotice.hidden = !code;
		referralCodeLabel.textContent = code || "";
	}

	function renderCharacters() {
		rosterTitle.textContent = characters.length + " / " + maxCharacters + " characters";
		slotBadge.textContent = Math.max(0, maxCharacters - characters.length) + " slots open";
		queueCharacter.disabled = characters.length >= maxCharacters;
		if (slotTrack) {
			slotTrack.innerHTML = Array.from({ length: maxCharacters }, function (_, index) {
				return '<span class="' + (index < characters.length ? "is-filled" : "") + '"></span>';
			}).join("");
		}
		characterCards.innerHTML = characters.map(function (character) {
			var stateLabel = characterStateLabel(character);
			var stateClass = characterStateClass(character);
			var gear = Array.isArray(character.gear) ? character.gear.slice(0, 4) : [];
			return [
				'<button class="character-card' + (character.name === selectedCharacter ? " is-selected" : "") + (character.linkStatus === "linked" ? " is-linked" : "") + '" type="button" data-character="' + escapeAttr(character.name) + '">',
				'<span class="state-badge ' + stateClass + '">' + escapeHtml(stateLabel) + '</span>',
				'<img src="' + escapeAttr(character.image) + '" alt="">',
				'<strong>' + escapeHtml(character.name) + '</strong>',
				'<span class="character-meta">' + escapeHtml(character.path) + '</span>',
				'<span class="character-meta">Lvl ' + escapeHtml(String(character.combat)) + " - Total " + escapeHtml(String(character.total)) + '</span>',
				'<span class="character-title">' + escapeHtml(character.title) + '</span>',
				'<div class="mini-loadout">' + gear.map(function (item) { return renderGearToken(item, true); }).join("") + '</div>',
				'<small>' + escapeHtml(character.status) + " - " + escapeHtml(characterSourceLabel(character)) + '</small>',
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
	}

	function renderSelectedCharacter() {
		var character = characters.find(function (entry) {
			return entry.name === selectedCharacter;
		}) || characters[0];
		if (!character) return;
		var gear = Array.isArray(character.gear) ? character.gear : [];

		activeCharacterTitle.textContent = character.name;
		activeCharacterImage.src = character.image;
		activeCharacterImage.alt = character.name + " character render";
		if (activeCharacterSource) activeCharacterSource.textContent = characterSourceLabel(character);
		if (activeCharacterDoll) {
			var visual = characterVisualState(character);
			activeCharacterDoll.className = "paper-doll " + visual.weaponClass + (visual.hasCape ? " has-cape" : "");
			Object.keys(visual.colors).forEach(function (key) {
				activeCharacterDoll.style.setProperty("--" + key, visual.colors[key]);
			});
			activeCharacterDoll.innerHTML = paperDollMarkup();
		}
		activeCharacterCombat.textContent = character.combat;
		activeCharacterTotal.textContent = character.total;
		activeCharacterQuest.textContent = character.quest;
		activeCharacterKills.textContent = character.kills;
		activeCharacterLocation.textContent = character.status;
		activeCharacterSubscription.textContent = character.subscription || "Unsubscribed";
		activeCharacterLastLogin.textContent = character.lastLogin || "Never";
		if (dashboardActiveTitle) dashboardActiveTitle.textContent = character.title || "No title equipped";
		activeCharacterGear.innerHTML = [
			'<strong>Equipped / appearance</strong>',
			'<span>' + escapeHtml(character.appearance) + '</span>',
			'<div class="loadout-grid">' + gear.map(renderGearToken).join("") + '</div>'
		].join("");
		localStorage.setItem(selectedKey, character.name);
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
		if (character.linkStatus === "linked") return "Linked save";
		if (character.source === "openrsc-sqlite") return "Saved state";
		if (character.linkStatus === "pending") return "Pending link";
		return "Preview";
	}

	function characterStateClass(character) {
		if (character.linkStatus === "linked") return "linked";
		if (character.source === "openrsc-sqlite") return "snapshot";
		if (character.linkStatus === "pending") return "pending";
		return "preview";
	}

	function characterSourceLabel(character) {
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

	function renderSecurity(security) {
		if (!security || !securityScore) return;
		securityScore.textContent = security.score;
		securityEmailCheck.checked = Boolean(security.emailVerified);
		securityRecoveryCheck.checked = Boolean(security.recoveryCodes && security.recoveryCodes.activeCount > 0);
		securityPasswordCheck.checked = Boolean(security.passwordChangedAt || (security.auth && security.auth.googleConnected));
		securitySessionCheck.checked = Array.isArray(security.sessions) && security.sessions.length <= 2;
		generateRecovery.textContent = security.recoveryCodes && security.recoveryCodes.activeCount
			? "Rotate codes"
			: "New codes";
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

	function renderDownloads(rows) {
		if (!downloadActions) return;
		downloadActions.innerHTML = rows.map(function (row) {
			var available = Boolean(row.available && row.url && row.url !== "#");
			var tag = available ? "a" : "button";
			var attrs = available
				? ' href="' + escapeAttr(row.url) + '" download'
				: ' type="button" disabled';
			return [
				"<" + tag + ' class="ghost-button download-action' + (available ? " is-ready" : "") + '"' + attrs + ">",
				"<strong>" + escapeHtml(row.label || "") + "</strong>",
				"<span>" + escapeHtml(row.state || "") + "</span>",
				"</" + tag + ">"
			].join("");
		}).join("");
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
			return Array.isArray(roster) && roster.length ? roster.slice(0, maxCharacters) : defaultCharacters();
		} catch (error) {
			return defaultCharacters();
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
		(fallbackInput || founderLink).select();
		document.execCommand("copy");
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
