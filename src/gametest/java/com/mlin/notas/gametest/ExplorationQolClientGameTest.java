package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.compass.CompassScreen;
import com.mlin.notas.client.dynlight.DynamicLightsClient;
import com.mlin.notas.compass.CompassKind;
import com.mlin.notas.compass.CompassServer;
import com.mlin.notas.compass.CompassTarget;
import com.mlin.notas.compass.Compasses;
import com.mlin.notas.dynlight.DynamicLightOptions;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * Game test de cliente da luz dinamica (sala de pedra fechada a noite: sem nada, com tocha na mao, com glowstone no
 * chao) e das bussolas (lista de biomas com busca, lista de estruturas, busca de uma vila e tooltip com distancia).
 */
@SuppressWarnings("UnstableApiUsage")
public class ExplorationQolClientGameTest implements FabricClientGameTest {
	private static final int ROOM_Y = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-bussola")).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule advance_time false");
			compasses(context, world);
			dynamicLights(context, world);
		}
	}

	private static void dynamicLights(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("time set midnight");
		BlockPos center = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			return new BlockPos(player.getBlockX(), ROOM_Y, player.getBlockZ());
		});
		int x = center.getX();
		int z = center.getZ();
		// sala 15x7x15 de pedra fechada, chao xadrez para a luz aparecer bem
		world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone hollow", x - 8, ROOM_Y - 1, z - 8, x + 8, ROOM_Y + 6, z + 8));
		for (int dx = -7; dx <= 7; dx += 2) {
			world.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:polished_andesite", x + dx, ROOM_Y - 1, z - 7, x + dx, ROOM_Y - 1, z + 7));
		}
		world.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:oak_planks", x + 2, ROOM_Y, z - 4));
		world.getServer().runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:oak_planks", x - 3, ROOM_Y, z - 3));
		world.getServer().runCommand("clear @p");
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 35", x + 0.5, ROOM_Y, z + 3.5));
		context.waitTicks(30);
		world.getConnection().waitForChunksRender();
		context.waitTicks(10);

		BlockPos probe = new BlockPos(x, ROOM_Y, z + 2);
		int darkBlock = context.computeOnClient(mc -> LightCoordsUtil.block(LightCoordsUtil.getLightCoords(mc.level, probe)));
		context.takeScreenshot("mlinnotas-dynlight-1-dark");

		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:torch");
		context.waitFor(mc -> !DynamicLightsClient.sources().isEmpty(), 40);
		context.waitTicks(10);
		world.getConnection().waitForChunksRender();
		context.waitTicks(5);
		int litBlock = context.computeOnClient(mc -> LightCoordsUtil.block(LightCoordsUtil.getLightCoords(mc.level, probe)));
		MlinNotas.LOGGER.info("[ExplorationQolClientGameTest] luz em {}: sem tocha {}, com tocha {}, fontes {}", probe, darkBlock, litBlock,
				DynamicLightsClient.sources());
		if (litBlock <= darkBlock || litBlock < 6) {
			throw new IllegalStateException("tocha na mao deveria iluminar: " + darkBlock + " -> " + litBlock);
		}
		context.takeScreenshot("mlinnotas-dynlight-2-torch");

		// tocha some da mao, glowstone jogada no chao a frente (yaw 0 = olhando para o sul, +Z)
		world.getServer().runCommand("clear @p");
		world.getServer().runCommand(String.format(Locale.ROOT,
				"summon minecraft:item %.1f %d %.1f {Item:{id:\"minecraft:glowstone\",count:1},PickupDelay:32767,Age:-32768}", x + 0.5, ROOM_Y, z + 7.5));
		context.waitFor(mc -> DynamicLightsClient.sources().size() == 1
				&& DynamicLightsClient.sources().getFirst().luminance() == 15, 60);
		context.waitTicks(10);
		world.getConnection().waitForChunksRender();
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-dynlight-3-dropped-glowstone");

		// desligado: some tudo
		context.runOnClient(mc -> {
			DynamicLightOptions off = new DynamicLightOptions();
			off.enabled = false;
			DynamicLightsClient.setOptions(off);
		});
		context.waitFor(mc -> DynamicLightsClient.sources().isEmpty(), 20);
		context.runOnClient(mc -> DynamicLightsClient.setOptions(new DynamicLightOptions()));
		world.getServer().runCommand("kill @e[type=minecraft:item]");
		world.getServer().runCommand("time set noon");
	}

	private static void compasses(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("time set noon");
		// lista de biomas com busca
		openCompass(world, Compasses.BIOME_COMPASS, CompassKind.BIOME);
		context.waitForScreen(CompassScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-compass-1-biomes");
		int biomes = context.computeOnClient(mc -> ((CompassScreen) mc.gui.screen()).shown().size());
		context.runOnClient(mc -> ((CompassScreen) mc.gui.screen()).setQuery("forest"));
		context.waitTicks(3);
		context.takeScreenshot("mlinnotas-compass-2-biomes-search");
		int forests = context.computeOnClient(mc -> ((CompassScreen) mc.gui.screen()).shown().size());
		if (biomes < 40 || forests < 3 || forests >= biomes) {
			throw new IllegalStateException("lista de biomas estranha: " + biomes + " biomas, " + forests + " florestas");
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));

		// lista de estruturas: Mineracao em Madeira, covis escondidos
		openCompass(world, Compasses.STRUCTURE_COMPASS, CompassKind.STRUCTURE);
		context.waitForScreen(CompassScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-compass-3-structures");
		List<String> structures = context.computeOnClient(mc -> List.copyOf(((CompassScreen) mc.gui.screen()).shown()));
		MlinNotas.LOGGER.info("[ExplorationQolClientGameTest] estruturas na lista: {}", structures);
		if (!structures.contains("minecraft:village") || !structures.contains("mlinnotas:ruined_chapel") || structures.contains("mlinnotas:cobalt_shrine")
				|| structures.contains("minecraft:village_plains")) {
			throw new IllegalStateException("lista de estruturas errada: " + structures);
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));

		// procura a vila mais perto e confere o alvo e o tooltip no cliente
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			CompassServer.startSearch(player, player.getMainHandItem(), CompassKind.STRUCTURE, "minecraft:village");
		});
		context.waitFor(mc -> {
			CompassTarget target = mc.player.getMainHandItem().get(Compasses.TARGET);
			return target != null && target.status() != CompassTarget.SEARCHING;
		}, 400);
		List<String> tooltip = context.computeOnClient(mc -> mc.player.getMainHandItem()
				.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList());
		MlinNotas.LOGGER.info("[ExplorationQolClientGameTest] tooltip da bussola: {}", tooltip);
		int status = context.computeOnClient(mc -> mc.player.getMainHandItem().get(Compasses.TARGET).status());
		if (status != CompassTarget.FOUND || tooltip.stream().noneMatch(line -> line.contains("blocos") || line.contains("blocks"))) {
			throw new IllegalStateException("bussola nao achou a vila: " + tooltip);
		}
		world.getServer().runCommand("execute as @p at @p run tp @p ~ ~ ~ 0 20");
		context.waitTicks(20);
		context.takeScreenshot("mlinnotas-compass-4-structure-in-hand");
	}

	private static void openCompass(TestSingleplayerContext world, Item item, CompassKind kind) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ItemStack stack = new ItemStack(item);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			CompassServer.open(player, kind, stack);
		});
	}
}
