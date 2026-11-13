package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.nether.MagmaSlug;
import com.mlin.notas.nether.NetherRegistry;
import com.mlin.notas.nether.WalkGrid;
import com.mlin.notas.nether.WalkPath;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Game test de cliente do Nether novo, num mundo normal (Nether gerado de verdade, com decoracao e estruturas):
 * <ol>
 *   <li>gera uma area de 512x512 blocos, monta a grade de caminhada a partir dos blocos reais e confere que da para
 *       andar entre os cantos opostos e entre pares sorteados (>= 90%);</li>
 *   <li>screenshots de cada bioma novo, de uma vista larga, da Lesma de Magma e de uma fortaleza e um bastiao
 *       (com a altura deles comparada ao chao).</li>
 * </ol>
 */
@SuppressWarnings("UnstableApiUsage")
public class NetherClientGameTest implements FabricClientGameTest {
	static final int SIZE = 512;
	static final int X0 = 0;
	static final int Z0 = 0;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(state -> state.setSeed("mlinnotas-nether")).create()) {
			context.runOnClient(mc -> mc.options.gamma().set(1.0));
			world.getConnection().waitForChunksRender();
			walkability(world);
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("time set midnight");
			biomes(context, world);
			wideView(context, world);
			slug(context, world);
			structures(context, world);
		}
	}

	// ------------------------------------------------------------------ caminhada

	private static ServerLevel nether(MinecraftServer server) {
		return server.getLevel(Level.NETHER);
	}

	private static DensitySampler floorSampler(ServerLevel nether) {
		var function = nether.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION)
				.getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, MlinNotas.id("nether/floor"))).value();
		return nether.getChunkSource().randomState().getSampler(function);
	}

	private static boolean hazard(BlockState state) {
		return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE);
	}

	private static void walkability(TestSingleplayerContext world) {
		long start = System.currentTimeMillis();
		// gera os chunks em faixas (cada chamada uma fileira de chunks)
		int chunks = SIZE / 16;
		for (int cz = 0; cz < chunks; cz++) {
			int row = cz;
			world.getServer().runOnServer(server -> {
				ServerLevel nether = nether(server);
				for (int cx = 0; cx < chunks; cx++) {
					nether.getChunk((X0 >> 4) + cx, (Z0 >> 4) + row);
				}
			});
		}
		long generated = System.currentTimeMillis();
		String report = world.getServer().computeOnServer(server -> {
			ServerLevel nether = nether(server);
			WalkGrid grid = new WalkGrid(SIZE, SIZE);
			boolean[] solid = new boolean[128];
			boolean[] hazard = new boolean[128];
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			Map<String, Integer> blocks = new LinkedHashMap<>();
			for (int z = 0; z < SIZE; z++) {
				for (int x = 0; x < SIZE; x++) {
					int wx = X0 + x;
					int wz = Z0 + z;
					LevelChunk chunk = nether.getChunk(wx >> 4, wz >> 4);
					for (int y = 0; y < 128; y++) {
						BlockState state = chunk.getBlockState(pos.set(wx, y, wz));
						solid[y] = y == 127 || !state.getCollisionShape(nether, pos).isEmpty();
						hazard[y] = hazard(state);
					}
					grid.setColumn(x, z, 0, solid, hazard);
				}
			}
			WalkPath path = new WalkPath(grid);
			DensitySampler floor = floorSampler(nether);
			// ponto "de chao" de uma coluna: a posicao em pe mais perto da altura do chao do terreno (ignora bolsoes de
			// caverna e topo de arvore)
			java.util.function.BiFunction<Integer, Integer, WalkPath.Node> ground = (x, z) -> {
				int feet = (int) Math.ceil(floor.sampleValue(SamplerContext.EMPTY_UNCACHED, X0 + x, 64, Z0 + z));
				return path.closestTo(x, z, feet, 4);
			};
			WalkPath.Node[] corners = new WalkPath.Node[4];
			int[][] cornerXz = {{8, 8}, {SIZE - 9, SIZE - 9}, {SIZE - 9, 8}, {8, SIZE - 9}};
			for (int c = 0; c < 4; c++) {
				for (int r = 0; r < 40 && corners[c] == null; r++) {
					for (int dx = -r; dx <= r && corners[c] == null; dx++) {
						for (int dz = -r; dz <= r && corners[c] == null; dz++) {
							int x = Math.clamp(cornerXz[c][0] + dx, 0, SIZE - 1);
							int z = Math.clamp(cornerXz[c][1] + dz, 0, SIZE - 1);
							corners[c] = ground.apply(x, z);
						}
					}
				}
			}
			boolean diagonal1 = corners[0] != null && corners[1] != null && path.mutual(corners[0], corners[1]);
			boolean diagonal2 = corners[2] != null && corners[3] != null && path.mutual(corners[2], corners[3]);
			List<WalkPath.Node> samples = new ArrayList<>();
			Random random = new Random(7);
			int dryColumns = 0;
			int tries = 0;
			while (samples.size() < 120 && tries++ < 5000) {
				WalkPath.Node node = ground.apply(random.nextInt(SIZE), random.nextInt(SIZE));
				if (node != null) {
					samples.add(node);
				}
			}
			for (int z = 0; z < SIZE; z += 4) {
				for (int x = 0; x < SIZE; x += 4) {
					if (ground.apply(x, z) != null) {
						dryColumns++;
					}
				}
			}
			double pairs = path.connectedPairFraction(samples);
			boolean[] fromCorner = corners[0] == null ? new boolean[path.nodeCount()] : path.reachableFrom(corners[0]);
			int reach = path.count(fromCorner);
			String result = String.format(Locale.ROOT,
					"area %dx%d em (%d,%d): %d posicoes em pe; cantos (0,0)<->(%d,%d) %s, (%d,0)<->(0,%d) %s; pares a pe %.1f%% (%d pontos); "
							+ "colunas com chao seco %.1f%%; alcancavel do canto %d (%.1f%%)",
					SIZE, SIZE, X0, Z0, path.nodeCount(), SIZE, SIZE, diagonal1, SIZE, SIZE, diagonal2, pairs * 100, samples.size(),
					100.0 * dryColumns / ((SIZE / 4) * (SIZE / 4)), reach, 100.0 * reach / path.nodeCount());
			MlinNotas.LOGGER.info("[NetherClientGameTest] caminhada: {}", result);
			if (!diagonal1 || !diagonal2 || pairs < 0.9) {
				throw new IllegalStateException("o Nether deveria ser atravessavel a pe: " + result);
			}
			return result;
		});
		MlinNotas.LOGGER.info("[NetherClientGameTest] geracao {} s, analise {} s: {}", (generated - start) / 1000, (System.currentTimeMillis() - generated) / 1000,
				report);
	}

	// ------------------------------------------------------------------ screenshots

	/** Teleporta para (x, z) no Nether com os olhos {@code above} blocos acima do chao e olhando para {@code pitch}. */
	private static void look(ClientGameTestContext context, TestSingleplayerContext world, int x, int z, int above, float yaw, float pitch) {
		world.getServer().runCommand("execute in minecraft:the_nether run tp @p " + x + " 100 " + z);
		world.getServer().runOnServer(server -> {
			var player = server.getPlayerList().getPlayers().getFirst();
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			nether(server).getChunk(x >> 4, z >> 4);
		});
		context.waitTicks(20);
		// olhos a {@code above} blocos do chao, subindo ate achar ar (nao abrir a camera dentro de uma copa ou parede)
		int eye = world.getServer().computeOnServer(server -> {
			ServerLevel nether = nether(server);
			DensitySampler sampler = floorSampler(nether);
			int y = Math.max((int) Math.ceil(sampler.sampleValue(SamplerContext.EMPTY_UNCACHED, x, 64, z)), 32) + above;
			for (int tries = 0; tries < 30 && y < 120; tries++, y++) {
				if (nether.getBlockState(new BlockPos(x, y - 1, z)).isAir() && nether.getBlockState(new BlockPos(x, y, z)).isAir()
						&& nether.getBlockState(new BlockPos(x, y + 1, z)).isAir()) {
					break;
				}
			}
			return y - 1;
		});
		world.getServer().runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @p %d %d %d %.1f %.1f", x, eye, z, yaw, pitch));
		context.waitTicks(60);
		world.getConnection().waitForChunksRender();
		context.waitTicks(20);
	}

	private static final List<ResourceKey<Biome>> OURS = List.of(NetherRegistry.ASHEN_PLAINS, NetherRegistry.INFERNITE_PEAKS,
			NetherRegistry.BURNING_GROVE, Biomes.CRIMSON_FOREST, Biomes.BASALT_DELTAS);

	private static Map<ResourceKey<Biome>, BlockPos> biomeCenters(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel nether = nether(server);
			BiomeResolver resolver = nether.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(nether.getChunkSource().randomState());
			Map<ResourceKey<Biome>, BlockPos> best = new LinkedHashMap<>();
			Map<ResourceKey<Biome>, Integer> score = new LinkedHashMap<>();
			for (int x = -3000; x <= 3000; x += 64) {
				for (int z = -3000; z <= 3000; z += 64) {
					Holder<Biome> biome = resolver.getNoiseBiome(x >> 2, 16, z >> 2);
					for (ResourceKey<Biome> key : OURS) {
						if (!biome.is(key)) {
							continue;
						}
						int same = 0;
						for (int dx = -64; dx <= 64; dx += 16) {
							for (int dz = -64; dz <= 64; dz += 16) {
								if (resolver.getNoiseBiome((x + dx) >> 2, 16, (z + dz) >> 2).is(key)) {
									same++;
								}
							}
						}
						// mais perto da origem desempata (menos chunks para gerar)
						int value = same * 10000 - (Math.abs(x) + Math.abs(z)) / 10;
						if (value > score.getOrDefault(key, Integer.MIN_VALUE)) {
							score.put(key, value);
							best.put(key, new BlockPos(x, 64, z));
						}
					}
				}
			}
			MlinNotas.LOGGER.info("[NetherClientGameTest] centros dos biomas: {}", best);
			return best;
		});
	}

	private static void biomes(ClientGameTestContext context, TestSingleplayerContext world) {
		Map<ResourceKey<Biome>, BlockPos> centers = biomeCenters(world);
		for (ResourceKey<Biome> key : OURS) {
			BlockPos pos = centers.get(key);
			if (pos == null) {
				throw new IllegalStateException("bioma " + key.identifier() + " nao apareceu num raio de 3000 blocos");
			}
			look(context, world, pos.getX(), pos.getZ(), 9, 0.0F, 18.0F);
			String name = key.identifier().getPath();
			context.takeScreenshot("mlinnotas-nether-" + name);
			Map<String, Integer> found = world.getServer().computeOnServer(server -> countDecor(nether(server), pos));
			MlinNotas.LOGGER.info("[NetherClientGameTest] {} em {}: {}", name, pos, found);
			if (key == NetherRegistry.BURNING_GROVE && found.getOrDefault("burning_stem", 0) == 0) {
				throw new IllegalStateException("Bosque Ardente sem arvores perto de " + pos + ": " + found);
			}
			if (key == NetherRegistry.ASHEN_PLAINS && found.getOrDefault("nether_ash", 0) == 0) {
				throw new IllegalStateException("Planicies de Cinza sem cinza perto de " + pos + ": " + found);
			}
			if (key == NetherRegistry.INFERNITE_PEAKS && found.getOrDefault("infernite_ore", 0) == 0) {
				throw new IllegalStateException("Picos de Infernita sem infernita perto de " + pos + ": " + found);
			}
		}
	}

	private static Map<String, Integer> countDecor(ServerLevel nether, BlockPos center) {
		Map<String, Block> blocks = new LinkedHashMap<>();
		blocks.put("nether_ash", NetherRegistry.NETHER_ASH);
		blocks.put("soul_soil", Blocks.SOUL_SOIL);
		blocks.put("basalt", Blocks.BASALT);
		blocks.put("blackstone", Blocks.BLACKSTONE);
		blocks.put("infernite_ore", com.mlin.notas.registry.ModBlocks.INFERNITE_ORE);
		blocks.put("ember_nylium", NetherRegistry.EMBER_NYLIUM);
		blocks.put("burning_stem", NetherRegistry.BURNING_STEM);
		blocks.put("burning_cap", NetherRegistry.BURNING_CAP);
		blocks.put("weeping_vines", Blocks.WEEPING_VINES);
		blocks.put("lava", Blocks.LAVA);
		Map<String, Integer> counts = new LinkedHashMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -32; dx <= 32; dx++) {
			for (int dz = -32; dz <= 32; dz++) {
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				if (!nether.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				for (int y = 1; y < 127; y++) {
					BlockState state = nether.getBlockState(pos.set(x, y, z));
					for (Map.Entry<String, Block> entry : blocks.entrySet()) {
						if (state.is(entry.getValue())) {
							counts.merge(entry.getKey(), 1, Integer::sum);
						}
					}
				}
			}
		}
		return counts;
	}

	private static void wideView(ClientGameTestContext context, TestSingleplayerContext world) {
		look(context, world, 256, 256, 22, 45.0F, 12.0F);
		context.takeScreenshot("mlinnotas-nether-wide");
		look(context, world, 256, 256, 3, 200.0F, 0.0F);
		context.takeScreenshot("mlinnotas-nether-eye-level");
	}

	private static void slug(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos grove = biomeCenters(world).get(NetherRegistry.BURNING_GROVE);
		look(context, world, grove.getX(), grove.getZ(), 2, 180.0F, 25.0F);
		world.getServer().runOnServer(server -> {
			var player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel nether = nether(server);
			BlockPos feet = player.blockPosition();
			for (int i = 0; i < 3; i++) {
				BlockPos at = feet.offset(-1 + i, -2, -3 - i % 2);
				while (!nether.getBlockState(at).isAir() && at.getY() < feet.getY() + 4) {
					at = at.above();
				}
				while (nether.getBlockState(at.below()).isAir() && at.getY() > 33) {
					at = at.below();
				}
				MagmaSlug mob = NetherRegistry.MAGMA_SLUG.spawn(nether, at, EntitySpawnReason.COMMAND);
				if (mob != null) {
					mob.setNoAi(true);
					mob.setYRot(30.0F * i);
					mob.yBodyRot = mob.getYRot();
					mob.setYHeadRot(mob.getYRot());
				}
			}
		});
		context.waitTicks(30);
		context.takeScreenshot("mlinnotas-nether-magma-slug");
	}

	/** Fortaleza e bastiao mais perto do centro: fundo da estrutura x chao do terreno em volta, e um screenshot de cada. */
	private static void structures(ClientGameTestContext context, TestSingleplayerContext world) {
		for (String id : List.of("fortress", "bastion_remnant")) {
			String info = world.getServer().computeOnServer(server -> {
				ServerLevel nether = nether(server);
				Holder<Structure> structure = nether.registryAccess().lookupOrThrow(Registries.STRUCTURE)
						.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id)));
				var found = nether.getChunkSource().getGenerator().findNearestMapStructure(nether, HolderSet.direct(structure), new BlockPos(256, 64, 256),
						60, false);
				if (found == null) {
					return null;
				}
				BlockPos at = found.getFirst();
				nether.getChunk(at.getX() >> 4, at.getZ() >> 4);
				StructureStart start = nether.structureManager().getStartForStructure(structure.value(), nether.getChunk(at.getX() >> 4, at.getZ() >> 4));
				if (start == null || !start.isValid()) {
					return at.getX() + " " + at.getZ() + " ? ?";
				}
				var box = start.getBoundingBox();
				DensitySampler floor = floorSampler(nether);
				int cx = box.getCenter().getX();
				int cz = box.getCenter().getZ();
				int ground = (int) Math.ceil(floor.sampleValue(SamplerContext.EMPTY_UNCACHED, cx, 64, cz));
				MlinNotas.LOGGER.info("[NetherClientGameTest] {} em {}: caixa y {}..{}, chao no centro {}", id, at, box.minY(), box.maxY(), ground);
				return cx + " " + cz + " " + box.minY() + " " + ground;
			});
			if (info == null) {
				MlinNotas.LOGGER.warn("[NetherClientGameTest] {} nao encontrado perto do centro", id);
				continue;
			}
			String[] parts = info.split(" ");
			int x = Integer.parseInt(parts[0]);
			int z = Integer.parseInt(parts[1]);
			int back = id.equals("fortress") ? 26 : 48;
			look(context, world, x - back, z - back, 18, -45.0F, 22.0F);
			context.takeScreenshot("mlinnotas-nether-" + id);
		}
	}
}
