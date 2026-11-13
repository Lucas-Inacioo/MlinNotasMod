package com.mlin.notas.skills;

import com.mlin.notas.tier.Tier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nivel de Mineracao que cada bloco de minerio exige para ser quebrado (logica pura). Blocos fora da tabela nao tem
 * trava. Carvao = Pedra, cobre = Cobre, ferro/lapis = Ferro, ouro/redstone = Ouro, cobalto = Cobalto,
 * diamante/esmeralda = Diamante, detritos ancestrais = Netherite, infernita/endita/astralita = o proprio tier.
 */
public final class OreTiers {
	private static final Map<String, Tier> ORES = new LinkedHashMap<>();

	static {
		both("coal_ore", Tier.STONE);
		both("copper_ore", Tier.COPPER);
		both("iron_ore", Tier.IRON);
		both("lapis_ore", Tier.IRON);
		both("gold_ore", Tier.GOLD);
		ORES.put("minecraft:nether_gold_ore", Tier.GOLD);
		both("redstone_ore", Tier.GOLD);
		ORES.put("mlinnotas:cobalt_ore", Tier.COBALT);
		ORES.put("mlinnotas:deepslate_cobalt_ore", Tier.COBALT);
		both("diamond_ore", Tier.DIAMOND);
		both("emerald_ore", Tier.DIAMOND);
		ORES.put("minecraft:ancient_debris", Tier.NETHERITE);
		ORES.put("mlinnotas:infernite_ore", Tier.INFERNITE);
		ORES.put("mlinnotas:endite_ore", Tier.ENDITE);
		ORES.put("mlinnotas:astralite_ore", Tier.ASTRALITE);
	}

	private OreTiers() {
	}

	private static void both(String vanilla, Tier tier) {
		ORES.put("minecraft:" + vanilla, tier);
		ORES.put("minecraft:deepslate_" + vanilla, tier);
	}

	/** Tier exigido pelo bloco {@code blockId} ou null se nao e minerio travado. */
	public static Tier required(String blockId) {
		return ORES.get(blockId);
	}

	/** Pode quebrar {@code blockId} com Mineracao {@code miningLevel} (1 = Madeira)? */
	public static boolean canBreak(String blockId, int miningLevel) {
		Tier tier = required(blockId);
		return tier == null || miningLevel >= tier.level();
	}

	public static Map<String, Tier> all() {
		return Collections.unmodifiableMap(ORES);
	}
}
