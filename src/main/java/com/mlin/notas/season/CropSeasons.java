package com.mlin.notas.season;

import java.util.Map;

/**
 * Crescimento das culturas por estacao. Cada cultura tem um multiplicador por estacao (primavera, verao, outono,
 * inverno); no inicio/fim da estacao ele mistura 30% com a estacao vizinha. Menor que 1 = o random tick de crescimento
 * e pulado com essa chance; maior que 1 = um tick extra com a chance que sobra. Estufa (teto de vidro ou bloco de luz
 * forte sob teto) nunca fica abaixo de 1. Bioma quente: o inverno vale como o outono (estacao seca). Logica pura.
 */
public final class CropSeasons {
	/** Primavera, verao, outono, inverno. */
	public record Profile(float spring, float summer, float autumn, float winter) {
		float of(Season season) {
			return switch (season) {
				case SPRING -> spring;
				case SUMMER -> summer;
				case AUTUMN -> autumn;
				case WINTER -> winter;
			};
		}
	}

	public static final Profile GRAIN = new Profile(1.25F, 1.0F, 0.6F, 0.05F);
	public static final Profile ROOT = new Profile(1.2F, 0.8F, 1.15F, 0.05F);
	public static final Profile BEET = new Profile(0.8F, 0.6F, 1.3F, 0.1F);
	public static final Profile PUMPKIN = new Profile(0.5F, 1.0F, 1.35F, 0.0F);
	public static final Profile MELON = new Profile(0.7F, 1.35F, 0.6F, 0.0F);
	public static final Profile BERRY = new Profile(0.8F, 1.25F, 1.0F, 0.05F);
	public static final Profile CANE = new Profile(1.0F, 1.25F, 0.8F, 0.2F);
	public static final Profile FLOWER = new Profile(1.3F, 1.0F, 0.6F, 0.05F);
	public static final Profile PITCHER = new Profile(1.0F, 1.3F, 0.7F, 0.05F);
	public static final Profile SAPLING = new Profile(1.25F, 1.0F, 0.75F, 0.3F);

	private static final Map<String, Profile> BY_ID = Map.ofEntries(
			Map.entry("minecraft:wheat", GRAIN),
			Map.entry("minecraft:potatoes", GRAIN),
			Map.entry("minecraft:carrots", ROOT),
			Map.entry("minecraft:beetroots", BEET),
			Map.entry("minecraft:pumpkin_stem", PUMPKIN),
			Map.entry("minecraft:melon_stem", MELON),
			Map.entry("minecraft:sweet_berry_bush", BERRY),
			Map.entry("minecraft:sugar_cane", CANE),
			Map.entry("minecraft:torchflower_crop", FLOWER),
			Map.entry("minecraft:pitcher_crop", PITCHER));

	/** Culturas da cozinha do mod (alface, tomate, milho...), definidas em {@code food/FoodCatalog}. */
	private static final Map<String, Profile> MOD_CROPS = com.mlin.notas.food.FoodCatalog.cropProfiles();

	/** Quao bem a cultura vai na data, para o HUD. */
	public enum Status {
		/** >= 1,15 */
		THRIVING,
		/** >= 0,9 */
		GOOD,
		/** >= 0,4 */
		SLOW,
		/** >= 0,15 */
		POOR,
		/** quase parada */
		DORMANT;

		public static Status of(float multiplier) {
			if (multiplier >= 1.15F) {
				return THRIVING;
			}
			if (multiplier >= 0.9F) {
				return GOOD;
			}
			if (multiplier >= 0.4F) {
				return SLOW;
			}
			return multiplier >= 0.15F ? POOR : DORMANT;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private CropSeasons() {
	}

	/** Perfil do bloco (id {@code ns:caminho}); mudas de qualquer madeira contam. Null = nao e sazonal. */
	public static Profile profile(String blockId) {
		Profile profile = BY_ID.get(blockId);
		if (profile != null) {
			return profile;
		}
		profile = MOD_CROPS.get(blockId);
		if (profile != null) {
			return profile;
		}
		if (blockId.startsWith("minecraft:") && (blockId.endsWith("_sapling") || blockId.equals("minecraft:mangrove_propagule"))) {
			return SAPLING;
		}
		return null;
	}

	/** Multiplicador da estacao, com a mistura do inicio/fim com a estacao vizinha. */
	public static float seasonal(Profile profile, SeasonDate date, SeasonClimate climate) {
		float here = value(profile, date.season(), climate);
		return switch (date.phase()) {
			case MID -> here;
			case EARLY -> 0.7F * here + 0.3F * value(profile, date.season().previous(), climate);
			case LATE -> 0.7F * here + 0.3F * value(profile, date.season().next(), climate);
		};
	}

	private static float value(Profile profile, Season season, SeasonClimate climate) {
		if (climate == SeasonClimate.HOT && season == Season.WINTER) {
			return profile.of(Season.AUTUMN);
		}
		return profile.of(season);
	}

	/** Multiplicador final: estufa nunca fica abaixo de 1. */
	public static float multiplier(Profile profile, SeasonDate date, SeasonClimate climate, boolean greenhouse) {
		float m = seasonal(profile, date, climate);
		return greenhouse ? Math.max(1.0F, m) : m;
	}

	/**
	 * Estufa: a cultura tem teto ({@code covered}) e o teto e de vidro, ou o lugar tem luz de bloco forte (>= 10,
	 * lampadas/tochas de uma fazenda coberta).
	 */
	public static boolean greenhouse(boolean covered, boolean glassRoof, int blockLight) {
		return covered && (glassRoof || blockLight >= 10);
	}

	/** O tick de crescimento acontece? {@code roll} uniforme em [0,1). Para multiplicadores &lt; 1. */
	public static boolean allowTick(float multiplier, double roll) {
		return multiplier >= 1.0F || roll < multiplier;
	}

	/** Tick extra depois do normal? Para multiplicadores &gt; 1 (chance = o que passa de 1). */
	public static boolean bonusTick(float multiplier, double roll) {
		return multiplier > 1.0F && roll < multiplier - 1.0F;
	}
}
