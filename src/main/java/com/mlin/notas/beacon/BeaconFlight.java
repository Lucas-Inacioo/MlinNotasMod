package com.mlin.notas.beacon;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModEffects;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Poder "Voo" do sinalizador de celestio: enquanto o jogador tem o efeito e esta dentro da caixa de alcance de um
 * sinalizador ativo com Voo, pode voar como no criativo. Ao sair do alcance, ao trocar de dimensao ou quando o
 * sinalizador para (quebrado, sem piramide/feixe: deixa de renovar a zona), o voo e o efeito somem; quem estava voando
 * ganha Queda Lenta para nao cair seco.
 *
 * <p>As zonas ficam em memoria (o sinalizador renova a cada 80 ticks); quem recebeu o voo e marcado num attachment
 * persistente, para tirar o voo mesmo depois de reiniciar o servidor.</p>
 */
public final class BeaconFlight {
	/** Marca do jogador que recebeu o voo do sinalizador. */
	public static final AttachmentType<Boolean> GRANTED = AttachmentRegistry.create(MlinNotas.id("beacon_flight_granted"),
			builder -> builder.persistent(Codec.BOOL));
	/** Validade da zona depois da ultima renovacao (o sinalizador renova a cada 80 ticks). */
	public static final long ZONE_TICKS = 100;
	/** Intervalo da checagem dos jogadores. */
	public static final int CHECK_EVERY = 5;

	private record Zone(AABB box, long expires) {
	}

	private static final Map<ResourceKey<Level>, Map<BlockPos, Zone>> ZONES = new HashMap<>();

	private BeaconFlight() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZONES.clear());
	}

	/** Renova a zona de voo do sinalizador em {@code pos}. */
	public static void register(Level level, BlockPos pos, AABB box) {
		ZONES.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(pos.immutable(), new Zone(box, level.getGameTime() + ZONE_TICKS));
	}

	/** O sinalizador parou (removido): a zona deixa de valer na hora. */
	public static void unregister(Level level, BlockPos pos) {
		if (level == null) {
			return;
		}
		Map<BlockPos, Zone> zones = ZONES.get(level.dimension());
		if (zones != null) {
			zones.remove(pos);
		}
	}

	/** O jogador esta dentro de alguma zona de voo ativa da dimensao dele. */
	public static boolean inZone(ServerPlayer player) {
		Map<BlockPos, Zone> zones = ZONES.get(player.level().dimension());
		if (zones == null) {
			return false;
		}
		long now = player.level().getGameTime();
		boolean inside = false;
		for (Iterator<Zone> it = zones.values().iterator(); it.hasNext(); ) {
			Zone zone = it.next();
			if (zone.expires() < now) {
				it.remove();
			} else if (zone.box().intersects(player.getBoundingBox())) {
				inside = true;
			}
		}
		return inside;
	}

	public static boolean granted(ServerPlayer player) {
		return Boolean.TRUE.equals(((AttachmentTarget) player).getAttached(GRANTED));
	}

	/** Liga ou desliga o voo de um jogador conforme o efeito e as zonas. */
	public static void tick(ServerPlayer player) {
		boolean hasEffect = player.hasEffect(ModEffects.BEACON_FLIGHT);
		if (hasEffect && inZone(player)) {
			Abilities abilities = player.getAbilities();
			if (!abilities.mayfly) {
				abilities.mayfly = true;
				player.onUpdateAbilities();
			}
			if (!granted(player)) {
				((AttachmentTarget) player).setAttached(GRANTED, true);
			}
			return;
		}
		if (hasEffect) {
			player.removeEffect(ModEffects.BEACON_FLIGHT);
		}
		if (granted(player)) {
			((AttachmentTarget) player).removeAttached(GRANTED);
			revoke(player);
		}
	}

	private static void revoke(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		Abilities abilities = player.getAbilities();
		boolean wasFlying = abilities.flying;
		abilities.mayfly = false;
		abilities.flying = false;
		player.onUpdateAbilities();
		if (wasFlying) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, true, true));
		}
		if (player.connection != null) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.beacon_flight.lost"));
		}
	}
}
