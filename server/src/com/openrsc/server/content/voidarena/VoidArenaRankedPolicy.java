package com.openrsc.server.content.voidarena;

import com.google.common.net.InetAddresses;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

/** Deterministic ranked-arena rules shared by admission, settlement, and tests. */
public final class VoidArenaRankedPolicy {
	public enum ResultType {
		DEATH,
		FORFEIT,
		TIMEOUT_DRAW,
		SERVER_SHUTDOWN_NO_CONTEST,
		SERVER_RESTART_NO_CONTEST
	}

	public enum PairEligibility {
		ELIGIBLE,
		INVALID_PLAYER,
		STAT_REQUIREMENT,
		AMBIGUOUS_PROXY_IP,
		SAME_PUBLIC_NETWORK,
		RATED_RESULT_COOLDOWN,
		DAILY_DECISIVE_CAP,
		DATABASE_UNAVAILABLE
	}

	public static int ratingTransfer(int winnerRating, int loserRating) {
		double difference = (double) loserRating - (double) winnerRating;
		double expected = 1.0D / (1.0D + Math.pow(10.0D,
			difference / (double) VoidArenaConfig.ELO_DIVISOR));
		int calculated = Math.max(1,
			(int) Math.round(VoidArenaConfig.ELO_K_FACTOR * (1.0D - expected)));
		return Math.min(calculated, Math.max(0, loserRating - 1));
	}

	public static boolean isDecisive(ResultType resultType) {
		return resultType == ResultType.DEATH || resultType == ResultType.FORFEIT;
	}

	public static boolean cooldownActive(long latestDecisiveEndedAt, long now) {
		return latestDecisiveEndedAt > 0
			&& latestDecisiveEndedAt + VoidArenaConfig.RATED_PAIR_COOLDOWN_MS > now;
	}

	public static boolean dailyCapReached(int decisiveResultsToday) {
		return decisiveResultsToday >= VoidArenaConfig.MAX_RATED_PAIR_RESULTS_PER_UTC_DAY;
	}

	public static long utcDayStart(long epochMillis) {
		return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
			.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
	}

	public static boolean samePublicNetwork(String first, String second) {
		byte[] firstAddress = parseAddress(first);
		byte[] secondAddress = parseAddress(second);
		if (firstAddress == null || secondAddress == null
			|| firstAddress.length != secondAddress.length
			|| !isPublicAddress(firstAddress) || !isPublicAddress(secondAddress)) {
			return false;
		}
		int prefixBytes = firstAddress.length == 16 ? 8 : firstAddress.length;
		for (int i = 0; i < prefixBytes; i++) {
			if (firstAddress[i] != secondAddress[i]) {
				return false;
			}
		}
		return true;
	}

	public static boolean sameAddress(String first, String second) {
		byte[] firstAddress = parseAddress(first);
		byte[] secondAddress = parseAddress(second);
		return firstAddress != null && secondAddress != null && Arrays.equals(firstAddress, secondAddress);
	}

	public static boolean isPublicAddress(String value) {
		byte[] address = parseAddress(value);
		return address != null && isPublicAddress(address);
	}

	public static boolean hasAmbiguousWebSocketOrigin(boolean webSocket, String remoteAddress,
		boolean allowAmbiguousProxyRanked) {
		return webSocket && !allowAmbiguousProxyRanked && !isPublicAddress(remoteAddress);
	}

	private static byte[] parseAddress(String value) {
		if (value == null || value.trim().isEmpty() || value.indexOf('%') >= 0) {
			return null;
		}
		try {
			InetAddress address = InetAddresses.forString(value.trim());
			byte[] bytes = address.getAddress();
			if (bytes.length == 16 && isIpv4Mapped(bytes)) {
				return Arrays.copyOfRange(bytes, 12, 16);
			}
			return bytes;
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static boolean isIpv4Mapped(byte[] bytes) {
		for (int i = 0; i < 10; i++) {
			if (bytes[i] != 0) {
				return false;
			}
		}
		return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
	}

	private static boolean isPublicAddress(byte[] bytes) {
		if (bytes.length == 4) {
			int a = bytes[0] & 0xff;
			int b = bytes[1] & 0xff;
			int c = bytes[2] & 0xff;
			return a != 0 && a != 10 && a != 127 && a < 224
				&& !(a == 100 && b >= 64 && b <= 127)
				&& !(a == 169 && b == 254)
				&& !(a == 172 && b >= 16 && b <= 31)
				&& !(a == 192 && b == 0 && (c == 0 || c == 2))
				&& !(a == 192 && b == 88 && c == 99)
				&& !(a == 192 && b == 168)
				&& !(a == 198 && (b == 18 || b == 19))
				&& !(a == 198 && b == 51 && c == 100)
				&& !(a == 203 && b == 0 && c == 113);
		}
		if (bytes.length != 16) {
			return false;
		}
		boolean allZero = true;
		for (byte value : bytes) {
			allZero &= value == 0;
		}
		if (allZero || isIpv6Loopback(bytes)) {
			return false;
		}
		int first = bytes[0] & 0xff;
		int second = bytes[1] & 0xff;
		if (first == 0xff || (first & 0xfe) == 0xfc
			|| (first == 0xfe && (second & 0xc0) == 0x80)) {
			return false;
		}
		return !(first == 0x20 && second == 0x01 && (bytes[2] & 0xff) == 0x0d
			&& (bytes[3] & 0xff) == 0xb8);
	}

	private static boolean isIpv6Loopback(byte[] bytes) {
		for (int i = 0; i < 15; i++) {
			if (bytes[i] != 0) {
				return false;
			}
		}
		return bytes[15] == 1;
	}

	private VoidArenaRankedPolicy() {
	}
}
