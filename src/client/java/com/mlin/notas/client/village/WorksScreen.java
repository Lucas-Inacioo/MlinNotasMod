package com.mlin.notas.client.village;

import com.mlin.notas.village.works.WorkCategory;
import com.mlin.notas.village.works.WorkSpec;
import com.mlin.notas.village.works.WorksPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Obras publicas gratuitas da vila: a fila (nome, categoria, andamento) e um botao por categoria para ligar e desligar
 * ("nao mexer nas ruas", "nas fachadas"...). Abre pelo botao da aba Obras do quadro; o servidor manda a fila quando a tela
 * abre, a cada segundo e depois de cada botao.
 */
public class WorksScreen extends Screen {
	private static final int PANEL = 0xF0201A14;
	private static final int PANEL_EDGE = 0xFF8A6A3C;
	private static final int ROW = 0x40000000;
	private static final int GOLD = 0xFFFFD36A;
	private static final int ROW_H = 14;

	private final BlockPos board;
	private final Screen parent;
	private WorksPayloads.Sync data;
	private int scroll;
	private int tick;

	public WorksScreen(BlockPos board, Screen parent) {
		super(Component.translatable("gui.mlinnotas.village.works.title"));
		this.board = board;
		this.parent = parent;
	}

	public void update(WorksPayloads.Sync sync) {
		this.data = sync;
		rebuildWidgets();
	}

	/** Linhas visiveis: primeiro o que esta andando e o que espera, depois o que ja ficou pronto. */
	private List<WorksPayloads.Row> rows() {
		List<WorksPayloads.Row> out = new ArrayList<>();
		if (data == null) {
			return out;
		}
		for (int pass = 0; pass < 2; pass++) {
			for (WorksPayloads.Row row : data.rows()) {
				boolean pending = row.state() <= 1;
				if (pass == 0 ? pending : !pending) {
					out.add(row);
				}
			}
		}
		return out;
	}

	private int panelW() {
		return Math.min(404, width - 12);
	}

	private int panelH() {
		return Math.min(236, height - 6);
	}

	private int left() {
		return (width - panelW()) / 2;
	}

	private int top() {
		return (height - panelH()) / 2;
	}

	private int listRight() {
		return left() + panelW() - 130;
	}

	private int visibleRows() {
		return Math.max(1, (top() + panelH() - 30 - (top() + 36)) / ROW_H);
	}

	@Override
	protected void init() {
		int x = listRight() + 8;
		int y = top() + 36;
		for (WorkCategory category : WorkCategory.values()) {
			if (category == WorkCategory.FACADES) {
				continue;
			}
			boolean on = data == null || (data.disabled() & category.bit()) == 0;
			Component label = Component.translatable("gui.mlinnotas.village.works.cat." + category.id()).append(": ")
					.append(Component.translatable(on ? "gui.mlinnotas.village.works.on" : "gui.mlinnotas.village.works.off"));
			Button button = Button.builder(label, b -> VillageClient.send(board, "works", "toggle:" + category.id())).bounds(x, y, 114, 16)
					.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.village.works.cat." + category.id() + ".tooltip"))).build();
			addRenderableWidget(button);
			y += 18;
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(left() + panelW() - 76, top() + panelH() - 24, 68, 18).build());
		VillageClient.send(board, "works", "list");
	}

	@Override
	public void tick() {
		if (++tick % 20 == 0) {
			VillageClient.send(board, "works", "list");
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(left(), top(), left() + panelW(), top() + panelH(), PANEL);
		graphics.outline(left(), top(), panelW(), panelH(), PANEL_EDGE);
		graphics.outline(left() + 2, top() + 2, panelW() - 4, panelH() - 4, 0xFF4A3A22);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left() + 8, top() + 7, GOLD);
		graphics.text(font, Component.translatable("gui.mlinnotas.village.works.hint").withStyle(ChatFormatting.GRAY), left() + 8, top() + 20, 0xFFFFFFFF);
		if (data != null && data.paidJobRunning()) {
			graphics.text(font, Component.translatable("gui.mlinnotas.village.works.paid_running").withStyle(ChatFormatting.YELLOW), left() + 8,
					top() + panelH() - 20, 0xFFFFFFFF);
		}
		List<WorksPayloads.Row> rows = rows();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
		int y = top() + 36;
		int x = left() + 8;
		int right = listRight();
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("gui.mlinnotas.village.works.empty").withStyle(ChatFormatting.DARK_GRAY), x, y, 0xFFFFFFFF);
			return;
		}
		for (int i = scroll; i < Math.min(rows.size(), scroll + visibleRows()); i++) {
			WorksPayloads.Row row = rows.get(i);
			graphics.fill(x, y, right, y + ROW_H - 1, ROW);
			WorkSpec spec = WorkSpec.byId(row.id());
			Component name = spec == null ? Component.literal(row.id()) : Component.translatable(spec.langKey());
			boolean disabled = spec != null && data != null && (data.disabled() & spec.category().bit()) != 0;
			graphics.text(font, name.copy().withStyle(row.state() >= 2 ? ChatFormatting.GRAY : disabled ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE), x + 3,
					y + 3, 0xFFFFFFFF);
			Component status = switch (row.state()) {
				case 1 -> Component.translatable("gui.mlinnotas.village.works.state.running", row.total() <= 0 ? 0 : 100 * row.done() / row.total());
				case 2 -> Component.translatable("gui.mlinnotas.village.works.state.done");
				case 3 -> Component.translatable("gui.mlinnotas.village.works.state.replaced");
				default -> Component.translatable(disabled ? "gui.mlinnotas.village.works.state.off" : "gui.mlinnotas.village.works.state.queued");
			};
			int color = switch (row.state()) {
				case 1 -> 0xFFFFD36A;
				case 2 -> 0xFF6FCF6F;
				case 3 -> 0xFF8A8A8A;
				default -> disabled ? 0xFF8A5050 : 0xFFB0B0B0;
			};
			graphics.text(font, status, right - 4 - font.width(status), y + 3, color);
			y += ROW_H;
		}
		if (rows.size() > visibleRows()) {
			int trackTop = top() + 36;
			int trackH = visibleRows() * ROW_H;
			int thumb = Math.max(8, trackH * visibleRows() / rows.size());
			int thumbY = trackTop + (trackH - thumb) * scroll / Math.max(1, rows.size() - visibleRows());
			graphics.fill(right + 1, trackTop, right + 4, trackTop + trackH, 0x40000000);
			graphics.fill(right + 1, thumbY, right + 4, thumbY + thumb, 0xFFB09050);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 2);
		return true;
	}
}
