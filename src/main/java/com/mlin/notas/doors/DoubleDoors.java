package com.mlin.notas.doors;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Portas duplas: botao direito numa porta de abrir com a mao (madeira, cobre; ferro nao) tambem aciona a parceira
 * ({@link DoubleDoorRules}). Sem mixin: o {@link UseBlockCallback} roda antes do uso vanilla, aciona a parceira e
 * devolve PASS para a porta clicada abrir/fechar pelo caminho normal.
 */
public final class DoubleDoors {
	private DoubleDoors() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (DoubleDoorRules.triggers(true, player.isShiftKeyDown(), player.isSpectator()) && DoorBlock.isWoodenDoor(state)) {
				togglePartner(player, level, pos, state);
			}
			return InteractionResult.PASS;
		});
	}

	/** Parceira da porta em {@code pos} (metade de baixo), ou null. */
	public static BlockPos partner(Level level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof DoorBlock)) {
			return null;
		}
		BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
		BlockState door = level.getBlockState(lower);
		if (!(door.getBlock() instanceof DoorBlock)) {
			return null;
		}
		Direction facing = door.getValue(DoorBlock.FACING);
		boolean hingeLeft = door.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
		BlockPos other = lower.relative(DoubleDoorRules.partnerClockwise(hingeLeft) ? facing.getClockWise() : facing.getCounterClockWise());
		BlockState partner = level.getBlockState(other);
		if (!DoorBlock.isWoodenDoor(partner) || partner.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
			return null;
		}
		boolean paired = DoubleDoorRules.pairs(facing.getSerializedName(), hingeLeft, door.getValue(DoorBlock.OPEN),
				partner.getValue(DoorBlock.FACING).getSerializedName(), partner.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT,
				partner.getValue(DoorBlock.OPEN));
		return paired ? other : null;
	}

	private static void togglePartner(Player player, Level level, BlockPos pos, BlockState state) {
		BlockPos other = partner(level, pos, state);
		if (other == null) {
			return;
		}
		BlockState partner = level.getBlockState(other);
		((DoorBlock) partner.getBlock()).setOpen(player, level, partner, other, !partner.getValue(DoorBlock.OPEN));
	}
}
