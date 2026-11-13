package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.storage.TerminalScreen;
import com.mlin.notas.storage.ItemKey;
import com.mlin.notas.storage.StorageCableBlock;
import com.mlin.notas.storage.StorageRegistry;
import com.mlin.notas.storage.StorageTerminalBlock;
import com.mlin.notas.storage.StorageTier;
import com.mlin.notas.storage.StorageUnitBlock;
import com.mlin.notas.storage.StorageUnitBlockEntity;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.minecraft.world.level.block.Blocks;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Game test de cliente da rede de armazenamento: monta uma sala com as 10 unidades (uma por nivel), cabos, baus e os
 * dois terminais; enche a rede com centenas de tipos e fotografa os blocos, a tela do terminal (lista cheia, busca
 * {@code @mod}, ordem por nome, escala 3 em 1280x720), o terminal de crafting e o HUD WAILA mirando uma unidade.
 * Tambem clica de verdade na lista e confere que a pilha veio para o cursor.
 */
@SuppressWarnings("UnstableApiUsage")
public class StorageClientGameTest implements FabricClientGameTest {
	private record Room(BlockPos origin, BlockPos terminal, BlockPos craftingTerminal, BlockPos celestium) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule advance_time false");
			Room room = sp.getServer().computeOnServer(StorageClientGameTest::build);
			MlinNotas.LOGGER.info("[StorageClientGameTest] sala em {}", room.origin());

			hideHud(context, true);
			BlockPos o = room.origin();
			shot(context, sp, o, new double[] {-6.5, 3.5, -4.5}, new double[] {0, 1, 5}, "mlinnotas-storage-room");
			shot(context, sp, o, new double[] {6.5, 2.2, 1.5}, new double[] {4, 1, 5}, "mlinnotas-storage-terminals");
			shot(context, sp, o, new double[] {-1.5, 1.8, 2.0}, new double[] {-1.5, 0.5, 5}, "mlinnotas-storage-units-close");

			// terminal: lista cheia
			openTerminal(context, sp, room.terminal(), false);
			int shown = context.computeOnClient(mc -> ((TerminalScreen) mc.gui.screen()).visibleEntries());
			MlinNotas.LOGGER.info("[StorageClientGameTest] terminal mostra {} tipos", shown);
			if (shown < 300) {
				throw new IllegalStateException("terminal deveria listar centenas de tipos, mostra " + shown);
			}
			hover(context, 10);
			context.takeScreenshot("mlinnotas-storage-terminal");

			// clique de verdade: a pilha vem para o cursor e volta ao clicar de novo
			ItemStack target = context.computeOnClient(mc -> ((TerminalScreen) mc.gui.screen()).stackAt(0).copy());
			click(context, 0, InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(mc -> ItemStack.isSameItemSameComponents(mc.player.containerMenu.getCarried(), target), 40);
			hover(context, 20);
			context.takeScreenshot("mlinnotas-storage-terminal-carried");
			click(context, 20, InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(mc -> mc.player.containerMenu.getCarried().isEmpty(), 40);

			// busca por mod e ordem por nome
			context.runOnClient(mc -> ((TerminalScreen) mc.gui.screen()).searchBox().setValue("@mlinnotas"));
			context.waitTicks(3);
			int mod = context.computeOnClient(mc -> ((TerminalScreen) mc.gui.screen()).visibleEntries());
			if (mod <= 0 || mod >= shown) {
				throw new IllegalStateException("busca @mlinnotas deveria filtrar: " + mod + " de " + shown);
			}
			hover(context, 4);
			context.takeScreenshot("mlinnotas-storage-terminal-search-mod");
			context.runOnClient(mc -> ((TerminalScreen) mc.gui.screen()).searchBox().setValue("ingot"));
			// Qtd. -> Mod -> Nome
			context.clickScreenButton("gui.mlinnotas.storage.sort.count");
			context.waitTicks(2);
			context.clickScreenButton("gui.mlinnotas.storage.sort.mod");
			context.waitTicks(3);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			context.takeScreenshot("mlinnotas-storage-terminal-search-name");
			context.runOnClient(mc -> ((TerminalScreen) mc.gui.screen()).searchBox().setValue(""));
			context.runOnClient(mc -> ((TerminalScreen) mc.gui.screen()).scrollTo(12));
			context.waitTicks(2);
			context.takeScreenshot("mlinnotas-storage-terminal-scrolled");

			// 1280x720 com escala 3
			context.getInput().resizeWindow(1280, 720);
			context.waitTicks(2);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(3);
				mc.resizeGui();
			});
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-storage-terminal-scale3");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// terminal de crafting (escala 3 em 1280x720 e depois o tamanho padrao)
			openTerminal(context, sp, room.craftingTerminal(), true);
			context.takeScreenshot("mlinnotas-storage-crafting-scale3");
			context.runOnClient(mc -> mc.options.guiScale().set(0));
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(mc -> mc.resizeGui());
			context.waitTicks(5);
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (player.containerMenu instanceof com.mlin.notas.storage.TerminalMenu menu && menu.crafting()) {
					menu.slots.get(1).set(new ItemStack(Items.OAK_PLANKS));
					menu.slots.get(2).set(new ItemStack(Items.OAK_PLANKS));
					menu.slots.get(4).set(new ItemStack(Items.OAK_PLANKS));
					menu.slots.get(5).set(new ItemStack(Items.OAK_PLANKS));
					menu.broadcastChanges();
				}
			});
			context.waitFor(mc -> !mc.player.containerMenu.getSlot(0).getItem().isEmpty(), 40);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(3);
			context.takeScreenshot("mlinnotas-storage-crafting");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// HUD WAILA olhando a unidade de celestio
			hideHud(context, false);
			BlockPos c = room.celestium();
			look(context, sp, new double[] {c.getX() + 0.5, c.getY() + 0.0, c.getZ() - 2.2}, c, GameType.CREATIVE);
			context.waitTicks(30);
			context.takeScreenshot("mlinnotas-storage-waila");
		}
	}

	/** Sala de exposicao: 10 unidades em fila (frente para o norte), baus em cima, cabos e os terminais na ponta. */
	private static Room build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		BlockPos o = player.blockPosition();
		int z = o.getZ() + 5;
		// chao de pedra lisa e limpa o espaco
		for (int x = -9; x <= 9; x++) {
			for (int dz = -6; dz <= 4; dz++) {
				level.setBlock(new BlockPos(o.getX() + x, o.getY() - 1, z + dz), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
				for (int y = 0; y < 5; y++) {
					level.setBlock(new BlockPos(o.getX() + x, o.getY() + y, z + dz), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		StorageTier[] tiers = StorageTier.values();
		BlockPos celestium = null;
		for (int i = 0; i < tiers.length; i++) {
			BlockPos pos = new BlockPos(o.getX() - 5 + i, o.getY(), z);
			level.setBlock(pos, StorageRegistry.unit(tiers[i]).defaultBlockState().setValue(StorageUnitBlock.FACING, Direction.NORTH), 3);
			if (tiers[i] == StorageTier.CELESTIUM) {
				celestium = pos;
			}
		}
		// baus em cima das unidades: duplo, barril, bau de ferro do mod, caixa de shulker
		BlockPos chestA = new BlockPos(o.getX() - 5, o.getY() + 1, z);
		level.setBlock(chestA, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH), 3);
		level.setBlock(chestA.east(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH), 3);
		level.setBlock(new BlockPos(o.getX() - 2, o.getY() + 1, z), Blocks.BARREL.defaultBlockState(), 3);
		level.setBlock(new BlockPos(o.getX(), o.getY() + 1, z), com.mlin.notas.registry.ModBlocks.IRON_CHEST.defaultBlockState(), 3);
		level.setBlock(new BlockPos(o.getX() + 2, o.getY() + 1, z), Blocks.DYED_SHULKER_BOX.cyan().defaultBlockState(), 3);
		// terminais na ponta leste (encostados na ultima unidade)
		BlockPos terminal = new BlockPos(o.getX() + 5, o.getY(), z);
		BlockPos crafting = new BlockPos(o.getX() + 6, o.getY(), z);
		level.setBlock(terminal, StorageRegistry.TERMINAL.defaultBlockState().setValue(StorageTerminalBlock.FACING, Direction.NORTH), 3);
		level.setBlock(crafting, StorageRegistry.CRAFTING_TERMINAL.defaultBlockState().setValue(StorageTerminalBlock.FACING, Direction.NORTH), 3);
		// cabo saindo da ponta oeste ate um barril afastado, e subindo ate um bau
		List<BlockPos> cables = new ArrayList<>();
		for (int dz = 0; dz >= -3; dz--) {
			cables.add(new BlockPos(o.getX() - 6, o.getY(), z + dz));
		}
		cables.add(new BlockPos(o.getX() - 6, o.getY() + 1, z));
		cables.add(new BlockPos(o.getX() - 6, o.getY() + 2, z));
		for (BlockPos pos : cables) {
			level.setBlock(pos, StorageRegistry.CABLE.defaultBlockState(), 3);
		}
		level.setBlock(new BlockPos(o.getX() - 6, o.getY(), z - 4), Blocks.BARREL.defaultBlockState(), 3);
		level.setBlock(new BlockPos(o.getX() - 6, o.getY() + 3, z), Blocks.CHEST.defaultBlockState(), 3);
		for (BlockPos pos : cables) {
			level.setBlock(pos, StorageCableBlock.withConnections(level, pos, StorageRegistry.CABLE.defaultBlockState()), 3);
		}

		// conteudo: centenas de tipos com quantidades bem diferentes (de 1 a milhoes)
		List<Item> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item != Items.AIR && !(item instanceof com.mlin.notas.storage.StorageUnitItem)) {
				items.add(item);
			}
		}
		java.util.Collections.shuffle(items, new Random(42));
		// itens do mod garantidos (para a busca @mlinnotas)
		List<Item> mod = items.stream().filter(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(MlinNotas.MOD_ID)).limit(40).toList();
		items.removeAll(mod);
		items.addAll(0, mod);
		Random random = new Random(7);
		long[] amounts = {1, 3, 16, 64, 250, 999, 1_500, 12_000, 345_000, 2_400_000, 67_000_000};
		int next = 0;
		for (int i = 0; i < tiers.length; i++) {
			StorageUnitBlockEntity unit = (StorageUnitBlockEntity) level.getBlockEntity(new BlockPos(o.getX() - 5 + i, o.getY(), z));
			int types = tiers[i].types() * 3 / 4;
			for (int t = 0; t < types && next < items.size(); t++) {
				long amount = Math.min(tiers[i].perType(), amounts[random.nextInt(amounts.length)]);
				unit.cell().insert(ItemKey.of(new ItemStack(items.get(next++))), amount, false);
			}
		}
		for (BlockPos pos : List.of(chestA, chestA.east(), new BlockPos(o.getX() - 2, o.getY() + 1, z), new BlockPos(o.getX(), o.getY() + 1, z),
				new BlockPos(o.getX() - 6, o.getY(), z - 4))) {
			if (level.getBlockEntity(pos) instanceof Container container) {
				for (int s = 0; s < Math.min(12, container.getContainerSize()) && next < items.size(); s++) {
					Item item = items.get(next++);
					container.setItem(s, new ItemStack(item, 1 + random.nextInt(item.getDefaultMaxStackSize())));
				}
			}
		}
		// ingredientes do crafting
		StorageUnitBlockEntity copper = (StorageUnitBlockEntity) level.getBlockEntity(new BlockPos(o.getX() - 5, o.getY(), z));
		copper.cell().extract(copper.cell().contents().keySet().iterator().next(), Long.MAX_VALUE, false);
		copper.cell().insert(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 900, false);
		return new Room(o, terminal, crafting, celestium);
	}

	private static void openTerminal(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos terminal, boolean crafting) {
		look(context, sp, new double[] {terminal.getX() + 0.5, terminal.getY(), terminal.getZ() - 2.5}, terminal, GameType.CREATIVE);
		sp.getServer().runOnServer(server -> StorageTerminalBlock.open(server.getPlayerList().getPlayers().getFirst(), terminal, crafting));
		context.waitForScreen(TerminalScreen.class);
		context.waitFor(mc -> ((TerminalScreen) mc.gui.screen()).visibleEntries() > 0, 100);
		context.waitTicks(5);
	}

	private static void hover(ClientGameTestContext context, int cell) {
		double[] pos = context.computeOnClient(mc -> {
			double[] center = ((TerminalScreen) mc.gui.screen()).cellCenter(cell);
			int scale = mc.getWindow().getGuiScale();
			return new double[] {center[0] * scale, center[1] * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(3);
	}

	private static void click(ClientGameTestContext context, int cell, int button) {
		hover(context, cell);
		context.getInput().pressMouse(button);
		context.waitTicks(3);
	}

	private static void hideHud(ClientGameTestContext context, boolean hide) {
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() != hide) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static void look(ClientGameTestContext context, TestSingleplayerContext sp, double[] eye, BlockPos target, GameType mode) {
		sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(mode));
		double dx = target.getX() + 0.5 - eye[0];
		double dz = target.getZ() + 0.5 - eye[2];
		double dy = target.getY() + 0.5 - (eye[1] + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", eye[0], eye[1], eye[2], yaw, pitch));
		context.waitTicks(10);
	}

	/** Camera em {@code eye} (relativo a origem) olhando para {@code at}; espera os chunks e tira a foto. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos o, double[] eye, double[] at, String name) {
		sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
		double ex = o.getX() + 0.5 + eye[0];
		double ey = o.getY() + eye[1];
		double ez = o.getZ() + 0.5 + eye[2];
		double dx = o.getX() + 0.5 + at[0] - ex;
		double dz = o.getZ() + 0.5 + at[2] - ez;
		double dy = o.getY() + at[1] - (ey + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", ex, ey, ez, yaw, pitch));
		context.waitTicks(20);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot(name);
	}

}
