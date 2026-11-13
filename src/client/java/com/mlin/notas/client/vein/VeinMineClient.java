package com.mlin.notas.client.vein;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.vein.VeinMineServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Avisa o servidor quando a tecla de mineracao em veio e pressionada ou solta (segurar = ativo). */
public final class VeinMineClient {
	private static boolean sent;

	private VeinMineClient() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sent = false);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			boolean down = ModKeys.VEIN_MINE.isDown();
			if (down != sent && ClientPlayNetworking.canSend(VeinMineServer.Payload.TYPE)) {
				ClientPlayNetworking.send(new VeinMineServer.Payload(down));
				sent = down;
			}
		});
	}
}
