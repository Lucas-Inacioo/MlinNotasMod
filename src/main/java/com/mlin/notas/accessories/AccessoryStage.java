package com.mlin.notas.accessories;

import com.mlin.notas.tier.Tier;

/**
 * Estagio do mundo para o teto de bonus dos acessorios, derivado do nivel de Mineracao (arvore de habilidades do
 * mundo): antes do Golem (abaixo de Cobalto), Golem-Guardiao (Cobalto..Diamante), Netherite-Dragao
 * (Netherite..Infernita) e Endita em diante. Tabela do plano de progressao ("Accessory budget"). Logica pura.
 */
public enum AccessoryStage {
	//              dano   reducao vel    vida
	PRE_GOLEM("pre_golem", 0.05, 0.04, 0.05, 0),
	GOLEM_GUARDIAN("golem_guardian", 0.10, 0.08, 0.08, 2),
	NETHERITE_DRAGON("netherite_dragon", 0.15, 0.12, 0.10, 4),
	ENDGAME("endgame", 0.20, 0.15, 0.15, 6);

	private final String id;
	private final double damage;
	private final double reduction;
	private final double speed;
	private final double health;

	AccessoryStage(String id, double damage, double reduction, double speed, double health) {
		this.id = id;
		this.damage = damage;
		this.reduction = reduction;
		this.speed = speed;
		this.health = health;
	}

	public String id() {
		return id;
	}

	/** Teto de um atributo com teto por estagio (dano, reducao de dano, velocidade, vida). */
	public double cap(AccessoryStat stat) {
		return switch (stat) {
			case DAMAGE -> damage;
			case DAMAGE_REDUCTION -> reduction;
			case SPEED -> speed;
			case MAX_HEALTH -> health;
			default -> throw new IllegalArgumentException(stat + " nao tem teto por estagio");
		};
	}

	/** Estagio para um nivel de Mineracao (1 = Madeira .. 12 = Celestio). */
	public static AccessoryStage fromMiningLevel(int level) {
		if (level >= Tier.ENDITE.level()) {
			return ENDGAME;
		}
		if (level >= Tier.NETHERITE.level()) {
			return NETHERITE_DRAGON;
		}
		if (level >= Tier.COBALT.level()) {
			return GOLEM_GUARDIAN;
		}
		return PRE_GOLEM;
	}
}
