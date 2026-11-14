package com.mlin.notas.client.grave;

import com.mlin.notas.grave.GraveInfo;
import com.mlin.notas.grave.GravePayloads;
import com.mlin.notas.grave.GraveRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import java.util.List;

/** Lado cliente das lapides: lista sincronizada (minimapa e mapa grande) e o nome gravado na pedra. */
public final class GraveClient {
	private static List<GraveInfo> graves = List.of();

	private GraveClient() {
	}

	public static List<GraveInfo> all() {
		return graves;
	}

	public static List<GraveInfo> inDimension(String dimension) {
		return graves.stream().filter(g -> g.dimension().equals(dimension)).toList();
	}

	/** Para testes de cliente: troca a lista local. */
	public static void setAll(List<GraveInfo> list) {
		graves = List.copyOf(list);
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(GravePayloads.Sync.TYPE, (payload, context) -> graves = List.copyOf(payload.graves()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> graves = List.of());
		BlockEntityRenderers.register(GraveRegistry.GRAVE_ENTITY, GraveRenderer::new);
	}
}
