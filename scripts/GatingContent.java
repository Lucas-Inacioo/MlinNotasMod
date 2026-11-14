import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conteudo da trava de ferramentas e do Tablet de Rede para o ContentGen:
 * <ul>
 *   <li>tags de item {@code mlinnotas:tool_tier/<nivel>} (ferramentas vanilla e do mod, as 5 classes de arma do combate,
 *   armas de chefe no nivel dos numeros delas e varinhas; mesma lista de {@code com.mlin.notas.gating.ToolGate.itemIds},
 *   conferida pelo {@code ToolGateResourcesTest});</li>
 *   <li>item {@code network_tablet} (modelo, receita, textos) e a quest no capitulo Tecnologia;</li>
 *   <li>textos da trava (aviso, dica, arvore de habilidades).</li>
 * </ul>
 * Pendura a quest via {@code QuestContent.EXTRA}, por isso roda antes do QuestContent.
 */
class GatingContent {
	static final String[] GEAR = {"sword", "shovel", "pickaxe", "axe", "hoe", "spear"};
	static final String[] WEAPON_CLASSES = {"greatsword", "dagger", "warhammer", "glaive", "rapier"};
	/** Arma de chefe -> nivel em que os numeros dela se baseiam (BossWeapon.powerTier). */
	static final String[][] BOSS_WEAPONS = {
			{"golem_hammer", "cobalt"}, {"abyssal_blade", "netherite"}, {"igneous_greatsword", "infernite"},
			{"dragon_fangs", "endite"}, {"astral_lance", "astralite"}};

	static void generate() throws IOException {
		for (ContentGen.T t : ContentGen.TIERS) {
			String prefix = t.id().equals("wood") ? "wooden" : t.id().equals("gold") ? "golden" : t.id();
			String namespace = t.vanilla() ? "minecraft:" : "mlinnotas:";
			List<String> ids = new ArrayList<>();
			for (String gear : GEAR) {
				ids.add(namespace + prefix + "_" + gear);
			}
			for (String weaponClass : WEAPON_CLASSES) {
				ids.add("mlinnotas:" + prefix + "_" + weaponClass);
			}
			for (String[] weapon : BOSS_WEAPONS) {
				if (weapon[1].equals(t.id())) {
					ids.add("mlinnotas:" + weapon[0]);
				}
			}
			ids.add("mlinnotas:" + (t.id().equals("wood") ? "wooden_wand" : t.id() + "_wand"));
			ContentGen.tag("item", "mlinnotas:tool_tier/" + t.id(), ids, false);
		}

		QuestContent.text("message.mlinnotas.tool_locked", "Requer Mineração: %s", "Requires Mining: %s");
		QuestContent.text("tooltip.mlinnotas.tool_tier", "Requer Mineração: %s", "Requires Mining: %s");
		QuestContent.text("skill.mlinnotas.unlock.tools", "Usar ferramentas e armas de %s", "Use %s tools and weapons");

		// --- Tablet de Rede ---
		ContentGen.simpleItem("network_tablet", "item/generated");
		ContentGen.name("item", "network_tablet", "Tablet de Rede", "Network Tablet");
		ContentGen.shaped("network_tablet", "CPC|CTC|CRC", Map.of("C", "mlinnotas:cobalt_ingot", "P", "minecraft:ender_pearl",
				"T", "mlinnotas:storage_terminal", "R", "minecraft:redstone"), "misc");
		QuestContent.text("item.mlinnotas.network_tablet.unlinked", "Shift+botão direito num terminal para vincular",
				"Shift+right-click a terminal to link it");
		QuestContent.text("item.mlinnotas.network_tablet.linked", "Vinculado: %s", "Linked: %s");
		QuestContent.text("item.mlinnotas.network_tablet.position", "%s, %s, %s (%s)", "%s, %s, %s (%s)");
		QuestContent.text("item.mlinnotas.network_tablet.usage", "Botão direito ou tecla I: abrir de qualquer distância",
				"Right-click or press I: open from any distance");
		QuestContent.text("message.mlinnotas.network_tablet.linked", "Tablet vinculado: %s em %s, %s, %s", "Tablet linked: %s at %s, %s, %s");
		QuestContent.text("message.mlinnotas.network_tablet.unlinked", "Tablet sem vínculo: Shift+botão direito num terminal",
				"Unlinked tablet: Shift+right-click a terminal");
		QuestContent.text("message.mlinnotas.network_tablet.other_dimension", "O terminal vinculado está em outra dimensão",
				"The linked terminal is in another dimension");
		QuestContent.text("message.mlinnotas.network_tablet.lost", "O terminal em %s, %s, %s sumiu: vínculo apagado",
				"The terminal at %s, %s, %s is gone: link cleared");
		QuestContent.text("container.mlinnotas.remote_terminal", "Terminal Remoto", "Remote Terminal");
		QuestContent.text("container.mlinnotas.remote_crafting_terminal", "Crafting Remoto", "Remote Crafting");
		QuestContent.text("key.mlinnotas.network_tablet", "Tablet de Rede", "Network Tablet");

		QuestContent.EXTRA.computeIfAbsent("tech", k -> new ArrayList<>()).add(QuestContent.q("network_tablet", "mlinnotas:network_tablet", 3, 5,
				"Rede no Bolso", "Network in Your Pocket",
				"O Tablet de Rede abre um terminal de qualquer distância na mesma dimensão. Shift+botão direito no Terminal (ou no Terminal de Crafting, com a grade 3x3) para vincular; depois botão direito em qualquer lugar ou tecla I com ele no inventário. Receita: terminal + pérola do End + lingotes de cobalto.",
				"The Network Tablet opens a terminal from any distance in the same dimension. Shift+right-click the Terminal (or the Crafting Terminal, with its 3x3 grid) to link it; then right-click anywhere or press I with it in your inventory. Recipe: terminal + ender pearl + cobalt ingots.")
				.dep("storage_terminal", "mining/skill_cobalt").have("mlinnotas:network_tablet", 1).xp(120));
	}
}
