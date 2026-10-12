package com.github.antonsakhanovych.gatedwarp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings from {@code config/gatedwarp.json}. Missing or invalid values fall back to the defaults. */
public final class GatedWarpConfig {
	public static final int VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	int version = VERSION;
	int cooldownSeconds = 30;
	int warmupSeconds = 3;
	int combatTagSeconds = 10;
	double snapRadius = 4.0;

	public int cooldownSeconds() {
		return cooldownSeconds;
	}

	public int warmupSeconds() {
		return warmupSeconds;
	}

	public int combatTagSeconds() {
		return combatTagSeconds;
	}

	public double snapRadius() {
		return snapRadius;
	}

	/** Reads the config, writing the defaults first if the file does not exist. Never throws. */
	public static GatedWarpConfig load(Path file, Logger logger) {
		if (!Files.exists(file)) {
			GatedWarpConfig defaults = new GatedWarpConfig();
			defaults.write(file, logger);
			return defaults;
		}
		try {
			GatedWarpConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GatedWarpConfig.class);
			if (loaded == null) {
				throw new JsonParseException("file is empty");
			}
			return loaded.sanitized(logger);
		} catch (IOException | JsonParseException e) {
			logger.error("Could not read config {}, using defaults: {}", file, e.getMessage());
			return new GatedWarpConfig();
		}
	}

	/** Clamps out-of-range values back to something safe, so a typo cannot disable the checks by accident. */
	GatedWarpConfig sanitized(Logger logger) {
		GatedWarpConfig defaults = new GatedWarpConfig();
		if (cooldownSeconds < 0) {
			logger.warn("cooldownSeconds must not be negative, using {}", defaults.cooldownSeconds);
			cooldownSeconds = defaults.cooldownSeconds;
		}
		if (warmupSeconds < 0) {
			logger.warn("warmupSeconds must not be negative, using {}", defaults.warmupSeconds);
			warmupSeconds = defaults.warmupSeconds;
		}
		if (combatTagSeconds < 0) {
			logger.warn("combatTagSeconds must not be negative, using {}", defaults.combatTagSeconds);
			combatTagSeconds = defaults.combatTagSeconds;
		}
		if (!Double.isFinite(snapRadius) || snapRadius <= 0) {
			logger.warn("snapRadius must be positive, using {}", defaults.snapRadius);
			snapRadius = defaults.snapRadius;
		}
		return this;
	}

	private void write(Path file, Logger logger) {
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.error("Could not write default config {}: {}", file, e.getMessage());
		}
	}
}
