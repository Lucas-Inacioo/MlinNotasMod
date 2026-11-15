package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.village.VillageOverlay;
import com.mlin.notas.client.village.VillagePlacement;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageStyle;
import com.mlin.notas.village.WorldTerrain;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.List;

/**
 * Fotos do plano da vila (chamadas pelo {@link VillageClientGameTest}): contorno no mundo, fantasma da escolha de lugar
 * (verde num lote planejado, vermelho em cima da praca) e uma vila crescida numa encosta, com e sem contorno.
 */
@SuppressWarnings("UnstableApiUsage")
final class Village2Shots {
	private Village2Shots() {
	}

	private static void showOverlay(TestSingleplayerContext real, ClientGameTestContext context, BlockPos center) {
		real.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			VillageState village = VillageServer.nearest(server.overworld(), center, 4);
			VillagePlanning.show(player, village, 600);
		});
		context.waitFor(mc -> VillageOverlay.active(), 100);
	}

	/**
	 * Plano diretor da vila inicial (Aldeia): contorno inteiro de cima (ruas curvas, quarteiroes por zona, aneis
	 * fechados em cinza) e de lado; depois o anel 3 aplainado na hora, com e sem contorno.
	 */
	static void masterPlanShots(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c) {
		showOverlay(real, context, c);
		VillageClientGameTest.shot(context, real, c, new double[] {0.5, 62, 3}, new double[] {0.5, 0, 0}, "mlinnotas-village-plan-top");
		VillageClientGameTest.shot(context, real, c, new double[] {-50, 36, 50}, new double[] {0, 0, 0}, "mlinnotas-village-plan-angle");
		VillageClientGameTest.shot(context, real, c, new double[] {12, 9, 30}, new double[] {-6, 0, 10}, "mlinnotas-village-plan-street");
		real.getServer().runOnServer(server -> {
			VillageState village = VillageClientGameTest.starter(server);
			com.mlin.notas.village.VillageTerrain.prepareNow(server.overworld(), village, 3, false);
			MlinNotas.LOGGER.info("[VillageClientGameTest] anel 3 aplainado: {} colunas", village.ring(3).columns());
		});
		showOverlay(real, context, c);
		VillageClientGameTest.shot(context, real, c, new double[] {-50, 36, 50}, new double[] {0, 0, 0}, "mlinnotas-village-ring3-overlay");
		context.runOnClient(mc -> VillageOverlay.clear());
		VillageClientGameTest.shot(context, real, c, new double[] {-46, 26, 46}, new double[] {0, 0, 0}, "mlinnotas-village-ring3-prepared");
		VillageClientGameTest.shot(context, real, c, new double[] {0.5, 70, 34}, new double[] {0.5, 0, 0}, "mlinnotas-village-ring3-top");
	}

	/** Contorno do acampamento visto de cima e de lado; depois a escolha de lugar com o fantasma. */
	static void overlayAndGhost(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c) {
		// a Aldeia ja planeja lotes livres (o acampamento so tem os que ja estao construidos)
		real.getServer().runOnServer(server -> {
			VillageState village = VillageClientGameTest.starter(server);
			village.setLevel(VillageLevel.HAMLET);
			VillagePlanning.grow(server.overworld(), village);
		});
		showOverlay(real, context, c);
		VillageClientGameTest.shot(context, real, c, new double[] {0.5, 40, 24}, new double[] {0.5, 0, 2}, "mlinnotas-village-overlay-top");
		VillageClientGameTest.shot(context, real, c, new double[] {-22, 14, 22}, new double[] {0, 0, 0}, "mlinnotas-village-overlay-side");
		VillageState state = real.getServer().computeOnServer(VillageClientGameTest::starter);
		VillageLayout.Lot free = state.layout().firstFree("house");
		if (free == null) {
			MlinNotas.LOGGER.warn("[VillageClientGameTest] sem lote livre de casa para o fantasma");
			return;
		}
		VillageStyle style = state.style();
		BlockPos board = VillageServer.center(state);
		context.runOnClient(mc -> VillagePlacement.start("house", board, board, VillageOverlay.current().layout(), style));
		double lx = free.x() + free.w() / 2.0;
		double lz = free.z() + free.d() / 2.0;
		double len = Math.max(1, Math.sqrt(lx * lx + lz * lz));
		VillageClientGameTest.hideHud(context, false);
		VillageClientGameTest.shot(context, real, c, new double[] {lx + lx / len * 16, 22, lz + lz / len * 16}, new double[] {lx, 0, lz},
				"mlinnotas-village-ghost-snap");
		// em cima da praca: vermelho
		VillageClientGameTest.shot(context, real, c, new double[] {lx / len * 16, 20, lz / len * 16}, new double[] {0.5, 0, 0.5},
				"mlinnotas-village-ghost-invalid");
		// lugar novo livre, girado
		context.runOnClient(mc -> VillagePlacement.rotate(1));
		double ox = lx + lz / len * 14;
		double oz = lz - lx / len * 14;
		VillageClientGameTest.shot(context, real, c, new double[] {ox * 1.5, 22, oz * 1.5}, new double[] {ox, 0, oz}, "mlinnotas-village-ghost-free");
		context.runOnClient(mc -> VillagePlacement.stop());
		VillageClientGameTest.hideHud(context, true);
	}

	/** Lugar inclinado (desnivel 7 a 16 em 60 blocos), seco, a 150-400 blocos da vila inicial. */
	private static BlockPos slopeSite(MinecraftServer server, BlockPos from) {
		ServerLevel level = server.overworld();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -400; dx <= 400; dx += 40) {
			for (int dz = -400; dz <= 400; dz += 40) {
				if (Math.abs(dx) < 150 && Math.abs(dz) < 150) {
					continue;
				}
				int x = from.getX() + dx;
				int z = from.getZ() + dz;
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				boolean dry = true;
				for (int sx = -30; sx <= 30 && dry; sx += 6) {
					for (int sz = -30; sz <= 30; sz += 6) {
						int surface = generator.getBaseHeight(x + sx, z + sz, Heightmap.Types.WORLD_SURFACE_WG, level, random);
						int floor = generator.getBaseHeight(x + sx, z + sz, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
						if (floor < surface || surface <= level.getSeaLevel() + 1) {
							dry = false;
							break;
						}
						min = Math.min(min, surface);
						max = Math.max(max, surface);
					}
				}
				int spread = max - min;
				if (!dry || spread < 12 || spread > 28) {
					continue;
				}
				double score = Math.abs(spread - 18) + Math.hypot(dx, dz) / 200.0;
				if (score < bestScore) {
					bestScore = score;
					best = new BlockPos(x, 0, z);
				}
			}
		}
		return best;
	}

	/** Vila numa encosta crescida ate Cidade: fotos com e sem contorno. */
	static void slopeVillage(ClientGameTestContext context, TestSingleplayerContext real, BlockPos from) {
		BlockPos center = real.getServer().computeOnServer(server -> {
			BlockPos site = slopeSite(server, from);
			if (site == null) {
				return null;
			}
			ServerLevel level = server.overworld();
			for (int cx = (site.getX() - 64) >> 4; cx <= (site.getX() + 64) >> 4; cx++) {
				for (int cz = (site.getZ() - 64) >> 4; cz <= (site.getZ() + 64) >> 4; cz++) {
					level.getChunk(cx, cz);
				}
			}
			WorldTerrain terrain = new WorldTerrain(level, site);
			BlockPos board = null;
			for (int r = 0; r <= 10 && board == null; r++) {
				for (int dx = -r; dx <= r && board == null; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if ((Math.abs(dx) == r || Math.abs(dz) == r) && terrain.free(dx, dz) && terrain.free(dx, dz + 1)) {
							board = site.offset(dx, 0, dz).atY(terrain.ground(dx, dz) + 1);
							break;
						}
					}
				}
			}
			if (board == null) {
				return null;
			}
			VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
			VillageState village = VillageServer.create(level, board, false);
			village.setLevel(VillageLevel.TOWN);
			village.addProsperity(600);
			com.mlin.notas.village.VillageTerrain.prepareOpenRings(level, village, false);
			VillagePlanning.grow(level, village);
			for (VillageProject p : List.of(VillageProject.PLAZA, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM, VillageProject.HOUSE,
					VillageProject.SMITHY, VillageProject.MARKET, VillageProject.HOUSE, VillageProject.FARM, VillageProject.HOUSE, VillageProject.LAMPS)) {
				village.enqueue(p);
			}
			VillageServer.completeAll(server, village);
			MlinNotas.LOGGER.info("[VillageClientGameTest] vila na encosta {} em {}: {} lotes, {} prontos", village.name(), board,
					village.layout().lots().size(), village.layout().lots().stream().filter(VillageLayout.Lot::built).count());
			return board;
		});
		if (center == null) {
			MlinNotas.LOGGER.warn("[VillageClientGameTest] nenhuma encosta seca perto para a vila inclinada");
			return;
		}
		VillageClientGameTest.shot(context, real, center, new double[] {-34, 22, 34}, new double[] {0, 0, 0}, "mlinnotas-village-slope-sw");
		VillageClientGameTest.shot(context, real, center, new double[] {34, 18, -34}, new double[] {0, 0, 0}, "mlinnotas-village-slope-ne");
		VillageClientGameTest.shot(context, real, center, new double[] {0.5, 60, 16}, new double[] {0.5, 0, 0}, "mlinnotas-village-slope-top");
		VillageClientGameTest.shot(context, real, center, new double[] {8, 3, 10}, new double[] {-6, 2, -10}, "mlinnotas-village-slope-low");
		showOverlay(real, context, center);
		VillageClientGameTest.shot(context, real, center, new double[] {-34, 26, 34}, new double[] {0, 0, 0}, "mlinnotas-village-slope-overlay");
		VillageClientGameTest.shot(context, real, center, new double[] {0.5, 62, 3}, new double[] {0.5, 0, 0}, "mlinnotas-village-slope-plan");
		context.runOnClient(mc -> VillageOverlay.clear());
		// muro de arrimo entre dois platos, visto do lado baixo
		int[] wall = real.getServer().computeOnServer(server -> {
			VillageState village = VillageServer.nearest(server.overworld(), center, 4);
			com.mlin.notas.village.MasterPlan plan = village.layout().master();
			com.mlin.notas.village.VillagePalette palette = com.mlin.notas.village.VillagePalette.of(village.style());
			int[] best = null;
			int bestDrop = -100;
			for (int ring = 1; ring <= 5; ring++) {
				for (long col : plan.columns(ring)) {
					int x = VillageLayout.colX(col);
					int z = VillageLayout.colZ(col);
					int h = plan.plannedHeight(x, z);
					var below = server.overworld().getBlockState(new BlockPos(village.x() + x, h - 1, village.z() + z));
					if (!below.is(palette.brick) && !below.is(palette.foundation)) {
						continue;
					}
					for (com.mlin.notas.village.Facing f : com.mlin.notas.village.Facing.values()) {
						int nh = server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, village.x() + x + f.dx, village.z() + z + f.dz) - 1;
						int drop = nh == Integer.MIN_VALUE ? 0 : h - nh;
						if (drop > bestDrop && Math.hypot(x, z) < 70) {
							bestDrop = drop;
							best = new int[] {x, h - village.y(), z, f.dx, f.dz, drop};
						}
					}
				}
			}
			return best;
		});
		if (wall != null) {
			MlinNotas.LOGGER.info("[VillageClientGameTest] muro de arrimo em {},{} com {} de queda", wall[0], wall[2], wall[5]);
			VillageClientGameTest.shot(context, real, center, new double[] {wall[0] + wall[3] * 8 + 0.5, wall[1] + 2, wall[2] + wall[4] * 8 + 0.5},
					new double[] {wall[0] + 0.5, wall[1] - 1, wall[2] + 0.5}, "mlinnotas-village-slope-wall");
		}
	}
}
