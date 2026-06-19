const stateEls = {
  clientVersion: document.querySelector("#client-version"),
  nextItem: document.querySelector("#next-item"),
  nextNpc: document.querySelector("#next-npc"),
  validationSummary: document.querySelector("#validation-summary"),
  allocation: document.querySelector("#allocation"),
  findings: document.querySelector("#findings"),
  packs: document.querySelector("#packs"),
  commands: document.querySelector("#commands"),
  kind: document.querySelector("#kind"),
  message: document.querySelector("#form-message"),
  refresh: document.querySelector("#refresh"),
  form: document.querySelector("#pack-form"),
};

let latestState = null;

function text(value) {
  return value === null || value === undefined || value === "" ? "none" : String(value);
}

function setBusy(busy) {
  stateEls.refresh.disabled = busy;
  stateEls.form.querySelector("button[type='submit']").disabled = busy;
}

function setMessage(message, isError = false) {
  stateEls.message.textContent = message || "";
  stateEls.message.classList.toggle("error", isError);
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  const payload = await response.json();
  if (!response.ok || !payload.ok) {
    throw new Error(payload.error || `Request failed: ${response.status}`);
  }
  return payload;
}

function renderKinds(kinds) {
  const previous = stateEls.kind.value;
  stateEls.kind.innerHTML = "";
  for (const kind of kinds) {
    const option = document.createElement("option");
    option.value = kind;
    option.textContent = kind;
    stateEls.kind.append(option);
  }
  if (previous) {
    stateEls.kind.value = previous;
  }
}

function renderAllocation(allocation) {
  const rows = [
    ["Server item range", `${allocation.items.serverRange} (${allocation.items.serverCount})`],
    ["Client item range", `${allocation.items.clientRange} (${allocation.items.clientCount})`],
    ["Next item ID", allocation.items.nextId],
    ["Next spriteID", allocation.items.nextSpriteId],
    ["Next archive index", allocation.items.nextArchiveIndex],
    ["Skipped archive slots", allocation.items.skippedArchiveSlots.join(", ") || "none"],
    ["Server NPC range", `${allocation.npcs.serverRange} (${allocation.npcs.serverCount})`],
    ["Client NPC count", allocation.npcs.clientCount],
    ["Next NPC ID", allocation.npcs.nextId],
  ];
  stateEls.allocation.innerHTML = "";
  for (const [label, value] of rows) {
    const dt = document.createElement("dt");
    dt.textContent = label;
    const dd = document.createElement("dd");
    dd.textContent = text(value);
    stateEls.allocation.append(dt, dd);
  }
}

function renderFindings(validation) {
  stateEls.findings.innerHTML = "";
  if (!validation.findings.length) {
    const empty = document.createElement("p");
    empty.textContent = "No validation findings.";
    stateEls.findings.append(empty);
    return;
  }
  for (const finding of validation.findings) {
    const item = document.createElement("div");
    item.className = `finding ${finding.severity.toLowerCase()}`;
    const title = document.createElement("strong");
    title.textContent = `${finding.severity}: ${finding.code}`;
    const body = document.createElement("p");
    body.textContent = finding.detail ? `${finding.message} (${finding.detail})` : finding.message;
    item.append(title, body);
    stateEls.findings.append(item);
  }
}

function renderPacks(packs) {
  stateEls.packs.innerHTML = "";
  if (!packs.length) {
    const empty = document.createElement("p");
    empty.textContent = "No content packs yet.";
    stateEls.packs.append(empty);
    return;
  }
  for (const pack of packs) {
    const item = document.createElement("div");
    item.className = "pack";
    const title = document.createElement("strong");
    title.textContent = pack.name || pack.slug;
    const body = document.createElement("p");
    body.textContent = pack.description || pack.slug;
    const meta = document.createElement("div");
    meta.className = "pack-meta";
    meta.textContent = [pack.kind, pack.status].filter(Boolean).join(" / ") || "draft";
    item.append(title, body, meta);
    stateEls.packs.append(item);
  }
}

function renderCommands(commands) {
  stateEls.commands.innerHTML = "";
  for (const command of commands) {
    const item = document.createElement("li");
    item.textContent = command;
    stateEls.commands.append(item);
  }
}

function renderState(state) {
  latestState = state;
  stateEls.clientVersion.textContent = text(state.allocation.clientVersion);
  stateEls.nextItem.textContent = text(state.allocation.items.nextId);
  stateEls.nextNpc.textContent = text(state.allocation.npcs.nextId);
  stateEls.validationSummary.textContent = `${state.validation.errors} errors / ${state.validation.warnings} warnings`;
  renderKinds(state.validKinds);
  renderAllocation(state.allocation);
  renderFindings(state.validation);
  renderPacks(state.packs);
  renderCommands(state.commands);
}

async function loadState() {
  setBusy(true);
  try {
    const payload = await fetchJson("/api/state");
    renderState(payload.state);
    setMessage(latestState ? "Refreshed." : "");
  } catch (error) {
    setMessage(error.message, true);
  } finally {
    setBusy(false);
  }
}

async function createPack(event) {
  event.preventDefault();
  setBusy(true);
  setMessage("Creating pack...");
  const formData = new FormData(stateEls.form);
  const payload = {
    kind: formData.get("kind"),
    slug: formData.get("slug"),
    name: formData.get("name"),
    like: formData.get("like"),
    description: formData.get("description"),
    force: formData.get("force") === "on",
  };
  try {
    const response = await fetchJson("/api/packs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    renderState(response.state);
    setMessage(`Created ${response.created}`);
    stateEls.form.reset();
  } catch (error) {
    setMessage(error.message, true);
  } finally {
    setBusy(false);
  }
}

stateEls.refresh.addEventListener("click", loadState);
stateEls.form.addEventListener("submit", createPack);
loadState();
