package com.mlin.notas.skills;

/**
 * Ramos da arvore de habilidades (logica pura). Mineracao e o tronco (12 niveis = os niveis de {@code Tier}, comeca em
 * Madeira = 1); os ramos laterais tem 5 niveis e comecam em 0.
 */
public enum SkillBranch {
	MINING("mining", 12, 1, "minecraft:wooden_pickaxe"),
	COMBAT("combat", 5, 0, "minecraft:iron_sword"),
	VIGOR("vigor", 5, 0, "minecraft:golden_apple"),
	AGILITY("agility", 5, 0, "minecraft:feather"),
	PROSPECTING("prospecting", 5, 0, "minecraft:raw_gold");

	private final String id;
	private final int maxLevel;
	private final int startLevel;
	private final String icon;

	SkillBranch(String id, int maxLevel, int startLevel, String icon) {
		this.id = id;
		this.maxLevel = maxLevel;
		this.startLevel = startLevel;
		this.icon = icon;
	}

	public String id() {
		return id;
	}

	public int maxLevel() {
		return maxLevel;
	}

	/** Nivel de um mundo novo (Mineracao: Madeira = 1, ramos laterais: 0). */
	public int startLevel() {
		return startLevel;
	}

	/** Item que representa o ramo na tela. */
	public String icon() {
		return icon;
	}

	public static SkillBranch byId(String id) {
		for (SkillBranch branch : values()) {
			if (branch.id.equals(id)) {
				return branch;
			}
		}
		return null;
	}
}
