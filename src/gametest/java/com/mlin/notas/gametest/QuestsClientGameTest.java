package com.mlin.notas.gametest;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.quests.QuestBookScreen;
import com.mlin.notas.client.quests.QuestClient;
import com.mlin.notas.quests.Quest;
import com.mlin.notas.quests.QuestServer;
import com.mlin.notas.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Game test de cliente do livro de quests: mundo novo (o livro chega na primeira entrada), algumas quests completas e
 * parciais; abre pelo item e pela tecla Y, fotografa o capitulo, o detalhe, a recompensa para resgatar, os capitulos
 * largos (Mineracao, Chefes) e a tela em 1280x720 com escala 3; marca e resgata pelos botoes.
 */
@SuppressWarnings("UnstableApiUsage")
public class QuestsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode survival @a");
			context.waitFor(mc -> !QuestClient.book().quests().isEmpty(), 200);
			context.waitFor(mc -> mc.player.getInventory().countItem(ModItems.QUEST_BOOK) == 1, 100);

			// estado: Bem-vindos resgatavel, Atalhos concluida, troncos 10/16 e trigo parcial
			singleplayer.getServer().runOnServer(server -> {
				QuestServer.reset(server);
				QuestServer.forceComplete(server, QuestServer.book().quest("basics/hud"));
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getInventory().add(new ItemStack(Items.OAK_LOG, 10));
				player.getInventory().add(new ItemStack(Items.COBBLESTONE, 3));
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.QUEST_BOOK));
				QuestServer.checkPeriodic(server);
			});
			context.waitFor(mc -> QuestClient.progress().isComplete("basics/hud")
					&& QuestClient.progress().progress(QuestClient.book().quest("basics/wood"), 0) == 10, 100);
			context.waitTicks(130); // deixa os toasts sumirem

			// abre pelo item (botao direito)
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(QuestBookScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(3);
			context.takeScreenshot("quests_chapter");

			// detalhe de quest em andamento (troncos 10/16)
			select(context, "basics/wood");
			context.takeScreenshot("quests_detail");

			// clique no no "Minimapa" e marca como feito pelo botao
			context.runOnClient(mc -> ((QuestBookScreen) mc.gui.screen()).select(null));
			Quest minimap = QuestClient.book().quest("basics/minimap");
			clickNode(context, minimap);
			context.waitFor(mc -> ((QuestBookScreen) mc.gui.screen()).selected() != null
					&& ((QuestBookScreen) mc.gui.screen()).selected().key().equals("basics/minimap"), 20);
			context.waitTicks(2);
			context.takeScreenshot("quests_checkmark");
			context.clickScreenButton("gui.mlinnotas.quests.check");
			context.waitFor(mc -> QuestClient.progress().isComplete("basics/minimap"), 100);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(3);
			context.takeScreenshot("quests_claimable");
			context.clickScreenButton("gui.mlinnotas.quests.claim");
			context.waitFor(mc -> mc.player.getInventory().countItem(Items.MAP) == 1, 100);
			context.waitTicks(3);
			context.takeScreenshot("quests_claimed");

			// capitulo largo com quest bloqueada (requisitos de outro capitulo)
			select(context, "mining/skill_cobalt");
			context.takeScreenshot("quests_mining_locked");
			select(context, "bosses/legends");
			context.takeScreenshot("quests_bosses");
			context.runOnClient(mc -> ((QuestBookScreen) mc.gui.screen()).select(null));
			context.runOnClient(mc -> ((QuestBookScreen) mc.gui.screen()).selectChapter(QuestClient.book().chapter("end")));
			context.waitTicks(3);
			context.takeScreenshot("quests_end");

			// tecla Y fecha e reabre (volta no ultimo capitulo)
			context.getInput().pressKey(ModKeys.QUESTS);
			context.waitFor(mc -> mc.gui.screen() == null, 20);
			context.getInput().pressKey(ModKeys.QUESTS);
			context.waitForScreen(QuestBookScreen.class);
			context.waitFor(mc -> ((QuestBookScreen) mc.gui.screen()).chapter().id().equals("end"), 20);

			// 1280x720 com escala 3 (mesma area logica, pixels maiores)
			context.getInput().resizeWindow(1280, 720);
			context.waitTicks(2);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(3);
				mc.resizeGui();
			});
			context.waitTicks(5);
			select(context, "exploration/biomes");
			context.takeScreenshot("quests_scale3");
			int scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
			if (scale != 3) {
				throw new IllegalStateException("escala 3 nao aplicada: " + scale);
			}
			context.runOnClient(mc -> mc.options.guiScale().set(0));
			context.getInput().resizeWindow(854, 480);
			context.runOnClient(mc -> mc.resizeGui());
			context.waitTicks(5);

			// toast ao completar (fora da tela)
			context.runOnClient(mc -> mc.gui.setScreen(null));
			singleplayer.getServer().runOnServer(server -> QuestServer.forceComplete(server, QuestServer.book().quest("basics/recipes")));
			context.waitFor(mc -> QuestClient.progress().isComplete("basics/recipes"), 100);
			context.waitTicks(15);
			context.takeScreenshot("quests_toast");

			singleplayer.getServer().runOnServer(QuestServer::reset);
		}
	}

	private static void select(ClientGameTestContext context, String key) {
		context.runOnClient(mc -> ((QuestBookScreen) mc.gui.screen()).select(QuestClient.book().quest(key)));
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
	}

	private static void clickNode(ClientGameTestContext context, Quest quest) {
		double[] pos = context.computeOnClient(mc -> {
			QuestBookScreen screen = (QuestBookScreen) mc.gui.screen();
			int scale = mc.getWindow().getGuiScale();
			return new double[] {(screen.nodeX(quest) + QuestBookScreen.NODE / 2.0) * scale, (screen.nodeY(quest) + QuestBookScreen.NODE / 2.0) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}
}
