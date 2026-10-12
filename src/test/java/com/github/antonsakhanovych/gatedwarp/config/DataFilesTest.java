package com.github.antonsakhanovych.gatedwarp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataFilesTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("gatedwarp-test");

	@TempDir
	Path configDir;

	@Test
	void usesTheModFolderAndCreatesIt() {
		DataFiles files = DataFiles.prepare(configDir, LOGGER);

		assertEquals(configDir.resolve("gatedwarp"), files.directory());
		assertEquals(configDir.resolve("gatedwarp/config.json"), files.config());
		assertEquals(configDir.resolve("gatedwarp/warps.json"), files.warps());
		assertTrue(Files.isDirectory(files.directory()));
	}

	@Test
	void movesFilesFromTheOldLocations() throws IOException {
		Files.writeString(configDir.resolve("gatedwarp.json"), "old config");
		Files.writeString(configDir.resolve("warps.json"), "old warps");

		DataFiles files = DataFiles.prepare(configDir, LOGGER);

		assertEquals("old config", Files.readString(files.config()));
		assertEquals("old warps", Files.readString(files.warps()));
		assertFalse(Files.exists(configDir.resolve("gatedwarp.json")));
		assertFalse(Files.exists(configDir.resolve("warps.json")));
	}

	@Test
	void neverOverwritesAFileInTheNewFolder() throws IOException {
		Files.createDirectories(configDir.resolve("gatedwarp"));
		Files.writeString(configDir.resolve("gatedwarp/warps.json"), "new warps");
		Files.writeString(configDir.resolve("warps.json"), "old warps");

		DataFiles files = DataFiles.prepare(configDir, LOGGER);

		assertEquals("new warps", Files.readString(files.warps()));
		assertEquals("old warps", Files.readString(configDir.resolve("warps.json")));
	}

	@Test
	void doesNothingWhenThereIsNothingToMove() {
		DataFiles files = DataFiles.prepare(configDir, LOGGER);

		assertFalse(Files.exists(files.config()));
		assertFalse(Files.exists(files.warps()));
	}
}
