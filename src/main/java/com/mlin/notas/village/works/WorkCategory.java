package com.mlin.notas.village.works;

import java.util.Locale;

/**
 * Categorias de obra publica (o quadro liga e desliga cada uma). {@link #FACADES} fica reservada para as fases das
 * construcoes novas: ainda nao ha obra nela, entao a tela nao mostra o botao.
 */
public enum WorkCategory {
	STREETS, PLAZA, GREEN, DECOR, SUBDIVISION, RURAL, BRIDGES, ROADS, WALL, FACADES;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public int bit() {
		return 1 << ordinal();
	}

	public static WorkCategory byOrdinal(int i) {
		return values()[Math.max(0, Math.min(values().length - 1, i))];
	}

	public static WorkCategory byId(String id) {
		for (WorkCategory c : values()) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}
}
