package com.mlin.notas.client.village;

import com.mlin.notas.village.Bounty;
import com.mlin.notas.village.BountyBoard;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePayloads;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageShop;
import com.mlin.notas.village.VillageState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tela do quadro de missoes. Cabecalho com nome, nivel e barra de prosperidade ate o proximo nivel; tres abas:
 * Projetos (entregas que constroem a vila), Missoes do dia (entregar, cacar, explorar) e Mercado (o que a vila vende
 * no nivel atual). Rodape com a obra em andamento. Todas as acoes sao validadas no servidor.
 */
public class VillageBoardScreen extends Screen {
	public enum Tab {
		PROJECTS, BOUNTIES, SHOP, BUILDINGS
	}

	private static final int PANEL = 0xF0201A14;
	private static final int PANEL_EDGE = 0xFF8A6A3C;
	private static final int ROW = 0x40000000;
	private static final int ROW_HOVER = 0x60FFFFFF;
	private static final int GOLD = 0xFFFFD36A;
	private static final int PROJECT_ROW = 24;
	private static final int PROJECTS_PER_PAGE = 6;
	private static final int SHOP_ROWS = 5;
	private static final int AREA_BUTTON = 86;
	private static final int BUILDING_ROW = 22;

	private VillagePayloads.View view;
	private Tab tab = Tab.PROJECTS;
	private int page;

	public VillageBoardScreen(VillagePayloads.View view) {
		super(Component.translatable("gui.mlinnotas.village.title"));
		this.view = view;
	}

	public BlockPos board() {
		return view.board();
	}

	public VillageState state() {
		return view.state();
	}

	public void update(VillagePayloads.View view) {
		this.view = view;
		rebuildWidgets();
	}

	/** Troca de aba (testes de cliente). */
	public void setTab(Tab tab) {
		this.tab = tab;
		this.page = 0;
		rebuildWidgets();
	}

	// ---------------------------------------------------------------- layout

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

	private int contentTop() {
		return top() + 56;
	}

	private int contentBottom() {
		return top() + panelH() - 26;
	}

	private int pages() {
		return switch (tab) {
			case PROJECTS -> (VillageProject.values().length + PROJECTS_PER_PAGE - 1) / PROJECTS_PER_PAGE;
			case SHOP -> (VillageShop.OFFERS.size() + SHOP_ROWS * 2 - 1) / (SHOP_ROWS * 2);
			case BUILDINGS -> Math.max(1, (buildingRows().size() + buildingsPerPage() - 1) / buildingsPerPage());
			case BOUNTIES -> 1;
		};
	}

	@Override
	protected void init() {
		int l = left();
		int t = top();
		int w = panelW();
		int tabW = (w - 28) / 4;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			Tab target = tabs[i];
			Button button = Button.builder(Component.translatable("gui.mlinnotas.village.tab." + target.name().toLowerCase(java.util.Locale.ROOT)),
					b -> setTab(target)).bounds(l + 8 + i * (tabW + 4), t + 34, tabW, 18).build();
			button.active = target != tab;
			addRenderableWidget(button);
		}
		page = Math.min(page, pages() - 1);
		switch (tab) {
			case PROJECTS -> initProjects();
			case BOUNTIES -> initBounties();
			case SHOP -> initShop();
			case BUILDINGS -> initBuildings();
		}
		int bottom = t + panelH() - 22;
		if (pages() > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page = (page + pages() - 1) % pages();
				rebuildWidgets();
			}).bounds(l + w - 118, bottom, 18, 18).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page = (page + 1) % pages();
				rebuildWidgets();
			}).bounds(l + w - 98, bottom, 18, 18).build());
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(l + w - 76, bottom, 68, 18).build());
		boolean showing = VillageOverlay.showing(state().key());
		addRenderableWidget(Button.builder(Component.translatable(showing ? "gui.mlinnotas.village.area_hide" : "gui.mlinnotas.village.area_show"), b -> {
			if (VillageOverlay.showing(state().key())) {
				VillageOverlay.clear();
			} else {
				VillageClient.send(view.board(), "overlay", "");
			}
			minecraft.execute(this::rebuildWidgets);
		}).bounds(l + 8, bottom, AREA_BUTTON, 18).tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.village.area_tooltip"))).build());
	}

	/**
	 * Linhas da aba de projetos: primeiro a terraplenagem dos aneis que a vila ja pode pagar (Integer = anel), depois
	 * os projetos.
	 */
	private List<Object> projectRows() {
		List<Object> rows = new ArrayList<>();
		VillageState s = state();
		if (s.planned()) {
			for (int ring = 1; ring <= Math.min(com.mlin.notas.village.MasterPlan.RINGS, s.level().rings() + 1); ring++) {
				rows.add(ring);
			}
		}
		rows.addAll(List.of(VillageProject.values()));
		return rows;
	}

	private void initProjects() {
		List<Object> rows = projectRows();
		int y = contentTop();
		for (int i = page * PROJECTS_PER_PAGE; i < Math.min(rows.size(), (page + 1) * PROJECTS_PER_PAGE); i++) {
			if (rows.get(i) instanceof Integer ring) {
				Button button = Button.builder(Component.translatable("gui.mlinnotas.village.deposit"),
						b -> VillageClient.send(view.board(), "terrain", String.valueOf(ring))).bounds(left() + panelW() - 62, y + 3, 54, 18).build();
				button.active = state().ring(ring).state() == com.mlin.notas.village.RingWork.State.OPEN;
				addRenderableWidget(button);
				y += PROJECT_ROW;
				continue;
			}
			VillageProject project = (VillageProject) rows.get(i);
			Button button = Button.builder(Component.translatable("gui.mlinnotas.village.deposit"),
					b -> VillageClient.send(view.board(), "deposit", project.id())).bounds(left() + panelW() - 62, y + 3, 54, 18).build();
			button.active = state().status(project) == VillageState.Status.AVAILABLE;
			addRenderableWidget(button);
			y += PROJECT_ROW;
		}
	}

	private void initBounties() {
		List<Bounty> bounties = state().bounties();
		int y = contentTop();
		for (int i = 0; i < bounties.size(); i++) {
			Bounty bounty = bounties.get(i);
			int index = i;
			boolean done = state().bountyDone(i);
			Component label = done ? Component.translatable("gui.mlinnotas.village.bounty_paid")
					: bounty.kind() == Bounty.Kind.DELIVER ? Component.translatable("gui.mlinnotas.village.deliver")
					: Component.translatable("gui.mlinnotas.village.claim");
			Button button = Button.builder(label, b -> VillageClient.send(view.board(), "bounty", String.valueOf(index)))
					.bounds(left() + panelW() - 72, y + 12, 64, 18).build();
			button.active = !done && (bounty.kind() == Bounty.Kind.DELIVER || state().bountyReady(i));
			addRenderableWidget(button);
			y += bountyRow();
		}
	}

	private int bountyRow() {
		return (contentBottom() - contentTop()) / BountyBoard.PER_DAY;
	}

	private void initShop() {
		int colW = (panelW() - 20) / 2;
		List<VillageShop.Offer> offers = VillageShop.OFFERS;
		for (int i = page * SHOP_ROWS * 2; i < Math.min(offers.size(), (page + 1) * SHOP_ROWS * 2); i++) {
			VillageShop.Offer offer = offers.get(i);
			int slot = i - page * SHOP_ROWS * 2;
			int x = left() + 8 + (slot % 2) * (colW + 4);
			int y = contentTop() + (slot / 2) * shopRow();
			Button button = Button.builder(Component.translatable("gui.mlinnotas.village.buy"), b -> VillageClient.send(view.board(), "buy", offer.id()))
					.bounds(x + colW - 50, y + 3, 46, 18).build();
			button.active = state().level().ordinal() >= offer.level().ordinal();
			addRenderableWidget(button);
		}
	}

	private int shopRow() {
		return (contentBottom() - contentTop()) / SHOP_ROWS;
	}

	// ---------------------------------------------------------------- obras (fila, lotes, demolir)

	/** Linha da aba Obras: obra da fila, tipo com lote livre ou obra pronta. */
	private record BuildingRow(int queueIndex, String kind, VillageLayout.Lot lot) {
	}

	private int buildingsPerPage() {
		return Math.max(1, (contentBottom() - contentTop() - 12 - 16) / BUILDING_ROW);
	}

	private List<BuildingRow> buildingRows() {
		List<BuildingRow> rows = new ArrayList<>();
		VillageState s = state();
		for (int i = 0; i < s.queue().size(); i++) {
			rows.add(new BuildingRow(i, s.queue().get(i).id(), null));
		}
		VillageLayout layout = s.layout();
		if (layout != null) {
			for (VillageProject project : VillageLayout.LOT_PROJECTS) {
				if (!layout.freeLots(project.id()).isEmpty() && s.status(project) != VillageState.Status.LOCKED) {
					rows.add(new BuildingRow(-1, project.id(), null));
				}
			}
			for (VillageLayout.Lot lot : layout.lots()) {
				if (lot.built()) {
					rows.add(new BuildingRow(-1, lot.kind(), lot));
				}
			}
		}
		return rows;
	}

	private void initBuildings() {
		addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.village.works.open"), b -> minecraft.gui.setScreen(new WorksScreen(view.board(), this)))
				.bounds(left() + 8, contentBottom() - 14, 140, 14).tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.village.works.open_tooltip"))).build());
		List<BuildingRow> rows = buildingRows();
		int per = buildingsPerPage();
		int y = contentTop() + 12;
		int right = left() + panelW() - 8;
		for (int i = page * per; i < Math.min(rows.size(), (page + 1) * per); i++) {
			BuildingRow row = rows.get(i);
			Button button;
			if (row.queueIndex() >= 0) {
				int index = row.queueIndex();
				button = Button.builder(Component.translatable("gui.mlinnotas.village.cancel"), b -> VillageClient.send(view.board(), "cancel",
						String.valueOf(index))).bounds(right - 92, y + 1, 88, 18)
						.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.village.cancel_tooltip"))).build();
				button.active = !(index == 0 && !view.building().isEmpty());
			} else if (row.lot() == null) {
				String kind = row.kind();
				button = Button.builder(Component.translatable("gui.mlinnotas.village.choose"), b -> choose(kind)).bounds(right - 92, y + 1, 88, 18)
						.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.village.choose_tooltip"))).build();
			} else {
				VillageLayout.Lot lot = row.lot();
				button = Button.builder(Component.translatable("gui.mlinnotas.village.demolish"), b -> confirmDemolish(lot)).bounds(right - 92, y + 1, 88,
						18).tooltip(Tooltip.create(Component.translatable(lot.legacy() ? "gui.mlinnotas.village.demolish_legacy"
						: "gui.mlinnotas.village.demolish_tooltip"))).build();
				button.active = !lot.legacy();
			}
			addRenderableWidget(button);
			y += BUILDING_ROW;
		}
	}

	/** Fecha a tela e comeca a escolher o lugar da proxima obra do tipo. */
	public void choose(String kind) {
		VillageLayout layout = state().layout();
		if (layout == null) {
			return;
		}
		BlockPos center = new BlockPos(state().x(), state().y(), state().z());
		if (VillagePlacement.start(kind, view.board(), center, layout, state().style())) {
			onClose();
		}
	}

	private void confirmDemolish(VillageLayout.Lot lot) {
		Component name = VillageOverlay.kindName(lot.kind());
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				VillageClient.send(view.board(), "demolish", String.valueOf(lot.id()));
			}
			minecraft.gui.setScreen(this);
		}, Component.translatable("gui.mlinnotas.village.demolish_title", name), Component.translatable("gui.mlinnotas.village.demolish_message",
				name, (int) Math.round(com.mlin.notas.village.Refunds.DEMOLISH_FRACTION * 100))));
	}

	private void buildings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		VillageState s = state();
		List<BuildingRow> rows = buildingRows();
		int x = left() + 8;
		int right = left() + panelW() - 8;
		graphics.text(font, Component.translatable("gui.mlinnotas.village.buildings_hint").withStyle(ChatFormatting.GRAY), x, contentTop(), 0xFFFFFFFF);
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("gui.mlinnotas.village.buildings_empty").withStyle(ChatFormatting.DARK_GRAY), x, contentTop() + 16,
					0xFFFFFFFF);
			return;
		}
		int per = buildingsPerPage();
		int y = contentTop() + 12;
		for (int i = page * per; i < Math.min(rows.size(), (page + 1) * per); i++) {
			BuildingRow row = rows.get(i);
			graphics.fill(x, y, right, y + BUILDING_ROW - 2, ROW);
			VillageProject project = VillageProject.byId(row.kind());
			ItemStack icon = project == null ? new ItemStack(Items.CAMPFIRE) : stack(project.icon());
			graphics.item(icon, x + 2, y + 2);
			Component title;
			Component sub;
			if (row.queueIndex() >= 0) {
				title = Component.translatable("gui.mlinnotas.village.row.queued", VillageOverlay.kindName(row.kind()), row.queueIndex() + 1);
				boolean building = row.queueIndex() == 0 && !view.building().isEmpty();
				sub = building ? Component.translatable("gui.mlinnotas.village.building", view.total() == 0 ? 0 : view.done() * 100 / view.total())
						.withStyle(ChatFormatting.YELLOW) : view.waiting() && row.queueIndex() == 0
						? Component.translatable("gui.mlinnotas.village.row.waiting").withStyle(ChatFormatting.RED)
						: Component.translatable("gui.mlinnotas.village.queued").withStyle(ChatFormatting.GRAY);
			} else if (row.lot() == null) {
				int free = s.layout().freeLots(row.kind()).size();
				title = Component.translatable("gui.mlinnotas.village.row.free", VillageOverlay.kindName(row.kind()), free);
				VillageLayout.Lot next = s.layout().firstFree(row.kind());
				sub = Component.translatable(next != null && next.preferred() ? "gui.mlinnotas.village.row.chosen" : "gui.mlinnotas.village.row.auto")
						.withStyle(ChatFormatting.GRAY);
			} else {
				VillageLayout.Lot lot = row.lot();
				title = Component.translatable("gui.mlinnotas.village.row.built", VillageOverlay.kindName(lot.kind()));
				int dx = lot.x() + lot.w() / 2;
				int dz = lot.z() + lot.d() / 2;
				sub = Component.translatable("gui.mlinnotas.village.row.where", Math.round(Math.sqrt(dx * dx + dz * dz)), direction(dx, dz))
						.withStyle(ChatFormatting.GRAY);
			}
			graphics.text(font, title, x + 22, y + 2, 0xFFFFFFFF);
			graphics.text(font, sub, x + 22, y + 11, 0xFFFFFFFF);
			y += BUILDING_ROW;
		}
	}

	/** Direcao (N, NE, L...) do quadro ate o ponto. */
	private static Component direction(int dx, int dz) {
		String[] keys = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};
		double angle = Math.toDegrees(Math.atan2(dz, dx));
		int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
		return Component.translatable("gui.mlinnotas.village.dir." + keys[index]);
	}

	/** Reconstroi os botoes (contorno ligado/desligado chegou). */
	public void refresh() {
		rebuildWidgets();
	}

	// ---------------------------------------------------------------- desenho

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int l = left();
		int t = top();
		graphics.fill(l, t, l + panelW(), t + panelH(), PANEL);
		graphics.outline(l, t, panelW(), panelH(), PANEL_EDGE);
		graphics.outline(l + 2, t + 2, panelW() - 4, panelH() - 4, 0xFF4A3A22);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		header(graphics, mouseX, mouseY);
		switch (tab) {
			case PROJECTS -> projects(graphics, mouseX, mouseY);
			case BOUNTIES -> bounties(graphics, mouseX, mouseY);
			case SHOP -> shop(graphics, mouseX, mouseY);
			case BUILDINGS -> buildings(graphics, mouseX, mouseY);
		}
		footer(graphics);
	}

	private static Component levelName(VillageLevel level) {
		return Component.translatable("village.mlinnotas.level." + level.id());
	}

	private static Component projectName(VillageProject project) {
		return Component.translatable("village.mlinnotas.project." + project.id());
	}

	private void header(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		VillageState s = state();
		int l = left() + 8;
		int t = top() + 7;
		int r = left() + panelW() - 8;
		graphics.text(font, Component.literal(s.name()).withStyle(ChatFormatting.BOLD), l, t, GOLD);
		Component level = levelName(s.level()).copy().withStyle(ChatFormatting.AQUA);
		int lw = font.width(level);
		graphics.text(font, level, r - lw, t, 0xFFFFFFFF);
		// barra de prosperidade ate o proximo nivel
		VillageLevel next = s.level().next();
		int barY = t + 14;
		int barX0 = l;
		int barX1 = r;
		graphics.fill(barX0, barY, barX1, barY + 9, 0xFF101010);
		double fraction = next == null ? 1 : Math.min(1.0, (double) s.prosperity() / next.prosperity());
		graphics.fill(barX0 + 1, barY + 1, barX0 + 1 + (int) ((barX1 - barX0 - 2) * fraction), barY + 8, next == null ? 0xFF8A6A20 : 0xFF3F8F3F);
		Component barText = next == null
				? Component.translatable("gui.mlinnotas.village.prosperity_max", s.prosperity())
				: Component.translatable("gui.mlinnotas.village.prosperity", s.prosperity(), next.prosperity(), levelName(next));
		graphics.centeredText(font, barText, (barX0 + barX1) / 2, barY + 1, 0xFFFFFFFF);
		boolean hoverLevel = mouseX >= barX0 && mouseX <= barX1 && mouseY >= t - 2 && mouseY <= barY + 10;
		if (hoverLevel) {
			graphics.setTooltipForNextFrame(font, levelTooltip(s), mouseX, mouseY);
		}
	}

	/** Vantagens do nivel atual e o que falta para o proximo. */
	private List<FormattedCharSequence> levelTooltip(VillageState s) {
		List<Component> lines = new ArrayList<>();
		VillageLevel level = s.level();
		lines.add(levelName(level).copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		lines.add(Component.translatable("gui.mlinnotas.village.perk.trade_cap", Component.translatable("merchant.level." + level.tradeCap()))
				.withStyle(ChatFormatting.GRAY));
		if (level.discount() > 0) {
			lines.add(Component.translatable("gui.mlinnotas.village.perk.discount", level.discount()).withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable("gui.mlinnotas.village.perk.radius", level.radius()).withStyle(ChatFormatting.GRAY));
		VillageLevel next = level.next();
		if (next != null) {
			lines.add(Component.translatable("gui.mlinnotas.village.next_level", levelName(next)).withStyle(ChatFormatting.YELLOW));
			for (String missing : next.missing(s.levelContext())) {
				Component line;
				if (missing.equals("prosperity")) {
					line = Component.translatable("gui.mlinnotas.village.need.prosperity", s.prosperity(), next.prosperity());
				} else if (missing.equals("projects")) {
					line = Component.translatable("gui.mlinnotas.village.need.projects", s.buildPoints(), next.projectsNeeded());
				} else if (missing.startsWith("ring:")) {
					line = Component.translatable("gui.mlinnotas.village.need.ring", missing.substring(5));
				} else {
					line = Component.translatable("gui.mlinnotas.village.need.project", projectName(VillageProject.byId(missing.substring(8))));
				}
				lines.add(Component.literal(" - ").append(line).withStyle(ChatFormatting.RED));
			}
			lines.add(Component.translatable("gui.mlinnotas.village.next_perks", Component.translatable("merchant.level." + next.tradeCap()),
					next.discount()).withStyle(ChatFormatting.DARK_GREEN));
		}
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component line : lines) {
			out.addAll(font.split(line, 260));
		}
		return out;
	}

	private ItemStack costStack(String id) {
		if (id.startsWith("#")) {
			TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(id.substring(1)));
			List<Holder<Item>> items = new ArrayList<>();
			BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(items::add);
			if (items.isEmpty()) {
				return new ItemStack(Items.BARRIER);
			}
			// alterna entre os itens da tag, como nos visualizadores de receita
			int index = (int) ((System.currentTimeMillis() / 1000) % items.size());
			return new ItemStack(items.get(index));
		}
		return stack(id);
	}

	private static ItemStack stack(String id) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		return item == null || item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
	}

	private static Component costName(String id) {
		if (id.startsWith("#")) {
			return Component.translatable("village.mlinnotas.cost." + id.substring(1).replace(':', '.').replace('/', '.'));
		}
		return stack(id).getHoverName();
	}

	private boolean hover(int mouseX, int mouseY, int x0, int y0, int x1, int y1) {
		return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
	}

	/** Linha da terraplenagem de um anel: situacao, esmeraldas e terra entregues, andamento da obra. */
	private void ringRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int ring, int x, int y, int r) {
		VillageState s = state();
		com.mlin.notas.village.RingWork work = s.ring(ring);
		boolean rowHover = hover(mouseX, mouseY, x, y, r, y + PROJECT_ROW - 1);
		graphics.fill(x, y, left() + panelW() - 8, y + PROJECT_ROW - 1, rowHover ? ROW_HOVER : ROW);
		graphics.item(new ItemStack(work.done() ? Items.GRASS_BLOCK : Items.DIRT), x + 3, y + 3);
		graphics.text(font, Component.translatable("gui.mlinnotas.village.terrain.name", ring), x + 23, y + 3, 0xFFFFFFFF);
		Component sub;
		switch (work.state()) {
			case DONE -> sub = Component.translatable("gui.mlinnotas.village.terrain.done").withStyle(ChatFormatting.GREEN);
			case QUEUED -> sub = work.columns() > 0 && work.ready() > 0
					? Component.translatable("gui.mlinnotas.village.terrain.working", work.ready() * 100 / work.columns()).withStyle(ChatFormatting.YELLOW)
					: Component.translatable("gui.mlinnotas.village.queued").withStyle(ChatFormatting.YELLOW);
			default -> sub = ring > s.level().rings()
					? Component.translatable("gui.mlinnotas.village.terrain.next_level").withStyle(ChatFormatting.GOLD)
					: Component.translatable("gui.mlinnotas.village.terrain.ready_count", work.ready(), work.columns()).withStyle(ChatFormatting.GRAY);
		}
		graphics.text(font, sub, x + 23, y + 13, 0xFFFFFFFF);
		int cx = x + 150;
		if (work.state() == com.mlin.notas.village.RingWork.State.OPEN) {
			for (VillageCost cost : work.costs()) {
				int have = work.deposited(cost.id());
				graphics.item(costStack(cost.id()), cx, y + 3);
				String count = have + "/" + cost.count();
				graphics.text(font, count, cx + 17, y + 8, have >= cost.count() ? 0xFF80FF80 : 0xFFE0E0E0);
				if (hover(mouseX, mouseY, cx, y + 2, cx + 16, y + 20)) {
					graphics.setTooltipForNextFrame(font, Component.translatable("gui.mlinnotas.village.cost_tooltip", costName(cost.id()), have,
							cost.count()), mouseX, mouseY);
				}
				cx += 18 + font.width(count) + 4;
			}
			double fraction = work.fraction();
			if (fraction > 0) {
				graphics.fill(x + 23, y + PROJECT_ROW - 3, x + 145, y + PROJECT_ROW - 2, 0xFF303030);
				graphics.fill(x + 23, y + PROJECT_ROW - 3, x + 23 + (int) (122 * fraction), y + PROJECT_ROW - 2, 0xFF60C0FF);
			}
		}
		if (rowHover && !hover(mouseX, mouseY, x + 150, y, r, y + PROJECT_ROW)) {
			List<FormattedCharSequence> tip = new ArrayList<>(font.split(Component.translatable("gui.mlinnotas.village.terrain.desc", ring)
					.withStyle(ChatFormatting.GRAY), 240));
			tip.add(Component.translatable("gui.mlinnotas.village.terrain.earthwork", work.cut(), work.filled(), s.stockpile())
					.withStyle(ChatFormatting.DARK_AQUA).getVisualOrderText());
			graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
		}
	}

	private void projects(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		VillageState s = state();
		List<Object> rows = projectRows();
		int x = left() + 8;
		int r = left() + panelW() - 66;
		int y = contentTop();
		for (int i = page * PROJECTS_PER_PAGE; i < Math.min(rows.size(), (page + 1) * PROJECTS_PER_PAGE); i++) {
			if (rows.get(i) instanceof Integer ring) {
				ringRow(graphics, mouseX, mouseY, ring, x, y, r);
				y += PROJECT_ROW;
				continue;
			}
			VillageProject project = (VillageProject) rows.get(i);
			VillageState.Status status = s.status(project);
			boolean rowHover = hover(mouseX, mouseY, x, y, r, y + PROJECT_ROW - 1);
			graphics.fill(x, y, left() + panelW() - 8, y + PROJECT_ROW - 1, rowHover ? ROW_HOVER : ROW);
			graphics.item(stack(project.icon()), x + 3, y + 3);
			int nameColor = status == VillageState.Status.LOCKED ? 0xFF808080 : 0xFFFFFFFF;
			graphics.text(font, projectName(project), x + 23, y + 3, nameColor);
			int built = s.built(project);
			int max = project.maxBuilds(s.level());
			Component sub;
			if (view.building().equals(project.id())) {
				int pct = view.total() == 0 ? 0 : view.done() * 100 / view.total();
				sub = Component.translatable("gui.mlinnotas.village.building", pct).withStyle(ChatFormatting.YELLOW);
			} else if (s.queued(project) > 0) {
				sub = Component.translatable(view.waiting() ? "gui.mlinnotas.village.no_space" : "gui.mlinnotas.village.queued")
						.withStyle(view.waiting() ? ChatFormatting.RED : ChatFormatting.YELLOW);
			} else if (status == VillageState.Status.LOCKED) {
				sub = Component.translatable("gui.mlinnotas.village.requires", levelName(project.minLevel())).withStyle(ChatFormatting.DARK_GRAY);
			} else if (status == VillageState.Status.MAXED) {
				sub = Component.translatable("gui.mlinnotas.village.maxed", built, max).withStyle(ChatFormatting.GREEN);
			} else {
				sub = Component.translatable("gui.mlinnotas.village.built_count", built, max, project.prosperity()).withStyle(ChatFormatting.GRAY);
			}
			graphics.text(font, sub, x + 23, y + 13, 0xFFFFFFFF);
			// custos: icone + entregue/total
			int cx = x + 150;
			for (VillageCost cost : project.costs()) {
				int have = s.deposited(project, cost.id());
				boolean full = have >= cost.count();
				graphics.item(costStack(cost.id()), cx, y + 3);
				String count = have + "/" + cost.count();
				graphics.text(font, count, cx + 17, y + 8, full ? 0xFF80FF80 : status == VillageState.Status.LOCKED ? 0xFF707070 : 0xFFE0E0E0);
				if (hover(mouseX, mouseY, cx, y + 2, cx + 16, y + 20)) {
					graphics.setTooltipForNextFrame(font, Component.translatable("gui.mlinnotas.village.cost_tooltip", costName(cost.id()), have,
							cost.count()), mouseX, mouseY);
				}
				cx += 18 + font.width(count) + 4;
			}
			// barra do quanto ja foi entregue
			double fraction = s.fraction(project);
			if (fraction > 0 && status == VillageState.Status.AVAILABLE) {
				graphics.fill(x + 23, y + PROJECT_ROW - 3, x + 145, y + PROJECT_ROW - 2, 0xFF303030);
				graphics.fill(x + 23, y + PROJECT_ROW - 3, x + 23 + (int) (122 * fraction), y + PROJECT_ROW - 2, 0xFF60C0FF);
			}
			if (rowHover && !hover(mouseX, mouseY, x + 150, y, r, y + PROJECT_ROW)) {
				List<FormattedCharSequence> tip = new ArrayList<>(font.split(Component.translatable("village.mlinnotas.project." + project.id()
						+ ".desc").withStyle(ChatFormatting.GRAY), 240));
				if (project.villagers() > 0) {
					tip.add(Component.translatable("gui.mlinnotas.village.reward.villagers", project.villagers()).withStyle(ChatFormatting.GREEN)
							.getVisualOrderText());
				}
				if (project.golems() > 0) {
					tip.add(Component.translatable("gui.mlinnotas.village.reward.golems", project.golems()).withStyle(ChatFormatting.GREEN)
							.getVisualOrderText());
				}
				tip.add(Component.translatable("gui.mlinnotas.village.reward.prosperity", project.prosperity()).withStyle(ChatFormatting.GOLD)
						.getVisualOrderText());
				graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
			}
			y += PROJECT_ROW;
		}
	}

	/** Texto da missao: "Entregar 16 Trigo", "Cacar 8 Zumbis", "Explorar: um deserto". */
	public static Component bountyText(Bounty bounty) {
		return switch (bounty.kind()) {
			case DELIVER -> Component.translatable("gui.mlinnotas.village.bounty.deliver", bounty.count(), stack(bounty.target()).getHoverName());
			case HUNT -> {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(bounty.target()));
				yield Component.translatable("gui.mlinnotas.village.bounty.hunt", bounty.count(), type.getDescription());
			}
			case EXPLORE -> Component.translatable("gui.mlinnotas.village.bounty.explore",
					Component.translatable("village.mlinnotas.explore." + bounty.target()));
		};
	}

	private static final Map<String, Item> EXPLORE_ICONS = Map.ofEntries(Map.entry("desert", Items.SAND), Map.entry("jungle", Items.JUNGLE_SAPLING),
			Map.entry("snowy", Items.SNOW_BLOCK), Map.entry("badlands", Items.RED_SAND), Map.entry("mushroom", Items.RED_MUSHROOM),
			Map.entry("cherry", Items.CHERRY_SAPLING), Map.entry("swamp", Items.LILY_PAD), Map.entry("deep_dark", Items.SCULK_SENSOR),
			Map.entry("ruin", Items.CRACKED_STONE_BRICKS), Map.entry("outpost", Items.CROSSBOW), Map.entry("nether", Items.NETHERRACK),
			Map.entry("end", Items.END_STONE), Map.entry("shrine", Items.COMPASS));

	private static ItemStack bountyIcon(Bounty bounty) {
		return switch (bounty.kind()) {
			case DELIVER -> stack(bounty.target());
			case HUNT -> {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(bounty.target()));
				yield SpawnEggItem.byId(type).map(h -> new ItemStack(h)).orElse(new ItemStack(Items.IRON_SWORD));
			}
			case EXPLORE -> new ItemStack(EXPLORE_ICONS.getOrDefault(bounty.target(), Items.FILLED_MAP));
		};
	}

	private void bounties(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		VillageState s = state();
		int x = left() + 8;
		int y = contentTop();
		int rowH = bountyRow();
		for (int i = 0; i < s.bounties().size(); i++) {
			Bounty bounty = s.bounties().get(i);
			boolean done = s.bountyDone(i);
			graphics.fill(x, y, left() + panelW() - 8, y + rowH - 3, done ? 0x40306030 : ROW);
			graphics.item(bountyIcon(bounty), x + 5, y + 6);
			Component kind = Component.translatable("gui.mlinnotas.village.kind." + bounty.kind().id()).withStyle(ChatFormatting.DARK_AQUA);
			graphics.text(font, kind, x + 26, y + 3, 0xFFFFFFFF);
			graphics.text(font, bountyText(bounty), x + 26 + font.width(kind) + 6, y + 3, done ? 0xFF90C090 : 0xFFFFFFFF);
			int progress = s.bountyProgress(i);
			Component state = done ? Component.translatable("gui.mlinnotas.village.bounty_done").withStyle(ChatFormatting.GREEN)
					: Component.translatable("gui.mlinnotas.village.progress", progress, bounty.count())
					.withStyle(s.bountyReady(i) ? ChatFormatting.GREEN : ChatFormatting.GRAY);
			graphics.text(font, state, x + 26, y + 14, 0xFFFFFFFF);
			// recompensas
			int rx = x + 26;
			int ry = y + 24;
			graphics.text(font, Component.translatable("gui.mlinnotas.village.rewards").withStyle(ChatFormatting.GOLD), rx, ry + 4, 0xFFFFFFFF);
			rx += font.width(Component.translatable("gui.mlinnotas.village.rewards")) + 4;
			graphics.item(new ItemStack(Items.EMERALD), rx, ry);
			graphics.text(font, "x" + bounty.emeralds(), rx + 17, ry + 4, 0xFFFFFFFF);
			rx += 17 + font.width("x" + bounty.emeralds()) + 6;
			ItemStack reward = stack(bounty.rewardItem());
			graphics.item(reward, rx, ry);
			if (hover(mouseX, mouseY, rx, ry, rx + 16, ry + 16)) {
				graphics.setTooltipForNextFrame(font, reward.getHoverName(), mouseX, mouseY);
			}
			graphics.text(font, "x" + bounty.rewardCount(), rx + 17, ry + 4, 0xFFFFFFFF);
			rx += 17 + font.width("x" + bounty.rewardCount()) + 6;
			graphics.text(font, Component.translatable("gui.mlinnotas.village.reward.prosperity_short", bounty.prosperity())
					.withStyle(ChatFormatting.YELLOW), rx, ry + 4, 0xFFFFFFFF);
			y += rowH;
		}
	}

	private static Component offerName(VillageShop.Offer offer) {
		return Component.translatable("village.mlinnotas.shop." + offer.id());
	}

	private static ItemStack offerIcon(VillageShop.Offer offer) {
		return switch (offer.kind()) {
			case ITEM -> {
				ItemStack stack = stack(offer.target());
				stack.setCount(offer.count());
				yield stack;
			}
			case MAP -> new ItemStack(Items.FILLED_MAP);
			case BOOK, RARE_BOOK -> new ItemStack(Items.ENCHANTED_BOOK);
			case REPAIR -> new ItemStack(Items.ANVIL);
		};
	}

	private void shop(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		VillageState s = state();
		int colW = (panelW() - 20) / 2;
		int rowH = shopRow();
		List<VillageShop.Offer> offers = VillageShop.OFFERS;
		for (int i = page * SHOP_ROWS * 2; i < Math.min(offers.size(), (page + 1) * SHOP_ROWS * 2); i++) {
			VillageShop.Offer offer = offers.get(i);
			int slot = i - page * SHOP_ROWS * 2;
			int x = left() + 8 + (slot % 2) * (colW + 4);
			int y = contentTop() + (slot / 2) * rowH;
			boolean locked = s.level().ordinal() < offer.level().ordinal();
			graphics.fill(x, y, x + colW, y + rowH - 2, locked ? 0x30000000 : ROW);
			ItemStack icon = offerIcon(offer);
			graphics.item(icon, x + 3, y + 4);
			graphics.itemDecorations(font, icon, x + 3, y + 4);
			MutableComponent name = offerName(offer).copy();
			FormattedCharSequence line = font.split(name, colW - 80).getFirst();
			graphics.text(font, line, x + 22, y + 3, locked ? 0xFF707070 : 0xFFFFFFFF);
			Component sub = locked ? Component.translatable("gui.mlinnotas.village.requires", levelName(offer.level())).withStyle(ChatFormatting.DARK_GRAY)
					: offer.kind() == VillageShop.Kind.REPAIR ? Component.translatable("gui.mlinnotas.village.repair_price").withStyle(ChatFormatting.GREEN)
					: Component.translatable("gui.mlinnotas.village.price", offer.price()).withStyle(ChatFormatting.GREEN);
			graphics.text(font, sub, x + 22, y + 13, 0xFFFFFFFF);
			if (hover(mouseX, mouseY, x, y, x + colW - 52, y + rowH - 2)) {
				graphics.setTooltipForNextFrame(font, font.split(Component.translatable("village.mlinnotas.shop." + offer.id() + ".desc")
						.withStyle(ChatFormatting.GRAY), 220), mouseX, mouseY);
			}
		}
	}

	private void footer(GuiGraphicsExtractor graphics) {
		VillageState s = state();
		int x = left() + 12 + AREA_BUTTON;
		int y = top() + panelH() - 17;
		Component status;
		if (!view.building().isEmpty()) {
			int pct = view.total() == 0 ? 0 : view.done() * 100 / view.total();
			status = Component.translatable("gui.mlinnotas.village.footer.building", projectName(VillageProject.byId(view.building())), pct)
					.withStyle(ChatFormatting.YELLOW);
		} else if (view.waiting()) {
			status = Component.translatable("gui.mlinnotas.village.footer.no_space").withStyle(ChatFormatting.RED);
		} else if (tab == Tab.BOUNTIES && minecraft.level != null) {
			long left = 24000 - Math.floorMod(minecraft.level.getOverworldClockTime(), 24000L);
			status = Component.translatable("gui.mlinnotas.village.footer.new_bounties", Math.max(1, left / 1200)).withStyle(ChatFormatting.GRAY);
		} else {
			status = Component.translatable("gui.mlinnotas.village.footer.idle", s.totalBuilt()).withStyle(ChatFormatting.GRAY);
		}
		int max = panelW() - (pages() > 1 ? 136 : 92) - AREA_BUTTON - 4;
		graphics.text(font, font.split(status, max).getFirst(), x, y, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pages() > 1 && scrollY != 0) {
			page = Math.floorMod(page + (scrollY < 0 ? 1 : -1), pages());
			rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
