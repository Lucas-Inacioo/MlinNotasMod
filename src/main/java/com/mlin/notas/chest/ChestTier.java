package com.mlin.notas.chest;

/**
 * Niveis de bau grande. Logica pura (sem Minecraft).
 */
public enum ChestTier {
	IRON("iron", 6, 9),
	GOLD("gold", 9, 9),
	DIAMOND("diamond", 9, 12);

	private final String name;
	private final int rows;
	private final int cols;

	ChestTier(String name, int rows, int cols) {
		this.name = name;
		this.rows = rows;
		this.cols = cols;
	}

	/** Nome usado nos ids: iron_chest, gold_chest, diamond_chest. */
	public String blockName() {
		return name + "_chest";
	}

	public String upgradeName() {
		return "chest_upgrade_" + name;
	}

	public int rows() {
		return rows;
	}

	public int cols() {
		return cols;
	}

	public int size() {
		return rows * cols;
	}

	/** Proximo nivel, ou null se ja for o ultimo. */
	public ChestTier next() {
		int i = ordinal() + 1;
		return i < values().length ? values()[i] : null;
	}

	/** Nivel anterior, ou null para o primeiro (o anterior do primeiro e o bau vanilla). */
	public ChestTier previous() {
		int i = ordinal() - 1;
		return i >= 0 ? values()[i] : null;
	}

	/** Largura da moldura da tela em pixels: 18 por coluna + 14 de borda (9 colunas = 176, o tamanho vanilla). */
	public int screenWidth() {
		return cols * 18 + 14;
	}
}
