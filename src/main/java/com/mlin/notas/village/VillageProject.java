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
	// ---- Vilas 2.0 fase 6: oficios e servicos (apendice no fim do enum: ids salvos por nome, nunca por ordem) ----
	/** Pedreira (arredores, I-III): o pedreiro enche um bau de pedra aos poucos e a vila ganha desconto em material de rua. */
	QUARRY("quarry", "minecraft:stonecutter", 2, 45, new int[] {0, 0, 1, 1, 1, 2, 2}, 1, 0,
			List.of(VillageCost.of("minecraft:iron_ingot", 12), VillageCost.of("#minecraft:planks", 32), VillageCost.of("#minecraft:logs", 12),
					VillageCost.of("minecraft:stonecutter", 1))),
	/** Alfaiataria (bairros, I-II): tecelao, la tingida, tapetes e mochila mais barata. */
	TAILOR("tailor", "minecraft:loom", 2, 40, new int[] {0, 0, 1, 1, 1, 1, 2}, 1, 0,
			List.of(VillageCost.of("#minecraft:wool", 24), VillageCost.of("#minecraft:planks", 32), VillageCost.of("minecraft:string", 16),
					VillageCost.of("minecraft:loom", 1))),
	/** Curtume (arredores, I-II): unico lugar onde a carne podre vira couro (tina de curtir). */
	TANNERY("tannery", "minecraft:leather", 2, 40, new int[] {0, 0, 1, 1, 1, 1, 2}, 1, 0,
			List.of(VillageCost.of("minecraft:leather", 16), VillageCost.of("#minecraft:planks", 24), VillageCost.of("#minecraft:logs", 12),
					VillageCost.of("minecraft:cauldron", 1))),
	/** Flecharia (bairros, I-III): flecheiro, flechas com pocao mais baratas e um campo de tiro. */
	FLETCHER("fletcher", "minecraft:fletching_table", 2, 40, new int[] {0, 0, 1, 1, 1, 1, 2}, 1, 0,
			List.of(VillageCost.of("minecraft:arrow", 32), VillageCost.of("minecraft:feather", 16), VillageCost.of("#minecraft:planks", 24),
					VillageCost.of("minecraft:flint", 16))),
	/** Encantador (centro, Cidade, I-V): o unico lugar onde se encanta; o nivel da casa limita o nivel do encantamento. */
	ENCHANTER("enchanter", "minecraft:enchanting_table", 3, 90, new int[] {0, 0, 0, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("minecraft:diamond", 4), VillageCost.of("minecraft:obsidian", 8), VillageCost.of("minecraft:book", 8),
					VillageCost.of("#mlinnotas:village/stone", 64), VillageCost.of("minecraft:lapis_lazuli", 16), VillageCost.of("minecraft:emerald", 16))),
	/** Taverna (centro, I-III): vende boatos sobre estruturas ainda nao achadas e uma missao extra por dia. */
	TAVERN("tavern", "minecraft:barrel", 2, 60, new int[] {0, 0, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#minecraft:planks", 48), VillageCost.of("#minecraft:logs", 16), VillageCost.of("minecraft:barrel", 4),
					VillageCost.of("minecraft:emerald", 12), VillageCost.of("minecraft:bread", 8))),
	/** Jardim (qualquer zona, I-III): lago, ponte, flores da estacao e bancos. Lazer. */
	GARDEN("garden", "minecraft:oxeye_daisy", 1, 25, new int[] {0, 1, 1, 2, 2, 3, 3}, 0, 0,
			List.of(VillageCost.of("minecraft:water_bucket", 1), VillageCost.of("#minecraft:flowers", 8), VillageCost.of("#mlinnotas:village/stone", 16),
					VillageCost.of("#minecraft:planks", 12))),
	/** Igreja (centro, I-III): clerigo, sino ao anoitecer, bencao do altar e cemiterio no III. Lazer e fe. */
	CHURCH("church", "minecraft:bell", 2, 70, new int[] {0, 0, 1, 1, 1, 1, 1}, 1, 0,
			List.of(VillageCost.of("#mlinnotas:village/stone", 128), VillageCost.of("#mlinnotas:village/glass", 16), VillageCost.of("#minecraft:logs", 16),
					VillageCost.of("minecraft:bell", 1), VillageCost.of("minecraft:emerald", 12), VillageCost.of("#minecraft:wool", 8))),
	/** Lote vazio (bairros): patamar preparado com estacas para o jogador construir a propria casa; a vila nunca mexe nele. */
	EMPTY_LOT("empty_lot", "minecraft:oak_fence", 1, 5, new int[] {0, 1, 1, 2, 2, 2, 2}, 0, 0,
			List.of(VillageCost.of("#minecraft:wooden_fences", 8), VillageCost.of("minecraft:emerald", 2)));

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
