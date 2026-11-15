package com.mlin.notas.gametest;

import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.PlotPlanner;
import com.mlin.notas.village.PlotServer;
import com.mlin.notas.village.StartingVillage;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageFieldServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.WorldTerrain;
import com.mlin.notas.village.works.VillageWorks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Terrenos do jogador (Vilas 3.0 fase 5, lote {@code mlinnotas-gametest:v3_plots}): a vila inicial nova tem 2 terrenos
 * planos com estacas e placa, cama e porta dentro contam como casa, as obras publicas nunca poem bloco dentro, e
 * {@code plot add} em volta de uma casa numa vila que ja existe nivela o entorno sem mexer na casa.
 */
public class PlotsGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 9_000_000;

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new IllegalStateException(message);
		}
	}

	private static Map<BlockPos, BlockState> snapshot(ServerLevel level, VillageState v, MasterPlan.Parcel p, int y0, int y1) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		var r = p.rect();
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				for (int y = y0; y <= y1; y++) {
					BlockPos pos = new BlockPos(v.x() + x, y, v.z() + z);
					out.put(pos, level.getBlockState(pos));
				}
			}
		}
		return out;
	}

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:v3_plots")
	public void newStartingVillageHasTwoPlotsAndTheVillageStaysOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		LevelData.RespawnData spawn = level.getRespawnData();
		HeightField previous = VillageFieldServer.active();
		VillageData data = VillageData.get(server);
		VillageState village = StartingVillage.create(level, new BlockPos(RUN, 0, -RUN / 5));
		try {
			MasterPlan plan = village.layout().master();
			List<MasterPlan.Parcel> plots = plan.plots();
			check(plots.size() == 2, "dois terrenos: " + plots.size());
			for (MasterPlan.Parcel p : plots) {
				int floor = plan.plotFloor(p);
				var r = p.rect();
				for (int x = r.x0(); x <= r.x1(); x++) {
					for (int z = r.z0(); z <= r.z1(); z++) {
						int top = WorldTerrain.scan(level, village.x() + x, village.z() + z, false)[0];
						check(top == floor || top == floor + 1, "terreno plano em " + x + "," + z + ": " + top + " / " + floor);
					}
				}
				for (int[] c : p.corners()) {
					BlockPos pos = new BlockPos(village.x() + c[0], floor + 1, village.z() + c[1]);
					check(level.getBlockState(pos).getBlock() instanceof FenceBlock, "estaca em " + pos);
				}
				int[] s = PlotPlanner.signSpot(p);
				BlockPos sign = new BlockPos(village.x() + s[0], floor + 1, village.z() + s[1]);
				check(level.getBlockState(sign).getBlock() instanceof StandingSignBlock, "placa em " + sign);
				check(level.getBlockEntity(sign) instanceof SignBlockEntity e
						&& (e.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).getFirst().getString().contains("plot") || e.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).getFirst().getString().contains("Terreno")),
						"texto da placa");
			}
			// cama e porta dentro de um terreno contam como casa
			check(village.playerHouses() == 0, "sem casa de jogador ainda");
			MasterPlan.Parcel plot = plots.getFirst();
			int floor = plan.plotFloor(plot);
			var r = plot.rect();
			int cx = (r.x0() + r.x1()) / 2;
			int cz = (r.z0() + r.z1()) / 2;
			BlockPos base = new BlockPos(village.x() + cx, floor + 1, village.z() + cz);
			level.setBlock(base, Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT), 3);
			level.setBlock(base.east(), Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD), 3);
			level.setBlock(base.south(3), Blocks.OAK_DOOR.defaultBlockState(), 3);
			level.setBlock(base.south(3).above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
			VillageTerrain.refresh(level, village);
			check(village.playerHouses() >= 1, "casa no terreno conta: " + village.playerHouses());
			// as obras publicas nunca poem bloco dentro do terreno (fora o que o jogador pos)
			Map<BlockPos, BlockState> before = snapshot(level, village, plot, floor - 3, floor + 8);
			VillageTerrain.prepareOpenRings(level, village, true);
			VillageWorks.runAll(level, village);
			Map<BlockPos, BlockState> after = snapshot(level, village, plot, floor - 3, floor + 8);
			check(before.equals(after), "obra mexeu dentro do terreno");
		} finally {
			data.remove(village.key());
			level.setRespawnData(spawn);
			VillageFieldServer.setActive(previous);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 600, environment = "mlinnotas-gametest:v3_plots")
	public void plotAddAroundAHouseOnAMoundLevelsTheSurroundings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		LevelData.RespawnData spawn = level.getRespawnData();
		HeightField previous = VillageFieldServer.active();
		VillageData data = VillageData.get(server);
		VillageState village = StartingVillage.create(level, new BlockPos(RUN, 0, RUN / 7));
		try {
			MasterPlan plan = village.layout().master();
			// casa no anel 3 (ainda sem aplainar), numa elevacao feita a mao
			int[] site = null;
			for (MasterPlan.Block b : plan.blocks()) {
				if (b.ring() != 3 || b.cells() < 200) {
					continue;
				}
				for (int x = b.cx() - 14; x <= b.cx() + 14 && site == null; x++) {
					for (int z = b.cz() - 14; z <= b.cz() + 14 && site == null; z++) {
						boolean ok = true;
						for (int dx = 0; dx < 13 && ok; dx++) {
							for (int dz = 0; dz < 13 && ok; dz++) {
								ok = plan.blockAt(x + dx, z + dz) == b && !plan.insidePlot(x + dx, z + dz) && village.layout().lotAt(x + dx, z + dz) == null;
							}
						}
						if (ok) {
							site = new int[] {x, z};
						}
					}
				}
				if (site != null) {
					break;
				}
			}
			check(site != null, "lugar livre no anel 3");
			int hx = village.x() + site[0];
			int hz = village.z() + site[1];
			for (int cx = (hx - 56) >> 4; cx <= (hx + 70) >> 4; cx++) {
				for (int cz = (hz - 56) >> 4; cz <= (hz + 70) >> 4; cz++) {
					level.getChunk(cx, cz);
				}
			}
			int floor = WorldTerrain.scan(level, hx + 6, hz + 6, true)[0] + 3;
			// montinho de terra de 13x13 e a casa de 7x7 em cima (piso, paredes de pedra, cama)
			for (int dx = 3; dx <= 9; dx++) {
				for (int dz = 3; dz <= 9; dz++) {
					for (int y = floor - 12; y <= floor; y++) {
						BlockPos p = new BlockPos(hx + dx, y, hz + dz);
						if (level.getBlockState(p).isAir() || y == floor) {
							level.setBlock(p, y == floor ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
						}
					}
					for (int y = floor + 1; y <= floor + 8; y++) {
						level.setBlock(new BlockPos(hx + dx, y, hz + dz), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
			for (int dx = 4; dx <= 8; dx++) {
				for (int dz = 4; dz <= 8; dz++) {
					level.setBlock(new BlockPos(hx + dx, floor, hz + dz), Blocks.COBBLESTONE.defaultBlockState(), 2);
					boolean wall = dx == 4 || dx == 8 || dz == 4 || dz == 8;
					for (int y = floor + 1; y <= floor + 4; y++) {
						level.setBlock(new BlockPos(hx + dx, y, hz + dz), wall ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
			level.setBlock(new BlockPos(hx + 5, floor + 1, hz + 5), Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT), 2);
			level.setBlock(new BlockPos(hx + 6, floor + 1, hz + 5), Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD), 2);
			Map<BlockPos, BlockState> house = new HashMap<>();
			for (int dx = 4; dx <= 8; dx++) {
				for (int dz = 4; dz <= 8; dz++) {
					for (int y = floor - 2; y <= floor + 5; y++) {
						BlockPos p = new BlockPos(hx + dx, y, hz + dz);
						house.put(p, level.getBlockState(p));
					}
				}
			}
			// registra o terreno em volta (casa + 3 de folga)
			PlotServer.Outcome out = PlotServer.add(level, village, hx + 3, hz + 3, hx + 9, hz + 9, floor);
			check(out.ok(), "terreno registrado: " + out.problem());
			check(out.plot().terraformPending() && out.plot().plot(), "terreno pendente de nivelar");
			HeightField field = data.field(village.key());
			check(field != null && field.height(hx + 6, hz + 6) == floor, "campo pinado no chao do terreno");
			// o tick nivela em volta aos poucos
			int ticks = 0;
			while (out.plot().terraformPending() && ticks++ < 3000) {
				PlotServer.tick(server, data, ticks);
			}
			check(!out.plot().terraformPending(), "nivelamento terminou");
			for (var e : house.entrySet()) {
				check(level.getBlockState(e.getKey()).equals(e.getValue()), "casa mexida em " + e.getKey());
			}
			// o entorno imediato fica a no maximo 1 do chao do terreno
			int bad = 0; StringBuilder info = new StringBuilder("piso " + floor + " bloco " + plan.ring(site[0], site[1]) + " ring3pronto " + plan.ringPrepared(3) + " linha:"); for (int k = 0; k <= 12; k += 1) { info.append(" " + field.height(hx + k, hz + 6) + field.kind(hx + k, hz + 6).name().charAt(0) + (field.pinnedRel(hx + k - field.centerX(), hz + 6 - field.centerZ()) ? "*" : "")); }
			int total = 0;
			for (int dx = 2; dx <= 10; dx++) {
				for (int dz = 2; dz <= 10; dz++) {
					if (dx >= 3 && dx <= 9 && dz >= 3 && dz <= 9 || (dx == 2 || dx == 10) && (dz == 2 || dz == 10)) {
						continue;
					}
					total++;
					int top = WorldTerrain.scan(level, hx + dx, hz + dz, true)[0];
					if (Math.abs(top - floor) > 1) {
						bad++; if (bad <= 6) { info.append(" [" + dx + "," + dz + ": mundo " + top + " campo " + field.height(hx + dx, hz + dz) + " " + field.kind(hx + dx, hz + dz) + "]"); }
					}
				}
			}
			check(bad == 0, "entorno do terreno fora de 1 do chao: " + bad + "/" + total + info);
			check(walkOut(level, hx + 3, hz, floor), "sem caminho a pe a partir do terreno");
			check(PlotServer.remove(level, village, out.plot().id()), "remove");
			check(plan.plots().size() == 2, "so os dois terrenos da vila nova");
		} finally {
			data.remove(village.key());
			level.setRespawnData(spawn);
			VillageFieldServer.setActive(previous);
		}
		helper.succeed();
	}

	/** Busca em largura pelas colunas do mundo a partir da borda oeste do terreno ate 20 blocos para fora, degrau de 1. */
	private static boolean walkOut(ServerLevel level, int hx, int hz, int floor) {
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		queue.add(new int[] {hx - 1, hz + 6, floor});
		seen.add(BlockPos.asLong(hx - 1, 0, hz + 6));
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			if (hx - c[0] >= 20) {
				return true;
			}
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nx = c[0] + d[0];
				int nz = c[1] + d[1];
				if (nx > hx - 1 || !seen.add(BlockPos.asLong(nx, 0, nz))) {
					continue;
				}
				int top = WorldTerrain.scan(level, nx, nz, true)[0];
				if (Math.abs(top - c[2]) <= 1 && Math.abs(nz - hz - 6) <= 30) {
					queue.add(new int[] {nx, nz, top});
				}
			}
		}
		return false;
	}
}
