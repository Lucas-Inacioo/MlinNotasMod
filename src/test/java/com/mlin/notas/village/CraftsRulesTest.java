package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras das construcoes de oficios e servicos (Vilas 2.0, fase 6): numeros de pedreira, curtume, encantador, igreja, taverna e feira. */
class CraftsRulesTest {
	@Test
	void tanningGivesOneLeatherPerTwoFleshAtLevelOneAndOneToOneAtLevelTwo() {
		assertEquals(32, CraftsRules.tanned(64, 1));
		assertEquals(3, CraftsRules.tanned(5, 1), "arredonda para cima");
		assertEquals(64, CraftsRules.tanned(64, 2));
		assertEquals(0, CraftsRules.tanned(0, 2));
		assertEquals(12000, CraftsRules.TANNING_TICKS, "meio dia de jogo");
		assertEquals(8, CraftsRules.VAT_SLOTS);
	}

	@Test
	void quarryFillsFasterWithLevelAndOnlyEmitsStoneKinds() {
		assertTrue(CraftsRules.quarryPerDay(1) < CraftsRules.quarryPerDay(2) && CraftsRules.quarryPerDay(2) < CraftsRules.quarryPerDay(3));
		assertEquals(24000L, (long) CraftsRules.quarryPeriod(1) * CraftsRules.quarryPerDay(1) / 1, "o periodo divide o dia");
		// um dia inteiro rende exatamente os itens do dia
		for (int level = 1; level <= 3; level++) {
			assertEquals(CraftsRules.quarryPerDay(level), CraftsRules.quarryDue(level, 0, 24000), "nivel " + level);
			assertEquals(CraftsRules.quarryPerDay(level) * 2, CraftsRules.quarryDue(level, 5000, 5000 + 48000), "dois dias, nivel " + level);
		}
		assertEquals(0, CraftsRules.quarryDue(1, 100, 100));
		boolean deepslate = false;
		for (int roll = 0; roll < 100; roll++) {
			String id = CraftsRules.quarryDrop(1, roll);
			assertTrue(id.equals("minecraft:stone") || id.equals("minecraft:cobblestone") || id.equals("minecraft:stone_bricks"), id);
			deepslate |= CraftsRules.quarryDrop(3, roll).equals("minecraft:cobbled_deepslate");
		}
		assertTrue(deepslate, "o nivel III traz ardosia");
		assertTrue(CraftsRules.quarryFillCredit(0) == 0 && CraftsRules.quarryFillCredit(1) < CraftsRules.quarryFillCredit(3));
	}

	@Test
	void quarryDiscountLowersTheRingFillBill() {
		// RingCost cobra 1 item a cada FILL_PER_ITEM blocos de aterro que o corte e o estoque nao cobrem: o credito entra no estoque
		int fill = 500;
		int plain = (int) Math.ceil(Math.max(0, fill - 0) / (double) RingWork.FILL_PER_ITEM);
		int withQuarry = (int) Math.ceil(Math.max(0, fill - CraftsRules.quarryFillCredit(3)) / (double) RingWork.FILL_PER_ITEM);
		assertTrue(withQuarry < plain, "pedreira III: " + withQuarry + " < " + plain);
	}

	@Test
	void shopPricesGetCheaperWithTailorAndFletcherLevels() {
		assertEquals(18, CraftsRules.price("backpack", 18, Map.of("tailor", 1)));
		assertEquals(13, CraftsRules.price("backpack", 18, Map.of("tailor", 2)));
		assertEquals(8, CraftsRules.price("tipped_harming", 9, Map.of("fletcher", 2)));
		assertEquals(7, CraftsRules.price("tipped_harming", 9, Map.of("fletcher", 3)), "III baixa mais um");
		assertEquals(5, CraftsRules.price("tipped_poison", 6, Map.of("fletcher", 2)));
		assertEquals(4, CraftsRules.price("tipped_poison", 6, Map.of("fletcher", 3)));
		assertEquals(3, CraftsRules.price("tipped_poison", 3, Map.of("fletcher", 3)), "nunca abaixo de 3");
		assertEquals(10, CraftsRules.price("bread", 10, Map.of("tailor", 2)), "outras ofertas nao mudam");
	}

	@Test
	void enchanterLevelCapsEnchantLevelsOnTopOfMining() {
		com.mlin.notas.tier.Tier astralite = com.mlin.notas.tier.Tier.ASTRALITE;
		// mundo no maximo, casa nivel I: so I; casa V: sem limite
		assertEquals(1, com.mlin.notas.enchanting.EnchantRules.miningCap(CraftsRules.effectiveMining(astralite.level(), 1)));
		assertEquals(2, com.mlin.notas.enchanting.EnchantRules.miningCap(CraftsRules.effectiveMining(astralite.level(), 2)));
		assertEquals(3, com.mlin.notas.enchanting.EnchantRules.miningCap(CraftsRules.effectiveMining(astralite.level(), 3)));
		assertEquals(4, com.mlin.notas.enchanting.EnchantRules.miningCap(CraftsRules.effectiveMining(astralite.level(), 4)));
		assertEquals(Integer.MAX_VALUE, com.mlin.notas.enchanting.EnchantRules.miningCap(CraftsRules.effectiveMining(astralite.level(), 5)));
		// a Mineracao continua limitando: mundo no Ouro com casa V ainda e II
		assertEquals(2, com.mlin.notas.enchanting.EnchantRules.miningCap(CraftsRules.effectiveMining(com.mlin.notas.tier.Tier.GOLD.level(), 5)));
		// sem Encantador (mesa de teste fora da vila) nao muda nada
		assertEquals(com.mlin.notas.tier.Tier.GOLD.level(), CraftsRules.effectiveMining(com.mlin.notas.tier.Tier.GOLD.level(), 0));
		assertEquals(1, CraftsRules.enchantCap(5, 1));
		assertEquals(Integer.MAX_VALUE, CraftsRules.enchantCap(Integer.MAX_VALUE, 5));
	}

	@Test
	void churchBellRingsOnceAtDuskAndBlessingIsDaily() {
		assertTrue(CraftsRules.duskBell(12000) && CraftsRules.duskBell(12100) && !CraftsRules.duskBell(11999) && !CraftsRules.duskBell(12200));
		assertTrue(CraftsRules.duskBell(12000 + 24000L * 5), "todo dia");
		assertTrue(CraftsRules.gathering(12500) && !CraftsRules.gathering(6000));
		assertTrue(CraftsRules.canBless(-1, 0) && CraftsRules.canBless(3, 4));
		assertFalse(CraftsRules.canBless(4, 4), "uma vez por dia");
		assertEquals(300, CraftsRules.BLESSING_TICKS, "regeneracao curta");
	}

	@Test
	void tavernRumorLimitsAndPrices() {
		assertEquals(1, CraftsRules.rumorsPerDay(1));
		assertEquals(3, CraftsRules.rumorsPerDay(3));
		assertEquals(1, CraftsRules.extraBounties(1));
		assertEquals(2, CraftsRules.extraBounties(3));
		for (Rumors.Category c : Rumors.Category.values()) {
			assertTrue(c.price() >= 4 && c.price() <= 16, c + " preco entre 4 e 16");
		}
		assertEquals(4, Rumors.Category.RUIN.price());
		assertEquals(16, Rumors.Category.LAIR.price());
		// limite diario: o contador zera no dia seguinte
		long stored = 0;
		assertTrue(Rumors.mayBuy(stored, 5, 1));
		stored = Rumors.afterBuying(stored, 5);
		assertFalse(Rumors.mayBuy(stored, 5, 1), "taverna I: 1 por dia");
		assertTrue(Rumors.mayBuy(stored, 6, 1), "outro dia");
		stored = Rumors.afterBuying(Rumors.afterBuying(Rumors.afterBuying(0, 7), 7), 7);
		assertEquals(3, Rumors.usedToday(stored, 7));
		assertFalse(Rumors.mayBuy(stored, 7, 3));
		assertTrue(Rumors.mayBuy(Rumors.afterBuying(Rumors.afterBuying(0, 7), 7), 7, 3), "taverna III: 3 por dia");
	}

	@Test
	void lairRumorsRespectTheCompassMiningLock() {
		// sem Mineracao suficiente nenhum covil e contado; com Ouro, so o santuario de cobalto; com Diamante, tambem o abissal
		assertTrue(Rumors.candidates(Rumors.Category.LAIR, com.mlin.notas.tier.Tier.IRON.level()).isEmpty());
		assertEquals(java.util.List.of("mlinnotas:cobalt_shrine"), Rumors.candidates(Rumors.Category.LAIR, com.mlin.notas.tier.Tier.GOLD.level()));
		assertEquals(2, Rumors.candidates(Rumors.Category.LAIR, com.mlin.notas.tier.Tier.DIAMOND.level()).size());
		assertEquals(Rumors.Category.RUIN.structures().size(), Rumors.candidates(Rumors.Category.RUIN, 1).size(), "ruinas sem trava");
	}

	@Test
	void weeklyFairNeedsMarketTwoAndFallsOnTheLastDayOfTheWeek() {
		assertFalse(CraftsRules.fairDay(6, 1), "mercado I nao abre feira");
		assertTrue(CraftsRules.fairDay(6, 2) && CraftsRules.fairDay(13, 2) && CraftsRules.fairDay(20, 3));
		assertFalse(CraftsRules.fairDay(7, 2) || CraftsRules.fairDay(0, 2));
		int fairDays = 0;
		for (long d = 0; d < 70; d++) {
			fairDays += CraftsRules.fairDay(d, 2) ? 1 : 0;
		}
		assertEquals(10, fairDays, "um dia por semana");
		assertEquals(1, CraftsRules.fairMerchants(2));
		assertEquals(2, CraftsRules.fairMerchants(3));
	}

	@Test
	void leftoversAppearWithTheVillageLevel() {
		assertTrue(CraftsRules.leftoversFor(VillageLevel.VILLAGE).isEmpty());
		assertEquals(java.util.List.of(CraftsRules.Leftover.FACADES), CraftsRules.leftoversFor(VillageLevel.TOWN));
		assertTrue(CraftsRules.leftoversFor(VillageLevel.CAPITAL).contains(CraftsRules.Leftover.TOWNHOUSES));
		assertTrue(CraftsRules.leftoversFor(VillageLevel.METROPOLIS).contains(CraftsRules.Leftover.ARCADES));
	}

	@Test
	void bountyBoardAddsExtraBountiesWithoutChangingTheBaseThree() {
		java.util.List<Bounty> base = BountyBoard.roll(77L, 5, VillageLevel.TOWN);
		java.util.List<Bounty> extra = BountyBoard.roll(77L, 5, VillageLevel.TOWN, 1);
		assertEquals(BountyBoard.PER_DAY, base.size());
		assertEquals(BountyBoard.PER_DAY + 1, extra.size());
		assertEquals(base, extra.subList(0, BountyBoard.PER_DAY), "as tres de sempre nao mudam");
		assertEquals(BountyBoard.MAX_PER_DAY, BountyBoard.roll(77L, 5, VillageLevel.TOWN, 9).size(), "teto de missoes no quadro");
	}
}
