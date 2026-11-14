import java.io.IOException;

/**
 * Fortaleza do Nether e bastiao refeitos para o ContentGen: troca os JSONs vanilla ({@code minecraft:fortress} e
 * {@code minecraft:bastion_remnant}) pelo tipo {@code mlinnotas:vanilla_remake} (kinds {@code nether_fortress} e
 * {@code bastion_remnant}), mantendo id, biomas, structure set e o {@code spawn_overrides} vanilla da fortaleza (blazes,
 * esqueletos wither... dentro das caixas das pecas). O bastiao mantem o {@code beard_thin} do vanilla para aplainar o
 * chao embaixo. Chamado por ContentGen.main.
 */
class SNetherContent {
	static void generate() throws IOException {
		ContentGen.write("data/minecraft/worldgen/structure/fortress.json", "{\"type\":\"mlinnotas:vanilla_remake\",\"kind\":\"nether_fortress\","
				+ "\"biomes\":\"#minecraft:has_structure/nether_fortress\",\"spawn_overrides\":{\"monster\":{\"bounding_box\":\"piece\","
				+ "\"spawns\":[{\"type\":\"minecraft:blaze\",\"count\":{\"type\":\"minecraft:uniform\",\"max_inclusive\":3,\"min_inclusive\":2},"
				+ "\"weight\":10},{\"type\":\"minecraft:zombified_piglin\",\"count\":4,\"weight\":5},{\"type\":\"minecraft:wither_skeleton\",\"count\":5,"
				+ "\"weight\":8},{\"type\":\"minecraft:skeleton\",\"count\":5,\"weight\":2},{\"type\":\"minecraft:magma_cube\",\"count\":4,\"weight\":3}]}},"
				+ "\"step\":\"underground_decoration\"}");
		ContentGen.write("data/minecraft/worldgen/structure/bastion_remnant.json", "{\"type\":\"mlinnotas:vanilla_remake\",\"kind\":\"bastion_remnant\","
				+ "\"biomes\":\"#minecraft:has_structure/bastion_remnant\",\"spawn_overrides\":{},\"step\":\"surface_structures\","
				+ "\"terrain_adaptation\":\"beard_thin\"}");
	}
}
