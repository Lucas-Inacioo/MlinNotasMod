import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Conteudo do End para o ContentGen: Cidadela do End (substitui a End City), biomas novos, blocos, criaturas e drops.
 * Chamado por ContentGen.main (o launcher do Java compila este arquivo junto).
 */
class EndContent {
	static void generate(List<String> pickaxeMineable) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/blocks/end_moss.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":"
				+ "\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"condition\":\"minecraft:tool/can_silk_touch\",\"name\":"
				+ "\"mlinnotas:end_moss\"},{\"type\":\"minecraft:item\",\"condition\":{\"type\":\"minecraft:survives_explosion\"},\"name\":"
				+ "\"minecraft:end_stone\"}]}],\"rolls\":1}],\"random_sequence\":\"mlinnotas:blocks/end_moss\"}");
		cubeBottomTop("end_moss", "mlinnotas:block/end_moss_top", "mlinnotas:block/end_moss_side", "minecraft:block/end_stone");
		ContentGen.name("block", "end_moss", "Musgo do End", "End Moss");

		cubeAll("void_ash", "Cinza do Vazio", "Void Ash");
		cubeAll("ether_crystal_block", "Bloco de Cristal Etéreo", "Block of Ether Crystal");
		cubeAll("void_bricks", "Tijolos do Vazio", "Void Bricks");
		cubeAll("purpur_lamp", "Lâmpada de Purpur", "Purpur Lamp");
		cubeBottomTop("purpur_elevator", "mlinnotas:block/purpur_elevator_top", "mlinnotas:block/purpur_elevator_side", "minecraft:block/purpur_block");
		ContentGen.name("block", "purpur_elevator", "Elevador de Purpur", "Purpur Elevator");
		selfDrop("purpur_elevator");

		// planta e aglomerado: modelo em cruz, item com a textura do bloco
		ContentGen.write("assets/mlinnotas/blockstates/void_grass.json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/void_grass\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/void_grass.json", "{\"parent\":\"minecraft:block/cross\",\"textures\":{\"cross\":\"mlinnotas:block/void_grass\"}}");
		flatBlockItem("void_grass");
		ContentGen.name("block", "void_grass", "Grama do Vazio", "Void Grass");
		ContentGen.write("data/mlinnotas/loot_table/blocks/void_grass.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"condition\":{\"type\":"
				+ "\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},\"entries\":[{\"type\":\"minecraft:item\",\"name\":"
				+ "\"mlinnotas:void_grass\"}],\"rolls\":1}],\"random_sequence\":\"mlinnotas:blocks/void_grass\"}");

		StringBuilder cluster = new StringBuilder("{\"variants\":{");
		String[][] facings = {{"down", "180", "0"}, {"east", "90", "90"}, {"north", "90", "0"}, {"south", "90", "180"}, {"up", "0", "0"}, {"west", "90", "270"}};
		for (int i = 0; i < facings.length; i++) {
			cluster.append(i > 0 ? "," : "").append("\"facing=").append(facings[i][0]).append("\":{\"model\":\"mlinnotas:block/ether_crystal_cluster\"");
			cluster.append(facings[i][1].equals("0") ? "" : ",\"x\":" + facings[i][1]).append(facings[i][2].equals("0") ? "" : ",\"y\":" + facings[i][2]).append("}");
		}
		ContentGen.write("assets/mlinnotas/blockstates/ether_crystal_cluster.json", cluster.append("}}").toString());
		ContentGen.write("assets/mlinnotas/models/block/ether_crystal_cluster.json",
				"{\"parent\":\"minecraft:block/cross\",\"textures\":{\"cross\":\"mlinnotas:block/ether_crystal_cluster\"}}");
		flatBlockItem("ether_crystal_cluster");
		ContentGen.name("block", "ether_crystal_cluster", "Aglomerado de Cristal Etéreo", "Ether Crystal Cluster");
		ContentGen.write("data/mlinnotas/loot_table/blocks/ether_crystal_cluster.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":"
				+ "\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"condition\":\"minecraft:tool/can_silk_touch\",\"name\":"
				+ "\"mlinnotas:ether_crystal_cluster\"},{\"type\":\"minecraft:item\",\"modifier\":[{\"type\":\"minecraft:set_count\",\"count\":{\"type\":"
				+ "\"minecraft:uniform\",\"min\":2,\"max\":4}},{\"type\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\",\"formula\":"
				+ "\"minecraft:ore_drops\"},{\"type\":\"minecraft:explosion_decay\"}],\"name\":\"mlinnotas:ether_crystal_shard\"}]}],\"rolls\":1}],"
				+ "\"random_sequence\":\"mlinnotas:blocks/ether_crystal_cluster\"}");

		for (String block : List.of("end_moss", "ether_crystal_block", "ether_crystal_cluster", "void_bricks", "purpur_lamp", "purpur_elevator")) {
			pickaxeMineable.add("mlinnotas:" + block);
		}
		ContentGen.tag("block", "minecraft:mineable/shovel", List.of("mlinnotas:void_ash"), false);

		// itens
		item("ether_crystal_shard", "Fragmento de Cristal Etéreo", "Ether Crystal Shard");
		item("sentinel_core", "Núcleo de Sentinela", "Sentinel Core");
		item("void_jelly", "Geleia do Vazio", "Void Jelly");
		item("purpur_sentinel_spawn_egg", "Ovo Gerador de Sentinela de Purpur", "Purpur Sentinel Spawn Egg");
		item("void_jelly_spawn_egg", "Ovo Gerador de Água-viva do Vazio", "Void Jelly Spawn Egg");
		item("crystal_mite_spawn_egg", "Ovo Gerador de Ácaro Cristalino", "Crystal Mite Spawn Egg");
		ContentGen.name("entity", "purpur_sentinel", "Sentinela de Purpur", "Purpur Sentinel");
		ContentGen.name("entity", "void_jelly", "Água-viva do Vazio", "Void Jelly");
		ContentGen.name("entity", "crystal_mite", "Ácaro Cristalino", "Crystal Mite");
		ContentGen.name("biome", "end_moss_fields", "Campos de Musgo do End", "End Moss Fields");
		ContentGen.name("biome", "ether_crystal_grove", "Bosque Cristalino", "Ether Crystal Grove");
		ContentGen.name("biome", "void_wastes", "Ermos do Vazio", "Void Wastes");
		ContentGen.PT.put("block.mlinnotas.purpur_elevator.tooltip", "Pule em cima para subir até o próximo elevador; agache para descer");
		ContentGen.EN.put("block.mlinnotas.purpur_elevator.tooltip", "Jump on it to go up to the next elevator; sneak to go down");

		entityLoot("purpur_sentinel", "{\"condition\":{\"type\":\"minecraft:random_chance\",\"chance\":0.5},\"entries\":[{\"type\":\"minecraft:item\","
				+ "\"name\":\"mlinnotas:sentinel_core\"}],\"rolls\":1},{\"entries\":[{\"type\":\"minecraft:item\",\"modifier\":[{\"type\":"
				+ "\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":0,\"max\":2}}],\"name\":\"minecraft:popped_chorus_fruit\"}],\"rolls\":1}");
		entityLoot("void_jelly", "{\"entries\":[{\"type\":\"minecraft:item\",\"modifier\":[{\"type\":\"minecraft:set_count\",\"count\":{\"type\":"
				+ "\"minecraft:uniform\",\"min\":1,\"max\":2}}],\"name\":\"mlinnotas:void_jelly\"}],\"rolls\":1}");
		entityLoot("crystal_mite", "{\"entries\":[{\"type\":\"minecraft:item\",\"modifier\":[{\"type\":\"minecraft:set_count\",\"count\":{\"type\":"
				+ "\"minecraft:uniform\",\"min\":0,\"max\":2}}],\"name\":\"mlinnotas:ether_crystal_shard\"}],\"rolls\":1}");

		// receitas
		ContentGen.shaped("purpur_elevator", "PPP|PCP|PPP", Map.of("P", "minecraft:purpur_block", "C", "mlinnotas:sentinel_core"), "building", 4);
		ContentGen.shaped("purpur_lamp", " P |PGP| P ", Map.of("P", "minecraft:purpur_block", "G", "minecraft:glowstone"), "building", 2);
		ContentGen.shaped("void_bricks", "AB|BA", Map.of("A", "mlinnotas:void_ash", "B", "minecraft:end_stone_bricks"), "building", 4);
		ContentGen.shaped("ether_crystal_block", "SS|SS", Map.of("S", "mlinnotas:ether_crystal_shard"), "building", 1);

		// Cidadela do End no lugar da End City (mesmo espacamento) e a conquista de achar a cidade apontando para ela
		ContentGen.write("data/mlinnotas/worldgen/structure/end_citadel.json", "{\"type\":\"mlinnotas:end_citadel\",\"biomes\":"
				+ "\"#minecraft:has_structure/end_city\",\"spawn_overrides\":{},\"step\":\"surface_structures\"}");
		ContentGen.write("data/minecraft/worldgen/structure_set/end_cities.json", "{\"placement\":{\"type\":\"minecraft:random_spread\",\"salt\":10387313,"
				+ "\"separation\":11,\"spacing\":20,\"spread_type\":\"triangular\"},\"structures\":[{\"structure\":\"mlinnotas:end_citadel\",\"weight\":1}]}");
		ContentGen.write("data/minecraft/advancement/end/find_end_city.json", "{\"parent\":\"minecraft:end/enter_end_gateway\",\"criteria\":{\"in_city\":"
				+ "{\"conditions\":{\"player\":{\"type\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"minecraft:location\":"
				+ "{\"structures\":\"mlinnotas:end_citadel\"}}}},\"trigger\":\"minecraft:location\"}},\"display\":{\"description\":{\"translate\":"
				+ "\"advancements.end.find_end_city.description\"},\"icon\":{\"id\":\"minecraft:purpur_block\"},\"title\":{\"translate\":"
				+ "\"advancements.end.find_end_city.title\"}},\"requirements\":[[\"in_city\"]],\"sends_telemetry_event\":true}");

		// biomas
		ContentGen.tag("worldgen/biome", "minecraft:is_end", List.of("mlinnotas:end_moss_fields", "mlinnotas:ether_crystal_grove", "mlinnotas:void_wastes"), false);
		ContentGen.tag("worldgen/biome", "minecraft:has_structure/end_city", List.of("mlinnotas:end_moss_fields", "mlinnotas:ether_crystal_grove"), false);
		biome("end_moss_fields", true, "[\"mlinnotas:patch_void_grass\",\"minecraft:chorus_plant\"]",
				"\"creature\":[{\"type\":\"mlinnotas:void_jelly\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":1,\"max_inclusive\":3},\"weight\":8}],",
				"#1b3a3c", "minecraft:spore_blossom_air", 0.006, "");
		biome("ether_crystal_grove", true, "[\"mlinnotas:ether_crystal_spikes\",\"mlinnotas:patch_ether_crystal_cluster\"]",
				"", "#2a2450", "minecraft:end_rod", 0.003, ",{\"type\":\"mlinnotas:crystal_mite\",\"count\":{\"type\":\"minecraft:uniform\","
						+ "\"min_inclusive\":1,\"max_inclusive\":3},\"weight\":6}");
		biome("void_wastes", false, "[\"mlinnotas:patch_void_grass_sparse\"]", "", "#1a1520", "minecraft:white_ash", 0.02,
				",{\"type\":\"mlinnotas:crystal_mite\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":1,\"max_inclusive\":2},\"weight\":3}");

		// superficie por bioma: musgo nos Campos, cinza nos Ermos, pedra do End no resto
		ContentGen.write("data/minecraft/worldgen/material_rule/end.json", "{\"type\":\"minecraft:sequence\",\"sequence\":[{\"type\":\"minecraft:condition\","
				+ "\"if_true\":\"minecraft:on_floor\",\"then_run\":{\"type\":\"minecraft:sequence\",\"sequence\":["
				+ "{\"type\":\"minecraft:condition\",\"if_true\":{\"type\":\"minecraft:biome\",\"biome_is\":\"mlinnotas:end_moss_fields\"},"
				+ "\"then_run\":{\"type\":\"minecraft:block\",\"result_state\":\"mlinnotas:end_moss\"}},"
				+ "{\"type\":\"minecraft:condition\",\"if_true\":{\"type\":\"minecraft:biome\",\"biome_is\":\"mlinnotas:void_wastes\"},"
				+ "\"then_run\":{\"type\":\"minecraft:block\",\"result_state\":\"mlinnotas:void_ash\"}}]}},"
				+ "{\"type\":\"minecraft:block\",\"result_state\":\"minecraft:end_stone\"}]}");

		// vegetacao
		String onGround = "{\"type\":\"minecraft:all_of\",\"predicates\":[{\"type\":\"minecraft:matching_block_tag\",\"tag\":\"minecraft:air\"},"
				+ "{\"type\":\"minecraft:matching_blocks\",\"blocks\":[\"mlinnotas:end_moss\",\"minecraft:end_stone\",\"mlinnotas:void_ash\"],\"offset\":[0,-1,0]}]}";
		ContentGen.write("data/mlinnotas/worldgen/feature/void_grass.json", "{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"mlinnotas:void_grass\"}}");
		patch("patch_void_grass", "void_grass", 4, 24, 6, onGround);
		patch("patch_void_grass_sparse", "void_grass", 1, 6, 5, onGround);
		ContentGen.write("data/mlinnotas/worldgen/feature/ether_crystal_cluster.json", "{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":"
				+ "\"mlinnotas:ether_crystal_cluster\",\"properties\":{\"facing\":\"up\"}}}");
		patch("patch_ether_crystal_cluster", "ether_crystal_cluster", 3, 10, 4, onGround);
		ContentGen.write("data/mlinnotas/worldgen/feature/ether_crystal_spike.json", "{\"type\":\"minecraft:block_column\",\"allowed_placement\":{\"type\":"
				+ "\"minecraft:matching_block_tag\",\"tag\":\"minecraft:air\"},\"direction\":\"up\",\"layers\":[{\"height\":{\"type\":\"minecraft:biased_to_bottom\","
				+ "\"min_inclusive\":2,\"max_inclusive\":6},\"provider\":{\"id\":\"mlinnotas:ether_crystal_block\"}},{\"height\":1,\"provider\":{\"id\":"
				+ "\"mlinnotas:ether_crystal_cluster\",\"properties\":{\"facing\":\"up\"}}}],\"prioritize_tip\":true}");
		ContentGen.write("data/mlinnotas/worldgen/placed_feature/ether_crystal_spikes.json", "{\"feature\":\"mlinnotas:ether_crystal_spike\",\"placement\":["
				+ "{\"type\":\"minecraft:count\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":0,\"max_inclusive\":3}},{\"type\":\"minecraft:in_square\"},"
				+ "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},{\"type\":\"minecraft:biome\"},"
				+ "{\"type\":\"minecraft:block_predicate_filter\",\"predicate\":" + onGround + "}]}");
	}

	static void cubeAll(String id, String pt, String en) throws IOException {
		ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/" + id + "\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mlinnotas:block/" + id + "\"}}");
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
		ContentGen.name("block", id, pt, en);
		selfDrop(id);
	}

	static void cubeBottomTop(String id, String top, String side, String bottom) throws IOException {
		ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/" + id + "\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{\"top\":\"" + top
				+ "\",\"side\":\"" + side + "\",\"bottom\":\"" + bottom + "\"}}");
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
	}

	static void flatBlockItem(String id) throws IOException {
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:item/" + id + "\"}}");
		ContentGen.write("assets/mlinnotas/models/item/" + id + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mlinnotas:block/" + id + "\"}}");
	}

	static void selfDrop(String id) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"condition\":{\"type\":"
				+ "\"minecraft:survives_explosion\"},\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + id + "\"}],\"rolls\":1}],"
				+ "\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
	}

	static void item(String id, String pt, String en) throws IOException {
		ContentGen.simpleItem(id, "item/generated");
		ContentGen.name("item", id, pt, en);
	}

	static void entityLoot(String id, String pools) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/entities/" + id + ".json", "{\"type\":\"minecraft:entity\",\"pools\":[" + pools
				+ "],\"random_sequence\":\"mlinnotas:entities/" + id + "\"}");
	}

	static void patch(String id, String feature, int tries, int perTry, int spread, String predicate) throws IOException {
		ContentGen.write("data/mlinnotas/worldgen/placed_feature/" + id + ".json", "{\"feature\":\"mlinnotas:" + feature + "\",\"placement\":["
				+ "{\"type\":\"minecraft:count\",\"count\":" + tries + "},{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\","
				+ "\"heightmap\":\"WORLD_SURFACE_WG\"},{\"type\":\"minecraft:biome\"},{\"type\":\"minecraft:count\",\"count\":" + perTry + "},"
				+ "{\"type\":\"minecraft:offset\",\"x\":{\"type\":\"minecraft:trapezoid\",\"max\":" + spread + ",\"min\":-" + spread + ",\"plateau\":0},"
				+ "\"y\":{\"type\":\"minecraft:trapezoid\",\"max\":2,\"min\":-2,\"plateau\":0},\"z\":{\"type\":\"minecraft:trapezoid\",\"max\":" + spread
				+ ",\"min\":-" + spread + ",\"plateau\":0}},{\"type\":\"minecraft:block_predicate_filter\",\"predicate\":" + predicate + "}]}");
	}

	/** Bioma no formato do 26.3 (atributos de ambiente). {@code extraMonster} comeca com virgula (ou vazio). */
	static void biome(String id, boolean gateways, String vegetation, String creatures, String fog, String particle, double chance,
			String extraMonster) throws IOException {
		String monsters = "{\"type\":\"minecraft:enderman\",\"count\":4,\"weight\":10}" + extraMonster;
		String features = "[[],[],[],[]," + (gateways ? "[\"minecraft:end_gateway_return\"]" : "[]") + ",[],[],[],[]," + vegetation + "]";
		ContentGen.write("data/mlinnotas/worldgen/biome/" + id + ".json", "{\"attributes\":{\"minecraft:gameplay/natural_mob_spawns\":{\"argument\":"
				+ "{\"spawn_costs\":{},\"spawns_by_category\":{" + creatures + "\"monster\":[" + monsters + "]}},\"modifier\":\"overlay\"},"
				+ "\"minecraft:visual/ambient_particles\":{\"argument\":[{\"particle\":{\"type\":\"" + particle + "\"},\"probability\":" + chance + "}],"
				+ "\"modifier\":\"append\"},\"minecraft:visual/fog_color\":\"" + fog + "\"},\"carvers\":[],\"downfall\":0.5,"
				+ "\"effects\":{\"water_color\":\"#3f76e4\"},\"features\":" + features + ",\"has_precipitation\":false,\"temperature\":0.5}");
	}
}
