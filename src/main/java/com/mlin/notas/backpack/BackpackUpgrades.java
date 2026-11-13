package com.mlin.notas.backpack;

/**
 * Estado da mochila guardado no item (upgrades instalados, tanques e progresso da fundicao). Logica pura e imutavel.
 *
 * @param sizeLevel    0 = 27 slots, 1 = 54, 2 = 81
 * @param crafting     grade de crafting 3x3 na tela
 * @param furnace      funde itens do conteudo enquanto esta no inventario
 * @param water        tanque de agua infinito (depois de encher uma vez)
 * @param lava         tanque de lava (uma carga por fonte coletada)
 * @param waterFilled  tanque de agua ja encheu
 * @param lavaCharges  cargas de lava disponiveis
 * @param lavaSelected modo de fluido selecionado (false = agua, true = lava)
 * @param smelting     progresso da fundicao
 */
public record BackpackUpgrades(int sizeLevel, boolean crafting, boolean furnace, boolean water, boolean lava,
							   boolean waterFilled, int lavaCharges, boolean lavaSelected, SmeltingProgress smelting) {
	public static final int MAX_SIZE_LEVEL = 2;
	public static final int MAX_LAVA_CHARGES = 64;
	public static final BackpackUpgrades DEFAULT = new BackpackUpgrades(0, false, false, false, false, false, 0, false, SmeltingProgress.IDLE);

	public BackpackUpgrades {
		if (sizeLevel < 0 || sizeLevel > MAX_SIZE_LEVEL) {
			throw new IllegalArgumentException("sizeLevel fora de 0.." + MAX_SIZE_LEVEL + ": " + sizeLevel);
		}
		if (lavaCharges < 0) {
			throw new IllegalArgumentException("lavaCharges negativo");
		}
		if (smelting == null) {
			smelting = SmeltingProgress.IDLE;
		}
	}

	/** Numero de slots do conteudo para o nivel de tamanho. */
	public static int slotCountFor(int sizeLevel) {
		return 27 * (sizeLevel + 1);
	}

	public int slotCount() {
		return slotCountFor(sizeLevel);
	}

	public int rows() {
		return slotCount() / 9;
	}

	/** True se o upgrade pode ser instalado (nao instalado ainda / tamanho abaixo do maximo). */
	public boolean canApply(BackpackUpgradeKind kind) {
		return switch (kind) {
			case SIZE -> sizeLevel < MAX_SIZE_LEVEL;
			case CRAFTING -> !crafting;
			case FURNACE -> !furnace;
			case WATER -> !water;
			case LAVA -> !lava;
		};
	}

	/** Devolve uma copia com o upgrade instalado; a mesma instancia se nao puder aplicar. */
	public BackpackUpgrades apply(BackpackUpgradeKind kind) {
		if (!canApply(kind)) {
			return this;
		}
		return switch (kind) {
			case SIZE -> new BackpackUpgrades(sizeLevel + 1, crafting, furnace, water, lava, waterFilled, lavaCharges, lavaSelected, smelting);
			case CRAFTING -> new BackpackUpgrades(sizeLevel, true, furnace, water, lava, waterFilled, lavaCharges, lavaSelected, smelting);
			case FURNACE -> new BackpackUpgrades(sizeLevel, crafting, true, water, lava, waterFilled, lavaCharges, lavaSelected, smelting);
			case WATER -> new BackpackUpgrades(sizeLevel, crafting, furnace, true, lava, waterFilled, lavaCharges, lavaSelected, smelting);
			case LAVA -> new BackpackUpgrades(sizeLevel, crafting, furnace, water, true, waterFilled, lavaCharges, lavaSelected, smelting);
		};
	}

	public BackpackUpgrades withWaterFilled(boolean filled) {
		return new BackpackUpgrades(sizeLevel, crafting, furnace, water, lava, filled, lavaCharges, lavaSelected, smelting);
	}

	public BackpackUpgrades withLavaCharges(int charges) {
		return new BackpackUpgrades(sizeLevel, crafting, furnace, water, lava, waterFilled, Math.min(MAX_LAVA_CHARGES, charges), lavaSelected, smelting);
	}

	public BackpackUpgrades withLavaSelected(boolean selected) {
		return new BackpackUpgrades(sizeLevel, crafting, furnace, water, lava, waterFilled, lavaCharges, selected, smelting);
	}

	public BackpackUpgrades withSmelting(SmeltingProgress progress) {
		return new BackpackUpgrades(sizeLevel, crafting, furnace, water, lava, waterFilled, lavaCharges, lavaSelected, progress);
	}

	/** Pode colocar o fluido selecionado agora? */
	public boolean canPlaceSelectedFluid() {
		return lavaSelected ? lava && lavaCharges > 0 : water && waterFilled;
	}
}
