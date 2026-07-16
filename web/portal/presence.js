(function () {
	"use strict";

	if (window.location.protocol === "file:") return;

	var endpoint = "/api/presence/heartbeat";
	var storageKey = "voidscape.presence.visitorId";
	var heartbeatMs = 15000;
	var hiddenHeartbeatMs = 60000;
	var localStore = safeStorage("localStorage");
	var sessionStore = safeStorage("sessionStorage");
	var visitorId = loadVisitorId();
	var timer = 0;

	if (!visitorId) return;

	function safeStorage(name) {
		try {
			return window[name];
		} catch (error) {
			return null;
		}
	}

	function loadVisitorId() {
		var existing = readStoredId(localStore) || readStoredId(sessionStore);
		if (existing) return existing;
		var next = createVisitorId();
		writeStoredId(localStore, next);
		if (!readStoredId(localStore)) writeStoredId(sessionStore, next);
		return next;
	}

	function readStoredId(storage) {
		try {
			var value = String(storage.getItem(storageKey) || "").trim();
			return /^[A-Za-z0-9_-]{16,96}$/.test(value) ? value : "";
		} catch (error) {
			return "";
		}
	}

	function writeStoredId(storage, value) {
		try {
			storage.setItem(storageKey, value);
		} catch (error) {}
	}

	function createVisitorId() {
		var bytes = new Uint8Array(18);
		if (window.crypto && window.crypto.getRandomValues) {
			window.crypto.getRandomValues(bytes);
		} else {
			for (var index = 0; index < bytes.length; index += 1) {
				bytes[index] = Math.floor(Math.random() * 256);
			}
		}
		var binary = "";
		for (var offset = 0; offset < bytes.length; offset += 1) {
			binary += String.fromCharCode(bytes[offset]);
		}
		return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
	}

	function currentPayload() {
		return {
			visitorId: visitorId,
			page: window.location.pathname + window.location.search + window.location.hash,
			title: document.title || "",
			referrer: document.referrer || "",
			visible: document.visibilityState !== "hidden"
		};
	}

	function send(useBeacon) {
		var body = JSON.stringify(currentPayload());
		if (useBeacon && navigator.sendBeacon) {
			try {
				if (navigator.sendBeacon(endpoint, new Blob([body], { type: "application/json" }))) return;
			} catch (error) {}
		}
		fetch(endpoint, {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: body,
			keepalive: true
		}).catch(function () {});
	}

	function schedule() {
		window.clearTimeout(timer);
		timer = window.setTimeout(tick, document.visibilityState === "hidden" ? hiddenHeartbeatMs : heartbeatMs);
	}

	function tick() {
		send(false);
		schedule();
	}

	window.addEventListener("pagehide", function () {
		send(true);
	});
	window.addEventListener("hashchange", function () {
		send(false);
	});
	window.addEventListener("popstate", function () {
		send(false);
	});
	document.addEventListener("visibilitychange", function () {
		send(true);
		schedule();
	});

	send(false);
	schedule();
})();
