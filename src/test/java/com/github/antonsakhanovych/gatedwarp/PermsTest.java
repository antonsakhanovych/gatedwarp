package com.github.antonsakhanovych.gatedwarp;

import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.minecraft.server.permissions.PermissionLevel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The permission fallback: with no provider installed, the nodes mean "op level 2 or higher". */
class PermsTest {
	private static PermissionContext subjectAt(PermissionLevel level) {
		return PermissionContext.create(UUID.randomUUID(), PermissionContext.Type.PLAYER, level);
	}

	@Test
	void ordinaryPlayersAndModeratorsAreNotAdmins() {
		assertFalse(Perms.isAdmin(subjectAt(PermissionLevel.ALL)));
		assertFalse(Perms.isAdmin(subjectAt(PermissionLevel.MODERATORS)));
		assertFalse(Perms.canBypassLimits(subjectAt(PermissionLevel.ALL)));
		assertFalse(Perms.canBypassLimits(subjectAt(PermissionLevel.MODERATORS)));
	}

	@Test
	void everyoneMayUseWarpsByDefault() {
		assertTrue(Perms.canUse(subjectAt(PermissionLevel.ALL), false));
	}

	@Test
	void whenAGrantIsRequiredOnlyAdminsPassWithoutOne() {
		assertFalse(Perms.canUse(subjectAt(PermissionLevel.ALL), true));
		assertFalse(Perms.canUse(subjectAt(PermissionLevel.MODERATORS), true));
		assertTrue(Perms.canUse(subjectAt(PermissionLevel.GAMEMASTERS), true));
	}

	@Test
	void opLevelTwoAndAboveAreAdmins() {
		assertTrue(Perms.isAdmin(subjectAt(PermissionLevel.GAMEMASTERS)));
		assertTrue(Perms.isAdmin(subjectAt(PermissionLevel.ADMINS)));
		assertTrue(Perms.isAdmin(subjectAt(PermissionLevel.OWNERS)));
		assertTrue(Perms.canBypassLimits(subjectAt(PermissionLevel.GAMEMASTERS)));
	}
}
