package com.mlin.notas.accessories;

/**
 * Efeito especial de um acessorio que nao e um numero somado. Os marcados como {@code proc} disparam em combate e
 * respeitam a recarga minima de {@link ProcCooldowns#MIN_INTERVAL} ticks (no maximo 1 por segundo). Logica pura.
 */
public enum AccessoryAbility {
	/** Pulo duplo no ar. */
	DOUBLE_JUMP("double_jump", false),
	/** Escala paredes encostando nelas (agachar segura). */
	CLIMB("climb", false),
	/** Visao noturna curta ao entrar no escuro de cavernas, com recarga longa. */
	LANTERN("lantern", false),
	/** Puxa itens soltos por perto (agachar desliga). */
	MAGNET("magnet", false),
	/** Mostra hora, dia e estacao no canto da tela. */
	WATCH("watch", false),
	/** Devolve dano a quem bate corpo a corpo. */
	THORNS("thorns", true),
	/** Golpe corpo a corpo deixa o alvo lento. */
	FROST("frost", true),
	/** Cura meio coracao a cada 5 s sob o sol. */
	SUNLIGHT("sunlight", true),
	/** Golpe corpo a corpo aplica Murchar I. */
	WITHER_TOUCH("wither_touch", true);

	private final String id;
	private final boolean proc;

	AccessoryAbility(String id, boolean proc) {
		this.id = id;
		this.proc = proc;
	}

	public String id() {
		return id;
	}

	public boolean proc() {
		return proc;
	}
}
