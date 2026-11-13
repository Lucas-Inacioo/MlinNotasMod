package com.mlin.notas.gametest;

import com.mlin.notas.chest.BigChestBlockEntity;
import com.mlin.notas.chest.BigChestMenu;
import com.mlin.notas.chest.ChestTier;
import com.mlin.notas.chest.ChestUpgrades;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.sorting.InventorySortServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Game tests dos baus grandes: upgrades, conteudo, menu e ordenacao. */
public class ChestGameTest {
	@GameTest
	public void ironChestHolds54Slots(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, ModBlocks.IRON_CHEST);
		BigChestBlockEntity chest = helper.getBlockEntity(new BlockPos(1, 1, 1), BigChestBlockEntity.class);
		helper.assertTrue(chest.getContainerSize() == 54, "iron chest deveria ter 54 slots");
		helper.assertTrue(chest.tier() == ChestTier.IRON, "tier ferro");
		chest.setItem(53, new ItemStack(Items.DIAMOND, 3));
		helper.assertTrue(chest.getItem(53).getCount() == 3, "ultimo slot guarda item");
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void upgradeVanillaChestKeepsContentsNameAndFacing(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		ChestBlockEntity vanilla = helper.getBlockEntity(new BlockPos(1, 1, 1), ChestBlockEntity.class);
		vanilla.setItem(0, new ItemStack(Items.APPLE, 5));
		vanilla.setItem(26, new ItemStack(Items.STONE, 64));

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack upgrade = new ItemStack(ModItems.CHEST_UPGRADE_IRON);
		boolean applied = ChestUpgrades.apply(helper.getLevel(), pos, player, upgrade);
		helper.assertTrue(applied, "upgrade de ferro deveria aplicar no bau vanilla");
		helper.assertBlockPresent(ModBlocks.IRON_CHEST, 1, 1, 1);
		helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).getValue(ChestBlock.FACING) == Direction.EAST, "facing preservado");
		BigChestBlockEntity big = helper.getBlockEntity(new BlockPos(1, 1, 1), BigChestBlockEntity.class);
		helper.assertTrue(big.getItem(0).is(Items.APPLE) && big.getItem(0).getCount() == 5, "apple preservada no slot 0");
		helper.assertTrue(big.getItem(26).is(Items.STONE) && big.getItem(26).getCount() == 64, "stone preservada no slot 26");
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
		helper.assertTrue(dropped.isEmpty(), "nada deveria cair no chao ao converter");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void upgradeChainIronToGoldToDiamond(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, ModBlocks.IRON_CHEST);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		BigChestBlockEntity iron = helper.getBlockEntity(new BlockPos(1, 1, 1), BigChestBlockEntity.class);
		iron.setItem(53, new ItemStack(Items.GOLD_INGOT, 7));
		iron.setCustomName(Component.literal("Tesouro"));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		helper.assertTrue(!ChestUpgrades.apply(helper.getLevel(), pos, player, new ItemStack(ModItems.CHEST_UPGRADE_DIAMOND)), "diamante nao aplica em ferro");
		helper.assertTrue(ChestUpgrades.apply(helper.getLevel(), pos, player, new ItemStack(ModItems.CHEST_UPGRADE_GOLD)), "ouro aplica em ferro");
		helper.assertBlockPresent(ModBlocks.GOLD_CHEST, 1, 1, 1);
		BigChestBlockEntity gold = helper.getBlockEntity(new BlockPos(1, 1, 1), BigChestBlockEntity.class);
		helper.assertTrue(gold.getContainerSize() == 81, "81 slots");
		helper.assertTrue(gold.getItem(53).is(Items.GOLD_INGOT), "item preservado na mesma posicao");
		helper.assertTrue(gold.getCustomName() != null && gold.getCustomName().getString().equals("Tesouro"), "nome preservado");

		helper.assertTrue(ChestUpgrades.apply(helper.getLevel(), pos, player, new ItemStack(ModItems.CHEST_UPGRADE_DIAMOND)), "diamante aplica em ouro");
		helper.assertBlockPresent(ModBlocks.DIAMOND_CHEST, 1, 1, 1);
		BigChestBlockEntity diamond = helper.getBlockEntity(new BlockPos(1, 1, 1), BigChestBlockEntity.class);
		helper.assertTrue(diamond.getContainerSize() == 108, "108 slots");
		helper.assertTrue(diamond.getItem(53).is(Items.GOLD_INGOT), "item ainda la");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest
	public void breakingChestDropsContents(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, ModBlocks.GOLD_CHEST);
		BigChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 1, 2), BigChestBlockEntity.class);
		chest.setItem(10, new ItemStack(Items.EMERALD, 9));
		BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.getLevel().destroyBlock(pos, true);
		helper.runAfterDelay(2, () -> {
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
			boolean emerald = dropped.stream().anyMatch(e -> e.getItem().is(Items.EMERALD) && e.getItem().getCount() == 9);
			boolean block = dropped.stream().anyMatch(e -> e.getItem().is(ModItems.GOLD_CHEST));
			helper.assertTrue(emerald, "esmeraldas deveriam cair");
			helper.assertTrue(block, "o proprio bau deveria cair (loot table)");
			helper.succeed();
		});
	}

	@SuppressWarnings("removal")
	@GameTest
	public void menuOpensWithRightSlotCountAndSorts(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, ModBlocks.DIAMOND_CHEST);
		BigChestBlockEntity chest = helper.getBlockEntity(new BlockPos(1, 1, 1), BigChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.STONE, 30));
		chest.setItem(50, new ItemStack(Items.APPLE, 2));
		chest.setItem(107, new ItemStack(Items.STONE, 40));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.openMenu(chest);
			helper.assertTrue(player.containerMenu instanceof BigChestMenu, "menu do bau grande aberto");
			BigChestMenu menu = (BigChestMenu) player.containerMenu;
			helper.assertTrue(menu.containerSlotCount() == 108, "108 slots de container");
			helper.assertTrue(menu.slots.size() == 108 + 36, "slots do container + 36 do jogador");

			InventorySortServer.sortOpenContainer(player);
			helper.assertTrue(chest.getItem(0).is(Items.APPLE) && chest.getItem(0).getCount() == 2, "apple primeiro apos ordenar");
			helper.assertTrue(chest.getItem(1).is(Items.STONE) && chest.getItem(1).getCount() == 64, "stone fundida");
			helper.assertTrue(chest.getItem(2).is(Items.STONE) && chest.getItem(2).getCount() == 6, "resto da stone");
			helper.assertTrue(chest.getItem(107).isEmpty(), "ultimo slot vazio apos ordenar");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void openingAndClosingTracksViewersForLidAnimation(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, ModBlocks.GOLD_CHEST);
		BigChestBlockEntity chest = helper.getBlockEntity(new BlockPos(1, 1, 1), BigChestBlockEntity.class);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			helper.assertTrue(chest.openerCount() == 0, "ninguem com o bau aberto no inicio");
			player.openMenu(chest);
			helper.assertTrue(chest.openerCount() == 1, "abrir conta 1 espectador (tampa abre)");
			player.closeContainer();
			helper.assertTrue(chest.openerCount() == 0, "fechar zera os espectadores (tampa fecha)");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}
}
