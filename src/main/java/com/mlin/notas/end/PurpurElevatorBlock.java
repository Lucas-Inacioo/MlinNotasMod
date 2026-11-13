package com.mlin.notas.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Elevador de Purpur: em cima dele, pular leva ao proximo elevador acima na mesma coluna e agachar ao proximo
 * abaixo ({@link Elevators}, regra em {@link ElevatorLogic}). Solta particulas de portal para ser facil de achar.
 */
public class PurpurElevatorBlock extends Block {
	public PurpurElevatorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
					0.0, 0.3 + random.nextDouble() * 0.3, 0.0);
		}
	}
}
