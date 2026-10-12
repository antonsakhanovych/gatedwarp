package com.github.antonsakhanovych.gatedwarp.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Where the mod keeps its files: {@code config/gatedwarp/config.json} and {@code config/gatedwarp/warps.json}. */
public record DataFiles(Path directory) {
	public Path config() {
		return directory.resolve("config.json");
	}

	public Path warps() {
		return directory.resolve("warps.json");
	}

	/**
	 * Creates the mod's folder and moves files from where version 1.0.0 kept them
	 * ({@code config/gatedwarp.json} and {@code config/warps.json}), unless the new file already exists.
	 */
	public static DataFiles prepare(Path configDir, Logger logger) {
		DataFiles files = new DataFiles(configDir.resolve("gatedwarp"));
		try {
			Files.createDirectories(files.directory());
		} catch (IOException e) {
			logger.error("Could not create {}: {}", files.directory(), e.getMessage());
		}
		migrate(configDir.resolve("gatedwarp.json"), files.config(), logger);
		migrate(configDir.resolve("warps.json"), files.warps(), logger);
		return files;
	}

	private static void migrate(Path legacy, Path target, Logger logger) {
		if (!Files.exists(legacy) || Files.exists(target)) {
			return;
		}
		try {
			Files.move(legacy, target);
			logger.info("Moved {} to {}", legacy, target);
		} catch (IOException e) {
			logger.error("Could not move {} to {}: {}", legacy, target, e.getMessage());
		}
	}
}
