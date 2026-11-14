package com.mlin.notas.village.civic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Colecoes do Museu (logica pura), estilo Stardew Valley: uma peca de cada minerio, lingote, gema, trofeu de chefe,
 * artefato, comida e peixe. Cada colecao completa da recompensa e prosperidade uma vez. O nivel do Museu libera as
 * colecoes (I minerios, lingotes e gemas; II peixes e comidas; III chefes e artefatos). Uma doacao vale em todas as
 * colecoes que pedem aquele item.
 */
public final class MuseumSets {
	public record Reward(String item, int count) {
	}

	public record MuseumSet(String id, int minLevel, List<String> items, int prosperity, List<Reward> rewards) {
		public boolean complete(Set<String> donated) {
			return donated.containsAll(items);
		}

		public int have(Set<String> donated) {
			int n = 0;
			for (String item : items) {
				if (donated.contains(item)) {
					n++;
				}
			}
			return n;
		}
	}

	private static final List<MuseumSet> ALL = List.of(
			new MuseumSet("ores", 1, List.of("minecraft:coal", "minecraft:raw_copper", "minecraft:raw_iron", "minecraft:raw_gold",
					"minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:diamond", "minecraft:emerald", "mlinnotas:raw_cobalt",
					"minecraft:netherite_scrap", "mlinnotas:raw_infernite", "mlinnotas:raw_endite", "mlinnotas:raw_astralite"),
					40, List.of(new Reward("minecraft:emerald", 16), new Reward("minecraft:experience_bottle", 8))),
			new MuseumSet("ingots", 1, List.of("minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot", "mlinnotas:cobalt_ingot",
					"minecraft:netherite_ingot", "mlinnotas:infernite_ingot", "mlinnotas:endite_ingot", "mlinnotas:astralite_ingot",
					"mlinnotas:celestium_ingot"),
					50, List.of(new Reward("minecraft:emerald", 24), new Reward("minecraft:golden_apple", 1))),
			new MuseumSet("gems", 1, List.of("minecraft:diamond", "minecraft:emerald", "minecraft:amethyst_shard", "minecraft:quartz",
					"minecraft:prismarine_crystals", "minecraft:echo_shard", "mlinnotas:ether_crystal_shard"),
					30, List.of(new Reward("minecraft:emerald", 16), new Reward("minecraft:experience_bottle", 12))),
			new MuseumSet("fish", 2, List.of("minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
					"minecraft:nautilus_shell"),
					30, List.of(new Reward("minecraft:emerald", 12), new Reward("minecraft:golden_carrot", 16), new Reward("minecraft:name_tag", 2))),
			new MuseumSet("food", 2, List.of("mlinnotas:feijoada", "mlinnotas:rice_and_beans", "mlinnotas:galinhada", "mlinnotas:pasta",
					"mlinnotas:beef_stew", "mlinnotas:strawberry_pie", "mlinnotas:aged_cheese", "mlinnotas:charque"),
					40, List.of(new Reward("minecraft:emerald", 16), new Reward("minecraft:golden_apple", 2))),
			new MuseumSet("bosses", 3, List.of("mlinnotas:cobalt_heart", "mlinnotas:abyssal_core", "mlinnotas:colossus_ember",
					"mlinnotas:astral_fragment", "mlinnotas:dragon_scale", "minecraft:nether_star", "minecraft:wither_skeleton_skull",
					"minecraft:dragon_head"),
					100, List.of(new Reward("minecraft:emerald", 48), new Reward("minecraft:experience_bottle", 32), new Reward("minecraft:golden_apple", 4))),
			new MuseumSet("artifacts", 3, List.of("mlinnotas:rabbit_foot_charm", "mlinnotas:climbing_glove", "mlinnotas:miner_lantern",
					"mlinnotas:life_amulet", "mlinnotas:feather_charm", "mlinnotas:magnet_ring", "mlinnotas:pocket_watch",
					"mlinnotas:traveler_belt", "mlinnotas:lucky_horseshoe", "mlinnotas:frost_ring"),
					60, List.of(new Reward("minecraft:emerald", 32), new Reward("minecraft:experience_bottle", 24))));

	private MuseumSets() {
	}

	public static List<MuseumSet> all() {
		return ALL;
	}

	public static Optional<MuseumSet> byId(String id) {
		return ALL.stream().filter(s -> s.id().equals(id)).findFirst();
	}

	/** Colecoes liberadas pelo nivel do Museu. */
	public static List<MuseumSet> available(int level) {
		return ALL.stream().filter(s -> s.minLevel() <= level).toList();
	}

	/** Alguma colecao liberada pede este item? */
	public static boolean wanted(String item, int level) {
		return available(level).stream().anyMatch(s -> s.items().contains(item));
	}

	/** Colecoes liberadas, completas e ainda sem recompensa resgatada. */
	public static List<MuseumSet> newlyComplete(Set<String> donated, int level, java.util.Collection<String> claimed) {
		List<MuseumSet> out = new ArrayList<>();
		for (MuseumSet s : available(level)) {
			if (!claimed.contains(s.id()) && s.complete(donated)) {
				out.add(s);
			}
		}
		return out;
	}

	/** Moldeiras que o nivel pode mostrar: a quantidade de itens distintos das colecoes liberadas. */
	public static int distinctItems(int level) {
		return (int) available(level).stream().flatMap(s -> s.items().stream()).distinct().count();
	}
}
