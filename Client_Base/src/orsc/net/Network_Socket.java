package orsc.net;

import orsc.PacketHandler;
import orsc.Config;
import orsc.util.GenUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public final class Network_Socket extends Network_Base implements Runnable {
	private static final int WRITE_BUFFER_SIZE = 5000;
	private static final int WRITE_BUFFER_GUARD = 100;
	private static final AtomicInteger ACTIVE_WRITERS = new AtomicInteger();

	private final byte[] tmpRead = new byte[1];
	private volatile boolean closed = false;
	private boolean closeAfterFlush = false;
	private boolean resourcesClosed = false;
	private InputStream inStream;
	private boolean m_X = true;
	private OutputStream outStream;
	private Socket sock;
	private byte[] writeBuffer;

	private int writeBufRead = 0;

	private int writeBufWrite = 0;

	public Network_Socket(Socket sock, PacketHandler var2) throws IOException {
		try {
			this.sock = sock;
			this.inStream = sock.getInputStream();
			this.outStream = sock.getOutputStream();
			this.m_X = false;
			var2.startThread(1, this);
		} catch (IOException error) {
			closeResources();
			throw error;
		} catch (RuntimeException var4) {
			closeResources();
			throw GenUtil.makeThrowable(var4,
				"da.<init>(" + (sock != null ? "{...}" : "null") + ',' + (var2 != null ? "{...}" : "null") + ')');
		}
	}

	@Override
	public final int available() throws IOException {
		try {
			return this.closed ? 0 : this.inStream.available();
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "da.B(" + -124 + ')');
		}
	}

	@Override
	public final void close() {
		try {
			super.close();
			synchronized (this) {
				if (this.closed && this.m_X) {
					return;
				}
				this.closed = true;
				this.m_X = true;
				this.closeAfterFlush = false;
				this.notifyAll();
			}
			closeResources();
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "da.G(" + closed + ')');
		}
	}

	@Override
	public final void closeAfterFlush() {
		boolean closeImmediately;
		synchronized (this) {
			if (this.closed || this.m_X) {
				return;
			}
			this.closeAfterFlush = true;
			closeImmediately = this.writeBufRead == this.writeBufWrite;
			if (closeImmediately) {
				this.closed = true;
				this.m_X = true;
			}
			this.notifyAll();
		}
		if (closeImmediately) {
			closeResources();
		}
	}

	@Override
	public final int read() throws IOException {
		try {
			if (!this.closed) {
				this.read(this.tmpRead, 0, 1);
				return 255 & this.tmpRead[0];
			} else {
				return 0;
			}
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "da.A(" + true + ')');
		}
	}

	@Override
	public final void read(byte[] data, int offset, int count) throws IOException {
		try {
			if (!this.closed) {
				int totalRead = 0;

				int readCount;
				for (; totalRead < count; totalRead += readCount) {
					if ((readCount = this.inStream.read(data, offset + totalRead, count - totalRead)) <= 0) {
						throw new IOException("EOF: " + totalRead + ", " + count);
					}
				}

			}
		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8,
				"da.F(" + (data != null ? "{...}" : "null") + ',' + count + ',' + offset + ',' + "dummy" + ')');
		}
	}

	@Override
	public final void run() {
		int active = ACTIVE_WRITERS.incrementAndGet();
		boolean logWriterLifecycle = isWriterLifecycleLoggingEnabled();
		if (logWriterLifecycle) {
			System.out.println("VOIDSCAPE_NETWORK_WRITER event=start active=" + active);
		}
		try {
			while (true) {
				int begin;
				int len;
				byte[] buffer;
				synchronized (this) {
					while (!this.m_X && this.writeBufWrite == this.writeBufRead && !this.closeAfterFlush) {
						try {
							this.wait();
						} catch (InterruptedException var8) {
							Thread.currentThread().interrupt();
							this.m_X = true;
							this.closed = true;
						}
					}

					if (this.closeAfterFlush && this.writeBufWrite == this.writeBufRead) {
						this.m_X = true;
						this.closed = true;
					}
					if (this.m_X) {
						break;
					}

					if (this.writeBufRead > this.writeBufWrite) {
						begin = WRITE_BUFFER_SIZE - this.writeBufRead;
					} else {
						begin = this.writeBufWrite - this.writeBufRead;
					}

					len = this.writeBufRead;
					buffer = this.writeBuffer;
				}

				if (begin > 0) {
					boolean flush;
					boolean closeDrained;
					try {
						this.outStream.write(buffer, len, begin);
					} catch (IOException var7) {
						this.errorCode = "Twriter:" + var7;
						this.errorHappened = true;
						close();
						break;
					}

					synchronized (this) {
						this.writeBufRead = (this.writeBufRead + begin) % WRITE_BUFFER_SIZE;
						flush = this.writeBufRead == this.writeBufWrite;
						closeDrained = flush && this.closeAfterFlush;
						if (closeDrained) {
							this.closed = true;
							this.m_X = true;
						}
					}

					if (flush) {
						try {
							this.outStream.flush();
						} catch (IOException var6) {
							this.errorCode = "Twriter:" + var6;
							this.errorHappened = true;
							close();
							closeDrained = true;
						}
					}
					if (closeDrained) {
						closeResources();
						break;
					}
				}
			}

		} catch (RuntimeException var10) {
			throw GenUtil.makeThrowable(var10, "da.run()");
		} finally {
			synchronized (this) {
				this.closed = true;
				this.m_X = true;
				this.notifyAll();
			}
			closeResources();
			int remaining = ACTIVE_WRITERS.decrementAndGet();
			if (logWriterLifecycle) {
				System.out.println("VOIDSCAPE_NETWORK_WRITER event=stop active=" + remaining);
			}
		}
	}

	@Override
	final void send(byte[] data, int offset, int count) throws IOException {
		try {
			synchronized (this) {
				if (this.closed || this.m_X || this.closeAfterFlush) {
					throw new IOException("connection closed");
				}
				if (null == this.writeBuffer) {
					this.writeBuffer = new byte[WRITE_BUFFER_SIZE];
				}

				for (int i = 0; i < count; ++i) {
					this.writeBuffer[this.writeBufWrite] = data[offset + i];
					this.writeBufWrite = (this.writeBufWrite + 1) % WRITE_BUFFER_SIZE;
					if (this.writeBufWrite == (WRITE_BUFFER_SIZE - WRITE_BUFFER_GUARD + this.writeBufRead) % WRITE_BUFFER_SIZE) {
						throw new IOException("buffer overflow");
					}
				}

				this.notifyAll();
			}
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9,
				"da.D(" + (data != null ? "{...}" : "null") + ',' + offset + ',' + count + ',' + "dummy" + ')');
		}
	}

	private void closeResources() {
		InputStream input;
		OutputStream output;
		Socket socket;
		synchronized (this) {
			if (this.resourcesClosed) {
				return;
			}
			this.resourcesClosed = true;
			input = this.inStream;
			output = this.outStream;
			socket = this.sock;
		}
		try {
			if (input != null) {
				input.close();
			}
		} catch (IOException ignored) {
		}
		try {
			if (output != null) {
				output.close();
			}
		} catch (IOException ignored) {
		}
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (IOException ignored) {
		}
	}

	private static boolean isWriterLifecycleLoggingEnabled() {
		return Config.F_ANDROID_SMOKE_DIR != null
			&& !Config.F_ANDROID_SMOKE_DIR.isEmpty()
			&& new File(Config.F_ANDROID_SMOKE_DIR, "android-smoke-network.flag").isFile();
	}
}
