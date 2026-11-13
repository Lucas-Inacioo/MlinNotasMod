import java.io.IOException;

/**
 * Conteudo das estruturas vanilla refeitas para o ContentGen: troca os JSONs vanilla (templo do deserto, templo da
 * selva, posto de saqueadores e as duas minas abandonadas) pelo tipo {@code mlinnotas:vanilla_remake} mantendo os ids,
 * os biomas e os structure sets vanilla (entao /locate, mapas e conquistas continuam valendo), e cria as tabelas de
 * loot extras do mod. Os baus "de identidade" usam as tabelas vanilla (desert_pyramid, jungle_temple,
 * jungle_temple_dispenser, pillager_outpost, abandoned_mineshaft). Chamado por ContentGen.main.
 */
class VanillaStructuresContent {
	static void generate() throws IOException {
		structure("desert_pyramid", "desert_pyramid", "surface_structures", "{}");
		structure("jungle_pyramid", "jungle_temple", "surface_structures", "{}");
		structure("pillager_outpost", "pillager_outpost", "surface_structures", "{\"monster\":{\"bounding_box\":\"full\",\"spawns\":[{\"type\":"
				+ "\"minecraft:pillager\",\"count\":1,\"weight\":1}]}}");
		structure("mineshaft", "mineshaft", "underground_structures", "{}");
		structure("mineshaft_mesa", "mineshaft_mesa", "underground_structures", "{}");

		String rare = "{\"entries\":[{\"type\":\"minecraft:empty\",\"weight\":10},{\"type\":\"minecraft:item\",\"modifier\":{\"type\":"
				+ "\"minecraft:enchant_randomly\",\"options\":\"#minecraft:on_random_loot\"},\"name\":\"minecraft:book\",\"weight\":3},"
				+ "{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:return_scroll\",\"weight\":2}],\"rolls\":1}";
		loot("desert_tomb", new String[] {
				item("minecraft:gold_ingot", 12, 2, 6), item("minecraft:gold_nugget", 10, 4, 12), item("minecraft:emerald", 8, 1, 4),
				item("minecraft:diamond", 3, 1, 2), item("minecraft:golden_apple", 4, 1, 1), item("minecraft:enchanted_golden_apple", 1, 1, 1),
				item("minecraft:golden_horse_armor", 3, 1, 1), item("minecraft:bone", 10, 2, 6), item("minecraft:rotten_flesh", 8, 2, 6),
				item("minecraft:lapis_lazuli", 6, 3, 8), item("minecraft:dune_armor_trim_smithing_template", 2, 1, 1),
				item("mlinnotas:raw_cobalt", 4, 1, 3)}, 4, 7, rare);
		loot("desert_storage", new String[] {
				item("minecraft:bread", 10, 1, 3), item("minecraft:dried_kelp", 8, 2, 6), item("minecraft:string", 8, 1, 4),
				item("minecraft:bone", 10, 1, 5), item("minecraft:sand", 8, 2, 8), item("minecraft:orange_terracotta", 5, 1, 4),
				item("minecraft:brush", 3, 1, 1), item("minecraft:paper", 6, 1, 4), item("minecraft:gold_nugget", 8, 1, 6),
				item("minecraft:copper_ingot", 6, 1, 4), item("minecraft:cactus", 4, 1, 3)}, 3, 6, rare);
		loot("jungle_storage", new String[] {
				item("minecraft:cocoa_beans", 10, 2, 6), item("minecraft:melon_slice", 8, 2, 6), item("minecraft:bamboo", 10, 2, 10),
				item("minecraft:emerald", 6, 1, 3), item("minecraft:gold_ingot", 6, 1, 4), item("minecraft:arrow", 8, 4, 12),
				item("minecraft:string", 8, 1, 4), item("minecraft:jungle_sapling", 6, 1, 3), item("minecraft:glow_berries", 6, 1, 4),
				item("mlinnotas:raw_cobalt", 3, 1, 2)}, 3, 6, rare);
		loot("outpost_armory", new String[] {
				item("minecraft:arrow", 14, 6, 16), item("minecraft:crossbow", 5, 1, 1), item("minecraft:iron_ingot", 8, 1, 4),
				item("minecraft:iron_axe", 4, 1, 1), item("minecraft:iron_helmet", 2, 1, 1), item("minecraft:iron_chestplate", 1, 1, 1),
				item("minecraft:string", 8, 1, 4), item("minecraft:emerald", 6, 1, 4),
				item("mlinnotas:cobalt_ingot", 2, 1, 2)}, 3, 6, rare);
		loot("outpost_supplies", new String[] {
				item("minecraft:wheat", 10, 2, 8), item("minecraft:potato", 8, 2, 6), item("minecraft:carrot", 8, 2, 6),
				item("minecraft:pumpkin_pie", 5, 1, 2), item("minecraft:dark_oak_log", 8, 2, 6), item("minecraft:white_wool", 6, 1, 3),
				item("minecraft:arrow", 8, 2, 8), item("minecraft:emerald", 3, 1, 2)}, 3, 6, rare);
		loot("mineshaft_workshop", new String[] {
				item("minecraft:coal", 12, 3, 10), item("minecraft:raw_iron", 10, 1, 5), item("minecraft:raw_copper", 10, 2, 6),
				item("minecraft:raw_gold", 5, 1, 3), item("minecraft:iron_pickaxe", 3, 1, 1), item("minecraft:rail", 8, 4, 12),
				item("minecraft:powered_rail", 4, 1, 4), item("minecraft:torch", 10, 4, 12), item("minecraft:bread", 8, 1, 3),
				item("minecraft:tnt", 3, 1, 2), item("minecraft:lantern", 4, 1, 2), item("mlinnotas:raw_cobalt", 4, 1, 3),
				item("minecraft:diamond", 1, 1, 1)}, 3, 6, rare);
	}

	private static void structure(String id, String kind, String step, String spawns) throws IOException {
		ContentGen.write("data/minecraft/worldgen/structure/" + id + ".json", "{\"type\":\"mlinnotas:vanilla_remake\",\"kind\":\"" + kind
				+ "\",\"biomes\":\"#minecraft:has_structure/" + (id.equals("jungle_pyramid") ? "jungle_temple" : id) + "\",\"spawn_overrides\":" + spawns
				+ ",\"step\":\"" + step + "\"}");
	}

	private static void loot(String id, String[] entries, int min, int max, String extraPool) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/chests/" + id + ".json", "{\"type\":\"minecraft:chest\",\"pools\":[{\"entries\":["
				+ String.join(",", entries) + "],\"rolls\":{\"type\":\"minecraft:uniform\",\"max\":" + max + ",\"min\":" + min + "}}," + extraPool
				+ "],\"random_sequence\":\"mlinnotas:chests/" + id + "\"}");
	}

	private static String item(String id, int weight, int min, int max) {
		return RuinsContent.item(id, weight, min, max);
	}
}
