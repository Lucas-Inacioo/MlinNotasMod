package com.mlin.notas.client.quests;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.skills.SkillTreeScreen;
import com.mlin.notas.quests.Quest;
import com.mlin.notas.quests.QuestBook;
import com.mlin.notas.quests.QuestChapter;
import com.mlin.notas.quests.QuestPayloads;
import com.mlin.notas.quests.QuestProgress;
import com.mlin.notas.quests.QuestReward;
import com.mlin.notas.quests.QuestTask;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tela do livro de quests (tecla Y ou item): capitulos a esquerda, grafo do capitulo no meio (arrastar/roda para mover,
 * linhas de dependencia), painel de detalhe a direita quando uma quest esta selecionada (descricao, tarefas com
 * progresso, recompensas e botoes Entregar / Marcar / Resgatar / Arvore). Cores: cinza = bloqueada, amarelo = disponivel,
 * dourado com "!" = recompensa para resgatar, verde = concluida.
 */
public class QuestBookScreen extends Screen {
	public static final int NODE = 22;
	public static final int CELL = 30;
	static final int PAD = 4;
	static final int HEADER = 18;
	static final int ROW = 22;

	private static final int PANEL = 0xD0101014;
	private static final int PANEL_EDGE = 0xFF3A3A44;
	private static final int LINE_DONE = 0xFF6BD86B;
	private static final int LINE_READY = 0xFFE0C050;
	private static final int LINE_OFF = 0xFF505058;

	/** Ultimo capitulo aberto (volta nele ao reabrir). */
	private static String lastChapter;

	private QuestChapter chapter;
	private Quest selected;
	private double panX;
	private double panY;
	private boolean dragging;
	private boolean dragMoved;
	private double detailScroll;
	private int detailContentHeight;
	private int chapterScroll;

	public QuestBookScreen() {
		super(Component.translatable("gui.mlinnotas.quests.title"));
	}

	// ---- layout ----

	int listWidth() {
		return compactList() ? ROW + 4 : Mth.clamp(width / 4, 92, 124);
	}

	/** So icones na lista de capitulos: tela estreita, ou quest aberta numa tela pequena (sobra espaco para o grafo). */
	boolean compactList() {
		return width < 380 || (selected != null && width < 560);
	}

	int detailWidth() {
		return Mth.clamp(width * 2 / 5, 150, 230);
	}

	int graphLeft() {
		return PAD + listWidth() + PAD;
	}

	int graphRight() {
		return selected != null ? width - PAD - detailWidth() - PAD : width - PAD;
	}

	int graphTop() {
		return HEADER;
	}

	int graphBottom() {
		return height - PAD;
	}

	int detailLeft() {
		return width - PAD - detailWidth();
	}

	/** Canto superior esquerdo do no da quest na tela. */
	public int nodeX(Quest quest) {
		return graphLeft() + (int) Math.round(panX + quest.x() * CELL);
	}

	public int nodeY(Quest quest) {
		return graphTop() + (int) Math.round(panY + quest.y() * CELL);
	}

	// ---- estado ----

	public QuestChapter chapter() {
		return chapter;
	}

	public Quest selected() {
		return selected;
	}

	/** Livro ou progresso novo do servidor. */
	public void refresh() {
		QuestBook book = QuestClient.book();
		if (chapter == null || book.chapter(chapter.id()) == null) {
			chapter = null;
			pickInitialChapter();
		} else {
			chapter = book.chapter(chapter.id());
		}
		if (selected != null) {
			selected = book.quest(selected.key());
		}
		rebuildWidgets();
	}

	private void pickInitialChapter() {
		QuestBook book = QuestClient.book();
		QuestProgress progress = QuestClient.progress();
		if (lastChapter != null && book.chapter(lastChapter) != null) {
			selectChapter(book.chapter(lastChapter));
			return;
		}
		for (QuestChapter c : book.chapters()) {
			for (Quest quest : c.quests()) {
				if (progress.status(quest) == QuestProgress.Status.ACTIVE) {
					selectChapter(c);
					return;
				}
			}
		}
		if (!book.chapters().isEmpty()) {
			selectChapter(book.chapters().getFirst());
		}
	}

	public void selectChapter(QuestChapter value) {
		chapter = value;
		lastChapter = value == null ? null : value.id();
		if (selected != null && (value == null || !selected.chapter().equals(value.id()))) {
			selected = null;
		}
		centerChapter();
		rebuildWidgets();
	}

	public void select(Quest quest) {
		if (quest != null && (chapter == null || !chapter.id().equals(quest.chapter()))) {
			QuestChapter c = QuestClient.book().chapter(quest.chapter());
			if (c != null) {
				chapter = c;
				lastChapter = c.id();
				centerChapter();
			}
		}
		int oldLeft = graphLeft();
		selected = quest;
		detailScroll = 0;
		if (chapter != null && fits()) {
			centerChapter(); // o grafo mudou de largura (painel/lista): recentraliza
		} else {
			panX += oldLeft - graphLeft(); // a lista encolhe/volta: o grafo fica parado na tela
			if (quest != null) {
				keepVisible(quest);
			}
		}
		rebuildWidgets();
	}

	/** Centraliza o capitulo no grafo (ou encosta no canto quando nao cabe). */
	private void centerChapter() {
		if (chapter == null || chapter.quests().isEmpty()) {
			panX = panY = 8;
			return;
		}
		double[] box = bounds();
		int graphW = graphRight() - graphLeft();
		int graphH = graphBottom() - graphTop();
		double contentW = (box[2] - box[0]) * CELL + NODE;
		double contentH = (box[3] - box[1]) * CELL + NODE;
		panX = contentW + 16 <= graphW ? (graphW - contentW) / 2 - box[0] * CELL : 10 - box[0] * CELL;
		panY = contentH + 16 <= graphH ? (graphH - contentH) / 2 - box[1] * CELL : 10 - box[1] * CELL;
	}

	/** O capitulo inteiro cabe no grafo. */
	private boolean fits() {
		if (chapter.quests().isEmpty()) {
			return true;
		}
		double[] box = bounds();
		return (box[2] - box[0]) * CELL + NODE + 16 <= graphRight() - graphLeft() && (box[3] - box[1]) * CELL + NODE + 16 <= graphBottom() - graphTop();
	}

	/** minX, minY, maxX, maxY (celulas). */
	private double[] bounds() {
		double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for (Quest quest : chapter.quests()) {
			box[0] = Math.min(box[0], quest.x());
			box[1] = Math.min(box[1], quest.y());
			box[2] = Math.max(box[2], quest.x());
			box[3] = Math.max(box[3], quest.y());
		}
		return box;
	}

	private void clampPan() {
		if (chapter == null || chapter.quests().isEmpty()) {
			return;
		}
		double[] box = bounds();
		int graphW = graphRight() - graphLeft();
		int graphH = graphBottom() - graphTop();
		double a = 8 - box[0] * CELL;
		double b = graphW - 8 - NODE - box[2] * CELL;
		panX = Mth.clamp(panX, Math.min(a, b), Math.max(a, b));
		double c = 8 - box[1] * CELL;
		double d = graphH - 8 - NODE - box[3] * CELL;
		panY = Mth.clamp(panY, Math.min(c, d), Math.max(c, d));
	}

	/** Move o grafo para a quest nao ficar escondida atras do painel de detalhe. */
	private void keepVisible(Quest quest) {
		int right = graphRight() - 6;
		int x = nodeX(quest);
		if (x + NODE > right) {
			panX -= x + NODE - right;
		}
		if (x < graphLeft() + 6) {
			panX += graphLeft() + 6 - x;
		}
		int y = nodeY(quest);
		if (y + NODE > graphBottom() - 6) {
			panY -= y + NODE - (graphBottom() - 6);
		}
		if (y < graphTop() + 6) {
			panY += graphTop() + 6 - y;
		}
	}

	// ---- widgets ----

	@Override
	protected void init() {
		if (chapter == null) {
			pickInitialChapter();
		}
		if (selected == null) {
			return;
		}
		QuestProgress progress = QuestClient.progress();
		QuestProgress.View view = progress.view(selected, QuestClient.playerId());
		List<Button> buttons = new ArrayList<>();
		boolean active = view == QuestProgress.View.ACTIVE;
		if (active && hasOpen(progress, QuestTask.Type.ITEM, true)) {
			buttons.add(Button.builder(Component.translatable("gui.mlinnotas.quests.submit"),
					b -> QuestClient.action(selected, QuestPayloads.Action.SUBMIT)).build());
		}
		if (active && hasOpen(progress, QuestTask.Type.CHECKMARK, false)) {
			buttons.add(Button.builder(Component.translatable("gui.mlinnotas.quests.check"),
					b -> QuestClient.action(selected, QuestPayloads.Action.CHECK)).build());
		}
		if (view == QuestProgress.View.CLAIMABLE) {
			buttons.add(Button.builder(Component.translatable("gui.mlinnotas.quests.claim"),
					b -> QuestClient.action(selected, QuestPayloads.Action.CLAIM)).build());
		}
		if (hasOpen(progress, QuestTask.Type.SKILL, false)) {
			buttons.add(Button.builder(Component.translatable("gui.mlinnotas.quests.open_skills"),
					b -> minecraft.gui.setScreen(new SkillTreeScreen())).build());
		}
		if (buttons.isEmpty()) {
			return;
		}
		int left = detailLeft() + 4;
		int w = detailWidth() - 8;
		int gap = 4;
		int each = (w - gap * (buttons.size() - 1)) / buttons.size();
		for (int i = 0; i < buttons.size(); i++) {
			Button button = buttons.get(i);
			button.setRectangle(each, 20, left + i * (each + gap), graphBottom() - 24);
			addRenderableWidget(button);
		}
	}

	private boolean hasOpen(QuestProgress progress, QuestTask.Type type, boolean delivery) {
		for (int i = 0; i < selected.tasks().size(); i++) {
			QuestTask task = selected.tasks().get(i);
			if (task.type() == type && (!delivery || task.isDelivery()) && !progress.taskDone(selected, i)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasButtons() {
		return children().stream().anyMatch(c -> c instanceof Button);
	}

	// ---- entrada ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		QuestChapter clickedChapter = chapterAt(mx, my);
		if (clickedChapter != null) {
			selectChapter(clickedChapter);
			return true;
		}
		if (selected != null && mx >= detailLeft()) {
			if (closeHover(mx, my)) {
				select(null);
			}
			return true;
		}
		if (inGraph(mx, my)) {
			Quest quest = questAt(mx, my);
			if (quest != null) {
				select(quest);
				return true;
			}
			dragging = true;
			dragMoved = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			panX += dx;
			panY += dy;
			if (Math.abs(dx) + Math.abs(dy) > 0) {
				dragMoved = true;
			}
			clampPan();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			if (!dragMoved && selected != null) {
				select(null);
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x < graphLeft() - PAD) {
			int max = Math.max(0, QuestClient.book().chapters().size() * ROW - (graphBottom() - graphTop()));
			chapterScroll = Mth.clamp(chapterScroll - (int) Math.signum(scrollY) * ROW, 0, max);
			return true;
		}
		if (selected != null && x >= detailLeft()) {
			int visible = detailBottom() - (graphTop() + 4);
			detailScroll = Mth.clamp(detailScroll - scrollY * 12, 0, Math.max(0, detailContentHeight - visible));
			return true;
		}
		if (inGraph(x, y)) {
			if (minecraft.hasShiftDown()) {
				panX += scrollY * CELL;
			} else {
				panX += scrollX * CELL;
				panY += scrollY * CELL;
			}
			clampPan();
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ModKeys.QUESTS.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private boolean inGraph(double mx, double my) {
		return mx >= graphLeft() && mx < graphRight() && my >= graphTop() && my < graphBottom();
	}

	public Quest questAt(double mx, double my) {
		if (chapter == null || !inGraph(mx, my)) {
			return null;
		}
		for (Quest quest : chapter.quests()) {
			int x = nodeX(quest);
			int y = nodeY(quest);
			if (mx >= x && mx < x + NODE && my >= y && my < y + NODE) {
				return quest;
			}
		}
		return null;
	}

	private QuestChapter chapterAt(double mx, double my) {
		if (mx < PAD || mx >= PAD + listWidth() || my < graphTop() || my >= graphBottom()) {
			return null;
		}
		int index = (int) ((my - graphTop() - 2 + chapterScroll) / ROW);
		List<QuestChapter> chapters = QuestClient.book().chapters();
		return index >= 0 && index < chapters.size() ? chapters.get(index) : null;
	}

	/** Linha do capitulo na lista (testes de cliente). */
	public int chapterRowY(QuestChapter value) {
		return graphTop() + 2 + QuestClient.book().chapters().indexOf(value) * ROW - chapterScroll;
	}

	private boolean closeHover(double mx, double my) {
		int x = width - PAD - 12;
		int y = graphTop() + 2;
		return mx >= x && mx < x + 10 && my >= y && my < y + 10;
	}

	private int detailBottom() {
		return graphBottom() - (hasButtons() ? 28 : 4);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- desenho ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		QuestBook book = QuestClient.book();
		QuestProgress progress = QuestClient.progress();
		String player = QuestClient.playerId();

		int done = 0;
		for (QuestChapter c : book.chapters()) {
			done += progress.completedCount(c);
		}
		graphics.centeredText(font, title, (graphLeft() + graphRight()) / 2, 5, 0xFFFFFFFF);
		graphics.text(font, Component.translatable("gui.mlinnotas.quests.total", done, book.quests().size()), PAD + 2, 5, 0xFFA0A0A0);

		drawChapters(graphics, book, progress, player, mouseX, mouseY);
		List<Component> tooltip = null;
		if (book.chapters().isEmpty()) {
			graphics.centeredText(font, Component.translatable("gui.mlinnotas.quests.empty"), (graphLeft() + graphRight()) / 2, height / 2, 0xFFA0A0A0);
		} else if (chapter != null) {
			tooltip = drawGraph(graphics, progress, player, mouseX, mouseY);
		}
		if (selected != null) {
			drawDetail(graphics, progress, player, mouseX, mouseY);
		}
		// botoes por cima dos paineis
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
		}
	}

	private void drawChapters(GuiGraphicsExtractor graphics, QuestBook book, QuestProgress progress, String player, int mouseX, int mouseY) {
		int x0 = PAD;
		int x1 = PAD + listWidth();
		graphics.fill(x0, graphTop(), x1, graphBottom(), PANEL);
		graphics.outline(x0, graphTop(), x1 - x0, graphBottom() - graphTop(), PANEL_EDGE);
		graphics.enableScissor(x0 + 1, graphTop() + 1, x1 - 1, graphBottom() - 1);
		int y = graphTop() + 2 - chapterScroll;
		for (QuestChapter c : book.chapters()) {
			boolean current = chapter != null && chapter.id().equals(c.id());
			boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW && mouseY >= graphTop() && mouseY < graphBottom();
			if (current) {
				graphics.fill(x0 + 2, y, x1 - 2, y + ROW - 1, 0xFF34343E);
				graphics.fill(x0 + 2, y, x0 + 4, y + ROW - 1, 0xFFFFD040);
			} else if (hover) {
				graphics.fill(x0 + 2, y, x1 - 2, y + ROW - 1, 0xFF24242C);
			}
			graphics.item(QuestTexts.stack(c.icon()), x0 + 5, y + 3);
			int count = progress.completedCount(c);
			int total = c.quests().size();
			int claimable = progress.claimableCount(c, player);
			if (!compactList()) {
				int textX = x0 + 24;
				int maxW = x1 - textX - 3;
				graphics.text(font, ellipsize(Component.translatable(c.titleKey()), maxW), textX, y + 3, current ? 0xFFFFFFFF : 0xFFD0D0D0);
				int color = count == total ? 0xFF6BD86B : 0xFF909090;
				graphics.text(font, count + "/" + total, textX, y + 12, color);
				if (claimable > 0) {
					graphics.text(font, Component.translatable("gui.mlinnotas.quests.rewards_badge", claimable), textX + font.width(count + "/" + total) + 4, y + 12, 0xFFFFC020);
				}
			} else if (claimable > 0) {
				badge(graphics, x0 + 15, y + 1);
			}
			if (hover && (compactList() || claimable > 0)) {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.translatable(c.titleKey()).copy().append(" " + count + "/" + total));
				if (claimable > 0) {
					lines.add(Component.translatable("gui.mlinnotas.quests.rewards_waiting", claimable).withStyle(ChatFormatting.GOLD));
				}
				graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
			}
			y += ROW;
		}
		graphics.disableScissor();
	}

	private FormattedCharSequence ellipsize(Component text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text.getVisualOrderText();
		}
		String plain = font.plainSubstrByWidth(text.getString(), maxWidth - font.width("…"));
		return Component.literal(plain + "…").getVisualOrderText();
	}

	private List<Component> drawGraph(GuiGraphicsExtractor graphics, QuestProgress progress, String player, int mouseX, int mouseY) {
		int x0 = graphLeft();
		int y0 = graphTop();
		int x1 = graphRight();
		int y1 = graphBottom();
		graphics.fill(x0, y0, x1, y1, 0xC0141418);
		graphics.outline(x0, y0, x1 - x0, y1 - y0, PANEL_EDGE);
		graphics.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
		// pontinhos da grade
		int startX = x0 + Math.floorMod((int) Math.round(panX) + NODE / 2, CELL);
		int startY = y0 + Math.floorMod((int) Math.round(panY) + NODE / 2, CELL);
		for (int gx = startX; gx < x1; gx += CELL) {
			for (int gy = startY; gy < y1; gy += CELL) {
				graphics.fill(gx, gy, gx + 1, gy + 1, 0xFF26262C);
			}
		}
		int half = NODE / 2;
		for (Quest quest : chapter.quests()) {
			for (String dep : quest.dependencies()) {
				Quest from = QuestClient.book().quest(dep);
				if (from == null || !from.chapter().equals(chapter.id())) {
					continue;
				}
				int color = progress.isComplete(quest.key()) ? LINE_DONE : progress.isComplete(dep) ? LINE_READY : LINE_OFF;
				connect(graphics, nodeX(from), nodeY(from), nodeX(quest), nodeY(quest), color);
			}
		}
		Quest hovered = null;
		for (Quest quest : chapter.quests()) {
			int x = nodeX(quest);
			int y = nodeY(quest);
			if (x > x1 || y > y1 || x + NODE < x0 || y + NODE < y0) {
				continue;
			}
			drawNode(graphics, quest, x, y, progress.view(quest, player), progress.fraction(quest), quest == selected || (selected != null && quest.key().equals(selected.key())));
			if (crossChapterLocked(quest, progress)) {
				graphics.fill(x - 3, y + half - 1, x, y + half + 1, LINE_OFF);
			}
			if (mouseX >= x && mouseX < x + NODE && mouseY >= y && mouseY < y + NODE && inGraph(mouseX, mouseY)) {
				hovered = quest;
			}
		}
		graphics.disableScissor();
		if (hovered == null) {
			return null;
		}
		return nodeTooltip(hovered, progress, player);
	}

	private boolean crossChapterLocked(Quest quest, QuestProgress progress) {
		for (String dep : quest.dependencies()) {
			if (!dep.startsWith(quest.chapter() + "/")) {
				return true;
			}
		}
		return false;
	}

	/** Linha em L (ou em degrau) de {@code a} para {@code b}, 2 px. */
	private void connect(GuiGraphicsExtractor graphics, int ax, int ay, int bx, int by, int color) {
		int half = NODE / 2;
		if (bx >= ax + NODE + 4) {
			int sx = ax + NODE;
			int sy = ay + half;
			int ex = bx;
			int ey = by + half;
			int mid = (sx + ex) / 2;
			hLine(graphics, sx, mid, sy, color);
			vLine(graphics, mid, sy, ey, color);
			hLine(graphics, mid, ex, ey, color);
		} else {
			int sx = ax + half;
			int ex = bx + half;
			boolean down = by >= ay;
			int sy = down ? ay + NODE : ay;
			int ey = down ? by : by + NODE;
			int mid = (sy + ey) / 2;
			vLine(graphics, sx, sy, mid, color);
			hLine(graphics, sx, ex, mid, color);
			vLine(graphics, ex, mid, ey, color);
		}
	}

	private static void hLine(GuiGraphicsExtractor graphics, int xa, int xb, int y, int color) {
		graphics.fill(Math.min(xa, xb), y - 1, Math.max(xa, xb) + 1, y + 1, color);
	}

	private static void vLine(GuiGraphicsExtractor graphics, int x, int ya, int yb, int color) {
		graphics.fill(x - 1, Math.min(ya, yb), x + 1, Math.max(ya, yb) + 1, color);
	}

	private void drawNode(GuiGraphicsExtractor graphics, Quest quest, int x, int y, QuestProgress.View view, double fraction, boolean isSelected) {
		int bg;
		int edge;
		switch (view) {
			case DONE -> {
				bg = 0xFF27502A;
				edge = 0xFF6BD86B;
			}
			case CLAIMABLE -> {
				bg = 0xFF5A4410;
				boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
				edge = blink ? 0xFFFFE070 : 0xFFE0A020;
			}
			case ACTIVE -> {
				bg = 0xFF3A3420;
				edge = 0xFFFFD040;
			}
			default -> {
				bg = 0xFF222226;
				edge = 0xFF55555C;
			}
		}
		if (isSelected) {
			graphics.outline(x - 2, y - 2, NODE + 4, NODE + 4, 0xFFFFFFFF);
		}
		graphics.fill(x, y, x + NODE, y + NODE, bg);
		graphics.outline(x, y, NODE, NODE, edge);
		if (quest.isMilestone()) {
			graphics.outline(x + 1, y + 1, NODE - 2, NODE - 2, edge);
		}
		graphics.item(QuestTexts.stack(quest.icon()), x + 3, y + 3);
		if (view == QuestProgress.View.LOCKED) {
			graphics.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, 0xA0000000);
		}
		if (view == QuestProgress.View.ACTIVE && fraction > 0) {
			graphics.fill(x + 2, y + NODE - 4, x + NODE - 2, y + NODE - 2, 0xFF101018);
			graphics.fill(x + 2, y + NODE - 4, x + 2 + (int) Math.round((NODE - 4) * fraction), y + NODE - 2, 0xFF60C0FF);
		}
		if (view == QuestProgress.View.CLAIMABLE) {
			badge(graphics, x + NODE - 5, y - 3);
		}
		if (view == QuestProgress.View.DONE) {
			// visto verde no canto
			graphics.fill(x + NODE - 7, y + NODE - 4, x + NODE - 5, y + NODE - 2, LINE_DONE);
			graphics.fill(x + NODE - 5, y + NODE - 6, x + NODE - 3, y + NODE - 2, LINE_DONE);
			graphics.fill(x + NODE - 3, y + NODE - 9, x + NODE - 1, y + NODE - 5, LINE_DONE);
		}
	}

	/** "!" dourado (recompensa para resgatar). */
	private void badge(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + 7, y + 10, 0xFF201000);
		graphics.fill(x + 1, y + 1, x + 6, y + 9, 0xFFFFC020);
		graphics.fill(x + 3, y + 2, x + 4, y + 6, 0xFF201000);
		graphics.fill(x + 3, y + 7, x + 4, y + 8, 0xFF201000);
	}

	private List<Component> nodeTooltip(Quest quest, QuestProgress progress, String player) {
		List<Component> lines = new ArrayList<>();
		QuestProgress.View view = progress.view(quest, player);
		lines.add(QuestTexts.title(quest).copy().withStyle(viewColor(view), ChatFormatting.BOLD));
		lines.add(viewText(view));
		for (int i = 0; i < quest.tasks().size(); i++) {
			QuestTask task = quest.tasks().get(i);
			boolean taskDone = progress.taskDone(quest, i);
			Component line = Component.literal(taskDone ? "✔ " : "• ").append(QuestTexts.taskText(task));
			if (task.goal() > 1 && !taskDone) {
				line = line.copy().append(" (" + progress.progress(quest, i) + "/" + task.goal() + ")");
			}
			lines.add(line.copy().withStyle(taskDone ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		return lines;
	}

	static ChatFormatting viewColor(QuestProgress.View view) {
		return switch (view) {
			case LOCKED -> ChatFormatting.GRAY;
			case ACTIVE -> ChatFormatting.YELLOW;
			case CLAIMABLE -> ChatFormatting.GOLD;
			case DONE -> ChatFormatting.GREEN;
		};
	}

	static Component viewText(QuestProgress.View view) {
		return Component.translatable("gui.mlinnotas.quests.status." + view.name().toLowerCase(java.util.Locale.ROOT)).withStyle(viewColor(view));
	}

	private void drawDetail(GuiGraphicsExtractor graphics, QuestProgress progress, String player, int mouseX, int mouseY) {
		int x0 = detailLeft();
		int x1 = width - PAD;
		int top = graphTop();
		int bottom = graphBottom();
		graphics.fill(x0, top, x1, bottom, 0xE80E0E12);
		graphics.outline(x0, top, x1 - x0, bottom - top, PANEL_EDGE);
		// fechar
		boolean closeHot = closeHover(mouseX, mouseY);
		graphics.text(font, "×", width - PAD - 10, top + 3, closeHot ? 0xFFFFFFFF : 0xFF909090);

		int contentTop = top + 4;
		int contentBottom = detailBottom();
		int innerW = x1 - x0 - 10;
		int x = x0 + 5;
		graphics.enableScissor(x0 + 1, contentTop, x1 - 1, contentBottom);
		int y = contentTop - (int) detailScroll;
		int startY = y;
		QuestProgress.View view = progress.view(selected, player);

		graphics.item(QuestTexts.stack(selected.icon()), x, y);
		int titleY = y;
		for (FormattedCharSequence line : font.split(QuestTexts.title(selected).copy().withStyle(viewColor(view), ChatFormatting.BOLD), innerW - 32)) {
			graphics.text(font, line, x + 20, titleY, 0xFFFFFFFF);
			titleY += font.lineHeight;
		}
		y = Math.max(y + 18, titleY + 1);
		graphics.text(font, viewText(view), x, y, 0xFFFFFFFF);
		y += font.lineHeight + 4;

		for (FormattedCharSequence line : font.split(Component.translatable(selected.descriptionKey()), innerW)) {
			graphics.text(font, line, x, y, 0xFFC8C8C8);
			y += font.lineHeight;
		}
		y += 4;

		if (view == QuestProgress.View.LOCKED) {
			for (String dep : selected.dependencies()) {
				if (progress.isComplete(dep)) {
					continue;
				}
				Quest other = QuestClient.book().quest(dep);
				Component name = other == null ? Component.literal(dep) : QuestTexts.title(other);
				if (other != null && !other.chapter().equals(selected.chapter())) {
					QuestChapter c = QuestClient.book().chapter(other.chapter());
					name = Component.translatable("gui.mlinnotas.quests.in_chapter", name, c == null ? other.chapter() : Component.translatable(c.titleKey()));
				}
				for (FormattedCharSequence line : font.split(Component.translatable("gui.mlinnotas.quests.requires", name).withStyle(ChatFormatting.RED), innerW)) {
					graphics.text(font, line, x, y, 0xFFFFFFFF);
					y += font.lineHeight;
				}
			}
			y += 3;
		}

		if (!selected.tasks().isEmpty()) {
			graphics.text(font, Component.translatable("gui.mlinnotas.quests.tasks").withStyle(ChatFormatting.UNDERLINE), x, y, 0xFFE0E0E0);
			y += font.lineHeight + 3;
			for (int i = 0; i < selected.tasks().size(); i++) {
				QuestTask task = selected.tasks().get(i);
				boolean taskDone = progress.taskDone(selected, i);
				String count = taskDone ? "✔" : task.goal() > 1 ? progress.progress(selected, i) + "/" + task.goal() : "";
				int countW = count.isEmpty() ? 0 : font.width(count) + 4;
				graphics.item(QuestTexts.taskIcon(task), x, y);
				int ty = y + (font.split(QuestTexts.taskText(task), innerW - 20 - countW).size() == 1 ? 4 : 0);
				int rowStart = y;
				for (FormattedCharSequence line : font.split(QuestTexts.taskText(task), innerW - 20 - countW)) {
					graphics.text(font, line, x + 19, ty, taskDone ? 0xFF8BE88B : 0xFFFFFFFF);
					ty += font.lineHeight;
				}
				if (!count.isEmpty()) {
					graphics.text(font, count, x + innerW - font.width(count), rowStart + 4, taskDone ? 0xFF6BD86B : 0xFFFFD040);
				}
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16 && mouseY >= contentTop && mouseY < contentBottom
						&& task.type() == QuestTask.Type.ITEM) {
					graphics.setTooltipForNextFrame(font, QuestTexts.taskIcon(task), mouseX, mouseY);
				}
				y = Math.max(y + 18, ty + 2);
			}
			y += 3;
		}

		if (!selected.rewards().isEmpty()) {
			graphics.text(font, Component.translatable("gui.mlinnotas.quests.rewards").withStyle(ChatFormatting.UNDERLINE), x, y, 0xFFE0E0E0);
			y += font.lineHeight + 3;
			int rx = x;
			for (QuestReward reward : selected.rewards()) {
				if (reward.isItem()) {
					if (rx + 18 > x + innerW) {
						rx = x;
						y += 18;
					}
					ItemStack stack = QuestTexts.stack(reward.item());
					stack.setCount(Math.min(reward.count(), stack.getMaxStackSize()));
					graphics.item(stack, rx, y);
					graphics.itemDecorations(font, stack, rx, y, reward.count() > 1 ? String.valueOf(reward.count()) : null);
					if (mouseX >= rx && mouseX < rx + 16 && mouseY >= y && mouseY < y + 16 && mouseY >= contentTop && mouseY < contentBottom) {
						graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
					}
					rx += 18;
				}
			}
			for (QuestReward reward : selected.rewards()) {
				if (!reward.isItem()) {
					Component xp = Component.translatable("gui.mlinnotas.quests.reward_xp", reward.xp());
					if (rx + font.width(xp) > x + innerW) {
						rx = x;
						y += 18;
					}
					graphics.text(font, xp, rx + 1, y + 4, 0xFF80FF20);
					rx += font.width(xp) + 6;
				}
			}
			y += 18;
			if (progress.hasClaimed(player, selected)) {
				graphics.text(font, Component.translatable("gui.mlinnotas.quests.claimed").withStyle(ChatFormatting.GREEN), x, y, 0xFFFFFFFF);
				y += font.lineHeight + 2;
			} else if (view != QuestProgress.View.CLAIMABLE) {
				for (FormattedCharSequence line : font.split(Component.translatable("gui.mlinnotas.quests.reward_note").withStyle(ChatFormatting.DARK_GRAY), innerW)) {
					graphics.text(font, line, x, y, 0xFFFFFFFF);
					y += font.lineHeight;
				}
			}
		}
		graphics.disableScissor();
		detailContentHeight = y - startY + 4;
		int visible = contentBottom - contentTop;
		if (detailContentHeight > visible) {
			// barra de rolagem
			int barH = Math.max(12, visible * visible / detailContentHeight);
			int barY = contentTop + (int) ((visible - barH) * (detailScroll / Math.max(1, detailContentHeight - visible)));
			graphics.fill(x1 - 3, barY, x1 - 1, barY + barH, 0xFF707080);
		}
	}
}
