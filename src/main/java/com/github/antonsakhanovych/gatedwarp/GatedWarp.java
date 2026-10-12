package com.github.antonsakhanovych.gatedwarp;

import com.github.antonsakhanovych.gatedwarp.config.GatedWarpConfig;
import com.github.antonsakhanovych.gatedwarp.warp.WarpRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class GatedWarp implements ModInitializer {
	public static final String MOD_ID = "gatedwarp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		GatedWarpConfig config = GatedWarpConfig.load(configDir.resolve("gatedwarp.json"), LOGGER);
		WarpRegistry registry = new WarpRegistry(configDir.resolve("warps.json"), LOGGER);
		TeleportService teleports = new TeleportService(registry, config);
		Commands commands = new Commands(registry, new WarpAdmin(registry, LOGGER), teleports);

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> commands.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTING.register(server -> registry.load());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> teleports.clear());
		ServerTickEvents.END_SERVER_TICK.register(teleports::onTick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				teleports.onDamaged(player, damageTaken);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			teleports.onDisconnect(handler.getPlayer().getUUID(), server.getTickCount()));
	}
}
