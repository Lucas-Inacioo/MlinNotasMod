package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.chest.BigChestBlock;
import com.mlin.notas.chest.ChestTier;
import com.mlin.notas.tier.Tier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Blocos do mod. Os BlockItems correspondentes ficam em {@link ModItems}. */
public final class ModBlocks {
	private static final Map<ChestTier, Block> CHESTS = new EnumMap<>(ChestTier.class);
	private static final List<Block> ORES = new ArrayList<>();

	// minerios: cobalto no Overworld (pedra e ardosia), infernita so no Nether, endita e astralita so no End
	public static final Block COBALT_ORE = registerOre("cobalt_ore", BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(3.5F, 3.0F).sound(SoundType.STONE));
	public static final Block DEEPSLATE_COBALT_ORE = registerOre("deepslate_cobalt_ore", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(5.0F, 3.0F).sound(SoundType.DEEPSLATE));
	public static final Block INFERNITE_ORE = registerOre("infernite_ore", BlockBehaviour.Properties.of().mapColor(MapColor.NETHER)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(4.5F, 6.0F).sound(SoundType.NETHER_ORE));
	public static final Block ENDITE_ORE = registerOre("endite_ore", BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(5.5F, 9.0F).sound(SoundType.STONE));
	public static final Block ASTRALITE_ORE = registerOre("astralite_ore", BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(6.5F, 12.0F).sound(SoundType.STONE).lightLevel(state -> 5));

	static {
		for (ChestTier tier : ChestTier.values()) {
			registerChest(tier);
		}
	}

	public static final Block IRON_CHEST = chest(ChestTier.IRON);
	public static final Block GOLD_CHEST = chest(ChestTier.GOLD);
	public static final Block DIAMOND_CHEST = chest(ChestTier.DIAMOND);

	private ModBlocks() {
	}

	public static ResourceKey<Block> key(String name) {
		return ResourceKey.create(Registries.BLOCK, MlinNotas.id(name));
	}

	private static Block registerOre(String name, BlockBehaviour.Properties properties) {
		Block block = Blocks.register(key(name), Block::new, properties.requiresCorrectToolForDrops());
		ORES.add(block);
		return block;
	}

	private static void registerChest(ChestTier tier) {
		Tier material = tier.material();
		MapColor color = switch (material) {
			case STONE, COBALT -> MapColor.STONE;
			case COPPER -> MapColor.COLOR_ORANGE;
			case GOLD -> MapColor.GOLD;
			case DIAMOND -> MapColor.DIAMOND;
			case NETHERITE -> MapColor.COLOR_BLACK;
			case INFERNITE -> MapColor.NETHER;
			case ENDITE, ASTRALITE -> MapColor.COLOR_PURPLE;
			case CELESTIUM -> MapColor.SNOW;
			default -> MapColor.METAL;
		};
		float strength = 2.5F + material.level() * 0.25F;
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(color)
				.strength(strength, material.fireResistant() ? 1200.0F : 6.0F)
				.sound(material == Tier.STONE ? SoundType.STONE : SoundType.METAL)
				.requiresCorrectToolForDrops().noOcclusion();
		CHESTS.put(tier, Blocks.register(key(tier.blockName()), p -> new BigChestBlock(p, tier), properties));
	}

	public static Block chest(ChestTier tier) {
		return CHESTS.get(tier);
	}

	public static List<Block> chests() {
		return List.copyOf(CHESTS.values());
	}

	/** Minerios na ordem de registro. */
	public static List<Block> ores() {
		return Collections.unmodifiableList(ORES);
	}

	public static void init() {
	}
}
