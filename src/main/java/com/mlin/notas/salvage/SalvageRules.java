package com.mlin.notas.salvage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reciclagem de equipamento na fornalha (logica pura): ferramenta, arma ou armadura de metal devolve parte do material
 * conforme a durabilidade que sobrou.
 *
 * <p>Conta: {@code unidades = pecas * 0,5 * fracaoDaDurabilidade} (pecas = o que a receita gasta: picareta 3, espada 2,
 * peitoral 8...). Metais com pepita (cobre, ferro, ouro) devolvem tudo em pepitas ({@code floor(unidades * 9)}, no
 * minimo 1): a fornalha tem uma saida so, e pepitas sempre do mesmo tipo empilham na saida e nao travam a fila. Sem
 * pepita (cobalto, diamante) devolve {@code floor(unidades)} lingotes/gemas; se der 0 a fornalha nao aceita a peca.
 * Netherite devolve sucata (1 lingote = 4 sucatas, entao ate 2). Os niveis da ferraria (infernita..celestio) devolvem no
 * maximo o 1 lingote que a melhoria custou, so com metade da durabilidade ou mais (nunca a peca anterior da cadeia).
 * Diamante, netherite e os niveis da ferraria so no alto-forno.</p>
 *
 * <p>Teto de progressao: no maximo metade do material com a peca nova; um peitoral de diamante achado num bau devolve
 * no maximo 4 diamantes.</p>
 */
public final class SalvageRules {
	/** Fracao do material devolvida com a durabilidade cheia. */
	public static final double RETURN_FRACTION = 0.5;
	/** Pepitas por lingote. */
	public static final int NUGGETS_PER_UNIT = 9;
	/** Sucatas de netherite por lingote. */
	public static final int SCRAP_PER_INGOT = 4;
	/** Durabilidade minima (fracao) para os niveis da ferraria devolverem o lingote. */
	public static final double SMITHING_MIN_FRACTION = 0.5;
	/** XP extra maximo por peca encantada. */
	public static final int MAX_ENCHANT_XP = 10;

	/** Como o material volta. */
	public enum Kind {
		/** Metal com pepita: tudo em pepitas. */
		NUGGETS,
		/** Lingote ou gema inteira (sem pepita). */
		UNITS,
		/** Netherite: sucata. */
		SCRAP,
		/** Nivel da ferraria: no maximo 1 lingote. */
		SMITHING
	}

	/** Materiais reciclaveis, com o prefixo dos ids dos itens. */
	public enum Material {
		COPPER("copper", "minecraft:copper_ingot", "minecraft:copper_nugget", Kind.NUGGETS, false, 0.1F),
		IRON("iron", "minecraft:iron_ingot", "minecraft:iron_nugget", Kind.NUGGETS, false, 0.1F),
		GOLD("golden", "minecraft:gold_ingot", "minecraft:gold_nugget", Kind.NUGGETS, false, 0.1F),
		COBALT("cobalt", "mlinnotas:cobalt_ingot", null, Kind.UNITS, false, 0.2F),
		DIAMOND("diamond", "minecraft:diamond", null, Kind.UNITS, true, 0.3F),
		NETHERITE("netherite", "minecraft:netherite_ingot", "minecraft:netherite_scrap", Kind.SCRAP, true, 0.5F),
		INFERNITE("infernite", "mlinnotas:infernite_ingot", null, Kind.SMITHING, true, 0.5F),
		ENDITE("endite", "mlinnotas:endite_ingot", null, Kind.SMITHING, true, 0.5F),
		ASTRALITE("astralite", "mlinnotas:astralite_ingot", null, Kind.SMITHING, true, 0.5F),
		CELESTIUM("celestium", "mlinnotas:celestium_ingot", null, Kind.SMITHING, true, 0.5F);

		private final String prefix;
		private final String unit;
		private final String small;
		private final Kind kind;
		private final boolean blastOnly;
		private final float experience;

		Material(String prefix, String unit, String small, Kind kind, boolean blastOnly, float experience) {
			this.prefix = prefix;
			this.unit = unit;
			this.small = small;
			this.kind = kind;
			this.blastOnly = blastOnly;
			this.experience = experience;
		}

		/** Prefixo do id do item: {@code golden} em {@code golden_sword}. */
		public String prefix() {
			return prefix;
		}

		/** Lingote/gema do material. */
		public String unit() {
			return unit;
		}

		/** Item que sai da fornalha: pepita, sucata, lingote ou gema. */
		public String output() {
			return kind == Kind.NUGGETS || kind == Kind.SCRAP ? small : unit;
		}

		public Kind kind() {
			return kind;
		}

		/** So no alto-forno (diamante, netherite e niveis da ferraria). */
		public boolean blastOnly() {
			return blastOnly;
		}

		/** XP da receita por peca (sem encantamentos). */
		public float experience() {
			return experience;
		}
	}

	/** Pecas e quantas unidades de material a receita gasta (vanilla e classes de arma do mod). */
	public static final Map<String, Integer> PIECES = pieces();

	private static Map<String, Integer> pieces() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("sword", 2);
		map.put("pickaxe", 3);
		map.put("axe", 3);
		map.put("shovel", 1);
		map.put("hoe", 2);
		map.put("spear", 1);
		map.put("helmet", 5);
		map.put("chestplate", 8);
		map.put("leggings", 7);
		map.put("boots", 4);
		// classes de arma do combate (combat/WeaponClass; receitas em scripts/CombatContent.java)
		map.put("greatsword", 4);
		map.put("dagger", 1);
		map.put("warhammer", 5);
		map.put("glaive", 3);
		map.put("rapier", 2);
		// sem receita de mesa (achados): valor parecido com o de uma armadura
		map.put("horse_armor", 6);
		map.put("nautilus_armor", 5);
		return java.util.Collections.unmodifiableMap(map);
	}

	/** Cota de malha (achada, feita de correntes): metade da armadura de ferro equivalente. */
	private static final Map<String, Integer> CHAINMAIL = Map.of("helmet", 2, "chestplate", 4, "leggings", 3, "boots", 2);

	/** Peca reconhecida: material e unidades que a receita gasta. */
	public record Piece(Material material, int units) {
	}

	/** O que sai da fornalha. */
	public record Yield(String item, int count) {
	}

	private SalvageRules() {
	}

	/** Peca do id ({@code minecraft:iron_pickaxe}, {@code mlinnotas:cobalt_rapier}); vazio se nao for reciclavel. */
	public static Optional<Piece> piece(String itemId) {
		int colon = itemId.indexOf(':');
		String namespace = colon < 0 ? "minecraft" : itemId.substring(0, colon);
		String path = colon < 0 ? itemId : itemId.substring(colon + 1);
		if (!namespace.equals("minecraft") && !namespace.equals("mlinnotas")) {
			return Optional.empty();
		}
		if (path.startsWith("chainmail_")) {
			Integer units = CHAINMAIL.get(path.substring("chainmail_".length()));
			return units == null ? Optional.empty() : Optional.of(new Piece(Material.IRON, units));
		}
		for (Material material : Material.values()) {
			if (!path.startsWith(material.prefix() + "_")) {
				continue;
			}
			Integer units = PIECES.get(path.substring(material.prefix().length() + 1));
			if (units == null) {
				return Optional.empty();
			}
			return Optional.of(new Piece(material, units));
		}
		return Optional.empty();
	}

	/** Fracao da durabilidade que sobrou, de 0 a 1 (item sem durabilidade = cheio). */
	public static double durabilityFraction(int damage, int maxDamage) {
		if (maxDamage <= 0) {
			return 1.0;
		}
		return Math.clamp((double) (maxDamage - damage) / maxDamage, 0.0, 1.0);
	}

	/** Resultado da reciclagem; contagem 0 = a fornalha nao aceita a peca. */
	public static Yield yield(Piece piece, int damage, int maxDamage) {
		Material material = piece.material();
		double fraction = durabilityFraction(damage, maxDamage);
		double units = piece.units() * RETURN_FRACTION * fraction;
		int count = switch (material.kind()) {
			case NUGGETS -> Math.max(1, (int) Math.floor(units * NUGGETS_PER_UNIT + 1e-9));
			case UNITS -> (int) Math.floor(units + 1e-9);
			case SCRAP -> (int) Math.floor(SCRAP_PER_INGOT * RETURN_FRACTION * fraction + 1e-9);
			case SMITHING -> fraction + 1e-9 >= SMITHING_MIN_FRACTION ? 1 : 0;
		};
		return new Yield(material.output(), count);
	}

	/** XP extra de uma peca encantada (soma dos niveis dos encantamentos, com teto). */
	public static int enchantXp(int totalEnchantLevels) {
		return Math.clamp(totalEnchantLevels, 0, MAX_ENCHANT_XP);
	}

	/** Ids de todas as pecas de um material, na ordem de {@link #PIECES} (mais a cota de malha no ferro). */
	public static List<String> itemIds(Material material, java.util.function.Predicate<String> exists) {
		List<String> out = new ArrayList<>();
		for (String suffix : PIECES.keySet()) {
			for (String namespace : List.of("minecraft", "mlinnotas")) {
				String id = namespace + ":" + material.prefix() + "_" + suffix;
				if (exists.test(id)) {
					out.add(id);
				}
			}
		}
		if (material == Material.IRON) {
			for (String suffix : CHAINMAIL.keySet()) {
				String id = "minecraft:chainmail_" + suffix;
				if (exists.test(id)) {
					out.add(id);
				}
			}
		}
		return out;
	}
}
