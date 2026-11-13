package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.beacon.BeaconTiers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Efeitos novos do sinalizador. Nao fazem nada sozinhos: o Garimpo e lido ao quebrar minerio
 * ({@code beacon/Prospecting}) e o Voo libera o voo enquanto o jogador esta no alcance ({@code beacon/BeaconFlight}).
 * Icones em {@code textures/mob_effect/<id>.png} (TexGen).
 */
public final class ModEffects {
	public static final Holder<MobEffect> PROSPECTING = register(BeaconTiers.Extra.PROSPECTING.id(), 0xE6B422);
	public static final Holder<MobEffect> BEACON_FLIGHT = register(BeaconTiers.Extra.FLIGHT.id(), 0xDCE8FF);

	private ModEffects() {
	}

	private static Holder<MobEffect> register(String name, int color) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MlinNotas.id(name), new MobEffect(MobEffectCategory.BENEFICIAL, color) {
		});
	}

	public static Holder<MobEffect> forExtra(BeaconTiers.Extra extra) {
		return switch (extra) {
			case PROSPECTING -> PROSPECTING;
			case FLIGHT -> BEACON_FLIGHT;
		};
	}

	/** Poder extra correspondente ao efeito (null se for um efeito vanilla). */
	public static BeaconTiers.Extra extraOf(Holder<MobEffect> effect) {
		if (effect == null) {
			return null;
		}
		for (BeaconTiers.Extra extra : BeaconTiers.Extra.values()) {
			if (forExtra(extra).equals(effect)) {
				return extra;
			}
		}
		return null;
	}

	public static void init() {
	}
}
