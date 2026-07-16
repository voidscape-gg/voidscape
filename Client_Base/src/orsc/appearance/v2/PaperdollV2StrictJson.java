package orsc.appearance.v2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal fail-closed JSON parser for bounded compiler-owned contracts. */
final class PaperdollV2StrictJson {
	private static final int MAX_DEPTH = 64;

	private final String text;
	private final String label;
	private int index;

	private PaperdollV2StrictJson(String text, String label) {
		this.text = text;
		this.label = label;
	}

	static Object parse(byte[] bytes, String label) throws IOException {
		if (bytes == null || bytes.length == 0) throw new IOException(label + " JSON is empty");
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException error) {
			throw new IOException(label + " JSON is not valid UTF-8", error);
		}
		PaperdollV2StrictJson parser = new PaperdollV2StrictJson(text, label);
		parser.skipWhitespace();
		Object result = parser.readValue(0);
		parser.skipWhitespace();
		if (parser.index != text.length()) {
			throw parser.failure("has trailing content");
		}
		return result;
	}

	private Object readValue(int depth) throws IOException {
		if (depth > MAX_DEPTH) throw failure("exceeds maximum nesting depth");
		if (index >= text.length()) throw failure("ends before a value");
		char current = text.charAt(index);
		switch (current) {
			case '{': return readObject(depth + 1);
			case '[': return readArray(depth + 1);
			case '"': return readString();
			case 't': readLiteral("true"); return Boolean.TRUE;
			case 'f': readLiteral("false"); return Boolean.FALSE;
			case 'n': readLiteral("null"); return null;
			default:
				if (current == '-' || current >= '0' && current <= '9') return readInteger();
				throw failure("contains an unsupported value");
		}
	}

	private Map<String, Object> readObject(int depth) throws IOException {
		index++;
		skipWhitespace();
		LinkedHashMap<String, Object> result = new LinkedHashMap<>();
		if (consume('}')) return result;
		while (true) {
			if (index >= text.length() || text.charAt(index) != '"') {
				throw failure("object key is not a string");
			}
			String key = readString();
			if (result.containsKey(key)) throw failure("contains duplicate key " + key);
			skipWhitespace();
			require(':');
			skipWhitespace();
			result.put(key, readValue(depth));
			skipWhitespace();
			if (consume('}')) return result;
			require(',');
			skipWhitespace();
			if (index < text.length() && text.charAt(index) == '}') {
				throw failure("contains a trailing object comma");
			}
		}
	}

	private List<Object> readArray(int depth) throws IOException {
		index++;
		skipWhitespace();
		ArrayList<Object> result = new ArrayList<>();
		if (consume(']')) return result;
		while (true) {
			result.add(readValue(depth));
			skipWhitespace();
			if (consume(']')) return result;
			require(',');
			skipWhitespace();
			if (index < text.length() && text.charAt(index) == ']') {
				throw failure("contains a trailing array comma");
			}
		}
	}

	private String readString() throws IOException {
		require('"');
		StringBuilder result = new StringBuilder();
		while (index < text.length()) {
			char current = text.charAt(index++);
			if (current == '"') return result.toString();
			if (current < 0x20) throw failure("string contains an unescaped control character");
			if (current != '\\') {
				if (Character.isHighSurrogate(current)) {
					if (index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) {
						throw failure("string contains an unpaired surrogate");
					}
					result.append(current).append(text.charAt(index++));
				} else if (Character.isLowSurrogate(current)) {
					throw failure("string contains an unpaired surrogate");
				} else {
					result.append(current);
				}
				continue;
			}
			if (index >= text.length()) throw failure("string has a truncated escape");
			char escaped = text.charAt(index++);
			switch (escaped) {
				case '"': result.append('"'); break;
				case '\\': result.append('\\'); break;
				case '/': result.append('/'); break;
				case 'b': result.append('\b'); break;
				case 'f': result.append('\f'); break;
				case 'n': result.append('\n'); break;
				case 'r': result.append('\r'); break;
				case 't': result.append('\t'); break;
				case 'u':
					char decoded = readUnicodeEscape();
					if (Character.isHighSurrogate(decoded)) {
						if (index + 1 >= text.length() || text.charAt(index) != '\\'
							|| text.charAt(index + 1) != 'u') {
							throw failure("string contains an unpaired unicode surrogate");
						}
						index += 2;
						char low = readUnicodeEscape();
						if (!Character.isLowSurrogate(low)) {
							throw failure("string contains an invalid unicode surrogate pair");
						}
						result.append(decoded).append(low);
					} else if (Character.isLowSurrogate(decoded)) {
						throw failure("string contains an unpaired unicode surrogate");
					} else {
						result.append(decoded);
					}
					break;
				default: throw failure("string contains an unsupported escape");
			}
		}
		throw failure("has an unterminated string");
	}

	private char readUnicodeEscape() throws IOException {
		if (index + 4 > text.length()) throw failure("has a truncated unicode escape");
		int value = 0;
		for (int count = 0; count < 4; count++) {
			char current = text.charAt(index++);
			int digit = Character.digit(current, 16);
			if (digit < 0) throw failure("has a malformed unicode escape");
			value = value << 4 | digit;
		}
		return (char) value;
	}

	private Long readInteger() throws IOException {
		int start = index;
		if (text.charAt(index) == '-') index++;
		if (index >= text.length()) throw failure("has a truncated number");
		if (text.charAt(index) == '0') {
			index++;
			if (index < text.length() && Character.isDigit(text.charAt(index))) {
				throw failure("number has a leading zero");
			}
		} else {
			if (text.charAt(index) < '1' || text.charAt(index) > '9') {
				throw failure("number is malformed");
			}
			while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
		}
		if (index < text.length()) {
			char suffix = text.charAt(index);
			if (suffix == '.' || suffix == 'e' || suffix == 'E') {
				throw failure("contains a non-integer number");
			}
		}
		try {
			return Long.valueOf(text.substring(start, index));
		} catch (NumberFormatException error) {
			throw failure("integer is out of range");
		}
	}

	private void readLiteral(String expected) throws IOException {
		if (!text.regionMatches(index, expected, 0, expected.length())) {
			throw failure("contains a malformed literal");
		}
		index += expected.length();
	}

	private void skipWhitespace() {
		while (index < text.length()) {
			char current = text.charAt(index);
			if (current != ' ' && current != '\n' && current != '\r' && current != '\t') return;
			index++;
		}
	}

	private boolean consume(char expected) {
		if (index < text.length() && text.charAt(index) == expected) {
			index++;
			return true;
		}
		return false;
	}

	private void require(char expected) throws IOException {
		if (!consume(expected)) throw failure("expected '" + expected + "'");
	}

	private IOException failure(String reason) {
		return new IOException(label + " JSON " + reason + " at character " + index);
	}
}
