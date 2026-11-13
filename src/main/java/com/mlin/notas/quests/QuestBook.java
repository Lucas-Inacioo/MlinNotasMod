package com.mlin.notas.quests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mlin.notas.skills.SkillBranch;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Livro de quests carregado (logica pura): todos os capitulos, busca por chave e validacao (dependencias existentes, sem
 * ciclos, tarefas e recompensas bem formadas, posicoes sem sobreposicao).
 */
public final class QuestBook {
	public static final QuestBook EMPTY = new QuestBook(List.of());

	private final List<QuestChapter> chapters;
	private final Map<String, Quest> byKey = new LinkedHashMap<>();
	private final Map<String, QuestChapter> chapterById = new HashMap<>();
	private final List<Quest> all;

	public QuestBook(List<QuestChapter> chapters) {
		List<QuestChapter> sorted = new ArrayList<>(chapters);
		sorted.sort(Comparator.comparingInt(QuestChapter::order).thenComparing(QuestChapter::id));
		this.chapters = List.copyOf(sorted);
		for (QuestChapter chapter : this.chapters) {
			chapterById.putIfAbsent(chapter.id(), chapter);
			for (Quest quest : chapter.quests()) {
				byKey.putIfAbsent(quest.key(), quest);
			}
		}
		this.all = List.copyOf(byKey.values());
	}

	public List<QuestChapter> chapters() {
		return chapters;
	}

	public QuestChapter chapter(String id) {
		return chapterById.get(id);
	}

	public Quest quest(String key) {
		return byKey.get(key);
	}

	/** Todas as quests, na ordem dos capitulos. */
	public List<Quest> quests() {
		return all;
	}

	/** Quests que dependem de {@code key}. */
	public List<Quest> dependents(String key) {
		List<Quest> out = new ArrayList<>();
		for (Quest quest : byKey.values()) {
			if (quest.dependencies().contains(key)) {
				out.add(quest);
			}
		}
		return out;
	}

	// ---- JSON ----

	/** Le um capitulo do JSON do arquivo {@code <id>.json}. */
	public static QuestChapter parseChapter(String id, String json) {
		JsonElement element = JsonParser.parseString(json);
		QuestChapter.Definition definition = QuestChapter.Definition.CODEC.parse(JsonOps.INSTANCE, element)
				.getOrThrow(message -> new IllegalArgumentException("capitulo " + id + ": " + message));
		return QuestChapter.of(id, definition);
	}

	public static String toJson(QuestChapter chapter) {
		return QuestChapter.Definition.CODEC.encodeStart(JsonOps.INSTANCE, chapter.definition()).getOrThrow().toString();
	}

	// ---- validacao ----

	/** Problemas do livro (vazio = ok). */
	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		Set<String> chapterIds = new HashSet<>();
		Set<String> keys = new HashSet<>();
		for (QuestChapter chapter : chapters) {
			if (!chapterIds.add(chapter.id())) {
				errors.add("capitulo repetido: " + chapter.id());
			}
			if (chapter.id().isEmpty() || chapter.id().contains("/")) {
				errors.add("id de capitulo invalido: '" + chapter.id() + "'");
			}
			Map<String, String> positions = new HashMap<>();
			for (Quest quest : chapter.quests()) {
				if (!keys.add(quest.key())) {
					errors.add("quest repetida: " + quest.key());
				}
				if (quest.id().isEmpty() || quest.id().contains("/")) {
					errors.add("id de quest invalido: '" + quest.key() + "'");
				}
				String other = positions.put(quest.x() + "," + quest.y(), quest.key());
				if (other != null) {
					errors.add(quest.key() + " esta na mesma posicao que " + other);
				}
				validateQuest(quest, errors);
			}
		}
		for (Quest quest : byKey.values()) {
			for (String dep : quest.dependencies()) {
				if (dep.equals(quest.key())) {
					errors.add(quest.key() + " depende de si mesma");
				} else if (!byKey.containsKey(dep)) {
					errors.add(quest.key() + " depende de quest inexistente: " + dep);
				}
			}
		}
		errors.addAll(cycles());
		return errors;
	}

	private static void validateQuest(Quest quest, List<String> errors) {
		String key = quest.key();
		for (int i = 0; i < quest.tasks().size(); i++) {
			QuestTask task = quest.tasks().get(i);
			String where = key + " tarefa " + i + " (" + task.type().id() + ")";
			if (task.type() != QuestTask.Type.CHECKMARK && task.type() != QuestTask.Type.SKILL && !isId(task.targetId())) {
				errors.add(where + ": alvo invalido '" + task.target() + "'");
			}
			if ((task.type() == QuestTask.Type.ITEM || task.type() == QuestTask.Type.KILL) && task.count() < 1) {
				errors.add(where + ": contagem precisa ser >= 1");
			}
			if (task.consume() && task.type() != QuestTask.Type.ITEM) {
				errors.add(where + ": consume so vale para item");
			}
			if (task.isTag() && (task.type() == QuestTask.Type.DIMENSION || task.type() == QuestTask.Type.ADVANCEMENT)) {
				errors.add(where + ": nao aceita tag");
			}
			if (task.type() == QuestTask.Type.SKILL) {
				SkillBranch branch = SkillBranch.byId(task.target());
				if (branch == null) {
					errors.add(where + ": ramo desconhecido '" + task.target() + "'");
				} else if (task.level() < 1 || task.level() > branch.maxLevel()) {
					errors.add(where + ": nivel fora de 1.." + branch.maxLevel());
				}
			}
			if (!task.icon().isEmpty() && !isId(task.icon())) {
				errors.add(where + ": icone invalido '" + task.icon() + "'");
			}
		}
		for (QuestReward reward : quest.rewards()) {
			if (reward.isItem() == (reward.xp() > 0)) {
				errors.add(key + ": recompensa precisa de item ou xp (um so)");
			} else if (reward.isItem() && (!isId(reward.item()) || reward.count() < 1)) {
				errors.add(key + ": recompensa de item invalida " + reward.item() + " x" + reward.count());
			}
		}
		if (!isId(quest.icon())) {
			errors.add(key + ": icone invalido '" + quest.icon() + "'");
		}
	}

	/** {@code ns:caminho} com caracteres de Identifier. */
	static boolean isId(String id) {
		int colon = id.indexOf(':');
		return colon > 0 && colon < id.length() - 1 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
	}

	private List<String> cycles() {
		List<String> errors = new ArrayList<>();
		Map<String, Integer> state = new HashMap<>();
		for (String key : byKey.keySet()) {
			List<String> path = new ArrayList<>();
			if (visit(key, state, path)) {
				errors.add("ciclo de dependencias: " + String.join(" -> ", path));
				return errors;
			}
		}
		return errors;
	}

	/** DFS (0 = novo, 1 = na pilha, 2 = pronto); true se achou ciclo, deixando o caminho em {@code path}. */
	private boolean visit(String key, Map<String, Integer> state, List<String> path) {
		int s = state.getOrDefault(key, 0);
		if (s == 2) {
			return false;
		}
		path.add(key);
		if (s == 1) {
			int start = path.indexOf(key);
			List<String> cycle = new ArrayList<>(path.subList(start, path.size()));
			path.clear();
			path.addAll(cycle);
			return true;
		}
		state.put(key, 1);
		Quest quest = byKey.get(key);
		if (quest != null) {
			for (String dep : quest.dependencies()) {
				if (byKey.containsKey(dep) && visit(dep, state, path)) {
					return true;
				}
			}
		}
		state.put(key, 2);
		path.removeLast();
		return false;
	}

	/** Chaves de lang que o livro usa (titulos, descricoes, capitulos, titulos de tarefa). */
	public List<String> translationKeys() {
		List<String> out = new ArrayList<>();
		for (QuestChapter chapter : chapters) {
			out.add(chapter.titleKey());
			for (Quest quest : chapter.quests()) {
				out.add(quest.titleKey());
				out.add(quest.descriptionKey());
				for (QuestTask task : quest.tasks()) {
					if (!task.title().isEmpty()) {
						out.add(task.title());
					}
				}
			}
		}
		return Collections.unmodifiableList(out);
	}
}
