package com.mlin.notas.beacon;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconTiersTest {
	@Test
	void metalOfKnowsVanillaAndModBlocks() {
		assertEquals(Tier.IRON, BeaconTiers.metalOf("minecraft:iron_block"));
		assertEquals(Tier.GOLD, BeaconTiers.metalOf("minecraft:gold_block"));
		assertEquals(Tier.DIAMOND, BeaconTiers.metalOf("minecraft:diamond_block"));
		assertEquals(Tier.DIAMOND, BeaconTiers.metalOf("minecraft:emerald_block"));
		assertEquals(Tier.NETHERITE, BeaconTiers.metalOf("minecraft:netherite_block"));
		assertEquals(Tier.COBALT, BeaconTiers.metalOf("mlinnotas:cobalt_block"));
		assertEquals(Tier.CELESTIUM, BeaconTiers.metalOf("mlinnotas:celestium_block"));
		assertNull(BeaconTiers.metalOf("mlinnotas:raw_cobalt_block"));
		assertNull(BeaconTiers.metalOf("mlinnotas:stone_block"));
		assertNull(BeaconTiers.metalOf("minecraft:copper_block"));
	}

	@Test
	void pyramidTierIsTheWeakestMetal() {
		assertEquals(Tier.CELESTIUM, BeaconTiers.pyramidTier(List.of(Tier.CELESTIUM, Tier.CELESTIUM)));
		assertEquals(Tier.GOLD, BeaconTiers.pyramidTier(List.of(Tier.CELESTIUM, Tier.GOLD, Tier.ENDITE)));
		assertEquals(Tier.COBALT, BeaconTiers.pyramidTier(List.of(Tier.DIAMOND, Tier.COBALT, Tier.NETHERITE)));
		// bloco de base desconhecido conta como ferro
		assertEquals(Tier.IRON, BeaconTiers.pyramidTier(Arrays.asList(Tier.CELESTIUM, null)));
		assertNull(BeaconTiers.pyramidTier(Collections.emptyList()));
	}

	@Test
	void ironKeepsVanillaRangeAndCelestiumReaches200() {
		for (int levels = 1; levels <= 4; levels++) {
			assertEquals(levels * 10 + 10, BeaconTiers.range(Tier.IRON, levels));
		}
		assertEquals(50, BeaconTiers.range(Tier.IRON, 4));
		assertEquals(100, BeaconTiers.range(Tier.NETHERITE, 4));
		assertEquals(200, BeaconTiers.range(Tier.CELESTIUM, 4));
		assertEquals(0, BeaconTiers.range(Tier.CELESTIUM, 0));
		int previous = 0;
		for (Tier metal : BeaconTiers.METALS) {
			int range = BeaconTiers.range(metal, 4);
			assertTrue(range > previous, metal + " alcance cresce");
			previous = range;
		}
	}

	@Test
	void levelThreeUnlocksPerMetal() {
		assertFalse(BeaconTiers.levelThree(BeaconTiers.Primary.SPEED, Tier.NETHERITE));
		assertTrue(BeaconTiers.levelThree(BeaconTiers.Primary.SPEED, Tier.INFERNITE));
		assertFalse(BeaconTiers.levelThree(BeaconTiers.Primary.HASTE, Tier.INFERNITE));
		assertTrue(BeaconTiers.levelThree(BeaconTiers.Primary.HASTE, Tier.ENDITE));
		assertFalse(BeaconTiers.levelThree(BeaconTiers.Primary.STRENGTH, Tier.ENDITE));
		assertTrue(BeaconTiers.levelThree(BeaconTiers.Primary.STRENGTH, Tier.ASTRALITE));
		assertFalse(BeaconTiers.levelThree(BeaconTiers.Primary.RESISTANCE, Tier.ASTRALITE));
		assertTrue(BeaconTiers.levelThree(BeaconTiers.Primary.RESISTANCE, Tier.CELESTIUM));
		assertFalse(BeaconTiers.levelThree(BeaconTiers.Primary.JUMP_BOOST, Tier.CELESTIUM));
	}

	@Test
	void vanillaPyramidsKeepVanillaAmplifiers() {
		for (Tier vanilla : List.of(Tier.IRON, Tier.GOLD, Tier.DIAMOND, Tier.NETHERITE)) {
			for (BeaconTiers.Primary primary : BeaconTiers.Primary.values()) {
				assertEquals(0, BeaconTiers.primaryAmplifier(primary, vanilla, false));
				assertEquals(1, BeaconTiers.primaryAmplifier(primary, vanilla, true));
			}
		}
		assertEquals(2, BeaconTiers.primaryAmplifier(BeaconTiers.Primary.HASTE, Tier.CELESTIUM, true));
		assertEquals(0, BeaconTiers.primaryAmplifier(BeaconTiers.Primary.HASTE, Tier.CELESTIUM, false));
	}

	@Test
	void extrasNeedFourLevelsAndTheRightMetal() {
		assertFalse(BeaconTiers.unlocks(BeaconTiers.Extra.PROSPECTING, Tier.NETHERITE, 4));
		assertTrue(BeaconTiers.unlocks(BeaconTiers.Extra.PROSPECTING, Tier.INFERNITE, 4));
		assertFalse(BeaconTiers.unlocks(BeaconTiers.Extra.PROSPECTING, Tier.CELESTIUM, 3));
		assertFalse(BeaconTiers.unlocks(BeaconTiers.Extra.FLIGHT, Tier.ASTRALITE, 4));
		assertTrue(BeaconTiers.unlocks(BeaconTiers.Extra.FLIGHT, Tier.CELESTIUM, 4));
		assertFalse(BeaconTiers.unlocks(BeaconTiers.Extra.FLIGHT, null, 4));
	}

	@Test
	void encodeRoundTrips() {
		assertEquals(0, BeaconTiers.encode(null));
		assertNull(BeaconTiers.decode(0));
		for (Tier metal : BeaconTiers.METALS) {
			assertEquals(metal, BeaconTiers.decode(BeaconTiers.encode(metal)));
		}
		assertNull(BeaconTiers.decode(99));
	}
}
