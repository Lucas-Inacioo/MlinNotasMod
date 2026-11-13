package com.mlin.notas.client.recipes;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.recipes.RecipeKind;
import com.mlin.notas.recipes.RecipeView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela que mostra as receitas que produzem um item (ou os usos dele), uma por pagina.
 * Clicar num ingrediente navega para as receitas dele; Esc volta para a tela de container original.
 */
public class RecipeScreen extends Screen {
	public enum Mode { RECIPES, USAGES }

	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	private static final int PANEL_WIDTH = 176;
	private static final int PANEL_HEIGHT = 130;
	private static final int SLOT = 18;

	private final Screen root;
	private final Screen parent;
	private final ItemStack subject;
	private final Mode mode;
	private final List<RecipeView> recipes;
	private int page;
	private int panelX;
	private int panelY;
	private Button previousButton;
	private Button nextButton;

	/** Slots desenhados na pagina atual, para cliques e tooltips. */
	private final List<DrawnSlot> drawnSlots = new ArrayList<>();

	private record DrawnSlot(int x, int y, List<Integer> alternatives, ItemStack fixed, boolean output) {
		ItemStack current() {
			if (fixed != null) {
				return fixed;
			}
			if (alternatives.isEmpty()) {
				return ItemStack.EMPTY;
			}
			int index = (int) ((System.currentTimeMillis() / 1000L) % alternatives.size());
			return RecipeViewerClient.stackOf(alternatives.get(index));
		}

		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + SLOT && my < y + SLOT;
		}
	}

	public RecipeScreen(Screen root, Screen parent, ItemStack subject, Mode mode, List<RecipeView> recipes) {
		super(Component.translatable(mode == Mode.RECIPES ? "gui.mlinnotas.recipes.title" : "gui.mlinnotas.recipes.usages", subject.getHoverName()));
		this.root = root;
		this.parent = parent;
		this.subject = subject;
		this.mode = mode;
		this.recipes = recipes;
	}

	public Screen root() {
		return root;
	}

	@Override
	protected void init() {
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = (height - PANEL_HEIGHT) / 2;
		previousButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
				.bounds(panelX + 4, panelY + PANEL_HEIGHT - 24, 20, 20).build());
		nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
				.bounds(panelX + PANEL_WIDTH - 24, panelY + PANEL_HEIGHT - 24, 20, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.recipes.back"), b -> minecraft.gui.setScreen(parent))
				.bounds(panelX + 30, panelY + PANEL_HEIGHT - 24, 50, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.recipes.close"), b -> onClose())
				.bounds(panelX + PANEL_WIDTH - 80, panelY + PANEL_HEIGHT - 24, 50, 20).build());
		updateButtons();
	}

	private void turn(int delta) {
		page = Math.floorMod(page + delta, recipes.size());
		updateButtons();
	}

	private void updateButtons() {
		previousButton.active = recipes.size() > 1;
		nextButton.active = recipes.size() > 1;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(root);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		drawPanel(graphics);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		drawRecipe(graphics, recipes.get(page));
		drawTooltip(graphics, mouseX, mouseY);
	}

	private void drawPanel(GuiGraphicsExtractor graphics) {
		graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, 0xFF000000);
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFFC6C6C6);
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, 0xFFFFFFFF);
		graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, 0xFFFFFFFF);
		graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF555555);
		graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF555555);
		graphics.centeredText(font, title, panelX + PANEL_WIDTH / 2, panelY + 6, 0xFF404040);
	}

	private void drawRecipe(GuiGraphicsExtractor graphics, RecipeView recipe) {
		drawnSlots.clear();
		Component kindLabel = Component.translatable("gui.mlinnotas.recipes.kind." + recipe.kind().name().toLowerCase(java.util.Locale.ROOT));
		String pageLabel = (page + 1) + "/" + recipes.size();
		graphics.text(font, kindLabel, panelX + 8, panelY + 18, 0xFF404040);
		graphics.text(font, pageLabel, panelX + PANEL_WIDTH - 8 - font.width(pageLabel), panelY + 18, 0xFF404040);

		int top = panelY + 32;
		ItemStack output = RecipeViewerClient.stackOf(recipe.output()).copyWithCount(recipe.outputCount());
		if (recipe.kind().isCrafting()) {
			int gridX = panelX + 12;
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					addSlot(gridX + col * SLOT, top + row * SLOT, recipe.gridSlot(row, col), null, false);
				}
			}
			drawArrow(graphics, gridX + 3 * SLOT + 10, top + SLOT + 5);
			addSlot(gridX + 3 * SLOT + 40, top + SLOT, List.of(), output, true);
		} else if (recipe.kind().isCooking()) {
			int inX = panelX + 30;
			addSlot(inX, top + SLOT, recipe.inputs().getFirst(), null, false);
			drawArrow(graphics, inX + SLOT + 22, top + SLOT + 5);
			addSlot(inX + SLOT + 64, top + SLOT, List.of(), output, true);
			String time = String.format(java.util.Locale.ROOT, "%.1fs", recipe.cookTicks() / 20.0F);
			String xp = String.format(java.util.Locale.ROOT, "%.1f xp", recipe.experience());
			graphics.text(font, time, inX + SLOT + 14, top + 2 * SLOT + 6, 0xFF404040);
			graphics.text(font, xp, inX + SLOT + 14, top + 2 * SLOT + 16, 0xFF404040);
		} else {
			int inX = panelX + 30;
			addSlot(inX, top + SLOT, recipe.inputs().getFirst(), null, false);
			drawArrow(graphics, inX + SLOT + 22, top + SLOT + 5);
			addSlot(inX + SLOT + 64, top + SLOT, List.of(), output, true);
		}

		for (DrawnSlot slot : drawnSlots) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, slot.x(), slot.y(), SLOT, SLOT);
			ItemStack stack = slot.current();
			if (!stack.isEmpty()) {
				graphics.item(stack, slot.x() + 1, slot.y() + 1);
				graphics.itemDecorations(font, stack, slot.x() + 1, slot.y() + 1);
			}
		}
	}

	private void addSlot(int x, int y, List<Integer> alternatives, ItemStack fixed, boolean output) {
		drawnSlots.add(new DrawnSlot(x, y, alternatives, fixed, output));
	}

	private void drawArrow(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y + 3, x + 14, y + 7, 0xFF404040);
		for (int i = 0; i < 5; i++) {
			graphics.fill(x + 14 + i, y + i, x + 15 + i, y + 10 - i, 0xFF404040);
		}
	}

	private DrawnSlot slotAt(double mouseX, double mouseY) {
		for (DrawnSlot slot : drawnSlots) {
			if (slot.contains(mouseX, mouseY)) {
				return slot;
			}
		}
		return null;
	}

	private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		DrawnSlot slot = slotAt(mouseX, mouseY);
		if (slot != null) {
			ItemStack stack = slot.current();
			if (!stack.isEmpty()) {
				graphics.fill(slot.x() + 1, slot.y() + 1, slot.x() + SLOT - 1, slot.y() + SLOT - 1, 0x80FFFFFF);
				graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		DrawnSlot slot = slotAt(event.x(), event.y());
		if (slot == null) {
			return false;
		}
		ItemStack stack = slot.current();
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			return RecipeViewerClient.openRecipes(stack, this);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			return RecipeViewerClient.openUsages(stack, this);
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
		double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
		DrawnSlot slot = slotAt(mouseX, mouseY);
		ItemStack hovered = slot == null ? ItemStack.EMPTY : slot.current();
		if (ModKeys.SHOW_RECIPES.matches(event)) {
			return RecipeViewerClient.openRecipes(hovered, this);
		}
		if (ModKeys.SHOW_USAGES.matches(event)) {
			return RecipeViewerClient.openUsages(hovered, this);
		}
		if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_PAGEUP) {
			turn(-1);
			return true;
		}
		if (event.key() == InputConstants.KEY_RIGHT || event.key() == InputConstants.KEY_PAGEDOWN) {
			turn(1);
			return true;
		}
		if (minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		return false;
	}

	public ItemStack subject() {
		return subject;
	}

	public Mode mode() {
		return mode;
	}

	public RecipeKind currentKind() {
		return recipes.get(page).kind();
	}
}
