package com.mlin.notas.quests;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pacotes do livro de quests. */
public final class QuestPayloads {
	private QuestPayloads() {
	}

	private static final StreamCodec<ByteBuf, String> BIG_STRING = ByteBufCodecs.stringUtf8(1 << 20);
	private static final StreamCodec<ByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

	/** Servidor -> cliente: capitulos (id -> JSON). Enviado ao entrar e a cada {@code /reload}. */
	public record Book(Map<String, String> chapters) implements CustomPacketPayload {
		public static final Type<Book> TYPE = new Type<>(MlinNotas.id("quest_book"));
		public static final StreamCodec<ByteBuf, Book> CODEC = ByteBufCodecs.<ByteBuf, String, String, Map<String, String>>map(HashMap::new,
				ByteBufCodecs.STRING_UTF8, BIG_STRING).map(Book::new, Book::chapters);

		public static Book of(QuestBook book) {
			Map<String, String> chapters = new HashMap<>();
			for (QuestChapter chapter : book.chapters()) {
				chapters.put(chapter.id(), QuestBook.toJson(chapter));
			}
			return new Book(chapters);
		}

		public QuestBook toBook() {
			return new QuestBook(chapters.entrySet().stream().map(e -> QuestBook.parseChapter(e.getKey(), e.getValue())).toList());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Servidor -> cliente: progresso do mundo + os resgates do proprio jogador. */
	public record Progress(List<String> completed, Map<String, List<Integer>> tasks, List<String> claimed) implements CustomPacketPayload {
		public static final Type<Progress> TYPE = new Type<>(MlinNotas.id("quest_progress"));
		public static final StreamCodec<ByteBuf, Progress> CODEC = StreamCodec.composite(
				STRINGS, Progress::completed,
				ByteBufCodecs.<ByteBuf, String, List<Integer>, Map<String, List<Integer>>>map(HashMap::new, ByteBufCodecs.STRING_UTF8,
						ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())), Progress::tasks,
				STRINGS, Progress::claimed,
				Progress::new);

		public static Progress of(QuestProgress progress, String player) {
			return new Progress(progress.completedList(), progress.taskMap(), progress.claimedMap().getOrDefault(player, List.of()));
		}

		public QuestProgress toProgress(String player) {
			return QuestProgress.of(completed, tasks, Map.of(player, claimed));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Servidor -> cliente: quests recem-completas (toast). */
	public record Completed(List<String> quests) implements CustomPacketPayload {
		public static final Type<Completed> TYPE = new Type<>(MlinNotas.id("quest_completed"));
		public static final StreamCodec<ByteBuf, Completed> CODEC = STRINGS.map(Completed::new, Completed::quests);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Cliente -> servidor: acao do jogador numa quest. */
	public record Action(String quest, int action) implements CustomPacketPayload {
		public static final int SUBMIT = 0;
		public static final int CHECK = 1;
		public static final int CLAIM = 2;

		public static final Type<Action> TYPE = new Type<>(MlinNotas.id("quest_action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Action::quest,
				ByteBufCodecs.VAR_INT, Action::action,
				Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
