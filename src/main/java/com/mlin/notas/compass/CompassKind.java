package com.mlin.notas.compass;

/** Os dois tipos de bussola do mod (logica pura). */
public enum CompassKind {
	BIOME("biome"),
	STRUCTURE("structure");

	private final String id;

	CompassKind(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static CompassKind byId(String id) {
		for (CompassKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return BIOME;
	}
}
