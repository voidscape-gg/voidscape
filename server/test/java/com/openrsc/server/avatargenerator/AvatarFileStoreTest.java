package com.openrsc.server.avatargenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class AvatarFileStoreTest {
	public static void main(String[] args) throws Exception {
		createsMissingRuntimeDirectory();
		rejectsNonDirectoryRuntimePath();
		System.out.println("Avatar file-store tests passed");
	}

	private static void createsMissingRuntimeDirectory() throws Exception {
		File root = Files.createTempDirectory("voidscape-avatar-store-").toFile();
		try {
			File directory = new File(root, "nested/avatars");
			File output = AvatarFileStore.outputFile(
				directory.getPath(), "voidscape", 42);

			assertTrue(directory.isDirectory(), "missing avatar directory was not created");
			assertEquals(directory.getCanonicalFile(), output.getParentFile().getCanonicalFile(),
				"avatar parent directory");
			assertEquals("voidscape+42.png", output.getName(), "avatar filename");
		} finally {
			deleteRecursively(root);
		}
	}

	private static void rejectsNonDirectoryRuntimePath() throws Exception {
		File path = File.createTempFile("voidscape-avatar-store-", ".tmp");
		try {
			try {
				AvatarFileStore.outputFile(path.getPath(), "voidscape", 1);
				throw new AssertionError("regular-file avatar path must fail closed");
			} catch (IOException expected) {
				assertTrue(expected.getMessage().contains("Could not create avatar directory"),
					"unexpected directory failure message: " + expected.getMessage());
			}
		} finally {
			path.delete();
		}
	}

	private static void deleteRecursively(File file) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				deleteRecursively(child);
			}
		}
		file.delete();
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(
				message + ": expected " + expected + ", got " + actual);
		}
	}

	private AvatarFileStoreTest() {
	}
}
