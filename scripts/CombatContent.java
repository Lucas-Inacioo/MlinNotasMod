import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * "Combate melhor" para o ContentGen: as 5 classes de arma (montante, adaga, martelo de guerra, glaive, rapieira)
 * em todos os 12 niveis e as 5 armas de chefe — modelos (com poses de mao por classe), receitas, nomes, descricoes,
 * tags de encantamento e as quests "Arsenal". Chamado por ContentGen.main; as quests entram pelo gancho
 * {@link #arsenal} em QuestContent.chapter.
 *
 * <p>Lista de classes igual a com.mlin.notas.combat.WeaponClass (CombatResourcesTest confere). Receitas: madeira a
 * diamante e os niveis novos usam o lingote/material do nivel + gravetos (formato das ferramentas do nivel);
 * netherite e upgrade na mesa de ferraria a partir da arma de diamante, como o vanilla.</p>
 */
class CombatContent {
	/** sufixo, nome pt (prefixo), nome en, padrao de receita, descricao pt, descricao en. */
	static final String[][] CLASSES = {
			{"greatsword", "Montante", "Greatsword", " X | X |X#X",
					"Montante: lento e pesado, golpe em arco largo que acerta todos à frente",
					"Greatsword: slow and heavy, a wide arc that hits everyone in front"},
			{"dagger", "Adaga", "Dagger", "X|#",
					"Adaga: muito rápida e curta; +40% pelas costas",
					"Dagger: very fast and short; +40% from behind"},
			{"warhammer", "Martelo de Guerra", "Warhammer", "XXX|X#X| # ",
					"Martelo de guerra: ignora 35% da armadura, golpe carregado solta onda de choque e desarma escudos",
					"Warhammer: ignores 35% of armor, charged hits release a shockwave and disable shields"},
			{"glaive", "Glaive", "Glaive", " XX| #X|#  ",
					"Glaive: alcance longo (+1,5) e golpe em arco",
					"Glaive: long reach (+1.5) and an arc swing"},
			{"rapier", "Rapieira", "Rapier", "  X| X |#  ",
					"Rapieira: estocada rápida com alcance extra; o golpe final atravessa o alvo",
					"Rapier: quick thrust with extra reach; the finisher pierces through the target"},
	};

	/** id do nivel, prefixo do item, nome pt, nome en, material (igual a lista TIERS do ContentGen). */
	static final String[][] TIERS = {
			{"wood", "wooden", "Madeira", "Wooden", "#minecraft:planks"},
			{"stone", "stone", "Pedra", "Stone", "#minecraft:stone_tool_materials"},
			{"copper", "copper", "Cobre", "Copper", "minecraft:copper_ingot"},
			{"iron", "iron", "Ferro", "Iron", "minecraft:iron_ingot"},
			{"gold", "golden", "Ouro", "Golden", "minecraft:gold_ingot"},
			{"cobalt", "cobalt", "Cobalto", "Cobalt", "mlinnotas:cobalt_ingot"},
			{"diamond", "diamond", "Diamante", "Diamond", "minecraft:diamond"},
			{"netherite", "netherite", "Netherite", "Netherite", "minecraft:netherite_ingot"},
			{"infernite", "infernite", "Infernita", "Infernite", "mlinnotas:infernite_ingot"},
			{"endite", "endite", "Endita", "Endite", "mlinnotas:endite_ingot"},
			{"astralite", "astralite", "Astralita", "Astralite", "mlinnotas:astralite_ingot"},
			{"celestium", "celestium", "Celéstio", "Celestium", "mlinnotas:celestium_ingot"},
	};

	/** Poses na mao por classe: escala e deslocamento em terceira e primeira pessoa (base = item/handheld). */
	static final Map<String, double[]> POSES = Map.of(
			// {escala 3a, y 3a, z 3a, escala 1a, y 1a}
			"greatsword", new double[] {1.25, 6.0, 0.5, 0.74, 3.4},
			"dagger", new double[] {0.6, 2.0, 0.5, 0.55, 2.6},
			"warhammer", new double[] {1.05, 4.5, 1.0, 0.7, 3.2},
			"glaive", new double[] {1.5, 7.5, 0.5, 0.74, 3.4},
			"rapier", new double[] {1.0, 4.5, 0.5, 0.68, 3.2});

	/** id, classe, nome pt, nome en, descricao pt, descricao en, padrao, chaves (letra=item, separadas por virgula). */
	static final String[][] BOSS_WEAPONS = {
			{"golem_hammer", "warhammer", "Martelo do Golem", "Golem Hammer",
					"Terremoto: golpe carregado abre uma onda de choque maior que levanta e desacelera (a cada 2 s)",
					"Quake: charged hits open a larger shockwave that lifts and slows (every 2 s)",
					"BHB| # | # ", "B=minecraft:iron_block,H=mlinnotas:cobalt_heart,#=minecraft:stick"},
			{"abyssal_blade", "rapier", "Lâmina Abissal", "Abyssal Blade",
					"Prisão de sculk: prende o alvo e enfraquece quem está perto (a cada 3 s)",
					"Sculk bind: roots the target and weakens those nearby (every 3 s)",
					" DE|DCD|#D ", "D=minecraft:diamond,E=minecraft:echo_shard,C=mlinnotas:abyssal_core,#=minecraft:stick"},
			{"igneous_greatsword", "greatsword", "Montante Ígneo", "Igneous Greatsword",
					"Incendeia a cada golpe; o golpe final solta uma onda de fogo em anel",
					"Sets targets ablaze; the finisher releases a ring of fire",
					" N | E |B#B", "N=minecraft:netherite_ingot,E=mlinnotas:colossus_ember,B=minecraft:blaze_rod,#=minecraft:stick"},
			{"dragon_fangs", "dagger", "Presas do Dragão", "Dragon Fangs",
					"Golpe final ou pelas costas explode em sopro do vazio em volta do alvo",
					"Finishers and backstabs burst into void breath around the target",
					"S|I|#", "S=mlinnotas:dragon_scale,I=mlinnotas:infernite_ingot,#=minecraft:stick"},
			{"astral_lance", "glaive", "Lança Astral", "Astral Lance",
					"Golpe carregado dispara um raio estelar no inimigo mais próximo",
					"Charged hits fire a star bolt at the nearest enemy",
					" EF| #E|#  ", "E=mlinnotas:endite_ingot,F=mlinnotas:astral_fragment,#=minecraft:stick"},
	};

	static void generate() throws IOException {
		List<String> weapons = new ArrayList<>();
		for (String[] c : CLASSES) {
			pose(c[0]);
			ContentGen.PT.put("weapon.mlinnotas." + c[0] + ".desc", c[4]);
			ContentGen.EN.put("weapon.mlinnotas." + c[0] + ".desc", c[5]);
			String diamond = null;
			for (String[] t : TIERS) {
				String id = t[1] + "_" + c[0];
				item(id, c[0]);
				ContentGen.name("item", id, c[1] + " de " + t[2], t[3] + " " + c[2]);
				if (t[0].equals("netherite")) {
					ContentGen.write("data/mlinnotas/recipe/" + id + ".json", "{\"type\":\"minecraft:smithing_transform\",\"addition\":"
							+ "\"#minecraft:netherite_tool_materials\",\"base\":\"mlinnotas:" + diamond + "\",\"result\":{\"id\":\"mlinnotas:" + id
							+ "\"},\"template\":\"minecraft:netherite_upgrade_smithing_template\"}");
				} else {
					ContentGen.shaped(id, c[3], Map.of("X", t[4], "#", "minecraft:stick"), "equipment");
				}
				if (t[0].equals("diamond")) {
					diamond = id;
				}
				weapons.add("mlinnotas:" + id);
			}
		}
		for (String[] b : BOSS_WEAPONS) {
			item(b[0], b[1]);
			ContentGen.name("item", b[0], b[2], b[3]);
			ContentGen.PT.put("item.mlinnotas." + b[0] + ".desc", b[4]);
			ContentGen.EN.put("item.mlinnotas." + b[0] + ".desc", b[5]);
			Map<String, String> key = new java.util.LinkedHashMap<>();
			for (String entry : b[7].split(",")) {
				key.put(entry.substring(0, 1), entry.substring(2));
			}
			ContentGen.shaped(b[0], b[6], key, "equipment");
			weapons.add("mlinnotas:" + b[0]);
		}
		// encantamentos de arma (afiacao, aspecto flamejante, repulsao, saque...) e durabilidade; sem "varredura"
		ContentGen.tag("item", "minecraft:enchantable/melee_weapon", weapons, false);
		ContentGen.tag("item", "minecraft:enchantable/durability", weapons, false);
		ContentGen.tag("item", "mlinnotas:weapons", weapons, false);
		List<String> bossWeapons = Arrays.stream(BOSS_WEAPONS).map(b -> "mlinnotas:" + b[0]).toList();
		ContentGen.tag("item", "mlinnotas:boss_weapons", bossWeapons, false);

		text("weapon.mlinnotas.combo", "Combo: o 3º golpe seguido faz +%s%%", "Combo: the 3rd hit in a row deals +%s%%");
		text("key.mlinnotas.dodge", "Esquivar", "Dodge");
	}

	static void pose(String kind) throws IOException {
		double[] p = POSES.get(kind);
		String third = "\"rotation\":[0,%s,%s],\"translation\":[0,%s,%s],\"scale\":[%s,%s,%s]";
		String first = "\"rotation\":[0,%s,%s],\"translation\":[1.13,%s,1.13],\"scale\":[%s,%s,%s]";
		ContentGen.write("assets/mlinnotas/models/item/weapon_" + kind + ".json", "{\"parent\":\"minecraft:item/generated\",\"display\":{"
				+ "\"thirdperson_righthand\":{" + String.format(java.util.Locale.ROOT, third, -90, 55, p[1], p[2], p[0], p[0], p[0]) + "},"
				+ "\"thirdperson_lefthand\":{" + String.format(java.util.Locale.ROOT, third, 90, -55, p[1], p[2], p[0], p[0], p[0]) + "},"
				+ "\"firstperson_righthand\":{" + String.format(java.util.Locale.ROOT, first, -90, 25, p[4], p[3], p[3], p[3]) + "},"
				+ "\"firstperson_lefthand\":{" + String.format(java.util.Locale.ROOT, first, 90, -25, p[4], p[3], p[3], p[3]) + "}}}");
	}

	static void item(String id, String kind) throws IOException {
		ContentGen.write("assets/mlinnotas/items/" + id + ".json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:item/" + id + "\"}}");
		ContentGen.write("assets/mlinnotas/models/item/" + id + ".json", "{\"parent\":\"mlinnotas:item/weapon_" + kind
				+ "\",\"textures\":{\"layer0\":\"mlinnotas:item/" + id + "\"}}");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}

	/** Gancho do QuestContent.chapter: soma as quests do arsenal aos capitulos Primeiros Passos e Chefes. */
	static QuestContent.Q[] arsenal(String chapter, QuestContent.Q[] quests) {
		List<QuestContent.Q> extra = new ArrayList<>();
		if (chapter.equals("basics")) {
			extra.add(QuestContent.q("arsenal", "mlinnotas:iron_greatsword", 3, 1, "Arsenal", "Arsenal",
					"Além de espada e machado há cinco classes de arma em todos os níveis: montante (arco largo), adaga (rápida, +40% pelas costas), martelo de guerra (fura armadura, onda de choque), glaive (alcance longo, arco) e rapieira (estocada que atravessa). O 3º golpe seguido com carga cheia é um golpe final mais forte. Faça qualquer uma.",
					"Besides swords and axes there are five weapon classes in every tier: greatsword (wide arc), dagger (fast, +40% from behind), warhammer (pierces armor, shockwave), glaive (long reach, arc) and rapier (thrust that pierces). The 3rd fully charged hit in a row is a stronger finisher. Craft any of them.")
					.dep("stone_tools").haveTag("mlinnotas:weapons", 1, "mlinnotas:iron_greatsword", "any_weapon", "Tenha uma arma nova", "Have a new weapon")
					.item("minecraft:iron_ingot", 4).xp(30));
			extra.add(QuestContent.q("dodge", "minecraft:feather", 4, 1, "Esquiva", "Dodge",
					"Alt esquerdo dá um impulso curto na direção em que você anda (para trás se parado) e 6 ticks sem tomar dano. Recarga de 1,5 s e gasta um pouco de fome. Ótimo contra os ataques avisados dos chefes. Marque quando testar.",
					"Left Alt dashes a short way in the direction you are moving (backwards if standing) with 6 ticks of invulnerability. 1.5 s cooldown and costs a little hunger. Great against the bosses' telegraphed attacks. Check when you've tried it.")
					.dep("arsenal").check().item("minecraft:cooked_beef", 4).xp(20));
		} else if (chapter.equals("bosses")) {
			String[][] trophies = {
					{"golem_hammer", "cobalt_golem", "0", "Martelo do Golem", "Golem Hammer", "Coração de Cobalto + 2 blocos de ferro: martelo de guerra com terremoto.",
							"Cobalt Heart + 2 iron blocks: a warhammer that quakes the ground."},
					{"abyssal_blade", "abyssal_guardian", "1", "Lâmina Abissal", "Abyssal Blade", "Núcleo Abissal, diamantes e um fragmento de eco: rapieira que prende em sculk.",
							"Abyssal Core, diamonds and an echo shard: a rapier that binds in sculk."},
					{"igneous_greatsword", "igneous_colossus", "2", "Montante Ígneo", "Igneous Greatsword", "Brasa do Colosso, netherite e varas de blaze: montante em chamas.",
							"Colossus Ember, netherite and blaze rods: a burning greatsword."},
					{"dragon_fangs", "ender_dragon", "3", "Presas do Dragão", "Dragon Fangs", "Uma Escama de Dragão sobre um lingote de infernita: adaga do vazio.",
							"A Dragon Scale over an infernite ingot: a void dagger."},
					{"astral_lance", "astral_herald", "4", "Lança Astral", "Astral Lance", "Fragmento Astral e endita: glaive que dispara raios estelares.",
							"Astral Fragment and endite: a glaive that fires star bolts."},
			};
			List<String> ids = new ArrayList<>();
			for (String[] t : trophies) {
				extra.add(QuestContent.q(t[0], "mlinnotas:" + t[0], 3, Double.parseDouble(t[2]), t[3], t[4], t[5], t[6])
						.dep(t[1]).have("mlinnotas:" + t[0], 1).xp(100));
				ids.add(t[0]);
			}
			extra.add(QuestContent.q("legendary_arsenal", "mlinnotas:astral_lance", 4, 4.5, "Arsenal Lendário", "Legendary Arsenal",
					"As cinco armas dos chefes. Esta quest completa sozinha.", "All five boss weapons. This quest completes by itself.")
					.dep(ids.toArray(String[]::new)).item("minecraft:golden_apple", 2).xp(300));
		}
		if (extra.isEmpty()) {
			return quests;
		}
		List<QuestContent.Q> all = new ArrayList<>(Arrays.asList(quests));
		all.addAll(extra);
		return all.toArray(QuestContent.Q[]::new);
	}
}
