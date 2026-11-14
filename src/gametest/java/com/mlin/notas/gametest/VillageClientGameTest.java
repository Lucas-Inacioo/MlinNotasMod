package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.village.VillageBoardScreen;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;

/**
 * Game test de cliente das vilas: mundo normal novo nasce no acampamento inicial (fotos do acampamento), tela do
 * quadro (projetos, missoes, mercado) e a vila crescida com varios projetos construidos (fotos de cima e da rua).
 */
@SuppressWarnings("UnstableApiUsage")
public class VillageClientGameTest implements FabricClientGameTest {
	private record Info(BlockPos center, String name, String style, int lots, int villagers) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-vila")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			Info camp = info(real);
			MlinNotas.LOGGER.info("[VillageClientGameTest] acampamento {} ({}) em {}: {} lotes, {} aldeoes", camp.name(), camp.style(), camp.center(),
					camp.lots(), camp.villagers());
			if (camp.lots() < 3 || camp.villagers() < 2) {
				throw new IllegalStateException("acampamento incompleto: " + camp);
			}
			double near = context.computeOnClient(mc -> mc.player == null ? 999 : Math.sqrt(mc.player.blockPosition().distSqr(camp.center())));
			if (near > 12) {
				throw new IllegalStateException("o jogador deveria nascer perto do quadro, esta a " + near);
			}
			hideHud(context, true);
			BlockPos c = camp.center();
			shots(context, real, c, "camp", 26, 18);

			Village2Shots.overlayAndGhost(context, real, c);
			Village2Shots.masterPlanShots(context, real, c);
			boardScreens(context, real, c, "camp");

			// a vila cresce: varios projetos construidos de uma vez
			String grown = real.getServer().computeOnServer(server -> {
				VillageState village = starter(server);
				village.setLevel(VillageLevel.CAPITAL);
				village.addProsperity(1200);
				com.mlin.notas.village.VillageTerrain.prepareOpenRings(server.overworld(), village, false);
				for (VillageProject p : List.of(VillageProject.PLAZA, VillageProject.LAMPS, VillageProject.HOUSE, VillageProject.HOUSE,
						VillageProject.HOUSE, VillageProject.FARM, VillageProject.SMITHY, VillageProject.MARKET, VillageProject.HOUSE,
						VillageProject.INN, VillageProject.LIBRARY, VillageProject.HOUSE, VillageProject.FARM, VillageProject.LAMPS,
						VillageProject.HOUSE, VillageProject.WALL)) {
					village.enqueue(p);
				}
				VillageServer.completeAll(server, village);
				StringBuilder sb = new StringBuilder();
				for (VillageProject p : VillageProject.values()) {
					sb.append(p.id()).append('=').append(village.built(p)).append(' ');
				}
				return sb.toString();
			});
			MlinNotas.LOGGER.info("[VillageClientGameTest] vila crescida: {}", grown);
			Info town = info(real);
			if (town.lots() < camp.lots() + 8) {
				throw new IllegalStateException("a vila deveria ter crescido bastante: " + grown);
			}
			shots(context, real, c, "grown", 46, 34);
			// da praca olhando para as quatro ruas, e um portao da muralha visto de fora
			int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
			String[] names = {"south", "east", "north", "west"};
			for (int i = 0; i < 4; i++) {
				shot(context, real, c, new double[] {dirs[i][0] * 3.5 - dirs[i][1] * 1.5, 3.2, dirs[i][1] * 3.5 + dirs[i][0] * 1.5},
						new double[] {dirs[i][0] * 24, 2, dirs[i][1] * 24}, "mlinnotas-village-grown-street-" + names[i]);
			}
			shot(context, real, c, new double[] {0, 9, 62}, new double[] {0, 3, 49}, "mlinnotas-village-grown-gate");
			boardScreens(context, real, c, "grown");
			vanillaVillage(context, real, c);
			Village2Shots.slopeVillage(context, real, c);
			hideHud(context, false);
		}
	}

	/** A vila vanilla mais perto ganhou o quadro de missoes perto do sino da praca. */
	private static void vanillaVillage(ClientGameTestContext context, TestSingleplayerContext real, BlockPos from) {
		BlockPos board = real.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos found = level.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, from.offset(400, 0, 0), 100, false);
			if (found == null) {
				return null;
			}
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = -48; dx <= 48; dx++) {
				for (int dz = -48; dz <= 48; dz++) {
					int top = level.getChunk((found.getX() + dx) >> 4, (found.getZ() + dz) >> 4)
							.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (found.getX() + dx) & 15, (found.getZ() + dz) & 15);
					for (int y = top + 1; y >= top - 6; y--) {
						pos.set(found.getX() + dx, y, found.getZ() + dz);
						if (level.getBlockState(pos).is(ModBlocks.VILLAGE_BOARD)) {
							return pos.immutable();
						}
					}
				}
			}
			throw new IllegalStateException("vila vanilla em " + found + " sem quadro de missoes");
		});
		if (board == null) {
			MlinNotas.LOGGER.warn("[VillageClientGameTest] nenhuma vila vanilla perto para conferir o quadro");
			return;
		}
		MlinNotas.LOGGER.info("[VillageClientGameTest] quadro da vila vanilla em {}", board);
		shot(context, real, board, new double[] {5, 3, 6}, new double[] {0, 1, 0}, "mlinnotas-village-vanilla-board");
	}

	static VillageState starter(net.minecraft.server.MinecraftServer server) {
		for (VillageState village : VillageData.get(server).villages()) {
			if (village.starter()) {
				return village;
			}
		}
		throw new IllegalStateException("mundo novo sem acampamento inicial");
	}

	private static Info info(TestSingleplayerContext real) {
		return real.getServer().computeOnServer(server -> {
			VillageState v = starter(server);
			BlockPos center = VillageServer.center(v);
			ServerLevel level = server.overworld();
			if (!level.getBlockState(center).is(ModBlocks.VILLAGE_BOARD)) {
				throw new IllegalStateException("quadro sumiu de " + center);
			}
			int villagers = level.getEntities(EntityTypes.VILLAGER, new AABB(center).inflate(60), e -> true).size();
			return new Info(center, v.name(), v.style().id(), v.lots().size(), villagers);
		});
	}

	static void hideHud(ClientGameTestContext context, boolean hide) {
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() != hide) {
				mc.gui.hud.toggle();
			}
		});
	}

	/** Quatro fotos: 3/4 de dois lados, de cima e baixinha. */
	private static void shots(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c, String name, double dist, double up) {
		spectator(real, true);
		shot(context, real, c, new double[] {-dist * 0.7, up, dist * 0.7}, new double[] {0, 0, 0}, "mlinnotas-village-" + name + "-sw");
		shot(context, real, c, new double[] {dist * 0.7, up * 0.8, -dist * 0.7}, new double[] {0, 0, 0}, "mlinnotas-village-" + name + "-ne");
		shot(context, real, c, new double[] {0.5, up * 2.2, dist * 0.35}, new double[] {0.5, 0, 0}, "mlinnotas-village-" + name + "-top");
		shot(context, real, c, new double[] {dist * 0.22, 3, dist * 0.28}, new double[] {0, 1.5, 0}, "mlinnotas-village-" + name + "-low");
	}

	static void spectator(TestSingleplayerContext real, boolean on) {
		real.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
				.setGameMode(on ? GameType.SPECTATOR : GameType.CREATIVE));
	}

	private static void boardScreens(ClientGameTestContext context, TestSingleplayerContext real, BlockPos c, String name) {
		spectator(real, false);
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d %d %d 180 10", c.getX(), c.getY(), c.getZ() + 3));
		context.waitTicks(10);
		real.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			VillageServer.use(player, server.overworld(), c);
		});
		context.waitForScreen(VillageBoardScreen.class);
		context.waitTicks(10);
		hideHud(context, false);
		context.takeScreenshot("mlinnotas-village-" + name + "-board-projects");
		context.runOnClient(mc -> ((VillageBoardScreen) mc.gui.screen()).setTab(VillageBoardScreen.Tab.BOUNTIES));
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-village-" + name + "-board-bounties");
		context.runOnClient(mc -> ((VillageBoardScreen) mc.gui.screen()).setTab(VillageBoardScreen.Tab.SHOP));
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-village-" + name + "-board-shop");
		context.runOnClient(mc -> ((VillageBoardScreen) mc.gui.screen()).setTab(VillageBoardScreen.Tab.BUILDINGS));
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-village-" + name + "-board-buildings");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		hideHud(context, true);
	}

	/** Camera em {@code eye} (relativo ao quadro) olhando para {@code look}; espera os chunks e tira a foto. */
	static void shot(ClientGameTestContext context, TestSingleplayerContext real, BlockPos o, double[] eye, double[] look, String name) {
		spectator(real, true);
		double ex = o.getX() + 0.5 + eye[0];
		double ey = o.getY() + eye[1];
		double ez = o.getZ() + 0.5 + eye[2];
		double dx = o.getX() + 0.5 + look[0] - ex;
		double dz = o.getZ() + 0.5 + look[2] - ez;
		double dy = o.getY() + look[1] - (ey + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", ex, ey, ez, yaw, pitch));
		context.waitTicks(40);
		real.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
