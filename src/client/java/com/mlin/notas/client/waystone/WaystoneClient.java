package com.mlin.notas.client.waystone;

import com.mlin.notas.waystone.WaystoneInfo;
import com.mlin.notas.waystone.WaystonePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Lado cliente das pedras de teleporte: lista sincronizada (minimapa, tela) e abertura da tela. */
public final class WaystoneClient {
	private static List<WaystoneInfo> waystones = List.of();

	private WaystoneClient() {
	}

	/** Todas as pedras ativadas no mundo (todas as dimensoes). */
	public static List<WaystoneInfo> all() {
		return waystones;
	}

	public static List<WaystoneInfo> inDimension(String dimension) {
		return waystones.stream().filter(w -> w.dimension().equals(dimension)).toList();
	}

	/** Para testes de cliente: troca a lista local. */
	public static void setAll(List<WaystoneInfo> list) {
		waystones = List.copyOf(list);
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WaystonePayloads.Sync.TYPE, (payload, context) -> {
			waystones = List.copyOf(payload.waystones());
			if (context.client().gui.screen() instanceof WaystoneScreen screen) {
				screen.refresh();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WaystonePayloads.Open.TYPE, (payload, context) ->
				context.client().gui.setScreen(new WaystoneScreen(payload.source(), payload.justActivated())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> waystones = List.of());
	}

	public static void travel(WaystoneInfo source, WaystoneInfo target) {
		if (ClientPlayNetworking.canSend(WaystonePayloads.Travel.TYPE)) {
			ClientPlayNetworking.send(new WaystonePayloads.Travel(new net.minecraft.core.BlockPos(source.x(), source.y(), source.z()), target.key()));
		}
	}

	public static void rename(WaystoneInfo source, String name) {
		if (ClientPlayNetworking.canSend(WaystonePayloads.Rename.TYPE)) {
			ClientPlayNetworking.send(new WaystonePayloads.Rename(new net.minecraft.core.BlockPos(source.x(), source.y(), source.z()), name));
		}
	}

	static boolean creative(Minecraft minecraft) {
		return minecraft.player != null && minecraft.player.hasInfiniteMaterials();
	}
}
