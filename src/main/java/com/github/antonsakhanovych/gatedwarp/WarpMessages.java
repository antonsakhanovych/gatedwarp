package com.github.antonsakhanovych.gatedwarp;

import com.github.antonsakhanovych.gatedwarp.warp.Warp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Builds the chat components shown to players. */
public final class WarpMessages {
	private WarpMessages() {
	}

	public static MutableComponent info(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GREEN);
	}

	public static MutableComponent error(String text) {
		return Component.literal(text).withStyle(ChatFormatting.RED);
	}

	/** "overworld" for "minecraft:overworld", other namespaces stay intact. */
	public static String dimensionName(String dimension) {
		return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
	}

	public static String blockCoords(Warp warp) {
		return String.format(Locale.ROOT, "%d %d %d",
			(int) Math.floor(warp.x()), (int) Math.floor(warp.y()), (int) Math.floor(warp.z()));
	}

	public static String exactCoords(Warp warp) {
		return String.format(Locale.ROOT, "%.2f %.2f %.2f", warp.x(), warp.y(), warp.z());
	}

	/** Trims "4.0" to "4" so config radii read naturally. */
	public static String number(double value) {
		return value == Math.rint(value) ? String.format(Locale.ROOT, "%d", (long) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	public static Component warpList(List<Warp> warps) {
		if (warps.isEmpty()) {
			return Component.literal("No warps have been set yet.").withStyle(ChatFormatting.YELLOW);
		}
		MutableComponent list = Component.literal("Warps (" + warps.size() + "), click one to travel:")
			.withStyle(ChatFormatting.GOLD);
		for (Warp warp : warps) {
			list.append("\n").append(listEntry(warp));
		}
		return list;
	}

	private static Component listEntry(Warp warp) {
		MutableComponent line = Component.literal("  ")
			.withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand("/warp " + warp.name()))
				.withHoverEvent(new HoverEvent.ShowText(hover(warp))));
		line.append(Component.literal(warp.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE));
		if (!warp.description().isBlank()) {
			line.append(Component.literal(": " + warp.description()).withStyle(ChatFormatting.GRAY));
		}
		line.append(Component.literal(" (" + dimensionName(warp.dimension()) + ", " + blockCoords(warp) + ")")
			.withStyle(ChatFormatting.DARK_GRAY));
		return line;
	}

	private static Component hover(Warp warp) {
		MutableComponent tip = Component.literal(warp.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
		tip.append(Component.literal("\n" + (warp.description().isBlank() ? "No description" : warp.description()))
			.withStyle(ChatFormatting.GRAY));
		tip.append(Component.literal("\nDimension: ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(dimensionName(warp.dimension())).withStyle(ChatFormatting.WHITE));
		tip.append(Component.literal("\nCoordinates: ").withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(blockCoords(warp)).withStyle(ChatFormatting.WHITE));
		tip.append(Component.literal("\nClick to warp here").withStyle(ChatFormatting.YELLOW));
		return tip;
	}

	/** Full details for /warpinfo. {@code creatorName} may be a plain UUID when the player is offline. */
	public static Component warpInfo(Warp warp, String creatorName) {
		MutableComponent info = Component.literal("Warp ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(warp.name()).withStyle(ChatFormatting.AQUA));
		info.append(detail("Description", warp.description().isBlank() ? "none" : warp.description()));
		info.append(detail("Dimension", warp.dimension()));
		info.append(detail("Position", exactCoords(warp)));
		info.append(detail("Rotation", String.format(Locale.ROOT, "yaw %.1f, pitch %.1f", warp.yaw(), warp.pitch())));
		info.append(detail("Created by", creatorName));
		info.append(detail("Created at", Instant.ofEpochMilli(warp.createdAt()).toString()));
		return info;
	}

	private static Component detail(String label, String value) {
		return Component.literal("\n" + label + ": ").withStyle(ChatFormatting.DARK_GRAY)
			.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}
}
