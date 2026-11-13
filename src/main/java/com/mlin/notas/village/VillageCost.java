package com.mlin.notas.village;

/** Parte de um custo: id de item ({@code minecraft:bread}) ou tag ({@code #minecraft:planks}) e quantidade. */
public record VillageCost(String id, int count) {
	public static VillageCost of(String id, int count) {
		return new VillageCost(id, count);
	}

	public boolean isTag() {
		return id.startsWith("#");
	}
}
