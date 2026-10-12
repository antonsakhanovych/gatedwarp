package com.github.antonsakhanovych.gatedwarp;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

/**
 * Permission nodes, via Fabric API's permission API. With no permission provider installed each
 * node falls back to vanilla op level 2 (gamemaster). A provider such as LuckPerms may grant or
 * revoke the nodes per player.
 */
public final class Perms {
	/** Create, delete, rename and inspect warps. */
	public static final Identifier ADMIN_NODE = Identifier.fromNamespaceAndPath(GatedWarp.MOD_ID, "admin");
	/** Skip the cooldown, the warmup and the combat tag. */
	public static final Identifier BYPASS_NODE = Identifier.fromNamespaceAndPath(GatedWarp.MOD_ID, "bypass");

	static final PermissionLevel FALLBACK = PermissionLevel.GAMEMASTERS;

	/** For {@code .requires()}: hides the command from tab completion and refuses execution. */
	public static final Predicate<CommandSourceStack> ADMIN = PermissionPredicates.require(ADMIN_NODE, FALLBACK);

	private Perms() {
	}

	public static boolean isAdmin(PermissionContextOwner subject) {
		return subject.checkPermission(ADMIN_NODE, FALLBACK);
	}

	public static boolean canBypassLimits(PermissionContextOwner subject) {
		return subject.checkPermission(BYPASS_NODE, FALLBACK);
	}
}
