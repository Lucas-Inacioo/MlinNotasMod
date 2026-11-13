package com.mlin.notas.recipes;

/** Tipos de receita que o visualizador entende. A ordem e usada na serializacao (ordinal). */
public enum RecipeKind {
	SHAPED,
	SHAPELESS,
	SMELTING,
	BLASTING,
	SMOKING,
	CAMPFIRE,
	STONECUTTING,
	/** Panela da cozinha: ingredientes e, se {@code width == 1}, o ultimo input e o recipiente. */
	COOKING_POT,
	/** Tabua de corte. */
	CUTTING,
	/** Barril de Conserva: {@code cookTicks} = tempo de cura. */
	FERMENTING;

	private static final RecipeKind[] VALUES = values();

	public static RecipeKind byOrdinal(int ordinal) {
		if (ordinal < 0 || ordinal >= VALUES.length) {
			throw new IllegalArgumentException("RecipeKind invalido: " + ordinal);
		}
		return VALUES[ordinal];
	}

	public boolean isCrafting() {
		return this == SHAPED || this == SHAPELESS;
	}

	public boolean isCooking() {
		return this == SMELTING || this == BLASTING || this == SMOKING || this == CAMPFIRE;
	}
}
