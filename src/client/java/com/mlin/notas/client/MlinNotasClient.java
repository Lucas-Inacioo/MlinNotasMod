package com.mlin.notas.client;

import com.mlin.notas.client.recipes.RecipeViewerClient;
import com.mlin.notas.client.minimap.MinimapClient;
import com.mlin.notas.client.waila.WailaClient;
import com.mlin.notas.backpack.OpenBackpackPayload;
import com.mlin.notas.client.screen.BackpackScreen;
import com.mlin.notas.client.screen.BigChestScreen;
import com.mlin.notas.chest.ChestTier;
import com.mlin.notas.client.screen.SelectiveEnchantScreen;
import com.mlin.notas.registry.ModMenus;
import com.mlin.notas.registry.ModBlockEntities;
import com.mlin.notas.client.render.BigChestRenderer;
import com.mlin.notas.client.render.EndCreatureRenderers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import com.mlin.notas.tablet.OpenTabletPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

/**
 * Entrypoint de cliente: renderizacao, telas, keybinds.
 */
public class MlinNotasClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModKeys.init();
		InventorySortClient.init();
		RecipeViewerClient.init();
		MinimapClient.init();
		com.mlin.notas.client.waystone.WaystoneClient.init();
		WailaClient.init();
		com.mlin.notas.client.beacon.BeaconScreenExtras.init();
		com.mlin.notas.client.skills.SkillClient.init();
		for (ChestTier tier : ChestTier.values()) {
			MenuScreens.register(ModMenus.forTier(tier), BigChestScreen::new);
		}
		MenuScreens.register(ModMenus.BACKPACK, BackpackScreen::new);
		MenuScreens.register(ModMenus.SELECTIVE_ENCHANT, SelectiveEnchantScreen::new);
		BlockEntityRenderers.register(ModBlockEntities.BIG_CHEST, BigChestRenderer::new);
		EndCreatureRenderers.init();
		com.mlin.notas.client.render.NetherRenderers.init();
		com.mlin.notas.client.render.BossRenderers.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			while (ModKeys.GAMMABRIGHT.consumeClick()) {
				boolean on = GammaBright.toggle();
				client.player.sendOverlayMessage(Component.translatable(on ? "message.mlinnotas.gammabright.on" : "message.mlinnotas.gammabright.off"));
			}
			while (ModKeys.CRAFTING_TABLET.consumeClick()) {
				if (ClientPlayNetworking.canSend(OpenTabletPayload.TYPE)) {
					ClientPlayNetworking.send(OpenTabletPayload.INSTANCE);
				}
			}
			while (ModKeys.BACKPACK.consumeClick()) {
				if (ClientPlayNetworking.canSend(OpenBackpackPayload.TYPE)) {
					ClientPlayNetworking.send(OpenBackpackPayload.INSTANCE);
				}
			}
			InventorySortClient.tick();
			ElevatorClient.tick(client);
		});
	}
}
