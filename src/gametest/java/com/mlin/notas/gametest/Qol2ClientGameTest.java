package com.mlin.notas.gametest;

import com.mlin.notas.chest.BigChestBlockEntity;
import com.mlin.notas.chest.ChestTier;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.minimap.MinimapClient;
import com.mlin.notas.client.screen.BigChestScreen;
import com.mlin.notas.registry.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Game test de cliente do lote de qualidade de vida 2: texto embaixo do minimapa em varios tamanhos e escalas
 * (screenshots {@code mlinnotas-qol2-minimap-<tamanho>-gui<escala>}) e o botao/tecla de guardar rapido nas telas de bau.
 */
@SuppressWarnings("UnstableApiUsage")
public class Qol2ClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			// coordenadas longas (negativas, 5 digitos) para o pior caso de largura; chao do mundo plano
			sp.getServer().runCommand("tp @p -12345 -60 -23456");
			sp.getServer().runCommand("time set noon");
			context.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			// bioma de nome comprido para ver a quebra de linha
			sp.getServer().runCommand("fillbiome -12360 -64 -23470 -12330 -40 -23440 minecraft:old_growth_spruce_taiga");
			context.waitTicks(10);

			// escala 2 na janela padrao (854x480); escala 4 precisa de uma janela maior (senao o jogo limita a 2)
			int[][] cases = {{2, 854, 480}, {4, 1920, 1080}};
			for (int[] c : cases) {
				final int scale = c[0];
				context.getInput().resizeWindow(c[1], c[2]);
				context.waitTicks(2);
				context.runOnClient(mc -> {
					mc.options.guiScale().set(scale);
					mc.resizeGui();
				});
				context.waitTicks(2);
				int actual = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
				if (actual != scale) {
					throw new IllegalStateException("escala da GUI deveria ser " + scale + ", e " + actual);
				}
				for (int size : new int[] {48, 96, 192}) {
					final int s = size;
					context.runOnClient(mc -> {
						MinimapClient.options().size = s;
						mc.gui.toastManager().clear();
					});
					context.waitTicks(5);
					context.takeScreenshot("mlinnotas-qol2-minimap-" + size + "-gui" + scale);
				}
			}
			context.runOnClient(mc -> {
				MinimapClient.options().size = 96;
				mc.options.guiScale().set(0);
			});
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(2);
			context.runOnClient(Minecraft::resizeGui);
			context.waitTicks(2);

			// guardar rapido: bau vanilla com pedregulho; o jogador tem pedregulho e areia no inventario principal
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(0, 0, 2);
				player.level().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
				ChestBlockEntity chest = (ChestBlockEntity) player.level().getBlockEntity(pos);
				chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
				player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 40));
				player.getInventory().setItem(10, new ItemStack(Items.SAND, 16));
				player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
				player.openMenu(chest);
			});
			context.waitForScreen(ContainerScreen.class);
			context.waitFor(mc -> quickStackButton(mc.gui.screen()) != null, 40);
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			hoverQuickStack(context);
			context.takeScreenshot("mlinnotas-qol2-quickstack-chest");
			context.clickScreenButton("button.mlinnotas.quick_stack");
			context.waitTicks(5);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			context.takeScreenshot("mlinnotas-qol2-quickstack-chest-after");
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (!player.getInventory().getItem(9).isEmpty() || !player.getInventory().getItem(10).is(Items.SAND)
						|| player.getInventory().getItem(0).getCount() != 64) {
					throw new IllegalStateException("guardar rapido deveria levar so o pedregulho do inventario principal");
				}
			});
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitForScreen(null);
			context.waitTicks(5); // o servidor processa o fechamento antes de abrir o proximo bau

			// tecla X num bau grande do mod
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(2, 0, 2);
				player.level().setBlockAndUpdate(pos, ModBlocks.chest(ChestTier.IRON).defaultBlockState());
				BigChestBlockEntity chest = (BigChestBlockEntity) player.level().getBlockEntity(pos);
				chest.setItem(30, new ItemStack(Items.SAND, 1));
				player.openMenu(chest);
			});
			context.waitForScreen(BigChestScreen.class);
			context.waitFor(mc -> quickStackButton(mc.gui.screen()) != null, 40);
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.getInput().pressKey(ModKeys.QUICK_STACK);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-qol2-quickstack-bigchest");
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (!player.getInventory().getItem(10).isEmpty()) {
					throw new IllegalStateException("tecla de guardar rapido deveria levar a areia ao bau grande; menu "
							+ player.containerMenu + ", slot 10 = " + player.getInventory().getItem(10));
				}
			});
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitForScreen(null);
		}
	}

	private static AbstractWidget quickStackButton(Screen screen) {
		if (screen == null) {
			return null;
		}
		for (var widget : Screens.getWidgets(screen)) {
			if (widget instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t
					&& t.getKey().equals("button.mlinnotas.quick_stack")) {
				return w;
			}
		}
		return null;
	}

	/** Poe o mouse sobre o botao de guardar rapido (para a dica aparecer no screenshot). */
	private static void hoverQuickStack(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(mc -> {
			AbstractWidget widget = quickStackButton(mc.gui.screen());
			double scale = mc.getWindow().getGuiScale();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(15);
	}
}
