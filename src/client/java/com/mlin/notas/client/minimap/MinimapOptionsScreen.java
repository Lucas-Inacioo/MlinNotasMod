package com.mlin.notas.client.minimap;

import com.mlin.notas.minimap.MinimapOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Opcoes do minimapa: liga/desliga, modo caverna, filtros de entidades, tamanho e zoom.
 */
public class MinimapOptionsScreen extends Screen {
	private final Screen parent;
	private final MinimapOptions options;

	public MinimapOptionsScreen(Screen parent) {
		super(Component.translatable("gui.mlinnotas.minimap.options"));
		this.parent = parent;
		this.options = MinimapClient.options();
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		int y = 36;

		toggle(left, y, "enabled", options.enabled, v -> options.enabled = v);
		addRenderableWidget(CycleButton.<MinimapOptions.CaveMode>builder(mode ->
						Component.translatable("option.mlinnotas.minimap.cave_mode." + mode.name().toLowerCase()), options.caveMode)
				.withValues(MinimapOptions.CaveMode.values())
				.create(right, y, 150, 20, Component.translatable("option.mlinnotas.minimap.cave_mode"), (b, v) -> {
					options.caveMode = v;
					MinimapClient.cache().clearCaveLayers();
				}));
		y += 24;
		toggle(left, y, "players", options.showPlayers, v -> options.showPlayers = v);
		toggle(right, y, "hostiles", options.showHostiles, v -> options.showHostiles = v);
		y += 24;
		toggle(left, y, "passives", options.showPassives, v -> options.showPassives = v);
		toggle(right, y, "items", options.showItems, v -> options.showItems = v);
		y += 24;
		toggle(left, y, "villagers", options.showVillagers, v -> options.showVillagers = v);
		toggle(right, y, "waypoints", options.showWaypoints, v -> options.showWaypoints = v);
		y += 24;
		toggle(left, y, "coordinates", options.showCoordinates, v -> options.showCoordinates = v);
		y += 24;

		List<Integer> sizes = java.util.Arrays.stream(MinimapOptions.SIZES).boxed().toList();
		addRenderableWidget(CycleButton.<Integer>builder(size -> Component.literal(size + " px"), options.size)
				.withValues(sizes)
				.create(left, y, 150, 20, Component.translatable("option.mlinnotas.minimap.size"), (b, v) -> options.size = v));
		List<Float> zooms = new java.util.ArrayList<>();
		for (float z : MinimapOptions.ZOOMS) {
			zooms.add(z);
		}
		addRenderableWidget(CycleButton.<Float>builder(zoom -> Component.literal(zoom + "x"), options.zoom)
				.withValues(zooms)
				.create(right, y, 150, 20, Component.translatable("option.mlinnotas.minimap.zoom"), (b, v) -> options.zoom = v));
		y += 32;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 75, y, 150, 20).build());
	}

	private void toggle(int x, int y, String key, boolean initial, Consumer<Boolean> setter) {
		addRenderableWidget(CycleButton.onOffBuilder(initial)
				.create(x, y, 150, 20, Component.translatable("option.mlinnotas.minimap." + key), (b, v) -> setter.accept(v)));
	}

	@Override
	public void onClose() {
		MinimapClient.saveOptions();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
	}
}
