package com.mlin.notas.client.quests;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.quests.Quest;
import com.mlin.notas.quests.QuestBook;
import com.mlin.notas.quests.QuestBookItem;
import com.mlin.notas.quests.QuestPayloads;
import com.mlin.notas.quests.QuestProgress;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Lado cliente do livro de quests: livro e progresso sincronizados, tecla L / item abrem a tela, toast ao completar.
 */
public final class QuestClient {
	private static QuestBook book = QuestBook.EMPTY;
	private static QuestProgress progress = new QuestProgress();

	private QuestClient() {
	}

	public static QuestBook book() {
		return book;
	}

	public static QuestProgress progress() {
		return progress;
	}

	/** Id do jogador local (chave dos resgates). */
	public static String playerId() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? "" : mc.player.getStringUUID();
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(QuestPayloads.Book.TYPE, (payload, context) -> {
			try {
				book = payload.toBook();
			} catch (RuntimeException e) {
				MlinNotas.LOGGER.error("[quests] livro recebido invalido: {}", e.getMessage());
			}
			refreshScreen(context.client());
		});
		ClientPlayNetworking.registerGlobalReceiver(QuestPayloads.Progress.TYPE, (payload, context) -> {
			progress = payload.toProgress(playerId());
			refreshScreen(context.client());
		});
		ClientPlayNetworking.registerGlobalReceiver(QuestPayloads.Completed.TYPE, (payload, context) -> {
			for (String key : payload.quests()) {
				Quest quest = book.quest(key);
				if (quest != null) {
					context.client().gui.toastManager().addToast(new QuestToast(quest));
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			book = QuestBook.EMPTY;
			progress = new QuestProgress();
		});
		QuestBookItem.opener = () -> open(Minecraft.getInstance());
		// no INICIO do tick: L tambem e a tecla padrao das conquistas vanilla; abrindo o livro antes do handleKeybinds, a
		// tela nova solta todas as teclas (KeyMapping.releaseAll) e as conquistas nao abrem por cima. As conquistas seguem
		// no menu de pausa, e qualquer uma das duas teclas pode ser trocada nos controles.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			while (ModKeys.QUESTS.consumeClick()) {
				open(client);
			}
		});
	}

	public static void open(Minecraft client) {
		if (client.gui.screen() == null) {
			client.gui.setScreen(new QuestBookScreen());
		}
	}

	private static void refreshScreen(Minecraft client) {
		if (client.gui.screen() instanceof QuestBookScreen screen) {
			screen.refresh();
		}
	}

	public static void action(Quest quest, int action) {
		if (ClientPlayNetworking.canSend(QuestPayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new QuestPayloads.Action(quest.key(), action));
		}
	}
}
