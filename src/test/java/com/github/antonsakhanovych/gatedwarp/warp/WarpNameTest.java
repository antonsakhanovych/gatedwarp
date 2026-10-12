package com.github.antonsakhanovych.gatedwarp.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpNameTest {
	@Test
	void acceptsLowercaseDigitsUnderscoreAndDash() {
		assertTrue(WarpName.isValid("spawn"));
		assertTrue(WarpName.isValid("base_2-north"));
		assertTrue(WarpName.isValid("a"));
		assertTrue(WarpName.isValid("a".repeat(32)));
	}

	@Test
	void rejectsEverythingElse() {
		assertFalse(WarpName.isValid(null));
		assertFalse(WarpName.isValid(""));
		assertFalse(WarpName.isValid("a".repeat(33)));
		assertFalse(WarpName.isValid("Spawn"));
		assertFalse(WarpName.isValid("my warp"));
		assertFalse(WarpName.isValid("a.b"));
		assertFalse(WarpName.isValid("a+b"));
		assertFalse(WarpName.isValid("../etc"));
		assertFalse(WarpName.isValid("spawn\n"));
		assertFalse(WarpName.isValid("café"));
	}
}
