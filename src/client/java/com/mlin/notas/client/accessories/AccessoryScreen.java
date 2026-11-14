package com.mlin.notas.client.accessories;

import com.mlin.notas.accessories.AccessoryBudget;
import com.mlin.notas.accessories.AccessoryMenu;
import com.mlin.notas.accessories.AccessoryStat;
import com.mlin.notas.client.screen.GridScreenBackground;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela dos acessorios: encaixes dos dois lados do boneco do jogador, painel do estagio do mundo com uma barra por
 * atributo com teto (cheia = no teto; vermelha = passou e esta sendo cortada) e o inventario embaixo. Mouse no painel
 * lista todos os bonus somados.
 */
public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> {
	static final int PANEL_X = 99;
	static final int PANEL_W = 70;
	static final int BAR_X = 127;
	static final int BAR_W = 41;
	static final int FIRST_BAR_Y = 34;
	static final int BAR_STEP = 11;
	static final AccessoryStat[] BARS = {AccessoryStat.DAMAGE, AccessoryStat.DAMAGE_REDUCTION, AccessoryStat.SPEED, AccessoryStat.MAX_HEALTH};

	public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, AccessoryMenu.WIDTH, AccessoryMenu.HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(new AccessoryClient.IconButton(leftPos + AccessoryClient.BUTTON_X, topPos + AccessoryClient.BUTTON_Y,
				new ItemStack(Items.CHEST), Component.translatable("button.mlinnotas.accessories.back"), button -> AccessoryClient.backToInventory()));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		GridScreenBackground.panel(graphics, x, y, imageWidth, imageHeight);
		for (int[] pos : AccessoryMenu.SLOT_POS) {
			GridScreenBackground.slot(graphics, x + pos[0] - 1, y + pos[1] - 1);
		}
		GridScreenBackground.playerInventory(graphics, x + 8, y + AccessoryMenu.INVENTORY_TOP);
		// janela do boneco (como a do inventario vanilla)
		graphics.fill(x + 26, y + 8, x + 76, y + 78, 0xFF373737);
		graphics.fill(x + 27, y + 9, x + 76, y + 78, 0xFFFFFFFF);
		graphics.fill(x + 27, y + 9, x + 75, y + 77, 0xFF000000);
		if (minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + 27, y + 9, x + 75, y + 77, 30, 0.0625F, mouseX, mouseY,
					minecraft.player);
		}
		// painel do estagio
		graphics.fill(x + PANEL_X - 1, y + 7, x + PANEL_X + PANEL_W + 1, y + 79, 0xFF8B8B8B);
		graphics.fill(x + PANEL_X, y + 8, x + PANEL_X + PANEL_W, y + 78, 0xFF6F6F6F);
		AccessoryBudget.Totals totals = AccessoryClient.totals();
		for (int i = 0; i < BARS.length; i++) {
			AccessoryStat stat = BARS[i];
			int barY = y + FIRST_BAR_Y + i * BAR_STEP;
			int bx = x + BAR_X;
			graphics.fill(bx - 1, barY - 1, bx + BAR_W + 1, barY + 6, 0xFF2A2A2A);
			graphics.fill(bx, barY, bx + BAR_W, barY + 5, 0xFF4A4A4A);
			double cap = stat.cap(totals.stage());
			double raw = totals.raw(stat);
			if (cap > 0 && raw > 0) {
				int fill = (int) Math.round(BAR_W * Math.min(1.0, raw / cap));
				int color = totals.capped(stat) ? 0xFFE0503A : raw >= cap - 1.0E-9 ? 0xFFE8C03A : 0xFF5AC85A;
				graphics.fill(bx, barY, bx + fill, barY + 5, color);
				graphics.fill(bx, barY, bx + fill, barY + 1, 0x40FFFFFF);
			} else if (cap <= 0) {
				// sem orcamento neste estagio: barra riscada
				for (int px = 0; px < BAR_W; px += 4) {
					graphics.fill(bx + px, barY + 2, bx + px + 2, barY + 3, 0xFF7A7A7A);
				}
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		AccessoryBudget.Totals totals = AccessoryClient.totals();
		graphics.text(font, Component.translatable("gui.mlinnotas.accessories.stage"), PANEL_X + 3, 11, 0xFFE0E0E0, false);
		// nome do estagio encolhe para caber no painel
		Component stageName = Component.translatable("accessory.mlinnotas.stage." + totals.stage().id());
		float scale = Math.min(1.0F, (PANEL_W - 6) / (float) Math.max(1, font.width(stageName)));
		graphics.pose().pushMatrix();
		graphics.pose().translate(PANEL_X + 3, 21 + (1.0F - scale) * 4);
		graphics.pose().scale(scale, scale);
		graphics.text(font, stageName, 0, 0, 0xFFFFE07A, false);
		graphics.pose().popMatrix();
		for (int i = 0; i < BARS.length; i++) {
			graphics.text(font, Component.translatable("gui.mlinnotas.accessories.bar." + BARS[i].id()), PANEL_X + 3,
					FIRST_BAR_Y - 1 + i * BAR_STEP, 0xFFFFFFFF, false);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int px = mouseX - leftPos;
		int py = mouseY - topPos;
		if (px >= PANEL_X && px < PANEL_X + PANEL_W && py >= 8 && py < 78 && menu.getCarried().isEmpty()) {
			graphics.setComponentTooltipForNextFrame(font, summary(AccessoryClient.totals()), mouseX, mouseY);
		}
	}

	/** Texto do painel: estagio, cada atributo somado (com teto) e os efeitos. */
	static List<Component> summary(AccessoryBudget.Totals totals) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("gui.mlinnotas.accessories.summary", Component.translatable("accessory.mlinnotas.stage." + totals.stage().id()))
				.withStyle(ChatFormatting.YELLOW));
		for (AccessoryStat stat : BARS) {
			lines.add(statLine(totals, stat));
		}
		for (AccessoryStat stat : AccessoryStat.values()) {
			if (!stat.staged() && totals.lines().containsKey(stat)) {
				lines.add(statLine(totals, stat));
			}
		}
		if (totals.lines().isEmpty()) {
			lines.add(Component.translatable("gui.mlinnotas.accessories.none").withStyle(ChatFormatting.GRAY));
		}
		totals.abilities().forEach(ability -> lines.add(Component.translatable("accessory.mlinnotas.ability." + ability.id())
				.withStyle(ChatFormatting.DARK_AQUA)));
		lines.add(Component.translatable("gui.mlinnotas.accessories.stage_hint").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private static Component statLine(AccessoryBudget.Totals totals, AccessoryStat stat) {
		double raw = totals.raw(stat);
		double cap = stat.cap(totals.stage());
		Component name = Component.translatable("accessory.mlinnotas.stat." + stat.id(), stat.format(totals.applied(stat)));
		if (totals.capped(stat)) {
			return Component.translatable("gui.mlinnotas.accessories.line_capped", name, stat.format(raw)).withStyle(ChatFormatting.RED);
		}
		return Component.translatable("gui.mlinnotas.accessories.line", name, stat.format(cap))
				.withStyle(raw > 0 ? ChatFormatting.BLUE : ChatFormatting.GRAY);
	}
}
