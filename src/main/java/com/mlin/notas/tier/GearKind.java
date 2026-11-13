package com.mlin.notas.tier;

/**
 * Tipos de ferramenta/arma de cada nivel, com o dano e a velocidade de ataque base (os mesmos do netherite vanilla).
 * Logica pura.
 */
public enum GearKind {
	SWORD("sword", 3.0F, -2.4F),
	SHOVEL("shovel", 1.5F, -3.0F),
	PICKAXE("pickaxe", 1.0F, -2.8F),
	AXE("axe", 5.0F, -3.0F),
	HOE("hoe", 0.0F, 0.0F),
	SPEAR("spear", 0.0F, 0.0F);

	private final String suffix;
	private final float attackDamageBaseline;
	private final float attackSpeedBaseline;

	GearKind(String suffix, float attackDamageBaseline, float attackSpeedBaseline) {
		this.suffix = suffix;
		this.attackDamageBaseline = attackDamageBaseline;
		this.attackSpeedBaseline = attackSpeedBaseline;
	}

	public String suffix() {
		return suffix;
	}

	/** Id do item vanilla ou do mod: wooden_sword, golden_hoe, cobalt_pickaxe... */
	public String itemName(Tier tier) {
		return itemPrefix(tier) + "_" + suffix;
	}

	/** Prefixo dos ids vanilla de ferramenta (wooden, golden) ou o id do material. */
	public static String itemPrefix(Tier tier) {
		return switch (tier) {
			case WOOD -> "wooden";
			case GOLD -> "golden";
			default -> tier.id();
		};
	}

	public float attackDamageBaseline() {
		return attackDamageBaseline;
	}

	public float attackSpeedBaseline() {
		return attackSpeedBaseline;
	}
}
