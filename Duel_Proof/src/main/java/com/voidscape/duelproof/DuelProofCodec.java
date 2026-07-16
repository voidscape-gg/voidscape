package com.voidscape.duelproof;

/** Byte-level helpers used by the canonical proof format. */
public final class DuelProofCodec {

	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private DuelProofCodec() {
	}

	public static byte[] ascii(String value) {
		if (value == null) {
			throw new IllegalArgumentException("value must not be null");
		}
		byte[] encoded = new byte[value.length()];
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character > 0x7f) {
				throw new IllegalArgumentException("value must contain ASCII only");
			}
			encoded[i] = (byte) character;
		}
		return encoded;
	}

	public static byte[] unsignedInt32(long value) {
		if (value < 0 || value > 0xffffffffL) {
			throw new IllegalArgumentException("value is outside unsigned 32-bit range");
		}
		return new byte[] {
			(byte) (value >>> 24),
			(byte) (value >>> 16),
			(byte) (value >>> 8),
			(byte) value
		};
	}

	public static byte[] unsignedByte(int value) {
		if (value < 0 || value > 0xff) {
			throw new IllegalArgumentException("value is outside unsigned 8-bit range");
		}
		return new byte[] {(byte) value};
	}

	public static byte[] concat(byte[]... parts) {
		if (parts == null) {
			throw new IllegalArgumentException("parts must not be null");
		}
		long totalLength = 0;
		for (byte[] part : parts) {
			if (part == null) {
				throw new IllegalArgumentException("parts must not contain null");
			}
			totalLength += part.length;
			if (totalLength > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("combined byte sequence is too large");
			}
		}
		byte[] combined = new byte[(int) totalLength];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, combined, offset, part.length);
			offset += part.length;
		}
		return combined;
	}

	public static String hexLower(byte[] bytes) {
		if (bytes == null) {
			throw new IllegalArgumentException("bytes must not be null");
		}
		char[] encoded = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xff;
			encoded[i * 2] = HEX[value >>> 4];
			encoded[i * 2 + 1] = HEX[value & 0x0f];
		}
		return new String(encoded);
	}

	/** Parses canonical lowercase hexadecimal with an exact decoded length. */
	public static byte[] parseHexLowerExact(String text, int byteCount) {
		if (text == null) {
			throw new IllegalArgumentException("text must not be null");
		}
		if (byteCount < 0 || text.length() != byteCount * 2) {
			throw new IllegalArgumentException("hex value has the wrong length");
		}
		byte[] decoded = new byte[byteCount];
		for (int i = 0; i < byteCount; i++) {
			int high = lowerHexValue(text.charAt(i * 2));
			int low = lowerHexValue(text.charAt(i * 2 + 1));
			decoded[i] = (byte) ((high << 4) | low);
		}
		return decoded;
	}

	private static int lowerHexValue(char character) {
		if (character >= '0' && character <= '9') {
			return character - '0';
		}
		if (character >= 'a' && character <= 'f') {
			return character - 'a' + 10;
		}
		throw new IllegalArgumentException("hex value must use lowercase ASCII");
	}
}
