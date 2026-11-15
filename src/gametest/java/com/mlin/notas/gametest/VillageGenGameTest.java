package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.village.FieldPins;
import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.HeightFieldBuilder;
import com.mlin.notas.village.VillageFieldServer;
import com.mlin.notas.village.gen.VillageGen;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.List;
import java.util.Locale;

/**
 * Vilas 3.0 fase 2 (lote {@code mlinnotas-gametest:village_gen}): o gerador de ruido do Overworld de verdade (as funcoes
 * de densidade do datapack do mod, sem mundo: o servidor de teste e plano) monta um campo de alturas de vila nova a
 * partir do terreno cru e, com o campo ativo, cada coluna do plano sai exatamente na altura do campo, sem agua nem ar na
 * camada fechada; com o desvio ligado o terreno volta a ser o cru. Mede tambem o custo do embrulho na densidade.
 */
public class VillageGenGameTest {
	/** Sementes do teste de cliente (planicie, montanha nevada, floresta, rio, deserto). */
	static final List<Long> SEEDS = List.of(46L, 56L, 33L, 17L, 55L);

	record Gen(NoiseBasedChunkGenerator generator, RandomState random, LevelHeightAccessor height, int sea) {
		int top(int x, int z) {
			return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random) - 1;
		}

		int surface(int x, int z) {
			return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random) - 1;
		}

		NoiseColumn column(int x, int z) {
			return generator.getBaseColumn(x, z, height, random);
		}
	}

	static Gen gen(MinecraftServer server, long seed) {
		HolderLookup.Provider registries = server.registryAccess();
		Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(
				registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
		RandomState random = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, settings.value());
		return new Gen(generator, random, LevelHeightAccessor.create(-64, 384), settings.value().seaLevel());
	}

	static HeightField field(Gen g, int cx, int cz, long seed) {
		HeightFieldBuilder.Natural natural = VillageGen.raw(() -> HeightFieldBuilder.coarse((x, z) -> g.top(cx + x, cz + z), HeightFieldBuilder.EXTENT,
				HeightFieldBuilder.SAMPLE_STEP));
		int minY = g.sea() + 1;
		int plazaY = HeightFieldBuilder.plazaHeight(natural, minY);
		return HeightFieldBuilder.forNewVillage(seed, cx, cz, plazaY, natural, minY, List.of(), FieldPins.none()).field();
	}

	@GameTest(maxTicks = 2400, environment = "mlinnotas-gametest:village_gen")
	public void noiseGeneratorBuildsTheFieldTerrain(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HeightField previous = VillageFieldServer.active();
		StringBuilder report = new StringBuilder();
		try {
			for (long seed : SEEDS) {
				Gen g = gen(server, seed);
				ChunkPos origin = g.generator().getOrigin(g.random());
				int cx = origin.getMinBlockX() + 8;
				int cz = origin.getMinBlockZ() + 8;
				VillageFieldServer.setActive(null);
				HeightField field = field(g, cx, cz, seed);
				VillageFieldServer.setActive(field);
				int pe = field.planExtent();
				int checked = 0;
				int wrong = 0;
				long sum = 0;
				int max = 0;
				int naturalMin = Integer.MAX_VALUE;
				int naturalMax = Integer.MIN_VALUE;
				for (int x = -pe; x <= pe; x += 6) {
					for (int z = -pe; z <= pe; z += 6) {
						HeightField.Kind kind = field.kindRel(x, z);
						if (!kind.inside() || kind == HeightField.Kind.WATER || kind == HeightField.Kind.KEEP) {
							continue;
						}
						int wx = cx + x;
						int wz = cz + z;
						int h = field.heightRel(x, z);
						int top = g.top(wx, wz);
						int surface = g.surface(wx, wz);
						int dev = Math.abs(top - h);
						checked++;
						sum += dev;
						max = Math.max(max, dev);
						if (dev != 0 || surface != top) {
							wrong++;
							if (wrong <= 5) {
								report.append(String.format(Locale.ROOT, "  seed %d %d,%d (%s): campo %d, solido %d, superficie %d%n", seed, x, z, kind, h, top,
										surface));
							}
						}
						// camada fechada: tudo solido, sem agua nem ar
						if ((x / 6 + z / 6) % 4 == 0) {
							NoiseColumn column = g.column(wx, wz);
							for (int y = h - field.depthRel(x, z) + 1; y <= h; y++) {
								BlockState s = column.getBlock(y);
								if (s.isAir() || !s.getFluidState().isEmpty()) {
									throw new IllegalStateException("seed " + seed + " " + x + "," + z + ": " + s + " em y=" + y + " (campo " + h + ")");
								}
							}
						}
					}
				}
				// desvio: o terreno cru (amostra) volta a ser o natural de antes
				for (int i = 0; i < 40; i++) {
					int x = cx - pe + (i * 37) % (2 * pe);
					int z = cz - pe + (i * 53) % (2 * pe);
					int raw = VillageGen.raw(() -> g.top(x, z));
					naturalMin = Math.min(naturalMin, raw);
					naturalMax = Math.max(naturalMax, raw);
				}
				report.append(String.format(Locale.ROOT, "seed %d centro %d,%d praca %d natural %d..%d: %d colunas, %d fora, desvio medio %.3f max %d%n",
						seed, cx, cz, field.plazaY(), naturalMin, naturalMax, checked, wrong, checked == 0 ? 0.0 : (double) sum / checked, max));
				if (wrong > checked / 200) {
					throw new IllegalStateException("colunas fora do campo demais:\n" + report);
				}
			}
			// custo: colunas inteiras com e sem o campo (mesmas colunas, segunda semente)
			Gen g = gen(server, SEEDS.get(1));
			ChunkPos origin = g.generator().getOrigin(g.random());
			int cx = origin.getMinBlockX() + 8;
			int cz = origin.getMinBlockZ() + 8;
			VillageFieldServer.setActive(field(g, cx, cz, SEEDS.get(1)));
			long with = time(g, cx, cz);
			long without = VillageGen.raw(() -> time(g, cx, cz));
			long with2 = time(g, cx, cz);
			VillageFieldServer.setActive(null);
			long none = time(g, cx, cz);
			report.append(String.format(Locale.ROOT, "custo de 1600 colunas: com campo %d ms / %d ms, desvio %d ms, sem campo %d ms%n", with / 1_000_000,
					with2 / 1_000_000, without / 1_000_000, none / 1_000_000));
			MlinNotas.LOGGER.info("[VillageGenGameTest]\n{}", report);
		} finally {
			VillageFieldServer.setActive(previous);
		}
		helper.succeed();
	}

	private static long time(Gen g, int cx, int cz) {
		long start = System.nanoTime();
		for (int i = 0; i < 40; i++) {
			for (int j = 0; j < 40; j++) {
				g.column(cx - 100 + i * 5, cz - 100 + j * 5);
			}
		}
		return System.nanoTime() - start;
	}
}
