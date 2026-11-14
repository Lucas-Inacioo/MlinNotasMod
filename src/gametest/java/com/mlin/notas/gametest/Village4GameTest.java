package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.RingWork;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillageNeeds;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Map;

/**
 * Game tests das construcoes com nivel (lote {@code mlinnotas-gametest:village4}): melhoria no lugar guarda o
 * conteudo dos baus e o bloco do jogador e traz morador novo, a Prefeitura limita as outras e a vila limita a
 * Prefeitura (e a exigencia {@code town_hall:2} passa a valer), necessidades rendem prosperidade por dia, da Cidade em
 * diante subir de nivel exige as necessidades, obra sem morador livre fica parada, conserto na forja, teto de
 * profissao pelo posto de trabalho e {@code villages.dat} sem niveis carrega tudo no nivel I.
 */
public class Village4GameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 3_200_000;
	private static final int R = 70;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Chao plano de grama em volta de (x, 0), quadro no meio, vila no nivel dado com os aneis aplainados e as obras prontas. */
	private static VillageState village(GameTestHelper helper, int x, VillageLevel lvl, VillageProject... projects) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 6, ground(level, x, 0).getY()) + 4;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 6; y < y0 + 30; y++) {
					BlockState state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		BlockPos board = ground(level, x, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState village = VillageServer.create(level, board, false);
		VillagePlanning.ensureLayout(level, village);
		village.setLevel(lvl);
		VillagePlanning.grow(level, village);
		VillageTerrain.prepareOpenRings(level, village, false);
		for (VillageProject p : projects) {
			village.enqueue(p);
		}
		VillageServer.completeAll(level.getServer(), village);
		for (VillageProject p : projects) {
			int want = (int) java.util.Arrays.stream(projects).filter(q -> q == p).count();
			if (village.built(p) < want) {
				throw new IllegalStateException(p + " nao ficou pronto (" + village.built(p) + " de " + want + ")");
			}
		}
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

	private static VillageLayout.Lot lot(VillageState v, String kind) {
		return v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst()
				.orElseThrow(() -> new IllegalStateException("sem " + kind + " pronto"));
	}

	private static Item representative(String id) {
		return switch (id) {
			case "#minecraft:planks" -> Items.OAK_PLANKS;
			case "#minecraft:logs" -> Items.OAK_LOG;
			case "#mlinnotas:village/glass" -> Items.GLASS;
			case "#mlinnotas:village/stone" -> Items.COBBLESTONE;
			case "#minecraft:beds" -> Items.BED.red();
			case "#mlinnotas:village/food" -> Items.BREAD;
			case "#mlinnotas:village/seeds" -> Items.WHEAT_SEEDS;
			case "#minecraft:wool" -> Items.WOOL.white();
			case "#minecraft:coals" -> Items.COAL;
			case "#minecraft:wooden_fences" -> Items.OAK_FENCE;
			default -> BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
		};
	}

	/** Jogador falso com tudo o que a melhoria do projeto para {@code level} custa. */
	private static ServerPlayer payer(GameTestHelper helper, VillageProject project, int level) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		for (VillageCost cost : BuildingTypes.upgradeCost(project, level)) {
			Item item = representative(cost.id());
			int left = cost.count();
			while (left > 0) {
				int n = Math.min(left, item.getDefaultMaxStackSize());
				player.getInventory().add(new ItemStack(item, n));
				left -= n;
			}
		}
		return player;
	}

	private static int villagers(ServerLevel level, VillageState v) {
		return level.getEntitiesOfClass(Villager.class, new AABB(VillageServer.center(v)).inflate(R, 40, R)).size();
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village4")
	public void upgradeInPlaceKeepsChestContentsAndPlayerBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 90_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.TOWN_HALL);
		VillageData data = VillageData.get(level.getServer());
		VillageLayout.Lot house = lot(v, "house");
		VillageData.BuildRecord record = data.record(v.key(), house.id());
		BlockPos chest = null;
		for (int i = 0; i < record.size(); i++) {
			if (record.block(i).equals("minecraft:chest")) {
				chest = BlockPos.of(record.positions[i]);
			}
		}
		if (chest == null || !(level.getBlockEntity(chest) instanceof Container c)) {
			throw new IllegalStateException("casa sem bau");
		}
		c.setItem(0, new ItemStack(Items.DIAMOND, 7));
		// bloco do jogador num canto do lote
		BlockPos gold = new BlockPos(v.x() + house.x(), record.ground + 1, v.z() + house.z());
		level.setBlock(gold, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
		int people = villagers(level, v);
		// a Prefeitura no I segura tudo no I
		ServerPlayer player = payer(helper, VillageProject.HOUSE, 2);
		if (BuildingServer.deposit(player, v, house.id()) != BuildingsState.Outcome.CAPPED) {
			throw new IllegalStateException("com a Prefeitura no I a casa nao pode ir ao II");
		}
		v.buildingsState().setLevel(lot(v, "town_hall").id(), 2);
		if (BuildingServer.deposit(player, v, house.id()) != BuildingsState.Outcome.COMPLETE) {
			throw new IllegalStateException("a entrega deveria pagar a melhoria");
		}
		BuildingServer.completeUpgrades(level.getServer(), v);
		if (v.buildingsState().level(house.id()) != 2) {
			throw new IllegalStateException("casa nao chegou ao II");
		}
		if (!level.getBlockState(gold).is(Blocks.GOLD_BLOCK)) {
			throw new IllegalStateException("a melhoria mexeu no bloco do jogador");
		}
		VillageData.BuildRecord after = data.record(v.key(), house.id());
		if (after == null || after.size() <= record.size()) {
			throw new IllegalStateException("registro da casa II nao foi guardado ou nao cresceu");
		}
		int diamonds = 0;
		VillageState.Rect r = lot(v, "house").rect();
		for (int i = 0; i < after.size(); i++) {
			BlockPos p = BlockPos.of(after.positions[i]);
			if (level.getBlockEntity(p) instanceof Container box) {
				for (int s = 0; s < box.getContainerSize(); s++) {
					if (box.getItem(s).is(Items.DIAMOND)) {
						diamonds += box.getItem(s).getCount();
					}
				}
			}
		}
		if (diamonds != 7) {
			throw new IllegalStateException("os diamantes do bau deveriam estar num bau da casa II: " + diamonds);
		}
		if (villagers(level, v) < people + 1) {
			throw new IllegalStateException("casa II deveria trazer mais um morador");
		}
		if (r.x0() > house.x() || r.z0() > house.z()) {
			throw new IllegalStateException("lote mudou de lugar");
		}
		release(helper, v);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village4")
	public void townHallIsCappedByTheVillageAndUnlocksTheLevelRequirement(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 91_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.TOWN_HALL);
		VillageLayout.Lot hall = lot(v, "town_hall");
		if (!VillageLevel.CAPITAL.missing(v.levelContext()).contains("level:town_hall:2")) {
			throw new IllegalStateException("Capital deveria pedir a Prefeitura II: " + VillageLevel.CAPITAL.missing(v.levelContext()));
		}
		ServerPlayer player = payer(helper, VillageProject.TOWN_HALL, 2);
		if (BuildingServer.deposit(player, v, hall.id()) != BuildingsState.Outcome.COMPLETE) {
			throw new IllegalStateException("Prefeitura II deveria ser paga na Vila");
		}
		BuildingServer.completeUpgrades(level.getServer(), v);
		if (VillageBuildings.townHallLevel(v) != 2) {
			throw new IllegalStateException("Prefeitura nao chegou ao II");
		}
		if (VillageLevel.CAPITAL.missing(v.levelContext()).contains("level:town_hall:2")) {
			throw new IllegalStateException("com a Prefeitura II a exigencia deveria sumir");
		}
		ServerPlayer more = payer(helper, VillageProject.TOWN_HALL, 3);
		if (BuildingServer.deposit(more, v, hall.id()) != BuildingsState.Outcome.CAPPED) {
			throw new IllegalStateException("a Vila nao deixa a Prefeitura passar do II");
		}
		if (VillageBuildings.cap(v, "smithy") != 2 || VillageBuildings.cap(v, "house") != 2) {
			throw new IllegalStateException("Prefeitura II deveria liberar o II para as outras");
		}
		// a planta do II ficou no lugar com atril (o bloco do atril e da vila)
		boolean lectern = false;
		VillageData.BuildRecord record = VillageData.get(level.getServer()).record(v.key(), hall.id());
		for (int i = 0; i < record.size(); i++) {
			lectern |= record.block(i).equals("minecraft:lectern");
		}
		if (!lectern) {
			throw new IllegalStateException("Prefeitura sem atril");
		}
		release(helper, v);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village4")
	public void needsGiveProsperityOncePerDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 92_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM,
				VillageProject.MARKET, VillageProject.TOWN_HALL);
		long day = VillageServer.day(level.getServer()) + 1000;
		BuildingServer.dailyNeeds(level.getServer(), v, day);
		int before = v.prosperity();
		BuildingServer.dailyNeeds(level.getServer(), v, day);
		if (v.prosperity() != before) {
			throw new IllegalStateException("no mesmo dia nao rende de novo");
		}
		BuildingServer.dailyNeeds(level.getServer(), v, day + 1);
		Map<VillageNeeds.Need, Integer> values = VillageNeeds.values(v);
		int expected = VillageNeeds.dailyProsperity(values);
		if (expected <= 0) {
			throw new IllegalStateException("moradia, comida e comercio cheios deveriam render: " + values + " pop " + v.buildingsState().population());
		}
		if (v.prosperity() != before + expected) {
			throw new IllegalStateException("prosperidade do dia: " + (v.prosperity() - before) + " em vez de " + expected + " " + values);
		}
		if (v.buildingsState().population() < 2) {
			throw new IllegalStateException("moradores nao foram contados: " + v.buildingsState().population());
		}
		release(helper, v);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village4")
	public void fromTownLevelUpWaitsForTheNeeds(GameTestHelper helper) {
		VillageState v = village(helper, 93_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.TOWN_HALL);
		for (int ring = 1; ring <= 6; ring++) {
			v.ring(ring).setState(RingWork.State.DONE);
		}
		v.addBuilt(VillageProject.WALL);
		v.addBuilt(VillageProject.MARKET);
		for (int i = 0; i < 16; i++) {
			v.addBuilt(VillageProject.LAMPS);
		}
		v.addProsperity(VillageLevel.TOWN.prosperity());
		if (v.level() != VillageLevel.VILLAGE) {
			throw new IllegalStateException("com necessidades baixas a vila nao deveria virar Cidade");
		}
		if (!VillageLevel.TOWN.missing(v.levelContext()).equals(java.util.List.of("needs"))) {
			throw new IllegalStateException("so as necessidades deveriam faltar: " + VillageLevel.TOWN.missing(v.levelContext()) + " "
					+ VillageNeeds.values(v));
		}
		release(helper, v);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village4")
	public void craftBuildingWithoutWorkerStandsStillAndSmithyRepairs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 94_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.FARM, VillageProject.SMITHY,
				VillageProject.MARKET, VillageProject.TOWN_HALL);
		if (VillageBuildings.idle(v).size() != 1) {
			throw new IllegalStateException("1 casa (2 moradores) para 3 oficios: 1 deveria ficar parada, " + VillageBuildings.idle(v));
		}
		int idleLot = VillageBuildings.idle(v).iterator().next();
		String idleKind = v.layout().lot(idleLot).kind();
		if (VillageBuildings.level(v, idleKind) != 0) {
			throw new IllegalStateException(idleKind + " parada nao deveria funcionar");
		}
		v.enqueue(VillageProject.HOUSE);
		VillageServer.completeAll(level.getServer(), v);
		if (!VillageBuildings.idle(v).isEmpty()) {
			throw new IllegalStateException("com 2 casas ninguem fica parado");
		}
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.setDamageValue(pick.getMaxDamage() - 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, pick);
		player.getInventory().add(new ItemStack(Items.EMERALD, 3));
		player.getInventory().add(new ItemStack(Items.IRON_INGOT, 10));
		if (BuildingServer.forgeRepair(player, v)) {
			throw new IllegalStateException("3 esmeraldas nao pagam o conserto de um item quase quebrado na forja I");
		}
		player.getInventory().add(new ItemStack(Items.EMERALD, 10));
		if (!BuildingServer.forgeRepair(player, v) || player.getMainHandItem().getDamageValue() != 0) {
			throw new IllegalStateException("a forja deveria consertar");
		}
		int[] price = com.mlin.notas.village.VillageShop.forgeRepair(pick.getMaxDamage() - 1, pick.getMaxDamage(), 1);
		int emeralds = player.getInventory().countItem(Items.EMERALD);
		int iron = player.getInventory().countItem(Items.IRON_INGOT);
		if (emeralds != 13 - price[0] || iron != 10 - price[1]) {
			throw new IllegalStateException("conserto cobrou errado: " + emeralds + " esmeraldas, " + iron + " ferro, preco " + price[0] + "/" + price[1]);
		}
		// teto de profissao pelo posto: ferreiro com posto na forja IV numa Capital chega a especialista
		v.setLevel(VillageLevel.CAPITAL);
		v.buildingsState().setLevel(lot(v, "town_hall").id(), 4);
		VillageLayout.Lot smithy = lot(v, "smithy");
		v.buildingsState().setLevel(smithy.id(), 4);
		VillageData.BuildRecord record = VillageData.get(level.getServer()).record(v.key(), smithy.id());
		BlockPos site = BlockPos.of(record.positions[record.size() / 2]);
		Villager smith = EntityTypes.VILLAGER.spawn(level, site.above(), EntitySpawnReason.MOB_SUMMONED);
		if (BuildingServer.professionCap(level, smith) != 3) {
			throw new IllegalStateException("sem posto o teto e jornaleiro");
		}
		smith.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), site));
		if (BuildingServer.professionCap(level, smith) != 4) {
			throw new IllegalStateException("posto na forja IV deveria deixar chegar a especialista: " + BuildingServer.professionCap(level, smith));
		}
		smith.discard();
		release(helper, v);
		helper.succeed();
	}

	@GameTest(maxTicks = 40, environment = "mlinnotas-gametest:village4")
	public void oldVillagesFileLoadsEveryBuildingAtLevelOne(GameTestHelper helper) {
		VillageState v = new VillageState("minecraft:overworld", 10, 70, 10, 5L, com.mlin.notas.village.VillageStyle.PLAINS, "Antiga");
		v.addBuilt(VillageProject.HOUSE);
		v.addBuilt(VillageProject.SMITHY);
		v.buildingsState().setLevel(3, 2);
		Tag saved = VillageData.VILLAGE_CODEC.encodeStart(NbtOps.INSTANCE, v).getOrThrow();
		if (!((CompoundTag) saved).contains("buildings")) {
			throw new IllegalStateException("formato novo deveria gravar os niveis");
		}
		((CompoundTag) saved).remove("buildings");
		VillageState old = VillageData.VILLAGE_CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		if (old.buildingsState().level(3) != 1 || VillageBuildings.bestLevel(old, "smithy") != 1 || old.built(VillageProject.HOUSE) != 1) {
			throw new IllegalStateException("vila antiga deveria carregar tudo no nivel I");
		}
		helper.succeed();
	}
}
