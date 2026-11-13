package com.mlin.notas.dragon;

import com.mlin.notas.MlinNotas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * Tesouro do dragao: quando o dragao da luta morre, um bau com o loot {@code mlinnotas:chests/dragon_hoard} aparece no
 * estrado norte do santuario ({@link ArenaPlanner#HOARD_X}, {@link ArenaPlanner#HOARD_Z}), a cada morte.
 */
public final class DragonHoard {
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, MlinNotas.id("chests/dragon_hoard"));

	private DragonHoard() {
	}

	/** Poe o bau (no primeiro ar em cima do estrado; sobe se ja houver um bau ali). Devolve a posicao ou null. */
	public static @Nullable BlockPos place(ServerLevel level) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, ArenaPlanner.HOARD_X, ArenaPlanner.HOARD_Z);
		if (y <= level.getMinY() + 1) {
			return null;
		}
		BlockPos pos = new BlockPos(ArenaPlanner.HOARD_X, y, ArenaPlanner.HOARD_Z);
		for (int i = 0; i < 4 && !level.getBlockState(pos).isAir(); i++) {
			pos = pos.above();
		}
		if (!level.getBlockState(pos).isAir()) {
			return null;
		}
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, LOOT);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.5, 1.5, 0.5, 0.08);
		level.playSound(null, pos, SoundEvents.ENDER_CHEST_OPEN, SoundSource.BLOCKS, 2.0F, 0.6F);
		for (ServerPlayer p : level.players()) {
			if (p.connection != null) {
				p.sendSystemMessage(Component.translatable("message.mlinnotas.dragon.hoard"));
			}
		}
		return pos;
	}
}
