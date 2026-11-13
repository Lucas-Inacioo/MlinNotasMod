package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.chest.BigChestBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/** Tipos de block entity do mod. */
public final class ModBlockEntities {
	public static final BlockEntityType<BigChestBlockEntity> BIG_CHEST = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, MlinNotas.id("big_chest"),
			new BlockEntityType<>(BigChestBlockEntity::new, Set.copyOf(ModBlocks.chests())));

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
