package com.mlin.notas.client.beacon;

import com.mlin.notas.beacon.BeaconMenuTier;
import com.mlin.notas.beacon.BeaconTiers;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.client.mixin.BeaconScreenAccessor;
import com.mlin.notas.registry.ModEffects;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.BeaconMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Extras da tela vanilla do sinalizador (sem substituir a tela): acima da janela, o metal da piramide, o alcance e os
 * poderes que chegam ao nivel III; na coluna secundaria, abaixo dos botoes vanilla, os poderes Garimpo e Voo
 * (escolher um deles ocupa o lugar do secundario, como a Regeneracao).
 */
public final class BeaconScreenExtras {
	private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("container/beacon/button_disabled");
	private static final Identifier BUTTON_SELECTED = Identifier.withDefaultNamespace("container/beacon/button_selected");
	private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("container/beacon/button_highlighted");
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("container/beacon/button");

	private BeaconScreenExtras() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof BeaconScreen beacon)) {
				return;
			}
			AbstractContainerScreenAccessor frame = (AbstractContainerScreenAccessor) beacon;
			int left = frame.mlinnotas$leftPos();
			int top = frame.mlinnotas$topPos();
			List<ExtraButton> buttons = new ArrayList<>();
			BeaconTiers.Extra[] extras = BeaconTiers.Extra.values();
			int width = extras.length * 22 + (extras.length - 1) * 2;
			for (int i = 0; i < extras.length; i++) {
				ExtraButton button = new ExtraButton(left + 167 - width / 2 + i * 24, top + 72, beacon, extras[i]);
				button.update();
				buttons.add(button);
				Screens.getWidgets(screen).add(button);
			}
			ScreenEvents.afterTick(screen).register(s -> buttons.forEach(ExtraButton::update));
			int centerX = left + frame.mlinnotas$imageWidth() / 2;
			int bottom = top + frame.mlinnotas$imageHeight();
			// o texto vai depois da tela (por cima do fundo); a dica antes, para entrar na fila de dicas deste quadro
			ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
					tierTooltip(graphics, Minecraft.getInstance().font, beacon.getMenu(), centerX, infoY(top, bottom, s.height), mouseX, mouseY));
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
					drawTierInfo(graphics, Minecraft.getInstance().font, beacon.getMenu(), centerX, infoY(top, bottom, s.height)));
		});
	}

	/** Metal da piramide sincronizado pelo servidor (null = sem piramide). */
	public static Tier tier(BeaconMenu menu) {
		return BeaconTiers.decode(((BeaconMenuTier) menu).mlinnotas$tierCode());
	}

	public static Component metalName(Tier tier) {
		return Component.translatable("gui.mlinnotas.beacon.metal." + tier.id());
	}

	/** Texto com o metal e o alcance (e quais primarios chegam ao III). */
	public static List<Component> tierInfo(BeaconMenu menu) {
		List<Component> lines = new ArrayList<>();
		Tier tier = tier(menu);
		int levels = menu.getLevels();
		if (tier == null || levels <= 0) {
			lines.add(Component.translatable("gui.mlinnotas.beacon.no_pyramid"));
			return lines;
		}
		lines.add(Component.translatable("gui.mlinnotas.beacon.tier", metalName(tier), BeaconTiers.range(tier, levels)));
		MutableComponent boosted = null;
		for (BeaconTiers.Primary primary : BeaconTiers.Primary.values()) {
			if (BeaconTiers.levelThree(primary, tier)) {
				Component name = Component.translatable(effectOf(primary).value().getDescriptionId());
				boosted = boosted == null ? Component.empty().append(name) : boosted.append(", ").append(name);
			}
		}
		if (boosted != null) {
			lines.add(Component.translatable("gui.mlinnotas.beacon.level_three", boosted));
		}
		return lines;
	}

	private static Holder<MobEffect> effectOf(BeaconTiers.Primary primary) {
		return switch (primary) {
			case SPEED -> MobEffects.SPEED;
			case HASTE -> MobEffects.HASTE;
			case RESISTANCE -> MobEffects.RESISTANCE;
			case JUMP_BOOST -> MobEffects.JUMP_BOOST;
			case STRENGTH -> MobEffects.STRENGTH;
			case REGENERATION -> MobEffects.REGENERATION;
		};
	}

	/**
	 * Linha com o metal e o alcance acima da janela (ou abaixo, se nao couber); a lista de poderes nivel III aparece
	 * ao passar o mouse sobre ela.
	 */
	/** Altura da linha do metal: acima da janela, ou abaixo se nao couber em cima. */
	public static int infoY(int top, int bottom, int screenHeight) {
		return top >= 13 ? top - 11 : (bottom + 11 <= screenHeight ? bottom + 2 : top + 1);
	}

	private static Component infoLine(List<Component> lines) {
		return lines.size() > 1 ? lines.get(0).copy().append(" [III]") : lines.get(0);
	}

	private static void drawTierInfo(GuiGraphicsExtractor graphics, Font font, BeaconMenu menu, int centerX, int y) {
		List<Component> lines = tierInfo(menu);
		graphics.centeredText(font, infoLine(lines), centerX, y, lines.size() > 1 ? 0xFFFFD86E : 0xFFFFFFFF);
	}

	private static void tierTooltip(GuiGraphicsExtractor graphics, Font font, BeaconMenu menu, int centerX, int y, int mouseX, int mouseY) {
		List<Component> lines = tierInfo(menu);
		int width = font.width(infoLine(lines));
		if (lines.size() > 1 && mouseX >= centerX - width / 2 && mouseX <= centerX + width / 2 && mouseY >= y - 1 && mouseY <= y + 9) {
			graphics.setComponentTooltipForNextFrame(font, lines.subList(1, lines.size()), mouseX, mouseY);
		}
	}

	/** Botao de poder extra no estilo dos botoes vanilla do sinalizador. */
	public static final class ExtraButton extends AbstractButton {
		private final BeaconScreen screen;
		private final BeaconTiers.Extra extra;
		private final Holder<MobEffect> effect;

		ExtraButton(int x, int y, BeaconScreen screen, BeaconTiers.Extra extra) {
			super(x, y, 22, 22, Component.translatable(ModEffects.forExtra(extra).value().getDescriptionId()));
			this.screen = screen;
			this.extra = extra;
			this.effect = ModEffects.forExtra(extra);
		}

		private BeaconScreenAccessor accessor() {
			return (BeaconScreenAccessor) screen;
		}

		public BeaconTiers.Extra extra() {
			return extra;
		}

		public boolean selected() {
			return effect.equals(accessor().mlinnotas$secondary());
		}

		void update() {
			BeaconMenu menu = screen.getMenu();
			Tier tier = tier(menu);
			this.active = BeaconTiers.unlocks(extra, tier, menu.getLevels());
			MutableComponent tooltip = Component.translatable(effect.value().getDescriptionId()).append("\n")
					.append(Component.translatable("gui.mlinnotas.beacon." + (extra == BeaconTiers.Extra.FLIGHT ? "flight" : "prospecting")));
			if (!this.active) {
				tooltip.append("\n").append(Component.translatable("gui.mlinnotas.beacon.requires", metalName(extra.requires())));
			}
			this.setTooltip(Tooltip.create(tooltip));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (this.active && !selected()) {
				accessor().mlinnotas$setSecondary(effect);
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			Identifier sprite;
			if (!this.active) {
				sprite = BUTTON_DISABLED;
			} else if (selected()) {
				sprite = BUTTON_SELECTED;
			} else if (this.isHoveredOrFocused()) {
				sprite = BUTTON_HIGHLIGHTED;
			} else {
				sprite = BUTTON;
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.width, this.height);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect), this.getX() + 2, this.getY() + 2, 18, 18);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
