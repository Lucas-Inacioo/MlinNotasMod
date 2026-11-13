package com.mlin.notas.combat;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponStatsTest {
	@Test
	void swordMatchesVanillaFormula() {
		assertEquals(6.0F, WeaponStats.swordDamage(Tier.IRON), 1.0E-4);
		assertEquals(1.6F, WeaponStats.SWORD_SPEED, 1.0E-4);
		assertEquals(9.6F, WeaponStats.swordDps(Tier.IRON), 1.0E-4);
	}

	@Test
	void sustainedDpsIsNormalizedAgainstTheSameTierSword() {
		for (Tier tier : Tier.values()) {
			float sword = WeaponStats.swordDps(tier);
			for (WeaponClass weaponClass : WeaponClass.ALL) {
				float ratio = WeaponStats.of(weaponClass, tier).dps() / sword;
				String where = weaponClass + " " + tier + " = " + ratio;
				if (weaponClass.sweeps()) {
					// classes de arco: -20% num alvo so (com folga de arredondamento)
					assertTrue(ratio >= 0.72F && ratio <= 0.88F, where);
				} else {
					assertTrue(ratio >= 0.90F && ratio <= 1.10F, where);
				}
			}
		}
	}

	@Test
	void finishersAreCappedAndHitsNeverExceedTheMaximum() {
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			assertTrue(weaponClass.finisherMultiplier() > 1.0F && weaponClass.finisherMultiplier() <= 1.25F, weaponClass.name());
			assertTrue(WeaponStats.hitMultiplier(weaponClass, true, true) <= WeaponStats.MAX_HIT_MULTIPLIER);
			assertEquals(1.0F, WeaponStats.hitMultiplier(weaponClass, false, false));
		}
		assertEquals(1.4F, WeaponStats.hitMultiplier(WeaponClass.DAGGER, false, true), 1.0E-4);
		// so a adaga ganha bonus pelas costas
		assertEquals(1.0F, WeaponStats.hitMultiplier(WeaponClass.GREATSWORD, false, true), 1.0E-4);
	}

	@Test
	void damageNeverDropsAlongTheLadder() {
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			float previous = 0.0F;
			for (Tier tier : Tier.values()) {
				float damage = WeaponStats.of(weaponClass, tier).damage();
				assertTrue(damage >= previous, weaponClass + " " + tier);
				previous = damage;
			}
		}
	}

	@Test
	void classesFeelDifferent() {
		WeaponStats.Stats greatsword = WeaponStats.of(WeaponClass.GREATSWORD, Tier.IRON);
		WeaponStats.Stats dagger = WeaponStats.of(WeaponClass.DAGGER, Tier.IRON);
		WeaponStats.Stats hammer = WeaponStats.of(WeaponClass.WARHAMMER, Tier.IRON);
		WeaponStats.Stats glaive = WeaponStats.of(WeaponClass.GLAIVE, Tier.IRON);
		WeaponStats.Stats rapier = WeaponStats.of(WeaponClass.RAPIER, Tier.IRON);
		float sword = WeaponStats.swordDamage(Tier.IRON);
		assertTrue(hammer.damage() > greatsword.damage() && greatsword.damage() > sword, "pesadas batem mais forte por golpe");
		assertTrue(dagger.damage() < sword && dagger.attackSpeed() > WeaponStats.SWORD_SPEED, "adaga: leve e rapida");
		assertTrue(glaive.reach() > rapier.reach() && rapier.reach() > WeaponStats.PLAYER_BASE_REACH, "hastes alcancam mais");
		assertTrue(dagger.reach() < WeaponStats.PLAYER_BASE_REACH);
		Set<Float> speeds = new HashSet<>();
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			speeds.add(weaponClass.attackSpeed());
		}
		assertEquals(WeaponClass.ALL.size(), speeds.size(), "cada classe tem seu ritmo");
	}

	@Test
	void fastWeaponsRechargeWithinVanillaInvulnerabilityOnlyWhenHandled() {
		// a adaga recarrega antes dos 10 ticks de invulnerabilidade (o servidor libera); as outras nao precisam
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			int cooldown = WeaponStats.of(weaponClass, Tier.IRON).cooldownTicks();
			assertTrue(cooldown >= 10 || weaponClass.attackSpeed() > 2.0F, weaponClass.name());
		}
	}

	@Test
	void itemNamesFollowVanillaPrefixes() {
		assertEquals("wooden_greatsword", WeaponClass.GREATSWORD.itemName(Tier.WOOD));
		assertEquals("golden_dagger", WeaponClass.DAGGER.itemName(Tier.GOLD));
		assertEquals("cobalt_warhammer", WeaponClass.WARHAMMER.itemName(Tier.COBALT));
		assertEquals(WeaponClass.RAPIER, WeaponClass.bySuffix("rapier"));
	}
}
