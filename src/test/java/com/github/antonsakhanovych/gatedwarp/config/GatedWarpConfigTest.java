package com.github.antonsakhanovych.gatedwarp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatedWarpConfigTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("gatedwarp-test");

	@TempDir
	Path dir;

	@Test
	void missingFileWritesAndReturnsDefaults() {
		Path file = dir.resolve("gatedwarp.json");
		GatedWarpConfig config = GatedWarpConfig.load(file, LOGGER);

		assertEquals(30, config.cooldownSeconds());
		assertEquals(3, config.warmupSeconds());
		assertEquals(10, config.combatTagSeconds());
		assertEquals(4.0, config.snapRadius());
		assertTrue(Files.exists(file));
	}

	@Test
	void partialFileKeepsDefaultsForMissingKeys() throws IOException {
		Path file = dir.resolve("gatedwarp.json");
		Files.writeString(file, "{\"warmupSeconds\": 7, \"snapRadius\": 2.5}");
		GatedWarpConfig config = GatedWarpConfig.load(file, LOGGER);

		assertEquals(7, config.warmupSeconds());
		assertEquals(2.5, config.snapRadius());
		assertEquals(30, config.cooldownSeconds());
	}

	@Test
	void invalidValuesFallBackToDefaults() throws IOException {
		Path file = dir.resolve("gatedwarp.json");
		Files.writeString(file, "{\"cooldownSeconds\": -5, \"snapRadius\": 0}");
		GatedWarpConfig config = GatedWarpConfig.load(file, LOGGER);

		assertEquals(30, config.cooldownSeconds());
		assertEquals(4.0, config.snapRadius());
	}

	@Test
	void malformedFileFallsBackToDefaults() throws IOException {
		Path file = dir.resolve("gatedwarp.json");
		Files.writeString(file, "not json at all");
		GatedWarpConfig config = GatedWarpConfig.load(file, LOGGER);

		assertEquals(3, config.warmupSeconds());
	}
}
