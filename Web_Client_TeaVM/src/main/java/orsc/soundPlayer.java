package orsc;

import org.teavm.jso.JSBody;

public final class soundPlayer {
	private soundPlayer() {
	}

	public static void playSoundFile(String key) {
		if (mudclient.optionSoundDisabled || !isChristmasCrackerSound(key)) {
			return;
		}
		playBrowserSound(key);
	}

	private static boolean isChristmasCrackerSound(String key) {
		return "mechanical".equals(key) || "click".equals(key) || "spellfail".equals(key)
			|| "foundgem".equals(key) || "victory".equals(key);
	}

	@JSBody(params = {"key"}, script =
		"try {" +
		"  const AudioContextClass = window.AudioContext || window.webkitAudioContext;" +
		"  if (!AudioContextClass) return;" +
		"  const context = window.__voidscapeAudioContext || (window.__voidscapeAudioContext = new AudioContextClass());" +
		"  if (context.state === 'suspended') context.resume().catch(function() {});" +
		"  const buffers = window.__voidscapeCrackerSoundBuffers || (window.__voidscapeCrackerSoundBuffers = {});" +
		"  const play = function(buffer) {" +
		"    const source = context.createBufferSource(); const gain = context.createGain();" +
		"    source.buffer = buffer; gain.gain.value = key === 'click' ? 0.45 : 0.7;" +
		"    source.connect(gain); gain.connect(context.destination); source.start(0);" +
		"  };" +
		"  if (buffers[key] && buffers[key] !== true) { play(buffers[key]); return; }" +
		"  if (buffers[key] === true) return;" +
		"  buffers[key] = true;" +
		"  const token = String(window.__voidscapeAssetToken || '').trim();" +
		"  let url = 'Cache/audio/' + encodeURIComponent(key) + '.wav';" +
		"  if (token) url += '?v=' + encodeURIComponent(token);" +
		"  fetch(url).then(function(response) { if (!response.ok) throw new Error('HTTP ' + response.status); return response.arrayBuffer(); })" +
		"    .then(function(bytes) { return context.decodeAudioData(bytes); })" +
		"    .then(function(buffer) { buffers[key] = buffer; play(buffer); })" +
		"    .catch(function() { delete buffers[key]; });" +
		"} catch (ignored) {}")
	private static native void playBrowserSound(String key);
}
