package com.mlin.notas.backpack;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: tecla da mochila pressionada; abre a primeira mochila do inventario. */
public record OpenBackpackPayload() implements CustomPacketPayload {
	public static final OpenBackpackPayload INSTANCE = new OpenBackpackPayload();
	public static final Type<OpenBackpackPayload> TYPE = new Type<>(MlinNotas.id("open_backpack"));
	public static final StreamCodec<ByteBuf, OpenBackpackPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
