package com.mlin.notas.quests;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Capitulo do livro de quests (logica pura): um arquivo {@code data/<ns>/quests/<id>.json}. {@code order} ordena a lista
 * de capitulos (menor primeiro). Titulo em {@code quest.mlinnotas.chapter.<id>} (ou a chave em {@code title}).
 */
public record QuestChapter(String id, String icon, int order, String title, List<Quest> quests) {
	/** Formato do arquivo (o id vem do nome do arquivo). */
	public record Definition(String icon, int order, String title, List<Quest> quests) {
		public static final Codec<Definition> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("icon", "minecraft:book").forGetter(Definition::icon),
				Codec.INT.optionalFieldOf("order", 100).forGetter(Definition::order),
				Codec.STRING.optionalFieldOf("title", "").forGetter(Definition::title),
				Quest.CODEC.listOf().fieldOf("quests").forGetter(Definition::quests)
		).apply(i, Definition::new));
	}

	public QuestChapter {
		quests = List.copyOf(quests);
	}

	public static QuestChapter of(String id, Definition definition) {
		return new QuestChapter(id, definition.icon(), definition.order(), definition.title(),
				definition.quests().stream().map(q -> q.inChapter(id)).toList());
	}

	public Definition definition() {
		return new Definition(icon, order, title, quests.stream().map(Quest::relativized).toList());
	}

	public String titleKey() {
		return title.isEmpty() ? "quest.mlinnotas.chapter." + id : title;
	}
}
