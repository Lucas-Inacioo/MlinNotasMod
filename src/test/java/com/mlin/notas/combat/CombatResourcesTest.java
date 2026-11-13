package com.mlin.notas.combat;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere os arquivos gerados por scripts/CombatContent.java (via ContentGen) e scripts/WeaponTexGen.java para cada
 * arma registrada: modelo, textura, nome nos dois idiomas, receita e as descricoes das classes.
 */
class CombatResourcesTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/mlinnotas");
	private static final Path DATA = Path.of("src/main/resources/data/mlinnotas");

	private static void check(List<String> missing, String pt, String en, String id) {
		for (Path file : List.of(ASSETS.resolve("items/" + id + ".json"), ASSETS.resolve("models/item/" + id + ".json"),
				ASSETS.resolve("textures/item/" + id + ".png"), DATA.resolve("recipe/" + id + ".json"))) {
			if (!Files.exists(file)) {
				missing.add(file.toString());
			}
		}
		if (!pt.contains("\"item.mlinnotas." + id + "\"")) {
			missing.add("pt_br: " + id);
		}
		if (!en.contains("\"item.mlinnotas." + id + "\"")) {
			missing.add("en_us: " + id);
		}
	}

	@Test
	void everyWeaponHasModelTextureNameAndRecipe() throws IOException {
		String pt = Files.readString(ASSETS.resolve("lang/pt_br.json"), StandardCharsets.UTF_8);
		String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8);
		List<String> missing = new ArrayList<>();
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			for (Tier tier : Tier.values()) {
				check(missing, pt, en, weaponClass.itemName(tier));
			}
			String desc = "\"weapon.mlinnotas." + weaponClass.suffix() + ".desc\"";
			if (!pt.contains(desc) || !en.contains(desc)) {
				missing.add("lang: " + desc);
			}
			if (!Files.exists(ASSETS.resolve("models/item/weapon_" + weaponClass.suffix() + ".json"))) {
				missing.add("models/item/weapon_" + weaponClass.suffix() + ".json");
			}
		}
		for (BossWeapon weapon : BossWeapon.values()) {
			check(missing, pt, en, weapon.id());
			if (!pt.contains("\"item.mlinnotas." + weapon.id() + ".desc\"") || !en.contains("\"item.mlinnotas." + weapon.id() + ".desc\"")) {
				missing.add("lang: " + weapon.id() + ".desc");
			}
		}
		for (String key : List.of("\"weapon.mlinnotas.combo\"", "\"key.mlinnotas.dodge\"")) {
			if (!pt.contains(key) || !en.contains(key)) {
				missing.add("lang: " + key);
			}
		}
		assertTrue(missing.isEmpty(), "faltando (rode scripts/ContentGen.java e scripts/WeaponTexGen.java): " + missing);
	}

	@Test
	void netheriteWeaponsAreSmithingUpgradesOfDiamond() throws IOException {
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			String recipe = Files.readString(DATA.resolve("recipe/" + weaponClass.itemName(Tier.NETHERITE) + ".json"));
			assertTrue(recipe.contains("smithing_transform") && recipe.contains(weaponClass.itemName(Tier.DIAMOND)), weaponClass.name());
		}
	}
}
