import java.io.IOException;
import java.util.List;

/**
 * Conteudo dos chefes para o ContentGen: altares (blockstate/modelo), itens que os chefes soltam, ovos, loot dos chefes
 * e dos baus dos covis, estruturas dos covis (Santuario de Cobalto, Santuario Abissal, Forja do Colosso) com structure
 * set e tag de bioma proprios, e textos (lang). Chamado por ContentGen.main (o launcher do Java compila junto).
 */
class BossContent {
	// id, nome pt, nome en, textura de baixo (vanilla)
	static final String[][] ALTARS = {
			{"cobalt_altar", "Altar de Cobalto", "Cobalt Altar", "minecraft:block/polished_andesite"},
			{"abyssal_altar", "Altar Abissal", "Abyssal Altar", "minecraft:block/deepslate_tiles"},
			{"astral_altar", "Altar Astral", "Astral Altar", "minecraft:block/purpur_block"},
	};
	// id, pt, en
	static final String[][] BOSSES = {
			{"cobalt_golem", "Golem de Cobalto", "Cobalt Golem"},
			{"abyssal_guardian", "Guardião das Profundezas", "Abyssal Guardian"},
			{"igneous_colossus", "Colosso Ígneo", "Igneous Colossus"},
			{"astral_herald", "Arauto Astral", "Astral Herald"},
	};
	static final String[][] DROPS = {
			{"cobalt_heart", "Coração de Cobalto", "Cobalt Heart", "Solto pelo Golem de Cobalto", "Dropped by the Cobalt Golem"},
			{"abyssal_core", "Núcleo Abissal", "Abyssal Core", "Solto pelo Guardião das Profundezas", "Dropped by the Abyssal Guardian"},
			{"colossus_ember", "Brasa do Colosso", "Colossus Ember", "Solta pelo Colosso Ígneo", "Dropped by the Igneous Colossus"},
			{"astral_fragment", "Fragmento Astral", "Astral Fragment", "Solto pelo Arauto Astral", "Dropped by the Astral Herald"},
			{"dragon_scale", "Escama de Dragão", "Dragon Scale", "Solta pelo Dragão do Ender (2 a 4)", "Dropped by the Ender Dragon (2 to 4)"},
	};
	// id, step, spacing, separation, salt, biomes
	static final String[][] LAIRS = {
			{"cobalt_shrine", "surface_structures", "36", "12", "583201",
					"minecraft:plains,minecraft:sunflower_plains,minecraft:meadow,#minecraft:is_forest,#minecraft:is_taiga,#minecraft:is_savanna,minecraft:snowy_plains"},
			{"abyssal_sanctum", "underground_structures", "12", "4", "583202", "minecraft:deep_dark"},
			{"colossus_forge", "surface_structures", "16", "6", "583203", "mlinnotas:infernite_peaks"},
	};

	static void generate(List<String> pickaxeMineable) throws IOException {
		for (String[] a : ALTARS) {
			String id = a[0];
			ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{\"\":{\"model\":\"mlinnotas:block/" + id + "\"}}}");
			ContentGen.write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{\"top\":"
					+ "\"mlinnotas:block/" + id + "_top\",\"side\":\"mlinnotas:block/" + id + "_side\",\"bottom\":\"" + a[3] + "\"}}");
			ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
			ContentGen.name("block", id, a[1], a[2]);
		}
		text("block.mlinnotas.cobalt_altar.tooltip", "Botão direito com 4 blocos de ferro no inventário invoca o Golem de Cobalto",
				"Right-click with 4 iron blocks in your inventory to summon the Cobalt Golem");
		text("block.mlinnotas.abyssal_altar.tooltip", "Botão direito com 4 fragmentos de eco invoca o Guardião das Profundezas",
				"Right-click with 4 echo shards to summon the Abyssal Guardian");
		text("block.mlinnotas.astral_altar.tooltip", "Botão direito com 4 Núcleos de Sentinela invoca o Arauto Astral",
				"Right-click with 4 Sentinel Cores to summon the Astral Herald");

		for (String[] b : BOSSES) {
			ContentGen.name("entity", b[0], b[1], b[2]);
			ContentGen.simpleItem(b[0] + "_spawn_egg", "item/generated");
			ContentGen.name("item", b[0] + "_spawn_egg", "Ovo Gerador de " + b[1], b[2] + " Spawn Egg");
		}
		ContentGen.name("entity", "cobalt_rock", "Pedra de Cobalto", "Cobalt Rock");
		ContentGen.name("entity", "magma_meteor", "Meteoro de Magma", "Magma Meteor");
		ContentGen.name("entity", "star_bolt", "Raio Estelar", "Star Bolt");
		for (String[] d : DROPS) {
			ContentGen.simpleItem(d[0], "item/generated");
			ContentGen.name("item", d[0], d[1], d[2]);
			text("item.mlinnotas." + d[0] + ".tooltip", d[3], d[4]);
		}

		// loot extra dos chefes (o item principal sai sempre, pelo codigo)
		entity("cobalt_golem", pool("mlinnotas:raw_cobalt", 3, 6) + "," + pool("minecraft:iron_ingot", 4, 8) + "," + pool("minecraft:experience_bottle", 2, 4));
		entity("abyssal_guardian", pool("minecraft:echo_shard", 1, 3) + "," + pool("minecraft:amethyst_shard", 2, 6) + ","
				+ pool("minecraft:experience_bottle", 3, 6) + "," + chance("minecraft:sculk_catalyst", 0.5));
		entity("igneous_colossus", pool("mlinnotas:raw_infernite", 2, 5) + "," + pool("minecraft:magma_cream", 4, 8) + ","
				+ pool("minecraft:blaze_rod", 2, 4) + "," + pool("minecraft:experience_bottle", 4, 8) + "," + chance("minecraft:netherite_scrap", 0.35));
		entity("astral_herald", pool("mlinnotas:raw_endite", 2, 4) + "," + pool("minecraft:shulker_shell", 1, 3) + ","
				+ pool("minecraft:ender_pearl", 2, 5) + "," + pool("minecraft:experience_bottle", 5, 10) + "," + chance("mlinnotas:raw_astralite", 0.5));

		// baus dos covis
		chest("cobalt_shrine", "[" + RuinsContent.item("minecraft:iron_ingot", 12, 2, 6) + "," + RuinsContent.item("minecraft:iron_nugget", 10, 4, 12) + ","
				+ RuinsContent.item("mlinnotas:raw_cobalt", 6, 1, 3) + "," + RuinsContent.item("minecraft:bread", 10, 2, 4) + ","
				+ RuinsContent.item("minecraft:torch", 8, 4, 12) + "," + RuinsContent.item("minecraft:lapis_lazuli", 6, 2, 8) + ","
				+ RuinsContent.item("minecraft:iron_block", 2, 1, 1) + "," + RuinsContent.item("minecraft:golden_apple", 2, 1, 1) + "]", 4, 7);
		chest("abyssal_sanctum", "[" + RuinsContent.item("minecraft:echo_shard", 10, 1, 2) + "," + RuinsContent.item("minecraft:amethyst_shard", 10, 2, 6) + ","
				+ RuinsContent.item("minecraft:sculk", 8, 2, 8) + "," + RuinsContent.item("minecraft:candle", 6, 1, 4) + ","
				+ RuinsContent.item("minecraft:experience_bottle", 8, 1, 4) + "," + RuinsContent.item("minecraft:diamond", 3, 1, 2) + ","
				+ RuinsContent.item("minecraft:disc_fragment_5", 2, 1, 2) + "]", 4, 7);
		chest("colossus_forge", "[" + RuinsContent.item("minecraft:gold_ingot", 12, 2, 6) + "," + RuinsContent.item("mlinnotas:raw_infernite", 6, 1, 3) + ","
				+ RuinsContent.item("minecraft:magma_cream", 8, 2, 5) + "," + RuinsContent.item("minecraft:blaze_rod", 6, 1, 3) + ","
				+ RuinsContent.item("minecraft:fire_charge", 6, 2, 5) + "," + RuinsContent.item("minecraft:netherite_scrap", 1, 1, 1) + ","
				+ RuinsContent.item("minecraft:golden_apple", 3, 1, 1) + "]", 4, 7);

		// covis: estrutura + structure set proprio + tag de bioma
		for (String[] l : LAIRS) {
			String id = l[0];
			ContentGen.write("data/mlinnotas/worldgen/structure/" + id + ".json", "{\"type\":\"mlinnotas:lair\",\"kind\":\"" + id
					+ "\",\"biomes\":\"#mlinnotas:has_structure/" + id + "\",\"spawn_overrides\":{},\"step\":\"" + l[1] + "\"}");
			ContentGen.tag("worldgen/biome", "mlinnotas:has_structure/" + id, List.of(l[5].split(",")), false);
			ContentGen.write("data/mlinnotas/worldgen/structure_set/" + id + ".json", "{\"placement\":{\"type\":\"minecraft:random_spread\",\"salt\":" + l[4]
					+ ",\"separation\":" + l[3] + ",\"spacing\":" + l[2] + "},\"structures\":[{\"structure\":\"mlinnotas:" + id + "\",\"weight\":1}]}");
		}

		text("message.mlinnotas.boss.summoned", "%s foi invocado!", "%s has been summoned!");
		text("message.mlinnotas.boss.awakens", "%s despertou!", "%s awakens!");
		text("message.mlinnotas.boss.phase_two", "%s está enfurecido!", "%s is enraged!");
		text("message.mlinnotas.altar.missing", "O altar pede %s × %s", "The altar asks for %s × %s");
		text("message.mlinnotas.altar.busy", "O chefe deste altar ainda está vivo por perto", "This altar's boss is still alive nearby");
		text("message.mlinnotas.altar.no_room", "Não há espaço para o chefe surgir", "There is no room for the boss to appear");
	}

	static String pool(String item, int min, int max) {
		return "{\"entries\":[{\"type\":\"minecraft:item\",\"modifier\":{\"type\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\","
				+ "\"max\":" + max + ",\"min\":" + min + "}},\"name\":\"" + item + "\"}],\"rolls\":1}";
	}

	static String chance(String item, double chance) {
		return "{\"condition\":{\"type\":\"minecraft:random_chance\",\"chance\":" + chance + "},\"entries\":[{\"type\":\"minecraft:item\",\"name\":\""
				+ item + "\"}],\"rolls\":1}";
	}

	static void entity(String id, String pools) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/entities/" + id + ".json", "{\"type\":\"minecraft:entity\",\"pools\":[" + pools
				+ "],\"random_sequence\":\"mlinnotas:entities/" + id + "\"}");
	}

	static void chest(String id, String entries, int min, int max) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/chests/" + id + ".json", "{\"type\":\"minecraft:chest\",\"pools\":[{\"entries\":" + entries
				+ ",\"rolls\":{\"type\":\"minecraft:uniform\",\"max\":" + max + ",\"min\":" + min + "}}],\"random_sequence\":\"mlinnotas:chests/" + id + "\"}");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
