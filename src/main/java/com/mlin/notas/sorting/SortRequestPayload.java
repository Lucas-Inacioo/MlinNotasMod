package com.mlin.notas.sorting;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente -> servidor: pedido para ordenar o inventario do jogador ({@code container == false})
 * ou o container aberto ({@code container == true}).
 */
public record SortRequestPayload(boolean container) implements CustomPacketPayload {
	public static final Type<SortRequestPayload> TYPE = new Type<>(MlinNotas.id("sort_request"));
	public static final StreamCodec<ByteBuf, SortRequestPayload> CODEC =
			ByteBufCodecs.BOOL.map(SortRequestPayload::new, SortRequestPayload::container);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
