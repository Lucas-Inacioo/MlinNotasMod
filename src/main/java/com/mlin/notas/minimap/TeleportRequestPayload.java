package com.mlin.notas.minimap;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: pedido de teleporte a partir do mapa (so e atendido com permissao de operador). */
public record TeleportRequestPayload(double x, double y, double z) implements CustomPacketPayload {
	public static final Type<TeleportRequestPayload> TYPE = new Type<>(MlinNotas.id("teleport_request"));
	public static final StreamCodec<ByteBuf, TeleportRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, TeleportRequestPayload::x,
			ByteBufCodecs.DOUBLE, TeleportRequestPayload::y,
			ByteBufCodecs.DOUBLE, TeleportRequestPayload::z,
			TeleportRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
