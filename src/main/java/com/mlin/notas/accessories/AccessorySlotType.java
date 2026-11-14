package com.mlin.notas.accessories;

/** Tipo de encaixe de acessorio. Logica pura. */
public enum AccessorySlotType {
	RING("ring"),
	NECKLACE("necklace"),
	BELT("belt"),
	GLOVES("gloves"),
	CHARM("charm");

	private final String id;

	AccessorySlotType(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}
}
