package com.github.antonsakhanovych.gatedwarp;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

import java.util.function.Predicate;

/**
 * The one place that decides who counts as an admin: vanilla op level 2 (gamemaster).
 * Used both for hiding commands via {@code requires()} and for bypassing warp limits.
 */
public final class Perms {
	/** For {@code .requires()}: hides the command from tab completion and refuses execution. */
	public static final Predicate<CommandSourceStack> ADMIN = source -> isAdmin(source.permissions());

	private Perms() {
	}

	public static boolean isAdmin(PermissionSet permissions) {
		return permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
