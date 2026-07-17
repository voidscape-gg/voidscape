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
	var pulseRendered = false;
	var hoardAnimated = false;
	var feedAnimated = false;
	var recordsAnimated = false;
	var monumentAnimate = true;
	var seenFeedKeys = null;
	var fictionCountdownTimer = null;
	var factAutoTimer = null;
	var factAutoPaused = false;
	var SINCE_STORE_KEY = "chronicle-last-reading";
	var SINCE_DISMISS_KEY = "chronicle-since-dismissed";
	var liveLineTimer = null;
	var liveLineIndex = 0;
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

	/* Every record stone gets real art: painted kit pieces for the grand firsts,
	   integer-scaled game item sprites for the grind firsts. */
	var RECORD_ART = {
		first_max: { art: "assets/world/medal-voidspike.png" },
		first_total: { art: "assets/world/crown-gold.png" },
		first_quests: { art: "assets/world/obelisk.png" },
		first_swfish: { sprite: 370, scale: 2 },
		first_coal: { sprite: 155, scale: 2 },
		first_mlogs: { sprite: 636, scale: 2 },
		first_herbs: { sprite: 165, scale: 2 },
		first_gems: { sprite: 157, scale: 2 },
		first_t1700: { art: "assets/world/medal-gold.png" },
		first_spells: { sprite: 38, scale: 2 }
	};

	var POD_CROWNS = ["assets/world/crown-gold.png", "assets/world/crown-silver.png", "assets/world/crown-iron.png"];
	var POD_SHIELDS = ["assets/world/shield-1.png", "assets/world/shield-2.png", "assets/world/shield-3.png"];
	/* Authentic RSC item picture masks. The source archive stores neutral gray
	   artwork; the client supplies each item's palette at draw time. */
	var ITEM_PICTURE_MASKS = {
		75: 0x00FFFF,
		81: 0x00FFFF,
		172: 0xFFCC4C,
		373: 0xB06000
	};
	var smallStage = window.matchMedia("(max-width: 600px)");

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

	/* Static sprites declared in the HTML carry data-px; give them real
	   integer-scaled layout boxes so nothing can bleed over neighbors. */
	function sizeStaticPixelSprites() {
		Array.prototype.forEach.call(document.querySelectorAll("img[data-px]"), function (image) {
			var scale = Math.max(1, parseInt(image.getAttribute("data-px"), 10) || 1);
			function apply() {
				if (!image.naturalWidth) return;
				image.style.width = image.naturalWidth * scale + "px";
				image.style.height = image.naturalHeight * scale + "px";
			}
			if (image.complete) apply();
			else image.addEventListener("load", apply);
		});
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

	function maskedPixelCanvas(src, scale, pictureMask, className) {
		var canvas = el("canvas", "px" + (className ? " " + className : ""));
		canvas.width = 1;
		canvas.height = 1;
		canvas.setAttribute("aria-hidden", "true");
		var source = new Image();
		source.decoding = "async";
		source.addEventListener("load", function () {
			canvas.width = source.naturalWidth;
			canvas.height = source.naturalHeight;
			canvas.style.width = source.naturalWidth * scale + "px";
			canvas.style.height = source.naturalHeight * scale + "px";
			var context = canvas.getContext("2d", { willReadFrequently: true });
			context.imageSmoothingEnabled = false;
			context.drawImage(source, 0, 0);
			var pixels = context.getImageData(0, 0, canvas.width, canvas.height);
			var maskR = pictureMask >> 16 & 0xFF;
			var maskG = pictureMask >> 8 & 0xFF;
			var maskB = pictureMask & 0xFF;
			for (var index = 0; index < pixels.data.length; index += 4) {
				var red = pixels.data[index];
				var green = pixels.data[index + 1];
				var blue = pixels.data[index + 2];
				if (!pixels.data[index + 3] || red !== green || green !== blue) continue;
				pixels.data[index] = red * maskR >> 8;
				pixels.data[index + 1] = green * maskG >> 8;
				pixels.data[index + 2] = blue * maskB >> 8;
			}
			context.putImageData(pixels, 0, 0);
		});
		source.src = src;
		return canvas;
	}

	function itemPixelArt(itemId, scale, className) {
		var pictureMask = ITEM_PICTURE_MASKS[itemId];
		var src = "assets/npc-database/item/" + itemId + ".png";
		return pictureMask
			? maskedPixelCanvas(src, scale, pictureMask, className)
			: pixelImg(src, scale, className);
	}

	function goldMedallion() {
		var crop = el("span", "gold-medallion");
		crop.setAttribute("aria-hidden", "true");
		var image = el("img", "px");
		image.src = "assets/world/medal-gold.png";
		if (image.tagName === "IMG") {
			image.alt = "";
			image.loading = "lazy";
		}
		crop.appendChild(image);
		return crop;
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

	/* Numbers tally up the first time their tile scrolls into view. Values at a
	   billion or beyond stay compact; everything else gets its full glory. */
	function displayNumber(value) { return value >= 1e9 ? compact(value) : fmt(value); }

	function countUp(node, target) {
		if (reduceMotion || node.dataset.counted === String(target)) {
			node.dataset.counted = String(target);
			node.textContent = displayNumber(target);
			return;
		}
		node.dataset.counted = String(target);
		var start = null;
		function frame(ts) {
			if (node.dataset.counted !== String(target)) return;
			if (!start) start = ts;
			var p = Math.min(1, (ts - start) / 1500);
			var eased = 1 - Math.pow(1 - p, 4);
			node.textContent = displayNumber(Math.round(target * eased));
			if (p < 1) window.requestAnimationFrame(frame);
		}
		window.requestAnimationFrame(frame);
	}

	function countWhenVisible(node, target) {
		if (reduceMotion || typeof IntersectionObserver !== "function") {
			countUp(node, target);
			return;
		}
		node.textContent = "0";
		var observer = new IntersectionObserver(function (entries) {
			entries.forEach(function (entry) {
				if (!entry.isIntersecting) return;
				observer.disconnect();
				countUp(node, target);
			});
		}, { threshold: 0.35 });
		observer.observe(node);
	}

	function renderPulse(world) {
		var pulse = world.pulse;
		var firstPaint = !pulseRendered;
		pulseRendered = true;
		Array.prototype.forEach.call(document.querySelectorAll("[data-pulse]"), function (tile) {
			var value = number(pulse[tile.getAttribute("data-pulse")]);
			var target = tile.querySelector("[data-count]");
			var changed = !firstPaint && target.dataset.counted && target.dataset.counted !== String(value);
			if (firstPaint) countWhenVisible(target, value);
			else countUp(target, value);
			if (changed && !reduceMotion) {
				target.classList.remove("is-ticking");
				void target.offsetWidth;
				target.classList.add("is-ticking");
			}
			target.setAttribute("aria-label", fmt(value));
			target.title = fmt(value);
		});
		$("nav-online").textContent = world.live
			? fmt(pulse.online) + " adventurers online"
			: (world.demo ? "A fictional vision, for now" : "Sample ledger");
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

	/* Rich version of feedDescription: player names in cream, the deed's object
	   accented, all built from the same normalized fields. */
	function feedSentence(entry) {
		var fragment = document.createDocumentFragment();
		function name(value) { fragment.appendChild(el("strong", "", value)); }
		function plain(value) { fragment.appendChild(document.createTextNode(value)); }
		function accent(value) { fragment.appendChild(el("em", "", value)); }
		var player = text(entry.player, "An adventurer");
		var skill = skillByKey(text(entry.skill));
		var skillLabel = skill ? skill.label : text(entry.skill, "a skill");
		if (entry.type === "pk") {
			name(player); plain(" has slain "); accent(text(entry.victim, "a rival"));
			plain(number(entry.wildernessLevel) ? " in level-" + number(entry.wildernessLevel) + " Wilderness." : " in the Wilderness.");
		} else if (entry.type === "max") {
			name(player); plain(" has achieved "); accent("level-99 " + skillLabel); plain(", the maximum possible!");
		} else if (entry.type === "level") {
			name(player); plain(" has achieved level-" + number(entry.level) + " in "); accent(skillLabel); plain("!");
		} else if (entry.type === "quest") {
			name(player); plain(" has completed "); accent(text(entry.questName, "a quest"));
			plain(number(entry.questPoints) ? " and now has " + number(entry.questPoints) + " quest points!" : "!");
		} else if (entry.type === "rare") {
			var item = text(entry.itemName, "a rare treasure");
			name(player); plain(/^[aeiou]/i.test(item) ? " has obtained an " : " has obtained a "); accent(item); plain("!");
		} else if (entry.type === "new") {
			plain("Welcome "); name(player); plain(" to Voidscape. This is their first time in the world.");
		} else if (entry.type === "total") {
			name(player); plain(" has crossed "); accent(fmt(entry.totalLevel) + " total level"); plain(".");
		} else {
			plain(feedDescription(entry));
		}
		return fragment;
	}

	function feedIcon(entry) {
		var wrap = el("span", "feed-icon");
		var image;
		if (entry.type === "pk") {
			image = el("img");
			image.src = "assets/world/mini-voidspike.png";
		} else if ((entry.type === "max" || entry.type === "level") && skillByKey(text(entry.skill))) {
			image = el("img");
			image.src = skillByKey(text(entry.skill)).icon;
		} else if (entry.type === "rare" && Number.isFinite(Number(entry.itemId))) {
			var itemId = number(entry.itemId);
			image = itemPixelArt(itemId, 1);
		} else if (entry.type === "total") {
			image = el("img");
			image.src = "assets/world/mini-void.png";
		} else if (entry.type === "quest") {
			image = goldMedallion();
		} else {
			image = el("img");
			image.src = "assets/world/mini-gold.png";
		}
		image.alt = "";
		image.loading = "lazy";
		wrap.appendChild(image);
		return wrap;
	}

	function renderFeed(world) {
		var list = $("feed-list");
		list.replaceChildren();
		if (!world.feed.length) {
			list.appendChild(el("li", "feed-waiting", "The live feed is quiet. New deeds will appear after the next world save."));
			$("feed-showcase").hidden = true;
			return;
		}
		var previouslySeen = seenFeedKeys;
		var nowSeen = new Set();
		world.feed.forEach(function (entry, index) {
			var key = text(entry.type) + "|" + text(entry.player) + "|" + number(entry.at);
			nowSeen.add(key);
			var isNew = previouslySeen !== null && !previouslySeen.has(key);
			var item = el("li", "feed-entry feed-" + text(entry.type, "event") + (isNew && !reduceMotion ? " is-new" : ""));
			if (feedAnimated || reduceMotion) item.style.animation = isNew && !reduceMotion ? "" : "none";
			else item.style.animationDelay = Math.min(index * 55, 550) + "ms";
			item.appendChild(feedIcon(entry));
			var copy = el("span", "feed-copy");
			var line = el("span", "feed-text");
			line.appendChild(feedSentence(entry));
			copy.appendChild(line);
			var time = el("time", "feed-time", ago(entry.at));
			time.title = feedDescription(entry);
			copy.appendChild(time);
			item.appendChild(copy);
			list.appendChild(item);
		});

		/* The most recent Wilderness kill gets the carved stone plaque. */
		var showcase = $("feed-showcase");
		var pk = null;
		for (var i = 0; i < world.feed.length; i += 1) {
			if (world.feed[i].type === "pk") { pk = world.feed[i]; break; }
		}
		if (pk) {
			var line2 = $("feed-showcase-text");
			line2.replaceChildren();
			var sentence = el("span", "feed-showcase-sentence");
			sentence.appendChild(el("span", "who", text(pk.player, "Unknown")));
			sentence.appendChild(document.createTextNode(" has slain "));
			sentence.appendChild(el("span", "whom", text(pk.victim, "Unknown")));
			if (number(pk.wildernessLevel)) sentence.appendChild(document.createTextNode(" · level-" + number(pk.wildernessLevel) + " wild"));
			line2.appendChild(sentence);
			showcase.hidden = false;
		} else {
			showcase.hidden = true;
		}
		feedAnimated = true;
		seenFeedKeys = nowSeen;
	}

	/* Built once, then only the active state is synced — periodic refreshes must
	   never yank chips out from under a tap. */
	function renderSkillRail() {
		var rail = $("skill-rail");
		var chips = [{ key: "overall", label: "Overall", icon: "assets/world/title-marker.png" }].concat(SKILLS);
		if (rail.childElementCount !== chips.length) {
			rail.replaceChildren();
			chips.forEach(function (skill) {
				var button = el("button", "skill-tab");
				button.type = "button";
				button.setAttribute("role", "tab");
				button.setAttribute("aria-controls", "board");
				button.dataset.skill = skill.key;
				button.title = skill.label;
				var image = el("img", "skill-icon");
				image.src = skill.icon;
				image.alt = "";
				image.loading = "lazy";
				button.appendChild(image);
				button.appendChild(el("span", "skill-label", skill.label));
				button.addEventListener("click", function () {
					activeBoard = skill.key;
					monumentAnimate = true;
					renderMonument(lastGoodWorld || emptyWorld());
				});
				rail.appendChild(button);
			});
		}
		Array.prototype.forEach.call(rail.children, function (button) {
			var selected = button.dataset.skill === activeBoard;
			button.classList.toggle("is-active", selected);
			button.setAttribute("aria-selected", selected ? "true" : "false");
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

	function podAvatar(entry) {
		var scale = smallStage.matches ? 1 : 2;
		var image = avatarImage(entry, "pod-avatar px");
		image.addEventListener("load", function () {
			image.style.width = image.naturalWidth * scale + "px";
			image.style.height = image.naturalHeight * scale + "px";
		});
		return image;
	}

	function renderMonument(world) {
		if (!world.highscores.skills[activeBoard] && activeBoard !== "overall") activeBoard = "overall";
		renderSkillRail();
		var rows = boardRows(world);
		var boardLabel = activeBoard === "overall" ? "Overall highscores" : (skillByKey(activeBoard).label + " highscores");
		$("board-caption").textContent = boardLabel;
		var podium = $("podium");
		var animate = monumentAnimate && !reduceMotion;
		monumentAnimate = false;
		podium.replaceChildren();
		rows.slice(0, 3).forEach(function (entry) {
			var place = el("article", "pod pod-" + entry.rank + " pod-rank-" + entry.rank);
			if (!animate) place.style.animation = "none";

			var figure = el("span", "pod-figure");

			var crown = el("img", "pod-crown");
			crown.src = POD_CROWNS[entry.rank - 1] || POD_CROWNS[2];
			crown.alt = "";
			figure.appendChild(crown);

			var stage = el("span", "pod-stage");
			stage.appendChild(podAvatar(entry));
			figure.appendChild(stage);

			var shield = el("img", "pod-shield");
			shield.src = POD_SHIELDS[entry.rank - 1] || POD_SHIELDS[2];
			shield.alt = "Rank " + entry.rank;
			figure.appendChild(shield);
			place.appendChild(figure);

			var name = el("p", "pod-name");
			name.appendChild(el("span", "pod-player", entry.player));
			if (entry.epithet) name.appendChild(el("span", "epithet", " " + entry.epithet));
			place.appendChild(name);
			if (entry.honorific) place.appendChild(el("span", "pod-honorific", entry.honorific));

			var stat = el("p", "pod-stat");
			stat.appendChild(el("b", "", activeBoard === "overall" ? fmt(entry.level) + " total" : "Level " + fmt(entry.level)));
			stat.appendChild(document.createTextNode(" · " + compact(entry.xp) + " xp"));
			stat.title = fmt(entry.xp) + " experience";
			place.appendChild(stat);
			podium.appendChild(place);
		});
		if (!rows.length) podium.appendChild(el("p", "monument-empty", "No eligible adventurers have entered this monument yet."));

		var body = $("board-body");
		body.replaceChildren();
		rows.forEach(function (entry, index) {
			var row = document.createElement("tr");
			if (!animate) row.style.animation = "none";
			else row.style.animationDelay = Math.min(index * 40, 400) + "ms";
			row.appendChild(el("td", "c-rank", String(entry.rank)));
			var adventurer = el("td", "board-player");
			var name = el("span", "board-name");
			decoratedName(name, entry, "player-name");
			adventurer.appendChild(name);
			row.appendChild(adventurer);
			row.appendChild(el("td", "c-lvl", fmt(entry.level)));
			var xpCell = el("td", "c-xp");
			xpCell.appendChild(el("b", "", fmt(entry.xp)));
			xpCell.appendChild(document.createTextNode(" xp"));
			row.appendChild(xpCell);
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
		world.records.forEach(function (record, index) {
			var card = el("article", "record-card" + (record.claimed ? "" : " is-unclaimed"));
			if (recordsAnimated || reduceMotion) card.style.animation = "none";
			else card.style.animationDelay = Math.min(index * 60, 480) + "ms";
			var artWrap = el("span", "record-art-wrap");
			var art = RECORD_ART[record.key] || { art: "assets/world/medal-void.png" };
			var image;
			if (art.sprite) {
				image = pixelImg("assets/npc-database/item/" + art.sprite + ".png", art.scale || 2, "record-art");
			} else {
				image = el("img", "record-art");
				image.src = art.art;
				image.alt = "";
				image.loading = "lazy";
			}
			artWrap.appendChild(image);
			card.appendChild(artWrap);
			card.appendChild(el("p", "record-label", record.label));
			var holder = el("h3", "record-holder");
			decoratedName(holder, { player: record.holder, honorific: record.honorific, epithet: record.epithet }, "record-player");
			card.appendChild(holder);
			card.appendChild(el("p", "record-value", record.value));
			card.appendChild(el("p", "record-detail", record.detail));
			card.appendChild(el("p", "record-date", record.claimed && record.etchedAt ? "Etched " + ago(record.etchedAt) : "Waiting for its first claimant"));
			if (record.claimed && record.etchedAt && world.generatedAt - record.etchedAt < 7 * 86400) {
				card.appendChild(el("span", "record-fresh", "Freshly etched"));
			}
			grid.appendChild(card);
		});
	}

	function renderEconomy(world) {
		var totalNode = $("economy-total");
		if (pulseRendered && totalNode.dataset.counted) countUp(totalNode, world.economy.totalGp);
		else countWhenVisible(totalNode, world.economy.totalGp);
		totalNode.title = fmt(world.economy.totalGp);
		$("economy-sub").textContent = world.economy.gpPerHead
			? "That is " + fmt(world.economy.gpPerHead) + " gp for every eligible soul in the world — if it were shared, which it never will be."
			: "No saved coin holdings have been counted yet.";
		var list = $("hoard-list");
		list.replaceChildren();
		if (!world.economy.topItems.length) {
			list.appendChild(el("li", "hoard-empty", "The vault inventory is quiet."));
			return;
		}
		var max = number(world.economy.topItems[0].count) || 1;
		world.economy.topItems.forEach(function (item, index) {
			var row = el("li", "hoard-row");
			row.appendChild(el("span", "hoard-rank", String(index + 1)));
			row.appendChild(itemPixelArt(item.itemId, 1, "hoard-sprite"));
			var meta = el("span", "hoard-meta");
			var name = el("span", "hoard-name", item.name);
			name.title = item.name;
			meta.appendChild(name);
			var bar = el("span", "hoard-bar");
			var fill = el("i");
			var ratio = Math.max(0.04, Math.sqrt(number(item.count) / max));
			if (reduceMotion || hoardAnimated) {
				fill.style.transition = "none";
				fill.style.transform = "scaleX(" + ratio + ")";
			} else {
				fill.style.transform = "scaleX(0)";
				window.requestAnimationFrame(function () {
					window.requestAnimationFrame(function () { fill.style.transform = "scaleX(" + ratio + ")"; });
				});
			}
			bar.appendChild(fill);
			meta.appendChild(bar);
			row.appendChild(meta);
			var count = el("strong", "hoard-count", compact(item.count));
			count.title = fmt(item.count);
			row.appendChild(count);
			list.appendChild(row);
		});
		hoardAnimated = true;
		recordsAnimated = true;
	}

	function renderWilderness(world) {
		var killsNode = $("wild-kills");
		if (killsNode.dataset.counted) countUp(killsNode, world.wilderness.killsWeek);
		else countWhenVisible(killsNode, world.wilderness.killsWeek);
		killsNode.title = fmt(world.wilderness.killsWeek);
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
			item.appendChild(el("span", "wild-versus", "slew"));
			item.appendChild(el("span", "wild-victim", text(kill.victim, "Unknown")));
			if (number(kill.wildernessLevel)) item.appendChild(el("span", "wild-level", "in level-" + number(kill.wildernessLevel)));
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

	function showFact(index, animate) {
		if (!currentFacts.length) {
			$("almanac-fact").textContent = "The Almanac has no verified entry to show right now.";
			$("almanac-source").textContent = "";
			$("almanac-btn").disabled = true;
			return;
		}
		factIndex = ((index % currentFacts.length) + currentFacts.length) % currentFacts.length;
		var fact = currentFacts[factIndex];
		var factNode = $("almanac-fact");
		function paint() {
			factNode.replaceChildren();
			appendMarkedFact(factNode, fact.text);
			$("almanac-source").textContent = fact.source;
			factNode.classList.remove("is-swapping");
		}
		if (animate && !reduceMotion) {
			factNode.classList.add("is-swapping");
			window.setTimeout(paint, 300);
		} else {
			paint();
		}
		$("almanac-btn").disabled = currentFacts.length < 2;
	}

	/* Auto-turn the Almanac's page every so often; silent for screen readers
	   (only an explicit consult announces), paused while the reader lingers. */
	function stopFactRotation() {
		if (factAutoTimer) window.clearInterval(factAutoTimer);
		factAutoTimer = null;
	}

	function startFactRotation() {
		stopFactRotation();
		if (reduceMotion || currentFacts.length < 2) return;
		factAutoTimer = window.setInterval(function () {
			if (factAutoPaused || document.hidden) return;
			showFact(factIndex + 1, true);
		}, 14000);
	}

	function renderAlmanac(world) {
		currentFacts = world.facts;
		factIndex = currentFacts.length ? world.generatedAt % currentFacts.length : 0;
		showFact(factIndex);
		startFactRotation();
	}

	/* The hero line cycles through the feed like a herald reading the ledger aloud. */
	function renderLiveLine(world) {
		var node = $("live-line-text");
		if (liveLineTimer) window.clearInterval(liveLineTimer);
		liveLineTimer = null;
		if (!world.feed.length) {
			node.textContent = "The ledger is open. New deeds will appear after the next world save.";
			return;
		}
		liveLineIndex = 0;
		function paint() {
			node.replaceChildren(feedSentence(world.feed[liveLineIndex % world.feed.length]));
			liveLineIndex += 1;
		}
		function swap() {
			if (reduceMotion) { paint(); return; }
			node.classList.add("is-swapping");
			window.setTimeout(function () {
				paint();
				node.classList.remove("is-swapping");
			}, 360);
		}
		paint();
		if (world.feed.length > 1) liveLineTimer = window.setInterval(swap, 5200);
	}

	function formatLaunch(iso) {
		var value = Date.parse(iso);
		if (!Number.isFinite(value)) return "At launch";
		return new Date(value).toLocaleString(undefined, {
			month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit", timeZoneName: "short"
		});
	}

	function shortDuration(ms) {
		var minutes = Math.max(1, Math.round(ms / 60000));
		if (minutes < 60) return minutes + "m";
		var hours = Math.floor(minutes / 60);
		if (hours < 48) return hours + "h " + (minutes % 60) + "m";
		return Math.floor(hours / 24) + "d " + (hours % 24) + "h";
	}

	function renderFictionNotice(world) {
		var notice = $("fiction-notice");
		notice.hidden = !world.demo;
		if (fictionCountdownTimer) window.clearInterval(fictionCountdownTimer);
		fictionCountdownTimer = null;
		if (!world.demo) return;
		$("fiction-launch-copy").textContent = formatLaunch(world.launchAt) + ", this vision clears and real player deeds take its place.";
		var launchMs = Date.parse(world.launchAt);
		if (!Number.isFinite(launchMs)) return;
		function tick() {
			var remaining = launchMs - Date.now();
			$("fiction-countdown").textContent = remaining > 0 ? "The gates open in " + shortDuration(remaining) + "." : "";
		}
		tick();
		fictionCountdownTimer = window.setInterval(tick, 30000);
	}

	/* ---- since your last reading (return-visit delta, stored locally) ---- */

	var SINCE_CHIPS = [
		{ key: "npcKills", label: "monsters slain", icon: "assets/world/skills/skill-attack.png" },
		{ key: "deaths", label: "deaths", icon: "assets/npc-database/item/20.png" },
		{ key: "questsCompleted", label: "quests completed", icon: "assets/world/mini-gold.png" },
		{ key: "accounts", label: "souls forged", icon: "assets/world/title-marker.png" },
		{ key: "totalGp", label: "coins minted", icon: "assets/npc-database/item/10.png" }
	];

	function readSinceSnapshot() {
		try {
			var raw = window.localStorage.getItem(SINCE_STORE_KEY);
			return raw ? JSON.parse(raw) : null;
		} catch (error) { return null; }
	}

	function writeSinceSnapshot(world) {
		try {
			window.localStorage.setItem(SINCE_STORE_KEY, JSON.stringify({
				at: world.generatedAt, source: world.source, pulse: world.pulse
			}));
		} catch (error) { /* private mode: the strip simply never appears */ }
	}

	function renderSinceStrip(world) {
		var strip = $("since-strip");
		var previous = readSinceSnapshot();
		var dismissed = false;
		try { dismissed = window.sessionStorage.getItem(SINCE_DISMISS_KEY) === "1"; } catch (error) { /* ignore */ }
		var show = false;
		if (previous && previous.source === world.source && previous.pulse
			&& world.generatedAt - number(previous.at) > 600 && !dismissed) {
			var chips = $("since-chips");
			chips.replaceChildren();
			SINCE_CHIPS.forEach(function (item) {
				var delta = number(world.pulse[item.key]) - number(previous.pulse[item.key]);
				if (delta <= 0) return;
				var chip = el("span", "since-chip");
				var icon = el("img");
				icon.src = item.icon;
				icon.alt = "";
				chip.appendChild(icon);
				chip.appendChild(el("b", "", "+" + fmt(delta)));
				chip.appendChild(document.createTextNode(" " + item.label));
				chips.appendChild(chip);
				show = true;
			});
		}
		strip.hidden = !show;
		writeSinceSnapshot(world);
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
		renderSinceStrip(world);
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

	function consultTheVoid(flare) {
		showFact(factIndex + 1, true);
		if (flare && !reduceMotion) {
			var portal = document.querySelector(".almanac-portal");
			portal.classList.remove("is-flaring");
			void portal.offsetWidth;
			portal.classList.add("is-flaring");
		}
		var fact = currentFacts[factIndex];
		if (fact) $("almanac-announce").textContent = fact.text.replace(/\*/g, "");
		startFactRotation();
	}

	$("almanac-btn").addEventListener("click", function () { consultTheVoid(true); });
	/* pointer-only delight: the portal itself answers; the button remains the accessible path */
	document.querySelector(".almanac-portal").addEventListener("click", function () { consultTheVoid(true); });

	var almanacBox = document.querySelector(".almanac");
	almanacBox.addEventListener("mouseenter", function () { factAutoPaused = true; });
	almanacBox.addEventListener("mouseleave", function () { factAutoPaused = false; });
	almanacBox.addEventListener("focusin", function () { factAutoPaused = true; });
	almanacBox.addEventListener("focusout", function () { factAutoPaused = false; });

	$("since-dismiss").addEventListener("click", function () {
		$("since-strip").hidden = true;
		try { window.sessionStorage.setItem(SINCE_DISMISS_KEY, "1"); } catch (error) { /* ignore */ }
	});

	sizeStaticPixelSprites();

	if (smallStage.addEventListener) smallStage.addEventListener("change", function () {
		if (lastGoodWorld) renderMonument(lastGoodWorld);
	});
	var motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
	if (motionQuery.addEventListener) motionQuery.addEventListener("change", function (event) {
		reduceMotion = event.matches;
		if (reduceMotion) stopFactRotation();
		else startFactRotation();
	});
	if (reduceMotion) document.documentElement.classList.add("reduce-motion");

	refresh();
	window.setInterval(refresh, REFRESH_MS);
	document.addEventListener("visibilitychange", function () {
		if (!document.hidden && lastGoodWorld && Date.now() / 1000 - lastGoodWorld.generatedAt > REFRESH_MS / 1000) refresh();
	});
}());
