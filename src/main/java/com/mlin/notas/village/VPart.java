package com.mlin.notas.village;

/**
 * Papel de cada bloco das construcoes da vila (logica pura). A ponte com o jogo ({@code VillagePalette}) troca cada
 * papel pelo bloco do estilo do bioma. Partes orientadas usam a direcao da {@link Placement}.
 */
public enum VPart {
	/** Limpa o espaco (so tira blocos naturais: grama, folhas, terra acima do lote). */
	AIR(Flag.NONE),
	/** Alicerce de pedra (desce ate o chao). */
	FOUNDATION(Flag.SOLID | Flag.FILL_STONE),
	STONE(Flag.SOLID),
	STONE_ALT(Flag.SOLID),
	/** Pedra de destaque (cantoneiras, pilares, chamine). */
	BRICK(Flag.SOLID),
	STONE_STAIRS(Flag.SOLID),
	STONE_STAIRS_TOP(Flag.SOLID),
	STONE_SLAB(Flag.SOLID),
	STONE_SLAB_TOP(Flag.SOLID),
	STONE_WALL(Flag.SOLID),
	/** Calcamento da praca (duas pedras misturadas). */
	PAVING(Flag.SOLID | Flag.FILL_STONE | Flag.GROUND),
	PAVING_ALT(Flag.SOLID | Flag.FILL_STONE | Flag.GROUND),
	/** Caminho de terra batida, no nivel do chao. */
	PATH(Flag.SOLID | Flag.FILL_DIRT | Flag.GROUND),
	GRAVEL(Flag.SOLID | Flag.FILL_DIRT | Flag.GROUND),
	/** Terra com grama (quintal); areia no deserto. */
	SOIL(Flag.SOLID | Flag.FILL_DIRT | Flag.GROUND),
	FARMLAND(Flag.SOLID | Flag.FILL_DIRT | Flag.GROUND),
	WATER(Flag.FILL_DIRT | Flag.GROUND),
	/** Assoalho (desce com alicerce de pedra). */
	FLOOR(Flag.SOLID | Flag.FILL_STONE),
	PLANKS(Flag.SOLID),
	/** Enchimento das paredes entre as vigas (reboco). */
	PLASTER(Flag.SOLID),
	/** Tronco: em pe sem direcao; deitado no eixo da direcao. */
	LOG(Flag.SOLID),
	/** Tronco descascado (viga). */
	BEAM(Flag.SOLID),
	/** Telhado: degrau com a parte alta para o lado da direcao. */
	ROOF(Flag.SOLID),
	ROOF_TOP(Flag.SOLID),
	ROOF_SLAB(Flag.SOLID),
	ROOF_SLAB_TOP(Flag.SOLID),
	ROOF_BLOCK(Flag.SOLID),
	WOOD_STAIRS(Flag.SOLID),
	WOOD_SLAB(Flag.SOLID),
	WOOD_SLAB_TOP(Flag.SOLID),
	FENCE(Flag.SOLID),
	FENCE_GATE(Flag.PASSABLE),
	/** Porta (a ponte coloca as duas metades). */
	DOOR(Flag.PASSABLE | Flag.LATE),
	/** Metade de cima da porta (so ocupa o espaco no plano). */
	DOOR_TOP(Flag.PASSABLE | Flag.SKIP),
	/** Vidraca (painel ligado ao longo da parede). */
	WINDOW(Flag.SOLID),
	/** Veneziana: alcapao aberto encostado na parede atras (lado oposto ao da direcao). */
	SHUTTER(Flag.LATE),
	/** Alcapao fechado na metade de cima (tampo, prateleira). */
	TRAPDOOR_TOP(Flag.SOLID),
	/** Escada de mao; a parede fica atras (lado oposto ao da direcao). */
	LADDER(Flag.CLIMB | Flag.LATE),
	/** Pe da cama; a cabeceira fica no lado da direcao. */
	BED(Flag.STANDABLE_SOFT | Flag.LATE),
	BED_HEAD(Flag.STANDABLE_SOFT | Flag.SKIP),
	CARPET(Flag.LATE),
	CARPET_ALT(Flag.LATE),
	WOOL(Flag.SOLID),
	WOOL_ALT(Flag.SOLID),
	CHEST(Flag.SOLID | Flag.LATE),
	BARREL(Flag.SOLID),
	CRAFTING(Flag.SOLID),
	FURNACE(Flag.SOLID),
	SMOKER(Flag.SOLID),
	COMPOSTER(Flag.SOLID),
	BLAST_FURNACE(Flag.SOLID),
	SMITHING_TABLE(Flag.SOLID),
	GRINDSTONE(Flag.SOLID | Flag.LATE),
	LECTERN(Flag.SOLID),
	BOOKSHELF(Flag.SOLID),
	LOOM(Flag.SOLID),
	STONECUTTER(Flag.SOLID),
	CARTOGRAPHY(Flag.SOLID),
	FLETCHING(Flag.SOLID),
	BREWING(Flag.SOLID | Flag.LATE),
	CAULDRON(Flag.SOLID),
	LAVA_CAULDRON(Flag.SOLID),
	ANVIL(Flag.SOLID | Flag.LATE),
	LANTERN(Flag.LATE),
	LANTERN_HANGING(Flag.LATE),
	/** Fogueira acesa (chamine, fogueira do acampamento). */
	CAMPFIRE(Flag.SOLID | Flag.LATE),
	FLOWER(Flag.LATE),
	PLANT(Flag.LATE),
	FLOWER_POT(Flag.LATE),
	/** Folhas permanentes (cerca viva). */
	HEDGE(Flag.SOLID),
	HAY(Flag.SOLID),
	/** Abobora esculpida virada para a direcao. */
	PUMPKIN(Flag.SOLID),
	CROP_WHEAT(Flag.LATE),
	CROP_CARROT(Flag.LATE),
	CROP_POTATO(Flag.LATE),
	CROP_BEET(Flag.LATE),
	/** Terraplenagem: grama no topo do chao aplainado (natural; nao entra no registro de demolicao). */
	TURF(Flag.SOLID | Flag.GROUND | Flag.EARTHWORK),
	/** Terraplenagem: terra do aterro. */
	EARTH(Flag.SOLID | Flag.EARTHWORK),
	/** Terraplenagem: pedra natural no fundo do aterro. */
	ROCK(Flag.SOLID | Flag.EARTHWORK),
	/** Terraplenagem: muro de arrimo na borda de aterro alto. */
	RETAIN(Flag.SOLID | Flag.EARTHWORK),
	/** Muda de arvore (replantio nos arredores depois da terraplenagem). */
	SAPLING(Flag.LATE),
	/** Terraplenagem: tira a agua rasa (e o que for natural) para o chao ficar seco. */
	DRAIN(Flag.EARTHWORK);

	private static final class Flag {
		static final int NONE = 0;
		static final int SOLID = 1;
		static final int FILL_STONE = 2;
		static final int FILL_DIRT = 4;
		static final int GROUND = 8;
		static final int PASSABLE = 16;
		static final int LATE = 32;
		static final int SKIP = 64;
		static final int CLIMB = 128;
		static final int STANDABLE_SOFT = 256;
		static final int EARTHWORK = 512;
	}

	private final int flags;

	VPart(int flags) {
		this.flags = flags;
	}

	/** Ocupa o espaco (o jogador nao atravessa e pode ficar em cima). */
	public boolean solid() {
		return (flags & Flag.SOLID) != 0;
	}

	/** Da para ficar em pe em cima (solido ou cama). */
	public boolean standable() {
		return solid() || (flags & Flag.STANDABLE_SOFT) != 0;
	}

	/** Porta/portao: o jogador passa. */
	public boolean passable() {
		return (flags & Flag.PASSABLE) != 0;
	}

	/** Escada de mao. */
	public boolean climbable() {
		return (flags & Flag.CLIMB) != 0;
	}

	/** Na camada do chao, enche por baixo com pedra (alicerce) ate achar terreno. */
	public boolean fillsStone() {
		return (flags & Flag.FILL_STONE) != 0;
	}

	/** Na camada do chao, enche por baixo com terra ate achar terreno. */
	public boolean fillsDirt() {
		return (flags & Flag.FILL_DIRT) != 0;
	}

	/** Bloco do proprio chao (troca o topo do terreno). */
	public boolean ground() {
		return (flags & Flag.GROUND) != 0;
	}

	/** Precisa de apoio (vai por ultimo na construcao). */
	public boolean late() {
		return (flags & Flag.LATE) != 0;
	}

	/** Bloco de terraplenagem (corte/aterro), nao faz parte da construcao. */
	public boolean earthwork() {
		return (flags & Flag.EARTHWORK) != 0;
	}

	/** So reserva espaco no plano (a ponte coloca junto com outra parte). */
	public boolean skip() {
		return (flags & Flag.SKIP) != 0;
	}
}
