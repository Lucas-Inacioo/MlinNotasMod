package com.mlin.notas.client.recipes;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.recipes.RecipeIndex;
import com.mlin.notas.recipes.RecipeSyncPayload;
import com.mlin.notas.recipes.RecipeView;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Lado cliente do visualizador de receitas (estilo NEI/JEI): recebe as receitas do servidor,
 * mostra a lista de itens ao lado das telas de container e abre {@link RecipeScreen}.
 */
public final class RecipeViewerClient {
	private static RecipeIndex index = RecipeIndex.EMPTY;
	private static final List<RecipeView> pending = new ArrayList<>();
	private static List<ItemStack> allItems;

	private RecipeViewerClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(RecipeSyncPayload.TYPE, (payload, context) -> {
			if (payload.first()) {
				pending.clear();
			}
			pending.addAll(payload.recipes());
			if (payload.last()) {
				index = new RecipeIndex(pending);
				pending.clear();
				MlinNotas.LOGGER.info("[{}] {} receitas recebidas do servidor", MlinNotas.MOD_ID, index.size());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			index = RecipeIndex.EMPTY;
			pending.clear();
		});
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen && !(screen instanceof CreativeModeInventoryScreen)) {
				ItemListOverlay.attach(containerScreen);
			}
		});
	}

	public static RecipeIndex index() {
		return index;
	}

	/** Todos os itens registrados (menos ar), uma pilha de cada. Calculado uma vez. */
	public static List<ItemStack> allItems() {
		if (allItems == null) {
			List<ItemStack> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item != Items.AIR) {
					list.add(new ItemStack(item));
				}
			}
			allItems = List.copyOf(list);
		}
		return allItems;
	}

	public static ItemStack stackOf(int itemId) {
		Item item = BuiltInRegistries.ITEM.byId(itemId);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	public static int idOf(ItemStack stack) {
		return BuiltInRegistries.ITEM.getId(stack.getItem());
	}

	/** "namespace:path" do item. */
	public static String idString(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/** Abre a tela de receitas que produzem o item (se houver). Devolve false se nao ha receita. */
	public static boolean openRecipes(ItemStack stack, Screen parent) {
		return open(stack, parent, RecipeScreen.Mode.RECIPES);
	}

	/** Abre a tela de usos do item (se houver). */
	public static boolean openUsages(ItemStack stack, Screen parent) {
		return open(stack, parent, RecipeScreen.Mode.USAGES);
	}

	private static boolean open(ItemStack stack, Screen parent, RecipeScreen.Mode mode) {
		if (stack.isEmpty()) {
			return false;
		}
		int id = idOf(stack);
		List<RecipeView> recipes = mode == RecipeScreen.Mode.RECIPES ? index.recipesFor(id) : index.usagesOf(id);
		if (recipes.isEmpty()) {
			return false;
		}
		Screen root = parent instanceof RecipeScreen recipeScreen ? recipeScreen.root() : parent;
		Minecraft.getInstance().gui.setScreen(new RecipeScreen(root, parent, stack, mode, recipes));
		return true;
	}
}
