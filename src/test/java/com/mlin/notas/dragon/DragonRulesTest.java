package com.mlin.notas.dragon;

import com.mlin.notas.boss.AttackScheduler;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonRulesTest {
	@Test
	void threePhasesByHealth() {
		assertEquals(1, DragonRules.phase(400, 400));
		assertEquals(1, DragonRules.phase(200, 400));
		assertEquals(2, DragonRules.phase(199, 400));
		assertEquals(2, DragonRules.phase(80, 400));
		assertEquals(3, DragonRules.phase(79, 400));
		assertEquals(1, DragonRules.phase(10, 0));
	}

	@Test
	void healthIsBuiltForTwoPlayers() {
		assertEquals(1000.0, DragonRules.maxHealth(1));
		assertEquals(1000.0, DragonRules.maxHealth(2));
		assertEquals(1500.0, DragonRules.maxHealth(3));
		assertEquals(2000.0, DragonRules.maxHealth(4));
		assertTrue(DragonRules.maxHealth(2) > 200.0, "mais forte que o vanilla");
	}

	@Test
	void bedAndAnchorExplosionsAreBlockedAroundThePortal() {
		assertTrue(DragonRules.guardsExplosionsAt(0, 0));
		assertTrue(DragonRules.guardsExplosionsAt(100, -100), "pilares e santuario");
		assertTrue(DragonRules.guardsExplosionsAt(150, 0));
		assertFalse(DragonRules.guardsExplosionsAt(120, 120), "fora do raio de 150");
		assertFalse(DragonRules.guardsExplosionsAt(1200, 40), "ilhas externas: cama explode como no vanilla");
	}

	@Test
	void rescaleKeepsTheFractionAndOnlyGoesUp() {
		assertEquals(400.0, DragonRules.rescaledHealth(200, 200, 400));
		assertEquals(300.0, DragonRules.rescaledHealth(200, 400, 600));
		assertEquals(1.0, DragonRules.rescaledHealth(0.1, 400, 600));
		assertTrue(DragonRules.shouldRescale(0, 1));
		assertTrue(DragonRules.shouldRescale(2, 3));
		assertFalse(DragonRules.shouldRescale(2, 1), "ate 2 jogadores a vida e a mesma");
		assertFalse(DragonRules.shouldRescale(3, 2), "jogador que sai nao enfraquece o dragao");
		assertFalse(DragonRules.shouldRescale(1, 2));
	}

	@Test
	void attacksUnlockByPhase() {
		AttackScheduler scheduler = DragonRules.scheduler();
		Set<String> p1 = ids(scheduler.available(1, 40));
		assertEquals(Set.of(DragonRules.METEORS, DragonRules.MITES), p1);
		Set<String> p2 = ids(scheduler.available(2, 40));
		assertTrue(p2.containsAll(Set.of(DragonRules.METEORS, DragonRules.MITES, DragonRules.DIVE, DragonRules.SENTINELS)));
		assertFalse(p2.contains(DragonRules.BREATH_VOLLEY));
		assertTrue(ids(scheduler.available(3, 40)).contains(DragonRules.BREATH_VOLLEY));
		// mergulho precisa de distancia para pegar embalo
		assertFalse(ids(scheduler.available(2, 5)).contains(DragonRules.DIVE));
	}

	@Test
	void schedulerRotatesAttacksInPhaseThree() {
		AttackScheduler scheduler = DragonRules.scheduler();
		Random random = new Random(3);
		Set<String> used = new HashSet<>();
		for (int t = 0; t < 4000; t++) {
			scheduler.tick();
			scheduler.next(3, 40, random).ifPresent(a -> used.add(a.id()));
		}
		assertEquals(5, used.size(), "todos os ataques aparecem: " + used);
	}

	private static Set<String> ids(List<AttackScheduler.Attack> attacks) {
		Set<String> out = new HashSet<>();
		attacks.forEach(a -> out.add(a.id()));
		return out;
	}

	@Test
	void meteorAndShockwaveAreFair() {
		assertEquals(8.0F, DragonRules.meteorFalloff(0, 8.0F));
		assertEquals(4.0F, DragonRules.meteorFalloff(DragonRules.METEOR_RADIUS, 8.0F));
		assertEquals(0.0F, DragonRules.meteorFalloff(DragonRules.METEOR_RADIUS + 0.1, 8.0F));
		assertTrue(DragonRules.METEOR_WARNING >= 30, "aviso de pelo menos 1,5 s");
		assertTrue(DragonRules.shockwaveHits(5.0, 5.2, 0.0));
		assertFalse(DragonRules.shockwaveHits(5.0, 5.2, 1.5), "pular por cima escapa");
		assertFalse(DragonRules.shockwaveHits(2.0, 5.2, 0.0), "fora do anel");
		assertTrue(DragonRules.meteorsPerPlayer(3) > DragonRules.meteorsPerPlayer(1));
	}

	@Test
	void addsRespectTheCap() {
		assertEquals(2, DragonRules.addsToSpawn(2, 0, DragonRules.MAX_MITES));
		assertEquals(1, DragonRules.addsToSpawn(4, 5, DragonRules.MAX_MITES));
		assertEquals(0, DragonRules.addsToSpawn(2, 3, DragonRules.MAX_SENTINELS));
		assertEquals(1, DragonRules.addsToSpawn(0, 0, DragonRules.MAX_SENTINELS));
	}

	@Test
	void regrownCrystalsGoOnTheLowestOpenPillars() {
		List<DragonRules.Pillar> pillars = List.of(
				new DragonRules.Pillar(42, 0, 2, 76, false, false),
				new DragonRules.Pillar(33, 24, 2, 79, true, false),
				new DragonRules.Pillar(12, 39, 3, 82, false, true),
				new DragonRules.Pillar(-13, 39, 4, 91, false, false),
				new DragonRules.Pillar(-34, 24, 5, 103, false, false));
		List<DragonRules.Pillar> targets = DragonRules.regrowTargets(pillars, DragonRules.REGROWN_CRYSTALS);
		assertEquals(2, targets.size());
		assertEquals(76, targets.get(0).height());
		assertEquals(91, targets.get(1).height(), "sem gaiola antes, o de 82 ainda tem cristal");
		assertEquals(0, DragonRules.regrowTargets(List.of(new DragonRules.Pillar(0, 0, 2, 76, false, true)), 2).size());
	}
}
