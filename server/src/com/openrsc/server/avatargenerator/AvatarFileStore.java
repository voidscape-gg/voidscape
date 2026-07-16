package com.openrsc.server.avatargenerator;

import java.io.File;
import java.io.IOException;

final class AvatarFileStore {
	static File outputFile(String directoryPath, String databaseName, int playerId)
		throws IOException {
		File directory = new File(directoryPath);
		if (!directory.isDirectory()
			&& !directory.mkdirs()
			&& !directory.isDirectory()) {
			throw new IOException(
				"Could not create avatar directory: " + directory.getAbsolutePath());
		}
		return new File(directory, databaseName + "+" + playerId + ".png");
	}

	private AvatarFileStore() {
	}
}
