package com.mlin.notas.grave;

/**
 * Numeros e regras puras das lapides (sem Minecraft): quanto XP fica guardado, o padrao do keepInventory e a
 * disposicao dos itens dentro da lapide ({@link #SIZE} posicoes).
 *
 * <p>Disposicao (indice da lapide): 0..35 = mesmos indices do inventario do jogador (0..8 barra de atalhos),
 * 36..42 = equipamento com os indices do {@code Inventory} (36 botas, 37 calca, 38 peitoral, 39 capacete, 40 mao
 * secundaria, 41 corpo, 42 sela), 43..48 = os 6 encaixes de acessorio, 49..53 = sobras (o que nao tem lugar certo).</p>
 */
public final class GraveRules {
	/** Porcentagem dos pontos de experiencia do jogador que ficam na lapide (o resto se perde, como no vanilla). */
	public static final int XP_KEEP_PERCENT = 50;
	/** Valor que o mod aplica uma vez por mundo na gamerule {@code keep_inventory}: as lapides guardam os itens. */
	public static final boolean MOD_DEFAULT_KEEP_INVENTORY = false;

	/** Posicoes do inventario do jogador que vao para os mesmos indices (36 principal + 7 de equipamento). */
	public static final int INVENTORY_SLOTS = 43;
	public static final int MAIN_SLOTS = 36;
	public static final int ACCESSORY_START = 43;
	public static final int ACCESSORY_SLOTS = 6;
	public static final int EXTRA_START = ACCESSORY_START + ACCESSORY_SLOTS;
	public static final int SIZE = 54;

	/** Ordem na tela (6 linhas de 9, como um bau grande): equipamento e acessorios em cima, inventario, barra embaixo. */
	private static final int[] MENU_ORDER = buildMenuOrder();

	private GraveRules() {
	}

	/** Pontos guardados na lapide a partir do total de pontos do jogador ao morrer. */
	public static int storedXp(int totalPoints) {
		return (int) ((long) Math.max(0, totalPoints) * XP_KEEP_PERCENT / 100);
	}

	/** Deve aplicar o padrao do mod (keepInventory desligado)? So uma vez por mundo; depois vale o que o jogador escolher. */
	public static boolean shouldApplyKeepInventoryDefault(boolean alreadyApplied) {
		return !alreadyApplied;
	}

	/** Indice da lapide mostrado no slot {@code menuSlot} (0..53) da tela. */
	public static int graveIndexForMenuSlot(int menuSlot) {
		return MENU_ORDER[menuSlot];
	}

	/** Indice do acessorio (0..5) de um indice da lapide, ou -1. */
	public static int accessoryIndex(int graveIndex) {
		return graveIndex >= ACCESSORY_START && graveIndex < EXTRA_START ? graveIndex - ACCESSORY_START : -1;
	}

	/** Indice do inventario do jogador (0..42) de um indice da lapide, ou -1 (acessorio ou sobra). */
	public static int inventoryIndex(int graveIndex) {
		return graveIndex >= 0 && graveIndex < INVENTORY_SLOTS ? graveIndex : -1;
	}

	private static int[] buildMenuOrder() {
		int[] order = new int[SIZE];
		int i = 0;
		// linha 1: capacete, peitoral, calca, botas, mao secundaria, 4 acessorios
		for (int slot : new int[] {39, 38, 37, 36, 40, 43, 44, 45, 46}) {
			order[i++] = slot;
		}
		// linha 2: 2 acessorios, corpo, sela, 5 sobras
		for (int slot : new int[] {47, 48, 41, 42, 49, 50, 51, 52, 53}) {
			order[i++] = slot;
		}
		for (int slot = 9; slot < MAIN_SLOTS; slot++) {
			order[i++] = slot;
		}
		for (int slot = 0; slot < 9; slot++) {
			order[i++] = slot;
		}
		return order;
	}
}
