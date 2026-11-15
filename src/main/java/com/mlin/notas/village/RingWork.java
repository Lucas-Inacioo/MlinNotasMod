package com.mlin.notas.village;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Terraplenagem de um anel do plano diretor (logica pura): "Terraplenagem do anel N", paga com entregas parciais como
 * os projetos, com o preco fixo de {@link RingCost} (esmeraldas + terra). Paga: entra na fila e a vila leva cada coluna
 * do anel para a altura do campo de alturas aos poucos; pronta: os quarteiroes do anel recebem obras e o nivel pode subir.
 */
public final class RingWork {
	public static final String EMERALD = "minecraft:emerald";
	public static final String FILL = "#mlinnotas:village/fill";
	/** Blocos de aterro pagos por item de terra/pedregulho. */
	public static final int FILL_PER_ITEM = 4;

	public enum State {
		/** Aceita entregas. */
		OPEN,
		/** Paga: esperando ou sendo aplainado. */
		QUEUED,
		/** Pronto. */
		DONE;

		public static State byOrdinal(int i) {
			return values()[Math.max(0, Math.min(values().length - 1, i))];
		}
	}

	private final int ring;
	private State state = State.OPEN;
	private int emeralds;
	private int fill;
	private int depositedEmeralds;
	private int depositedFill;
	/** Andamento da obra: colunas do anel e quantas ja foram aplainadas. */
	private int columns;
	private int ready;
	private final Map<String, Integer> paid = new LinkedHashMap<>();

	public RingWork(int ring) {
		this.ring = ring;
		this.emeralds = RingCost.emeralds(ring);
		this.fill = RingCost.fillItems(ring, 0);
	}

	public int ring() {
		return ring;
	}

	public State state() {
		return state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public boolean done() {
		return state == State.DONE;
	}

	public int emeralds() {
		return emeralds;
	}

	public int fill() {
		return fill;
	}

	public int depositedEmeralds() {
		return depositedEmeralds;
	}

	public int depositedFill() {
		return depositedFill;
	}

	public int columns() {
		return columns;
	}

	public int ready() {
		return ready;
	}

	public Map<String, Integer> paid() {
		return Map.copyOf(paid);
	}

	/** Custo como lista de entregas (para a tela e a entrega). */
	public List<VillageCost> costs() {
		return fill > 0 ? List.of(VillageCost.of(EMERALD, emeralds), VillageCost.of(FILL, fill)) : List.of(VillageCost.of(EMERALD, emeralds));
	}

	public int deposited(String id) {
		return id.equals(EMERALD) ? depositedEmeralds : id.equals(FILL) ? depositedFill : 0;
	}

	public int remaining(String id) {
		int need = id.equals(EMERALD) ? emeralds : id.equals(FILL) ? fill : 0;
		return Math.max(0, need - deposited(id));
	}

	/** Tudo entregue (custo zero conta). */
	public boolean paidOff() {
		return remaining(EMERALD) == 0 && remaining(FILL) == 0;
	}

	public double fraction() {
		int total = emeralds + fill;
		int done = Math.min(emeralds, depositedEmeralds) + Math.min(fill, depositedFill);
		return total == 0 ? 1 : (double) done / total;
	}

	/**
	 * Preco da tabela fixa ({@link RingCost}) com o credito da pedreira da vila em blocos de aterro; so enquanto aceita
	 * entregas (pago, o preco nao muda mais).
	 */
	public void reprice(int quarryCreditBlocks) {
		if (state == State.OPEN) {
			emeralds = RingCost.emeralds(ring);
			fill = RingCost.fillItems(ring, quarryCreditBlocks);
		}
	}

	/** Andamento da obra (colunas aplainadas de quantas), mostrado na tela. */
	public void setProgress(int done, int total) {
		ready = done;
		columns = total;
	}

	/** Entrega do que falta ({@code available} = quanto o jogador tem de cada id). Devolve o que tirar. */
	public Map<String, Integer> deposit(Map<String, Integer> available) {
		Map<String, Integer> taken = new LinkedHashMap<>();
		if (state != State.OPEN) {
			return taken;
		}
		for (String id : List.of(EMERALD, FILL)) {
			int give = Math.min(remaining(id), Math.max(0, available.getOrDefault(id, 0)));
			if (give > 0) {
				taken.put(id, give);
				if (id.equals(EMERALD)) {
					depositedEmeralds += give;
				} else {
					depositedFill += give;
				}
			}
		}
		return taken;
	}

	public void recordPaid(Map<String, Integer> items) {
		items.forEach((id, n) -> paid.merge(id, n, Integer::sum));
	}

	/** Restaura do arquivo. */
	public void restore(State state, int emeralds, int fill, int depositedEmeralds, int depositedFill, int columns, int ready,
			Map<String, Integer> paid) {
		this.state = state;
		this.emeralds = emeralds;
		this.fill = fill;
		this.depositedEmeralds = depositedEmeralds;
		this.depositedFill = depositedFill;
		this.columns = columns;
		this.ready = ready;
		this.paid.clear();
		this.paid.putAll(paid);
	}
}
