package com.mlin.notas.gametest;

import com.mlin.notas.client.village.VillageOverlay;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.PlotServer;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Fotos dos terrenos do jogador (Vilas 3.0 fase 5, {@code mlinnotas-v3-plot-*}): os dois terrenos da vila inicial de cima e
 * de perto (estacas e placa), o contorno com o nome do terreno, e um terreno registrado com {@code plot add} em volta de
 * uma casinha num monte, antes e depois do nivelamento.
 */
@SuppressWarnings("UnstableApiUsage")
public class PlotsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-vila")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			VillageClientGameTest.hideHud(context, true);
			// centro de cada terreno e do quadro
			int[][] centers = real.getServer().computeOnServer(server -> {
				VillageState v = VillageClientGameTest.starter(server);
				MasterPlan plan = v.layout().master();
				return plan.plots().stream().map(p -> new int[] {v.x() + (p.rect().x0() + p.rect().x1()) / 2, plan.plotFloor(p),
						v.z() + (p.rect().z0() + p.rect().z1()) / 2}).toArray(int[][]::new);
			});
			for (int i = 0; i < centers.length; i++) {
				BlockPos c = new BlockPos(centers[i][0], centers[i][1], centers[i][2]);
				VillageClientGameTest.shot(context, real, c, new double[] {0, 48, 6}, new double[] {0, 0, 0}, "mlinnotas-v3-plot-top-" + i);
				VillageClientGameTest.shot(context, real, c, new double[] {-14, 7, 14}, new double[] {0, 0, 0}, "mlinnotas-v3-plot-angle-" + i);
			}
			// contorno com o nome do terreno
			BlockPos c0 = new BlockPos(centers[0][0], centers[0][1], centers[0][2]);
			real.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				VillagePlanning.show(player, VillageClientGameTest.starter(server), 600);
			});
			context.waitFor(mc -> VillageOverlay.active(), 100);
			VillageClientGameTest.shot(context, real, c0, new double[] {-20, 12, 20}, new double[] {0, 0, 0}, "mlinnotas-v3-plot-overlay");

			// terreno registrado em volta de uma casinha num monte (anel 3)
			int[] spot = real.getServer().computeOnServer(server -> {
				VillageState v = VillageClientGameTest.starter(server);
				MasterPlan plan = v.layout().master();
				for (MasterPlan.Block b : plan.blocks()) {
					if (b.ring() != 3 || b.cells() < 200) {
						continue;
					}
					for (int x = b.cx() - 14; x <= b.cx() + 14; x++) {
						for (int z = b.cz() - 14; z <= b.cz() + 14; z++) {
							boolean ok = true;
							for (int dx = 0; dx < 13 && ok; dx++) {
								for (int dz = 0; dz < 13 && ok; dz++) {
									ok = plan.blockAt(x + dx, z + dz) == b && !plan.insidePlot(x + dx, z + dz) && v.layout().lotAt(x + dx, z + dz) == null;
								}
							}
							if (ok) {
								return new int[] {v.x() + x, v.z() + z};
							}
						}
					}
				}
				throw new IllegalStateException("sem lugar livre no anel 3");
			});
			int hx = spot[0];
			int hz = spot[1];
			int floor = real.getServer().computeOnServer(server -> {
				var level = server.overworld();
				level.getChunk(hx >> 4, hz >> 4);
				int f = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hx + 6, hz + 6) + 2;
				for (int dx = 3; dx <= 9; dx++) {
					for (int dz = 3; dz <= 9; dz++) {
						for (int y = f - 10; y <= f; y++) {
							level.setBlock(new BlockPos(hx + dx, y, hz + dz), y == f ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
						}
						boolean wall = dx == 4 || dx == 8 || dz == 4 || dz == 8;
						if (dx >= 4 && dx <= 8 && dz >= 4 && dz <= 8) {
							for (int y = f + 1; y <= f + 4; y++) {
								level.setBlock(new BlockPos(hx + dx, y, hz + dz), wall ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
							}
						}
					}
				}
				return f;
			});
			BlockPos house = new BlockPos(hx + 6, floor, hz + 6);
			VillageClientGameTest.shot(context, real, house, new double[] {-18, 9, 18}, new double[] {0, 1, 0}, "mlinnotas-v3-plot-add-before");
			real.getServer().runOnServer(server -> {
				var level = server.overworld();
				VillageState v = VillageClientGameTest.starter(server);
				PlotServer.Outcome out = PlotServer.add(level, v, hx + 3, hz + 3, hx + 9, hz + 9, floor);
				if (!out.ok()) {
					throw new IllegalStateException("terreno nao registrado: " + out.problem());
				}
				PlotServer.terraformNow(level, v, out.plot());
			});
			context.waitTicks(10);
			VillageClientGameTest.shot(context, real, house, new double[] {-18, 9, 18}, new double[] {0, 1, 0}, "mlinnotas-v3-plot-add-after");
			VillageClientGameTest.shot(context, real, house, new double[] {0, 40, 4}, new double[] {0, 0, 0}, "mlinnotas-v3-plot-add-top");
			VillageClientGameTest.spectator(real, false);
		}
	}

}
