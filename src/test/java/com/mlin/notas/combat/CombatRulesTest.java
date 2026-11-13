package com.mlin.notas.combat;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatRulesTest {
	// ---------------------------------------------------------------- geometria

	@Test
	void lookFollowsMinecraftYaw() {
		double[] south = ArcGeometry.look(0.0F);
		assertEquals(0.0, south[0], 1.0E-9);
		assertEquals(1.0, south[1], 1.0E-9);
		double[] west = ArcGeometry.look(90.0F);
		assertEquals(-1.0, west[0], 1.0E-9);
	}

	@Test
	void greatswordArcCatchesThreeTargetsInFrontButNotBehind() {
		// jogador na origem olhando para +z (yaw 0), alcance 3,5, arco de 160 graus
		double r = 3.5;
		assertTrue(ArcGeometry.inArc(0, 0, 0, 0.0F, 0.0, 0, 2.0, 0.3, r, 160));
		assertTrue(ArcGeometry.inArc(0, 0, 0, 0.0F, -1.5, 0, 1.5, 0.3, r, 160));
		assertTrue(ArcGeometry.inArc(0, 0, 0, 0.0F, 1.5, 0, 1.5, 0.3, r, 160));
		// atras, longe demais ou muito acima nao entram
		assertFalse(ArcGeometry.inArc(0, 0, 0, 0.0F, 0.0, 0, -2.0, 0.3, r, 160));
		assertFalse(ArcGeometry.inArc(0, 0, 0, 0.0F, 0.0, 0, 5.0, 0.3, r, 160));
		assertFalse(ArcGeometry.inArc(0, 0, 0, 0.0F, 0.0, 3.5, 2.0, 0.3, r, 160));
	}

	@Test
	void narrowerGlaiveArcRejectsTheFlanks() {
		// 70 graus para o lado: dentro dos 160 do montante, fora dos 100 da glaive
		double x = Math.sin(Math.toRadians(70)) * 3.0;
		double z = Math.cos(Math.toRadians(70)) * 3.0;
		assertTrue(ArcGeometry.inArc(0, 0, 0, 0.0F, x, 0, z, 0.0, 4.5, 160));
		assertFalse(ArcGeometry.inArc(0, 0, 0, 0.0F, x, 0, z, 0.0, 4.5, 100));
	}

	@Test
	void arcRotatesWithYaw() {
		// olhando para -x (yaw 90): alvo em -x entra, em +x nao
		assertTrue(ArcGeometry.inArc(0, 0, 0, 90.0F, -2.0, 0, 0.0, 0.3, 3.0, 100));
		assertFalse(ArcGeometry.inArc(0, 0, 0, 90.0F, 2.0, 0, 0.0, 0.3, 3.0, 100));
	}

	@Test
	void shockwaveRadiusCountsTheTargetEdge() {
		assertTrue(ArcGeometry.inRadius(0, 0, 0, 2.7, 0, 0, 0.3, 2.5));
		assertFalse(ArcGeometry.inRadius(0, 0, 0, 3.2, 0, 0, 0.3, 2.5));
		assertFalse(ArcGeometry.inRadius(0, 0, 0, 1.0, 3.0, 0, 0.3, 2.5));
	}

	@Test
	void thrustOnlyHitsTheOneRightBehind() {
		// jogador olhando para +z, primeiro alvo em z=2
		assertTrue(ArcGeometry.behindInLine(0, 0, 0.0F, 0, 2, 0.2, 3.5, 2.5, 0.9));
		assertFalse(ArcGeometry.behindInLine(0, 0, 0.0F, 0, 2, 0, 1.0, 2.5, 0.9));
		assertFalse(ArcGeometry.behindInLine(0, 0, 0.0F, 0, 2, 2.0, 3.0, 2.5, 0.9));
		assertFalse(ArcGeometry.behindInLine(0, 0, 0.0F, 0, 2, 0, 6.0, 2.5, 0.9));
	}

	@Test
	void backstabNeedsTheAttackerBehindTheTarget() {
		// alvo olhando para +z (yaw 0) na origem
		assertTrue(ArcGeometry.behind(0.0F, 0, 0, 0, -2));
		assertTrue(ArcGeometry.behind(0.0F, 0, 0, 0.6, -2));
		assertFalse(ArcGeometry.behind(0.0F, 0, 0, 0, 2));
		assertFalse(ArcGeometry.behind(0.0F, 0, 0, 2, 0));
		// alvo virado para -x: atacante em +x esta nas costas
		assertTrue(ArcGeometry.behind(90.0F, 0, 0, 2, 0));
	}

	// ---------------------------------------------------------------- combo

	@Test
	void threeTimedHitsEndInAFinisherAndLoop() {
		int cooldown = 22;
		ComboRules.State state = null;
		long t = 100;
		int[] expected = {0, 1, 2, 0, 1};
		for (int step : expected) {
			state = ComboRules.next(state, "greatsword", t, cooldown, 1.0F);
			assertEquals(step, state.step());
			t += cooldown + 2;
		}
		assertFalse(state.finisher());
		state = ComboRules.next(state, "greatsword", t, cooldown, 1.0F);
		assertTrue(state.finisher());
	}

	@Test
	void comboBreaksOnWeakHitDelayOrWeaponSwap() {
		int cooldown = 10;
		ComboRules.State state = ComboRules.next(null, "dagger", 0, cooldown, 1.0F);
		state = ComboRules.next(state, "dagger", 10, cooldown, 1.0F);
		assertEquals(1, state.step());
		// demorou mais que recarga + folga
		ComboRules.State late = ComboRules.next(state, "dagger", 10 + cooldown + ComboRules.GRACE_TICKS + 1, cooldown, 1.0F);
		assertEquals(0, late.step());
		// golpe fraco zera e o proximo comeca do 0
		ComboRules.State weak = ComboRules.next(state, "dagger", 15, cooldown, 0.5F);
		assertEquals(-1, weak.step());
		assertEquals(0, ComboRules.next(weak, "dagger", 25, cooldown, 1.0F).step());
		// trocar de arma zera
		assertEquals(0, ComboRules.next(state, "rapier", 20, cooldown, 1.0F).step());
		assertTrue(ComboRules.alive(state, 10 + cooldown + ComboRules.GRACE_TICKS, cooldown));
		assertFalse(ComboRules.alive(state, 10 + cooldown + ComboRules.GRACE_TICKS + 1, cooldown));
	}

	// ---------------------------------------------------------------- esquiva

	@Test
	void dodgeHasCooldownHungerAndGroundRules() {
		long last = 1000;
		assertEquals(DodgeRules.Check.COOLDOWN, DodgeRules.check(last + DodgeRules.COOLDOWN_TICKS - 1, last, 20, true, false));
		assertEquals(DodgeRules.Check.OK, DodgeRules.check(last + DodgeRules.COOLDOWN_TICKS, last, 20, true, false));
		assertEquals(DodgeRules.Check.HUNGRY, DodgeRules.check(5000, last, DodgeRules.MIN_FOOD - 1, true, false));
		assertEquals(DodgeRules.Check.OK, DodgeRules.check(5000, last, 0, true, true), "criativo nao gasta fome");
		assertEquals(DodgeRules.Check.AIRBORNE, DodgeRules.check(5000, last, 20, false, false));
	}

	@Test
	void dodgeGivesAShortInvulnerabilityWindow() {
		long last = 50;
		assertTrue(DodgeRules.invulnerable(50, last));
		assertTrue(DodgeRules.invulnerable(50 + DodgeRules.INVULNERABLE_TICKS - 1, last));
		assertFalse(DodgeRules.invulnerable(50 + DodgeRules.INVULNERABLE_TICKS, last));
		assertFalse(DodgeRules.invulnerable(49, last));
		assertTrue(DodgeRules.INVULNERABLE_TICKS < DodgeRules.COOLDOWN_TICKS, "nao da para ficar invulneravel sempre");
		assertEquals(0.5F, DodgeRules.cooldownProgress(last + DodgeRules.COOLDOWN_TICKS / 2, last), 0.05F);
	}

	@Test
	void dodgeDirectionFollowsInputAndDefaultsBackwards() {
		// yaw 0 olha para +z: sem entrada vai para tras (-z), frente vai para +z, esquerda (strafe +) vai para +x
		double[] back = DodgeRules.direction(0.0F, 0.0F, 0.0F);
		assertEquals(-1.0, back[1], 1.0E-9);
		double[] forward = DodgeRules.direction(0.0F, 1.0F, 0.0F);
		assertEquals(1.0, forward[1], 1.0E-9);
		double[] left = DodgeRules.direction(1.0F, 0.0F, 0.0F);
		assertEquals(1.0, left[0], 1.0E-9);
		double[] diagonal = DodgeRules.direction(1.0F, 1.0F, 0.0F);
		assertEquals(1.0, Math.hypot(diagonal[0], diagonal[1]), 1.0E-9);
	}

	// ---------------------------------------------------------------- armas de chefe

	@Test
	void bossWeaponsAreAtMostOneTierAboveWhereTheyDrop() {
		// nivel de equipamento com que cada chefe e enfrentado (tabela de progressao)
		Map<BossWeapon, Tier> fightTier = Map.of(
				BossWeapon.GOLEM_HAMMER, Tier.GOLD,
				BossWeapon.ABYSSAL_BLADE, Tier.DIAMOND,
				BossWeapon.IGNEOUS_GREATSWORD, Tier.NETHERITE,
				BossWeapon.DRAGON_FANGS, Tier.INFERNITE,
				BossWeapon.ASTRAL_LANCE, Tier.ENDITE);
		for (BossWeapon weapon : BossWeapon.values()) {
			Tier fight = fightTier.get(weapon);
			assertTrue(weapon.powerTier().ordinal() <= fight.ordinal() + 1, weapon.name());
			float classDps = WeaponStats.of(weapon.weaponClass(), fight.next()).dps();
			assertTrue(weapon.stats().dps() <= classDps + 1.0E-4, weapon.name());
			assertTrue(weapon.procCooldownTicks() >= BossWeapon.MIN_PROC_COOLDOWN, "efeito no maximo 1/s: " + weapon);
		}
	}

	@Test
	void bossProcsRespectCooldownAndTrigger() {
		BossWeapon fangs = BossWeapon.DRAGON_FANGS;
		assertFalse(fangs.canProc(100, 90, true, true, false), "recarga");
		assertTrue(fangs.canProc(100, 80, true, true, false));
		assertTrue(fangs.canProc(100, 80, true, false, true), "pelas costas tambem");
		assertFalse(fangs.canProc(100, 80, true, false, false));
		assertFalse(fangs.canProc(100, 0, false, true, true), "golpe fraco nunca");
		assertFalse(BossWeapon.IGNEOUS_GREATSWORD.canProc(1000, 0, true, false, false), "onda de fogo so no golpe final");
		assertTrue(BossWeapon.ASTRAL_LANCE.canProc(1000, 0, true, false, false));
		assertEquals(BossWeapon.GOLEM_HAMMER, BossWeapon.byId("golem_hammer"));
	}
}
