package com.mlin.notas.village;

/**
 * Terreno em volta da vila, visto pelo planejador (logica pura). Coordenadas x/z relativas ao centro; y absoluto. A
 * ponte com o jogo amostra o mundo; os testes usam mapas falsos.
 */
public interface Terrain {
	/** Y do bloco do topo do chao natural (grama, terra, areia, pedra...). Com agua em cima: o topo da agua. */
	int ground(int x, int z);

	/**
	 * Da para construir na coluna: o topo e chao natural, nao ha agua e acima so ha ar/plantas/folhas soltas. Coluna
	 * com qualquer bloco de jogador ou de outra estrutura (tabuas, caminho, tocha, bau...) nunca e livre.
	 */
	boolean free(int x, int z);

	/** Blocos de agua em cima do fundo (0 = seco). O fundo solido fica em {@code ground - water}. */
	default int water(int x, int z) {
		return 0;
	}

	/** A coluna tem algo colocado por jogador ou por outra estrutura (nao e chao natural nem agua). */
	default boolean playerBuilt(int x, int z) {
		return !free(x, z) && water(x, z) == 0;
	}

	/** Topo do chao solido (fundo, se tiver agua em cima). */
	default int bed(int x, int z) {
		return ground(x, z) - water(x, z);
	}
}
