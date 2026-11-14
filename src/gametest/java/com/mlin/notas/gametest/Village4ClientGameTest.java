package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.village.VillageBoardScreen;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.Facing;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fotos das construcoes com nivel (Vilas 2.0, fase 4): uma vila numa clareira plana com casas, plantacao, forja,
 * mercado e Prefeitura; a mesma casa no nivel I e depois de melhorada para o III; a Prefeitura do I ao V; e o quadro
 * nas abas Obras (botao Melhorar, obra parada) e Necessidades.
 */
@SuppressWarnings("UnstableApiUsage")
public class Village4ClientGameTest implements FabricClientGameTest {
	private static final int R = 64;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-vila4")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			VillageClientGameTest.hideHud(context, true);
			BlockPos board = real.getServer().computeOnServer(Village4ClientGameTest::build);
			MlinNotas.LOGGER.info("[Village4ClientGameTest] vila em {}", board);
			// casa no nivel I
			int[] house = real.getServer().computeOnServer(server -> lotView(server, board, "house"));
			VillageClientGameTest.shot(context, real, board, eye(house), look(house), "mlinnotas-village4-house-1");
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 72, 34}, new double[] {0.5, 0, -6}, "mlinnotas-village4-overview-1");
			boardTabs(context, real, board, "before");
			// a Prefeitura sobe um nivel por vez (fotos no I, III, IV e V); a casa vai ao III no meio
			int[] hall = real.getServer().computeOnServer(server -> lotView(server, board, "town_hall"));
			VillageClientGameTest.shot(context, real, board, eye(hall), look(hall), "mlinnotas-village4-hall-1");
			real.getServer().runOnServer(server -> VillageServer.nearest(server.overworld(), board, 4).setLevel(VillageLevel.METROPOLIS));
			for (int level = 2; level <= 5; level++) {
				int to = level;
				real.getServer().runOnServer(server -> upgrade(server, VillageServer.nearest(server.overworld(), board, 4), "town_hall", to));
				hall = real.getServer().computeOnServer(server -> lotView(server, board, "town_hall"));
				if (to >= 3) {
					VillageClientGameTest.shot(context, real, board, eye(hall), look(hall), "mlinnotas-village4-hall-" + to);
				}
				if (to == 3) {
					real.getServer().runOnServer(server -> upgrade(server, VillageServer.nearest(server.overworld(), board, 4), "house", 3));
					house = real.getServer().computeOnServer(server -> lotView(server, board, "house"));
					VillageClientGameTest.shot(context, real, board, eye(house), look(house), "mlinnotas-village4-house-3");
				}
			}
			real.getServer().runOnServer(server -> {
				VillageState v = VillageServer.nearest(server.overworld(), board, 4);
				upgrade(server, v, "smithy", 5);
				upgrade(server, v, "market", 3);
			});
			int[] smithy = real.getServer().computeOnServer(server -> lotView(server, board, "smithy"));
			VillageClientGameTest.shot(context, real, board, high(smithy), look(smithy), "mlinnotas-village4-smithy-5");
			int[] market = real.getServer().computeOnServer(server -> lotView(server, board, "market"));
			VillageClientGameTest.shot(context, real, board, high(market), look(market), "mlinnotas-village4-market-3");
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 72, 34}, new double[] {0.5, 0, -6}, "mlinnotas-village4-overview-2");
			boardTabs(context, real, board, "after");
			VillageClientGameTest.hideHud(context, false);
		}
	}

	/** Clareira plana de grama longe do acampamento, quadro no meio e a Vila com casas, oficios e Prefeitura prontos. */
	private static BlockPos build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		int x = spawn.getX() + 400;
		int z = spawn.getZ();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = (z - R) >> 4; cz <= (z + R) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 8; y < y0 + 40; y++) {
					BlockState state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, z + dz), state, 2);
				}
			}
		}
		BlockPos board = new BlockPos(x, y0, z);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState v = VillageServer.create(level, board, false);
		VillagePlanning.ensureLayout(level, v);
		v.setLevel(VillageLevel.VILLAGE);
		VillagePlanning.grow(level, v);
		VillageTerrain.prepareOpenRings(level, v, false);
		for (VillageProject p : List.of(VillageProject.PLAZA, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM, VillageProject.SMITHY,
				VillageProject.MARKET, VillageProject.TOWN_HALL, VillageProject.LAMPS)) {
			v.enqueue(p);
		}
		VillageServer.completeAll(server, v);
		BuildingServer.count(level, v);
		MlinNotas.LOGGER.info("[Village4ClientGameTest] obras: {} lotes prontos, parados {}", v.layout().lots().stream().filter(VillageLayout.Lot::built)
				.count(), com.mlin.notas.village.VillageBuildings.idle(v));
		VillageServer.sync(server, v);
		return board;
	}

	private static VillageLayout.Lot lot(VillageState v, String kind) {
		return v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst()
				.orElseThrow(() -> new IllegalStateException("sem " + kind));
	}

	/** Paga e constroi na hora as melhorias da obra do tipo ate {@code to} (a Prefeitura acompanha se precisar). */
	private static void upgrade(MinecraftServer server, VillageState v, String kind, int to) {
		VillageLayout.Lot lot = lot(v, kind);
		BuildingsState b = v.buildingsState();
		if (!kind.equals(BuildingTypes.TOWN_HALL) && com.mlin.notas.village.VillageBuildings.townHallLevel(v) < to) {
			b.setLevel(lot(v, BuildingTypes.TOWN_HALL).id(), to);
		}
		while (b.level(lot.id()) < to) {
			int from = b.level(lot.id());
			Map<String, Integer> all = new HashMap<>();
			for (VillageCost cost : BuildingTypes.upgradeCost(lot.project(), from + 1)) {
				all.put(cost.id(), cost.count());
			}
			BuildingsState.Deposit d = b.deposit(lot.id(), lot.project(), from, com.mlin.notas.village.VillageBuildings.cap(v, kind), all);
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

	/** Centro do lote (relativo ao quadro) e a direcao da frente: {cx, cz, fx, fz, altura}. */
	private static int[] lotView(MinecraftServer server, BlockPos board, String kind) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		Facing front = Facing.SOUTH.rotate(lot.rot());
		int cx = lot.x() + lot.w() / 2;
		int cz = lot.z() + lot.d() / 2;
		return new int[] {cx, cz, front.dx, front.dz, Math.max(lot.w(), lot.d())};
	}


	private static double[] eye(int[] v) {
		double dist = v[4] * 1.5 + 8;
		// de frente e um pouco de lado, para ver fachada e telhado
		return new double[] {v[0] + v[2] * dist - v[3] * dist * 0.35, 16, v[1] + v[3] * dist + v[2] * dist * 0.35};
	}

	/** De cima, na frente do lote (as casas vizinhas nao tapam). */
	private static double[] high(int[] v) {
		return new double[] {v[0] + v[2] * 11, 26, v[1] + v[3] * 11};
	}

	private static double[] look(int[] v) {
		return new double[] {v[0], 6, v[1]};
	}

	private static void boardTabs(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c, String name) {
		VillageClientGameTest.spectator(real, false);
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d %d %d 180 10", c.getX(), c.getY(), c.getZ() + 3));
		context.waitTicks(10);
		real.getServer().runOnServer(server -> VillageServer.use(server.getPlayerList().getPlayers().getFirst(), server.overworld(), c));
		context.waitForScreen(VillageBoardScreen.class);
		context.waitTicks(10);
		VillageClientGameTest.hideHud(context, false);
		context.runOnClient(mc -> ((VillageBoardScreen) mc.gui.screen()).setTab(VillageBoardScreen.Tab.BUILDINGS));
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-village4-board-buildings-" + name);
		context.runOnClient(mc -> ((VillageBoardScreen) mc.gui.screen()).setPage(-1));
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-village4-board-buildings2-" + name);
		context.runOnClient(mc -> ((VillageBoardScreen) mc.gui.screen()).setTab(VillageBoardScreen.Tab.NEEDS));
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-village4-board-needs-" + name);
		context.runOnClient(mc -> {
			VillageBoardScreen screen = (VillageBoardScreen) mc.gui.screen();
			screen.setTab(VillageBoardScreen.Tab.SHOP);
			screen.setPage(-1);
		});
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-village4-board-shop-" + name);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		VillageClientGameTest.hideHud(context, true);
	}
}
