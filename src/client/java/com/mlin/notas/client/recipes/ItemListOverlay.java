package com.mlin.notas.client.recipes;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
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
	private static final int NAV_HEIGHT = 14;
	private static final int NAV_BUTTON = 16;

	/** Ultima busca digitada, mantida entre telas como no JEI. */
	private static String lastSearch = "";

	private ItemListOverlay() {
	}

	public static void attach(AbstractContainerScreen<?> screen) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		int x0 = accessor.mlinnotas$leftPos() + accessor.mlinnotas$imageWidth() + GAP_AFTER_GUI;
		int x1 = screen.width - MARGIN;
		int columns = (x1 - x0) / ItemGridWidget.CELL;
		// busca, linha de navegacao (botoes de pagina), grade
		int rows = (screen.height - MARGIN * 2 - SEARCH_HEIGHT - 4 - NAV_HEIGHT - 2) / ItemGridWidget.CELL;
		if (columns < 2 || rows < 2) {
			return; // tela estreita demais
		}
		int gridWidth = columns * ItemGridWidget.CELL;
		int gridX = x1 - gridWidth;

		ItemGridWidget grid = new ItemGridWidget(screen, gridX, MARGIN + SEARCH_HEIGHT + 4 + NAV_HEIGHT + 2, columns, rows, RecipeViewerClient.allItems());
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

		int navY = MARGIN + SEARCH_HEIGHT + 3;
		Screens.getWidgets(screen).add(Button.builder(Component.literal("<"), b -> grid.previousPage())
				.bounds(gridX, navY, NAV_BUTTON, NAV_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.recipes.previous_page"))).build());
		Screens.getWidgets(screen).add(Button.builder(Component.literal(">"), b -> grid.nextPage())
				.bounds(gridX + gridWidth - NAV_BUTTON, navY, NAV_BUTTON, NAV_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.recipes.next_page"))).build());

		// AbstractContainerScreen nao repassa a roda do mouse para widgets: tratamos aqui.
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
				!grid.mouseScrolled(mouseX, mouseY, horizontal, vertical));

		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> handleKey(screen, accessor, grid, search, event));
	}

	private static boolean handleKey(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor,
			ItemGridWidget grid, EditBox search, KeyEvent event) {
		if (search.isFocused()) {
			// Digitando: Esc so tira o foco; as demais teclas vao para a caixa (e nao fecham o inventario com "E").
			if (event.isEscape()) {
				search.setFocused(false);
				return false;
			}
			search.keyPressed(event);
			return false;
		}
		if (screen.getFocused() instanceof EditBox own && own.isFocused()) {
			// a tela tem a propria caixa de texto (ex.: busca do terminal de armazenamento): R/U sao letras
			return true;
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
