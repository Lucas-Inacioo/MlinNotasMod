package com.mlin.notas.client.season;

import com.mlin.notas.client.ambient.AmbientClient;
import com.mlin.notas.season.Season;
import com.mlin.notas.season.SeasonDate;
import com.mlin.notas.season.SeasonState;
import com.mlin.notas.season.SeasonSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * Estacoes no cliente: recebe a data do servidor, atualiza o {@link SeasonState} (lido pelos mixins de temperatura e
 * de cor), avisa no chat quando a estacao muda e refaz as cores dos chunks quando a fase visual muda. O refazer e
 * progressivo: limpa o cache de tinta e marca os chunks como sujos em aneis a partir do jogador (2 aneis por tick),
 * em vez de recarregar o mundo inteiro de uma vez (que piscaria com distancia de visao alta).
 */
public final class SeasonClient {
	private static final int RINGS_PER_TICK = 2;

	private static SeasonDate date;
	private static int retintRing = -1;
	private static int retintCenterX;
	private static int retintCenterZ;

	private SeasonClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SeasonSyncPayload.TYPE, (payload, context) -> receive(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			date = null;
			retintRing = -1;
			if (!client.hasSingleplayerServer()) {
				SeasonState.clear();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(SeasonClient::tick);
	}

	/** Data atual vista pelo cliente; null = estacoes desligadas ou servidor sem o mod. */
	public static SeasonDate date() {
		return date;
	}

	static void receive(Minecraft minecraft, SeasonSyncPayload payload) {
		SeasonDate previous = date;
		SeasonDate now = payload.date();
		date = now;
		SeasonState.set(now);
		SeasonState.setSeasonIndex(payload.absoluteDay(), payload.lengthDays());
		int oldKey = previous == null ? -1 : previous.visualKey();
		int newKey = now == null ? -1 : now.visualKey();
		if (oldKey != newKey) {
			startRetint(minecraft);
		}
		if (previous != null && now != null && previous.season() != now.season() && minecraft.player != null
				&& AmbientClient.options().seasonMessages) {
			minecraft.player.sendSystemMessage(Component.translatable("message.mlinnotas.season.start." + now.season().id())
					.withStyle(color(now.season())));
		}
	}

	public static ChatFormatting color(Season season) {
		return switch (season) {
			case SPRING -> ChatFormatting.GREEN;
			case SUMMER -> ChatFormatting.YELLOW;
			case AUTUMN -> ChatFormatting.GOLD;
			case WINTER -> ChatFormatting.AQUA;
		};
	}

	/** Cor ARGB da estacao para o HUD. */
	public static int argb(Season season) {
		return switch (season) {
			case SPRING -> 0xFF8BE36B;
			case SUMMER -> 0xFFFFE066;
			case AUTUMN -> 0xFFF0A040;
			case WINTER -> 0xFFA8DCFF;
		};
	}

	private static void startRetint(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		minecraft.level.clearTintCaches();
		ChunkPos center = minecraft.player.chunkPosition();
		retintCenterX = center.x();
		retintCenterZ = center.z();
		retintRing = 0;
	}

	/** True enquanto as cores ainda estao sendo refeitas (para testes esperarem). */
	public static boolean retinting() {
		return retintRing >= 0;
	}

	private static void tick(Minecraft minecraft) {
		if (retintRing < 0) {
			return;
		}
		if (minecraft.level == null) {
			retintRing = -1;
			return;
		}
		int maxRing = minecraft.options.getEffectiveRenderDistance() + 2;
		int minSection = minecraft.level.getMinSectionY();
		int maxSection = minecraft.level.getMaxSectionY();
		for (int i = 0; i < RINGS_PER_TICK && retintRing >= 0; i++) {
			int r = retintRing;
			if (r == 0) {
				dirty(minecraft, retintCenterX, retintCenterZ, minSection, maxSection);
			} else {
				for (int d = -r; d <= r; d++) {
					dirty(minecraft, retintCenterX + d, retintCenterZ - r, minSection, maxSection);
					dirty(minecraft, retintCenterX + d, retintCenterZ + r, minSection, maxSection);
					if (d != -r && d != r) {
						dirty(minecraft, retintCenterX - r, retintCenterZ + d, minSection, maxSection);
						dirty(minecraft, retintCenterX + r, retintCenterZ + d, minSection, maxSection);
					}
				}
			}
			retintRing = r + 1 > maxRing ? -1 : r + 1;
		}
	}

	private static void dirty(Minecraft minecraft, int chunkX, int chunkZ, int minSection, int maxSection) {
		if (minecraft.level.hasChunk(chunkX, chunkZ)) {
			minecraft.levelExtractor.setSectionRangeDirty(chunkX, minSection, chunkZ, chunkX, maxSection, chunkZ);
		}
	}
}
