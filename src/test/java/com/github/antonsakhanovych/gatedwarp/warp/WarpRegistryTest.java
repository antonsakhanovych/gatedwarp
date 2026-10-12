package com.github.antonsakhanovych.gatedwarp.warp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpRegistryTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("gatedwarp-test");

	@TempDir
	Path dir;

	private WarpRegistry registry() {
		return new WarpRegistry(dir.resolve("warps.json"), LOGGER);
	}

	private static Warp warp(String name) {
		return new Warp(name, "minecraft:the_nether", 10.5, 64.0, -20.25, 90.5f, -15f,
			"A \"quoted\" description", UUID.fromString("11111111-2222-3333-4444-555555555555"), 1_700_000_000_000L);
	}

	@Test
	void roundTripsThroughDisk() {
		WarpRegistry first = registry();
		assertEquals(WarpRegistry.SetResult.CREATED, first.set(warp("spawn")));
		assertEquals(WarpRegistry.SetResult.CREATED, first.set(warp("base")));

		WarpRegistry second = registry();
		second.load();
		assertEquals(first.all(), second.all());
		assertEquals(warp("spawn"), second.get("spawn").orElseThrow());
	}

	@Test
	void listsSortedByName() {
		WarpRegistry registry = registry();
		registry.set(warp("zeta"));
		registry.set(warp("alpha"));
		registry.set(warp("mid"));
		assertEquals(List.of("alpha", "mid", "zeta"), registry.names());
	}

	@Test
	void setOverwritesExistingName() {
		WarpRegistry registry = registry();
		registry.set(warp("spawn"));
		Warp moved = new Warp("spawn", "minecraft:overworld", 1, 2, 3, 0, 0, "new", null, 5L);
		assertEquals(WarpRegistry.SetResult.REPLACED, registry.set(moved));
		assertEquals(moved, registry.get("spawn").orElseThrow());
	}

	@Test
	void rejectsInvalidNames() {
		WarpRegistry registry = registry();
		assertEquals(WarpRegistry.SetResult.INVALID_NAME, registry.set(warp("Bad Name")));
		registry.set(warp("ok"));
		assertEquals(WarpRegistry.RenameResult.INVALID_NAME, registry.rename("ok", "Bad"));
		assertEquals(List.of("ok"), registry.names());
	}

	@Test
	void deleteRemovesAndPersists() {
		WarpRegistry registry = registry();
		registry.set(warp("spawn"));
		assertEquals(WarpRegistry.DeleteResult.NOT_FOUND, registry.delete("nope"));
		assertEquals(WarpRegistry.DeleteResult.DELETED, registry.delete("spawn"));

		WarpRegistry reloaded = registry();
		reloaded.load();
		assertTrue(reloaded.all().isEmpty());
	}

	@Test
	void renameMovesWarpAndRefusesToClobber() {
		WarpRegistry registry = registry();
		registry.set(warp("old"));
		registry.set(warp("taken"));

		assertEquals(WarpRegistry.RenameResult.TARGET_EXISTS, registry.rename("old", "taken"));
		assertEquals(WarpRegistry.RenameResult.NOT_FOUND, registry.rename("ghost", "fresh"));
		assertEquals(WarpRegistry.RenameResult.RENAMED, registry.rename("old", "fresh"));

		assertFalse(registry.get("old").isPresent());
		assertEquals("fresh", registry.get("fresh").orElseThrow().name());
		assertEquals(warp("old").withName("fresh"), registry.get("fresh").orElseThrow());
	}

	@Test
	void missingFileStartsEmpty() {
		WarpRegistry registry = registry();
		registry.load();
		assertTrue(registry.all().isEmpty());
	}

	@Test
	void malformedFileStartsEmptyAndIsSetAside() throws IOException {
		Path file = dir.resolve("warps.json");
		Files.writeString(file, "{ this is not json");

		WarpRegistry registry = registry();
		registry.load();
		assertTrue(registry.all().isEmpty());
		assertFalse(Files.exists(file));
		assertEquals(1, brokenCopies());
	}

	@Test
	void newerFormatVersionIsNotOverwritten() throws IOException {
		Files.writeString(dir.resolve("warps.json"), "{\"version\": 99, \"warps\": {}}");

		WarpRegistry registry = registry();
		registry.load();
		assertTrue(registry.all().isEmpty());
		assertEquals(1, brokenCopies());
	}

	@Test
	void invalidEntriesAreSkippedButGoodOnesLoad() throws IOException {
		Files.writeString(dir.resolve("warps.json"), """
			{"version": 1, "warps": {
				"good": {"dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3, "yaw": 0, "pitch": 0,
					"description": "ok", "creator": "11111111-2222-3333-4444-555555555555", "createdAt": 7},
				"Bad Name": {"dimension": "minecraft:overworld", "x": 1, "y": 2, "z": 3},
				"nodim": {"x": 1, "y": 2, "z": 3}
			}}""");

		WarpRegistry registry = registry();
		registry.load();
		assertEquals(List.of("good"), registry.names());
	}

	@Test
	void savedFileCarriesVersionAndLeavesNoTempFiles() throws IOException {
		WarpRegistry registry = registry();
		registry.set(warp("spawn"));

		String json = Files.readString(dir.resolve("warps.json"));
		assertTrue(json.contains("\"version\": 1"));
		try (Stream<Path> files = Files.list(dir)) {
			assertEquals(List.of("warps.json"), files.map(p -> p.getFileName().toString()).toList());
		}
	}

	private long brokenCopies() throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.getFileName().toString().startsWith("warps.json.broken-")).count();
		}
	}
}
