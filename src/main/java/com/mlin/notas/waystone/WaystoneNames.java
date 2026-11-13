package com.mlin.notas.waystone;

import java.util.Random;

/** Nome inicial sorteado para uma pedra recem-ativada (o jogador pode renomear). Logica pura. */
public final class WaystoneNames {
	private static final String[] START = {"Al", "Bel", "Cor", "Dor", "El", "Fen", "Gal", "Hol", "Ir", "Jor", "Kal", "Lum", "Mor", "Nor", "Or",
			"Pel", "Quel", "Ros", "Sil", "Tor", "Ul", "Val", "Wen", "Zar"};
	private static final String[] MIDDLE = {"a", "e", "i", "o", "u", "ar", "en", "ir", "or", "an"};
	private static final String[] END = {"dor", "mar", "via", "wyn", "thal", "ria", "dell", "mont", "vale", "rock", "ford", "stad", "lin", "ros"};

	private WaystoneNames() {
	}

	public static String generate(long seed) {
		Random random = new Random(seed);
		String name = START[random.nextInt(START.length)];
		if (random.nextBoolean()) {
			name += MIDDLE[random.nextInt(MIDDLE.length)];
		}
		return name + END[random.nextInt(END.length)];
	}

	public static long seed(String dimension, int x, int y, int z) {
		return ((long) x * 3129871L) ^ ((long) z * 116129781L) ^ ((long) y * 42317861L) ^ dimension.hashCode();
	}
}
