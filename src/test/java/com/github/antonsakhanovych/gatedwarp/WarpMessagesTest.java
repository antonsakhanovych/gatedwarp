package com.github.antonsakhanovych.gatedwarp;

import com.github.antonsakhanovych.gatedwarp.warp.Warp;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpMessagesTest {
	private static Warp warp(String name, String description) {
		return new Warp(name, "minecraft:the_nether", 10.5, 64, -20.5, 0f, 0f, description, null, 0L);
	}

	@Test
	void everyEntryRunsWarpOnClickAndShowsATooltip() {
		Component list = WarpMessages.warpList(List.of(warp("alpha", "First"), warp("beta", "")));

		List<Component> entries = list.getSiblings().stream().filter(c -> c.getStyle().getClickEvent() != null).toList();
		assertEquals(2, entries.size());

		ClickEvent.RunCommand click = assertInstanceOf(ClickEvent.RunCommand.class, entries.get(0).getStyle().getClickEvent());
		assertEquals("/warp alpha", click.command());
		assertEquals("/warp beta", assertInstanceOf(ClickEvent.RunCommand.class, entries.get(1).getStyle().getClickEvent()).command());

		HoverEvent.ShowText hover = assertInstanceOf(HoverEvent.ShowText.class, entries.get(0).getStyle().getHoverEvent());
		String tooltip = hover.value().getString();
		assertTrue(tooltip.contains("First"), tooltip);
		assertTrue(tooltip.contains("the_nether"), tooltip);
		assertTrue(tooltip.contains("10 64 -21"), tooltip);
	}

	@Test
	void entriesShowNameDescriptionDimensionAndCoordinates() {
		String text = WarpMessages.warpList(List.of(warp("alpha", "The first one"))).getString();
		assertTrue(text.contains("alpha: The first one (the_nether, 10 64 -21)"), text);
	}

	@Test
	void keepsTheOrderItIsGivenSoCallersControlSorting() {
		String text = WarpMessages.warpList(List.of(warp("alpha", ""), warp("beta", ""), warp("gamma", ""))).getString();
		assertTrue(text.indexOf("alpha") < text.indexOf("beta") && text.indexOf("beta") < text.indexOf("gamma"), text);
	}

	@Test
	void emptyListSaysSo() {
		assertEquals("No warps have been set yet.", WarpMessages.warpList(List.of()).getString());
	}

	@Test
	void dimensionNamesDropOnlyTheVanillaNamespace() {
		assertEquals("overworld", WarpMessages.dimensionName("minecraft:overworld"));
		assertEquals("mymod:caves", WarpMessages.dimensionName("mymod:caves"));
	}
}
