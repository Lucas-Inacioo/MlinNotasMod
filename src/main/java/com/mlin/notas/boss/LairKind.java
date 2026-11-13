package com.mlin.notas.boss;

/**
 * Covis de chefe gerados no mundo (logica pura). {@code enclosed} = cavado na rocha (fora do plano e tudo solido);
 * senao fica ao ar livre, com chao abaixo de y=0.
 */
public enum LairKind {
	/** Santuario de pedras em pe com o altar do Golem de Cobalto (Overworld, superficie). */
	COBALT_SHRINE("cobalt_shrine", BossKind.COBALT_GOLEM, false),
	/** Salao cavado no Deep Dark (abaixo de y=0) com o altar do Guardiao das Profundezas. */
	ABYSSAL_SANCTUM("abyssal_sanctum", BossKind.ABYSSAL_GUARDIAN, true),
	/** Arena de basalto nos Picos de Infernita onde o Colosso Igneo dorme. */
	COLOSSUS_FORGE("colossus_forge", BossKind.IGNEOUS_COLOSSUS, false);

	private final String id;
	private final BossKind boss;
	private final boolean enclosed;

	LairKind(String id, BossKind boss, boolean enclosed) {
		this.id = id;
		this.boss = boss;
		this.enclosed = enclosed;
	}

	public String id() {
		return id;
	}

	public BossKind boss() {
		return boss;
	}

	public boolean enclosed() {
		return enclosed;
	}

	public static LairKind byName(String name) {
		for (LairKind kind : values()) {
			if (kind.id.equals(name)) {
				return kind;
			}
		}
		return COBALT_SHRINE;
	}
}
