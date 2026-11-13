package com.mlin.notas.season;

/**
 * Clima e cores por data, so para biomas temperados. Logica pura.
 *
 * <p>Temperatura: um deslocamento somado a temperatura do bioma (o vanilla neva abaixo de 0,15). No meio do inverno
 * -0,8 leva planicies (0,8) a 0, entao chuva vira neve, a agua congela e camadas de neve se acumulam; na primavera o
 * deslocamento volta a 0 e a neve derrete. Nunca e positivo: neve natural das montanhas nao derrete.
 *
 * <p>Cores: a cor vanilla da grama/folhagem e misturada com um alvo por fase (primavera viva, verao vanilla, outono
 * amarelo -> laranja -> marrom, inverno cinza-amarronzado).
 */
public final class SeasonVisuals {
	/** Alvo e quantidade da mistura de cor da grama e da folhagem numa fase. */
	public record Tint(int grassTarget, float grassAmount, int foliageTarget, float foliageAmount) {
	}

	private static final Tint NONE = new Tint(0, 0, 0, 0);
	/** Indice = estacao * 3 + fase ({@link SeasonDate#visualKey()}). */
	private static final Tint[] TINTS = {
			// primavera: verde vivo, mais forte no comeco
			new Tint(0x7CD34A, 0.25F, 0x68CF3C, 0.25F),
			new Tint(0x6FD040, 0.18F, 0x5FC83A, 0.15F),
			new Tint(0x6FD040, 0.1F, 0x5FC83A, 0.08F),
			// verao: vanilla, amarelando no fim
			NONE,
			NONE,
			new Tint(0xB8B04A, 0.12F, 0xA8A040, 0.12F),
			// outono: amarelo -> laranja/vermelho -> marrom
			new Tint(0xB8A84A, 0.28F, 0xD8A42A, 0.45F),
			new Tint(0xB89040, 0.45F, 0xE06A1C, 0.78F),
			new Tint(0xA08650, 0.55F, 0xA8522A, 0.72F),
			// inverno: cinza amarronzado, voltando a esverdear no fim
			new Tint(0x8E9478, 0.5F, 0x8E7A58, 0.6F),
			new Tint(0x9AA092, 0.6F, 0x857D6B, 0.65F),
			new Tint(0x8E9A70, 0.45F, 0x7E8460, 0.5F)};

	private static final float[] TEMPERATURE = {
			-0.3F, -0.1F, 0.0F, // primavera
			0.0F, 0.0F, 0.0F, // verao
			0.0F, -0.15F, -0.35F, // outono
			-0.6F, -0.8F, -0.7F}; // inverno

	private SeasonVisuals() {
	}

	public static Tint tint(SeasonDate date) {
		return TINTS[date.visualKey()];
	}

	/** Deslocamento de temperatura (sempre &lt;= 0) de um bioma temperado na data. */
	public static float temperatureOffset(SeasonDate date) {
		return TEMPERATURE[date.visualKey()];
	}

	public static int grassColor(int vanilla, SeasonDate date) {
		Tint tint = tint(date);
		return blend(vanilla, tint.grassTarget(), tint.grassAmount());
	}

	public static int foliageColor(int vanilla, SeasonDate date) {
		Tint tint = tint(date);
		return blend(vanilla, tint.foliageTarget(), tint.foliageAmount());
	}

	/** Copas amarelas e vermelhas no outono: alvo e quantidade maxima da variacao por lugar. */
	private static final int AUTUMN_YELLOW = 0xE8C030;
	private static final int AUTUMN_RED = 0xB82818;
	/** Tamanho das manchas de cor das copas no outono, em blocos. */
	private static final double PATCH = 9.0;

	/**
	 * Variacao por lugar da folhagem (ja tingida pela estacao) no outono: um ruido de valor suave em manchas de ~9
	 * blocos puxa algumas copas para o amarelo e outras para o vermelho. Fora do outono devolve a cor como esta.
	 */
	public static int foliageVariation(int color, double x, double z, SeasonDate date) {
		if (date.season() != Season.AUTUMN) {
			return color;
		}
		double n = valueNoise(x / PATCH, z / PATCH);
		float strength = date.phase() == Season.Phase.EARLY ? 0.4F : 0.65F;
		if (n < 0.4) {
			return blend(color, AUTUMN_YELLOW, (float) ((0.4 - n) / 0.4) * strength);
		}
		if (n > 0.6) {
			return blend(color, AUTUMN_RED, (float) ((n - 0.6) / 0.4) * strength);
		}
		return color;
	}

	/** Ruido de valor 2D em [0,1], interpolado com suavizacao (deterministico, sem estado). */
	public static double valueNoise(double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fz = smooth(z - z0);
		double a = hash(x0, z0);
		double b = hash(x0 + 1, z0);
		double c = hash(x0, z0 + 1);
		double d = hash(x0 + 1, z0 + 1);
		double top = a + (b - a) * fx;
		double bottom = c + (d - c) * fx;
		return top + (bottom - top) * fz;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double hash(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Mistura RGB linear; preserva o alfa de {@code base}. */
	public static int blend(int base, int target, float amount) {
		if (amount <= 0) {
			return base;
		}
		float k = Math.min(1, amount);
		int r = mix((base >> 16) & 0xFF, (target >> 16) & 0xFF, k);
		int g = mix((base >> 8) & 0xFF, (target >> 8) & 0xFF, k);
		int b = mix(base & 0xFF, target & 0xFF, k);
		return (base & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static int mix(int a, int b, float k) {
		return Math.round(a + (b - a) * k);
	}
}
