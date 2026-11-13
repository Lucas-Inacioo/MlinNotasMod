package com.mlin.notas.tablet;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: tecla do tablet pressionada; abre a mesa portatil se o jogador tiver um tablet. */
public record OpenTabletPayload() implements CustomPacketPayload {
	public static final OpenTabletPayload INSTANCE = new OpenTabletPayload();
	public static final Type<OpenTabletPayload> TYPE = new Type<>(MlinNotas.id("open_tablet"));
	public static final StreamCodec<ByteBuf, OpenTabletPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
