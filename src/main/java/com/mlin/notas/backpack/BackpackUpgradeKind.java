package com.mlin.notas.backpack;

/** Tipos de upgrade da mochila. Logica pura. */
public enum BackpackUpgradeKind {
	SIZE("size"),
	CRAFTING("crafting"),
	FURNACE("furnace"),
	WATER("water"),
	LAVA("lava");

	private final String name;

	BackpackUpgradeKind(String name) {
		this.name = name;
	}

	/** Id do item: backpack_upgrade_<name>. */
	public String itemName() {
		return "backpack_upgrade_" + name;
	}
}
