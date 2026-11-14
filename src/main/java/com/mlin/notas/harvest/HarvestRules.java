package com.mlin.notas.harvest;

import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.Tier;

import java.util.List;
import java.util.Map;

/**
 * Regras da colheita com botao direito (logica pura, sem classes do Minecraft): quais plantas colhem,
 * qual drop e a semente replantada e o tamanho da area colhida por nivel de enxada.
 */
public final class HarvestRules {
	/** Planta colhivel -> item que volta para o chao como semente (o proprio item do bloco no vanilla). */
	private static final Map<String, String> SEEDS = new java.util.HashMap<>(Map.of(
			"minecraft:wheat", "minecraft:wheat_seeds",
			"minecraft:carrots", "minecraft:carrot",
			"minecraft:potatoes", "minecraft:potato",
			"minecraft:beetroots", "minecraft:beetroot_seeds",
			"minecraft:nether_wart", "minecraft:nether_wart",
			"minecraft:cocoa", "minecraft:cocoa_beans"));

	static {
		// culturas da cozinha (food/FoodCatalog): replanta com a semente ou com o proprio produto (cebola, alho...)
		for (com.mlin.notas.food.FoodCatalog.Crop crop : com.mlin.notas.food.FoodCatalog.CROPS) {
			SEEDS.put("mlinnotas:" + crop.blockName(), "mlinnotas:" + crop.seed());
		}
	}

	private HarvestRules() {
	}

	/** O bloco e uma planta que este mod colhe e replanta (arbusto de frutas doces fica de fora). */
	public static boolean isHarvestable(String blockId) {
		return SEEDS.containsKey(blockId);
	}

	/** Item consumido dos drops para replantar, ou null se o bloco nao for colhivel. */
	public static String seedFor(String blockId) {
		return SEEDS.get(blockId);
	}

	/** Planta madura: idade no maximo. */
	public static boolean isMature(int age, int maxAge) {
		return maxAge > 0 && age >= maxAge;
	}

	/** Indice do primeiro drop que e a semente, ou -1 (entao a planta nao e replantada). */
	public static int seedIndex(List<String> dropIds, String seedId) {
		return seedId == null ? -1 : dropIds.indexOf(seedId);
	}

	/**
	 * Raio da area colhida (0 = so o bloco clicado): madeira/pedra 1x1, cobre/ferro 3x3,
	 * ouro..diamante 5x5, netherite e acima 7x7.
	 */
	public static int radius(Tier hoeTier) {
		if (hoeTier == null) {
			return 0;
		}
		if (hoeTier.ordinal() >= Tier.NETHERITE.ordinal()) {
			return 3;
		}
		if (hoeTier.ordinal() >= Tier.GOLD.ordinal()) {
			return 2;
		}
		if (hoeTier.ordinal() >= Tier.COPPER.ordinal()) {
			return 1;
		}
		return 0;
	}

	/** Lado do quadrado colhido. */
	public static int side(Tier hoeTier) {
		return radius(hoeTier) * 2 + 1;
	}

	/** Nivel da enxada pelo id do item ({@code minecraft:golden_hoe}, {@code mlinnotas:cobalt_hoe}), ou null se nao for enxada. */
	public static Tier hoeTier(String itemId) {
		for (Tier tier : Tier.values()) {
			String namespace = tier.vanilla() ? "minecraft" : "mlinnotas";
			if (itemId.equals(namespace + ":" + GearKind.HOE.itemName(tier))) {
				return tier;
			}
		}
		return null;
	}
}
