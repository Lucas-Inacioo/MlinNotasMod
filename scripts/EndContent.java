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
		ContentGen.tag("worldgen/biome", "minecraft:is_end", List.of("mlinnotas:end_moss_fields", "mlinnotas:ether_crystal_grove", "mlinnotas:void_wastes",
				"mlinnotas:chorus_forest"), false);
		ContentGen.tag("worldgen/biome", "minecraft:has_structure/end_city", List.of("mlinnotas:end_moss_fields", "mlinnotas:ether_crystal_grove",
				"mlinnotas:chorus_forest"), false);
		String spawn = "{\"type\":\"mlinnotas:%s\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":%d,\"max_inclusive\":%d},\"weight\":%d}";
		biome("end_moss_fields", List.of("void_ponds", "end_gateway_return", "void_trees", "void_bushes", "patch_void_grass", "patch_void_lily",
						"minecraft:chorus_plant"),
				"\"creature\":[" + spawn.formatted("void_jelly", 2, 4, 14) + "],", "#1b3a3c", "minecraft:spore_blossom_air", 0.006, "");
		biome("ether_crystal_grove", List.of("crystal_geodes", "calcite_patches", "end_gateway_return", "giant_ether_crystal_spikes",
						"ether_crystal_spikes", "patch_ether_crystal_cluster"),
				"", "#2a2450", "minecraft:end_rod", 0.003, "," + spawn.formatted("crystal_mite", 1, 3, 8));
		biome("void_wastes", List.of("void_craters", "giant_fossils", "void_basalt_pillars", "patch_void_grass_sparse"), "", "#1a1520",
				"minecraft:white_ash", 0.06, "," + spawn.formatted("crystal_mite", 1, 2, 3));
		biome("chorus_forest", List.of("end_gateway_return", "giant_chorus_plants", "minecraft:chorus_plant", "patch_chorus_flower", "patch_end_mushroom"),
				"\"creature\":[" + spawn.formatted("chorus_hopper", 2, 4, 12) + "],", "#2b1a33", "minecraft:reverse_portal", 0.004, "");
		ContentGen.name("biome", "chorus_forest", "Floresta de Coro", "Chorus Forest");

		// superficie por bioma: musgo nos Campos, cinza nos Ermos, pedra do End no resto (a Floresta de Coro precisa de
		// pedra do End para o coro crescer)
		ContentGen.write("data/minecraft/worldgen/material_rule/end.json", "{\"type\":\"minecraft:sequence\",\"sequence\":[{\"type\":\"minecraft:condition\","
				+ "\"if_true\":\"minecraft:on_floor\",\"then_run\":{\"type\":\"minecraft:sequence\",\"sequence\":["
				+ "{\"type\":\"minecraft:condition\",\"if_true\":{\"type\":\"minecraft:biome\",\"biome_is\":\"mlinnotas:end_moss_fields\"},"
				+ "\"then_run\":{\"type\":\"minecraft:block\",\"result_state\":\"mlinnotas:end_moss\"}},"
				+ "{\"type\":\"minecraft:condition\",\"if_true\":{\"type\":\"minecraft:biome\",\"biome_is\":\"mlinnotas:void_wastes\"},"
				+ "\"then_run\":{\"type\":\"minecraft:block\",\"result_state\":\"mlinnotas:void_ash\"}},"
				+ "{\"type\":\"minecraft:condition\",\"if_true\":{\"type\":\"minecraft:biome\",\"biome_is\":\"mlinnotas:chorus_forest\"},"
				+ "\"then_run\":{\"type\":\"minecraft:block\",\"result_state\":\"minecraft:end_stone\"}}]}},"
				+ "{\"type\":\"minecraft:block\",\"result_state\":\"minecraft:end_stone\"}]}");
		biomeDecor();
		pickaxeMineable.add("mlinnotas:void_basalt");

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

	/** Ordem global das features por etapa de geracao (1 = lagos, 2 = modificacoes locais, 4 = estruturas de superficie, 9 = vegetacao). */
	static final String[][] FEATURE_ORDER = {
			{"1", "void_ponds"},
			{"2", "void_craters"}, {"2", "crystal_geodes"}, {"2", "calcite_patches"},
			{"4", "end_gateway_return"}, {"4", "giant_fossils"}, {"4", "void_basalt_pillars"}, {"4", "giant_ether_crystal_spikes"},
			{"9", "void_trees"}, {"9", "void_bushes"}, {"9", "patch_void_grass"}, {"9", "patch_void_grass_sparse"}, {"9", "patch_void_lily"},
			{"9", "ether_crystal_spikes"}, {"9", "patch_ether_crystal_cluster"}, {"9", "giant_chorus_plants"}, {"9", "minecraft:chorus_plant"},
			{"9", "patch_chorus_flower"}, {"9", "patch_end_mushroom"}};

	/** Blocos, features e criatura novos dos biomas grandes (arvores do vazio, coro gigante, geodos, crateras, fosseis). */
	static void biomeDecor() throws IOException {
		// tronco e basalto: pilar com eixo, como os vanilla
		pillar("void_log", "Tronco do Vazio", "Void Log");
		pillar("void_basalt", "Basalto do Vazio", "Void Basalt");
		cubeAll("void_planks", "Tábuas do Vazio", "Void Planks");
		ContentGen.write("assets/mlinnotas/blockstates/void_leaves.json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/void_leaves\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/void_leaves.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mlinnotas:block/void_leaves\"}}");
		ContentGen.write("assets/mlinnotas/items/void_leaves.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/void_leaves\"}}");
		ContentGen.name("block", "void_leaves", "Folhas do Vazio", "Void Leaves");
		ContentGen.write("data/mlinnotas/loot_table/blocks/void_leaves.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":"
				+ "\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"condition\":{\"type\":\"minecraft:any_of\",\"terms\":["
				+ "{\"type\":\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},\"minecraft:tool/can_silk_touch\"]},"
				+ "\"name\":\"mlinnotas:void_leaves\"},{\"type\":\"minecraft:item\",\"condition\":{\"type\":\"minecraft:random_chance\",\"chance\":0.05},"
				+ "\"name\":\"mlinnotas:void_lily\"}]}],\"rolls\":1}],\"random_sequence\":\"mlinnotas:blocks/void_leaves\"}");
		for (String[] plant : new String[][] {{"void_lily", "Lírio do Vazio", "Void Lily"}, {"end_mushroom", "Cogumelo do End", "End Mushroom"}}) {
			ContentGen.write("assets/mlinnotas/blockstates/" + plant[0] + ".json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/" + plant[0] + "\"}}}");
			ContentGen.write("assets/mlinnotas/models/block/" + plant[0] + ".json", "{\"parent\":\"minecraft:block/cross\",\"textures\":{\"cross\":\"mlinnotas:block/"
					+ plant[0] + "\"}}");
			flatBlockItem(plant[0]);
			ContentGen.name("block", plant[0], plant[1], plant[2]);
			selfDrop(plant[0]);
		}
		ContentGen.tag("block", "minecraft:logs", List.of("mlinnotas:void_log"), false);
		ContentGen.tag("item", "minecraft:logs", List.of("mlinnotas:void_log"), false);
		ContentGen.tag("block", "minecraft:planks", List.of("mlinnotas:void_planks"), false);
		ContentGen.tag("item", "minecraft:planks", List.of("mlinnotas:void_planks"), false);
		ContentGen.tag("block", "minecraft:leaves", List.of("mlinnotas:void_leaves"), false);
		ContentGen.tag("block", "minecraft:mineable/axe", List.of("mlinnotas:void_log", "mlinnotas:void_planks"), false);
		ContentGen.tag("block", "minecraft:mineable/hoe", List.of("mlinnotas:void_leaves"), false);
		ContentGen.shaped("void_planks", "L", Map.of("L", "mlinnotas:void_log"), "building", 4);

		// Saltador de Coro
		item("chorus_hopper_spawn_egg", "Ovo Gerador de Saltador de Coro", "Chorus Hopper Spawn Egg");
		ContentGen.name("entity", "chorus_hopper", "Saltador de Coro", "Chorus Hopper");
		entityLoot("chorus_hopper", "{\"entries\":[{\"type\":\"minecraft:item\",\"modifier\":[{\"type\":\"minecraft:set_count\",\"count\":{\"type\":"
				+ "\"minecraft:uniform\",\"min\":1,\"max\":2}}],\"name\":\"minecraft:chorus_fruit\"}],\"rolls\":1}");

		// features (tipos mlinnotas:* em EndFeatures) e onde/quantas vezes aparecem
		String feature = "data/mlinnotas/worldgen/feature/";
		ContentGen.write(feature + "void_tree.json", "{\"type\":\"mlinnotas:void_tree\"}");
		ContentGen.write(feature + "void_bush.json", "{\"type\":\"mlinnotas:void_tree\",\"bush\":true}");
		ContentGen.write(feature + "giant_ether_crystal_spike.json", "{\"type\":\"mlinnotas:giant_crystal_spike\",\"min_height\":6,\"max_height\":14}");
		ContentGen.write(feature + "crystal_geode.json", "{\"type\":\"mlinnotas:crystal_geode\"}");
		ContentGen.write(feature + "void_crater.json", "{\"type\":\"mlinnotas:void_crater\"}");
		ContentGen.write(feature + "giant_fossil.json", "{\"type\":\"mlinnotas:giant_fossil\"}");
		ContentGen.write(feature + "void_basalt_pillars.json", "{\"type\":\"mlinnotas:void_basalt_pillars\"}");
		ContentGen.write(feature + "void_pond.json", "{\"type\":\"mlinnotas:void_pond\"}");
		ContentGen.write(feature + "giant_chorus.json", "{\"type\":\"mlinnotas:giant_chorus\"}");
		ContentGen.write(feature + "calcite_patch.json", "{\"type\":\"mlinnotas:surface_patch\",\"state\":\"minecraft:calcite\",\"radius\":3}");
		ContentGen.write(feature + "void_lily.json", "{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"mlinnotas:void_lily\"}}");
		ContentGen.write(feature + "end_mushroom.json", "{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"mlinnotas:end_mushroom\"}}");
		ContentGen.write(feature + "chorus_flower.json", "{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"minecraft:chorus_flower\"}}");

		surface("void_trees", "void_tree", "{\"type\":\"minecraft:count\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":0,\"max_inclusive\":2}}");
		surface("void_bushes", "void_bush", "{\"type\":\"minecraft:count\",\"count\":2}");
		surface("giant_ether_crystal_spikes", "giant_ether_crystal_spike", "{\"type\":\"minecraft:count\",\"count\":{\"type\":\"minecraft:uniform\","
				+ "\"min_inclusive\":0,\"max_inclusive\":2}}");
		surface("crystal_geodes", "crystal_geode", "{\"type\":\"minecraft:rarity_filter\",\"chance\":4}");
		surface("calcite_patches", "calcite_patch", "{\"type\":\"minecraft:count\",\"count\":1}");
		surface("void_craters", "void_crater", "{\"type\":\"minecraft:rarity_filter\",\"chance\":3}");
		surface("giant_fossils", "giant_fossil", "{\"type\":\"minecraft:rarity_filter\",\"chance\":8}");
		surface("void_basalt_pillars", "void_basalt_pillars", "{\"type\":\"minecraft:count\",\"count\":{\"type\":\"minecraft:uniform\","
				+ "\"min_inclusive\":0,\"max_inclusive\":2}}");
		surface("void_ponds", "void_pond", "{\"type\":\"minecraft:rarity_filter\",\"chance\":3}");
		surface("giant_chorus_plants", "giant_chorus", "{\"type\":\"minecraft:count\",\"count\":4}");
		String onGround = "{\"type\":\"minecraft:all_of\",\"predicates\":[{\"type\":\"minecraft:matching_block_tag\",\"tag\":\"minecraft:air\"},"
				+ "{\"type\":\"minecraft:matching_blocks\",\"blocks\":[\"mlinnotas:end_moss\",\"minecraft:end_stone\",\"mlinnotas:void_ash\"],\"offset\":[0,-1,0]}]}";
		patch("patch_void_lily", "void_lily", 2, 10, 5, onGround);
		patch("patch_end_mushroom", "end_mushroom", 3, 8, 5, onGround);
		patch("patch_chorus_flower", "chorus_flower", 2, 6, 6, onGround);
	}

	/** Placed feature de superficie: frequencia, ponto aleatorio no chunk, topo do terreno, so no bioma certo. */
	static void surface(String id, String feature, String frequency) throws IOException {
		ContentGen.write("data/mlinnotas/worldgen/placed_feature/" + id + ".json", "{\"feature\":\"mlinnotas:" + feature + "\",\"placement\":["
				+ frequency + ",{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},"
				+ "{\"type\":\"minecraft:biome\"}]}");
	}

	/** Bloco com eixo (tronco, basalto): lateral {@code <id>} e topo {@code <id>_top}. */
	static void pillar(String id, String pt, String en) throws IOException {
		ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{\"axis=x\":{\"model\":\"mlinnotas:block/" + id
				+ "_horizontal\",\"x\":90,\"y\":90},\"axis=y\":{\"model\":\"mlinnotas:block/" + id + "\"},\"axis=z\":{\"model\":\"mlinnotas:block/"
				+ id + "_horizontal\",\"x\":90}}}");
		ContentGen.write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/cube_column\",\"textures\":{\"end\":"
				+ "\"mlinnotas:block/" + id + "_top\",\"side\":\"mlinnotas:block/" + id + "\"}}");
		ContentGen.write("assets/mlinnotas/models/block/" + id + "_horizontal.json", "{\"parent\":\"minecraft:block/cube_column_horizontal\","
				+ "\"textures\":{\"end\":\"mlinnotas:block/" + id + "_top\",\"side\":\"mlinnotas:block/" + id + "\"}}");
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
		ContentGen.name("block", id, pt, en);
		selfDrop(id);
	}

	/** Bioma no formato do 26.3 (atributos de ambiente). {@code extraMonster} comeca com virgula (ou vazio). */
	static void biome(String id, List<String> decor, String creatures, String fog, String particle, double chance,
			String extraMonster) throws IOException {
		String monsters = "{\"type\":\"minecraft:enderman\",\"count\":4,\"weight\":10}" + extraMonster;
		// cada etapa segue a ordem global de FEATURE_ORDER (a ordem tem que ser a mesma em todos os biomas)
		StringBuilder steps = new StringBuilder("[");
		for (int step = 0; step < 10; step++) {
			List<String> inStep = new java.util.ArrayList<>();
			for (String[] entry : FEATURE_ORDER) {
				if (Integer.parseInt(entry[0]) == step && decor.contains(entry[1])) {
					inStep.add(entry[1].contains(":") ? entry[1] : (entry[1].equals("end_gateway_return") ? "minecraft:" : "mlinnotas:") + entry[1]);
				}
			}
			steps.append(step > 0 ? "," : "").append("[").append(ContentGen.quoteAll(inStep)).append("]");
		}
		for (String feature : decor) {
			if (java.util.Arrays.stream(FEATURE_ORDER).noneMatch(e -> e[1].equals(feature))) {
				throw new IllegalArgumentException("feature fora de FEATURE_ORDER: " + feature);
			}
		}
		String features = steps.append("]").toString();
		ContentGen.write("data/mlinnotas/worldgen/biome/" + id + ".json", "{\"attributes\":{\"minecraft:gameplay/natural_mob_spawns\":{\"argument\":"
				+ "{\"spawn_costs\":{},\"spawns_by_category\":{" + creatures + "\"monster\":[" + monsters + "]}},\"modifier\":\"overlay\"},"
				+ "\"minecraft:visual/ambient_particles\":{\"argument\":[{\"particle\":{\"type\":\"" + particle + "\"},\"probability\":" + chance + "}],"
				+ "\"modifier\":\"append\"},\"minecraft:visual/fog_color\":\"" + fog + "\"},\"carvers\":[],\"downfall\":0.5,"
				+ "\"effects\":{\"water_color\":\"#3f76e4\"},\"features\":" + features + ",\"has_precipitation\":false,\"temperature\":0.5}");
	}
}
