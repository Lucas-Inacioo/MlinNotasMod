package com.mlin.notas.storage;

/**
 * Quantidades curtas para caber no canto de um slot: 999, 1.2k, 34k, 1.2M, 34M, 1.2B. Sempre arredonda para baixo
 * (nunca mostra mais do que existe). Logica pura.
 */
public final class CountFormat {
	private static final String[] SUFFIXES = {"k", "M", "B", "T"};

	private CountFormat() {
	}

	public static String compact(long n) {
		if (n < 0) {
			return "-" + compact(-n);
		}
		if (n < 1000) {
			return Long.toString(n);
		}
		long unit = 1000;
		for (String suffix : SUFFIXES) {
			if (n < unit * 1000 || suffix.equals("T")) {
				long whole = n / unit;
				if (whole < 10) {
					long tenth = (n % unit) * 10 / unit;
					return tenth == 0 ? whole + suffix : whole + "." + tenth + suffix;
				}
				return whole + suffix;
			}
			unit *= 1000;
		}
		throw new AssertionError();
	}

	/** Numero inteiro com separador de milhar (1,234,567). */
	public static String full(long n) {
		return String.format(java.util.Locale.ROOT, "%,d", n);
	}
}
