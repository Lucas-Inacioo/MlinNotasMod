package com.mlin.notas.client.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.village.VillagePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.core.BlockPos;

/** Lado cliente do quadro de missoes: abre/atualiza a tela, manda as acoes, contorno da vila e escolha de lugar. */
public final class VillageClient {
	private VillageClient() {
	}

	public static void init() {
		WorksClient.init();
		ClientPlayNetworking.registerGlobalReceiver(VillagePayloads.View.TYPE, (payload, context) -> {
			if (payload.open()) {
				context.client().gui.setScreen(new VillageBoardScreen(payload));
			} else if (context.client().gui.screen() instanceof VillageBoardScreen screen && screen.board().equals(payload.board())) {
				screen.update(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(VillagePayloads.Overlay.TYPE, (payload, context) -> {
			VillageOverlay.accept(payload);
			if (context.client().gui.screen() instanceof VillageBoardScreen screen) {
				screen.refresh();
			}
		});
		LevelExtractionEvents.END_EXTRACTION.register(context -> {
			VillageOverlay.render();
			VillagePlacement.render();
		});
		ClientTickEvents.START_CLIENT_TICK.register(VillagePlacement::startTick);
		HudElementRegistry.addLast(MlinNotas.id("village_placement"), VillagePlacement::hud);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			VillageOverlay.clear();
			VillagePlacement.stop();
		});
	}

	public static void send(BlockPos board, String action, String arg) {
		if (ClientPlayNetworking.canSend(VillagePayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new VillagePayloads.Action(board, action, arg));
		}
	}
}
