package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.village.VillageBoardScreen;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.BuildingPlans;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.Canvas;
import com.mlin.notas.village.Facing;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanner;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fotos das construcoes de oficios e servicos (Vilas 2.0, fase 6): uma vila numa clareira plana com casas, Prefeitura V e
 * as nove obras; cada obra em cada nivel (vista 3/4 da frente), mais interiores (mesa do Encantador, nave da igreja,
 * salao da taverna), as tinas do curtume, o campo de tiro e o jardim. Fotos {@code mlinnotas-crafts-<tipo>-<nivel>}.
 */
@SuppressWarnings("UnstableApiUsage")
public class CraftsClientGameTest implements FabricClientGameTest {
	private static final int R = 80;
	private static final List<String> KINDS = List.of("quarry", "tailor", "tannery", "fletcher", "enchanter", "tavern", "garden", "church", "empty_lot");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-oficios")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			VillageClientGameTest.hideHud(context, true);
			BlockPos board = real.getServer().computeOnServer(CraftsClientGameTest::build);
			MlinNotas.LOGGER.info("[CraftsClientGameTest] vila em {}", board);
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 72, 40}, new double[] {0.5, 0, -6}, "mlinnotas-crafts-overview-1");
			for (String kind : KINDS) {
				photo(context, real, board, kind, 1);
			}
			for (int level = 2; level <= 5; level++) {
				for (String kind : KINDS) {
					if (BuildingTypes.maxLevel(kind) >= level) {
						int to = level;
						real.getServer().runOnServer(server -> upgrade(server, VillageServer.nearest(server.overworld(), board, 4), kind, to));
						photo(context, real, board, kind, level);
					}
				}
			}
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 72, 40}, new double[] {0.5, 0, -6}, "mlinnotas-crafts-overview-2");
			interiors(context, real, board);
			boardShop(context, real, board);
			VillageClientGameTest.hideHud(context, false);
		}
	}

	private static void photo(ClientGameTestContext context, TestSingleplayerContext real, BlockPos board, String kind, int level) {
		int[] view = real.getServer().computeOnServer(server -> lotView(server, board, kind));
		VillageClientGameTest.shot(context, real, board, eye(view), look(view), "mlinnotas-crafts-" + kind + "-" + level);
		if (level == BuildingTypes.maxLevel(kind) && !kind.equals("garden") && !kind.equals("empty_lot")) {
			VillageClientGameTest.shot(context, real, board, high(view), look(view), "mlinnotas-crafts-" + kind + "-" + level + "-high");
		}
	}

	private static void interiors(ClientGameTestContext context, TestSingleplayerContext real, BlockPos board) {
		// {tipo, x da planta, z da planta, ate x, ate z, altura do olho}
		Object[][] spots = {
				{"enchanter", 9, 9, 9, 4, 3.0, "mlinnotas-crafts-enchanter-inside"},
				{"church", 10, 10, 10, 3, 3.5, "mlinnotas-crafts-church-inside"},
				{"tavern", 7, 9, 9, 4, 3.0, "mlinnotas-crafts-tavern-inside"},
				{"tailor", 7, 9, 7, 3, 3.0, "mlinnotas-crafts-tailor-inside"},
				{"quarry", 10, 9, 10, 6, 3.0, "mlinnotas-crafts-quarry-inside"},
				{"tannery", 11, 9, 11, 3, 3.0, "mlinnotas-crafts-tannery-vats"},
				{"fletcher", 11, 10, 11, 1, 3.0, "mlinnotas-crafts-fletcher-range"},
				{"garden", 3, 6, 3, 3, 4.0, "mlinnotas-crafts-garden-close"}};
		for (Object[] s : spots) {
			String kind = (String) s[0];
			double[][] pts = real.getServer().computeOnServer(server -> localPoints(server, board, kind, (int) s[1], (int) s[2], (int) s[3], (int) s[4], (double) s[5]));
			VillageClientGameTest.shot(context, real, board, pts[0], pts[1], (String) s[6]);
		}
	}

	/** Olho e alvo (relativos ao quadro) para dois pontos locais (x, z) da planta do lote. */
	private static double[][] localPoints(MinecraftServer server, BlockPos board, String kind, int ex, int ez, int tx, int tz, double eyeY) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		Canvas canvas = BuildingPlans.plan(lot.kind(), v.style(), lot.seed(), v.buildingsState().level(lot.id()));
		int[] a = VillagePlanner.rotate(ex, ez, canvas.w(), canvas.d(), lot.rot());
		int[] b = VillagePlanner.rotate(tx, tz, canvas.w(), canvas.d(), lot.rot());
		return new double[][] {{lot.x() + a[0], eyeY, lot.z() + a[1]}, {lot.x() + b[0], 1.5, lot.z() + b[1]}};
	}

	/** Clareira plana de grama longe do acampamento, quadro no meio e a Metropole com casas, Prefeitura e as nove obras prontas. */
	private static BlockPos build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		int x = spawn.getX() + 400;
		int z = spawn.getZ();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = (z - R) >> 4; cz <= (z + R) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 8; y < y0 + 44; y++) {
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
		v.setLevel(VillageLevel.METROPOLIS);
		for (VillageProject p : List.of(VillageProject.PLAZA, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE,
				VillageProject.HOUSE, VillageProject.TOWN_HALL, VillageProject.LAMPS, VillageProject.ENCHANTER, VillageProject.CHURCH, VillageProject.TAVERN,
				VillageProject.TAILOR, VillageProject.FLETCHER, VillageProject.QUARRY, VillageProject.TANNERY, VillageProject.GARDEN,
				VillageProject.EMPTY_LOT)) {
			v.enqueue(p);
		}
		// fila antes do plano: as obras pagas reservam lote antes dos outros oficios (VillageLayout.first)
		VillagePlanning.grow(level, v);
		VillageTerrain.prepareOpenRings(level, v, false);
		VillageServer.completeAll(server, v);
		BuildingServer.count(level, v);
		MlinNotas.LOGGER.info("[CraftsClientGameTest] obras: {} lotes prontos, parados {}", v.layout().lots().stream().filter(VillageLayout.Lot::built)
				.count(), VillageBuildings.idle(v));
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
		if (VillageBuildings.townHallLevel(v) < to) {
			b.setLevel(lot(v, BuildingTypes.TOWN_HALL).id(), to);
		}
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

	/** Centro do lote (relativo ao quadro) e a direcao da frente: {cx, cz, fx, fz, tamanho}. */
	private static int[] lotView(MinecraftServer server, BlockPos board, String kind) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		Facing front = Facing.SOUTH.rotate(lot.rot());
		return new int[] {lot.x() + lot.w() / 2, lot.z() + lot.d() / 2, front.dx, front.dz, Math.max(lot.w(), lot.d())};
	}

	private static double[] eye(int[] v) {
		double dist = v[4] * 1.4 + 8;
		return new double[] {v[0] + v[2] * dist - v[3] * dist * 0.35, 15, v[1] + v[3] * dist + v[2] * dist * 0.35};
	}

	private static double[] high(int[] v) {
		return new double[] {v[0] + v[2] * 12, 26, v[1] + v[3] * 12};
	}

	private static double[] look(int[] v) {
		return new double[] {v[0], 4, v[1]};
	}

	/** So para o tela do quadro nao ser esquecida: abre as ofertas novas do mercado (mochila, flechas, boatos). */
	@SuppressWarnings("unused")
	private static void boardShop(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c) {
		VillageClientGameTest.spectator(real, false);
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d %d %d 180 10", c.getX(), c.getY(), c.getZ() + 3));
		context.waitTicks(10);
		real.getServer().runOnServer(server -> VillageServer.use(server.getPlayerList().getPlayers().getFirst(), server.overworld(), c));
		context.waitForScreen(VillageBoardScreen.class);
		VillageClientGameTest.hideHud(context, false);
		context.runOnClient(mc -> {
			VillageBoardScreen screen = (VillageBoardScreen) mc.gui.screen();
			screen.setTab(VillageBoardScreen.Tab.SHOP);
			screen.setPage(1);
		});
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-crafts-board-shop");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		VillageClientGameTest.hideHud(context, true);
	}
}
