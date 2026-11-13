package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado de uma vila (logica pura): centro (o quadro de missoes), estilo, nivel, prosperidade, obras prontas e
 * entregas parciais de cada projeto, fila de obras esperando construcao, lotes ja ocupados e as missoes do dia. Toda
 * a progressao e compartilhada: qualquer jogador entrega, caca ou explora pela vila.
 */
public final class VillageState {
	public enum Status {
		/** Aceita entrega. */
		AVAILABLE,
		/** A vila ainda nao tem o nivel do projeto. */
		LOCKED,
		/** Ja feito o maximo de vezes para o nivel atual (contando a fila). */
		MAXED
	}

	public enum Outcome {
		LOCKED, MAXED, NOTHING, PARTIAL, COMPLETE
	}

	/** Resultado de uma entrega: o que tirar do jogador. */
	public record Deposit(Outcome outcome, Map<String, Integer> taken) {
		public boolean accepted() {
			return outcome == Outcome.PARTIAL || outcome == Outcome.COMPLETE;
		}
	}

	public enum BountyOutcome {
		/** Indice invalido ou missao ja paga hoje. */
		INVALID,
		/** Ainda falta (cacar/explorar) ou o jogador nao tem nada para entregar. */
		NOT_READY,
		/** Entrega parcial aceita. */
		PARTIAL,
		/** Completa: pagar o jogador. */
		PAID
	}

	public record BountyResult(BountyOutcome outcome, int taken, Bounty bounty) {
	}

	/** Lote ocupado (relativo ao centro, inclusivo). */
	public record Rect(int x0, int z0, int x1, int z1) {
		public boolean overlaps(Rect o, int margin) {
			return x0 - margin <= o.x1 && o.x0 <= x1 + margin && z0 - margin <= o.z1 && o.z0 <= z1 + margin;
		}

		public boolean contains(int x, int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}
	}

	private final String dimension;
	private final int x;
	private final int y;
	private final int z;
	private final long seed;
	private final VillageStyle style;
	private String name;
	private boolean starter;
	private VillageLevel level = VillageLevel.CAMP;
	private int prosperity;
	private final EnumMap<VillageProject, Integer> built = new EnumMap<>(VillageProject.class);
	private final EnumMap<VillageProject, Map<String, Integer>> deposits = new EnumMap<>(VillageProject.class);
	private final List<VillageProject> queue = new ArrayList<>();
	private final List<Rect> lots = new ArrayList<>();
	private long bountyDay = Long.MIN_VALUE;
	private final List<Bounty> bounties = new ArrayList<>();
	private final int[] bountyProgress = new int[BountyBoard.PER_DAY];
	private final boolean[] bountyDone = new boolean[BountyBoard.PER_DAY];

	public VillageState(String dimension, int x, int y, int z, long seed, VillageStyle style, String name) {
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.seed = seed;
		this.style = style;
		this.name = name;
	}

	public static String key(String dimension, int x, int y, int z) {
		return dimension + "@" + x + "," + y + "," + z;
	}

	public String key() {
		return key(dimension, x, y, z);
	}

	public String dimension() {
		return dimension;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int z() {
		return z;
	}

	public long seed() {
		return seed;
	}

	public VillageStyle style() {
		return style;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/** Vila inicial (nascimento do mundo). */
	public boolean starter() {
		return starter;
	}

	public void setStarter(boolean starter) {
		this.starter = starter;
	}

	public VillageLevel level() {
		return level;
	}

	public int prosperity() {
		return prosperity;
	}

	public int built(VillageProject project) {
		return built.getOrDefault(project, 0);
	}

	public Map<VillageProject, Integer> builtMap() {
		return Collections.unmodifiableMap(built);
	}

	public int totalBuilt() {
		return built.values().stream().mapToInt(Integer::intValue).sum();
	}

	public List<VillageProject> queue() {
		return Collections.unmodifiableList(queue);
	}

	public List<Rect> lots() {
		return Collections.unmodifiableList(lots);
	}

	public void addLot(Rect rect) {
		lots.add(rect);
	}

	/** Distancia horizontal (quadrada) do centro, com a posicao absoluta. */
	public long distanceSq(int px, int pz) {
		long dx = px - x;
		long dz = pz - z;
		return dx * dx + dz * dz;
	}

	// ---- projetos ----

	public int queued(VillageProject project) {
		return (int) queue.stream().filter(p -> p == project).count();
	}

	public Status status(VillageProject project) {
		if (level.ordinal() < project.minLevel().ordinal()) {
			return Status.LOCKED;
		}
		if (built(project) + queued(project) >= project.maxBuilds(level)) {
			return Status.MAXED;
		}
		return Status.AVAILABLE;
	}

	public int deposited(VillageProject project, String costId) {
		Map<String, Integer> map = deposits.get(project);
		return map == null ? 0 : map.getOrDefault(costId, 0);
	}

	public int remaining(VillageProject project, String costId) {
		return Math.max(0, project.count(costId) - deposited(project, costId));
	}

	public Map<VillageProject, Map<String, Integer>> depositsMap() {
		Map<VillageProject, Map<String, Integer>> out = new EnumMap<>(VillageProject.class);
		deposits.forEach((p, m) -> {
			if (!m.isEmpty()) {
				out.put(p, Map.copyOf(m));
			}
		});
		return out;
	}

	/** Fracao entregue (0..1) do projeto, para a barra da tela. */
	public double fraction(VillageProject project) {
		int total = 0;
		int done = 0;
		for (VillageCost cost : project.costs()) {
			total += cost.count();
			done += Math.min(cost.count(), deposited(project, cost.id()));
		}
		return total == 0 ? 1 : (double) done / total;
	}

	/**
	 * Entrega para {@code project} o que o jogador tiver do que falta ({@code available} = quanto ele tem de cada id de
	 * custo). Completou: o projeto entra na fila de construcao e as entregas zeram.
	 */
	public Deposit deposit(VillageProject project, Map<String, Integer> available) {
		Status status = status(project);
		if (status == Status.LOCKED) {
			return new Deposit(Outcome.LOCKED, Map.of());
		}
		if (status == Status.MAXED) {
			return new Deposit(Outcome.MAXED, Map.of());
		}
		Map<String, Integer> taken = new LinkedHashMap<>();
		for (VillageCost cost : project.costs()) {
			int give = Math.min(remaining(project, cost.id()), Math.max(0, available.getOrDefault(cost.id(), 0)));
			if (give > 0) {
				taken.put(cost.id(), give);
			}
		}
		if (taken.isEmpty()) {
			return new Deposit(Outcome.NOTHING, Map.of());
		}
		Map<String, Integer> map = deposits.computeIfAbsent(project, p -> new LinkedHashMap<>());
		taken.forEach((id, count) -> map.merge(id, count, Integer::sum));
		for (VillageCost cost : project.costs()) {
			if (remaining(project, cost.id()) > 0) {
				return new Deposit(Outcome.PARTIAL, taken);
			}
		}
		deposits.remove(project);
		queue.add(project);
		return new Deposit(Outcome.COMPLETE, taken);
	}

	/** Coloca na fila sem custo (testes, comandos, vila inicial). */
	public void enqueue(VillageProject project) {
		queue.add(project);
	}

	/** A obra da frente da fila ficou pronta: conta, soma prosperidade. Devolve o nivel novo se subiu (senao null). */
	public VillageLevel finishBuild(VillageProject project) {
		queue.remove(project);
		built.merge(project, 1, Integer::sum);
		return addProsperity(project.prosperity());
	}

	/** Tira da fila sem construir (sem lugar). */
	public void dropQueued(VillageProject project) {
		queue.remove(project);
	}

	/** Conta uma obra pronta sem prosperidade (construcoes da vila inicial). */
	public void addBuilt(VillageProject project) {
		built.merge(project, 1, Integer::sum);
	}

	/** Soma prosperidade e recalcula o nivel. Devolve o nivel novo se subiu (senao null). */
	public VillageLevel addProsperity(int amount) {
		prosperity = Math.max(0, prosperity + amount);
		return recompute();
	}

	/** Recalcula o nivel (nunca desce). Devolve o nivel novo se subiu (senao null). */
	public VillageLevel recompute() {
		VillageLevel reached = VillageLevel.reached(level, prosperity, built);
		if (reached != level) {
			level = reached;
			return reached;
		}
		return null;
	}

	/** Forca nivel/prosperidade (testes, comandos). */
	public void setLevel(VillageLevel level) {
		this.level = level;
	}

	// ---- missoes ----

	/** Troca as missoes se mudou o dia. Devolve true se trocou. */
	public boolean refreshBounties(long day) {
		if (day == bountyDay && bounties.size() == BountyBoard.PER_DAY) {
			return false;
		}
		bountyDay = day;
		bounties.clear();
		bounties.addAll(BountyBoard.roll(seed, day, level));
		java.util.Arrays.fill(bountyProgress, 0);
		java.util.Arrays.fill(bountyDone, false);
		return true;
	}

	public long bountyDay() {
		return bountyDay;
	}

	public List<Bounty> bounties() {
		return Collections.unmodifiableList(bounties);
	}

	public int bountyProgress(int index) {
		return index >= 0 && index < bountyProgress.length ? bountyProgress[index] : 0;
	}

	public boolean bountyDone(int index) {
		return index >= 0 && index < bountyDone.length && bountyDone[index];
	}

	/** Missao pronta para receber (cacada/exploracao completas). */
	public boolean bountyReady(int index) {
		if (index < 0 || index >= bounties.size() || bountyDone[index]) {
			return false;
		}
		return bountyProgress[index] >= bounties.get(index).count();
	}

	/**
	 * Conta progresso de cacada/exploracao: soma {@code amount} em toda missao aberta do tipo com esse alvo. Devolve os
	 * indices que acabaram de ficar prontos.
	 */
	public List<Integer> progress(Bounty.Kind kind, String target, int amount) {
		List<Integer> ready = new ArrayList<>();
		for (int i = 0; i < bounties.size(); i++) {
			Bounty b = bounties.get(i);
			if (b.kind() == kind && b.target().equals(target) && !bountyDone[i] && bountyProgress[i] < b.count()) {
				bountyProgress[i] = Math.min(b.count(), bountyProgress[i] + amount);
				if (bountyProgress[i] >= b.count()) {
					ready.add(i);
				}
			}
		}
		return ready;
	}

	/**
	 * Entregar/receber a missao {@code index}. Entrega: {@code available} = quanto o jogador tem do item; aceita
	 * entregas parciais. Cacada/exploracao: so paga quando completa.
	 */
	public BountyResult claim(int index, int available) {
		if (index < 0 || index >= bounties.size() || bountyDone[index]) {
			return new BountyResult(BountyOutcome.INVALID, 0, null);
		}
		Bounty bounty = bounties.get(index);
		int taken = 0;
		if (bounty.kind() == Bounty.Kind.DELIVER) {
			taken = Math.min(Math.max(0, available), bounty.count() - bountyProgress[index]);
			if (taken <= 0) {
				return new BountyResult(BountyOutcome.NOT_READY, 0, bounty);
			}
			bountyProgress[index] += taken;
			if (bountyProgress[index] < bounty.count()) {
				return new BountyResult(BountyOutcome.PARTIAL, taken, bounty);
			}
		} else if (bountyProgress[index] < bounty.count()) {
			return new BountyResult(BountyOutcome.NOT_READY, 0, bounty);
		}
		bountyDone[index] = true;
		return new BountyResult(BountyOutcome.PAID, taken, bounty);
	}

	// ---- salvar/carregar (a ponte com o Codec fica em VillageData) ----

	public int[] bountyProgressArray() {
		return bountyProgress.clone();
	}

	public boolean[] bountyDoneArray() {
		return bountyDone.clone();
	}

	public void restore(VillageLevel level, int prosperity, Map<VillageProject, Integer> built, Map<VillageProject, Map<String, Integer>> deposits,
			List<VillageProject> queue, List<Rect> lots, long bountyDay, List<Bounty> bounties, int[] progress, boolean[] done) {
		this.level = level;
		this.prosperity = prosperity;
		this.built.clear();
		this.built.putAll(built);
		this.deposits.clear();
		deposits.forEach((p, m) -> this.deposits.put(p, new LinkedHashMap<>(m)));
		this.queue.clear();
		this.queue.addAll(queue);
		this.lots.clear();
		this.lots.addAll(lots);
		this.bountyDay = bountyDay;
		this.bounties.clear();
		this.bounties.addAll(bounties);
		for (int i = 0; i < BountyBoard.PER_DAY; i++) {
			bountyProgress[i] = i < progress.length ? progress[i] : 0;
			bountyDone[i] = i < done.length && done[i];
		}
	}
}
