package com.mlin.notas.quests;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Livro de Quests: botao direito abre a tela (so no cliente; o entrypoint de cliente preenche {@link #opener}, entao
 * nada de cliente e referenciado daqui).
 */
public class QuestBookItem extends Item {
	/** Abre a tela do livro; trocado pelo cliente. No servidor dedicado nao faz nada. */
	public static Runnable opener = () -> {
	};

	public QuestBookItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			opener.run();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mlinnotas.quest_book.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
