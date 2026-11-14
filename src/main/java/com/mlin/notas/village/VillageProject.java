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
					VillageCost.of("minecraft:emerald", 8), VillageCost.of("minecraft:lectern", 1)));

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
