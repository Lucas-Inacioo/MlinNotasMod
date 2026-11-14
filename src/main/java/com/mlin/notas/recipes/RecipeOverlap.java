package com.mlin.notas.recipes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Detecta receitas de crafting que casam com a mesma grade (logica pura). Cada receita vira uma {@link Grid}: forma
 * fixa (com ou sem espelho) ou lista sem forma; cada espaco e o conjunto de itens aceitos (tags ja expandidas).
 * Duas receitas conflitam quando existe uma grade que as duas aceitam: as duas so ganham do jogador por ordem de
 * carregamento, entao a do mod pode esconder a vanilla (ou o contrario).
 *
 * <p>Usado pelo game test que compara todas as receitas do mod com todas as outras.</p>
 */
public final class RecipeOverlap {
	private RecipeOverlap() {
	}

	/**
	 * Uma forma de casar a receita. {@code shaped}: {@code width x height} espacos em ordem de linha (conjunto vazio =
	 * espaco vazio), {@code mirrorable} = tambem casa espelhada. Sem forma: so {@code cells} (todos nao vazios).
	 */
	public record Grid(boolean shaped, int width, int height, List<Set<Integer>> cells, boolean mirrorable) {
		public static Grid shaped(int width, int height, List<Set<Integer>> cells) {
			if (cells.size() != width * height) {
				throw new IllegalArgumentException("forma " + width + "x" + height + " com " + cells.size() + " espacos");
			}
			return new Grid(true, width, height, List.copyOf(cells), true);
		}

		public static Grid shapeless(List<Set<Integer>> cells) {
			return new Grid(false, 0, 0, List.copyOf(cells), false);
		}

		/** Espacos ocupados (sem os vazios da forma). */
		public List<Set<Integer>> filled() {
			List<Set<Integer>> out = new ArrayList<>();
			for (Set<Integer> cell : cells) {
				if (!cell.isEmpty()) {
					out.add(cell);
				}
			}
			return out;
		}

		Grid mirrored() {
			List<Set<Integer>> out = new ArrayList<>(cells.size());
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					out.add(cells.get(y * width + (width - 1 - x)));
				}
			}
			return new Grid(true, width, height, out, mirrorable);
		}
	}

	/** Verdadeiro se alguma grade de itens casa as duas formas. */
	public static boolean overlaps(Grid a, Grid b) {
		if (a.shaped() && b.shaped()) {
			if (a.width() != b.width() || a.height() != b.height()) {
				return false;
			}
			if (sameCells(a, b)) {
				return true;
			}
			// espelhar uma das duas basta: espelhar as duas e o mesmo que nao espelhar nenhuma
			return (a.mirrorable() || b.mirrorable()) && sameCells(a.mirrored(), b);
		}
		// pelo menos uma sem forma: a com forma pode ir para qualquer lugar da grade, so importa o casamento dos itens
		List<Set<Integer>> left = a.filled();
		List<Set<Integer>> right = b.filled();
		return left.size() == right.size() && perfectMatching(left, right);
	}

	private static boolean sameCells(Grid a, Grid b) {
		for (int i = 0; i < a.cells().size(); i++) {
			Set<Integer> x = a.cells().get(i);
			Set<Integer> y = b.cells().get(i);
			if (x.isEmpty() != y.isEmpty()) {
				return false;
			}
			if (!x.isEmpty() && !intersects(x, y)) {
				return false;
			}
		}
		return true;
	}

	static boolean intersects(Set<Integer> a, Set<Integer> b) {
		Set<Integer> small = a.size() <= b.size() ? a : b;
		Set<Integer> big = small == a ? b : a;
		for (Integer item : small) {
			if (big.contains(item)) {
				return true;
			}
		}
		return false;
	}

	/** Casamento perfeito (Kuhn) entre os espacos das duas listas, ligando os que aceitam um item em comum. */
	static boolean perfectMatching(List<Set<Integer>> left, List<Set<Integer>> right) {
		int n = left.size();
		boolean[][] edge = new boolean[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				edge[i][j] = intersects(left.get(i), right.get(j));
			}
		}
		int[] matchOfRight = new int[n];
		Arrays.fill(matchOfRight, -1);
		for (int i = 0; i < n; i++) {
			if (!augment(i, edge, matchOfRight, new boolean[n])) {
				return false;
			}
		}
		return true;
	}

	private static boolean augment(int i, boolean[][] edge, int[] matchOfRight, boolean[] seen) {
		for (int j = 0; j < matchOfRight.length; j++) {
			if (edge[i][j] && !seen[j]) {
				seen[j] = true;
				if (matchOfRight[j] < 0 || augment(matchOfRight[j], edge, matchOfRight, seen)) {
					matchOfRight[j] = i;
					return true;
				}
			}
		}
		return false;
	}
}
