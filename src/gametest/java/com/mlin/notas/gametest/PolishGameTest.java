package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.RumorsData;
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
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Game tests da rodada de acabamento das Vilas 2.0 (lote {@code mlinnotas-gametest:polish}). */
public class PolishGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 7_000_000;
	private static final int R = 80;

	@GameTest(maxTicks = 40, environment = "mlinnotas-gametest:polish")
	public void rumorsToldPersistAndOldFormatLoadsEmpty(GameTestHelper helper) {
		var old = RumorsData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		if (old.size() != 0) {
			throw new IllegalStateException("formato antigo (sem campo) deveria vir vazio");
		}
		RumorsData data = new RumorsData();
		data.tell(42L);
		data.tell(42L);
		data.tell(7L);
		JsonObject json = RumorsData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
		RumorsData back = RumorsData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		if (back.size() != 2 || !back.isTold(42L) || !back.isTold(7L) || back.isTold(1L)) {
			throw new IllegalStateException("boatos contados nao sobreviveram ao salvar");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 600, environment = "mlinnotas-gametest:polish")
	public void centreFacadesAreBuiltAndPlayerBlocksSurvive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = RUN;
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		level.getChunk(x >> 4, 0);
		int y0 = Math.max(level.getSeaLevel() + 6, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, 0)).getY()) + 4;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 6; y < y0 + 40; y++) {
					BlockState state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		BlockPos board = new BlockPos(x, y0, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState village = VillageServer.create(level, board, false);
		VillagePlanning.ensureLayout(level, village);
		village.setLevel(VillageLevel.METROPOLIS);
		for (VillageProject p : new VillageProject[] {VillageProject.PLAZA, VillageProject.TOWN_HALL, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.MARKET}) {
			village.enqueue(p);
		}
		VillagePlanning.grow(level, village);
		VillageTerrain.prepareOpenRings(level, village, false);
		VillageServer.completeAll(level.getServer(), village);
		// um bloco de jogador no meio do centro (vira coluna de jogador: nenhuma obra pode troca-lo)
		BlockPos mine = board.offset(9, 0, 9);
		level.setBlock(mine, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
		VillageWorks.runAll(level, village);
		VillageWorksData.State st = VillageWorksData.get(level.getServer()).state(village.key());
		for (String id : new String[] {"facades:1", "townhouses:1", "arcades:1"}) {
			var e = st.entry(id);
			if (e == null || e.state != 2) {
				throw new IllegalStateException("obra " + id + " nao terminou: " + (e == null ? "sem entrada" : e.state));
			}
		}
		if (!level.getBlockState(mine).is(Blocks.DIAMOND_BLOCK)) {
			throw new IllegalStateException("bloco de jogador foi trocado");
		}
		VillageData.get(level.getServer()).remove(village.key());
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		helper.succeed();
	}
}
