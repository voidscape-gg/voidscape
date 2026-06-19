(function () {
	"use strict";

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
	var trustStaffBoard = document.getElementById("trust-staff-board");
	var trustStaffBoardTotal = document.getElementById("trust-staff-board-total");
	var trustStaffCategories = document.getElementById("trust-staff-categories");
	var trustStaffRecent = document.getElementById("trust-staff-recent");
	var trustItemsStatus = document.getElementById("trust-items-status");
	var trustItemsList = document.getElementById("trust-items-list");
	var trustItemsBoard = document.getElementById("trust-items-board");
	var trustItemsBoardTotal = document.getElementById("trust-items-board-total");
	var trustItemsSources = document.getElementById("trust-items-sources");
	var trustItemsRecent = document.getElementById("trust-items-recent");
	var trustScansStatus = document.getElementById("trust-scans-status");
	var trustScansList = document.getElementById("trust-scans-list");

	trustTabs.forEach(function (button) {
		button.addEventListener("click", function () {
			setTrustPanel(button.getAttribute("data-trust-tab"));
		});
	});
	if (trustTabs.length) setTrustPanel(trustTabs[0].getAttribute("data-trust-tab"));

	loadIntegrity();

	async function loadIntegrity() {
		try {
			var response = await fetch("/api/public", { headers: { accept: "application/json" } });
			if (!response.ok) throw new Error("public_api_failed");
			var state = await response.json();
			renderIntegrity(state.integrity || {});
		} catch (error) {
			setText(integrityUpdated, "Waiting for snapshot");
		}
	}

	function renderIntegrity(integrity) {
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
		renderStaffAuditBoard(staff);

		setText(trustItemsStatus, titleStatus(itemTrustStatus(items)));
		renderTrustList(trustItemsList, itemReceiptListItems(items));
		renderItemReceiptBoard(items);

		setText(trustScansStatus, titleStatus(scanTrustStatus(scans, accounts)));
		renderTrustList(trustScansList, scanListItems(scans, accounts));
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

	function renderStaffAuditBoard(staff) {
		if (!trustStaffBoard || !trustStaffCategories || !trustStaffRecent) return;
		var categories = Array.isArray(staff.categories) ? staff.categories.slice(0, 5) : [];
		var recent = Array.isArray(staff.recent) ? staff.recent.slice(0, 5) : [];
		var total = Number(staff.total24h || 0);
		var blocked = Number(staff.blocked24h || 0);
		var maxCount = categories.reduce(function (max, row) {
			return Math.max(max, Number(row.count || 0));
		}, 0);
		var hasAudit = categories.length > 0 || recent.length > 0 || total > 0;
		trustStaffBoard.hidden = !hasAudit;
		setText(trustStaffBoardTotal, formatCompactNumber(total) + " attempts / " + formatCompactNumber(blocked) + " blocked");
		if (!hasAudit) {
			trustStaffCategories.innerHTML = "";
			trustStaffRecent.innerHTML = "";
			return;
		}
		trustStaffCategories.innerHTML = categories.map(function (row) {
			var count = Number(row.count || 0);
			var width = maxCount > 0 ? Math.max(8, Math.round((count / maxCount) * 100)) : 0;
			return [
				'<div class="audit-source-row" style="--audit-width: ' + width + '%">',
				"<b>" + escapeHtml(sourceLabel(row.category || "staff")) + "</b>",
				'<div class="audit-source-meter"><i></i></div>',
				"<em>" + escapeHtml(formatCompactNumber(count)) + "</em>",
				"</div>"
			].join("");
		}).join("");
		trustStaffRecent.innerHTML = recent.map(function (row) {
			var status = row.status === "blocked" ? "blocked" : "allowed";
			return [
				'<div class="audit-event audit-event-' + escapeAttr(status) + '">',
				"<b>" + escapeHtml(titleStatus(status)) + "</b>",
				"<small>" + escapeHtml(relativeTime(row.at)) + "</small>",
				"<em>" + escapeHtml(sourceLabel(row.category || "staff") + " command") + "</em>",
				"</div>"
			].join("");
		}).join("");
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

	function itemTrustStatus(items) {
		var status = String(items.status || "designing");
		var tracked = Number(items.trackedItems || items.totalEvents || 0);
		if (tracked > 0 && status.indexOf("recording") === 0) return "recording";
		return status;
	}

	function staffStatusDetail(status, total) {
		if (status === "waiting_for_game_snapshot") return "Waiting for snapshot";
		if (status === "missing_staff_log_table") return "No staff log table";
		if (status === "openrsc_unavailable") return "Game DB unavailable";
		if (!total) return "Quiet last 24h";
		return "last 24h";
	}

	function renderTrustList(target, rows) {
		if (!target) return;
		target.innerHTML = rows.map(function (row) {
			return "<li>" + escapeHtml(row) + "</li>";
		}).join("");
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

	function setText(target, value) {
		if (target) target.textContent = value;
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
