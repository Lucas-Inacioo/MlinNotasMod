package com.mlin.notas.season;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: estacoes ligadas, duracao e o dia absoluto atual. Enviado ao entrar e quando o dia, a duracao
 * ou o deslocamento mudam (no maximo uma vez por dia de jogo em uso normal).
 */
public record SeasonSyncPayload(boolean enabled, int lengthDays, long absoluteDay) implements CustomPacketPayload {
	public static final Type<SeasonSyncPayload> TYPE = new Type<>(MlinNotas.id("season_sync"));
	public static final StreamCodec<ByteBuf, SeasonSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, SeasonSyncPayload::enabled,
			ByteBufCodecs.VAR_INT, SeasonSyncPayload::lengthDays,
			ByteBufCodecs.VAR_LONG, SeasonSyncPayload::absoluteDay,
			SeasonSyncPayload::new);

	public SeasonDate date() {
		return enabled ? SeasonCalendar.date(absoluteDay, lengthDays) : null;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
