package com.mlin.notas.quests;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/**
 * Uma tarefa de quest (logica pura). {@code target} e um id ({@code minecraft:zombie}) ou tag ({@code #minecraft:logs});
 * {@code count} so vale para item/matar; {@code level} so para habilidade (ramo em {@code target}).
 *
 * <p>JSON: {@code {"type":"item","target":"#minecraft:logs","count":16}}, {@code {"type":"item","target":"minecraft:coal",
 * "count":10,"consume":true}} (entrega pelo botao), {@code {"type":"kill","target":"minecraft:zombie","count":5}},
 * {@code {"type":"structure","target":"mlinnotas:old_well"}}, {@code {"type":"biome","target":"#minecraft:is_jungle"}},
 * {@code {"type":"dimension","target":"minecraft:the_nether"}}, {@code {"type":"skill","target":"mining","level":4}},
 * {@code {"type":"advancement","target":"minecraft:story/enchant_item","title":"chave.de.lang"}},
 * {@code {"type":"checkmark"}}. Opcionais: {@code icon} (id de item) e {@code title} (chave de lang do texto).</p>
 */
public record QuestTask(Type type, String target, int count, int level, boolean consume, String icon, String title) {
	public enum Type {
		ITEM, KILL, STRUCTURE, BIOME, DIMENSION, SKILL, ADVANCEMENT, CHECKMARK;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Type byId(String id) {
			for (Type type : values()) {
				if (type.id().equals(id)) {
					return type;
				}
			}
			return null;
		}

		/** Verificada pelo servidor a cada segundo (posicao, inventario, habilidades, conquistas). */
		public boolean periodic() {
			return this == STRUCTURE || this == BIOME || this == DIMENSION || this == SKILL || this == ADVANCEMENT;
		}

		public static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(id -> {
			Type type = byId(id);
			return type == null ? DataResult.error(() -> "tipo de tarefa desconhecido: " + id) : DataResult.success(type);
		}, Type::id);
	}

	public static final Codec<QuestTask> CODEC = RecordCodecBuilder.create(i -> i.group(
			Type.CODEC.fieldOf("type").forGetter(QuestTask::type),
			Codec.STRING.optionalFieldOf("target", "").forGetter(QuestTask::target),
			Codec.INT.optionalFieldOf("count", 1).forGetter(QuestTask::count),
			Codec.INT.optionalFieldOf("level", 1).forGetter(QuestTask::level),
			Codec.BOOL.optionalFieldOf("consume", false).forGetter(QuestTask::consume),
			Codec.STRING.optionalFieldOf("icon", "").forGetter(QuestTask::icon),
			Codec.STRING.optionalFieldOf("title", "").forGetter(QuestTask::title)
	).apply(i, QuestTask::new));

	public static QuestTask of(Type type, String target, int count) {
		return new QuestTask(type, target, count, 1, false, "", "");
	}

	public static QuestTask checkmark() {
		return of(Type.CHECKMARK, "", 1);
	}

	public boolean isTag() {
		return target.startsWith("#");
	}

	/** Id sem o {@code #} das tags. */
	public String targetId() {
		return isTag() ? target.substring(1) : target;
	}

	/** Quanto de progresso completa a tarefa (contagem para item/matar, 1 para as de sim/nao). */
	public int goal() {
		return type == Type.ITEM || type == Type.KILL ? count : 1;
	}

	/** Entregue pelo botao (o item sai do inventario). */
	public boolean isDelivery() {
		return type == Type.ITEM && consume;
	}

	/** Item que o jogador so precisa ter (verificado a cada segundo). */
	public boolean isPossession() {
		return type == Type.ITEM && !consume;
	}
}
