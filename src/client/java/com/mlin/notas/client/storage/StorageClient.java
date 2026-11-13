package com.mlin.notas.client.storage;

import com.mlin.notas.storage.StorageRegistry;
import com.mlin.notas.storage.TerminalMenu;
import com.mlin.notas.storage.TerminalPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/** Cliente da rede de armazenamento: tela do terminal e recepcao da sincronizacao. */
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
	}
}
