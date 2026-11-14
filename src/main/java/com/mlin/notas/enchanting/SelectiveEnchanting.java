package com.mlin.notas.enchanting;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

/**
 * Encantamento escolhido: a mesa de encantamento vanilla abre {@link SelectiveEnchantMenu} em vez do menu aleatorio.
 * Agachar com um item na mao continua colocando blocos encostados na mesa, como em qualquer bloco.
 */
public final class SelectiveEnchanting {
	public static final Component TITLE = Component.translatable("container.mlinnotas.selective_enchant");

	private SelectiveEnchanting() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(EnchantRequestPayload.TYPE, EnchantRequestPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EnchantRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.containerMenu instanceof SelectiveEnchantMenu menu) {
				EnchantRules.Check check = menu.tryEnchant(player, payload.enchantment(), payload.level());
				if (check != EnchantRules.Check.OK) {
					player.sendOverlayMessage(message(check));
				}
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.getBlockState(hit.getBlockPos()).is(Blocks.ENCHANTING_TABLE) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				// so se encanta no Encantador da vila: mesa em outro lugar (inclusive as ja colocadas no mundo) nao abre nada
				if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
						&& com.mlin.notas.village.CraftsServer.levelAt(serverLevel, hit.getBlockPos(), "enchanter") < 1) {
					if (serverPlayer.connection != null) serverPlayer.sendOverlayMessage(Component.translatable("message.mlinnotas.enchanter.only").withStyle(net.minecraft.ChatFormatting.RED));
					return InteractionResult.SUCCESS;
				}
				open(serverPlayer, ContainerLevelAccess.create(level, hit.getBlockPos()));
			}
			return InteractionResult.SUCCESS;
		});
	}

	public static void open(ServerPlayer player, ContainerLevelAccess access) {
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SelectiveEnchantMenu(id, inventory, access), TITLE));
	}

	public static Component message(EnchantRules.Check check) {
		return Component.translatable("message.mlinnotas.enchant." + check.name().toLowerCase(Locale.ROOT));
	}
}
