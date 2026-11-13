package com.mlin.notas.gametest;

import com.mlin.notas.client.GammaBright;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.recipes.RecipeScreen;
import com.mlin.notas.client.recipes.RecipeViewerClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Game test de cliente: abre o jogo de verdade (precisa de janela/GPU) e tira screenshots das telas do mod.
 * Rodar com: gradlew runClientGameTest. Screenshots em build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class MlinNotasClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("mlinnotas-singleplayer");

			// Gammabright ligado
			context.getInput().pressKey(ModKeys.GAMMABRIGHT);
			context.waitTicks(5);
			if (!context.computeOnClient(mc -> GammaBright.isEnabled())) {
				throw new IllegalStateException("gammabright deveria estar ligado apos a tecla");
			}
			context.takeScreenshot("mlinnotas-gammabright");
			context.getInput().pressKey(ModKeys.GAMMABRIGHT);

			// Receitas recebidas do servidor integrado
			context.waitFor(mc -> RecipeViewerClient.index().size() > 0, 200);

			// Inventario com a lista de itens do visualizador
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-inventory-item-list");

			// Tela de receitas do bau, aberta por codigo (mesmo caminho do clique/tecla R)
			context.runOnClient(mc -> {
				if (!RecipeViewerClient.openRecipes(new ItemStack(Items.CHEST), mc.gui.screen())) {
					throw new IllegalStateException("bau deveria ter receita no visualizador");
				}
			});
			context.waitForScreen(RecipeScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-recipe-chest");
			context.clickScreenButton("gui.mlinnotas.recipes.close");
			context.waitForScreen(InventoryScreen.class);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(null);
		}
	}
}
