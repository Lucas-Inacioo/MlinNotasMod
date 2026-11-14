package com.mlin.notas.client;

import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.client.screen.BackpackScreen;
import com.mlin.notas.client.screen.BigChestScreen;
import com.mlin.notas.sorting.QuickStackPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;

/**
 * Guardar rapido no cliente: botao logo abaixo do "Ord" nas telas de bau (bau, barril, bau do fim, caixa de shulker,
 * baus grandes do mod, mochila) e a tecla {@link ModKeys#QUICK_STACK} (X) nessas telas. O servidor faz o resto
 * ({@code sorting/QuickStackServer}).
 */
public final class QuickStackClient {
	private QuickStackClient() {
	}

	/** Telas que ganham o botao. */
	public static boolean supports(Screen screen) {
		return screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen || screen instanceof BigChestScreen
				|| screen instanceof BackpackScreen;
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!supports(screen)) {
				return;
			}
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) (AbstractContainerScreen<?>) screen;
			int x = accessor.mlinnotas$leftPos() + accessor.mlinnotas$imageWidth() + 2;
			int y = accessor.mlinnotas$topPos() + 16; // embaixo do "Ord" (24x14)
			Button button = Button.builder(Component.translatable("button.mlinnotas.quick_stack"), b -> send())
					.bounds(x, y, 24, 14)
					.tooltip(Tooltip.create(Component.translatable("button.mlinnotas.quick_stack.tooltip",
							ModKeys.QUICK_STACK.getTranslatedKeyMessage())))
					.build();
			Screens.getWidgets(screen).add(button);

			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				if (!ModKeys.QUICK_STACK.matches(event) || s.getFocused() instanceof EditBox box && box.isFocused()) {
					return true;
				}
				send();
				return false;
			});
		});
	}

	public static void send() {
		if (ClientPlayNetworking.canSend(QuickStackPayload.TYPE)) {
			ClientPlayNetworking.send(QuickStackPayload.INSTANCE);
		}
	}
}
