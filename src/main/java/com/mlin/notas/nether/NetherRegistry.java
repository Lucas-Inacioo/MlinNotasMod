package com.mlin.notas.nether;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherSproutsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Nether atravessavel a pe: blocos, criatura, biomas novos e o embrulho de estruturas. O terreno em si e so dado
 * (data/minecraft/worldgen/noise_settings/nether.json, gerado por scripts/NetherContent.java).
 *
 * <p>Biomas novos entram no multi-noise do Nether pela Fabric ({@link NetherBiomes}). O roteador de ruido do terreno
 * novo poe o tipo de terreno em {@code erosion} (baixo = planicie, alto = planalto) e um ruido proprio em
 * {@code weirdness}; os biomas vanilla tem esses dois em 0, entao:</p>
 * <ul>
 *   <li>Planicies de Cinza: erosion bem baixa (casa com o terreno plano);</li>
 *   <li>Picos de Infernita: erosion bem alta (planaltos). Futuro: covil de chefe aqui;</li>
 *   <li>Bosque Ardente: weirdness alta com erosion no meio (colinas).</li>
 * </ul>
 */
public final class NetherRegistry {
	public static final ResourceKey<Biome> ASHEN_PLAINS = biome("ashen_plains");
	public static final ResourceKey<Biome> INFERNITE_PEAKS = biome("infernite_peaks");
	public static final ResourceKey<Biome> BURNING_GROVE = biome("burning_grove");

	private static final List<Block> BLOCKS = new ArrayList<>();

	public static final Block NETHER_ASH = block("nether_ash", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.strength(0.5F).sound(SoundType.SAND));
	public static final Block EMBER_NYLIUM = block("ember_nylium", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(0.4F).sound(SoundType.NYLIUM));
	public static final Block EMBER_SPROUTS = block("ember_sprouts", NetherSproutsBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).replaceable().noCollision().instabreak().sound(SoundType.NETHER_SPROUTS).lightLevel(state -> 3)
			.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED));
	public static final Block BURNING_STEM = block("burning_stem", RotatedPillarBlock::new, Blocks.netherStemProperties(MapColor.COLOR_ORANGE));
	public static final Block BURNING_PLANKS = block("burning_planks", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.NETHER_WOOD));
	public static final Block BURNING_CAP = block("burning_cap", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.strength(1.0F).sound(SoundType.WART_BLOCK).lightLevel(state -> 6));

	public static final EntityType<MagmaSlug> MAGMA_SLUG = entity("magma_slug",
			EntityType.Builder.of(MagmaSlug::new, MobCategory.CREATURE).sized(0.9F, 0.7F).eyeHeight(0.55F).fireImmune().clientTrackingRange(8));

	static {
		for (Block block : BLOCKS) {
			ModItems.register(BuiltInRegistries.BLOCK.getKey(block).getPath(), p -> new BlockItem(block, p.useBlockDescriptionPrefix()), new Item.Properties());
		}
	}

	public static final Item MAGMA_SLUG_SPAWN_EGG = ModItems.register("magma_slug_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(MAGMA_SLUG));

	public static final StructureType<GroundedStructure> GROUNDED = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			MlinNotas.id("grounded"), () -> GroundedStructure.CODEC);

	private NetherRegistry() {
	}

	private static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, MlinNotas.id(name));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = Blocks.register(ModBlocks.key(name), factory, properties);
		BLOCKS.add(block);
		return block;
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MlinNotas.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** Blocos do Nether na ordem de registro. */
	public static List<Block> blocks() {
		return Collections.unmodifiableList(BLOCKS);
	}

	/** Faixa de erosion/weirdness em que um bioma novo ganha dos vanilla (que ficam em 0). */
	static final float NEW_BIOME_THRESHOLD = 1.0F;

	public static void init() {
		FabricDefaultAttributeRegistry.register(MAGMA_SLUG, MagmaSlug.createAttributes());
		SpawnPlacements.register(MAGMA_SLUG, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, MagmaSlug::checkSpawnRules);
		NetherFeatures.init();

		Climate.Parameter any = Climate.Parameter.span(-2.0F, 2.0F);
		Climate.Parameter zero = Climate.Parameter.point(0.0F);
		float t = NEW_BIOME_THRESHOLD;
		NetherBiomes.addNetherBiome(ASHEN_PLAINS, Climate.parameters(any, any, zero, Climate.Parameter.span(-2.0F, -t), zero, any, 0.0F));
		NetherBiomes.addNetherBiome(INFERNITE_PEAKS, Climate.parameters(any, any, zero, Climate.Parameter.span(t, 2.0F), zero, any, 0.0F));
		NetherBiomes.addNetherBiome(BURNING_GROVE, Climate.parameters(any, any, zero, Climate.Parameter.span(-0.35F, 0.35F), zero,
				Climate.Parameter.span(t, 2.0F), 0.0F));

		// sem as cavernas vanilla do Nether: furam o chao e fazem labirintos
		BiomeModifications.create(MlinNotas.id("no_nether_caves")).add(ModificationPhase.REMOVALS, BiomeSelectors.tag(BiomeTags.IS_NETHER),
				context -> context.getGenerationSettings().removeCarver(Carvers.NETHER_CAVE));
	}
}
