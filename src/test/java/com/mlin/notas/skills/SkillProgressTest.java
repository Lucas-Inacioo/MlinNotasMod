package com.mlin.notas.skills;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillProgressTest {
	private static final SkillNode STONE = SkillTree.node(SkillBranch.MINING, Tier.STONE.level());
	private static final SkillNode IRON = SkillTree.node(SkillBranch.MINING, Tier.IRON.level());

	@Test
	void newWorldStartsWithWoodOnly() {
		SkillProgress progress = new SkillProgress();
		assertEquals(1, progress.level(SkillBranch.MINING));
		assertEquals(0, progress.level(SkillBranch.COMBAT));
		assertEquals(SkillProgress.Status.AVAILABLE, progress.status(STONE));
		assertEquals(SkillProgress.Status.LOCKED, progress.status(IRON));
		assertEquals(SkillProgress.Status.LOCKED, progress.status(SkillTree.node(SkillBranch.VIGOR, 1)));
	}

	@Test
	void partialDepositsAccumulateUntilUnlock() {
		SkillProgress progress = new SkillProgress();
		SkillProgress.Deposit first = progress.deposit(STONE, Map.of("minecraft:cobblestone", 20), 0);
		assertEquals(SkillProgress.Outcome.PARTIAL, first.outcome());
		assertEquals(Map.of("minecraft:cobblestone", 20), first.taken());
		assertEquals(12, progress.remaining(STONE, "minecraft:cobblestone"));
		assertEquals(1, progress.level(SkillBranch.MINING));

		// segundo jogador tem 64: so leva os 12 que faltam
		SkillProgress.Deposit second = progress.deposit(STONE, Map.of("minecraft:cobblestone", 64), 30);
		assertEquals(SkillProgress.Outcome.UNLOCKED, second.outcome());
		assertEquals(Map.of("minecraft:cobblestone", 12), second.taken());
		assertEquals(0, second.xpTaken(), "Pedra nao custa XP");
		assertEquals(Tier.STONE.level(), progress.level(SkillBranch.MINING));
		assertEquals(0, progress.deposited(STONE, "minecraft:cobblestone"), "deposito limpo ao liberar");
		assertEquals(SkillProgress.Status.UNLOCKED, progress.status(STONE));
	}

	@Test
	void xpIsDepositedToo() {
		SkillProgress progress = new SkillProgress();
		progress.setLevel(SkillBranch.MINING, Tier.COPPER.level());
		SkillProgress.Deposit onlyXp = progress.deposit(IRON, Map.of(), 2);
		assertEquals(SkillProgress.Outcome.PARTIAL, onlyXp.outcome());
		assertEquals(2, onlyXp.xpTaken());
		assertEquals(1, progress.remainingXp(IRON));
		SkillProgress.Deposit items = progress.deposit(IRON, Map.of(SkillTree.COPPER, 10), 0);
		assertEquals(SkillProgress.Outcome.PARTIAL, items.outcome(), "ainda falta 1 nivel");
		assertFalse(progress.complete(IRON));
		SkillProgress.Deposit done = progress.deposit(IRON, Map.of(SkillTree.COPPER, 5), 7);
		assertEquals(SkillProgress.Outcome.UNLOCKED, done.outcome());
		assertTrue(done.taken().isEmpty(), "cobre ja estava completo");
		assertEquals(1, done.xpTaken());
	}

	@Test
	void refusesLockedUnlockedAndEmptyDeposits() {
		SkillProgress progress = new SkillProgress();
		assertEquals(SkillProgress.Outcome.LOCKED, progress.deposit(IRON, Map.of(SkillTree.COPPER, 10), 10).outcome());
		assertEquals(0, progress.deposited(IRON, SkillTree.COPPER), "recusado nao muda nada");
		assertEquals(SkillProgress.Outcome.NOTHING, progress.deposit(STONE, Map.of("minecraft:dirt", 64), 0).outcome());
		progress.setLevel(SkillBranch.MINING, Tier.STONE.level());
		assertEquals(SkillProgress.Outcome.ALREADY_UNLOCKED, progress.deposit(STONE, Map.of("minecraft:cobblestone", 64), 0).outcome());
	}

	@Test
	void sideBranchNeedsMiningLevel() {
		SkillProgress progress = new SkillProgress();
		SkillNode combat1 = SkillTree.node(SkillBranch.COMBAT, 1);
		assertEquals(SkillProgress.Outcome.LOCKED, progress.deposit(combat1, Map.of("minecraft:copper_ingot", 16), 5).outcome());
		progress.setLevel(SkillBranch.MINING, Tier.COPPER.level());
		assertEquals(SkillProgress.Outcome.UNLOCKED, progress.deposit(combat1, Map.of("minecraft:copper_ingot", 16), 5).outcome());
		assertEquals(1, progress.level(SkillBranch.COMBAT));
	}

	@Test
	void roundTripsThroughSavedForm() {
		SkillProgress progress = new SkillProgress();
		progress.setLevel(SkillBranch.MINING, Tier.COPPER.level());
		progress.deposit(IRON, Map.of(SkillTree.COPPER, 4), 1);
		SkillProgress copy = SkillProgress.of(progress.levelsById(), progress.itemDeposits(), progress.xpDeposits());
		assertEquals(Tier.COPPER.level(), copy.level(SkillBranch.MINING));
		assertEquals(4, copy.deposited(IRON, SkillTree.COPPER));
		assertEquals(1, copy.depositedXp(IRON));
		// lixo e ignorado/limitado
		SkillProgress junk = SkillProgress.of(Map.of("mining", 99, "nope", 3), Map.of("nope/1", Map.of("x", 1)), Map.of("mining/3", -2));
		assertEquals(12, junk.level(SkillBranch.MINING));
		assertTrue(junk.itemDeposits().isEmpty());
		assertTrue(junk.xpDeposits().isEmpty());
		junk.clear();
		assertEquals(1, junk.level(SkillBranch.MINING));
	}
}
