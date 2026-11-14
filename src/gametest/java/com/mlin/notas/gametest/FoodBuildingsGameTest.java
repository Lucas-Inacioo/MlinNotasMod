package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.FoodBuildingsServer;
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
import com.mlin.notas.village.VillageShop;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Game tests das construcoes de producao e comida (Vilas 2.0 fase 5, lote {@code mlinnotas-gametest:food_buildings}):
 * as nove ficam prontas numa vila da Cidade com moradores para todas, tem trabalhador e bau de trabalho; moinho, padaria,
 * vinicola, acougue, apiario e curral fazem a sua funcao sobre o bau a cada ciclo; plantacao, pomar, curral e padaria
 * abastecem o celeiro; o celeiro da bonus de colheita; o pomar se colhe com o botao direito, a bancada da serraria da 6
 * tabuas por tronco e as abelhas nao machucam aldeoes; as ofertas do mercado dependem de cada construcao.
 */
public class FoodBuildingsGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 4_800_000;
	private static final int R = 80;

	private static final List<VillageProject> ALL = List.of(VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE,
			VillageProject.HOUSE, VillageProject.FARM, VillageProject.TOWN_HALL, VillageProject.GRANARY, VillageProject.MILL, VillageProject.BAKERY,
			VillageProject.ORCHARD, VillageProject.PEN, VillageProject.APIARY, VillageProject.BUTCHER, VillageProject.WINERY, VillageProject.SAWMILL);

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Chao plano de grama, quadro no meio, vila da Cidade com os aneis aplainados e as obras prontas. */
	private static VillageState village(GameTestHelper helper, int x, List<VillageProject> projects) {
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
		village.setLevel(VillageLevel.TOWN);
		for (VillageProject p : projects) {
			village.enqueue(p);
		}
		// fila antes do plano: as obras pagas reservam lote antes dos outros oficios (VillageLayout.first)
		VillagePlanning.grow(level, village);
		VillageTerrain.prepareOpenRings(level, village, false);
		VillageServer.completeAll(level.getServer(), village);
		for (VillageProject p : projects) {
			int want = (int) projects.stream().filter(q -> q == p).count();
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

	private static Container chest(GameTestHelper helper, VillageState v, String kind) {
		BlockPos pos = FoodBuildingsServer.workChest(helper.getLevel().getServer(), v, kind);
		if (pos == null) {
			throw new IllegalStateException(kind + " sem bau de trabalho (nao esta funcionando?)");
		}
		if (!(helper.getLevel().getBlockEntity(pos) instanceof Container c)) {
			throw new IllegalStateException(kind + ": nao ha bau em " + pos);
		}
		return c;
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
	}

	private static int count(Container c, String id) {
		int n = 0;
		Item item = item(id);
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static void put(Container c, String id, int amount) {
		Item item = item(id);
		for (int left = amount; left > 0; ) {
			int n = Math.min(left, item.getDefaultMaxStackSize());
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (c.getItem(i).isEmpty()) {
					c.setItem(i, new ItemStack(item, n));
					break;
				}
			}
			left -= n;
		}
	}

	private static void expect(boolean ok, String message) {
		if (!ok) {
			throw new IllegalStateException(message);
		}
	}

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:food_buildings")
	public void everyFoodBuildingIsBuiltWorkingAndGatesItsShopOffers(GameTestHelper helper) {
		VillageState v = village(helper, 100_000 + RUN, ALL);
		VillageData data = VillageData.get(helper.getLevel().getServer());
		for (String kind : List.of("granary", "mill", "bakery", "orchard", "pen", "apiary", "butcher", "winery", "sawmill")) {
			expect(VillageBuildings.level(v, kind) >= 1, kind + " deveria estar funcionando: ocupados " + VillageBuildings.idle(v));
			expect(FoodBuildingsServer.workChest(helper.getLevel().getServer(), v, kind) != null, kind + " sem bau");
			VillageLayout.Lot lot = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst().orElseThrow();
			expect(data.record(v.key(), lot.id()) != null && data.record(v.key(), lot.id()).size() > 40, kind + " sem registro de obra");
		}
		expect(VillageLevel.TOWN.missing(v.levelContext()).stream().noneMatch(s -> s.contains("granary")), "com celeiro a Cidade nao pede mais celeiro");
		// as ofertas aparecem so com a construcao funcionando no nivel
		expect(VillageShop.available(VillageShop.byId("mill_flour"), v), "moinho I vende farinha");
		expect(VillageShop.available(VillageShop.byId("bakery_bread"), v), "padaria I vende pao");
		expect(VillageShop.available(VillageShop.byId("butcher_jerky"), v), "acougue I vende charque");
		expect(!VillageShop.available(VillageShop.byId("butcher_jerky_bulk"), v), "charque em quantidade e do acougue II");
		expect(!VillageShop.available(VillageShop.byId("winery_beer"), v), "cerveja e da vinicola II");
		expect(!VillageShop.available(VillageShop.byId("bakery_cake"), v), "bolo e da padaria III");
		// a comida da vila cresce com as construcoes
		int food = VillageNeeds.supply(VillageNeeds.Need.FOOD, VillageNeeds.input(v));
		expect(food >= 5 + 10 + 4 + 5 + 4 + 5 + 2 + 4, "pontos de comida somam as construcoes: " + food);
		release(helper, v);
		helper.succeed();
	}

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:food_buildings")
	public void workshopsDoTheirJobOnTheChestEveryCycle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 101_000 + RUN, ALL);
		// os animais e as abelhas nascem nas chunks recem-forcadas: espera elas ficarem com entidades ativas (senao a busca por
		// area nao enxerga quem acabou de nascer e o curral/apiario poem mais a cada ciclo)
		helper.runAfterDelay(20, () -> {
			// moinho: 16 trigos viram 20 farinhas (5 por 4)
			Container mill = chest(helper, v, "mill");
			put(mill, "minecraft:wheat", 16);
			// padaria: 6 farinhas viram 8 paes (2 vao para o celeiro)
			Container bakery = chest(helper, v, "bakery");
			put(bakery, "mlinnotas:flour", 6);
			// vinicola: 8 uvas viram 6 vinhos
			Container winery = chest(helper, v, "winery");
			put(winery, "mlinnotas:grapes", 8);
			put(winery, "minecraft:wheat", 8);
			// acougue I nao faz charque
			Container butcher = chest(helper, v, "butcher");
			put(butcher, "minecraft:cooked_beef", 4);
			// os numeros assumem estacao neutra: o calendario estatico pode estar no inverno (fator 0,5) por causa de outros lotes
			com.mlin.notas.season.SeasonState.clear();
			FoodBuildingsServer.cycle(level.getServer(), v);
			expect(count(mill, "mlinnotas:flour") == 20 && count(mill, "minecraft:wheat") == 0, "moinho I: 20 farinhas, achou " + count(mill, "mlinnotas:flour"));
			expect(count(bakery, "minecraft:bread") + count(chest(helper, v, "granary"), "minecraft:bread") == 8, "padaria I: 6 farinhas = 8 paes");
			expect(count(bakery, "mlinnotas:flour") == 0, "farinha gasta");
			expect(count(chest(helper, v, "granary"), "minecraft:bread") == 2, "a padaria I leva 2 paes ao celeiro por ciclo");
			expect(count(winery, "mlinnotas:wine") == 6, "vinicola I: vinho, achou " + count(winery, "mlinnotas:wine"));
			expect(count(winery, "mlinnotas:beer") == 0, "cerveja so no II");
			expect(count(butcher, "mlinnotas:charque") == 0 && count(butcher, "minecraft:cooked_beef") == 4, "o acougue I nao seca carne");
			// plantacao, pomar e curral abastecem o celeiro
			Container granary = chest(helper, v, "granary");
			expect(count(granary, "minecraft:apple") >= 2, "pomar abastece o celeiro com macas");
			int crops = count(granary, "minecraft:wheat") + count(granary, "minecraft:carrot") + count(granary, "minecraft:potato")
					+ count(granary, "minecraft:beetroot");
			expect(crops >= 3, "plantacao abastece o celeiro: " + crops);
			int meat = count(granary, "minecraft:beef") + count(granary, "minecraft:porkchop") + count(granary, "minecraft:mutton");
			expect(meat >= 1, "curral abastece o celeiro com carne");
			// apiario: favos e mel no bau, abelhas em volta
			Container apiary = chest(helper, v, "apiary");
			expect(count(apiary, "minecraft:honeycomb") >= 2 && count(apiary, "minecraft:honey_bottle") >= 2, "apiario I poe favos e mel (verao/sem estacao)");
			// curral: no primeiro ciclo chegam 2 de cada; no segundo nasce um filhote de cada ate o limite
			AABB all = new AABB(VillageServer.center(v)).inflate(com.mlin.notas.village.RuralRing.EXTENT, 200, com.mlin.notas.village.RuralRing.EXTENT);
			int cows = level.getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, all).size();
			int sheep = level.getEntitiesOfClass(net.minecraft.world.entity.animal.sheep.Sheep.class, all).size();
			expect(cows == 2 && sheep == 2, "curral I recebe 2 vacas e 2 ovelhas: " + cows + "/" + sheep);
			FoodBuildingsServer.cycle(level.getServer(), v);
			cows = level.getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, all).size();
			expect(cows == 3, "as vacas se criam sozinhas: " + cows);
			Container pen = chest(helper, v, "pen");
			expect(count(pen, "minecraft:white_wool") >= 2 && count(pen, "minecraft:milk_bucket") >= 1, "curral poe la e leite no bau");
			for (int i = 0; i < 6; i++) {
				FoodBuildingsServer.cycle(level.getServer(), v);
			}
			cows = level.getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class, all).size();
			expect(cows <= 4, "o curral I segura no maximo 4 vacas: " + cows);
			expect(level.getEntitiesOfClass(Bee.class, all).size() >= 2, "abelhas no apiario");
			release(helper, v);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:food_buildings")
	public void butcherAndBakeryLevelsUnlockMoreRecipes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 102_000 + RUN, ALL);
		// a Prefeitura II libera o II das outras; o acougue sobe de verdade (planta nova, baus novos)
		VillageLayout.Lot hall = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals("town_hall")).findFirst().orElseThrow();
		v.buildingsState().setLevel(hall.id(), 3);
		VillageLayout.Lot butcherLot = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals("butcher")).findFirst().orElseThrow();
		upgrade(level, v, butcherLot, 2);
		expect(VillageBuildings.level(v, "butcher") == 2, "acougue II funcionando");
		Container butcher = chest(helper, v, "butcher");
		put(butcher, "minecraft:cooked_beef", 4);
		FoodBuildingsServer.cycle(level.getServer(), v);
		expect(count(butcher, "mlinnotas:charque") == 6, "acougue II seca 4 carnes em 6 charques: " + count(butcher, "mlinnotas:charque"));
		expect(VillageShop.available(VillageShop.byId("butcher_jerky_bulk"), v), "charque em quantidade no acougue II");
		VillageLayout.Lot bakeryLot = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals("bakery")).findFirst().orElseThrow();
		upgrade(level, v, bakeryLot, 3);
		Container bakery = chest(helper, v, "bakery");
		put(bakery, "mlinnotas:flour", 3);
		put(bakery, "minecraft:sugar", 2);
		put(bakery, "minecraft:egg", 1);
		put(bakery, "minecraft:milk_bucket", 3);
		FoodBuildingsServer.cycle(level.getServer(), v);
		expect(count(bakery, "minecraft:cake") == 1 && count(bakery, "minecraft:bucket") == 3, "padaria III assa bolo e devolve os baldes");
		expect(VillageShop.available(VillageShop.byId("bakery_cake"), v), "bolo na padaria III");
		release(helper, v);
		helper.succeed();
	}

	/** Paga e constroi na hora a melhoria do lote ate {@code to}. */
	private static void upgrade(ServerLevel level, VillageState v, VillageLayout.Lot lot, int to) {
		BuildingsState b = v.buildingsState();
		while (b.level(lot.id()) < to) {
			int from = b.level(lot.id());
			Map<String, Integer> all = new HashMap<>();
			for (VillageCost cost : BuildingTypes.upgradeCost(lot.project(), from + 1)) {
				all.put(cost.id(), cost.count());
			}
			BuildingsState.Deposit d = b.deposit(lot.id(), lot.project(), from, VillageBuildings.cap(v, lot.kind()), all);
			expect(d.outcome() == BuildingsState.Outcome.COMPLETE, "melhoria de " + lot.kind() + " recusada: " + d.outcome());
			BuildingServer.completeUpgrades(level.getServer(), v);
			expect(b.level(lot.id()) == from + 1, lot.kind() + " nao subiu de " + from);
		}
	}

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:food_buildings")
	public void orchardLeavesSawBenchGranaryBonusAndBees(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 103_000 + RUN, ALL);
		// frutas, abelhas e aldeoes nascem nas chunks recem-forcadas: espera elas ficarem com entidades ativas
		helper.runAfterDelay(20, () -> {
			VillageData data = VillageData.get(level.getServer());
			ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			// pomar: botao direito nas folhas da macieira dao fruta uma vez por dia
			VillageLayout.Lot orchard = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals("orchard")).findFirst().orElseThrow();
			BlockPos leaf = find(data, v, orchard, "_leaves");
			player.setPos(leaf.getX() + 0.5, leaf.getY() - 1, leaf.getZ() + 0.5);
			AABB items = new AABB(leaf).inflate(3);
			expect(use(player, level, leaf) == InteractionResult.SUCCESS, "colher no pomar");
			int apples = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, items, e -> e.getItem().is(Items.APPLE)).stream()
					.mapToInt(e -> e.getItem().getCount()).sum();
			expect(apples >= 2, "o pomar I da pelo menos 2 macas: " + apples);
			int before = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, items).size();
			expect(use(player, level, leaf) == InteractionResult.SUCCESS, "segunda colheita na mesma folha");
			expect(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, items).size() == before, "a arvore ainda esta verde: nada cai");
			// serraria: troncos no cortador de pedras = 6 tabuas cada
			VillageLayout.Lot sawmill = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals("sawmill")).findFirst().orElseThrow();
			BlockPos bench = find(data, v, sawmill, "minecraft:stonecutter");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG, 4));
			expect(use(player, level, bench) == InteractionResult.SUCCESS, "usar a bancada");
			int planks = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(Items.OAK_PLANKS)) {
					planks += player.getInventory().getItem(i).getCount();
				}
			}
			expect(planks == 24, "4 troncos = 24 tabuas (6 por tronco): " + planks);
			// celeiro I: +10% na colheita das plantacoes perto
			// (a plantacao a 6 blocos do celeiro: o plano diretor pode por o celeiro no bairro ou nos arredores)
			VillageLayout.Lot granary = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals("granary")).findFirst().orElseThrow();
			List<ItemStack> drops = new java.util.ArrayList<>(List.of(new ItemStack(Items.WHEAT, 100), new ItemStack(Items.WHEAT_SEEDS, 1)));
			FoodBuildingsServer.harvestBonus(level, VillageServer.center(v).offset((granary.rect().x0() + granary.rect().x1()) / 2 + 6, 0,
					(granary.rect().z0() + granary.rect().z1()) / 2 + 6), drops, 1);
			expect(drops.get(0).getCount() == 110, "celeiro I: 100 trigos viram 110: " + drops.get(0).getCount());
			expect(drops.get(1).getCount() == 1, "a semente que replanta nao ganha bonus");
			// abelhas nao machucam aldeoes
			Bee bee = EntityTypes.BEE.spawn(level, VillageServer.center(v).offset(2, 1, 2), EntitySpawnReason.MOB_SUMMONED);
			Villager villager = EntityTypes.VILLAGER.spawn(level, VillageServer.center(v).offset(3, 1, 2), EntitySpawnReason.MOB_SUMMONED);
			float hp = villager.getHealth();
			boolean hurt = villager.hurtServer(level, level.damageSources().mobAttack(bee), 4.0F);
			expect(!hurt && villager.getHealth() == hp, "a abelha nao fere o aldeao");
			release(helper, v);
			helper.succeed();
		});
	}

	private static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
	}

	/** Um bloco do tipo registrado na obra do lote (do registro de blocos colocados). */
	private static BlockPos find(VillageData data, VillageState v, VillageLayout.Lot lot, String block) {
		VillageData.BuildRecord record = data.record(v.key(), lot.id());
		for (int i = 0; i < record.size(); i++) {
			if (record.block(i).contains(block)) {
				return BlockPos.of(record.positions[i]);
			}
		}
		throw new IllegalStateException(lot.kind() + " sem " + block);
	}
}
