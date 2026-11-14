package com.mlin.notas.client.storage;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.storage.NetworkTablet;
import com.mlin.notas.storage.StorageRegistry;
import com.mlin.notas.storage.TerminalMenu;
import com.mlin.notas.storage.TerminalPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/** Cliente da rede de armazenamento: tela do terminal, recepcao da sincronizacao e tecla do Tablet de Rede. */
public final class StorageClient {
	private StorageClient() {
	}

	public static void init() {
		MenuScreens.register(StorageRegistry.TERMINAL_MENU, TerminalScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(TerminalPayloads.Sync.TYPE, (payload, context) -> {
			if (context.player() != null && context.player().containerMenu instanceof TerminalMenu menu
					&& menu.containerId == payload.containerId()) {
				menu.applySync(payload);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ModKeys.NETWORK_TABLET.consumeClick()) {
				if (client.player != null && client.gui.screen() == null
						&& client.player.getInventory().contains(stack -> stack.is(NetworkTablet.ITEM))
						&& ClientPlayNetworking.canSend(NetworkTablet.OpenPayload.TYPE)) {
					ClientPlayNetworking.send(NetworkTablet.OpenPayload.INSTANCE);
				}
			}
		});
	}
}
