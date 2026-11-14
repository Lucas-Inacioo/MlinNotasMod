package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parte do estado da vila sobre as construcoes com nivel (logica pura): nivel de cada obra pronta (por id do lote;
 * sem registro = nivel I, como nas vilas de antes), melhorias abertas (entregas parciais) e na fila, e o que o
 * servidor contou no mundo para as necessidades (moradores, golens) e o ultimo dia em que elas renderam.
 */
public final class BuildingsState {
	public enum Outcome {
		/** Lote sem obra pronta, tipo sem nivel ou ja melhorando. */
		INVALID,
		/** Ja no nivel maximo que a Prefeitura (ou a vila) deixa. */
		CAPPED,
		NOTHING, PARTIAL, COMPLETE
	}

	/** Resultado de uma entrega de melhoria: o que tirar do jogador. */
	public record Deposit(Outcome outcome, Map<String, Integer> taken, int toLevel) {
		public boolean accepted() {
			return outcome == Outcome.PARTIAL || outcome == Outcome.COMPLETE;
		}
	}

	/** Melhoria de um lote para {@code toLevel}: entregue ate agora e itens de verdade pagos (devolucao). */
	public static final class Upgrade {
		private final int lot;
		private final int toLevel;
		private final Map<String, Integer> deposits = new LinkedHashMap<>();
		private final Map<String, Integer> paid = new LinkedHashMap<>();
		private boolean queued;

		public Upgrade(int lot, int toLevel) {
			this.lot = lot;
			this.toLevel = toLevel;
		}

		public int lot() {
			return lot;
		}

		public int toLevel() {
			return toLevel;
		}

		public boolean queued() {
			return queued;
		}

		public Map<String, Integer> deposits() {
			return Collections.unmodifiableMap(deposits);
		}

		public Map<String, Integer> paid() {
			return Collections.unmodifiableMap(paid);
		}

		public int deposited(String id) {
			return deposits.getOrDefault(id, 0);
		}

		/** Restauracao (codec). */
		public void restore(Map<String, Integer> deposits, Map<String, Integer> paid, boolean queued) {
			this.deposits.clear();
			this.deposits.putAll(deposits);
			this.paid.clear();
			this.paid.putAll(paid);
			this.queued = queued;
		}
	}

	private final Map<Integer, Integer> levels = new LinkedHashMap<>();
	private final Map<Integer, Upgrade> upgrades = new LinkedHashMap<>();
	/** Lotes com melhoria paga, na ordem de construcao. */
	private final List<Integer> queue = new ArrayList<>();
	private int population;
	private int golems;
	private long needsDay = Long.MIN_VALUE;

	/** Nivel da obra do lote (I sem registro). */
	public int level(int lot) {
		return levels.getOrDefault(lot, 1);
	}

	public void setLevel(int lot, int level) {
		if (level <= 1) {
			levels.remove(lot);
		} else {
			levels.put(lot, Math.min(5, level));
		}
	}

	/** Obra demolida: o lote volta ao nivel I e perde a melhoria aberta. */
	public void forget(int lot) {
		levels.remove(lot);
		upgrades.remove(lot);
		queue.remove((Integer) lot);
	}

	public Map<Integer, Integer> levels() {
		return Collections.unmodifiableMap(levels);
	}

	public Upgrade upgrade(int lot) {
		return upgrades.get(lot);
	}

	public Map<Integer, Upgrade> upgrades() {
		return Collections.unmodifiableMap(upgrades);
	}

	public List<Integer> queue() {
		return Collections.unmodifiableList(queue);
	}

	public int population() {
		return population;
	}

	public int golems() {
		return golems;
	}

	/** Moradores e golens contados no mundo agora (o servidor conta; os testes poem direto). */
	public void setCounts(int population, int golems) {
		this.population = Math.max(0, population);
		this.golems = Math.max(0, golems);
	}

	public long needsDay() {
		return needsDay;
	}

	public void setNeedsDay(long day) {
		this.needsDay = day;
	}

	/**
	 * Entrega para melhorar o lote {@code lot} (obra {@code project} no nivel {@code from}) o que o jogador tiver do
	 * que falta. {@code cap}: nivel maximo agora. Completou: a melhoria entra na fila e as entregas zeram.
	 */
	public Deposit deposit(int lot, VillageProject project, int from, int cap, Map<String, Integer> available) {
		Upgrade up = upgrades.get(lot);
		if (up != null && up.queued) {
			return new Deposit(Outcome.INVALID, Map.of(), up.toLevel);
		}
		int to = from + 1;
		if (BuildingTypes.of(project.id()) == null || from >= BuildingTypes.maxLevel(project.id())) {
			return new Deposit(Outcome.INVALID, Map.of(), to);
		}
		if (to > cap) {
			return new Deposit(Outcome.CAPPED, Map.of(), to);
		}
		if (up == null || up.toLevel != to) {
			up = new Upgrade(lot, to);
		}
		Map<String, Integer> taken = new LinkedHashMap<>();
		List<VillageCost> costs = BuildingTypes.upgradeCost(project, to);
		for (VillageCost cost : costs) {
			int need = cost.count() - up.deposited(cost.id());
			int give = Math.min(Math.max(0, need), Math.max(0, available.getOrDefault(cost.id(), 0)));
			if (give > 0) {
				taken.put(cost.id(), give);
			}
		}
		if (taken.isEmpty()) {
			return new Deposit(Outcome.NOTHING, Map.of(), to);
		}
		upgrades.put(lot, up);
		Upgrade target = up;
		taken.forEach((id, n) -> target.deposits.merge(id, n, Integer::sum));
		for (VillageCost cost : costs) {
			if (up.deposited(cost.id()) < cost.count()) {
				return new Deposit(Outcome.PARTIAL, taken, to);
			}
		}
		up.queued = true;
		queue.add(lot);
		return new Deposit(Outcome.COMPLETE, taken, to);
	}

	/** Anota os itens de verdade tirados do jogador na melhoria do lote. */
	public void recordPaid(int lot, Map<String, Integer> items) {
		Upgrade up = upgrades.get(lot);
		if (up != null) {
			items.forEach((id, n) -> up.paid.merge(id, n, Integer::sum));
		}
	}

	/** Fracao entregue (0..1) da melhoria aberta. */
	public static double fraction(Upgrade up, VillageProject project) {
		int total = 0;
		int done = 0;
		for (VillageCost cost : BuildingTypes.upgradeCost(project, up.toLevel())) {
			total += cost.count();
			done += Math.min(cost.count(), up.deposited(cost.id()));
		}
		return total == 0 ? 1 : (double) done / total;
	}

	/** Proxima melhoria paga (lote) ou -1. */
	public int nextQueued() {
		return queue.isEmpty() ? -1 : queue.getFirst();
	}

	/** Melhoria construida: o lote sobe de nivel. */
	public void finish(int lot) {
		Upgrade up = upgrades.remove(lot);
		queue.remove((Integer) lot);
		if (up != null) {
			setLevel(lot, up.toLevel);
		}
	}

	/** Cancela a melhoria do lote (aberta ou na fila, ainda nao comecada): devolve os itens pagos ou null. */
	public Map<String, Integer> cancel(int lot) {
		Upgrade up = upgrades.remove(lot);
		queue.remove((Integer) lot);
		return up == null ? null : Map.copyOf(up.paid);
	}

	/** Restauracao (codec). */
	public void restore(Map<Integer, Integer> levels, List<Upgrade> upgrades, List<Integer> queue, int population, int golems, long needsDay) {
		this.levels.clear();
		levels.forEach(this::setLevel);
		this.upgrades.clear();
		for (Upgrade up : upgrades) {
			this.upgrades.put(up.lot, up);
		}
		this.queue.clear();
		for (int lot : queue) {
			Upgrade up = this.upgrades.get(lot);
			if (up != null && up.queued && !this.queue.contains(lot)) {
				this.queue.add(lot);
			}
		}
		this.population = Math.max(0, population);
		this.golems = Math.max(0, golems);
		this.needsDay = needsDay;
	}

	public boolean isEmpty() {
		return levels.isEmpty() && upgrades.isEmpty() && population == 0 && golems == 0 && needsDay == Long.MIN_VALUE;
	}
}
