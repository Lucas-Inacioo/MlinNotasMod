package com.mlin.notas.combat;

import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.Tier;

/**
 * Numeros das armas, normalizados pela espada do mesmo nivel. Logica pura.
 *
 * <p>Regra: o DPS sustentado contra um alvo (combo de 3 golpes acertado sempre, carga cheia) fica a ate 10% da
 * espada do nivel; classes que acertam em arco ({@link WeaponClass#sweeps()}) ficam 20% abaixo. O golpe final do
 * combo nunca passa de +25%. O dano por golpe sai de {@code dpsEspada * razao / (velocidade * mediaDoCombo)},
 * arredondado em quartos de ponto.</p>
 */
public final class WeaponStats {
	/** Dano base do jogador (atributo attack_damage sem item). */
	public static final float PLAYER_BASE_DAMAGE = 1.0F;
	/** Velocidade base do jogador (atributo attack_speed sem item). */
	public static final float PLAYER_BASE_SPEED = 4.0F;
	/** Alcance base de ataque do jogador. */
	public static final float PLAYER_BASE_REACH = 3.0F;
	/** Velocidade da espada (4 - 2,4). */
	public static final float SWORD_SPEED = PLAYER_BASE_SPEED + GearKind.SWORD.attackSpeedBaseline();

	/** Numeros finais de uma arma: dano por golpe com carga cheia, ataques por segundo e alcance. */
	public record Stats(float damage, float attackSpeed, float reach, float finisherMultiplier) {
		/** Media de dano por golpe ao longo de um combo completo. */
		public float averageHit() {
			return damage * comboAverage(finisherMultiplier);
		}

		/** Dano por segundo sustentado contra um alvo (combo acertado sempre). */
		public float dps() {
			return averageHit() * attackSpeed;
		}

		/** Ticks para recarregar o golpe (20 / velocidade). */
		public int cooldownTicks() {
			return Math.round(20.0F / attackSpeed);
		}
	}

	private WeaponStats() {
	}

	/** Dano da espada do nivel com carga cheia (jogador 1 + espada 3 + bonus do material). */
	public static float swordDamage(Tier tier) {
		return PLAYER_BASE_DAMAGE + GearKind.SWORD.attackDamageBaseline() + tier.attackBonus();
	}

	public static float swordDps(Tier tier) {
		return swordDamage(tier) * SWORD_SPEED;
	}

	/** Media do multiplicador por golpe num combo de {@link WeaponClass#COMBO_LENGTH} golpes. */
	public static float comboAverage(float finisherMultiplier) {
		return (WeaponClass.COMBO_LENGTH - 1 + finisherMultiplier) / WeaponClass.COMBO_LENGTH;
	}

	public static Stats of(WeaponClass weaponClass, Tier tier) {
		float target = swordDps(tier) * weaponClass.dpsRatio();
		float raw = target / (weaponClass.attackSpeed() * comboAverage(weaponClass.finisherMultiplier()));
		float damage = Math.max(1.0F, Math.round(raw * 4.0F) / 4.0F);
		return new Stats(damage, weaponClass.attackSpeed(), PLAYER_BASE_REACH + weaponClass.reachBonus(), weaponClass.finisherMultiplier());
	}

	/** Valor do modificador de dano do item (o jogador ja tem 1). */
	public static float damageModifier(Stats stats) {
		return stats.damage() - PLAYER_BASE_DAMAGE;
	}

	/** Valor do modificador de velocidade do item (o jogador ja tem 4). */
	public static float speedModifier(Stats stats) {
		return stats.attackSpeed() - PLAYER_BASE_SPEED;
	}

	/** Valor do modificador de alcance do item. */
	public static float reachModifier(Stats stats) {
		return stats.reach() - PLAYER_BASE_REACH;
	}

	/** Multiplicador maximo de um golpe somando golpe final e ataque pelas costas. */
	public static final float MAX_HIT_MULTIPLIER = 1.6F;

	/**
	 * Multiplicador de dano de um golpe: golpe final do combo e/ou pelas costas (adaga), limitado a
	 * {@link #MAX_HIT_MULTIPLIER}.
	 */
	public static float hitMultiplier(WeaponClass weaponClass, boolean finisher, boolean behind) {
		float multiplier = 1.0F;
		if (finisher) {
			multiplier *= weaponClass.finisherMultiplier();
		}
		if (behind) {
			multiplier *= weaponClass.backstabMultiplier();
		}
		return Math.min(MAX_HIT_MULTIPLIER, multiplier);
	}
}
