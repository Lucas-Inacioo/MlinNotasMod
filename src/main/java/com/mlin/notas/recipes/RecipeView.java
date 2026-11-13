package com.mlin.notas.recipes;

import java.util.List;

/**
 * Receita achatada, sem classes do Minecraft. Itens sao ids numericos do registro (iguais em cliente e servidor
 * gracas ao registry sync da Fabric).
 *
 * @param id          id da receita (ex.: "minecraft:chest")
 * @param kind        tipo
 * @param width       largura da grade (SHAPED; 0 nos demais)
 * @param height      altura da grade (SHAPED; 0 nos demais)
 * @param inputs      um elemento por slot de entrada; cada elemento lista as alternativas (tag) e lista vazia = slot vazio
 * @param output      id do item produzido
 * @param outputCount quantidade produzida
 * @param cookTicks   tempo de cozimento (receitas de fornalha), 0 nas demais
 * @param experience  experiencia (receitas de fornalha)
 */
public record RecipeView(
		String id,
		RecipeKind kind,
		int width,
		int height,
		List<List<Integer>> inputs,
		int output,
		int outputCount,
		int cookTicks,
		float experience
) {
	public RecipeView {
		inputs = List.copyOf(inputs.stream().map(List::copyOf).toList());
	}

	/** True se o item aparece como ingrediente em algum slot. */
	public boolean usesItem(int item) {
		for (List<Integer> slot : inputs) {
			if (slot.contains(item)) {
				return true;
			}
		}
		return false;
	}

	/** Para receitas SHAPED: alternativas do slot (linha, coluna) da grade 3x3; vazio fora da area da receita. */
	public List<Integer> gridSlot(int row, int col) {
		if (kind != RecipeKind.SHAPED) {
			int index = row * 3 + col;
			return index < inputs.size() ? inputs.get(index) : List.of();
		}
		if (row >= height || col >= width) {
			return List.of();
		}
		return inputs.get(row * width + col);
	}
}
