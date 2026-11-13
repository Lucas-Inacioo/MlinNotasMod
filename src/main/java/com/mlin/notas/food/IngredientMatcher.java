package com.mlin.notas.food;

import java.util.Arrays;
import java.util.function.BiPredicate;

/**
 * Casamento sem forma (logica pura): cada ingrediente da receita precisa de um item diferente, e todo item presente
 * precisa ser usado. Busca com retrocesso (no maximo 6x6 na panela), entao ingredientes de tag que se sobrepoem
 * (ex.: "qualquer queijo" e "queijo curado") sempre acham a combinacao certa.
 */
public final class IngredientMatcher {
	private IngredientMatcher() {
	}

	/**
	 * Para {@code ingredients} ingredientes e {@code items} itens nao vazios, devolve {@code a[i]} = indice do item
	 * usado pelo ingrediente {@code i}, ou null se nao casa. {@code fits.test(i, j)}: o item j serve no ingrediente i.
	 */
	public static int[] match(int ingredients, int items, BiPredicate<Integer, Integer> fits) {
		if (ingredients != items) {
			return null;
		}
		int[] assignment = new int[ingredients];
		Arrays.fill(assignment, -1);
		boolean[] used = new boolean[items];
		return assign(0, ingredients, items, fits, assignment, used) ? assignment : null;
	}

	private static boolean assign(int i, int ingredients, int items, BiPredicate<Integer, Integer> fits, int[] assignment, boolean[] used) {
		if (i == ingredients) {
			return true;
		}
		for (int j = 0; j < items; j++) {
			if (!used[j] && fits.test(i, j)) {
				used[j] = true;
				assignment[i] = j;
				if (assign(i + 1, ingredients, items, fits, assignment, used)) {
					return true;
				}
				used[j] = false;
				assignment[i] = -1;
			}
		}
		return false;
	}
}
