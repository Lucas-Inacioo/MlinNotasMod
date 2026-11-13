package com.mlin.notas.end;

import com.mlin.notas.registry.ModBlocks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.OptionalInt;

/**
 * Lado servidor do Elevador de Purpur: o cliente avisa que o jogador pulou/agachou em cima de um elevador
 * ({@link ElevatorPayload}); o servidor confere e teleporta para o proximo elevador da coluna ({@link ElevatorLogic}).
 */
public final class Elevators {
	public static final int RANGE = CitadelPlanner.ELEVATOR_RANGE;

	private Elevators() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ElevatorPayload.TYPE, ElevatorPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ElevatorPayload.TYPE, (payload, context) -> use(context.player(), payload.up()));
	}

	/** Bloco sob os pes do jogador, se for um elevador. */
	public static BlockPos elevatorBelow(Level level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y - 0.2, z);
		return level.getBlockState(pos).is(ModBlocks.PURPUR_ELEVATOR) ? pos : null;
	}

	/** Teleporta o jogador para o proximo elevador acima/abaixo. Devolve true se teleportou. */
	public static boolean use(ServerPlayer player, boolean up) {
		Level level = player.level();
		BlockPos from = elevatorBelow(level, player.getX(), player.getY(), player.getZ());
		if (from == null) {
			return false;
		}
		OptionalInt target = ElevatorLogic.target(from.getY(), up,
				y -> level.getBlockState(new BlockPos(from.getX(), y, from.getZ())).is(ModBlocks.PURPUR_ELEVATOR),
				y -> {
					BlockPos pos = new BlockPos(from.getX(), y, from.getZ());
					return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
				},
				RANGE);
		if (target.isEmpty()) {
			return false;
		}
		player.teleportTo(from.getX() + 0.5, target.getAsInt() + 1.0, from.getZ() + 0.5);
		player.resetFallDistance();
		level.playSound(null, from.getX() + 0.5, target.getAsInt() + 1.0, from.getZ() + 0.5, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.5F, 1.6F);
		return true;
	}
}
