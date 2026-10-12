package com.github.antonsakhanovych.gatedwarp.warp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpSnapTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	private static Warp warp(String name, String dimension, double x, double y, double z) {
		return new Warp(name, dimension, x, y, z, 0f, 0f, "", null, 0L);
	}

	@Test
	void picksNearestWithinRadius() {
		List<Warp> warps = List.of(warp("far", OVERWORLD, 3, 64, 0), warp("near", OVERWORLD, 1, 64, 0));
		assertEquals("near", WarpSnap.nearest(warps, OVERWORLD, 0, 0, 4).orElseThrow().name());
	}

	@Test
	void radiusIsInclusiveAndOutsideIsEmpty() {
		List<Warp> warps = List.of(warp("edge", OVERWORLD, 4, 64, 0));
		assertTrue(WarpSnap.nearest(warps, OVERWORLD, 0, 0, 4).isPresent());
		assertEquals(Optional.empty(), WarpSnap.nearest(warps, OVERWORLD, 0, 0, 3.99));
	}

	@Test
	void ignoresHeight() {
		List<Warp> warps = List.of(warp("high", OVERWORLD, 1, 300, 1));
		assertTrue(WarpSnap.nearest(warps, OVERWORLD, 0, 0, 4).isPresent());
	}

	@Test
	void usesHorizontalDistanceNotPerAxis() {
		List<Warp> warps = List.of(warp("diag", OVERWORLD, 3, 64, 3));
		assertEquals(Optional.empty(), WarpSnap.nearest(warps, OVERWORLD, 0, 0, 4));
	}

	@Test
	void ignoresOtherDimensions() {
		List<Warp> warps = List.of(warp("nether-base", NETHER, 0, 64, 0));
		assertEquals(Optional.empty(), WarpSnap.nearest(warps, OVERWORLD, 0, 0, 4));
		assertTrue(WarpSnap.nearest(warps, NETHER, 0, 0, 4).isPresent());
	}

	@Test
	void tiesBreakByName() {
		List<Warp> warps = List.of(warp("b", OVERWORLD, 1, 64, 0), warp("a", OVERWORLD, -1, 64, 0));
		assertEquals("a", WarpSnap.nearest(warps, OVERWORLD, 0, 0, 4).orElseThrow().name());
	}

	@Test
	void emptyWhenNoWarps() {
		assertEquals(Optional.empty(), WarpSnap.nearest(List.of(), OVERWORLD, 0, 0, 4));
	}
}
