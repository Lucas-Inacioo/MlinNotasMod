package com.mlin.notas.gametest;

import com.mlin.notas.backpack.BackpackItem;
import com.mlin.notas.backpack.BackpackMenu;
import com.mlin.notas.backpack.BackpackSmelter;
import com.mlin.notas.backpack.BackpackUpgradeKind;
import com.mlin.notas.backpack.BackpackUpgrades;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.sorting.InventorySortServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/** Game tests da mochila: persistencia do conteudo, upgrades, crafting, fundicao e ordenacao. */
public class BackpackGameTest {
	private static NonNullList<ItemStack> contents(ItemStack backpack) {
		NonNullList<ItemStack> list = NonNullList.withSize(BackpackItem.upgrades(backpack).slotCount(), ItemStack.EMPTY);
		backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(list);
		return list;
	}

	@SuppressWarnings("removal")
	@GameTest
	public void contentsPersistAcrossOpenClose(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.getInventory().setItem(3, new ItemStack(ModItems.BACKPACK));
			helper.assertTrue(BackpackItem.findBackpackSlot(player) == 3, "mochila encontrada no slot 3");
			BackpackItem.open(player, 3);
			helper.assertTrue(player.containerMenu instanceof BackpackMenu, "menu da mochila aberto");
			BackpackMenu menu = (BackpackMenu) player.containerMenu;
			helper.assertTrue(menu.contentSlotCount() == 27, "27 slots sem upgrade");
			menu.getContainer().setItem(5, new ItemStack(Items.DIAMOND, 4));
			menu.getContainer().setItem(26, new ItemStack(Items.BREAD, 7));
			player.closeContainer();

			ItemStack backpack = player.getInventory().getItem(3);
			NonNullList<ItemStack> items = contents(backpack);
			helper.assertTrue(items.get(5).is(Items.DIAMOND) && items.get(5).getCount() == 4, "diamantes gravados no item");
			helper.assertTrue(items.get(26).is(Items.BREAD), "pao gravado no item");

			BackpackItem.open(player, 3);
			BackpackMenu reopened = (BackpackMenu) player.containerMenu;
			helper.assertTrue(reopened.getContainer().getItem(5).is(Items.DIAMOND), "conteudo reaparece ao reabrir");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void lockedSlotAndNoBackpackInsideBackpack(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.getInventory().setItem(0, new ItemStack(ModItems.BACKPACK));
			BackpackItem.open(player, 0);
			BackpackMenu menu = (BackpackMenu) player.containerMenu;
			Slot locked = null;
			for (Slot slot : menu.slots) {
				if (slot.container == player.getInventory() && slot.getContainerSlot() == 0) {
					locked = slot;
				}
			}
			helper.assertTrue(locked != null && !locked.mayPickup(player), "slot da mochila aberta travado");
			helper.assertTrue(!menu.slots.get(0).mayPlace(new ItemStack(ModItems.BACKPACK)), "mochila nao entra em mochila");
			helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(Items.STONE)), "outros itens entram");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void sizeUpgradeKeepsItemsAndCraftingAddsGrid(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			ItemStack backpack = new ItemStack(ModItems.BACKPACK);
			player.getInventory().setItem(1, backpack);
			BackpackItem.open(player, 1);
			((BackpackMenu) player.containerMenu).getContainer().setItem(20, new ItemStack(Items.IRON_INGOT, 12));
			player.closeContainer();

			ItemStack inInventory = player.getInventory().getItem(1);
			helper.assertTrue(BackpackItem.applyUpgrade(inInventory, BackpackUpgradeKind.SIZE), "tamanho aplica");
			helper.assertTrue(BackpackItem.applyUpgrade(inInventory, BackpackUpgradeKind.CRAFTING), "crafting aplica");
			helper.assertTrue(!BackpackItem.applyUpgrade(inInventory, BackpackUpgradeKind.CRAFTING), "crafting nao aplica duas vezes");
			BackpackUpgrades upgrades = BackpackItem.upgrades(inInventory);
			helper.assertTrue(upgrades.slotCount() == 54, "54 slots");

			BackpackItem.open(player, 1);
			BackpackMenu menu = (BackpackMenu) player.containerMenu;
			helper.assertTrue(menu.contentSlotCount() == 54, "menu com 54 slots");
			helper.assertTrue(menu.hasCrafting(), "grade de crafting presente");
			helper.assertTrue(menu.slots.size() == 54 + 36 + 9 + 1, "54 conteudo + 36 jogador + 9 grade + resultado");
			helper.assertTrue(menu.getContainer().getItem(20).is(Items.IRON_INGOT), "ferro preservado apos upgrade de tamanho");

			// coloca 4 tabuas na grade e espera o resultado (mesa de trabalho)
			int gridStart = 54 + 36;
			menu.slots.get(gridStart).set(new ItemStack(Items.OAK_PLANKS));
			menu.slots.get(gridStart + 1).set(new ItemStack(Items.OAK_PLANKS));
			menu.slots.get(gridStart + 3).set(new ItemStack(Items.OAK_PLANKS));
			menu.slots.get(gridStart + 4).set(new ItemStack(Items.OAK_PLANKS));
			menu.slotsChanged(menu.slots.get(gridStart).container);
			helper.assertTrue(menu.slots.get(gridStart + 9).getItem().is(Items.CRAFTING_TABLE), "resultado da receita calculado");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void furnaceUpgradeSmeltsRawIron(GameTestHelper helper) {
		ItemStack backpack = new ItemStack(ModItems.BACKPACK);
		BackpackItem.applyUpgrade(backpack, BackpackUpgradeKind.FURNACE);
		NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
		items.set(0, new ItemStack(Items.RAW_IRON, 2));
		items.set(1, new ItemStack(Items.COAL, 1));
		backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));

		helper.assertTrue(BackpackSmelter.burnTime(helper.getLevel(), new ItemStack(Items.COAL)) == 1600, "carvao queima 1600 ticks");
		for (int i = 0; i < 210; i++) {
			BackpackSmelter.tick(backpack, helper.getLevel(), true);
		}
		NonNullList<ItemStack> after = contents(backpack);
		boolean ingot = after.stream().anyMatch(s -> s.is(Items.IRON_INGOT) && s.getCount() == 1);
		boolean rawLeft = after.stream().anyMatch(s -> s.is(Items.RAW_IRON) && s.getCount() == 1);
		boolean coalGone = after.stream().noneMatch(s -> s.is(Items.COAL));
		helper.assertTrue(ingot, "1 lingote produzido depois de 200 ticks");
		helper.assertTrue(rawLeft, "sobrou 1 ferro cru");
		helper.assertTrue(coalGone, "carvao consumido");
		helper.assertTrue(BackpackItem.upgrades(backpack).smelting().isLit(), "ainda acesa (carvao dura 1600 ticks)");

		for (int i = 0; i < 200; i++) {
			BackpackSmelter.tick(backpack, helper.getLevel(), true);
		}
		after = contents(backpack);
		boolean two = after.stream().anyMatch(s -> s.is(Items.IRON_INGOT) && s.getCount() == 2);
		helper.assertTrue(two, "segundo lingote fundido na mesma pilha");
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void sortButtonSortsBackpackContents(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.getInventory().setItem(2, new ItemStack(ModItems.BACKPACK));
			BackpackItem.open(player, 2);
			BackpackMenu menu = (BackpackMenu) player.containerMenu;
			menu.getContainer().setItem(0, new ItemStack(Items.STONE, 10));
			menu.getContainer().setItem(10, new ItemStack(Items.APPLE, 1));
			menu.getContainer().setItem(20, new ItemStack(Items.STONE, 10));
			InventorySortServer.sortOpenContainer(player);
			helper.assertTrue(menu.getContainer().getItem(0).is(Items.APPLE), "apple primeiro");
			helper.assertTrue(menu.getContainer().getItem(1).is(Items.STONE) && menu.getContainer().getItem(1).getCount() == 20, "stone fundida");
			helper.assertTrue(menu.getContainer().getItem(20).isEmpty(), "slot 20 vazio");
			player.closeContainer();
			List<ItemStack> saved = contents(player.getInventory().getItem(2));
			helper.assertTrue(saved.get(0).is(Items.APPLE), "ordenacao persistida no item");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}
}
