package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.BuildingPlans;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.Canvas;
import com.mlin.notas.village.Facing;
import com.mlin.notas.village.FoodBuildingsServer;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.Zone;
import com.mlin.notas.village.works.VillageWorks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fotos das construcoes de producao e comida (Vilas 2.0, fase 5): uma vila da Cidade numa clareira plana com as nove
 * construcoes; cada uma do nivel I ao maximo (3/4 da frente), por dentro no nivel maximo e por tras; e uma vila da
 * Capital com os celeiros e moinhos decorativos das obras rurais.
 */
@SuppressWarnings("UnstableApiUsage")
public class FoodBuildingsClientGameTest implements FabricClientGameTest {
	private static final int R = 70;
	private static final String P = "mlinnotas-food-";
	private static final List<String> KINDS = List.of("granary", "mill", "bakery", "orchard", "pen", "apiary", "butcher", "winery", "sawmill");
	/** Camera por dentro (coordenadas da planta): {camX, camY, camZ, olharX, olharY, olharZ}. */
	private static final Map<String, double[]> INSIDE = Map.of(
			"granary", new double[] {7, 2.2, 8.4, 7, 1.6, 2},
			"mill", new double[] {7, 2.2, 7.4, 7, 1.4, 3},
			"bakery", new double[] {5, 2.2, 6.4, 4, 1.4, 2},
			"butcher", new double[] {5, 2.2, 6.4, 5, 1.4, 2},
			"winery", new double[] {14, 2.2, 8.4, 14, 1.4, 3},
			"sawmill", new double[] {7, 2.4, 10.4, 7, 1.4, 3},
			"apiary", new double[] {5, 2.2, 4.4, 5, 1.4, 1});

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-comida")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			VillageClientGameTest.hideHud(context, true);
			BlockPos board = real.getServer().computeOnServer(server -> build(server, 400, VillageLevel.TOWN, true));
			MlinNotas.LOGGER.info("[FoodBuildingsClientGameTest] vila em {}", board);
			for (String kind : KINDS) {
				int max = BuildingTypes.maxLevel(kind);
				for (int level = 1; level <= max; level++) {
					if (level > 1) {
						int to = level;
						real.getServer().runOnServer(server -> upgrade(server, VillageServer.nearest(server.overworld(), board, 4), kind, to));
					}
					double[][] view = real.getServer().computeOnServer(server -> outside(server, board, kind, 1.0));
					VillageClientGameTest.shot(context, real, board, view[0], view[1], P + kind + "-" + level);
					if (level == max) {
						double[][] back = real.getServer().computeOnServer(server -> outside(server, board, kind, -1.0));
						VillageClientGameTest.shot(context, real, board, back[0], back[1], P + kind + "-" + level + "-back");
						double[][] top = real.getServer().computeOnServer(server -> top(server, board, kind));
						VillageClientGameTest.shot(context, real, board, top[0], top[1], P + kind + "-" + level + "-top");
						if (INSIDE.containsKey(kind)) {
							double[][] in = real.getServer().computeOnServer(server -> inside(server, board, kind));
							VillageClientGameTest.shot(context, real, board, in[0], in[1], P + kind + "-" + level + "-inside");
						}
					}
				}
			}
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 62, 3}, new double[] {0.5, 0, 0}, P + "overview-top");
			VillageClientGameTest.shot(context, real, board, new double[] {-46, 40, 46}, new double[] {0, 0, 0}, P + "overview-angle");
			// obras rurais: celeiros de apoio e moinhos decorativos nos lotes vagos dos arredores
			BlockPos rural = real.getServer().computeOnServer(server -> build(server, 800, VillageLevel.CAPITAL, false));
			int[][] spots = real.getServer().computeOnServer(server -> ruralSpots(server, rural));
			MlinNotas.LOGGER.info("[FoodBuildingsClientGameTest] lotes rurais com celeiro/moinho: {}", spots.length);
			int shown = 0;
			for (int[] s : spots) {
				if (shown >= 4) {
					break;
				}
				String name = s[2] == 1 ? "barn" : "windmill";
				VillageClientGameTest.shot(context, real, rural, new double[] {s[0] + 14, 11, s[1] + 14}, new double[] {s[0], 4, s[1]}, P + "rural-" + name + "-" + shown);
				shown++;
			}
			VillageClientGameTest.hideHud(context, false);
		}
	}

	/** Clareira plana de grama longe do acampamento, quadro no meio e a vila no nivel dado (com as nove construcoes se pedido). */
	private static BlockPos build(MinecraftServer server, int dx0, VillageLevel level, boolean withBuildings) {
		ServerLevel world = server.overworld();
		BlockPos spawn = world.getRespawnData().pos();
		int x = spawn.getX() + dx0;
		int z = spawn.getZ();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = (z - R) >> 4; cz <= (z + R) >> 4; cz++) {
				world.getChunk(cx, cz);
			}
		}
		int y0 = Math.max(world.getSeaLevel() + 4, world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 8; y < y0 + 40; y++) {
					BlockState state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					world.setBlock(new BlockPos(x + dx, y, z + dz), state, 2);
				}
			}
		}
		BlockPos board = new BlockPos(x, y0, z);
		VillageBoardBlock.placeAt(world, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState v = VillageServer.create(world, board, false);
		VillagePlanning.ensureLayout(world, v);
		v.setLevel(level);
		VillagePlanning.grow(world, v);
		VillageTerrain.prepareOpenRings(world, v, false);
		if (withBuildings) {
			for (VillageProject p : List.of(VillageProject.PLAZA, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE,
					VillageProject.HOUSE, VillageProject.FARM, VillageProject.TOWN_HALL, VillageProject.GRANARY, VillageProject.MILL, VillageProject.BAKERY,
					VillageProject.ORCHARD, VillageProject.PEN, VillageProject.APIARY, VillageProject.BUTCHER, VillageProject.WINERY,
					VillageProject.SAWMILL, VillageProject.LAMPS)) {
				v.enqueue(p);
			}
			VillageServer.completeAll(server, v);
			VillageBuildings.townHallLevel(v);
			VillageLayout.Lot hall = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals("town_hall")).findFirst().orElseThrow();
			v.buildingsState().setLevel(hall.id(), 3);
			BuildingServer.count(world, v);
			MlinNotas.LOGGER.info("[FoodBuildingsClientGameTest] obras: {} lotes prontos, parados {}", v.layout().lots().stream().filter(VillageLayout.Lot::built)
					.count(), VillageBuildings.idle(v));
			FoodBuildingsServer.cycle(server, v);
		} else {
			for (int ring = 1; ring <= 6; ring++) {
				if (!v.ring(ring).done()) {
					VillageTerrain.prepareNow(world, v, ring, true);
				}
			}
			VillagePlanning.grow(world, v);
			VillageWorks.runAll(world, v);
		}
		VillageServer.sync(server, v);
		return board;
	}

	private static VillageLayout.Lot lot(VillageState v, String kind) {
		return v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst()
				.orElseThrow(() -> new IllegalStateException("sem " + kind));
	}

	private static void upgrade(MinecraftServer server, VillageState v, String kind, int to) {
		VillageLayout.Lot lot = lot(v, kind);
		BuildingsState b = v.buildingsState();
		while (b.level(lot.id()) < to) {
			int from = b.level(lot.id());
			Map<String, Integer> all = new HashMap<>();
			for (VillageCost cost : BuildingTypes.upgradeCost(lot.project(), from + 1)) {
				all.put(cost.id(), cost.count());
			}
			BuildingsState.Deposit d = b.deposit(lot.id(), lot.project(), from, VillageBuildings.cap(v, kind), all);
			if (d.outcome() != BuildingsState.Outcome.COMPLETE) {
				throw new IllegalStateException("melhoria de " + kind + " recusada: " + d.outcome());
			}
			BuildingServer.completeUpgrades(server, v);
			if (b.level(lot.id()) != from + 1) {
				throw new IllegalStateException(kind + " nao subiu de " + from);
			}
		}
		VillageServer.sync(server, v);
	}

	private static int groundRel(MinecraftServer server, BlockPos board, VillageState v, VillageLayout.Lot lot) {
		VillageData.BuildRecord record = VillageData.get(server).record(v.key(), lot.id());
		return record.ground - board.getY();
	}

	/** Camera 3/4 de frente ({@code side} 1) ou por tras (-1), relativa ao quadro: {olho, alvo}. */
	private static double[][] outside(MinecraftServer server, BlockPos board, String kind, double side) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		Facing front = Facing.SOUTH.rotate(lot.rot());
		double cx = lot.x() + lot.w() / 2.0;
		double cz = lot.z() + lot.d() / 2.0;
		int g = groundRel(server, board, v, lot);
		double dist = Math.max(lot.w(), lot.d()) * 1.35 + 7;
		double h = g + 12 + (kind.equals("mill") ? 4 : 0);
		double ex = cx + side * (front.dx * dist - front.dz * dist * 0.4);
		double ez = cz + side * (front.dz * dist + front.dx * dist * 0.4);
		return new double[][] {{ex, h, ez}, {cx, g + 3, cz}};
	}

	private static double[][] top(MinecraftServer server, BlockPos board, String kind) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		Facing front = Facing.SOUTH.rotate(lot.rot());
		double cx = lot.x() + lot.w() / 2.0;
		double cz = lot.z() + lot.d() / 2.0;
		int g = groundRel(server, board, v, lot);
		return new double[][] {{cx + front.dx * 5, g + 30, cz + front.dz * 5}, {cx, g, cz}};
	}

	/** Camera por dentro: coordenadas da planta giradas para o lote. */
	private static double[][] inside(MinecraftServer server, BlockPos board, String kind) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		VillageData.BuildRecord record = VillageData.get(server).record(v.key(), lot.id());
		Canvas canvas = BuildingPlans.plan(lot.kind(), v.style(), lot.seed(), v.buildingsState().level(lot.id()));
		double[] p = INSIDE.get(kind);
		double[] eye = world(lot, canvas, p[0], p[2]);
		double[] look = world(lot, canvas, p[3], p[5]);
		int g = record.ground - board.getY();
		return new double[][] {{eye[0], g + p[1], eye[1]}, {look[0], g + p[4], look[1]}};
	}

	/** Ponto da planta (x, z com fracao: o centro do bloco e +0,5) no mundo, relativo ao quadro. */
	private static double[] world(VillageLayout.Lot lot, Canvas canvas, double x, double z) {
		int[] a = VillagePlanner.rotate((int) Math.floor(x), (int) Math.floor(z), canvas.w(), canvas.d(), lot.rot());
		return new double[] {lot.x() + a[0] + 0.5, lot.z() + a[1] + 0.5};
	}

	/** Lotes vagos dos arredores onde a obra rural pos celeiro (1) ou moinho (2): {x, z relativos ao quadro, tipo}. */
	private static int[][] ruralSpots(MinecraftServer server, BlockPos board) {
		ServerLevel level = server.overworld();
		VillageState v = VillageServer.nearest(level, board, 4);
		MasterPlan plan = v.layout().master();
		List<int[]> out = new java.util.ArrayList<>();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.zone() != Zone.OUTSKIRTS || p.retired() || p.size().width() < 11) {
				continue;
			}
			int x0 = p.rect().x0();
			int z0 = p.rect().z0();
			int hay = 0;
			int wool = 0;
			int stairs = 0;
			for (int x = p.rect().x0(); x <= p.rect().x1(); x++) {
				for (int z = p.rect().z0(); z <= p.rect().z1(); z++) {
					for (int y = -2; y <= 12; y++) {
						BlockState s = level.getBlockState(board.offset(x, y, z));
						hay += s.is(Blocks.HAY_BLOCK) ? 1 : 0;
						wool += s.is(Blocks.WOOL.white()) ? 1 : 0;
						stairs += s.is(Blocks.OAK_STAIRS) || s.is(Blocks.STONE_BRICK_STAIRS) ? 1 : 0;
					}
				}
			}
			int cx = (x0 + p.rect().x1()) / 2;
			int cz = (z0 + p.rect().z1()) / 2;
			if (wool > 4 && stairs > 8) {
				out.add(new int[] {cx, cz, 2});
			} else if (hay > 4 && stairs > 8) {
				out.add(new int[] {cx, cz, 1});
			}
		}
		return out.toArray(new int[0][]);
	}
}
