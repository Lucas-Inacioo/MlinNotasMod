package com.mlin.notas.variants;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobVariantsTest {
	@Test
	void eachVariantMatchesItsBiomes() {
		for (MobVariant v : MobVariant.values()) {
			for (String biome : v.biomes()) {
				assertEquals(Optional.of(v), MobVariants.pick(v.baseType(), biome, "NATURAL", 0.0), v + " em " + biome);
			}
		}
	}

	@Test
	void tableFromSpec() {
		assertEquals(Optional.of(MobVariant.MOSSY_ZOMBIE), MobVariants.candidate("minecraft:zombie", "minecraft:lush_caves"));
		assertEquals(Optional.of(MobVariant.SNOW_SPIDER), MobVariants.candidate("minecraft:spider", "minecraft:ice_spikes"));
		assertEquals(Optional.of(MobVariant.BLAZING_SKELETON), MobVariants.candidate("minecraft:skeleton", "minecraft:badlands"));
		assertEquals(Optional.of(MobVariant.CRYSTAL_CREEPER), MobVariants.candidate("minecraft:creeper", "minecraft:deep_dark"));
		assertEquals(Optional.of(MobVariant.MANGROVE_DROWNED), MobVariants.candidate("minecraft:drowned", "minecraft:mangrove_swamp"));
		assertEquals(Optional.of(MobVariant.ASHEN_WITHER_SKELETON), MobVariants.candidate("minecraft:wither_skeleton", "mlinnotas:ashen_plains"));
		assertEquals(Optional.of(MobVariant.ASHEN_WITHER_SKELETON), MobVariants.candidate("minecraft:wither_skeleton", "minecraft:basalt_deltas"));
	}

	@Test
	void wrongBiomeOrTypeGivesNothing() {
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:plains", "NATURAL", 0.0).isEmpty());
		assertTrue(MobVariants.pick("minecraft:husk", "minecraft:jungle", "NATURAL", 0.0).isEmpty());
		assertTrue(MobVariants.pick("minecraft:skeleton", "minecraft:jungle", "NATURAL", 0.0).isEmpty());
	}

	@Test
	void chanceIsSixtyPercent() {
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "NATURAL", 0.59).isPresent());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "NATURAL", 0.6).isEmpty());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "NATURAL", 0.99).isEmpty());
	}

	@Test
	void onlySpawnReasonsThatCount() {
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "SPAWNER", 0.0).isPresent());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "SPAWN_ITEM_USE", 0.0).isPresent());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "COMMAND", 0.0).isEmpty());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "STRUCTURE", 0.0).isEmpty());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "LOAD", 0.0).isEmpty());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", "CONVERSION", 0.0).isEmpty());
		assertTrue(MobVariants.pick("minecraft:zombie", "minecraft:jungle", null, 0.0).isEmpty());
	}

	@Test
	void idsAreUniqueAndRoundTrip() {
		Set<String> ids = new HashSet<>();
		for (MobVariant v : MobVariant.values()) {
			assertTrue(ids.add(v.id()));
			assertEquals(Optional.of(v), MobVariant.byId(v.id()));
			assertTrue(v.translationKey().startsWith("entity.mlinnotas.variant."));
		}
		assertTrue(MobVariant.byId("nope").isEmpty());
	}
}
