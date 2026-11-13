package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageStateTest {
	private static VillageState village() {
		return new VillageState("minecraft:overworld", 0, 64, 0, 42L, VillageStyle.PLAINS, "Teste");
	}

	@Test
	void partialDepositsAccumulateUntilProjectIsQueued() {
		VillageState v = village();
		VillageState.Deposit first = v.deposit(VillageProject.FARM, Map.of("#mlinnotas:village/food", 20));
		assertEquals(VillageState.Outcome.PARTIAL, first.outcome());
		assertEquals(Map.of("#mlinnotas:village/food", 20), first.taken());
		assertEquals(12, v.remaining(VillageProject.FARM, "#mlinnotas:village/food"));
		// so tira o que falta
		VillageState.Deposit second = v.deposit(VillageProject.FARM, Map.of("#mlinnotas:village/food", 64, "#mlinnotas:village/seeds", 64));
		assertEquals(VillageState.Outcome.COMPLETE, second.outcome());
		assertEquals(12, second.taken().get("#mlinnotas:village/food"));
		assertEquals(16, second.taken().get("#mlinnotas:village/seeds"));
		assertEquals(List.of(VillageProject.FARM), v.queue());
		assertEquals(0, v.deposited(VillageProject.FARM, "#mlinnotas:village/food"));
		// no Acampamento cabe 1 plantacao: a da fila ja conta
		assertEquals(VillageState.Status.MAXED, v.status(VillageProject.FARM));
		assertEquals(VillageState.Outcome.MAXED, v.deposit(VillageProject.FARM, Map.of("#mlinnotas:village/food", 5)).outcome());
	}

	@Test
	void nothingAndLockedAreRefusedWithoutChanges() {
		VillageState v = village();
		assertEquals(VillageState.Outcome.NOTHING, v.deposit(VillageProject.HOUSE, Map.of()).outcome());
		VillageState.Deposit locked = v.deposit(VillageProject.SMITHY, Map.of("minecraft:iron_ingot", 64));
		assertEquals(VillageState.Outcome.LOCKED, locked.outcome());
		assertTrue(locked.taken().isEmpty());
		assertEquals(0, v.deposited(VillageProject.SMITHY, "minecraft:iron_ingot"));
	}

	@Test
	void levelNeedsProsperityProjectsAndRequiredBuildings() {
		VillageState v = village();
		v.addBuilt(VillageProject.HOUSE);
		v.addBuilt(VillageProject.HOUSE);
		v.addBuilt(VillageProject.FARM);
		assertNull(v.addProsperity(200), "sem a praca nao vira Aldeia");
		assertEquals(VillageLevel.CAMP, v.level());
		v.enqueue(VillageProject.PLAZA);
		assertEquals(VillageLevel.HAMLET, v.finishBuild(VillageProject.PLAZA));
		assertEquals(240, v.prosperity());
		assertTrue(v.queue().isEmpty());
		// Vila exige forja e plantacao + 7 obras + 250
		v.addBuilt(VillageProject.SMITHY);
		v.addBuilt(VillageProject.LAMPS);
		v.addBuilt(VillageProject.HOUSE);
		assertEquals(VillageLevel.VILLAGE, v.addProsperity(20));
		assertEquals(4, v.level().tradeCap());
		assertEquals(1, v.level().discount());
	}

	@Test
	void levelNeverGoesDown() {
		VillageState v = village();
		v.setLevel(VillageLevel.TOWN);
		assertNull(v.recompute());
		assertEquals(VillageLevel.TOWN, v.level());
	}

	@Test
	void maxBuildsGrowWithLevel() {
		VillageState v = village();
		assertEquals(2, VillageProject.HOUSE.maxBuilds(VillageLevel.CAMP));
		v.addBuilt(VillageProject.HOUSE);
		v.addBuilt(VillageProject.HOUSE);
		assertEquals(VillageState.Status.MAXED, v.status(VillageProject.HOUSE));
		v.setLevel(VillageLevel.HAMLET);
		assertEquals(VillageState.Status.AVAILABLE, v.status(VillageProject.HOUSE));
		for (VillageProject p : VillageProject.values()) {
			for (VillageLevel level : VillageLevel.values()) {
				if (level.next() != null) {
					assertTrue(p.maxBuilds(level.next()) >= p.maxBuilds(level), p + " nao pode diminuir ao subir");
				}
			}
			assertTrue(p.maxBuilds(p.minLevel()) >= 1, p + " precisa caber no nivel minimo");
		}
	}

	@Test
	void bountiesRefreshOncePerDayAndPay() {
		VillageState v = village();
		assertTrue(v.refreshBounties(5));
		assertFalse(v.refreshBounties(5));
		assertEquals(BountyBoard.PER_DAY, v.bounties().size());
		Bounty deliver = v.bounties().getFirst();
		assertEquals(Bounty.Kind.DELIVER, deliver.kind());
		VillageState.BountyResult partial = v.claim(0, 1);
		assertEquals(VillageState.BountyOutcome.PARTIAL, partial.outcome());
		assertEquals(1, partial.taken());
		VillageState.BountyResult paid = v.claim(0, 999);
		assertEquals(VillageState.BountyOutcome.PAID, paid.outcome());
		assertEquals(deliver.count() - 1, paid.taken());
		assertEquals(VillageState.BountyOutcome.INVALID, v.claim(0, 999).outcome());
		// cacada so paga quando completa
		Bounty hunt = v.bounties().get(1);
		assertEquals(VillageState.BountyOutcome.NOT_READY, v.claim(1, 0).outcome());
		List<Integer> ready = List.of();
		for (int i = 0; i < hunt.count(); i++) {
			ready = v.progress(Bounty.Kind.HUNT, hunt.target(), 1);
		}
		assertEquals(List.of(1), ready);
		assertTrue(v.bountyReady(1));
		assertEquals(VillageState.BountyOutcome.PAID, v.claim(1, 0).outcome());
		// dia novo: tudo de novo
		assertTrue(v.refreshBounties(6));
		assertFalse(v.bountyDone(0));
		assertEquals(0, v.bountyProgress(1));
	}

	@Test
	void huntProgressIgnoresOtherTargets() {
		VillageState v = village();
		v.refreshBounties(1);
		Bounty hunt = v.bounties().get(1);
		v.progress(Bounty.Kind.HUNT, "minecraft:pig_that_does_not_exist", 50);
		assertEquals(0, v.bountyProgress(1));
		v.progress(Bounty.Kind.HUNT, hunt.target(), 1000);
		assertEquals(hunt.count(), v.bountyProgress(1), "progresso nao passa do pedido");
	}

	@Test
	void shopRepairPriceScalesWithWear() {
		assertEquals(0, VillageShop.repairPrice(0, 250));
		assertEquals(1, VillageShop.repairPrice(1, 250));
		assertEquals(3, VillageShop.repairPrice(125, 250));
		assertEquals(5, VillageShop.repairPrice(249, 250));
		for (VillageShop.Offer offer : VillageShop.OFFERS) {
			assertTrue(offer.price() > 0);
			assertNotEquals(null, VillageShop.byId(offer.id()));
		}
	}
}
