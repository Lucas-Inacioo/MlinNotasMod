import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conteudo do lote de qualidade de vida 2 para o ContentGen: carne podre -> couro na fornalha e no defumador, e as
 * receitas de reciclagem de equipamento ({@code mlinnotas:salvage_smelting}/{@code salvage_blasting}, regras em
 * {@code salvage/SalvageRules}). As receitas vanilla de pepita por fundicao ({@code iron/gold/copper_nugget_from_*})
 * sao trocadas pelo tipo de reciclagem com o mesmo id. Chamado por ContentGen.main.
 */
class Qol2Content {
	static final String[] TOOLS = {"sword", "pickaxe", "axe", "shovel", "hoe", "spear"};
	static final String[] ARMOR = {"helmet", "chestplate", "leggings", "boots"};
	static final String[] WEAPONS = {"greatsword", "dagger", "warhammer", "glaive", "rapier"};

	//                 prefixo      saida                          xp     vanilla? so alto-forno?  id vanilla da receita (null = do mod)
	static final Object[][] MATERIALS = {
			{"copper", "minecraft:copper_nugget", 0.1, true, false, "copper_nugget"},
			{"iron", "minecraft:iron_nugget", 0.1, true, false, "iron_nugget"},
			{"golden", "minecraft:gold_nugget", 0.1, true, false, "gold_nugget"},
			{"cobalt", "mlinnotas:cobalt_ingot", 0.2, false, false, null},
			{"diamond", "minecraft:diamond", 0.3, true, true, null},
			{"netherite", "minecraft:netherite_scrap", 0.5, true, true, null},
			{"infernite", "mlinnotas:infernite_ingot", 0.5, false, true, null},
			{"endite", "mlinnotas:endite_ingot", 0.5, false, true, null},
			{"astralite", "mlinnotas:astralite_ingot", 0.5, false, true, null},
			{"celestium", "mlinnotas:celestium_ingot", 0.5, false, true, null},
	};

	static void generate() throws IOException {
		// guardar rapido (sorting/QuickStackServer, client/QuickStackClient)
		lang("key.mlinnotas.quick_stack", "Guardar rápido no baú", "Quick stack to chest");
		lang("button.mlinnotas.quick_stack", "⇩", "⇩");
		lang("button.mlinnotas.quick_stack.tooltip",
				"Guardar rápido: leva do seu inventário (sem a barra de atalhos) tudo o que já existe neste baú (tecla %s)",
				"Quick stack: moves everything from your inventory (not the hotbar) that this chest already has (key %s)");

		// carne podre -> couro: devagar como qualquer fundicao (o defumador nao acelera)
		cooking("leather_from_smelting_rotten_flesh", "minecraft:smelting", "minecraft:rotten_flesh", "minecraft:leather", 200, 0.1, "misc");
		cooking("leather_from_smoking_rotten_flesh", "minecraft:smoking", "minecraft:rotten_flesh", "minecraft:leather", 200, 0.1, "food");

		for (Object[] m : MATERIALS) {
			String prefix = (String) m[0];
			String output = (String) m[1];
			double xp = (double) m[2];
			boolean vanilla = (boolean) m[3];
			boolean blastOnly = (boolean) m[4];
			String vanillaId = (String) m[5];
			List<String> items = items(prefix, vanilla);
			for (String furnace : blastOnly ? List.of("blasting") : List.of("smelting", "blasting")) {
				String file = vanillaId != null
						? "data/minecraft/recipe/" + vanillaId + "_from_" + furnace + ".json"
						: "data/mlinnotas/recipe/salvage_" + prefix + "_from_" + furnace + ".json";
				ContentGen.write(file, "{\"type\":\"mlinnotas:salvage_" + furnace + "\",\"category\":\"misc\",\"cookingtime\":"
						+ (furnace.equals("smelting") ? 200 : 100) + ",\"experience\":" + String.format(Locale.ROOT, "%.1f", xp)
						+ ",\"ingredient\":[" + ContentGen.quoteAll(items) + "],\"result\":{\"id\":\"" + output + "\"}}");
			}
		}
	}

	/** Pecas reciclaveis de um material (mesma lista que {@code SalvageRules.itemIds}; o game test confere). */
	static List<String> items(String prefix, boolean vanilla) {
		List<String> out = new ArrayList<>();
		String toolNs = vanilla ? "minecraft:" : "mlinnotas:";
		for (String tool : TOOLS) {
			out.add(toolNs + prefix + "_" + tool);
		}
		for (String piece : ARMOR) {
			out.add(toolNs + prefix + "_" + piece);
		}
		if (vanilla && !prefix.equals("cobalt")) {
			out.add("minecraft:" + prefix + "_horse_armor");
			out.add("minecraft:" + prefix + "_nautilus_armor");
		}
		for (String weapon : WEAPONS) {
			out.add("mlinnotas:" + prefix + "_" + weapon);
		}
		if (prefix.equals("iron")) {
			for (String piece : ARMOR) {
				out.add("minecraft:chainmail_" + piece);
			}
		}
		return out;
	}

	private static void lang(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}

	private static void cooking(String id, String type, String input, String result, int time, double xp, String category) throws IOException {
		ContentGen.write("data/mlinnotas/recipe/" + id + ".json", "{\"type\":\"" + type + "\",\"category\":\"" + category + "\",\"cookingtime\":"
				+ time + ",\"experience\":" + String.format(Locale.ROOT, "%.1f", xp) + ",\"ingredient\":\"" + input + "\",\"result\":{\"id\":\""
				+ result + "\"}}");
	}
}
