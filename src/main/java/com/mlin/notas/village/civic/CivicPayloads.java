package com.mlin.notas.village.civic;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Pacotes das construcoes civicas (servidor -> cliente). */
public final class CivicPayloads {
	private CivicPayloads() {
	}

	/** Chaves das pedras de teleporte que sao de vilas (viagem entre elas custa metade). */
	public record VillageWaystones(List<String> keys) implements CustomPacketPayload {
		public static final Type<VillageWaystones> TYPE = new Type<>(MlinNotas.id("village_waystones"));
		public static final StreamCodec<ByteBuf, VillageWaystones> CODEC = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
				.map(VillageWaystones::new, VillageWaystones::keys);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Monstros a ate o raio da vila que a Torre de vigia enxerga a noite (pares x, z) para o minimapa. */
	public record Hostiles(List<Integer> coords) implements CustomPacketPayload {
		public static final Type<Hostiles> TYPE = new Type<>(MlinNotas.id("tower_hostiles"));
		public static final StreamCodec<ByteBuf, Hostiles> CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())
				.map(Hostiles::new, Hostiles::coords);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
