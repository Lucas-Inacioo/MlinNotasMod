package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.OuterWallPlanner;
import com.mlin.notas.village.RuralRing;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.WorldTerrain;
import com.mlin.notas.village.works.CrownWorks;
import com.mlin.notas.village.works.VillageWorks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Fotos da fase 8 das Vilas 2.0 (Metropole e Cidade da Coroa): a vila inicial de um mundo normal sobe ate a Cidade da
 * Coroa com o anel rural aplainado, obras rurais nos campos, muralha de pedra (centro historico), muralha externa com
 * barbacas, cerca viva e postes; fotos de cima, em 3/4, de um portao da muralha externa, do portao aberto do centro
 * historico e dos campos de noite com os postes acesos. Arquivos {@code mlinnotas-crown-*}.
 */
@SuppressWarnings("UnstableApiUsage")
public class CrownClientGameTest implements FabricClientGameTest {
	private static final String P = "mlinnotas-crown-";

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
			String summary = real.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				VillageState v = VillageClientGameTest.starter(server);
				v.setLevel(VillageLevel.CROWN_CITY);
				for (VillageProject p : List.of(VillageProject.WALL, VillageProject.OUTER_WALL, VillageProject.HOUSE, VillageProject.HOUSE,
						VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM, VillageProject.FARM, VillageProject.MILL, VillageProject.ORCHARD,
						VillageProject.PEN, VillageProject.WINERY, VillageProject.APIARY, VillageProject.QUARRY, VillageProject.BARRACKS)) {
					v.enqueue(p);
				}
				VillagePlanning.grow(level, v);
				for (int ring = 1; ring <= RuralRing.RING; ring++) {
					if (!v.ring(ring).done()) {
						VillageTerrain.prepareNow(level, v, ring, true);
					}
				}
				VillagePlanning.grow(level, v);
				WorldTerrain wt = new WorldTerrain(level, VillageServer.center(v));
				List<int[]> gaps = OuterWallPlanner.breaches(v.layout().master(), wt);
				int breaches = gaps.size();
				StringBuilder why = new StringBuilder();
				for (int i = 0; i < gaps.size(); i += Math.max(1, gaps.size() / 12)) {
					int[] g = gaps.get(i);
					why.append(g[0]).append(",").append(g[1]).append("=").append(wt.inspect(g[0], g[1])).append(" area=").append(v.layout().master().insidePlayerArea(g[0], g[1])).append("; ");
				}
				MlinNotas.LOGGER.info("[CrownClientGameTest] vaos: {}", why);
				VillageServer.completeAll(server, v);
				int done = VillageWorks.runAll(level, v);
				MasterPlan plan = v.layout().master();
				StringBuilder sb = new StringBuilder("lotes " + v.layout().lots().size() + ", obras publicas " + done + ", vaos da muralha externa "
						+ breaches + ", postes rurais "
						+ CrownWorks.lampSpots(plan).size() + " | ");
				for (var lot : v.layout().lots()) {
					var p = plan.parcel(lot.parcel());
					sb.append(lot.kind()).append(lot.built() ? "" : "(vazio)").append('@').append(p == null ? "?" : p.zone().id()).append(' ');
				}
				return sb.toString();
			});
			MlinNotas.LOGGER.info("[CrownClientGameTest] {}", summary);
			context.runOnClient(mc -> com.mlin.notas.client.village.VillageOverlay.clear());
			// alcance maior: a vila da Coroa tem 225 blocos de lado (cliente e servidor integrado)
			int distance = context.computeOnClient(mc -> mc.options.renderDistance().get());
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(12);
				mc.options.broadcastOptions();
			});
			real.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(12));
			// aquece: carrega e desenha a area toda uma vez de cima
			VillageClientGameTest.spectator(real, true);
			real.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %d %d %d 0 90", c.getX(), c.getY() + 120, c.getZ()));
			context.waitTicks(900);
			shot(context, real, c, new double[] {0.5, 150, 3}, new double[] {0.5, 0, 0}, P + "top");
			shot(context, real, c, new double[] {-125, 75, 125}, new double[] {0, 0, 0}, P + "angle");
			shot(context, real, c, new double[] {130, 60, -60}, new double[] {40, 0, -10}, P + "angle-ne");
			// portao leste da muralha externa visto dos campos (barbaca na frente) e de dentro
			// um portao de eixo da muralha externa (com a barbaca) e um campo com obra rural, achados no plano
			int[] spots = real.getServer().computeOnServer(server -> {
				VillageState v = VillageClientGameTest.starter(server);
				MasterPlan plan = v.layout().master();
				int[] out = {75, 0, 1, 0, 95, 0};
				for (OuterWallPlanner.Gate g : OuterWallPlanner.gates(plan, new WorldTerrain(server.overworld(), VillageServer.center(v)))) {
					if (g.axis()) {
						out[0] = g.center()[0];
						out[1] = g.center()[1];
						out[2] = g.dx();
						out[3] = g.dz();
						break;
					}
				}
				for (var lot : v.layout().lots()) {
					var p = plan.parcel(lot.parcel());
					if (lot.built() && p != null && p.zone() == com.mlin.notas.village.Zone.RURAL && !lot.kind().equals("farm")) {
						out[4] = (lot.rect().x0() + lot.rect().x1()) / 2;
						out[5] = (lot.rect().z0() + lot.rect().z1()) / 2;
						break;
					}
				}
				return out;
			});
			int gx = spots[0];
			int gz = spots[1];
			int ox = spots[2];
			int oz = spots[3];
			shot(context, real, c, new double[] {gx + ox * 22 + oz * 9, 12, gz + oz * 22 + ox * 9}, new double[] {gx, 5, gz}, P + "gate-outside");
			shot(context, real, c, new double[] {gx - ox * 16 - oz * 6, 9, gz - oz * 16 - ox * 6}, new double[] {gx, 5, gz}, P + "gate-inside");
			// centro historico: portao aberto da muralha de pedra e o passeio em cima
			shot(context, real, c, new double[] {34, 7, 10}, new double[] {50, 4, 0}, P + "historic-gate");
			shot(context, real, c, new double[] {28, 16, 30}, new double[] {50, 7, 20}, P + "historic-promenade");
			// campos: de dia e de noite com os postes acesos
			shot(context, real, c, new double[] {spots[4] * 1.25 + 8, 20, spots[5] * 1.25 + 8}, new double[] {spots[4], 0, spots[5]}, P + "fields-day");
			real.getServer().runCommand("time set midnight");
			shot(context, real, c, new double[] {spots[4] * 1.25 + 8, 20, spots[5] * 1.25 + 8}, new double[] {spots[4], 0, spots[5]}, P + "fields-night");
			shot(context, real, c, new double[] {-130, 70, 130}, new double[] {0, 0, 0}, P + "angle-night");
			real.getServer().runCommand("time set noon");
			VillageClientGameTest.hideHud(context, false);
			context.runOnClient(mc -> mc.options.renderDistance().set(distance));
		}
	}

	/** Como {@link VillageClientGameTest#shot}, com mais tempo para os chunks de longe (distancia de render 12). */
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
		real.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", ex, ey, ez, yaw, pitch));
		context.waitTicks(100);
		// sem waitForChunksRender: com alcance 11 ele nunca fecha; tempo fixo para os chunks de longe chegarem
		context.waitTicks(260);
		context.waitTicks(40);
		context.takeScreenshot(name);
	}
}
