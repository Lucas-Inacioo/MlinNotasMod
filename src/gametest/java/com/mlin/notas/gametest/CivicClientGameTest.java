package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.Facing;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.civic.CivicBuildings;
import com.mlin.notas.village.civic.CivicSites;
import com.mlin.notas.village.civic.DefenseServer;
import com.mlin.notas.village.civic.MuseumServer;
import com.mlin.notas.village.civic.MuseumSets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fotos das construcoes civicas e de defesa (Vilas 2.0, fase 7), todas num mundo real: Correio, Escola, Torre de vigia,
 * Quartel (com os guardas no patio), Pedra de teleporte da vila, Farol comunitario, Museu (com doacoes nas molduras e por
 * dentro) e Guilda dos Exploradores (com o quadro por dentro), em cada nivel. Nome: {@code mlinnotas-civic-<tipo>-<nivel>}.
 */
@SuppressWarnings("UnstableApiUsage")
public class CivicClientGameTest implements FabricClientGameTest {
	private static final int R = 84;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-civic")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("gamerule advance_time false");
			VillageClientGameTest.hideHud(context, true);
			BlockPos board = real.getServer().computeOnServer(CivicClientGameTest::build);
			MlinNotas.LOGGER.info("[CivicClientGameTest] vila em {}", board);

			for (String kind : List.of(CivicBuildings.POST_OFFICE, CivicBuildings.SCHOOL, CivicBuildings.WATCHTOWER, CivicBuildings.BARRACKS,
					CivicBuildings.WAYSTONE, CivicBuildings.BEACON, CivicBuildings.MUSEUM, CivicBuildings.GUILD)) {
				int max = BuildingTypes.maxLevel(kind);
				for (int level = 1; level <= max; level++) {
					int to = level;
					if (to > 1) {
						real.getServer().runOnServer(server -> upgrade(server, VillageServer.nearest(server.overworld(), board, 4), kind, to));
					}
					if (kind.equals(CivicBuildings.MUSEUM)) {
						real.getServer().runOnServer(CivicClientGameTest::donate);
					}
					if (kind.equals(CivicBuildings.BARRACKS)) {
						real.getServer().runOnServer(server -> DefenseServer.guards(server, server.overworld(), VillageServer.nearest(server.overworld(), board, 4)));
					}
					int[] view = real.getServer().computeOnServer(server -> lotView(server, board, kind));
					boolean tall = kind.equals(CivicBuildings.WATCHTOWER) || kind.equals(CivicBuildings.BEACON);
					VillageClientGameTest.shot(context, real, board, eye(view, tall), look(view, tall), "mlinnotas-civic-" + kind + "-" + level);
					if (level == max && (kind.equals(CivicBuildings.MUSEUM) || kind.equals(CivicBuildings.GUILD) || kind.equals(CivicBuildings.POST_OFFICE)
							|| kind.equals(CivicBuildings.SCHOOL) || kind.equals(CivicBuildings.BARRACKS))) {
						VillageClientGameTest.shot(context, real, board, inside(view), insideLook(view), "mlinnotas-civic-" + kind + "-" + level + "-inside");
					}
				}
			}
			// Prefeitura III com o sino de verdade no turreto
			real.getServer().runOnServer(server -> upgrade(server, VillageServer.nearest(server.overworld(), board, 4), BuildingTypes.TOWN_HALL, 3));
			int[] hall = real.getServer().computeOnServer(server -> lotView(server, board, BuildingTypes.TOWN_HALL));
			VillageClientGameTest.shot(context, real, board, eye(hall, false), look(hall, false), "mlinnotas-civic-town_hall-3");
			VillageClientGameTest.shot(context, real, board, new double[] {0.5, 90, 70}, new double[] {0.5, 0, 0}, "mlinnotas-civic-overview");
			VillageClientGameTest.hideHud(context, false);
		}
	}

	/** Clareira plana de grama longe do spawn, quadro no meio e a Metropole com casas e tudo pronto. */
	private static BlockPos build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		int x = spawn.getX() + 500;
		int z = spawn.getZ();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = (z - R) >> 4; cz <= (z + R) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
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
		v.setLevel(VillageLevel.METROPOLIS);
		v.enqueue(VillageProject.PLAZA);
		v.enqueue(VillageProject.TOWN_HALL);
		for (int i = 0; i < 6; i++) {
			v.enqueue(VillageProject.HOUSE);
		}
		for (VillageProject p : CivicBuildings.PROJECTS) {
			v.enqueue(p);
		}
		// fila antes do plano: as obras pagas reservam lote antes dos outros oficios (VillageLayout.first)
		VillagePlanning.grow(level, v);
		VillageTerrain.prepareOpenRings(level, v, false);
		VillageServer.completeAll(server, v);
		MlinNotas.LOGGER.info("[CivicClientGameTest] obras: {} lotes prontos, parados {}", v.layout().lots().stream().filter(VillageLayout.Lot::built).count(),
				VillageBuildings.idle(v));
		VillageServer.sync(server, v);
		return board;
	}

	private static VillageLayout.Lot lot(VillageState v, String kind) {
		return v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst()
				.orElseThrow(() -> new IllegalStateException("sem " + kind));
	}

	/** Paga e constroi na hora as melhorias do tipo ate {@code to} (a Prefeitura acompanha se precisar). */
	private static void upgrade(MinecraftServer server, VillageState v, String kind, int to) {
		VillageLayout.Lot lot = lot(v, kind);
		BuildingsState b = v.buildingsState();
		if (!kind.equals(BuildingTypes.TOWN_HALL) && VillageBuildings.townHallLevel(v) < to) {
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

	/** Doa as gemas e os minerios ao Museu (as molduras da parede mostram as doacoes). */
	private static void donate(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		for (VillageState v : com.mlin.notas.village.VillageData.get(server).villages()) {
			for (CivicSites.Site site : CivicSites.working(server, v, CivicBuildings.MUSEUM)) {
				BlockPos pedestal = site.marks("pedestal").get(0);
				for (String set : List.of("gems", "ores", "ingots", "fish", "food")) {
					for (String id : MuseumSets.byId(set).orElseThrow().items()) {
						if (MuseumSets.wanted(id, site.level())) {
							player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MuseumServer.item(id), 1));
							MuseumServer.use(player, level, pedestal);
						}
					}
				}
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
		}
	}

	/** Centro do lote (relativo ao quadro), direcao da frente e tamanho: {cx, cz, fx, fz, tamanho}. */
	private static int[] lotView(MinecraftServer server, BlockPos board, String kind) {
		VillageState v = VillageServer.nearest(server.overworld(), board, 4);
		VillageLayout.Lot lot = lot(v, kind);
		Facing front = Facing.SOUTH.rotate(lot.rot());
		int cx = lot.x() + lot.w() / 2;
		int cz = lot.z() + lot.d() / 2;
		return new int[] {cx, cz, front.dx, front.dz, Math.max(lot.w(), lot.d())};
	}

	private static double[] eye(int[] v, boolean tall) {
		double dist = v[4] * (tall ? 0.95 : 0.85) + (tall ? 5 : 4);
		double h = tall ? 17 : 10;
		return new double[] {v[0] + v[2] * dist - v[3] * dist * 0.4, h, v[1] + v[3] * dist + v[2] * dist * 0.4};
	}

	private static double[] look(int[] v, boolean tall) {
		return new double[] {v[0], tall ? 8 : 3.5, v[1]};
	}

	/** Dentro do predio, perto da porta, olhando para o fundo. */
	private static double[] inside(int[] v) {
		return new double[] {v[0] + v[2] * 0.5, 2.4, v[1] + v[3] * 0.5};
	}

	private static double[] insideLook(int[] v) {
		return new double[] {v[0] - v[2] * 8, 2.0, v[1] - v[3] * 8};
	}
}
