import java.io.IOException;
import java.util.List;

/**
 * Conteudo do rework do Dragao do Ender para o ContentGen: feature da arena na ilha central (tipo
 * {@code mlinnotas:dragon_arena}, so no bioma {@code minecraft:the_end}), loot do bau do tesouro, tag
 * {@code minecraft:dragon_immune} com os blocos da arena (o dragao atravessa sem quebrar), projetil e textos.
 * Chamado por ContentGen.main.
 */
class DragonContent {
	static void generate(List<String> pickaxeMineable) throws IOException {
		ContentGen.write("data/mlinnotas/worldgen/feature/dragon_arena.json", "{\"type\":\"mlinnotas:dragon_arena\"}");
		ContentGen.write("data/mlinnotas/worldgen/placed_feature/dragon_arena.json",
				"{\"feature\":\"mlinnotas:dragon_arena\",\"placement\":[{\"type\":\"minecraft:biome\"}]}");

		// blocos da arena que o dragao nao destroi ao passar voando (a pedra do End e a obsidiana ja sao do vanilla)
		ContentGen.tag("block", "minecraft:dragon_immune", List.of("minecraft:end_stone_bricks", "minecraft:end_stone_brick_slab",
				"minecraft:end_stone_brick_wall", "minecraft:end_stone_brick_stairs", "minecraft:purpur_block", "minecraft:purpur_pillar",
				"minecraft:purpur_slab", "minecraft:purpur_stairs", "minecraft:end_rod", "minecraft:soul_lantern", "minecraft:chest",
				"mlinnotas:void_bricks", "mlinnotas:purpur_lamp", "mlinnotas:ether_crystal_block", "mlinnotas:ether_crystal_cluster",
				"mlinnotas:end_moss"), false);

		// tesouro do dragao: aparece no estrado norte do santuario quando o dragao morre. Completo (com o unico Totem
		// garantido do mundo) so na primeira morte; reinvocado, o bau pequeno (sem astralita: ela vem das ilhas externas)
		String common = "[" + RuinsContent.item("minecraft:diamond", 10, 2, 5) + "," + RuinsContent.item("mlinnotas:raw_endite", 10, 2, 5) + ","
				+ RuinsContent.item("minecraft:ender_pearl", 10, 3, 8) + "," + RuinsContent.item("minecraft:experience_bottle", 10, 6, 12) + ","
				+ RuinsContent.item("minecraft:golden_apple", 6, 1, 3) + "," + RuinsContent.item("minecraft:shulker_shell", 6, 2, 4) + ","
				+ RuinsContent.item("minecraft:chorus_fruit", 6, 6, 16) + ","
				+ RuinsContent.item("minecraft:netherite_scrap", 1, 1, 2) + "," + RuinsContent.item("minecraft:enchanted_golden_apple", 1, 1, 1) + "]";
		String rare = "[" + RuinsContent.item("minecraft:elytra", 2, 1, 1) + "," + RuinsContent.item("minecraft:dragon_head", 2, 1, 1) + ","
				+ RuinsContent.item("mlinnotas:dragon_scale", 4, 1, 2) + "]";
		String small = "[" + RuinsContent.item("minecraft:ender_pearl", 10, 2, 6) + "," + RuinsContent.item("minecraft:experience_bottle", 10, 4, 8) + ","
				+ RuinsContent.item("mlinnotas:raw_endite", 6, 1, 3) + "," + RuinsContent.item("minecraft:shulker_shell", 4, 1, 2) + ","
				+ RuinsContent.item("minecraft:golden_apple", 4, 1, 1) + "," + RuinsContent.item("minecraft:chorus_fruit", 6, 4, 10) + "]";
		ContentGen.write("data/mlinnotas/loot_table/chests/dragon_hoard_small.json", "{\"type\":\"minecraft:chest\",\"pools\":["
				+ "{\"entries\":" + small + ",\"rolls\":{\"type\":\"minecraft:uniform\",\"max\":4,\"min\":2}}],"
				+ "\"random_sequence\":\"mlinnotas:chests/dragon_hoard_small\"}");
		ContentGen.write("data/mlinnotas/loot_table/chests/dragon_hoard.json", "{\"type\":\"minecraft:chest\",\"pools\":["
				+ "{\"entries\":" + common + ",\"rolls\":{\"type\":\"minecraft:uniform\",\"max\":9,\"min\":6}},"
				+ "{\"entries\":" + rare + ",\"rolls\":1},"
				+ "{\"entries\":[" + RuinsContent.item("minecraft:totem_of_undying", 1, 1, 1) + "],\"rolls\":1},"
				+ "{\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:enchanted_book\",\"functions\":[{\"function\":"
				+ "\"minecraft:enchant_with_levels\",\"levels\":{\"type\":\"minecraft:uniform\",\"min\":30,\"max\":40},\"options\":\"#minecraft:on_random_loot\"}]}],"
				+ "\"rolls\":2}],\"random_sequence\":\"mlinnotas:chests/dragon_hoard\"}");

		ContentGen.name("entity", "void_meteor", "Meteoro do Vazio", "Void Meteor");
		text("boss.mlinnotas.dragon.phase", "%s · Fase %s", "%s · Phase %s");
		text("message.mlinnotas.dragon.health", "O Dragão do Ender sente %s desafiante(s): %s de vida",
				"The Ender Dragon senses %s challenger(s): %s health");
		text("message.mlinnotas.dragon.phase_two", "O Dragão do Ender ruge e os cristais renascem nos pilares!",
				"The Ender Dragon roars and crystals regrow on the pillars!");
		text("message.mlinnotas.dragon.phase_three", "O Dragão do Ender entra em fúria!", "The Ender Dragon flies into a rage!");
		text("message.mlinnotas.dragon.dive", "O Dragão do Ender mergulha! Pule a onda de choque!", "The Ender Dragon dives! Jump over the shockwave!");
		text("message.mlinnotas.dragon.hoard_small", "Um pequeno tesouro apareceu no estrado norte do santuário",
				"A small hoard appeared on the north dais of the sanctuary");
		text("message.mlinnotas.dragon.no_explosions", "A magia do dragão abafa a explosão: nada de camas ou âncoras perto da arena",
				"The dragon's magic smothers the blast: no beds or anchors near the arena");
		text("message.mlinnotas.dragon.hoard", "O tesouro do dragão apareceu no estrado norte do santuário",
				"The dragon's hoard appeared on the north dais of the sanctuary");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
