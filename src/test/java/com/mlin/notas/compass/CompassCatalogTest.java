package com.mlin.notas.compass;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompassCatalogTest {
	private static final List<String> OVERWORLD = List.of("minecraft:village_plains", "minecraft:village_desert", "minecraft:ruined_portal",
			"minecraft:ruined_portal_desert", "minecraft:shipwreck_beached", "minecraft:shipwreck", "minecraft:mineshaft", "minecraft:mineshaft_mesa",
			"minecraft:stronghold", "minecraft:trail_ruins", "minecraft:abandoned_camp_taiga", "mlinnotas:ruined_chapel", "mlinnotas:cobalt_shrine",
			"mlinnotas:abyssal_sanctum", "mlinnotas:forgotten_crypt");

	@Test
	void groupsVanillaVariants() {
		assertEquals("minecraft:village", CompassCatalog.structureGroup("minecraft:village_snowy"));
		assertEquals("minecraft:ruined_portal", CompassCatalog.structureGroup("minecraft:ruined_portal"));
		assertEquals("minecraft:ruined_portal", CompassCatalog.structureGroup("minecraft:ruined_portal_nether"));
		assertEquals("minecraft:ocean_ruin", CompassCatalog.structureGroup("minecraft:ocean_ruin_cold"));
		assertEquals("minecraft:mineshaft", CompassCatalog.structureGroup("minecraft:mineshaft_mesa"));
		assertEquals("minecraft:trail_ruins", CompassCatalog.structureGroup("minecraft:trail_ruins"));
		assertEquals("mlinnotas:abandoned_camp", CompassCatalog.structureGroup("mlinnotas:abandoned_camp"), "estruturas do mod nao agrupam");
		assertEquals("minecraft:abandoned_camp", CompassCatalog.structureGroup("minecraft:abandoned_camp_meadow"));
	}

	@Test
	void entriesAreDistinctAndLairsWaitForMining() {
		List<String> fresh = CompassCatalog.structureEntries(OVERWORLD, Tier.WOOD.level());
		assertEquals(List.of("minecraft:abandoned_camp", "minecraft:mineshaft", "minecraft:ruined_portal", "minecraft:shipwreck",
				"minecraft:stronghold", "minecraft:trail_ruins", "minecraft:village", "mlinnotas:forgotten_crypt", "mlinnotas:ruined_chapel"), fresh);
		List<String> gold = CompassCatalog.structureEntries(OVERWORLD, Tier.GOLD.level());
		assertTrue(gold.contains("mlinnotas:cobalt_shrine"), "Golem de Cobalto aparece com Mineracao Ouro");
		assertFalse(gold.contains("mlinnotas:abyssal_sanctum"));
		List<String> diamond = CompassCatalog.structureEntries(OVERWORLD, Tier.DIAMOND.level());
		assertTrue(diamond.contains("mlinnotas:abyssal_sanctum"));
		assertFalse(CompassCatalog.unlocked("mlinnotas:end_citadel", Tier.INFERNITE.level()));
		assertTrue(CompassCatalog.unlocked("mlinnotas:end_citadel", Tier.ENDITE.level()));
		assertTrue(CompassCatalog.unlocked("mlinnotas:colossus_forge", Tier.NETHERITE.level()));
		assertNull(CompassCatalog.requiredTier("minecraft:village"));
	}

	@Test
	void everyGateIsTheTierBeforeTheBossReward() {
		// o chefe libera o tier seguinte: o covil aparece um nivel antes
		Map<String, Tier> rewards = Map.of("mlinnotas:cobalt_shrine", Tier.COBALT, "mlinnotas:abyssal_sanctum", Tier.NETHERITE,
				"mlinnotas:colossus_forge", Tier.INFERNITE, "mlinnotas:end_citadel", Tier.ASTRALITE);
		rewards.forEach((id, reward) -> assertEquals(reward.ordinal() - 1, CompassCatalog.requiredTier(id).ordinal(), id));
	}

	@Test
	void structuresOfAnEntry() {
		assertEquals(List.of("minecraft:village_plains", "minecraft:village_desert"), CompassCatalog.structuresOf("minecraft:village", OVERWORLD));
		assertEquals(List.of("mlinnotas:ruined_chapel"), CompassCatalog.structuresOf("mlinnotas:ruined_chapel", OVERWORLD));
	}

	@Test
	void searchMatchesNameIdAndNamespaceIgnoringAccents() {
		Map<String, String> names = Map.of("minecraft:swamp", "Pântano", "minecraft:mangrove_swamp", "Pântano de Mangue",
				"mlinnotas:ashen_plains", "Planícies de Cinza", "minecraft:plains", "Planícies");
		List<String> ids = List.copyOf(names.keySet());
		assertEquals(List.of("minecraft:swamp", "minecraft:mangrove_swamp"), CompassCatalog.filter(ids, "pantano", names::get));
		assertEquals(List.of("minecraft:plains", "mlinnotas:ashen_plains"), CompassCatalog.filter(ids, "PLAN", names::get), "ordem pelo nome");
		assertEquals(List.of("mlinnotas:ashen_plains"), CompassCatalog.filter(ids, "@mlin", names::get));
		assertEquals(List.of("mlinnotas:ashen_plains"), CompassCatalog.filter(ids, "@mlinnotas cinza", names::get));
		assertEquals(List.of(), CompassCatalog.filter(ids, "@mlinnotas mangue", names::get));
		assertEquals(List.of("minecraft:mangrove_swamp"), CompassCatalog.filter(ids, "mangrove", names::get), "o id tambem conta");
		assertEquals(4, CompassCatalog.filter(ids, "  ", names::get).size());
	}

	@Test
	void namesAndKeys() {
		assertEquals("Trail Ruins", CompassCatalog.prettyName("minecraft:trail_ruins"));
		assertEquals("biome.mlinnotas.ashen_plains", CompassCatalog.nameKey(CompassKind.BIOME, "mlinnotas:ashen_plains"));
		assertEquals("compass.mlinnotas.structure.minecraft.village", CompassCatalog.nameKey(CompassKind.STRUCTURE, "minecraft:village"));
	}

	@Test
	void distanceAndDirection() {
		assertEquals(500, CompassCatalog.horizontalDistance(0, 0, 300, 400));
		assertEquals("n", CompassCatalog.direction(0, 0, 0, -100));
		assertEquals("e", CompassCatalog.direction(0, 0, 100, 0));
		assertEquals("s", CompassCatalog.direction(0, 0, 0, 100));
		assertEquals("w", CompassCatalog.direction(0, 0, -100, 0));
		assertEquals("ne", CompassCatalog.direction(0, 0, 100, -100));
		assertEquals("sw", CompassCatalog.direction(0, 0, -70, 75));
		assertEquals("here", CompassCatalog.direction(10, 10, 10.5, 10.5));
	}

	@Test
	void spiralVisitsEveryColumnOnceInRings() {
		SpiralSearch spiral = new SpiralSearch(100, -50, 64, 32);
		Set<String> seen = new HashSet<>();
		int lastRing = 0;
		int count = 0;
		while (spiral.next()) {
			int dx = (spiral.x() - 100) / 32;
			int dz = (spiral.z() + 50) / 32;
			int ring = Math.max(Math.abs(dx), Math.abs(dz));
			assertTrue(ring >= lastRing, "anel nunca volta");
			lastRing = ring;
			assertTrue(seen.add(dx + "," + dz), "coluna repetida " + dx + "," + dz);
			assertEquals(0, (spiral.x() - 100) % 32);
			count++;
		}
		assertEquals(25, count, "5x5 colunas para raio de 2 passos");
		assertFalse(spiral.next());
		SpiralSearch first = new SpiralSearch(7, 9, 6400, 32);
		assertTrue(first.next());
		assertEquals(7, first.x());
		assertEquals(9, first.z(), "comeca na coluna do jogador");
	}
}
