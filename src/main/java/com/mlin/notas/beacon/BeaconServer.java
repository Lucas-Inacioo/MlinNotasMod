package com.mlin.notas.beacon;

import com.mlin.notas.mixin.BeaconBlockEntityAccessor;
import com.mlin.notas.registry.ModEffects;

/** Liga as partes do sinalizador por nivel: efeitos novos aceitos pelo sinalizador, Voo e Garimpo. */
public final class BeaconServer {
	private BeaconServer() {
	}

	public static void init() {
		ModEffects.init();
		// o sinalizador filtra os efeitos (menu e NBT) por este conjunto
		for (BeaconTiers.Extra extra : BeaconTiers.Extra.values()) {
			BeaconBlockEntityAccessor.mlinnotas$validEffects().add(ModEffects.forExtra(extra));
		}
		BeaconFlight.init();
		Prospecting.init();
	}
}
