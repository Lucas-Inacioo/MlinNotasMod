package com.mlin.notas.village.civic;

import com.mlin.notas.accessories.AccessoryCatalog;
import com.mlin.notas.waystone.WaystoneCost;
import com.mlin.notas.waystone.WaystoneInfo;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras puras das construcoes civicas: Correio, Museu, Guilda, Escola, Torre, Quartel, Farol, pedras de vila, Refugio e Seguro. */
class CivicRulesTest {
	// ---------------------------------------------------------------- correio

	@Test
	void mailArrivesTheNextDay() {
		assertEquals(24_000L, PostRules.deliveryTick(0, 1));
		assertEquals(24_000L, PostRules.deliveryTick(23_999, 1));
		assertEquals(48_000L, PostRules.deliveryTick(24_000, 1));
		assertEquals(12_000L, PostRules.deliveryTick(0, 2), "nivel II entrega na meia virada");
		assertEquals(24_000L, PostRules.deliveryTick(12_000, 2));
	}

	@Test
	void mailboxSizesGrowWithTheLevel() {
		assertEquals(9, PostRules.outboxSlots(1));
		assertEquals(18, PostRules.outboxSlots(2));
		assertEquals(4, PostRules.menuRows(1));
		assertEquals(5, PostRules.menuRows(2));
		assertEquals(0, PostRules.cycle(2, 3, 1));
		assertEquals(2, PostRules.cycle(0, 3, -1));
		assertEquals(0, PostRules.cycle(0, 0, 1));
		assertEquals("v:Kalanros", PostRules.villageInbox("Kalanros"));
		assertEquals("p:abc", PostRules.playerInbox("abc"));
	}

	// ---------------------------------------------------------------- museu

	@Test
	void museumSetsAreUnlockedByLevelAndDonationsCountInEveryCollection() {
		assertEquals(3, MuseumSets.available(1).size());
		assertEquals(5, MuseumSets.available(2).size());
		assertEquals(7, MuseumSets.available(3).size());
		assertTrue(MuseumSets.wanted("minecraft:diamond", 1), "diamante vale em minerios e gemas");
		assertFalse(MuseumSets.wanted("mlinnotas:cobalt_heart", 2), "troféu de chefe so no nivel III");
		assertTrue(MuseumSets.wanted("mlinnotas:cobalt_heart", 3));
		assertFalse(MuseumSets.wanted("minecraft:dirt", 3));
	}

	@Test
	void completingASetPaysOnceAndOnlyAtItsLevel() {
		Set<String> donated = new HashSet<>(MuseumSets.byId("gems").orElseThrow().items());
		List<MuseumSets.MuseumSet> done = MuseumSets.newlyComplete(donated, 1, Set.of());
		assertEquals(List.of("gems"), done.stream().map(MuseumSets.MuseumSet::id).toList());
		assertTrue(MuseumSets.newlyComplete(donated, 1, Set.of("gems")).isEmpty(), "recompensa so uma vez");
		donated.addAll(MuseumSets.byId("fish").orElseThrow().items());
		assertTrue(MuseumSets.newlyComplete(donated, 1, Set.of("gems")).isEmpty(), "peixes so no nivel II");
		assertEquals(1, MuseumSets.newlyComplete(donated, 2, Set.of("gems")).size());
		for (MuseumSets.MuseumSet set : MuseumSets.all()) {
			assertTrue(set.prosperity() > 0 && !set.rewards().isEmpty() && !set.items().isEmpty(), set.id());
			assertEquals(set.items().size(), new HashSet<>(set.items()).size(), set.id() + " tem item repetido");
		}
	}

	@Test
	void museumArtifactsExistInTheAccessoryCatalog() {
		for (String id : MuseumSets.byId("artifacts").orElseThrow().items()) {
			assertTrue(AccessoryCatalog.byId(id.substring("mlinnotas:".length())) != null, id + " nao e um artefato do catalogo");
		}
	}

	// ---------------------------------------------------------------- guilda

	@Test
	void gearTiersAreReadFromItemIds() {
		assertEquals(3, GearTiers.parse("minecraft:iron_pickaxe").orElseThrow().tier());
		assertEquals(GearTiers.Kind.TOOL, GearTiers.parse("minecraft:iron_pickaxe").orElseThrow().kind());
		assertEquals(GearTiers.Kind.WEAPON, GearTiers.parse("mlinnotas:cobalt_greatsword").orElseThrow().kind());
		assertEquals(5, GearTiers.parse("mlinnotas:cobalt_greatsword").orElseThrow().tier());
		assertEquals(GearTiers.Kind.BOOTS, GearTiers.parse("minecraft:golden_boots").orElseThrow().kind());
		assertEquals(11, GearTiers.parse("mlinnotas:celestium_helmet").orElseThrow().tier());
		assertEquals(0, GearTiers.parse("minecraft:leather_chestplate").orElseThrow().tier());
		assertTrue(GearTiers.parse("minecraft:bread").isEmpty());
		assertTrue(GearTiers.parse("minecraft:diamond").isEmpty());
	}

	@Test
	void betterGearShortensTheTripAndRaisesTheLoot() {
		ExpeditionRules.Gear none = ExpeditionRules.Gear.NONE;
		ExpeditionRules.Gear iron = new ExpeditionRules.Gear(3, 3, new int[] {3, 3, 3, 3}, 0);
		ExpeditionRules.Gear celestium = new ExpeditionRules.Gear(11, 11, new int[] {11, 11, 11, 11}, 8);
		assertEquals(3, ExpeditionRules.durationDays(none));
		assertEquals(2, ExpeditionRules.durationDays(iron));
		assertEquals(1, ExpeditionRules.durationDays(celestium));
		assertTrue(ExpeditionRules.lootMultiplier(none) < ExpeditionRules.lootMultiplier(iron));
		assertTrue(ExpeditionRules.lootMultiplier(iron) < ExpeditionRules.lootMultiplier(celestium));
		assertEquals(1.0, ExpeditionRules.lootMultiplier(none), 1e-9);
		assertEquals(1, ExpeditionRules.maxExplorers(1));
		assertEquals(5, ExpeditionRules.maxExplorers(5));
		assertTrue(ExpeditionRules.rolls(5) > ExpeditionRules.rolls(1));
		assertTrue(ExpeditionRules.wearPerTrip(3) > ExpeditionRules.wearPerTrip(1));
	}

	@Test
	void oresNeverExceedTheWorldMiningLevelOrTheTool() {
		ExpeditionRules.Gear diamondTool = new ExpeditionRules.Gear(6, 6, new int[] {6, 6, 6, 6}, 4);
		for (int mining = 1; mining <= 11; mining++) {
			for (long seed = 0; seed < 60; seed++) {
				for (ExpeditionRules.Drop drop : ExpeditionRules.roll(ExpeditionRules.Destination.MINE, diamondTool, mining, 5, new Random(seed))) {
					int tier = oreTier(drop.item());
					assertTrue(tier <= mining, "minerio " + drop.item() + " acima da Mineracao " + mining);
					assertTrue(tier <= 6, "minerio acima da ferramenta: " + drop.item());
				}
			}
		}
		// ferro no mundo (3): nada de cobalto, diamante ou esmeralda
		for (ExpeditionRules.Entry entry : ExpeditionRules.eligible(ExpeditionRules.Destination.MINE, diamondTool, 3)) {
			assertTrue(entry.oreTier() <= 3, entry.item());
		}
		// sem ferramenta: so pedra e carvao
		for (ExpeditionRules.Entry entry : ExpeditionRules.eligible(ExpeditionRules.Destination.MINE, ExpeditionRules.Gear.NONE, 11)) {
			assertTrue(entry.oreTier() <= 1, entry.item());
		}
	}

	private static int oreTier(String item) {
		for (ExpeditionRules.Destination d : ExpeditionRules.Destination.values()) {
			for (ExpeditionRules.Entry e : ExpeditionRules.table(d)) {
				if (e.item().equals(item) && e.oreTier() >= 0) {
					return e.oreTier();
				}
			}
		}
		return -1;
	}

	@Test
	void expeditionTablesNeverHandOutBossItems() {
		Set<String> banned = Set.of("mlinnotas:cobalt_heart", "mlinnotas:abyssal_core", "mlinnotas:colossus_ember", "mlinnotas:astral_fragment",
				"mlinnotas:dragon_scale", "mlinnotas:forge_key", "minecraft:nether_star", "minecraft:dragon_head", "minecraft:wither_skeleton_skull",
				"minecraft:elytra", "minecraft:totem_of_undying", "minecraft:dragon_egg");
		for (ExpeditionRules.Destination d : ExpeditionRules.Destination.values()) {
			assertFalse(ExpeditionRules.table(d).isEmpty());
			for (ExpeditionRules.Entry e : ExpeditionRules.table(d)) {
				assertFalse(banned.contains(e.item()), e.item());
				assertFalse(e.item().contains("smithing_template"), e.item());
				assertTrue(e.min() >= 1 && e.max() >= e.min() && e.weight() > 0, e.item());
			}
		}
	}

	@Test
	void netherAndEndOnlyAfterTheyWereVisited() {
		assertEquals(4, ExpeditionRules.destinations(Set.of("minecraft:overworld")).size());
		assertTrue(ExpeditionRules.destinations(Set.of("minecraft:overworld", "minecraft:the_nether")).contains(ExpeditionRules.Destination.NETHER));
		assertFalse(ExpeditionRules.destinations(Set.of("minecraft:overworld", "minecraft:the_nether")).contains(ExpeditionRules.Destination.END));
		assertEquals(6, ExpeditionRules.destinations(Set.of("minecraft:the_nether", "minecraft:the_end")).size());
		assertEquals(ExpeditionRules.Destination.HUNT, ExpeditionRules.Destination.byId("hunt"));
	}

	@Test
	void lootScalesWithGear() {
		ExpeditionRules.Gear celestium = new ExpeditionRules.Gear(11, 11, new int[] {11, 11, 11, 11}, 8);
		long plain = total(ExpeditionRules.Gear.NONE);
		long geared = total(celestium);
		assertTrue(geared > plain * 2, plain + " -> " + geared);
	}

	private static long total(ExpeditionRules.Gear gear) {
		long n = 0;
		for (long seed = 0; seed < 100; seed++) {
			for (ExpeditionRules.Drop d : ExpeditionRules.roll(ExpeditionRules.Destination.FOREST, gear, 11, 3, new Random(seed))) {
				n += d.count();
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- escola, torre, quartel, farol

	@Test
	void schoolSpeedsUpBabiesAndNovices() {
		assertEquals(0, CivicRules.babyExtraTicksPerSecond(0));
		assertEquals(20, CivicRules.babyExtraTicksPerSecond(1), "2x: +1 tique por tique");
		assertEquals(40, CivicRules.babyExtraTicksPerSecond(2), "3x");
		assertEquals(0, CivicRules.noviceXpInterval(1));
		assertTrue(CivicRules.noviceXpInterval(2) > 0);
		assertTrue(CivicRules.noviceXpCap() < 10, "o ponto da escola nunca levanta o nivel sozinho");
	}

	@Test
	void towerRingsOncePerRaidAndMarksMonstersAtNight() {
		assertTrue(CivicRules.shouldRing(true, false));
		assertFalse(CivicRules.shouldRing(true, true));
		assertFalse(CivicRules.shouldRing(false, false));
		assertTrue(CivicRules.isNight(14_000));
		assertTrue(CivicRules.isNight(14_000 + 24_000 * 5));
		assertFalse(CivicRules.isNight(6_000));
		assertTrue(CivicRules.towerMarksMonsters(1, 15_000));
		assertFalse(CivicRules.towerMarksMonsters(1, 3_000));
		assertFalse(CivicRules.towerMarksMonsters(0, 15_000));
		assertEquals(0, CivicRules.archers(2));
		assertEquals(4, CivicRules.archers(3));
		assertTrue(CivicRules.archerRange(3) > 20 && CivicRules.archerRange(2) == 0);
	}

	@Test
	void barracksGuardsAreTwoFourSix() {
		assertEquals(0, CivicRules.guards(0));
		assertEquals(2, CivicRules.guards(1));
		assertEquals(4, CivicRules.guards(2));
		assertEquals(6, CivicRules.guards(3));
		assertEquals("minecraft:chainmail_helmet", CivicRules.guardArmor(1, 0));
		assertEquals("minecraft:iron_boots", CivicRules.guardArmor(2, 3));
		assertEquals("minecraft:diamond_chestplate", CivicRules.guardArmor(3, 1));
		assertTrue(CivicRules.patrolNow(15_000) && !CivicRules.patrolNow(2_000));
		for (long seed = 0; seed < 50; seed++) {
			Random random = new Random(seed);
			int[] p = CivicRules.patrolPoint(random::nextDouble, 10, 40);
			double d = Math.hypot(p[0], p[1]);
			assertTrue(d >= 9 && d <= 41, "ponto fora do anel: " + d);
		}
	}

	@Test
	void communityBeaconStacksEffectsAndCoversTheVillage() {
		assertEquals(0, CivicRules.beaconEffects(0).size());
		for (int level = 1; level <= 5; level++) {
			assertEquals(level, CivicRules.beaconEffects(level).size());
		}
		assertEquals("minecraft:speed", CivicRules.beaconEffects(1).get(0).id());
		assertEquals("minecraft:regeneration", CivicRules.beaconEffects(5).get(4).id());
		assertTrue(CivicRules.beaconRange(52, 1) >= 60);
		assertEquals(CivicRules.beaconRange(10, 5), com.mlin.notas.beacon.BeaconTiers.range(com.mlin.notas.tier.Tier.CELESTIUM, 4));
		assertEquals(com.mlin.notas.tier.Tier.IRON, CivicRules.beaconMetal(1));
		assertEquals(com.mlin.notas.tier.Tier.CELESTIUM, CivicRules.beaconMetal(5));
		assertTrue(CivicRules.beaconDuration() > 80, "a duracao cobre o intervalo de renovacao (4 s)");
	}

	// ---------------------------------------------------------------- pedras, refugio, seguro

	@Test
	void villageWaystonesHalveTheCost() {
		WaystoneInfo a = new WaystoneInfo("minecraft:overworld", 0, 64, 0, "A");
		WaystoneInfo b = new WaystoneInfo("minecraft:overworld", 3000, 64, 0, "B");
		WaystoneInfo c = new WaystoneInfo("minecraft:overworld", 6000, 64, 0, "C");
		try {
			WaystoneCost.setVillageKeys(Set.of());
			assertEquals(6, WaystoneCost.levels(a, b));
			WaystoneCost.setVillageKeys(Set.of(a.key(), b.key()));
			assertEquals(3, WaystoneCost.levels(a, b), "duas pedras de vila: metade");
			assertEquals(12, WaystoneCost.levels(a, c), "so uma e de vila: preco cheio");
			assertEquals(1, CivicRules.halfCost(1));
			assertEquals(2, CivicRules.halfCost(3));
		} finally {
			WaystoneCost.setVillageKeys(Set.of());
		}
	}

	@Test
	void refugeAndInsuranceRules() {
		assertTrue(CivicRules.refugeBlocksSpawn(true, true, true));
		assertFalse(CivicRules.refugeBlocksSpawn(false, true, true), "sem muralha completa nao protege");
		assertFalse(CivicRules.refugeBlocksSpawn(true, false, true), "fora das construcoes nasce normal");
		assertFalse(CivicRules.refugeBlocksSpawn(true, true, false), "ovo/comando/gerador nao contam");
		assertTrue(CivicRules.insured(2, 30, 52));
		assertTrue(CivicRules.insured(3, 70, 52));
		assertFalse(CivicRules.insured(1, 10, 52), "so com a Estalagem II");
		assertFalse(CivicRules.insured(2, 100, 52), "longe da vila");
		assertEquals(24_000L, CivicRules.lostFoundTicks());
	}
}
