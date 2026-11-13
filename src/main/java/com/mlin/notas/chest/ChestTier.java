package com.mlin.notas.chest;

import com.mlin.notas.tier.Tier;

/**
 * Niveis de bau grande, um por material da escada {@link Tier} acima da madeira (o bau de madeira e o vanilla).
 * Todos tem 9 colunas; a tela mostra no maximo {@link #MAX_VISIBLE_ROWS} linhas e rola o resto. Logica pura.
 */
public enum ChestTier {
	STONE(Tier.STONE),
	COPPER(Tier.COPPER),
	IRON(Tier.IRON),
	GOLD(Tier.GOLD),
	COBALT(Tier.COBALT),
	DIAMOND(Tier.DIAMOND),
	NETHERITE(Tier.NETHERITE),
	INFERNITE(Tier.INFERNITE),
	ENDITE(Tier.ENDITE),
	ASTRALITE(Tier.ASTRALITE),
	CELESTIUM(Tier.CELESTIUM);

	public static final int COLUMNS = 9;
	public static final int MAX_VISIBLE_ROWS = 6;

	private final Tier material;

	ChestTier(Tier material) {
		this.material = material;
	}

	public Tier material() {
		return material;
	}

	/** Nome usado nos ids: stone_chest, iron_chest, celestium_chest... */
	public String blockName() {
		return material.id() + "_chest";
	}

	public String upgradeName() {
		return "chest_upgrade_" + material.id();
	}

	/** Nome da textura em textures/entity/chest. */
	public String textureName() {
		return material.id();
	}

	public int rows() {
		return size() / COLUMNS;
	}

	public int cols() {
		return COLUMNS;
	}

	public int size() {
		return material.chestSlots();
	}

	/** Linhas visiveis na tela ao mesmo tempo. */
	public int visibleRows() {
		return Math.min(rows(), MAX_VISIBLE_ROWS);
	}

	/** Quantas linhas a tela pode rolar (0 se tudo cabe). */
	public int maxScroll() {
		return rows() - visibleRows();
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

	/** Largura da moldura da tela: 9 colunas (176, o tamanho vanilla) + 14 da barra de rolagem quando ha rolagem. */
	public int screenWidth() {
		return COLUMNS * 18 + 14 + (maxScroll() > 0 ? 14 : 0);
	}

	public static ChestTier of(Tier material) {
		for (ChestTier tier : values()) {
			if (tier.material == material) {
				return tier;
			}
		}
		return null;
	}

	/**
	 * Primeira linha visivel depois de rolar {@code delta} linhas a partir de {@code current}, limitada a [0, maxScroll].
	 */
	public int clampScroll(int current, int delta) {
		return Math.clamp((long) current + delta, 0, maxScroll());
	}
}
