package com.mlin.notas.smithing;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cadeia de ferraria pos-netherite tem receita para toda peca, moldes com duplicacao, quem solta e textos. */
class SmithingChainTest {
	private static final Path RES = Path.of("src/main/resources");

	@Test
	void chainCoversEveryTierAboveNetheriteInOrder() {
		Tier expected = Tier.NETHERITE;
		for (SmithingChain step : SmithingChain.values()) {
			assertEquals(expected, step.from(), step + " parte do nivel anterior");
			assertEquals(expected.ordinal() + 1, step.to().ordinal());
			assertEquals(step, SmithingChain.forTier(step.to()));
			expected = step.to();
		}
		assertEquals(Tier.CELESTIUM, expected);
		assertNull(SmithingChain.forTier(Tier.COBALT), "cobalto continua na mesa de trabalho");
	}

	/** Ferramentas/armaduras + as classes de arma do combate ({@code WeaponClass.ALL}). */
	private static java.util.List<String> pieces() {
		java.util.List<String> all = new java.util.ArrayList<>(SmithingChain.PIECES);
		for (com.mlin.notas.combat.WeaponClass weapon : com.mlin.notas.combat.WeaponClass.ALL) {
			all.add(weapon.suffix());
		}
		return all;
	}

	@Test
	void everyPieceIsASmithingUpgrade() throws IOException {
		for (SmithingChain step : SmithingChain.values()) {
			for (String piece : pieces()) {
				Path recipe = RES.resolve("data/mlinnotas/recipe/" + step.to().id() + "_" + piece + ".json");
				String json = Files.readString(recipe, StandardCharsets.UTF_8);
				assertTrue(json.contains("\"minecraft:smithing_transform\""), recipe + " e de ferraria");
				assertTrue(json.contains("\"mlinnotas:" + step.templateId() + "\""), recipe + " usa o molde");
				String base = step.from() == Tier.NETHERITE && SmithingChain.PIECES.contains(piece) ? "minecraft:netherite_" + piece : "mlinnotas:" + step.from().id() + "_" + piece;
				assertTrue(json.contains("\"" + base + "\""), recipe + " parte de " + base);
				assertTrue(json.contains("\"" + step.ingot() + "\""), recipe + " gasta " + step.ingot());
				assertFalse(json.contains("crafting_shaped"), recipe + " sem receita de mesa");
			}
		}
	}

	@Test
	void templatesDuplicateAndDropFromTheirBoss() throws IOException {
		for (SmithingChain step : SmithingChain.values()) {
			String dup = Files.readString(RES.resolve("data/mlinnotas/recipe/" + step.templateId() + ".json"), StandardCharsets.UTF_8);
			assertTrue(dup.contains("\"count\": 2") && dup.contains("\"" + step.duplicationMaterial() + "\"") && dup.contains("\"" + step.duplicationBlock() + "\""),
					step + " duplica com 7 do material + bloco base");
			assertTrue(Files.exists(RES.resolve("assets/mlinnotas/textures/item/" + step.templateId() + ".png")), step + " textura");
			for (String lang : new String[] {"pt_br", "en_us"}) {
				String json = Files.readString(RES.resolve("assets/mlinnotas/lang/" + lang + ".json"), StandardCharsets.UTF_8);
				for (String key : new String[] {"item.mlinnotas." + step.templateId(), "item.mlinnotas." + step.templateId() + ".tooltip",
						"item.mlinnotas.smithing_template." + step.id() + ".applies_to", "item.mlinnotas.smithing_template." + step.id() + ".ingredients",
						"item.mlinnotas.smithing_template." + step.id() + ".base_slot_description",
						"item.mlinnotas.smithing_template." + step.id() + ".additions_slot_description"}) {
					assertTrue(json.contains("\"" + key + "\""), lang + " sem " + key);
				}
			}
		}
		// chefes do mod pela loot table; o dragao pelo codigo (DragonScales); o wither pela loot table vanilla sobrescrita
		assertTrue(Files.readString(RES.resolve("data/mlinnotas/loot_table/entities/igneous_colossus.json")).contains(SmithingChain.INFERNAL.templateId()));
		assertTrue(Files.readString(RES.resolve("data/mlinnotas/loot_table/entities/astral_herald.json")).contains(SmithingChain.ASTRAL.templateId()));
		assertTrue(Files.readString(RES.resolve("data/minecraft/loot_table/entities/wither.json")).contains(SmithingChain.CELESTIAL.templateId()));
	}
}
