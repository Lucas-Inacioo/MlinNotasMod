package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyBoardTest {
	@Test
	void sameVillageAndDayGiveSameBounties() {
		assertEquals(BountyBoard.roll(7, 3, VillageLevel.HAMLET), BountyBoard.roll(7, 3, VillageLevel.HAMLET));
		Set<List<Bounty>> days = new HashSet<>();
		for (int day = 0; day < 10; day++) {
			days.add(BountyBoard.roll(7, day, VillageLevel.HAMLET));
		}
		assertTrue(days.size() > 5, "as missoes devem variar de um dia para o outro");
	}

	@Test
	void oneDeliveryOneHuntAndAnExplorationAfterCamp() {
		for (VillageLevel level : VillageLevel.values()) {
			for (int day = 0; day < 30; day++) {
				List<Bounty> bounties = BountyBoard.roll(99, day, level);
				assertEquals(BountyBoard.PER_DAY, bounties.size());
				assertEquals(Bounty.Kind.DELIVER, bounties.get(0).kind());
				assertEquals(Bounty.Kind.HUNT, bounties.get(1).kind());
				assertEquals(level == VillageLevel.CAMP ? Bounty.Kind.DELIVER : Bounty.Kind.EXPLORE, bounties.get(2).kind());
				assertNotEquals(bounties.get(0).target(), bounties.get(2).target());
				for (Bounty b : bounties) {
					assertTrue(b.count() >= 1 && b.emeralds() >= 1 && b.rewardCount() >= 1 && b.prosperity() >= 10, b.toString());
					if (b.kind() == Bounty.Kind.EXPLORE) {
						assertTrue(BountyBoard.EXPLORE_KEYS.contains(b.target()), b.target());
					}
				}
			}
		}
	}

	@Test
	void campNeverAsksForLateGameThings() {
		for (int day = 0; day < 200; day++) {
			for (Bounty b : BountyBoard.roll(5, day, VillageLevel.CAMP)) {
				assertTrue(!b.target().contains("blaze") && !b.target().contains("ender_pearl") && !b.target().contains("wither"), b.toString());
				assertTrue(!b.rewardItem().contains("totem") && !b.rewardItem().contains("netherite"), b.toString());
			}
		}
	}
}
