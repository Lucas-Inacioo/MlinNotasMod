package com.mlin.notas.gating;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolGateTest {
	private static final Path DATA = Path.of("src/main/resources/data/mlinnotas");
	private static final Path LANG = Path.of("src/main/resources/assets/mlinnotas/lang");

	@Test
	void woodIsAlwaysAllowedAndItemsWithoutTierToo() {
		assertTrue(ToolGate.allowed(Tier.WOOD, 1));
		assertTrue(ToolGate.allowed(null, 1));
		assertTrue(ToolGate.allowed(Tier.WOOD, 0));
	}

	@Test
	void tierNeedsTheSameMiningLevel() {
		// mundo do casal: Mineracao em Ouro (nivel 5)
		int gold = Tier.GOLD.level();
		assertTrue(ToolGate.allowed(Tier.IRON, gold), "ferro liberado no Ouro");
		assertTrue(ToolGate.allowed(Tier.GOLD, gold), "ouro liberado no Ouro");
		assertFalse(ToolGate.allowed(Tier.COBALT, gold), "cobalto (depois do ouro) travado");
		assertFalse(ToolGate.allowed(Tier.DIAMOND, gold), "diamante travado no Ouro");
		assertTrue(ToolGate.allowed(Tier.DIAMOND, Tier.DIAMOND.level()));
		assertFalse(ToolGate.allowed(Tier.STONE, Tier.WOOD.level()), "mundo novo: nem pedra");
		assertTrue(ToolGate.allowed(Tier.CELESTIUM, 12));
		for (Tier tier : Tier.values()) {
			assertEquals(tier.level(), ToolGate.requiredLevel(tier));
		}
	}

	@Test
	void creativeBypassesAndMissingReportsTheTier() {
		assertEquals(Tier.DIAMOND, ToolGate.missing(Tier.DIAMOND, Tier.GOLD.level(), false));
		assertNull(ToolGate.missing(Tier.DIAMOND, Tier.GOLD.level(), true));
		assertNull(ToolGate.missing(Tier.IRON, Tier.GOLD.level(), false));
	}

	@Test
	void tierOfPicksTheHighestTag() {
		assertNull(ToolGate.tierOf(tier -> false));
		assertEquals(Tier.DIAMOND, ToolGate.tierOf(tier -> tier == Tier.DIAMOND));
		assertEquals(Tier.NETHERITE, ToolGate.tierOf(tier -> tier == Tier.IRON || tier == Tier.NETHERITE));
	}

	@Test
	void messageIsRateLimited() {
		assertTrue(ToolGate.shouldNotify(-1, 0));
		assertFalse(ToolGate.shouldNotify(100, 100 + ToolGate.MESSAGE_COOLDOWN_TICKS - 1));
		assertTrue(ToolGate.shouldNotify(100, 100 + ToolGate.MESSAGE_COOLDOWN_TICKS));
		assertTrue(ToolGate.shouldNotify(100, 5), "relogio voltou (outro mundo): avisa");
	}

	@Test
	void itemIdsCoverToolsWeaponsBossWeaponsAndWands() {
		List<String> diamond = ToolGate.itemIds(Tier.DIAMOND);
		assertTrue(diamond.containsAll(List.of("minecraft:diamond_pickaxe", "minecraft:diamond_sword", "minecraft:diamond_spear",
				"minecraft:diamond_hoe", "mlinnotas:diamond_greatsword", "mlinnotas:diamond_rapier", "mlinnotas:diamond_wand")), diamond.toString());
		List<String> gold = ToolGate.itemIds(Tier.GOLD);
		assertTrue(gold.containsAll(List.of("minecraft:golden_axe", "mlinnotas:golden_dagger", "mlinnotas:gold_wand")), gold.toString());
		assertTrue(ToolGate.itemIds(Tier.COBALT).containsAll(List.of("mlinnotas:cobalt_pickaxe", "mlinnotas:golem_hammer")));
		assertTrue(ToolGate.itemIds(Tier.ASTRALITE).contains("mlinnotas:astral_lance"));
		assertTrue(ToolGate.itemIds(Tier.WOOD).contains("mlinnotas:wooden_wand"));
		// nada de armadura
		for (Tier tier : Tier.values()) {
			for (String id : ToolGate.itemIds(tier)) {
				assertFalse(id.endsWith("_chestplate") || id.endsWith("_helmet"), id);
			}
		}
	}

	@Test
	void generatedTagsMatchTheRule() throws IOException {
		Pattern value = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_/.-]+)\"");
		List<String> problems = new ArrayList<>();
		for (Tier tier : Tier.values()) {
			Path file = DATA.resolve("tags/item/" + ToolGate.tagPath(tier) + ".json");
			if (!Files.exists(file)) {
				problems.add("falta " + file);
				continue;
			}
			Set<String> values = new HashSet<>();
			Matcher m = value.matcher(Files.readString(file, StandardCharsets.UTF_8));
			while (m.find()) {
				values.add(m.group(1));
			}
			if (!values.equals(new HashSet<>(ToolGate.itemIds(tier)))) {
				problems.add(tier + ": tag " + values + " != regra " + ToolGate.itemIds(tier));
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void textsExistInBothLanguages() throws IOException {
		for (String lang : List.of("pt_br", "en_us")) {
			String text = Files.readString(LANG.resolve(lang + ".json"), StandardCharsets.UTF_8);
			for (String key : List.of("message.mlinnotas.tool_locked", "tooltip.mlinnotas.tool_tier", "skill.mlinnotas.unlock.tools",
					"item.mlinnotas.network_tablet", "container.mlinnotas.remote_terminal", "key.mlinnotas.network_tablet",
					"message.mlinnotas.network_tablet.lost", "quest.mlinnotas.tech.network_tablet")) {
				assertTrue(text.contains("\"" + key + "\""), lang + ": " + key);
			}
		}
	}
}
