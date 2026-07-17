/* Voidscape: The Chronicle (/world).
   Live data comes from /api/world. Before the configured launch instant, that API
   deliberately returns a clearly labeled fictional Chronicle. */

(function () {
	"use strict";

	var API_URL = "/api/world";
	var REFRESH_MS = 60000;
	var requestInFlight = null;
	var lastGoodWorld = null;
	var activeBoard = "overall";
	var currentFacts = [];
	var factIndex = 0;
	var launchTransitionTimer = null;
	var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
	var previewRequested = new URLSearchParams(window.location.search).get("preview") === "1";
	var localPreviewAllowed = /^(localhost|127\.0\.0\.1|\[::1\])$/.test(window.location.hostname);

	var SKILLS = [
		{ key: "attack", label: "Attack", icon: "assets/world/skills/skill-attack.png" },
		{ key: "defense", label: "Defense", icon: "assets/world/skills/skill-defence.png" },
		{ key: "strength", label: "Strength", icon: "assets/world/skills/skill-strength.png" },
		{ key: "hits", label: "Hits", icon: "assets/world/skills/skill-hits.png" },
		{ key: "ranged", label: "Ranged", icon: "assets/world/skills/skill-ranged.png" },
		{ key: "prayer", label: "Prayer", icon: "assets/world/skills/skill-prayer.png" },
		{ key: "magic", label: "Magic", icon: "assets/world/skills/skill-magic.png" },
		{ key: "cooking", label: "Cooking", icon: "assets/npc-database/item/132.png" },
		{ key: "woodcut", label: "Woodcutting", icon: "assets/world/skills/skill-woodcut.png" },
		{ key: "fletching", label: "Fletching", icon: "assets/world/skills/skill-fletching.png" },
		{ key: "fishing", label: "Fishing", icon: "assets/world/skills/skill-fishing.png" },
		{ key: "firemaking", label: "Firemaking", icon: "assets/world/skills/skill-firemaking.png" },
		{ key: "crafting", label: "Crafting", icon: "assets/world/skills/skill-crafting.png" },
		{ key: "smithing", label: "Smithing", icon: "assets/world/skills/skill-smithing.png" },
		{ key: "mining", label: "Mining", icon: "assets/world/skills/skill-mining.png" },
		{ key: "herblaw", label: "Herblaw", icon: "assets/world/skills/skill-herblaw.png" },
		{ key: "agility", label: "Agility", icon: "assets/world/skills/skill-agility.png" },
		{ key: "thieving", label: "Thieving", icon: "assets/world/skills/skill-thieving.png" }
	];

	var RECORD_ART = {
		first_max: "medal-gold.png",
		first_total: "crown-gold.png",
		first_quests: "badge-legend.png",
		first_swfish: "shield-1.png",
		first_coal: "shield-2.png",
		first_mlogs: "shield-3.png",
		first_herbs: "shield-4.png",
		first_gems: "medal-void.png",
		first_t1700: "medal-gold.png",
		first_spells: "shield-5.png"
	};

	var FALLBACK_AVATARS = ["assets/rsc-knight.png", "assets/rsc-ranger.png", "assets/rsc-mage.png"];

	function $(id) { return document.getElementById(id); }

	function el(tag, className, text) {
		var node = document.createElement(tag);
		if (className) node.className = className;
		if (text !== undefined) node.textContent = text;
		return node;
	}

	function object(value) {
		return value && typeof value === "object" && !Array.isArray(value) ? value : {};
	}

	function array(value) { return Array.isArray(value) ? value : []; }

	function text(value, fallback) {
		var clean = typeof value === "string" ? value.trim() : "";
		return clean || (fallback || "");
	}

	function number(value) {
		var parsed = Number(value);
		return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed) : 0;
	}

	function fmt(value) { return number(value).toLocaleString("en-US"); }

	function compact(value) {
		var n = number(value);
		if (n >= 1e9) return (n / 1e9).toFixed(n >= 1e10 ? 0 : 1) + "b";
		if (n >= 1e6) return (n / 1e6).toFixed(n >= 1e7 ? 0 : 1) + "m";
		if (n >= 1e3) return (n / 1e3).toFixed(n >= 1e4 ? 0 : 1) + "k";
		return String(n);
	}

	function ago(epochSeconds) {
		var seconds = Math.max(1, Math.floor(Date.now() / 1000) - number(epochSeconds));
		if (!number(epochSeconds)) return "recently";
		if (seconds < 60) return "now";
		if (seconds < 3600) return Math.floor(seconds / 60) + "m ago";
		if (seconds < 86400) return Math.floor(seconds / 3600) + "h ago";
		return Math.floor(seconds / 86400) + "d ago";
	}

	function skillByKey(key) {
		for (var i = 0; i < SKILLS.length; i += 1) if (SKILLS[i].key === key) return SKILLS[i];
		return null;
	}

	function hashName(name) {
		var hash = 0;
		for (var i = 0; i < name.length; i += 1) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
		return hash;
	}

	function avatarFor(entry) {
		return entry.avatar || FALLBACK_AVATARS[hashName(entry.player) % FALLBACK_AVATARS.length];
	}

	function decoratedName(parent, entry, nameClass) {
		if (entry.honorific) parent.appendChild(el("span", "honorific", entry.honorific + " "));
		parent.appendChild(el("span", nameClass || "", entry.player));
		if (entry.epithet) parent.appendChild(el("span", "epithet", " " + entry.epithet));
	}

	function pixelImg(src, scale, className) {
		var image = el("img", "px" + (className ? " " + className : ""));
		image.alt = "";
		image.loading = "lazy";
		image.decoding = "async";
		image.addEventListener("load", function () {
			image.style.width = image.naturalWidth * scale + "px";
			image.style.height = image.naturalHeight * scale + "px";
		});
		image.src = src;
		return image;
	}

	function normalizeEntry(raw, index) {
		var source = object(raw);
		return {
			rank: number(source.rank) || index + 1,
			player: text(source.player, "Unknown adventurer"),
			honorific: text(source.honorific),
			epithet: text(source.epithet),
			level: number(source.level),
			xp: number(source.xp),
			avatar: text(source.avatar)
		};
	}

	function normalizeWorld(payload) {
		var root = object(payload);
		if (!root.generatedAt || !root.pulse || !root.highscores) throw new Error("invalid_world_payload");
		var rawScores = object(root.highscores);
		var rawSkills = object(rawScores.skills);
		var skills = {};
		SKILLS.forEach(function (skill) {
			skills[skill.key] = array(rawSkills[skill.key]).slice(0, 10).map(normalizeEntry);
		});
		return {
			generatedAt: number(root.generatedAt),
			live: root.live === true,
			demo: root.demo === true,
			source: text(root.source),
			launchAt: text(root.launchAt),
			refreshAfterSeconds: Math.max(15, number(root.refreshAfterSeconds) || 45),
			pulse: {
				online: number(root.pulse.online), totalGp: number(root.pulse.totalGp),
				accounts: number(root.pulse.accounts), npcKills: number(root.pulse.npcKills),
				deaths: number(root.pulse.deaths), questsCompleted: number(root.pulse.questsCompleted)
			},
			feed: array(root.feed).slice(0, 12).map(function (entry) { return object(entry); }),
			highscores: {
				hiddenCount: number(rawScores.hiddenCount),
				overall: array(rawScores.overall).slice(0, 10).map(normalizeEntry),
				skills: skills
			},
			records: array(root.records).slice(0, 12).map(function (record) {
				var source = object(record);
				return {
					key: text(source.key), label: text(source.label, "Unmarked first"),
					value: text(source.value), detail: text(source.detail),
					claimed: source.claimed === true, anonymous: source.anonymous === true,
					holder: text(source.holder, "Unclaimed"), etchedAt: number(source.etchedAt),
					honorific: text(source.honorific), epithet: text(source.epithet)
				};
			}),
			economy: {
				totalGp: number(object(root.economy).totalGp),
				gpPerHead: number(object(root.economy).gpPerHead),
				topItems: array(object(root.economy).topItems).slice(0, 5).map(function (item) {
					return { itemId: number(item.itemId), name: text(item.name, "Unknown treasure"), count: number(item.count) };
				})
			},
			wilderness: {
				killsWeek: number(object(root.wilderness).killsWeek),
				deadliest: object(object(root.wilderness).deadliest),
				recent: array(object(root.wilderness).recent).slice(0, 8).map(function (entry) { return object(entry); })
			},
			facts: array(root.facts).slice(0, 12).map(function (fact) {
				return { text: text(object(fact).text), source: text(object(fact).source) };
			}).filter(function (fact) { return fact.text; }),
			availability: object(root.availability)
		};
	}

	function emptyWorld() {
		var skills = {};
		SKILLS.forEach(function (skill) { skills[skill.key] = []; });
		return {
			generatedAt: Math.floor(Date.now() / 1000), live: false, pulse: {}, feed: [],
			highscores: { hiddenCount: 0, overall: [], skills: skills }, records: [],
			economy: { totalGp: 0, gpPerHead: 0, topItems: [] },
			wilderness: { killsWeek: 0, deadliest: {}, recent: [] }, facts: []
		};
	}

	function sampleWorld() {
		var now = Math.floor(Date.now() / 1000);
		var rows = [
			{ rank: 1, player: "Maeve", honorific: "Saint", epithet: "", level: 1712, xp: 189231004 },
			{ rank: 2, player: "Vex", honorific: "", epithet: "the Warden", level: 1664, xp: 172440221 },
			{ rank: 3, player: "Bregan", honorific: "Knight", epithet: "", level: 1598, xp: 151882490 },
			{ rank: 4, player: "Ghostbeard", honorific: "", epithet: "", level: 1452, xp: 128110744 },
			{ rank: 5, player: "Ironvein", honorific: "", epithet: "the Unbroken", level: 1391, xp: 115908320 }
		];
		var skills = {};
		SKILLS.forEach(function (skill, skillIndex) {
			skills[skill.key] = rows.map(function (row, rowIndex) {
				return Object.assign({}, row, { level: Math.max(70, 99 - rowIndex * 3 - (skillIndex % 2)), xp: Math.max(1000000, 14200000 - rowIndex * 1700000 - skillIndex * 31000) });
			});
		});
		return normalizeWorld({
			generatedAt: now, live: false, source: "sample-preview", refreshAfterSeconds: 60,
			pulse: { online: 143, totalGp: 214183647, accounts: 1892, npcKills: 1204882, deaths: 45120, questsCompleted: 8241 },
			feed: [
				{ type: "pk", at: now - 150, player: "Vex", victim: "Saltmarsh", wildernessLevel: 12 },
				{ type: "max", at: now - 640, player: "Maeve", skill: "smithing" },
				{ type: "rare", at: now - 1300, player: "Ghostbeard", itemId: 594, itemName: "Dragon axe" },
				{ type: "quest", at: now - 2500, player: "Bregan", questName: "Dragon Slayer", questPoints: 44 }
			],
			highscores: { hiddenCount: 3, overall: rows, skills: skills },
			records: [
				{ key: "first_max", label: "First level 99", value: "The first maxed skill", detail: "First to reach level 99 in any skill", claimed: true, holder: "Maeve", honorific: "Saint", etchedAt: now - 1036800 },
				{ key: "first_total", label: "First max total", value: "1,782 total", detail: "First to master all eighteen skills", claimed: false, holder: "Unclaimed" },
				{ key: "first_quests", label: "First Lorekeeper", value: "Every quest completed", detail: "First to finish the full quest book", claimed: true, holder: "Bregan", honorific: "Knight", etchedAt: now - 604800 }
			],
			economy: { totalGp: 214183647, gpPerHead: 113204, topItems: [
				{ itemId: 373, name: "Lobster", count: 141207 }, { itemId: 40, name: "Nature rune", count: 96410 },
				{ itemId: 172, name: "Gold bar", count: 44892 }, { itemId: 81, name: "Rune 2-handed Sword", count: 214 }
			] },
			wilderness: { killsWeek: 47, deadliest: { player: "Vex", epithet: "the Warden", kills: 11 }, recent: [
				{ killer: "Vex", victim: "Saltmarsh", at: now - 150, wildernessLevel: 12 },
				{ killer: "Wolfsbane", victim: "Quintus", at: now - 8500, wildernessLevel: 31 }
			] },
			facts: [
				{ text: "There are **214,183,647 coins** in saved circulation.", source: "sample data for visual preview" },
				{ text: "The Chronicle counts **47 level-99 skills**.", source: "sample data for visual preview" },
				{ text: "The most hoarded treasure after coins is **Lobster**.", source: "sample data for visual preview" }
			]
		});
	}

	function setStatus(mode, message) {
		var status = $("chronicle-status");
		status.className = "chronicle-status is-" + mode;
		$("chronicle-status-text").textContent = message;
	}

	function renderPulse(world) {
		var pulse = world.pulse;
		Array.prototype.forEach.call(document.querySelectorAll("[data-pulse]"), function (tile) {
			var value = number(pulse[tile.getAttribute("data-pulse")]);
			var target = tile.querySelector("[data-count]");
			target.textContent = value >= 100000 ? compact(value) : fmt(value);
			target.setAttribute("aria-label", fmt(value));
			target.title = fmt(value);
		});
		$("nav-online").textContent = world.live ? fmt(pulse.online) + " online" : (world.demo ? "Prelaunch fiction" : "Sample ledger");
	}

	function feedDescription(entry) {
		var player = text(entry.player, "An adventurer");
		var skill = skillByKey(text(entry.skill));
		if (entry.type === "pk") return player + " defeated " + text(entry.victim, "a rival") + (number(entry.wildernessLevel) ? " in level " + number(entry.wildernessLevel) + " Wilderness" : " in the Wilderness");
		if (entry.type === "max") return player + " reached level 99 " + (skill ? skill.label : text(entry.skill, "in a skill"));
		if (entry.type === "level") return player + " reached level " + number(entry.level) + " " + (skill ? skill.label : text(entry.skill, "in a skill"));
		if (entry.type === "quest") return player + " completed " + text(entry.questName, "a quest");
		if (entry.type === "rare") return player + " found " + text(entry.itemName, "a rare treasure");
		return player + " left a mark on the world";
	}

	function feedIcon(entry) {
		if (entry.type === "pk") return pixelImg("assets/npc-database/item/20.png", 2, "feed-icon");
		if ((entry.type === "max" || entry.type === "level") && skillByKey(text(entry.skill))) {
			var image = el("img", "feed-art-icon");
			image.src = skillByKey(text(entry.skill)).icon;
			image.alt = "";
			image.loading = "lazy";
			return image;
		}
		if (entry.type === "rare" && Number.isFinite(Number(entry.itemId))) return pixelImg("assets/npc-database/item/" + number(entry.itemId) + ".png", 2, "feed-icon");
		var fallback = el("img", "feed-art-icon");
		fallback.src = "assets/world/mini-gold.png";
		fallback.alt = "";
		fallback.loading = "lazy";
		return fallback;
	}

	function renderFeed(world) {
		var list = $("feed-list");
		list.replaceChildren();
		if (!world.feed.length) {
			list.appendChild(el("li", "feed-waiting", "The live feed is quiet. New deeds will appear after the next world save."));
			$("feed-showcase").hidden = true;
			return;
		}
		world.feed.forEach(function (entry) {
			var item = el("li", "feed-entry feed-" + text(entry.type, "event"));
			item.appendChild(feedIcon(entry));
			var copy = el("span", "feed-copy");
			copy.appendChild(el("span", "feed-text", feedDescription(entry)));
			copy.appendChild(el("time", "feed-time", ago(entry.at)));
			item.appendChild(copy);
			list.appendChild(item);
		});
		var showcase = $("feed-showcase");
		var sentence = el("span", "feed-showcase-sentence", feedDescription(world.feed[0]));
		$("feed-showcase-text").replaceChildren(sentence);
		showcase.hidden = false;
	}

	function renderSkillRail() {
		var rail = $("skill-rail");
		rail.replaceChildren();
		[{ key: "overall", label: "Overall", icon: "assets/world/title-marker.png" }].concat(SKILLS).forEach(function (skill) {
			var button = el("button", "skill-tab" + (activeBoard === skill.key ? " is-active" : ""));
			button.type = "button";
			button.setAttribute("role", "tab");
			button.setAttribute("aria-selected", activeBoard === skill.key ? "true" : "false");
			button.setAttribute("aria-controls", "board");
			button.title = skill.label;
			var image = el("img", "skill-icon");
			image.src = skill.icon;
			image.alt = "";
			image.loading = "lazy";
			button.appendChild(image);
			button.appendChild(el("span", "skill-label", skill.label));
			button.addEventListener("click", function () {
				activeBoard = skill.key;
				renderMonument(lastGoodWorld || emptyWorld());
			});
			rail.appendChild(button);
		});
	}

	function boardRows(world) {
		return activeBoard === "overall" ? world.highscores.overall : (world.highscores.skills[activeBoard] || []);
	}

	function avatarImage(entry, className) {
		var image = el("img", className);
		image.alt = "Character sprite for " + entry.player;
		image.loading = "lazy";
		image.decoding = "async";
		var fallback = FALLBACK_AVATARS[hashName(entry.player) % FALLBACK_AVATARS.length];
		image.addEventListener("error", function onError() {
			image.removeEventListener("error", onError);
			image.src = fallback;
		});
		image.src = avatarFor(entry);
		return image;
	}

	function renderMonument(world) {
		if (!world.highscores.skills[activeBoard] && activeBoard !== "overall") activeBoard = "overall";
		renderSkillRail();
		var rows = boardRows(world);
		var boardLabel = activeBoard === "overall" ? "Overall highscores" : (skillByKey(activeBoard).label + " highscores");
		$("board-caption").textContent = boardLabel;
		var podium = $("podium");
		podium.replaceChildren();
		rows.slice(0, 3).forEach(function (entry) {
			var place = el("article", "pod pod-rank-" + entry.rank);
			place.appendChild(el("span", "pod-number", String(entry.rank)));
			place.appendChild(avatarImage(entry, "pod-avatar"));
			var name = el("p", "pod-name");
			decoratedName(name, entry, "pod-player");
			place.appendChild(name);
			place.appendChild(el("p", "pod-stat", (activeBoard === "overall" ? fmt(entry.level) + " total" : "Level " + fmt(entry.level)) + " · " + fmt(entry.xp) + " xp"));
			podium.appendChild(place);
		});
		if (!rows.length) podium.appendChild(el("p", "monument-empty", "No eligible adventurers have entered this monument yet."));

		var body = $("board-body");
		body.replaceChildren();
		rows.forEach(function (entry) {
			var row = document.createElement("tr");
			row.appendChild(el("td", "c-rank", String(entry.rank)));
			var adventurer = el("td", "board-player");
			adventurer.appendChild(avatarImage(entry, "board-avatar"));
			var name = el("span", "board-name");
			decoratedName(name, entry, "player-name");
			adventurer.appendChild(name);
			row.appendChild(adventurer);
			row.appendChild(el("td", "c-lvl", fmt(entry.level)));
			row.appendChild(el("td", "c-xp", fmt(entry.xp)));
			body.appendChild(row);
		});
		if (!rows.length) {
			var emptyRow = document.createElement("tr");
			var emptyCell = el("td", "board-empty", "The stone is waiting for its first name.");
			emptyCell.colSpan = 4;
			emptyRow.appendChild(emptyCell);
			body.appendChild(emptyRow);
		}
		$("hidden-count").textContent = world.highscores.hiddenCount ? fmt(world.highscores.hiddenCount) + " adventurer" + (world.highscores.hiddenCount === 1 ? " is" : "s are") + " currently hidden." : "";
	}

	function renderRecords(world) {
		var grid = $("records-grid");
		grid.replaceChildren();
		if (!world.records.length) {
			grid.appendChild(el("p", "section-empty", "The permanent record stones are unavailable right now."));
			return;
		}
		world.records.forEach(function (record) {
			var card = el("article", "record-card" + (record.claimed ? "" : " is-unclaimed"));
			var image = el("img", "record-art");
			image.src = "assets/world/" + (RECORD_ART[record.key] || "medal-void.png");
			image.alt = "";
			image.loading = "lazy";
			card.appendChild(image);
			card.appendChild(el("p", "record-label", record.label));
			var holder = el("h3", "record-holder");
			decoratedName(holder, { player: record.holder, honorific: record.honorific, epithet: record.epithet }, "record-player");
			card.appendChild(holder);
			card.appendChild(el("p", "record-value", record.value));
			card.appendChild(el("p", "record-detail", record.detail));
			card.appendChild(el("p", "record-date", record.claimed && record.etchedAt ? "Etched " + ago(record.etchedAt) : "Waiting for its first claimant"));
			grid.appendChild(card);
		});
	}

	function renderEconomy(world) {
		$("economy-total").textContent = fmt(world.economy.totalGp);
		$("economy-total").title = fmt(world.economy.totalGp);
		$("economy-sub").textContent = world.economy.gpPerHead ? fmt(world.economy.gpPerHead) + " gp per eligible adventurer" : "No saved coin holdings have been counted yet.";
		var list = $("hoard-list");
		list.replaceChildren();
		if (!world.economy.topItems.length) {
			list.appendChild(el("li", "hoard-empty", "The vault inventory is quiet."));
			return;
		}
		world.economy.topItems.forEach(function (item, index) {
			var row = el("li", "hoard-row");
			row.appendChild(el("span", "hoard-rank", String(index + 1)));
			row.appendChild(pixelImg("assets/npc-database/item/" + item.itemId + ".png", 2, "hoard-sprite"));
			row.appendChild(el("span", "hoard-name", item.name));
			row.appendChild(el("strong", "hoard-count", fmt(item.count)));
			list.appendChild(row);
		});
	}

	function renderWilderness(world) {
		$("wild-kills").textContent = fmt(world.wilderness.killsWeek);
		var deadliest = object(world.wilderness.deadliest);
		var deadliestName = $("wild-deadliest");
		deadliestName.replaceChildren();
		if (text(deadliest.player)) {
			decoratedName(deadliestName, {
				player: text(deadliest.player), honorific: text(deadliest.honorific), epithet: text(deadliest.epithet)
			}, "wild-player");
			$("wild-deadliest-kills").textContent = fmt(deadliest.kills) + " kill" + (number(deadliest.kills) === 1 ? "" : "s");
		} else {
			deadliestName.textContent = "No claimant";
			$("wild-deadliest-kills").textContent = "The Wilderness has been quiet this week";
		}
		var list = $("wild-list");
		list.replaceChildren();
		if (!world.wilderness.recent.length) {
			list.appendChild(el("li", "wild-empty", "No saved player kills this week."));
			return;
		}
		world.wilderness.recent.forEach(function (kill) {
			var item = el("li", "wild-entry");
			item.appendChild(el("span", "wild-killer", text(kill.killer, "Unknown")));
			item.appendChild(el("span", "wild-versus", "defeated"));
			item.appendChild(el("span", "wild-victim", text(kill.victim, "Unknown")));
			item.appendChild(el("time", "wild-time", ago(kill.at)));
			list.appendChild(item);
		});
	}

	function appendMarkedFact(parent, factText) {
		var parts = factText.split("**");
		parts.forEach(function (part, index) {
			parent.appendChild(index % 2 ? el("strong", "", part) : document.createTextNode(part));
		});
	}

	function showFact(index) {
		if (!currentFacts.length) {
			$("almanac-fact").textContent = "The Almanac has no verified entry to show right now.";
			$("almanac-source").textContent = "";
			$("almanac-btn").disabled = true;
			return;
		}
		factIndex = ((index % currentFacts.length) + currentFacts.length) % currentFacts.length;
		var fact = currentFacts[factIndex];
		var factNode = $("almanac-fact");
		factNode.replaceChildren();
		appendMarkedFact(factNode, fact.text);
		$("almanac-source").textContent = fact.source;
		$("almanac-btn").disabled = currentFacts.length < 2;
	}

	function renderAlmanac(world) {
		currentFacts = world.facts;
		factIndex = currentFacts.length ? world.generatedAt % currentFacts.length : 0;
		showFact(factIndex);
	}

	function renderLiveLine(world) {
		var message = world.feed.length ? feedDescription(world.feed[0]) + " · " + ago(world.feed[0].at) : "The ledger is open. New deeds will appear after the next world save.";
		$("live-line-text").textContent = message;
	}

	function formatLaunch(iso) {
		var value = Date.parse(iso);
		if (!Number.isFinite(value)) return "At launch";
		return new Date(value).toLocaleString(undefined, {
			month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit", timeZoneName: "short"
		});
	}

	function renderFictionNotice(world) {
		var notice = $("fiction-notice");
		notice.hidden = !world.demo;
		if (!world.demo) return;
		$("fiction-launch-copy").textContent = formatLaunch(world.launchAt) + ", this vision clears and real player deeds take its place.";
	}

	function armLaunchTransition(world) {
		if (launchTransitionTimer) window.clearTimeout(launchTransitionTimer);
		launchTransitionTimer = null;
		if (!world.demo || !world.launchAt) return;
		var delay = Date.parse(world.launchAt) - Date.now();
		if (!Number.isFinite(delay)) return;
		if (delay > 2147483000) return;
		launchTransitionTimer = window.setTimeout(function () {
			launchTransitionTimer = null;
			lastGoodWorld = null;
			renderUnavailable();
			refresh();
		}, Math.max(0, delay + 50));
	}

	function render(world) {
		renderFictionNotice(world);
		renderPulse(world);
		renderFeed(world);
		renderMonument(world);
		renderRecords(world);
		renderEconomy(world);
		renderWilderness(world);
		renderAlmanac(world);
		renderLiveLine(world);
	}

	function renderUnavailable() {
		lastGoodWorld = null;
		render(emptyWorld());
		$("nav-online").textContent = "Ledger unavailable";
		$("data-note").textContent = "Live world data is unavailable. The Chronicle will try again automatically.";
		setStatus("error", "The live ledger cannot be reached. No sample statistics are being shown.");
	}

	function fetchWorld() {
		if (requestInFlight) return requestInFlight;
		var controller = typeof AbortController === "function" ? new AbortController() : null;
		var timeout = window.setTimeout(function () { if (controller) controller.abort(); }, 8000);
		requestInFlight = fetch(API_URL, {
			headers: { Accept: "application/json" },
			signal: controller ? controller.signal : undefined
		}).then(function (response) {
			if (!response.ok) throw new Error("world_http_" + response.status);
			return response.json();
		}).then(normalizeWorld).finally(function () {
			window.clearTimeout(timeout);
			requestInFlight = null;
		});
		return requestInFlight;
	}

	function refresh() {
		if (previewRequested) {
			lastGoodWorld = sampleWorld();
			render(lastGoodWorld);
			setStatus("preview", "Sample preview: these names and statistics are not live world data.");
			$("data-note").textContent = "Sample preview data. Remove ?preview=1 to read the live ledger.";
			return Promise.resolve();
		}
		if (!lastGoodWorld) setStatus("loading", "Opening the live ledger...");
		return fetchWorld().then(function (world) {
			lastGoodWorld = world;
			render(world);
			armLaunchTransition(world);
			if (world.demo) {
				setStatus("fiction", "Prelaunch fiction: every name, deed, and statistic shown below is imagined.");
				$("data-note").textContent = "Fictional prelaunch data. At launch, it clears automatically and the real Chronicle begins.";
			} else {
				setStatus("live", "Live ledger: saved world data refreshed " + ago(world.generatedAt) + ".");
				$("data-note").textContent = "Live saved-world data. The ledger checks for updates every minute.";
			}
		}).catch(function (error) {
			if (window.console && typeof window.console.warn === "function") window.console.warn("Chronicle refresh failed", error);
			if (lastGoodWorld) {
				setStatus("stale", "The next reading failed. Showing the last verified ledger from " + ago(lastGoodWorld.generatedAt) + ".");
				$("data-note").textContent = "Last verified saved-world data. Reconnection happens automatically.";
				return;
			}
			if (localPreviewAllowed) {
				lastGoodWorld = sampleWorld();
				render(lastGoodWorld);
				setStatus("preview", "Local sample preview: the live game database is not connected.");
				$("data-note").textContent = "Local sample preview. These names and statistics are not live.";
				return;
			}
			renderUnavailable();
		});
	}

	$("almanac-btn").addEventListener("click", function () { showFact(factIndex + 1); });
	var motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
	if (motionQuery.addEventListener) motionQuery.addEventListener("change", function (event) { reduceMotion = event.matches; });
	if (reduceMotion) document.documentElement.classList.add("reduce-motion");

	refresh();
	window.setInterval(refresh, REFRESH_MS);
	document.addEventListener("visibilitychange", function () {
		if (!document.hidden && lastGoodWorld && Date.now() / 1000 - lastGoodWorld.generatedAt > REFRESH_MS / 1000) refresh();
	});
}());
