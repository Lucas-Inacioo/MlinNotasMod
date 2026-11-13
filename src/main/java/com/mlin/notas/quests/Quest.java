package com.mlin.notas.quests;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma quest (logica pura). {@code chapter} vem do arquivo (nao do JSON); {@code dependencies} ficam com chaves absolutas
 * ({@code capitulo/quest}) depois de {@link #inChapter}: no JSON, {@code "wood"} e uma quest do mesmo capitulo e
 * {@code "mining/skill_iron"} uma de outro. Posicao {@code x/y} em celulas do grafo (aceita meio: 2.5).
 *
 * <p>Textos: {@code quest.mlinnotas.<capitulo>.<id>} (titulo) e {@code ...<id>.desc} (descricao), ou as chaves dadas em
 * {@code title}/{@code description}. Quest sem tarefas e um marco: completa sozinha quando as dependencias completam.</p>
 */
public record Quest(String chapter, String id, String icon, double x, double y, List<String> dependencies, List<QuestTask> tasks,
		List<QuestReward> rewards, String title, String description) {

	public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Quest::id),
			Codec.STRING.optionalFieldOf("icon", "minecraft:book").forGetter(Quest::icon),
			Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Quest::x),
			Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(Quest::y),
			Codec.STRING.listOf().optionalFieldOf("dependencies", List.of()).forGetter(Quest::dependencies),
			QuestTask.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(Quest::tasks),
			QuestReward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
			Codec.STRING.optionalFieldOf("title", "").forGetter(Quest::title),
			Codec.STRING.optionalFieldOf("description", "").forGetter(Quest::description)
	).apply(i, (id, icon, x, y, deps, tasks, rewards, title, desc) -> new Quest("", id, icon, x, y, deps, tasks, rewards, title, desc)));

	public Quest {
		dependencies = List.copyOf(dependencies);
		tasks = List.copyOf(tasks);
		rewards = List.copyOf(rewards);
	}

	/** Coloca a quest no capitulo e torna as dependencias absolutas. */
	public Quest inChapter(String chapterId) {
		List<String> resolved = new ArrayList<>();
		for (String dep : dependencies) {
			resolved.add(resolve(chapterId, dep));
		}
		return new Quest(chapterId, id, icon, x, y, resolved, tasks, rewards, title, description);
	}

	/** {@code dep} relativo ao capitulo vira {@code capitulo/dep}; ja absoluto fica igual. */
	public static String resolve(String chapterId, String dep) {
		return dep.contains("/") ? dep : chapterId + "/" + dep;
	}

	/** Volta as dependencias para relativas quando sao do proprio capitulo (para gravar o JSON). */
	public Quest relativized() {
		List<String> out = new ArrayList<>();
		String prefix = chapter + "/";
		for (String dep : dependencies) {
			out.add(dep.startsWith(prefix) ? dep.substring(prefix.length()) : dep);
		}
		return new Quest(chapter, id, icon, x, y, out, tasks, rewards, title, description);
	}

	public String key() {
		return chapter + "/" + id;
	}

	public String titleKey() {
		return title.isEmpty() ? "quest.mlinnotas." + chapter + "." + id : title;
	}

	public String descriptionKey() {
		return description.isEmpty() ? "quest.mlinnotas." + chapter + "." + id + ".desc" : description;
	}

	public boolean isMilestone() {
		return tasks.isEmpty();
	}
}
