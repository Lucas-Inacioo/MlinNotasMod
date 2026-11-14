package com.mlin.notas.village;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Terraplenagem de um anel do plano diretor (logica pura): "Terraplenagem do anel N", paga com entregas parciais como
 * os projetos (esmeraldas fixas do anel + 1 terra/pedregulho a cada 4 blocos de aterro que o corte do proprio anel e
 * o estoque da vila nao cobrem). O custo cai junto com o que o jogador ja acertou a mao. Paga: entra na fila e a vila
 * aplaina o anel aos poucos; pronta: os quarteiroes do anel recebem obras e o nivel pode subir.
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
	/** Colunas do anel, ja prontas, blocos de corte e de aterro (ultima conta). */
	private int columns;
	private int ready;
	private int cut;
	private int filled;
	private final Map<String, Integer> paid = new LinkedHashMap<>();

	public RingWork(int ring) {
		this.ring = ring;
		this.emeralds = MasterPlan.RING_EMERALDS[Math.max(0, Math.min(MasterPlan.RINGS - 1, ring - 1))];
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

	public int cut() {
		return cut;
	}

	public int filled() {
		return filled;
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

	/** Custo novo lido do terreno (o preco so muda enquanto aceita entregas). */
	public void setCost(RingCost cost) {
		columns = cost.columns();
		ready = cost.ready();
		cut = cost.cut();
		filled = cost.fill();
		if (state == State.OPEN) {
			emeralds = cost.emeralds();
			fill = cost.fillItems();
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
	public void restore(State state, int emeralds, int fill, int depositedEmeralds, int depositedFill, int columns, int ready, int cut, int filled,
			Map<String, Integer> paid) {
		this.state = state;
		this.emeralds = emeralds;
		this.fill = fill;
		this.depositedEmeralds = depositedEmeralds;
		this.depositedFill = depositedFill;
		this.columns = columns;
		this.ready = ready;
		this.cut = cut;
		this.filled = filled;
		this.paid.clear();
		this.paid.putAll(paid);
	}
}
