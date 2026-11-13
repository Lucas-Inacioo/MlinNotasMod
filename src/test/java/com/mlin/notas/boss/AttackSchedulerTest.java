package com.mlin.notas.boss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackSchedulerTest {
	private static AttackScheduler golem() {
		return new AttackScheduler(30, List.of(
				new AttackScheduler.Attack("slam", 80, 1, 0, 6, 3),
				new AttackScheduler.Attack("rock", 60, 1, 5, 24, 2),
				new AttackScheduler.Attack("charge", 120, 2, 4, 20, 3)));
	}

	private static void skip(AttackScheduler s, int ticks) {
		for (int i = 0; i < ticks; i++) {
			s.tick();
		}
	}

	@Test
	void waitsTheGlobalGapBeforeTheFirstAttack() {
		AttackScheduler s = golem();
		Random random = new Random(1);
		assertTrue(s.next(1, 3, random).isEmpty());
		skip(s, 30);
		assertEquals("slam", s.next(1, 3, random).orElseThrow().id());
		assertTrue(s.next(1, 3, random).isEmpty(), "pausa depois do ataque");
	}

	@Test
	void phaseTwoAttackNeverInPhaseOne() {
		AttackScheduler s = golem();
		Random random = new Random(2);
		for (int i = 0; i < 2000; i++) {
			s.tick();
			s.next(1, 10, random).ifPresent(a -> assertNotEquals("charge", a.id()));
		}
		AttackScheduler s2 = golem();
		boolean charged = false;
		for (int i = 0; i < 2000 && !charged; i++) {
			s2.tick();
			charged = s2.next(2, 10, random).map(a -> a.id().equals("charge")).orElse(false);
		}
		assertTrue(charged, "fase 2 usa a investida");
	}

	@Test
	void respectsRangeAndCooldown() {
		AttackScheduler s = golem();
		Random random = new Random(3);
		skip(s, 30);
		// longe: so a pedra serve
		Optional<AttackScheduler.Attack> first = s.next(1, 15, random);
		assertEquals("rock", first.orElseThrow().id());
		assertEquals(60, s.cooldown("rock"));
		skip(s, 30);
		assertTrue(s.next(1, 15, random).isEmpty(), "pedra ainda recarregando e o golpe nao alcanca");
		skip(s, 30);
		assertEquals("rock", s.next(1, 15, random).orElseThrow().id());
	}

	@Test
	void avoidsRepeatingWhenThereIsAnotherOption() {
		AttackScheduler s = new AttackScheduler(1, List.of(
				new AttackScheduler.Attack("a", 0, 1, 0, 10, 100),
				new AttackScheduler.Attack("b", 0, 1, 0, 10, 1)));
		Random random = new Random(4);
		String last = null;
		for (int i = 0; i < 200; i++) {
			s.tick();
			Optional<AttackScheduler.Attack> next = s.next(1, 1, random);
			if (next.isPresent()) {
				assertNotEquals(last, next.get().id());
				last = next.get().id();
			}
		}
	}

	@Test
	void phaseTwoAttacksMoreOften() {
		AttackScheduler s = golem();
		Random random = new Random(5);
		skip(s, 30);
		s.next(2, 3, random).orElseThrow();
		assertEquals(20, s.delay());
	}
}
