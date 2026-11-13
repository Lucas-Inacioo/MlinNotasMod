package com.mlin.notas.gametest;

import com.mlin.notas.recipes.RecipeExporter;
import com.mlin.notas.recipes.RecipeIndex;
import com.mlin.notas.recipes.RecipeKind;
import com.mlin.notas.recipes.RecipeSyncPayload;
import com.mlin.notas.recipes.RecipeView;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * Game tests do visualizador de receitas: exportacao das receitas vanilla e ida e volta pelo pacote.
 */
public class RecipeViewerGameTest {
	private static RecipeHolder<?> vanilla(MinecraftServer server, String path) {
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(path));
		return server.getRecipeManager().byKey(key).orElseThrow(() -> new IllegalStateException("receita vanilla nao encontrada: " + path));
	}

	@GameTest
	public void exportsShapedRecipe(GameTestHelper helper) {
		RecipeView chest = RecipeExporter.export(vanilla(helper.getLevel().getServer(), "chest"));
		helper.assertTrue(chest != null, "bau deveria exportar");
		helper.assertTrue(chest.kind() == RecipeKind.SHAPED, "bau e shaped");
		helper.assertTrue(chest.width() == 3 && chest.height() == 3, "bau 3x3");
		helper.assertTrue(chest.inputs().size() == 9, "9 slots");
		helper.assertTrue(chest.gridSlot(1, 1).isEmpty(), "centro vazio");
		int planks = BuiltInRegistries.ITEM.getId(Items.OAK_PLANKS);
		helper.assertTrue(chest.gridSlot(0, 0).contains(planks), "tag planks inclui oak_planks");
		helper.assertTrue(chest.output() == BuiltInRegistries.ITEM.getId(Items.CHEST) && chest.outputCount() == 1, "resultado bau");
		helper.succeed();
	}

	@GameTest
	public void exportsSmeltingRecipe(GameTestHelper helper) {
		RecipeView iron = RecipeExporter.export(vanilla(helper.getLevel().getServer(), "iron_ingot_from_smelting_raw_iron"));
		helper.assertTrue(iron != null && iron.kind() == RecipeKind.SMELTING, "fundicao de ferro");
		helper.assertTrue(iron.cookTicks() == 200, "200 ticks");
		helper.assertTrue(iron.inputs().getFirst().contains(BuiltInRegistries.ITEM.getId(Items.RAW_IRON)), "entrada raw_iron");
		helper.assertTrue(iron.output() == BuiltInRegistries.ITEM.getId(Items.IRON_INGOT), "saida iron_ingot");
		helper.succeed();
	}

	@GameTest
	public void exportsWholeRecipeManagerAndIndexesIt(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<RecipeView> all = RecipeExporter.export(server.getRecipeManager());
		helper.assertTrue(all.size() > 500, "deveria exportar centenas de receitas vanilla, exportou " + all.size());
		RecipeIndex index = new RecipeIndex(all);
		int stick = BuiltInRegistries.ITEM.getId(Items.STICK);
		helper.assertTrue(!index.recipesFor(stick).isEmpty(), "graveto tem receita");
		helper.assertTrue(index.usagesOf(stick).size() > 10, "graveto e usado em muitas receitas");
		helper.assertTrue(index.recipesFor(BuiltInRegistries.ITEM.getId(Items.BEDROCK)).isEmpty(), "bedrock nao tem receita");
		helper.succeed();
	}

	@GameTest
	public void payloadRoundTrip(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<RecipeView> all = RecipeExporter.export(server.getRecipeManager());
		List<RecipeSyncPayload> batches = RecipeSyncPayload.batches(all);
		helper.assertTrue(batches.getFirst().first() && batches.getLast().last(), "primeiro e ultimo marcados");
		int total = 0;
		for (RecipeSyncPayload batch : batches) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
			RecipeSyncPayload.CODEC.encode(buf, batch);
			helper.assertTrue(buf.readableBytes() < 1_000_000, "lote abaixo do limite de 1 MiB");
			RecipeSyncPayload decoded = RecipeSyncPayload.CODEC.decode(buf);
			helper.assertTrue(decoded.recipes().equals(batch.recipes()), "lote identico apos decodificar");
			total += decoded.recipes().size();
		}
		helper.assertTrue(total == all.size(), "todas as receitas passaram pelos lotes");
		helper.succeed();
	}
}
