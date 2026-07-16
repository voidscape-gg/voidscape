(() => {
	"use strict";

	const DATA_URL = document.body.dataset.sourceUrl || "assets/loot-editor-data.json";
	const STORAGE_NAMESPACE = "voidscape.loot-editor.draft.v1";
	const MAX_INT = 2147483647;
	const MAX_HISTORY = 50;
	const MAX_IMPORT_BYTES = 5 * 1024 * 1024;

	const elements = {
		shell: document.getElementById("editor-shell"),
		loading: document.getElementById("editor-loading"),
		content: document.getElementById("editor-content"),
		loadError: document.getElementById("load-error"),
		loadErrorMessage: document.getElementById("load-error-message"),
		retryLoad: document.getElementById("retry-load"),
		saveState: document.getElementById("save-state"),
		tableSearch: document.getElementById("table-search"),
		filterButtons: Array.from(document.querySelectorAll("[data-filter]")),
		browserSummary: document.getElementById("browser-summary"),
		tableList: document.getElementById("table-list"),
		changeCount: document.getElementById("change-count"),
		groupArt: document.getElementById("group-art"),
		groupKicker: document.getElementById("group-kicker"),
		groupTitle: document.getElementById("group-title"),
		groupSource: document.getElementById("group-source"),
		scopeNotice: document.getElementById("scope-notice"),
		affectedNotice: document.getElementById("affected-notice"),
		undoButton: document.getElementById("undo-button"),
		resetGroupButton: document.getElementById("reset-group-button"),
		addItemButton: document.getElementById("add-item-button"),
		weightStatus: document.getElementById("weight-status"),
		weightSummaryHelp: document.getElementById("weight-summary-help"),
		editableWeight: document.getElementById("editable-weight"),
		lockedWeight: document.getElementById("locked-weight"),
		remainingWeight: document.getElementById("remaining-weight"),
		totalWeight: document.getElementById("total-weight"),
		remainderLabel: document.getElementById("remainder-label"),
		validationBox: document.getElementById("validation-box"),
		validationList: document.getElementById("validation-list"),
		editableRowsHelp: document.getElementById("editable-rows-help"),
		editableRows: document.getElementById("editable-rows"),
		editableEmpty: document.getElementById("editable-empty"),
		lockedSection: document.getElementById("locked-section"),
		lockedRows: document.getElementById("locked-rows"),
		changedGroups: document.getElementById("changed-groups"),
		errorCount: document.getElementById("error-count"),
		changeNote: document.getElementById("change-note"),
		exportButton: document.getElementById("export-button"),
		importButton: document.getElementById("import-button"),
		importFile: document.getElementById("import-file"),
		resetAllButton: document.getElementById("reset-all-button"),
		sourceFingerprint: document.getElementById("source-fingerprint"),
		sourceGenerator: document.getElementById("source-generator"),
		itemDialog: document.getElementById("item-dialog"),
		itemDialogForm: document.getElementById("item-dialog-form"),
		itemDialogClose: document.getElementById("item-dialog-close"),
		itemSearch: document.getElementById("item-search"),
		catalogSummary: document.getElementById("catalog-summary"),
		itemResults: document.getElementById("item-results"),
		toast: document.getElementById("toast")
	};

	const state = {
		data: null,
		activeKey: null,
		filter: "all",
		draft: emptyDraft(""),
		history: [],
		storageAvailable: true,
		newRowCounter: 0,
		toastTimer: null
	};

	function emptyDraft(fingerprint) {
		return {
			version: 1,
			sourceFingerprint: fingerprint,
			groups: {},
			changeNote: "",
			updatedAt: null
		};
	}

	function clone(value) {
		return JSON.parse(JSON.stringify(value));
	}

	function asArray(value) {
		return Array.isArray(value) ? value : [];
	}

	function text(value, fallback = "") {
		return typeof value === "string" && value.trim() ? value.trim() : fallback;
	}

	function integer(value, fallback = 0) {
		const number = Number(value);
		return Number.isInteger(number) ? number : fallback;
	}

	function optionalInteger(value) {
		if (value === "" || value === null || value === undefined) return null;
		const number = Number(value);
		return Number.isInteger(number) ? number : null;
	}

	function itemSprite(itemId, supplied) {
		return text(supplied, `assets/npc-database/item/${itemId}.png`);
	}

	function npcSprite(npcId, supplied) {
		return text(supplied, `assets/npc-database/npc/${npcId}.png`);
	}

	function sourceFingerprint(raw) {
		return text(raw?.source?.fingerprint || raw?.sourceFingerprint || raw?.baseFingerprint || raw?.fingerprint);
	}

	function normalizeSource(raw) {
		if (!raw || typeof raw !== "object") return { file: "", line: null };
		return {
			file: text(raw.file || raw.path),
			line: optionalInteger(raw.line)
		};
	}

	function normalizeNpc(raw) {
		const id = integer(raw?.id, -1);
		return {
			id,
			enum: text(raw?.enum),
			name: text(raw?.name, id >= 0 ? `NPC ${id}` : "NPC"),
			combatLevel: optionalInteger(raw?.combatLevel),
			sprite: npcSprite(id, raw?.sprite)
		};
	}

	function normalizeCatalogItem(raw) {
		const id = integer(raw?.id ?? raw?.itemId, -1);
		return {
			id,
			name: text(raw?.name || raw?.itemName, id >= 0 ? `Item ${id}` : "Unknown item"),
			sprite: itemSprite(id, raw?.sprite)
		};
	}

	function normalizeKind(value) {
		const kind = text(value, "item").toLowerCase();
		if (kind === "nothing" || kind === "empty") return "nothing";
		if (kind === "table" || kind === "nested") return "table";
		return "item";
	}

	function makeBaselineRowId(groupId, index) {
		return `${groupId}:row:${index + 1}`;
	}

	function normalizeNpcRow(raw, group, index, catalogById) {
		const kind = normalizeKind(raw?.kind || raw?.type);
		const itemId = kind === "item" ? integer(raw?.itemId ?? raw?.id, -1) : null;
		const catalogItem = itemId === null ? null : catalogById.get(itemId);
		const editable = Boolean(
			group.editable &&
			kind === "item" &&
			integer(raw?.weight, 0) > 0 &&
			(raw?.editable !== false)
		);
		return {
			rowId: text(raw?.rowId || raw?.id, makeBaselineRowId(group.groupId, index)),
			kind,
			editable,
			source: normalizeSource(raw?.source),
			derivedNothing: kind === "nothing" && Boolean(raw?.derived || raw?.derivedNothing),
			lockedReason: text(raw?.lockedReason),
			itemId,
			itemName: kind === "item"
				? text(raw?.itemName || raw?.name, catalogItem?.name || `Item ${itemId}`)
				: "",
			amount: kind === "item" ? integer(raw?.amount, 1) : null,
			weight: integer(raw?.weight, 0),
			noted: kind === "item" && Boolean(raw?.noted),
			sprite: kind === "item" ? itemSprite(itemId, raw?.sprite || catalogItem?.sprite) : "",
			targetGroupId: kind === "table" ? text(raw?.targetGroupId || raw?.tableId || raw?.target) : ""
		};
	}

	function normalizeGroup(raw, index, catalogById) {
		const groupId = text(raw?.groupId || raw?.id, `group-${index + 1}`);
		const scopeValue = text(raw?.scope, "direct").toLowerCase();
		const scope = ["direct", "nested", "shared"].includes(scopeValue) ? scopeValue : "shared";
		const denominator = integer(raw?.denominator || raw?.rollWeight || raw?.totalWeight, 0);
		const editable = raw?.editable === true && scope === "direct" && denominator > 0;
		const group = {
			key: `npc:${groupId}`,
			groupId,
			kind: "npc",
			title: text(raw?.title || raw?.name || raw?.label, groupId),
			scope,
			editable,
			source: normalizeSource(raw?.source),
			denominator,
			npcs: asArray(raw?.npcs || raw?.affectedNpcs).map(normalizeNpc).filter((npc) => npc.id >= 0),
			baselineRows: [],
			editableRows: [],
			lockedRows: []
		};
		group.baselineRows = asArray(raw?.rows).map((row, rowIndex) => normalizeNpcRow(row, group, rowIndex, catalogById));
		group.editableRows = group.baselineRows.filter((row) => row.editable).map(clone);
		group.lockedRows = group.baselineRows.filter((row) => !row.editable).map(clone);
		return group;
	}

	function normalizeVoidChest(raw, catalogById) {
		if (!raw || typeof raw !== "object") return null;
		const groupId = text(raw.groupId || raw.id, "void-chest");
		const rows = asArray(raw.rows).map((sourceRow, index) => {
			const itemId = integer(sourceRow?.itemId ?? sourceRow?.id, -1);
			const catalogItem = catalogById.get(itemId);
			return {
				rowId: text(sourceRow?.rowId || sourceRow?.id, makeBaselineRowId(groupId, index)),
				kind: "item",
				editable: true,
				source: normalizeSource(sourceRow?.source),
				itemId,
				itemName: text(sourceRow?.itemName || sourceRow?.name, catalogItem?.name || `Item ${itemId}`),
				minAmount: integer(sourceRow?.minAmount ?? sourceRow?.amount, 1),
				maxAmount: integer(sourceRow?.maxAmount ?? sourceRow?.amount, 1),
				weight: integer(sourceRow?.weight, 0),
				noted: Boolean(sourceRow?.noted),
				sprite: itemSprite(itemId, sourceRow?.sprite || catalogItem?.sprite)
			};
		});
		const suppliedTotal = integer(raw.totalWeight || raw.denominator, 0);
		return {
			key: `void:${groupId}`,
			groupId,
			kind: "void-chest",
			title: text(raw.title || raw.name, "Void Chest"),
			scope: "direct",
			editable: true,
			source: normalizeSource(raw.source),
			denominator: suppliedTotal > 0 ? suppliedTotal : sumWeights(rows),
			npcs: [],
			baselineRows: rows.map(clone),
			editableRows: rows.map(clone),
			lockedRows: []
		};
	}

	function normalizeData(raw) {
		if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
			throw new Error("The generated loot data is not a JSON object.");
		}
		if (integer(raw.schemaVersion, 0) !== 1) {
			throw new Error("This editor supports loot data schemaVersion 1.");
		}
		const fingerprint = sourceFingerprint(raw);
		if (!fingerprint) {
			throw new Error("The generated loot data is missing source.fingerprint.");
		}

		let items = asArray(raw.items || raw.itemCatalog).map(normalizeCatalogItem).filter((item) => item.id >= 0);
		const catalogById = new Map(items.map((item) => [item.id, item]));
		const groups = asArray(raw.groups || raw.npcGroups).map((group, index) => normalizeGroup(group, index, catalogById));
		const voidChest = normalizeVoidChest(raw.voidChest || raw.voidChestGroup, catalogById);
		if (voidChest) groups.unshift(voidChest);

		if (!items.length) {
			const derived = new Map();
			groups.forEach((group) => {
				group.baselineRows.forEach((row) => {
					if (row.kind === "item" && row.itemId >= 0 && !derived.has(row.itemId)) {
						derived.set(row.itemId, { id: row.itemId, name: row.itemName, sprite: row.sprite });
					}
				});
			});
			items = Array.from(derived.values());
		}

		items.sort((left, right) => left.name.localeCompare(right.name) || left.id - right.id);
		groups.sort((left, right) => {
			if (left.kind === "void-chest") return -1;
			if (right.kind === "void-chest") return 1;
			const scopeOrder = { direct: 0, nested: 1, shared: 2 };
			return scopeOrder[left.scope] - scopeOrder[right.scope] || left.title.localeCompare(right.title);
		});

		if (!groups.length) throw new Error("The generated loot data does not contain any tables.");
		return {
			schemaVersion: 1,
			fingerprint,
			generatedBy: text(raw?.source?.generatedBy || raw?.generatedBy, "Unknown generator"),
			files: asArray(raw?.source?.files),
			items,
			itemsById: new Map(items.map((item) => [item.id, item])),
			groups,
			groupsByKey: new Map(groups.map((group) => [group.key, group])),
			groupsById: new Map(groups.map((group) => [group.groupId, group]))
		};
	}

	function storageKey() {
		return `${STORAGE_NAMESPACE}:${state.data?.fingerprint || "unknown"}`;
	}

	function loadDraft() {
		state.draft = emptyDraft(state.data.fingerprint);
		try {
			const stored = localStorage.getItem(storageKey());
			if (!stored) return;
			const parsed = JSON.parse(stored);
			if (parsed?.version !== 1 || parsed?.sourceFingerprint !== state.data.fingerprint || typeof parsed.groups !== "object") {
				return;
			}
			state.draft.groups = {};
			Object.entries(parsed.groups).forEach(([key, groupDraft]) => {
				const group = state.data.groupsByKey.get(key);
				if (!group || !group.editable || !Array.isArray(groupDraft?.rows)) return;
				state.draft.groups[key] = { rows: normalizeDraftRows(group, groupDraft.rows), updatedAt: groupDraft.updatedAt || null };
			});
			state.draft.changeNote = text(parsed.changeNote);
			state.draft.updatedAt = parsed.updatedAt || null;
		} catch (error) {
			state.storageAvailable = false;
			showToast("The saved browser draft could not be read. A fresh draft is open.");
		}
	}

	function saveDraft() {
		state.draft.updatedAt = new Date().toISOString();
		try {
			localStorage.setItem(storageKey(), JSON.stringify(state.draft));
			state.storageAvailable = true;
		} catch (error) {
			state.storageAvailable = false;
		}
		updateSaveState();
	}

	function normalizeDraftRows(group, rows) {
		return asArray(rows).map((row, index) => {
			const itemId = integer(row?.itemId, -1);
			const catalogItem = state.data?.itemsById.get(itemId);
			const rowId = text(row?.rowId, makeBaselineRowId(group.groupId, index));
			const baselineRow = group.editableRows.find((candidate) => candidate.rowId === rowId);
			const normalized = {
				rowId,
				kind: "item",
				editable: true,
				source: normalizeSource(baselineRow?.source),
				itemId,
				itemName: catalogItem?.name || `Item ${itemId}`,
				weight: optionalInteger(row?.weight),
				noted: Boolean(row?.noted),
				sprite: itemSprite(itemId, catalogItem?.sprite)
			};
			if (group.kind === "void-chest") {
				normalized.minAmount = optionalInteger(row?.minAmount ?? row?.amount);
				normalized.maxAmount = optionalInteger(row?.maxAmount ?? row?.amount);
			} else {
				normalized.amount = optionalInteger(row?.amount);
			}
			return normalized;
		});
	}

	function canonicalRows(group, rows) {
		return rows.map((row) => {
			const base = {
				rowId: row.rowId,
				itemId: row.itemId,
				weight: row.weight,
				noted: Boolean(row.noted)
			};
			if (group.kind === "void-chest") {
				base.minAmount = row.minAmount;
				base.maxAmount = row.maxAmount;
			} else {
				base.amount = row.amount;
			}
			return base;
		});
	}

	function rowsEqual(group, left, right) {
		return JSON.stringify(canonicalRows(group, left)) === JSON.stringify(canonicalRows(group, right));
	}

	function currentRows(group) {
		return state.draft.groups[group.key]?.rows || group.editableRows;
	}

	function isGroupChanged(group) {
		return group.editable && !rowsEqual(group, group.editableRows, currentRows(group));
	}

	function changedGroups() {
		return state.data.groups.filter(isGroupChanged);
	}

	function pushHistory() {
		state.history.push(clone(state.draft.groups));
		if (state.history.length > MAX_HISTORY) state.history.shift();
	}

	function setGroupRows(group, rows, options = {}) {
		if (!group.editable) return;
		if (options.recordHistory !== false) pushHistory();
		const normalized = normalizeDraftRows(group, rows);
		if (rowsEqual(group, group.editableRows, normalized)) {
			delete state.draft.groups[group.key];
		} else {
			state.draft.groups[group.key] = { rows: normalized, updatedAt: new Date().toISOString() };
		}
		saveDraft();
		if (options.render === false) {
			renderValidation(group);
			renderLockedRows(group);
			updateDraftOverview();
			elements.undoButton.disabled = state.history.length === 0;
			elements.resetGroupButton.disabled = !isGroupChanged(group);
			return;
		}
		renderAll();
	}

	function activeGroup() {
		return state.data?.groupsByKey.get(state.activeKey) || null;
	}

	function sumWeights(rows) {
		return rows.reduce((total, row) => total + (Number.isInteger(row.weight) ? row.weight : 0), 0);
	}

	function lockedRollWeight(group) {
		return group.lockedRows.reduce((total, row) => {
			if (row.kind === "nothing" && row.derivedNothing) return total;
			return total + (Number.isInteger(row.weight) ? row.weight : 0);
		}, 0);
	}

	function groupMetrics(group, rows = currentRows(group)) {
		const editable = sumWeights(rows);
		const locked = group.kind === "void-chest" ? 0 : lockedRollWeight(group);
		return {
			editable,
			locked,
			remaining: group.denominator - editable - locked,
			denominator: group.denominator
		};
	}

	function validPositiveInteger(value) {
		return Number.isInteger(value) && value >= 1 && value <= MAX_INT;
	}

	function validItemId(value) {
		return Number.isInteger(value) && value >= 0 && value <= MAX_INT;
	}

	function validateGroup(group, rows = currentRows(group)) {
		if (!group.editable) return [];
		const errors = [];
		const rowIds = new Set();
		rows.forEach((row, index) => {
			const rowName = `Row ${index + 1}`;
			if (!row.rowId || rowIds.has(row.rowId)) errors.push(`${rowName} needs a unique row ID.`);
			rowIds.add(row.rowId);
			if (!validItemId(row.itemId)) errors.push(`${rowName} has an invalid item ID.`);
			if (!state.data.itemsById.has(row.itemId)) errors.push(`${rowName} uses item ${row.itemId}, which is not in the source catalog.`);
			if (!validPositiveInteger(row.weight)) errors.push(`${rowName} weight must be a whole number from 1 to ${MAX_INT}.`);
			if (group.kind === "void-chest") {
				if (!validPositiveInteger(row.minAmount)) errors.push(`${rowName} minimum amount must be a positive whole number.`);
				if (!validPositiveInteger(row.maxAmount)) errors.push(`${rowName} maximum amount must be a positive whole number.`);
				if (validPositiveInteger(row.minAmount) && validPositiveInteger(row.maxAmount) && row.minAmount > row.maxAmount) {
					errors.push(`${rowName} minimum amount cannot be greater than its maximum amount.`);
				}
			} else if (!validPositiveInteger(row.amount)) {
				errors.push(`${rowName} amount must be a positive whole number.`);
			}
		});
		if (!rows.length) errors.push("Add at least one item row.");

		const metrics = groupMetrics(group, rows);
		if (group.denominator < 1) {
			errors.push("The source table is missing a valid fixed denominator.");
		} else if (group.kind === "void-chest" && metrics.remaining !== 0) {
			errors.push(`Void Chest weights must total ${group.denominator}. They currently total ${metrics.editable}.`);
		} else if (group.kind !== "void-chest" && metrics.remaining < 0) {
			errors.push(`Item and locked weights exceed the fixed roll total by ${Math.abs(metrics.remaining)}.`);
		}
		return errors;
	}

	function allValidationErrors() {
		return changedGroups().flatMap((group) => validateGroup(group).map((message) => ({ group, message })));
	}

	function createElement(tag, className, content) {
		const element = document.createElement(tag);
		if (className) element.className = className;
		if (content !== undefined) element.textContent = content;
		return element;
	}

	function createArt(src, fallback, className) {
		const wrap = createElement("span", className);
		if (src) {
			const image = document.createElement("img");
			image.src = src;
			image.alt = "";
			image.loading = "lazy";
			image.addEventListener("error", () => {
				image.remove();
				if (!wrap.firstChild) wrap.append(createElement("span", "", fallback));
			}, { once: true });
			wrap.append(image);
		} else {
			wrap.append(createElement("span", "", fallback));
		}
		return wrap;
	}

	function fillArt(container, src, fallback) {
		container.replaceChildren();
		if (!src) {
			container.append(createElement("span", "", fallback));
			return;
		}
		const image = document.createElement("img");
		image.src = src;
		image.alt = "";
		image.addEventListener("error", () => {
			container.replaceChildren(createElement("span", "", fallback));
		}, { once: true });
		container.append(image);
	}

	function groupArt(group) {
		if (group.kind === "void-chest") return { src: "assets/bank-icon.png", fallback: "VC" };
		const npc = group.npcs[0];
		return { src: npc?.sprite || "", fallback: npc?.id >= 0 ? String(npc.id) : "NPC" };
	}

	function searchableGroupText(group) {
		return [
			group.title,
			group.groupId,
			group.scope,
			group.kind,
			...group.npcs.flatMap((npc) => [npc.id, npc.enum, npc.name])
		].join(" ").toLowerCase();
	}

	function visibleGroups() {
		const query = elements.tableSearch.value.trim().toLowerCase();
		return state.data.groups.filter((group) => {
			if (state.filter === "editable" && !group.editable) return false;
			if (state.filter === "locked" && group.editable) return false;
			return !query || searchableGroupText(group).includes(query);
		});
	}

	function renderTableList() {
		const groups = visibleGroups();
		elements.tableList.replaceChildren();
		groups.forEach((group) => {
			const button = createElement("button", "table-option");
			button.type = "button";
			button.dataset.groupKey = group.key;
			button.classList.toggle("is-active", group.key === state.activeKey);
			button.classList.toggle("has-change", isGroupChanged(group));
			button.setAttribute("aria-current", group.key === state.activeKey ? "true" : "false");
			const art = groupArt(group);
			button.append(createArt(art.src, art.fallback, "table-option-art"));

			const copy = createElement("span", "table-option-copy");
			copy.append(createElement("span", "table-option-title", group.title));
			let meta = group.kind === "void-chest" ? "Void Chest rewards" : `${group.npcs.length} NPC${group.npcs.length === 1 ? "" : "s"}`;
			if (group.kind !== "void-chest" && group.scope !== "direct") meta = `${group.scope} table`;
			copy.append(createElement("span", "table-option-meta", meta));
			button.append(copy);

			const status = createElement("span", `table-option-state${group.editable ? " editable" : ""}`, group.editable ? "Edit" : "Locked");
			button.append(status);
			button.addEventListener("click", () => selectGroup(group.key));
			elements.tableList.append(button);
		});

		if (!groups.length) {
			elements.tableList.append(createElement("p", "browser-empty", "No tables match this search and filter."));
		}
		elements.browserSummary.textContent = `${groups.length} of ${state.data.groups.length} tables shown`;
	}

	function selectGroup(key) {
		if (!state.data.groupsByKey.has(key)) return;
		state.activeKey = key;
		renderTableList();
		renderActiveGroup();
	}

	function renderActiveGroup() {
		const group = activeGroup();
		if (!group) return;
		const art = groupArt(group);
		fillArt(elements.groupArt, art.src, art.fallback);
		elements.groupKicker.textContent = group.kind === "void-chest"
			? "Void Chest"
			: group.scope === "direct"
				? "NPC table"
				: `${capitalize(group.scope)} table, reference only`;
		elements.groupTitle.textContent = group.title;
		elements.groupSource.textContent = sourceLabel(group.source);
		renderScopeNotice(group);
		renderAffectedNotice(group);
		renderWeightSummary(group);
		renderValidation(group);
		renderEditableRows(group);
		renderLockedRows(group);

		elements.undoButton.disabled = state.history.length === 0;
		elements.resetGroupButton.disabled = !isGroupChanged(group);
		elements.addItemButton.disabled = !group.editable;
		elements.addItemButton.hidden = !group.editable;
	}

	function capitalize(value) {
		return value.charAt(0).toUpperCase() + value.slice(1);
	}

	function sourceLabel(source) {
		if (!source?.file) return "Source location unavailable";
		return source.line ? `${source.file}, line ${source.line}` : source.file;
	}

	function renderScopeNotice(group) {
		elements.scopeNotice.replaceChildren();
		const strong = createElement("strong");
		if (group.kind === "void-chest") {
			strong.textContent = "Editable reward table. ";
			elements.scopeNotice.append(strong, document.createTextNode(`Change reward amounts, noted state, order, and weights. The total must stay ${group.denominator}.`));
		} else if (group.editable) {
			strong.textContent = "Direct item rows are editable. ";
			elements.scopeNotice.append(strong, document.createTextNode("Guaranteed drops, nested tables, shared tables, and the fixed roll total stay locked."));
		} else {
			strong.textContent = "Reference only. ";
			const reason = group.scope === "shared"
				? "This shared table can affect many NPCs and needs a separate review."
				: "This nested table is part of a larger roll and needs a separate review.";
			elements.scopeNotice.append(strong, document.createTextNode(reason));
		}
	}

	function renderAffectedNotice(group) {
		if (group.kind === "void-chest" || group.npcs.length < 2) {
			elements.affectedNotice.hidden = true;
			elements.affectedNotice.replaceChildren();
			return;
		}
		const names = group.npcs.map((npc) => `${npc.name} (${npc.id})`);
		const strong = createElement("strong", "", `This table affects ${group.npcs.length} NPCs. `);
		elements.affectedNotice.replaceChildren(strong, document.createTextNode(names.join(", ")));
		elements.affectedNotice.hidden = false;
	}

	function renderWeightSummary(group, metrics = groupMetrics(group)) {
		elements.editableWeight.textContent = String(metrics.editable);
		elements.lockedWeight.textContent = String(metrics.locked);
		elements.remainingWeight.textContent = String(metrics.remaining);
		elements.totalWeight.textContent = String(metrics.denominator);
		elements.remainderLabel.textContent = group.kind === "void-chest" ? "Unassigned weight" : "Remaining Nothing";
		if (group.kind === "void-chest") {
			elements.weightSummaryHelp.textContent = "Every point must be assigned to a reward row.";
			elements.weightStatus.textContent = `${metrics.editable} / ${metrics.denominator}`;
			elements.weightStatus.classList.toggle("is-error", metrics.remaining !== 0);
		} else {
			elements.weightSummaryHelp.textContent = "Unused weight becomes the Nothing outcome automatically.";
			elements.weightStatus.textContent = `${metrics.editable + metrics.locked} used of ${metrics.denominator}`;
			elements.weightStatus.classList.toggle("is-error", metrics.remaining < 0);
		}
	}

	function renderValidation(group) {
		const errors = validateGroup(group);
		elements.validationList.replaceChildren(...errors.map((message) => createElement("li", "", message)));
		elements.validationBox.hidden = errors.length === 0;
	}

	function createSprite(row) {
		return createArt(row.sprite || itemSprite(row.itemId), String(row.itemId), "item-sprite");
	}

	function createNumberField(group, row, field, label, accessibleRowName) {
		const wrapper = createElement("label", "field compact");
		wrapper.append(createElement("span", "", label));
		const input = document.createElement("input");
		input.type = "number";
		input.min = "1";
		input.max = String(MAX_INT);
		input.step = "1";
		input.inputMode = "numeric";
		input.value = row[field] ?? "";
		input.dataset.rowId = row.rowId;
		input.dataset.field = field;
		input.setAttribute("aria-label", `${accessibleRowName}, ${label.toLowerCase()}`);
		input.setAttribute("aria-invalid", String(!validPositiveInteger(row[field])));
		input.addEventListener("input", () => {
			const recordHistory = input.dataset.historyRecorded !== "true";
			const changed = updateRowField(group, row.rowId, field, optionalInteger(input.value), { recordHistory, render: false });
			if (changed) input.dataset.historyRecorded = "true";
			previewCurrentInputs(group);
			const currentRow = currentRows(group).find((candidate) => candidate.rowId === row.rowId);
			input.closest(".loot-row")?.classList.toggle("is-invalid", !currentRow || rowErrors(group, currentRow).length > 0);
		});
		input.addEventListener("blur", () => {
			delete input.dataset.historyRecorded;
			renderTableList();
		});
		wrapper.append(input);
		return wrapper;
	}

	function createNotedField(group, row, accessibleRowName) {
		const label = createElement("label", "noted-control");
		const checkbox = document.createElement("input");
		checkbox.type = "checkbox";
		checkbox.checked = Boolean(row.noted);
		checkbox.setAttribute("aria-label", `${accessibleRowName}, noted`);
		checkbox.addEventListener("change", () => updateRowField(group, row.rowId, "noted", checkbox.checked));
		label.append(checkbox, document.createTextNode("Noted"));
		return label;
	}

	function oddsValues(weight, denominator) {
		if (!validPositiveInteger(weight) || !validPositiveInteger(denominator)) return { percent: "Invalid", oneIn: "Check weight" };
		const percent = (weight / denominator) * 100;
		const oneIn = denominator / weight;
		return {
			percent: `${percent < 0.01 ? percent.toFixed(4) : percent < 1 ? percent.toFixed(2) : percent.toFixed(1)}%`,
			oneIn: `1/${oneIn < 10 ? oneIn.toFixed(2).replace(/\.00$/, "") : oneIn.toFixed(1).replace(/\.0$/, "")}`
		};
	}

	function createOdds(row, denominator) {
		const odds = oddsValues(row.weight, denominator);
		const block = createElement("div", "odds-block");
		block.dataset.oddsRowId = row.rowId;
		block.append(createElement("strong", "", odds.percent), createElement("span", "", odds.oneIn));
		return block;
	}

	function createRowActions(group, row, index, total, accessibleRowName) {
		const actions = createElement("div", "row-actions");
		const up = createElement("button", "row-action", "Move up");
		up.type = "button";
		up.disabled = index === 0;
		up.setAttribute("aria-label", `Move up ${accessibleRowName}`);
		up.addEventListener("click", () => moveRow(group, index, -1));
		const down = createElement("button", "row-action", "Move down");
		down.type = "button";
		down.disabled = index === total - 1;
		down.setAttribute("aria-label", `Move down ${accessibleRowName}`);
		down.addEventListener("click", () => moveRow(group, index, 1));
		const remove = createElement("button", "row-action remove", "Remove");
		remove.type = "button";
		remove.setAttribute("aria-label", `Remove ${accessibleRowName}`);
		remove.addEventListener("click", () => removeRow(group, row.rowId));
		actions.append(up, down, remove);
		return actions;
	}

	function renderEditableRows(group) {
		const rows = group.editable ? currentRows(group) : [];
		elements.editableRows.replaceChildren();
		elements.editableEmpty.hidden = rows.length > 0;
		elements.editableRowsHelp.textContent = group.kind === "void-chest"
			? "Change amount ranges, noted state, weight, or order."
			: "Change amounts, noted state, weight, or order.";
		rows.forEach((row, index) => {
			const card = createElement("article", "loot-row");
			const accessibleRowName = `${row.itemName}, item ${row.itemId}, row ${index + 1}`;
			card.dataset.rowId = row.rowId;
			card.setAttribute("aria-label", accessibleRowName);
			card.classList.toggle("is-invalid", rowErrors(group, row).length > 0);
			card.append(createSprite(row));
			const identity = createElement("div", "item-identity");
			identity.append(createElement("strong", "", row.itemName), createElement("small", "", `Item ${row.itemId}`));
			card.append(identity);

			if (group.kind === "void-chest") {
				const ranges = createElement("div", "range-fields");
				ranges.append(
					createNumberField(group, row, "minAmount", "Min amount", accessibleRowName),
					createNumberField(group, row, "maxAmount", "Max amount", accessibleRowName)
				);
				card.append(ranges);
			} else {
				card.append(createNumberField(group, row, "amount", "Amount", accessibleRowName));
			}

			card.append(
				createNumberField(group, row, "weight", "Weight", accessibleRowName),
				createNotedField(group, row, accessibleRowName),
				createOdds(row, group.denominator),
				createRowActions(group, row, index, rows.length, accessibleRowName)
			);
			elements.editableRows.append(card);
		});
	}

	function rowErrors(group, row) {
		const errors = [];
		if (!validItemId(row.itemId) || !state.data.itemsById.has(row.itemId)) errors.push("item");
		if (!validPositiveInteger(row.weight)) errors.push("weight");
		if (group.kind === "void-chest") {
			if (!validPositiveInteger(row.minAmount) || !validPositiveInteger(row.maxAmount) || row.minAmount > row.maxAmount) errors.push("amount");
		} else if (!validPositiveInteger(row.amount)) errors.push("amount");
		return errors;
	}

	function lockedReason(row, group) {
		if (row.lockedReason) return row.lockedReason;
		if (row.kind === "nothing") return row.derivedNothing
			? "Automatically fills the unused roll weight."
			: "This fixed Nothing outcome stays locked in this editor.";
		if (row.kind === "table") return "Nested or shared table logic is locked for a separate review.";
		if (row.kind === "item" && row.weight === 0) return "Guaranteed drops stay locked to protect game behavior.";
		if (!group.editable) return "This reference table is locked in this editor.";
		return "This row needs a separate review.";
	}

	function lockedRowTitle(row, group) {
		if (row.kind === "nothing") return "Nothing";
		if (row.kind === "table") return row.targetGroupId ? `Table: ${row.targetGroupId}` : "Nested table roll";
		const amount = row.amount && row.amount !== 1 ? ` x${row.amount}` : "";
		return `${row.itemName}${amount}${row.noted ? " noted" : ""}`;
	}

	function renderLockedRows(group) {
		const rows = group.lockedRows;
		elements.lockedRows.replaceChildren();
		elements.lockedSection.hidden = rows.length === 0;
		const metrics = groupMetrics(group);
		rows.forEach((row) => {
			const item = createElement("div", "locked-row");
			if (row.kind === "item") {
				item.append(createSprite(row));
			} else {
				item.append(createArt("", row.kind === "nothing" ? "N" : "T", "item-sprite"));
			}
			const copy = createElement("div", "locked-copy");
			copy.append(createElement("strong", "", lockedRowTitle(row, group)), createElement("span", "", lockedReason(row, group)));
			let weightLabel = row.weight === 0 && row.kind === "item" ? "Guaranteed" : `Weight ${row.weight}`;
			if (row.kind === "nothing" && row.derivedNothing) weightLabel = `Weight ${metrics.remaining}`;
			item.append(copy, createElement("span", "locked-weight", weightLabel));
			elements.lockedRows.append(item);
		});
	}

	function previewCurrentInputs(group) {
		const rows = currentRows(group).map(clone);
		elements.editableRows.querySelectorAll("input[data-row-id]").forEach((input) => {
			const row = rows.find((candidate) => candidate.rowId === input.dataset.rowId);
			if (!row) return;
			const value = optionalInteger(input.value);
			row[input.dataset.field] = value;
			input.setAttribute("aria-invalid", String(!validPositiveInteger(value)));
		});
		const metrics = groupMetrics(group, rows);
		renderWeightSummary(group, metrics);
		rows.forEach((row) => {
			const block = elements.editableRows.querySelector(`[data-odds-row-id="${cssEscape(row.rowId)}"]`);
			if (!block) return;
			const odds = oddsValues(row.weight, group.denominator);
			block.children[0].textContent = odds.percent;
			block.children[1].textContent = odds.oneIn;
		});
	}

	function cssEscape(value) {
		if (window.CSS?.escape) return window.CSS.escape(value);
		return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
	}

	function updateRowField(group, rowId, field, value, options = {}) {
		const rows = currentRows(group).map(clone);
		const row = rows.find((candidate) => candidate.rowId === rowId);
		if (!row || row[field] === value) {
			if (options.render !== false) renderActiveGroup();
			return false;
		}
		row[field] = value;
		setGroupRows(group, rows, options);
		return true;
	}

	function moveRow(group, index, direction) {
		const rows = currentRows(group).map(clone);
		const target = index + direction;
		if (target < 0 || target >= rows.length) return;
		[rows[index], rows[target]] = [rows[target], rows[index]];
		setGroupRows(group, rows);
		showToast("Item order updated. Undo is available.");
	}

	function removeRow(group, rowId) {
		const rows = currentRows(group).filter((row) => row.rowId !== rowId).map(clone);
		setGroupRows(group, rows);
		showToast("Item removed. Undo is available.");
	}

	function nextRowId(group) {
		state.newRowCounter += 1;
		const unique = window.crypto?.randomUUID
			? window.crypto.randomUUID()
			: `${Date.now().toString(36)}-${state.newRowCounter.toString(36)}`;
		return `${group.groupId}:new:${unique}`;
	}

	function openItemDialog() {
		const group = activeGroup();
		if (!group?.editable) return;
		elements.itemSearch.value = "";
		renderItemCatalog();
		if (typeof elements.itemDialog.showModal === "function") {
			elements.itemDialog.showModal();
			requestAnimationFrame(() => elements.itemSearch.focus());
		} else {
			showToast("This browser does not support the item picker dialog.");
		}
	}

	function renderItemCatalog() {
		const query = elements.itemSearch.value.trim().toLowerCase();
		const matches = state.data.items.filter((item) => !query || `${item.name} ${item.id}`.toLowerCase().includes(query));
		const shown = matches.slice(0, 80);
		elements.itemResults.replaceChildren();
		shown.forEach((item) => {
			const button = createElement("button", "catalog-item");
			button.type = "button";
			button.append(createArt(item.sprite, String(item.id), "item-sprite"));
			const copy = createElement("span", "catalog-item-copy");
			copy.append(createElement("strong", "", item.name), createElement("span", "", `Item ${item.id}`));
			button.append(copy, createElement("span", "catalog-item-action", "Add"));
			button.addEventListener("click", () => addCatalogItem(item));
			elements.itemResults.append(button);
		});
		if (!shown.length) elements.itemResults.append(createElement("p", "catalog-empty", "No existing items match this search."));
		elements.catalogSummary.textContent = matches.length > shown.length
			? `Showing ${shown.length} of ${matches.length} matching items. Refine your search to see more.`
			: `${matches.length} matching item${matches.length === 1 ? "" : "s"}`;
	}

	function addCatalogItem(item) {
		const group = activeGroup();
		if (!group?.editable) return;
		const row = {
			rowId: nextRowId(group),
			kind: "item",
			editable: true,
			itemId: item.id,
			itemName: item.name,
			weight: 1,
			noted: false,
			sprite: item.sprite
		};
		if (group.kind === "void-chest") {
			row.minAmount = 1;
			row.maxAmount = 1;
		} else {
			row.amount = 1;
		}
		setGroupRows(group, [...currentRows(group).map(clone), row]);
		elements.itemDialog.close();
		showToast(`${item.name} added with weight 1.`);
	}

	function updateDraftOverview() {
		const changed = changedGroups();
		const errors = allValidationErrors();
		elements.changedGroups.textContent = String(changed.length);
		elements.changeCount.textContent = String(changed.length);
		elements.changeCount.classList.toggle("has-changes", changed.length > 0);
		elements.errorCount.textContent = String(errors.length);
		elements.errorCount.classList.toggle("has-errors", errors.length > 0);
		elements.resetAllButton.disabled = changed.length === 0 && !state.draft.changeNote;
		elements.exportButton.disabled = changed.length === 0 || errors.length > 0;
	}

	function updateSaveState() {
		const count = state.data ? changedGroups().length : 0;
		elements.saveState.classList.toggle("has-changes", count > 0);
		if (!state.storageAvailable) {
			elements.saveState.textContent = "Browser autosave unavailable. Export before leaving.";
		} else if (count > 0) {
			elements.saveState.textContent = `${count} changed table${count === 1 ? "" : "s"} saved in this browser`;
		} else {
			elements.saveState.textContent = "No browser changes";
		}
	}

	function renderAll() {
		if (!state.data) return;
		renderTableList();
		renderActiveGroup();
		updateDraftOverview();
		updateSaveState();
	}

	function undo() {
		if (!state.history.length) return;
		state.draft.groups = state.history.pop();
		saveDraft();
		renderAll();
		showToast("Last table change undone.");
	}

	function resetActiveGroup() {
		const group = activeGroup();
		if (!group || !isGroupChanged(group)) return;
		if (!window.confirm(`Reset all browser changes for ${group.title}?`)) return;
		pushHistory();
		delete state.draft.groups[group.key];
		saveDraft();
		renderAll();
		showToast(`${group.title} reset to source data.`);
	}

	function resetAll() {
		if (!window.confirm("Reset every loot change and the optional note saved in this browser?")) return;
		pushHistory();
		state.draft.groups = {};
		state.draft.changeNote = "";
		elements.changeNote.value = "";
		saveDraft();
		renderAll();
		showToast("All browser changes were reset.");
	}

	function exportRow(group, row, index, rows) {
		const exported = {
			rowId: row.rowId,
			afterRowId: index === 0 ? null : rows[index - 1]?.rowId || null,
			itemId: row.itemId,
			itemName: row.itemName,
			weight: row.weight,
			noted: Boolean(row.noted)
		};
		if (row.source?.file) {
			exported.source = { file: row.source.file, line: row.source.line };
		}
		if (group.kind === "void-chest") {
			exported.minAmount = row.minAmount;
			exported.maxAmount = row.maxAmount;
		} else {
			exported.amount = row.amount;
		}
		return exported;
	}

	function exportRows(group, rows) {
		return rows.map((row, index) => exportRow(group, row, index, rows));
	}

	function exportNpc(npc) {
		return {
			id: npc.id,
			enum: npc.enum,
			name: npc.name,
			combatLevel: npc.combatLevel
		};
	}

	function createExportArtifact() {
		const changed = changedGroups();
		return {
			format: "voidscape-loot-change-request",
			schemaVersion: 1,
			reviewNotice: "This file is a review artifact for Codex. It is not server-ingestible configuration.",
			sourceFingerprint: state.data.fingerprint,
			exportedAt: new Date().toISOString(),
			changeNote: text(state.draft.changeNote) || null,
			changes: changed.map((group) => {
				const beforeRows = group.editableRows.map(clone);
				const afterRows = currentRows(group).map(clone);
				return {
					groupId: group.groupId,
					kind: group.kind,
					title: group.title,
					scope: group.scope,
					source: { file: group.source.file, line: group.source.line },
					denominator: group.denominator,
					affectedNpcs: group.npcs.map(exportNpc),
					before: {
						rows: exportRows(group, beforeRows),
						remainingNothingWeight: group.kind === "void-chest" ? null : groupMetrics(group, beforeRows).remaining
					},
					after: {
						rows: exportRows(group, afterRows),
						remainingNothingWeight: group.kind === "void-chest" ? null : groupMetrics(group, afterRows).remaining
					}
				};
			})
		};
	}

	function exportChanges() {
		const changed = changedGroups();
		const errors = allValidationErrors();
		if (!changed.length) {
			showToast("Make at least one loot change before exporting.");
			return;
		}
		if (errors.length) {
			showToast("Fix the validation errors before exporting.");
			return;
		}
		const artifact = createExportArtifact();
		const blob = new Blob([`${JSON.stringify(artifact, null, 2)}\n`], { type: "application/json" });
		const url = URL.createObjectURL(blob);
		const link = document.createElement("a");
		const date = new Date().toISOString().slice(0, 10);
		link.href = url;
		link.download = `voidscape-loot-changes-${date}.json`;
		document.body.append(link);
		link.click();
		link.remove();
		setTimeout(() => URL.revokeObjectURL(url), 0);
		showToast("Export ready. Attach the JSON file in Codex for review.");
	}

	function importRowsForGroup(group, rows) {
		const normalized = normalizeDraftRows(group, rows);
		const ids = new Set();
		normalized.forEach((row, index) => {
			if (!row.rowId || ids.has(row.rowId)) throw new Error(`${group.title} row ${index + 1} has a missing or duplicate rowId.`);
			ids.add(row.rowId);
		});
		return normalized;
	}

	function importArtifact(artifact) {
		if (!artifact || typeof artifact !== "object" || Array.isArray(artifact)) throw new Error("The selected file is not a loot change object.");
		if (artifact.format !== "voidscape-loot-change-request") throw new Error("This file is not a Voidscape loot change request export.");
		if (integer(artifact.schemaVersion, 0) !== 1) throw new Error("This editor supports change request schemaVersion 1.");
		if (text(artifact.sourceFingerprint) !== state.data.fingerprint) {
			throw new Error("This export was created from different loot source data. Regenerate or use the matching editor data first.");
		}
		const changes = asArray(artifact.changes);
		if (!changes.length) throw new Error("The export does not contain any changed tables.");
		if (changedGroups().length && !window.confirm("Replace the current browser loot changes with this imported export?")) return;
		const importedGroups = {};
		changes.forEach((change) => {
			const group = state.data.groupsById.get(text(change?.groupId));
			if (!group || !group.editable) throw new Error(`Table ${text(change?.groupId, "unknown")} is not editable in this source data.`);
			if (change.kind && change.kind !== group.kind) throw new Error(`${group.title} has a different table kind in this source data.`);
			const rows = importRowsForGroup(group, change?.after?.rows || change?.afterRows);
			if (rowsEqual(group, group.editableRows, rows)) {
				delete importedGroups[group.key];
			} else {
				importedGroups[group.key] = { rows, updatedAt: new Date().toISOString() };
			}
		});
		pushHistory();
		state.draft.groups = importedGroups;
		state.draft.changeNote = text(artifact.changeNote);
		elements.changeNote.value = state.draft.changeNote;
		saveDraft();
		renderAll();
		showToast(`${changes.length} changed table${changes.length === 1 ? "" : "s"} imported.`);
	}

	async function handleImportFile(file) {
		if (!file) return;
		if (file.size > MAX_IMPORT_BYTES) {
			showToast("The selected JSON file is too large.");
			return;
		}
		try {
			const artifact = JSON.parse(await file.text());
			importArtifact(artifact);
		} catch (error) {
			showToast(error instanceof SyntaxError ? "The selected file is not valid JSON." : error.message);
		} finally {
			elements.importFile.value = "";
		}
	}

	function showToast(message) {
		clearTimeout(state.toastTimer);
		elements.toast.textContent = message;
		elements.toast.hidden = false;
		state.toastTimer = setTimeout(() => {
			elements.toast.hidden = true;
		}, 4200);
	}

	function bindEvents() {
		elements.retryLoad.addEventListener("click", loadData);
		elements.tableSearch.addEventListener("input", renderTableList);
		elements.filterButtons.forEach((button) => {
			button.addEventListener("click", () => {
				state.filter = button.dataset.filter;
				elements.filterButtons.forEach((candidate) => {
					const active = candidate === button;
					candidate.classList.toggle("is-active", active);
					candidate.setAttribute("aria-pressed", String(active));
				});
				renderTableList();
			});
		});
		elements.undoButton.addEventListener("click", undo);
		elements.resetGroupButton.addEventListener("click", resetActiveGroup);
		elements.addItemButton.addEventListener("click", openItemDialog);
		elements.itemDialogForm.addEventListener("submit", (event) => event.preventDefault());
		elements.itemDialogClose.addEventListener("click", () => elements.itemDialog.close());
		elements.itemSearch.addEventListener("input", renderItemCatalog);
		elements.changeNote.addEventListener("input", () => {
			state.draft.changeNote = elements.changeNote.value.slice(0, 500);
			saveDraft();
			updateDraftOverview();
		});
		elements.exportButton.addEventListener("click", exportChanges);
		elements.importButton.addEventListener("click", () => elements.importFile.click());
		elements.importFile.addEventListener("change", () => handleImportFile(elements.importFile.files?.[0]));
		elements.resetAllButton.addEventListener("click", resetAll);
	}

	async function loadData() {
		elements.loadError.hidden = true;
		elements.shell.setAttribute("aria-busy", "true");
		elements.loading.hidden = false;
		elements.content.hidden = true;
		elements.saveState.textContent = "Loading source data";
		try {
			const response = await fetch(DATA_URL, { cache: "no-store", credentials: "same-origin" });
			if (!response.ok) throw new Error(`Source data request returned HTTP ${response.status}.`);
			state.data = normalizeData(await response.json());
			state.history = [];
			state.activeKey = state.data.groups.find((group) => group.editable)?.key || state.data.groups[0].key;
			loadDraft();
			elements.changeNote.value = state.draft.changeNote;
			elements.sourceFingerprint.textContent = state.data.fingerprint;
			elements.sourceGenerator.textContent = state.data.generatedBy;
			elements.loading.hidden = true;
			elements.content.hidden = false;
			elements.shell.setAttribute("aria-busy", "false");
			renderAll();
		} catch (error) {
			state.data = null;
			elements.loading.hidden = true;
			elements.content.hidden = true;
			elements.shell.setAttribute("aria-busy", "false");
			elements.loadErrorMessage.textContent = error?.message || "Check that the generated data file is available, then try again.";
			elements.loadError.hidden = false;
			elements.saveState.textContent = "Source data unavailable";
		}
	}

	bindEvents();
	loadData();
})();
