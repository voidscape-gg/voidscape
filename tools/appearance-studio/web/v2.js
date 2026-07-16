"use strict";

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const makeCanvas = (width, height) => {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  context.imageSmoothingEnabled = false;
  return canvas;
};

const ui = {
  base: $("#baseCanvas"), paint: $("#paintCanvas"), overlay: $("#overlayCanvas"),
  preview: $("#previewCanvas"), stack: $("#canvasStack"),
};
const bx = ui.base.getContext("2d");
const px = ui.paint.getContext("2d", { willReadFrequently: true });
const ox = ui.overlay.getContext("2d", { willReadFrequently: true });
const vx = ui.preview.getContext("2d", { willReadFrequently: true });
[bx, px, ox, vx].forEach((context) => { context.imageSmoothingEnabled = false; });

const app = {
  state: null,
  poseId: null,
  layerKey: null,
  stackId: null,
  visibleAssets: new Set(),
  tool: "paint",
  gray: 128,
  alpha: 255,
  zoom: 4,
  phase: 0,
  previewScale: 2,
  documents: new Map(),
  images: new Map(),
  stamp: null,
  pointer: null,
  renderEpoch: 0,
};

function status(message, error = false) {
  $("#status").textContent = message;
  $("#status").classList.toggle("error", error);
}

function currentPose() {
  return app.state.poses.find((pose) => pose.id === app.poseId);
}

function layers() {
  const result = [];
  for (const asset of app.state.assets) {
    for (const channel of asset.channels) {
      if (channel.masters) {
        result.push({
          key: `${asset.id}/${channel.id}`,
          asset, channel,
          label: `${asset.label} · ${channel.label}`,
        });
      }
    }
  }
  return result;
}

function currentLayer() {
  return layers().find((layer) => layer.key === app.layerKey);
}

function currentFrame() {
  const pose = currentPose();
  const offset = pose.frameOffsets[app.phase];
  return app.state.editor.frames.find((frame) => frame.offset === offset);
}

function image(url, bust = "") {
  const key = url + bust;
  if (!app.images.has(key)) {
    app.images.set(key, new Promise((resolve, reject) => {
      const element = new Image();
      element.onload = () => resolve(element);
      element.onerror = () => reject(new Error(`Could not load ${url}`));
      element.src = `${url}${url.includes("?") ? "&" : "?"}v=${encodeURIComponent(bust || "0")}`;
    }));
  }
  return app.images.get(key);
}

function documentKey(layer = currentLayer(), poseId = app.poseId) {
  return `${layer.key}/${poseId}`;
}

async function getDocument(layer = currentLayer(), poseId = app.poseId) {
  const key = documentKey(layer, poseId);
  if (app.documents.has(key)) return app.documents.get(key);
  const pose = app.state.poses.find((item) => item.id === poseId);
  const [width, height] = pose.canvas;
  const canvas = makeCanvas(width, height);
  const source = await image(layer.channel.masterUrls[poseId]);
  canvas.getContext("2d").drawImage(source, 0, 0);
  const doc = { key, layer, poseId, canvas, undo: [], redo: [], dirty: false, revision: 0 };
  app.documents.set(key, doc);
  return doc;
}

function clonePixels(doc) {
  return doc.canvas.getContext("2d").getImageData(0, 0, doc.canvas.width, doc.canvas.height);
}

function pushUndo(doc) {
  doc.undo.push(clonePixels(doc));
  if (doc.undo.length > 64) doc.undo.shift();
  doc.redo.length = 0;
  updateHistoryButtons(doc);
}

function markDirty(doc) {
  doc.dirty = true;
  const badge = $("#dirtyBadge");
  badge.textContent = "Unsaved";
  badge.className = "dirty-badge dirty";
  $("#save").disabled = !doc.layer.channel.editable;
}

function updateHistoryButtons(doc) {
  $("#undo").disabled = !doc.undo.length;
  $("#redo").disabled = !doc.redo.length;
}

function setTool(tool) {
  app.tool = tool;
  $$('[data-tool]').forEach((button) => button.classList.toggle("active", button.dataset.tool === tool));
  status(`${tool[0].toUpperCase()}${tool.slice(1)} tool selected.`);
}

function setGray(value) {
  app.gray = Math.max(0, Math.min(255, Number(value)));
  $("#grayRange").value = app.gray;
  $("#grayValue").value = app.gray;
  $$("#graySwatches .swatch").forEach((button) => button.classList.toggle("active", Number(button.dataset.value) === app.gray));
}

function setAlpha(value) {
  app.alpha = Math.max(0, Math.min(255, Number(value)));
  $("#alphaRange").value = app.alpha;
  $("#alphaValue").value = app.alpha;
  $$("#alphaSwatches .swatch").forEach((button) => button.classList.toggle("active", Number(button.dataset.value) === app.alpha));
}

function buildSwatches() {
  const grayRoot = $("#graySwatches");
  grayRoot.replaceChildren();
  for (const value of app.state.editor.grayscaleSwatches) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "swatch";
    button.dataset.value = value;
    button.title = `Grayscale ${value}`;
    button.style.background = `rgb(${value} ${value} ${value})`;
    button.addEventListener("click", () => setGray(value));
    grayRoot.append(button);
  }
  const alphaRoot = $("#alphaSwatches");
  alphaRoot.replaceChildren();
  for (const value of app.state.editor.alphaSwatches) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "swatch";
    button.dataset.value = value;
    button.title = `Alpha ${value}`;
    button.style.setProperty("--alpha", value / 255);
    button.addEventListener("click", () => setAlpha(value));
    alphaRoot.append(button);
  }
  setGray(app.gray);
  setAlpha(app.alpha);
}

function buildPoseList() {
  const root = $("#poseList");
  root.replaceChildren();
  app.state.poses.forEach((pose, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.pose = pose.id;
    button.innerHTML = `<span>${String(index + 1).padStart(2, "0")}</span><span>${pose.label}<small>${pose.visualPose}</small></span>`;
    button.addEventListener("click", () => selectPose(pose.id));
    root.append(button);
  });
}

function buildLayerSelect() {
  const select = $("#channelSelect");
  select.replaceChildren();
  const byAsset = new Map();
  for (const layer of layers()) {
    if (!byAsset.has(layer.asset.id)) {
      const group = document.createElement("optgroup");
      group.label = layer.asset.label;
      byAsset.set(layer.asset.id, group);
      select.append(group);
    }
    const option = document.createElement("option");
    option.value = layer.key;
    option.textContent = `${layer.channel.label} · ${layer.channel.tintRole}`;
    byAsset.get(layer.asset.id).append(option);
  }
  select.addEventListener("change", () => selectLayer(select.value));
}

function buildStackSelect() {
  const select = $("#stackSelect");
  select.replaceChildren();
  for (const stack of app.state.renderStacks) {
    const option = document.createElement("option");
    option.value = stack.id;
    option.textContent = stack.label;
    select.append(option);
  }
  select.addEventListener("change", () => selectStack(select.value));
}

function selectStack(stackId) {
  app.stackId = stackId;
  $("#stackSelect").value = stackId;
  const stack = app.state.renderStacks.find((item) => item.id === stackId);
  app.visibleAssets = new Set(stack.assets);
  const root = $("#stackLayers");
  root.replaceChildren();
  for (const assetId of stack.assets) {
    const asset = app.state.assets.find((item) => item.id === assetId);
    const label = document.createElement("label");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = true;
    checkbox.addEventListener("change", () => {
      checkbox.checked ? app.visibleAssets.add(assetId) : app.visibleAssets.delete(assetId);
      renderPreview();
    });
    label.append(checkbox, document.createTextNode(asset.label));
    root.append(label);
  }
  renderPreview();
}

async function selectPose(poseId) {
  app.poseId = poseId;
  app.phase = 0;
  app.stamp = null;
  $("#stampControls").hidden = true;
  $$("#poseList button").forEach((button) => button.classList.toggle("active", button.dataset.pose === poseId));
  $("#poseCounter").textContent = `${app.state.poses.findIndex((pose) => pose.id === poseId) + 1} / 6`;
  rebuildMaskSelect();
  updatePhaseButtons();
  await render();
}

async function selectLayer(layerKey) {
  app.layerKey = layerKey;
  app.stamp = null;
  $("#stampControls").hidden = true;
  $("#channelSelect").value = layerKey;
  const layer = currentLayer();
  $("#channelMeta").textContent = `${layer.asset.kind} · ${layer.channel.tintRole} tint · ${layer.asset.sourceMode} · ${layer.channel.editable ? "editable" : "read-only"}`;
  await render();
}

function rebuildMaskSelect() {
  const select = $("#maskSelect");
  const previous = select.value;
  select.replaceChildren(new Option("None", ""));
  const masks = currentPose().guideUrls.masks;
  for (const [role, url] of Object.entries(masks)) {
    if (!url) continue;
    const option = new Option(role.replaceAll("_", " "), role);
    select.append(option);
  }
  select.value = Object.hasOwn(masks, previous) && masks[previous] ? previous : "";
  const legend = $("#anchorLegend");
  legend.replaceChildren();
  for (const [name, anchor] of Object.entries(currentPose().anchors)) {
    if (!anchor) continue;
    const row = document.createElement("span");
    const label = document.createElement("b");
    label.textContent = name.replaceAll("_", " ");
    row.append(label, document.createTextNode(`${anchor.absolute[0]},${anchor.absolute[1]}`));
    legend.append(row);
  }
}

function updatePhaseButtons() {
  $$("#phaseButtons button").forEach((button) => button.classList.toggle("active", Number(button.dataset.phase) === app.phase));
  const frame = currentFrame();
  $("#frameLabel").textContent = `frame ${frame.key}`;
}

function resizeEditorCanvases(width, height) {
  for (const canvas of [ui.base, ui.paint, ui.overlay]) {
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    canvas.getContext("2d").imageSmoothingEnabled = false;
  }
  ui.stack.style.width = `${width * app.zoom}px`;
  ui.stack.style.height = `${height * app.zoom}px`;
}

function phaseDelta() {
  return $("#showDerived").checked ? currentFrame().crownDelta : [0, 0];
}

async function render() {
  if (!app.state || !app.poseId || !app.layerKey) return;
  const epoch = ++app.renderEpoch;
  try {
    const pose = currentPose();
    const doc = await getDocument();
    const baseGuide = await image(pose.guideUrls.base);
    if (epoch !== app.renderEpoch) return;
    const [width, height] = pose.canvas;
    resizeEditorCanvases(width, height);
    bx.clearRect(0, 0, width, height);
    if ($("#showBase").checked) bx.drawImage(baseGuide, 0, 0);
    px.clearRect(0, 0, width, height);
    const [dx, dy] = phaseDelta();
    px.drawImage(doc.canvas, dx, dy);
    await renderOverlay(epoch);
    updateHistoryButtons(doc);
    const layer = currentLayer();
    $("#canvasTitle").textContent = `${layer.asset.label} · ${layer.channel.label}`;
    $("#canvasMeta").textContent = `${pose.canvas[0]}×${pose.canvas[1]} native canvas · ${pose.visualPose} · frame ${currentFrame().key}`;
    const badge = $("#dirtyBadge");
    badge.textContent = doc.dirty ? "Unsaved" : "Saved";
    badge.className = `dirty-badge ${doc.dirty ? "dirty" : "clean"}`;
    $("#save").disabled = !doc.dirty || !layer.channel.editable;
    renderPreview();
  } catch (error) {
    status(error.message, true);
  }
}

async function renderOverlay(epoch = app.renderEpoch) {
  const pose = currentPose();
  ox.clearRect(0, 0, ui.overlay.width, ui.overlay.height);
  const maskRole = $("#maskSelect").value;
  if (maskRole) {
    const mask = await image(pose.guideUrls.masks[maskRole]);
    if (epoch !== app.renderEpoch) return;
    const temp = makeCanvas(mask.width, mask.height);
    const context = temp.getContext("2d", { willReadFrequently: true });
    context.drawImage(mask, 0, 0);
    const pixels = context.getImageData(0, 0, temp.width, temp.height);
    for (let index = 0; index < pixels.data.length; index += 4) {
      const enabled = pixels.data[index] > 127;
      pixels.data[index] = 255;
      pixels.data[index + 1] = 61;
      pixels.data[index + 2] = 107;
      pixels.data[index + 3] = enabled ? Math.round(255 * Number($("#maskOpacity").value) / 100) : 0;
    }
    context.putImageData(pixels, 0, 0);
    ox.drawImage(temp, 0, 0);
  }
  if ($("#showAnchors").checked) {
    for (const anchor of Object.values(pose.anchors)) {
      if (!anchor) continue;
      const [x, y] = anchor.absolute;
      ox.fillStyle = "#68fff3";
      ox.fillRect(x - 1, y, 3, 1);
      ox.fillRect(x, y - 1, 1, 3);
    }
  }
  if ($("#showCrown").checked) {
    const [x, y] = pose.crown;
    ox.fillStyle = "#ffd676";
    ox.fillRect(x - 4, y, 9, 1);
    ox.fillRect(x, y - 4, 1, 9);
  }
  drawStamp(ox);
}

function drawStamp(context) {
  if (!app.stamp) return;
  const stamp = app.stamp;
  context.save();
  context.imageSmoothingEnabled = false;
  context.globalAlpha = .82;
  if (stamp.mirrored) {
    context.translate(stamp.x + stamp.width, stamp.y);
    context.scale(-1, 1);
    context.drawImage(stamp.image, 0, 0, stamp.width, stamp.height);
  } else {
    context.drawImage(stamp.image, stamp.x, stamp.y, stamp.width, stamp.height);
  }
  context.restore();
  context.strokeStyle = "#fff17a";
  context.lineWidth = 1;
  context.setLineDash([2, 2]);
  context.strokeRect(stamp.x + .5, stamp.y + .5, stamp.width, stamp.height);
  context.setLineDash([]);
}

function tintCanvas(source, role) {
  const output = makeCanvas(source.width, source.height);
  const context = output.getContext("2d", { willReadFrequently: true });
  context.drawImage(source, 0, 0);
  if (role === "fixed") return output;
  const tintValue = app.state.palette.defaultRgb[role];
  const tint = [(tintValue >> 16) & 255, (tintValue >> 8) & 255, tintValue & 255];
  const pixels = context.getImageData(0, 0, output.width, output.height);
  for (let index = 0; index < pixels.data.length; index += 4) {
    const shade = pixels.data[index];
    pixels.data[index] = shade * tint[0] >> 8;
    pixels.data[index + 1] = shade * tint[1] >> 8;
    pixels.data[index + 2] = shade * tint[2] >> 8;
  }
  context.putImageData(pixels, 0, 0);
  return output;
}

async function previewSource(asset, channel, pose, frame) {
  if (channel.masters) {
    const layer = layers().find((item) => item.asset.id === asset.id && item.channel.id === channel.id);
    return (await getDocument(layer, pose.id)).canvas;
  }
  if (channel.frames) return image(channel.frameUrls[frame.key]);
  throw new Error(`No preview source for ${asset.id}.${channel.id}`);
}

async function renderPreview() {
  if (!app.state || !app.poseId || !app.stackId) return;
  const epoch = app.renderEpoch;
  const pose = currentPose();
  const frame = currentFrame();
  const stack = app.state.renderStacks.find((item) => item.id === app.stackId);
  try {
    const poseCanvas = makeCanvas(...pose.canvas);
    const context = poseCanvas.getContext("2d");
    for (const assetId of stack.assets) {
      if (!app.visibleAssets.has(assetId)) continue;
      const asset = app.state.assets.find((item) => item.id === assetId);
      for (const channel of asset.channels) {
        const source = await previewSource(asset, channel, pose, frame);
        if (epoch !== app.renderEpoch) return;
        const tinted = tintCanvas(source, channel.tintRole);
        const [dx, dy] = asset.propagation === "rigid-head" ? frame.crownDelta : [0, 0];
        context.drawImage(tinted, dx, dy);
      }
    }
    if (epoch !== app.renderEpoch) return;
    const preview = app.state.preview;
    const native = makeCanvas(preview.width, preview.height);
    const nx = preview.drawX - Math.floor((pose.canvas[0] - preview.drawWidth) / 2);
    native.getContext("2d").drawImage(poseCanvas, nx, preview.drawY);
    const divisor = app.previewScale === 1 ? 2 : 1;
    ui.preview.width = preview.width / divisor;
    ui.preview.height = preview.height / divisor;
    vx.imageSmoothingEnabled = false;
    vx.clearRect(0, 0, ui.preview.width, ui.preview.height);
    vx.drawImage(native, 0, 0, ui.preview.width, ui.preview.height);
    ui.preview.style.width = `${ui.preview.width}px`;
    ui.preview.style.height = `${ui.preview.height}px`;
  } catch (error) {
    status(error.message, true);
  }
}

function pointerCoordinates(event) {
  const rect = ui.overlay.getBoundingClientRect();
  return {
    x: Math.floor((event.clientX - rect.left) * ui.overlay.width / rect.width),
    y: Math.floor((event.clientY - rect.top) * ui.overlay.height / rect.height),
  };
}

function showPixel(x, y) {
  $("#cursorPosition").textContent = `x ${x} · y ${y}`;
  if (x < 0 || y < 0 || x >= ui.paint.width || y >= ui.paint.height) return;
  const rgba = px.getImageData(x, y, 1, 1).data;
  $("#pixelValue").textContent = `rgba ${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3]}`;
}

function paintPoint(doc, x, y) {
  const context = doc.canvas.getContext("2d");
  const size = Math.max(1, Math.min(16, Number($("#brushSize").value)));
  const left = x - Math.floor(size / 2);
  const top = y - Math.floor(size / 2);
  if (app.tool === "erase") {
    context.clearRect(left, top, size, size);
  } else {
    context.save();
    context.globalCompositeOperation = "copy";
    context.fillStyle = `rgba(${app.gray},${app.gray},${app.gray},${app.alpha / 255})`;
    context.fillRect(left, top, size, size);
    context.restore();
  }
}

function line(doc, from, to) {
  let x0 = from.x, y0 = from.y;
  const x1 = to.x, y1 = to.y;
  const dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
  const dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
  let error = dx + dy;
  while (true) {
    paintPoint(doc, x0, y0);
    if (x0 === x1 && y0 === y1) break;
    const doubled = 2 * error;
    if (doubled >= dy) { error += dy; x0 += sx; }
    if (doubled <= dx) { error += dx; y0 += sy; }
  }
}

async function beginPointer(event) {
  if ($("#showDerived").checked) {
    return status("Derived phase inspection is read-only. Uncheck it to paint the master.", true);
  }
  const point = pointerCoordinates(event);
  showPixel(point.x, point.y);
  if (app.stamp) {
    const inside = point.x >= app.stamp.x && point.y >= app.stamp.y && point.x < app.stamp.x + app.stamp.width && point.y < app.stamp.y + app.stamp.height;
    if (inside) {
      app.pointer = { mode: "stamp", dx: point.x - app.stamp.x, dy: point.y - app.stamp.y };
      ui.overlay.setPointerCapture(event.pointerId);
    }
    return;
  }
  const doc = await getDocument();
  if (app.tool === "pick") {
    const rgba = doc.canvas.getContext("2d").getImageData(point.x, point.y, 1, 1).data;
    setGray(Math.round((rgba[0] + rgba[1] + rgba[2]) / 3));
    setAlpha(rgba[3]);
    status(`Picked grayscale ${app.gray}, alpha ${app.alpha}.`);
    return;
  }
  pushUndo(doc);
  app.pointer = { mode: "paint", last: point, doc };
  paintPoint(doc, point.x, point.y);
  markDirty(doc);
  ui.overlay.setPointerCapture(event.pointerId);
  render();
}

function movePointer(event) {
  const point = pointerCoordinates(event);
  showPixel(point.x, point.y);
  if (!app.pointer) return;
  if (app.pointer.mode === "stamp") {
    app.stamp.x = point.x - app.pointer.dx;
    app.stamp.y = point.y - app.pointer.dy;
    renderOverlay();
  } else {
    line(app.pointer.doc, app.pointer.last, point);
    app.pointer.last = point;
    markDirty(app.pointer.doc);
    render();
  }
}

function endPointer(event) {
  if (app.pointer && ui.overlay.hasPointerCapture(event.pointerId)) ui.overlay.releasePointerCapture(event.pointerId);
  app.pointer = null;
}

async function shiftLayer(dx, dy) {
  if (app.stamp) {
    app.stamp.x += dx;
    app.stamp.y += dy;
    return renderOverlay();
  }
  if ($("#showDerived").checked) return status("Transforms are disabled in derived phase view.", true);
  const doc = await getDocument();
  pushUndo(doc);
  const source = clonePixels(doc);
  const context = doc.canvas.getContext("2d");
  context.clearRect(0, 0, doc.canvas.width, doc.canvas.height);
  context.putImageData(source, dx, dy);
  markDirty(doc);
  render();
}

async function mirrorLayer() {
  if ($("#showDerived").checked) return status("Transforms are disabled in derived phase view.", true);
  const doc = await getDocument();
  pushUndo(doc);
  const copy = makeCanvas(doc.canvas.width, doc.canvas.height);
  const context = copy.getContext("2d");
  context.translate(copy.width, 0);
  context.scale(-1, 1);
  context.drawImage(doc.canvas, 0, 0);
  doc.canvas.getContext("2d").clearRect(0, 0, doc.canvas.width, doc.canvas.height);
  doc.canvas.getContext("2d").drawImage(copy, 0, 0);
  markDirty(doc);
  render();
}

async function clearLayer() {
  if ($("#showDerived").checked) return status("Clear is disabled in derived phase view.", true);
  const doc = await getDocument();
  pushUndo(doc);
  doc.canvas.getContext("2d").clearRect(0, 0, doc.canvas.width, doc.canvas.height);
  markDirty(doc);
  render();
}

async function undo() {
  const doc = await getDocument();
  if (!doc.undo.length) return;
  doc.redo.push(clonePixels(doc));
  doc.canvas.getContext("2d").putImageData(doc.undo.pop(), 0, 0);
  markDirty(doc);
  updateHistoryButtons(doc);
  render();
}

async function redo() {
  const doc = await getDocument();
  if (!doc.redo.length) return;
  doc.undo.push(clonePixels(doc));
  doc.canvas.getContext("2d").putImageData(doc.redo.pop(), 0, 0);
  markDirty(doc);
  updateHistoryButtons(doc);
  render();
}

async function save() {
  const doc = await getDocument();
  if (!doc.dirty || !doc.layer.channel.editable) return;
  const badge = $("#dirtyBadge");
  badge.textContent = "Saving";
  badge.className = "dirty-badge saving";
  $("#save").disabled = true;
  const blob = await new Promise((resolve) => doc.canvas.toBlob(resolve, "image/png"));
  try {
    const response = await fetch(doc.layer.channel.masterUrls[doc.poseId], {
      method: "PUT",
      headers: {
        "Content-Type": "image/png",
        "X-Appearance-Token": app.state.editor.token,
      },
      body: blob,
    });
    if (!response.ok) throw new Error(await response.text());
    doc.dirty = false;
    doc.revision += 1;
    badge.textContent = "Saved";
    badge.className = "dirty-badge clean";
    status(`Saved ${doc.layer.key} / ${doc.poseId} deterministically under repository tmp.`);
  } catch (error) {
    markDirty(doc);
    status(`Save failed: ${error.message}`, true);
  }
}

async function importStamp(file) {
  if (!file) return;
  if (file.type !== "image/png") return status("Import must be a PNG.", true);
  const source = new Image();
  const objectUrl = URL.createObjectURL(file);
  try {
    source.src = objectUrl;
    await source.decode();
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
  if (source.width > 2048 || source.height > 2048) return status("Import is too large (maximum 2048×2048).", true);
  const pose = currentPose();
  const scale = Math.min(1, pose.canvas[0] / source.width, pose.canvas[1] / source.height);
  const width = Math.max(1, Math.round(source.width * scale));
  const height = Math.max(1, Math.round(source.height * scale));
  app.stamp = {
    image: source, width, height,
    x: Math.floor((pose.canvas[0] - width) / 2),
    y: Math.floor((pose.canvas[1] - height) / 2),
    mirrored: false,
  };
  $("#stampControls").hidden = false;
  describeStamp("Import stamp ready");
  renderOverlay();
}

function describeStamp(prefix) {
  if (!app.stamp) return;
  const mirror = app.stamp.mirrored ? " · mirrored" : "";
  status(`${prefix}: ${app.stamp.width}×${app.stamp.height}${mirror}. Drag it on the canvas, then Apply.`);
}

function scaleStamp(factor) {
  if (!app.stamp) return;
  const centerX = app.stamp.x + app.stamp.width / 2;
  const centerY = app.stamp.y + app.stamp.height / 2;
  app.stamp.width = Math.max(1, Math.min(2048, Math.round(app.stamp.width * factor)));
  app.stamp.height = Math.max(1, Math.min(2048, Math.round(app.stamp.height * factor)));
  app.stamp.x = Math.round(centerX - app.stamp.width / 2);
  app.stamp.y = Math.round(centerY - app.stamp.height / 2);
  describeStamp("Import stamp scaled");
  renderOverlay();
}

function mirrorStamp() {
  if (!app.stamp) return;
  app.stamp.mirrored = !app.stamp.mirrored;
  describeStamp("Import stamp updated");
  renderOverlay();
}

function pasteImport(event) {
  const files = [...(event.clipboardData?.files || [])];
  const items = [...(event.clipboardData?.items || [])];
  const file = files.find((candidate) => candidate.type === "image/png")
    || items.find((candidate) => candidate.type === "image/png")?.getAsFile();
  if (!file) return;
  event.preventDefault();
  importStamp(file);
}

async function applyStamp() {
  if (!app.stamp) return;
  if ($("#showDerived").checked) return status("Stamping is disabled in derived phase view.", true);
  const doc = await getDocument();
  pushUndo(doc);
  const context = doc.canvas.getContext("2d");
  context.save();
  context.imageSmoothingEnabled = false;
  if (app.stamp.mirrored) {
    context.translate(app.stamp.x + app.stamp.width, app.stamp.y);
    context.scale(-1, 1);
    context.drawImage(app.stamp.image, 0, 0, app.stamp.width, app.stamp.height);
  } else {
    context.drawImage(app.stamp.image, app.stamp.x, app.stamp.y, app.stamp.width, app.stamp.height);
  }
  context.restore();
  app.stamp = null;
  $("#stampControls").hidden = true;
  $("#importFile").value = "";
  markDirty(doc);
  render();
}

function cancelStamp() {
  app.stamp = null;
  $("#stampControls").hidden = true;
  $("#importFile").value = "";
  renderOverlay();
}

function bindEvents() {
  $$('[data-tool]').forEach((button) => button.addEventListener("click", () => setTool(button.dataset.tool)));
  $("#grayRange").addEventListener("input", (event) => setGray(event.target.value));
  $("#alphaRange").addEventListener("input", (event) => setAlpha(event.target.value));
  $("#zoom").addEventListener("input", (event) => {
    app.zoom = Number(event.target.value);
    $("#zoomLabel").value = `${app.zoom}×`;
    render();
  });
  $("#undo").addEventListener("click", undo);
  $("#redo").addEventListener("click", redo);
  $("#save").addEventListener("click", save);
  $("#nudgeUp").addEventListener("click", () => shiftLayer(0, -1));
  $("#nudgeDown").addEventListener("click", () => shiftLayer(0, 1));
  $("#nudgeLeft").addEventListener("click", () => shiftLayer(-1, 0));
  $("#nudgeRight").addEventListener("click", () => shiftLayer(1, 0));
  $("#mirrorLayer").addEventListener("click", mirrorLayer);
  $("#clearLayer").addEventListener("click", clearLayer);
  $("#importFile").addEventListener("change", (event) => importStamp(event.target.files[0]));
  $("#stampSmaller").addEventListener("click", () => scaleStamp(.5));
  $("#stampLarger").addEventListener("click", () => scaleStamp(2));
  $("#stampMirror").addEventListener("click", mirrorStamp);
  $("#stampApply").addEventListener("click", applyStamp);
  $("#stampCancel").addEventListener("click", cancelStamp);
  ui.overlay.addEventListener("pointerdown", beginPointer);
  ui.overlay.addEventListener("pointermove", movePointer);
  ui.overlay.addEventListener("pointerup", endPointer);
  ui.overlay.addEventListener("pointercancel", endPointer);
  ui.overlay.addEventListener("pointerleave", () => { $("#cursorPosition").textContent = "x — · y —"; });
  for (const id of ["showBase", "showAnchors", "showCrown", "showDerived"]) {
    $(`#${id}`).addEventListener("change", render);
  }
  $("#maskSelect").addEventListener("change", () => renderOverlay());
  $("#maskOpacity").addEventListener("input", () => renderOverlay());
  $$("#phaseButtons button").forEach((button) => button.addEventListener("click", () => {
    app.phase = Number(button.dataset.phase);
    updatePhaseButtons();
    render();
  }));
  $$("#previewScaleButtons button").forEach((button) => button.addEventListener("click", () => {
    app.previewScale = Number(button.dataset.scale);
    $$("#previewScaleButtons button").forEach((item) => item.classList.toggle("active", item === button));
    $("#previewScaleLabel").textContent = app.previewScale === 1 ? "1× comparison" : "2× native";
    renderPreview();
  }));
  window.addEventListener("keydown", (event) => {
    if (["INPUT", "SELECT", "TEXTAREA"].includes(event.target.tagName)) return;
    const command = event.metaKey || event.ctrlKey;
    if (command && event.key.toLowerCase() === "s") { event.preventDefault(); save(); return; }
    if (command && event.key.toLowerCase() === "z") { event.preventDefault(); event.shiftKey ? redo() : undo(); return; }
    if (event.key.toLowerCase() === "p") setTool("paint");
    else if (event.key.toLowerCase() === "e") setTool("erase");
    else if (event.key.toLowerCase() === "i") setTool("pick");
    else if (event.key === "[") $("#brushSize").value = Math.max(1, Number($("#brushSize").value) - 1);
    else if (event.key === "]") $("#brushSize").value = Math.min(16, Number($("#brushSize").value) + 1);
    else if (event.key === "ArrowUp") { event.preventDefault(); shiftLayer(0, -1); }
    else if (event.key === "ArrowDown") { event.preventDefault(); shiftLayer(0, 1); }
    else if (event.key === "ArrowLeft") { event.preventDefault(); shiftLayer(-1, 0); }
    else if (event.key === "ArrowRight") { event.preventDefault(); shiftLayer(1, 0); }
  });
  window.addEventListener("paste", pasteImport);
  window.addEventListener("beforeunload", (event) => {
    if ([...app.documents.values()].some((doc) => doc.dirty)) {
      event.preventDefault();
      event.returnValue = "";
    }
  });
}

async function boot() {
  bindEvents();
  try {
    const response = await fetch("/api/state", { cache: "no-store" });
    if (!response.ok) throw new Error(await response.text());
    app.state = await response.json();
    $("#workspaceName").textContent = app.state.name;
    buildPoseList();
    buildLayerSelect();
    buildStackSelect();
    buildSwatches();
    app.poseId = app.state.poses[0].id;
    const editableLayers = layers().filter((layer) => layer.channel.editable);
    const initialLayer = editableLayers.find((layer) => layer.asset.kind === "hair") || editableLayers[0] || layers()[0];
    if (!initialLayer) throw new Error("Workspace does not expose a master channel.");
    app.layerKey = initialLayer.key;
    await selectPose(app.poseId);
    await selectLayer(app.layerKey);
    const initialStack = app.state.renderStacks.find((stack) => stack.assets.includes(initialLayer.asset.id)) || app.state.renderStacks[0];
    if (!initialStack) throw new Error("Workspace does not expose a preview stack.");
    selectStack(initialStack.id);
    status("Ready. Every save is confined to this repository's tmp workspace.");
  } catch (error) {
    status(`Editor startup failed: ${error.message}`, true);
  }
}

boot();
