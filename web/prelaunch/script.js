(function () {
	"use strict";

	var accountKey = "voidscape.prelaunch.account";
	var ledgerKey = "voidscape.prelaunch.referrals";
	var params = new URLSearchParams(window.location.search);
	var pendingRef = normalizeCode(params.get("ref") || "");

	var form = document.getElementById("reserve-form");
	var launchPanel = document.querySelector(".launch-panel");
	var openReserve = document.getElementById("open-reserve");
	var closeReserve = document.getElementById("close-reserve");
	var reserveDetails = document.getElementById("reserve-details");
	var username = document.getElementById("username");
	var email = document.getElementById("email");
	var message = document.getElementById("form-message");
	var founderState = document.getElementById("founder-state");
	var reservedName = document.getElementById("reserved-name");
	var unlockBadge = document.getElementById("unlock-badge");
	var progressFill = document.getElementById("progress-fill");
	var subscriptionState = document.getElementById("subscription-state");
	var inviteLink = document.getElementById("invite-link");
	var copyLink = document.getElementById("copy-link");

	render();

	openReserve.addEventListener("click", function () {
		render({ open: true });
		showReserveDetails();
		if (loadAccount()) {
			copyLink.focus();
		} else {
			username.focus();
		}
	});

	closeReserve.addEventListener("click", function () {
		hideReserveDetails();
		openReserve.focus();
	});

	form.addEventListener("submit", function (event) {
		event.preventDefault();
		var cleanName = username.value.trim().replace(/\s+/g, " ");
		var cleanEmail = email.value.trim();

		if (!/^[a-zA-Z0-9 ]{2,12}$/.test(cleanName)) {
			setMessage("Choose a 2-12 character username.");
			username.focus();
			return;
		}
		if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
			setMessage("Enter a valid email.");
			email.focus();
			return;
		}

		var existing = loadAccount();
		var code = existing && existing.code ? existing.code : makeCode(cleanName);
		var account = {
			username: cleanName,
			email: cleanEmail,
			code: code,
			referrer: pendingRef && pendingRef !== code ? pendingRef : existing && existing.referrer || "",
			creditedRef: existing && existing.creditedRef || ""
		};

		if (account.referrer && account.creditedRef !== account.referrer) {
			creditReferral(account.referrer, account.code);
			account.creditedRef = account.referrer;
		}

		saveAccount(account);
		setMessage("Name request saved. Share your founder link.");
		render({ open: true });
	});

	copyLink.addEventListener("click", function () {
		if (!inviteLink.value) return;
		copyText(inviteLink.value);
		copyLink.textContent = "Copied";
		window.setTimeout(function () {
			copyLink.textContent = "Copy";
		}, 1400);
	});

	function render(options) {
		options = options || {};
		var account = loadAccount();
		if (!account) {
			openReserve.querySelector("span").textContent = "Begin Reservation";
			form.hidden = false;
			founderState.hidden = true;
			if (pendingRef) {
				showReserveDetails();
				setMessage("Referral gate linked: " + pendingRef);
			}
			return;
		}

		openReserve.querySelector("span").textContent = "View Founder Pass";
		form.hidden = true;
		username.value = account.username || "";
		email.value = account.email || "";
		reservedName.textContent = account.username || "-";
		inviteLink.value = makeInviteLink(account.code);

		var count = inviteCount(account.code);
		var capped = Math.min(2, count);
		unlockBadge.textContent = count >= 2 ? "Unlocked" : capped + " / 2 invites";
		unlockBadge.classList.toggle("complete", count >= 2);
		progressFill.style.width = (capped / 2 * 100) + "%";
		subscriptionState.textContent = count >= 2
			? "Free launch subscription unlocked."
			: "Free subscription unlocks at 2 confirmed invites.";
		subscriptionState.classList.toggle("complete", count >= 2);
		founderState.hidden = false;

		if (options.open) {
			showReserveDetails();
		}
	}

	function showReserveDetails() {
		launchPanel.classList.add("is-open");
		reserveDetails.hidden = false;
		openReserve.hidden = true;
	}

	function hideReserveDetails() {
		launchPanel.classList.remove("is-open");
		reserveDetails.hidden = true;
		openReserve.hidden = false;
	}

	function setMessage(text) {
		message.textContent = text;
	}

	function makeInviteLink(code) {
		var url = new URL(window.location.href);
		url.hash = "";
		url.search = "";
		url.searchParams.set("ref", code);
		return url.toString();
	}

	function makeCode(name) {
		var stem = name.toUpperCase().replace(/[^A-Z0-9]+/g, "").slice(0, 7) || "VOID";
		var suffix = Math.random().toString(36).slice(2, 6).toUpperCase();
		return stem + "-" + suffix;
	}

	function normalizeCode(code) {
		return code.toUpperCase().replace(/[^A-Z0-9-]/g, "").slice(0, 18);
	}

	function loadAccount() {
		try {
			return JSON.parse(localStorage.getItem(accountKey));
		} catch (error) {
			return null;
		}
	}

	function saveAccount(account) {
		localStorage.setItem(accountKey, JSON.stringify(account));
	}

	function loadLedger() {
		try {
			return JSON.parse(localStorage.getItem(ledgerKey)) || {};
		} catch (error) {
			return {};
		}
	}

	function saveLedger(ledger) {
		localStorage.setItem(ledgerKey, JSON.stringify(ledger));
	}

	function creditReferral(referrerCode, newCode) {
		var ledger = loadLedger();
		if (!ledger[referrerCode]) {
			ledger[referrerCode] = [];
		}
		if (ledger[referrerCode].indexOf(newCode) === -1) {
			ledger[referrerCode].push(newCode);
		}
		saveLedger(ledger);
	}

	function inviteCount(code) {
		var ledger = loadLedger();
		return ledger[code] ? ledger[code].length : 0;
	}

	function copyText(text) {
		if (navigator.clipboard && window.isSecureContext) {
			navigator.clipboard.writeText(text);
			return;
		}
		inviteLink.select();
		document.execCommand("copy");
	}
}());
