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
		private static final StreamCodec<ByteBuf, VillageState> STATE = ByteBufCodecs.fromCodecTrusted(VillageData.VILLAGE_CODEC);
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

	/** Situacao de um lote no contorno: {@code status} = ordinal de {@link LotStatus}, {@code ground} = altura alvo. */
	public record LotInfo(int id, int status, int ground) {
		public static final StreamCodec<ByteBuf, LotInfo> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, LotInfo::id,
				ByteBufCodecs.VAR_INT, LotInfo::status,
				ByteBufCodecs.VAR_INT, LotInfo::ground,
				LotInfo::new);
	}

	/** Situacao de cada lote vista pelo servidor. */
	public enum LotStatus {
		/** Pronto para construir. */
		FREE,
		/** Tem arvores (o construtor derruba sozinho). */
		CLEAR,
		/** Tem bloco de jogador: e preciso tirar. */
		BLOCKED,
		/** Agua no lote. */
		WATER,
		/** Desnivel maior do que o construtor aplaina. */
		STEEP,
		/** Ja tem a obra. */
		BUILT,
		/** Obra em andamento. */
		BUILDING,
		/** Obra parada: o plato do lote foi mexido. */
		PAUSED,
		/** Quarteirao ainda sem terraplenagem. */
		UNPREPARED,
		/** Lote de jogador (a vila nao mexe). */
		PLAYER;

		public static LotStatus byOrdinal(int i) {
			return values()[Math.max(0, Math.min(values().length - 1, i))];
		}
	}

	/**
	 * Servidor -> cliente: contorno da vila para desenhar no mundo por {@code seconds} segundos (0 = esconder): plano
	 * (lotes, ruas, postes), situacao de cada lote e blocos que atrapalham ({@code obstacles} = posicoes, {@code kinds}
	 * = ordinal de {@link WorldTerrain.Obstacle}). {@code y} de cada coluna vem do proprio mundo do cliente.
	 */
	public record Overlay(String village, BlockPos center, VillageLayout layout, java.util.List<LotInfo> lots, java.util.List<Long> obstacles,
			java.util.List<Integer> kinds, int seconds, int rings) implements CustomPacketPayload {
		/** Aneis abertos no nivel da vila (1..6). */
		public int openRings() {
			return rings & 15;
		}

		/** Anel com terraplenagem pronta. */
		public boolean ringDone(int ring) {
			return (rings & (1 << (ring + 3))) != 0;
		}

		public static final Type<Overlay> TYPE = new Type<>(MlinNotas.id("village_overlay"));
		private static final StreamCodec<ByteBuf, VillageLayout> LAYOUT = ByteBufCodecs.fromCodecTrusted(VillageData.LAYOUT_CODEC);
		public static final StreamCodec<ByteBuf, Overlay> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Overlay::village,
				BlockPos.STREAM_CODEC, Overlay::center,
				LAYOUT, Overlay::layout,
				LotInfo.CODEC.apply(ByteBufCodecs.list()), Overlay::lots,
				ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), Overlay::obstacles,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Overlay::kinds,
				ByteBufCodecs.VAR_INT, Overlay::seconds,
				ByteBufCodecs.VAR_INT, Overlay::rings,
				Overlay::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
