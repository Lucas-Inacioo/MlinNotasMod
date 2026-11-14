import java.io.IOException;

/**
 * Conteudo da fortaleza (stronghold) e da mansao da floresta refeitas para o ContentGen: troca os JSONs vanilla pelo
 * tipo {@code mlinnotas:vanilla_remake} mantendo ids, biomas e structure sets vanilla (o olho do fim, /locate, mapas de
 * explorador e conquistas seguem valendo) e cria o loot dos despensers das armadilhas da fortaleza. Os baus usam as
 * tabelas vanilla (stronghold_library/corridor/crossing, woodland_mansion). Chamado por ContentGen.main.
 */
class SmainContent {
	static void generate() throws IOException {
		ContentGen.write("data/minecraft/worldgen/structure/stronghold.json", "{\"type\":\"mlinnotas:vanilla_remake\",\"kind\":\"stronghold\","
				+ "\"biomes\":\"#minecraft:has_structure/stronghold\",\"spawn_overrides\":{},\"step\":\"surface_structures\","
				+ "\"terrain_adaptation\":\"encapsulate\"}");
		ContentGen.write("data/minecraft/worldgen/structure/mansion.json", "{\"type\":\"mlinnotas:vanilla_remake\",\"kind\":\"woodland_mansion\","
				+ "\"biomes\":\"#minecraft:has_structure/woodland_mansion\",\"spawn_overrides\":{},\"step\":\"surface_structures\"}");
		ContentGen.write("data/mlinnotas/loot_table/chests/stronghold_trap.json", "{\"type\":\"minecraft:chest\",\"pools\":[{\"entries\":["
				+ RuinsContent.item("minecraft:arrow", 1, 4, 9) + "],\"rolls\":{\"type\":\"minecraft:uniform\",\"max\":3,\"min\":2}}],"
				+ "\"random_sequence\":\"mlinnotas:chests/stronghold_trap\"}");
	}
}
