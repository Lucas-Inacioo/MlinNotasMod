package com.mlin.notas.quests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Progresso do livro de quests (logica pura). Completar e do <b>mundo</b> (qualquer jogador conta); resgatar a recompensa
 * e <b>por jogador</b> (id = UUID em texto). Tarefas so andam em quests ativas (dependencias completas, quest ainda
 * aberta). Progresso de tarefa por indice na lista da quest.
 */
public final class QuestProgress {
	public enum Status {
		LOCKED, ACTIVE, COMPLETE
	}

	/** Como a quest aparece para um jogador. */
	public enum View {
		LOCKED, ACTIVE, CLAIMABLE, DONE
	}

	private final Set<String> completed = new LinkedHashSet<>();
	private final Map<String, int[]> tasks = new HashMap<>();
	private final Map<String, Set<String>> claimed = new HashMap<>();

	public static QuestProgress of(List<String> completed, Map<String, List<Integer>> tasks, Map<String, List<String>> claimed) {
		QuestProgress progress = new QuestProgress();
		progress.completed.addAll(completed);
		tasks.forEach((key, values) -> progress.tasks.put(key, values.stream().mapToInt(Integer::intValue).toArray()));
		claimed.forEach((player, keys) -> progress.claimed.put(player, new LinkedHashSet<>(keys)));
		return progress;
	}

	// ---- leitura ----

	public boolean isComplete(String key) {
		return completed.contains(key);
	}

	public boolean isUnlocked(Quest quest) {
		for (String dep : quest.dependencies()) {
			if (!completed.contains(dep)) {
				return false;
			}
		}
		return true;
	}

	public Status status(Quest quest) {
		if (completed.contains(quest.key())) {
			return Status.COMPLETE;
		}
		return isUnlocked(quest) ? Status.ACTIVE : Status.LOCKED;
	}

	public View view(Quest quest, String player) {
		return switch (status(quest)) {
			case LOCKED -> View.LOCKED;
			case ACTIVE -> View.ACTIVE;
			case COMPLETE -> !quest.rewards().isEmpty() && !hasClaimed(player, quest) ? View.CLAIMABLE : View.DONE;
		};
	}

	public boolean hasClaimed(String player, Quest quest) {
		Set<String> keys = claimed.get(player);
		return keys != null && keys.contains(quest.key());
	}

	/** Progresso da tarefa {@code index} (cheio quando a quest esta completa). */
	public int progress(Quest quest, int index) {
		QuestTask task = quest.tasks().get(index);
		if (completed.contains(quest.key())) {
			return task.goal();
		}
		int[] values = tasks.get(quest.key());
		return values == null || index >= values.length ? 0 : Math.min(task.goal(), values[index]);
	}

	public boolean taskDone(Quest quest, int index) {
		return progress(quest, index) >= quest.tasks().get(index).goal();
	}

	public int remaining(Quest quest, int index) {
		return quest.tasks().get(index).goal() - progress(quest, index);
	}

	/** Fracao feita (0..1) somando as tarefas. */
	public double fraction(Quest quest) {
		if (completed.contains(quest.key())) {
			return 1;
		}
		int total = 0;
		int done = 0;
		for (int i = 0; i < quest.tasks().size(); i++) {
			total += quest.tasks().get(i).goal();
			done += progress(quest, i);
		}
		return total == 0 ? 0 : (double) done / total;
	}

	/** Quests ativas do livro (dependencias completas e ainda abertas). */
	public List<Quest> active(QuestBook book) {
		List<Quest> out = new ArrayList<>();
		for (Quest quest : book.quests()) {
			if (status(quest) == Status.ACTIVE) {
				out.add(quest);
			}
		}
		return out;
	}

	public int completedCount(QuestChapter chapter) {
		int count = 0;
		for (Quest quest : chapter.quests()) {
			if (completed.contains(quest.key())) {
				count++;
			}
		}
		return count;
	}

	/** Recompensas que {@code player} ainda pode resgatar no capitulo. */
	public int claimableCount(QuestChapter chapter, String player) {
		int count = 0;
		for (Quest quest : chapter.quests()) {
			if (view(quest, player) == View.CLAIMABLE) {
				count++;
			}
		}
		return count;
	}

	// ---- mudancas ----

	/**
	 * Soma {@code amount} na tarefa (so em quest ativa, ate o objetivo). Devolve quanto foi aceito.
	 */
	public int add(Quest quest, int index, int amount) {
		if (amount <= 0 || status(quest) != Status.ACTIVE) {
			return 0;
		}
		int accepted = Math.min(amount, remaining(quest, index));
		if (accepted > 0) {
			values(quest)[index] = progress(quest, index) + accepted;
		}
		return accepted;
	}

	/** Leva a tarefa ate pelo menos {@code value} (tarefas de "ter"); true se mudou. */
	public boolean raise(Quest quest, int index, int value) {
		int current = progress(quest, index);
		return value > current && add(quest, index, value - current) > 0;
	}

	/** Marca a tarefa como feita (posicao, checkmark...); true se mudou. */
	public boolean finish(Quest quest, int index) {
		return add(quest, index, remaining(quest, index)) > 0;
	}

	private int[] values(Quest quest) {
		int size = quest.tasks().size();
		int[] values = tasks.get(quest.key());
		if (values == null || values.length < size) {
			int[] grown = new int[size];
			if (values != null) {
				System.arraycopy(values, 0, grown, 0, values.length);
			}
			values = grown;
			tasks.put(quest.key(), values);
		}
		return values;
	}

	/**
	 * Completa toda quest ativa com todas as tarefas feitas (marcos sem tarefa completam assim que liberam), repetindo
	 * enquanto completar libera outras. Devolve as recem-completas, na ordem.
	 */
	public List<Quest> settle(QuestBook book) {
		List<Quest> done = new ArrayList<>();
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Quest quest : book.quests()) {
				if (status(quest) != Status.ACTIVE) {
					continue;
				}
				boolean all = true;
				for (int i = 0; i < quest.tasks().size() && all; i++) {
					all = taskDone(quest, i);
				}
				if (all) {
					markComplete(quest);
					done.add(quest);
					changed = true;
				}
			}
		}
		return done;
	}

	private void markComplete(Quest quest) {
		completed.add(quest.key());
		tasks.remove(quest.key());
	}

	/** Completa a quest e, antes, todas as dependencias que faltam (comando de operador). Devolve as recem-completas. */
	public List<Quest> forceComplete(QuestBook book, Quest quest) {
		List<Quest> done = new ArrayList<>();
		forceComplete(book, quest, done);
		return done;
	}

	private void forceComplete(QuestBook book, Quest quest, List<Quest> done) {
		if (completed.contains(quest.key())) {
			return;
		}
		markComplete(quest); // antes das dependencias: protege contra ciclo
		for (String dep : quest.dependencies()) {
			Quest other = book.quest(dep);
			if (other != null) {
				forceComplete(book, other, done);
			}
		}
		done.add(quest);
	}

	/** Registra o resgate de {@code player}; false se a quest nao esta completa, nao tem recompensa ou ja foi resgatada. */
	public boolean claim(String player, Quest quest) {
		if (view(quest, player) != View.CLAIMABLE) {
			return false;
		}
		claimed.computeIfAbsent(player, p -> new LinkedHashSet<>()).add(quest.key());
		return true;
	}

	public void clear() {
		completed.clear();
		tasks.clear();
		claimed.clear();
	}

	// ---- exportacao (codec / pacote) ----

	public List<String> completedList() {
		return List.copyOf(completed);
	}

	public Map<String, List<Integer>> taskMap() {
		Map<String, List<Integer>> out = new LinkedHashMap<>();
		tasks.forEach((key, values) -> {
			List<Integer> list = new ArrayList<>();
			for (int value : values) {
				list.add(value);
			}
			out.put(key, list);
		});
		return out;
	}

	public Map<String, List<String>> claimedMap() {
		Map<String, List<String>> out = new LinkedHashMap<>();
		claimed.forEach((player, keys) -> out.put(player, List.copyOf(keys)));
		return out;
	}

	/** Copia so com os resgates de {@code player} (o que o cliente dele precisa). */
	public QuestProgress forPlayer(String player) {
		Map<String, List<String>> own = new HashMap<>();
		Set<String> keys = claimed.get(player);
		if (keys != null) {
			own.put(player, List.copyOf(keys));
		}
		return of(completedList(), taskMap(), own);
	}
}
