package com.mlin.notas.minimap;

/**
 * Sombreamento de cores do mapa. Logica pura (sem Minecraft), cores em ARGB.
 */
public final class MapColorShading {
	/** Fator aplicado quando a coluna e mais alta que a vizinha ao norte. */
	public static final float HIGHER = 1.15F;
	/** Fator aplicado quando a coluna e mais baixa que a vizinha ao norte. */
	public static final float LOWER = 0.78F;
	/** Fator de colunas com a mesma altura da vizinha. */
	public static final float FLAT = 1.0F;

	private MapColorShading() {
	}

	/** Clareia colunas mais altas que o vizinho ao norte e escurece as mais baixas (estilo mapa vanilla). */
	public static int shadeByHeight(int argb, int height, int northHeight) {
		if (height > northHeight) {
			return scale(argb, HIGHER);
		}
		if (height < northHeight) {
			return scale(argb, LOWER);
		}
		return scale(argb, FLAT);
	}

	/** Agua: quanto mais funda, mais escura. {@code depth} em blocos (>= 1). */
	public static int waterByDepth(int waterArgb, int depth) {
		float factor = Math.max(0.35F, 1.0F - Math.max(0, depth - 1) * 0.05F);
		return scale(waterArgb, factor);
	}

	/** Modo caverna: blocos mais abaixo dos olhos do jogador ficam mais escuros. */
	public static int caveByDepth(int argb, int depthBelowEye) {
		float factor = clamp(1.0F - Math.max(0, depthBelowEye) * 0.035F, 0.3F, 1.0F);
		return scale(argb, factor);
	}

	/** Multiplica os canais RGB por {@code factor}, mantendo alpha e saturando em 255. */
	public static int scale(int argb, float factor) {
		int a = (argb >>> 24) & 0xFF;
		int r = clamp255(Math.round(((argb >> 16) & 0xFF) * factor));
		int g = clamp255(Math.round(((argb >> 8) & 0xFF) * factor));
		int b = clamp255(Math.round((argb & 0xFF) * factor));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Converte RGB opaco (sem alpha) em ARGB opaco. */
	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/** Converte ARGB em ABGR (formato do NativeImage do Minecraft). */
	public static int argbToAbgr(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}
}
