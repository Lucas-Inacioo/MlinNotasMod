package com.mlin.notas.village.works;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Pacote das obras publicas: a fila de uma vila para a tela do quadro (as acoes vao pelo pacote de acao do quadro). */
public final class WorksPayloads {
	private WorksPayloads() {
	}

	/** Uma obra da fila: id, estado (0 fila, 1 andamento, 2 pronta, 3 substituida), blocos feitos e total. */
	public record Row(String id, int state, int done, int total) {
		public static final StreamCodec<ByteBuf, Row> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Row::id,
				ByteBufCodecs.VAR_INT, Row::state,
				ByteBufCodecs.VAR_INT, Row::done,
				ByteBufCodecs.VAR_INT, Row::total,
				Row::new);
	}

	/**
	 * Servidor -> cliente: obras publicas da vila do quadro {@code board} (chave da vila), categorias desligadas (bits de
	 * {@link WorkCategory#bit()}) e se uma obra paga esta segurando a fila.
	 */
	public record Sync(String village, List<Row> rows, int disabled, boolean paidJobRunning) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(MlinNotas.id("village_works"));
		public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Sync::village,
				Row.CODEC.apply(ByteBufCodecs.list()), Sync::rows,
				ByteBufCodecs.VAR_INT, Sync::disabled,
				ByteBufCodecs.BOOL, Sync::paidJobRunning,
				Sync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
