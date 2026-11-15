package com.mlin.notas.season;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Lado servidor das estacoes: gamerules ({@code mlinnotas:seasons} liga/desliga, {@code mlinnotas:season_length}
 * dias por estacao), calculo da data a cada segundo a partir do relogio do Overworld, sincronizacao com os clientes e
 * o comando {@code /season}.
 */
public final class SeasonServer {
	public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.UPDATES)
			.buildAndRegister(MlinNotas.id("seasons"));
	public static final GameRule<Integer> LENGTH = GameRuleBuilder.forInteger(SeasonCalendar.DEFAULT_LENGTH).range(1, 365)
			.category(GameRuleCategory.UPDATES).buildAndRegister(MlinNotas.id("season_length"));

	private static boolean lastEnabled;
	private static int lastLength;
	private static long lastDay = Long.MIN_VALUE;

	private SeasonServer() {
	}

	public static void init() {
		SeasonBiomes.init();
		PayloadTypeRegistry.clientboundPlay().register(SeasonSyncPayload.TYPE, SeasonSyncPayload.CODEC);
		// os chunks do spawn sao gerados antes do SERVER_STARTED: a data precisa valer ja no carregamento do Overworld,
		// senao eles nascem sem a neve e a temperatura da estacao (quadrado sem neve em volta do spawn no inverno)
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				try {
					refresh(server);
				} catch (RuntimeException e) {
					MlinNotas.LOGGER.error("[{}] falha ao carregar a estacao no Overworld", MlinNotas.MOD_ID, e);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(SeasonServer::refresh);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SeasonState.clear();
			lastDay = Long.MIN_VALUE;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				refresh(server);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> ServerPlayNetworking.send(player, payload(player.level().getServer())));
		GameRuleEvents.changeCallback(LENGTH).register((value, server) -> refresh(server));
		GameRuleEvents.changeCallback(ENABLED).register((value, server) -> refresh(server));
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> SeasonCommand.register(dispatcher));
	}

	public static boolean enabled(MinecraftServer server) {
		return server.getGameRules().get(ENABLED);
	}

	public static int length(MinecraftServer server) {
		return server.getGameRules().get(LENGTH);
	}

	static long clockDay(MinecraftServer server) {
		return SeasonCalendar.clockDay(server.overworld().getOverworldClockTime());
	}

	/** Dia absoluto (relogio + deslocamento); refaz o deslocamento se a duracao mudou, mantendo a data. */
	public static long absoluteDay(MinecraftServer server) {
		SeasonData data = SeasonData.get(server);
		int length = length(server);
		long clockDay = clockDay(server);
		if (data.lengthDays() != length) {
			data.setOffset(SeasonCalendar.offsetKeepingDate(clockDay, data.offsetDays(), data.lengthDays(), length), length);
		}
		return clockDay + data.offsetDays();
	}

	/** Data atual; null com as estacoes desligadas. */
	public static SeasonDate date(MinecraftServer server) {
		return enabled(server) ? SeasonCalendar.date(absoluteDay(server), length(server)) : null;
	}

	public static SeasonSyncPayload payload(MinecraftServer server) {
		return new SeasonSyncPayload(enabled(server), length(server), absoluteDay(server));
	}

	/** Recalcula a data, atualiza o estado global e sincroniza os clientes se algo mudou. */
	public static void refresh(MinecraftServer server) {
		boolean enabled = enabled(server);
		int length = length(server);
		long day = absoluteDay(server);
		SeasonState.set(enabled ? SeasonCalendar.date(day, length) : null);
		SeasonState.setSeasonIndex(day, length);
		if (enabled != lastEnabled || length != lastLength || day != lastDay) {
			lastEnabled = enabled;
			lastLength = length;
			lastDay = day;
			SeasonSyncPayload payload = new SeasonSyncPayload(enabled, length, day);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.connection != null) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	/** Pula para o comeco da fase da estacao (sem mexer na hora do dia). */
	public static void setSeason(MinecraftServer server, Season season, Season.Phase phase) {
		int length = length(server);
		SeasonData.get(server).setOffset(SeasonCalendar.offsetFor(clockDay(server), season, phase, length), length);
		refresh(server);
	}

	/** Volta o calendario para o relogio puro (dia 0 do mundo = comeco da primavera). */
	public static void reset(MinecraftServer server) {
		SeasonData.get(server).setOffset(0L, length(server));
		refresh(server);
	}
}
