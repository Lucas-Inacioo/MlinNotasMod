package com.mlin.notas.client.screen;

import com.mlin.notas.enchanting.EnchantOptions;
import com.mlin.notas.enchanting.EnchantRequestPayload;
import com.mlin.notas.enchanting.EnchantRules;
import com.mlin.notas.enchanting.SelectiveEnchantMenu;
import com.mlin.notas.enchanting.SelectiveEnchanting;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tela da mesa de encantamento com escolha: a lista a direita mostra os encantamentos possiveis para o item;
 * clique numa linha para selecionar, use - e + para o nivel e "Encantar" para aplicar.
 */
public class SelectiveEnchantScreen extends AbstractContainerScreen<SelectiveEnchantMenu> {
	private static final int WIDTH = 276;
	private static final int HEIGHT = 186;
	private static final int LIST_X = 46;
	private static final int LIST_Y = 16;
	private static final int LIST_W = 222;
	private static final int ROW_H = 15;
	private static final int ROWS = 4;
	private static final int NAME_W = 104;
	private static final int MINUS_X = 108;
	private static final int LEVEL_X = 120;
	private static final int PLUS_X = 140;
	private static final int COST_X = 154;
	private static final int BUTTON_W = 12;

	private static final int TEXT = 0xFF404040;
	private static final int TEXT_DISABLED = 0xFF8A8A8A;
	private static final int TEXT_BAD = 0xFFB02020;
	private static final int ROW_BG = 0xFF8B8B8B;
	private static final int ROW_SELECTED = 0xFF5E7DB3;
	private static final int ROW_HOVER = 0xFFA0A0A0;

	private List<EnchantOptions.Option> options = List.of();
	private ItemStack shownStack = ItemStack.EMPTY;
	private final Map<Holder<Enchantment>, Integer> chosenLevels = new HashMap<>();
	private @Nullable Holder<Enchantment> selected;
	private int scroll;
	private Button enchantButton;

	public SelectiveEnchantScreen(SelectiveEnchantMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.inventoryLabelX = SelectiveEnchantMenu.INVENTORY_X;
		this.inventoryLabelY = SelectiveEnchantMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		enchantButton = addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.enchant.apply"), b -> sendRequest())
				.bounds(leftPos + LIST_X + LIST_W - 70, topPos + LIST_Y + ROWS * ROW_H + 2, 70, 14)
				.build());
		refreshOptions();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshOptions();
	}

	/** Recalcula a lista quando o item do slot muda (inclusive depois de encantar). */
	private void refreshOptions() {
		ItemStack stack = menu.item();
		if (ItemStack.matches(stack, shownStack)) {
			updateButton();
			return;
		}
		boolean sameItem = ItemStack.isSameItem(stack, shownStack);
		shownStack = stack.copy();
		options = minecraft == null || minecraft.level == null ? List.of() : EnchantOptions.forItem(minecraft.level.registryAccess(), stack);
		chosenLevels.clear();
		if (!sameItem) {
			selected = null;
			scroll = 0;
		}
		scroll = Math.min(scroll, Math.max(0, options.size() - ROWS));
		updateButton();
	}

	private int chosenLevel(EnchantOptions.Option option) {
		return chosenLevels.getOrDefault(option.enchantment(), defaultLevel(option));
	}

	/** Nivel inicial: o maior que as estantes permitem (no minimo o atual + 1). */
	private int defaultLevel(EnchantOptions.Option option) {
		int allowed = Math.min(EnchantRules.maxAllowedLevel(option.maxLevel(), menu.bookshelves()),
				EnchantRules.cappedLevel(option.maxLevel(), menu.miningLevel()));
		return Math.min(option.maxLevel(), Math.max(option.currentLevel() + 1, allowed));
	}

	private EnchantRules.Check check(EnchantOptions.Option option, int level) {
		return EnchantRules.check(!menu.item().isEmpty(), true, option.compatible(), option.currentLevel(), level,
				option.maxLevel(), option.anvilCost(), menu.bookshelves(), menu.miningLevel(), minecraft.player.experienceLevel,
				menu.lapis().getCount(), option.reagent().base(), menu.reagentCount(option.reagent()), minecraft.player.hasInfiniteMaterials());
	}

	private EnchantOptions.@Nullable Option selectedOption() {
		if (selected == null) {
			return null;
		}
		for (EnchantOptions.Option option : options) {
			if (option.enchantment().equals(selected)) {
				return option;
			}
		}
		return null;
	}

	private void updateButton() {
		if (enchantButton == null) {
			return;
		}
		EnchantOptions.Option option = selectedOption();
		if (option == null) {
			enchantButton.active = false;
			enchantButton.setTooltip(Tooltip.create(Component.translatable(menu.item().isEmpty()
					? "message.mlinnotas.enchant.no_item" : "gui.mlinnotas.enchant.select")));
			return;
		}
		EnchantRules.Check check = check(option, chosenLevel(option));
		enchantButton.active = check == EnchantRules.Check.OK;
		enchantButton.setTooltip(check == EnchantRules.Check.OK ? null : Tooltip.create(reason(option, check)));
	}

	private Component reason(EnchantOptions.Option option, EnchantRules.Check check) {
		if (check == EnchantRules.Check.NEEDS_BOOKSHELVES) {
			return Component.translatable("gui.mlinnotas.enchant.needs_bookshelves",
					EnchantRules.bookshelvesNeeded(option.maxLevel(), chosenLevel(option)));
		}
		if (check == EnchantRules.Check.MINING_CAP) {
			return miningCapReason(chosenLevel(option));
		}
		if (check == EnchantRules.Check.NOT_ENOUGH_REAGENT) {
			return Component.translatable("gui.mlinnotas.enchant.needs_reagent", reagentNeeded(option, chosenLevel(option)), reagentStack(option).getHoverName());
		}
		return SelectiveEnchanting.message(check);
	}

	/** "Nivel IV exige Mineracao: Infernita" (teto do mundo pela arvore de habilidades). */
	/** Item do reagente do encantamento (barreira se o id nao existir). */
	private static ItemStack reagentStack(EnchantOptions.Option option) {
		return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(option.reagent().item()))
				.orElse(net.minecraft.world.item.Items.BARRIER));
	}

	private static int reagentNeeded(EnchantOptions.Option option, int level) {
		return com.mlin.notas.enchanting.EnchantReagents.cost(option.reagent().base(), option.currentLevel(), level);
	}

	private static Component miningCapReason(int level) {
		return Component.translatable("gui.mlinnotas.enchant.mining_cap", Component.translatable("enchantment.level." + level),
				com.mlin.notas.skills.SkillServer.tierName(EnchantRules.tierForLevel(level)));
	}

	/** Seleciona Afiacao (ou o primeiro encantamento da lista); usado pelo client game test para a foto. */
	public void selectFirstUsable() {
		refreshOptions();
		for (EnchantOptions.Option option : options) {
			if (option.enchantment().is(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS)) {
				selected = option.enchantment();
				updateButton();
				return;
			}
		}
		if (!options.isEmpty()) {
			selected = options.getFirst().enchantment();
			updateButton();
		}
	}

	private void sendRequest() {
		EnchantOptions.Option option = selectedOption();
		if (option != null && ClientPlayNetworking.canSend(EnchantRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new EnchantRequestPayload(option.enchantment(), chosenLevel(option)));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		GridScreenBackground.panel(graphics, x, y, imageWidth, imageHeight);
		GridScreenBackground.slot(graphics, x + SelectiveEnchantMenu.ITEM_X - 1, y + SelectiveEnchantMenu.ITEM_Y - 1);
		GridScreenBackground.slot(graphics, x + SelectiveEnchantMenu.LAPIS_X - 1, y + SelectiveEnchantMenu.LAPIS_Y - 1);
		GridScreenBackground.slot(graphics, x + SelectiveEnchantMenu.REAGENT_X - 1, y + SelectiveEnchantMenu.REAGENT_Y - 1);
		GridScreenBackground.playerInventory(graphics, x + SelectiveEnchantMenu.INVENTORY_X, y + SelectiveEnchantMenu.INVENTORY_Y);

		graphics.text(font, Component.translatable("gui.mlinnotas.enchant.bookshelves"), x + 6, y + 86, TEXT, false);
		graphics.text(font, Math.min(menu.bookshelves(), EnchantRules.MAX_BOOKSHELVES) + "/" + EnchantRules.MAX_BOOKSHELVES, x + 6, y + 96, TEXT, false);
		int cap = EnchantRules.miningCap(menu.miningLevel());
		graphics.text(font, Component.translatable("gui.mlinnotas.enchant.cap", cap == Integer.MAX_VALUE
				? Component.translatable("gui.mlinnotas.enchant.cap_none") : Component.translatable("enchantment.level." + cap)), x + 6, y + 106, TEXT, false);

		drawList(graphics, mouseX, mouseY);
	}

	private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int lx = leftPos + LIST_X;
		int ly = topPos + LIST_Y;
		graphics.fill(lx - 1, ly - 1, lx + LIST_W + 1, ly + ROWS * ROW_H + 1, GridScreenBackground.SLOT_DARK);
		graphics.fill(lx, ly, lx + LIST_W, ly + ROWS * ROW_H, 0xFF6B6B6B);
		if (options.isEmpty()) {
			Component hint = Component.translatable(menu.item().isEmpty() ? "gui.mlinnotas.enchant.place_item" : "gui.mlinnotas.enchant.none");
			graphics.centeredText(font, hint, lx + LIST_W / 2, ly + ROWS * ROW_H / 2 - 4, 0xFFE0E0E0);
			return;
		}
		for (int row = 0; row < ROWS && scroll + row < options.size(); row++) {
			EnchantOptions.Option option = options.get(scroll + row);
			int ry = ly + row * ROW_H;
			boolean isSelected = option.enchantment().equals(selected);
			boolean hovered = mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;
			graphics.fill(lx + 1, ry + 1, lx + LIST_W - 1, ry + ROW_H - 1, isSelected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW_BG);

			int level = chosenLevel(option);
			EnchantRules.Check check = check(option, level);
			boolean usable = option.compatible() && option.currentLevel() < option.maxLevel();
			int nameColor = !usable ? TEXT_DISABLED : isSelected ? 0xFFFFFFFF : TEXT;
			String name = font.plainSubstrByWidth(option.name(), NAME_W - 6);
			graphics.text(font, name, lx + 4, ry + 4, nameColor, false);

			if (option.maxLevel() > 1) {
				drawSmallButton(graphics, lx + MINUS_X, ry + 2, "-", level > option.currentLevel() + 1);
				graphics.centeredText(font, Component.translatable("enchantment.level." + level), lx + (LEVEL_X + PLUS_X) / 2, ry + 4,
						check == EnchantRules.Check.NEEDS_BOOKSHELVES || check == EnchantRules.Check.MINING_CAP ? TEXT_BAD : 0xFFFFFFFF);
				drawSmallButton(graphics, lx + PLUS_X, ry + 2, "+", level < option.maxLevel());
			}

			if (!option.compatible()) {
				graphics.text(font, Component.translatable("gui.mlinnotas.enchant.incompatible"), lx + COST_X, ry + 4, TEXT_BAD, false);
			} else if (option.currentLevel() >= option.maxLevel()) {
				graphics.text(font, Component.translatable("gui.mlinnotas.enchant.maxed"), lx + COST_X, ry + 4, TEXT_DISABLED, false);
			} else {
				int xp = EnchantRules.xpCost(option.anvilCost(), option.currentLevel(), level);
				int lapis = EnchantRules.lapisCost(option.currentLevel(), level);
				int xpColor = check == EnchantRules.Check.NOT_ENOUGH_XP ? TEXT_BAD : 0xFF80FF20;
				int lapisColor = check == EnchantRules.Check.NOT_ENOUGH_LAPIS ? TEXT_BAD : 0xFF6080FF;
				String xpText = xp + "XP";
				String lapisText = lapis + "L";
				graphics.text(font, xpText, lx + COST_X, ry + 4, xpColor, true);
				int rx = lx + COST_X + font.width(xpText) + 4;
				graphics.text(font, lapisText, rx, ry + 4, lapisColor, true);
				// reagente: icone pequeno + quantidade (vermelho se faltar)
				rx += font.width(lapisText) + 3;
				graphics.pose().pushMatrix();
				graphics.pose().translate(rx, ry + 3);
				graphics.pose().scale(0.5F, 0.5F);
				graphics.item(reagentStack(option), 0, 0);
				graphics.pose().popMatrix();
				int need = reagentNeeded(option, level);
				graphics.text(font, String.valueOf(need), rx + 9, ry + 4,
						check == EnchantRules.Check.NOT_ENOUGH_REAGENT || menu.reagentCount(option.reagent()) < need ? TEXT_BAD : 0xFFFFE080, true);
			}
		}
		// reagente do encantamento escolhido, por extenso, ao lado do botao
		EnchantOptions.Option chosen = selectedOption();
		if (chosen != null && chosen.compatible() && chosen.currentLevel() < chosen.maxLevel()) {
			int level = chosenLevel(chosen);
			int need = reagentNeeded(chosen, level);
			int have = menu.reagentCount(chosen.reagent());
			int by = ly + ROWS * ROW_H + 5;
			graphics.item(reagentStack(chosen), lx, by - 4);
			Component line = Component.translatable("gui.mlinnotas.enchant.reagent", reagentStack(chosen).getHoverName(), need, have);
			graphics.text(font, font.split(line, LIST_W - 92).getFirst(), lx + 18, by, have >= need ? TEXT : TEXT_BAD, false);
		}
		// barra de rolagem
		if (options.size() > ROWS) {
			int trackH = ROWS * ROW_H;
			int thumbH = Math.max(8, trackH * ROWS / options.size());
			int thumbY = ly + (trackH - thumbH) * scroll / (options.size() - ROWS);
			graphics.fill(lx + LIST_W - 3, thumbY, lx + LIST_W - 1, thumbY + thumbH, 0xFFE0E0E0);
		}
	}

	private void drawSmallButton(GuiGraphicsExtractor graphics, int x, int y, String label, boolean active) {
		graphics.fill(x, y, x + BUTTON_W, y + 11, active ? 0xFF3A3A3A : 0xFF5A5A5A);
		graphics.fill(x + 1, y + 1, x + BUTTON_W - 1, y + 10, active ? 0xFF9A9A9A : 0xFF7A7A7A);
		graphics.centeredText(font, label, x + BUTTON_W / 2, y + 2, active ? 0xFFFFFFFF : 0xFFB0B0B0);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int row = rowAt(mouseX, mouseY);
		if (row >= 0) {
			EnchantOptions.Option option = options.get(row);
			int relX = mouseX - leftPos - LIST_X;
			if (relX < NAME_W) {
				Component tip = Component.translatable("gui.mlinnotas.enchant.row_tooltip",
						Enchantment.getFullname(option.enchantment(), option.maxLevel()),
						option.currentLevel());
				graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
			}
		}
	}

	/** Indice (em options) da linha sob o mouse, ou -1. */
	private int rowAt(double mouseX, double mouseY) {
		int lx = leftPos + LIST_X;
		int ly = topPos + LIST_Y;
		if (mouseX < lx || mouseX >= lx + LIST_W || mouseY < ly || mouseY >= ly + ROWS * ROW_H) {
			return -1;
		}
		int index = scroll + (int) ((mouseY - ly) / ROW_H);
		return index < options.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int row = rowAt(event.x(), event.y());
		if (row >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			EnchantOptions.Option option = options.get(row);
			int relX = (int) event.x() - leftPos - LIST_X;
			int level = chosenLevel(option);
			if (option.maxLevel() > 1 && relX >= MINUS_X && relX < MINUS_X + BUTTON_W) {
				chosenLevels.put(option.enchantment(), Math.max(option.currentLevel() + 1, level - 1));
			} else if (option.maxLevel() > 1 && relX >= PLUS_X && relX < PLUS_X + BUTTON_W) {
				chosenLevels.put(option.enchantment(), Math.min(option.maxLevel(), level + 1));
			}
			selected = option.enchantment();
			updateButton();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (rowAt(x, y) >= 0 || (options.size() > ROWS && x >= leftPos + LIST_X && x < leftPos + LIST_X + LIST_W)) {
			scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, options.size() - ROWS));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
}
