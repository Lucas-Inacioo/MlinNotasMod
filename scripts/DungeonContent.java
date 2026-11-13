import java.io.IOException;
import java.util.List;

/**
 * Conteudo das masmorras medias para o ContentGen: estruturas (uma por tema, tipo mlinnotas:dungeon) num structure set
 * proprio, tags de bioma, tabelas de loot dungeon_* (baus comuns, biblioteca, arsenal, nicho do enigma, bau final e o
 * cofre com premio por jogador), Portao Selado, Chave da Masmorra e textos (lang). Chamado por ContentGen.main.
 */
class DungeonContent {
	// id da estrutura, tema, nome pt, nome en, mini-chefe pt, mini-chefe en, biomas
	static final String[][] DUNGEONS = {
			{"forgotten_crypt", "forgotten_crypt", "Cripta Esquecida", "Forgotten Crypt", "Senhor da Cripta", "Crypt Lord",
					"minecraft:plains,minecraft:sunflower_plains,minecraft:meadow,#minecraft:is_forest,minecraft:dark_forest,minecraft:swamp"},
			{"sunken_hold", "sunken_hold", "Fortaleza Anã Afundada", "Sunken Dwarven Hold", "Rei Anão Caído", "Fallen Dwarf King",
					"#minecraft:is_mountain,#minecraft:is_hill,minecraft:windswept_gravelly_hills,minecraft:windswept_forest,minecraft:stony_shore"},
			{"drowned_temple", "drowned_temple", "Templo Afogado", "Drowned Temple", "Sumo Sacerdote Afogado", "Drowned High Priest",
					"#minecraft:is_beach,minecraft:stony_shore"},
			{"sand_catacomb", "sand_catacomb", "Catacumba de Areia", "Sand Catacomb", "Faraó Mumificado", "Mummified Pharaoh",
					"minecraft:desert,#minecraft:is_badlands"},
	};

	static void generate() throws IOException {
		StringBuilder set = new StringBuilder();
		for (String[] d : DUNGEONS) {
			String id = d[0];
			ContentGen.write("data/mlinnotas/worldgen/structure/" + id + ".json", "{\"type\":\"mlinnotas:dungeon\",\"theme\":\"" + d[1]
					+ "\",\"biomes\":\"#mlinnotas:has_structure/" + id + "\",\"spawn_overrides\":{},\"step\":\"surface_structures\"}");
			ContentGen.tag("worldgen/biome", "mlinnotas:has_structure/" + id, List.of(d[6].split(",")), false);
			set.append(set.length() > 0 ? "," : "").append("{\"structure\":\"mlinnotas:").append(id).append("\",\"weight\":1}");
			text("structure.mlinnotas." + id, d[2], d[3]);
			text("entity.mlinnotas.dungeon_boss." + id, d[4], d[5]);
		}
		// uma tentativa a cada ~30 chunks (~480 blocos); se o tema sorteado nao cabe no bioma/terreno o jogo tenta os outros
		ContentGen.write("data/mlinnotas/worldgen/structure_set/dungeons.json", "{\"placement\":{\"type\":\"minecraft:random_spread\",\"salt\":915377,"
				+ "\"separation\":10,\"spacing\":30},\"structures\":[" + set + "]}");

		// --- loot ---
		String book = "{\"type\":\"minecraft:item\",\"modifier\":{\"type\":\"minecraft:enchant_randomly\",\"options\":\"#minecraft:on_random_loot\"},"
				+ "\"name\":\"minecraft:book\",\"weight\":%d}";
		chest("dungeon_common", "[" + item("minecraft:bread", 12, 1, 4) + "," + item("minecraft:cooked_beef", 8, 1, 3) + ","
				+ item("minecraft:baked_potato", 8, 2, 5) + "," + item("minecraft:arrow", 10, 4, 12) + "," + item("minecraft:torch", 10, 4, 12) + ","
				+ item("minecraft:iron_ingot", 10, 1, 4) + "," + item("minecraft:iron_nugget", 8, 3, 9) + "," + item("minecraft:gold_ingot", 6, 1, 3) + ","
				+ item("minecraft:gold_nugget", 6, 3, 9) + "," + item("minecraft:copper_ingot", 8, 2, 6) + "," + item("minecraft:coal", 10, 2, 8) + ","
				+ item("minecraft:bone", 8, 1, 5) + "," + item("minecraft:string", 6, 1, 4) + "," + item("minecraft:gunpowder", 5, 1, 4) + ","
				+ item("mlinnotas:raw_cobalt", 3, 1, 2) + "," + item("minecraft:redstone", 5, 2, 6) + "," + item("minecraft:lapis_lazuli", 4, 2, 6) + ","
				+ item("minecraft:name_tag", 1, 1, 1) + "," + item("minecraft:saddle", 1, 1, 1) + "]", 4, 7,
				pool("[{\"type\":\"minecraft:empty\",\"weight\":10}," + String.format(book, 2) + "," + item("mlinnotas:return_scroll", 3, 1, 1) + ","
						+ item("minecraft:golden_apple", 1, 1, 1) + "]", 1, 1));
		chest("dungeon_library", "[" + item("minecraft:book", 12, 1, 3) + "," + item("minecraft:paper", 10, 2, 6) + "," + item("minecraft:feather", 6, 1, 3)
				+ "," + item("minecraft:ink_sac", 6, 1, 3) + "," + item("minecraft:lapis_lazuli", 8, 2, 8) + "," + item("minecraft:experience_bottle", 6, 1, 4) + ","
				+ item("minecraft:candle", 5, 1, 3) + "," + item("minecraft:map", 3, 1, 1) + "," + item("minecraft:compass", 2, 1, 1) + "]", 3, 5,
				pool("[" + String.format(book, 10) + "," + item("mlinnotas:return_scroll", 3, 1, 1) + ",{\"type\":\"minecraft:empty\",\"weight\":3}]", 1, 2));
		chest("dungeon_armory", "[" + enchanted("minecraft:iron_sword", 6, 5, 15) + "," + enchanted("minecraft:iron_axe", 5, 5, 15) + ","
				+ enchanted("minecraft:iron_pickaxe", 5, 5, 15) + "," + enchanted("minecraft:iron_helmet", 4, 5, 15) + ","
				+ enchanted("minecraft:iron_chestplate", 3, 5, 15) + "," + enchanted("minecraft:iron_leggings", 3, 5, 15) + ","
				+ enchanted("minecraft:iron_boots", 4, 5, 15) + "," + enchanted("minecraft:bow", 4, 5, 15) + "," + enchanted("minecraft:crossbow", 3, 5, 15) + ","
				+ item("minecraft:shield", 4, 1, 1) + "," + item("minecraft:arrow", 10, 8, 20) + "," + item("minecraft:iron_ingot", 8, 2, 5) + ","
				+ enchanted("minecraft:golden_sword", 3, 10, 20) + "," + enchanted("mlinnotas:cobalt_sword", 1, 5, 15) + ","
				+ enchanted("mlinnotas:cobalt_pickaxe", 1, 5, 15) + "]", 2, 4, null);
		chest("dungeon_secret", "[" + item("minecraft:gold_ingot", 10, 2, 6) + "," + item("minecraft:emerald", 8, 1, 4) + ","
				+ item("minecraft:diamond", 4, 1, 2) + "," + item("mlinnotas:raw_cobalt", 6, 2, 4) + "," + item("minecraft:golden_apple", 4, 1, 2) + ","
				+ String.format(book, 6) + "," + item("mlinnotas:return_scroll", 5, 1, 2) + "," + item("minecraft:experience_bottle", 6, 2, 6) + "]", 3, 5, null);
		chest("dungeon_final", "[" + item("mlinnotas:cobalt_ingot", 8, 2, 5) + "," + item("mlinnotas:raw_cobalt", 8, 3, 6) + ","
				+ item("minecraft:gold_ingot", 10, 3, 8) + "," + item("minecraft:diamond", 5, 1, 3) + "," + item("minecraft:emerald", 6, 2, 5) + ","
				+ item("minecraft:experience_bottle", 8, 3, 8) + "," + item("minecraft:golden_apple", 4, 1, 2) + "," + item("mlinnotas:return_scroll", 6, 1, 2)
				+ "," + enchantedBook(6, 15, 30) + "," + item("minecraft:gold_block", 2, 1, 1) + "]", 5, 8,
				pool("[{\"type\":\"minecraft:empty\",\"weight\":6}," + item("minecraft:enchanted_golden_apple", 1, 1, 1) + ","
						+ item("minecraft:totem_of_undying", 1, 1, 1) + "," + item("minecraft:diamond", 2, 2, 4) + "]", 1, 1));
		// cofre: um premio grande + 1 a 2 menores, sorteados para cada jogador que usa a chave
		String rare = "[" + enchanted("mlinnotas:cobalt_sword", 6, 15, 25) + "," + enchanted("mlinnotas:cobalt_pickaxe", 5, 15, 25) + ","
				+ enchanted("mlinnotas:cobalt_axe", 4, 15, 25) + "," + enchanted("mlinnotas:cobalt_chestplate", 4, 15, 25) + ","
				+ enchanted("mlinnotas:cobalt_helmet", 4, 15, 25) + "," + enchanted("minecraft:bow", 4, 20, 30) + ","
				+ enchantedBook(6, 20, 30) + "," + item("minecraft:diamond", 5, 2, 4) + "," + item("mlinnotas:cobalt_ingot", 6, 4, 8) + ","
				+ item("minecraft:enchanted_golden_apple", 1, 1, 1) + "," + item("minecraft:totem_of_undying", 1, 1, 1) + "]";
		String small = "[" + item("minecraft:golden_apple", 6, 1, 2) + "," + item("minecraft:experience_bottle", 8, 4, 10) + ","
				+ item("mlinnotas:return_scroll", 6, 1, 2) + "," + item("minecraft:emerald", 6, 2, 6) + "," + item("mlinnotas:raw_cobalt", 6, 2, 5) + ","
				+ item("minecraft:gold_ingot", 8, 3, 8) + "]";
		ContentGen.write("data/mlinnotas/loot_table/chests/dungeon_vault.json", "{\"type\":\"minecraft:chest\",\"pools\":[" + pool(rare, 1, 1) + ","
				+ pool(small, 1, 2) + "],\"random_sequence\":\"mlinnotas:chests/dungeon_vault\"}");

		// --- portao e chave ---
		ContentGen.write("assets/mlinnotas/blockstates/dungeon_gate.json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/dungeon_gate\"}}}");
		ContentGen.write("assets/mlinnotas/models/block/dungeon_gate.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":"
				+ "\"mlinnotas:block/dungeon_gate\"}}");
		ContentGen.write("assets/mlinnotas/items/dungeon_gate.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/dungeon_gate\"}}");
		ContentGen.name("block", "dungeon_gate", "Portão Selado", "Sealed Gate");
		text("block.mlinnotas.dungeon_gate.tooltip", "Abre com a Chave da Masmorra", "Opens with a Dungeon Key");
		ContentGen.simpleItem("dungeon_key", "item/generated");
		ContentGen.name("item", "dungeon_key", "Chave da Masmorra", "Dungeon Key");
		text("item.mlinnotas.dungeon_key.tooltip", "Abre o Portão Selado e, no cofre, rende um prêmio para quem a usar",
				"Opens the Sealed Gate; use it on the vault for your own reward");

		text("message.mlinnotas.dungeon.gate_locked", "O portão está selado. Derrote o guardião da masmorra para conseguir a chave.",
				"The gate is sealed. Defeat the dungeon's guardian to get the key.");
		text("message.mlinnotas.dungeon.gate_opened", "A chave gira e o portão se abre", "The key turns and the gate opens");
		text("message.mlinnotas.dungeon.boss_defeated", "%s caiu! Soltou %s Chave(s) da Masmorra: uma para cada um abrir o cofre.",
				"%s has fallen! It dropped %s Dungeon Key(s): one for each of you to open the vault.");
	}

	static String item(String id, int weight, int min, int max) {
		return RuinsContent.item(id, weight, min, max);
	}

	static String enchanted(String id, int weight, int min, int max) {
		return "{\"type\":\"minecraft:item\",\"modifier\":{\"type\":\"minecraft:enchant_with_levels\",\"levels\":{\"type\":\"minecraft:uniform\",\"max\":"
				+ max + ",\"min\":" + min + "},\"options\":\"#minecraft:on_random_loot\"},\"name\":\"" + id + "\",\"weight\":" + weight + "}";
	}

	static String enchantedBook(int weight, int min, int max) {
		return enchanted("minecraft:book", weight, min, max);
	}

	static String pool(String entries, int min, int max) {
		String rolls = min == max ? String.valueOf(min) : "{\"type\":\"minecraft:uniform\",\"max\":" + max + ",\"min\":" + min + "}";
		return "{\"entries\":" + entries + ",\"rolls\":" + rolls + "}";
	}

	static void chest(String id, String entries, int min, int max, String extraPool) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/chests/" + id + ".json", "{\"type\":\"minecraft:chest\",\"pools\":[" + pool(entries, min, max)
				+ (extraPool == null ? "" : "," + extraPool) + "],\"random_sequence\":\"mlinnotas:chests/" + id + "\"}");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
