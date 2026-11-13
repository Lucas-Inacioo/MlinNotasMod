package com.mlin.notas.client.skills;

import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillEffects;
import com.mlin.notas.skills.SkillNode;
import com.mlin.notas.skills.SkillProgress;
import com.mlin.notas.skills.SkillTree;
import com.mlin.notas.tier.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tela da arvore de habilidades (tecla K): tronco de Mineracao em cima (12 tiers) e os 4 ramos laterais embaixo, cada
 * nivel alinhado com o tier de Mineracao que exige. Clique seleciona um no; "Depositar" entrega ao servidor o que o
 * jogador tiver do custo (o servidor valida tudo). Tooltip mostra custo, depositado/faltando e o que libera.
 */
public class SkillTreeScreen extends Screen {
	public static final int NODE = 22;
	static final int COL = 28;
	static final int LABEL_W = 72;
	static final int MINING_Y = 38;
	static final int SIDE_Y = 70;
	static final int ROW = 26;

	private static final int BG_UNLOCKED = 0xFF27502A;
	private static final int BG_AVAILABLE = 0xFF5A4A16;
	private static final int BG_LOCKED = 0xFF222226;
	private static final int EDGE_UNLOCKED = 0xFF6BD86B;
	private static final int EDGE_AVAILABLE = 0xFFFFD040;
	private static final int EDGE_LOCKED = 0xFF55555C;
	private static final int LINE_ON = 0xFFE0C050;
	private static final int LINE_OFF = 0xFF505058;

	private SkillNode selected;
	private Button depositButton;
	private Button guideButton;
	private boolean guideMode;

	public SkillTreeScreen() {
		super(Component.translatable("gui.mlinnotas.skills.title"));
	}

	public SkillNode selected() {
		return selected;
	}

	/** Seleciona um no (testes de cliente). */
	public void select(SkillNode node) {
		selected = node;
		updateButton();
	}

	/** Progresso novo do servidor. */
	public void refresh() {
		updateButton();
	}

	private int treeWidth() {
		return LABEL_W + COL * 12;
	}

	int left() {
		return (width - treeWidth()) / 2;
	}

	/** Canto superior esquerdo do no do nivel {@code level} do ramo. */
	public int nodeX(SkillBranch branch, int level) {
		int col = branch == SkillBranch.MINING ? level - 1 : SkillTree.SIDE_REQUIREMENTS[level - 1].ordinal();
		return left() + LABEL_W + col * COL + (COL - NODE) / 2;
	}

	public int nodeY(SkillBranch branch) {
		return branch == SkillBranch.MINING ? MINING_Y : SIDE_Y + (branch.ordinal() - 1) * ROW;
	}

	@Override
	protected void init() {
		depositButton = addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.skills.deposit"), b -> {
			if (selected != null) {
				SkillClient.deposit(selected);
			}
		}).bounds(width / 2 - 154, height - 28, 100, 20).build());
		guideButton = addRenderableWidget(Button.builder(guideLabel(), b -> setGuideMode(!guideMode))
				.bounds(width / 2 - 50, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 54, height - 28, 100, 20).build());
		if (selected == null) {
			// abre ja no proximo objetivo do mundo, com o guia "onde achar" visivel
			selected = SkillTree.nextObjective(SkillClient.progress()::level);
		}
		updateButton();
	}

	private Component guideLabel() {
		return Component.translatable(guideMode ? "gui.mlinnotas.skills.tree" : "gui.mlinnotas.skills.guide_button");
	}

	/** Alterna para a pagina de guia (testes de cliente). */
	public void setGuideMode(boolean value) {
		guideMode = value;
		if (guideButton != null) {
			guideButton.setMessage(guideLabel());
		}
	}

	private void updateButton() {
		if (depositButton != null) {
			depositButton.active = selected != null && SkillClient.progress().status(selected) == SkillProgress.Status.AVAILABLE;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		SkillNode node = nodeAt(event.x(), event.y());
		if (node != null) {
			select(node);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private SkillNode nodeAt(double mx, double my) {
		for (SkillNode node : SkillTree.nodes()) {
			int x = nodeX(node.branch(), node.level());
			int y = nodeY(node.branch());
			if (mx >= x && mx < x + NODE && my >= y && my < y + NODE) {
				return node;
			}
		}
		return null;
	}

	private static boolean startHover(SkillTreeScreen s, double mx, double my) {
		int x = s.nodeX(SkillBranch.MINING, 1);
		return mx >= x && mx < x + NODE && my >= MINING_Y && my < MINING_Y + NODE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		SkillProgress progress = SkillClient.progress();
		int cx = width / 2;
		graphics.centeredText(font, title, cx, 6, 0xFFFFFFFF);
		SkillNode objective = SkillTree.nextObjective(progress::level);
		graphics.centeredText(font, objective == null ? Component.translatable("gui.mlinnotas.skills.objective_done")
				: Component.translatable("gui.mlinnotas.skills.objective", nodeTitle(objective)), cx, 18, 0xFFFFD040);
		int levels = minecraft.player == null ? 0 : minecraft.player.experienceLevel;
		graphics.text(font, Component.translatable("gui.mlinnotas.skills.your_levels", levels), 6, 6, 0xFF80FF20);
		if (guideMode) {
			drawGuidePage(graphics, progress);
			return;
		}

		int left = left();
		int bottom = SIDE_Y + 3 * ROW + NODE + 6;
		graphics.fill(left - 6, MINING_Y - 8, left + treeWidth() + 4, bottom, 0xB0101014);
		graphics.outline(left - 6, MINING_Y - 8, treeWidth() + 10, bottom - MINING_Y + 8, 0xFF3A3A44);

		// linhas
		int half = NODE / 2;
		for (SkillBranch branch : SkillBranch.values()) {
			int y = nodeY(branch) + half;
			for (int level = 2; level <= branch.maxLevel(); level++) {
				int x0 = nodeX(branch, level - 1) + NODE;
				int x1 = nodeX(branch, level);
				boolean on = progress.level(branch) >= level;
				graphics.fill(x0, y - 1, x1, y + 1, on ? LINE_ON : LINE_OFF);
			}
		}
		// espinha: Mineracao (Cobre) -> primeiro nivel de cada ramo lateral
		int spineX = nodeX(SkillBranch.COMBAT, 1) + half;
		int spineTop = MINING_Y + NODE;
		boolean copper = progress.has(SkillBranch.MINING, Tier.COPPER.level());
		for (SkillBranch branch : SkillBranch.values()) {
			if (branch == SkillBranch.MINING) {
				continue;
			}
			int top = branch == SkillBranch.COMBAT ? spineTop : nodeY(branch) - ROW + NODE;
			graphics.fill(spineX - 1, top, spineX + 1, nodeY(branch), copper ? LINE_ON : LINE_OFF);
		}

		// rotulos dos ramos
		for (SkillBranch branch : SkillBranch.values()) {
			Component label = Component.translatable("skill.mlinnotas.branch." + branch.id());
			int y = nodeY(branch) + (NODE - font.lineHeight) / 2 + 1;
			graphics.text(font, label, left, y, 0xFFE0E0E0);
			Component lv = Component.literal(progress.level(branch) + "/" + branch.maxLevel());
			graphics.text(font, lv, left, y + 10, 0xFF909090);
		}

		// no inicial (Madeira)
		drawNode(graphics, nodeX(SkillBranch.MINING, 1), MINING_Y, SkillTree.pickaxe(Tier.WOOD), SkillProgress.Status.UNLOCKED, false, 0);
		for (SkillNode node : SkillTree.nodes()) {
			SkillProgress.Status status = progress.status(node);
			drawNode(graphics, nodeX(node.branch(), node.level()), nodeY(node.branch()), node.icon(), status, node == selected,
					status == SkillProgress.Status.UNLOCKED ? 0 : fraction(progress, node));
		}

		// rodape: selecao e niveis do jogador
		Component info = selected == null ? Component.translatable("gui.mlinnotas.skills.select")
				: nodeTitle(selected).copy().withStyle(statusColor(progress.status(selected)));
		int infoY = Math.min(bottom + 6, height - 40);
		graphics.centeredText(font, info, cx, infoY, 0xFFFFFFFF);
		if (infoY + 12 + font.lineHeight <= height - 32) {
			graphics.centeredText(font, Component.translatable("gui.mlinnotas.skills.guide_hint"), cx, infoY + 12, 0xFFA0A0A0);
		}

		// tooltip
		SkillNode hovered = nodeAt(mouseX, mouseY);
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, tooltip(hovered, progress), Optional.empty(), mouseX, mouseY);
		} else if (startHover(this, mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, List.of(
					Component.translatable("skill.mlinnotas.node.mining", Component.translatable("skill.mlinnotas.branch.mining"),
							Component.translatable("skill.mlinnotas.tier.wood")).withStyle(ChatFormatting.GREEN),
					Component.translatable("gui.mlinnotas.skills.status.unlocked").withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
		}
	}

	private void drawNode(GuiGraphicsExtractor graphics, int x, int y, String icon, SkillProgress.Status status, boolean selectedNode, double fraction) {
		int bg = switch (status) {
			case UNLOCKED -> BG_UNLOCKED;
			case AVAILABLE -> BG_AVAILABLE;
			case LOCKED -> BG_LOCKED;
		};
		int edge = switch (status) {
			case UNLOCKED -> EDGE_UNLOCKED;
			case AVAILABLE -> EDGE_AVAILABLE;
			case LOCKED -> EDGE_LOCKED;
		};
		if (selectedNode) {
			graphics.outline(x - 2, y - 2, NODE + 4, NODE + 4, 0xFFFFFFFF);
		}
		graphics.fill(x, y, x + NODE, y + NODE, bg);
		graphics.outline(x, y, NODE, NODE, edge);
		graphics.item(stack(icon), x + 3, y + 3);
		if (status == SkillProgress.Status.LOCKED) {
			graphics.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, 0x99000000);
		}
		if (fraction > 0) {
			graphics.fill(x + 2, y + NODE - 4, x + NODE - 2, y + NODE - 2, 0xFF101018);
			graphics.fill(x + 2, y + NODE - 4, x + 2 + (int) Math.round((NODE - 4) * fraction), y + NODE - 2, 0xFF60C0FF);
		}
	}

	/**
	 * Pagina de guia (botao Guia), estilo livro de quests: o no selecionado (ou o proximo objetivo), cada item do custo
	 * com icone, depositado/inventario e onde achar, e o que o nivel libera.
	 */
	private void drawGuidePage(GuiGraphicsExtractor graphics, SkillProgress progress) {
		int left = left();
		int top = MINING_Y - 8;
		int right = left + treeWidth() + 4;
		int limit = height - 34;
		graphics.fill(left - 6, top, right, limit, 0xC0101014);
		graphics.outline(left - 6, top, right - left + 6, limit - top, 0xFF3A3A44);
		SkillNode node = selected;
		int x = left;
		int y = top + 6;
		if (node == null) {
			graphics.text(font, Component.translatable("gui.mlinnotas.skills.objective_done"), x, y, 0xFFFFFFFF);
			return;
		}
		SkillProgress.Status status = progress.status(node);
		graphics.item(stack(node.icon()), x, y);
		graphics.text(font, nodeTitle(node).copy().withStyle(statusColor(status), ChatFormatting.BOLD), x + 20, y + 1, 0xFFFFFFFF);
		graphics.text(font, Component.translatable("gui.mlinnotas.skills.status." + status.name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(statusColor(status)), x + 20, y + 10, 0xFFFFFFFF);
		y += 22;
		int textWidth = right - x - 26;
		if (status == SkillProgress.Status.UNLOCKED) {
			graphics.text(font, Component.translatable("gui.mlinnotas.skills.guide_done").withStyle(ChatFormatting.GREEN), x, y, 0xFFFFFFFF);
			return;
		}
		for (String key : SkillTree.missingPrerequisites(node, progress::level)) {
			SkillNode missing = SkillTree.byKey(key);
			if (missing != null && y + font.lineHeight <= limit) {
				graphics.text(font, Component.translatable("gui.mlinnotas.skills.guide_locked", nodeTitle(missing)).withStyle(ChatFormatting.RED), x, y, 0xFFFFFFFF);
				y += font.lineHeight + 2;
			}
		}
		for (SkillNode.Cost cost : node.items()) {
			if (y + 16 > limit) {
				return;
			}
			int have = Math.min(cost.count(), progress.deposited(node, cost.id()));
			graphics.item(stack(cost.display()), x, y);
			Component head = Component.translatable("gui.mlinnotas.skills.guide_item", costName(cost), have, cost.count(), inInventory(cost.id()))
					.withStyle(have >= cost.count() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
			graphics.text(font, head, x + 20, y, 0xFFFFFFFF);
			int ly = y + font.lineHeight + 1;
			for (var part : font.split(Component.translatable(SkillTree.hintKey(cost.id())).withStyle(ChatFormatting.GRAY), textWidth)) {
				if (ly + font.lineHeight > limit) {
					return;
				}
				graphics.text(font, part, x + 20, ly, 0xFFFFFFFF);
				ly += font.lineHeight + 1;
			}
			y = Math.max(y + 18, ly + 3);
		}
		if (node.xpLevels() > 0 && y + font.lineHeight <= limit) {
			int have = Math.min(node.xpLevels(), progress.depositedXp(node));
			graphics.text(font, Component.translatable("gui.mlinnotas.skills.cost_xp", have, node.xpLevels()).withStyle(ChatFormatting.GREEN), x + 20, y, 0xFFFFFFFF);
			y += font.lineHeight + 3;
		}
		for (Component unlock : unlocks(node)) {
			if (y + font.lineHeight > limit) {
				return;
			}
			graphics.text(font, Component.translatable("gui.mlinnotas.skills.unlocks").append(" ").append(unlock).withStyle(ChatFormatting.LIGHT_PURPLE), x, y, 0xFFFFFFFF);
			y += font.lineHeight + 1;
		}
	}

	/** Fracao do custo ja depositada (itens + XP). */
	static double fraction(SkillProgress progress, SkillNode node) {
		int total = node.xpLevels();
		int done = progress.depositedXp(node);
		for (SkillNode.Cost cost : node.items()) {
			total += cost.count();
			done += Math.min(cost.count(), progress.deposited(node, cost.id()));
		}
		return total == 0 ? 0 : (double) done / total;
	}

	static ItemStack stack(String id) {
		return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(Items.BARRIER));
	}

	static Component nodeTitle(SkillNode node) {
		Component branch = Component.translatable("skill.mlinnotas.branch." + node.branch().id());
		if (node.branch() == SkillBranch.MINING) {
			return Component.translatable("skill.mlinnotas.node.mining", branch, Component.translatable("skill.mlinnotas.tier." + node.tier().id()));
		}
		return Component.translatable("skill.mlinnotas.node.side", branch, Component.translatable("enchantment.level." + node.level()));
	}

	private static ChatFormatting statusColor(SkillProgress.Status status) {
		return switch (status) {
			case UNLOCKED -> ChatFormatting.GREEN;
			case AVAILABLE -> ChatFormatting.YELLOW;
			case LOCKED -> ChatFormatting.GRAY;
		};
	}

	static Component costName(SkillNode.Cost cost) {
		if (cost.isTag()) {
			String path = cost.id().substring(cost.id().lastIndexOf('/') + 1);
			return Component.translatable("skill.mlinnotas.cost." + path);
		}
		return stack(cost.id()).getHoverName();
	}

	/** Quanto o jogador local tem no inventario do item de custo. */
	static int inInventory(String costId) {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		return player == null ? 0 : net.minecraft.world.ContainerHelper.clearOrCountMatchingItems(player.getInventory(),
				com.mlin.notas.skills.SkillServer.matcher(costId), Integer.MAX_VALUE, true);
	}

	static List<Component> tooltip(SkillNode node, SkillProgress progress) {
		SkillProgress.Status status = progress.status(node);
		List<Component> lines = new ArrayList<>();
		lines.add(nodeTitle(node).copy().withStyle(statusColor(status), ChatFormatting.BOLD));
		lines.add(Component.translatable("gui.mlinnotas.skills.status." + status.name().toLowerCase(java.util.Locale.ROOT)).withStyle(statusColor(status)));
		if (status == SkillProgress.Status.LOCKED) {
			for (String key : SkillTree.missingPrerequisites(node, progress::level)) {
				SkillNode missing = SkillTree.byKey(key);
				if (missing != null) {
					lines.add(Component.translatable("gui.mlinnotas.skills.requires", nodeTitle(missing)).withStyle(ChatFormatting.RED));
				}
			}
		}
		if (status != SkillProgress.Status.UNLOCKED) {
			lines.add(Component.translatable("gui.mlinnotas.skills.cost").withStyle(ChatFormatting.GRAY));
			int missingItems = 0;
			for (SkillNode.Cost cost : node.items()) {
				int have = Math.min(cost.count(), progress.deposited(node, cost.id()));
				missingItems += cost.count() - have;
				lines.add(Component.literal(" ").append(Component.translatable("gui.mlinnotas.skills.cost_line", costName(cost), have, cost.count(), inInventory(cost.id())))
						.withStyle(have >= cost.count() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
			}
			if (node.xpLevels() > 0) {
				int have = Math.min(node.xpLevels(), progress.depositedXp(node));
				lines.add(Component.literal(" ").append(Component.translatable("gui.mlinnotas.skills.cost_xp", have, node.xpLevels()))
						.withStyle(have >= node.xpLevels() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
			}
			lines.add(Component.translatable("gui.mlinnotas.skills.remaining", missingItems, progress.remainingXp(node)).withStyle(ChatFormatting.AQUA));
		}
		lines.add(Component.translatable("gui.mlinnotas.skills.unlocks").withStyle(ChatFormatting.GRAY));
		for (Component unlock : unlocks(node)) {
			lines.add(Component.literal(" ").append(unlock).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return lines;
	}

	/** O que o no libera (valores totais no nivel). */
	static List<Component> unlocks(SkillNode node) {
		List<Component> out = new ArrayList<>();
		int level = node.level();
		switch (node.branch()) {
			case MINING -> {
				out.add(Component.translatable("skill.mlinnotas.unlock.mining", Component.translatable("skill.mlinnotas.tier." + node.tier().id())));
				if (node.tier() == Tier.CELESTIUM) {
					out.add(Component.translatable("skill.mlinnotas.unlock.celestium_alloy"));
				}
			}
			case COMBAT -> out.add(Component.translatable("skill.mlinnotas.unlock.combat", fmt(SkillEffects.meleeBonus(level))));
			case VIGOR -> out.add(Component.translatable("skill.mlinnotas.unlock.vigor", fmt(SkillEffects.healthBonus(level))));
			case AGILITY -> out.add(Component.translatable("skill.mlinnotas.unlock.agility",
					pct(SkillEffects.speedBonus(level)), pct(SkillEffects.fallReduction(level))));
			case PROSPECTING -> out.add(Component.translatable("skill.mlinnotas.unlock.prospecting", pct(SkillEffects.prospectingChance(level))));
		}
		return out;
	}

	private static String fmt(double value) {
		return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
	}

	private static String pct(double fraction) {
		return String.valueOf((int) Math.round(fraction * 100));
	}

	@Override
	public void tick() {
		super.tick();
		updateButton();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
