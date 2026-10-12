package com.github.antonsakhanovych.gatedwarp.gametest;

import com.github.antonsakhanovych.gatedwarp.Perms;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.permission.v1.PermissionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the real mod on a real dedicated server with mock players. A mock player is an ordinary
 * non-op player, so these exercise the permission boundary through the actual Brigadier dispatcher.
 */
public class GatedWarpGameTests {
	private static final int WARMUP_AND_A_BIT = 70;

	/** What a command returns, or REFUSED when the dispatcher rejects it (unknown or not permitted). */
	private static final int REFUSED = -1;

	/** Players a stand-in permission provider grants both gatedwarp nodes to (what LuckPerms would do). */
	private static final Set<UUID> GRANTED = ConcurrentHashMap.newKeySet();

	static {
		PermissionEvents.ON_REQUEST.register(new PermissionEvents.OnRequest() {
			@Override
			public <T> T handlePermissionRequest(PermissionContext context, PermissionNode<T> node) {
				boolean ours = node.key().equals(Perms.ADMIN_NODE) || node.key().equals(Perms.BYPASS_NODE);
				return ours && GRANTED.contains(context.uuid()) ? node.cast(Boolean.TRUE) : null;
			}
		});
	}

	private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
		CommandDispatcher<CommandSourceStack> dispatcher = server(helper).getCommands().getDispatcher();
		try {
			return dispatcher.execute(command, source);
		} catch (CommandSyntaxException e) {
			return REFUSED;
		}
	}

	private static MinecraftServer server(GameTestHelper helper) {
		return helper.getLevel().getServer();
	}

	private static CommandSourceStack asOp(CommandSourceStack source) {
		return source.withPermission(LevelBasedPermissionSet.GAMEMASTER);
	}

	private static Set<String> usableRootCommands(GameTestHelper helper, CommandSourceStack source) {
		Set<String> usable = new TreeSet<>();
		for (CommandNode<CommandSourceStack> node : server(helper).getCommands().getDispatcher().getRoot().getChildren()) {
			if (node.canUse(source)) {
				usable.add(node.getName());
			}
		}
		return usable;
	}

	/** A mock player, parked at its own spot so one test's warp cannot be another's snap target. */
	// makeMockServerPlayerInLevel is the only mock with a network connection, which teleporting needs.
	// It is marked for removal upstream and has no replacement yet.
	@SuppressWarnings("removal")
	private static ServerPlayer mockPlayer(GameTestHelper helper, int slot) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Until the client reports it has loaded, the server treats the player as invulnerable.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setNoGravity(true);
		moveTo(helper, player, new Vec3(slot * 100.0, 64.0, 0.0));
		return player;
	}

	private static ServerPlayer nonOpPlayer(GameTestHelper helper, int slot) {
		ServerPlayer player = mockPlayer(helper, slot);
		if (Perms.isAdmin(player) || Perms.canBypassLimits(player)) {
			helper.fail("the mock player should not be an admin");
		}
		return player;
	}

	private static void moveTo(GameTestHelper helper, ServerPlayer player, Vec3 target) {
		player.teleportTo(helper.getLevel(), target.x, target.y, target.z, Set.of(), 0f, 0f, true);
	}

	private static String coords(Vec3 pos) {
		return String.format(Locale.ROOT, "%.3f %.3f %.3f", pos.x, pos.y, pos.z);
	}

	private static boolean at(ServerPlayer player, Vec3 target) {
		return player.position().distanceToSqr(target) < 0.01;
	}

	@GameTest
	public void nonOpSeesOnlyThePlayerCommands(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 1);
		Set<String> usable = usableRootCommands(helper, player.createCommandSourceStack());

		for (String name : new String[] {"warp", "warps", "warptp"}) {
			helper.assertTrue(usable.contains(name), "non-op should see /" + name);
		}
		for (String name : new String[] {"setwarp", "delwarp", "renamewarp", "warpinfo", "tp", "teleport"}) {
			helper.assertTrue(!usable.contains(name), "non-op must not see /" + name);
		}

		Set<String> opUsable = usableRootCommands(helper, asOp(player.createCommandSourceStack()));
		for (String name : new String[] {"warp", "warps", "warptp", "setwarp", "delwarp", "renamewarp", "warpinfo", "tp"}) {
			helper.assertTrue(opUsable.contains(name), "op should see /" + name);
		}
		helper.succeed();
	}

	@GameTest
	public void nonOpCannotRunAdminCommandsOrVanillaTp(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 2);
		CommandSourceStack source = player.createCommandSourceStack();
		String[] refused = {
			"setwarp gt-denied", "setwarp gt-denied some description", "delwarp gt-denied",
			"renamewarp gt-denied gt-denied2", "warpinfo gt-denied",
			"tp @s 0 100 0", "teleport @s 0 100 0",
		};
		for (String command : refused) {
			helper.assertTrue(run(helper, source, command) == REFUSED, "non-op must be refused: /" + command);
		}
		// Nothing was created by the refused /setwarp.
		helper.assertTrue(run(helper, source, "warp gt-denied") == 0, "the refused /setwarp must not have created a warp");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void nonOpWarpsAfterWarmupThenHitsCooldown(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 3);
		CommandSourceStack source = player.createCommandSourceStack();
		Vec3 warpPos = player.position();

		helper.assertTrue(run(helper, asOp(source), "setwarp gt-flow The flow test warp") == 1, "op /setwarp should work");
		helper.assertTrue(run(helper, source, "warps") == 1, "non-op /warps should work");

		Vec3 away = warpPos.add(10, 0, 0);
		moveTo(helper, player, away);
		helper.assertTrue(run(helper, source, "warp gt-flow") == 1, "non-op /warp should start the warmup");

		helper.runAfterDelay(40, () -> helper.assertTrue(at(player, away), "must still be waiting during the warmup"));
		helper.runAfterDelay(WARMUP_AND_A_BIT, () -> {
			helper.assertTrue(at(player, warpPos), "should have been teleported to the warp, now at " + player.position());
			helper.assertTrue(run(helper, source, "warp gt-flow") == 0, "a second warp right away should hit the cooldown");
			helper.assertTrue(run(helper, asOp(source), "delwarp gt-flow") == 1, "op /delwarp should work");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void warptpSnapsToTheStoredWarpNotTheClick(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 4);
		CommandSourceStack source = player.createCommandSourceStack();
		Vec3 warpPos = player.position();
		helper.assertTrue(run(helper, asOp(source), "setwarp gt-snap") == 1, "op /setwarp should work");

		Vec3 away = warpPos.add(10, 0, 0);
		moveTo(helper, player, away);
		// A click 2 blocks east and 1 south of the warp, at a nonsense height.
		String click = coords(new Vec3(warpPos.x + 2, 1.0, warpPos.z + 1));
		helper.assertTrue(run(helper, source, "warptp " + click) == 1, "a click within 4 blocks should snap");

		helper.runAfterDelay(WARMUP_AND_A_BIT, () -> {
			helper.assertTrue(at(player, warpPos), "must land on the stored warp position, now at " + player.position());
			helper.assertTrue(run(helper, asOp(source), "delwarp gt-snap") == 1, "op /delwarp should work");
			helper.succeed();
		});
	}

	@GameTest
	public void warptpFailsOutsideTheSnapRadius(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 5);
		CommandSourceStack source = player.createCommandSourceStack();
		Vec3 warpPos = player.position();
		helper.assertTrue(run(helper, asOp(source), "setwarp gt-far") == 1, "op /setwarp should work");

		String click = coords(new Vec3(warpPos.x + 10, warpPos.y, warpPos.z));
		helper.assertTrue(run(helper, source, "warptp " + click) == 0, "a click 10 blocks away must find no warp");
		helper.assertTrue(run(helper, asOp(source), "delwarp gt-far") == 1, "op /delwarp should work");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void damageCancelsTheWarmupAndStartsTheCombatTag(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 6);
		CommandSourceStack source = player.createCommandSourceStack();
		Vec3 warpPos = player.position();
		helper.assertTrue(run(helper, asOp(source), "setwarp gt-damage") == 1, "op /setwarp should work");

		Vec3 away = warpPos.add(10, 0, 0);
		moveTo(helper, player, away);
		helper.assertTrue(run(helper, source, "warp gt-damage") == 1, "non-op /warp should start the warmup");

		helper.runAfterDelay(10, () -> {
			// The mock player is in creative mode, so use damage that bypasses invulnerability.
			boolean hurt = player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1.0f);
			helper.assertTrue(hurt, "the mock player should have taken the damage");
		});
		helper.runAfterDelay(WARMUP_AND_A_BIT, () -> {
			helper.assertTrue(at(player, away), "taking damage must cancel the warp, now at " + player.position());
			helper.assertTrue(run(helper, source, "warp gt-damage") == 0, "the combat tag must block a new warp");
			helper.assertTrue(run(helper, asOp(source), "delwarp gt-damage") == 1, "op /delwarp should work");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void movingCancelsTheWarmup(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 7);
		CommandSourceStack source = player.createCommandSourceStack();
		Vec3 warpPos = player.position();
		helper.assertTrue(run(helper, asOp(source), "setwarp gt-move") == 1, "op /setwarp should work");

		Vec3 away = warpPos.add(10, 0, 0);
		moveTo(helper, player, away);
		helper.assertTrue(run(helper, source, "warp gt-move") == 1, "non-op /warp should start the warmup");

		Vec3 moved = away.add(3, 0, 0);
		helper.runAfterDelay(10, () -> moveTo(helper, player, moved));
		helper.runAfterDelay(WARMUP_AND_A_BIT, () -> {
			helper.assertTrue(at(player, moved), "moving must cancel the warp, now at " + player.position());
			helper.assertTrue(run(helper, asOp(source), "delwarp gt-move") == 1, "op /delwarp should work");
			helper.succeed();
		});
	}

	@GameTest
	public void playerGrantedTheNodesBypassesWarmupAndCooldown(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper, 8);
		GRANTED.add(player.getUUID());
		try {
			helper.assertTrue(Perms.isAdmin(player) && Perms.canBypassLimits(player), "a granted player should hold both nodes");
			CommandSourceStack source = player.createCommandSourceStack();
			Vec3 warpPos = player.position();
			helper.assertTrue(run(helper, source, "setwarp gt-granted") == 1, "granted /setwarp should work without op");

			for (int i = 0; i < 2; i++) {
				moveTo(helper, player, warpPos.add(10, 0, 0));
				helper.assertTrue(run(helper, source, "warp gt-granted") == 1, "granted /warp should succeed (attempt " + (i + 1) + ")");
				helper.assertTrue(at(player, warpPos), "granted warp should be instant with no cooldown, now at " + player.position());
			}
			helper.assertTrue(run(helper, source, "warpinfo gt-granted") == 1, "granted /warpinfo should work");
			helper.assertTrue(run(helper, source, "delwarp gt-granted") == 1, "granted /delwarp should work");
		} finally {
			GRANTED.remove(player.getUUID());
		}
		helper.succeed();
	}

	@GameTest
	public void opSourceCanRunEveryAdminCommand(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper, 9);
		CommandSourceStack op = asOp(player.createCommandSourceStack());

		helper.assertTrue(run(helper, op, "setwarp gt-admin first description") == 1, "/setwarp should create");
		helper.assertTrue(run(helper, op, "setwarp gt-admin second description") == 1, "/setwarp should overwrite");
		helper.assertTrue(run(helper, op, "setwarp gt-admin-other") == 1, "/setwarp should create a second warp");
		helper.assertTrue(run(helper, op, "warpinfo gt-admin") == 1, "/warpinfo should work");
		helper.assertTrue(run(helper, op, "renamewarp gt-admin gt-admin-other") == 0, "/renamewarp must refuse to clobber");
		helper.assertTrue(run(helper, op, "renamewarp gt-admin gt-renamed") == 1, "/renamewarp should rename");
		helper.assertTrue(run(helper, op, "setwarp Bad_Name") == 0, "/setwarp must reject an invalid name");
		helper.assertTrue(run(helper, op, "delwarp gt-admin") == 0, "the old name must be gone after the rename");
		helper.assertTrue(run(helper, op, "delwarp gt-renamed") == 1, "/delwarp should delete");
		helper.assertTrue(run(helper, op, "delwarp gt-admin-other") == 1, "/delwarp should delete the second warp");
		helper.succeed();
	}
}
