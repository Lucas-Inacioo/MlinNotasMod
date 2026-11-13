package com.mlin.notas.tier;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierTest {
	@Test
	void atLeastTwelveTiers() {
		assertTrue(Tier.values().length >= 12);
		assertEquals(1, Tier.WOOD.level());
		assertEquals(12, Tier.CELESTIUM.level());
	}

	@Test
	void someTiersOnlyInNetherAndEnd() {
		assertTrue(Arrays.stream(Tier.values()).anyMatch(t -> t.origin() == Tier.Origin.NETHER && !t.vanilla()));
		assertTrue(Arrays.stream(Tier.values()).anyMatch(t -> t.origin() == Tier.Origin.END && t.hasOre()));
		assertEquals(Tier.Origin.NETHER, Tier.INFERNITE.origin());
		assertEquals(Tier.Origin.END, Tier.ASTRALITE.origin());
	}

	@Test
	void statsNeverGoDownAlongTheLadder() {
		Tier previous = null;
		for (Tier tier : Tier.values()) {
			if (previous != null) {
				assertTrue(tier.toolDurability() > previous.toolDurability(), tier + " durabilidade");
				assertTrue(tier.miningSpeed() >= previous.miningSpeed(), tier + " velocidade");
				assertTrue(tier.attackBonus() >= previous.attackBonus(), tier + " dano");
				assertTrue(tier.fullSetDefense() > previous.fullSetDefense(), tier + " armadura");
				assertTrue(tier.toughness() >= previous.toughness(), tier + " tenacidade");
				assertTrue(tier.armorDurabilityMultiplier() >= previous.armorDurabilityMultiplier(), tier + " durabilidade da armadura");
				assertTrue(tier.wandBlocks() > previous.wandBlocks(), tier + " varinha");
				assertTrue(tier.chestSlots() > previous.chestSlots(), tier + " bau");
				assertTrue(tier.furnaceSpeed() > previous.furnaceSpeed(), tier + " fornalha");
			}
			previous = tier;
		}
	}

	@Test
	void diamondArmorIsFarFromFullDefense() {
		assertTrue(Tier.DIAMOND.fullSetDefense() < Tier.FULL_ARMOR);
		assertTrue(Tier.NETHERITE.fullSetDefense() < Tier.FULL_ARMOR);
		assertEquals(Tier.FULL_ARMOR, Tier.CELESTIUM.fullSetDefense(), "so o ultimo tier enche a barra");
		for (Tier tier : Tier.values()) {
			assertTrue(tier.fullSetDefense() <= Tier.FULL_ARMOR);
		}
	}

	@Test
	void vanillaDiamondAndNetheriteAreNerfed() {
		// vanilla: diamante 1561 de durabilidade e 20 de armadura; netherite 2031 e 20
		assertTrue(Tier.DIAMOND.toolDurability() < 1561);
		assertTrue(Tier.NETHERITE.toolDurability() < 2031);
		assertTrue(Tier.DIAMOND.fullSetDefense() < 20);
	}

	@Test
	void oreRequirementsFormAChain() {
		assertEquals(Tier.IRON, Tier.COBALT.oreRequires());
		assertEquals(Tier.NETHERITE, Tier.INFERNITE.oreRequires());
		assertEquals(Tier.INFERNITE, Tier.ENDITE.oreRequires());
		assertEquals(Tier.ENDITE, Tier.ASTRALITE.oreRequires());
		assertNull(Tier.CELESTIUM.oreRequires());
		assertTrue(Tier.NETHERITE.canMine(Tier.NETHERITE));
		assertFalse(Tier.DIAMOND.canMine(Tier.NETHERITE));
		assertFalse(Tier.CELESTIUM.hasOre());
		assertTrue(Tier.COBALT.hasOre());
		assertFalse(Tier.DIAMOND.hasOre());
	}

	@Test
	void lookupAndNeighbours() {
		assertEquals(Tier.ENDITE, Tier.byId("endite"));
		assertNull(Tier.byId("mithril"));
		assertEquals(Tier.STONE, Tier.WOOD.next());
		assertNull(Tier.CELESTIUM.next());
		assertNull(Tier.WOOD.previous());
		assertTrue(Tier.INFERNITE.fireResistant());
		assertFalse(Tier.DIAMOND.fireResistant());
	}
}
