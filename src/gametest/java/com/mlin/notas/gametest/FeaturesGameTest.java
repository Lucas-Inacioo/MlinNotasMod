package com.mlin.notas.gametest;

import com.mlin.notas.furnace.FurnaceUpgrades;
import com.mlin.notas.tier.Tier;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.sorting.InventorySortServer;
import com.mlin.notas.tablet.CraftingTabletItem;
import com.mlin.notas.tree.TreeCapitator;
import com.mlin.notas.wand.BuildingWandItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;

/**
 * Game tests das features de jogo: arvore inteira, laco em aldeao, ordenacao, varinha, upgrade de fornalha e tablet.
 */
public class FeaturesGameTest {
	@GameTest
	public void treeCapitatorFellsWholeTree(GameTestHelper helper) {
		for (int y = 1; y <= 6; y++) {
			helper.setBlock(2, y, 2, Blocks.OAK_LOG);
		}
		helper.setBlock(2, 7, 2, Blocks.OAK_LEAVES);
		helper.setBlock(3, 6, 2, Blocks.OAK_LEAVES);

		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
		player.setItemSlot(EquipmentSlot.MAINHAND, axe);

		BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
		var brokenState = helper.getLevel().getBlockState(base);
		helper.getLevel().destroyBlock(base, true, player);
		int broken = TreeCapitator.fellTree(helper.getLevel(), player, base, brokenState, axe);

		helper.assertTrue(broken == 5, "deveria derrubar 5 troncos, derrubou " + broken);
		for (int y = 1; y <= 6; y++) {
			helper.assertBlockNotPresent(Blocks.OAK_LOG, 2, y, 2);
		}
		helper.assertTrue(axe.getDamageValue() == 5, "machado deveria perder 5 de durabilidade");
		helper.succeed();
	}

	@GameTest
	public void treeCapitatorIgnoresWoodWithoutLeaves(GameTestHelper helper) {
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(2, y, 2, Blocks.OAK_LOG);
		}
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
		BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
		var brokenState = helper.getLevel().getBlockState(base);
		helper.getLevel().destroyBlock(base, true, player);
		int broken = TreeCapitator.fellTree(helper.getLevel(), player, base, brokenState, axe);
		helper.assertTrue(broken == 0, "sem folhas nao e arvore");
		helper.assertBlockPresent(Blocks.OAK_LOG, 2, 2, 2);
		helper.succeed();
	}

	@GameTest
	public void villagerCanBeLeashed(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 1, 2));
		helper.assertTrue(villager.canBeLeashed(), "aldeao deveria aceitar laco");
		helper.succeed();
	}

	@GameTest
	public void sortPlayerInventoryMergesAndOrders(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		var inventory = player.getInventory();
		inventory.setItem(9, new ItemStack(Items.STONE, 30));
		inventory.setItem(12, new ItemStack(Items.APPLE, 3));
		inventory.setItem(20, new ItemStack(Items.STONE, 40));
		inventory.setItem(0, new ItemStack(Items.TORCH, 5)); // hotbar nao e tocada

		InventorySortServer.sortPlayerInventory(player);

		helper.assertTrue(inventory.getItem(9).is(Items.APPLE) && inventory.getItem(9).getCount() == 3, "apple primeiro");
		helper.assertTrue(inventory.getItem(10).is(Items.STONE) && inventory.getItem(10).getCount() == 64, "stone fundida em 64");
		helper.assertTrue(inventory.getItem(11).is(Items.STONE) && inventory.getItem(11).getCount() == 6, "resto da stone");
		helper.assertTrue(inventory.getItem(12).isEmpty(), "slot 12 deveria ficar vazio");
		helper.assertTrue(inventory.getItem(0).is(Items.TORCH), "hotbar intacta");
		helper.succeed();
	}

	@GameTest
	public void sortStacksKeepsComponentsSeparate(GameTestHelper helper) {
		ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
		named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Excalibur"));
		List<ItemStack> sorted = InventorySortServer.sortStacks(List.of(named, new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY));
		helper.assertTrue(sorted.get(0).is(Items.DIAMOND_SWORD) && !sorted.get(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME), "espada sem nome primeiro");
		helper.assertTrue(sorted.get(1).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME), "espada nomeada mantida");
		helper.assertTrue(sorted.get(2).isEmpty(), "ultimo vazio");
		helper.succeed();
	}

	@GameTest
	public void buildingWandExtendsFloor(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(x, 1, z, Blocks.STONE);
			}
		}
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(9, new ItemStack(Items.STONE, 64));
		ItemStack wand = new ItemStack(ModItems.WOODEN_WAND);
		player.setItemSlot(EquipmentSlot.MAINHAND, wand);

		int placed = ((BuildingWandItem) ModItems.WOODEN_WAND).extend(helper.getLevel(), player,
				helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, wand, EquipmentSlot.MAINHAND);

		helper.assertTrue(placed == 9, "deveria colocar 9 blocos, colocou " + placed);
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.assertBlockPresent(Blocks.STONE, x, 2, z);
			}
		}
		helper.assertTrue(player.getInventory().getItem(9).getCount() == 55, "deveria consumir 9 pedras");
		helper.assertTrue(wand.getDamageValue() == 1, "varinha perde 1 de durabilidade por uso");
		helper.succeed();
	}

	@GameTest
	public void buildingWandStopsWhenOutOfBlocks(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			helper.setBlock(x, 1, 1, Blocks.COBBLESTONE);
		}
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 2));
		ItemStack wand = new ItemStack(ModItems.STONE_WAND);
		int placed = ((BuildingWandItem) ModItems.STONE_WAND).extend(helper.getLevel(), player,
				helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP, wand, EquipmentSlot.MAINHAND);
		helper.assertTrue(placed == 2, "so tinha 2 blocos, colocou " + placed);
		helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "blocos consumidos");
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest(maxTicks = 300)
	public void furnaceUpgradeSmeltsFaster(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.FURNACE);
		helper.setBlock(3, 1, 1, Blocks.FURNACE);
		AbstractFurnaceBlockEntity slow = helper.getBlockEntity(new BlockPos(1, 1, 1), AbstractFurnaceBlockEntity.class);
		AbstractFurnaceBlockEntity fast = helper.getBlockEntity(new BlockPos(3, 1, 1), AbstractFurnaceBlockEntity.class);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack upgrade = new ItemStack(ModItems.FURNACE_UPGRADE_DIAMOND);
		boolean applied = FurnaceUpgrades.apply(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1)), player, upgrade);
		helper.assertTrue(applied, "upgrade deveria aplicar");
		// jogador mock in-level e criativo: o upgrade nao e consumido
		helper.assertTrue(FurnaceUpgrades.tierOf(fast) == Tier.DIAMOND, "nivel diamante");

		for (AbstractFurnaceBlockEntity furnace : List.of(slow, fast)) {
			furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
			furnace.setItem(1, new ItemStack(Items.COAL, 8));
		}

		// Normal: 200 ticks por item. Diamante (6x): ~34 ticks. Em 120 ticks a rapida deve ter >= 2 e a lenta 0.
		helper.runAfterDelay(120, () -> {
			int fastCount = fast.getItem(2).getCount();
			int slowCount = slow.getItem(2).getCount();
			helper.assertTrue(fastCount >= 2, "fornalha com upgrade deveria ter >= 2 lingotes, tem " + fastCount);
			helper.assertTrue(slowCount == 0, "fornalha normal nao deveria ter terminado, tem " + slowCount);
			helper.getLevel().getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}

	@SuppressWarnings("removal")
	@GameTest
	public void furnaceUpgradeRejectsLowerTier(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.BLAST_FURNACE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.assertTrue(FurnaceUpgrades.apply(helper.getLevel(), pos, player, new ItemStack(ModItems.FURNACE_UPGRADE_DIAMOND)), "diamante aplica");
		helper.assertTrue(!FurnaceUpgrades.apply(helper.getLevel(), pos, player, new ItemStack(ModItems.FURNACE_UPGRADE_IRON)), "ferro nao substitui diamante");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void craftingTabletOpensCraftingMenu(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			helper.assertTrue(!CraftingTabletItem.hasTablet(player), "sem tablet no inicio");
			player.getInventory().setItem(20, new ItemStack(ModItems.CRAFTING_TABLET));
			helper.assertTrue(CraftingTabletItem.hasTablet(player), "tablet detectado no inventario");
			CraftingTabletItem.open(player);
			helper.assertTrue(player.containerMenu instanceof CraftingMenu, "menu de crafting aberto");
			helper.assertTrue(player.containerMenu.stillValid(player), "menu continua valido sem mesa no mundo");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}
}
