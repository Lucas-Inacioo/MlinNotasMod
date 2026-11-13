package com.mlin.notas.client.quests;

import com.mlin.notas.quests.Quest;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Aviso "Quest concluida" no canto da tela (fundo de conquista, icone da quest e titulo). */
public class QuestToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
	private static final long DISPLAY_MS = 5000L;

	private final Quest quest;
	private Visibility visibility = Visibility.SHOW;

	public QuestToast(Quest quest) {
		this.quest = quest;
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		visibility = fullyVisibleForMs >= DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public SoundEvent getSoundEvent() {
		return SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		graphics.text(font, Component.translatable("gui.mlinnotas.quests.toast"), 30, 7, 0xFFFFFF00, false);
		List<FormattedCharSequence> lines = font.split(Component.translatable(quest.titleKey()), width() - 34);
		if (!lines.isEmpty()) {
			graphics.text(font, lines.getFirst(), 30, 18, 0xFFFFFFFF, false);
		}
		graphics.fakeItem(QuestTexts.stack(quest.icon()), 8, 8);
	}
}
