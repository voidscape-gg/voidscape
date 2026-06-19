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
			setText(integrityUpdated, "Waiting for update");
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

		setText(integrityUpdated, generatedAt ? "Updated " + relativeTime(generatedAt) : "Waiting for update");
		setText(integrityStaffTotal, formatCompactNumber(total));
		setText(integrityStaffDetail, staffStatusDetail(staff.status, total));
		setText(integrityBlockedTotal, formatCompactNumber(blocked));
		setText(integrityBlockedDetail, blocked === 1 ? "attempt stopped" : "attempts stopped");
		setText(integrityBuildStatus, simpleStatus(build.status || "available"));
		setText(integrityBuildDetail, downloadCheckDetail(build));
		setText(integrityItemsStatus, formatCompactNumber(itemReceipts));
		setText(integrityItemsDetail, itemReceipts === 1 ? "item move today" : "item moves today");

		setText(trustPasswordsStatus, "Now");
		renderTrustList(trustPasswordsList, [
			"Download first. Discord stays optional.",
			"Use a fresh game password.",
			"We do not show private account details on public pages."
		]);

		setText(trustSourceStatus, simpleStatus(build.status || "available"));
		renderTrustList(trustSourceList, sourceListItems(build, integrity, staff));
		renderSourceBuildBoard(build);

		setText(trustStaffStatus, simpleStatus(staff.status || "waiting_for_game_snapshot"));
		renderTrustList(trustStaffList, staffListItems(staff, topCategory));
		renderStaffAuditBoard(staff);

		setText(trustItemsStatus, simpleStatus(itemTrustStatus(items)));
		renderTrustList(trustItemsList, itemReceiptListItems(items));
		renderItemReceiptBoard(items);

		setText(trustScansStatus, simpleStatus(scanTrustStatus(scans, accounts)));
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
			trustSourceRepo.textContent = source.repositoryUrl ? "View public code" : simpleStatus(source.status || "source_pending");
		}
		if (trustSourceCommit) {
			var commitText = source.shortCommit
				? (source.dirty ? "Code version " : "Code version ") + source.shortCommit + (source.dirty ? " - live changes pending" : "")
				: "Code version pending";
			trustSourceCommit.textContent = commitText;
			trustSourceCommit.title = source.commit || commitText;
		}
		if (trustSourceManifest) {
			var manifestText = manifest.status === "available"
				? formatCompactNumber(manifest.fileCount || 0) + " file checks ready"
				: simpleStatus(manifest.status || "manifest_pending");
			trustSourceManifest.textContent = manifestText;
			trustSourceManifest.title = manifest.clientSha256 || manifestText;
		}
		trustBuildArtifacts.innerHTML = artifacts.map(function (artifact) {
			var available = artifact.available === true && artifact.sha256;
			var fileCheck = available ? "File checked" : "Waiting";
			var updated = artifact.updatedAt ? relativeTime(artifact.updatedAt) : "";
			return [
				'<a class="build-proof-tile' + (available ? " is-ready" : "") + '" href="' + escapeAttr(artifact.url || "#") + '" title="' + escapeAttr(artifact.sha256 || "File check pending") + '"' + (available ? " download" : ' aria-disabled="true"') + ">",
				"<b>" + escapeHtml(artifact.label || "Download") + "</b>",
				"<code>" + escapeHtml(fileCheck) + "</code>",
				"<span>" + escapeHtml(available ? ((updated ? "Updated " + updated : formatBytesLabel(artifact.sizeBytes))) : "Not ready yet") + "</span>",
				"</a>"
			].join("");
		}).join("");
	}

	function sourceListItems(build, integrity, staff) {
		var source = build.source || {};
		var manifest = build.manifest || {};
		var rows = ["The game code is public, so people can inspect what is being run."];
		if (source.shortCommit && source.dirty) {
			rows.push("The live server is based on public code version " + source.shortCommit + "; live-only changes are marked until published.");
		} else if (source.shortCommit) {
			rows.push("Current public code version: " + source.shortCommit + ".");
		} else {
			rows.push("The public code version is waiting for the next update.");
		}
		rows.push("Downloads are checked when they are served, so replacing a file changes the check.");
		if (manifest.status === "available") {
			rows.push("The launcher currently checks " + formatCompactNumber(manifest.fileCount || 0) + " game file" + (Number(manifest.fileCount || 0) === 1 ? "." : "s."));
		} else {
			rows.push("The next public update will refresh the download check list.");
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
		setText(trustStaffBoardTotal, formatCompactNumber(total) + " actions / " + formatCompactNumber(blocked) + " stopped");
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
				"<b>" + escapeHtml(staffCategoryLabel(row.category || "staff")) + "</b>",
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
				"<em>" + escapeHtml(staffCategoryLabel(row.category || "staff")) + "</em>",
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
		setText(trustItemsBoardTotal, formatCompactNumber(total) + (total === 1 ? " item move" : " item moves"));
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
				"<b>" + escapeHtml(itemPlaceLabel(row.category || "unknown")) + "</b>",
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
				"<b>" + escapeHtml(itemEventLabel(row.eventType || "item_event")) + "</b>",
				"<small>" + escapeHtml(relativeTime(row.at)) + "</small>",
				"<em>" + escapeHtml(itemPathLabel(row.source || "unknown", row.destination || "unknown")) + "</em>",
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
				staff.status === "waiting_for_game_snapshot" ? "Waiting for the next live update." : "No recent staff tool use recorded.",
				"We show counts here, not staff names, player names, or private details.",
				"Stopped staff actions will appear here too."
			];
		}
		var rows = [
			formatCompactNumber(total) + " staff action" + (total === 1 ? "" : "s") + " counted in the last 24 hours.",
			formatCompactNumber(blocked) + " staff action" + (blocked === 1 ? " was" : "s were") + " stopped by safety rules."
		];
		if (topCategory) {
			rows.push("Most common type: " + staffCategoryLabel(topCategory.category) + " (" + formatCompactNumber(topCategory.count) + ").");
		}
		rows.push("We show counts here, not staff names, player names, or private details.");
		return rows;
	}

	function itemReceiptListItems(items) {
		var status = simpleStatus(items.status || "not_recording").toLowerCase();
		var staffMints = Number(items.staffMints24h || 0);
		var npcDrops = Number(items.npcDrops24h || 0);
		var transfers = Number(items.transfers24h || 0);
		var tracked = Number(items.trackedItems || items.totalEvents || 0);
		var lines = [
			"Item history is " + status + ".",
			"Today: " + formatCompactNumber(npcDrops) + " monster drops, " + formatCompactNumber(transfers) + " player item moves, " + formatCompactNumber(staffMints) + " staff-created items.",
			formatCompactNumber(tracked) + " item move" + (tracked === 1 ? "" : "s") + " counted so far."
		];
		if (items.reason === "missing_item_provenance_table") {
			lines.push("The game is waiting for item history to be turned on.");
		} else {
			lines.push("Covered: drops, pickups, deaths, trades, shops, and auction-house sales.");
		}
		return lines;
	}

	function scanListItems(scans, accounts) {
		var status = simpleStatus(scanTrustStatus(scans, accounts)).toLowerCase();
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
			lastScanAt ? "Last check " + relativeTime(lastScanAt) + "." : "Daily checks are waiting for the first update.",
			"We checked " + formatCompactNumber(trackedItems) + " item records across " + formatCompactNumber(checkedPlayers) + " players.",
			"Staff review: " + formatCompactNumber(privileged) + " staff accounts, " + formatCompactNumber(sensitive) + " powerful staff action" + (sensitive === 1 ? "" : "s") + " today.",
			formatCompactNumber(flagged) + " problem" + (flagged === 1 ? "" : "s") + " found. " + formatCompactNumber(review) + " normal staff review item" + (review === 1 ? "" : "s") + "."
		];
		if (flagged > 0) {
			var categories = (Array.isArray(scans.categories) ? scans.categories : [])
				.concat(Array.isArray(accounts.categories) ? accounts.categories : []);
			var top = categories.length ? categories[0] : null;
			rows.push(top ? "Most common problem: " + plainFindingLabel(top.category) + " (" + formatCompactNumber(top.count) + ")." : "Staff can see the private details.");
		} else if (lastScanAt) {
			rows.push("No impossible stats, broken item ownership, bad account settings, or impossible item stacks found.");
		} else {
			rows.push("Check status: " + status + ".");
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
		if (status === "waiting_for_game_snapshot") return "Waiting for update";
		if (status === "missing_staff_log_table") return "Not connected yet";
		if (status === "openrsc_unavailable") return "Game data unavailable";
		if (!total) return "Quiet today";
		return "today";
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

	function formatBytesLabel(value) {
		var bytes = Number(value || 0);
		if (!Number.isFinite(bytes) || bytes <= 0) return "Built file";
		var mb = bytes / (1024 * 1024);
		return "Built " + (mb >= 10 ? mb.toFixed(1) : mb.toFixed(2)) + " MB";
	}

	function sourceLabel(value) {
		return String(value || "snapshot").replace(/[_-]+/g, " ");
	}

	function simpleStatus(value) {
		var key = String(value || "unknown").toLowerCase();
		var labels = {
			active: "Recent",
			allowed: "Allowed",
			available: "Ready",
			blocked: "Stopped",
			clean: "Clean",
			click: "Click",
			designing: "Planned",
			flagged: "Needs Review",
			manifest_pending: "Waiting",
			missing_staff_log_table: "Waiting",
			no_recent_staff_commands: "Quiet",
			not_recording: "Waiting",
			openrsc_unavailable: "Waiting",
			planned: "Planned",
			recording: "Tracking",
			recording_no_recent_staff_mints: "Tracking",
			recording_no_staff_mints: "Tracking",
			source_pending: "Waiting",
			waiting_for_game_snapshot: "Waiting"
		};
		return labels[key] || titleStatus(key);
	}

	function downloadCheckDetail(build) {
		var manifest = build && build.manifest || {};
		if (manifest.status === "available") {
			return formatCompactNumber(manifest.fileCount || 0) + " files checked";
		}
		return "Launcher ready";
	}

	function staffCategoryLabel(value) {
		var key = String(value || "staff").toLowerCase();
		var labels = {
			account: "Account tools",
			item: "Item tools",
			moderation: "Moderation",
			movement: "Movement tools",
			quest: "Quest tools",
			server: "Server tools",
			staff: "Staff tools",
			teleport: "Movement tools"
		};
		return labels[key] || titleStatus(key);
	}

	function itemEventLabel(value) {
		var key = String(value || "item_event").toLowerCase();
		var labels = {
			item_origin: "Item created",
			item_transfer: "Item moved",
			staff_mint: "Staff-created item"
		};
		return labels[key] || "Item update";
	}

	function itemPathLabel(source, destination) {
		return itemPlaceLabel(source) + " -> " + itemPlaceLabel(destination);
	}

	function itemPlaceLabel(value) {
		var key = String(value || "unknown").toLowerCase();
		var labels = {
			auction_buyer: "auction buyer",
			auction_collectible: "auction pickup",
			auction_listing: "auction listing",
			auction_mod_delete: "staff auction delete",
			bank: "bank",
			death_drop: "death drop",
			ground: "ground",
			ground_player_drop: "ground",
			inventory: "inventory",
			item_drop: "drop",
			npc_drop: "monster drop",
			player_bank: "bank",
			player_inventory: "inventory",
			player_trade: "trade",
			shop: "shop",
			staff_command: "staff tool",
			subscription_ledger: "subscription card",
			unknown: "unknown"
		};
		return labels[key] || sourceLabel(key);
	}

	function plainFindingLabel(value) {
		var key = String(value || "problem").toLowerCase();
		var labels = {
			broken_item_reference: "item without a valid owner",
			duplicate_item_owner: "item owned in more than one place",
			invalid_account_flag: "bad account setting",
			invalid_bank_slot: "bad bank slot",
			invalid_current_stat: "impossible current stat",
			invalid_experience: "impossible XP",
			invalid_inventory_slot: "bad inventory slot",
			invalid_item_amount: "impossible item stack",
			invalid_item_catalog: "unknown item",
			invalid_subscription_expiry: "bad subscription date",
			invalid_subscription_key: "bad subscription record",
			missing_skill_row: "missing stats",
			orphan_account_cache: "account note without a player",
			orphan_item_status: "item record without an owner",
			skill_total_mismatch: "wrong total level",
			subscription_cache_scope: "subscription record in the wrong place",
			unknown_group: "unknown account rank"
		};
		return labels[key] || titleStatus(key);
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
