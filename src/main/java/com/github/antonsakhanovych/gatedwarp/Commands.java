package com.github.antonsakhanovych.gatedwarp;

import com.github.antonsakhanovych.gatedwarp.warp.WarpRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Registers the Brigadier nodes and nothing else. Op-only commands carry {@link Perms#ADMIN} in
 * {@code requires()}, so they are hidden from tab completion and refused on execution. The player
 * commands use the {@code canUse} predicate, which lets everyone in unless the config asks for an
 * explicit grant of the use permission.
 */
public final class Commands {
	private final WarpRegistry registry;
	private final WarpAdmin admin;
	private final TeleportService teleports;
	private final Predicate<CommandSourceStack> canUse;

	public Commands(WarpRegistry registry, WarpAdmin admin, TeleportService teleports, Predicate<CommandSourceStack> canUse) {
		this.registry = registry;
		this.admin = admin;
		this.teleports = teleports;
		this.canUse = canUse;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		SuggestionProvider<CommandSourceStack> warpNames =
			(context, builder) -> SharedSuggestionProvider.suggest(registry.names(), builder);

		// Player commands.
		dispatcher.register(literal("warp")
			.requires(canUse)
			.then(warpName("name", warpNames)
				.executes(context -> withPlayer(context, player -> teleports.warpByName(player, name(context))))));

		dispatcher.register(literal("warps")
			.requires(canUse)
			.executes(context -> list(context)));

		dispatcher.register(literal("warptp")
			.requires(canUse)
			.then(argument("pos", Vec3Argument.vec3(false))
				.executes(context -> withPlayer(context, player ->
					teleports.warpNear(player, Vec3Argument.getVec3(context, "pos"))))));

		// Op-only commands.
		dispatcher.register(literal("setwarp")
			.requires(Perms.ADMIN)
			.then(argument("name", StringArgumentType.word())
				.executes(context -> admin.set(context.getSource(), name(context), ""))
				.then(argument("description", StringArgumentType.greedyString())
					.executes(context -> admin.set(context.getSource(), name(context),
						StringArgumentType.getString(context, "description"))))));

		dispatcher.register(literal("delwarp")
			.requires(Perms.ADMIN)
			.then(warpName("name", warpNames)
				.executes(context -> admin.delete(context.getSource(), name(context)))));

		dispatcher.register(literal("renamewarp")
			.requires(Perms.ADMIN)
			.then(warpName("old", warpNames)
				.then(argument("new", StringArgumentType.word())
					.executes(context -> admin.rename(context.getSource(),
						StringArgumentType.getString(context, "old"),
						StringArgumentType.getString(context, "new"))))));

		dispatcher.register(literal("warpinfo")
			.requires(Perms.ADMIN)
			.then(warpName("name", warpNames)
				.executes(context -> admin.info(context.getSource(), name(context)))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> warpName(String argumentName, SuggestionProvider<CommandSourceStack> suggestions) {
		return argument(argumentName, StringArgumentType.word()).suggests(suggestions);
	}

	private static String name(CommandContext<CommandSourceStack> context) {
		return StringArgumentType.getString(context, "name");
	}

	private int list(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> WarpMessages.warpList(registry.all()), false);
		return 1;
	}

	private interface PlayerAction {
		int run(ServerPlayer player);
	}

	private static int withPlayer(CommandContext<CommandSourceStack> context, PlayerAction action) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(WarpMessages.error("Only a player can use this command."));
			return 0;
		}
		return action.run(player);
	}
}
