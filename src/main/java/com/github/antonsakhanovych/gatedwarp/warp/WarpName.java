package com.github.antonsakhanovych.gatedwarp.warp;

import java.util.regex.Pattern;

/** Warp name rules: lowercase letters, digits, underscore and dash, at most 32 characters. */
public final class WarpName {
	public static final int MAX_LENGTH = 32;
	private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1," + MAX_LENGTH + "}");

	private WarpName() {
	}

	public static boolean isValid(String name) {
		return name != null && VALID.matcher(name).matches();
	}
}
