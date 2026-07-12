(() => {
	"use strict";

	const skillNames = [
		"Attack", "Defense", "Strength", "Hits", "Ranged", "Prayer", "Magic",
		"Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting",
		"Smithing", "Mining", "Herblaw", "Agility", "Thieving", "Runecraft", "Harvesting"
	];
	const maximumTimeMs = 8640000000000000;
	const playerNamePattern = /^[a-zA-Z0-9 ]{2,12}$/;
	const seasonPattern = /^[a-z0-9][a-z0-9_-]{0,31}$/;
	const timeFormatter = new Intl.DateTimeFormat("en-US", {
		timeZone: "UTC",
		month: "short",
		day: "numeric",
		year: "numeric",
		hour: "numeric",
		minute: "2-digit",
		hour12: true
	});

	const shell = document.querySelector("#legends-shell");
	const status = document.querySelector("#legends-status");
	const statusKicker = document.querySelector("#legends-status-kicker");
	const statusTitle = document.querySelector("#legends-status-title");
	const statusCopy = document.querySelector("#legends-status-copy");
	const loader = document.querySelector("#legends-loader");
	const retry = document.querySelector("#legends-retry");
	const content = document.querySelector("#legends-content");
	const seasonLabel = document.querySelector("#legends-season-label");
	const firstCount = document.querySelector("#legends-first-count");
	const topPlayer = document.querySelector("#legends-top-player");
	const bestStreak = document.querySelector("#legends-best-streak");
	const firstList = document.querySelector("#world-first-list");
	const firstListCount = document.querySelector("#world-firsts-count");
	const firstEmpty = document.querySelector("#world-firsts-empty");
	const wildernessList = document.querySelector("#wilderness-list");
	const wildernessListCount = document.querySelector("#wilderness-leaders-count");
	const wildernessEmpty = document.querySelector("#wilderness-empty");
	let requestNumber = 0;

	function isObject(value) {
		return value !== null && typeof value === "object" && !Array.isArray(value);
	}

	function isSafeNonNegativeInteger(value) {
		return Number.isSafeInteger(value) && value >= 0;
	}

	function isPublicTime(value) {
		return Number.isSafeInteger(value) && value > 0 && value <= maximumTimeMs;
	}

	function validPlayerName(value) {
		return typeof value === "string"
			&& value === value.trim()
			&& !value.includes("  ")
			&& playerNamePattern.test(value);
	}

	function validFirst(row) {
		if (!isObject(row) || !validPlayerName(row.playerName)
			|| !isSafeNonNegativeInteger(row.subjectId)
			|| !isSafeNonNegativeInteger(row.value)
			|| !isPublicTime(row.achievedAtMs)) return false;
		if (row.type === "first_skill") {
			return row.subjectId < skillNames.length && [80, 90, 99].includes(row.value);
		}
		return row.type === "first_item" && row.subjectId === 575 && row.value === 1;
	}

	function validLeader(row, expectedRank) {
		return isObject(row)
			&& row.rank === expectedRank
			&& validPlayerName(row.playerName)
			&& isSafeNonNegativeInteger(row.currentStreak)
			&& Number.isSafeInteger(row.bestStreak) && row.bestStreak > 0
			&& Number.isSafeInteger(row.qualifiedKills) && row.qualifiedKills > 0
			&& row.currentStreak <= row.bestStreak
			&& row.bestStreak <= row.qualifiedKills
			&& isPublicTime(row.lastQualifiedAtMs);
	}

	function publicModel(payload) {
		if (!isObject(payload)
			|| typeof payload.seasonId !== "string" || !seasonPattern.test(payload.seasonId)
			|| !Array.isArray(payload.firsts) || payload.firsts.length > 200
			|| !isObject(payload.pvp)
			|| !Array.isArray(payload.pvp.leaders) || payload.pvp.leaders.length > 50) return null;
		if (!payload.firsts.every(validFirst)) return null;
		if (!payload.pvp.leaders.every((leader, index) => validLeader(leader, index + 1))) return null;
		return {
			seasonId: payload.seasonId,
			firsts: payload.firsts.map((row) => ({
				type: row.type,
				playerName: row.playerName,
				subjectId: row.subjectId,
				value: row.value,
				achievedAtMs: row.achievedAtMs
			})),
			leaders: payload.pvp.leaders.map((row) => ({
				rank: row.rank,
				playerName: row.playerName,
				currentStreak: row.currentStreak,
				bestStreak: row.bestStreak,
				qualifiedKills: row.qualifiedKills,
				lastQualifiedAtMs: row.lastQualifiedAtMs
			}))
		};
	}

	function node(tagName, className, text) {
		const element = document.createElement(tagName);
		if (className) element.className = className;
		if (text !== undefined) element.textContent = text;
		return element;
	}

	function timeNode(milliseconds) {
		const date = new Date(milliseconds);
		const element = node("time", "", `${timeFormatter.format(date)} UTC`);
		element.dateTime = date.toISOString();
		return element;
	}

	function seasonName(seasonId) {
		if (seasonId === "launch-2026") return "Launch 2026 season";
		return `${seasonId.replace(/[-_]+/g, " ")} season`;
	}

	function renderFirst(row) {
		const item = node("li", `world-first-card ${row.type === "first_skill" ? `level-${row.value}` : "first-item"}`);
		const seal = node("span", "world-first-seal", row.type === "first_skill" ? String(row.value) : "✦");
		seal.setAttribute("aria-hidden", "true");
		const copy = node("div", "world-first-copy");
		const meta = node("div", "world-first-meta");
		const milestone = row.type === "first_skill" ? `Level ${row.value}` : "Rare discovery";
		meta.append(node("span", "", milestone), timeNode(row.achievedAtMs));
		copy.append(meta, node("h3", "", row.playerName));
		const description = row.type === "first_skill"
			? `First player to reach level ${row.value} ${skillNames[row.subjectId]}.`
			: "First player to find a launch-season Christmas cracker.";
		copy.append(node("p", "", description));
		item.append(seal, copy);
		return item;
	}

	function stat(label, value) {
		const wrapper = node("span", "", label);
		wrapper.prepend(node("strong", "", String(value)));
		return wrapper;
	}

	function renderLeader(row) {
		const item = node("li", `wilderness-card${row.rank === 1 ? " rank-1" : ""}`);
		const rank = node("span", "wilderness-rank", `#${row.rank}`);
		rank.setAttribute("aria-label", `Rank ${row.rank}`);
		const body = node("div", "wilderness-body");
		const nameRow = node("div", "wilderness-name-row");
		nameRow.append(node("h3", "", row.playerName));
		const last = timeNode(row.lastQualifiedAtMs);
		last.className = "wilderness-last";
		nameRow.append(last);
		const stats = node("div", "wilderness-stats");
		stats.append(
			stat("Best", row.bestStreak),
			stat("Current", row.currentStreak),
			stat("Kills", row.qualifiedKills)
		);
		body.append(nameRow, stats);
		item.append(rank, body);
		return item;
	}

	function showLoading() {
		shell.dataset.state = "loading";
		shell.setAttribute("aria-busy", "true");
		content.hidden = true;
		status.hidden = false;
		loader.hidden = false;
		retry.hidden = true;
		statusKicker.textContent = "Opening the archive";
		statusTitle.textContent = "Reading the launch ledger…";
		statusCopy.textContent = "World firsts and qualified Wilderness records will appear here.";
	}

	function showUnavailable() {
		shell.dataset.state = "error";
		shell.setAttribute("aria-busy", "false");
		content.hidden = true;
		status.hidden = false;
		loader.hidden = true;
		retry.hidden = false;
		statusKicker.textContent = "Archive unavailable";
		statusTitle.textContent = "Legends are temporarily unavailable.";
		statusCopy.textContent = "The public ledger could not be read safely. Try again in a moment.";
	}

	function showModel(model) {
		seasonLabel.textContent = seasonName(model.seasonId);
		firstCount.textContent = String(model.firsts.length);
		topPlayer.textContent = model.leaders[0] ? model.leaders[0].playerName : "Unclaimed";
		bestStreak.textContent = model.leaders[0] ? String(model.leaders[0].bestStreak) : "—";
		firstListCount.textContent = `${model.firsts.length} ${model.firsts.length === 1 ? "record" : "records"}`;
		wildernessListCount.textContent = `${model.leaders.length} ranked`;
		firstList.replaceChildren(...model.firsts.map(renderFirst));
		wildernessList.replaceChildren(...model.leaders.map(renderLeader));
		firstEmpty.hidden = model.firsts.length !== 0;
		wildernessEmpty.hidden = model.leaders.length !== 0;
		shell.dataset.state = "ready";
		shell.setAttribute("aria-busy", "false");
		status.hidden = true;
		content.hidden = false;
	}

	async function loadLegends() {
		const thisRequest = ++requestNumber;
		showLoading();
		try {
			const response = await fetch("/api/legends", {
				method: "GET",
				headers: { accept: "application/json" },
				cache: "no-store",
				credentials: "same-origin"
			});
			if (!response.ok) throw new Error("unavailable");
			const model = publicModel(await response.json());
			if (!model) throw new Error("invalid_response");
			if (thisRequest === requestNumber) showModel(model);
		} catch (error) {
			if (thisRequest === requestNumber) showUnavailable();
		}
	}

	retry.addEventListener("click", loadLegends);
	loadLegends();
})();
