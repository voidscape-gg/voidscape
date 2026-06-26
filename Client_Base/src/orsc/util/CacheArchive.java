package orsc.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CacheArchive {
	private final Map<String, byte[]> entries;

	private CacheArchive(Map<String, byte[]> entries) {
		this.entries = entries;
	}

	public static CacheArchive read(InputStream input) throws IOException {
		Map<String, byte[]> entries = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(input)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					entries.put(entry.getName(), readEntry(zip));
				}
				zip.closeEntry();
			}
		}
		return new CacheArchive(entries);
	}

	public byte[] getEntry(String name) {
		return entries.get(name);
	}

	public ByteBuffer getEntryBuffer(String name) {
		byte[] data = getEntry(name);
		return data == null ? null : ByteBuffer.wrap(data);
	}

	private static byte[] readEntry(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) != -1) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}
}
