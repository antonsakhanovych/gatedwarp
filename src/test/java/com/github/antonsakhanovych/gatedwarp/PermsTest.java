package com.github.antonsakhanovych.gatedwarp;

import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermsTest {
	private static boolean adminAt(PermissionLevel level) {
		return Perms.isAdmin(LevelBasedPermissionSet.forLevel(level));
	}

	@Test
	void ordinaryPlayersAndModeratorsAreNotAdmins() {
		assertFalse(adminAt(PermissionLevel.ALL));
		assertFalse(adminAt(PermissionLevel.MODERATORS));
		assertFalse(Perms.isAdmin(PermissionSet.NO_PERMISSIONS));
	}

	@Test
	void opLevelTwoAndAboveAreAdmins() {
		assertTrue(adminAt(PermissionLevel.GAMEMASTERS));
		assertTrue(adminAt(PermissionLevel.ADMINS));
		assertTrue(adminAt(PermissionLevel.OWNERS));
		assertTrue(Perms.isAdmin(PermissionSet.ALL_PERMISSIONS));
	}
}
