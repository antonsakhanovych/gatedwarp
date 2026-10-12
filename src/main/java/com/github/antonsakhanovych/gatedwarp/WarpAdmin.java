package com.github.antonsakhanovych.gatedwarp;

import com.github.antonsakhanovych.gatedwarp.warp.Warp;
import com.github.antonsakhanovych.gatedwarp.warp.WarpName;
import com.github.antonsakhanovych.gatedwarp.warp.WarpRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.UUID;

/** The op-only operations behind /setwarp, /delwarp, /renamewarp and /warpinfo. */
public final class WarpAdmin {
	static final int MAX_DESCRIPTION_LENGTH = 200;

	private final WarpRegistry registry;
	private final Logger logger;

	public WarpAdmin(WarpRegistry registry, Logger logger) {
		this.registry = registry;
		this.logger = logger;
	}

	public int set(CommandSourceStack source, String name, String description) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return fail(source, "Only a player can set a warp, it uses your position.");
		}
		if (!WarpName.isValid(name)) {
			return fail(source, invalidName());
		}
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			return fail(source, "The description is too long, the limit is " + MAX_DESCRIPTION_LENGTH + " characters.");
		}
		Warp warp = new Warp(name, player.level().dimension().identifier().toString(),
			player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
			description, player.getUUID(), System.currentTimeMillis());

		WarpRegistry.SetResult result = registry.set(warp);
		return switch (result) {
			case CREATED, REPLACED -> {
				String verb = result == WarpRegistry.SetResult.CREATED ? "created" : "overwrote";
				done(source, verb + " warp '" + name + "' at " + WarpMessages.blockCoords(warp)
					+ " in " + WarpMessages.dimensionName(warp.dimension()));
				yield 1;
			}
			case INVALID_NAME -> fail(source, invalidName());
			case IO_ERROR -> fail(source, saveFailed());
		};
	}

	public int delete(CommandSourceStack source, String name) {
		return switch (registry.delete(name)) {
			case DELETED -> {
				done(source, "deleted warp '" + name + "'");
				yield 1;
			}
			case NOT_FOUND -> fail(source, "There is no warp called '" + name + "'.");
			case IO_ERROR -> fail(source, saveFailed());
		};
	}

	public int rename(CommandSourceStack source, String oldName, String newName) {
		return switch (registry.rename(oldName, newName)) {
			case RENAMED -> {
				done(source, "renamed warp '" + oldName + "' to '" + newName + "'");
				yield 1;
			}
			case NOT_FOUND -> fail(source, "There is no warp called '" + oldName + "'.");
			case TARGET_EXISTS -> fail(source, "A warp called '" + newName + "' already exists, delete it first.");
			case INVALID_NAME -> fail(source, invalidName());
			case IO_ERROR -> fail(source, saveFailed());
		};
	}

	public int info(CommandSourceStack source, String name) {
		return registry.get(name)
			.map(warp -> {
				source.sendSuccess(() -> WarpMessages.warpInfo(warp, creatorName(source.getServer(), warp.creator())), false);
				return 1;
			})
			.orElseGet(() -> fail(source, "There is no warp called '" + name + "'."));
	}

	private static String creatorName(MinecraftServer server, UUID creator) {
		if (creator == null) {
			return "unknown";
		}
		ServerPlayer online = server.getPlayerList().getPlayer(creator);
		return online == null ? creator.toString() : online.getName().getString() + " (" + creator + ")";
	}

	/** Tells the executor, the other online ops and the server log. */
	private void done(CommandSourceStack source, String what) {
		String line = source.getTextName() + " " + what;
		logger.info("{}", line);
		source.sendSuccess(() -> Component.literal("You " + what).withStyle(ChatFormatting.GREEN), false);

		Component broadcast = Component.literal("[GatedWarp] " + line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		ServerPlayer actor = source.getPlayer();
		for (ServerPlayer other : source.getServer().getPlayerList().getPlayers()) {
			if (other != actor && Perms.isAdmin(other)) {
				other.sendSystemMessage(broadcast);
			}
		}
	}

	private static int fail(CommandSourceStack source, String message) {
		source.sendFailure(Component.literal(message));
		return 0;
	}

	private static String invalidName() {
		return "Warp names may only use lowercase letters, digits, '_' and '-', up to " + WarpName.MAX_LENGTH + " characters.";
	}

	private static String saveFailed() {
		return "The warp file could not be written, nothing was changed. Check the server log.";
	}
}
