import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gera os JSONs do conteudo com niveis (escada de 12 materiais de com.mlin.notas.tier.Tier): minerios, lingotes,
 * ferramentas, armaduras, varinhas, baus, upgrades de fornalha e de bau, tags de mineracao, worldgen e nomes (lang).
 *
 * Uso (na raiz do projeto): java scripts/ContentGen.java src/main/resources
 *
 * Idempotente: reescreve os arquivos gerados e mescla as chaves de lang nos arquivos existentes (as demais chaves
 * ficam intactas). Ao mudar a escada em Tier.java, mude a lista TIERS abaixo tambem (o teste
 * TierResourcesTest confere que os arquivos existem para cada item registrado).
 */
public class ContentGen {
	record T(String id, String pt, String en, boolean vanilla, boolean ore, String material) {
		boolean modGear() {
			return !vanilla;
		}
	}

	static final List<T> TIERS = List.of(
			new T("wood", "Madeira", "Wooden", true, false, "#minecraft:planks"),
			new T("stone", "Pedra", "Stone", true, false, "minecraft:cobblestone"),
			new T("copper", "Cobre", "Copper", true, false, "minecraft:copper_ingot"),
			new T("iron", "Ferro", "Iron", true, false, "minecraft:iron_ingot"),
			new T("gold", "Ouro", "Gold", true, false, "minecraft:gold_ingot"),
			new T("cobalt", "Cobalto", "Cobalt", false, true, "mlinnotas:cobalt_ingot"),
			new T("diamond", "Diamante", "Diamond", true, false, "minecraft:diamond"),
			new T("netherite", "Netherite", "Netherite", true, false, "minecraft:netherite_ingot"),
			new T("infernite", "Infernita", "Infernite", false, true, "mlinnotas:infernite_ingot"),
			new T("endite", "Endita", "Endite", false, true, "mlinnotas:endite_ingot"),
			new T("astralite", "Astralita", "Astralite", false, true, "mlinnotas:astralite_ingot"),
			new T("celestium", "Celéstio", "Celestium", false, false, "mlinnotas:celestium_ingot"));

	/** Tags "needs_X_tool" e o nivel minimo (indice em TIERS) que as quebra. */
	static final Map<String, Integer> NEEDS = new LinkedHashMap<>();

	static {
		NEEDS.put("#minecraft:needs_stone_tool", 1);
		NEEDS.put("#minecraft:needs_iron_tool", 3);
		NEEDS.put("#mlinnotas:needs_cobalt_tool", 5);
		NEEDS.put("#minecraft:needs_diamond_tool", 6);
		NEEDS.put("#mlinnotas:needs_netherite_tool", 7);
		NEEDS.put("#mlinnotas:needs_infernite_tool", 8);
		NEEDS.put("#mlinnotas:needs_endite_tool", 9);
		NEEDS.put("#mlinnotas:needs_astralite_tool", 10);
	}

	// ferramentas: sufixo, nome pt, nome en, padrao de receita
	static final String[][] GEAR = {
			{"sword", "Espada", "Sword", "X|X|#"},
			{"shovel", "Pá", "Shovel", "X|#|#"},
			{"pickaxe", "Picareta", "Pickaxe", "XXX| # | # "},
			{"axe", "Machado", "Axe", "XX|X#| #"},
			{"hoe", "Enxada", "Hoe", "XX| #| #"},
			{"spear", "Lança", "Spear", "  X| # |#  "},
	};
	static final String[][] ARMOR = {
			{"helmet", "Capacete", "Helmet", "XXX|X X", "head_armor"},
			{"chestplate", "Peitoral", "Chestplate", "X X|XXX|XXX", "chest_armor"},
			{"leggings", "Calças", "Leggings", "XXX|X X|X X", "leg_armor"},
			{"boots", "Botas", "Boots", "X X|X X", "foot_armor"},
	};

	static Path root;
	static final Map<String, String> PT = new LinkedHashMap<>();
	static final Map<String, String> EN = new LinkedHashMap<>();

	public static void main(String[] args) throws IOException {
		root = Path.of(args.length > 0 ? args[0] : "src/main/resources");
		List<String> pickaxeMineable = new ArrayList<>();
		Map<String, List<String>> vanillaItemTags = new LinkedHashMap<>();

		// --- materiais e minerios ---
		for (T t : TIERS) {
			if (t.ore) {
				simpleItem("raw_" + t.id, "item/generated");
				name("item", "raw_" + t.id, t.pt + " Bruto", "Raw " + t.en);
				cooking("smelting", "raw_" + t.id, t.id + "_ingot", 200, 1.0);
				cooking("blasting", "raw_" + t.id, t.id + "_ingot", 100, 1.0);
			}
			if (t.modGear()) {
				simpleItem(t.id + "_ingot", "item/generated");
				name("item", t.id + "_ingot", "Lingote de " + t.pt, t.en + " Ingot");
				tag("item", "mlinnotas:" + t.id + "_tool_materials", List.of(t.material), false);
				tag("item", "mlinnotas:repairs_" + t.id + "_armor", List.of(t.material), false);
			}
		}
		write("data/mlinnotas/recipe/celestium_ingot.json", "{\"type\":\"mlinnotas:skill_gated_shapeless\",\"category\":\"misc\",\"skill\":\"mining\",\"level\":12,\"ingredients\":["
				+ quoteAll(List.of("mlinnotas:astralite_ingot", "mlinnotas:astralite_ingot", "mlinnotas:endite_ingot", "mlinnotas:endite_ingot",
						"mlinnotas:infernite_ingot", "mlinnotas:infernite_ingot", "minecraft:netherite_ingot"))
				+ "],\"result\":{\"id\":\"mlinnotas:celestium_ingot\"}}");

		ore("cobalt_ore", "Minério de Cobalto", "Cobalt Ore", "cobalt");
		ore("deepslate_cobalt_ore", "Minério de Cobalto de Ardósia", "Deepslate Cobalt Ore", "cobalt");
		ore("infernite_ore", "Minério de Infernita", "Infernite Ore", "infernite");
		ore("endite_ore", "Minério de Endita", "Endite Ore", "endite");
		ore("astralite_ore", "Minério de Astralita", "Astralite Ore", "astralite");
		for (String ore : List.of("cobalt_ore", "deepslate_cobalt_ore", "infernite_ore", "endite_ore", "astralite_ore")) {
			pickaxeMineable.add("mlinnotas:" + ore);
		}

		// --- requisitos de mineracao ---
		tag("block", "minecraft:needs_iron_tool", List.of("mlinnotas:cobalt_ore", "mlinnotas:deepslate_cobalt_ore"), false);
		tag("block", "mlinnotas:needs_cobalt_tool", List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"), false);
		tag("block", "mlinnotas:needs_netherite_tool", List.of("mlinnotas:infernite_ore"), false);
		tag("block", "mlinnotas:needs_infernite_tool", List.of("mlinnotas:endite_ore"), false);
		tag("block", "mlinnotas:needs_endite_tool", List.of("mlinnotas:astralite_ore"), false);
		for (int level = 0; level < TIERS.size(); level++) {
			T t = TIERS.get(level);
			List<String> incorrect = new ArrayList<>();
			for (Map.Entry<String, Integer> need : NEEDS.entrySet()) {
				boolean modTag = need.getKey().startsWith("#mlinnotas:");
				// vanilla (menos ouro): as tags vanilla ja estao la, so somamos as do mod
				boolean include = t.modGear() || t.id.equals("gold") || modTag;
				if (need.getValue() > level && include) {
					incorrect.add(need.getKey());
				}
			}
			String vanillaName = switch (t.id) {
				case "wood" -> "wooden";
				default -> t.id;
			};
			if (t.vanilla) {
				// ouro vira um nivel de verdade (entre ferro e cobalto): substitui a tag vanilla
				tag("block", "minecraft:incorrect_for_" + vanillaName + "_tool", incorrect, t.id.equals("gold"));
			} else {
				tag("block", "mlinnotas:incorrect_for_" + t.id + "_tool", incorrect, false);
			}
		}

		// --- ferramentas e armaduras dos niveis novos ---
		for (T t : TIERS) {
			if (!t.modGear()) {
				continue;
			}
			for (String[] g : GEAR) {
				String id = t.id + "_" + g[0];
				if (g[0].equals("spear")) {
					spearItem(id);
				} else {
					simpleItem(id, "item/handheld");
				}
				name("item", id, g[1] + " de " + t.pt, t.en + " " + g[2]);
				if (!ProgressionContent.smithingUpgrade(t.id, g[0])) { // infernita+ = mesa de ferraria
					shaped(id, g[3], Map.of("X", t.material, "#", "minecraft:stick"), "equipment");
				}
				vanillaItemTags.computeIfAbsent(g[0] + "s", k -> new ArrayList<>()).add("mlinnotas:" + id);
			}
			for (String[] a : ARMOR) {
				String id = t.id + "_" + a[0];
				simpleItem(id, "item/generated");
				name("item", id, a[1] + " de " + t.pt, t.en + " " + a[2]);
				if (!ProgressionContent.smithingUpgrade(t.id, a[0])) {
					shaped(id, a[3], Map.of("X", t.material), "equipment");
				}
				vanillaItemTags.computeIfAbsent(a[4], k -> new ArrayList<>()).add("mlinnotas:" + id);
			}
			write("assets/mlinnotas/equipment/" + t.id + ".json", "{\"layers\":{\"humanoid\":[{\"texture\":\"mlinnotas:" + t.id
					+ "\"}],\"humanoid_leggings\":[{\"texture\":\"mlinnotas:" + t.id + "\"}]}}");
		}
		for (Map.Entry<String, List<String>> entry : vanillaItemTags.entrySet()) {
			tag("item", "minecraft:" + entry.getKey(), entry.getValue(), false);
		}

		// --- varinhas (todos os niveis) ---
		for (T t : TIERS) {
			String id = t.id.equals("wood") ? "wooden_wand" : t.id + "_wand";
			simpleItem(id, "item/handheld");
			name("item", id, "Varinha de Construção de " + t.pt, t.en + " Building Wand");
			// 2x2 na diagonal: o padrao antigo ("  M| S |S  ") era o mesmo da lanca vanilla
			shaped(id, " M|S ", Map.of("M", t.material, "S", "minecraft:stick"), "equipment");
		}

		// --- upgrades de fornalha (pedra em diante; cada um usa o anterior no centro) ---
		String previousFurnace = "minecraft:furnace";
		for (T t : TIERS) {
			if (t.id.equals("wood")) {
				continue;
			}
			String id = "furnace_upgrade_" + t.id;
			simpleItem(id, "item/generated");
			name("item", id, "Upgrade de Fornalha de " + t.pt, t.en + " Furnace Upgrade");
			String material = t.id.equals("stone") ? "minecraft:smooth_stone" : t.material;
			shaped(id, "III|IFI|III", Map.of("I", material, "F", previousFurnace), "equipment");
			previousFurnace = "mlinnotas:" + id;
		}

		// --- baus (pedra em diante; o de madeira e o vanilla) ---
		String previousChest = "minecraft:chest";
		for (T t : TIERS) {
			if (t.id.equals("wood")) {
				continue;
			}
			String id = t.id + "_chest";
			String pt = t.id.equals("copper") ? "Baú Grande de Cobre" : "Baú de " + t.pt;
			String en = t.id.equals("copper") ? "Large Copper Chest" : t.en + " Chest";
			name("block", id, pt, en);
			name("container", id, pt, en);
			write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{"
					+ "\"facing=east\":{\"model\":\"mlinnotas:block/" + id + "\",\"y\":90},"
					+ "\"facing=north\":{\"model\":\"mlinnotas:block/" + id + "\"},"
					+ "\"facing=south\":{\"model\":\"mlinnotas:block/" + id + "\",\"y\":180},"
					+ "\"facing=west\":{\"model\":\"mlinnotas:block/" + id + "\",\"y\":270}}}");
			write("assets/mlinnotas/models/block/" + id + ".json", "{\"textures\":{\"particle\":\"mlinnotas:block/" + id + "_side\"}}");
			write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:special\",\"base\":\"mlinnotas:item/" + id
					+ "\",\"model\":{\"type\":\"minecraft:chest\",\"texture\":\"mlinnotas:" + t.id + "\"}}}");
			write("assets/mlinnotas/models/item/" + id + ".json", "{\"parent\":\"minecraft:item/template_chest\",\"textures\":{\"particle\":\"mlinnotas:block/"
					+ id + "_side\"}}");
			write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"condition\":{\"type\":"
					+ "\"minecraft:survives_explosion\"},\"entries\":[{\"type\":\"minecraft:item\",\"modifier\":{\"type\":\"minecraft:copy_components\","
					+ "\"include\":[\"minecraft:custom_name\"],\"source\":\"block_entity\"},\"name\":\"mlinnotas:" + id + "\"}],\"rolls\":1}],"
					+ "\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
			shaped(id, "MMM|MCM|MMM", Map.of("M", t.material, "C", previousChest), "misc");
			previousChest = "mlinnotas:" + id;
			pickaxeMineable.add("mlinnotas:" + id);

			String upgrade = "chest_upgrade_" + t.id;
			simpleItem(upgrade, "item/generated");
			name("item", upgrade, "Upgrade de Baú de " + t.pt, t.en + " Chest Upgrade");
			shaped(upgrade, "MMM|MCM|MMM", Map.of("M", t.material, "C", "#minecraft:planks"), "misc");
		}
		EndContent.generate(pickaxeMineable);
		RuinsContent.generate(pickaxeMineable);
		NetherContent.generate(pickaxeMineable);
		BossContent.generate(pickaxeMineable);
		DungeonContent.generate();
		BeaconContent.generate(pickaxeMineable);
		DragonContent.generate(pickaxeMineable);
		GuideContent.generate();
		AccessoryContent.generate();
		GatingContent.generate();
		GraveContent.generate();
		QuestContent.generate();
		VanillaStructuresContent.generate();
		VillageContent.generate();
		Village2Content.generate();
		Village3Content.generate();
		Village4Content.generate();
		CraftsContent.generate();
		WorksContent.generate();
		SNetherContent.generate();
		CompassContent.generate();
		StorageContent.generate(pickaxeMineable);
		SeaStructuresContent.generate();
		FoodContent.generate();
		CombatContent.generate();
		SmainContent.generate();
		ProgressionContent.generate();
		Qol2Content.generate();
		FoodBuildingsContent.generate();
		CivicContent.generate(pickaxeMineable);
		VillageCapacityContent.generate();
		PolishContent.generate();
		CrownContent.generate();
		FieldContent.generate();
		PlotsContent.generate();
		tag("block", "minecraft:mineable/pickaxe", pickaxeMineable, false);

		// --- worldgen ---
		String stone = "{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"}";
		String deepslate = "{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:deepslate_ore_replaceables\"}";
		String netherrack = "{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:netherrack\"}";
		String endStone = "{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:end_stone\"}";
		oreFeature("ore_cobalt", 5, 0.3, "{\"state\":\"mlinnotas:cobalt_ore\",\"target\":" + stone + "},{\"state\":\"mlinnotas:deepslate_cobalt_ore\",\"target\":" + deepslate + "}");
		placed("ore_cobalt", 4, "{\"type\":\"minecraft:trapezoid\",\"max_inclusive\":{\"absolute\":16},\"min_inclusive\":{\"above_bottom\":0}}");
		oreFeature("ore_infernite", 5, 0.2, "{\"state\":\"mlinnotas:infernite_ore\",\"target\":" + netherrack + "}");
		placed("ore_infernite", 5, "{\"type\":\"minecraft:uniform\",\"max_inclusive\":{\"below_top\":10},\"min_inclusive\":{\"above_bottom\":10}}");
		oreFeature("ore_endite", 6, 0.0, "{\"state\":\"mlinnotas:endite_ore\",\"target\":" + endStone + "}");
		placed("ore_endite", 5, "{\"type\":\"minecraft:uniform\",\"max_inclusive\":{\"absolute\":96},\"min_inclusive\":{\"absolute\":8}}");
		oreFeature("ore_astralite", 4, 0.0, "{\"state\":\"mlinnotas:astralite_ore\",\"target\":" + endStone + "}");
		placed("ore_astralite", 3, "{\"type\":\"minecraft:uniform\",\"max_inclusive\":{\"absolute\":96},\"min_inclusive\":{\"absolute\":8}}");

		// --- textos fixos ---
		PT.put("gui.mlinnotas.chest.rows", "Linhas %s-%s de %s");
		EN.put("gui.mlinnotas.chest.rows", "Rows %s-%s of %s");

		mergeLang("assets/mlinnotas/lang/pt_br.json", PT);
		mergeLang("assets/mlinnotas/lang/en_us.json", EN);
		System.out.println("ok");
	}

	static void ore(String id, String pt, String en, String material) throws IOException {
		write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/" + id + "\"}}}");
		write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"mlinnotas:block/" + id + "\"}}");
		write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
		name("block", id, pt, en);
		write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"entries\":[{\"type\":\"minecraft:alternatives\","
				+ "\"children\":[{\"type\":\"minecraft:item\",\"condition\":\"minecraft:tool/can_silk_touch\",\"name\":\"mlinnotas:" + id + "\"},"
				+ "{\"type\":\"minecraft:item\",\"modifier\":[{\"type\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\","
				+ "\"formula\":\"minecraft:ore_drops\"},{\"type\":\"minecraft:explosion_decay\"}],\"name\":\"mlinnotas:raw_" + material + "\"}]}],"
				+ "\"rolls\":1}],\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
		// minerio direto na fornalha tambem da o lingote (como os vanilla)
		cooking("smelting", id, material + "_ingot", 200, 1.0);
		cooking("blasting", id, material + "_ingot", 100, 1.0);
	}

	static void oreFeature(String id, int size, double discard, String targets) throws IOException {
		write("data/mlinnotas/worldgen/feature/" + id + ".json", "{\"type\":\"minecraft:ore\",\"discard_chance_on_air_exposure\":" + discard
				+ ",\"size\":" + size + ",\"targets\":[" + targets + "]}");
	}

	static void placed(String id, int count, String height) throws IOException {
		write("data/mlinnotas/worldgen/placed_feature/" + id + ".json", "{\"feature\":\"mlinnotas:" + id + "\",\"placement\":["
				+ "{\"type\":\"minecraft:count\",\"count\":" + count + "},{\"type\":\"minecraft:in_square\"},"
				+ "{\"type\":\"minecraft:height_range\",\"height\":" + height + "},{\"type\":\"minecraft:biome\"}]}");
	}

	static void simpleItem(String id, String parent) throws IOException {
		write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:item/" + id + "\"}}");
		write("assets/mlinnotas/models/item/" + id + ".json", "{\"parent\":\"minecraft:" + parent + "\",\"textures\":{\"layer0\":\"mlinnotas:item/" + id + "\"}}");
	}

	static void spearItem(String id) throws IOException {
		write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:select\",\"cases\":[{\"model\":{\"type\":\"minecraft:model\","
				+ "\"model\":\"mlinnotas:item/" + id + "\"},\"when\":[\"gui\",\"ground\",\"fixed\",\"on_shelf\"]}],\"fallback\":{\"type\":\"minecraft:model\","
				+ "\"model\":\"mlinnotas:item/" + id + "_in_hand\"},\"property\":\"minecraft:display_context\"},\"swap_animation_scale\":1.95}");
		write("assets/mlinnotas/models/item/" + id + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mlinnotas:item/" + id + "\"}}");
		write("assets/mlinnotas/models/item/" + id + "_in_hand.json", "{\"parent\":\"minecraft:item/spear_in_hand\",\"textures\":{\"layer0\":\"mlinnotas:item/"
				+ id + "_in_hand\"}}");
	}

	static void cooking(String type, String input, String output, int time, double xp) throws IOException {
		write("data/mlinnotas/recipe/" + output + "_from_" + type + "_" + input + ".json", "{\"type\":\"minecraft:" + type + "\",\"cookingtime\":" + time
				+ ",\"experience\":" + xp + ",\"group\":\"" + output + "\",\"ingredient\":\"mlinnotas:" + input + "\",\"result\":{\"id\":\"mlinnotas:" + output + "\"}}");
	}

	static void shaped(String id, String pattern, Map<String, String> key, String category, int count) throws IOException {
		shaped(id, pattern, key, category);
		if (count > 1) {
			Path path = root.resolve("data/mlinnotas/recipe/" + id + ".json");
			Files.writeString(path, Files.readString(path).replace("\"id\": \"mlinnotas:" + id + "\"", "\"id\": \"mlinnotas:" + id + "\",\n    \"count\": " + count));
		}
	}

	static void shaped(String id, String pattern, Map<String, String> key, String category) throws IOException {
		String keys = new java.util.TreeMap<>(key).entrySet().stream()
				.filter(e -> pattern.contains(e.getKey()))
				.map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
				.collect(Collectors.joining(","));
		String rows = List.of(pattern.split("\\|")).stream().map(r -> "\"" + r + "\"").collect(Collectors.joining(","));
		write("data/mlinnotas/recipe/" + id + ".json", "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"" + category + "\",\"key\":{" + keys
				+ "},\"pattern\":[" + rows + "],\"result\":{\"id\":\"mlinnotas:" + id + "\"}}");
	}

	static final Map<String, java.util.Set<String>> TAGS = new LinkedHashMap<>();

	static void tag(String kind, String tag, List<String> values, boolean replace) throws IOException {
		String[] parts = tag.split(":");
		String path = "data/" + parts[0] + "/tags/" + kind + "/" + parts[1] + ".json";
		// a mesma tag pode vir de varias partes da geracao (End, Nether...): junta os valores em vez de sobrescrever
		java.util.Set<String> merged = TAGS.computeIfAbsent(path, k -> new java.util.LinkedHashSet<>());
		merged.addAll(values);
		write(path, "{" + (replace ? "\"replace\":true," : "") + "\"values\":[" + quoteAll(List.copyOf(merged)) + "]}");
	}

	static String quoteAll(List<String> values) {
		return values.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(","));
	}

	static void name(String kind, String id, String pt, String en) {
		PT.put(kind + ".mlinnotas." + id, pt);
		EN.put(kind + ".mlinnotas." + id, en);
	}

	static void write(String relative, String json) throws IOException {
		Path path = root.resolve(relative);
		Files.createDirectories(path.getParent());
		Files.writeString(path, pretty(json) + "\n", StandardCharsets.UTF_8);
	}

	/** Indenta JSON compacto com 2 espacos (sem depender de biblioteca). */
	static String pretty(String json) {
		StringBuilder out = new StringBuilder();
		int indent = 0;
		boolean inString = false;
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
				inString = !inString;
			}
			if (inString) {
				out.append(c);
				continue;
			}
			switch (c) {
				case '{', '[' -> {
					char close = c == '{' ? '}' : ']';
					if (i + 1 < json.length() && json.charAt(i + 1) == close) {
						out.append(c).append(close);
						i++;
					} else {
						out.append(c).append('\n').append("  ".repeat(++indent));
					}
				}
				case '}', ']' -> out.append('\n').append("  ".repeat(--indent)).append(c);
				case ',' -> out.append(",\n").append("  ".repeat(indent));
				case ':' -> out.append(": ");
				default -> out.append(c);
			}
		}
		return out.toString();
	}

	static final Pattern LANG_LINE = Pattern.compile("^\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,?\\s*$");

	/** Mescla as chaves geradas no lang existente, preservando ordem e textos das chaves que ja existem fora da geracao. */
	static void mergeLang(String relative, Map<String, String> generated) throws IOException {
		Path path = root.resolve(relative);
		Map<String, String> entries = new LinkedHashMap<>();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			Matcher m = LANG_LINE.matcher(line);
			if (m.matches()) {
				entries.put(m.group(1), m.group(2));
			}
		}
		for (Map.Entry<String, String> entry : generated.entrySet()) {
			entries.put(entry.getKey(), entry.getValue().replace("\\", "\\\\").replace("\"", "\\\""));
		}
		StringBuilder out = new StringBuilder("{\n");
		int i = 0;
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			out.append("  \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append('"');
			out.append(++i < entries.size() ? ",\n" : "\n");
		}
		out.append("}\n");
		Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
	}
}
