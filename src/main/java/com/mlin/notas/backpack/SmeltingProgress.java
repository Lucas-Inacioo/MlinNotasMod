package com.mlin.notas.backpack;

/**
 * Maquina de estados da fundicao da mochila, igual a da fornalha vanilla. Logica pura e imutavel.
 *
 * @param burnRemaining ticks de combustivel restantes (> 0 = acesa)
 * @param burnTotal     duracao total do ultimo combustivel
 * @param cookProgress  ticks de cozimento do item atual
 */
public record SmeltingProgress(int burnRemaining, int burnTotal, int cookProgress) {
	public static final SmeltingProgress IDLE = new SmeltingProgress(0, 0, 0);
	/** Tempo padrao de cozimento da fornalha. */
	public static final int DEFAULT_COOK_TIME = 200;

	/**
	 * Resultado de um tick.
	 *
	 * @param next        novo estado
	 * @param consumeFuel o chamador deve consumir 1 combustivel
	 * @param finished    o chamador deve consumir 1 entrada e produzir 1 saida
	 */
	public record Step(SmeltingProgress next, boolean consumeFuel, boolean finished) {
	}

	public boolean isLit() {
		return burnRemaining > 0;
	}

	/**
	 * @param hasInput     ha um item fundivel na entrada
	 * @param canOutput    ha espaco para o resultado
	 * @param fuelBurnTime duracao do combustivel disponivel (0 = sem combustivel)
	 * @param cookTime     tempo de cozimento da receita atual
	 */
	public Step tick(boolean hasInput, boolean canOutput, int fuelBurnTime, int cookTime) {
		int remaining = burnRemaining;
		int total = burnTotal;
		int progress = cookProgress;
		boolean lit = remaining > 0;
		if (lit) {
			remaining--;
			lit = remaining > 0;
		}
		boolean consumeFuel = false;
		boolean finished = false;
		if (hasInput && canOutput) {
			if (!lit && fuelBurnTime > 0) {
				remaining = fuelBurnTime;
				total = fuelBurnTime;
				lit = true;
				consumeFuel = true;
			}
			if (lit) {
				progress++;
				if (progress >= cookTime) {
					progress = 0;
					finished = true;
				}
			} else {
				progress = 0;
			}
		} else if (progress > 0) {
			progress = Math.max(0, progress - 2);
		}
		return new Step(new SmeltingProgress(remaining, total, progress), consumeFuel, finished);
	}
}
