package com.mlin.notas.tier;

import com.mlin.notas.chest.ChestTier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere que os arquivos gerados por scripts/ContentGen.java, TexGen.java e TierTexGen.java existem para cada
 * item da escada (se o Tier mudar e os scripts nao forem rodados de novo, este teste aponta o que falta).
 */
class TierResourcesTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/mlinnotas");
	private static final Path DATA = Path.of("src/main/resources/data/mlinnotas");

	private static void item(List<String> missing, String pt, String en, String id, boolean texture) {
		if (!Files.exists(ASSETS.resolve("items/" + id + ".json"))) {
			missing.add("items/" + id + ".json");
		}
		if (texture && !Files.exists(ASSETS.resolve("textures/item/" + id + ".png"))) {
			missing.add("textures/item/" + id + ".png");
		}
		if (!pt.contains("\"item.mlinnotas." + id + "\"") && !pt.contains("\"block.mlinnotas." + id + "\"")) {
			missing.add("pt_br: " + id);
		}
		if (!en.contains("\"item.mlinnotas." + id + "\"") && !en.contains("\"block.mlinnotas." + id + "\"")) {
			missing.add("en_us: " + id);
		}
		if (!Files.exists(DATA.resolve("recipe/" + id + ".json")) && !id.startsWith("raw_") && !id.endsWith("_ore")
				&& !Files.exists(DATA.resolve("recipe/" + id + "_from_smelting_raw_" + id.replace("_ingot", "") + ".json"))) {
			missing.add("recipe/" + id + ".json");
		}
	}

	@Test
	void everyTieredItemHasModelTextureNameAndRecipe() throws IOException {
		String pt = Files.readString(ASSETS.resolve("lang/pt_br.json"), StandardCharsets.UTF_8);
		String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8);
		List<String> missing = new ArrayList<>();
		for (Tier tier : Tier.values()) {
			item(missing, pt, en, tier == Tier.WOOD ? "wooden_wand" : tier.id() + "_wand", true);
			if (tier != Tier.WOOD) {
				item(missing, pt, en, "furnace_upgrade_" + tier.id(), true);
			}
			if (tier.hasOre()) {
				item(missing, pt, en, "raw_" + tier.id(), true);
			}
			if (!tier.modGear()) {
				continue;
			}
			item(missing, pt, en, tier.id() + "_ingot", true);
			for (GearKind kind : GearKind.values()) {
				item(missing, pt, en, kind.itemName(tier), true);
			}
			for (Tier.ArmorPiece piece : Tier.ArmorPiece.values()) {
				item(missing, pt, en, VanillaRebalance.armorName(tier, piece), true);
			}
			for (String layer : List.of("humanoid", "humanoid_leggings")) {
				Path texture = ASSETS.resolve("textures/entity/equipment/" + layer + "/" + tier.id() + ".png");
				if (!Files.exists(texture)) {
					missing.add(texture.toString());
				}
			}
			if (!Files.exists(ASSETS.resolve("equipment/" + tier.id() + ".json"))) {
				missing.add("equipment/" + tier.id() + ".json");
			}
		}
		for (ChestTier chest : ChestTier.values()) {
			item(missing, pt, en, chest.blockName(), false);
			item(missing, pt, en, chest.upgradeName(), true);
			if (!Files.exists(ASSETS.resolve("textures/entity/chest/" + chest.textureName() + ".png"))) {
				missing.add("textures/entity/chest/" + chest.textureName() + ".png");
			}
		}
		for (String ore : List.of("cobalt_ore", "deepslate_cobalt_ore", "infernite_ore", "endite_ore", "astralite_ore")) {
			item(missing, pt, en, ore, false);
			if (!Files.exists(ASSETS.resolve("textures/block/" + ore + ".png"))) {
				missing.add("textures/block/" + ore + ".png");
			}
			if (!Files.exists(DATA.resolve("loot_table/blocks/" + ore + ".json"))) {
				missing.add("loot_table/blocks/" + ore + ".json");
			}
		}
		assertTrue(missing.isEmpty(), "faltando (rode scripts/ContentGen.java, TexGen.java e TierTexGen.java): " + missing);
	}
}
