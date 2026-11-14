package com.mlin.notas.compass;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Registro das bussolas (itens, data component) e do lado servidor. */
public final class Compasses {
	public static final DataComponentType<CompassTarget> TARGET = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MlinNotas.id("compass_target"), DataComponentType.<CompassTarget>builder()
					.persistent(CompassTarget.CODEC)
					.networkSynchronized(CompassTarget.STREAM_CODEC)
					.build());

	public static final Item BIOME_COMPASS = ModItems.register("biome_compass", p -> new ExplorerCompassItem(p, CompassKind.BIOME),
			new Item.Properties().stacksTo(1));
	public static final Item STRUCTURE_COMPASS = ModItems.register("structure_compass", p -> new ExplorerCompassItem(p, CompassKind.STRUCTURE),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	private Compasses() {
	}

	public static void init() {
		CompassServer.init();
	}
}
