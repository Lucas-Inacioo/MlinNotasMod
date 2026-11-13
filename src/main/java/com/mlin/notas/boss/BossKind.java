package com.mlin.notas.boss;

/**
 * Os quatro chefes do mod (logica pura): vida base pensada para 2 jogadores, XP, oferenda do altar e item que solta.
 * O Colosso Igneo nao tem altar: dorme no covil e acorda quando alguem chega a {@link #wakeRadius()} blocos.
 */
public enum BossKind {
	COBALT_GOLEM("cobalt_golem", 220.0, 150, "minecraft:iron_block", 4, "cobalt_heart", 0),
	ABYSSAL_GUARDIAN("abyssal_guardian", 300.0, 220, "minecraft:echo_shard", 4, "abyssal_core", 0),
	IGNEOUS_COLOSSUS("igneous_colossus", 400.0, 300, null, 0, "colossus_ember", 16),
	ASTRAL_HERALD("astral_herald", 360.0, 400, "mlinnotas:sentinel_core", 4, "astral_fragment", 0);

	private final String id;
	private final double baseHealth;
	private final int xp;
	private final String offering;
	private final int offeringCount;
	private final String drop;
	private final int wakeRadius;

	BossKind(String id, double baseHealth, int xp, String offering, int offeringCount, String drop, int wakeRadius) {
		this.id = id;
		this.baseHealth = baseHealth;
		this.xp = xp;
		this.offering = offering;
		this.offeringCount = offeringCount;
		this.drop = drop;
		this.wakeRadius = wakeRadius;
	}

	/** Caminho do id da entidade (mlinnotas:&lt;id&gt;). */
	public String id() {
		return id;
	}

	/** Vida maxima com ate 2 jogadores por perto. */
	public double baseHealth() {
		return baseHealth;
	}

	public int xp() {
		return xp;
	}

	/** Id do item consumido no altar, ou null (sem altar). */
	public String offering() {
		return offering;
	}

	public int offeringCount() {
		return offeringCount;
	}

	/** Caminho do id do item que o chefe sempre solta (mlinnotas:&lt;drop&gt;). */
	public String drop() {
		return drop;
	}

	/** Raio em que o chefe adormecido acorda (0 = nao dorme). */
	public int wakeRadius() {
		return wakeRadius;
	}

	public boolean hasAltar() {
		return offering != null;
	}
}
