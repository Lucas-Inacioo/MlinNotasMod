package com.mlin.notas.village.gen;

import com.mlin.notas.village.HeightField;

/**
 * Regras puras da geracao do terreno da vila inicial (Vilas 3.0 fase 2): como a densidade do gerador vira o chao do
 * {@link HeightField}, que altura de superficie o aquifero e as regras de superficie enxergam, que decoracoes e
 * estruturas podem nascer em cada coluna. Sem nada do Minecraft: {@code VillageBlendFunction} e os mixins so chamam
 * daqui.
 *
 * <h2>Densidade</h2>
 * Densidade &gt; 0 = solido (convencao do gerador). Numa coluna do campo com altura {@code H}, profundidade fechada
 * {@code D}, chao natural {@code N} (superficie preliminar do gerador, sem cavernas) e densidade natural {@code d}:
 * <ul>
 * <li>acima de {@code H}: ar ({@code -(y - H - 0.5) * SCALE}, ate -1) - morro natural acima do plano e cortado;</li>
 * <li>de {@code H - D + 1} ate {@code H}: solido sempre - nenhuma boca de caverna nem agua de aquifero perto da
 * superficie da vila;</li>
 * <li>abaixo disso: o natural onde o natural ja era chao ({@code y <= N}: cavernas fundas ficam), solido onde era ceu
 * (aterro de vale nunca fica oco);</li>
 * </ul>
 * e o resultado e {@code d + (alvo - d) * alpha(peso)}: dentro do plano e na maior parte da faixa {@code alpha = 1} (o
 * chao e exatamente o campo, cuja altura na faixa ja e a mistura suave com o natural); so no terco de fora da faixa,
 * onde o campo ja quase encostou no natural, a densidade se mistura com a natural para devolver as formas 3D (beirais,
 * bocas de caverna) sem corte seco na borda.
 */
public final class VillageGenRules {
	/** Inclinacao da densidade alvo por bloco (mesma ordem de grandeza da natural perto da superficie). */
	public static final float SCALE = 0.08F;
	/** Peso abaixo do qual a densidade comeca a se misturar com a natural (alpha = peso / isto, ate 1). */
	public static final float ALPHA_BAND = 0.35F;
	/** Folga abaixo da camada fechada em que decoracoes que mexem no chao (lagos, fontes, geodos) tambem sao barradas. */
	public static final int FEATURE_MARGIN = 8;
	/** Fracao das decoracoes grandes que some na faixa colada no plano (peso 1); cai com o peso ate 0 na borda. */
	public static final float BAND_THINNING = 0.85F;

	private VillageGenRules() {
	}

	/** Quanto a coluna segue o campo (0..1) pelo peso dele e pelo tipo (area recortada fica natural). */
	public static float alpha(float weight, HeightField.Kind kind) {
		if (kind == HeightField.Kind.KEEP || weight <= 0) {
			return 0;
		}
		return Math.min(1.0F, weight / ALPHA_BAND);
	}

	/** Densidade alvo do campo no bloco (ver a classe). */
	public static float target(int y, int height, int depth, int naturalSurface, float natural) {
		if (y > height) {
			return Math.max(-1.0F, -(y - height - 0.5F) * SCALE);
		}
		float solid = Math.min(1.0F, (height + 0.5F - y) * SCALE);
		if (y > height - depth) {
			return solid;
		}
		return y <= naturalSurface ? natural : Math.max(natural, solid);
	}

	/** Mistura linear da natural com o alvo. */
	public static float blend(float natural, float target, float alpha) {
		return natural + (target - natural) * alpha;
	}

	/** Densidade final do bloco. */
	public static float density(int y, int height, int depth, int naturalSurface, float natural, float alpha) {
		if (alpha <= 0) {
			return natural;
		}
		return blend(natural, target(y, height, depth, naturalSurface, natural), alpha);
	}

	/** Superficie preliminar que o aquifero e as regras de superficie enxergam (a do campo, misturada como a densidade). */
	public static float surface(float natural, int height, float alpha) {
		return alpha <= 0 ? natural : natural + (height - natural) * alpha;
	}

	// ---------------------------------------------------------------- decoracao

	/** Classe de uma decoracao (feature) para a regra de onde ela pode nascer. */
	public enum FeatureClass {
		/** Varre o chunk inteiro (neve e gelo do topo): sempre. */
		CHUNK_WIDE,
		/** Minerio: so troca pedra, pode em qualquer lugar embaixo do chao. */
		ORE,
		/** Uma planta solta (grama, flor): pode no quarteirao e na faixa da muralha, nao na rua/praca. */
		GROUND_COVER,
		/** Mexe no chao ou no relevo (lago, fonte, geodo, disco de areia...): barrada perto da superficie de todo o campo. */
		TERRAIN,
		/** Resto: arvores, pedregulhos, aboboras, troncos caidos, cogumelos gigantes... */
		OTHER
	}

	/**
	 * A decoracao com origem em {@code y} pode nascer na coluna? Fora do campo ({@code kind} NATURAL) sempre. Na faixa de
	 * transicao, tudo menos o que mexe no chao perto da superficie. Dentro do plano, perto da superficie, so plantas soltas
	 * em quarteirao/faixa livre; rua, praca, portao e caminho ficam limpos. Bem abaixo da superficie, tudo.
	 */
	public static boolean allowsFeature(HeightField.Kind kind, FeatureClass cls, int y, int height, int depth) {
		return allowsFeature(kind, cls, y, height, depth, 0, 1);
	}

	/**
	 * Como o de cima, com o rareamento da faixa: arvores e outras decoracoes grandes na faixa nascem so se {@code roll}
	 * (0..1, sorteio fixo pela posicao) passar de {@code weight * BAND_THINNING}, entao a mata vai rareando ate a borda do
	 * plano em vez de parar num paredao.
	 */
	public static boolean allowsFeature(HeightField.Kind kind, FeatureClass cls, int y, int height, int depth, float weight, float roll) {
		if (kind == HeightField.Kind.NATURAL || kind == HeightField.Kind.KEEP || cls == FeatureClass.CHUNK_WIDE || cls == FeatureClass.ORE) {
			return true;
		}
		if (y < height - depth - FEATURE_MARGIN) {
			return true;
		}
		if (cls == FeatureClass.TERRAIN) {
			return false;
		}
		if (kind == HeightField.Kind.BAND && cls == FeatureClass.OTHER) {
			return roll >= weight * BAND_THINNING;
		}
		if (kind == HeightField.Kind.BAND || kind == HeightField.Kind.WATER) {
			return true;
		}
		if (cls == FeatureClass.GROUND_COVER) {
			return kind == HeightField.Kind.BLOCK || kind == HeightField.Kind.FREE;
		}
		return false;
	}

	/** O carver (caverna, canion) pode abrir o bloco? Nunca na camada fechada (nem acima dela). */
	public static boolean allowsCarve(int y, int height, int depth, float alpha) {
		if (alpha <= 0 || height == Integer.MIN_VALUE || depth <= 0) {
			return true;
		}
		return y <= height - depth;
	}

	// ---------------------------------------------------------------- estruturas

	/**
	 * Uma estrutura cuja caixa cruza o campo pode nascer? De superficie, nunca (nem na faixa). Subterranea so se o topo da
	 * caixa fica abaixo da camada fechada mais funda sob ela ({@code minClosedFloor}: menor {@code H - D} do campo na
	 * pegada da caixa).
	 */
	public static boolean allowsStructure(boolean underground, int boxMaxY, int minClosedFloor) {
		return underground && boxMaxY < minClosedFloor;
	}
}
