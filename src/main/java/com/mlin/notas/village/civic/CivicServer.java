package com.mlin.notas.village.civic;

import com.mlin.notas.grave.GraveHooks;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Ponto de entrada das construcoes civicas e de defesa (Vilas 2.0, fase 7): pacotes, eventos e o relogio das funcoes
 * (Correio, Guilda, Escola, Torre de vigia, Quartel, Farol, pedras de vila, Refugio e Seguro de morte).
 */
public final class CivicServer {
	private CivicServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(CivicPayloads.VillageWaystones.TYPE, CivicPayloads.VillageWaystones.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CivicPayloads.Hostiles.TYPE, CivicPayloads.Hostiles.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DefenseServer.tick(server);
			if (server.getTickCount() % 100 == 0) {
				PostServer.tick(server);
				GuildServer.tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PostServer.clear();
			GuildServer.clear();
			DefenseServer.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			CivicData data = CivicData.get(server);
			if (!player.getName().getString().equals(data.knownPlayers().get(player.getStringUUID()))) {
				data.knownPlayers().put(player.getStringUUID(), player.getName().getString());
				data.touch();
			}
			data.markVisited(player.level().dimension().identifier().toString());
			DefenseServer.syncWaystones(server, true);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
				CivicData.get(destination.getServer()).markVisited(destination.dimension().identifier().toString()));
		// refugio: dentro das construcoes de uma vila de muralha completa, monstro nao nasce
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Enemy && !((EntityLoadData) entity).isLoadedFromDisk() && natural(((EntityLoadData) entity).spawnReason())
					&& DefenseServer.refugeBlocks(level, entity.blockPosition())) {
				entity.discard();
			}
		});
		// seguro de morte da Estalagem II
		GraveHooks.CLAIM_DEATH_ITEMS.register(DefenseServer::insure);
	}

	/** Nascimento por geracao de mundo ou spawn natural/reforco (nao ovo, nao comando, nao gerador do jogador). */
	static boolean natural(EntitySpawnReason reason) {
		return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION || reason == EntitySpawnReason.REINFORCEMENT
				|| reason == EntitySpawnReason.PATROL;
	}

	/** Botao direito num bloco de balcao civico. */
	public static void use(CivicBlock.Kind kind, ServerPlayer player, ServerLevel level, BlockPos pos) {
		switch (kind) {
			case MAILBOX -> PostServer.use(player, level, pos);
			case PEDESTAL -> MuseumServer.use(player, level, pos);
			case GUILD_BOARD -> GuildServer.use(player, level, pos);
		}
	}
}
