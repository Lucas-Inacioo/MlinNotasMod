package com.mlin.notas.village;

/**
 * Estilo das construcoes da vila, escolhido pelo bioma do centro (logica pura; a paleta de blocos fica em
 * {@code VillagePalette}). O deserto usa telhado plano com parapeito; os demais, telhado de duas aguas.
 */
public enum VillageStyle {
	PLAINS(false), DESERT(true), SAVANNA(false), TAIGA(false), SNOWY(false);

	private final boolean flatRoof;

	VillageStyle(boolean flatRoof) {
		this.flatRoof = flatRoof;
	}

	public boolean flatRoof() {
		return flatRoof;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static VillageStyle byId(String id) {
		for (VillageStyle style : values()) {
			if (style.id().equals(id)) {
				return style;
			}
		}
		return PLAINS;
	}

	/** Estilo pelo id do bioma ({@code minecraft:desert}...); desconhecido = planicie. */
	public static VillageStyle forBiome(String biome) {
		if (biome == null) {
			return PLAINS;
		}
		String b = biome.substring(biome.indexOf(':') + 1);
		if (b.contains("desert") || b.contains("badlands") || b.contains("ashen")) {
			return DESERT;
		}
		if (b.contains("savanna")) {
			return SAVANNA;
		}
		if (b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove") || b.contains("peaks")) {
			return SNOWY;
		}
		if (b.contains("taiga") || b.contains("old_growth") || b.contains("windswept")) {
			return TAIGA;
		}
		return PLAINS;
	}
}
