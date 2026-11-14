import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lapides para o ContentGen: estado/modelo do bloco {@code gravestone} (base de tijolo musgoso, degrau, pedra com
 * arco, papoula na frente), item, textos, tags de imunidade (wither/dragao), a conquista escondida
 * {@code mlinnotas:recovered_grave} e a quest "Descanse em Paz" em Primeiros Passos (via {@code QuestContent.EXTRA},
 * por isso roda antes do QuestContent). Texturas em {@code scripts/GraveTexGen.java}. Chamado por ContentGen.main.
 */
class GraveContent {
	static void generate() throws IOException {
		// --- bloco e item ---
		StringBuilder variants = new StringBuilder();
		String[][] facings = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};
		for (String water : new String[] {"false", "true"}) {
			for (String[] f : facings) {
				if (variants.length() > 0) {
					variants.append(',');
				}
				variants.append("\"facing=").append(f[0]).append(",waterlogged=").append(water).append("\":{\"model\":\"mlinnotas:block/gravestone\"")
						.append(f[1].equals("0") ? "" : ",\"y\":" + f[1]).append('}');
			}
		}
		ContentGen.write("assets/mlinnotas/blockstates/gravestone.json", "{\"variants\":{" + variants + "}}");
		ContentGen.write("assets/mlinnotas/models/block/gravestone.json", model());
		ContentGen.write("assets/mlinnotas/items/gravestone.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"mlinnotas:block/gravestone\"}}");

		// wither e dragao nao quebram a lapide (explosao ja nao quebra pela resistencia)
		ContentGen.tag("block", "minecraft:wither_immune", List.of("mlinnotas:gravestone"), false);
		ContentGen.tag("block", "minecraft:dragon_immune", List.of("mlinnotas:gravestone"), false);

		// conquista escondida (sem display: sem aviso nem aba); dada pelo GraveServer ao recuperar a propria lapide
		ContentGen.write("data/mlinnotas/advancement/recovered_grave.json", "{\"criteria\":{\"recovered\":{\"trigger\":\"minecraft:impossible\"}}}");

		// --- textos ---
		ContentGen.name("block", "gravestone", "Lápide", "Gravestone");
		text("block.mlinnotas.gravestone.of", "Lápide de %s", "%s's Gravestone");
		text("block.mlinnotas.gravestone.day", "Dia %s", "Day %s");
		text("container.mlinnotas.gravestone", "Lápide de %s", "%s's Gravestone");
		text("message.mlinnotas.grave.placed", "☠ Seus itens estão numa lápide em %s (%s): %s pilhas e %s de XP. O minimapa marca o lugar.",
				"☠ Your items are in a gravestone at %s (%s): %s stacks and %s XP. The minimap marks the spot.");
		text("message.mlinnotas.grave.placed_other", "☠ A lápide de %s está em %s (%s)", "☠ %s's gravestone is at %s (%s)");
		text("message.mlinnotas.grave.restored", "Você recuperou suas coisas", "You got your things back");
		text("message.mlinnotas.grave.dim.overworld", "Superfície", "Overworld");
		text("message.mlinnotas.grave.dim.nether", "Nether", "Nether");
		text("message.mlinnotas.grave.dim.end", "End", "End");
		text("hud.mlinnotas.waila.grave.summary", "%s pilhas · %s de XP", "%s stacks · %s XP");
		text("hud.mlinnotas.waila.grave.owner_hint", "Agache + botão direito: tudo de volta no lugar", "Sneak + right-click: everything back in place");
		text("gui.mlinnotas.grave.map_tooltip", "Lápide de %s (%s, %s, %s) · %s pilhas, %s XP", "%s's gravestone (%s, %s, %s) · %s stacks, %s XP");
		text("block.mlinnotas.gravestone.tooltip", "Decoração. Lápides de verdade aparecem quando alguém morre.",
				"Decoration. Real gravestones appear when someone dies.");
		text("quest.mlinnotas.task.recover_grave", "Recupere uma lápide sua", "Recover one of your gravestones");

		// --- quest ---
		QuestContent.EXTRA.computeIfAbsent("basics", k -> new ArrayList<>()).add(
				QuestContent.q("rest_in_peace", "mlinnotas:gravestone", 3, 4, "Descanse em Paz", "Rest in Peace",
						"Ao morrer, tudo o que você levava (inventário, armadura, acessórios e metade do XP) fica numa lápide no lugar seguro mais perto. O chat diz onde e o minimapa marca. Qualquer um pode abrir; o dono, agachado e com botão direito, recebe tudo de volta nos mesmos lugares.",
						"When you die, everything you carried (inventory, armor, accessories and half your XP) stays in a gravestone at the nearest safe spot. Chat tells you where and the minimap marks it. Anyone can open it; the owner, sneaking and right-clicking, gets everything back in the same slots.")
						.dep("minimap")
						.advancement("mlinnotas:recovered_grave", "mlinnotas:gravestone", "recover_grave", "Recupere uma lápide sua", "Recover one of your gravestones")
						.item("minecraft:golden_apple", 1).xp(30));
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}

	/** Modelo com a frente para o norte (rotacionado pelo blockstate). */
	static String model() {
		List<String> elements = new ArrayList<>();
		// base de tijolo musgoso
		elements.add(box(1, 0, 1, 15, 2, 15, "#base", "#base_top", "#base"));
		// degrau
		elements.add(box(2, 2, 4, 14, 3, 12, "#base", "#base_top", "#base"));
		// corpo da pedra: frente com a cruz/moldura, laterais com musgo no alto
		elements.add(stone(3, 3, 13, 13));
		// arco em degraus
		elements.add(stone(4, 13, 12, 14));
		elements.add(stone(5, 14, 11, 15));
		elements.add(stone(6, 15, 10, 16));
		// papoula na frente, a esquerda (dois planos em X)
		elements.add(flower(4.0, 2.6, 45));
		elements.add(flower(4.0, 2.6, -45));
		return "{\"parent\":\"minecraft:block/block\",\"ambientocclusion\":true,\"textures\":{\"particle\":\"mlinnotas:block/gravestone_side\","
				+ "\"front\":\"mlinnotas:block/gravestone_front\",\"side\":\"mlinnotas:block/gravestone_side\",\"base\":\"minecraft:block/mossy_stone_bricks\","
				+ "\"base_top\":\"minecraft:block/mossy_cobblestone\",\"flower\":\"minecraft:block/poppy\"},"
				+ "\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[0,0.5,0],\"scale\":[0.75,0.75,0.75]},"
				+ "\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,3,0],\"scale\":[0.4,0.4,0.4]},"
				+ "\"fixed\":{\"rotation\":[0,180,0],\"translation\":[0,0,0],\"scale\":[0.6,0.6,0.6]},"
				+ "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.375,0.375,0.375]},"
				+ "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"translation\":[0,0,0],\"scale\":[0.4,0.4,0.4]}},"
				+ "\"elements\":[" + String.join(",", elements) + "]}";
	}

	static String box(double x1, double y1, double z1, double x2, double y2, double z2, String side, String top, String bottom) {
		return "{\"from\":[" + n(x1) + "," + n(y1) + "," + n(z1) + "],\"to\":[" + n(x2) + "," + n(y2) + "," + n(z2) + "],\"faces\":{"
				+ face("north", side, x1, 16 - y2, x2, 16 - y1) + "," + face("south", side, x1, 16 - y2, x2, 16 - y1) + ","
				+ face("east", side, z1, 16 - y2, z2, 16 - y1) + "," + face("west", side, z1, 16 - y2, z2, 16 - y1) + ","
				+ face("up", top, x1, z1, x2, z2) + "," + face("down", bottom, x1, z1, x2, z2) + "}}";
	}

	/** Bloco da pedra (z 6..10): frente com a textura entalhada nas mesmas coordenadas, resto com a lateral. */
	static String stone(double x1, double y1, double x2, double y2) {
		double z1 = 6;
		double z2 = 10;
		return "{\"from\":[" + n(x1) + "," + n(y1) + "," + n(z1) + "],\"to\":[" + n(x2) + "," + n(y2) + "," + n(z2) + "],\"faces\":{"
				+ face("north", "#front", 16 - x2, 16 - y2, 16 - x1, 16 - y1) + "," + face("south", "#side", x1, 16 - y2, x2, 16 - y1) + ","
				+ face("east", "#side", z1, 16 - y2, z2, 16 - y1) + "," + face("west", "#side", z1, 16 - y2, z2, 16 - y1) + ","
				+ face("up", "#side", x1, z1, x2, z2) + "," + face("down", "#side", x1, z1, x2, z2) + "}}";
	}

	/** Plano da flor, 4 px de largura e 6 de altura, girado em Y em volta do proprio centro. */
	static String flower(double cx, double cz, int angle) {
		return "{\"from\":[" + n(cx - 2) + ",2," + n(cz) + "],\"to\":[" + n(cx + 2) + ",8," + n(cz) + "],\"shade\":false,"
				+ "\"rotation\":{\"origin\":[" + n(cx) + ",2," + n(cz) + "],\"axis\":\"y\",\"angle\":" + angle + "},"
				+ "\"faces\":{\"north\":{\"uv\":[4,4,12,16],\"texture\":\"#flower\"},\"south\":{\"uv\":[4,4,12,16],\"texture\":\"#flower\"}}}";
	}

	static String face(String dir, String texture, double u1, double v1, double u2, double v2) {
		return "\"" + dir + "\":{\"uv\":[" + n(u1) + "," + n(v1) + "," + n(u2) + "," + n(v2) + "],\"texture\":\"" + texture + "\""
				+ (dir.equals("down") ? ",\"cullface\":\"down\"" : "") + "}";
	}

	static String n(double v) {
		return v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v);
	}
}
