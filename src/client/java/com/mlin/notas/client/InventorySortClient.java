package com.mlin.notas.client;

import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.sorting.SortRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Lado cliente do organizador: tecla R (no mundo ou numa tela de container) e botao "Ord" nas telas.
 * Em telas de container, R ordena o container e Shift+R o inventario do jogador.
 */
public final class InventorySortClient {
	private InventorySortClient() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			if (screen instanceof CreativeModeInventoryScreen) {
				return;
			}
			boolean playerOnly = screen instanceof InventoryScreen;
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
			int x = accessor.mlinnotas$leftPos() + accessor.mlinnotas$imageWidth() + 2;
			int y = accessor.mlinnotas$topPos();
			Button button = Button.builder(Component.translatable("button.mlinnotas.sort"), b -> send(!playerOnly))
					.bounds(x, y, 24, 14)
					.tooltip(Tooltip.create(Component.translatable(playerOnly ? "button.mlinnotas.sort.player" : "button.mlinnotas.sort.container")))
					.build();
			Screens.getWidgets(screen).add(button);

			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				if (!ModKeys.SORT.matches(event) || isTyping(s)) {
					return true;
				}
				send(!playerOnly && !event.hasShiftDown());
				return false;
			});
		});
	}

	/** Chamado a cada tick do cliente: tecla pressionada sem tela aberta ordena o inventario do jogador. */
	public static void tick() {
		while (ModKeys.SORT.consumeClick()) {
			send(false);
		}
	}

	private static boolean isTyping(Screen screen) {
		return screen.getFocused() instanceof EditBox box && box.isFocused();
	}

	public static void send(boolean container) {
		if (ClientPlayNetworking.canSend(SortRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new SortRequestPayload(container));
		}
	}
}
