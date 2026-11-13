package com.mlin.notas.recipes;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Envia todas as receitas ao jogador quando ele entra e a todos quando os datapacks recarregam.
 * Jogadores sem o mod nao recebem nada (canSend).
 */
public final class RecipeSyncServer {
	private RecipeSyncServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(RecipeSyncPayload.TYPE, RecipeSyncPayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> send(server, listener.getPlayer()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				for (ServerPlayer player : PlayerLookup.all(server)) {
					send(server, player);
				}
			}
		});
	}

	public static void send(MinecraftServer server, ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, RecipeSyncPayload.TYPE)) {
			return;
		}
		List<RecipeView> recipes = RecipeExporter.export(server.getRecipeManager());
		for (RecipeSyncPayload batch : RecipeSyncPayload.batches(recipes)) {
			ServerPlayNetworking.send(player, batch);
		}
		MlinNotas.LOGGER.debug("{} receitas enviadas para {}", recipes.size(), player.getName().getString());
	}
}
