package com.mlin.notas.waila;

import com.mlin.notas.MlinNotas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: "o que tem neste container que estou mirando?". */
public record ContainerPeekRequestPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<ContainerPeekRequestPayload> TYPE = new Type<>(MlinNotas.id("container_peek_request"));
	public static final StreamCodec<FriendlyByteBuf, ContainerPeekRequestPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ContainerPeekRequestPayload::pos, ContainerPeekRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
