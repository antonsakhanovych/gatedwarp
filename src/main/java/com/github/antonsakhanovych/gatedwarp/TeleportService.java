package com.github.antonsakhanovych.gatedwarp;

import com.github.antonsakhanovych.gatedwarp.config.GatedWarpConfig;
import com.github.antonsakhanovych.gatedwarp.warp.Warp;
import com.github.antonsakhanovych.gatedwarp.warp.WarpRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Holds cooldown, warmup and combat tag state and performs the teleport itself, so players never
 * need vanilla /tp permission. Everything here runs on the server thread (command handlers, the
 * tick event and the damage event all do), so the per-player maps need no locking.
 */
public final class TeleportService {
	private static final int TICKS_PER_SECOND = 20;
	private static final int PRUNE_INTERVAL_TICKS = 1200;
	/** How far a player may drift during warmup before it counts as moving. */
	private static final double MOVE_TOLERANCE_SQ = 0.1 * 0.1;

	private record Pending(String warpName, ResourceKey<Level> dimension, Vec3 origin, long endTick) {
	}

	private final WarpRegistry registry;
	private final GatedWarpConfig config;
	private final Map<UUID, Pending> pending = new HashMap<>();
	private final Map<UUID, Long> cooldownUntil = new HashMap<>();
	private final Map<UUID, Long> combatUntil = new HashMap<>();

	public TeleportService(WarpRegistry registry, GatedWarpConfig config) {
		this.registry = registry;
		this.config = config;
	}

	/** /warp. Returns 1 when a warp was started, 0 when it was refused. */
	public int warpByName(ServerPlayer player, String name) {
		return registry.get(name)
			.map(warp -> begin(player, warp))
			.orElseGet(() -> fail(player, "There is no warp called '" + name + "'. Use /warps to see the list."));
	}

	/** /warptp. Snaps the clicked spot to a warp, the stored warp position is what gets used. */
	public int warpNear(ServerPlayer player, Vec3 clicked) {
		String dimension = player.level().dimension().identifier().toString();
		double radius = config.snapRadius();
		return registry.nearest(dimension, clicked.x, clicked.z, radius)
			.map(warp -> begin(player, warp))
			.orElseGet(() -> fail(player, "No warp within " + WarpMessages.number(radius) + " blocks of that spot in this dimension."));
	}

	private int begin(ServerPlayer player, Warp warp) {
		MinecraftServer server = player.level().getServer();
		if (resolveLevel(server, warp) == null) {
			return fail(player, "The dimension of warp '" + warp.name() + "' (" + warp.dimension() + ") is not loaded.");
		}

		UUID id = player.getUUID();
		long now = server.getTickCount();
		boolean bypass = Perms.canBypassLimits(player);

		if (!bypass) {
			if (pending.containsKey(id)) {
				return fail(player, "You are already warping. Stand still or take damage to cancel.");
			}
			long combatLeft = combatUntil.getOrDefault(id, 0L) - now;
			if (combatLeft > 0) {
				return fail(player, "You were recently in combat. You can warp in " + secondsLeft(combatLeft) + "s.");
			}
			long cooldownLeft = cooldownUntil.getOrDefault(id, 0L) - now;
			if (cooldownLeft > 0) {
				return fail(player, "Warp is on cooldown for " + secondsLeft(cooldownLeft) + "s.");
			}
		}

		int warmupSeconds = bypass ? 0 : config.warmupSeconds();
		if (warmupSeconds == 0) {
			return teleport(player, warp, now);
		}
		pending.put(id, new Pending(warp.name(), player.level().dimension(), player.position(), now + (long) warmupSeconds * TICKS_PER_SECOND));
		player.sendSystemMessage(WarpMessages.info("Warping to " + warp.name() + " in " + warmupSeconds + "s. Stand still."));
		return 1;
	}

	/** Performs the teleport. Must be called on the server thread. */
	private int teleport(ServerPlayer player, Warp warp, long now) {
		ServerLevel target = resolveLevel(player.level().getServer(), warp);
		if (target == null) {
			return fail(player, "The dimension of warp '" + warp.name() + "' (" + warp.dimension() + ") is not loaded.");
		}
		boolean moved = player.teleportTo(target, warp.x(), warp.y(), warp.z(), Set.of(), warp.yaw(), warp.pitch(), true);
		if (!moved) {
			return fail(player, "The teleport to '" + warp.name() + "' was refused.");
		}
		player.resetFallDistance();
		if (!Perms.canBypassLimits(player)) {
			cooldownUntil.put(player.getUUID(), now + (long) config.cooldownSeconds() * TICKS_PER_SECOND);
		}
		player.sendSystemMessage(WarpMessages.info("Warped to " + warp.name() + "."));
		return 1;
	}

	private static ServerLevel resolveLevel(MinecraftServer server, Warp warp) {
		Identifier id = Identifier.tryParse(warp.dimension());
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	/** Ticks the warmups: cancels on movement, counts down, and teleports when the time is up. */
	public void onTick(MinecraftServer server) {
		long now = server.getTickCount();
		Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending warmup = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			if (!player.level().dimension().equals(warmup.dimension())
				|| player.position().distanceToSqr(warmup.origin()) > MOVE_TOLERANCE_SQ) {
				it.remove();
				cancelled(player, "you moved");
				continue;
			}
			long left = warmup.endTick() - now;
			if (left <= 0) {
				it.remove();
				complete(player, warmup, now);
			} else if (left % TICKS_PER_SECOND == 0) {
				player.sendSystemMessage(Component.literal("Warping to " + warmup.warpName() + " in " + (left / TICKS_PER_SECOND) + "s"), true);
			}
		}
		if (now % PRUNE_INTERVAL_TICKS == 0) {
			prune(now);
		}
	}

	private void complete(ServerPlayer player, Pending warmup, long now) {
		registry.get(warmup.warpName()).ifPresentOrElse(
			warp -> teleport(player, warp, now),
			() -> fail(player, "Warp '" + warmup.warpName() + "' no longer exists."));
	}

	/** Starts the combat tag and cancels any warmup. Called for every damage a player takes. */
	public void onDamaged(ServerPlayer player, float damageTaken) {
		if (damageTaken <= 0) {
			return;
		}
		long now = player.level().getServer().getTickCount();
		if (config.combatTagSeconds() > 0) {
			combatUntil.put(player.getUUID(), now + (long) config.combatTagSeconds() * TICKS_PER_SECOND);
		}
		if (pending.remove(player.getUUID()) != null) {
			cancelled(player, "you took damage");
		}
	}

	/**
	 * Drops a leaving player's warmup. Cooldown and combat tag are kept until they expire on their
	 * own, otherwise reconnecting would be a free way around both.
	 */
	public void onDisconnect(UUID id, long now) {
		pending.remove(id);
		if (cooldownUntil.getOrDefault(id, 0L) <= now) {
			cooldownUntil.remove(id);
		}
		if (combatUntil.getOrDefault(id, 0L) <= now) {
			combatUntil.remove(id);
		}
	}

	/** Forgets everything, used when the server stops. */
	public void clear() {
		pending.clear();
		cooldownUntil.clear();
		combatUntil.clear();
	}

	private void prune(long now) {
		cooldownUntil.values().removeIf(until -> until <= now);
		combatUntil.values().removeIf(until -> until <= now);
	}

	private static void cancelled(ServerPlayer player, String reason) {
		player.sendSystemMessage(WarpMessages.error("Warp cancelled: " + reason + "."));
	}

	private static int fail(ServerPlayer player, String message) {
		player.sendSystemMessage(WarpMessages.error(message));
		return 0;
	}

	private static long secondsLeft(long ticks) {
		return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
	}
}
