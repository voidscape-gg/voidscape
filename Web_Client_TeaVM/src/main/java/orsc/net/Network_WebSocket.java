package orsc.net;

import java.io.IOException;

import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.jso.websocket.WebSocket;

public final class Network_WebSocket extends Network_Base {
	private static final int CONNECT_TIMEOUT_MS = 10000;
	private WebSocket socket;
	private boolean open;
	private boolean closed;
	private byte[] inbound = new byte[8192];
	private int readIndex;
	private int writeIndex;

	public Network_WebSocket(String host, int port) throws IOException {
		String url = buildWebSocketUrl(host, port);
		socket = WebSocket.create(url);
		socket.setBinaryType("arraybuffer");
		socket.onOpen(event -> open = true);
		socket.onClose(event -> closed = true);
		socket.onError(event -> {
			errorHappened = true;
			errorCode = "websocket error";
			closed = true;
		});
		socket.onMessage(this::appendMessage);
		waitForOpen();
	}

	@Override
	public int available() {
		return writeIndex - readIndex;
	}

	@Override
	public int read() throws IOException {
		waitForAvailable(1);
		return inbound[readIndex++] & 255;
	}

	@Override
	public void read(byte[] data, int offset, int count) throws IOException {
		waitForAvailable(count);
		System.arraycopy(inbound, readIndex, data, offset, count);
		readIndex += count;
		compactIfNeeded();
	}

	@Override
	public void close() {
		closed = true;
		if (socket != null) {
			socket.close();
			socket = null;
		}
	}

	@Override
	void send(byte[] data, int offset, int count) throws IOException {
		waitForOpen();
		byte[] packet = new byte[count];
		System.arraycopy(data, offset, packet, 0, count);
		Uint8Array buffer = Uint8Array.create(count);
		buffer.set(packet);
		socket.send(buffer);
	}

	private void appendMessage(MessageEvent event) {
		ArrayBuffer buffer = event.getDataAsArray();
		Uint8Array bytes = Uint8Array.create(buffer);
		int length = bytes.getLength();
		ensureCapacity(length);
		for (int i = 0; i < length; i++) {
			inbound[writeIndex++] = (byte) bytes.get(i);
		}
	}

	private void waitForOpen() throws IOException {
		long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
		while (!open && !closed && System.currentTimeMillis() < deadline) {
			sleep(10);
		}
		if (!open) {
			throw new IOException(errorCode.length() == 0 ? "websocket open timed out" : errorCode);
		}
	}

	private void waitForAvailable(int count) throws IOException {
		while (available() < count) {
			if (closed) {
				throw new IOException(errorCode.length() == 0 ? "websocket closed" : errorCode);
			}
			sleep(10);
		}
	}

	private void ensureCapacity(int additional) {
		compactIfNeeded();
		int required = writeIndex + additional;
		if (required <= inbound.length) {
			return;
		}
		int size = inbound.length;
		while (size < required) {
			size *= 2;
		}
		byte[] expanded = new byte[size];
		System.arraycopy(inbound, readIndex, expanded, 0, writeIndex - readIndex);
		writeIndex -= readIndex;
		readIndex = 0;
		inbound = expanded;
	}

	private void compactIfNeeded() {
		if (readIndex == 0) {
			return;
		}
		if (readIndex == writeIndex) {
			readIndex = 0;
			writeIndex = 0;
			return;
		}
		if (readIndex > inbound.length / 2) {
			System.arraycopy(inbound, readIndex, inbound, 0, writeIndex - readIndex);
			writeIndex -= readIndex;
			readIndex = 0;
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}

	@JSBody(params = { "host", "port" }, script =
		"const params = new URLSearchParams(window.location.search);" +
		"const endpoint = window.__voidscapeEndpoint || {};" +
		"const override = params.get('ws') || endpoint.ws;" +
		"if (override) {" +
		"  window.__voidscapeEffectiveWebSocketUrl = override;" +
		"  return override;" +
		"}" +
		"const selectedHost = host && host.length ? host : window.location.hostname;" +
		"const selectedPort = port > 0 ? port : 43496;" +
		"const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';" +
		"const url = protocol + '//' + selectedHost + ':' + selectedPort + '/';" +
		"window.__voidscapeEffectiveWebSocketUrl = url;" +
		"return url;")
	private static native String buildWebSocketUrl(String host, int port);
}
