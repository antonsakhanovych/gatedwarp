package com.github.antonsakhanovych.gatedwarp.warp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Owns the warp map and its persistence. All access is guarded by one lock. Mutations are applied
 * to a copy, written to disk, and only then published, so a failed write leaves memory unchanged.
 */
public final class WarpRegistry {
	public static final int FORMAT_VERSION = 1;

	public enum SetResult { CREATED, REPLACED, INVALID_NAME, IO_ERROR }

	public enum DeleteResult { DELETED, NOT_FOUND, IO_ERROR }

	public enum RenameResult { RENAMED, NOT_FOUND, TARGET_EXISTS, INVALID_NAME, IO_ERROR }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Logger logger;
	private final Object lock = new Object();
	private TreeMap<String, Warp> warps = new TreeMap<>();

	public WarpRegistry(Path file, Logger logger) {
		this.file = file;
		this.logger = logger;
	}

	/** Loads warps from disk. A missing file starts empty, a bad file is set aside and we start empty. */
	public void load() {
		synchronized (lock) {
			warps = new TreeMap<>();
			if (!Files.exists(file)) {
				logger.info("No warp file at {}, starting with no warps", file);
				return;
			}
			try {
				warps = parse(Files.readString(file, StandardCharsets.UTF_8));
				logger.info("Loaded {} warps from {}", warps.size(), file);
			} catch (IOException | JsonParseException | IllegalStateException e) {
				logger.error("Could not read warp file {}, starting with no warps: {}", file, e.getMessage());
				quarantine();
			}
		}
	}

	private TreeMap<String, Warp> parse(String json) {
		FileData data = GSON.fromJson(json, FileData.class);
		if (data == null) {
			throw new IllegalStateException("file is empty");
		}
		if (data.version != FORMAT_VERSION) {
			throw new IllegalStateException("unsupported format version " + data.version + ", expected " + FORMAT_VERSION);
		}
		TreeMap<String, Warp> loaded = new TreeMap<>();
		if (data.warps == null) {
			return loaded;
		}
		for (Map.Entry<String, StoredWarp> entry : data.warps.entrySet()) {
			Optional<Warp> warp = entry.getValue() == null ? Optional.empty() : entry.getValue().toWarp(entry.getKey());
			if (warp.isPresent()) {
				loaded.put(entry.getKey(), warp.get());
			} else {
				logger.warn("Skipping invalid warp entry '{}' in {}", entry.getKey(), file);
			}
		}
		return loaded;
	}

	/** Moves an unreadable file aside so the next save cannot silently destroy it. */
	private void quarantine() {
		Path broken = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
		try {
			Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
			logger.error("Moved the unreadable warp file to {}", broken);
		} catch (IOException e) {
			logger.error("Could not move the unreadable warp file aside: {}", e.getMessage());
		}
	}

	public Optional<Warp> get(String name) {
		synchronized (lock) {
			return Optional.ofNullable(warps.get(name));
		}
	}

	/** All warps sorted by name. */
	public List<Warp> all() {
		synchronized (lock) {
			return new ArrayList<>(warps.values());
		}
	}

	public List<String> names() {
		synchronized (lock) {
			return new ArrayList<>(warps.keySet());
		}
	}

	public Optional<Warp> nearest(String dimension, double x, double z, double radius) {
		synchronized (lock) {
			return WarpSnap.nearest(warps.values(), dimension, x, z, radius);
		}
	}

	public SetResult set(Warp warp) {
		if (!WarpName.isValid(warp.name())) {
			return SetResult.INVALID_NAME;
		}
		synchronized (lock) {
			TreeMap<String, Warp> next = new TreeMap<>(warps);
			boolean replaced = next.put(warp.name(), warp) != null;
			if (!save(next)) {
				return SetResult.IO_ERROR;
			}
			warps = next;
			return replaced ? SetResult.REPLACED : SetResult.CREATED;
		}
	}

	public DeleteResult delete(String name) {
		synchronized (lock) {
			if (!warps.containsKey(name)) {
				return DeleteResult.NOT_FOUND;
			}
			TreeMap<String, Warp> next = new TreeMap<>(warps);
			next.remove(name);
			if (!save(next)) {
				return DeleteResult.IO_ERROR;
			}
			warps = next;
			return DeleteResult.DELETED;
		}
	}

	public RenameResult rename(String oldName, String newName) {
		if (!WarpName.isValid(newName)) {
			return RenameResult.INVALID_NAME;
		}
		synchronized (lock) {
			Warp existing = warps.get(oldName);
			if (existing == null) {
				return RenameResult.NOT_FOUND;
			}
			if (warps.containsKey(newName)) {
				return RenameResult.TARGET_EXISTS;
			}
			TreeMap<String, Warp> next = new TreeMap<>(warps);
			next.remove(oldName);
			next.put(newName, existing.withName(newName));
			if (!save(next)) {
				return RenameResult.IO_ERROR;
			}
			warps = next;
			return RenameResult.RENAMED;
		}
	}

	/** Writes to a temp file in the same directory, then moves it over the target. */
	private boolean save(Map<String, Warp> toSave) {
		FileData data = new FileData();
		data.version = FORMAT_VERSION;
		data.warps = new TreeMap<>();
		toSave.forEach((name, warp) -> data.warps.put(name, StoredWarp.from(warp)));
		byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);

		Path directory = file.toAbsolutePath().getParent();
		Path temp = null;
		try {
			Files.createDirectories(directory);
			temp = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
			try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}
				channel.force(true);
			}
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			logger.error("Could not write warp file {}: {}", file, e.getMessage());
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// best effort cleanup of the temp file
				}
			}
			return false;
		}
	}

	/** On-disk shape of the whole file. */
	private static final class FileData {
		int version;
		Map<String, StoredWarp> warps;
	}

	/** On-disk shape of one warp, the name is the map key. */
	private static final class StoredWarp {
		String dimension;
		double x;
		double y;
		double z;
		float yaw;
		float pitch;
		String description;
		String creator;
		long createdAt;

		static StoredWarp from(Warp warp) {
			StoredWarp stored = new StoredWarp();
			stored.dimension = warp.dimension();
			stored.x = warp.x();
			stored.y = warp.y();
			stored.z = warp.z();
			stored.yaw = warp.yaw();
			stored.pitch = warp.pitch();
			stored.description = warp.description();
			stored.creator = warp.creator() == null ? null : warp.creator().toString();
			stored.createdAt = warp.createdAt();
			return stored;
		}

		Optional<Warp> toWarp(String name) {
			if (!WarpName.isValid(name) || dimension == null || dimension.isBlank()) {
				return Optional.empty();
			}
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				return Optional.empty();
			}
			UUID creatorId = null;
			if (creator != null) {
				try {
					creatorId = UUID.fromString(creator);
				} catch (IllegalArgumentException e) {
					return Optional.empty();
				}
			}
			return Optional.of(new Warp(name, dimension, x, y, z, yaw, pitch,
				description == null ? "" : description, creatorId, createdAt));
		}
	}
}
