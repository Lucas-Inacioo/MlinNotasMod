package com.mlin.notas.waila;

import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WailaRulesTest {
	@Test
	void requiredTierFollowsTheModLadder() {
		assertEquals(Tier.WOOD, WailaRules.requiredTier(Set.of("minecraft:mineable/pickaxe")::contains));
		assertEquals(Tier.STONE, WailaRules.requiredTier(Set.of("minecraft:needs_stone_tool")::contains));
		assertEquals(Tier.IRON, WailaRules.requiredTier(Set.of("minecraft:needs_iron_tool")::contains));
		assertEquals(Tier.COBALT, WailaRules.requiredTier(Set.of("mlinnotas:needs_cobalt_tool")::contains));
		assertEquals(Tier.DIAMOND, WailaRules.requiredTier(Set.of("minecraft:needs_diamond_tool")::contains));
		assertEquals(Tier.NETHERITE, WailaRules.requiredTier(Set.of("mlinnotas:needs_netherite_tool")::contains));
		assertEquals(Tier.INFERNITE, WailaRules.requiredTier(Set.of("mlinnotas:needs_infernite_tool")::contains));
		assertEquals(Tier.ENDITE, WailaRules.requiredTier(Set.of("mlinnotas:needs_endite_tool")::contains));
		assertEquals(Tier.ASTRALITE, WailaRules.requiredTier(Set.of("mlinnotas:needs_astralite_tool")::contains));
	}

	@Test
	void mostDemandingTagWins() {
		assertEquals(Tier.COBALT, WailaRules.requiredTier(Set.of("minecraft:needs_iron_tool", "mlinnotas:needs_cobalt_tool")::contains));
		assertTrue(WailaRules.requirementTags().contains("minecraft:needs_diamond_tool"));
	}

	@Test
	void toolKindFromMineableTags() {
		assertEquals(GearKind.PICKAXE, WailaRules.toolKind(Set.of("minecraft:mineable/pickaxe")::contains));
		assertEquals(GearKind.AXE, WailaRules.toolKind(Set.of("minecraft:mineable/axe")::contains));
		assertEquals(GearKind.SHOVEL, WailaRules.toolKind(Set.of("minecraft:mineable/shovel")::contains));
		assertEquals(GearKind.HOE, WailaRules.toolKind(Set.of("minecraft:mineable/hoe")::contains));
		assertNull(WailaRules.toolKind(Set.<String>of()::contains));
	}

	@Test
	void growthPercent() {
		assertEquals(0, WailaRules.growthPercent(0, 7));
		assertEquals(57, WailaRules.growthPercent(4, 7));
		assertEquals(100, WailaRules.growthPercent(7, 7));
		assertEquals(50, WailaRules.growthPercent(1, 2));
		assertEquals(100, WailaRules.growthPercent(9, 7));
	}

	@Test
	void reachIsEightBlocks() {
		assertTrue(WailaRules.withinReach(64.0));
		assertFalse(WailaRules.withinReach(64.1));
	}

	@Test
	void heartsSplitHalves() {
		assertArrayEquals(new int[] {10, 0}, WailaRules.hearts(20.0F, 10));
		assertArrayEquals(new int[] {3, 1}, WailaRules.hearts(7.0F, 10));
		assertArrayEquals(new int[] {3, 1}, WailaRules.hearts(6.5F, 10));
		assertArrayEquals(new int[] {0, 0}, WailaRules.hearts(0.0F, 10));
		assertArrayEquals(new int[] {5, 0}, WailaRules.hearts(30.0F, 5));
	}
}
