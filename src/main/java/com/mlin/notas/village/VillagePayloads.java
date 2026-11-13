package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacotes do quadro de missoes. */
public final class VillagePayloads {
	private VillagePayloads() {
	}

	/**
	 * Servidor -> cliente: estado da vila do quadro {@code board}, obra em andamento ({@code building} vazio = nenhuma,
	 * {@code done}/{@code total} blocos), se a fila esta parada por falta de espaco e o dia de jogo. {@code open} abre
	 * a tela; senao so atualiza se ela estiver aberta.
	 */
	public record View(VillageState state, BlockPos board, String building, int done, int total, boolean waiting, long day, boolean open)
			implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(MlinNotas.id("village_view"));
		private static final StreamCodec<ByteBuf, VillageState> STATE = ByteBufCodecs.fromCodec(VillageData.VILLAGE_CODEC);
		public static final StreamCodec<ByteBuf, View> CODEC = StreamCodec.composite(
				STATE, View::state,
				BlockPos.STREAM_CODEC, View::board,
				ByteBufCodecs.STRING_UTF8, View::building,
				ByteBufCodecs.VAR_INT, View::done,
				ByteBufCodecs.VAR_INT, View::total,
				ByteBufCodecs.BOOL, View::waiting,
				ByteBufCodecs.VAR_LONG, View::day,
				ByteBufCodecs.BOOL, View::open,
				View::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Cliente -> servidor: acao no quadro {@code board}. {@code action}: {@code deposit} (arg = id do projeto),
	 * {@code bounty} (arg = indice da missao) ou {@code buy} (arg = id da oferta do mercado).
	 */
	public record Action(BlockPos board, String action, String arg) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(MlinNotas.id("village_action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Action::board,
				ByteBufCodecs.STRING_UTF8, Action::action,
				ByteBufCodecs.STRING_UTF8, Action::arg,
				Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
