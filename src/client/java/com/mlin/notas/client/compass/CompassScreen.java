package com.mlin.notas.client.compass;

import com.mlin.notas.compass.CompassCatalog;
import com.mlin.notas.compass.CompassKind;
import com.mlin.notas.compass.ExplorerCompassItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista da bussola: busca no topo (nome ou id, {@code @ns} filtra o mod), lista rolavel com o alvo atual em verde;
 * clicar numa linha manda o servidor procurar e fecha a tela.
 */
public class CompassScreen extends Screen {
	private static final int ROW = 13;
	private static final int WIDTH = 260;

	private final CompassKind kind;
	private final List<String> entries;
	private final String current;
	private final Map<String, String> names = new HashMap<>();
	private EditBox search;
	private String query = "";
	private List<String> shown = List.of();
	private int scroll;

	public CompassScreen(CompassKind kind, List<String> entries, String current) {
		super(Component.translatable("gui.mlinnotas.compass." + kind.id() + ".title"));
		this.kind = kind;
		this.entries = List.copyOf(entries);
		this.current = current;
		for (String id : entries) {
			names.put(id, ExplorerCompassItem.targetName(kind, id).getString());
		}
	}

	public List<String> shown() {
		return shown;
	}

	/** Para testes: escreve na busca. */
	public void setQuery(String text) {
		if (search != null) {
			search.setValue(text);
		}
		refilter(text);
	}

	@Override
	protected void init() {
		int left = (width - WIDTH) / 2;
		search = new EditBox(font, left, 24, WIDTH, 18, Component.translatable("gui.mlinnotas.compass.search"));
		search.setHint(Component.translatable("gui.mlinnotas.compass.search"));
		search.setValue(query);
		search.setResponder(this::refilter);
		addRenderableWidget(search);
		setInitialFocus(search);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 - 50, height - 26, 100, 20).build());
		refilter(query);
		// abre rolado ate o alvo atual
		int index = shown.indexOf(current);
		if (index >= 0) {
			scroll = clampScroll(index - visibleRows() / 2);
		}
	}

	private void refilter(String text) {
		query = text;
		shown = CompassCatalog.filter(entries, text, id -> names.getOrDefault(id, id));
		scroll = clampScroll(scroll);
	}

	private int listTop() {
		return 48;
	}

	private int listBottom() {
		return height - 32;
	}

	private int visibleRows() {
		return Math.max(1, (listBottom() - listTop()) / ROW);
	}

	private int clampScroll(int value) {
		return Math.max(0, Math.min(value, Math.max(0, shown.size() - visibleRows())));
	}

	private int rowAt(double mouseX, double mouseY) {
		int left = (width - WIDTH) / 2;
		if (mouseX < left || mouseX >= left + WIDTH || mouseY < listTop() || mouseY >= listTop() + visibleRows() * ROW) {
			return -1;
		}
		int index = scroll + (int) ((mouseY - listTop()) / ROW);
		return index < shown.size() ? index : -1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int left = (width - WIDTH) / 2;
		graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
		int top = listTop();
		int rows = visibleRows();
		graphics.fill(left - 2, top - 2, left + WIDTH + 2, top + rows * ROW + 2, 0xA0000000);
		if (shown.isEmpty()) {
			graphics.centeredText(font, Component.translatable(entries.isEmpty() ? "gui.mlinnotas.compass.none" : "gui.mlinnotas.compass.no_match"),
					width / 2, top + 8, 0xFFA0A0A0);
		}
		int hovered = rowAt(mouseX, mouseY);
		for (int i = 0; i < rows && scroll + i < shown.size(); i++) {
			String id = shown.get(scroll + i);
			int y = top + i * ROW;
			if (scroll + i == hovered) {
				graphics.fill(left, y, left + WIDTH - 6, y + ROW, 0x40FFFFFF);
			}
			boolean isCurrent = id.equals(current);
			String name = names.getOrDefault(id, id);
			graphics.text(font, (isCurrent ? "> " : "") + name, left + 3, y + 3, isCurrent ? 0xFF80FF80 : 0xFFFFFFFF);
			String ns = id.substring(0, Math.max(0, id.indexOf(':')));
			int nsWidth = font.width(ns);
			graphics.text(font, ns, left + WIDTH - 10 - nsWidth, y + 3, 0xFF707070);
		}
		// barra de rolagem
		if (shown.size() > rows) {
			int trackHeight = rows * ROW;
			int barHeight = Math.max(10, trackHeight * rows / shown.size());
			int barY = top + (trackHeight - barHeight) * scroll / Math.max(1, shown.size() - rows);
			graphics.fill(left + WIDTH - 4, top, left + WIDTH, top + trackHeight, 0xFF303030);
			graphics.fill(left + WIDTH - 4, barY, left + WIDTH, barY + barHeight, 0xFFA0A0A0);
		}
		graphics.text(font, Component.translatable("gui.mlinnotas.compass.count", shown.size()), left, height - 20, 0xFF909090);
		if (hovered >= 0) {
			graphics.setTooltipForNextFrame(font, List.of(Component.literal(shown.get(hovered)).getVisualOrderText(),
					Component.translatable("gui.mlinnotas.compass.click").getVisualOrderText()), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int row = rowAt(event.x(), event.y());
		if (row >= 0) {
			CompassClient.select(kind, shown.get(row));
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = clampScroll(scroll - (int) Math.signum(scrollY) * 3);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
