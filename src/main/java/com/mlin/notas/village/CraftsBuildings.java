package com.mlin.notas.village;

/**
 * Registros das construcoes de oficios e servicos (Vilas 2.0, fase 6): tipos com nivel, trabalhadores, necessidades
 * e as regras de lote. Chamado de uma linha no bloco estatico de {@link BuildingTypes} e de {@link LotRules}.
 */
final class CraftsBuildings {
	static final String QUARRY = "quarry";
	static final String TAILOR = "tailor";
	static final String TANNERY = "tannery";
	static final String FLETCHER = "fletcher";
	static final String ENCHANTER = "enchanter";
	static final String TAVERN = "tavern";
	static final String GARDEN = "garden";
	static final String CHURCH = "church";
	static final String EMPTY_LOT = "empty_lot";

	private CraftsBuildings() {
	}

	/** Tipos com nivel (niveis maximos: pedreira III, alfaiataria II, curtume II, flecharia III, encantador V, taverna III, jardim III, igreja III). */
	static void registerTypes() {
		BuildingTypes.register(BuildingTypes.type(QUARRY).maxLevel(3).workers(1).need(VillageNeeds.Need.COMMERCE, 3, 5, 8));
		BuildingTypes.register(BuildingTypes.type(TAILOR).maxLevel(2).workers(1).need(VillageNeeds.Need.COMMERCE, 6, 10));
		BuildingTypes.register(BuildingTypes.type(TANNERY).maxLevel(2).workers(1).need(VillageNeeds.Need.COMMERCE, 4, 7));
		BuildingTypes.register(BuildingTypes.type(FLETCHER).maxLevel(3).workers(1).need(VillageNeeds.Need.SAFETY, 4, 6, 8)
				.need(VillageNeeds.Need.COMMERCE, 3, 4, 5));
		BuildingTypes.register(BuildingTypes.type(ENCHANTER).maxLevel(5).workers(1).need(VillageNeeds.Need.LEISURE, 4, 6, 8, 10, 12)
				.need(VillageNeeds.Need.COMMERCE, 8, 12, 16, 20, 25));
		BuildingTypes.register(BuildingTypes.type(TAVERN).maxLevel(3).workers(1).need(VillageNeeds.Need.LEISURE, 15, 22, 30)
				.need(VillageNeeds.Need.COMMERCE, 5, 8, 10));
		BuildingTypes.register(BuildingTypes.type(GARDEN).maxLevel(3).need(VillageNeeds.Need.LEISURE, 8, 14, 20).need(VillageNeeds.Need.LIGHT, 3, 5, 8));
		// a igreja e a maior fonte de lazer e fe da vila
		BuildingTypes.register(BuildingTypes.type(CHURCH).maxLevel(3).workers(1).need(VillageNeeds.Need.LEISURE, 30, 45, 60)
				.need(VillageNeeds.Need.SAFETY, 3, 5, 8));
		BuildingTypes.register(BuildingTypes.type(EMPTY_LOT).maxLevel(1));
	}

	/** Lotes: a classe de tamanho do nivel maximo e as zonas, na ordem de preferencia. */
	static void registerLots() {
		LotRules.register(QUARRY, SizeClass.M, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.register(TAILOR, SizeClass.M, false, Zone.DISTRICT, Zone.CENTER, Zone.OUTSKIRTS);
		LotRules.register(TANNERY, SizeClass.M, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		LotRules.register(FLETCHER, SizeClass.M, false, Zone.DISTRICT, Zone.CENTER, Zone.OUTSKIRTS);
		LotRules.register(ENCHANTER, SizeClass.L, false, Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register(TAVERN, SizeClass.M, false, Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register(GARDEN, SizeClass.T, false, Zone.DISTRICT, Zone.CENTER, Zone.OUTSKIRTS);
		LotRules.register(CHURCH, SizeClass.L, false, Zone.CENTER, Zone.DISTRICT, Zone.OUTSKIRTS);
		LotRules.register(EMPTY_LOT, SizeClass.S, false, Zone.DISTRICT, Zone.OUTSKIRTS);
	}
}
