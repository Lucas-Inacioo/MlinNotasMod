package com.mlin.notas.quests;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Recompensa de quest, resgatada uma vez por jogador (logica pura). JSON: {@code {"item":"minecraft:bread","count":8}}
 * ou {@code {"xp":50}} (pontos de experiencia).
 */
public record QuestReward(String item, int count, int xp) {
	public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("item", "").forGetter(QuestReward::item),
			Codec.INT.optionalFieldOf("count", 1).forGetter(QuestReward::count),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(QuestReward::xp)
	).apply(i, QuestReward::new));

	public static QuestReward item(String id, int count) {
		return new QuestReward(id, count, 0);
	}

	public static QuestReward xp(int points) {
		return new QuestReward("", 1, points);
	}

	public boolean isItem() {
		return !item.isEmpty();
	}
}
