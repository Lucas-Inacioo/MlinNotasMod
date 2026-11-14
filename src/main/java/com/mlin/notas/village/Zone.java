package com.mlin.notas.village;

import java.util.Locale;

/**
 * Zona do plano diretor (logica pura), pelo anel: centro civico e comercial (aneis 1-2), bairros de casas e oficios
 * (anel 3), arredores rurais, criacao e quartel (anel 5) e a faixa encostada na muralha externa (anel 6, torres e
 * portoes). O anel 4 e a faixa da muralha de pedra (sem lotes).
 */
public enum Zone {
	CENTER, DISTRICT, OUTSKIRTS, WALL;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Zona dos lotes do anel (1..6); null = anel sem lotes. */
	public static Zone ofRing(int ring) {
		return switch (ring) {
			case 1, 2 -> CENTER;
			case 3 -> DISTRICT;
			case 5 -> OUTSKIRTS;
			case 6 -> WALL;
			default -> null;
		};
	}

	public static Zone byOrdinal(int i) {
		return values()[Math.max(0, Math.min(values().length - 1, i))];
	}
}
