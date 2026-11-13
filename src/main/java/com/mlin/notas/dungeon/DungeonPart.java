package com.mlin.notas.dungeon;

/**
 * Papel de cada bloco no plano da masmorra ({@link DungeonPlan}). A peca troca cada papel pelo bloco da paleta do
 * tema. Partes orientadas usam a direcao do plano. Logica pura.
 *
 * <p>{@code solid}: da para ficar em pe em cima e nao da para atravessar. {@code passable}: o corpo atravessa
 * (ar, tapete, placa, redstone...). {@code support}: do que o bloco precisa para nao ficar solto.</p>
 */
public enum DungeonPart {
	AIR(false, true, Support.NONE),
	/** Pedra da parede; a peca mistura as variantes por peso (textura). */
	WALL(true, false, Support.NONE),
	WALL_ALT(true, false, Support.NONE),
	WALL_MOSSY(true, false, Support.NONE),
	WALL_CRACKED(true, false, Support.NONE),
	/** Ladrilho principal do chao. */
	FLOOR(true, false, Support.NONE),
	/** Ladrilho secundario (xadrez, faixas). */
	FLOOR_ALT(true, false, Support.NONE),
	/** Borda do chao junto as paredes e caminhos. */
	FLOOR_TRIM(true, false, Support.NONE),
	/** Pilar/coluna. */
	PILLAR(true, false, Support.NONE),
	/** Pedra de destaque (cantoneiras, frisos, verga das portas). */
	ACCENT(true, false, Support.NONE),
	/** Viga de madeira: deitada ao longo da direcao, ou em pe sem direcao. */
	BEAM(true, false, Support.NONE),
	PLANKS(true, false, Support.NONE),
	STAIRS(true, false, Support.NONE),
	/** Degrau de ponta-cabeca (misulas, arcos, bancos). */
	STAIRS_TOP(true, false, Support.NONE),
	SLAB(true, false, Support.NONE),
	SLAB_TOP(true, false, Support.NONE),
	/** Muro de pedra (parapeitos, lapides, grades das escadas). */
	STONE_WALL(true, false, Support.NONE),
	/** Bloco que brilha embutido na parede (lanterna do mar, cogumelo luminoso...). */
	GLOW(true, false, Support.NONE),
	/** Bloco de tesouro decorativo (ouro, minerio bruto). */
	TREASURE(true, false, Support.NONE),
	/** Veio de minerio na parede (fortaleza ana). */
	ORE(true, false, Support.NONE),
	/** Areia/cascalho solto do chao; {@link #SUSPICIOUS} esconde arqueologia. */
	SAND(true, false, Support.NONE),
	SUSPICIOUS(true, false, Support.NONE),
	/** Bloco de coral, osso ou outra decoracao macica do tema. */
	DECOR_BLOCK(true, false, Support.NONE),
	BOOKSHELF(true, false, Support.NONE),
	BARREL(true, false, Support.FLOOR),
	/** Bau com loot; o tipo do loot fica em {@link DungeonPlan#chests()}. */
	CHEST(true, false, Support.FLOOR),
	/** Cofre (um premio por jogador, abre com a Chave da Masmorra). */
	VAULT(true, false, Support.FLOOR),
	SPAWNER(true, false, Support.FLOOR),
	/** Portao selado da camara final (abre com a chave). */
	GATE(true, false, Support.NONE),
	/** Porta de ferro (metade de baixo / de cima), so abre por redstone. */
	IRON_DOOR(true, false, Support.FLOOR),
	IRON_DOOR_TOP(true, false, Support.NONE),
	/** Alavanca na parede (aponta para a direcao). */
	LEVER(false, true, Support.WALL),
	/** Botao na parede. */
	BUTTON(false, true, Support.WALL),
	/** Placa de pressao de armadilha (pedra) e o ladrilho de aviso embaixo dela. */
	TRAP_PLATE(false, true, Support.FLOOR),
	TRAP_FLOOR(true, false, Support.NONE),
	/** Ejetor com flechas na parede, apontando para a direcao. */
	DISPENSER(true, false, Support.NONE),
	/** Placa de madeira do enigma (qualquer coisa em cima ativa: jogador, mob, item jogado). */
	PUZZLE_PLATE(false, true, Support.FLOOR),
	REDSTONE(false, true, Support.FLOOR),
	IRON_BARS(true, false, Support.NONE),
	WATER(false, true, Support.NONE),
	/** Lava atras de grades (forja). */
	LAVA(false, false, Support.NONE),
	/** Tocha na parede (aponta para a direcao). */
	WALL_TORCH(false, true, Support.WALL),
	LANTERN(false, false, Support.FLOOR),
	LANTERN_HANGING(false, true, Support.CEILING),
	CHAIN(false, true, Support.CEILING),
	CANDLE(false, true, Support.FLOOR),
	SKULL(false, false, Support.FLOOR),
	COBWEB(false, true, Support.NONE),
	CARPET(false, true, Support.FLOOR),
	/** Planta do tema (coral em leque, pepino-do-mar, arbusto seco...). */
	PLANT(false, true, Support.FLOOR),
	DECORATED_POT(false, false, Support.FLOOR),
	ANVIL(true, false, Support.FLOOR),
	/** Bancada do tema (mesa de ferraria, alto-forno, pedra de amolar...). */
	WORKSTATION(true, false, Support.FLOOR),
	CAMPFIRE(false, false, Support.FLOOR),
	LECTERN(true, false, Support.FLOOR);

	public enum Support {
		NONE, FLOOR, CEILING, WALL
	}

	private final boolean solid;
	private final boolean passable;
	private final Support support;

	DungeonPart(boolean solid, boolean passable, Support support) {
		this.solid = solid;
		this.passable = passable;
		this.support = support;
	}

	/** Da para ficar em pe em cima. */
	public boolean solid() {
		return solid;
	}

	/** O corpo do jogador atravessa. */
	public boolean passable() {
		return passable;
	}

	public Support support() {
		return support;
	}
}
