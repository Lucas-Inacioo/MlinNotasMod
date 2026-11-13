package com.mlin.notas.compass;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Bussola de Biomas / de Estruturas: botao direito abre a lista (biomas da dimensao ou estruturas que geram nela);
 * agachado + botao direito procura de novo o mesmo alvo a partir de onde o jogador esta. A busca roda no servidor
 * aos poucos ({@link CompassServer}) e a agulha aponta para o resultado.
 */
public class ExplorerCompassItem extends Item {
	private final CompassKind kind;

	public ExplorerCompassItem(Item.Properties properties, CompassKind kind) {
		super(properties);
		this.kind = kind;
	}

	public CompassKind kind() {
		return kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack stack = player.getItemInHand(hand);
			CompassTarget target = stack.get(Compasses.TARGET);
			if (player.isShiftKeyDown() && target != null) {
				CompassServer.startSearch(serverPlayer, stack, kind, target.id());
			} else {
				CompassServer.open(serverPlayer, kind, stack);
			}
		}
		return InteractionResult.SUCCESS;
	}

	public static Component targetName(CompassKind kind, String id) {
		return Component.translatableWithFallback(CompassCatalog.nameKey(kind, id), CompassCatalog.prettyName(id));
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		CompassTarget target = stack.get(Compasses.TARGET);
		if (target == null) {
			tooltip.accept(Component.translatable("item.mlinnotas." + kind.id() + "_compass.tooltip").withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.accept(Component.translatable("compass.mlinnotas.target", targetName(kind, target.id()).copy().withStyle(ChatFormatting.WHITE))
				.withStyle(ChatFormatting.GRAY));
		if (target.status() == CompassTarget.SEARCHING) {
			tooltip.accept(Component.translatable("compass.mlinnotas.status.searching").withStyle(ChatFormatting.YELLOW));
		} else if (target.status() == CompassTarget.NOT_FOUND) {
			tooltip.accept(Component.translatable("compass.mlinnotas.status.not_found").withStyle(ChatFormatting.RED));
		}
		// a distancia e a direcao entram no cliente (CompassClient), que sabe onde o jogador esta
		tooltip.accept(Component.translatable("compass.mlinnotas.tooltip.again").withStyle(ChatFormatting.DARK_GRAY));
	}
}
