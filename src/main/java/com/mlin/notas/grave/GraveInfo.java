package com.mlin.notas.grave;

/**
 * Uma lapide no mundo (logica pura): dono, posicao e resumo. Fica no {@code GraveData} e vai para os clientes
 * (minimapa, mapa grande).
 *
 * @param owner UUID do dono em texto
 * @param ownerName nome do dono quando morreu
 * @param dimension id da dimensao ({@code minecraft:overworld})
 * @param items quantidade de pilhas guardadas
 * @param xp pontos de experiencia guardados
 * @param day dia do jogo (1 = primeiro dia) em que morreu
 */
public record GraveInfo(String owner, String ownerName, String dimension, int x, int y, int z, int items, int xp, long day) {
	/** Chave unica por posicao. */
	public String key() {
		return key(dimension, x, y, z);
	}

	public static String key(String dimension, int x, int y, int z) {
		return dimension + "@" + x + "," + y + "," + z;
	}

	public GraveInfo withContents(int newItems, int newXp) {
		return new GraveInfo(owner, ownerName, dimension, x, y, z, newItems, newXp, day);
	}
}
