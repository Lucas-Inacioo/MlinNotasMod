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
		REPAIR
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
			new Offer("totem", Kind.ITEM, "minecraft:totem_of_undying", 1, 48, VillageLevel.CAPITAL));

	private VillageShop() {
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
