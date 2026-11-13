package com.mlin.notas.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossRulesTest {
	@Test
	void phaseTwoStartsBelowHalfHealth() {
		assertEquals(1, BossRules.phase(300, 300));
		assertEquals(1, BossRules.phase(150, 300));
		assertEquals(2, BossRules.phase(149.9, 300));
		assertEquals(2, BossRules.phase(1, 300));
	}

	@Test
	void healthIsTunedForTwoPlayersAndGrowsHalfPerExtraPlayer() {
		assertEquals(1.0, BossRules.healthMultiplier(0));
		assertEquals(1.0, BossRules.healthMultiplier(1));
		assertEquals(1.0, BossRules.healthMultiplier(2));
		assertEquals(1.5, BossRules.healthMultiplier(3));
		assertEquals(2.0, BossRules.healthMultiplier(4));
		assertEquals(1800.0, BossRules.scaledHealth(1200.0, 3));
	}

	@Test
	void dragonDropsMoreScalesOnTheFirstKill() {
		assertEquals(2, BossRules.dragonScales(0, true));
		assertEquals(4, BossRules.dragonScales(2, true));
		assertEquals(4, BossRules.dragonScales(9, true));
		assertEquals(2, BossRules.dragonScales(-1, true));
		assertEquals(1, BossRules.dragonScales(0, false), "reinvocado: 1 a 2");
		assertEquals(2, BossRules.dragonScales(2, false));
		assertEquals(1, BossRules.dragonScales(-1, false));
	}

	/** Numeros da curva de progressao (plano de ritmo: cada chefe 2-4 min com o equipamento do nivel). */
	@Test
	void bossStatsFollowTheProgressionCurve() {
		assertEquals(1200.0, BossKind.COBALT_GOLEM.baseHealth());
		assertEquals(12.0, BossKind.COBALT_GOLEM.armor());
		assertEquals(11.0, BossKind.COBALT_GOLEM.melee());
		assertEquals(16.0F, BossRules.GOLEM_CHARGE);
		assertEquals(1600.0, BossKind.ABYSSAL_GUARDIAN.baseHealth());
		assertEquals(2.0, BossKind.ABYSSAL_GUARDIAN.toughness());
		assertEquals(1800.0, BossKind.IGNEOUS_COLOSSUS.baseHealth());
		assertEquals(20.0, BossKind.IGNEOUS_COLOSSUS.melee());
		assertEquals(2000.0, BossKind.ASTRAL_HERALD.baseHealth());
		assertEquals(11.0F, BossRules.HERALD_STAR);
		assertEquals(900.0, BossRules.WITHER_HEALTH);
		// cada chefe tem mais vida que o anterior e cabe no teto do atributo mesmo com 8 jogadores
		double previous = 0;
		for (BossKind kind : BossKind.values()) {
			assertTrue(kind.baseHealth() > previous, kind + " mais forte que o anterior");
			previous = kind.baseHealth();
			assertTrue(BossRules.scaledHealth(kind.baseHealth(), 8) <= BossRules.MAX_HEALTH_CAP);
			assertTrue(kind.armor() <= 30.0, "armadura cabe no atributo");
		}
	}

	@Test
	void fireWaveExpandsAndHitsOnlyItsRing() {
		assertEquals(0.0, BossRules.waveRadius(0, 20, 10.0));
		assertEquals(5.0, BossRules.waveRadius(10, 20, 10.0));
		assertEquals(10.0, BossRules.waveRadius(40, 20, 10.0));
		assertTrue(BossRules.inRing(5.4, 5.0, 1.5));
		assertFalse(BossRules.inRing(3.0, 5.0, 1.5));
	}

	@Test
	void slamPushesHardestInTheMiddle() {
		assertTrue(BossRules.slamKnockback(1.0, 6.0, 2.0) > BossRules.slamKnockback(5.0, 6.0, 2.0));
		assertEquals(0.0, BossRules.slamKnockback(6.0, 6.0, 2.0));
	}

	@Test
	void everyBossButTheColossusHasAnAltarAndADrop() {
		for (BossKind kind : BossKind.values()) {
			assertNotNull(kind.drop());
			assertTrue(kind.xp() > 0 && kind.baseHealth() > 100);
			if (kind == BossKind.IGNEOUS_COLOSSUS) {
				assertNull(kind.offering());
				assertEquals("mlinnotas:forge_key", kind.wakeItem(), "o colosso so acorda com a Chave da Forja");
				assertTrue(kind.sleeps());
			} else {
				assertTrue(kind.hasAltar());
				assertEquals(4, kind.offeringCount());
				assertEquals(kind == BossKind.ASTRAL_HERALD ? "mlinnotas:dragon_scale" : null, kind.extraOffering(), "o Arauto pede tambem uma escama");
				assertFalse(kind.sleeps());
			}
		}
	}
}
