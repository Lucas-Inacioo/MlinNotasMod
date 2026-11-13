package com.mlin.notas.enchanting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reagentes do encantamento escolhido (logica pura): cada encantamento pede, alem de XP e lapis, um item tematico que
 * obriga a explorar (acucar para Eficiencia, po de blaze para Aspecto Flamejante, escama de tatu da savana para
 * Protecao contra Projeteis...). Quanto mais alto o nivel, mais reagente: o total ate o nivel {@code n} e
 * {@code base * n * (n + 1) / 2} (nivel I = base, nivel V = 15 x base), sempre cabendo numa pilha. Subir um
 * encantamento que o item ja tem cobra so a diferenca, como o XP.
 *
 * <p>Encantamentos sem entrada (de outros mods) usam {@link #FALLBACK}.</p>
 */
public final class EnchantReagents {
	/** Item do reagente ({@code minecraft:sugar}) e quantidade do nivel I. */
	public record Reagent(String item, int base) {
		public Reagent {
			if (base <= 0) {
				throw new IllegalArgumentException("base invalida: " + base);
			}
		}
	}

	/** Reagente de encantamentos desconhecidos (outros mods): fragmento de cristal etereo do End. */
	public static final Reagent FALLBACK = new Reagent("mlinnotas:ether_crystal_shard", 2);

	private static final Map<String, Reagent> REAGENTS = new LinkedHashMap<>();

	static {
		// armadura
		put("protection", "minecraft:iron_ingot", 3);
		put("fire_protection", "minecraft:magma_cream", 2);
		put("feather_falling", "minecraft:feather", 4);
		put("blast_protection", "minecraft:gunpowder", 3);
		put("projectile_protection", "minecraft:armadillo_scute", 1);
		put("respiration", "minecraft:pufferfish", 2);
		put("aqua_affinity", "minecraft:prismarine_shard", 8);
		put("thorns", "minecraft:cactus", 6);
		put("depth_strider", "minecraft:ink_sac", 4);
		put("frost_walker", "minecraft:blue_ice", 4);
		put("soul_speed", "minecraft:soul_soil", 8);
		put("swift_sneak", "minecraft:echo_shard", 1);
		// armas
		put("sharpness", "minecraft:flint", 3);
		put("smite", "minecraft:bone", 3);
		put("bane_of_arthropods", "minecraft:spider_eye", 2);
		put("knockback", "minecraft:slime_ball", 6);
		put("fire_aspect", "minecraft:blaze_powder", 4);
		put("looting", "minecraft:emerald", 4);
		put("sweeping_edge", "minecraft:phantom_membrane", 2);
		put("density", "minecraft:breeze_rod", 1);
		put("breach", "minecraft:breeze_rod", 2);
		put("wind_burst", "minecraft:breeze_rod", 2);
		put("lunge", "minecraft:rabbit_foot", 1);
		// ferramentas
		put("efficiency", "minecraft:sugar", 3);
		put("silk_touch", "minecraft:string", 16);
		put("unbreaking", "minecraft:obsidian", 4);
		put("fortune", "minecraft:gold_ingot", 5);
		// arcos e bestas
		put("power", "minecraft:arrow", 3);
		put("punch", "minecraft:slime_ball", 6);
		put("flame", "minecraft:blaze_powder", 8);
		put("infinity", "minecraft:ender_pearl", 8);
		put("multishot", "minecraft:amethyst_shard", 12);
		put("quick_charge", "minecraft:redstone", 6);
		put("piercing", "minecraft:quartz", 3);
		// pesca e tridente
		put("luck_of_the_sea", "minecraft:prismarine_crystals", 4);
		put("lure", "minecraft:cod", 4);
		put("loyalty", "minecraft:nautilus_shell", 1);
		put("impaling", "minecraft:prismarine_shard", 3);
		put("riptide", "minecraft:glow_ink_sac", 3);
		put("channeling", "minecraft:lightning_rod", 4);
		// tesouros (nao saem da mesa, mas ficam mapeados se um datapack os puser la)
		put("mending", "mlinnotas:void_jelly", 8);
	}

	private EnchantReagents() {
	}

	private static void put(String path, String item, int base) {
		REAGENTS.put("minecraft:" + path, new Reagent(item, base));
	}

	/** Reagente do encantamento {@code enchantmentId} ({@code minecraft:sharpness}); {@link #FALLBACK} se nao mapeado. */
	public static Reagent of(String enchantmentId) {
		return REAGENTS.getOrDefault(enchantmentId, FALLBACK);
	}

	public static boolean mapped(String enchantmentId) {
		return REAGENTS.containsKey(enchantmentId);
	}

	public static Map<String, Reagent> all() {
		return Collections.unmodifiableMap(REAGENTS);
	}

	/** Reagente acumulado do nivel 0 ate {@code level}: {@code base * level * (level + 1) / 2}. */
	public static int total(int base, int level) {
		int n = Math.max(0, level);
		return base * n * (n + 1) / 2;
	}

	/** Reagente para subir de {@code fromLevel} (0 = nao tem) ate {@code toLevel}: so a diferenca. */
	public static int cost(int base, int fromLevel, int toLevel) {
		return Math.max(0, total(base, toLevel) - total(base, Math.max(0, fromLevel)));
	}
}
