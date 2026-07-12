(function () {
	"use strict";

	if (window.location.hash === "#account" || window.location.hash === "#security") {
		window.location.replace(window.location.hash === "#security" ? "/portal?auth=recovery" : "/portal?auth=login");
		return;
	}

	var sessionKey = "voidscape.portal.sessionToken";
	var founderKey = "voidscape.portal.founder";
	var referralKey = "voidscape.portal.referralCode";
	var sessionToken = localStorage.getItem(sessionKey) || "";
	var sessionValidated = false;
	var activeReferralCode = captureReferralFromLocation();
	var publicState = null;
	var launchSchedule = null;
	var serverClockOffset = 0;
	var launchTimer = 0;
	var launchOpen = false;
	var launchStarterCardOpen = true;
	var launchSignupModeActive = false;
	var downloadRows = [];
	var availabilityTimer = 0;
	var availabilityController = null;
	var googleClientId = "";
	var googleNonce = "";
	var googleButtonRendered = false;
	var googleScriptPromise = null;
	var captchaConfig = null;
	var captchaScriptPromise = null;
	var captchaWidgetId = null;
	var pendingVerificationEmail = "";

	var els = {
		signIn: document.querySelector("[data-signin]"),
		chipText: document.getElementById("launch-chip-text"),
		heroTitle: document.getElementById("hero-title"),
		heroSub: document.getElementById("hero-sub"),
		count: document.getElementById("count"),
		countDays: document.getElementById("count-days"),
		countHours: document.getElementById("count-hours"),
		countMinutes: document.getElementById("count-minutes"),
		countSeconds: document.getElementById("count-seconds"),
		reserveBlock: document.getElementById("reserve-block"),
		reserveForm: document.getElementById("reserve-form"),
		reserveName: document.getElementById("reserve-name"),
		reserveSubmit: document.getElementById("reserve-submit"),
		reserveMore: document.getElementById("reserve-more"),
		reserveEmail: document.getElementById("reserve-email"),
		reservePassword: document.getElementById("reserve-password"),
		reserveConfirm: document.getElementById("reserve-confirm"),
		nameHint: document.getElementById("name-hint"),
		reserveProof: document.getElementById("reserve-proof"),
		reserveCount: document.getElementById("reserve-count"),
		googleSignup: document.getElementById("google-signup"),
		googleSignupButton: document.getElementById("google-signup-button"),
		googleSignupMessage: document.getElementById("google-signup-message"),
		captchaWidget: document.getElementById("captcha-widget"),
		successBlock: document.getElementById("success-block"),
		successName: document.getElementById("success-name"),
		successSub: document.querySelector(".success-sub"),
		successCodeCard: document.getElementById("success-code-card"),
		successCode: document.getElementById("success-code"),
		successCodeHelp: document.getElementById("success-code-help"),
		copyCode: document.getElementById("copy-code"),
		verificationActions: document.getElementById("verification-actions"),
		verificationHelp: document.getElementById("verification-help"),
		resendVerification: document.getElementById("resend-verification"),
		verificationStatus: document.getElementById("verification-status"),
		readyWeb: document.getElementById("ready-web"),
		readyLauncher: document.getElementById("ready-launcher"),
		readyAndroid: document.getElementById("ready-android"),
		readyAndroidCopy: document.getElementById("ready-android-copy"),
		readyAndroidFallback: document.getElementById("ready-android-fallback"),
		playBlock: document.getElementById("play-block"),
		playLauncher: document.getElementById("play-launcher"),
		playWeb: document.getElementById("play-web"),
		playAndroid: document.getElementById("play-android"),
		playAndroidFallback: document.getElementById("play-android-fallback")
	};

	setupCharacterSelect();
	setupReserveFlow();
	setupCopyCode();
	setupVerificationResend();
	setupDisabledLinks();
	setupFunnelTracking();
	if (!sessionToken) document.body.classList.remove("session-pending");
	updateSignInCta();
	hydrate();

	window.addEventListener("storage", function (event) {
		if (event.key !== sessionKey) return;
		if (!event.newValue) {
			sessionToken = "";
			sessionValidated = false;
			document.body.classList.remove("session-pending");
			updateSignInCta();
			updateLaunchStateFromSchedule(publicState && publicState.status);
			return;
		}
		if (event.newValue !== sessionToken) window.location.reload();
	});

	function setupCharacterSelect() {
		var suggestions = {
			warrior: "Warbringer",
			knight: "Voidknight",
			mage: "Runeveil"
		};
		document.querySelectorAll(".champ").forEach(function (champ) {
			champ.setAttribute("tabindex", "0");
			var key = ["warrior", "knight", "mage"].find(function (name) {
				return champ.classList.contains("champ-" + name);
			});
			function choose() {
				document.querySelectorAll(".champ").forEach(function (entry) {
					entry.classList.toggle("is-chosen", entry === champ);
				});
				if (els.reserveName && !els.reserveName.value) {
					els.reserveName.placeholder = suggestions[key] || "Choose your username";
					els.reserveName.focus();
				}
			}
			champ.addEventListener("click", choose);
			champ.addEventListener("keydown", function (event) {
				if (event.key !== "Enter" && event.key !== " ") return;
				event.preventDefault();
				choose();
			});
		});
	}

	function setupReserveFlow() {
		if (!els.reserveForm) return;
		if (els.reserveName) {
			els.reserveName.addEventListener("input", function () {
				if (!els.reserveMore.hidden && els.reserveConfirm) {
					var name = normalizeName(els.reserveName.value || "");
					els.reserveConfirm.textContent = reserveConfirmLabel(name);
				}
				checkAvailabilitySoon();
			});
		}
		els.reserveForm.addEventListener("submit", function (event) {
			event.preventDefault();
			handleReserveSubmit();
		});
	}

	function setupCopyCode() {
		if (!els.copyCode) return;
		els.copyCode.addEventListener("click", function () {
			var code = els.successCode ? els.successCode.textContent.trim() : "";
			if (!code || code === "-") return;
			copyText(code);
			els.copyCode.textContent = "Copied";
			window.setTimeout(function () {
				els.copyCode.textContent = "Copy";
			}, 1400);
		});
	}

	function setupDisabledLinks() {
		document.addEventListener("click", function (event) {
			var disabled = event.target && event.target.closest ? event.target.closest('a[aria-disabled="true"]') : null;
			if (!disabled) return;
			event.preventDefault();
		});
	}

	async function hydrate() {
		try {
			publicState = await apiRequest("/api/public");
			applyPublicState(publicState);
		} catch (error) {
			setCountdownValue("--", "--", "--", "--");
			if (els.chipText) els.chipText.textContent = "Launch status";
		}
		if (!sessionToken) {
			finishSessionValidation(false);
			return;
		}
		try {
			await apiRequest("/api/account");
			finishSessionValidation(true);
		} catch (error) {
			localStorage.removeItem(sessionKey);
			sessionToken = "";
			finishSessionValidation(false);
		}
	}

	function finishSessionValidation(valid) {
		sessionValidated = Boolean(valid && sessionToken);
		document.body.classList.remove("session-pending");
		updateSignInCta();
		updateLaunchStateFromSchedule(publicState && publicState.status);
	}

	function applyPublicState(state) {
		launchSignupModeActive = Boolean(state && state.launchSignupMode);
		googleClientId = state && state.oauth && state.oauth.google && state.oauth.google.enabled
			? String(state.oauth.google.clientId || "")
			: "";
		captchaConfig = state && state.captcha || null;
		downloadRows = Array.isArray(state && state.downloads) ? state.downloads : [];
		launchSchedule = state && (state.launch || (state.beta && state.beta.schedule)) || null;
		launchStarterCardOpen = starterCardWindowOpen(launchSchedule);
		serverClockOffset = launchSchedule && launchSchedule.now
			? Date.parse(launchSchedule.now) - Date.now()
			: 0;
		renderSignupCounter(state && state.founderStats);
		updateDownloadLinks();
		updateLaunchStateFromSchedule(state && state.status);
		if (launchTimer) window.clearInterval(launchTimer);
		launchTimer = window.setInterval(function () {
			updateLaunchStateFromSchedule(publicState && publicState.status);
		}, 1000);
		updateGoogleSignupButton();
		updateCaptchaWidget();
	}

	function updateLaunchStateFromSchedule(status) {
		var open = Boolean(status && status.online);
		var previousStarterCardOpen = launchStarterCardOpen;
		launchStarterCardOpen = starterCardWindowOpen(launchSchedule);
		if (launchSchedule && launchSchedule.openAt) {
			var openAt = Date.parse(launchSchedule.openAt);
			if (Number.isFinite(openAt)) {
				var remaining = Math.max(0, openAt - (Date.now() + serverClockOffset));
				open = open || remaining <= 0 || launchSchedule.locked === false;
				setCountdownFromMs(remaining);
			} else {
				setCountdownValue("--", "--", "--", "--");
			}
		} else {
			setCountdownValue("--", "--", "--", "--");
		}
		setLaunchOpen(open, status);
		if (previousStarterCardOpen !== launchStarterCardOpen && els.reserveConfirm && els.reserveName) {
			els.reserveConfirm.textContent = reserveConfirmLabel(normalizeName(els.reserveName.value || ""));
		}
	}

	function setLaunchOpen(open, status) {
		launchOpen = Boolean(open);
		var loggedIn = sessionValidated;
		document.body.classList.toggle("state-launch", launchOpen);
		if (els.count) els.count.hidden = launchOpen;
		if (els.reserveBlock) {
			els.reserveBlock.hidden = launchOpen || loggedIn || Boolean(els.successBlock && !els.successBlock.hidden);
		}
		if (els.playBlock) els.playBlock.hidden = !(launchOpen || loggedIn);
		if ((launchOpen || loggedIn) && els.successBlock) els.successBlock.hidden = true;
		if (els.heroTitle) {
			els.heroTitle.textContent = launchOpen
				? "The world is open."
				: loggedIn
					? "Your account is ready."
					: "Who will you be?";
		}
		if (els.heroSub) {
			els.heroSub.textContent = launchOpen
				? "Voidscape is live. Download the launcher for the best experience, or play right in your browser."
				: loggedIn
					? "Download a client or manage your account while the countdown runs."
					: launchStarterCardOpen
					? "Reserve your name now - founders start with a free week."
					: "Create your account and first character.";
		}
		if (els.chipText) {
			var online = Math.max(0, Number(status && status.playersOnline || 0));
			els.chipText.textContent = launchOpen
				? "Live now \u00b7 " + online.toLocaleString() + " online"
				: launchDateLabel(launchSchedule && launchSchedule.openAt);
		}
		updateDownloadLinks();
	}

	function setCountdownFromMs(ms) {
		var totalSeconds = Math.floor(Math.max(0, ms) / 1000);
		var days = Math.floor(totalSeconds / 86400);
		var hours = Math.floor((totalSeconds % 86400) / 3600);
		var minutes = Math.floor((totalSeconds % 3600) / 60);
		var seconds = totalSeconds % 60;
		setCountdownValue(days, pad2(hours), pad2(minutes), pad2(seconds));
	}

	function setCountdownValue(days, hours, minutes, seconds) {
		if (els.countDays) els.countDays.textContent = String(days);
		if (els.countHours) els.countHours.textContent = String(hours);
		if (els.countMinutes) els.countMinutes.textContent = String(minutes);
		if (els.countSeconds) els.countSeconds.textContent = String(seconds);
	}

	function renderSignupCounter(stats) {
		var count = Number(stats && (stats.betaSignupCounter || stats.reservations || 0));
		if (!els.reserveProof || !els.reserveCount) return;
		if (!Number.isFinite(count) || count <= 0) {
			els.reserveProof.hidden = true;
			return;
		}
		els.reserveCount.textContent = Math.floor(count).toLocaleString();
		els.reserveProof.hidden = false;
	}

	function updateDownloadLinks() {
		var launcher = findDownload("launcher");
		var web = findDownload("web-client");
		var androidPlay = findDownload("android-play");
		var androidApk = findDownload("android-apk");
		var androidPrimary = androidPlay || androidApk;
		setDownloadLink(els.readyLauncher, launcher, { hiddenWhenUnavailable: true, download: true });
		setDownloadLink(els.readyAndroid, androidPrimary, { hiddenWhenUnavailable: true, download: true });
		setDownloadLink(els.readyAndroidFallback, androidPlay ? androidApk : null, { hiddenWhenUnavailable: true, download: true });
		setDownloadLink(els.readyWeb, web, { hiddenWhenUnavailable: true });
		if (els.readyWeb && !launchOpen) els.readyWeb.hidden = true;
		setDownloadLink(els.playLauncher, launcher, { download: true });
		setDownloadLink(els.playWeb, web, {});
		setDownloadLink(els.playAndroid, androidPrimary, { download: true });
		setDownloadLink(els.playAndroidFallback, androidPlay ? androidApk : null, { hiddenWhenUnavailable: true, download: true });
		if (els.readyAndroid) els.readyAndroid.textContent = androidPlay ? "Get it on Google Play" : "Get APK";
		if (els.playAndroid) els.playAndroid.textContent = androidPlay ? "Google Play" : "Android APK";
		if (els.readyAndroidCopy) {
			els.readyAndroidCopy.textContent = androidPlay
				? "Install from Google Play. The signed APK is available as a direct fallback."
				: "Sideload the APK ahead of time and skip the launch-day fiddling.";
		}
	}

	function setDownloadLink(anchor, row, options) {
		if (!anchor) return;
		options = options || {};
		var available = Boolean(row && row.available && row.url && row.url !== "#");
		if (options.hiddenWhenUnavailable) anchor.hidden = !available;
		if (!available) {
			anchor.href = "#";
			anchor.setAttribute("aria-disabled", "true");
			anchor.removeAttribute("download");
			return;
		}
		anchor.hidden = false;
		anchor.href = row.url;
		anchor.removeAttribute("aria-disabled");
		if (row.external) anchor.setAttribute("rel", "noopener");
		else anchor.removeAttribute("rel");
		if (options.download && !row.external) {
			anchor.setAttribute("download", "");
		} else {
			anchor.removeAttribute("download");
		}
	}

	function findDownload(slug) {
		return downloadRows.find(function (row) {
			if (!row) return false;
			var url = String(row.url || "").toLowerCase();
			var label = String(row.label || "").toLowerCase();
			if (slug === "web-client") return row.slug === slug || url.indexOf("/play") !== -1 || url.indexOf("voidscape.gg/play") !== -1;
			if (slug === "launcher") return row.slug === slug || url.indexOf("/downloads/launcher") !== -1 || label.indexOf("launcher") !== -1;
			return row.slug === slug;
		}) || null;
	}

	function checkAvailabilitySoon() {
		if (!els.reserveName || !els.nameHint) return;
		window.clearTimeout(availabilityTimer);
		if (availabilityController) availabilityController.abort();
		els.nameHint.textContent = "";
		els.nameHint.classList.remove("is-taken");
		var name = normalizeName(els.reserveName.value || "");
		if (name.length < 3) return;
		availabilityTimer = window.setTimeout(function () {
			checkAvailability(name);
		}, 450);
	}

	async function checkAvailability(name) {
		if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) return;
		availabilityController = new AbortController();
		try {
			var result = await apiRequest("/api/openrsc/characters/" + encodeURIComponent(name) + "?availability=1", {
				signal: availabilityController.signal
			});
			if (normalizeName(els.reserveName.value || "") !== name) return;
			if (result.available === false) {
				els.nameHint.textContent = "That name is taken - try another";
				els.nameHint.classList.add("is-taken");
			} else if (result.available === true) {
				els.nameHint.textContent = name + " is available";
				els.nameHint.classList.remove("is-taken");
			} else {
				els.nameHint.textContent = "";
				els.nameHint.classList.remove("is-taken");
			}
		} catch (error) {
			if (error.name === "AbortError" || normalizeName(els.reserveName.value || "") !== name) return;
			els.nameHint.textContent = "";
			els.nameHint.classList.remove("is-taken");
		}
	}

	async function handleReserveSubmit() {
		var name = normalizeName(els.reserveName && els.reserveName.value || "");
		if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
			setHint("Choose a 2-12 character username to reserve.", true);
			if (els.reserveName) els.reserveName.focus();
			return;
		}
		if (els.reserveMore && els.reserveMore.hidden) {
			els.reserveMore.hidden = false;
			if (els.reserveSubmit) els.reserveSubmit.hidden = true;
			if (els.reserveConfirm) els.reserveConfirm.textContent = reserveConfirmLabel(name);
			updateGoogleSignupButton();
			updateCaptchaWidget();
			if (els.reserveEmail) els.reserveEmail.focus();
			return;
		}
		var email = els.reserveEmail ? els.reserveEmail.value.trim() : "";
		var password = els.reservePassword ? els.reservePassword.value : "";
		if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
			setHint("Enter a valid email address. Your code is tied to it.", true);
			if (els.reserveEmail) els.reserveEmail.focus();
			return;
		}
		if (launchSignupModeActive && !isGamePassword(password, 8)) {
			setHint("Password must be 8-20 letters and numbers for your web account and first game login.", true);
			if (els.reservePassword) els.reservePassword.focus();
			return;
		}
		var captchaToken = "";
		if (captchaSignupRequired()) {
			captchaToken = currentCaptchaToken();
			if (!captchaToken) {
				setHint("Complete the security check to finish signup.", true);
				updateCaptchaWidget();
				return;
			}
		}
		setReserveBusy(true);
		setHint(launchSignupModeActive ? "Creating your account and first character..." : "Reserving " + name + "...", false);
		trackFunnelEvent("reserve_submit", els.reserveForm);
		try {
			if (launchSignupModeActive) {
				var state = await apiRequest("/api/accounts/register", {
					method: "POST",
					body: {
						username: name,
						email: email,
						password: password,
						captchaToken: captchaToken || undefined,
						referrerCode: currentReferralCode() || undefined
					}
				});
				if (state.verificationRequired) {
					if (els.reservePassword) els.reservePassword.value = "";
					showEmailVerificationSent(state, name);
					return;
				}
					if (state.token) {
						sessionToken = state.token;
						sessionValidated = true;
						localStorage.setItem(sessionKey, sessionToken);
				}
				if (state.founder) saveFounderState(state.founder);
				if (els.reservePassword) els.reservePassword.value = "";
				showAccountSuccess(state, name);
				updateSignInCta();
				return;
			}
			var result = await apiRequest("/api/founder/reservations", {
				method: "POST",
				body: {
					username: name,
					email: email,
					captchaToken: captchaToken || undefined,
					referrerCode: currentReferralCode() || undefined
				}
			});
			saveFounderState(result.founder);
			showReservationSuccess(result);
			hydrate();
		} catch (error) {
			if (isCaptchaError(error)) resetCaptchaWidget();
			handleReserveError(error);
		} finally {
			setReserveBusy(false);
		}
	}

	function showReservationSuccess(result) {
		var founder = result && result.founder || {};
		var signup = result && result.signup || {};
		if (els.reserveBlock) els.reserveBlock.hidden = true;
		if (els.successBlock) els.successBlock.hidden = false;
		if (els.successName) els.successName.textContent = founder.username || normalizeName(els.reserveName && els.reserveName.value || "") || "-";
		if (els.successSub) {
			els.successSub.textContent = signup.code
				? "Your code is saved. Your free week is reserved for the Lumbridge vendor on launch day."
				: "Your first character uses this name. Your free week is reserved for the Lumbridge vendor on launch day.";
		}
		if (els.successCodeCard) els.successCodeCard.hidden = !signup.code;
		if (signup.code && els.successCode) els.successCode.textContent = signup.code;
		if (signup.redeemHint && els.successCodeHelp) els.successCodeHelp.textContent = signup.redeemHint;
		setVerificationActionsVisible(false);
		updateDownloadLinks();
		els.successBlock.scrollIntoView({ behavior: "smooth", block: "center" });
	}

	function showEmailVerificationSent(state, name) {
		if (els.reserveBlock) els.reserveBlock.hidden = true;
		if (els.successBlock) els.successBlock.hidden = false;
		if (els.successName) els.successName.textContent = state.username || name || normalizeName(els.reserveName && els.reserveName.value || "") || "-";
		if (els.successSub) {
			els.successSub.textContent = "Check your email to verify. Your account, first character, and starter card are created only after verification.";
		}
		if (els.successCodeCard) els.successCodeCard.hidden = true;
		pendingVerificationEmail = String(state.email || (els.reserveEmail && els.reserveEmail.value) || "").trim();
		setVerificationActionsVisible(true, state.expiresAt);
		updateDownloadLinks();
		els.successBlock.scrollIntoView({ behavior: "smooth", block: "center" });
	}

	function handleReserveError(error) {
		if (error.code === "referrer_not_found") {
			setHint("Invite code was not found. Clear it or use a newer invite link.", true);
		} else if (error.code === "self_referral_not_allowed") {
			setHint("That invite belongs to this reservation. Use another founder's link.", true);
		} else if (error.status === 400 && error.code === "invalid_username") {
			setHint("Choose a 2-12 character username to reserve.", true);
		} else if (error.status === 400 && error.code === "invalid_email") {
			setHint("Enter a valid email address. Your code is tied to it.", true);
		} else if (error.status === 400 && (error.code === "invalid_password" || error.code === "invalid_game_password")) {
			setHint("Password must be 8-20 letters and numbers for your web account and first game login.", true);
		} else if (error.status === 409 && (error.code === "username_taken" || error.code === "username_reserved")) {
			setHint("That name is taken - try another.", true);
		} else if (error.status === 409 && error.code === "character_name_taken") {
			setHint("That name is taken - try another.", true);
		} else if (error.status === 409 && error.code === "account_exists") {
			setHint("That email already has an account. Sign in instead.", true);
		} else if (error.status === 409 && error.code === "already_signed_in") {
			setHint("You're already signed in. Use Manage Account to continue.", true);
			updateSignInCta();
			updateLaunchStateFromSchedule(publicState && publicState.status);
		} else if (error.status === 403 && (error.code === "captcha_required" || error.code === "captcha_failed")) {
			setHint("Complete the security check to finish signup.", true);
		} else if (error.status === 503 && (error.code === "captcha_unavailable" || error.code === "captcha_not_configured")) {
			setHint("Signup security check is unavailable right now. Try again shortly.", true);
		} else if (error.status === 503 && error.code === "email_verification_not_configured") {
			setHint("Email verification is unavailable right now. Try again shortly.", true);
		} else if (error.status === 429) {
			setHint("Too many recent attempts from this network. Wait a little and try again.", true);
		} else if (error.status === 503 && error.code === "openrsc_db_not_configured") {
			setHint("Launch signup needs the game database before creating characters.", true);
		} else {
			setHint("Signup is unavailable right now. Please try again soon.", true);
		}
	}

	function setReserveBusy(busy) {
		if (els.reserveSubmit) els.reserveSubmit.disabled = busy;
		if (els.reserveConfirm) els.reserveConfirm.disabled = busy;
	}

	function setHint(message, taken) {
		if (!els.nameHint) return;
		els.nameHint.textContent = message || "";
		els.nameHint.classList.toggle("is-taken", Boolean(taken));
	}

	function updateGoogleSignupButton() {
		if (!els.googleSignup || !els.googleSignupButton) return;
		var enabled = Boolean(googleClientId && els.reserveMore && !els.reserveMore.hidden && !(els.successBlock && !els.successBlock.hidden));
		els.googleSignup.hidden = !enabled;
		if (!enabled || googleButtonRendered) return;
		renderGoogleSignupButton();
	}

	async function renderGoogleSignupButton() {
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
			els.googleSignupButton.innerHTML = "";
			window.google.accounts.id.renderButton(els.googleSignupButton, {
				theme: "outline",
				size: "large",
				text: "continue_with",
				shape: "rectangular",
				width: Math.min(360, els.googleSignupButton.clientWidth || 360)
			});
			googleButtonRendered = true;
			if (els.googleSignupMessage) {
				els.googleSignupMessage.textContent = "Google creates the web login; the password above becomes the first character login.";
			}
		} catch (error) {
			googleButtonRendered = false;
			if (els.googleSignupMessage) {
				els.googleSignupMessage.textContent = "Google sign-in is not available right now. Use email to reserve.";
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

	function captchaSignupRequired() {
		return Boolean(captchaConfig && captchaConfig.signupRequired);
	}

	function updateCaptchaWidget() {
		if (!els.captchaWidget) return;
		var enabled = Boolean(captchaSignupRequired() && els.reserveMore && !els.reserveMore.hidden && !(els.successBlock && !els.successBlock.hidden));
		els.captchaWidget.hidden = !enabled;
		if (!enabled) return;
		if (!captchaConfig || !captchaConfig.configured || !captchaConfig.siteKey || captchaConfig.provider !== "turnstile") {
			setHint("Signup security check is not configured yet. Try again shortly.", true);
			return;
		}
		renderCaptchaWidget();
	}

	function renderCaptchaWidget() {
		if (!els.captchaWidget || captchaWidgetId !== null) return;
		ensureCaptchaScript().then(function () {
			if (!window.turnstile || !els.captchaWidget || captchaWidgetId !== null) return;
			captchaWidgetId = window.turnstile.render(els.captchaWidget, {
				sitekey: captchaConfig.siteKey,
				theme: "dark"
			});
		}).catch(function () {
			setHint("Signup security check is not available yet. Try again shortly.", true);
		});
	}

	function ensureCaptchaScript() {
		if (window.turnstile) return Promise.resolve();
		if (captchaScriptPromise) return captchaScriptPromise;
		captchaScriptPromise = new Promise(function (resolve, reject) {
			var existing = document.querySelector('script[src^="https://challenges.cloudflare.com/turnstile/v0/api.js"]');
			if (existing) {
				existing.addEventListener("load", resolve, { once: true });
				existing.addEventListener("error", reject, { once: true });
				return;
			}
			var script = document.createElement("script");
			script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
			script.async = true;
			script.defer = true;
			script.addEventListener("load", resolve, { once: true });
			script.addEventListener("error", reject, { once: true });
			document.head.appendChild(script);
		});
		return captchaScriptPromise;
	}

	function currentCaptchaToken() {
		if (!captchaSignupRequired()) return "";
		if (captchaConfig && captchaConfig.provider === "turnstile" && window.turnstile && captchaWidgetId !== null) {
			return window.turnstile.getResponse(captchaWidgetId) || "";
		}
		return "";
	}

	function resetCaptchaWidget() {
		if (captchaConfig && captchaConfig.provider === "turnstile" && window.turnstile && captchaWidgetId !== null) {
			window.turnstile.reset(captchaWidgetId);
		}
	}

	function isCaptchaError(error) {
		return error && (
			error.code === "captcha_required" ||
			error.code === "captcha_failed" ||
			error.code === "captcha_unavailable" ||
			error.code === "captcha_not_configured"
		);
	}

	async function handleGoogleCredential(response) {
		var credential = response && response.credential ? response.credential : "";
		var name = normalizeName(els.reserveName && els.reserveName.value || "");
		var gamePassword = els.reservePassword ? els.reservePassword.value : "";
		if (!/^[a-zA-Z0-9 ]{2,12}$/.test(name)) {
			setHint("Choose a 2-12 character username to reserve.", true);
			if (els.reserveName) els.reserveName.focus();
			return;
		}
		if (!isGamePassword(gamePassword, 8)) {
			setHint("Password must be 8-20 letters and numbers for your first game login.", true);
			if (els.reservePassword) els.reservePassword.focus();
			return;
		}
		if (!credential || !googleNonce) {
			setHint("Google sign-in did not return a usable login. Try again or use email.", true);
			googleButtonRendered = false;
			renderGoogleSignupButton();
			return;
		}
		var captchaToken = "";
		if (captchaSignupRequired()) {
			captchaToken = currentCaptchaToken();
			if (!captchaToken) {
				setHint("Complete the security check before using Google signup.", true);
				updateCaptchaWidget();
				return;
			}
		}
		setHint("Creating your account and first character with Google...", false);
		trackFunnelEvent("reserve_submit", els.reserveForm);
		try {
			var state = await apiRequest("/api/accounts/google", {
				method: "POST",
				body: {
					credential: credential,
					nonce: googleNonce,
					username: name,
					gamePassword: gamePassword,
					captchaToken: captchaToken || undefined,
					referrerCode: currentReferralCode() || undefined
				}
			});
			if (state.token) {
				sessionToken = state.token;
				sessionValidated = true;
				localStorage.setItem(sessionKey, sessionToken);
			}
			showAccountSuccess(state, name);
			updateSignInCta();
		} catch (error) {
			if (isCaptchaError(error)) resetCaptchaWidget();
			if (error.status === 400 && error.code === "google_nonce_invalid") {
				setHint("Google sign-in timed out. Try the Google button again.", true);
				googleButtonRendered = false;
				renderGoogleSignupButton();
			} else if (error.status === 409 && (error.code === "username_taken" || error.code === "username_reserved" || error.code === "character_name_taken")) {
				setHint("That name is taken - try another.", true);
			} else if (error.status === 503 && error.code === "openrsc_db_not_configured") {
				setHint("Launch signup needs the game database before creating characters.", true);
			} else if (error.status === 403 && (error.code === "captcha_required" || error.code === "captcha_failed")) {
				setHint("Complete the security check before using Google signup.", true);
			} else if (error.status === 503 && (error.code === "captcha_unavailable" || error.code === "captcha_not_configured")) {
				setHint("Signup security check is unavailable right now. Try again shortly.", true);
			} else {
				setHint("Google signup is unavailable right now. Use email to reserve.", true);
			}
		}
	}

	function showAccountSuccess(state, fallbackName) {
		var founder = state && state.founder || {};
		var firstCharacter = state && Array.isArray(state.characters) && state.characters[0];
		if (els.reserveBlock) els.reserveBlock.hidden = true;
		if (els.successBlock) els.successBlock.hidden = false;
		if (els.successName) els.successName.textContent = founder.username || (firstCharacter && firstCharacter.name) || fallbackName || "-";
		if (els.successSub) {
			els.successSub.textContent = starterCardWaiting(state)
				? "Your account and first character are ready. Your starter card is waiting for launch."
				: "Your account and first character are ready.";
		}
		if (els.successCodeCard) els.successCodeCard.hidden = true;
		setVerificationActionsVisible(false);
		updateDownloadLinks();
	}

	function setupVerificationResend() {
		if (!els.resendVerification) return;
		els.resendVerification.addEventListener("click", async function () {
			if (!pendingVerificationEmail) return;
			els.resendVerification.disabled = true;
			if (els.verificationStatus) els.verificationStatus.textContent = "Requesting another email...";
			try {
				await apiRequest("/api/accounts/verify-email/resend", {
					method: "POST",
					body: { email: pendingVerificationEmail }
				});
				if (els.verificationStatus) {
					els.verificationStatus.textContent = "If the signup is still pending, another verification email is on its way.";
				}
			} catch (error) {
				if (els.verificationStatus) {
					els.verificationStatus.textContent = error.status === 429
						? "Too many recent email requests. Wait a little and try again."
						: "Verification email could not be requested right now. Try again shortly.";
				}
			} finally {
				els.resendVerification.disabled = false;
			}
		});
	}

	function setVerificationActionsVisible(visible, expiresAt) {
		if (!els.verificationActions) return;
		els.verificationActions.hidden = !visible;
		if (!visible) {
			pendingVerificationEmail = "";
			if (els.verificationStatus) els.verificationStatus.textContent = "";
			return;
		}
		if (els.verificationStatus) els.verificationStatus.textContent = "";
		if (!els.verificationHelp) return;
		var expiryMs = Date.parse(expiresAt || "");
		var hours = Number.isFinite(expiryMs) ? Math.max(1, Math.ceil((expiryMs - Date.now()) / 3600000)) : 48;
		els.verificationHelp.textContent = "Check spam or promotions if it does not arrive. The link expires in about " + hours + " hours.";
	}

	function reserveConfirmLabel(name) {
		var display = name || "";
		if (!launchStarterCardOpen) return display ? "Create " + display + " account" : "Create account";
		return display ? "Reserve " + display + " + claim free week" : "Reserve + claim free week";
	}

	function starterCardWaiting(state) {
		var rewards = state && state.rewards;
		return Boolean(rewards && (rewards.starterSubscriptionCards > 0 || rewards.starterCardStatus === "waiting"));
	}

	function starterCardWindowOpen(schedule) {
		var card = schedule && schedule.starterCard;
		if (!card) return true;
		if (card.endsAt) {
			var endsAt = Date.parse(card.endsAt);
			if (Number.isFinite(endsAt)) return endsAt > Date.now();
		}
		return card.open !== false;
	}

	function updateSignInCta() {
		if (!els.signIn) return;
		els.signIn.textContent = sessionValidated ? "Manage Account" : "Sign in";
		els.signIn.href = sessionValidated ? "/portal#dashboard" : "/portal?auth=login";
	}

	async function apiRequest(path, options) {
		options = options || {};
		if (window.location.protocol === "file:") throw apiError(0, "api_unavailable");
		var headers = Object.assign({ "content-type": "application/json" }, options.headers || {});
		if (sessionToken) headers.authorization = "Bearer " + sessionToken;
		var response = await fetch(path, {
			method: options.method || "GET",
			headers: headers,
			body: options.body ? JSON.stringify(options.body) : undefined,
			signal: options.signal
		});
		var payload = {};
		try {
			payload = await response.json();
		} catch (error) {
			throw apiError(response.status, "api_unavailable");
		}
		if (!response.ok) throw apiError(response.status, payload.error || "api_error");
		return payload;
	}

	function apiError(status, code) {
		var error = new Error(code);
		error.status = status;
		error.code = code;
		return error;
	}

	function setupFunnelTracking() {
		document.addEventListener("click", function (event) {
			var target = event.target && event.target.closest ? event.target.closest("[data-funnel-event]") : null;
			if (!target || target.getAttribute("aria-disabled") === "true") return;
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
		return String(target.textContent || "").replace(/\s+/g, " ").trim().slice(0, 80);
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

	function saveFounder(founder) {
		localStorage.setItem(founderKey, JSON.stringify(founder));
	}

	function saveFounderState(founder) {
		if (!founder) return;
		saveFounder({
			username: founder.username,
			email: founder.email,
			code: founder.code,
			invites: founder.creditedReferrals || 0,
			rewardCodes: founder.referralRewardCodes || []
		});
	}

	function normalizeReferralCode(value) {
		return String(value || "").trim().toUpperCase().replace(/[^A-Z0-9-]/g, "").slice(0, 18);
	}

	function normalizeName(value) {
		return String(value || "").trim().replace(/\s+/g, " ");
	}

	function isGamePassword(value, minLength) {
		var text = String(value || "");
		var min = minLength || 4;
		return text.length >= min && text.length <= 20 && /^[a-zA-Z0-9]+$/.test(text);
	}

	function copyText(text) {
		if (navigator.clipboard && window.isSecureContext) {
			navigator.clipboard.writeText(text);
			return;
		}
		var scratch = document.createElement("textarea");
		scratch.value = text;
		document.body.appendChild(scratch);
		scratch.select();
		document.execCommand("copy");
		scratch.remove();
	}

	function launchDateLabel(value) {
		var date = new Date(value || "");
		if (Number.isNaN(date.getTime())) return "Launch date";
		return "Launches " + date.toLocaleDateString("en-US", {
			month: "long",
			day: "numeric",
			timeZone: "America/Los_Angeles"
		});
	}

	function pad2(value) {
		return String(value).padStart(2, "0");
	}
}());
