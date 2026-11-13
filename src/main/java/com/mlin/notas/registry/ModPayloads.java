package com.mlin.notas.registry;

import com.mlin.notas.minimap.MinimapServer;
import com.mlin.notas.minimap.TeleportRequestPayload;
import com.mlin.notas.sorting.SortRequestPayload;
import com.mlin.notas.tablet.CraftingTabletItem;
import com.mlin.notas.tablet.OpenTabletPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registro dos pacotes customizados (tipos + receptores do servidor).
 * Receptores do cliente ficam em MlinNotasClient.
 */
public final class ModPayloads {
	private ModPayloads() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(SortRequestPayload.TYPE, SortRequestPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(OpenTabletPayload.TYPE, OpenTabletPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TeleportRequestPayload.TYPE, TeleportRequestPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(OpenTabletPayload.TYPE, (payload, context) -> {
			if (CraftingTabletItem.hasTablet(context.player())) {
				CraftingTabletItem.open(context.player());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.TYPE, (payload, context) ->
				MinimapServer.teleport(context.player(), payload.x(), payload.y(), payload.z()));
	}
}
