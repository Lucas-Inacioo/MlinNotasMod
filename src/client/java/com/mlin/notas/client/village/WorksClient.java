package com.mlin.notas.client.village;

import com.mlin.notas.village.works.WorksPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Lado cliente das obras publicas: a fila que o servidor manda alimenta a tela de obras do quadro. */
public final class WorksClient {
	private WorksClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WorksPayloads.Sync.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof WorksScreen screen) {
				screen.update(payload);
			}
		});
	}
}
