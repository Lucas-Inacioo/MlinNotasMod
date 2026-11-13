package com.mlin.notas.tier;

import java.util.Collection;
import java.util.function.DoubleSupplier;

/**
 * Minerio extra ao quebrar um minerio (logica pura). Cada fonte (poder Garimpo do sinalizador, ramo "Prospeccao"
 * da arvore de habilidades no futuro...) soma uma chance; a soma vira itens extras: a parte inteira e garantida e a
 * fracao e sorteada. Ex.: 0,5 = 50% de +1; 1,25 = +1 garantido e 25% de +1.
 */
public final class OreYield {
	/** Uma fonte de chance extra. */
	public record Source(String id, double chance) {
		public Source {
			if (chance < 0 || Double.isNaN(chance)) {
				throw new IllegalArgumentException("chance invalida: " + chance);
			}
		}
	}

	private OreYield() {
	}

	/** Soma das chances das fontes. */
	public static double totalChance(Collection<Source> sources) {
		double total = 0;
		for (Source source : sources) {
			total += source.chance();
		}
		return total;
	}

	/** Media de itens extras para um minerio que soltou {@code baseCount} itens (0 se nao soltou nada). */
	public static double expectedExtra(int baseCount, Collection<Source> sources) {
		return baseCount <= 0 ? 0 : totalChance(sources);
	}

	/**
	 * Sorteia os extras. {@code random} devolve valores em [0, 1) (por exemplo {@code RandomSource::nextDouble}).
	 * Nada de extra se o minerio nao soltou o item (toque suave, ferramenta errada).
	 */
	public static int rollExtra(int baseCount, Collection<Source> sources, DoubleSupplier random) {
		if (baseCount <= 0) {
			return 0;
		}
		double total = totalChance(sources);
		int guaranteed = (int) Math.floor(total);
		double fraction = total - guaranteed;
		return guaranteed + (fraction > 0 && random.getAsDouble() < fraction ? 1 : 0);
	}
}
