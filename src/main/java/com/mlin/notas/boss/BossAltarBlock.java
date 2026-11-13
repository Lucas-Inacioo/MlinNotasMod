package com.mlin.notas.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Altar de invocacao de um chefe. Botao direito com a oferenda no inventario (4 blocos de ferro, 4 fragmentos de eco
 * ou 4 nucleos de sentinela) consome os itens e invoca o chefe em cima do altar. Inquebravel (fica na estrutura).
 */
public class BossAltarBlock extends Block {
	private final BossKind kind;

	public BossAltarBlock(BossKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public BossKind kind() {
		return kind;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BossSummoning.Result result = BossSummoning.trySummon(server, pos, player, kind);
		if (result != BossSummoning.Result.SUMMONED && player instanceof net.minecraft.server.level.ServerPlayer sp && sp.connection != null) {
			Component message = switch (result) {
				case MISSING_OFFERING -> Component.translatable("message.mlinnotas.altar.missing", kind.offeringCount(),
						new net.minecraft.world.item.ItemStack(BossRegistry.offeringItem(kind)).getHoverName());
				case BUSY -> Component.translatable("message.mlinnotas.altar.busy");
				default -> Component.translatable("message.mlinnotas.altar.no_room");
			};
			sp.sendOverlayMessage(message);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) {
			return;
		}
		ParticleOptions particle = switch (kind) {
			case ABYSSAL_GUARDIAN -> ParticleTypes.SCULK_SOUL;
			case ASTRAL_HERALD -> ParticleTypes.END_ROD;
			default -> ParticleTypes.ENCHANT;
		};
		level.addParticle(particle, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
	}
}
