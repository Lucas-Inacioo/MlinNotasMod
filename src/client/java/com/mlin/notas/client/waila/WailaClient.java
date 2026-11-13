package com.mlin.notas.client.waila;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.waila.ContainerPeekPayload;
import com.mlin.notas.waila.ContainerPeekRequestPayload;
import com.mlin.notas.waila.ContainerTooltip;
import com.mlin.notas.waila.WailaRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Cliente do HUD WAILA: tecla N liga/desliga, pede o conteudo do container mirado a cada
 * {@link WailaRules#REQUEST_INTERVAL} ticks e guarda a ultima resposta. Tambem registra a grade de conteudo
 * dos tooltips (caixa de shulker e mochila).
 */
public final class WailaClient {
	/** Resposta e descartada se nao for renovada neste tempo (ticks). */
	private static final int CONTENTS_TTL = 40;

	private static boolean enabled = true;
	private static ContainerPeekPayload contents;
	private static long contentsTick;
	private static long ticks;

	private WailaClient() {
	}

	public static void init() {
		HudElementRegistry.addLast(MlinNotas.id("waila"), WailaHud::render);
		ClientPlayNetworking.registerGlobalReceiver(ContainerPeekPayload.TYPE, (payload, context) -> {
			contents = payload;
			contentsTick = ticks;
		});
		ClientTooltipComponentCallback.EVENT.register(component ->
				component instanceof ContainerTooltip tooltip ? new ContainerTooltipRenderer(tooltip) : null);
		ClientTickEvents.END_CLIENT_TICK.register(WailaClient::tick);
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	/** Ultimo conteudo recebido para a posicao, se ainda for recente. */
	public static ContainerPeekPayload contentsFor(BlockPos pos) {
		if (contents == null || !contents.pos().equals(pos) || ticks - contentsTick > CONTENTS_TTL) {
			return null;
		}
		return contents;
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		if (minecraft.player == null || minecraft.level == null) {
			contents = null;
			return;
		}
		while (ModKeys.WAILA.consumeClick()) {
			enabled = !enabled;
			minecraft.player.sendOverlayMessage(Component.translatable(enabled ? "message.mlinnotas.waila.on" : "message.mlinnotas.waila.off"));
		}
		if (!enabled || ticks % WailaRules.REQUEST_INTERVAL != 0) {
			return;
		}
		HitResult hit = minecraft.hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			if ((minecraft.level.getBlockEntity(pos) instanceof BaseContainerBlockEntity || minecraft.level.getBlockEntity(pos) instanceof com.mlin.notas.storage.StorageUnitBlockEntity)
					&& ClientPlayNetworking.canSend(ContainerPeekRequestPayload.TYPE)) {
				ClientPlayNetworking.send(new ContainerPeekRequestPayload(pos));
			}
		}
	}
}
