package com.mlin.notas.waystone;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Pacotes das pedras de teleporte. */
public final class WaystonePayloads {
	private WaystonePayloads() {
	}

	public static final StreamCodec<ByteBuf, WaystoneInfo> INFO = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, WaystoneInfo::dimension,
			ByteBufCodecs.VAR_INT, WaystoneInfo::x,
			ByteBufCodecs.VAR_INT, WaystoneInfo::y,
			ByteBufCodecs.VAR_INT, WaystoneInfo::z,
			ByteBufCodecs.STRING_UTF8, WaystoneInfo::name,
			WaystoneInfo::new);

	/** Servidor -> cliente: todas as pedras ativadas (minimapa e tela). Enviado ao entrar e a cada mudanca. */
	public record Sync(List<WaystoneInfo> waystones) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(MlinNotas.id("waystone_sync"));
		public static final StreamCodec<ByteBuf, Sync> CODEC = INFO.apply(ByteBufCodecs.list()).map(Sync::new, Sync::waystones);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Servidor -> cliente: abre a tela da pedra em {@code source} (a lista vem do {@link Sync}). */
	public record Open(WaystoneInfo source, boolean justActivated) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(MlinNotas.id("waystone_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
				INFO, Open::source,
				ByteBufCodecs.BOOL, Open::justActivated,
				Open::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Cliente -> servidor: viajar da pedra {@code source} (a que o jogador usou) para {@code target} (chave). */
	public record Travel(BlockPos source, String target) implements CustomPacketPayload {
		public static final Type<Travel> TYPE = new Type<>(MlinNotas.id("waystone_travel"));
		public static final StreamCodec<ByteBuf, Travel> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Travel::source,
				ByteBufCodecs.STRING_UTF8, Travel::target,
				Travel::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Cliente -> servidor: renomear a pedra em {@code pos}. */
	public record Rename(BlockPos pos, String name) implements CustomPacketPayload {
		public static final Type<Rename> TYPE = new Type<>(MlinNotas.id("waystone_rename"));
		public static final StreamCodec<ByteBuf, Rename> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Rename::pos,
				ByteBufCodecs.STRING_UTF8, Rename::name,
				Rename::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
