package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.village.VillageBoardScreen;
import com.mlin.notas.client.village.WorksScreen;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.works.VillageWorks;
import com.mlin.notas.village.works.VillageWorksData;
import com.mlin.notas.waystone.WaystoneData;
import com.mlin.notas.waystone.WaystoneInfo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/**
 * Fotos das obras publicas da vila (fase 3): a vila inicial de um mundo normal nasce no acampamento e sobe nivel por nivel
 * (Povoado, Vila, Cidade, Capital, Cidade da Coroa), cada um com as suas obras feitas: ruas de cada material, praca
 * (poco, bancos, chafariz, coreto, estatua, monumento), palicada e portoes, muralha com ameias, arvores, jardins, hortas,
 * estacas e placas dos lotes livres, estrada ate uma pedra de teleporte; depois uma vila num rio com a ponte e a tela de
 * obras do quadro.
 */
@SuppressWarnings("UnstableApiUsage")
public class VillageWorksClientGameTest implements FabricClientGameTest {
	private static final String P = "mlinnotas-works-";

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
			// uma pedra de teleporte a leste para a estrada externa
			real.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.getChunk((c.getX() + 160) >> 4, (c.getZ() + 12) >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + 160, c.getZ() + 12);
				WaystoneData.get(server).registry().activate(new WaystoneInfo(VillageServer.dimension(level), c.getX() + 160, y, c.getZ() + 12, "Pedra Leste"));
			});
			stage(context, real, c, VillageLevel.HAMLET, 4, "hamlet");
			stage(context, real, c, VillageLevel.VILLAGE, 4, "village");
			stage(context, real, c, VillageLevel.TOWN, 5, "town");
			stage(context, real, c, VillageLevel.CAPITAL, 6, "capital");
			real.getServer().runOnServer(server -> {
				VillageWorksData data = VillageWorksData.get(server);
				data.lastBoss = "cobalt_golem";
				data.changed();
			});
			stage(context, real, c, VillageLevel.CROWN_CITY, 6, "crown");
			worksScreen(context, real, c);
			river(context, real);
			VillageClientGameTest.hideHud(context, false);
		}
	}

	/** Sobe a vila para {@code level}, aplaina os aneis, faz toda a fila de obras publicas e fotografa. */
	private void stage(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c, VillageLevel level, int rings, String name) {
		String summary = real.getServer().computeOnServer(server -> {
			ServerLevel world = server.overworld();
			VillageState village = VillageClientGameTest.starter(server);
			village.setLevel(level);
			for (int ring = 1; ring <= rings; ring++) {
				if (!village.ring(ring).done()) {
					VillageTerrain.prepareNow(world, village, ring, true);
				}
			}
			VillagePlanning.grow(world, village);
			if (level == VillageLevel.TOWN) {
				// a muralha de pedra (paga) toma o lugar da palicada
				village.enqueue(VillageProject.WALL);
				VillageServer.completeAll(server, village);
			}
			int done = VillageWorks.runAll(world, village);
			VillageWorksData.State st = VillageWorksData.get(server).state(village.key());
			StringBuilder sb = new StringBuilder("obras terminadas agora: " + done + " | ");
			for (VillageWorksData.Entry e : st.entries) {
				sb.append(e.id).append('=').append(e.state).append('/').append(e.total).append(' ');
			}
			return sb.toString();
		});
		MlinNotas.LOGGER.info("[VillageWorksClientGameTest] {}: {}", name, summary);
		context.runOnClient(mc -> com.mlin.notas.client.village.VillageOverlay.clear());
		VillageClientGameTest.spectator(real, true);
		VillageClientGameTest.shot(context, real, c, new double[] {0.5, 62, 3}, new double[] {0.5, 0, 0}, P + name + "-top");
		VillageClientGameTest.shot(context, real, c, new double[] {-46, 40, 46}, new double[] {0, 0, 0}, P + name + "-angle");
		VillageClientGameTest.shot(context, real, c, new double[] {9, 7, 9}, new double[] {-2, 1, -2}, P + name + "-plaza");
		VillageClientGameTest.shot(context, real, c, new double[] {-8, 8, 8}, new double[] {3, 2, -3}, P + name + "-plaza2");
		VillageClientGameTest.shot(context, real, c, new double[] {0.5, 6, 12}, new double[] {0.5, 2, 44}, P + name + "-avenue");
		VillageClientGameTest.shot(context, real, c, new double[] {0.5, 7, 36}, new double[] {0.5, 4, 52}, P + name + "-gate");
		VillageClientGameTest.shot(context, real, c, new double[] {-18, 9, 40}, new double[] {0.5, 4, 50}, P + name + "-gate-front");
		VillageClientGameTest.shot(context, real, c, new double[] {-44, 11, 36}, new double[] {-20, 3, 50}, P + name + "-wall");
		VillageClientGameTest.shot(context, real, c, new double[] {40, 18, -38}, new double[] {40, 0, -52}, P + name + "-outskirts");
	}

	/** Tela de obras do quadro: a fila com andamento e os botoes por categoria. */
	private void worksScreen(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c) {
		VillageClientGameTest.spectator(real, false);
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d %d %d 180 10", c.getX(), c.getY(), c.getZ() + 3));
		context.waitTicks(10);
		real.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			VillageServer.use(player, server.overworld(), c);
		});
		context.waitForScreen(VillageBoardScreen.class);
		context.waitTicks(5);
		VillageClientGameTest.hideHud(context, false);
		context.runOnClient(mc -> ((VillageBoardScreen) mc.gui.screen()).setTab(VillageBoardScreen.Tab.BUILDINGS));
		context.waitTicks(5);
		context.takeScreenshot(P + "board-buildings");
		context.runOnClient(mc -> mc.gui.setScreen(new WorksScreen(c, mc.gui.screen())));
		context.waitTicks(50);
		context.takeScreenshot(P + "screen");
		// uma categoria desligada aparece como "desligada"
		context.runOnClient(mc -> com.mlin.notas.client.village.VillageClient.send(c, "works", "toggle:streets"));
		context.waitTicks(40);
		context.takeScreenshot(P + "screen-toggled");
		context.runOnClient(mc -> com.mlin.notas.client.village.VillageClient.send(c, "works", "toggle:streets"));
		context.waitTicks(10);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		VillageClientGameTest.hideHud(context, true);
	}

	/** Vila num mundo plano com um rio fundo: a ponte de pedra na avenida. */
	private void river(ClientGameTestContext context, TestSingleplayerContext real) {
		BlockPos c = real.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int x = 30_000;
			for (int cx = (x - 80) >> 4; cx <= (x + 80) >> 4; cx++) {
				for (int cz = -80 >> 4; cz <= 80 >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
				}
			}
			int peak = level.getSeaLevel();
			for (int dx = -80; dx <= 80; dx += 16) {
				for (int dz = -80; dz <= 80; dz += 16) {
					level.getChunk((x + dx) >> 4, dz >> 4);
					peak = Math.max(peak, level.getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, dz));
				}
			}
			int y0 = peak + 6;
			for (int dx = -80; dx <= 80; dx++) {
				for (int dz = -80; dz <= 80; dz++) {
					boolean river = dz >= -40 && dz <= -34;
					for (int y = y0 - 16; y < y0 + 48; y++) {
						BlockState state;
						if (river && y >= y0 - 6 && y < y0) {
							state = Blocks.WATER.defaultBlockState();
						} else if (river && y < y0 - 6) {
							state = Blocks.DIRT.defaultBlockState();
						} else {
							state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
						}
						level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
					}
				}
			}
			BlockPos board = new BlockPos(x, y0 - 1, 0);
			VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
			VillageState village = VillageServer.create(level, board, false);
			VillagePlanning.ensureLayout(level, village);
			village.setLevel(VillageLevel.TOWN);
			for (int ring = 1; ring <= 5; ring++) {
				VillageTerrain.prepareNow(level, village, ring, true);
			}
			VillagePlanning.grow(level, village);
			VillageWorks.runAll(level, village);
			return board;
		});
		VillageClientGameTest.spectator(real, true);
		VillageClientGameTest.shot(context, real, c, new double[] {18, 16, -22}, new double[] {0, 0, -37}, P + "bridge-side");
		VillageClientGameTest.shot(context, real, c, new double[] {2, 5, -28}, new double[] {0, 1, -48}, P + "bridge-deck");
		VillageClientGameTest.shot(context, real, c, new double[] {0.5, 56, -22}, new double[] {0.5, 0, -22}, P + "bridge-top");
		real.getServer().runOnServer(server -> {
			VillageState v = VillageServer.nearest(server.overworld(), c, 4);
			if (v != null) {
				VillageData.get(server).remove(v.key());
			}
		});
	}
}
