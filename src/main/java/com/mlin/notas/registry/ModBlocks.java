package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.chest.BigChestBlock;
import com.mlin.notas.chest.ChestTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.Map;

/** Blocos do mod. Os BlockItems correspondentes ficam em {@link ModItems}. */
public final class ModBlocks {
	private static final Map<ChestTier, Block> CHESTS = new EnumMap<>(ChestTier.class);

	public static final Block IRON_CHEST = registerChest(ChestTier.IRON, MapColor.METAL, 3.0F);
	public static final Block GOLD_CHEST = registerChest(ChestTier.GOLD, MapColor.GOLD, 3.0F);
	public static final Block DIAMOND_CHEST = registerChest(ChestTier.DIAMOND, MapColor.DIAMOND, 4.0F);

	private ModBlocks() {
	}

	public static ResourceKey<Block> key(String name) {
		return ResourceKey.create(Registries.BLOCK, MlinNotas.id(name));
	}

	private static Block registerChest(ChestTier tier, MapColor color, float strength) {
		Block block = Blocks.register(key(tier.blockName()), p -> new BigChestBlock(p, tier),
				BlockBehaviour.Properties.of().mapColor(color).strength(strength, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
		CHESTS.put(tier, block);
		return block;
	}

	public static Block chest(ChestTier tier) {
		return CHESTS.get(tier);
	}

	public static void init() {
	}
}
