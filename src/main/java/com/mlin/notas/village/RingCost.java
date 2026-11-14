package com.mlin.notas.village;

/**
 * Conta da terraplenagem de um anel (logica pura): quantas colunas ele tem, quantas ja estao na altura (trabalho do
 * jogador inclusive), quantos blocos sobram para cortar e faltam para aterrar, e o preco: esmeraldas fixas do anel
 * proporcionais ao que falta e 1 terra/pedregulho a cada {@link RingWork#FILL_PER_ITEM} blocos de aterro que o corte
 * e o estoque da vila nao cobrem. Colunas de jogador ficam de fora (nunca sao mexidas).
 */
public record RingCost(int columns, int ready, int cut, int fill, int emeralds, int fillItems) {
	/** Preco do anel {@code ring} com o terreno de agora. */
	public static RingCost of(MasterPlan plan, int ring, Terrain terrain, int stockpile) {
		int columns = 0;
		int ready = 0;
		int cut = 0;
		int fill = 0;
		for (long col : plan.columns(ring)) {
			int x = VillageLayout.colX(col);
			int z = VillageLayout.colZ(col);
			int h = plan.plannedHeight(x, z);
			boolean street = plan.ground(x, z);
			if (!street && terrain.playerBuilt(x, z)) {
				continue;
			}
			columns++;
			int bed = terrain.bed(x, z);
			int water = terrain.water(x, z);
			if (bed == h && water == 0) {
				ready++;
				continue;
			}
			cut += Math.max(0, bed - h);
			fill += Math.max(0, h - bed);
		}
		int base = MasterPlan.RING_EMERALDS[Math.max(0, Math.min(MasterPlan.RINGS - 1, ring - 1))];
		int unready = columns - ready;
		int emeralds = unready <= 0 ? 0 : Math.max(1, (int) Math.ceil((double) base * unready / columns));
		int items = (int) Math.ceil(Math.max(0, fill - cut - stockpile) / (double) RingWork.FILL_PER_ITEM);
		return new RingCost(columns, ready, cut, fill, emeralds, items);
	}

	/** Estoque de terra da vila depois de aplainar o anel (sobra do corte paga aterro de outros aneis). */
	public int stockpileAfter(int stockpile) {
		return Math.max(0, stockpile + cut - fill);
	}
}
