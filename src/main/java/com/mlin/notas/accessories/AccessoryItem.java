package com.mlin.notas.accessories;

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

import java.util.Map;
import java.util.function.Consumer;

/**
 * Item de acessorio (anel, colar, cinto, luvas, amuleto). Botao direito equipa no primeiro encaixe livre; o tooltip
 * mostra encaixe, numeros e efeitos. O aviso de teto do estagio e somado no cliente (ver AccessoryClient).
 */
public class AccessoryItem extends Item {
	private final AccessoryDef def;

	public AccessoryItem(Properties properties, AccessoryDef def) {
		super(properties);
		this.def = def;
	}

	public AccessoryDef def() {
		return def;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			return AccessoryServer.equipFromHand(serverPlayer, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		appendLines(def, tooltip);
	}

	/** Linhas do tooltip de uma definicao (tambem usadas pela tela de acessorios). */
	public static void appendLines(AccessoryDef def, Consumer<Component> tooltip) {
		Component slot = Component.translatable("accessory.mlinnotas.slot." + def.slot().id());
		Component kind = switch (def.kind()) {
			case BASIC -> Component.translatable("accessory.mlinnotas.kind.basic", slot);
			case ARTIFACT -> Component.translatable("accessory.mlinnotas.kind.artifact", slot);
			case RELIC -> Component.translatable("accessory.mlinnotas.kind.relic", slot);
		};
		tooltip.accept(kind.copy().withStyle(def.kind() == AccessoryKind.RELIC ? ChatFormatting.LIGHT_PURPLE
				: def.kind() == AccessoryKind.ARTIFACT ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		for (Map.Entry<AccessoryStat, Double> entry : def.stats().entrySet()) {
			AccessoryStat stat = entry.getKey();
			double value = entry.getValue();
			tooltip.accept(Component.translatable("accessory.mlinnotas.stat." + stat.id(), stat.format(value))
					.withStyle(value >= 0 ? ChatFormatting.BLUE : ChatFormatting.RED));
		}
		for (AccessoryAbility ability : def.abilities()) {
			tooltip.accept(Component.translatable("accessory.mlinnotas.ability." + ability.id()).withStyle(ChatFormatting.DARK_AQUA));
		}
		if (def.kind() == AccessoryKind.RELIC) {
			tooltip.accept(Component.translatable("accessory.mlinnotas.relic_of." + def.bossFor()).withStyle(ChatFormatting.DARK_PURPLE,
					ChatFormatting.ITALIC));
		}
		if (def.unique()) {
			tooltip.accept(Component.translatable("accessory.mlinnotas.unique").withStyle(ChatFormatting.DARK_GRAY));
		}
		tooltip.accept(Component.translatable("accessory.mlinnotas.equip_hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
