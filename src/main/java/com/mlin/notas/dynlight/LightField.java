package com.mlin.notas.dynlight;

import java.util.List;

/**
 * Retrato imutavel das fontes de luz dinamica (logica pura). E lido pelas threads que montam a malha dos chunks, entao
 * nunca muda depois de criado: o cliente troca o retrato inteiro quando as fontes mudam.
 */
public final class LightField {
	public static final LightField EMPTY = new LightField(List.of());

	private final double[] xs;
	private final double[] ys;
	private final double[] zs;
	private final int[] lum;
	private final double minX;
	private final double maxX;
	private final double minY;
	private final double maxY;
	private final double minZ;
	private final double maxZ;

	public LightField(List<DynamicLightRules.Source> sources) {
		int n = sources.size();
		xs = new double[n];
		ys = new double[n];
		zs = new double[n];
		lum = new int[n];
		double x0 = Double.MAX_VALUE;
		double x1 = -Double.MAX_VALUE;
		double y0 = Double.MAX_VALUE;
		double y1 = -Double.MAX_VALUE;
		double z0 = Double.MAX_VALUE;
		double z1 = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			DynamicLightRules.Source s = sources.get(i);
			xs[i] = s.x() + 0.5;
			ys[i] = s.y() + 0.5;
			zs[i] = s.z() + 0.5;
			lum[i] = s.luminance();
			x0 = Math.min(x0, xs[i]);
			x1 = Math.max(x1, xs[i]);
			y0 = Math.min(y0, ys[i]);
			y1 = Math.max(y1, ys[i]);
			z0 = Math.min(z0, zs[i]);
			z1 = Math.max(z1, zs[i]);
		}
		double r = DynamicLightRules.RANGE;
		minX = x0 - r;
		maxX = x1 + r;
		minY = y0 - r;
		maxY = y1 + r;
		minZ = z0 - r;
		maxZ = z1 + r;
	}

	public boolean isEmpty() {
		return lum.length == 0;
	}

	public int size() {
		return lum.length;
	}

	/** Luz dinamica (0..15, com fracao) no centro do bloco (bx, by, bz): a maior entre as fontes. */
	public double levelAt(int bx, int by, int bz) {
		double px = bx + 0.5;
		double py = by + 0.5;
		double pz = bz + 0.5;
		if (lum.length == 0 || px < minX || px > maxX || py < minY || py > maxY || pz < minZ || pz > maxZ) {
			return 0.0;
		}
		double best = 0.0;
		double r = DynamicLightRules.RANGE;
		for (int i = 0; i < lum.length; i++) {
			double dx = xs[i] - px;
			double dy = ys[i] - py;
			double dz = zs[i] - pz;
			if (Math.abs(dx) >= r || Math.abs(dy) >= r || Math.abs(dz) >= r) {
				continue;
			}
			double level = DynamicLightRules.levelAt(Math.sqrt(dx * dx + dy * dy + dz * dz), lum[i]);
			if (level > best) {
				best = level;
			}
		}
		return best;
	}

	/**
	 * Junta a luz dinamica nas coordenadas de lightmap empacotadas ({@code bloco << 4 | ceu << 20}, com a fracao do
	 * bloco nos 4 bits baixos): fica a maior entre a luz de bloco vanilla e a dinamica.
	 */
	public static int applyToPacked(int packed, double dynamicLevel) {
		if (dynamicLevel <= 0.0) {
			return packed;
		}
		int block = packed & 0xFFFF;
		int dynamic = (int) Math.min(240.0, dynamicLevel * 16.0);
		return dynamic > block ? (packed & 0xFFFF0000) | dynamic : packed;
	}
}
