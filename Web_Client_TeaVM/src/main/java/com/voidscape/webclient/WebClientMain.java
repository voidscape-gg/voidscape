package com.voidscape.webclient;

import org.teavm.jso.JSBody;

import orsc.Config;
import orsc.PacketHandler;
import orsc.mudclient;
import orsc.osConfig;

public final class WebClientMain {
	private WebClientMain() {
	}

	public static void main(String[] args) {
		Config.F_CACHE_DIR = "Cache";
		configureEndpoint();
		Config.SERVER_IP = getServerHost();
		Config.SERVER_PORT = getServerPort();
		osConfig.F_ANDROID_BUILD = shouldUseMobileProfile();
		preparePage(Config.CLIENT_VERSION, osConfig.F_ANDROID_BUILD);
		startClient();
	}

	private static void startClient() {
		try {
			setStatus("Creating shared mudclient...");
			WebClientPort port = new WebClientPort();
			mudclient client = new mudclient(port);
			port.setClient(client);
			port.initListeners();
			client.packetHandler = new PacketHandler(client);
			setStatus("Starting shared game loop...");
			client.startMainThread();
		} catch (Throwable t) {
			setStatus("Client start failed: " + t.getClass().getName() + ": " + t.getMessage());
		}
	}

	@JSBody(params = { "clientVersion", "mobileProfile" }, script =
		"document.title = 'Voidscape';" +
		"document.body.classList.remove('in-game');" +
		"window.__voidscapeInGame = false;" +
		"let runtime = null;" +
		"if (typeof window.__voidscapeApplyRuntimeMode === 'function') runtime = window.__voidscapeApplyRuntimeMode();" +
		"const phoneProfile = runtime ? runtime.mode === 'phone' : !!mobileProfile;" +
		"const runtimeMode = runtime ? runtime.mode : (phoneProfile ? 'phone' : 'desktop');" +
		"document.documentElement.classList.toggle('touch', phoneProfile);" +
		"document.documentElement.classList.toggle('phone', runtimeMode === 'phone');" +
		"document.documentElement.classList.toggle('tablet', runtimeMode === 'tablet');" +
		"document.documentElement.classList.toggle('desktop', runtimeMode === 'desktop');" +
		"window.__voidscapeMobileProfile = phoneProfile;" +
		"const canvas = document.getElementById('game');" +
		"const ctx = canvas.getContext('2d');" +
		"ctx.imageSmoothingEnabled = false;" +
		"ctx.fillStyle = '#050505'; ctx.fillRect(0, 0, canvas.width, canvas.height);" +
		"ctx.fillStyle = '#c7b36a'; ctx.font = '18px monospace';" +
		"ctx.fillText('Voidscape', 24, 42);" +
		"ctx.fillStyle = '#d8d8d8'; ctx.font = '14px monospace';" +
		"ctx.fillText('Shared client version: ' + clientVersion, 24, 72);" +
		"document.getElementById('status').textContent = 'Starting Voidscape...';")
	private static native void preparePage(int clientVersion, boolean mobileProfile);

	@JSBody(params = { "status" }, script = "document.getElementById('status').textContent = status;")
	private static native void setStatus(String status);

	@JSBody(params = {}, script =
		"const params = new URLSearchParams(window.location.search);" +
		"const profile = window.__voidscapeProfile || { id: 'default' };" +
		"const storageKey = typeof window.__voidscapeProfileStorageKey === 'function'" +
		"  ? window.__voidscapeProfileStorageKey('voidscape.web.endpoint.v1')" +
		"  : 'voidscape.web.endpoint.v1';" +
		"const defaultPort = function() {" +
		"  if (window.location.protocol === 'https:') {" +
		"    const locationPort = parseInt(window.location.port || '443', 10);" +
		"    return Number.isFinite(locationPort) ? locationPort : 443;" +
		"  }" +
		"  return 43496;" +
		"};" +
		"const defaultSubpathWebSocket = function() {" +
		"  if (window.location.protocol !== 'https:') return '';" +
		"  const path = window.location.pathname || '/';" +
		"  if (path === '/play' || path.indexOf('/play/') === 0) {" +
		"    return 'wss://' + window.location.host + '/play/ws/';" +
		"  }" +
		"  return '';" +
		"};" +
		"const readStored = function() {" +
		"  try {" +
		"    const raw = window.localStorage ? window.localStorage.getItem(storageKey) : '';" +
		"    if (!raw) return null;" +
		"    const parsed = JSON.parse(raw);" +
		"    if (parsed && parsed.mode === 'ws' && typeof parsed.ws === 'string' && parsed.ws.length > 0) return parsed;" +
		"    if (parsed && parsed.mode === 'hostport' && typeof parsed.host === 'string' && parsed.host.length > 0) return parsed;" +
		"  } catch (ignored) {}" +
		"  return null;" +
		"};" +
		"const store = function(endpoint) {" +
		"  try {" +
		"    if (window.localStorage) window.localStorage.setItem(storageKey, JSON.stringify(endpoint));" +
		"  } catch (ignored) {}" +
		"};" +
		"const clearStored = function() {" +
		"  try { if (window.localStorage) window.localStorage.removeItem(storageKey); } catch (ignored) {}" +
		"};" +
		"const decorate = function(endpoint) {" +
		"  endpoint.profile = profile.id || 'default';" +
		"  endpoint.storageKey = storageKey;" +
		"  return endpoint;" +
		"};" +
		"let endpoint = null;" +
		"if (params.get('endpoint') === 'reset' || params.get('resetEndpoint') === '1') {" +
		"  clearStored();" +
		"}" +
		"const ws = params.get('ws');" +
		"if (ws && ws.length > 0) {" +
		"  endpoint = decorate({ mode: 'ws', ws: ws, source: 'query' });" +
		"  store({ mode: 'ws', ws: ws });" +
		"} else if (params.has('host') || params.has('port')) {" +
		"  const host = params.get('host') || window.location.hostname || '127.0.0.1';" +
		"  const parsedPort = parseInt(params.get('port') || String(defaultPort()), 10);" +
		"  const port = Number.isFinite(parsedPort) && parsedPort > 0 ? parsedPort : defaultPort();" +
		"  endpoint = decorate({ mode: 'hostport', host: host, port: port, source: 'query' });" +
		"  store({ mode: 'hostport', host: host, port: port });" +
		"} else {" +
		"  const stored = readStored();" +
		"  if (stored) {" +
		"    endpoint = decorate(Object.assign({}, stored, { source: 'stored' }));" +
		"  }" +
		"}" +
		"if (!endpoint) {" +
		"  const subpathWs = defaultSubpathWebSocket();" +
		"  if (subpathWs) {" +
		"    endpoint = decorate({ mode: 'ws', ws: subpathWs, source: 'default' });" +
		"  } else {" +
		"    endpoint = decorate({ mode: 'default', host: window.location.hostname || '127.0.0.1', port: defaultPort(), source: 'default' });" +
		"  }" +
		"}" +
		"window.__voidscapeEndpointStorageKey = storageKey;" +
		"window.__voidscapeEndpoint = endpoint;")
	private static native void configureEndpoint();

	@JSBody(params = {}, script =
		"const endpoint = window.__voidscapeEndpoint || {};" +
		"return endpoint.host || window.location.hostname || '127.0.0.1';")
	private static native String getServerHost();

	@JSBody(params = {}, script =
		"const endpoint = window.__voidscapeEndpoint || {};" +
		"if (endpoint.port > 0) return endpoint.port;" +
		"if (window.location.protocol === 'https:') {" +
		"  const locationPort = parseInt(window.location.port || '443', 10);" +
		"  return Number.isFinite(locationPort) ? locationPort : 443;" +
		"}" +
		"return 43496;")
	private static native int getServerPort();

	@JSBody(params = {}, script =
		"if (typeof window.__voidscapeApplyRuntimeMode === 'function') {" +
		"  return window.__voidscapeApplyRuntimeMode().mode === 'phone';" +
		"}" +
		"const params = new URLSearchParams(window.location.search);" +
		"if (params.get('phone') === '1' || params.get('mobile') === '1') return true;" +
		"if (params.get('desktop') === '1' || params.get('tablet') === '1' || params.get('mobile') === '0') return false;" +
		"if (window.__voidscapeMobileProfile === true) return true;" +
		"return /iPhone|iPod|Android.*Mobile/i.test(navigator.userAgent);")
	private static native boolean shouldUseMobileProfile();
}
