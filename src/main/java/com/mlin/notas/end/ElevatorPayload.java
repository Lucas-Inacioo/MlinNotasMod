package com.mlin.notas.end;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: o jogador pulou (up = true) ou agachou (up = false) em cima de um Elevador de Purpur. */
public record ElevatorPayload(boolean up) implements CustomPacketPayload {
	public static final Type<ElevatorPayload> TYPE = new Type<>(MlinNotas.id("elevator"));
	public static final StreamCodec<ByteBuf, ElevatorPayload> CODEC = ByteBufCodecs.BOOL.map(ElevatorPayload::new, ElevatorPayload::up);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
