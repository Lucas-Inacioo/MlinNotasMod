package com.mlin.notas.gametest;

import com.mlin.notas.accessories.AccessoryCatalog;
import com.mlin.notas.accessories.AccessoryData;
import com.mlin.notas.accessories.AccessoryDef;
import com.mlin.notas.accessories.AccessoryMenu;
import com.mlin.notas.accessories.AccessoryRegistry;
import com.mlin.notas.accessories.AccessoryServer;
import com.mlin.notas.client.accessories.AccessoryScreen;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Game test de cliente dos acessorios: aba na tela de inventario, tela de acessorios com 6 encaixes, boneco e barras do
 * estagio (com dano cortado em vermelho), inventario cheio com todos os icones, tooltips (artefato, reliquia, aviso de
 * teto), resumo do painel e o relogio de bolso no canto da tela.
 */
@SuppressWarnings("UnstableApiUsage")
public class AccessoriesClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode survival @a");
			singleplayer.getServer().runCommand("time set 6000");
			int original = singleplayer.getServer().computeOnServer(server -> SkillServer.progress(server).level(SkillBranch.MINING));

			// Golem -> Guardiao: dano ate 10%; dois aneis de netherite + luva do duelista = 16% (cortado)
			singleplayer.getServer().runOnServer(server -> {
				SkillServer.setLevel(server, SkillBranch.MINING, Tier.COBALT.level());
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				for (String id : List.of("netherite_ring", "netherite_ring", "life_amulet", "traveler_belt", "duelist_glove", "rabbit_foot_charm")) {
					AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item(id)));
				}
				// inventario cheio com todos os acessorios (36 = 12 basicos + 17 artefatos + 7 reliquias)
				List<AccessoryDef> all = AccessoryCatalog.all();
				for (int i = 0; i < all.size() && i < 36; i++) {
					player.getInventory().setItem(i, new ItemStack(AccessoryRegistry.item(all.get(i).id())));
				}
			});
			context.waitTicks(20);
			context.waitFor(mc -> AccessoryData.get(mc.player).stream().noneMatch(ItemStack::isEmpty), 100);

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(3);
			context.takeScreenshot("accessories_inventory_tab");

			context.clickScreenButton("button.mlinnotas.accessories");
			context.waitForScreen(AccessoryScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(5);
			context.takeScreenshot("accessories_screen");

			// tooltip do anel equipado: aviso de teto
			hoverSlot(context, 0);
			context.waitTicks(3);
			context.takeScreenshot("accessories_tooltip_capped");
			// artefato e reliquia no inventario
			hoverSlot(context, AccessorySlotIndex.inventory(AccessoryCatalog.all().indexOf(AccessoryCatalog.byId("climbing_glove"))));
			context.waitTicks(3);
			context.takeScreenshot("accessories_tooltip_artifact");
			hoverSlot(context, AccessorySlotIndex.inventory(AccessoryCatalog.all().indexOf(AccessoryCatalog.byId("abyssal_shell"))));
			context.waitTicks(3);
			context.takeScreenshot("accessories_tooltip_relic");
			// resumo do painel do estagio
			hoverPanel(context);
			context.waitTicks(3);
			context.takeScreenshot("accessories_panel_summary");

			// voltar ao inventario pela aba
			context.getInput().setCursorPos(0, 0);
			context.clickScreenButton("button.mlinnotas.accessories.back");
			context.waitForScreen(InventoryScreen.class);
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// estagio final: nada cortado
			singleplayer.getServer().runOnServer(server -> SkillServer.setLevel(server, SkillBranch.MINING, Tier.ENDITE.level()));
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			context.clickScreenButton("button.mlinnotas.accessories");
			context.waitForScreen(AccessoryScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(5);
			context.takeScreenshot("accessories_screen_endgame");
			context.runOnClient(mc -> mc.player.closeContainer());

			// relogio de bolso no amuleto: hora, dia e estacao no canto
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				AccessoryData.set(player, 5, new ItemStack(AccessoryRegistry.item("pocket_watch")));
				AccessoryServer.refresh(player);
			});
			context.waitFor(mc -> AccessoryData.get(mc.player, 5).is(AccessoryRegistry.item("pocket_watch")), 100);
			context.waitTicks(5);
			context.takeScreenshot("accessories_pocket_watch_hud");

			singleplayer.getServer().runOnServer(server -> {
				SkillServer.setLevel(server, SkillBranch.MINING, original);
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				AccessoryData.clear(player);
				AccessoryServer.refresh(player);
				player.getInventory().clearContent();
			});
		}
	}

	/** Indices dos slots do menu de acessorios. */
	private static final class AccessorySlotIndex {
		/** Slot do menu para a posicao {@code i} do inventario do jogador (0..8 hotbar, 9..35 principal). */
		static int inventory(int i) {
			// o menu tem 6 encaixes, depois o inventario principal (9..35) e por fim a hotbar (0..8)
			return i < 9 ? 6 + 27 + i : 6 + (i - 9);
		}
	}

	private static void hoverSlot(ClientGameTestContext context, int menuSlot) {
		double[] pos = context.computeOnClient(mc -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) mc.gui.screen();
			Slot slot = ((AccessoryScreen) mc.gui.screen()).getMenu().slots.get(menuSlot);
			int scale = mc.getWindow().getGuiScale();
			return new double[] {(screen.mlinnotas$leftPos() + slot.x + 8) * scale, (screen.mlinnotas$topPos() + slot.y + 8) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void hoverPanel(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(mc -> {
			AbstractContainerScreenAccessor screen = (AbstractContainerScreenAccessor) mc.gui.screen();
			int scale = mc.getWindow().getGuiScale();
			return new double[] {(screen.mlinnotas$leftPos() + 130) * scale, (screen.mlinnotas$topPos() + 20) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

}
