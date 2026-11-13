import java.io.IOException;

/**
 * Conteudo das estruturas do mar e pequenas refeitas para o ContentGen: troca os JSONs vanilla (monumento, naufragios,
 * ruinas oceanicas, tesouro enterrado, iglu, cabana da bruxa, os 7 portais em ruinas e as ruinas da trilha) pelo tipo
 * {@code mlinnotas:vanilla_remake} mantendo ids, biomas, spawn_overrides e structure sets vanilla (entao /locate, mapas
 * do tesouro e de explorador e conquistas continuam valendo), e cria as tabelas de loot extras do mod (tesouraria do
 * monumento e bau da bruxa). Os baus de identidade usam as tabelas vanilla. Chamado por ContentGen.main.
 */
class SeaStructuresContent {
	static void generate() throws IOException {
		structure("monument", "monument", "ocean_monument", "surface_structures", "{\"axolotls\":{\"bounding_box\":\"full\",\"spawns\":[]},"
				+ "\"monster\":{\"bounding_box\":\"full\",\"spawns\":[{\"type\":\"minecraft:guardian\",\"count\":{\"type\":\"minecraft:uniform\","
				+ "\"max_inclusive\":4,\"min_inclusive\":2},\"weight\":1}]},\"underground_water_creature\":{\"bounding_box\":\"full\",\"spawns\":[]}}");
		structure("shipwreck", "shipwreck", "shipwreck", "surface_structures", "{}");
		structure("shipwreck_beached", "shipwreck_beached", "shipwreck_beached", "surface_structures", "{}");
		structure("ocean_ruin_warm", "ocean_ruin_warm", "ocean_ruin_warm", "surface_structures", "{}");
		structure("ocean_ruin_cold", "ocean_ruin_cold", "ocean_ruin_cold", "surface_structures", "{}");
		structure("buried_treasure", "buried_treasure", "buried_treasure", "underground_structures", "{}");
		structure("igloo", "igloo", "igloo", "surface_structures", "{}");
		structure("swamp_hut", "swamp_hut", "swamp_hut", "surface_structures", "{\"creature\":{\"bounding_box\":\"piece\",\"spawns\":"
				+ "[{\"type\":\"minecraft:cat\",\"count\":1,\"weight\":1}]},\"monster\":{\"bounding_box\":\"piece\",\"spawns\":"
				+ "[{\"type\":\"minecraft:witch\",\"count\":1,\"weight\":1}]}}");
		structure("ruined_portal", "ruined_portal", "ruined_portal_standard", "surface_structures", "{}");
		for (String v : new String[] {"desert", "jungle", "swamp", "mountain", "ocean", "nether"}) {
			structure("ruined_portal_" + v, "ruined_portal_" + v, "ruined_portal_" + v, "surface_structures", "{}");
		}
		structure("trail_ruins", "trail_ruins", "trail_ruins", "underground_structures", "{}");

		String rare = "{\"entries\":[{\"type\":\"minecraft:empty\",\"weight\":10},{\"type\":\"minecraft:item\",\"modifier\":{\"type\":"
				+ "\"minecraft:enchant_randomly\",\"options\":\"#minecraft:on_random_loot\"},\"name\":\"minecraft:book\",\"weight\":3},"
				+ "{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:return_scroll\",\"weight\":2}],\"rolls\":1}";
		loot("monument_treasury", new String[] {
				item("minecraft:prismarine_shard", 14, 3, 10), item("minecraft:prismarine_crystals", 12, 2, 8), item("minecraft:gold_ingot", 10, 2, 6),
				item("minecraft:gold_nugget", 8, 4, 12), item("minecraft:sponge", 6, 1, 2), item("minecraft:sea_lantern", 6, 1, 3),
				item("minecraft:nautilus_shell", 4, 1, 2), item("minecraft:diamond", 3, 1, 2), item("minecraft:emerald", 5, 1, 3),
				item("minecraft:tide_armor_trim_smithing_template", 2, 1, 1), item("mlinnotas:cobalt_ingot", 3, 1, 2)}, 3, 6, rare);
		loot("witch_hut", new String[] {
				item("minecraft:glass_bottle", 12, 1, 4), item("minecraft:nether_wart", 8, 1, 3), item("minecraft:redstone", 10, 2, 6),
				item("minecraft:glowstone_dust", 8, 1, 4), item("minecraft:sugar", 8, 1, 4), item("minecraft:spider_eye", 8, 1, 3),
				item("minecraft:fermented_spider_eye", 4, 1, 2), item("minecraft:gunpowder", 6, 1, 3), item("minecraft:stick", 6, 1, 3),
				item("minecraft:red_mushroom", 8, 1, 3), item("minecraft:brown_mushroom", 8, 1, 3), item("minecraft:slime_ball", 4, 1, 2),
				item("minecraft:blaze_powder", 2, 1, 1), item("minecraft:emerald", 4, 1, 2)}, 3, 6, rare);
	}

	private static void structure(String id, String kind, String biomeTag, String step, String spawns) throws IOException {
		ContentGen.write("data/minecraft/worldgen/structure/" + id + ".json", "{\"type\":\"mlinnotas:vanilla_remake\",\"kind\":\"" + kind
				+ "\",\"biomes\":\"#minecraft:has_structure/" + biomeTag + "\",\"spawn_overrides\":" + spawns + ",\"step\":\"" + step + "\"}");
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
