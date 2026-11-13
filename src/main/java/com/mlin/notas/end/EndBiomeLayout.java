package com.mlin.notas.end;

/**
 * Layout dos biomas das ilhas externas do End em regioes grandes (logica pura, sem Minecraft).
 * <p>
 * O plano e dividido em celulas de {@link #CELL} blocos com um ponto sorteado em cada uma (Voronoi com grade
 * tremida): cada ponto do mundo pertence a regiao do ponto mais proximo. Antes da busca a coordenada e deslocada
 * por um ruido suave ({@link #WARP} blocos), entao as bordas ficam onduladas em vez de retas. Cada regiao sorteia
 * uma {@link Family}; a altura da ilha (classificacao vanilla terras altas / medias / baldias) escolhe entre o
 * bioma do centro e o da beirada ({@link #biome}).
 */
public final class EndBiomeLayout {
	/** Lado da celula da grade: regioes com ~400 blocos de diametro. */
	public static final int CELL = 400;
	/** Amplitude do deslocamento das bordas. */
	public static final double WARP = 70.0;
	/** Escala do ruido que ondula as bordas. */
	public static final double WARP_SCALE = 160.0;
	/** Distancia da origem (em blocos) a partir da qual o layout vale: so as ilhas externas. */
	public static final int MIN_DISTANCE = 1000;

	/** Familia de biomas de uma regiao. */
	public enum Family {
		MOSS_FIELDS, CRYSTAL_GROVE, CHORUS_FOREST, HIGHLANDS
	}

	/** Classificacao de altura que o End vanilla ja calcula. */
	public enum Height {
		HIGHLANDS, MIDLANDS, BARRENS
	}

	/** Bioma final. */
	public enum Biome {
		MOSS_FIELDS, CRYSTAL_GROVE, CHORUS_FOREST, VOID_WASTES, END_HIGHLANDS, END_MIDLANDS, END_BARRENS
	}

	private final long seed;

	public EndBiomeLayout(long seed) {
		this.seed = seed;
	}

	public long seed() {
		return seed;
	}

	/** Se o layout se aplica nessa coluna (longe da ilha principal). */
	public static boolean applies(int x, int z) {
		return (long) x * x + (long) z * z > (long) MIN_DISTANCE * MIN_DISTANCE;
	}

	/** Bioma de uma familia numa altura: centro (terras altas/medias) ou beirada (baldias). */
	public static Biome biome(Family family, Height height) {
		if (family == Family.HIGHLANDS) {
			return switch (height) {
				case HIGHLANDS -> Biome.END_HIGHLANDS;
				case MIDLANDS -> Biome.END_MIDLANDS;
				case BARRENS -> Biome.END_BARRENS;
			};
		}
		if (height == Height.BARRENS) {
			return Biome.VOID_WASTES;
		}
		return switch (family) {
			case MOSS_FIELDS -> Biome.MOSS_FIELDS;
			case CRYSTAL_GROVE -> Biome.CRYSTAL_GROVE;
			default -> Biome.CHORUS_FOREST;
		};
	}

	public Biome biomeAt(int x, int z, Height height) {
		return biome(familyAt(x, z), height);
	}

	public Family familyAt(int x, int z) {
		return familyOf(regionAt(x, z));
	}

	/** Familia de uma regiao (id de {@link #regionAt}). */
	public Family familyOf(long region) {
		Family[] families = Family.values();
		return families[(int) Long.remainderUnsigned(mix(region ^ seed * 0x9E3779B97F4A7C15L), families.length)];
	}

	/** Id da regiao (celula do ponto mais proximo, empacotada em um long) que contem a coluna. */
	public long regionAt(int x, int z) {
		double wx = x + WARP * noise(x / WARP_SCALE, z / WARP_SCALE, seed ^ 0x51ED270BL);
		double wz = z + WARP * noise(x / WARP_SCALE, z / WARP_SCALE, seed ^ 0x2545F491L);
		int cx = Math.floorDiv((int) Math.floor(wx), CELL);
		int cz = Math.floorDiv((int) Math.floor(wz), CELL);
		long best = 0;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int gx = cx + dx;
				int gz = cz + dz;
				long h = mix(seed + gx * 0x632BE59BD9B4E019L + gz * 0x85157AF5L);
				// ponto dentro dos 80% centrais da celula (celulas nao degeneram)
				double px = (gx + 0.1 + 0.8 * ((h & 0xFFFF) / 65535.0)) * CELL;
				double pz = (gz + 0.1 + 0.8 * (((h >>> 16) & 0xFFFF) / 65535.0)) * CELL;
				double d = (px - wx) * (px - wx) + (pz - wz) * (pz - wz);
				if (d < bestDist) {
					bestDist = d;
					best = ((long) gx << 32) | (gz & 0xFFFFFFFFL);
				}
			}
		}
		return best;
	}

	/** Ruido de valor suave em [-1, 1] com duas oitavas. */
	static double noise(double x, double z, long salt) {
		return 0.67 * valueNoise(x, z, salt) + 0.33 * valueNoise(x * 2.03 + 17.1, z * 2.03 - 9.4, salt * 31 + 7);
	}

	private static double valueNoise(double x, double z, long salt) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fz = smooth(z - z0);
		double a = lerp(fx, corner(x0, z0, salt), corner(x0 + 1, z0, salt));
		double b = lerp(fx, corner(x0, z0 + 1, salt), corner(x0 + 1, z0 + 1, salt));
		return lerp(fz, a, b);
	}

	private static double corner(int x, int z, long salt) {
		return (mix(salt + x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL) >>> 11) / (double) (1L << 53) * 2.0 - 1.0;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}

	/** SplitMix64. */
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
