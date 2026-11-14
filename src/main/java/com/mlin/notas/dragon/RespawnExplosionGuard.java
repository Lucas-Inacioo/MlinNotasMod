package com.mlin.notas.dragon;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sem explosao de cama ou ancora de renascimento na luta do dragao: no End, a ate
 * {@link DragonRules#EXPLOSION_GUARD_RADIUS} blocos do portal, usar uma cama (ou uma ancora carregada sem estar
 * carregando mais) e recusado com uma mensagem, em vez de explodir. A explosao so acontece no uso do bloco, entao o
 * {@link UseBlockCallback} cobre tudo sem mixin.
 */
public final class RespawnExplosionGuard {
	private RespawnExplosionGuard() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			if (level.dimension() != Level.END || !DragonRules.guardsExplosionsAt(pos.getX(), pos.getZ())) {
				return InteractionResult.PASS;
			}
			if (player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty()) {
				return InteractionResult.PASS; // agachado com item: coloca bloco, nao usa a cama
			}
			if (!wouldExplode(level.getBlockState(pos), player.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && sp.connection != null) {
				sp.sendOverlayMessage(Component.translatable("message.mlinnotas.dragon.no_explosions").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			return InteractionResult.FAIL;
		});
	}

	/** Usar este bloco com {@code inHand} explodiria fora do Overworld/Nether (cama, ou ancora carregada sem estar recarregando). */
	public static boolean wouldExplode(BlockState state, ItemStack inHand) {
		if (state.getBlock() instanceof BedBlock) {
			return true;
		}
		if (state.is(Blocks.RESPAWN_ANCHOR)) {
			int charge = state.getValue(RespawnAnchorBlock.CHARGE);
			boolean charging = inHand.is(Items.GLOWSTONE) && charge < RespawnAnchorBlock.MAX_CHARGES;
			return charge > 0 && !charging;
		}
		return false;
	}
}
