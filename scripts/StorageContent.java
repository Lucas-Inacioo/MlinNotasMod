import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conteudo da rede de armazenamento para o ContentGen: Unidades de Armazenamento (uma por nivel, do cobre ao
 * celestio), Cabo, Terminal e Terminal de Crafting (blockstates, modelos, itens, loot com o conteudo no item,
 * receitas, tags) e todos os textos. Chamado por ContentGen.main.
 */
class StorageContent {
	//                    tier          pt            en           material da melhoria (crafting_transmute)
	static final String[][] TIERS = {
			{"copper", "Cobre", "Copper", null},
			{"iron", "Ferro", "Iron", "minecraft:iron_block"},
			{"gold", "Ouro", "Gold", "minecraft:gold_block"},
			{"cobalt", "Cobalto", "Cobalt", "mlinnotas:cobalt_block"},
			{"diamond", "Diamante", "Diamond", "minecraft:diamond_block"},
			{"netherite", "Netherite", "Netherite", "minecraft:netherite_ingot"},
			{"infernite", "Infernita", "Infernite", "mlinnotas:infernite_block"},
			{"endite", "Endita", "Endite", "mlinnotas:endite_block"},
			{"astralite", "Astralita", "Astralite", "mlinnotas:astralite_block"},
			{"celestium", "Celéstio", "Celestium", "mlinnotas:celestium_ingot"},
	};

	static void generate(List<String> pickaxeMineable) throws IOException {
		List<String> all = new ArrayList<>();
		String previous = null;
		for (String[] t : TIERS) {
			String id = "storage_unit_" + t[0];
			orientable(id, "mlinnotas:block/" + id + "_front", "mlinnotas:block/" + id + "_side", "mlinnotas:block/" + id + "_top");
			ContentGen.name("block", id, "Unidade de Armazenamento de " + t[1], t[2] + " Storage Unit");
			ContentGen.write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":"
					+ "[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + id + "\",\"modifier\":{\"type\":\"minecraft:copy_components\","
					+ "\"source\":\"block_entity\",\"include\":[\"minecraft:custom_name\",\"mlinnotas:storage_contents\"]}}]}],"
					+ "\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
			if (previous == null) {
				ContentGen.shaped(id, "CRC|RHR|CRC", Map.of("C", "minecraft:copper_ingot", "R", "minecraft:redstone", "H", "minecraft:chest"), "misc");
			} else {
				// melhoria: unidade anterior + material, mantendo o conteudo (componentes copiados pelo transmute)
				ContentGen.write("data/mlinnotas/recipe/" + id + ".json", "{\"type\":\"minecraft:crafting_transmute\",\"category\":\"misc\","
						+ "\"group\":\"storage_unit_upgrade\",\"input\":\"mlinnotas:" + previous + "\",\"material\":\"" + t[3] + "\","
						+ "\"result\":{\"id\":\"mlinnotas:" + id + "\"}}");
			}
			all.add("mlinnotas:" + id);
			previous = id;
		}

		// cabo: nucleo + bracos (multipart pelas propriedades do PipeBlock)
		String cableTextures = "\"textures\":{\"particle\":\"mlinnotas:block/storage_cable\",\"cable\":\"mlinnotas:block/storage_cable\"}";
		String allFaces = "\"faces\":{\"north\":{\"texture\":\"#cable\",\"uv\":[5,5,11,11]},\"south\":{\"texture\":\"#cable\",\"uv\":[5,5,11,11]},"
				+ "\"east\":{\"texture\":\"#cable\",\"uv\":[5,5,11,11]},\"west\":{\"texture\":\"#cable\",\"uv\":[5,5,11,11]},"
				+ "\"up\":{\"texture\":\"#cable\",\"uv\":[5,5,11,11]},\"down\":{\"texture\":\"#cable\",\"uv\":[5,5,11,11]}}";
		String armFaces = "\"faces\":{\"north\":{\"texture\":\"#cable\",\"uv\":[5,5,11,11]},"
				+ "\"east\":{\"texture\":\"#cable\",\"uv\":[0,5,5,11]},\"west\":{\"texture\":\"#cable\",\"uv\":[11,5,16,11]},"
				+ "\"up\":{\"texture\":\"#cable\",\"uv\":[5,0,11,5]},\"down\":{\"texture\":\"#cable\",\"uv\":[5,11,11,16]}}";
		ContentGen.write("assets/mlinnotas/models/block/storage_cable_core.json", "{\"parent\":\"minecraft:block/block\"," + cableTextures
				+ ",\"elements\":[{\"from\":[5,5,5],\"to\":[11,11,11]," + allFaces + "}]}");
		ContentGen.write("assets/mlinnotas/models/block/storage_cable_arm.json", "{\"parent\":\"minecraft:block/block\"," + cableTextures
				+ ",\"elements\":[{\"from\":[5.5,5.5,0],\"to\":[10.5,10.5,5]," + armFaces + "}]}");
		ContentGen.write("assets/mlinnotas/models/block/storage_cable_item.json", "{\"parent\":\"minecraft:block/block\"," + cableTextures
				+ ",\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[0,0,0],\"scale\":[0.9,0.9,0.9]}},"
				+ "\"elements\":[{\"from\":[5,5,5],\"to\":[11,11,11]," + allFaces + "},"
				+ "{\"from\":[5.5,5.5,0],\"to\":[10.5,10.5,5]," + armFaces + "},"
				+ "{\"from\":[5.5,5.5,11],\"to\":[10.5,10.5,16]," + armFaces.replace("\"north\"", "\"south\"") + "}]}");
		String[][] arms = {{"north", ""}, {"east", ",\"y\":90"}, {"south", ",\"y\":180"}, {"west", ",\"y\":270"}, {"up", ",\"x\":270"}, {"down", ",\"x\":90"}};
		StringBuilder multipart = new StringBuilder("{\"apply\":{\"model\":\"mlinnotas:block/storage_cable_core\"}}");
		for (String[] arm : arms) {
			multipart.append(",{\"when\":{\"").append(arm[0]).append("\":\"true\"},\"apply\":{\"model\":\"mlinnotas:block/storage_cable_arm\"")
					.append(arm[1]).append(",\"uvlock\":false}}");
		}
		ContentGen.write("assets/mlinnotas/blockstates/storage_cable.json", "{\"multipart\":[" + multipart + "]}");
		ContentGen.write("assets/mlinnotas/items/storage_cable.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/storage_cable_item\"}}");
		ContentGen.name("block", "storage_cable", "Cabo de Armazenamento", "Storage Cable");
		simpleLoot("storage_cable");
		ContentGen.shaped("storage_cable", "CCC|RRR|CCC", Map.of("C", "minecraft:copper_ingot", "R", "minecraft:redstone"), "misc", 12);
		all.add("mlinnotas:storage_cable");

		// terminais
		orientable("storage_terminal", "mlinnotas:block/storage_terminal_front", "mlinnotas:block/storage_terminal_side", "mlinnotas:block/storage_terminal_top");
		orientable("crafting_terminal", "mlinnotas:block/crafting_terminal_front", "mlinnotas:block/storage_terminal_side", "mlinnotas:block/crafting_terminal_top");
		ContentGen.name("block", "storage_terminal", "Terminal de Armazenamento", "Storage Terminal");
		ContentGen.name("block", "crafting_terminal", "Terminal de Crafting", "Crafting Terminal");
		simpleLoot("storage_terminal");
		simpleLoot("crafting_terminal");
		ContentGen.shaped("storage_terminal", "IGI|RCR|III", Map.of("I", "minecraft:iron_ingot", "G", "minecraft:glass_pane", "R", "minecraft:redstone",
				"C", "mlinnotas:storage_cable"), "misc");
		ContentGen.write("data/mlinnotas/recipe/crafting_terminal.json", "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":"
				+ "[\"mlinnotas:storage_terminal\",\"minecraft:crafting_table\"],\"result\":{\"count\":1,\"id\":\"mlinnotas:crafting_terminal\"}}");
		all.add("mlinnotas:storage_terminal");
		all.add("mlinnotas:crafting_terminal");
		pickaxeMineable.addAll(all);

		// baus que entram na rede quando encostam num cabo/unidade/terminal
		List<String> connectable = new ArrayList<>(List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "#minecraft:shulker_boxes",
				"#minecraft:copper_chests"));
		for (String tier : List.of("stone", "copper", "iron", "gold", "cobalt", "diamond", "netherite", "infernite", "endite", "astralite", "celestium")) {
			connectable.add("mlinnotas:" + tier + "_chest");
		}
		ContentGen.tag("block", "mlinnotas:storage_connectable", connectable, false);

		// --- textos ---
		text("item.mlinnotas.storage_unit.capacity", "%s tipos × %s cada", "%s types × %s each");
		text("item.mlinnotas.storage_unit.contents", "Guarda %s/%s tipos, %s itens", "Holds %s/%s types, %s items");
		text("item.mlinnotas.storage_unit.line", "%s× %s", "%s× %s");
		text("item.mlinnotas.storage_unit.more", "e mais %s tipos...", "and %s more types...");
		text("message.mlinnotas.storage.unit_summary", "%s/%s tipos · %s/%s itens", "%s/%s types · %s/%s items");
		text("message.mlinnotas.storage.unit_full", "Não cabe nesta unidade", "No room in this unit");
		text("gui.mlinnotas.storage.search", "Buscar", "Search");
		text("gui.mlinnotas.storage.search.hint", "Buscar...", "Search...");
		text("gui.mlinnotas.storage.search.tooltip", "Texto no nome ou id; @mod filtra pelo mod. Botão direito limpa.",
				"Text in the name or id; @mod filters by mod. Right-click clears.");
		text("gui.mlinnotas.storage.sort.name", "Nome", "Name");
		text("gui.mlinnotas.storage.sort.count", "Qtd.", "Count");
		text("gui.mlinnotas.storage.sort.mod", "Mod", "Mod");
		text("gui.mlinnotas.storage.sort.tooltip", "Ordenar por nome, quantidade ou mod", "Sort by name, amount or mod");
		text("gui.mlinnotas.storage.order.tooltip", "Inverter a ordem", "Reverse the order");
		text("gui.mlinnotas.storage.status", "%s tipos · %s", "%s types · %s");
		text("gui.mlinnotas.storage.no_network", "Nada ligado à rede", "Nothing connected");
		text("gui.mlinnotas.storage.count", "Quantidade: %s", "Amount: %s");
		text("gui.mlinnotas.storage.hint", "Esq.: pilha · Dir.: metade · Shift: inventário", "Left: stack · Right: half · Shift: inventory");
		text("gui.mlinnotas.storage.truncated", "Rede grande demais: só os primeiros 1024 blocos contam",
				"Network too big: only the first 1024 blocks count");
		text("gui.mlinnotas.storage.crafting", "Crafting", "Crafting");
		text("gui.mlinnotas.storage.clear", "Limpar", "Clear");
		text("gui.mlinnotas.storage.clear.tooltip", "Devolve a grade para a rede (depois de cada craft a grade se repõe sozinha da rede)", "Returns the grid to the network (after each craft the grid refills itself from the network)");
	}

	static void orientable(String id, String front, String side, String top) throws IOException {
		ContentGen.write("assets/mlinnotas/models/block/" + id + ".json", "{\"parent\":\"minecraft:block/orientable\",\"textures\":{\"front\":\""
				+ front + "\",\"side\":\"" + side + "\",\"top\":\"" + top + "\"}}");
		ContentGen.write("assets/mlinnotas/blockstates/" + id + ".json", "{\"variants\":{\"facing=north\":{\"model\":\"mlinnotas:block/" + id + "\"},"
				+ "\"facing=east\":{\"model\":\"mlinnotas:block/" + id + "\",\"y\":90},\"facing=south\":{\"model\":\"mlinnotas:block/" + id + "\",\"y\":180},"
				+ "\"facing=west\":{\"model\":\"mlinnotas:block/" + id + "\",\"y\":270}}}");
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/" + id + "\"}}");
	}

	static void simpleLoot(String id) throws IOException {
		ContentGen.write("data/mlinnotas/loot_table/blocks/" + id + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"condition\":"
				+ "{\"type\":\"minecraft:survives_explosion\"},\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mlinnotas:" + id + "\"}]}],"
				+ "\"random_sequence\":\"mlinnotas:blocks/" + id + "\"}");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
