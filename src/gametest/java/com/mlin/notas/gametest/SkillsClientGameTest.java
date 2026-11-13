package com.mlin.notas.gametest;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.skills.SkillClient;
import com.mlin.notas.client.skills.SkillTreeScreen;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillData;
import com.mlin.notas.skills.SkillNode;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.skills.SkillTree;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Game test de cliente da arvore de habilidades: progresso do mundo com Mineracao ate Cobalto, deposito parcial em
 * Diamante e alguns ramos laterais; tecla K abre a tela, tooltip do Diamante (screenshot), selecao + Depositar manda o
 * cobalto bruto do jogador (o servidor soma e sincroniza), screenshot depois.
 */
@SuppressWarnings("UnstableApiUsage")
public class SkillsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode survival @a");
			SkillNode diamond = SkillTree.node(SkillBranch.MINING, Tier.DIAMOND.level());
			singleplayer.getServer().runOnServer(server -> {
				SkillServer.reset(server);
				SkillServer.setLevel(server, SkillBranch.MINING, Tier.COBALT.level());
				SkillServer.setLevel(server, SkillBranch.COMBAT, 2);
				SkillServer.setLevel(server, SkillBranch.VIGOR, 1);
				SkillData.get(server).deposit(diamond, Map.of("mlinnotas:raw_cobalt", 4), 6);
				SkillServer.syncAll(server);
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getInventory().add(new ItemStack(ModItems.raw(Tier.COBALT), 3));
				player.getInventory().add(new ItemStack(Items.DIAMOND_PICKAXE));
				player.giveExperienceLevels(4);
			});
			context.waitFor(mc -> SkillClient.progress().level(SkillBranch.MINING) == Tier.COBALT.level()
					&& SkillClient.progress().deposited(diamond, "mlinnotas:raw_cobalt") == 4, 100);

			context.getInput().pressKey(ModKeys.SKILLS);
			context.waitForScreen(SkillTreeScreen.class);
			context.waitTicks(3);
			hover(context, SkillBranch.MINING, diamond.level());
			context.waitTicks(3);
			context.takeScreenshot("skills_tree_tooltip");

			// tooltip de um no bloqueado de ramo lateral
			hover(context, SkillBranch.AGILITY, 4);
			context.waitTicks(3);
			context.takeScreenshot("skills_tree_locked");

			// seleciona Diamante e deposita: 4 + 3 = 7 de cobalto bruto e mais 4 niveis (10/15)
			hover(context, SkillBranch.MINING, diamond.level());
			context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
			context.waitFor(mc -> ((SkillTreeScreen) mc.gui.screen()).selected() == diamond, 20);
			context.clickScreenButton("gui.mlinnotas.skills.deposit");
			context.waitFor(mc -> SkillClient.progress().deposited(diamond, "mlinnotas:raw_cobalt") == 7, 100);
			int xp = singleplayer.getServer().computeOnServer(server -> SkillData.get(server).progress().depositedXp(diamond));
			if (xp != 10) {
				throw new IllegalStateException("XP depositado deveria ser 10, veio " + xp);
			}
			hover(context, SkillBranch.MINING, diamond.level());
			context.waitTicks(3);
			context.takeScreenshot("skills_tree_after_deposit");

			context.runOnClient(mc -> mc.gui.setScreen(null));
			singleplayer.getServer().runOnServer(SkillServer::reset);
		}
	}

	private static void hover(ClientGameTestContext context, SkillBranch branch, int level) {
		double[] pos = context.computeOnClient(mc -> {
			SkillTreeScreen screen = (SkillTreeScreen) mc.gui.screen();
			int scale = mc.getWindow().getGuiScale();
			return new double[] {(screen.nodeX(branch, level) + SkillTreeScreen.NODE / 2.0) * scale,
					(screen.nodeY(branch) + SkillTreeScreen.NODE / 2.0) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}
}
