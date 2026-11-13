package com.mlin.notas.gametest;

import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.client.waila.WailaClient;
import com.mlin.notas.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/**
 * Game test de cliente do HUD WAILA: bau cheio mirado (grade de conteudo vinda do servidor), lobo domado
 * (vida e dono) e tooltip com a grade de uma caixa de shulker e de uma mochila no inventario.
 */
@SuppressWarnings("UnstableApiUsage")
public class WailaClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("gamerule advance_time false");

			// Bau cheio 3 blocos a frente
			BlockPos chestPos = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(0, 0, 3);
				player.level().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
				ChestBlockEntity chest = (ChestBlockEntity) player.level().getBlockEntity(pos);
				ItemStack[] items = {new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIAMOND, 12),
						new ItemStack(Items.IRON_INGOT, 40), new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.BREAD, 7),
						new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.REDSTONE, 50), new ItemStack(Items.EMERALD, 3),
						new ItemStack(Items.GOLD_INGOT, 9), new ItemStack(ModItems.COBALT_INGOT, 5), new ItemStack(Items.TORCH, 64)};
				for (int i = 0; i < items.length; i++) {
					chest.setItem(i * 2, items[i]);
				}
				return pos;
			});
			context.getInput().lookAt(chestPos);
			context.waitFor(mc -> WailaClient.contentsFor(chestPos) != null, 100);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-waila-chest");

			// Minerio de diamante com a mao vazia: exige picareta de cobalto
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
					chest.clearContent();
				}
				level.setBlockAndUpdate(chestPos, Blocks.DIAMOND_ORE.defaultBlockState());
			});
			context.waitTicks(5);
			context.getInput().lookAt(chestPos);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-waila-ore");

			// Lobo domado no lugar do minerio
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				level.setBlockAndUpdate(chestPos, Blocks.AIR.defaultBlockState());
				Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
				wolf.snapTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 180.0F, 0.0F);
				wolf.tame(player);
				wolf.setNoAi(true);
				wolf.setHealth(13.0F);
				level.addFreshEntity(wolf);
			});
			context.waitTicks(5);
			context.getInput().lookAt(chestPos);
			context.waitFor(mc -> mc.hitResult instanceof EntityHitResult, 100);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-waila-mob");

			// Tooltip com grade: caixa de shulker e mochila no inventario
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
				shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64),
						new ItemStack(Items.DIAMOND, 30), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.APPLE, 5),
						new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.GOLDEN_APPLE, 2))));
				player.getInventory().setItem(9, shulker);
				ItemStack backpack = new ItemStack(ModItems.BACKPACK);
				backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.COAL, 64),
						new ItemStack(Items.RAW_IRON, 20), new ItemStack(Items.WHEAT_SEEDS, 12))));
				player.getInventory().setItem(10, backpack);
			});
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			hoverSlot(context, 9);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-waila-shulker-tooltip");
			hoverSlot(context, 10);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-waila-backpack-tooltip");
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(null);
		}
	}

	/** Poe o cursor sobre o slot do inventario do jogador (indice do inventario 9..35 = slot do menu igual). */
	private static void hoverSlot(ClientGameTestContext context, int slotIndex) {
		double[] pos = context.computeOnClient(mc -> {
			InventoryScreen screen = (InventoryScreen) mc.gui.screen();
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
			Slot slot = screen.getMenu().slots.get(slotIndex);
			int scale = Minecraft.getInstance().getWindow().getGuiScale();
			return new double[] {(accessor.mlinnotas$leftPos() + slot.x + 8) * scale, (accessor.mlinnotas$topPos() + slot.y + 8) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}
}
