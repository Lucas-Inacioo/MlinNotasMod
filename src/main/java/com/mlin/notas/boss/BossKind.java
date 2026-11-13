package com.mlin.notas.boss;

/**
 * Os quatro chefes do mod (logica pura): vida base pensada para 2 jogadores, armadura, dano corpo a corpo, XP, oferenda
 * do altar e item que solta. O Colosso Igneo nao tem altar: dorme no covil e so acorda com a Chave da Forja
 * ({@link #wakeItem()}).
 *
 * <p>Numeros da curva de progressao (~40-50 h para um casal): cada chefe leva 2 a 4 minutos com o equipamento do
 * nivel em que ele aparece (Golem: ferro/ouro; Guardiao: cobalto/diamante; Colosso: netherite; Arauto: endita).</p>
 */
public enum BossKind {
	COBALT_GOLEM("cobalt_golem", 1200.0, 12.0, 0.0, 11.0, 150, "minecraft:iron_block", 4, null, 0, "cobalt_heart", null),
	ABYSSAL_GUARDIAN("abyssal_guardian", 1600.0, 14.0, 2.0, 15.0, 220, "minecraft:echo_shard", 4, null, 0, "abyssal_core", null),
	IGNEOUS_COLOSSUS("igneous_colossus", 1800.0, 16.0, 6.0, 20.0, 300, null, 0, null, 0, "colossus_ember", "mlinnotas:forge_key"),
	ASTRAL_HERALD("astral_herald", 2000.0, 16.0, 6.0, 14.0, 400, "mlinnotas:sentinel_core", 4, "mlinnotas:dragon_scale", 1, "astral_fragment", null);

	private final String id;
	private final double baseHealth;
	private final double armor;
	private final double toughness;
	private final double melee;
	private final int xp;
	private final String offering;
	private final int offeringCount;
	private final String extraOffering;
	private final int extraOfferingCount;
	private final String drop;
	private final String wakeItem;

	BossKind(String id, double baseHealth, double armor, double toughness, double melee, int xp, String offering, int offeringCount,
			String extraOffering, int extraOfferingCount, String drop, String wakeItem) {
		this.id = id;
		this.baseHealth = baseHealth;
		this.armor = armor;
		this.toughness = toughness;
		this.melee = melee;
		this.xp = xp;
		this.offering = offering;
		this.offeringCount = offeringCount;
		this.extraOffering = extraOffering;
		this.extraOfferingCount = extraOfferingCount;
		this.drop = drop;
		this.wakeItem = wakeItem;
	}

	/** Caminho do id da entidade (mlinnotas:&lt;id&gt;). */
	public String id() {
		return id;
	}

	/** Vida maxima com ate 2 jogadores por perto. */
	public double baseHealth() {
		return baseHealth;
	}

	public double armor() {
		return armor;
	}

	public double toughness() {
		return toughness;
	}

	/** Dano do golpe corpo a corpo (atributo de ataque). */
	public double melee() {
		return melee;
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

	/** Segundo item da oferenda (o Arauto pede tambem uma escama de dragao), ou null. */
	public String extraOffering() {
		return extraOffering;
	}

	public int extraOfferingCount() {
		return extraOfferingCount;
	}

	/** Caminho do id do item que o chefe sempre solta (mlinnotas:&lt;drop&gt;). */
	public String drop() {
		return drop;
	}

	/** Item que acorda o chefe adormecido (gasto ao usar), ou null (chefe nao dorme). */
	public String wakeItem() {
		return wakeItem;
	}

	public boolean sleeps() {
		return wakeItem != null;
	}

	public boolean hasAltar() {
		return offering != null;
	}
}
