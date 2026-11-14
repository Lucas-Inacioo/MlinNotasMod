package com.mlin.notas.village;

/**
 * Registro das construcoes de producao e comida (Vilas 2.0, fase 5): celeiro, moinho, padaria, pomar, curral, apiario,
 * acougue, vinicola e serraria. Tipos com nivel em {@link BuildingTypes} (trabalhador, moradores, pontos de
 * necessidade por nivel) e lotes em {@link LotRules}. As plantas ficam em {@link FoodPlans}, os numeros de cada funcao
 * em {@link FoodRules} e o lado servidor em {@code FoodBuildingsServer}. Chamado dos blocos estaticos de
 * {@link BuildingTypes} e {@link LotRules} (assim os testes puros ja veem os tipos).
 */
public final class FoodBuildings {
	/** Oferta do mercado -> construcao funcionando no nivel (tipo:nivel); o resto de {@link VillageShop#REQUIRES}. */
	public static final java.util.Map<String, String> SHOP_REQUIRES = java.util.Map.ofEntries(
			java.util.Map.entry("mill_flour", "mill:1"), java.util.Map.entry("bakery_bread", "bakery:1"), java.util.Map.entry("bakery_broa", "bakery:1"),
			java.util.Map.entry("bakery_cheese_bread", "bakery:2"), java.util.Map.entry("bakery_cake", "bakery:3"),
			java.util.Map.entry("orchard_apples", "orchard:1"), java.util.Map.entry("pen_wool", "pen:1"), java.util.Map.entry("pen_milk", "pen:2"),
			java.util.Map.entry("apiary_honey", "apiary:1"), java.util.Map.entry("apiary_comb", "apiary:2"),
			java.util.Map.entry("butcher_beef", "butcher:1"), java.util.Map.entry("butcher_pork", "butcher:1"),
			java.util.Map.entry("butcher_jerky", "butcher:1"), java.util.Map.entry("butcher_jerky_bulk", "butcher:2"),
			java.util.Map.entry("winery_wine", "winery:1"), java.util.Map.entry("winery_beer", "winery:2"), java.util.Map.entry("winery_cider", "winery:3"),
			java.util.Map.entry("winery_grape_seeds", "winery:1"), java.util.Map.entry("sawmill_planks", "sawmill:1"),
			java.util.Map.entry("sawmill_planks_bulk", "sawmill:2"), java.util.Map.entry("sawmill_planks_birch", "sawmill:3"));

	private FoodBuildings() {
	}

	/** Niveis, trabalhadores e necessidades atendidas. */
	static void registerTypes() {
		BuildingTypes.register(BuildingTypes.type("granary").maxLevel(3).workers(1).need(VillageNeeds.Need.FOOD, 10, 16, 24));
		BuildingTypes.register(BuildingTypes.type("mill").maxLevel(3).workers(1).need(VillageNeeds.Need.FOOD, 4, 7, 10)
				.need(VillageNeeds.Need.COMMERCE, 2, 3, 5));
		BuildingTypes.register(BuildingTypes.type("bakery").maxLevel(3).workers(1).need(VillageNeeds.Need.FOOD, 5, 8, 12)
				.need(VillageNeeds.Need.COMMERCE, 4, 6, 8));
		BuildingTypes.register(BuildingTypes.type("orchard").maxLevel(3).workers(1).need(VillageNeeds.Need.FOOD, 4, 7, 10));
		BuildingTypes.register(BuildingTypes.type("pen").maxLevel(3).workers(1).need(VillageNeeds.Need.FOOD, 5, 8, 12));
		BuildingTypes.register(BuildingTypes.type("apiary").maxLevel(2).workers(1).need(VillageNeeds.Need.FOOD, 2, 4)
				.need(VillageNeeds.Need.COMMERCE, 2, 4));
		BuildingTypes.register(BuildingTypes.type("butcher").maxLevel(2).workers(1).need(VillageNeeds.Need.FOOD, 4, 7)
				.need(VillageNeeds.Need.COMMERCE, 4, 6));
		BuildingTypes.register(BuildingTypes.type("winery").maxLevel(3).workers(1).need(VillageNeeds.Need.LEISURE, 6, 10, 14)
				.need(VillageNeeds.Need.COMMERCE, 6, 10, 14));
		BuildingTypes.register(BuildingTypes.type("sawmill").maxLevel(3).workers(1).need(VillageNeeds.Need.COMMERCE, 4, 6, 9));
	}

	/** Classe de lote (no nivel maximo) e zonas de cada tipo. Rurais aceitam o fundo dos bairros ate os arredores abrirem. */
	static void registerLots() {
		LotRules.register("granary", SizeClass.M, true, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register("mill", SizeClass.M, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.register("bakery", SizeClass.S, false, Zone.CENTER, Zone.DISTRICT);
		LotRules.register("orchard", SizeClass.M, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.register("pen", SizeClass.M, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.register("apiary", SizeClass.S, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.register("butcher", SizeClass.S, false, Zone.DISTRICT, Zone.CENTER);
		LotRules.register("winery", SizeClass.L, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.register("sawmill", SizeClass.M, false, Zone.DISTRICT, Zone.OUTSKIRTS);
	}
}
