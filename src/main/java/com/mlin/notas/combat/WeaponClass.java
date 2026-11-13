package com.mlin.notas.combat;

import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.Tier;

import java.util.List;

/**
 * Classes de arma do "Combate melhor" (alem da espada, machado e lanca vanilla). Cada classe muda o ritmo da luta:
 * velocidade de ataque, alcance, combo de 3 golpes com um golpe final mais forte e uma caracteristica propria.
 * Os danos saem de {@link WeaponStats}, normalizados pela espada do mesmo nivel. Logica pura.
 *
 * <p>Ponto de extensao: {@link #ALL} lista as classes; cada uma existe em todos os 12 niveis de {@link Tier}
 * ({@link #itemName}). Quem iterar os tipos de equipamento por nivel (receitas de forja, loot...) deve somar estas
 * classes as de {@link GearKind}.</p>
 */
public enum WeaponClass {
	//            sufixo        vel   dps   alcance golpe final  arco(graus) respingo perfura costas
	GREATSWORD("greatsword", 0.9F, 0.80F, 0.5F, 1.25F, 160.0F, 0.6F, 0.0F, 1.0F),
	DAGGER("dagger", 2.4F, 0.95F, -0.5F, 1.15F, 0.0F, 0.0F, 0.0F, 1.4F),
	WARHAMMER("warhammer", 0.8F, 0.92F, 0.0F, 1.25F, 0.0F, 0.35F, 0.35F, 1.0F),
	GLAIVE("glaive", 1.1F, 0.80F, 1.5F, 1.2F, 100.0F, 0.5F, 0.0F, 1.0F),
	RAPIER("rapier", 2.0F, 1.0F, 0.75F, 1.25F, 0.0F, 0.5F, 0.0F, 1.0F);

	/** Todas as classes, na ordem da aba criativa. */
	public static final List<WeaponClass> ALL = List.of(values());

	/** Golpes num combo completo (o ultimo e o golpe final). */
	public static final int COMBO_LENGTH = 3;

	private final String suffix;
	private final float attackSpeed;
	private final float dpsRatio;
	private final float reachBonus;
	private final float finisherMultiplier;
	private final float arcDegrees;
	private final float splashRatio;
	private final float armorPierce;
	private final float backstabMultiplier;

	WeaponClass(String suffix, float attackSpeed, float dpsRatio, float reachBonus, float finisherMultiplier, float arcDegrees,
			float splashRatio, float armorPierce, float backstabMultiplier) {
		this.suffix = suffix;
		this.attackSpeed = attackSpeed;
		this.dpsRatio = dpsRatio;
		this.reachBonus = reachBonus;
		this.finisherMultiplier = finisherMultiplier;
		this.arcDegrees = arcDegrees;
		this.splashRatio = splashRatio;
		this.armorPierce = armorPierce;
		this.backstabMultiplier = backstabMultiplier;
	}

	public String suffix() {
		return suffix;
	}

	/** Id do item no nivel: wooden_greatsword, golden_dagger, cobalt_warhammer... */
	public String itemName(Tier tier) {
		return GearKind.itemPrefix(tier) + "_" + suffix;
	}

	/** Ataques por segundo com carga cheia (a espada faz 1,6). */
	public float attackSpeed() {
		return attackSpeed;
	}

	/** DPS sustentado contra um alvo, relativo a espada do mesmo nivel (classes de arco: -20%). */
	public float dpsRatio() {
		return dpsRatio;
	}

	/** Soma ao alcance de ataque (entity_interaction_range, base 3). */
	public float reachBonus() {
		return reachBonus;
	}

	/** Multiplicador do ultimo golpe do combo (no maximo +25%). */
	public float finisherMultiplier() {
		return finisherMultiplier;
	}

	/** Abertura do arco em graus (0 = so o alvo). */
	public float arcDegrees() {
		return arcDegrees;
	}

	/** Golpe em arco: acerta todo mundo num cone a frente. */
	public boolean sweeps() {
		return arcDegrees > 0.0F;
	}

	/**
	 * Fracao do dano que respinga: nos outros alvos do arco (montante, glaive), na onda de choque (martelo) ou no
	 * alvo atras do primeiro na estocada final (rapieira).
	 */
	public float splashRatio() {
		return splashRatio;
	}

	/** Fracao da armadura e tenacidade do alvo ignorada (martelo de guerra). */
	public float armorPierce() {
		return armorPierce;
	}

	/** Multiplicador ao acertar pelas costas (adaga). */
	public float backstabMultiplier() {
		return backstabMultiplier;
	}

	/** Martelo: golpe carregado solta uma onda de choque em volta do alvo. */
	public boolean shockwave() {
		return this == WARHAMMER;
	}

	/** Rapieira: o golpe final atravessa e acerta quem estiver logo atras do alvo. */
	public boolean thrustPierces() {
		return this == RAPIER;
	}

	/** Segundos que o golpe desarma o escudo do alvo (como o machado vanilla, que desarma 5 s). */
	public float disableBlockingSeconds() {
		return this == WARHAMMER ? 3.0F : 0.0F;
	}

	/** Raio da onda de choque do martelo. */
	public float shockwaveRadius() {
		return this == WARHAMMER ? 2.5F : 0.0F;
	}

	public static WeaponClass bySuffix(String suffix) {
		for (WeaponClass weaponClass : values()) {
			if (weaponClass.suffix.equals(suffix)) {
				return weaponClass;
			}
		}
		return null;
	}
}
