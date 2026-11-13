package com.mlin.notas.season;

/** As 4 estacoes, na ordem do ano (o mundo comeca na primavera). Logica pura. */
public enum Season {
	SPRING("spring"),
	SUMMER("summer"),
	AUTUMN("autumn"),
	WINTER("winter");

	private final String id;

	Season(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Season next() {
		return values()[(ordinal() + 1) % 4];
	}

	public Season previous() {
		return values()[(ordinal() + 3) % 4];
	}

	/** Estacao pelo id ({@code spring}...), ou null. */
	public static Season byId(String id) {
		for (Season season : values()) {
			if (season.id.equals(id)) {
				return season;
			}
		}
		return null;
	}

	/** Sub-fase da estacao: inicio, meio e fim (cada uma com ~1/3 dos dias). */
	public enum Phase {
		EARLY("early"),
		MID("mid"),
		LATE("late");

		private final String id;

		Phase(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Phase byId(String id) {
			for (Phase phase : values()) {
				if (phase.id.equals(id)) {
					return phase;
				}
			}
			return null;
		}
	}
}
