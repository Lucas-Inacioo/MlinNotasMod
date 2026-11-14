package com.mlin.notas.village;

import java.util.List;

/**
 * Projetos da vila (logica pura): entregas grandes, feitas aos poucos por qualquer jogador, que constroem uma melhoria
 * de verdade no mundo. Cada um tem custo, prosperidade ganha, nivel minimo da vila, quantas vezes pode estar pronto em
 * cada nivel e quantos aldeoes/golens chegam quando fica pronto.
 */
public enum VillageProject {
	/** Praca calcada em volta do quadro, com poco, bancos, canteiros e postes. */
	PLAZA("plaza", "minecraft:cobblestone", 0, 40, new int[] {1, 1, 1, 1, 1, 1, 1}, 0, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 64), VillageCost.of("#minecraft:logs", 8), VillageCost.of("minecraft:water_bucket", 1))),
	/** Casa nova com cama: chega um aldeao. */
	HOUSE("house", "minecraft:oak_door", 0, 35, new int[] {2, 4, 6, 8, 10, 12, 14}, 1, 0,
			List.of(VillageCost.of("#minecraft:planks", 64), VillageCost.of("#minecraft:logs", 12), VillageCost.of("#mlinnotas:village/glass", 6),
					VillageCost.of("#minecraft:beds", 1))),
	/** Plantacao irrigada com composteira: chega um fazendeiro. */
	FARM("farm", "minecraft:wheat", 0, 30, new int[] {1, 2, 3, 4, 4, 5, 6}, 1, 0,
			List.of(VillageCost.of("#mlinnotas:village/food", 32), VillageCost.of("#mlinnotas:village/seeds", 16))),
	/** Postes com lanterna nos caminhos e na praca. */
	LAMPS("lamps", "minecraft:lantern", 0, 20, new int[] {1, 1, 2, 2, 3, 3, 4}, 0, 0,
			List.of(VillageCost.of("minecraft:torch", 24), VillageCost.of("#minecraft:wooden_fences", 12))),
	/** Forja com bigorna, alto-forno e mesa de ferraria: chega um ferreiro e um golem de ferro. */
	SMITHY("smithy", "minecraft:anvil", 1, 60, new int[] {0, 1, 1, 1, 1, 1, 1}, 1, 1,
			List.of(VillageCost.of("minecraft:iron_ingot", 24), VillageCost.of("#mlinnotas:village/stone", 48), VillageCost.of("#minecraft:coals", 8))),
	/** Mercado com barracas: chegam dois comerciantes (pedreiro, pastor...). */
	MARKET("market", "minecraft:emerald", 1, 50, new int[] {0, 1, 1, 1, 1, 1, 1}, 2, 0,
			List.of(VillageCost.of("minecraft:emerald", 16), VillageCost.of("#minecraft:planks", 32), VillageCost.of("#minecraft:wool", 12))),
	/** Muralha octogonal com torres e quatro portoes. */
	WALL("wall", "minecraft:stone_bricks", 1, 100, new int[] {0, 1, 1, 1, 1, 1, 1}, 0, 1,
			List.of(VillageCost.of("#mlinnotas:village/stone", 384), VillageCost.of("#minecraft:logs", 32))),
	/** Estalagem de dois andares com quartos: chegam dois aldeoes. */
	INN("inn", "minecraft:red_bed", 2, 70, new int[] {0, 0, 1, 1, 1, 1, 1}, 2, 0,
			List.of(VillageCost.of("#minecraft:wool", 24), VillageCost.of("#minecraft:beds", 3), VillageCost.of("#minecraft:planks", 64),
					VillageCost.of("#minecraft:logs", 16))),
	/** Biblioteca com estantes e atris: chega um bibliotecario. */
	LIBRARY("library", "minecraft:bookshelf", 2, 70, new int[] {0, 0, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("minecraft:book", 24), VillageCost.of("#minecraft:planks", 32), VillageCost.of("#minecraft:logs", 8),
					VillageCost.of("#mlinnotas:village/glass", 6))),
	/**
	 * Prefeitura (centro, niveis I-V): limita o nivel das outras construcoes e abre o painel de necessidades. O nivel
	 * dela vai ate o teto do nivel da vila ({@link BuildingTypes#townHallCap}).
	 */
	TOWN_HALL("town_hall", "minecraft:bell", 1, 60, new int[] {0, 1, 1, 1, 1, 1, 1}, 0, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 96), VillageCost.of("#minecraft:logs", 24), VillageCost.of("#mlinnotas:village/glass", 12),
					VillageCost.of("minecraft:emerald", 8), VillageCost.of("minecraft:lectern", 1))),
	// ---- Vilas 2.0 fase 5: producao e comida (ver FoodBuildings). Novos valores sempre no fim: os saves usam o id.
	/** Celeiro: estoque de comida da vila; as plantacoes, o pomar, o curral e a padaria abastecem sozinhos. */
	GRANARY("granary", "minecraft:hay_block", 1, 45, new int[] {0, 1, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#minecraft:planks", 48), VillageCost.of("#minecraft:logs", 16), VillageCost.of("minecraft:hay_block", 4),
					VillageCost.of("#mlinnotas:village/food", 16))),
	/** Moinho de vento: mo o trigo em farinha com rendimento melhor. */
	MILL("mill", "minecraft:grindstone", 1, 45, new int[] {0, 1, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 64), VillageCost.of("#minecraft:planks", 24), VillageCost.of("#minecraft:logs", 12),
					VillageCost.of("#minecraft:wool", 8), VillageCost.of("minecraft:wheat", 16))),
	/** Padaria: o forno assa farinha e ingredientes do bau sozinho; o padeiro vende pao. */
	BAKERY("bakery", "minecraft:bread", 2, 60, new int[] {0, 0, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 48), VillageCost.of("#minecraft:planks", 24), VillageCost.of("#minecraft:logs", 8),
					VillageCost.of("minecraft:bricks", 16), VillageCost.of("minecraft:wheat", 16))),
	/** Pomar: macieiras e frutas da estacao, colhidas com o botao direito. */
	ORCHARD("orchard", "minecraft:apple", 1, 40, new int[] {0, 1, 1, 1, 1, 1, 2}, 1, 0,
			List.of(VillageCost.of("#minecraft:saplings", 6), VillageCost.of("#minecraft:wooden_fences", 16), VillageCost.of("#minecraft:logs", 8),
					VillageCost.of("#minecraft:planks", 16), VillageCost.of("minecraft:apple", 6))),
	/** Curral: vacas, ovelhas e porcos que se reproduzem ate o limite do nivel; leite e la no bau. */
	PEN("pen", "minecraft:white_wool", 1, 40, new int[] {0, 1, 1, 1, 1, 1, 2}, 1, 0,
			List.of(VillageCost.of("#minecraft:wooden_fences", 32), VillageCost.of("#minecraft:planks", 16), VillageCost.of("#minecraft:logs", 8),
					VillageCost.of("minecraft:hay_block", 6), VillageCost.of("minecraft:wheat", 16))),
	/** Apiario: colmeias, flores e mel no bau. */
	APIARY("apiary", "minecraft:honeycomb", 2, 50, new int[] {0, 0, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#minecraft:planks", 32), VillageCost.of("#minecraft:logs", 8), VillageCost.of("minecraft:honeycomb", 8),
					VillageCost.of("#minecraft:flowers", 16), VillageCost.of("minecraft:glass_bottle", 4))),
	/** Acougue: acougueiro, cortes de carne e charque mais barato. */
	BUTCHER("butcher", "minecraft:cooked_beef", 2, 50, new int[] {0, 0, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 40), VillageCost.of("#minecraft:planks", 24), VillageCost.of("#minecraft:logs", 8),
					VillageCost.of("minecraft:iron_ingot", 6), VillageCost.of("#minecraft:coals", 8))),
	/** Vinicola: parreiral de uvas no caramanchao, cervejaria e adega. */
	WINERY("winery", "minecraft:glass_bottle", 3, 90, new int[] {0, 0, 0, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 64), VillageCost.of("#minecraft:logs", 24), VillageCost.of("#minecraft:planks", 32),
					VillageCost.of("minecraft:barrel", 4), VillageCost.of("mlinnotas:grape_seeds", 8), VillageCost.of("minecraft:emerald", 6))),
	/** Serraria: carpinteiro, tabuas mais baratas e uma bancada que faz 6 tabuas por tronco. */
	SAWMILL("sawmill", "minecraft:stonecutter", 2, 55, new int[] {0, 0, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#minecraft:logs", 32), VillageCost.of("#minecraft:planks", 16), VillageCost.of("#mlinnotas:village/stone", 24),
					VillageCost.of("minecraft:iron_ingot", 8))),
	/** Correio (Cidade, centro, I-II): caixa de correio que manda itens para outra vila ou outro jogador, entregues no dia seguinte. */
	POST_OFFICE("post_office", "minecraft:paper", 3, 60, new int[] {0, 0, 0, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("minecraft:paper", 32), VillageCost.of("#minecraft:planks", 48), VillageCost.of("#minecraft:logs", 12),
					VillageCost.of("#mlinnotas:village/stone", 48), VillageCost.of("#mlinnotas:village/glass", 6), VillageCost.of("minecraft:emerald", 12))),
	/** Escola (Cidade, bairros, I-II): filhotes crescem mais rapido e novatos sobem de nivel antes. */
	SCHOOL("school", "minecraft:book", 3, 70, new int[] {0, 0, 0, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("minecraft:book", 12), VillageCost.of("#minecraft:planks", 48), VillageCost.of("#minecraft:logs", 12),
					VillageCost.of("#mlinnotas:village/stone", 48), VillageCost.of("#mlinnotas:village/glass", 12), VillageCost.of("minecraft:emerald", 12))),
	/** Torre de vigia (Cidade, junto da muralha, I-III): sino avisa de saqueadores, monstros no minimapa a noite, arqueiros no III. */
	WATCHTOWER("watchtower", "minecraft:spyglass", 3, 80, new int[] {0, 0, 0, 1, 1, 1, 1}, 0, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 160), VillageCost.of("#minecraft:logs", 24), VillageCost.of("minecraft:bell", 1),
					VillageCost.of("minecraft:iron_ingot", 12), VillageCost.of("minecraft:arrow", 16))),
	/** Quartel (Capital, arredores, I-III): guardas de armadura patrulham as ruas a noite (2/4/6). */
	BARRACKS("barracks", "minecraft:iron_sword", 4, 120, new int[] {0, 0, 0, 0, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 192), VillageCost.of("#minecraft:logs", 32), VillageCost.of("minecraft:iron_ingot", 48),
					VillageCost.of("#minecraft:planks", 48), VillageCost.of("#minecraft:wool", 12), VillageCost.of("minecraft:emerald", 24))),
	/** Pedra de teleporte da vila (Vila, centro): ja ativa; viajar entre pedras das suas vilas custa metade. */
	VILLAGE_WAYSTONE("village_waystone", "mlinnotas:waystone", 2, 40, new int[] {0, 0, 1, 1, 1, 1, 1}, 0, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 32), VillageCost.of("minecraft:ender_pearl", 2), VillageCost.of("mlinnotas:cobalt_ingot", 4),
					VillageCost.of("minecraft:emerald", 16))),
	/** Farol comunitario (Capital, centro, I-V): piramide de metal; o efeito vale para todos dentro da muralha. */
	BEACON("beacon", "minecraft:beacon", 4, 100, new int[] {0, 0, 0, 0, 1, 1, 1}, 0, 0,
			List.of(VillageCost.of("#mlinnotas:village/glass", 12), VillageCost.of("minecraft:obsidian", 8), VillageCost.of("minecraft:iron_ingot", 36),
					VillageCost.of("#mlinnotas:village/stone", 64), VillageCost.of("minecraft:emerald", 32))),
	/** Museu (Capital, centro, I-III): colecoes estilo Stardew com recompensa e prosperidade; doacoes em molduras. */
	MUSEUM("museum", "minecraft:item_frame", 4, 150, new int[] {0, 0, 0, 0, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("minecraft:item_frame", 24), VillageCost.of("#mlinnotas:village/stone", 128), VillageCost.of("#minecraft:planks", 64),
					VillageCost.of("#mlinnotas:village/glass", 24), VillageCost.of("minecraft:emerald", 48), VillageCost.of("minecraft:book", 8))),
	/** Guilda dos Exploradores (Cidade, bairros/arredores, I-V): manda aldeoes em expedicoes. */
	EXPLORERS_GUILD("explorers_guild", "minecraft:compass", 3, 90, new int[] {0, 0, 0, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#minecraft:planks", 64), VillageCost.of("#minecraft:logs", 24), VillageCost.of("#mlinnotas:village/stone", 64),
					VillageCost.of("minecraft:iron_ingot", 16), VillageCost.of("minecraft:compass", 2), VillageCost.of("minecraft:emerald", 24)));

	private final String id;
	private final String icon;
	private final int minLevel;
	private final int prosperity;
	private final int[] maxByLevel;
	private final int villagers;
	private final int golems;
	private final List<VillageCost> costs;

	VillageProject(String id, String icon, int minLevel, int prosperity, int[] maxByLevel, int villagers, int golems, List<VillageCost> costs) {
		this.id = id;
		this.icon = icon;
		this.minLevel = minLevel;
		this.prosperity = prosperity;
		this.maxByLevel = maxByLevel;
		this.villagers = villagers;
		this.golems = golems;
		this.costs = costs;
	}

	public String id() {
		return id;
	}

	/** Item mostrado na tela. */
	public String icon() {
		return icon;
	}

	public VillageLevel minLevel() {
		return VillageLevel.byOrdinal(minLevel);
	}

	public int prosperity() {
		return prosperity;
	}

	/** Quantas vezes pode estar pronto com a vila no nivel {@code level}. */
	public int maxBuilds(VillageLevel level) {
		return maxByLevel[Math.min(level.ordinal(), maxByLevel.length - 1)];
	}

	public int villagers() {
		return villagers;
	}

	public int golems() {
		return golems;
	}

	public List<VillageCost> costs() {
		return costs;
	}

	public int count(String costId) {
		for (VillageCost cost : costs) {
			if (cost.id().equals(costId)) {
				return cost.count();
			}
		}
		return 0;
	}

	public static VillageProject byId(String id) {
		for (VillageProject project : values()) {
			if (project.id.equals(id)) {
				return project;
			}
		}
		return null;
	}
}
