package com.mlin.notas.village.civic;

import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.LotRules;
import com.mlin.notas.village.SizeClass;
import com.mlin.notas.village.VillageCost;
import com.mlin.notas.village.VillageNeeds.Need;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.Zone;

import java.util.List;
import java.util.Set;

/**
 * Registro das construcoes civicas e de defesa (Vilas 2.0, fase 7): Correio, Escola, Torre de vigia, Quartel, Pedra de
 * teleporte da vila, Farol comunitario, Museu e Guilda dos Exploradores. Tipos com nivel em {@link BuildingTypes}
 * (trabalhadores e necessidades por nivel) e lotes em {@link LotRules}; chamado dos blocos estaticos dessas duas
 * classes para os testes puros tambem enxergarem os tipos.
 *
 * <p>Lote: Correio/Escola/Guilda M, Torre de vigia S (a faixa W so abre na Capital, e a torre e exigida para ela, entao
 * fica no fim dos bairros: {@link LotRules#preferOuter}), Quartel M nos arredores, Museu L no centro, Farol S no
 * centro e a pedra de teleporte T.
 */
public final class CivicBuildings {
	public static final String POST_OFFICE = "post_office";
	public static final String SCHOOL = "school";
	public static final String WATCHTOWER = "watchtower";
	public static final String BARRACKS = "barracks";
	public static final String WAYSTONE = "village_waystone";
	public static final String BEACON = "beacon";
	public static final String MUSEUM = "museum";
	public static final String GUILD = "explorers_guild";

	private CivicBuildings() {
	}

	/** Tipos com nivel (trabalhadores, necessidades). */
	public static void registerTypes() {
		BuildingTypes.register(BuildingTypes.type(POST_OFFICE).maxLevel(2).workers(1).need(Need.COMMERCE, 10, 18).need(Need.LEISURE, 3, 5));
		BuildingTypes.register(BuildingTypes.type(SCHOOL).maxLevel(2).workers(1).need(Need.LEISURE, 15, 25));
		BuildingTypes.register(BuildingTypes.type(WATCHTOWER).maxLevel(3).workers(0).need(Need.SAFETY, 20, 30, 45).need(Need.LIGHT, 4, 5, 8));
		BuildingTypes.register(BuildingTypes.type(BARRACKS).maxLevel(3).workers(1).need(Need.SAFETY, 30, 50, 70));
		BuildingTypes.register(BuildingTypes.type(WAYSTONE).maxLevel(1).workers(0).need(Need.COMMERCE, 5).need(Need.LEISURE, 5));
		BuildingTypes.register(BuildingTypes.type(BEACON).maxLevel(5).workers(0).need(Need.LIGHT, 20, 30, 40, 50, 60).need(Need.SAFETY, 4, 6, 8, 10, 12));
		BuildingTypes.register(BuildingTypes.type(MUSEUM).maxLevel(3).workers(1).need(Need.LEISURE, 30, 45, 60));
		BuildingTypes.register(BuildingTypes.type(GUILD).maxLevel(5).workers(1).need(Need.COMMERCE, 8, 12, 16, 20, 25).need(Need.LEISURE, 4, 6, 8, 10, 12));
	}

	/** Lotes de cada tipo. */
	public static void registerLots() {
		LotRules.register(POST_OFFICE, SizeClass.M, false, Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register(SCHOOL, SizeClass.M, false, Zone.DISTRICT, Zone.CENTER, Zone.OUTSKIRTS);
		LotRules.register(WATCHTOWER, SizeClass.S, false, Zone.DISTRICT);
		LotRules.preferOuter(WATCHTOWER);
		LotRules.register(BARRACKS, SizeClass.M, false, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.preferOuter(BARRACKS);
		LotRules.register(WAYSTONE, SizeClass.T, false, Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register(BEACON, SizeClass.S, false, Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register(MUSEUM, SizeClass.L, false, Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register(GUILD, SizeClass.M, false, Zone.DISTRICT, Zone.OUTSKIRTS);
	}

	/** Projetos civicos que ocupam lote (para {@code VillageLayout.LOT_PROJECTS}). */
	public static final List<VillageProject> PROJECTS = List.of(VillageProject.POST_OFFICE, VillageProject.SCHOOL, VillageProject.WATCHTOWER,
			VillageProject.BARRACKS, VillageProject.VILLAGE_WAYSTONE, VillageProject.BEACON, VillageProject.MUSEUM, VillageProject.EXPLORERS_GUILD);

	/** Quem aparece no mercado/menus: ids. */
	public static final Set<String> IDS = Set.of(POST_OFFICE, SCHOOL, WATCHTOWER, BARRACKS, WAYSTONE, BEACON, MUSEUM, GUILD);

	/**
	 * Materiais a mais da melhoria para o nivel: o Farol sobe com o metal da piramide do nivel (ouro, cobalto e diamante,
	 * netherite, celestio).
	 */
	public static List<VillageCost> upgradeExtras(VillageProject project, int level) {
		if (project != VillageProject.BEACON) {
			return List.of();
		}
		return switch (level) {
			case 2 -> List.of(VillageCost.of("minecraft:gold_ingot", 48));
			case 3 -> List.of(VillageCost.of("mlinnotas:cobalt_ingot", 24), VillageCost.of("minecraft:diamond", 12));
			case 4 -> List.of(VillageCost.of("minecraft:netherite_ingot", 6));
			case 5 -> List.of(VillageCost.of("mlinnotas:celestium_ingot", 4));
			default -> List.of();
		};
	}
}
