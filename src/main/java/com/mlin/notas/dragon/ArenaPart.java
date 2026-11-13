package com.mlin.notas.dragon;

/**
 * Papel de cada bloco da arena do dragao ({@link ArenaPlan}). A ponte com o jogo ({@code DragonArenaFeature}) troca
 * cada papel por um bloco. Os {@code PAVE_*} substituem o bloco do chao (mesmo y do terreno). Logica pura.
 */
public enum ArenaPart {
	PAVE_BRICK(Shape.PAVE),
	/** Tijolo gasto (pedra do End lisa no meio do piso). */
	PAVE_WORN(Shape.PAVE),
	PAVE_MOSS(Shape.PAVE),
	PAVE_VOID(Shape.PAVE),
	PAVE_PURPUR(Shape.PAVE),
	PAVE_OBSIDIAN(Shape.PAVE),
	BRICK(Shape.SOLID),
	BRICK_SLAB(Shape.SLAB),
	BRICK_WALL(Shape.SOLID),
	VOID_BRICK(Shape.SOLID),
	PURPUR(Shape.SOLID),
	PURPUR_PILLAR(Shape.SOLID),
	/** Pilar de purpur deitado (eixo X). */
	PURPUR_PILLAR_X(Shape.SOLID),
	/** Pilar de purpur deitado (eixo Z). */
	PURPUR_PILLAR_Z(Shape.SOLID),
	PURPUR_SLAB(Shape.SLAB),
	/** Laje na metade de cima (verga dos arcos, beiral dos colares). */
	PURPUR_SLAB_TOP(Shape.SOLID),
	OBSIDIAN(Shape.SOLID),
	CRYING_OBSIDIAN(Shape.SOLID),
	/** Lampada de purpur (bloco do mod). */
	LAMP(Shape.SOLID),
	CRYSTAL_BLOCK(Shape.SOLID),
	/** Haste do End, em pe sobre o bloco de baixo. */
	END_ROD(Shape.ATTACHED),
	/** Aglomerado de cristal etereo, em pe sobre o bloco de baixo. */
	CRYSTAL(Shape.ATTACHED),
	/** Lanterna de alma, em pe sobre o bloco de baixo. */
	SOUL_LANTERN(Shape.ATTACHED);

	/** PAVE = troca o chao; SOLID = bloco cheio; SLAB = meia altura (degrau); ATTACHED = decoracao apoiada embaixo. */
	public enum Shape {
		PAVE,
		SOLID,
		SLAB,
		ATTACHED
	}

	private final Shape shape;

	ArenaPart(Shape shape) {
		this.shape = shape;
	}

	public Shape shape() {
		return shape;
	}

	/** Bloco que sustenta outros (chao trocado, bloco cheio ou laje). */
	public boolean solid() {
		return shape != Shape.ATTACHED;
	}

	public boolean paving() {
		return shape == Shape.PAVE;
	}
}
