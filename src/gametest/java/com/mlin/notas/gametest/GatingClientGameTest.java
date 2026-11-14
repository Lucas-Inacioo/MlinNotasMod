package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.client.storage.TerminalScreen;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.storage.ItemKey;
import com.mlin.notas.storage.NetworkTablet;
import com.mlin.notas.storage.StorageCableBlock;
import com.mlin.notas.storage.StorageRegistry;
import com.mlin.notas.storage.StorageTerminalBlock;
import com.mlin.notas.storage.StorageTier;
import com.mlin.notas.storage.StorageUnitBlockEntity;
import com.mlin.notas.storage.TerminalMenu;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;

/**
 * Game test de cliente da trava de ferramentas e do Tablet de Rede: com a Mineracao do mundo em Ouro, fotografa a dica
 * da picareta de diamante (linha vermelha) e da de ferro (cinza), segura o ataque numa pedra com a picareta de
 * diamante (a pedra fica, aviso na barra de acao e linha no WAILA) e depois abre pela tecla I um terminal de crafting a
 * 300 blocos, fotografando a tela remota.
 */
@SuppressWarnings("UnstableApiUsage")
public class GatingClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule advance_time false");
			BlockPos origin = sp.getServer().computeOnServer(server -> {
				SkillServer.setLevel(server, SkillBranch.MINING, Tier.GOLD.level());
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setGameMode(GameType.SURVIVAL);
				List<Item> hotbar = List.of(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_SWORD, Items.GOLDEN_SWORD, NetworkTablet.ITEM);
				for (int i = 0; i < hotbar.size(); i++) {
					player.getInventory().setItem(i, new ItemStack(hotbar.get(i)));
				}
				player.getInventory().setSelectedSlot(0);
				return player.blockPosition();
			});
			context.waitFor(mc -> com.mlin.notas.client.skills.SkillClient.progress().level(SkillBranch.MINING) == Tier.GOLD.level(), 100);

			// dicas no inventario
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			hoverHotbar(context, 0);
			context.waitTicks(3);
			context.takeScreenshot("mlinnotas-gating-tooltip-locked");
			hoverHotbar(context, 1);
			context.waitTicks(3);
			context.takeScreenshot("mlinnotas-gating-tooltip-unlocked");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// tentar quebrar pedra com a picareta de diamante: a pedra fica
			BlockPos stone = origin.offset(0, 0, 2);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
				level.setBlock(stone.above(), Blocks.AIR.defaultBlockState(), 3);
			});
			look(context, sp, new double[] {stone.getX() + 0.5, stone.getY(), stone.getZ() - 1.6}, stone);
			context.getInput().holdKeyFor(options -> options.keyAttack, 40);
			context.waitTicks(2);
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.takeScreenshot("mlinnotas-gating-mining-refused");
			boolean stoneLeft = sp.getServer().computeOnServer(server -> server.overworld().getBlockState(stone).is(Blocks.STONE));
			if (!stoneLeft) {
				throw new IllegalStateException("a picareta de diamante nao deveria quebrar pedra com o mundo em Ouro");
			}

			// tablet: terminal de crafting a 300 blocos, vinculado, aberto pela tecla
			BlockPos terminal = sp.getServer().computeOnServer(server -> buildFarNetwork(server, origin.offset(300, 0, 0)));
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				NetworkTablet.link(player, player.getInventory().getItem(4), terminal);
			});
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			hoverHotbar(context, 4);
			context.waitTicks(3);
			context.takeScreenshot("mlinnotas-tablet-tooltip");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(2);

			context.getInput().pressKey(ModKeys.NETWORK_TABLET);
			context.waitForScreen(TerminalScreen.class);
			context.waitFor(mc -> ((TerminalScreen) mc.gui.screen()).visibleEntries() > 0, 100);
			boolean remote = context.computeOnClient(mc -> mc.player.containerMenu instanceof TerminalMenu menu && menu.remote() && menu.crafting());
			if (!remote) {
				throw new IllegalStateException("a tecla do tablet deveria abrir o terminal de crafting remoto");
			}
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-tablet-remote-terminal");
			MlinNotas.LOGGER.info("[GatingClientGameTest] terminal remoto em {} aberto de {}", terminal, origin);
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitTicks(5);
		}
	}

	/** Terminal de crafting + cabo + unidade de ferro com uma amostra de itens, em {@code at} (carrega a chunk na hora). */
	private static BlockPos buildFarNetwork(net.minecraft.server.MinecraftServer server, BlockPos at) {
		ServerLevel level = server.overworld();
		BlockPos terminal = at;
		level.setBlock(terminal.below(), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(terminal, StorageRegistry.CRAFTING_TERMINAL.defaultBlockState().setValue(StorageTerminalBlock.FACING, Direction.NORTH), 3);
		level.setBlock(terminal.east(), StorageRegistry.CABLE.defaultBlockState(), 3);
		level.setBlock(terminal.east(2), StorageRegistry.unit(StorageTier.IRON).defaultBlockState(), 3);
		level.setBlock(terminal.east(), StorageCableBlock.withConnections(level, terminal.east(), StorageRegistry.CABLE.defaultBlockState()), 3);
		StorageUnitBlockEntity unit = (StorageUnitBlockEntity) level.getBlockEntity(terminal.east(2));
		long amount = 7;
		for (Item item : List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.COAL,
				Items.TORCH, Items.BREAD, Items.OAK_LOG, Items.GLASS, Items.STICK, Items.COPPER_INGOT, Items.LAPIS_LAZULI, Items.DIAMOND)) {
			unit.cell().insert(ItemKey.of(new ItemStack(item)), amount, false);
			amount = amount * 3 + 11;
		}
		return terminal;
	}

	private static void hoverHotbar(ClientGameTestContext context, int index) {
		double[] pos = context.computeOnClient(mc -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) mc.gui.screen();
			Slot slot = ((InventoryScreen) mc.gui.screen()).getMenu().slots.get(36 + index);
			int scale = mc.getWindow().getGuiScale();
			return new double[] {(screen.mlinnotas$leftPos() + slot.x + 8) * scale, (screen.mlinnotas$topPos() + slot.y + 8) * scale};
		});
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void look(ClientGameTestContext context, TestSingleplayerContext sp, double[] eye, BlockPos target) {
		double dx = target.getX() + 0.5 - eye[0];
		double dz = target.getZ() + 0.5 - eye[2];
		double dy = target.getY() + 0.5 - (eye[1] + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", eye[0], eye[1], eye[2], yaw, pitch));
		context.waitTicks(10);
	}
}
