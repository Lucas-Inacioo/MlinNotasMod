package com.mlin.notas.combat;

import com.mlin.notas.tier.Tier;

/**
 * Armas assinadas dos chefes, feitas com o item que cada um solta. Usam os numeros da classe no nivel logo acima
 * do nivel em que o chefe e enfrentado (nunca mais que isso) e tem um efeito proprio que dispara no maximo uma vez
 * por segundo ({@link #procCooldownTicks}). Logica pura.
 */
public enum BossWeapon {
	/** Golem de Cobalto (enfrentado com ferro/ouro): onda de choque maior que levanta e desacelera. */
	GOLEM_HAMMER("golem_hammer", WeaponClass.WARHAMMER, Tier.COBALT, Proc.QUAKE, 40, 0.5F, 4.0F),
	/** Guardiao das Profundezas (cobalto/diamante): prende o alvo em sculk e enfraquece quem esta perto. */
	ABYSSAL_BLADE("abyssal_blade", WeaponClass.RAPIER, Tier.NETHERITE, Proc.SCULK_BIND, 60, 0.0F, 5.0F),
	/** Colosso Igneo (netherite): incendeia a cada golpe e o golpe final solta uma onda de fogo. */
	IGNEOUS_GREATSWORD("igneous_greatsword", WeaponClass.GREATSWORD, Tier.INFERNITE, Proc.FIRE_WAVE, 20, 4.0F, 4.0F),
	/** Dragao do Ender (infernita): golpe final ou pelas costas explode em sopro do vazio em volta do alvo. */
	DRAGON_FANGS("dragon_fangs", WeaponClass.DAGGER, Tier.ENDITE, Proc.VOID_BURST, 20, 3.0F, 2.5F),
	/** Arauto Astral (endita): golpe carregado dispara um raio estelar no inimigo mais proximo. */
	ASTRAL_LANCE("astral_lance", WeaponClass.GLAIVE, Tier.ASTRALITE, Proc.STAR_BOLT, 20, 4.0F, 10.0F);

	/** Efeito especial de cada arma. */
	public enum Proc {
		QUAKE,
		SCULK_BIND,
		FIRE_WAVE,
		VOID_BURST,
		STAR_BOLT
	}

	/** Recarga minima de qualquer efeito: 1 por segundo. */
	public static final int MIN_PROC_COOLDOWN = 20;

	private final String id;
	private final WeaponClass weaponClass;
	private final Tier powerTier;
	private final Proc proc;
	private final int procCooldownTicks;
	private final float procDamage;
	private final float procRadius;

	BossWeapon(String id, WeaponClass weaponClass, Tier powerTier, Proc proc, int procCooldownTicks, float procDamage, float procRadius) {
		this.id = id;
		this.weaponClass = weaponClass;
		this.powerTier = powerTier;
		this.proc = proc;
		this.procCooldownTicks = procCooldownTicks;
		this.procDamage = procDamage;
		this.procRadius = procRadius;
	}

	public String id() {
		return id;
	}

	public WeaponClass weaponClass() {
		return weaponClass;
	}

	/** Nivel cujos numeros a arma usa (um acima de quando o chefe aparece). */
	public Tier powerTier() {
		return powerTier;
	}

	public Proc proc() {
		return proc;
	}

	public int procCooldownTicks() {
		return Math.max(MIN_PROC_COOLDOWN, procCooldownTicks);
	}

	/**
	 * Dano do efeito: para o martelo do golem e uma fracao do golpe (onda de choque); para os outros, dano magico fixo.
	 */
	public float procDamage() {
		return procDamage;
	}

	public float procRadius() {
		return procRadius;
	}

	public WeaponStats.Stats stats() {
		return WeaponStats.of(weaponClass, powerTier);
	}

	/** O efeito so dispara se a recarga terminou e o golpe pede (carga cheia; algumas so no golpe final). */
	public boolean canProc(long now, long lastProc, boolean charged, boolean finisher, boolean behind) {
		if (!charged || now - lastProc < procCooldownTicks()) {
			return false;
		}
		return switch (proc) {
			case FIRE_WAVE -> finisher;
			case VOID_BURST -> finisher || behind;
			default -> true;
		};
	}

	public static BossWeapon byId(String id) {
		for (BossWeapon weapon : values()) {
			if (weapon.id.equals(id)) {
				return weapon;
			}
		}
		return null;
	}
}
