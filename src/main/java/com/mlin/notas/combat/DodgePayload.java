package com.mlin.notas.combat;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: o jogador esquivou na direcao (x, z). O impulso e do cliente; o servidor da a invulnerabilidade. */
public record DodgePayload(float dx, float dz) implements CustomPacketPayload {
	public static final Type<DodgePayload> TYPE = new Type<>(MlinNotas.id("dodge"));
	public static final StreamCodec<ByteBuf, DodgePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, DodgePayload::dx,
			ByteBufCodecs.FLOAT, DodgePayload::dz,
			DodgePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
