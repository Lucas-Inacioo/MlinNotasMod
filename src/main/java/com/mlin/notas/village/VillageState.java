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
	private final int[] bountyProgress = new int[BountyBoard.MAX_PER_DAY];
	private final boolean[] bountyDone = new boolean[BountyBoard.MAX_PER_DAY];
	/** Plano do espaco (null = vila de antes do plano; o servidor calcula ao carregar). */
	private VillageLayout layout;
	/** Itens de verdade entregues em cada projeto ainda aberto (devolucao). */
	private final EnumMap<VillageProject, Map<String, Integer>> paid = new EnumMap<>(VillageProject.class);
	/** Itens pagos de cada obra da fila (mesma ordem de {@link #queue}). */
	private final List<Map<String, Integer>> queuePaid = new ArrayList<>();
	/** Terraplenagem de cada anel do plano diretor (indice = anel - 1). */
	private final RingWork[] rings = new RingWork[MasterPlan.RINGS];
	/** Terra cortada sobrando (blocos), que paga aterro de outros aneis. */
	private int stockpile;
	/** Lotes de jogador dentro da vila com cama e porta: contam como casas (moradia e pontos de obra). */
	private int playerHouses;
	/** Niveis das construcoes, melhorias, moradores contados (Vilas 2.0, fase 4). */
	private final BuildingsState buildingsState = new BuildingsState();

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
		Map<String, Integer> items = paid.remove(project);
		queuePaid.add(items == null ? new LinkedHashMap<>() : items);
		return new Deposit(Outcome.COMPLETE, taken);
	}

	/** Coloca na fila sem custo (testes, comandos, vila inicial). */
	public void enqueue(VillageProject project) {
		queue.add(project);
		queuePaid.add(new LinkedHashMap<>());
	}

	/** A obra da frente da fila ficou pronta: conta, soma prosperidade. Devolve o nivel novo se subiu (senao null). */
	public VillageLevel finishBuild(VillageProject project) {
		removeQueued(queue.indexOf(project));
		built.merge(project, 1, Integer::sum);
		return addProsperity(project.prosperity());
	}

	/** Tira da fila sem construir (sem lugar). */
	public void dropQueued(VillageProject project) {
		removeQueued(queue.indexOf(project));
	}

	private void removeQueued(int index) {
		if (index < 0 || index >= queue.size()) {
			return;
		}
		queue.remove(index);
		if (index < queuePaid.size()) {
			queuePaid.remove(index);
		}
	}

	/** Itens pagos pela primeira obra do projeto na fila (vazio = desconhecido, obra antiga). */
	public Map<String, Integer> queuedPaid(VillageProject project) {
		int index = queue.indexOf(project);
		return index >= 0 && index < queuePaid.size() ? Map.copyOf(queuePaid.get(index)) : Map.of();
	}

	/** Itens pagos por cada obra da fila (mesma ordem de {@link #queue()}). */
	public List<Map<String, Integer>> queuePaid() {
		List<Map<String, Integer>> out = new ArrayList<>();
		for (int i = 0; i < queue.size(); i++) {
			out.add(i < queuePaid.size() ? Map.copyOf(queuePaid.get(i)) : Map.of());
		}
		return out;
	}

	/** Itens entregues ate agora num projeto aberto. */
	public Map<String, Integer> paid(VillageProject project) {
		Map<String, Integer> map = paid.get(project);
		return map == null ? Map.of() : Map.copyOf(map);
	}

	public Map<VillageProject, Map<String, Integer>> paidMap() {
		Map<VillageProject, Map<String, Integer>> out = new EnumMap<>(VillageProject.class);
		paid.forEach((p, m) -> {
			if (!m.isEmpty()) {
				out.put(p, Map.copyOf(m));
			}
		});
		return out;
	}

	/**
	 * Registra os itens de verdade tirados do jogador numa entrega ({@code complete}: a entrega fechou o projeto e os
	 * itens vao para a obra que acabou de entrar na fila).
	 */
	public void recordPaid(VillageProject project, Map<String, Integer> items, boolean complete) {
		Map<String, Integer> target;
		if (complete && !queuePaid.isEmpty() && queue.getLast() == project) {
			target = queuePaid.getLast();
		} else {
			target = paid.computeIfAbsent(project, p -> new LinkedHashMap<>());
		}
		items.forEach((id, n) -> target.merge(id, n, Integer::sum));
	}

	/**
	 * Cancela a obra {@code index} da fila (ainda nao comecada). Devolve o projeto e os itens pagos (vazio = obra
	 * antiga sem registro: a devolucao usa o custo), ou null se o indice nao existe.
	 */
	public Map.Entry<VillageProject, Map<String, Integer>> cancelQueued(int index) {
		if (index < 0 || index >= queue.size()) {
			return null;
		}
		VillageProject project = queue.get(index);
		Map<String, Integer> items = index < queuePaid.size() ? Map.copyOf(queuePaid.get(index)) : Map.of();
		removeQueued(index);
		return Map.entry(project, items);
	}

	/** A obra do projeto foi demolida: conta uma a menos e tira a prosperidade que ela deu (o nivel nao desce). */
	public void unbuild(VillageProject project) {
		int n = built(project);
		if (n <= 1) {
			built.remove(project);
		} else {
			built.put(project, n - 1);
		}
		prosperity = Math.max(0, prosperity - project.prosperity());
	}

	/** Tira um lote antigo (obra demolida) da lista de ocupados. */
	public void removeLot(Rect rect) {
		lots.remove(rect);
	}

	public VillageLayout layout() {
		return layout;
	}

	public void setLayout(VillageLayout layout) {
		this.layout = layout;
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

	// ---- plano diretor: terraplenagem e casas de jogador ----

	/** Terraplenagem do anel (1..6). */
	public RingWork ring(int ring) {
		int i = Math.max(0, Math.min(MasterPlan.RINGS - 1, ring - 1));
		if (rings[i] == null) {
			rings[i] = new RingWork(i + 1);
		}
		return rings[i];
	}

	/** Aneis com terraplenagem pronta (bit {@code 1 << (anel - 1)}). */
	public int ringsDone() {
		int mask = 0;
		for (int i = 0; i < rings.length; i++) {
			if (rings[i] != null && rings[i].done()) {
				mask |= 1 << i;
			}
		}
		return mask;
	}

	/** Vila com plano diretor (os aneis contam para o nivel). */
	public boolean planned() {
		return layout != null && layout.master() != null;
	}

	public int stockpile() {
		return stockpile;
	}

	public void setStockpile(int stockpile) {
		this.stockpile = Math.max(0, stockpile);
	}

	public int playerHouses() {
		return playerHouses;
	}

	/**
	 * Casas de jogador achadas na vila agora. Cada casa nova soma a prosperidade de uma casa (uma vez); devolve o nivel
	 * novo se subiu.
	 */
	public VillageLevel setPlayerHouses(int count) {
		int before = playerHouses;
		playerHouses = Math.max(0, count);
		if (playerHouses > before) {
			return addProsperity((playerHouses - before) * VillageProject.HOUSE.prosperity());
		}
		return recompute();
	}

	/** Pontos de obra: obras prontas + casas de jogador + niveis de melhoria. */
	public int buildPoints() {
		return totalBuilt() + playerHouses + VillageBuildings.upgradePoints(this);
	}

	public VillageLevel.Context levelContext() {
		int minNeed = VillageBuildings.needsActive(this) ? VillageNeeds.min(VillageNeeds.values(this)) : -1;
		return new VillageLevel.Context(prosperity, built, planned() ? ringsDone() : -1, playerHouses + VillageBuildings.upgradePoints(this),
				VillageBuildings.bestLevels(this), minNeed);
	}

	/** Niveis das construcoes, melhorias e contagens para as necessidades. */
	public BuildingsState buildingsState() {
		return buildingsState;
	}

	/** Recalcula o nivel (nunca desce). Devolve o nivel novo se subiu (senao null). */
	public VillageLevel recompute() {
		VillageLevel reached = VillageLevel.reached(level, levelContext());
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
		if (day == bountyDay && bounties.size() >= BountyBoard.PER_DAY) {
			return false;
		}
		bountyDay = day;
		bounties.clear();
		// a taverna acrescenta missoes extras a partir do dia seguinte a obra
		bounties.addAll(BountyBoard.roll(seed, day, level, CraftsRules.extraBounties(VillageBuildings.level(this, CraftsBuildings.TAVERN))));
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
		for (int i = 0; i < BountyBoard.MAX_PER_DAY; i++) {
			bountyProgress[i] = i < progress.length ? progress[i] : 0;
			bountyDone[i] = i < done.length && done[i];
		}
		this.queuePaid.clear();
		for (int i = 0; i < this.queue.size(); i++) {
			this.queuePaid.add(new LinkedHashMap<>());
		}
	}

	/** Restaura os itens pagos (formato novo; o antigo nao tem e fica vazio). */
	public void restorePaid(Map<VillageProject, Map<String, Integer>> paid, List<Map<String, Integer>> queuePaid) {
		this.paid.clear();
		paid.forEach((p, m) -> this.paid.put(p, new LinkedHashMap<>(m)));
		this.queuePaid.clear();
		for (int i = 0; i < queue.size(); i++) {
			this.queuePaid.add(i < queuePaid.size() ? new LinkedHashMap<>(queuePaid.get(i)) : new LinkedHashMap<>());
		}
	}

	/** Restaura a terraplenagem (formato novo; o antigo nao tem e comeca vazio). */
	public void restoreTerrain(List<RingWork> works, int stockpile, int playerHouses) {
		java.util.Arrays.fill(rings, null);
		for (RingWork w : works) {
			if (w.ring() >= 1 && w.ring() <= MasterPlan.RINGS) {
				rings[w.ring() - 1] = w;
			}
		}
		this.stockpile = Math.max(0, stockpile);
		this.playerHouses = Math.max(0, playerHouses);
	}

	/** Aneis ja criados (para salvar). */
	public List<RingWork> ringWorks() {
		List<RingWork> out = new ArrayList<>();
		for (RingWork w : rings) {
			if (w != null) {
				out.add(w);
			}
		}
		return out;
	}
}
