package com.mlin.notas.client.waystone;

import com.mlin.notas.waystone.WaystoneCost;
import com.mlin.notas.waystone.WaystoneInfo;
import com.mlin.notas.waystone.WaystoneRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Tela da pedra de teleporte: renomear a pedra atual e escolher o destino na lista de pedras ativadas (com custo em
 * niveis). Na primeira vez (recem-ativada) pede um nome.
 */
public class WaystoneScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int ROW_WIDTH = 240;
	private static final int COLOR_OK = 0x80FF80;
	private static final int COLOR_EXPENSIVE = 0xFF6060;

	private WaystoneInfo source;
	private final boolean justActivated;
	private EditBox nameBox;
	private String pendingName;
	private int page;
	private int shownLevels = -1;

	public WaystoneScreen(WaystoneInfo source, boolean justActivated) {
		super(Component.translatable("gui.mlinnotas.waystone.title"));
		this.source = source;
		this.justActivated = justActivated;
	}

	public WaystoneInfo source() {
		return source;
	}

	/** Lista nova do servidor: atualiza o nome atual e os destinos. */
	public void refresh() {
		for (WaystoneInfo info : WaystoneClient.all()) {
			if (info.key().equals(source.key())) {
				source = info;
			}
		}
		pendingName = nameBox == null ? null : nameBox.getValue();
		rebuildWidgets();
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int top = justActivated ? 40 : 30;
		nameBox = new EditBox(font, cx - ROW_WIDTH / 2, top, ROW_WIDTH - 74, 20, Component.translatable("gui.mlinnotas.waystone.here"));
		nameBox.setMaxLength(WaystoneInfo.MAX_NAME);
		nameBox.setValue(pendingName != null ? pendingName : source.name());
		addRenderableWidget(nameBox);
		addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.waystone.rename"), b -> {
			String clean = WaystoneInfo.cleanName(nameBox.getValue());
			if (clean != null && !clean.equals(source.name())) {
				WaystoneClient.rename(source, clean);
			}
			pendingName = null;
		}).bounds(cx + ROW_WIDTH / 2 - 70, top, 70, 20).build());
		if (justActivated) {
			setInitialFocus(nameBox);
		}

		List<WaystoneInfo> destinations = WaystoneRegistry.destinations(WaystoneClient.all(), source);
		int listTop = top + 34;
		int rows = Math.max(1, (height - listTop - 50) / ROW_HEIGHT);
		int pages = Math.max(1, (destinations.size() + rows - 1) / rows);
		page = Math.min(page, pages - 1);
		boolean creative = WaystoneClient.creative(minecraft);
		int levels = minecraft.player == null ? 0 : minecraft.player.experienceLevel;
		shownLevels = levels;
		int y = listTop;
		for (int i = page * rows; i < Math.min(destinations.size(), (page + 1) * rows); i++) {
			WaystoneInfo target = destinations.get(i);
			int cost = WaystoneCost.charged(source, target, creative);
			Component costText = cost == 0 ? Component.translatable("gui.mlinnotas.waystone.free")
					: cost == 1 ? Component.translatable("gui.mlinnotas.waystone.cost_one")
					: Component.translatable("gui.mlinnotas.waystone.cost", cost);
			int color = WaystoneCost.canAfford(levels, cost) ? COLOR_OK : COLOR_EXPENSIVE;
			Component label = Component.literal(target.name() + "  ").append(costText.copy().withColor(color));
			Component where = target.dimension().equals(source.dimension())
					? Component.translatable("gui.mlinnotas.waystone.distance", (int) Math.round(source.horizontalDistance(target)))
					: Component.translatable("gui.mlinnotas.waystone.other_dimension");
			addRenderableWidget(Button.builder(label, b -> {
						WaystoneClient.travel(source, target);
						onClose();
					})
					.bounds(cx - ROW_WIDTH / 2, y, ROW_WIDTH, 20)
					.tooltip(Tooltip.create(Component.literal(target.x() + ", " + target.y() + ", " + target.z() + " - ").append(where)))
					.build());
			y += ROW_HEIGHT;
		}

		int bottom = height - 28;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page = (page + pages - 1) % pages;
				refresh();
			}).bounds(cx - ROW_WIDTH / 2, bottom, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page = (page + 1) % pages;
				refresh();
			}).bounds(cx + ROW_WIDTH / 2 - 20, bottom, 20, 20).build());
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(cx - 40, bottom, 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = width / 2;
		graphics.centeredText(font, title, cx, 10, 0xFFFFFFFF);
		int top = justActivated ? 40 : 30;
		if (justActivated) {
			graphics.centeredText(font, Component.translatable("gui.mlinnotas.waystone.activated"), cx, 24, 0xFF80FF80);
		}
		if (WaystoneRegistry.destinations(WaystoneClient.all(), source).isEmpty()) {
			graphics.centeredText(font, Component.translatable("gui.mlinnotas.waystone.none"), cx, top + 40, 0xFFA0A0A0);
		}
		int levels = minecraft.player == null ? 0 : minecraft.player.experienceLevel;
		graphics.text(font, Component.translatable("gui.mlinnotas.waystone.your_levels", levels), 6, height - 22, 0xFF80FF20);
	}

	/** Os niveis do jogador podem chegar depois da tela abrir: refaz as cores dos custos quando mudam. */
	@Override
	public void tick() {
		super.tick();
		int levels = minecraft.player == null ? 0 : minecraft.player.experienceLevel;
		if (levels != shownLevels) {
			refresh();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
