package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.StartingVillage;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.WorldTerrain;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Map;

/**
 * Game tests do plano da vila (lote {@code mlinnotas-gametest:village2}): obra numa encosta (terreno preparado, sem
 * nada solto nem enterrado), cancelar devolve tudo, demolir tira os blocos e devolve metade, e vila vanilla nao nasce
 * perto da vila inicial.
 */
public class Village2GameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Encosta de grama sobre terra (sobe 1 a cada {@code run} blocos para leste) e quadro no meio. */
	private static VillageState slope(GameTestHelper helper, int x, int run) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - 64) >> 4; cx <= (x + 64) >> 4; cx++) {
			for (int cz = -64 >> 4; cz <= 64 >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 4, ground(level, x, 0).getY()) + 2;
		for (int dx = -56; dx <= 56; dx++) {
			int h = y0 + Math.floorDiv(dx, run);
			for (int dz = -56; dz <= 56; dz++) {
				for (int y = y0 - 24; y < y0 + 40; y++) {
					BlockState state = y < h - 1 ? Blocks.DIRT.defaultBlockState() : y == h - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		BlockPos board = ground(level, x, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		return VillageServer.create(level, board, false);
	}

	private static void release(GameTestHelper helper, VillageState village) {
		ServerLevel level = helper.getLevel();
		for (int cx = (village.x() - 64) >> 4; cx <= (village.x() + 64) >> 4; cx++) {
			for (int cz = -64 >> 4; cz <= 64 >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		VillageData.get(level.getServer()).remove(village.key());
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static ServerPlayer houseMaterials(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.SPRUCE_PLANKS, 64));
		player.getInventory().add(new ItemStack(Items.OAK_LOG, 12));
		player.getInventory().add(new ItemStack(Items.GLASS, 6));
		player.getInventory().add(new ItemStack(Items.BED.red(), 1));
		return player;
	}

	/** Blocos da obra no lote: porta, cama; e nenhum chao natural acima do alvo nem buraco no chao do lote. */
	private static String checkLot(ServerLevel level, VillageState village, VillageLayout.Lot lot, int target) {
		VillageState.Rect r = lot.rect();
		boolean door = false;
		boolean bed = false;
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				BlockPos floor = new BlockPos(village.x() + x, target, village.z() + z);
				BlockState at = level.getBlockState(floor);
				if (at.isAir()) {
					return "buraco no chao do lote em " + floor;
				}
				if (level.getBlockState(floor.below()).isAir()) {
					return "chao do lote solto no ar em " + floor;
				}
				for (int y = 1; y <= 8; y++) {
					BlockState above = level.getBlockState(floor.above(y));
					if (above.is(Blocks.DIRT) || above.is(Blocks.GRASS_BLOCK) || above.is(Blocks.STONE)) {
						return "obra enterrada: terra natural em " + floor.above(y);
					}
					door |= above.getBlock() instanceof DoorBlock;
					bed |= above.getBlock() instanceof BedBlock;
				}
			}
		}
		if (lot.project() == VillageProject.HOUSE && (!door || !bed)) {
			return "casa sem porta ou cama (porta " + door + ", cama " + bed + ")";
		}
		// a frente da porta fica no maximo 1 acima/abaixo do chao da obra
		int[] f = lot.front();
		int front = new WorldTerrain(level, VillageServer.center(village)).ground(f[0], f[1]);
		if (Math.abs(front - target) > 1) {
			return "degrau de " + Math.abs(front - target) + " na porta de " + lot.kind();
		}
		return null;
	}

	@GameTest(maxTicks = 600, environment = "mlinnotas-gametest:village2")
	public void buildsOnASlopeWithoutFloatingOrBuriedParts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState village = slope(helper, 50000 + RUN, 2);
		VillageLayout layout = VillagePlanning.ensureLayout(level, village);
		if (layout.lotsOf("house").size() < 2 || layout.lotsOf("farm").isEmpty()) {
			throw new IllegalStateException("a encosta deveria ter lotes para 2 casas e 1 plantacao: " + layout.lots().size());
		}
		village.enqueue(VillageProject.HOUSE);
		village.enqueue(VillageProject.FARM);
		village.enqueue(VillageProject.HOUSE);
		VillageServer.completeAll(level.getServer(), village);
		VillageData data = VillageData.get(level.getServer());
		int built = 0;
		for (VillageLayout.Lot lot : layout.lots()) {
			if (!lot.built()) {
				continue;
			}
			built++;
			VillageData.BuildRecord record = data.record(village.key(), lot.id());
			if (record == null || record.size() < 20) {
				throw new IllegalStateException("obra sem registro de blocos: " + lot.kind());
			}
			String problem = checkLot(level, village, lot, record.ground);
			if (problem != null) {
				throw new IllegalStateException(problem);
			}
		}
		if (built != 3) {
			throw new IllegalStateException("deveriam ficar prontas 3 obras na encosta, ficaram " + built);
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 40, environment = "mlinnotas-gametest:village2")
	public void cancellingAQueuedProjectRefundsEverything(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState village = slope(helper, 51000 + RUN, 1000);
		ServerPlayer player = houseMaterials(helper);
		player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 10));
		if (VillageServer.deposit(player, village, VillageProject.HOUSE) != VillageState.Outcome.COMPLETE) {
			throw new IllegalStateException("entrega completa deveria fechar a casa");
		}
		if (count(player, Items.SPRUCE_PLANKS) != 0 || count(player, Items.GLASS) != 0) {
			throw new IllegalStateException("a entrega deveria tirar os itens");
		}
		BlockPos board = VillageServer.center(village);
		if (!VillagePlanning.cancel(player, village, board, 0)) {
			throw new IllegalStateException("obra da fila (nao comecada) deveria poder ser cancelada");
		}
		if (!village.queue().isEmpty()) {
			throw new IllegalStateException("a fila deveria ficar vazia");
		}
		// devolve os mesmos itens: abetos, troncos, vidros e a cama
		if (count(player, Items.SPRUCE_PLANKS) != 64 || count(player, Items.OAK_PLANKS) != 10 || count(player, Items.OAK_LOG) != 12
				|| count(player, Items.GLASS) != 6 || count(player, Items.BED.red()) != 1) {
			throw new IllegalStateException("devolucao incompleta: abeto " + count(player, Items.SPRUCE_PLANKS) + ", tronco " + count(player, Items.OAK_LOG)
					+ ", vidro " + count(player, Items.GLASS));
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village2")
	public void demolishingRemovesTheBuildingAndRefundsHalf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState village = slope(helper, 52000 + RUN, 1000);
		ServerPlayer player = houseMaterials(helper);
		VillageServer.deposit(player, village, VillageProject.HOUSE);
		VillageServer.completeAll(level.getServer(), village);
		VillageLayout.Lot lot = village.layout().lotsOf("house").stream().filter(VillageLayout.Lot::built).findFirst()
				.orElseThrow(() -> new IllegalStateException("casa nao ficou pronta"));
		// o jogador mexeu: um bloco da obra trocado por um seu nao pode sumir
		VillageData.BuildRecord record = VillageData.get(level.getServer()).record(village.key(), lot.id());
		BlockPos mine = null;
		for (int i = 0; i < record.size(); i++) {
			BlockPos p = BlockPos.of(record.positions[i]);
			if (p.getY() > record.ground && level.getBlockState(p).is(BlockTags.LOGS)) {
				mine = p;
				break;
			}
		}
		if (mine != null) {
			level.setBlock(mine, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
		}
		int prosperity = village.prosperity();
		if (!VillagePlanning.demolish(player, village, VillageServer.center(village), lot.id())) {
			throw new IllegalStateException("demolir deveria funcionar numa obra com registro");
		}
		if (lot.built() || village.built(VillageProject.HOUSE) != 0) {
			throw new IllegalStateException("o lote deveria ficar livre e a conta de casas zerar");
		}
		if (village.prosperity() != Math.max(0, prosperity - VillageProject.HOUSE.prosperity())) {
			throw new IllegalStateException("demolir deveria tirar a prosperidade da obra");
		}
		VillageState.Rect r = lot.rect();
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				for (int y = 1; y <= 10; y++) {
					BlockState s = level.getBlockState(new BlockPos(village.x() + x, record.ground + y, village.z() + z));
					if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof BedBlock || s.is(BlockTags.PLANKS)) {
						throw new IllegalStateException("sobrou parte da casa em " + x + "," + z);
					}
				}
			}
		}
		if (mine != null && !level.getBlockState(mine).is(Blocks.GOLD_BLOCK)) {
			throw new IllegalStateException("demolir tirou um bloco do jogador");
		}
		// metade: 32 abetos, 6 troncos, 3 vidros (a cama sozinha nao divide)
		if (count(player, Items.SPRUCE_PLANKS) != 32 || count(player, Items.OAK_LOG) != 6 || count(player, Items.GLASS) != 3) {
			throw new IllegalStateException("devolucao de metade errada: abeto " + count(player, Items.SPRUCE_PLANKS) + ", tronco "
					+ count(player, Items.OAK_LOG) + ", vidro " + count(player, Items.GLASS));
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 2400, environment = "mlinnotas-gametest:village2")
	public void vanillaVillagesDoNotStartNearTheStarterVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int[] previous = StartingVillage.site();
		StructureSet villages = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(BuiltinStructureSets.VILLAGES).value();
		var state = level.getChunkSource().getGeneratorState();
		// longe de tudo, numa area que esta execucao ainda nao gerou: o centro fica num chunk onde uma vila comecaria
		int bx = (-3_000_000 - RUN * 4) >> 4;
		int bz = 3_000_000 >> 4;
		int sx = Integer.MIN_VALUE;
		int sz = 0;
		search:
		for (int dx = 0; dx < 80; dx++) {
			for (int dz = 0; dz < 80; dz++) {
				if (villages.placement().isStructureChunk(state, bx + dx, bz + dz)) {
					sx = ((bx + dx) << 4) + 8;
					sz = ((bz + dz) << 4) + 8;
					break search;
				}
			}
		}
		if (sx == Integer.MIN_VALUE) {
			throw new IllegalStateException("nenhum chunk de vila na area de teste");
		}
		StartingVillage.setSite(new int[] {sx, sz});
		try {
			int near = 0;
			int far = 0;
			int farStarts = 0;
			int r = (StartingVillage.SUPPRESS_RADIUS >> 4) + 90;
			for (int cx = (sx >> 4) - r; cx <= (sx >> 4) + r; cx++) {
				for (int cz = (sz >> 4) - r; cz <= (sz >> 4) + r; cz++) {
					if (!villages.placement().isStructureChunk(state, cx, cz)) {
						continue;
					}
					boolean suppressed = StartingVillage.suppresses(cx, cz);
					if (!suppressed && far >= 6) {
						continue;
					}
					ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS, true);
					if (chunk.getAllStarts().isEmpty()) {
						// o servidor de teste nao gera estruturas (generate_structures = false): roda a etapa de inicio de
						// estruturas a mao, que e onde o mixin age
						level.getChunkSource().getGenerator().createStructures(level.registryAccess(), state, level.structureManager(), chunk,
								level.getServer().getStructureTemplateManager(), level.dimension());
					}
					boolean village = false;
					for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
						if (e.getValue().isValid() && level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(e.getKey())
								.is(StructureTags.VILLAGE)) {
							village = true;
						}
					}
					if (suppressed) {
						near++;
						if (village) {
							throw new IllegalStateException("vila vanilla nasceu no chunk " + cx + "," + cz + " perto da vila inicial");
						}
					} else {
						far++;
						if (village) {
							farStarts++;
						}
					}
				}
			}
			if (near == 0) {
				throw new IllegalStateException("nenhum chunk de vila perto do centro para testar (raio " + StartingVillage.SUPPRESS_RADIUS + ")");
			}
			if (far > 0 && farStarts == 0) {
				throw new IllegalStateException("longe da vila inicial as vilas vanilla deveriam continuar nascendo (" + far + " chunks testados)");
			}
		} finally {
			StartingVillage.setSite(previous);
		}
		// o quadro do worldgen tambem nao aparece perto
		StartingVillage.setSite(new int[] {sx, sz});
		boolean nearBoard = StartingVillage.nearSite(new BlockPos(sx + 40, 64, sz - 30));
		boolean farBoard = StartingVillage.nearSite(new BlockPos(sx + 400, 64, sz));
		StartingVillage.setSite(previous);
		if (!nearBoard || farBoard) {
			throw new IllegalStateException("quadro do worldgen: perto " + nearBoard + ", longe " + farBoard);
		}
		helper.succeed();
	}
}
