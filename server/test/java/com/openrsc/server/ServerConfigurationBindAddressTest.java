package com.openrsc.server;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class ServerConfigurationBindAddressTest {
	public static void main(String[] args) throws Exception {
		missingAddressKeepsWildcardBinding();
		loopbackLiteralBindsOnlyIpv4Loopback();
		whitespaceIsNormalized();
		unsafeAddressesFailClosed();
		System.out.println("Server bind-address tests passed");
	}

	private static void missingAddressKeepsWildcardBinding() throws Exception {
		ServerConfiguration configuration = configured("");
		InetSocketAddress address = configuration.bindAddressForPort(43606);

		assertTrue(address.getAddress().isAnyLocalAddress(),
			"empty address must retain the legacy wildcard bind");
		assertEquals(43606, address.getPort(), "wildcard port");
	}

	private static void loopbackLiteralBindsOnlyIpv4Loopback() throws Exception {
		ServerConfiguration configuration = configured("127.0.0.1");
		InetSocketAddress address = configuration.bindAddressForPort(43506);

		assertEquals("127.0.0.1", address.getAddress().getHostAddress(),
			"explicit bind address");
		assertEquals(43506, address.getPort(), "loopback port");
	}

	private static void whitespaceIsNormalized() throws Exception {
		assertEquals("", ServerConfiguration.validateServerBindAddress(" \t "),
			"blank address normalization");
		assertEquals("127.0.0.1",
			ServerConfiguration.validateServerBindAddress(" 127.0.0.1 "),
			"loopback address normalization");
	}

	private static void unsafeAddressesFailClosed() throws Exception {
		String[] invalidAddresses = {
			null, "localhost", "0.0.0.0", "::", "::1", "127.0.0.2", "example.com"
		};
		for (String invalidAddress : invalidAddresses) {
			try {
				ServerConfiguration.validateServerBindAddress(invalidAddress);
				throw new AssertionError("unsafe address must fail closed: " + invalidAddress);
			} catch (IOException expected) {
				assertContains(expected.getMessage(), "127.0.0.1",
					"invalid address error");
			}
		}
	}

	private static ServerConfiguration configured(String address) throws IOException {
		ServerConfiguration configuration = new ServerConfiguration();
		configuration.SERVER_BIND_ADDRESS =
			ServerConfiguration.validateServerBindAddress(address);
		return configuration;
	}

	private static void assertContains(String actual, String expected, String message) {
		if (actual == null || !actual.contains(expected)) {
			throw new AssertionError(message + ": expected to contain " + expected
				+ ", got " + actual);
		}
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private ServerConfigurationBindAddressTest() {
	}
}
