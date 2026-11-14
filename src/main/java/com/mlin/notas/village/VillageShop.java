package com.mlin.notas.village;

import java.util.List;

/**
 * Mercado da vila (logica pura): o que o quadro vende por esmeraldas em cada nivel. Itens comuns, minerios do mod,
 * mapas para estruturas do mod, livros encantados, reparo do item na mao e raridades no nivel Capital.
 */
public final class VillageShop {
	public enum Kind {
		/** {@code target} = id do item, {@code count} = quantidade. */
		ITEM,
		/** Mapa de exploracao para a estrutura mais perto da tag {@code target}. */
		MAP,
		/** Livro com um encantamento aleatorio da mesa (nivel ate 3). */
		BOOK,
		/** Livro com um encantamento de tesouro (Remendo, Passos Gelados...). */
		RARE_BOOK,
		/** Conserta o item da mao principal; o preco cresce com o desgaste. */
		REPAIR,
		/** Quarto na estalagem: o ponto de renascer do jogador passa a ser a estalagem. */
		LODGING,
		/** Flechas com pocao ({@code target} = id da pocao, {@code count} = flechas); a flecharia baixa o preco. */
		TIPPED,
		/** Boato da taverna ({@code target} = categoria de {@link Rumors}): revela uma estrutura ainda nao achada. */
		RUMOR
	}

	public record Offer(String id, Kind kind, String target, int count, int price, VillageLevel level) {
	}

	public static final List<Offer> OFFERS = List.of(
			new Offer("bread", Kind.ITEM, "minecraft:bread", 6, 1, VillageLevel.CAMP),
			new Offer("torches", Kind.ITEM, "minecraft:torch", 16, 1, VillageLevel.CAMP),
			new Offer("bed", Kind.ITEM, "minecraft:white_bed", 1, 3, VillageLevel.CAMP),
			new Offer("tomato_seeds", Kind.ITEM, "mlinnotas:tomato_seeds", 4, 1, VillageLevel.CAMP),
			new Offer("cabbage_seeds", Kind.ITEM, "mlinnotas:cabbage_seeds", 4, 1, VillageLevel.CAMP),
			new Offer("cooking_pot", Kind.ITEM, "mlinnotas:cooking_pot", 1, 4, VillageLevel.HAMLET),
			new Offer("feijoada", Kind.ITEM, "mlinnotas:feijoada", 2, 3, VillageLevel.VILLAGE),
			new Offer("iron", Kind.ITEM, "minecraft:iron_ingot", 3, 4, VillageLevel.HAMLET),
			new Offer("return_scroll", Kind.ITEM, "mlinnotas:return_scroll", 1, 5, VillageLevel.HAMLET),
			new Offer("ruin_map", Kind.MAP, "mlinnotas:on_village_maps/ruins", 1, 6, VillageLevel.HAMLET),
			new Offer("raw_cobalt", Kind.ITEM, "mlinnotas:raw_cobalt", 2, 8, VillageLevel.VILLAGE),
			new Offer("book", Kind.BOOK, "", 1, 10, VillageLevel.VILLAGE),
			new Offer("repair", Kind.REPAIR, "", 1, 1, VillageLevel.VILLAGE),
			new Offer("shrine_map", Kind.MAP, "mlinnotas:on_village_maps/cobalt_shrine", 1, 12, VillageLevel.VILLAGE),
			new Offer("diamond", Kind.ITEM, "minecraft:diamond", 1, 20, VillageLevel.TOWN),
			new Offer("golden_carrot", Kind.ITEM, "minecraft:golden_carrot", 8, 3, VillageLevel.TOWN),
			new Offer("rare_book", Kind.RARE_BOOK, "", 1, 22, VillageLevel.TOWN),
			new Offer("abyss_map", Kind.MAP, "mlinnotas:on_village_maps/abyssal_sanctum", 1, 18, VillageLevel.TOWN),
			new Offer("xp", Kind.ITEM, "minecraft:experience_bottle", 8, 6, VillageLevel.CAPITAL),
			new Offer("netherite_scrap", Kind.ITEM, "minecraft:netherite_scrap", 1, 48, VillageLevel.CAPITAL),
			new Offer("totem", Kind.ITEM, "minecraft:totem_of_undying", 1, 48, VillageLevel.CAPITAL),
			// construcoes com nivel (Vilas 2.0, fase 4): exigencias em REQUIRES
			new Offer("cobalt_pickaxe", Kind.ITEM, "mlinnotas:cobalt_pickaxe", 1, 24, VillageLevel.VILLAGE),
			new Offer("cobalt_sword", Kind.ITEM, "mlinnotas:cobalt_sword", 1, 20, VillageLevel.VILLAGE),
			new Offer("cobalt_ingot", Kind.ITEM, "mlinnotas:cobalt_ingot", 1, 10, VillageLevel.VILLAGE),
			new Offer("lodging", Kind.LODGING, "", 1, 2, VillageLevel.HAMLET),
			// oficios e servicos (Vilas 2.0, fase 6)
			new Offer("backpack", Kind.ITEM, "mlinnotas:backpack", 1, 18, VillageLevel.VILLAGE),
			new Offer("tipped_poison", Kind.TIPPED, "minecraft:poison", 8, 6, VillageLevel.VILLAGE),
			new Offer("tipped_slowness", Kind.TIPPED, "minecraft:slowness", 8, 6, VillageLevel.VILLAGE),
			new Offer("tipped_harming", Kind.TIPPED, "minecraft:harming", 8, 9, VillageLevel.VILLAGE),
			new Offer("rumor_ruin", Kind.RUMOR, "ruin", 1, 4, VillageLevel.VILLAGE),
			new Offer("rumor_vanilla", Kind.RUMOR, "vanilla", 1, 8, VillageLevel.VILLAGE),
			new Offer("rumor_dungeon", Kind.RUMOR, "dungeon", 1, 12, VillageLevel.VILLAGE),
			new Offer("rumor_lair", Kind.RUMOR, "lair", 1, 16, VillageLevel.VILLAGE));

	/**
	 * Oferta que so aparece com uma construcao funcionando no nivel ({@code tipo:nivel}): forja I conserta, forja II
	 * vende ferramentas de cobalto, mercado III livros raros, estalagem II hospeda (ponto de renascer).
	 */
	public static final java.util.Map<String, String> REQUIRES = java.util.Map.ofEntries(
			java.util.Map.entry("repair", "smithy:1"),
			java.util.Map.entry("cobalt_pickaxe", "smithy:2"),
			java.util.Map.entry("cobalt_sword", "smithy:2"),
			java.util.Map.entry("cobalt_ingot", "smithy:2"),
			java.util.Map.entry("rare_book", "market:3"),
			java.util.Map.entry("lodging", "inn:2"),
			java.util.Map.entry("backpack", "tailor:1"),
			java.util.Map.entry("tipped_poison", "fletcher:1"),
			java.util.Map.entry("tipped_slowness", "fletcher:1"),
			java.util.Map.entry("tipped_harming", "fletcher:2"),
			java.util.Map.entry("rumor_ruin", "tavern:1"),
			java.util.Map.entry("rumor_vanilla", "tavern:1"),
			java.util.Map.entry("rumor_dungeon", "tavern:1"),
			java.util.Map.entry("rumor_lair", "tavern:1"));

	private VillageShop() {
	}

	/** Exigencia de construcao da oferta ({@code tipo:nivel}) ou null. */
	public static String requirement(Offer offer) {
		return REQUIRES.get(offer.id());
	}

	/** A vila tem o que a oferta exige (nivel da vila e construcao funcionando no nivel). */
	public static boolean available(Offer offer, VillageState state) {
		if (state.level().ordinal() < offer.level().ordinal()) {
			return false;
		}
		String req = requirement(offer);
		if (req == null) {
			return true;
		}
		String kind = req.substring(0, req.indexOf(':'));
		return VillageBuildings.level(state, kind) >= VillageLevel.requiredLevel(req);
	}

	/**
	 * Conserto na forja, sem bigorna: {esmeraldas, material}. Esmeraldas como no reparo antigo (1 a cada 20% de
	 * desgaste) menos 1 por nivel da forja acima do I (minimo 1); material (o de conserto do item) 1 a cada 25% de
	 * desgaste, menos 1 a partir da forja III (minimo 1). {0, 0} = nada a consertar.
	 */
	public static int[] forgeRepair(int damage, int maxDamage, int smithyLevel) {
		int base = repairPrice(damage, maxDamage);
		if (base == 0) {
			return new int[] {0, 0};
		}
		int level = Math.max(1, smithyLevel);
		int emeralds = Math.max(1, base - (level - 1));
		int material = Math.max(1, (int) Math.ceil(damage * 4.0 / maxDamage) - (level >= 3 ? 1 : 0));
		return new int[] {emeralds, material};
	}

	/** Preco da oferta com os descontos das construcoes (alfaiate na mochila, flecheiro nas flechas com pocao). */
	public static int price(Offer offer, VillageState state) {
		return CraftsRules.price(offer.id(), offer.price(), java.util.Map.of("tailor", VillageBuildings.level(state, "tailor"), "fletcher",
				VillageBuildings.level(state, "fletcher")));
	}

	public static Offer byId(String id) {
		for (Offer offer : OFFERS) {
			if (offer.id().equals(id)) {
				return offer;
			}
		}
		return null;
	}

	/** Preco do reparo: 1 esmeralda a cada 20% de desgaste (minimo 1, maximo 5); 0 = nada a consertar. */
	public static int repairPrice(int damage, int maxDamage) {
		if (damage <= 0 || maxDamage <= 0) {
			return 0;
		}
		return Math.max(1, Math.min(5, (int) Math.ceil(damage * 5.0 / maxDamage)));
	}
}
