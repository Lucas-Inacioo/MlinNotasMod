package com.mlin.notas.village;

/**
 * Terreno em volta da vila, visto pelo planejador (logica pura). Coordenadas x/z relativas ao centro; y absoluto. A
 * ponte com o jogo amostra o mundo; os testes usam mapas falsos.
 */
public interface Terrain {
	/** Y do bloco do topo do chao natural (grama, terra, areia, pedra...). */
	int ground(int x, int z);

	/**
	 * Da para construir na coluna: o topo e chao natural, nao ha agua e acima so ha ar/plantas/folhas soltas. Coluna
	 * com qualquer bloco de jogador ou de outra estrutura (tabuas, caminho, tocha, bau...) nunca e livre.
	 */
	boolean free(int x, int z);
}
