package com.mlin.notas.client.village;

import com.mlin.notas.village.VillagePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;

/** Lado cliente do quadro de missoes: abre/atualiza a tela e manda as acoes. */
public final class VillageClient {
	private VillageClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(VillagePayloads.View.TYPE, (payload, context) -> {
			if (payload.open()) {
				context.client().gui.setScreen(new VillageBoardScreen(payload));
			} else if (context.client().gui.screen() instanceof VillageBoardScreen screen && screen.board().equals(payload.board())) {
				screen.update(payload);
			}
		});
	}

	public static void send(BlockPos board, String action, String arg) {
		if (ClientPlayNetworking.canSend(VillagePayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new VillagePayloads.Action(board, action, arg));
		}
	}
}
