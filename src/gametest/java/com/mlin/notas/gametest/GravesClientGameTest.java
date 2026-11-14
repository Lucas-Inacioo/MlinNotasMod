package com.mlin.notas.gametest;

import com.mlin.notas.accessories.AccessoryRegistry;
import com.mlin.notas.accessories.AccessoryServer;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.minimap.WorldMapScreen;
import com.mlin.notas.grave.GraveData;
import com.mlin.notas.grave.GraveInfo;
import com.mlin.notas.grave.GraveRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Game test de cliente das lapides: o jogador morre de verdade (/kill) com inventario, armadura e acessorio, renasce e
 * volta a lapide. Screenshots {@code mlinnotas-grave-*}: chat da morte, lapide de dia (com WAILA e o marcador no
 * minimapa), de perto, de noite, marcador preso na borda do minimapa quando longe, mapa grande com a dica, tela da
 * lapide e o inventario depois de recuperar agachado.
 */
@SuppressWarnings("UnstableApiUsage")
public class GravesClientGameTest implements FabricClientGameTest {
	private static final int X = 40;
	private static final int Z = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			// cenario: grama do mundo plano, um caminho e umas flores para a foto nao ficar vazia
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var level = player.level();
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, X, Z);
				for (int dx = -6; dx <= 6; dx++) {
					for (int dz = -6; dz <= 6; dz++) {
						level.setBlockAndUpdate(new net.minecraft.core.BlockPos(X + dx, y, Z + dz), Blocks.AIR.defaultBlockState());
					}
				}
				for (int dz = -6; dz <= 6; dz++) {
					level.setBlockAndUpdate(new net.minecraft.core.BlockPos(X - 2, y - 1, Z + dz), Blocks.DIRT_PATH.defaultBlockState());
				}
				level.setBlockAndUpdate(new net.minecraft.core.BlockPos(X + 2, y, Z + 1), Blocks.POPPY.defaultBlockState());
				level.setBlockAndUpdate(new net.minecraft.core.BlockPos(X + 3, y, Z - 1), Blocks.SHORT_GRASS.defaultBlockState());
				level.setBlockAndUpdate(new net.minecraft.core.BlockPos(X - 3, y, Z + 2), Blocks.OXEYE_DAISY.defaultBlockState());
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				player.teleportTo(level, X + 0.5, y, Z + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
				player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
				player.getInventory().setItem(1, new ItemStack(Items.IRON_PICKAXE));
				player.getInventory().setItem(2, new ItemStack(Items.TORCH, 48));
				player.getInventory().setItem(8, new ItemStack(Items.BREAD, 12));
				player.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 64));
				player.getInventory().setItem(13, new ItemStack(Items.RAW_IRON, 23));
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item("iron_ring")));
				player.giveExperienceLevels(12);
			});
			context.waitTicks(10);
			sp.getConnection().waitForChunksRender();

			// morte de verdade: tela de morte, renascer
			sp.getServer().runCommand("kill @p");
			context.waitFor(mc -> mc.player != null && !mc.player.isAlive(), 100);
			context.waitTicks(20);
			context.runOnClient(mc -> mc.player.respawn());
			context.waitFor(mc -> mc.player != null && mc.player.isAlive() && mc.gui.screen() == null, 200);
			GraveInfo grave = sp.getServer().computeOnServer(server -> {
				List<GraveInfo> graves = GraveData.get(server).index().all();
				if (graves.size() != 1) {
					throw new IllegalStateException("deveria haver 1 lapide, ha " + graves);
				}
				GraveInfo g = graves.getFirst();
				if (!server.overworld().getBlockState(new net.minecraft.core.BlockPos(g.x(), g.y(), g.z())).is(GraveRegistry.GRAVE)) {
					throw new IllegalStateException("bloco da lapide ausente");
				}
				return g;
			});
			context.waitFor(mc -> !com.mlin.notas.client.grave.GraveClient.all().isEmpty(), 100);

			// volta a lapide (a frente olha para o norte: o jogador morreu olhando para o sul)
			look(sp, grave, -3.2, 26.0F);
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.takeScreenshot("mlinnotas-grave-day");
			// foto limpa (sem HUD), mais de perto e de lado para ver o modelo e o nome gravado
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %d %.2f -25 18", grave.x() + 0.5 - 0.9, grave.y(), grave.z() + 0.5 - 2.0));
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyToggleGui);
			context.waitTicks(2);
			context.takeScreenshot("mlinnotas-grave-model");
			context.getInput().pressKey(options -> options.keyToggleGui);
			look(sp, grave, -1.6, 38.0F);
			context.waitTicks(20);
			context.takeScreenshot("mlinnotas-grave-close");
			// chat com as coordenadas (abre o chat para nao sumir)
			context.getInput().pressKey(options -> options.keyChat);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-grave-chat");
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			sp.getServer().runCommand("time set midnight");
			look(sp, grave, -3.2, 26.0F);
			context.waitTicks(40);
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.takeScreenshot("mlinnotas-grave-night");
			sp.getServer().runCommand("time set noon");

			// longe: marcador preso na borda do minimapa
			sp.getServer().runCommand("tp @p " + (grave.x() + 150) + " " + grave.y() + " " + (grave.z() + 60) + " 0 0");
			context.waitTicks(30);
			context.takeScreenshot("mlinnotas-grave-minimap-far");

			// mapa grande perto da lapide, mouse em cima do icone
			sp.getServer().runCommand("tp @p " + (grave.x() + 12) + " " + grave.y() + " " + (grave.z() + 8) + " 0 0");
			context.waitTicks(30);
			context.getInput().pressKey(ModKeys.MAP);
			context.waitForScreen(WorldMapScreen.class);
			context.waitTicks(20);
			double[] cursor = context.computeOnClient(mc -> {
				try {
					WorldMapScreen screen = (WorldMapScreen) mc.gui.screen();
					Method zoom = WorldMapScreen.class.getDeclaredMethod("effectiveZoom");
					zoom.setAccessible(true);
					float z = (float) zoom.invoke(screen);
					Method sx = WorldMapScreen.class.getDeclaredMethod("screenX", double.class, float.class);
					Method sz = WorldMapScreen.class.getDeclaredMethod("screenZ", double.class, float.class);
					sx.setAccessible(true);
					sz.setAccessible(true);
					double scale = mc.getWindow().getGuiScale();
					return new double[] {(int) sx.invoke(screen, grave.x() + 0.5, z) * scale, (int) sz.invoke(screen, grave.z() + 0.5, z) * scale};
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			context.getInput().setCursorPos(cursor[0], cursor[1]);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-grave-worldmap");
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			// abrir (botao direito): tela de bau com o equipamento em cima
			look(sp, grave, -1.6, 38.0F);
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(ContainerScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-grave-screen");
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitForScreen(null);
			context.waitTicks(5);

			// agachado + botao direito: tudo volta nos mesmos lugares
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(3);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.getInput().releaseKey(options -> options.keyShift);
			sp.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (!player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) || !player.getInventory().getItem(0).is(Items.IRON_SWORD)
						|| !player.getOffhandItem().is(Items.SHIELD)) {
					throw new IllegalStateException("agachar deveria devolver tudo nos mesmos encaixes");
				}
				if (!GraveData.get(server).index().all().isEmpty()) {
					throw new IllegalStateException("lapide deveria sumir depois de recuperar");
				}
			});
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-grave-restored-inventory");
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitForScreen(null);
		}
	}

	/** Poe o jogador ao norte da lapide ({@code dz} negativo), olhando para o sul com a inclinacao dada. */
	private static void look(TestSingleplayerContext sp, GraveInfo grave, double dz, float pitch) {
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %d %.2f 0 %.1f", grave.x() + 0.5, grave.y(), grave.z() + 0.5 + dz, pitch));
	}
}
