package com.mlin.notas.food;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Onde o jogador acha as culturas: formas selvagens no mundo (uma feature por cultura, nos biomas do seu habitat),
 * sementes ao quebrar grama e sementes/produtos nos baus das casas de vila. Mercado da vila, missoes e o mercador
 * ambulante ficam nos dados (ContentGen) e em {@code village/}.
 */
public final class FoodServer {
	/** Chance de uma semente da horta ao quebrar grama (sem tesoura). */
	public static final float GRASS_SEED_CHANCE = 0.04F;

	private static final Set<ResourceKey<LootTable>> GRASS = Set.of(
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("blocks/short_grass")),
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("blocks/tall_grass")));
	private static final Set<ResourceKey<LootTable>> VILLAGE_HOUSES = Set.of(BuiltInLootTables.VILLAGE_PLAINS_HOUSE,
			BuiltInLootTables.VILLAGE_TAIGA_HOUSE, BuiltInLootTables.VILLAGE_SAVANNA_HOUSE, BuiltInLootTables.VILLAGE_SNOWY_HOUSE,
			BuiltInLootTables.VILLAGE_DESERT_HOUSE);

	/** Biomas de cada habitat das formas selvagens. */
	@SuppressWarnings("unchecked")
	private static final Map<FoodCatalog.WildHabitat, ResourceKey<Biome>[]> HABITATS = Map.of(
			FoodCatalog.WildHabitat.TEMPERATE, new ResourceKey[] {Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST},
			FoodCatalog.WildHabitat.FOREST, new ResourceKey[] {Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
					Biomes.FLOWER_FOREST, Biomes.CHERRY_GROVE, Biomes.MEADOW},
			FoodCatalog.WildHabitat.TROPICAL, new ResourceKey[] {Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE,
					Biomes.BAMBOO_JUNGLE},
			FoodCatalog.WildHabitat.WETLAND, new ResourceKey[] {Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.JUNGLE},
			FoodCatalog.WildHabitat.COLD, new ResourceKey[] {Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
					Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.SNOWY_TAIGA});

	private FoodServer() {
	}

	static void init() {
		// a ordem das features na etapa precisa ser a mesma em todos os biomas: sempre a ordem do catalogo
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, MlinNotas.id(crop.wildName()));
			BiomeModifications.addFeature(BiomeSelectors.includeByKey(HABITATS.get(crop.habitat())), GenerationStep.Decoration.VEGETAL_DECORATION, key);
		}
		LootTableEvents.MODIFY_DROPS.register(FoodServer::modifyDrops);
	}

	/** Sementes que a grama pode soltar (as culturas de semente separada). */
	public static List<Item> grassSeeds() {
		return FoodCatalog.CROPS.stream().filter(FoodCatalog.Crop::hasSeparateSeed).map(c -> FoodRegistry.item(c.seed())).toList();
	}

	private static void modifyDrops(Holder<LootTable> holder, LootContext context, List<ItemStack> drops) {
		ResourceKey<LootTable> key = holder.unwrapKey().orElse(null);
		if (key == null) {
			return;
		}
		RandomSource random = context.getRandom();
		if (GRASS.contains(key)) {
			ItemInstance tool = context.getOptional(LootContextParams.TOOL);
			boolean shears = tool != null && tool.typeHolder().value() == Items.SHEARS;
			if (!shears && random.nextFloat() < GRASS_SEED_CHANCE) {
				List<Item> seeds = grassSeeds();
				drops.add(new ItemStack(seeds.get(random.nextInt(seeds.size()))));
			}
		} else if (VILLAGE_HOUSES.contains(key)) {
			villageHouse(random, drops);
		}
	}

	/** 1 a 3 pilhas de sementes ou produtos secos (cebola, alho, arroz, feijao). */
	static void villageHouse(RandomSource random, List<ItemStack> drops) {
		List<FoodCatalog.Crop> crops = FoodCatalog.CROPS;
		int stacks = 1 + random.nextInt(3);
		for (int i = 0; i < stacks; i++) {
			FoodCatalog.Crop crop = crops.get(random.nextInt(crops.size()));
			drops.add(new ItemStack(FoodRegistry.item(crop.seed()), 2 + random.nextInt(4)));
		}
	}
}
