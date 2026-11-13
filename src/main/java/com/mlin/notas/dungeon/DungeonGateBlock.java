package com.mlin.notas.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Portao Selado da camara final de uma masmorra. Inquebravel; botao direito com a Chave da Masmorra na mao abre o
 * portao inteiro (todos os blocos ligados, ate {@link #MAX_BLOCKS}). A chave nao e gasta: ela ainda abre o cofre.
 */
public class DungeonGateBlock extends Block {
	public static final int MAX_BLOCKS = 64;

	public DungeonGateBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!stack.is(DungeonRegistry.KEY)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel server) {
			int opened = open(server, pos);
			if (player instanceof ServerPlayer sp && sp.connection != null) {
				sp.sendOverlayMessage(Component.translatable("message.mlinnotas.dungeon.gate_opened"));
			}
			if (opened == 0) {
				return InteractionResult.FAIL;
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player instanceof ServerPlayer sp && sp.connection != null) {
			sp.sendOverlayMessage(Component.translatable("message.mlinnotas.dungeon.gate_locked"));
		}
		return InteractionResult.SUCCESS;
	}

	/** Remove o portao inteiro (blocos ligados) com som e particulas. Devolve quantos blocos abriu. */
	public static int open(ServerLevel level, BlockPos start) {
		Set<BlockPos> gate = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		if (!level.getBlockState(start).is(DungeonRegistry.GATE)) {
			return 0;
		}
		gate.add(start);
		queue.add(start);
		while (!queue.isEmpty() && gate.size() < MAX_BLOCKS) {
			BlockPos p = queue.poll();
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (!gate.contains(n) && level.getBlockState(n).is(DungeonRegistry.GATE)) {
					gate.add(n);
					queue.add(n);
				}
			}
		}
		for (BlockPos p : gate) {
			level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
		}
		level.playSound(null, start, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.playSound(null, start, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.5F);
		return gate.size();
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SOUL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
					0, 0.01, 0);
		}
	}
}
