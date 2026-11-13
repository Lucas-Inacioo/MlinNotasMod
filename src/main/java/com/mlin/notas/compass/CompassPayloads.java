package com.mlin.notas.compass;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Pacotes das bussolas de bioma e de estrutura. */
public final class CompassPayloads {
	private CompassPayloads() {
	}

	/** Servidor -> cliente: abre a lista da bussola com as entradas possiveis na dimensao atual. */
	public record Open(String kind, List<String> entries, String current) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(MlinNotas.id("compass_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Open::kind,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Open::entries,
				ByteBufCodecs.STRING_UTF8, Open::current,
				Open::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Cliente -> servidor: procurar {@code id} com a bussola {@code kind} que esta na mao. */
	public record Select(String kind, String id) implements CustomPacketPayload {
		public static final Type<Select> TYPE = new Type<>(MlinNotas.id("compass_select"));
		public static final StreamCodec<ByteBuf, Select> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Select::kind,
				ByteBufCodecs.STRING_UTF8, Select::id,
				Select::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
