package com.mlin.notas.client.storage;

import com.mlin.notas.client.screen.GridScreenBackground;
import com.mlin.notas.storage.CountFormat;
import com.mlin.notas.storage.TerminalMenu;
import com.mlin.notas.storage.TerminalPayloads;
import com.mlin.notas.storage.TerminalQuery;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tela do terminal: grade rolavel (9 x 6) com tudo que a rede guarda, quantidade compacta no canto (1.2k, 34M),
 * busca (texto ou {@code @mod}), ordem por nome/quantidade/mod e inventario do jogador embaixo. Clique esquerdo tira
 * uma pilha (ou guarda o que esta no cursor), direito tira meia pilha (ou guarda 1), Shift+clique manda uma pilha
 * para o inventario; Shift+clique no inventario guarda na rede. A variante de crafting tem a grade 3x3 a esquerda.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
	private static final int BAR_WIDTH = 12;
	private static final int THUMB_HEIGHT = 15;
	private static final int SCREEN_BG = 0xFF1E2A2E;
	private static final int TEXT = 0xFF404040;

	// lembrados entre aberturas (so nesta sessao do jogo)
	private static String lastSearch = "";
	private static TerminalQuery.SortMode lastSort = TerminalQuery.SortMode.COUNT;
	private static boolean lastReversed;

	private EditBox search;
	private Button sortButton;
	private Button orderButton;
	private List<TerminalQuery.Entry<Integer>> view = List.of();
	private int viewVersion = -1;
	private String viewSearch = "";
	private int scrollRow;
	private boolean draggingBar;

	public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.offsetX() + TerminalMenu.MAIN_WIDTH, TerminalMenu.HEIGHT);
		this.titleLabelX = menu.offsetX() + 8;
		this.titleLabelY = 6;
	}

	@Override
	protected void init() {
		super.init();
		int ox = leftPos + menu.offsetX();
		search = new EditBox(font, ox + 99, topPos + 4, 72, 12, Component.translatable("gui.mlinnotas.storage.search"));
		search.setMaxLength(64);
		search.setHint(Component.translatable("gui.mlinnotas.storage.search.hint").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(lastSearch);
		search.setResponder(text -> {
			lastSearch = text;
			scrollRow = 0;
			refreshView(true);
		});
		search.setTooltip(Tooltip.create(Component.translatable("gui.mlinnotas.storage.search.tooltip")));
		addRenderableWidget(search);
		sortButton = Button.builder(sortLabel(), b -> {
			lastSort = lastSort.next();
			b.setMessage(sortLabel());
			refreshView(true);
		}).bounds(ox + 118, topPos + TerminalMenu.STATUS_Y - 3, 42, 13)
				.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.storage.sort.tooltip"))).build();
		addRenderableWidget(sortButton);
		orderButton = Button.builder(orderLabel(), b -> {
			lastReversed = !lastReversed;
			b.setMessage(orderLabel());
			refreshView(true);
		}).bounds(ox + 162, topPos + TerminalMenu.STATUS_Y - 3, 13, 13)
				.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.storage.order.tooltip"))).build();
		addRenderableWidget(orderButton);
		if (menu.crafting()) {
			addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.storage.clear"),
					b -> send(TerminalPayloads.Action.CLEAR_GRID, -1))
					.bounds(leftPos + 8, topPos + 136, 64, 14)
					.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.storage.clear.tooltip"))).build());
		}
		refreshView(true);
	}

	private static Component sortLabel() {
		return Component.translatable("gui.mlinnotas.storage.sort." + lastSort.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static Component orderLabel() {
		return Component.literal(lastReversed ? "↑" : "↓");
	}

	/** Refaz a lista filtrada/ordenada quando chega sincronizacao nova ou a busca/ordem muda. */
	private void refreshView(boolean force) {
		String text = search == null ? "" : search.getValue();
		if (!force && viewVersion == menu.clientVersion() && text.equals(viewSearch)) {
			return;
		}
		viewVersion = menu.clientVersion();
		viewSearch = text;
		List<TerminalQuery.Entry<Integer>> entries = new ArrayList<>(menu.clientCounts().size());
		for (Map.Entry<Integer, Long> entry : menu.clientCounts().entrySet()) {
			ItemStack stack = menu.clientItems().get(entry.getKey());
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			entries.add(new TerminalQuery.Entry<>(entry.getKey(), stack.getHoverName().getString(), id.toString(), entry.getValue()));
		}
		view = TerminalQuery.apply(entries, text, lastSort, lastReversed);
		scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));
	}

	private int totalRows() {
		return (view.size() + TerminalMenu.COLUMNS - 1) / TerminalMenu.COLUMNS;
	}

	private int maxScroll() {
		return Math.max(0, totalRows() - TerminalMenu.ROWS);
	}

	private int gridLeft() {
		return leftPos + menu.offsetX() + TerminalMenu.GRID_X;
	}

	private int gridTop() {
		return topPos + TerminalMenu.GRID_Y;
	}

	private int barLeft() {
		return gridLeft() + TerminalMenu.COLUMNS * TerminalMenu.SLOT + 3;
	}

	/** Indice na lista sob o mouse, -1 fora da grade, -2 numa casa vazia da grade. */
	private int indexAt(double mouseX, double mouseY) {
		int col = (int) Math.floor((mouseX - (gridLeft() - 1)) / TerminalMenu.SLOT);
		int row = (int) Math.floor((mouseY - (gridTop() - 1)) / TerminalMenu.SLOT);
		if (mouseX < gridLeft() - 1 || mouseY < gridTop() - 1 || col >= TerminalMenu.COLUMNS || row >= TerminalMenu.ROWS) {
			return -1;
		}
		int index = (scrollRow + row) * TerminalMenu.COLUMNS + col;
		return index < view.size() ? index : -2;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshView(false);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		refreshView(false);
		int ox = leftPos + menu.offsetX();
		if (menu.crafting()) {
			extractCraftingPanel(graphics);
		}
		GridScreenBackground.panel(graphics, ox, topPos, TerminalMenu.MAIN_WIDTH, imageHeight);
		// "tela" escura atras da lista, com moldura afundada
		int gx = gridLeft() - 1;
		int gy = gridTop() - 1;
		int gw = TerminalMenu.COLUMNS * TerminalMenu.SLOT;
		int gh = TerminalMenu.ROWS * TerminalMenu.SLOT;
		graphics.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, GridScreenBackground.SLOT_DARK);
		graphics.fill(gx, gy, gx + gw + 1, gy + gh + 1, GridScreenBackground.SLOT_LIGHT);
		graphics.fill(gx, gy, gx + gw, gy + gh, SCREEN_BG);
		for (int r = 0; r < TerminalMenu.ROWS; r++) {
			for (int c = 0; c < TerminalMenu.COLUMNS; c++) {
				int x = gx + c * TerminalMenu.SLOT;
				int y = gy + r * TerminalMenu.SLOT;
				graphics.fill(x + 1, y + 1, x + TerminalMenu.SLOT - 1, y + TerminalMenu.SLOT - 1, 0xFF26363B);
			}
		}
		extractScrollBar(graphics);
		GridScreenBackground.playerInventory(graphics, ox + TerminalMenu.GRID_X, topPos + TerminalMenu.INV_Y);
	}

	private void extractCraftingPanel(GuiGraphicsExtractor graphics) {
		int x = leftPos;
		int y = topPos;
		GridScreenBackground.panel(graphics, x, y, TerminalMenu.CRAFT_WIDTH + 6, 156);
		GridScreenBackground.grid(graphics, x + TerminalMenu.CRAFT_GRID_X, y + TerminalMenu.CRAFT_GRID_Y, 3, 3);
		// seta para baixo ate o resultado
		int ax = x + TerminalMenu.RESULT_X + 3;
		int ay = y + TerminalMenu.CRAFT_GRID_Y + 3 * TerminalMenu.SLOT + 2;
		graphics.fill(ax + 3, ay, ax + 7, ay + 8, GridScreenBackground.DARK);
		graphics.fill(ax, ay + 8, ax + 10, ay + 10, GridScreenBackground.DARK);
		graphics.fill(ax + 1, ay + 10, ax + 9, ay + 11, GridScreenBackground.DARK);
		graphics.fill(ax + 2, ay + 11, ax + 8, ay + 12, GridScreenBackground.DARK);
		graphics.fill(ax + 3, ay + 12, ax + 7, ay + 13, GridScreenBackground.DARK);
		graphics.fill(ax + 4, ay + 13, ax + 6, ay + 14, GridScreenBackground.DARK);
		// resultado: casa maior
		int rx = x + TerminalMenu.RESULT_X - 5;
		int ry = y + TerminalMenu.RESULT_Y - 5;
		graphics.fill(rx, ry, rx + 26, ry + 26, GridScreenBackground.SLOT_DARK);
		graphics.fill(rx + 1, ry + 1, rx + 26, ry + 26, GridScreenBackground.SLOT_LIGHT);
		graphics.fill(rx + 1, ry + 1, rx + 25, ry + 25, GridScreenBackground.SLOT_BG);
	}

	private void extractScrollBar(GuiGraphicsExtractor graphics) {
		int left = barLeft();
		int top = gridTop() - 1;
		int height = TerminalMenu.ROWS * TerminalMenu.SLOT;
		graphics.fill(left, top, left + BAR_WIDTH, top + height, GridScreenBackground.SLOT_DARK);
		graphics.fill(left + 1, top + 1, left + BAR_WIDTH, top + height, GridScreenBackground.SLOT_LIGHT);
		graphics.fill(left + 1, top + 1, left + BAR_WIDTH - 1, top + height - 1, GridScreenBackground.SLOT_BG);
		int max = maxScroll();
		int thumbY = top + 1 + (max == 0 ? 0 : (height - 2 - THUMB_HEIGHT) * scrollRow / max);
		int light = max == 0 ? GridScreenBackground.PANEL : GridScreenBackground.LIGHT;
		graphics.fill(left + 1, thumbY, left + BAR_WIDTH - 1, thumbY + THUMB_HEIGHT, GridScreenBackground.DARK);
		graphics.fill(left + 1, thumbY, left + BAR_WIDTH - 2, thumbY + THUMB_HEIGHT - 1, light);
		graphics.fill(left + 2, thumbY + 1, left + BAR_WIDTH - 2, thumbY + THUMB_HEIGHT - 1, GridScreenBackground.PANEL);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
		if (menu.crafting()) {
			graphics.text(font, Component.translatable("gui.mlinnotas.storage.crafting"), 8, 6, TEXT, false);
		}
		// itens da rede: desenhados aqui (ja transladado para leftPos/topPos)
		int gx = gridLeft() - leftPos;
		int gy = gridTop() - topPos;
		int hovered = indexAt(mouseX, mouseY);
		for (int r = 0; r < TerminalMenu.ROWS; r++) {
			for (int c = 0; c < TerminalMenu.COLUMNS; c++) {
				int index = (scrollRow + r) * TerminalMenu.COLUMNS + c;
				if (index >= view.size()) {
					break;
				}
				TerminalQuery.Entry<Integer> entry = view.get(index);
				ItemStack stack = menu.clientItems().get(entry.handle());
				int x = gx + c * TerminalMenu.SLOT;
				int y = gy + r * TerminalMenu.SLOT;
				if (index == hovered) {
					graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
				}
				graphics.item(stack, x, y);
				graphics.itemDecorations(font, stack, x, y, "");
			}
		}
		// quantidades por cima dos itens (outra camada)
		graphics.nextStratum();
		for (int r = 0; r < TerminalMenu.ROWS; r++) {
			for (int c = 0; c < TerminalMenu.COLUMNS; c++) {
				int index = (scrollRow + r) * TerminalMenu.COLUMNS + c;
				if (index >= view.size()) {
					break;
				}
				extractCount(graphics, view.get(index).count(), gx + c * TerminalMenu.SLOT, gy + r * TerminalMenu.SLOT);
			}
		}
		// resumo da rede
		long items = 0;
		for (TerminalQuery.Entry<Integer> entry : view) {
			items += entry.count();
		}
		Component status;
		if (menu.clientUnits() == 0 && menu.clientContainers() == 0) {
			status = Component.translatable("gui.mlinnotas.storage.no_network").withStyle(ChatFormatting.DARK_RED);
		} else {
			status = Component.translatable("gui.mlinnotas.storage.status", view.size(), CountFormat.compact(items));
		}
		graphics.text(font, status, menu.offsetX() + 9, TerminalMenu.STATUS_Y, TEXT, false);
	}

	/** Quantidade no canto inferior direito: ate 99 como no vanilla, o resto (999, 1.2k, 34M) em meia escala. */
	private void extractCount(GuiGraphicsExtractor graphics, long count, int x, int y) {
		if (count == 1) {
			return;
		}
		String text = CountFormat.compact(count);
		if (count < 100) {
			graphics.text(font, text, x + 17 - font.width(text), y + 9, 0xFFFFFFFF, true);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 16.5F, y + 12.5F);
		graphics.pose().scale(0.5F, 0.5F);
		graphics.text(font, text, -font.width(text), 0, 0xFFFFFFFF, true);
		graphics.pose().popMatrix();
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int index = indexAt(mouseX, mouseY);
		if (index < 0 || !menu.getCarried().isEmpty()) {
			if (barHovered(mouseX, mouseY) && menu.clientTruncated()) {
				graphics.setTooltipForNextFrame(font, Component.translatable("gui.mlinnotas.storage.truncated"), mouseX, mouseY);
			}
			return;
		}
		TerminalQuery.Entry<Integer> entry = view.get(index);
		ItemStack stack = menu.clientItems().get(entry.handle());
		List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
		lines.add(Component.translatable("gui.mlinnotas.storage.count", CountFormat.full(entry.count())).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("gui.mlinnotas.storage.hint").withStyle(ChatFormatting.DARK_GRAY));
		graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), mouseX, mouseY, stack.get(DataComponents.TOOLTIP_STYLE));
	}

	private boolean barHovered(double mouseX, double mouseY) {
		return mouseX >= barLeft() && mouseX < barLeft() + BAR_WIDTH && mouseY >= gridTop() - 1
				&& mouseY < gridTop() - 1 + TerminalMenu.ROWS * TerminalMenu.SLOT;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (search.isMouseOver(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			search.setValue("");
			return true;
		}
		int index = indexAt(event.x(), event.y());
		if (index != -1 && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
			setFocused(null);
			search.setFocused(false);
			int id = index >= 0 ? view.get(index).handle() : -1;
			int action;
			if (event.hasShiftDown() && menu.getCarried().isEmpty()) {
				action = TerminalPayloads.Action.SHIFT;
			} else {
				action = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? TerminalPayloads.Action.LEFT : TerminalPayloads.Action.RIGHT;
			}
			if (id >= 0 || !menu.getCarried().isEmpty()) {
				send(action, id);
			}
			return true;
		}
		if (barHovered(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			draggingBar = true;
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingBar) {
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		draggingBar = false;
		return super.mouseReleased(event);
	}

	private void scrollToMouse(double mouseY) {
		int max = maxScroll();
		if (max == 0) {
			return;
		}
		double top = gridTop() - 1 + THUMB_HEIGHT / 2.0;
		double span = TerminalMenu.ROWS * TerminalMenu.SLOT - THUMB_HEIGHT;
		scrollRow = (int) Math.round(Math.max(0, Math.min(1, (mouseY - top) / span)) * max);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (indexAt(x, y) != -1 || barHovered(x, y)) {
			scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search.isFocused()) {
			if (event.isEscape()) {
				search.setFocused(false);
				setFocused(null);
				return true;
			}
			search.keyPressed(event);
			// nao deixa "E" fechar a tela nem as teclas do inventario agirem enquanto digita
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		super.removed();
	}

	private void send(int action, int id) {
		if (ClientPlayNetworking.canSend(TerminalPayloads.Action.TYPE)) {
			ClientPlayNetworking.send(new TerminalPayloads.Action(menu.containerId, action, id));
		}
	}

	/** Para os testes de cliente: centro (coordenadas da GUI) da casa {@code cell} visivel da grade (0..53). */
	public double[] cellCenter(int cell) {
		return new double[] {gridLeft() + (cell % TerminalMenu.COLUMNS) * TerminalMenu.SLOT + 8,
				gridTop() + (cell / TerminalMenu.COLUMNS) * TerminalMenu.SLOT + 8};
	}

	/** Para os testes de cliente: o item na casa visivel {@code cell}, ou vazio. */
	public ItemStack stackAt(int cell) {
		int index = scrollRow * TerminalMenu.COLUMNS + cell;
		return index < view.size() ? menu.clientItems().get(view.get(index).handle()) : ItemStack.EMPTY;
	}

	/** Para os testes de cliente: rola ate a linha pedida. */
	public void scrollTo(int row) {
		scrollRow = Math.max(0, Math.min(maxScroll(), row));
	}

	/** Para os testes de cliente: quantos itens a lista mostra agora. */
	public int visibleEntries() {
		return view.size();
	}

	public EditBox searchBox() {
		return search;
	}
}
