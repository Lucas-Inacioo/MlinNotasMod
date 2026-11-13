package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.nether.BurningTreeShape;
import com.mlin.notas.nether.MagmaSlug;
import com.mlin.notas.nether.NetherFeatures;
import com.mlin.notas.nether.NetherRegistry;
import com.mlin.notas.nether.WalkGrid;
import com.mlin.notas.nether.WalkPath;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Game tests do Nether novo. O servidor de teste usa um Nether plano, entao o terreno e medido direto nas funcoes de
 * densidade do noise_settings (chao e teto por coluna, sem decoracao); a geracao de verdade fica no
 * NetherClientGameTest. Este teste tambem grava uma imagem de previa em nether-preview/ (dentro da pasta de execucao).
 */
public class NetherGameTest {
	@GameTest
	public void newBiomesAreRegisteredInTheNether(GameTestHelper helper) {
		HolderLookup.RegistryLookup<Biome> biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
		for (ResourceKey<Biome> key : List.of(NetherRegistry.ASHEN_PLAINS, NetherRegistry.INFERNITE_PEAKS, NetherRegistry.BURNING_GROVE)) {
			Holder<Biome> biome = biomes.getOrThrow(key);
			helper.assertTrue(biome.is(BiomeTags.IS_NETHER), key.identifier() + " na tag is_nether");
			helper.assertTrue(biome.is(BiomeTags.HAS_NETHER_FORTRESS), key.identifier() + " pode ter fortaleza");
			helper.assertTrue(MultiNoiseBiomeSourceParameterList.Preset.NETHER.usedBiomes().anyMatch(key::equals),
					key.identifier() + " no multi-noise do Nether");
			helper.assertTrue(biome.value().getGenerationSettings().getCarvers().iterator().hasNext() == false, key.identifier() + " sem cavernas vanilla");
		}
		// biomas vanilla do Nether sem a caverna vanilla (fura o chao)
		Holder<Biome> wastes = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.NETHER_WASTES);
		helper.assertTrue(wastes.value().getGenerationSettings().getCarvers().iterator().hasNext() == false, "nether_wastes sem nether_cave");
		// infernita em todos os biomas do Nether, inclusive os novos
		var infernite = helper.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(com.mlin.notas.tier.ModWorldgen.ORE_INFERNITE);
		for (ResourceKey<Biome> key : List.of(NetherRegistry.ASHEN_PLAINS, NetherRegistry.INFERNITE_PEAKS, NetherRegistry.BURNING_GROVE)) {
			boolean has = biomes.getOrThrow(key).value().getGenerationSettings().features().stream().anyMatch(set -> set.contains(infernite));
			helper.assertTrue(has, "minerio de infernita em " + key.identifier());
		}
		helper.succeed();
	}

	@GameTest
	public void fortressAndBastionAreGrounded(GameTestHelper helper) {
		// refeitas em vstructures/ (assentam no chao por NetherSiting); detalhes em SNetherGameTest
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String id : List.of("fortress", "bastion_remnant")) {
			Structure structure = structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id))).value();
			helper.assertTrue(structure.type() == com.mlin.notas.registry.ModStructures.VANILLA_REMAKE,
					id + " deveria usar mlinnotas:vanilla_remake, usa " + structure.type());
		}
		helper.succeed();
	}

	@GameTest
	public void netherBlocksAndDrops(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(1, 1, 1, NetherRegistry.EMBER_NYLIUM);
		helper.setBlock(1, 2, 1, NetherRegistry.EMBER_SPROUTS);
		helper.assertBlockPresent(NetherRegistry.EMBER_SPROUTS, 1, 2, 1);
		BlockPos nylium = helper.absolutePos(new BlockPos(1, 1, 1));
		List<ItemStack> drops = Block.getDrops(level.getBlockState(nylium), level, nylium, null, null, new ItemStack(Items.IRON_PICKAXE));
		helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Items.NETHERRACK), "nilio sem toque de seda solta netherrack: " + drops);
		helper.assertTrue(level.getBlockState(nylium).getLightEmission() == 0, "nilio nao brilha");
		helper.assertTrue(NetherRegistry.BURNING_CAP.defaultBlockState().getLightEmission() > 0, "chapeu ardente brilha");
		for (Block block : NetherRegistry.blocks()) {
			helper.assertTrue(block.asItem() != Items.AIR, "item de " + block);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void burningTreeGrowsOnEmberNylium(GameTestHelper helper) {
		// arbusto (cabe nos 8x8x8 da estrutura de teste)
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, NetherRegistry.EMBER_NYLIUM);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		boolean placed = new NetherFeatures.BurningTree(4, 4).place(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
				RandomSource.create(3), origin);
		helper.assertTrue(placed, "arbusto deveria nascer no nilio");
		helper.assertBlockPresent(NetherRegistry.BURNING_STEM, 4, 1, 4);
		helper.assertBlockPresent(NetherRegistry.BURNING_STEM, 4, 4, 4);
		// em netherrack nao nasce
		helper.setBlock(1, 0, 1, Blocks.NETHERRACK);
		boolean onRack = new NetherFeatures.BurningTree(4, 4).place(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
				RandomSource.create(3), helper.absolutePos(new BlockPos(1, 1, 1)));
		helper.assertFalse(onRack, "nao nasce fora do nilio");
		helper.assertTrue(BurningTreeShape.tree(16, new java.util.Random(1)).size() > 60, "arvore grande tem copa");
		helper.succeed();
	}

	@GameTest
	public void magmaSlugIsPassiveFireproofAndDropsMagmaCream(GameTestHelper helper) {
		MagmaSlug slug = NetherRegistry.MAGMA_SLUG.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)), EntitySpawnReason.COMMAND);
		helper.assertTrue(slug != null, "lesma nasce");
		helper.assertTrue(slug.fireImmune(), "imune a fogo");
		helper.assertTrue(NetherRegistry.MAGMA_SLUG.getCategory() == net.minecraft.world.entity.MobCategory.CREATURE, "passiva");
		var loot = helper.getLevel().getServer().reloadableRegistries().getLootTable(slug.getLootTable().orElseThrow());
		helper.assertTrue(loot != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "tabela de loot da lesma");
		helper.assertTrue(NetherRegistry.MAGMA_SLUG_SPAWN_EGG != null, "ovo");
		slug.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ terreno (funcoes de densidade)

	/** Chao e teto de uma area do terreno novo, direto das funcoes de densidade. */
	record Terrain(int size, int x0, int z0, float[] floor, float[] ceiling, Holder<Biome>[] biomes) {
		int idx(int x, int z) {
			return x + z * size;
		}
	}

	static DensityFunction function(ServerLevel level, String name) {
		return level.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION)
				.getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, MlinNotas.id("nether/" + name))).value();
	}

	@SuppressWarnings("unchecked")
	static Terrain sample(ServerLevel level, long seed, int x0, int z0, int size) {
		NoiseGeneratorSettings settings = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.NETHER).value();
		RandomState state = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE), seed, settings);
		DensityFunction floorFn = function(level, "floor");
		DensityFunction ceilingFn = function(level, "ceiling");
		var floorSampler = state.getSampler(floorFn);
		var ceilingSampler = state.getSampler(ceilingFn);
		MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
				.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
		Climate.Sampler climate = state.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		BiomeResolver resolver = source.createResolver(climate);
		float[] floor = new float[size * size];
		float[] ceiling = new float[size * size];
		Holder<Biome>[] biomes = new Holder[size * size];
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				int i = x + z * size;
				floor[i] = floorSampler.sampleValue(SamplerContext.EMPTY_UNCACHED, x0 + x, 64, z0 + z);
				ceiling[i] = ceilingSampler.sampleValue(SamplerContext.EMPTY_UNCACHED, x0 + x, 64, z0 + z);
				if ((x & 3) == 0 && (z & 3) == 0) {
					biomes[i] = resolver.getNoiseBiome((x0 + x) >> 2, 16, (z0 + z) >> 2);
				} else {
					biomes[i] = biomes[(x & ~3) + (z & ~3) * size];
				}
			}
		}
		return new Terrain(size, x0, z0, floor, ceiling, biomes);
	}


	/** Fracao de cada bioma do Nether numa area grande (16384 x 16384, amostra a cada 128 blocos). */
	static Map<String, Double> census(ServerLevel level, long seed) {
		NoiseGeneratorSettings settings = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.NETHER).value();
		RandomState state = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE), seed, settings);
		MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(level.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
				.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
		BiomeResolver resolver = source.createResolver(state.createClimateSampler(SamplerContext.EMPTY_UNCACHED));
		Map<String, Integer> counts = new java.util.TreeMap<>();
		int total = 0;
		for (int x = -8192; x < 8192; x += 128) {
			for (int z = -8192; z < 8192; z += 128) {
				counts.merge(resolver.getNoiseBiome(x >> 2, 16, z >> 2).unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), 1, Integer::sum);
				total++;
			}
		}
		Map<String, Double> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			out.put(e.getKey(), Math.round(1000.0 * e.getValue() / total) / 10.0);
		}
		return out;
	}

	@GameTest(maxTicks = 400)
	public void netherBiomesShareTheMap(GameTestHelper helper) {
		Map<String, Double> census = census(helper.getLevel(), 12345L);
		MlinNotas.LOGGER.info("[NetherGameTest] biomas do Nether (% da area): {}", census);
		for (String biome : List.of("nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas", "ashen_plains",
				"infernite_peaks", "burning_grove")) {
			double share = census.getOrDefault(biome, 0.0);
			helper.assertTrue(share >= 3.0 && share <= 30.0, biome + " deveria ter de 3% a 30% da area, tem " + share + "%: " + census);
		}
		helper.succeed();
	}

	/** Grade de caminhada do terreno sem decoracao: rocha abaixo do chao, lava ate y 31, teto. */
	static WalkGrid walkGrid(Terrain t) {
		WalkGrid grid = new WalkGrid(t.size(), t.size());
		boolean[] solid = new boolean[128];
		boolean[] hazard = new boolean[128];
		for (int z = 0; z < t.size(); z++) {
			for (int x = 0; x < t.size(); x++) {
				int i = t.idx(x, z);
				for (int y = 0; y < 128; y++) {
					solid[y] = y < t.floor()[i] || y > t.ceiling()[i];
					hazard[y] = !solid[y] && y < 32;
				}
				grid.setColumn(x, z, 0, solid, hazard);
			}
		}
		return grid;
	}

	@GameTest(maxTicks = 1200)
	public void terrainIsWalkableFromCornerToCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StringBuilder report = new StringBuilder();
		boolean ok = true;
		for (long seed : new long[] {12345L, 987654321L, -42L}) {
			Terrain t = sample(level, seed, -256, -256, 512);
			WalkGrid grid = walkGrid(t);
			WalkPath path = new WalkPath(grid);
			Stats s = stats(t);
			WalkPath.Node a = path.nearestFloor(4, 4, 40);
			WalkPath.Node b = path.nearestFloor(507, 507, 40);
			WalkPath.Node c = path.nearestFloor(507, 4, 40);
			WalkPath.Node d = path.nearestFloor(4, 507, 40);
			boolean corners = a != null && b != null && c != null && d != null && path.mutual(a, b) && path.mutual(c, d);
			double pairs = path.connectedPairFraction(80, seed);
			report.append(String.format(java.util.Locale.ROOT, "seed %d: cantos %s, pares %.3f, %s%n", seed, corners, pairs, s));
			ok &= corners && pairs >= 0.9 && s.minSpace >= 8;
			try {
				File dir = new File("nether-preview");
				dir.mkdirs();
				ImageIO.write(image(t), "png", new File(dir, "terrain-" + seed + ".png"));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
		MlinNotas.LOGGER.info("[NetherGameTest] terreno (previa em {}):\n{}", new File("nether-preview").getAbsolutePath(), report);
		helper.assertTrue(ok, "Nether deveria ser atravessavel a pe:\n" + report);
		helper.succeed();
	}

	static final class Stats {
		int minFloor = 999;
		int maxFloor = -999;
		double meanFloor;
		int minSpace = 999;
		double meanSpace;
		double lava;
		double steep;
		Map<String, Integer> biomes = new LinkedHashMap<>();

		@Override
		public String toString() {
			return String.format(java.util.Locale.ROOT, "chao %d..%d (media %.1f), vao min %d (media %.1f), lava %.1f%%, degraus >1 %.1f%%, biomas %s",
					minFloor, maxFloor, meanFloor, minSpace, meanSpace, lava * 100, steep * 100, biomes);
		}
	}

	static Stats stats(Terrain t) {
		Stats s = new Stats();
		int n = t.size() * t.size();
		int lava = 0;
		int steep = 0;
		int dry = 0;
		for (int z = 0; z < t.size(); z++) {
			for (int x = 0; x < t.size(); x++) {
				int i = t.idx(x, z);
				int top = (int) Math.ceil(t.floor()[i]);
				int roof = (int) Math.floor(t.ceiling()[i]);
				s.minFloor = Math.min(s.minFloor, top);
				s.maxFloor = Math.max(s.maxFloor, top);
				s.meanFloor += top;
				s.minSpace = Math.min(s.minSpace, roof - Math.max(top, 32) + 1);
				s.meanSpace += roof - top + 1;
				if (top < 32) {
					lava++;
				} else {
					dry++;
					if (x + 1 < t.size() && Math.abs((int) Math.ceil(t.floor()[i + 1]) - top) > 1
							|| z + 1 < t.size() && Math.abs((int) Math.ceil(t.floor()[i + t.size()]) - top) > 1) {
						steep++;
					}
				}
				if ((x & 15) == 0 && (z & 15) == 0) {
					s.biomes.merge(t.biomes()[i].unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), 1, Integer::sum);
				}
			}
		}
		s.meanFloor /= n;
		s.meanSpace /= n;
		s.lava = (double) lava / n;
		s.steep = (double) steep / Math.max(1, dry);
		return s;
	}

	/** Previa: cor do bioma sombreada pela altura do chao, lava em laranja, bordas com degrau > 1 em preto. */
	static BufferedImage image(Terrain t) {
		BufferedImage img = new BufferedImage(t.size(), t.size(), BufferedImage.TYPE_INT_RGB);
		Map<String, Integer> colors = Map.of("nether_wastes", 0x8A3A30, "crimson_forest", 0xC02030, "warped_forest", 0x208A80, "soul_sand_valley",
				0x6A5A48, "basalt_deltas", 0x505058, "ashen_plains", 0xB0A8A0, "infernite_peaks", 0x402020, "burning_grove", 0xE07010);
		for (int z = 0; z < t.size(); z++) {
			for (int x = 0; x < t.size(); x++) {
				int i = t.idx(x, z);
				int top = (int) Math.ceil(t.floor()[i]);
				int rgb;
				if (top < 32) {
					rgb = 0xFFFF40;
				} else {
					String biome = t.biomes()[i].unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
					int base = colors.getOrDefault(biome, 0xFF00FF);
					double shade = 0.45 + 0.55 * Math.clamp((top - 32) / 60.0, 0.0, 1.0);
					int r = (int) (((base >> 16) & 0xFF) * shade);
					int g = (int) (((base >> 8) & 0xFF) * shade);
					int b = (int) ((base & 0xFF) * shade);
					boolean edge = x + 1 < t.size() && Math.abs((int) Math.ceil(t.floor()[i + 1]) - top) > 1
							|| z + 1 < t.size() && Math.abs((int) Math.ceil(t.floor()[i + t.size()]) - top) > 1;
					rgb = edge ? 0x000000 : (r << 16) | (g << 8) | b;
					if (t.ceiling()[i] - top < 14) {
						rgb = 0xFFFFFF; // estalactite baixa
					}
				}
				img.setRGB(x, z, rgb);
			}
		}
		return img;
	}
}
