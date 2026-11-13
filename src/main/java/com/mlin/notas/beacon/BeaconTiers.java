package com.mlin.notas.beacon;

import com.mlin.notas.tier.Tier;

import java.util.Collection;

/**
 * Regras do sinalizador por nivel de metal (logica pura, sem classes do Minecraft).
 *
 * <p>O nivel da piramide e o do metal <b>mais fraco</b> usado em qualquer bloco das camadas ativas. Ordem dos metais
 * de base: ferro &lt; ouro &lt; cobalto &lt; diamante (= esmeralda) &lt; netherite &lt; infernita &lt; endita &lt;
 * astralita &lt; celestio. Bloco de base desconhecido (de outro mod, por exemplo) conta como ferro.</p>
 *
 * <ul>
 *   <li>Alcance: o vanilla ({@code camadas * 10 + 10}) vezes o multiplicador do metal; ferro mantem o vanilla (50 com 4
 *   camadas) e celestio chega a 200.</li>
 *   <li>Poder primario nivel III (no lugar do II do vanilla, ao escolher o mesmo efeito como secundario): Velocidade
 *   com infernita+, Pressa com endita+, Forca com astralita+, Resistencia so com celestio. Piramides de metais vanilla
 *   continuam no II.</li>
 *   <li>Poderes extras (secundarios, 4 camadas): Garimpo com infernita+, Voo so com celestio.</li>
 * </ul>
 */
public final class BeaconTiers {
	/** Metais que formam a base, do mais fraco ao mais forte. */
	public static final Tier[] METALS = {Tier.IRON, Tier.GOLD, Tier.COBALT, Tier.DIAMOND, Tier.NETHERITE, Tier.INFERNITE, Tier.ENDITE,
			Tier.ASTRALITE, Tier.CELESTIUM};

	/** Poderes primarios do vanilla que importam aqui. */
	public enum Primary {
		SPEED(Tier.INFERNITE),
		HASTE(Tier.ENDITE),
		RESISTANCE(Tier.CELESTIUM),
		JUMP_BOOST(null),
		STRENGTH(Tier.ASTRALITE),
		REGENERATION(null);

		private final Tier levelThree;

		Primary(Tier levelThree) {
			this.levelThree = levelThree;
		}

		/** Metal minimo para o nivel III (null = nunca passa do II). */
		public Tier levelThree() {
			return levelThree;
		}
	}

	/** Poderes novos do mod, escolhidos na coluna secundaria. */
	public enum Extra {
		PROSPECTING("prospecting", Tier.INFERNITE),
		FLIGHT("beacon_flight", Tier.CELESTIUM);

		private final String id;
		private final Tier requires;

		Extra(String id, Tier requires) {
			this.id = id;
			this.requires = requires;
		}

		/** Id do efeito ({@code mlinnotas:<id>}). */
		public String id() {
			return id;
		}

		/** Metal minimo da piramide. */
		public Tier requires() {
			return requires;
		}
	}

	/** Chance extra de minerio do Garimpo. */
	public static final double PROSPECTING_CHANCE = 0.5;

	private BeaconTiers() {
	}

	/** Metal de um bloco de base pelo id ({@code minecraft:iron_block}, {@code mlinnotas:cobalt_block}); null se nao for metal conhecido. */
	public static Tier metalOf(String blockId) {
		return switch (blockId) {
			case "minecraft:iron_block" -> Tier.IRON;
			case "minecraft:gold_block" -> Tier.GOLD;
			case "minecraft:diamond_block", "minecraft:emerald_block" -> Tier.DIAMOND;
			case "minecraft:netherite_block" -> Tier.NETHERITE;
			default -> {
				if (blockId.startsWith("mlinnotas:") && blockId.endsWith("_block") && !blockId.startsWith("mlinnotas:raw_")) {
					Tier tier = Tier.byId(blockId.substring("mlinnotas:".length(), blockId.length() - "_block".length()));
					yield isMetal(tier) ? tier : null;
				}
				yield null;
			}
		};
	}

	public static boolean isMetal(Tier tier) {
		if (tier == null) {
			return false;
		}
		for (Tier metal : METALS) {
			if (metal == tier) {
				return true;
			}
		}
		return false;
	}

	/** Posicao do metal na escada do sinalizador (ferro = 0, celestio = 8); -1 se nao for metal de base. */
	public static int rank(Tier tier) {
		for (int i = 0; i < METALS.length; i++) {
			if (METALS[i] == tier) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Nivel da piramide: o metal mais fraco entre os blocos. Entradas null (bloco de base desconhecido) contam como
	 * ferro. Lista vazia = null (sem piramide).
	 */
	public static Tier pyramidTier(Collection<Tier> blocks) {
		Tier weakest = null;
		for (Tier block : blocks) {
			Tier metal = isMetal(block) ? block : Tier.IRON;
			if (weakest == null || rank(metal) < rank(weakest)) {
				weakest = metal;
			}
		}
		return weakest;
	}

	/** Multiplicador de alcance: ferro 1 (vanilla) ... celestio 4. */
	public static double rangeMultiplier(Tier tier) {
		return switch (tier == null ? Tier.IRON : tier) {
			case GOLD -> 1.2;
			case COBALT -> 1.4;
			case DIAMOND -> 1.6;
			case NETHERITE -> 2.0;
			case INFERNITE -> 2.5;
			case ENDITE -> 3.0;
			case ASTRALITE -> 3.5;
			case CELESTIUM -> 4.0;
			default -> 1.0;
		};
	}

	/** Alcance (raio da caixa, em blocos) de uma piramide com {@code levels} camadas (0..4). */
	public static int range(Tier tier, int levels) {
		if (levels <= 0) {
			return 0;
		}
		return (int) Math.round((levels * 10 + 10) * rangeMultiplier(tier));
	}

	/** O poder primario chega ao nivel III com este metal. */
	public static boolean levelThree(Primary primary, Tier tier) {
		return primary != null && primary.levelThree() != null && tier != null && rank(tier) >= rank(primary.levelThree());
	}

	/** Amplificador maximo do primario (0 = I, 1 = II, 2 = III). */
	public static int maxAmplifier(Primary primary, Tier tier) {
		return levelThree(primary, tier) ? 2 : 1;
	}

	/**
	 * Amplificador do primario aplicado: 0 sem reforco; com reforco (secundario = primario, 4 camadas) II, ou III se o
	 * metal permitir.
	 */
	public static int primaryAmplifier(Primary primary, Tier tier, boolean boosted) {
		return boosted ? maxAmplifier(primary, tier) : 0;
	}

	/** O poder extra esta liberado para esta piramide (precisa das 4 camadas e do metal minimo). */
	public static boolean unlocks(Extra extra, Tier tier, int levels) {
		return levels >= 4 && tier != null && rank(tier) >= rank(extra.requires());
	}

	/** Duracao do efeito em ticks (a mesma do vanilla: 9 + 2 por camada, em segundos). */
	public static int durationTicks(int levels) {
		return (9 + levels * 2) * 20;
	}

	/** Codigo do nivel para sincronizar com o cliente: 0 = sem piramide, 1..9 = ferro..celestio. */
	public static int encode(Tier tier) {
		return tier == null ? 0 : rank(tier) + 1;
	}

	public static Tier decode(int code) {
		return code <= 0 || code > METALS.length ? null : METALS[code - 1];
	}
}
