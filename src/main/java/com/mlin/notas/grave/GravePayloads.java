package com.mlin.notas.grave;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Pacotes das lapides. */
public final class GravePayloads {
	private GravePayloads() {
	}

	public static final StreamCodec<ByteBuf, GraveInfo> INFO = new StreamCodec<>() {
		@Override
		public GraveInfo decode(ByteBuf buf) {
			return new GraveInfo(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, GraveInfo info) {
			ByteBufCodecs.STRING_UTF8.encode(buf, info.owner());
			ByteBufCodecs.STRING_UTF8.encode(buf, info.ownerName());
			ByteBufCodecs.STRING_UTF8.encode(buf, info.dimension());
			ByteBufCodecs.VAR_INT.encode(buf, info.x());
			ByteBufCodecs.VAR_INT.encode(buf, info.y());
			ByteBufCodecs.VAR_INT.encode(buf, info.z());
			ByteBufCodecs.VAR_INT.encode(buf, info.items());
			ByteBufCodecs.VAR_INT.encode(buf, info.xp());
			ByteBufCodecs.VAR_LONG.encode(buf, info.day());
		}
	};

	/** Servidor -> cliente: todas as lapides do mundo (minimapa e mapa grande). Enviado ao entrar e a cada mudanca. */
	public record Sync(List<GraveInfo> graves) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(MlinNotas.id("grave_sync"));
		public static final StreamCodec<ByteBuf, Sync> CODEC = INFO.apply(ByteBufCodecs.list()).map(Sync::new, Sync::graves);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
