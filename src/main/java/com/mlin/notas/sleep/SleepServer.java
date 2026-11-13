package com.mlin.notas.sleep;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sono por porcentagem: o mod usa 50% como padrao da gamerule {@code players_sleeping_percentage} (aplicado uma vez
 * por mundo, se ainda estiver no 100 do vanilla) e mostra na barra de acao quantos estao dormindo e quantos faltam.
 * A noite passa do jeito vanilla (instantaneo quando todos os necessarios dormem o suficiente).
 */
public final class SleepServer {
	/** Marca no Overworld: o padrao do mod ja foi aplicado neste mundo. */
	public static final AttachmentType<Boolean> DEFAULT_APPLIED = AttachmentRegistry.create(MlinNotas.id("sleep_default_applied"),
			builder -> builder.persistent(Codec.BOOL));

	private static final Map<ResourceKey<Level>, SleepRules.Status> LAST = new HashMap<>();

	private SleepServer() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(SleepServer::applyModDefault);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
		ServerTickEvents.END_SERVER_TICK.register(SleepServer::tick);
	}

	/** Troca 100 (vanilla) por 50 na primeira partida do mundo com o mod. Devolve true se mudou. */
	public static boolean applyModDefault(MinecraftServer server) {
		AttachmentTarget marker = (AttachmentTarget) server.overworld();
		boolean applied = Boolean.TRUE.equals(marker.getAttached(DEFAULT_APPLIED));
		GameRules rules = server.getGameRules();
		boolean change = SleepRules.shouldApplyModDefault(rules.get(GameRules.PLAYERS_SLEEPING_PERCENTAGE), applied);
		if (change) {
			rules.set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, SleepRules.MOD_DEFAULT_PERCENT, server);
		}
		marker.setAttached(DEFAULT_APPLIED, true);
		return change;
	}

	private static void tick(MinecraftServer server) {
		int percent = server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		boolean everySecond = server.getTickCount() % 20 == 0;
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> players = level.players();
			SleepRules.Status status = status(players, percent);
			SleepRules.Status last = LAST.put(level.dimension(), status);
			if (status.sleeping() == 0 || percent > 100) {
				continue;
			}
			if (everySecond || !status.equals(last)) {
				Component message = message(status);
				for (ServerPlayer player : players) {
					player.sendOverlayMessage(message);
				}
			}
		}
	}

	/** Contagem dos jogadores informados (espectadores nao contam). */
	public static SleepRules.Status status(List<? extends ServerPlayer> players, int percent) {
		int active = 0;
		int sleeping = 0;
		for (ServerPlayer player : players) {
			if (!player.isSpectator()) {
				active++;
				if (player.isSleeping()) {
					sleeping++;
				}
			}
		}
		return SleepRules.status(active, sleeping, percent);
	}

	/** "X/Y dormindo, faltam N" ou "A noite vai passar". */
	public static Component message(SleepRules.Status status) {
		if (status.enough()) {
			return Component.translatable("message.mlinnotas.sleep.skipping");
		}
		return Component.translatable("message.mlinnotas.sleep.status", status.sleeping(), status.needed(), status.missing());
	}
}
