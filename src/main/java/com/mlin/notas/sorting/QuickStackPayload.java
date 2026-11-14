package com.mlin.notas.sorting;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: guardar rapido no container aberto (botao ou tecla na tela). */
public record QuickStackPayload() implements CustomPacketPayload {
	public static final QuickStackPayload INSTANCE = new QuickStackPayload();
	public static final Type<QuickStackPayload> TYPE = new Type<>(MlinNotas.id("quick_stack"));
	public static final StreamCodec<ByteBuf, QuickStackPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
