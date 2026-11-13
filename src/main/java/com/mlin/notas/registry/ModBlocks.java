package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.chest.BigChestBlock;
import com.mlin.notas.chest.ChestTier;
import com.mlin.notas.end.PurpurElevatorBlock;
import com.mlin.notas.end.VoidGrassBlock;
import com.mlin.notas.tier.Tier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

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

	// End: blocos dos biomas novos e da Cidadela do End (registrados como itens em ModItems, na ordem)
	private static final List<Block> END_BLOCKS = new ArrayList<>();
	public static final Block END_MOSS = registerEnd("end_moss", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F, 9.0F).sound(SoundType.MOSS).requiresCorrectToolForDrops());
	public static final Block VOID_ASH = registerEnd("void_ash", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.strength(0.6F).sound(SoundType.SAND));
	public static final Block VOID_GRASS = registerEnd("void_grass", VoidGrassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.replaceable().noCollision().instabreak().sound(SoundType.GRASS).lightLevel(state -> 4).offsetType(BlockBehaviour.OffsetType.XZ)
			.pushReaction(PushReaction.POPPED));
	public static final Block ETHER_CRYSTAL_BLOCK = registerEnd("ether_crystal_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F).sound(SoundType.AMETHYST).lightLevel(state -> 6).requiresCorrectToolForDrops());
	public static final Block ETHER_CRYSTAL_CLUSTER = registerEnd("ether_crystal_cluster", p -> new AmethystClusterBlock(7.0F, 3.0F, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
					.strength(1.5F).lightLevel(state -> 7).pushReaction(PushReaction.POPPED));
	public static final Block VOID_BRICKS = registerEnd("void_bricks", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F, 9.0F).requiresCorrectToolForDrops());
	public static final Block PURPUR_LAMP = registerEnd("purpur_lamp", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA)
			.strength(1.5F, 6.0F).sound(SoundType.GLASS).lightLevel(state -> 15).requiresCorrectToolForDrops());
	public static final Block PURPUR_ELEVATOR = registerEnd("purpur_elevator", PurpurElevatorBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 6.0F).lightLevel(state -> 3)
			.requiresCorrectToolForDrops());
	// decoracao dos biomas grandes: arvores do vazio, lirios, cogumelos e basalto
	public static final Block VOID_LOG = registerEnd("void_log", net.minecraft.world.level.block.RotatedPillarBlock::new,
			Blocks.logProperties(MapColor.COLOR_PURPLE, MapColor.TERRACOTTA_PURPLE, SoundType.WOOD));
	public static final Block VOID_PLANKS = registerEnd("void_planks", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
			.instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
	public static final Block VOID_LEAVES = registerEnd("void_leaves", p -> new net.minecraft.world.level.block.LeavesBlock(
			net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
			Blocks.leavesProperties(SoundType.AZALEA_LEAVES).mapColor(MapColor.COLOR_CYAN).lightLevel(state -> 3));
	public static final Block VOID_LILY = registerEnd("void_lily", com.mlin.notas.end.EndPlantBlock::lily, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE).noCollision().instabreak().sound(SoundType.GRASS).lightLevel(state -> 9)
			.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED));
	public static final Block END_MUSHROOM = registerEnd("end_mushroom", com.mlin.notas.end.EndPlantBlock::mushroom, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_MAGENTA).noCollision().instabreak().sound(SoundType.FUNGUS).lightLevel(state -> 6)
			.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED));
	public static final Block VOID_BASALT = registerEnd("void_basalt", net.minecraft.world.level.block.RotatedPillarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 6.0F)
					.sound(SoundType.BASALT).requiresCorrectToolForDrops());

	static {
		for (ChestTier tier : ChestTier.values()) {
			registerChest(tier);
		}
	}

	/** Pedra de teleporte (2 de altura); ver waystone/. */
	public static final Block WAYSTONE = Blocks.register(key("waystone"), com.mlin.notas.waystone.WaystoneBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F, 1200.0F).sound(SoundType.STONE)
			.lightLevel(state -> 5).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.IMMOVEABLE));

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

	private static Block registerEnd(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = Blocks.register(key(name), factory, properties);
		END_BLOCKS.add(block);
		return block;
	}

	/** Blocos do End na ordem de registro. */
	public static List<Block> endBlocks() {
		return Collections.unmodifiableList(END_BLOCKS);
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
