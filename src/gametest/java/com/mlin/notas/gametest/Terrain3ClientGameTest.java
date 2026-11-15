package com.mlin.notas.gametest;

import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.VillageBuilder;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageFieldServer;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.WallRebuild;
import com.mlin.notas.village.WorldTerrain;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Fotos da muralha de pedra e dos portoes no mundo gerado pelo campo de alturas (Vilas 3.0 fase 4), depois de todos os aneis
 * aplainados pela terraplenagem fixa: o campo mostra, por fora e de tres quartos, a muralha acompanhando a encosta sem ficar
 * enterrada (fotos {@code mlinnotas-terrain3-wall-*}) e, de dentro de cada tipo de portao (eixo e diagonal), a passagem livre
 * com o arco bem acima da rua (fotos {@code mlinnotas-terrain3-gate-*}); e a muralha externa com um portao
 * ({@code mlinnotas-terrain3-outer-*}).
 */
@SuppressWarnings("UnstableApiUsage")
public class Terrain3ClientGameTest implements FabricClientGameTest {
	private static final String P = "mlinnotas-terrain3-";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-vila")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			VillageClientGameTest.hideHud(context, true);
			BlockPos c = real.getServer().computeOnServer(server -> VillageServer.center(VillageClientGameTest.starter(server)));
			int[][] gates = real.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				VillageState village = VillageClientGameTest.starter(server);
				village.setLevel(VillageLevel.CAPITAL);
				VillageTerrain.prepareOpenRings(level, village, false);
				for (int ring = 5; ring <= 6; ring++) {
					VillageTerrain.prepareNow(level, village, ring, false);
				}
				for (VillageProject project : new VillageProject[] {VillageProject.WALL, VillageProject.OUTER_WALL}) {
					VillagePlanner.Planned planned = VillagePlanner.plan(project, village, new WorldTerrain(level, VillageServer.center(village), true), 5L);
					if (planned != null) {
						VillageData.Job job = VillageData.Job.create(village.key(), planned);
						VillageBuilder.runJob(level, village, job, true);
						VillageData.get(server).putRecord(village.key(), WallRebuild.recordKey(project), job.buildRecord());
						village.addBuilt(project);
					}
				}
				HeightField field = VillageFieldServer.ensureField(level, village);
				// um portao de eixo (x > 0) e um diagonal da muralha de pedra, e um da externa: {x, z, nivel}
				int[][] out = new int[3][];
				for (HeightField.Gate g : field.gates()) {
					boolean axis = Math.abs(g.cz()) < 6 && g.cx() > 0;
					boolean diagonal = Math.abs(g.cx()) > 20 && Math.abs(g.cz()) > 20 && g.cx() > 0 && g.cz() > 0;
					if (g.wall() == HeightField.Wall.STONE && axis && out[0] == null) {
						out[0] = new int[] {g.cx(), g.cz(), g.level()};
					} else if (g.wall() == HeightField.Wall.STONE && diagonal && out[1] == null) {
						out[1] = new int[] {g.cx(), g.cz(), g.level()};
					} else if (g.wall() == HeightField.Wall.OUTER && axis && out[2] == null) {
						out[2] = new int[] {g.cx(), g.cz(), g.level()};
					}
				}
				return out;
			});
			if (gates[0] != null) {
				gate(context, real, c, gates[0], "axis");
			}
			if (gates[1] != null) {
				gate(context, real, c, gates[1], "diagonal");
			}
			// muralha de pedra de tres quartos, por fora e de dentro, e a vista do alto
			VillageClientGameTest.shot(context, real, c, new double[] {85, 28, -40}, new double[] {50, 0, 10}, P + "wall-outside");
			VillageClientGameTest.shot(context, real, c, new double[] {30, 14, 30}, new double[] {50, 4, 12}, P + "wall-inside");
			VillageClientGameTest.shot(context, real, c, new double[] {0.5, 140, 1}, new double[] {0.5, 0, 0}, P + "wall-top");
			if (gates[2] != null) {
				gate(context, real, c, gates[2], "outer");
			}
			VillageClientGameTest.hideHud(context, false);
		}
	}

	/** Olhando de dentro da vila para a passagem do portao (rua plana, arco com 4+ de ar) e de tres quartos por fora. */
	private static void gate(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c, int[] g, String name) {
		double len = Math.max(1, Math.hypot(g[0], g[1]));
		double ux = g[0] / len;
		double uz = g[1] / len;
		double dy = g[2] - c.getY();
		VillageClientGameTest.shot(context, real, c, new double[] {g[0] - ux * 9, dy + 2.5, g[1] - uz * 9}, new double[] {g[0] + ux * 6, dy + 2, g[1] + uz * 6},
				P + "gate-" + name + "-inside");
		VillageClientGameTest.shot(context, real, c, new double[] {g[0] + ux * 14 - uz * 10, dy + 9, g[1] + uz * 14 + ux * 10}, new double[] {g[0], dy + 3, g[1]},
				P + "gate-" + name + "-outside");
	}
}
