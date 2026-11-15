package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.RingWork;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.WorldTerrain;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.IntBinaryOperator;

/**
 * Game tests do plano diretor e da terraplenagem (lote {@code mlinnotas-gametest:village3}): encosta vira escada de
 * platos com muro de arrimo e nada solto, agua rasa aterrada, floresta cortada e mudas nos arredores, terreno ja
 * acertado a mao baixa o preco, construcao de jogador nunca e mexida (e casa de jogador com cama e porta conta), o
 * nivel so sobe com o anel pronto e a obra para quando o chao do lote e cavado, voltando quando ele e acertado.
 */
public class Village3GameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 3_000_000;
	private static final int R = 80;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Terreno de grama sobre terra com a altura {@code height} (relativa) em volta de (x, 0) e o quadro no meio. */
	private static VillageState site(GameTestHelper helper, int x, IntBinaryOperator height) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 6, ground(level, x, 0).getY()) + 4;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				int h = y0 + height.applyAsInt(dx, dz);
				for (int y = y0 - 16; y < y0 + 28; y++) {
					BlockState state = y < h - 1 ? Blocks.DIRT.defaultBlockState() : y == h - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		return null;
	}

	private static VillageState found(GameTestHelper helper, int x) {
		ServerLevel level = helper.getLevel();
		BlockPos board = ground(level, x, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState village = VillageServer.create(level, board, false);
		VillagePlanning.ensureLayout(level, village);
		return village;
	}

	private static void release(GameTestHelper helper, VillageState village) {
		ServerLevel level = helper.getLevel();
		for (int cx = (village.x() - R) >> 4; cx <= (village.x() + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		VillageData.get(level.getServer()).remove(village.key());
	}

	private static BlockPos at(VillageState v, int x, int y, int z) {
		return new BlockPos(v.x() + x, y, v.z() + z);
	}

	/** Todo o anel no plano: chao na altura, sem agua, nada solto no ar, rua com caminho. */
	private static String checkRing(ServerLevel level, VillageState village, int ring) {
		MasterPlan plan = village.layout().master();
		int columns = 0;
		for (long col : plan.columns(ring)) {
			int x = VillageLayout.colX(col);
			int z = VillageLayout.colZ(col);
			int target = plan.plannedHeight(x, z);
			int[] s = WorldTerrain.scan(level, village.x() + x, village.z() + z, false);
			if (s[2] > 0) {
				return "agua sobrou em " + x + "," + z + " (topo " + s[0] + ", agua " + s[2] + ", plano " + target + ", codigo " + plan.code(x, z) + ", " + level.getBlockState(at(village, x, s[0], z)) + ")";
			}
			boolean stake = s[0] == target + 1 && level.getBlockState(at(village, x, target + 1, z)).is(BlockTags.FENCES);
			if (s[0] != target && !stake) {
				return "coluna " + x + "," + z + " no y " + s[0] + ", plano " + target;
			}
			BlockPos top = at(village, x, target, z);
			if (level.getBlockState(top.below()).isAir()) {
				return "chao solto no ar em " + top;
			}
			if (plan.ground(x, z) && !level.getBlockState(top).is(Blocks.DIRT_PATH)) {
				return "rua sem caminho em " + top + ": " + level.getBlockState(top);
			}
			columns++;
		}
		return columns < 200 ? "poucas colunas no anel " + ring + ": " + columns : null;
	}

	@GameTest(maxTicks = 2400, environment = "mlinnotas-gametest:village3")
	public void slopeBecomesTerracedPlateausWithRetainingWalls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 70_000 + RUN;
		site(helper, x, (dx, dz) -> Math.floorDiv(dx, 3) + Math.floorDiv(dz, 5));
		VillageState village = found(helper, x);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.EMERALD, 64));
		for (int i = 0; i < 8; i++) {
			player.getInventory().add(new ItemStack(Items.DIRT, 64));
		}
		// anel 1 pago com entregas, aplainado aos poucos pelo tick
		if (!VillageTerrain.deposit(player, village, 1) || village.ring(1).state() != RingWork.State.QUEUED) {
			throw new IllegalStateException("entrega do anel 1 deveria pagar a terraplenagem: " + village.ring(1).state() + " " + village.ring(1).costs());
		}
		if (village.ring(1).emeralds() < 1) {
			throw new IllegalStateException("anel 1 numa encosta deveria custar esmeraldas");
		}
		boolean[] saw = {false};
		helper.succeedWhen(() -> {
			VillageData.TerrainJob job = VillageData.get(level.getServer()).terrainJob(village.key());
			if (job != null && job.index > 0) {
				saw[0] = true;
			}
			if (!village.ring(1).done()) {
				throw helper.assertionException("anel 1 ainda aplainando (" + (job == null ? "sem obra" : job.index) + ")");
			}
			if (!saw[0]) {
				throw helper.assertionException("a terraplenagem deveria ir aos poucos");
			}
			String problem = checkRing(level, village, 1);
			if (problem != null) {
				throw helper.assertionException(problem);
			}
			// anel 2 na hora: platos em degraus de no maximo 4 e muro de arrimo nas bordas altas
			VillageTerrain.prepareNow(level, village, 2, false);
			problem = checkRing(level, village, 2);
			if (problem != null) {
				throw helper.assertionException(problem);
			}
			MasterPlan plan = village.layout().master();
			int walls = 0;
			for (long col : plan.columns(2)) {
				int cx = VillageLayout.colX(col);
				int cz = VillageLayout.colZ(col);
				BlockState below = level.getBlockState(at(village, cx, plan.plannedHeight(cx, cz) - 1, cz));
				com.mlin.notas.village.VillagePalette palette = com.mlin.notas.village.VillagePalette.of(village.style());
				if (below.is(palette.brick) || below.is(palette.foundation)) {
					walls++;
				}
			}
			if (walls < 10) {
				throw helper.assertionException("encosta aplainada sem muro de arrimo (" + walls + ")");
			}
			int stakes = 0;
			for (MasterPlan.Parcel p : plan.parcels()) {
				if (plan.block(p.block()).ring() == 2) {
					int[] c = p.corners().getFirst();
					stakes += level.getBlockState(at(village, c[0], plan.block(p.block()).target() + 1, c[1])).is(BlockTags.FENCES) ? 1 : 0;
				}
			}
			if (stakes == 0) {
				throw helper.assertionException("lotes do anel sem estacas nos cantos");
			}
			release(helper, village);
		});
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village3")
	public void shallowWaterIsFilledAndForestCleared(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 71_000 + RUN;
		site(helper, x, (dx, dz) -> 0);
		BlockPos c = ground(level, x, 0);
		int y = c.getY() - 1;
		// poca de 2 de fundo no anel 2 (leste) e arvores no anel 2 (oeste)
		for (int dx = 18; dx <= 24; dx++) {
			for (int dz = 8; dz <= 12; dz++) {
				level.setBlock(new BlockPos(x + dx, y, dz), Blocks.WATER.defaultBlockState(), 2);
				level.setBlock(new BlockPos(x + dx, y - 1, dz), Blocks.WATER.defaultBlockState(), 2);
			}
		}
		for (int t = 0; t < 4; t++) {
			int tx = x - 24 + t * 2;
			int tz = -14 + t * 2;
			for (int h = 1; h <= 4; h++) {
				level.setBlock(new BlockPos(tx, y + h, tz), Blocks.OAK_LOG.defaultBlockState(), 2);
			}
			for (int lx = -1; lx <= 1; lx++) {
				for (int lz = -1; lz <= 1; lz++) {
					level.setBlock(new BlockPos(tx + lx, y + 5, tz + lz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), 2);
				}
			}
		}
		VillageState village = found(helper, x);
		VillageTerrain.prepareNow(level, village, 2, false);
		String problem = checkRing(level, village, 2);
		if (problem != null) {
			throw new IllegalStateException(problem);
		}
		for (int t = 0; t < 4; t++) {
			BlockPos log = new BlockPos(x - 24 + t * 2, y + 2, -14 + t * 2);
			MasterPlan m = village.layout().master();
			if (level.getBlockState(log).is(BlockTags.LOGS) && m.ring(log.getX() - x, log.getZ()) == 2 && m.code(log.getX() - x, log.getZ()) != MasterPlan.CARVED) {
				throw new IllegalStateException("arvore nao foi cortada em " + log);
			}
		}
		// arredores: mudas replantadas
		VillageTerrain.prepareNow(level, village, 5, false);
		int saplings = 0;
		MasterPlan plan = village.layout().master();
		for (long col : plan.columns(5)) {
			int cx = VillageLayout.colX(col);
			int cz = VillageLayout.colZ(col);
			if (level.getBlockState(at(village, cx, plan.plannedHeight(cx, cz) + 1, cz)).is(BlockTags.SAPLINGS)) {
				saplings++;
			}
		}
		if (saplings < 3 && village.style() != com.mlin.notas.village.VillageStyle.DESERT) {
			String sample = "";
			for (long col : plan.columns(5)) {
				int cx = VillageLayout.colX(col);
				int cz = VillageLayout.colZ(col);
				if (com.mlin.notas.village.TerrainWork.sapling(plan, cx, cz)) {
					BlockPos p = at(village, cx, plan.plannedHeight(cx, cz) + 1, cz);
					sample = cx + "," + cz + " y" + p.getY() + " " + level.getBlockState(p) + " abaixo " + level.getBlockState(p.below()) + " estilo "
							+ village.style();
					break;
				}
			}
			throw new IllegalStateException("arredores sem mudas replantadas: " + saplings + " (" + sample + ")");
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village3")
	public void workDoneByHandKeepsTheFixedPriceAndPlayerBlocksStay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 72_000 + RUN;
		site(helper, x, (dx, dz) -> Math.floorDiv(dx, 4));
		BlockPos c = ground(level, x, 0);
		// casa de pedra do jogador no anel 3 (com cama e porta) e um poste solto num lote do anel 2
		int hx = x + 36;
		int hz = 24;
		int hy = ground(level, hx, hz).getY();
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				for (int dy = 0; dy < 3; dy++) {
					boolean wall = dx == 0 || dz == 0 || dx == 4 || dz == 4;
					level.setBlock(new BlockPos(hx + dx, hy + dy, hz + dz), wall ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		BlockPos doorPos = new BlockPos(hx + 2, hy, hz);
		level.setBlock(doorPos, Blocks.OAK_DOOR.defaultBlockState(), 2);
		level.setBlock(doorPos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
		BlockPos bed = new BlockPos(hx + 2, hy, hz + 2);
		level.setBlock(bed, Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH), 2);
		level.setBlock(bed.south(), Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART,
				BedPart.HEAD), 2);
		VillageState village = found(helper, x);
		MasterPlan plan = village.layout().master();
		if (plan.playerAreas().isEmpty()) {
			throw new IllegalStateException("a casa do jogador deveria virar area de jogador");
		}
		// preco fixo do anel 2: acertar metade a mao nao muda a tabela
		VillageTerrain.refresh(level, village);
		int full = village.ring(2).emeralds();
		java.util.List<Long> cols = plan.columns(2);
		for (int i = 0; i < cols.size() / 2; i++) {
			int cx = VillageLayout.colX(cols.get(i));
			int cz = VillageLayout.colZ(cols.get(i));
			int target = plan.plannedHeight(cx, cz);
			int[] s = WorldTerrain.scan(level, x + cx, cz, false);
			for (int yy = Math.min(s[0], target) + 1; yy <= Math.max(s[0], target); yy++) {
				level.setBlock(at(village, cx, yy, cz), yy <= target ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
			}
			level.setBlock(at(village, cx, target, cz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		}
		VillageTerrain.refresh(level, village);
		int half = village.ring(2).emeralds();
		if (half != full || full != com.mlin.notas.village.RingCost.emeralds(2)) {
			throw new IllegalStateException("o preco do anel e fixo (" + full + " -> " + half + ")");
		}
		// nada da casa muda com a terraplenagem dos aneis 2 e 3
		VillageTerrain.prepareNow(level, village, 2, false);
		VillageTerrain.prepareNow(level, village, 3, false);
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				if ((dx == 0 || dz == 0 || dx == 4 || dz == 4) && !(dx == 2 && dz == 0)
						&& !level.getBlockState(new BlockPos(hx + dx, hy, hz + dz)).is(Blocks.COBBLESTONE)) {
					throw new IllegalStateException("a terraplenagem mexeu na casa do jogador");
				}
			}
		}
		if (!(level.getBlockState(bed).getBlock() instanceof BedBlock)) {
			throw new IllegalStateException("a cama do jogador sumiu");
		}
		// casa de jogador com cama e porta dentro dos aneis abertos conta como casa da vila
		village.setLevel(VillageLevel.HAMLET);
		village.ring(3).setState(RingWork.State.DONE);
		VillageTerrain.refresh(level, village);
		if (village.playerHouses() != 1) {
			throw new IllegalStateException("a casa do jogador deveria contar como casa: " + village.playerHouses());
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, environment = "mlinnotas-gametest:village3")
	public void levelWaitsForItsRing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 73_000 + RUN;
		// encosta: terreno plano ja estaria "pronto a mao" e o anel fecharia sozinho
		site(helper, x, (dx, dz) -> Math.floorDiv(dx + dz, 4));
		VillageState village = found(helper, x);
		village.addBuilt(VillageProject.PLAZA);
		village.addBuilt(VillageProject.HOUSE);
		village.addBuilt(VillageProject.HOUSE);
		village.addBuilt(VillageProject.FARM);
		VillageTerrain.prepareNow(level, village, 1, false);
		VillageTerrain.prepareNow(level, village, 2, false);
		if (village.addProsperity(200) != null || village.level() != VillageLevel.CAMP) {
			throw new IllegalStateException("sem o anel 3 pronto a vila nao deveria virar Aldeia");
		}
		VillageTerrain.prepareNow(level, village, 3, false);
		if (village.level() != VillageLevel.HAMLET) {
			throw new IllegalStateException("com o anel 3 pronto a vila deveria virar Aldeia, esta " + village.level());
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 1200, environment = "mlinnotas-gametest:village3")
	public void builderKeepsGoingWhenTheLotGroundIsDug(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 74_000 + RUN;
		site(helper, x, (dx, dz) -> 0);
		VillageState village = found(helper, x);
		VillageTerrain.prepareOpenRings(level, village, false);
		village.enqueue(VillageProject.HOUSE);
		VillageData.Job job = VillageServer.startNext(level.getServer(), village, level.getServer().getTickCount());
		if (job == null) {
			throw new IllegalStateException("a casa deveria comecar num lote aplainado");
		}
		VillageLayout.Lot lot = village.layout().lot(job.lot);
		VillageState.Rect r = lot.rect();
		// cava 2 blocos no canto do lote: sem pausa por "chao mexido", a obra segue e termina
		BlockPos hole = at(village, r.x1(), job.ground, r.z1());
		level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(hole.below(), Blocks.AIR.defaultBlockState(), 3);
		helper.succeedWhen(() -> {
			if (village.built(VillageProject.HOUSE) < 1) {
				throw helper.assertionException("casa ainda nao ficou pronta");
			}
			release(helper, village);
		});
	}
}
