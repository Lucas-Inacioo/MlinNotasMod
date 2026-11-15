package com.mlin.notas.village;

/**
 * Aldeoes ficam na vila e voltam para casa de noite (logica pura; Vilas 3.0). Coordenadas relativas ao quadro; {@code half}
 * = meia-largura da area (quadrado do plano diretor). De dia quem esta a mais de {@link #DAY_MARGIN} da area volta; do
 * anoitecer ({@link #DUSK}) quem esta a mais de {@link #BED_FAR} da cama anda ate ela; quem nao tem cama na vila e
 * ainda esta fora vai ao abrigo mais perto (ou ao interior da area, sem abrigo).
 */
public final class VillagerCurfew {
	public static final int DUSK = 11000;
	public static final int DAY_MARGIN = 16;
	public static final int BED_FAR = 24;
	/** Folga para dentro da borda ao voltar (o alvo fica a {@code half - INSET}). */
	public static final int INSET = 8;

	public enum Action {
		NONE, RETURN, BED, SHELTER
	}

	/** O que fazer e para onde (tx, tz relativos; so valem quando {@code action != NONE}). */
	public record Decision(Action action, int tx, int tz) {
		public static final Decision NONE = new Decision(Action.NONE, 0, 0);
	}

	private VillagerCurfew() {
	}

	/** Do anoitecer ate o amanhecer. */
	public static boolean night(long dayTime) {
		return Math.floorMod(dayTime, 24000L) >= DUSK;
	}

	/** Quanto a posicao passa da borda da area (0 = dentro), em distancia de xadrez. */
	public static int outside(int rx, int rz, int half) {
		return Math.max(0, Math.max(Math.abs(rx), Math.abs(rz)) - half);
	}

	/** A cama so conta se estiver dentro da area da vila. */
	public static boolean homeValid(int bedRx, int bedRz, int half) {
		return outside(bedRx, bedRz, half) == 0;
	}

	/** Ponto dentro da area mais perto de (rx, rz), recuado {@link #INSET} da borda. */
	public static int[] inward(int rx, int rz, int half) {
		int lim = Math.max(1, half - INSET);
		return new int[] {Math.max(-lim, Math.min(lim, rx)), Math.max(-lim, Math.min(lim, rz))};
	}

	/**
	 * @param bed cama (x, z relativos) ou null; ignorada se estiver fora da area
	 * @param shelter abrigo mais perto (x, z) ou null
	 */
	public static Decision decide(long dayTime, int rx, int rz, int half, int[] bed, int[] shelter) {
		int out = outside(rx, rz, half);
		boolean haveBed = bed != null && homeValid(bed[0], bed[1], half);
		if (!night(dayTime)) {
			if (out > DAY_MARGIN) {
				int[] p = inward(rx, rz, half);
				return new Decision(Action.RETURN, p[0], p[1]);
			}
			return Decision.NONE;
		}
		if (haveBed) {
			double d = Math.hypot(bed[0] - rx, bed[1] - rz);
			return d > BED_FAR ? new Decision(Action.BED, bed[0], bed[1]) : Decision.NONE;
		}
		if (out > 0) {
			if (shelter != null) {
				return new Decision(Action.SHELTER, shelter[0], shelter[1]);
			}
			int[] p = inward(rx, rz, half);
			return new Decision(Action.RETURN, p[0], p[1]);
		}
		return Decision.NONE;
	}
}
