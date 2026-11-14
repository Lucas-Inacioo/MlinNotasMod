package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.enchanting.EnchantRules;
import com.mlin.notas.enchanting.SelectiveEnchantMenu;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.tier.Tier;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.BuildingsState;
import com.mlin.notas.village.CraftsRegistry;
import com.mlin.notas.village.CraftsRules;
import com.mlin.notas.village.CraftsServer;
import com.mlin.notas.village.TanningVatBlockEntity;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Game tests das construcoes de oficios e servicos (Vilas 2.0, fase 6; lote {@code mlinnotas-gametest:crafts}): as nove
 * obras ficam prontas e funcionando e sobem ate o nivel maximo; no nivel I a tina so cura dentro do curtume, a pedreira
 * enche o bau, so o Encantador abre a mesa (teto I), o altar abencoa uma vez por dia, a taverna limita os boatos, traz uma
 * missao extra e o mercado vende livro de nivel I, a feira abre com mercadores e o lote vazio com cama e porta conta como
 * casa. Tambem confere que a mesa de encantamento vanilla perdeu a receita e a carne podre nao funde mais em couro.
 */
public class CraftsGameTest {
	private static final String ENV = "mlinnotas-gametest:crafts";
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 7_700_000;
	private static final int R = 80;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

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
		for (VillageProject p : projects) {
			village.enqueue(p);
		}
		// fila antes do plano: as obras pagas reservam lote antes dos outros oficios (VillageLayout.first)
		VillagePlanning.grow(level, village);
		VillageTerrain.prepareOpenRings(level, village, false);
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

	/** Posicoes dos blocos {@code block} que a obra do lote colocou. */
	private static List<BlockPos> blocksOf(MinecraftServer server, VillageState v, VillageLayout.Lot lot, String block) {
		VillageData.BuildRecord record = VillageData.get(server).record(v.key(), lot.id());
		List<BlockPos> out = new ArrayList<>();
		if (record != null) {
			for (int i = 0; i < record.size(); i++) {
				if (record.block(i).equals(block)) {
					out.add(BlockPos.of(record.positions[i]));
				}
			}
		}
		return out;
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
			case "#minecraft:flowers" -> Items.POPPY;
			default -> BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
		};
	}

	/** Paga e constroi na hora as melhorias do tipo ate {@code to} (a Prefeitura acompanha). */
	private static void upgrade(MinecraftServer server, VillageState v, String kind, int to) {
		VillageLayout.Lot lot = lot(v, kind);
		BuildingsState b = v.buildingsState();
		if (VillageBuildings.townHallLevel(v) < to && !kind.equals(BuildingTypes.TOWN_HALL)) {
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
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- niveis

	@GameTest(maxTicks = 600, environment = ENV)
	public void everyCraftsBuildingBuildsAndGoesUpToItsMaxLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 97_300 + RUN, VillageLevel.METROPOLIS, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE,
				VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.TOWN_HALL, VillageProject.ENCHANTER, VillageProject.CHURCH, VillageProject.TAVERN,
				VillageProject.TAILOR, VillageProject.FLETCHER, VillageProject.QUARRY, VillageProject.TANNERY, VillageProject.GARDEN,
				VillageProject.EMPTY_LOT);
		BuildingServer.count(level, v);
		List<String> kinds = List.of("enchanter", "church", "tavern", "tailor", "fletcher", "quarry", "tannery");
		for (String kind : kinds) {
			if (VillageBuildings.level(v, kind) != 1) {
				throw new IllegalStateException(kind + " deveria estar funcionando no I (parada por falta de morador? " + VillageBuildings.idle(v) + ")");
			}
		}
		for (String kind : List.of("enchanter", "church", "tavern", "tailor", "fletcher", "quarry", "tannery", "garden")) {
			upgrade(level.getServer(), v, kind, BuildingTypes.maxLevel(kind));
			if (VillageBuildings.bestLevel(v, kind) != BuildingTypes.maxLevel(kind)) {
				throw new IllegalStateException(kind + " nao chegou ao nivel maximo");
			}
		}
		VillageLayout.Lot tannery = lot(v, "tannery");
		if (blocksOf(level.getServer(), v, tannery, "mlinnotas:tanning_vat").size() != 2) {
			throw new IllegalStateException("curtume II tem duas tinas, tem " + blocksOf(level.getServer(), v, tannery, "mlinnotas:tanning_vat"));
		}
		if (blocksOf(level.getServer(), v, lot(v, "enchanter"), "minecraft:enchanting_table").size() != 1) {
			throw new IllegalStateException("encantador V com uma mesa so");
		}
		if (blocksOf(level.getServer(), v, lot(v, "church"), "minecraft:bell").size() != 1) {
			throw new IllegalStateException("igreja com um sino");
		}
		if (blocksOf(level.getServer(), v, lot(v, "quarry"), "minecraft:chest").isEmpty()) {
			throw new IllegalStateException("pedreira III perdeu o bau");
		}
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- funcoes no nivel I

	@GameTest(maxTicks = 600, environment = ENV)
	public void servicesWorkAtLevelOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		SkillServer.ensureLevel(server, SkillBranch.MINING, Tier.ASTRALITE.level());
		VillageState v = village(helper, 98_700 + RUN, VillageLevel.TOWN, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.HOUSE,
				VillageProject.HOUSE, VillageProject.ENCHANTER, VillageProject.CHURCH, VillageProject.TAVERN, VillageProject.TAILOR,
				VillageProject.FLETCHER, VillageProject.QUARRY, VillageProject.TANNERY, VillageProject.EMPTY_LOT);
		BuildingServer.count(level, v);
		for (String kind : List.of("enchanter", "church", "tavern", "tailor", "fletcher", "quarry", "tannery")) {
			if (VillageBuildings.level(v, kind) != 1) {
				throw new IllegalStateException(kind + " nao funciona no I: parados " + VillageBuildings.idle(v));
			}
		}

		// --- curtume: a tina cura carne podre so dentro do curtume funcionando
		VillageLayout.Lot tannery = lot(v, "tannery");
		BlockPos vatPos = blocksOf(server, v, tannery, "mlinnotas:tanning_vat").getFirst();
		TanningVatBlockEntity vat = (TanningVatBlockEntity) level.getBlockEntity(vatPos);
		vat.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 8));
		vat.setItem(1, new ItemStack(Items.ROTTEN_FLESH, 5));
		vat.setItem(2, new ItemStack(Items.BREAD, 3));
		helper.assertTrue(CraftsServer.levelAt(level, vatPos, "tannery") == 1, "a tina esta no curtume I: " + CraftsServer.buildingAt(level, vatPos) + " vila " + (VillageServer.villageAt(level, vatPos) == null ? null : VillageServer.villageAt(level, vatPos).name() + " " + VillageServer.villageAt(level, vatPos).key() + " eu=" + v.key()) + " lote " + tannery.id() + " idle " + VillageBuildings.idle(v));
		for (int i = 0; i < TanningVatBlockEntity.CHECK_TICKS; i++) {
			TanningVatBlockEntity.serverTick(level, vatPos, level.getBlockState(vatPos), vat);
		}
		helper.assertTrue(vat.progress(0) == TanningVatBlockEntity.CHECK_TICKS, "a carne comecou a curar: " + vat.progress(0));
		helper.assertTrue(vat.progress(2) == 0, "pao nao cura");
		vat.advance(CraftsRules.TANNING_TICKS, 1);
		helper.assertTrue(vat.getItem(0).is(Items.LEATHER) && vat.getItem(0).getCount() == 4, "8 carnes viram 4 couros no nivel I: " + vat.getItem(0));
		helper.assertTrue(vat.getItem(1).is(Items.LEATHER) && vat.getItem(1).getCount() == 3, "5 carnes viram 3 couros (arredonda para cima)");
		helper.assertTrue(vat.getItem(2).is(Items.BREAD), "pao fica");
		vat.setItem(3, new ItemStack(Items.ROTTEN_FLESH, 6));
		vat.advance(CraftsRules.TANNING_TICKS, 2);
		helper.assertTrue(vat.getItem(3).is(Items.LEATHER) && vat.getItem(3).getCount() == 6, "nivel II: 1 couro por carne");
		helper.assertFalse(vat.canPlaceItem(8, new ItemStack(Items.ROTTEN_FLESH)), "o nono espaco da tela fica fechado");
		BlockPos far = new BlockPos(v.x() + 5_000, vatPos.getY(), v.z() + 5_000);
		level.getChunk(far.getX() >> 4, far.getZ() >> 4);
		level.setBlock(far, CraftsRegistry.TANNING_VAT.defaultBlockState(), 3);
		TanningVatBlockEntity loose = (TanningVatBlockEntity) level.getBlockEntity(far);
		loose.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 4));
		for (int i = 0; i < TanningVatBlockEntity.CHECK_TICKS; i++) {
			TanningVatBlockEntity.serverTick(level, far, level.getBlockState(far), loose);
		}
		helper.assertTrue(loose.progress(0) == 0 && loose.getItem(0).is(Items.ROTTEN_FLESH), "tina fora do curtume nao cura");

		// --- pedreira: o bau enche com o tempo
		VillageLayout.Lot quarry = lot(v, "quarry");
		BlockPos chestPos = blocksOf(server, v, quarry, "minecraft:chest").getFirst();
		Container chest = (Container) level.getBlockEntity(chestPos);
		VillageData data = VillageData.get(server);
		CraftsServer.tick(server, data, 100);
		CraftsServer.tick(server, data, 100 + 24000);
		int stones = count(chest, Items.COBBLESTONE) + count(chest, Items.STONE) + count(chest, Items.STONE_BRICKS);
		helper.assertTrue(stones >= CraftsRules.quarryPerDay(1) - 2 && stones <= CraftsRules.quarryPerDay(1) + 2, "um dia enche " + CraftsRules.quarryPerDay(1) + ": " + stones);

		// --- encantador: so a mesa dele abre, e com o teto do nivel I
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos tablePos = blocksOf(server, v, lot(v, "enchanter"), "minecraft:enchanting_table").getFirst();
		InteractionResult opened = use(player, level, tablePos);
		helper.assertTrue(opened == InteractionResult.SUCCESS && player.containerMenu instanceof SelectiveEnchantMenu, "a mesa do Encantador abre o menu");
		SelectiveEnchantMenu menu = (SelectiveEnchantMenu) player.containerMenu;
		helper.assertTrue(EnchantRules.miningCap(menu.miningLevel()) == 1, "Encantador I: so nivel I de encantamento");
		player.closeContainer();
		BlockPos loneTable = new BlockPos(v.x() - 6_000, far.getY(), v.z() - 6_000);
		level.getChunk(loneTable.getX() >> 4, loneTable.getZ() >> 4);
		level.setBlock(loneTable, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
		use(player, level, loneTable);
		helper.assertFalse(player.containerMenu instanceof SelectiveEnchantMenu, "mesa fora do Encantador nao abre nada");

		// --- livro do mercado: nivel I com o Encantador I
		ServerPlayer buyer = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		buyer.getInventory().add(new ItemStack(Items.EMERALD, 64));
		helper.assertTrue(VillageServer.buy(buyer, v, "book"), "comprou o livro");
		for (int slot = 0; slot < buyer.getInventory().getContainerSize(); slot++) {
			ItemStack s = buyer.getInventory().getItem(slot);
			if (s.is(Items.ENCHANTED_BOOK)) {
				var stored = s.get(DataComponents.STORED_ENCHANTMENTS);
				helper.assertTrue(stored != null && stored.keySet().stream().allMatch(e -> stored.getLevel(e) <= 1), "livro de nivel I");
			}
		}

		// --- tavern: missao extra no dia seguinte, limite de boatos, sem esmeraldas nada acontece
		v.refreshBounties(VillageServer.day(server) + 1);
		helper.assertTrue(v.bounties().size() == 4, "taverna acrescenta uma missao: " + v.bounties().size());
		VillageShop.Offer rumor = VillageShop.byId("rumor_ruin");
		ServerPlayer poor = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		helper.assertFalse(CraftsServer.buyRumor(poor, v, rumor), "sem esmeraldas nao compra boato");
		buyer.setAttached(CraftsServer.RUMORS_TODAY, VillageServer.day(server) * 10 + 1);
		int before = count(buyer.getInventory(), Items.EMERALD);
		helper.assertFalse(CraftsServer.buyRumor(buyer, v, rumor), "taverna I: so um boato por dia");
		helper.assertTrue(count(buyer.getInventory(), Items.EMERALD) == before, "nada cobrado");
		helper.assertTrue(VillageShop.price(VillageShop.byId("backpack"), v) == 18 && VillageShop.available(VillageShop.byId("backpack"), v), "mochila no alfaiate I");

		// --- igreja: o altar abencoa uma vez por dia
		BlockPos altar = blocksOf(server, v, lot(v, "church"), "minecraft:brewing_stand").getFirst();
		helper.assertTrue(use(player, level, altar) == InteractionResult.SUCCESS && player.hasEffect(MobEffects.REGENERATION), "o altar abencoa");
		player.removeAllEffects();
		use(player, level, altar);
		helper.assertFalse(player.hasEffect(MobEffects.REGENERATION), "so uma bencao por dia");
		BlockPos strayStand = new BlockPos(v.x() - 7_000, far.getY(), v.z() - 7_000);
		level.getChunk(strayStand.getX() >> 4, strayStand.getZ() >> 4);
		level.setBlock(strayStand, Blocks.BREWING_STAND.defaultBlockState(), 3);
		helper.assertTrue(use(player, level, strayStand) == InteractionResult.PASS, "suporte de pocao fora da igreja segue vanilla");

		// --- feira: mercadores itinerantes com raridades
		List<WanderingTrader> traders = CraftsServer.holdFair(server, level, v, 2);
		helper.assertTrue(traders.size() == 1 && !traders.getFirst().getOffers().isEmpty(), "feira II: 1 mercador com ofertas");
		traders.forEach(t -> t.discard());

		// --- lote vazio com cama e porta conta como casa
		VillageLayout.Lot empty = lot(v, "empty_lot");
		int before2 = v.playerHouses();
		BlockPos in = new BlockPos(v.x() + empty.x() + 2, ground(level, v.x(), v.z()).getY(), v.z() + empty.z() + 2);
		level.setBlock(in, Blocks.OAK_DOOR.defaultBlockState(), 3);
		level.setBlock(in.east(2), Blocks.BED.red().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH), 3);
		VillageTerrain.refresh(level, v);
		helper.assertTrue(v.playerHouses() == before2 + 1, "o lote vazio com cama e porta conta como casa: " + before2 + " -> " + v.playerHouses());

		release(helper, v);
		helper.succeed();
	}

	private static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
	}

	// ---------------------------------------------------------------- receitas

	@GameTest(environment = ENV)
	public void enchantingTableHasNoRecipeAndRottenFleshNoLongerSmelts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean table = false;
		boolean flesh = false;
		for (var holder : level.getServer().getRecipeManager().getRecipes()) {
			String id = holder.id().identifier().toString();
			table |= id.equals("minecraft:enchanting_table") && !(holder.value() instanceof com.mlin.notas.skills.SkillGatedRecipe gated && gated.requiredLevel() >= 99);
			flesh |= id.contains("rotten_flesh") && id.contains("leather");
		}
		helper.assertFalse(table, "a mesa de encantamento nao tem mais receita de mesa de trabalho");
		helper.assertFalse(flesh, "carne podre nao funde mais em couro");
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void everyCraftsProjectIsRegisteredWithCostsAndLevels(GameTestHelper helper) {
		for (VillageProject p : List.of(VillageProject.QUARRY, VillageProject.TAILOR, VillageProject.TANNERY, VillageProject.FLETCHER,
				VillageProject.ENCHANTER, VillageProject.TAVERN, VillageProject.GARDEN, VillageProject.CHURCH, VillageProject.EMPTY_LOT)) {
			for (VillageCost cost : p.costs()) {
				Item item = representative(cost.id());
				helper.assertTrue(item != Items.AIR, p + ": custo desconhecido " + cost.id());
			}
			helper.assertTrue(BuildingTypes.of(p.id()) != null && VillageProject.byId(p.id()) == p, p + " registrado");
		}
		helper.assertTrue(CraftsBuildings_ok(), "tipos");
		helper.succeed();
	}

	private static boolean CraftsBuildings_ok() {
		return BuildingTypes.maxLevel("enchanter") == 5 && BuildingTypes.workers("tannery") == 1 && BuildingTypes.workers("garden") == 0;
	}
}
