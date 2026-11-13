import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conteudo do Nether para o ContentGen: terreno novo (noise_settings/nether com funcoes de densidade proprias), biomas
 * novos, regras de superficie, estruturas assentadas no chao, blocos, criatura e drops.
 * Chamado por ContentGen.main (o launcher do Java compila este arquivo junto).
 *
 * <h2>Terreno</h2>
 * O Nether vanilla e um queijo 3D (cavernas, paredes, pontes de netherrack). Aqui ele vira um "Overworld com teto":
 * <ul>
 *   <li>chao continuo vindo de um mapa de altura 2D (y ~40..90): colinas suaves no meio, planicies baixas onde o ruido
 *       {@code terrain_type} e baixo e planaltos com rampas onde ele e alto (o mesmo ruido escolhe os biomas novos via
 *       {@code erosion} do clima, entao Planicies de Cinza ficam no plano e Picos de Infernita nos planaltos);</li>
 *   <li>vales largos e rasos ({@code valleys}): so no fundo dos mais fundos o chao passa abaixo do mar de lava (y 31),
 *       virando lagos/rios de lava com margem em rampa; um ruido de "vau" corta os rios em trechos;</li>
 *   <li>teto solido ~40 blocos acima do chao (y 104..118), com estalactites ocasionais que nunca descem a menos de
 *       10 blocos do chao;</li>
 *   <li>poucas cavernas pequenas (bolsoes 3D) so bem abaixo do chao, sem furar a superficie.</li>
 * </ul>
 * Densidade = max((chao - y), (y - teto)) / 16 (positivo = rocha). Ver as funcoes em
 * data/mlinnotas/worldgen/density_function/nether.
 */
class NetherContent {
	static final String NS = "mlinnotas:";

	static void generate(List<String> pickaxeMineable) throws IOException {
		terrain();
		biomes();
		blocks(pickaxeMineable);
	}

	// ------------------------------------------------------------------ terreno

	static String num(double v) {
		return String.format(Locale.ROOT, "%s", v == Math.rint(v) ? String.valueOf((long) v) + ".0" : String.valueOf(v));
	}

	static String noise2d(String noise, double xz) {
		return "{\"type\":\"minecraft:noise\",\"noise\":\"" + noise + "\",\"xz_scale\":" + num(xz) + ",\"y_scale\":0.0}";
	}

	static String noise3d(String noise, double xz, double y) {
		return "{\"type\":\"minecraft:noise\",\"noise\":\"" + noise + "\",\"xz_scale\":" + num(xz) + ",\"y_scale\":" + num(y) + "}";
	}

	static String bin(String type, String a, String b) {
		return "{\"type\":\"minecraft:" + type + "\",\"left\":" + a + ",\"right\":" + b + "}";
	}

	static String add(String a, String b) {
		return bin("add", a, b);
	}

	static String mul(String a, String b) {
		return bin("mul", a, b);
	}

	static String max(String a, String b) {
		return bin("max", a, b);
	}

	static String min(String a, String b) {
		return bin("min", a, b);
	}

	static String unary(String type, String input) {
		return "{\"type\":\"minecraft:" + type + "\",\"input\":" + input + "}";
	}

	static String clamp(String input, double lo, double hi) {
		return "{\"type\":\"minecraft:clamp\",\"input\":" + input + ",\"min\":" + num(lo) + ",\"max\":" + num(hi) + "}";
	}

	static String lerp(String alpha, String first, String second) {
		return "{\"type\":\"minecraft:lerp\",\"alpha\":" + alpha + ",\"first\":" + first + ",\"second\":" + second + "}";
	}

	/** a + b * x */
	static String affine(double a, double b, String x) {
		return add(num(a), mul(num(b), x));
	}

	/** Spline cubica com derivada 0 em cada ponto (degraus suaves). Pontos: location, value, location, value... */
	static String spline(String coordinate, double... points) {
		StringBuilder out = new StringBuilder("{\"type\":\"minecraft:spline\",\"spline\":{\"coordinate\":" + coordinate + ",\"points\":[");
		for (int i = 0; i < points.length; i += 2) {
			out.append(i > 0 ? "," : "").append("{\"derivative\":0.0,\"location\":").append(num(points[i])).append(",\"value\":")
					.append(num(points[i + 1])).append("}");
		}
		return out.append("]}}").toString();
	}

	static String ref(String name) {
		return "\"" + NS + "nether/" + name + "\"";
	}

	/** y do bloco (0..128). */
	static final String Y = "{\"type\":\"minecraft:gradient\",\"axis\":\"y\",\"from_coordinate\":0,\"to_coordinate\":128,\"from_value\":0.0,\"to_value\":128.0}";

	static void noise(String name, int octave, int count, double amplitude) throws IOException {
		ContentGen.write("data/mlinnotas/worldgen/noise/" + name + ".json", "{\"base_amplitude\":" + num(amplitude) + ",\"base_octave\":" + octave
				+ ",\"octave_count\":" + count + "}");
	}

	static void function(String name, String json) throws IOException {
		ContentGen.write("data/mlinnotas/worldgen/density_function/nether/" + name + ".json", json);
	}

	/** Escala da densidade: 1/16 por bloco (positivo = rocha). */
	static final double PER_BLOCK = 1.0 / 16.0;

	static void terrain() throws IOException {
		// ruidos (desvio ~ amplitude/3, alcance ~ +-2*amplitude; octave -8 = feicoes de ~256 blocos)
		noise("nether_terrain_type", -9, 2, 1.0);
		noise("nether_hills", -8, 3, 1.0);
		noise("nether_detail", -5, 2, 1.0);
		noise("nether_valleys", -9, 1, 1.0);
		noise("nether_fords", -7, 1, 1.0);
		noise("nether_ceiling", -6, 2, 1.0);
		noise("nether_stalactites", -3, 1, 1.0);
		noise("nether_stalactite_gate", -6, 1, 1.0);
		noise("nether_grove", -8, 2, 1.0);
		noise("nether_caves", -5, 2, 1.0);

		String hills = ref("hills");
		String detail = noise2d(NS + "nether_detail", 1.0);
		function("terrain_type", noise2d(NS + "nether_terrain_type", 1.0));
		function("hills", noise2d(NS + "nether_hills", 1.0));
		// planicies: quase planas, baixas
		function("plains_height", add(affine(46.0, 7.0, hills), mul("1.0", detail)));
		// colinas do meio
		function("mid_height", add(affine(58.0, 34.0, hills), mul("3.0", detail)));
		// planaltos: patamares ligados por rampas (spline sobre as colinas)
		function("peaks_height", add(spline(hills, -1.0, 64.0, -0.25, 67.0, -0.1, 78.0, 0.15, 80.0, 0.3, 88.0, 1.0, 91.0), mul("1.5", detail)));
		String type = ref("terrain_type");
		function("plains_blend", spline(type, -0.6, 0.0, -0.35, 1.0));
		function("peaks_blend", spline(type, 0.35, 0.0, 0.6, 1.0));
		function("base_height", clamp(lerp(ref("peaks_blend"), lerp(ref("plains_blend"), ref("plains_height"), ref("mid_height")), ref("peaks_height")),
				30.0, 92.0));
		// vale: (1 - 4|v|)^2, cortado em trechos pelo ruido de vau; raso nas planicies, menor nos planaltos
		String valley = unary("square", max("0.0", affine(1.0, -4.0, unary("abs", noise2d(NS + "nether_valleys", 1.0)))));
		String gate = clamp(affine(0.5, 2.0, noise2d(NS + "nether_fords", 1.0)), 0.0, 1.0);
		String carve = mul(mul(mul(mul("34.0", valley), gate), affine(0.4, 0.6, ref("plains_blend"))), affine(1.0, -0.5, ref("peaks_blend")));
		function("valley_carve", carve);
		function("floor", add(ref("base_height"), mul("-1.0", ref("valley_carve"))));
		// teto: ~40 acima do chao (sem o vale), entre 104 e 118, com ondulacao e estalactites
		String stal = mul(mul("40.0", max("0.0", affine(-0.3, 1.0, noise2d(NS + "nether_stalactites", 1.0)))),
				clamp(affine(-0.6, 3.0, noise2d(NS + "nether_stalactite_gate", 1.0)), 0.0, 1.0));
		String roof = add(add(clamp(affine(40.0, 1.0, ref("base_height")), 104.0, 118.0), mul("3.0", noise2d(NS + "nether_ceiling", 1.0))),
				mul("-1.0", stal));
		function("ceiling", max(roof, affine(10.0, 1.0, ref("floor"))));
		// densidade: rocha abaixo do chao e acima do teto
		String ground = mul(num(PER_BLOCK), add(ref("floor"), mul("-1.0", Y)));
		String ceiling = mul(num(PER_BLOCK), add(Y, mul("-1.0", ref("ceiling"))));
		String solid = max(ground, ceiling);
		// cavernas: bolsoes onde o ruido 3D passa de 0.55, so 8+ blocos abaixo do chao e acima de y 36
		String cave = max(max(affine(0.55, -1.0, noise3d(NS + "nether_caves", 1.0, 1.5)),
				mul(num(PER_BLOCK), add(Y, affine(8.0, -1.0, ref("floor"))))), mul(num(PER_BLOCK), affine(36.0, -1.0, Y)));
		function("terrain", min(solid, cave));
		String finalDensity = add("{\"type\":\"minecraft:interpolated\",\"cell_size_xz\":4,\"cell_size_y\":4,\"input\":" + ref("terrain") + "}",
				"{\"type\":\"minecraft:beardifier\"}");

		// clima: biomas vanilla 2x maiores (escala 0.125 em vez de 0.25); erosion = tipo de terreno; weirdness = bosque
		String router = "{\"chunk_surface_level\":0.0,\"continents\":0.0,\"depth\":0.0,"
				+ "\"erosion\":" + ref("terrain_type") + ","
				+ "\"final_density\":" + finalDensity + ","
				+ "\"ridges\":" + noise2d(NS + "nether_grove", 1.0) + ","
				+ "\"temperature\":" + noise2d("minecraft:nether/temperature", 0.125) + ","
				+ "\"vegetation\":" + noise2d("minecraft:nether/vegetation", 0.125) + "}";
		ContentGen.write("data/minecraft/worldgen/noise_settings/nether.json", "{\"default_block\":\"minecraft:netherrack\",\"default_fluid\":"
				+ "\"minecraft:lava\",\"disable_mob_generation\":false,\"legacy_random_source\":true,\"material_rule\":\"minecraft:nether\","
				+ "\"noise\":{\"height\":128,\"min_y\":0},\"noise_router\":" + router + ",\"sea_level\":32,\"spawn_target\":[]}");
	}

	// ------------------------------------------------------------------ biomas

	static final String ASHEN = "ashen_plains";
	static final String PEAKS = "infernite_peaks";
	static final String GROVE = "burning_grove";

	static String spawn(String type, int min, int max, int weight) {
		return "{\"type\":\"" + type + "\",\"count\":{\"type\":\"minecraft:uniform\",\"max_inclusive\":" + max + ",\"min_inclusive\":" + min
				+ "},\"weight\":" + weight + "}";
	}

	static String quote(List<String> values) {
		return ContentGen.quoteAll(values);
	}

	/** Bioma do Nether no formato do 26.3; {@code steps} tem as listas por etapa (indices 0..9). */
	static void biome(String id, String sound, String music, String creatures, String monsters, String particle, double chance, String fog,
			List<List<String>> steps) throws IOException {
		StringBuilder features = new StringBuilder("[");
		for (int i = 0; i < steps.size(); i++) {
			features.append(i > 0 ? "," : "").append("[").append(quote(steps.get(i))).append("]");
		}
		features.append("]");
		ContentGen.write("data/mlinnotas/worldgen/biome/" + id + ".json", "{\"attributes\":{"
				+ "\"minecraft:audio/ambient_sounds\":{\"additions\":{\"sound\":\"minecraft:ambient." + sound + ".additions\",\"tick_chance\":0.0111},"
				+ "\"loop\":\"minecraft:ambient." + sound + ".loop\",\"mood\":{\"block_search_extent\":8,\"offset\":2.0,\"sound\":\"minecraft:ambient."
				+ sound + ".mood\",\"tick_delay\":6000}},"
				+ "\"minecraft:audio/background_music\":{\"default\":{\"max_delay\":24000,\"min_delay\":12000,\"sound\":\"minecraft:music.nether." + music
				+ "\"}},"
				+ "\"minecraft:gameplay/natural_mob_spawns\":{\"argument\":{\"spawn_costs\":{},\"spawns_by_category\":{\"creature\":[" + creatures
				+ "],\"monster\":[" + monsters + "]}},\"modifier\":\"overlay\"},"
				+ "\"minecraft:visual/ambient_particles\":{\"argument\":[{\"particle\":{\"type\":\"" + particle + "\"},\"probability\":" + chance
				+ "}],\"modifier\":\"append\"},"
				+ "\"minecraft:visual/fog_color\":\"" + fog + "\"},\"carvers\":[],\"downfall\":0.0,\"effects\":{\"water_color\":\"#3f76e4\"},"
				+ "\"features\":" + features + ",\"has_precipitation\":false,\"temperature\":2.0}");
	}

	static void biomes() throws IOException {
		List<String> none = List.of();
		String strider = spawn("minecraft:strider", 1, 2, 60);
		// etapa 7 (decoracao subterranea) na mesma ordem relativa dos biomas vanilla
		List<String> ores = List.of("minecraft:ore_magma", "minecraft:ore_gravel_nether", "minecraft:ore_blackstone", "minecraft:ore_gold_nether",
				"minecraft:ore_quartz_nether", "minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small");

		// Planicies de Cinza: plano e aberto, cinza e solo de almas, sem fogo nem fontes de lava; bom para viajar
		List<String> ashenUnder = new java.util.ArrayList<>(List.of("minecraft:glowstone_extra", "minecraft:glowstone"));
		ashenUnder.addAll(ores);
		biome(ASHEN, "soul_sand_valley", "soul_sand_valley", strider,
				spawn("minecraft:ghast", 1, 2, 40) + "," + spawn("minecraft:zombified_piglin", 2, 4, 60) + "," + spawn("minecraft:skeleton", 2, 3, 15) + ","
						+ spawn("minecraft:magma_cube", 1, 3, 10),
				"minecraft:white_ash", 0.09, "#4a403c",
				List.of(none, none, none, none, none, none, none, ashenUnder, none, List.of(NS + "ash_dunes")));

		// Picos de Infernita: planaltos escuros de basalto/pedra-negra com infernita exposta na superficie.
		// (Futuro: covil de chefe aqui.)
		List<String> peaksUnder = List.of("minecraft:basalt_blobs", "minecraft:blackstone_blobs", "minecraft:glowstone_extra", "minecraft:glowstone",
				"minecraft:ore_magma", "minecraft:ore_gold_deltas", "minecraft:ore_quartz_deltas", "minecraft:ore_ancient_debris_large",
				"minecraft:ore_debris_small");
		biome(PEAKS, "basalt_deltas", "basalt_deltas", strider,
				spawn("minecraft:magma_cube", 1, 3, 40) + "," + spawn("minecraft:ghast", 1, 1, 20) + "," + spawn("minecraft:zombified_piglin", 2, 4, 40) + ","
						+ spawn("minecraft:blaze", 1, 2, 5),
				"minecraft:lava", 0.002, "#3a1c16",
				List.of(none, none, none, none, none, none, List.of(NS + "ore_infernite_exposed"), peaksUnder, none,
						List.of(NS + "infernite_outcrops")));

		// Bosque Ardente: fungos gigantes de copa baixa, cipos e Lesmas de Magma
		List<String> groveUnder = new java.util.ArrayList<>(List.of("minecraft:glowstone_extra", "minecraft:glowstone"));
		groveUnder.addAll(ores);
		biome(GROVE, "crimson_forest", "crimson_forest", spawn(NS + "magma_slug", 2, 4, 80) + "," + strider,
				spawn("minecraft:zombified_piglin", 2, 4, 30) + "," + spawn("minecraft:hoglin", 2, 3, 6) + "," + spawn("minecraft:piglin", 2, 3, 6),
				"minecraft:flame", 0.004, "#3a1606",
				List.of(none, none, none, none, none, none, none, groveUnder, none,
						List.of(NS + "burning_trees", NS + "burning_bushes", NS + "patch_ember_sprouts")));
		ContentGen.name("biome", ASHEN, "Planícies de Cinza", "Ashen Plains");
		ContentGen.name("biome", PEAKS, "Picos de Infernita", "Infernite Peaks");
		ContentGen.name("biome", GROVE, "Bosque Ardente", "Burning Grove");

		List<String> all = List.of(NS + ASHEN, NS + PEAKS, NS + GROVE);
		ContentGen.tag("worldgen/biome", "minecraft:is_nether", all, false);
		ContentGen.tag("worldgen/biome", "minecraft:has_structure/nether_fortress", all, false);
		ContentGen.tag("worldgen/biome", "minecraft:has_structure/bastion_remnant", List.of(NS + ASHEN, NS + GROVE), false);
		ContentGen.tag("worldgen/biome", "minecraft:has_structure/ruined_portal_nether", all, false);
		ContentGen.tag("worldgen/biome", "minecraft:has_structure/nether_fossil", List.of(NS + ASHEN), false);

		// superficie: as regras dos biomas novos entram antes das vanilla
		materialRule();

		// features dos biomas novos
		String feature = "data/mlinnotas/worldgen/feature/";
		String placed = "data/mlinnotas/worldgen/placed_feature/";
		ContentGen.write(feature + "burning_tree.json", "{\"type\":\"mlinnotas:burning_tree\",\"min_height\":10,\"max_height\":18}");
		ContentGen.write(feature + "burning_bush.json", "{\"type\":\"mlinnotas:burning_tree\",\"min_height\":3,\"max_height\":5}");
		ContentGen.write(feature + "ash_dune.json", "{\"type\":\"mlinnotas:ash_dune\"}");
		ContentGen.write(feature + "ember_sprouts.json", "{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"mlinnotas:ember_sprouts\"}}");
		// infernita exposta: minerio que nao e descartado ao encostar no ar, em basalto/pedra-negra/netherrack
		String targets = "[{\"state\":\"mlinnotas:infernite_ore\",\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"mlinnotas:infernite_peaks_stone\"}}]";
		ContentGen.write(feature + "ore_infernite_exposed.json", "{\"type\":\"minecraft:ore\",\"discard_chance_on_air_exposure\":0.0,\"size\":7,"
				+ "\"targets\":" + targets + "}");
		ContentGen.write(feature + "infernite_outcrop.json", "{\"type\":\"minecraft:ore\",\"discard_chance_on_air_exposure\":0.0,\"size\":12,"
				+ "\"targets\":" + targets + "}");
		ContentGen.tag("block", "mlinnotas:infernite_peaks_stone", List.of("minecraft:basalt", "minecraft:blackstone", "minecraft:netherrack"), false);
		ContentGen.write(placed + "ore_infernite_exposed.json", "{\"feature\":\"mlinnotas:ore_infernite_exposed\",\"placement\":["
				+ "{\"type\":\"minecraft:count\",\"count\":10},{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:height_range\",\"height\":"
				+ "{\"type\":\"minecraft:uniform\",\"max_inclusive\":{\"absolute\":100},\"min_inclusive\":{\"absolute\":32}}},{\"type\":\"minecraft:biome\"}]}");
		// afloramento: procura o chao (de cima para baixo a partir de uma altura no vao) e poe o veio ali, exposto
		String findFloor = "{\"type\":\"minecraft:environment_scan\",\"allowed_search_condition\":{\"type\":\"minecraft:matching_block_tag\","
				+ "\"tag\":\"minecraft:air\"},\"direction_of_search\":\"down\",\"max_steps\":32,\"target_condition\":{\"type\":\"minecraft:solid\"}}";
		ContentGen.write(placed + "infernite_outcrops.json", "{\"feature\":\"mlinnotas:infernite_outcrop\",\"placement\":["
				+ "{\"type\":\"minecraft:count\",\"count\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":1,\"max_inclusive\":3}},"
				+ "{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:uniform\","
				+ "\"max_inclusive\":{\"absolute\":100},\"min_inclusive\":{\"absolute\":60}}}," + findFloor + ",{\"type\":\"minecraft:biome\"}]}");
		String floorScan = "{\"type\":\"minecraft:count_on_every_layer\",\"count\":%d}";
		ContentGen.write(placed + "burning_trees.json", "{\"feature\":\"mlinnotas:burning_tree\",\"placement\":["
				+ floorScan.formatted(1) + ",{\"type\":\"minecraft:biome\"}]}");
		ContentGen.write(placed + "burning_bushes.json", "{\"feature\":\"mlinnotas:burning_bush\",\"placement\":["
				+ floorScan.formatted(1) + ",{\"type\":\"minecraft:biome\"}]}");
		ContentGen.write(placed + "ash_dunes.json", "{\"feature\":\"mlinnotas:ash_dune\",\"placement\":["
				+ "{\"type\":\"minecraft:rarity_filter\",\"chance\":3}," + floorScan.formatted(1) + ",{\"type\":\"minecraft:biome\"}]}");
		ContentGen.write(placed + "patch_ember_sprouts.json", "{\"feature\":\"mlinnotas:ember_sprouts\",\"placement\":["
				+ floorScan.formatted(24) + ",{\"type\":\"minecraft:biome\"},{\"type\":\"minecraft:block_predicate_filter\",\"predicate\":"
				+ "{\"type\":\"minecraft:all_of\",\"predicates\":[{\"type\":\"minecraft:matching_block_tag\",\"tag\":\"minecraft:air\"},"
				+ "{\"type\":\"minecraft:matching_blocks\",\"blocks\":[\"mlinnotas:ember_nylium\"],\"offset\":[0,-1,0]}]}}]}");
	}

	/** Regra de superficie do Nether: a vanilla com as regras dos biomas novos na frente. */
	static void materialRule() throws IOException {
		String block = "{\"type\":\"minecraft:block\",\"result_state\":\"%s\"}";
		String ifBiome = "{\"type\":\"minecraft:condition\",\"if_true\":{\"type\":\"minecraft:biome\",\"biome_is\":\"%s\"},\"then_run\":%s}";
		String cond = "{\"type\":\"minecraft:condition\",\"if_true\":%s,\"then_run\":%s}";
		String noise = "{\"type\":\"minecraft:noise_threshold\",\"max_threshold\":1.7976931348623157E308,\"min_threshold\":%s,\"noise\":\"%s\"}";
		String seq = "{\"type\":\"minecraft:sequence\",\"sequence\":[%s]}";
		// cinza com manchas de solo de almas no chao; embaixo, algumas camadas de cinza
		String ashen = ifBiome.formatted(NS + ASHEN, seq.formatted(String.join(",",
				cond.formatted("\"minecraft:on_floor\"", seq.formatted(cond.formatted(noise.formatted("0.25", "minecraft:nether_state_selector"),
						block.formatted("minecraft:soul_soil")) + "," + block.formatted(NS + "nether_ash"))),
				cond.formatted("\"minecraft:under_floor\"", block.formatted(NS + "nether_ash")))));
		// basalto e pedra-negra (escuro) no chao e logo abaixo; teto de pedra-negra
		String peaks = ifBiome.formatted(NS + PEAKS, seq.formatted(String.join(",",
				cond.formatted("\"minecraft:under_ceiling\"", block.formatted("minecraft:blackstone")),
				cond.formatted("\"minecraft:under_floor\"", seq.formatted(cond.formatted(noise.formatted("0.0", "minecraft:nether_state_selector"),
						block.formatted("minecraft:basalt")) + "," + block.formatted("minecraft:blackstone"))))));
		// nilio de brasas no chao
		String grove = ifBiome.formatted(NS + GROVE, cond.formatted("\"minecraft:on_floor\"", block.formatted(NS + "ember_nylium")));
		String head = "{\"type\":\"minecraft:sequence\",\"sequence\":[\"minecraft:bedrock_floor\",\"minecraft:bedrock_roof\","
				+ "{\"type\":\"minecraft:condition\",\"if_true\":{\"type\":\"minecraft:y_above\",\"add_stone_depth\":false,\"anchor\":{\"below_top\":5},"
				+ "\"surface_depth_multiplier\":0},\"then_run\":{\"type\":\"minecraft:block\",\"result_state\":\"minecraft:netherrack\"}},";
		ContentGen.write("data/minecraft/worldgen/material_rule/nether.json", head + ashen + "," + peaks + "," + grove + ","
				+ VanillaNetherRule.TAIL.strip() + "]}");
	}

	// fortaleza e bastiao: refeitos em vstructures/ (JSON em SNetherContent)

	// ------------------------------------------------------------------ blocos, itens, criatura

	static void blocks(List<String> pickaxeMineable) throws IOException {
		EndContent.cubeAll("nether_ash", "Cinza do Nether", "Nether Ash");
		ContentGen.tag("block", "minecraft:mineable/shovel", List.of(NS + "nether_ash"), false);
		ContentGen.tag("block", "minecraft:soul_fire_base_blocks", List.of(NS + "nether_ash"), false);

		EndContent.pillar("burning_stem", "Caule Ardente", "Burning Stem");
		EndContent.cubeAll("burning_cap", "Chapéu Ardente", "Burning Cap");
		ContentGen.shaped("burning_planks", "S", Map.of("S", NS + "burning_stem"), "building", 4);
		EndContent.cubeAll("burning_planks", "Tábuas Ardentes", "Burning Planks");
		ContentGen.tag("block", "minecraft:mineable/axe", List.of(NS + "burning_stem", NS + "burning_planks"), false);
		ContentGen.tag("block", "minecraft:mineable/hoe", List.of(NS + "burning_cap"), false);
		ContentGen.tag("block", "minecraft:planks", List.of(NS + "burning_planks"), false);
		ContentGen.tag("item", "minecraft:planks", List.of(NS + "burning_planks"), false);
		ContentGen.tag("item", "minecraft:non_flammable_wood", List.of(NS + "burning_stem", NS + "burning_planks"), false);

		// nilio de brasas: topo laranja, lado netherrack com borda; solta netherrack sem toque de seda
		EndContent.cubeBottomTop("ember_nylium", NS + "block/ember_nylium", NS + "block/ember_nylium_side", "minecraft:block/netherrack");
		ContentGen.name("block", "ember_nylium", "Nílio de Brasas", "Ember Nylium");
		ContentGen.write("data/mlinnotas/loot_table/blocks/ember_nylium.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":"
				+ "\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"condition\":\"minecraft:tool/can_silk_touch\",\"name\":"
				+ "\"mlinnotas:ember_nylium\"},{\"type\":\"minecraft:item\",\"condition\":{\"type\":\"minecraft:survives_explosion\"},\"name\":"
				+ "\"minecraft:netherrack\"}]}],\"rolls\":1}],\"random_sequence\":\"mlinnotas:blocks/ember_nylium\"}");
		pickaxeMineable.add(NS + "ember_nylium");
		ContentGen.tag("block", "minecraft:nylium", List.of(NS + "ember_nylium"), false);
		ContentGen.tag("block", "minecraft:supports_nether_sprouts", List.of(NS + "ember_nylium"), false);

		// brotos de brasa: planta em cruz no nilio
		ContentGen.write("assets/mlinnotas/blockstates/ember_sprouts.json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/ember_sprouts\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/ember_sprouts.json", "{\"parent\":\"minecraft:block/cross\",\"textures\":{\"cross\":"
				+ "\"mlinnotas:block/ember_sprouts\"}}");
		EndContent.flatBlockItem("ember_sprouts");
		ContentGen.name("block", "ember_sprouts", "Brotos de Brasa", "Ember Sprouts");
		ContentGen.write("data/mlinnotas/loot_table/blocks/ember_sprouts.json", "{\"type\":\"minecraft:block\",\"pools\":[{\"condition\":{\"type\":"
				+ "\"minecraft:match_tool\",\"predicate\":{\"items\":\"minecraft:shears\"}},\"entries\":[{\"type\":\"minecraft:item\",\"name\":"
				+ "\"mlinnotas:ember_sprouts\"}],\"rolls\":1}],\"random_sequence\":\"mlinnotas:blocks/ember_sprouts\"}");

		// Lesma de Magma
		EndContent.item("magma_slug_spawn_egg", "Ovo Gerador de Lesma de Magma", "Magma Slug Spawn Egg");
		ContentGen.name("entity", "magma_slug", "Lesma de Magma", "Magma Slug");
		EndContent.entityLoot("magma_slug", "{\"entries\":[{\"type\":\"minecraft:item\",\"modifier\":[{\"type\":\"minecraft:set_count\",\"count\":"
				+ "{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":2}},{\"type\":\"minecraft:enchanted_count_increase\",\"enchantment\":"
				+ "\"minecraft:looting\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":0,\"max\":1}}],\"name\":\"minecraft:magma_cream\"}],\"rolls\":1}");
	}
}
