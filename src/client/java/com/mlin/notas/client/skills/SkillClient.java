package com.mlin.notas.client.skills;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.skills.SkillNode;
import com.mlin.notas.skills.SkillPayloads;
import com.mlin.notas.skills.SkillProgress;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Lado cliente da arvore de habilidades: progresso sincronizado do mundo, tecla K e pedido de deposito. */
public final class SkillClient {
	private static SkillProgress progress = new SkillProgress();

	private SkillClient() {
	}

	/** Progresso do mundo conforme o ultimo pacote do servidor (somente leitura). */
	public static SkillProgress progress() {
		return progress;
	}

	/** Para testes de cliente: troca o progresso local. */
	public static void setProgress(SkillProgress value) {
		progress = value;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SkillPayloads.Sync.TYPE, (payload, context) -> {
			progress = payload.toProgress();
			if (context.client().gui.screen() instanceof SkillTreeScreen screen) {
				screen.refresh();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> progress = new SkillProgress());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			while (ModKeys.SKILLS.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new SkillTreeScreen());
				}
			}
		});
	}

	public static void deposit(SkillNode node) {
		if (ClientPlayNetworking.canSend(SkillPayloads.Deposit.TYPE)) {
			ClientPlayNetworking.send(new SkillPayloads.Deposit(node.key()));
		}
	}
}
