package com.mlin.notas.salvage;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalvageRulesTest {
	private static SalvageRules.Piece piece(String id) {
		return SalvageRules.piece(id).orElseThrow(() -> new AssertionError("nao reconhecido: " + id));
	}

	@Test
	void recognizesVanillaAndModPieces() {
		assertEquals(new SalvageRules.Piece(SalvageRules.Material.IRON, 3), piece("minecraft:iron_pickaxe"));
		assertEquals(new SalvageRules.Piece(SalvageRules.Material.IRON, 8), piece("minecraft:iron_chestplate"));
		assertEquals(new SalvageRules.Piece(SalvageRules.Material.GOLD, 3), piece("minecraft:golden_axe"));
		assertEquals(new SalvageRules.Piece(SalvageRules.Material.COBALT, 5), piece("mlinnotas:cobalt_warhammer"));
		assertEquals(new SalvageRules.Piece(SalvageRules.Material.IRON, 4), piece("minecraft:chainmail_chestplate"));
		assertEquals(new SalvageRules.Piece(SalvageRules.Material.CELESTIUM, 1), piece("mlinnotas:celestium_dagger"));
		assertTrue(SalvageRules.piece("minecraft:iron_nugget").isEmpty());
		assertTrue(SalvageRules.piece("minecraft:wooden_sword").isEmpty());
		assertTrue(SalvageRules.piece("minecraft:stone_pickaxe").isEmpty());
		assertTrue(SalvageRules.piece("mlinnotas:iron_wand").isEmpty());
		assertTrue(SalvageRules.piece("othermod:iron_sword").isEmpty());
	}

	@Test
	void metalReturnsNuggetsByDurability() {
		SalvageRules.Piece pick = piece("minecraft:iron_pickaxe");
		// 3 * 0,5 = 1,5 lingote = 13 pepitas com a durabilidade cheia
		assertEquals(new SalvageRules.Yield("minecraft:iron_nugget", 13), SalvageRules.yield(pick, 0, 250));
		// metade da durabilidade: 0,75 lingote = 6 pepitas
		assertEquals(6, SalvageRules.yield(pick, 125, 250).count());
		// quase quebrada: pelo menos 1 pepita
		assertEquals(1, SalvageRules.yield(pick, 249, 250).count());
		// peitoral cheio: 4 lingotes = 36 pepitas
		assertEquals(36, SalvageRules.yield(piece("minecraft:iron_chestplate"), 0, 240).count());
	}

	@Test
	void neverMoreThanHalfTheMaterial() {
		for (String suffix : SalvageRules.PIECES.keySet()) {
			SalvageRules.Piece p = piece("minecraft:diamond_" + suffix);
			assertTrue(SalvageRules.yield(p, 0, 1000).count() <= p.units() / 2.0, suffix);
			SalvageRules.Piece iron = piece("minecraft:iron_" + suffix);
			assertTrue(SalvageRules.yield(iron, 0, 1000).count() <= Math.max(1, iron.units() * 9 / 2), suffix);
		}
		// peitoral de diamante achado num bau: no maximo 4 diamantes
		assertEquals(new SalvageRules.Yield("minecraft:diamond", 4), SalvageRules.yield(piece("minecraft:diamond_chestplate"), 0, 528));
		// espada de diamante usada nao devolve nada (e a fornalha nem aceita)
		assertEquals(0, SalvageRules.yield(piece("minecraft:diamond_sword"), 10, 1000).count());
	}

	@Test
	void cobaltHasNoNuggets() {
		assertEquals(new SalvageRules.Yield("mlinnotas:cobalt_ingot", 1), SalvageRules.yield(piece("mlinnotas:cobalt_pickaxe"), 0, 650));
		assertEquals(0, SalvageRules.yield(piece("mlinnotas:cobalt_pickaxe"), 400, 650).count());
	}

	@Test
	void netheriteGivesScrapOnly() {
		SalvageRules.Piece chest = piece("minecraft:netherite_chestplate");
		assertEquals(new SalvageRules.Yield("minecraft:netherite_scrap", 2), SalvageRules.yield(chest, 0, 592));
		assertEquals(1, SalvageRules.yield(chest, 296, 592).count());
		assertEquals(0, SalvageRules.yield(chest, 500, 592).count());
	}

	@Test
	void smithingTiersReturnAtMostTheirIngot() {
		for (String id : Set.of("mlinnotas:infernite_chestplate", "mlinnotas:endite_greatsword", "mlinnotas:astralite_pickaxe",
				"mlinnotas:celestium_boots")) {
			SalvageRules.Piece p = piece(id);
			assertEquals(1, SalvageRules.yield(p, 0, 1000).count(), id);
			assertEquals(1, SalvageRules.yield(p, 500, 1000).count(), id);
			assertEquals(0, SalvageRules.yield(p, 501, 1000).count(), id);
			assertTrue(SalvageRules.yield(p, 0, 1000).item().endsWith("_ingot"));
		}
	}

	@Test
	void blastOnlyForGemsAndTopTiers() {
		assertTrue(SalvageRules.Material.DIAMOND.blastOnly());
		assertTrue(SalvageRules.Material.NETHERITE.blastOnly());
		assertTrue(SalvageRules.Material.CELESTIUM.blastOnly());
		assertTrue(!SalvageRules.Material.IRON.blastOnly() && !SalvageRules.Material.COBALT.blastOnly());
	}

	@Test
	void enchantXpIsCapped() {
		assertEquals(0, SalvageRules.enchantXp(0));
		assertEquals(7, SalvageRules.enchantXp(7));
		assertEquals(SalvageRules.MAX_ENCHANT_XP, SalvageRules.enchantXp(40));
	}

	@Test
	void durabilityFractionClamps() {
		assertEquals(1.0, SalvageRules.durabilityFraction(0, 0));
		assertEquals(0.0, SalvageRules.durabilityFraction(300, 250));
		assertEquals(0.5, SalvageRules.durabilityFraction(125, 250));
	}
}
