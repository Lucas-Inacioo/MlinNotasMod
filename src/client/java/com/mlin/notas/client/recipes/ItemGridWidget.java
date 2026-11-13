package com.mlin.notas.client.recipes;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grade paginada de itens (lista do visualizador de receitas). Clique esquerdo = receitas, direito = usos.
 */
public class ItemGridWidget extends AbstractWidget {
	public static final int CELL = 18;

	private final Screen owner;
	private final List<ItemStack> source;
	private final List<ItemStack> filtered = new ArrayList<>();
	private final int columns;
	private final int rows;
	private int page;
	private String filter = "";

	public ItemGridWidget(Screen owner, int x, int y, int columns, int rows, List<ItemStack> source) {
		super(x, y, columns * CELL, rows * CELL, Component.translatable("gui.mlinnotas.recipes.items"));
		this.owner = owner;
		this.source = source;
		this.columns = Math.max(1, columns);
		this.rows = Math.max(1, rows);
		setFilter(filter);
	}

	public void setFilter(String text) {
		this.filter = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		filtered.clear();
		for (ItemStack stack : source) {
			if (matches(stack, filter)) {
				filtered.add(stack);
			}
		}
		page = 0;
	}

	/** Filtro: texto no nome; "@mod" filtra pelo namespace; "#" sem nada lista tudo. */
	static boolean matches(ItemStack stack, String filter) {
		if (filter.isEmpty()) {
			return true;
		}
		String id = RecipeViewerClient.idString(stack);
		if (filter.startsWith("@")) {
			String namespace = filter.substring(1);
			return id.startsWith(namespace);
		}
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains(filter) || id.contains(filter);
	}

	public int perPage() {
		return columns * rows;
	}

	public int pageCount() {
		return Math.max(1, (filtered.size() + perPage() - 1) / perPage());
	}

	public int page() {
		return page;
	}

	public void nextPage() {
		page = (page + 1) % pageCount();
	}

	public void previousPage() {
		page = (page - 1 + pageCount()) % pageCount();
	}

	/** Item sob o cursor, ou vazio. */
	public ItemStack hoveredStack(double mouseX, double mouseY) {
		if (mouseX < getX() || mouseY < getY() || mouseX >= getX() + columns * CELL || mouseY >= getY() + rows * CELL) {
			return ItemStack.EMPTY;
		}
		int col = (int) ((mouseX - getX()) / CELL);
		int row = (int) ((mouseY - getY()) / CELL);
		int index = page * perPage() + row * columns + col;
		return index < filtered.size() ? filtered.get(index) : ItemStack.EMPTY;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int start = page * perPage();
		for (int i = 0; i < perPage() && start + i < filtered.size(); i++) {
			int x = getX() + (i % columns) * CELL + 1;
			int y = getY() + (i / columns) * CELL + 1;
			ItemStack stack = filtered.get(start + i);
			graphics.item(stack, x, y);
			graphics.itemDecorations(owner.getFont(), stack, x, y);
		}
		ItemStack hovered = hoveredStack(mouseX, mouseY);
		if (!hovered.isEmpty()) {
			int col = (int) ((mouseX - getX()) / CELL);
			int row = (int) ((mouseY - getY()) / CELL);
			int x = getX() + col * CELL + 1;
			int y = getY() + row * CELL + 1;
			graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
			graphics.setTooltipForNextFrame(owner.getFont(), hovered, mouseX, mouseY);
		}
		String pageText = (page + 1) + "/" + pageCount();
		graphics.centeredText(owner.getFont(), pageText, getX() + getWidth() / 2, getY() + getHeight() + 2, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!visible || !active) {
			return false;
		}
		ItemStack hovered = hoveredStack(event.x(), event.y());
		if (hovered.isEmpty()) {
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			return RecipeViewerClient.openRecipes(hovered, owner);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			return RecipeViewerClient.openUsages(hovered, owner);
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (!isMouseOver(x, y)) {
			return false;
		}
		if (scrollY < 0) {
			nextPage();
		} else if (scrollY > 0) {
			previousPage();
		}
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
	}
}
