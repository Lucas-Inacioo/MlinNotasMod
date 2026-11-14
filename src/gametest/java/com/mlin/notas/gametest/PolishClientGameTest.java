package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.BuildingPlans;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.Canvas;
import com.mlin.notas.village.Facing;
import com.mlin.notas.village.MasterPlan;
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
import com.mlin.notas.village.VillageStyle;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.Zone;
import com.mlin.notas.village.civic.CivicBuildings;
import com.mlin.notas.village.works.Blueprint;
import com.mlin.notas.village.works.Mats;
import com.mlin.notas.village.works.PlazaSculpture;
import com.mlin.notas.village.works.VillageWorks;
import com.mlin.notas.village.works.VillageWorksData;
import com.mlin.notas.village.works.WBlock;
import com.mlin.notas.vstructures.VanillaRemakePiece;
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
 * Fotos do acabamento das Vilas 2.0 (rodada 3): galeria das estatuas de chefe, praca da Cidade da Coroa (chafariz, coreto,
 * torre do relogio), Quartel e Guilda dos Exploradores, construcoes novas com o acabamento comum (floreiras, venezianas,
 * lucarnas), fachadas/sobrados/arcadas do centro, pomar com macas, e os enquadramentos refeitos do curtume e da flecharia.
 * Nome: {@code mlinnotas-polish-<assunto>}.
 */
@SuppressWarnings("UnstableApiUsage")
public class PolishClientGameTest implements FabricClientGameTest {
	private static final int R = 84;
	private static final String P = "mlinnotas-polish-";
	private static final List<String> BOSSES = List.of("cobalt_golem", "abyssal_guardian", "igneous_colossus", "astral_herald", "ender_dragon", "wither", "");

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-polish")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			VillageClientGameTest.hideHud(context, true);
			BlockPos board = real.getServer().computeOnServer(PolishClientGameTest::build);
			MlinNotas.LOGGER.info("[PolishClientGameTest] vila em {}", board);

			// galeria das estatuas (7 silhuetas) numa faixa ao sul da vila
			for (int i = 0; i < BOSSES.size(); i++) {
				int cx = -30 + i * 10;
				VillageClientGameTest.shot(context, real, board, new double[] {cx + 5, 10, 111}, new double[] {cx, 7, 100}, P + "statue-" + (i + 1));
			}
			// praca da Cidade da Coroa
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 42, 26}, new double[] {0.5, 0, 0}, P + "plaza-top");
			VillageClientGameTest.shot(context, real, board, new double[] {11, 7, -11}, new double[] {4, 2, -4}, P + "fountain");
			VillageClientGameTest.shot(context, real, board, new double[] {-11, 7, 11}, new double[] {-4, 3, 4}, P + "bandstand");
			VillageClientGameTest.shot(context, real, board, new double[] {-1, 9, 8}, new double[] {-4, 7, -4}, P + "plaza-statue");
			VillageClientGameTest.shot(context, real, board, new double[] {13, 13, 13}, new double[] {4, 9, 4}, P + "clock-tower");
			VillageClientGameTest.shot(context, real, board, new double[] {7, 5, 12}, new double[] {4, 14, 4}, P + "clock-tower-low");

			// Quartel e Guilda
			for (String kind : List.of(CivicBuildings.BARRACKS, CivicBuildings.GUILD)) {
				int max = BuildingTypes.maxLevel(kind);
				for (int level = 1; level <= max; level++) {
					int to = level;
					if (to > 1) {
						real.getServer().runOnServer(server -> upgrade(server, VillageServer.nearest(server.overworld(), board, 4), kind, to));
					}
					if (level == 1 || level == 3 || level == max) {
						int[] view = real.getServer().computeOnServer(server -> lotView(server, board, kind));
						if (view != null) {
							VillageClientGameTest.shot(context, real, board, eye(view, 1.0, 11), look(view, 4), P + kind + "-" + level);
							if (level == max) {
								VillageClientGameTest.shot(context, real, board, eye(view, 0.55, 5), look(view, 3), P + kind + "-" + level + "-close");
								VillageClientGameTest.shot(context, real, board, inside(view), insideLook(view), P + kind + "-" + level + "-inside");
							}
						}
					}
				}
			}
			// construcoes novas com acabamento comum
			for (String kind : List.of("tavern", "tannery", "fletcher", "tailor", "church", "mill", "orchard", "inn", "market")) {
				int[] view = real.getServer().computeOnServer(server -> lotView(server, board, kind));
				if (view != null) {
					VillageClientGameTest.shot(context, real, board, eye(view, 1.0, 9), look(view, 3.5), P + "dressed-" + kind);
				}
			}
			// enquadramentos refeitos: tinas do curtume e campo de tiro da flecharia
			spot(context, real, board, "tannery", 14, 11, 4.5, 11, 5, P + "tannery-vats");
			spot(context, real, board, "fletcher", 14, 11, 4.5, 11, 3, P + "fletcher-range");
			// centro: fachadas, sobrados, arcadas
			double[][] centre = real.getServer().computeOnServer(server -> centreViews(server, board));
			for (int i = 0; i < centre.length; i += 2) {
				VillageClientGameTest.shot(context, real, board, centre[i], centre[i + 1], P + "centre-" + (i / 2 + 1));
			}
			// visao geral (antes em branco)
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 80, 38}, new double[] {0.5, 0, -6}, P + "overview");
			VillageClientGameTest.hideHud(context, false);
		}
	}

	/** Clareira plana, a Cidade da Coroa com as obras publicas feitas, e a galeria das estatuas ao sul. */
	private static BlockPos build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		int x = spawn.getX() + 600;
		int z = spawn.getZ();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = (z - R) >> 4; cz <= (z + R + 40) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R + 40; dz++) {
				for (int y = y0 - 8; y < y0 + 48; y++) {
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
		v.setLevel(VillageLevel.CROWN_CITY);
		for (VillageProject p : List.of(VillageProject.PLAZA, VillageProject.TOWN_HALL, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.MARKET,
				VillageProject.INN, VillageProject.BARRACKS, VillageProject.EXPLORERS_GUILD, VillageProject.TAVERN, VillageProject.TANNERY,
				VillageProject.FLETCHER, VillageProject.TAILOR, VillageProject.CHURCH, VillageProject.MILL, VillageProject.ORCHARD, VillageProject.LAMPS)) {
			v.enqueue(p);
		}
		VillagePlanning.grow(level, v);
		VillageTerrain.prepareOpenRings(level, v, false);
		VillageServer.completeAll(server, v);
		BuildingServer.count(level, v);
		VillageWorksData works = VillageWorksData.get(server);
		works.lastBoss = "astral_herald";
		works.changed();
		int done = VillageWorks.runAll(level, v);
		MlinNotas.LOGGER.info("[PolishClientGameTest] {} lotes prontos, parados {}, obras publicas terminadas {}", v.layout().lots().stream()
				.filter(VillageLayout.Lot::built).count(), VillageBuildings.idle(v), done);
		VillageServer.sync(server, v);
		// galeria: uma estatua por silhueta, de frente para o sul (+z)
		Mats mats = Mats.of(VillageStyle.PLAINS);
		for (int i = 0; i < BOSSES.size(); i++) {
			int cx = x - 30 + i * 10;
			int cz = z + 100;
			Blueprint bp = new Blueprint();
			PlazaSculpture.statue(mats, bp, cx - x, cz - z, y0 - 1, BOSSES.get(i), 1);
			for (WBlock b : bp.ordered()) {
				level.setBlock(new BlockPos(x + b.x(), b.y(), z + b.z()), VanillaRemakePiece.parse(b.state()), 3);
			}
		}
		return board;
	}

	private static VillageLayout.Lot lot(VillageState v, String kind) {
		return v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst().orElse(null);
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
		}
		VillageServer.sync(server, v);
	}

	private static int[] lotView(MinecraftServer server, BlockPos board, String kind) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		if (lot == null) {
			MlinNotas.LOGGER.warn("[PolishClientGameTest] sem {} na vila", kind);
			return null;
		}
		Facing front = Facing.SOUTH.rotate(lot.rot());
		return new int[] {lot.x() + lot.w() / 2, lot.z() + lot.d() / 2, front.dx, front.dz, Math.max(lot.w(), lot.d())};
	}

	private static double[] eye(int[] v, double scale, double h) {
		double dist = v[4] * scale + 6;
		return new double[] {v[0] + v[2] * dist - v[3] * dist * 0.4, h, v[1] + v[3] * dist + v[2] * dist * 0.4};
	}

	private static double[] look(int[] v, double h) {
		return new double[] {v[0], h, v[1]};
	}

	private static double[] inside(int[] v) {
		return new double[] {v[0] + v[2] * 0.5, 2.4, v[1] + v[3] * 0.5};
	}

	private static double[] insideLook(int[] v) {
		return new double[] {v[0] - v[2] * 8, 2.0, v[1] - v[3] * 8};
	}

	/** Foto de um ponto local (x, z da planta) para outro, a {@code eyeY} de altura. */
	private static void spot(ClientGameTestContext context, TestSingleplayerContext real, BlockPos board, String kind, int ex, int ez, double eyeY, int tx, int tz,
			String name) {
		double[][] pts = real.getServer().computeOnServer(server -> {
			VillageState v = VillageServer.nearest(server.overworld(), board, 4);
			VillageLayout.Lot lot = lot(v, kind);
			if (lot == null) {
				return null;
			}
			Canvas canvas = BuildingPlans.plan(lot.kind(), v.style(), lot.seed(), v.buildingsState().level(lot.id()));
			int[] a = VillagePlanner.rotate(ex, ez, canvas.w(), canvas.d(), lot.rot());
			int[] b = VillagePlanner.rotate(tx, tz, canvas.w(), canvas.d(), lot.rot());
			return new double[][] {{lot.x() + a[0], eyeY, lot.z() + a[1]}, {lot.x() + b[0], 1.5, lot.z() + b[1]}};
		});
		if (pts != null) {
			VillageClientGameTest.shot(context, real, board, pts[0], pts[1], name);
		}
	}

	/** Vistas do centro: {olho, alvo} de uma casa com fachada, um comercio com arcada e um sobrado de lote vago. */
	private static double[][] centreViews(MinecraftServer server, BlockPos board) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		MasterPlan plan = v.layout().master();
		List<double[]> out = new java.util.ArrayList<>();
		for (VillageLayout.Lot lot : v.layout().lots()) {
			if (!lot.built() || lot.parcel() < 0 || plan.parcel(lot.parcel()).zone() != Zone.CENTER) {
				continue;
			}
			if (!List.of("house", "market", "inn", "tavern").contains(lot.kind())) {
				continue;
			}
			Facing front = Facing.SOUTH.rotate(lot.rot());
			int cx = lot.x() + lot.w() / 2;
			int cz = lot.z() + lot.d() / 2;
			double dist = Math.max(lot.w(), lot.d()) * 0.9 + 5;
			out.add(new double[] {cx + front.dx * dist - front.dz * dist * 0.4, 8, cz + front.dz * dist + front.dx * dist * 0.4});
			out.add(new double[] {cx + front.dx * 3, 2.5, cz + front.dz * 3});
			if (out.size() >= 8) {
				break;
			}
		}
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.zone() != Zone.CENTER || p.retired() || p.player() || v.layout().lots().stream().anyMatch(l -> l.parcel() == p.id())) {
				continue;
			}
			int cx = (p.rect().x0() + p.rect().x1()) / 2;
			int cz = (p.rect().z0() + p.rect().z1()) / 2;
			double dist = 16;
			out.add(new double[] {cx + p.front().dx * dist - p.front().dz * 6, 8, cz + p.front().dz * dist + p.front().dx * 6});
			out.add(new double[] {cx, 4, cz});
			if (out.size() >= 12) {
				break;
			}
		}
		return out.toArray(new double[0][]);
	}
}
