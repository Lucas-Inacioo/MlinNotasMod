package com.mlin.notas.vstructures;

/** Estilo (variante) de cada estrutura conforme o bioma e a altura. Logica pura: usa so o id do bioma. */
public final class VariantRules {
	public static final int OUTPOST_PLAINS = 0;
	public static final int OUTPOST_TAIGA = 1;
	public static final int OUTPOST_DESERT = 2;
	public static final int OUTPOST_SNOWY = 3;
	public static final int OUTPOST_SAVANNA = 4;
	public static final int OUTPOST_CHERRY = 5;

	public static final int SHAFT_OAK = 0;
	public static final int SHAFT_DEEPSLATE = 1;
	public static final int SHAFT_ICE = 2;
	public static final int SHAFT_LUSH = 3;
	public static final int SHAFT_DRIPSTONE = 4;
	public static final int SHAFT_JUNGLE = 5;
	public static final int SHAFT_DESERT = 6;

	public static final int JUNGLE_PLAIN = 0;
	public static final int JUNGLE_BAMBOO = 1;

	private VariantRules() {
	}

	public static int variant(VKind kind, String biomeId, int y) {
		String b = biomeId == null ? "" : biomeId;
		return switch (kind) {
			case DESERT_PYRAMID, MINESHAFT_MESA -> 0;
			case MONUMENT, SHIPWRECK, SHIPWRECK_BEACHED, OCEAN_RUIN_WARM, OCEAN_RUIN_COLD, BURIED_TREASURE, IGLOO, SWAMP_HUT, RUINED_PORTAL, RUINED_PORTAL_DESERT,
					RUINED_PORTAL_JUNGLE, RUINED_PORTAL_SWAMP, RUINED_PORTAL_MOUNTAIN, RUINED_PORTAL_OCEAN, RUINED_PORTAL_NETHER, TRAIL_RUINS -> seaVariant(kind, b);
			case JUNGLE_TEMPLE -> b.contains("bamboo") ? JUNGLE_BAMBOO : JUNGLE_PLAIN;
			case PILLAGER_OUTPOST -> {
				if (b.contains("desert") || b.contains("badlands")) {
					yield OUTPOST_DESERT;
				}
				if (b.contains("savanna")) {
					yield OUTPOST_SAVANNA;
				}
				if (b.contains("cherry")) {
					yield OUTPOST_CHERRY;
				}
				if (b.contains("snowy") || b.contains("frozen") || b.contains("grove") || b.contains("jagged") || b.contains("ice")) {
					yield OUTPOST_SNOWY;
				}
				if (b.contains("taiga")) {
					yield OUTPOST_TAIGA;
				}
				yield OUTPOST_PLAINS;
			}
			case MINESHAFT -> {
				if (b.contains("lush")) {
					yield SHAFT_LUSH;
				}
				if (b.contains("dripstone")) {
					yield SHAFT_DRIPSTONE;
				}
				if (b.contains("snowy") || b.contains("frozen") || b.contains("ice") || b.contains("grove") || b.contains("jagged")) {
					yield SHAFT_ICE;
				}
				if (b.contains("jungle")) {
					yield SHAFT_JUNGLE;
				}
				if (b.contains("desert")) {
					yield SHAFT_DESERT;
				}
				yield y < 0 ? SHAFT_DEEPSLATE : SHAFT_OAK;
			}
		};
	}

	/** Portal em ruinas: 0 normal, 1 frio (bioma nevado: sem magma nem lava, com neve e gelo). */
	public static final int PORTAL_COLD = 1;

	private static int seaVariant(VKind kind, String b) {
		return switch (kind) {
			case MONUMENT -> b.contains("frozen") ? 1 : 0;
			case RUINED_PORTAL, RUINED_PORTAL_MOUNTAIN, RUINED_PORTAL_OCEAN -> b.contains("snowy") || b.contains("frozen") || b.contains("ice")
					|| b.contains("grove") || b.contains("jagged") || b.contains("cold") ? PORTAL_COLD : 0;
			default -> 0;
		};
	}
}
