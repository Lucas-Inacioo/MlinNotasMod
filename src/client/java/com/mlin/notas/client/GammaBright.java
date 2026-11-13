package com.mlin.notas.client;

/**
 * Estado do "gammabright" (luz maxima). O mixin em LightmapRenderStateExtractor le {@link #isEnabled()}.
 */
public final class GammaBright {
	/** Valor de brilho injetado no lightmap quando ativo. Bem acima de 1.0 = tudo claro. */
	public static final float BRIGHTNESS = 1000.0F;

	private static boolean enabled;

	private GammaBright() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	public static boolean toggle() {
		enabled = !enabled;
		return enabled;
	}
}
