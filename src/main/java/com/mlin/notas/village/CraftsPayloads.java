package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacotes das construcoes de oficios (Vilas 2.0, fase 6). */
public final class CraftsPayloads {
	private CraftsPayloads() {
	}

	/** Servidor -> todos os clientes: a taverna contou um boato (vira waypoint compartilhado e linha no livro de quests). */
	public record Rumor(String structure, int x, int z, String dimension, String village) implements CustomPacketPayload {
		public static final Type<Rumor> TYPE = new Type<>(MlinNotas.id("tavern_rumor"));
		public static final StreamCodec<ByteBuf, Rumor> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Rumor::structure,
				ByteBufCodecs.VAR_INT, Rumor::x, ByteBufCodecs.VAR_INT, Rumor::z, ByteBufCodecs.STRING_UTF8, Rumor::dimension,
				ByteBufCodecs.STRING_UTF8, Rumor::village, Rumor::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
