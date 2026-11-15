package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.village.FieldPins;
import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.HeightFieldBuilder;
import com.mlin.notas.village.VillageFieldServer;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.WorldTerrain;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Locale;

/**
 * Vilas 3.0 fase 2: mundos novos com cinco sementes de relevo diferente (planicie, montanha nevada perto do spawn,
 * floresta escura, rio, deserto). A vila inicial nasce com o terreno do campo de alturas ja gerado pelo gerador do mundo: o teste compara o chao
 * de verdade de toda coluna do plano com {@link HeightField#height} (desvio por tipo de coluna, agua e buraco na camada
 * fechada, degraus na faixa de transicao contra o natural de fora), mede o custo da geracao de chunks com e sem o campo
 * e fotografa do alto (3/4 dos dois lados e de cima, mostrando a faixa e o natural alem) e do chao na borda da faixa.
 * Arquivos {@code mlinnotas-vgen-<nome>-*}.
 */
@SuppressWarnings("UnstableApiUsage")
public class VillageGenClientGameTest implements FabricClientGameTest {
	/** Semente e nome de cada mundo. */
	static final List<String[]> WORLDS = List.of(new String[] {"46", "plains"}, new String[] {"56", "mountain"}, new String[] {"33", "forest"},
			new String[] {"17", "river"}, new String[] {"55", "desert"});

	@Override
	public void runTest(ClientGameTestContext context) {
		boolean first = true;
		for (String[] world : WORLDS) {
			run(context, world[0], world[1], first);
			first = false;
		}
	}

	private static void run(ClientGameTestContext context, String seed, String name, boolean perf) {
		String p = "mlinnotas-vgen-" + name + "-";
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed(seed)).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			real.getServer().runCommand("gamerule advance_weather false");
			VillageClientGameTest.hideHud(context, true);
			String stats = real.getServer().computeOnServer(VillageGenClientGameTest::measure);
			MlinNotas.LOGGER.info("[VillageGenClientGameTest] {} (semente {}): {}", name, seed, stats);
			if (perf) {
				String timing = real.getServer().computeOnServer(VillageGenClientGameTest::timing);
				MlinNotas.LOGGER.info("[VillageGenClientGameTest] custo: {}", timing);
			}
			int[] info = real.getServer().computeOnServer(server -> {
				VillageState v = VillageClientGameTest.starter(server);
				HeightField f = VillageFieldServer.active();
				BlockPos c = VillageServer.center(v);
				// ponto do chao logo fora da faixa (lado leste) e no meio da faixa (lado sul)
				int edge = f.extent() + 6;
				ServerLevel level = server.overworld();
				int ex = c.getX() + edge;
				level.getChunk((ex + 12) >> 4, (c.getZ() + 6) >> 4);
				// altura no lugar da camera (o natural pode subir logo fora da faixa)
				int ey = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex + 12, c.getZ() + 6);
				int mz = c.getZ() + f.planExtent() + f.band() / 2;
				level.getChunk((c.getX() + 20) >> 4, mz >> 4);
				int my = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + 20, mz);
				return new int[] {c.getX(), c.getY(), c.getZ(), f.extent(), f.planExtent(), edge, ey, mz - c.getZ(), my};
			});
			BlockPos c = new BlockPos(info[0], info[1], info[2]);
			int extent = info[3];
			int distance = context.computeOnClient(mc -> mc.options.renderDistance().get());
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(16);
				mc.options.broadcastOptions();
			});
			real.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(16));
			VillageClientGameTest.spectator(real, true);
			real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d %d %d 0 90", c.getX(), c.getY() + 150, c.getZ()));
			context.waitTicks(1200);
			shot(context, real, c, new double[] {-extent * 1.05, extent * 0.55, extent * 1.05}, new double[] {0, 0, 0}, p + "air-sw");
			shot(context, real, c, new double[] {extent * 1.05, extent * 0.5, -extent * 1.05}, new double[] {0, 0, 0}, p + "air-ne");
			shot(context, real, c, new double[] {0.5, extent * 1.45, 2}, new double[] {0.5, 0, 0}, p + "top");
			// chao: de fora da faixa olhando para a vila, e no meio da faixa olhando ao longo dela
			shot(context, real, c, new double[] {info[5] + 12, info[6] - c.getY() + 3, 6}, new double[] {info[4] - 20, info[6] - c.getY() - 2, 0},
					p + "ground-edge");
			shot(context, real, c, new double[] {20, info[8] - c.getY() + 3, info[7]}, new double[] {-60, info[8] - c.getY() - 4, info[7] - 10},
					p + "ground-band");
			VillageClientGameTest.hideHud(context, false);
			context.runOnClient(mc -> mc.options.renderDistance().set(distance));
		}
	}

	/** Como o {@code shot} do {@link CrownClientGameTest}: alcance grande, tempo fixo para os chunks de longe. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext real, BlockPos o, double[] eye, double[] look, String name) {
		VillageClientGameTest.spectator(real, true);
		double ex = o.getX() + 0.5 + eye[0];
		double ey = o.getY() + eye[1];
		double ez = o.getZ() + 0.5 + eye[2];
		double dx = o.getX() + 0.5 + look[0] - ex;
		double dz = o.getZ() + 0.5 + look[2] - ez;
		double dy = o.getY() + look[1] - (ey + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", ex, ey, ez, yaw, pitch));
		context.waitTicks(400);
		context.takeScreenshot(name);
	}

	/** Chao da coluna: desce do topo passando por construcao/planta ate o primeiro bloco de chao. */
	private static int ground(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		pos.set(x, top, z);
		for (int i = 0; i < 24; i++) {
			BlockState s = level.getBlockState(pos);
			if (WorldTerrain.naturalGround(s) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND)) {
				return pos.getY();
			}
			pos.move(0, -1, 0);
		}
		return top;
	}

	/** Gera o campo inteiro e compara o chao de verdade com o campo. */
	private static String measure(MinecraftServer server) {
		ServerLevel level = server.overworld();
		VillageState v = VillageClientGameTest.starter(server);
		HeightField f = VillageFieldServer.active();
		if (f == null) {
			throw new IllegalStateException("vila inicial sem campo ativo");
		}
		int cx = f.centerX();
		int cz = f.centerZ();
		int e = f.extent();
		long start = System.nanoTime();
		for (int x = (cx - e - 40) >> 4; x <= (cx + e + 40) >> 4; x++) {
			for (int z = (cz - e - 40) >> 4; z <= (cz + e + 40) >> 4; z++) {
				level.getChunk(x, z);
			}
		}
		long genMs = (System.nanoTime() - start) / 1_000_000;
		VillageLayout layout = v.layout();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int size = 2 * (e + 40) + 1;
		int[] g = new int[size * size];
		// [lote, rua, livre, praca] -> colunas, soma |desvio|, max, decoracao
		long[][] dev = new long[4][4];
		int holes = 0;
		int water = 0;
		int waterAbove = 0;
		String firstBad = "";
		for (int x = -e - 40; x <= e + 40; x++) {
			for (int z = -e - 40; z <= e + 40; z++) {
				int wx = cx + x;
				int wz = cz + z;
				int gy = ground(level, pos, wx, wz);
				g[(x + e + 40) * size + (z + e + 40)] = gy;
				HeightField.Kind kind = f.kindRel(x, z);
				if (!kind.inside() || kind == HeightField.Kind.WATER || kind == HeightField.Kind.KEEP) {
					continue;
				}
				if (Math.abs(x) <= 3 && Math.abs(z) <= 3 || builtLot(layout, x, z)) {
					continue;
				}
				int h = f.heightRel(x, z);
				int group = switch (kind) {
					case BLOCK -> 0;
					case STREET, GATE_STONE, GATE_OUTER, PATH -> 1;
					case PLAZA -> 3;
					default -> 2;
				};
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
				int d = Math.abs(gy - h);
				dev[group][0]++;
				dev[group][1] += d;
				dev[group][2] = Math.max(dev[group][2], d);
				if (top != gy) {
					dev[group][3]++;
				}
				if (d != 0 && firstBad.length() < 400) {
					firstBad += String.format(Locale.ROOT, "%d,%d %s campo %d chao %d (%s); ", x, z, kind, h, gy, level.getBlockState(pos.set(wx, gy, wz)));
				}
				for (int y = h - f.depthRel(x, z) + 1; y <= h; y++) {
					BlockState s = level.getBlockState(pos.set(wx, y, wz));
					if (!s.getFluidState().isEmpty()) {
						water++;
					} else if (s.isAir() || s.is(BlockTags.LEAVES)) {
						holes++;
					}
				}
				for (int y = h + 1; y <= h + 6; y++) {
					if (!level.getBlockState(pos.set(wx, y, wz)).getFluidState().isEmpty()) {
						waterAbove++;
					}
				}
			}
		}
		// degraus: maior diferenca entre vizinhos e quantos pares passam de 2, na faixa e no natural logo fora
		int[] bandSteps = steps(g, size, e + 40, f, true, e);
		int[] naturalSteps = steps(g, size, e + 40, f, false, e);
		StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "campo %d,%d praca %d, geracao de %d chunks em %d ms; ", cx, cz, f.plazaY(),
				(2 * ((e + 40) >> 4) + 1) * (2 * ((e + 40) >> 4) + 1), genMs));
		String[] names = {"lotes", "ruas", "livre", "praca"};
		for (int i = 0; i < 4; i++) {
			sb.append(String.format(Locale.ROOT, "%s: %d colunas, desvio medio %.3f max %d, decoracao %d; ", names[i], dev[i][0],
					dev[i][0] == 0 ? 0.0 : (double) dev[i][1] / dev[i][0], dev[i][2], dev[i][3]));
		}
		sb.append(String.format(Locale.ROOT, "buracos na camada fechada %d, agua na camada %d, agua acima %d; ", holes, water, waterAbove));
		sb.append(String.format(Locale.ROOT, "faixa: degrau max %d, pares >2: %d de %d; natural fora: degrau max %d, pares >2: %d de %d; ", bandSteps[0],
				bandSteps[1], bandSteps[2], naturalSteps[0], naturalSteps[1], naturalSteps[2]));
		sb.append("primeiros desvios: ").append(firstBad);
		return sb.toString();
	}

	private static boolean builtLot(VillageLayout layout, int x, int z) {
		if (layout == null) {
			return false;
		}
		for (VillageLayout.Lot lot : layout.lots()) {
			if (lot.built() && lot.rect().contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** {max, pares > 2, pares} na faixa ({@code band}) ou no anel natural de 40 logo fora do campo. */
	private static int[] steps(int[] g, int size, int half, HeightField f, boolean band, int e) {
		int max = 0;
		int big = 0;
		int pairs = 0;
		for (int x = -half; x < half; x++) {
			for (int z = -half; z < half; z++) {
				boolean in = band ? f.kindRel(x, z) == HeightField.Kind.BAND && f.kindRel(x + 1, z + 1) == HeightField.Kind.BAND
						: Math.max(Math.abs(x), Math.abs(z)) > e + 1 && Math.max(Math.abs(x + 1), Math.abs(z + 1)) > e + 1;
				if (!in) {
					continue;
				}
				int a = g[(x + half) * size + (z + half)];
				for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
					int b = g[(x + d[0] + half) * size + (z + d[1] + half)];
					int s = Math.abs(a - b);
					pairs++;
					max = Math.max(max, s);
					if (s > 2) {
						big++;
					}
				}
			}
		}
		return new int[] {max, big, pairs};
	}

	/**
	 * Custo da geracao: 8x8 chunks numa regiao longe com um campo de vila nova ativo la, e 8x8 numa outra regiao longe
	 * sem campo (o campo da vila inicial ativo, longe dali).
	 */
	private static String timing(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HeightField starter = VillageFieldServer.active();
		int rx = starter.centerX() + 6000;
		int rz = starter.centerZ() + 6000;
		HeightFieldBuilder.Natural natural = VillageFieldServer.natural(level, rx, rz);
		int minY = VillageFieldServer.minY(level);
		int plazaY = HeightFieldBuilder.plazaHeight(natural, minY);
		HeightField far = HeightFieldBuilder.forNewVillage(1234L, rx, rz, plazaY, natural, minY, List.of(), FieldPins.none()).field();
		try {
			VillageFieldServer.setActive(far);
			long with = gen(level, rx, rz);
			VillageFieldServer.setActive(starter);
			long without = gen(level, starter.centerX() - 6000, starter.centerZ() - 6000);
			VillageFieldServer.setActive(far);
			long with2 = gen(level, rx + 160, rz - 160);
			VillageFieldServer.setActive(starter);
			long without2 = gen(level, starter.centerX() - 6000 + 160, starter.centerZ() - 6000 - 160);
			return String.format(Locale.ROOT, "64 chunks com campo %.1f / %.1f ms por chunk, sem campo %.1f / %.1f ms por chunk", with / 64.0, with2 / 64.0,
					without / 64.0, without2 / 64.0);
		} finally {
			VillageFieldServer.setActive(starter);
		}
	}

	private static long gen(ServerLevel level, int x, int z) {
		long start = System.nanoTime();
		for (int cx = (x >> 4) - 4; cx < (x >> 4) + 4; cx++) {
			for (int cz = (z >> 4) - 4; cz < (z >> 4) + 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		return (System.nanoTime() - start) / 1_000_000;
	}
}
