package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.works.BridgeWorks;
import com.mlin.notas.village.works.VillageWorks;
import com.mlin.notas.village.works.VillageWorksData;
import com.mlin.notas.village.works.WorkCategory;
import com.mlin.notas.village.works.WorkSpec;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.IntBinaryOperator;

/**
 * Game tests das obras publicas gratuitas da vila (lote {@code mlinnotas-gametest:village_works}): a fila de cada nivel,
 * a obra so roda depois da obra paga e so em chao aplainado, bloco de jogador nunca e trocado, categoria desligada fica
 * parada, ponte sobre agua funda, estacas e placas saem quando alguem reserva o lote, a palicada sai quando a muralha de
 * pedra comeca e o plano antigo (versao 1) passa para a subdivisao densa sem mexer em lote reservado.
 */
public class VillageWorksGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 6_000_000;
	private static final int R = 80;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Terreno de grama plano (com um rio de {@code riverDepth} de agua em z = -40..-34 se pedido) e o quadro no meio. */
	private static void site(GameTestHelper helper, int x, int riverDepth) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 6, ground(level, x, 0).getY()) + 4;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				boolean river = riverDepth > 0 && dz >= -40 && dz <= -34;
				for (int y = y0 - 16; y < y0 + 28; y++) {
					BlockState state;
					if (river && y >= y0 - 1 - riverDepth && y < y0) {
						state = Blocks.WATER.defaultBlockState();
					} else if (river && y < y0 - 1 - riverDepth) {
						state = Blocks.DIRT.defaultBlockState();
					} else {
						state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
					}
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
	}

	private static VillageState found(GameTestHelper helper, int x, VillageLevel level, int rings) {
		ServerLevel world = helper.getLevel();
		BlockPos board = ground(world, x, 0);
		VillageBoardBlock.placeAt(world, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState village = VillageServer.create(world, board, false);
		VillagePlanning.ensureLayout(world, village);
		for (int ring = 1; ring <= rings; ring++) {
			VillageTerrain.prepareNow(world, village, ring, true);
		}
		village.setLevel(level);
		VillagePlanning.grow(world, village);
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
		VillageWorksData.get(level.getServer()).forget(village.key());
	}

	private static BlockPos at(VillageState v, int x, int y, int z) {
		return new BlockPos(v.x() + x, y, v.z() + z);
	}

	private static VillageWorksData.Entry entry(ServerLevel level, VillageState village, String id) {
		return VillageWorksData.get(level.getServer()).state(village.key()).entry(id);
	}

	/** Uma coluna de avenida longe da praca e dos cruzamentos (rua de chao no eixo x = 0). */
	private static int[] avenueCell(MasterPlan plan) {
		for (int z = 20; z < 30; z++) {
			if (plan.ground(0, z) && plan.ground(1, z) && plan.ground(-1, z)) {
				return new int[] {0, z};
			}
		}
		throw new IllegalStateException("sem avenida no eixo");
	}

	/** O plano como era antes da subdivisao densa: ate 2 lotes por quarteirao, nenhum minusculo (menos o de {@code keep}). */
	private static MasterPlan sparseOf(MasterPlan plan, int keep) {
		java.util.List<MasterPlan.Parcel> old = new java.util.ArrayList<>();
		java.util.Map<Integer, Integer> perBlock = new java.util.HashMap<>();
		for (MasterPlan.Parcel p : plan.parcels()) {
			boolean keepIt = p.id() == keep || !p.retired() && p.size() != com.mlin.notas.village.SizeClass.T && perBlock.merge(p.block(), 1, Integer::sum) <= 2;
			old.add(keepIt ? p : new MasterPlan.Parcel(p.id(), p.block(), new VillageState.Rect(0, 0, -1, -1), p.front(), p.size(), p.zone(), false));
		}
		MasterPlan sparse = MasterPlan.restore(plan.seed(), plan.extent(), plan.plazaY(), plan.gridRle(), plan.ringRle(), plan.streetHeights(), plan.blocks(), old,
				plan.playerAreas(), plan.fixedAreas());
		sparse.setVersion(MasterPlan.VERSION_SPARSE);
		return sparse;
	}

	@GameTest(maxTicks = 6000, environment = "mlinnotas-gametest:village_works")
	public void worksWaitForPaidJobsThenRunOnPreparedGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 60_000 + RUN;
		site(helper, x, 0);
		VillageState village = found(helper, x, VillageLevel.HAMLET, 3);
		MasterPlan plan = village.layout().master();
		int[] cell = avenueCell(plan);
		int h = plan.streetHeight(cell[0], cell[1]);
		BlockPos probe = at(village, cell[0], h, cell[1]);
		if (!level.getBlockState(probe).is(Blocks.DIRT_PATH)) {
			throw new IllegalStateException("a terraplenagem deixa a avenida em caminho de terra: " + level.getBlockState(probe));
		}
		// obra paga rodando: as obras publicas esperam
		village.enqueue(VillageProject.HOUSE);
		VillageData.Job job = VillageServer.startNext(level.getServer(), village, level.getServer().getTickCount());
		if (job == null) {
			throw new IllegalStateException("a casa deveria comecar");
		}
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			VillageWorksData.Entry e = entry(level, village, "streets:1");
			if (VillageData.get(level.getServer()).job(village.key()) != null && e != null && (e.index > 0 || e.state > 0)) {
				throw helper.assertionException("obra publica rodou com obra paga em andamento");
			}
			if (ticks[0] == 40 && VillageData.get(level.getServer()).job(village.key()) != null) {
				// termina a obra paga: agora as publicas andam
				VillageServer.completeAll(level.getServer(), village);
			}
		});
		helper.succeedWhen(() -> {
			VillageWorksData.Entry e = entry(level, village, "streets:1");
			if (e == null || e.state != 2) {
				throw helper.assertionException("ruas 1 ainda nao terminaram: " + (e == null ? "sem entrada" : e.state + " " + e.index + "/" + e.total));
			}
			if (level.getBlockState(probe).is(Blocks.DIRT_PATH) || level.getBlockState(probe).isAir()) {
				throw helper.assertionException("avenida nao virou cascalho: " + level.getBlockState(probe));
			}
			// anel 4 (muralha) nao foi aplainado: a palicada espera
			VillageWorksData.Entry palisade = entry(level, village, "wall:palisade");
			if (palisade != null && palisade.state == 2 && !plan.ringPrepared(4) && palisade.total > 0) {
				throw helper.assertionException("palicada em chao sem terraplenagem");
			}
			release(helper, village);
		});
	}

	@GameTest(maxTicks = 3000, environment = "mlinnotas-gametest:village_works")
	public void playerBlocksSurviveAndDisabledCategoriesWait(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 61_000 + RUN;
		site(helper, x, 0);
		VillageState village = found(helper, x, VillageLevel.HAMLET, 3);
		MasterPlan plan = village.layout().master();
		int[] cell = avenueCell(plan);
		int h = plan.streetHeight(cell[0], cell[1]);
		// o jogador calcou um pedaco da avenida com pedregulho e botou um bau na rua
		BlockPos cobble = at(village, cell[0] + 1, h, cell[1]);
		BlockPos chest = at(village, cell[0] - 1, h + 1, cell[1]);
		level.setBlock(cobble, Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
		BlockPos free = at(village, cell[0], h, cell[1]);
		// categoria de ruas desligada: nada acontece nela
		VillageWorksData works = VillageWorksData.get(level.getServer());
		VillageWorksData.State st = works.state(village.key());
		st.disabled |= WorkCategory.STREETS.bit();
		VillageWorks.runAll(level, village);
		if (!level.getBlockState(free).is(Blocks.DIRT_PATH)) {
			throw new IllegalStateException("categoria desligada: a avenida nao devia mudar (" + level.getBlockState(free) + ")");
		}
		VillageWorksData.Entry streets = st.entry("streets:1");
		if (streets == null || streets.state != 0) {
			throw new IllegalStateException("obra de ruas devia ficar na fila com a categoria desligada");
		}
		st.disabled = 0;
		VillageWorks.runAll(level, village);
		if (level.getBlockState(free).is(Blocks.DIRT_PATH) || level.getBlockState(free).isAir()) {
			throw new IllegalStateException("ligada de novo, a avenida devia mudar: " + level.getBlockState(free));
		}
		if (!level.getBlockState(cobble).is(Blocks.POLISHED_ANDESITE) || !level.getBlockState(chest).is(Blocks.CHEST)) {
			throw new IllegalStateException("bloco de jogador foi trocado: " + level.getBlockState(cobble) + " " + level.getBlockState(chest));
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 3000, environment = "mlinnotas-gametest:village_works")
	public void bridgeIsBuiltOverDeepWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 62_000 + RUN;
		site(helper, x, 5);
		VillageState village = found(helper, x, VillageLevel.HAMLET, 3);
		MasterPlan plan = village.layout().master();
		java.util.List<BridgeWorks.Span> spans = BridgeWorks.spans(plan, new com.mlin.notas.village.WorldTerrain(level, VillageServer.center(village)));
		if (spans.isEmpty()) {
			throw new IllegalStateException("o rio devia gerar ponte no plano");
		}
		VillageWorks.runAll(level, village);
		int decks = 0;
		for (BridgeWorks.Span span : spans) {
			if (span.ring() > 3) {
				continue;
			}
			for (int i = 0; i < span.cells().size(); i++) {
				int[] c = span.cells().get(i);
				BlockState deck = level.getBlockState(at(village, c[0], span.heights()[i], c[1]));
				if (deck.isAir() || !deck.getFluidState().isEmpty()) {
					throw new IllegalStateException("ponte sem tabuleiro em " + c[0] + "," + c[1] + " y " + span.heights()[i] + ": " + deck);
				}
				decks++;
			}
		}
		if (decks < 20) {
			throw new IllegalStateException("poucas celulas de ponte: " + decks);
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 3000, environment = "mlinnotas-gametest:village_works")
	public void reservingALotTakesTheStakesAndSignsAway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 63_000 + RUN;
		site(helper, x, 0);
		VillageState village = found(helper, x, VillageLevel.HAMLET, 3);
		VillageWorks.runAll(level, village);
		MasterPlan plan = village.layout().master();
		int signs = 0;
		MasterPlan.Parcel withSign = null;
		for (MasterPlan.Parcel p : plan.parcels()) {
			MasterPlan.Block b = plan.block(p.block());
			if (p.retired() || b.ring() > 3) {
				continue;
			}
			for (int px = p.rect().x0(); px <= p.rect().x1(); px++) {
				for (int pz = p.rect().z0(); pz <= p.rect().z1(); pz++) {
					if (level.getBlockState(at(village, px, b.target() + 1, pz)).is(BlockTags.STANDING_SIGNS)) {
						signs++;
						if (withSign == null && village.layout().lotAt(px, pz) == null) {
							withSign = p;
						}
					}
				}
			}
		}
		if (signs < 5 || withSign == null) {
			throw new IllegalStateException("placas de lote livre: " + signs);
		}
		village.enqueue(VillageProject.HOUSE);
		VillageData.Job job = VillageServer.startNext(level.getServer(), village, level.getServer().getTickCount());
		if (job == null) {
			throw new IllegalStateException("a casa deveria comecar");
		}
		MasterPlan.Parcel taken = plan.parcel(village.layout().lot(job.lot).parcel());
		MasterPlan.Block block = plan.block(taken.block());
		for (int px = taken.rect().x0() - 1; px <= taken.rect().x1() + 1; px++) {
			for (int pz = taken.rect().z0() - 1; pz <= taken.rect().z1() + 1; pz++) {
				BlockState above = level.getBlockState(at(village, px, block.target() + 1, pz));
				if (above.is(BlockTags.STANDING_SIGNS) || above.is(BlockTags.FENCES)) {
					throw new IllegalStateException("sobrou estaca/placa no lote reservado em " + px + "," + pz + ": " + above);
				}
			}
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 3000, environment = "mlinnotas-gametest:village_works")
	public void stoneWallReplacesThePalisade(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 64_000 + RUN;
		site(helper, x, 0);
		VillageState village = found(helper, x, VillageLevel.VILLAGE, 4);
		MasterPlan plan = village.layout().master();
		VillageWorks.runAll(level, village);
		VillageWorksData.Entry palisade = entry(level, village, "wall:palisade");
		if (palisade == null || palisade.state != 2 || palisade.total < 400) {
			throw new IllegalStateException("palicada devia estar pronta com o anel 4 aplainado: " + (palisade == null ? "sem entrada" : palisade.state + " " + palisade.total));
		}
		BlockPos post = null;
		for (int z = -60; z <= 60 && post == null; z++) {
			if (com.mlin.notas.village.VillagePlanner.wallCell(50, z) && plan.plannedHeight(50, z) != Integer.MIN_VALUE
					&& level.getBlockState(at(village, 50, plan.plannedHeight(50, z) + 1, z)).is(BlockTags.LOGS)) {
				post = at(village, 50, plan.plannedHeight(50, z) + 1, z);
			}
		}
		if (post == null) {
			throw new IllegalStateException("nenhum tronco de palicada na linha da muralha");
		}
		village.enqueue(VillageProject.WALL);
		VillageData.Job job = VillageServer.startNext(level.getServer(), village, level.getServer().getTickCount());
		if (job == null) {
			throw new IllegalStateException("a muralha deveria comecar");
		}
		if (level.getBlockState(post).is(BlockTags.LOGS)) {
			throw new IllegalStateException("a palicada devia sair quando a muralha de pedra comeca");
		}
		VillageWorksData.Entry after = entry(level, village, "wall:palisade");
		if (after.state != 3) {
			throw new IllegalStateException("a obra da palicada devia ficar como substituida: " + after.state);
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 600, environment = "mlinnotas-gametest:village_works")
	public void oldSparsePlanBecomesDenseKeepingReservedLots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 65_000 + RUN;
		site(helper, x, 0);
		VillageState village = found(helper, x, VillageLevel.CAMP, 2);
		village.enqueue(VillageProject.HOUSE);
		VillageData.Job job = VillageServer.startNext(level.getServer(), village, level.getServer().getTickCount());
		if (job == null) {
			throw new IllegalStateException("a casa deveria comecar");
		}
		VillageServer.completeAll(level.getServer(), village);
		MasterPlan plan = village.layout().master();
		int lotParcel = village.layout().lots().stream().filter(l -> l.kind().equals("house")).findFirst().orElseThrow().parcel();
		VillageState.Rect keptRect = plan.parcel(lotParcel).rect();
		// "salva" como era antes: versao 1, sem lotes minusculos
		MasterPlan sparse = sparseOf(plan, lotParcel);
		village.layout().setMaster(sparse);
		plan = sparse;
		int before = (int) plan.parcels().stream().filter(p -> !p.retired()).count();
		// e passa pelo formato salvo (codec) como o arquivo antigo
		DataResult<Tag> encoded = VillageData.VILLAGE_CODEC.encodeStart(NbtOps.INSTANCE, village);
		VillageState loaded = VillageData.VILLAGE_CODEC.parse(NbtOps.INSTANCE, encoded.getOrThrow()).getOrThrow();
		if (loaded.layout().master().version() != MasterPlan.VERSION_SPARSE) {
			throw new IllegalStateException("a versao salva do plano devia voltar igual: " + loaded.layout().master().version());
		}
		VillageData.get(level.getServer()).remove(village.key());
		VillageData.get(level.getServer()).add(loaded);
		VillagePlanning.ensureLayout(level, loaded);
		MasterPlan dense = loaded.layout().master();
		if (dense.version() != MasterPlan.VERSION) {
			throw new IllegalStateException("o plano antigo devia passar para a versao densa");
		}
		int after = (int) dense.parcels().stream().filter(p -> !p.retired()).count();
		if (after <= before) {
			throw new IllegalStateException("subdivisao densa devia ter mais lotes: " + before + " -> " + after);
		}
		if (!dense.parcel(lotParcel).rect().equals(keptRect) || dense.parcel(lotParcel).retired()) {
			throw new IllegalStateException("o lote da casa mudou de lugar");
		}
		VillageLayout.Lot lot = loaded.layout().lots().stream().filter(l -> l.kind().equals("house")).findFirst().orElseThrow();
		if (lot.parcel() != lotParcel || !lot.built()) {
			throw new IllegalStateException("a casa pronta perdeu o lote");
		}
		VillageData.get(level.getServer()).remove(loaded.key());
		release(helper, loaded);
		helper.succeed();
	}
}
