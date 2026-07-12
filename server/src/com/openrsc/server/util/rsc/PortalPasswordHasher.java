package com.openrsc.server.util.rsc;

import com.openrsc.server.util.BCrypt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Minimal stdin-only bridge for portal-managed game password hashing. */
public final class PortalPasswordHasher {
	private PortalPasswordHasher() {}

	public static void main(String[] args) throws Exception {
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String mode = input.readLine();
		String password = decode(input.readLine());
		String salt = decode(input.readLine());
		String compatible = compatiblePassword(password, salt);
		if ("hash".equals(mode)) {
			System.out.print(BCrypt.hashpw(compatible, BCrypt.gensalt(10, new SecureRandom())));
			return;
		}
		if ("check".equals(mode)) {
			String encoded = decode(input.readLine());
			boolean matches = encoded.startsWith("$2y$10$")
				? BCrypt.checkpw(compatible, encoded)
				: compatible.equals(encoded);
			System.out.print(matches ? "true" : "false");
			return;
		}
		throw new IllegalArgumentException("unsupported_mode");
	}

	private static String decode(String value) {
		if (value == null) throw new IllegalArgumentException("missing_input");
		return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
	}

	private static String compatiblePassword(String password, String salt) throws Exception {
		if (salt == null || salt.isEmpty()) return password;
		return hex("SHA-512", salt + hex("MD5", password));
	}

	private static String hex(String algorithm, String value) throws Exception {
		byte[] bytes = MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8));
		StringBuilder output = new StringBuilder(bytes.length * 2);
		for (byte current : bytes) output.append(String.format("%02x", current & 0xff));
		return output.toString();
	}
}
