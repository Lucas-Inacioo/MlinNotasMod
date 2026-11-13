package com.mlin.notas.client.recipes;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Anexa a caixa de busca e a grade de itens a direita de uma tela de container.
 * Teclas: R = receitas do item sob o cursor (grade ou slot), U = usos.
 */
public final class ItemListOverlay {
	/** Espaco reservado a direita da moldura para o botao "Ord". */
	private static final int GAP_AFTER_GUI = 30;
	private static final int MARGIN = 4;
	private static final int SEARCH_HEIGHT = 14;

	/** Ultima busca digitada, mantida entre telas como no JEI. */
	private static String lastSearch = "";

	private ItemListOverlay() {
	}

	public static void attach(AbstractContainerScreen<?> screen) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int x0 = accessor.mlinnotas$leftPos() + accessor.mlinnotas$imageWidth() + GAP_AFTER_GUI;
		int x1 = screen.width - MARGIN;
		int columns = (x1 - x0) / ItemGridWidget.CELL;
		int rows = (screen.height - MARGIN * 2 - SEARCH_HEIGHT - 4 - 12) / ItemGridWidget.CELL;
		if (columns < 2 || rows < 2) {
			return; // tela estreita demais
		}
		int gridWidth = columns * ItemGridWidget.CELL;
		int gridX = x1 - gridWidth;

		ItemGridWidget grid = new ItemGridWidget(screen, gridX, MARGIN + SEARCH_HEIGHT + 4, columns, rows, RecipeViewerClient.allItems());
		EditBox search = new EditBox(screen.getFont(), gridX, MARGIN, gridWidth, SEARCH_HEIGHT, Component.translatable("gui.mlinnotas.recipes.search"));
		search.setMaxLength(64);
		search.setHint(Component.translatable("gui.mlinnotas.recipes.search.hint"));
		search.setResponder(text -> {
			lastSearch = text;
			grid.setFilter(text);
		});
		search.setValue(lastSearch);
		grid.setFilter(lastSearch);

		Screens.getWidgets(screen).add(search);
		Screens.getWidgets(screen).add(grid);

		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> handleKey(screen, accessor, grid, search, event));
	}

	private static boolean handleKey(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor,
			ItemGridWidget grid, EditBox search, KeyEvent event) {
		if (search.isFocused()) {
			return true; // digitando: nao intercepta
		}
		boolean recipes = ModKeys.SHOW_RECIPES.matches(event);
		boolean usages = ModKeys.SHOW_USAGES.matches(event);
		if (!recipes && !usages) {
			return true;
		}
		ItemStack target = hoveredStack(screen, accessor, grid);
		if (target.isEmpty()) {
			return true;
		}
		boolean opened = recipes ? RecipeViewerClient.openRecipes(target, screen) : RecipeViewerClient.openUsages(target, screen);
		return !opened;
	}

	private static ItemStack hoveredStack(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor, ItemGridWidget grid) {
		Minecraft minecraft = Minecraft.getInstance();
		double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
		double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
		ItemStack fromGrid = grid.hoveredStack(mouseX, mouseY);
		if (!fromGrid.isEmpty()) {
			return fromGrid;
		}
		Slot slot = accessor.mlinnotas$hoveredSlot();
		return slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
	}
}
