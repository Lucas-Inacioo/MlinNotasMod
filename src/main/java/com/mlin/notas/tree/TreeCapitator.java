package com.mlin.notas.tree;

import com.mlin.notas.util.Pos;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Quebrar arvore inteira pelo primeiro bloco: ao quebrar um tronco com machado (sem agachar),
 * todos os troncos conectados caem e o machado perde durabilidade por tronco.
 */
public final class TreeCapitator {
	private TreeCapitator() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register(TreeCapitator::afterBreak);
	}

	static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (player.isShiftKeyDown() || !state.is(BlockTags.LOGS)) {
			return;
		}
		ItemStack axe = player.getMainHandItem();
		if (!axe.is(ItemTags.AXES)) {
			return;
		}
		fellTree(serverLevel, serverPlayer, pos, state, axe);
	}

	/** Derruba a arvore cujo tronco em {@code origin} (ja removido) tinha o estado {@code brokenState}. Devolve quantos troncos caíram. */
	public static int fellTree(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState brokenState, ItemStack axe) {
		TreeFeller.Result result = TreeFeller.fell(
				new Pos(origin.getX(), origin.getY(), origin.getZ()),
				p -> level.getBlockState(new BlockPos(p.x(), p.y(), p.z())).is(brokenState.getBlock()),
				p -> level.getBlockState(new BlockPos(p.x(), p.y(), p.z())).is(BlockTags.LEAVES),
				TreeFeller.DEFAULT_MAX_LOGS);
		if (!result.isTree()) {
			return 0;
		}
		int broken = 0;
		for (Pos p : result.logs()) {
			if (axe.isEmpty()) {
				break;
			}
			BlockPos target = new BlockPos(p.x(), p.y(), p.z());
			if (level.destroyBlock(target, !player.hasInfiniteMaterials(), player)) {
				broken++;
				if (!player.hasInfiniteMaterials()) {
					axe.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
				}
			}
		}
		return broken;
	}
}
