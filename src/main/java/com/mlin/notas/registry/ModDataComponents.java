package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.backpack.BackpackCodecs;
import com.mlin.notas.backpack.BackpackUpgrades;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Data components proprios do mod (dados guardados em ItemStacks). */
public final class ModDataComponents {
	public static final DataComponentType<BackpackUpgrades> BACKPACK_UPGRADES = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, MlinNotas.id("backpack_upgrades"),
			DataComponentType.<BackpackUpgrades>builder()
					.persistent(BackpackCodecs.UPGRADES_CODEC)
					.networkSynchronized(BackpackCodecs.UPGRADES_STREAM_CODEC)
					.build());

	private ModDataComponents() {
	}

	public static void init() {
	}
}
