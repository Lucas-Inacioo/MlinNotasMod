package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sorteio das missoes do dia (logica pura). Tres por dia de jogo, sempre as mesmas para a mesma vila/dia: uma entrega,
 * uma cacada e uma exploracao (no Acampamento, outra entrega). O nivel da vila libera pedidos mais dificeis e
 * recompensas melhores.
 */
public final class BountyBoard {
	public static final int PER_DAY = 3;
	/** Mais que isso nunca ha no quadro (as 3 do dia mais as extras da taverna). */
	public static final int MAX_PER_DAY = 5;

	/** Lugares das missoes de exploracao (o servidor sabe testar cada um). */
	public static final List<String> EXPLORE_KEYS = List.of("desert", "jungle", "snowy", "badlands", "mushroom", "cherry", "swamp",
			"deep_dark", "ruin", "outpost", "nether", "end", "shrine");

	private record Entry(Bounty.Kind kind, String target, int min, int max, int minLevel, int emeralds) {
	}

	private record Reward(String item, int min, int max, int minLevel) {
	}

	private static final List<Entry> ENTRIES = List.of(
			// entregas
			new Entry(Bounty.Kind.DELIVER, "minecraft:wheat", 16, 32, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:bread", 8, 16, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:cobblestone", 48, 64, 0, 2),
			new Entry(Bounty.Kind.DELIVER, "minecraft:oak_log", 16, 32, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:coal", 12, 24, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:leather", 6, 10, 0, 4),
			new Entry(Bounty.Kind.DELIVER, "minecraft:string", 8, 16, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:rotten_flesh", 16, 32, 0, 2),
			new Entry(Bounty.Kind.DELIVER, "minecraft:raw_copper", 16, 24, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:iron_ingot", 6, 12, 1, 5),
			new Entry(Bounty.Kind.DELIVER, "minecraft:bone", 12, 20, 1, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:gunpowder", 6, 10, 1, 5),
			new Entry(Bounty.Kind.DELIVER, "minecraft:white_wool", 8, 16, 1, 3),
			new Entry(Bounty.Kind.DELIVER, "minecraft:amethyst_shard", 8, 16, 2, 5),
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:raw_cobalt", 4, 8, 2, 8),
			// cozinha (food/): horta, pratos e conservas
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:tomato", 12, 24, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:corn", 12, 24, 0, 3),
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:rice_and_beans", 3, 6, 1, 6),
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:strawberry_jam", 3, 6, 1, 6),
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:feijoada", 2, 4, 2, 9),
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:aged_cheese", 4, 8, 2, 8),
			new Entry(Bounty.Kind.DELIVER, "minecraft:ender_pearl", 3, 6, 3, 10),
			new Entry(Bounty.Kind.DELIVER, "minecraft:blaze_rod", 3, 6, 3, 10),
			new Entry(Bounty.Kind.DELIVER, "mlinnotas:raw_infernite", 3, 6, 4, 14),
			// cacadas
			new Entry(Bounty.Kind.HUNT, "minecraft:zombie", 8, 12, 0, 4),
			new Entry(Bounty.Kind.HUNT, "minecraft:skeleton", 6, 10, 0, 4),
			new Entry(Bounty.Kind.HUNT, "minecraft:spider", 6, 10, 0, 4),
			new Entry(Bounty.Kind.HUNT, "minecraft:creeper", 3, 6, 0, 5),
			new Entry(Bounty.Kind.HUNT, "minecraft:pillager", 4, 6, 1, 7),
			new Entry(Bounty.Kind.HUNT, "minecraft:drowned", 4, 8, 1, 6),
			new Entry(Bounty.Kind.HUNT, "minecraft:enderman", 2, 4, 2, 8),
			new Entry(Bounty.Kind.HUNT, "minecraft:witch", 1, 2, 2, 8),
			new Entry(Bounty.Kind.HUNT, "minecraft:blaze", 3, 5, 3, 10),
			new Entry(Bounty.Kind.HUNT, "mlinnotas:magma_slug", 3, 5, 3, 10),
			new Entry(Bounty.Kind.HUNT, "minecraft:wither_skeleton", 2, 4, 4, 14),
			// exploracao (count = 1)
			new Entry(Bounty.Kind.EXPLORE, "desert", 1, 1, 1, 6),
			new Entry(Bounty.Kind.EXPLORE, "jungle", 1, 1, 1, 8),
			new Entry(Bounty.Kind.EXPLORE, "snowy", 1, 1, 1, 6),
			new Entry(Bounty.Kind.EXPLORE, "swamp", 1, 1, 1, 6),
			new Entry(Bounty.Kind.EXPLORE, "ruin", 1, 1, 1, 8),
			new Entry(Bounty.Kind.EXPLORE, "cherry", 1, 1, 2, 8),
			new Entry(Bounty.Kind.EXPLORE, "badlands", 1, 1, 2, 9),
			new Entry(Bounty.Kind.EXPLORE, "outpost", 1, 1, 2, 10),
			new Entry(Bounty.Kind.EXPLORE, "mushroom", 1, 1, 3, 12),
			new Entry(Bounty.Kind.EXPLORE, "shrine", 1, 1, 2, 10),
			new Entry(Bounty.Kind.EXPLORE, "nether", 1, 1, 2, 10),
			new Entry(Bounty.Kind.EXPLORE, "deep_dark", 1, 1, 3, 14),
			new Entry(Bounty.Kind.EXPLORE, "end", 1, 1, 4, 18));

	private static final List<Reward> REWARDS = List.of(
			new Reward("minecraft:bread", 4, 8, 0),
			new Reward("minecraft:torch", 16, 24, 0),
			new Reward("minecraft:iron_ingot", 2, 4, 0),
			new Reward("mlinnotas:return_scroll", 1, 1, 0),
			new Reward("minecraft:golden_carrot", 4, 8, 1),
			new Reward("mlinnotas:feijoada", 1, 2, 1),
			new Reward("minecraft:ender_pearl", 1, 2, 1),
			new Reward("minecraft:experience_bottle", 4, 8, 1),
			new Reward("mlinnotas:raw_cobalt", 2, 3, 1),
			new Reward("mlinnotas:cobalt_ingot", 1, 3, 2),
			new Reward("minecraft:diamond", 1, 1, 2),
			new Reward("minecraft:name_tag", 1, 1, 2),
			new Reward("minecraft:golden_apple", 1, 2, 3),
			new Reward("minecraft:diamond", 2, 3, 3),
			new Reward("mlinnotas:backpack_upgrade_size", 1, 1, 3),
			new Reward("minecraft:netherite_scrap", 1, 1, 4),
			new Reward("minecraft:totem_of_undying", 1, 1, 4));

	private BountyBoard() {
	}

	/** Missoes do dia {@code day} para a vila de semente {@code seed} no nivel {@code level}. */
	public static List<Bounty> roll(long seed, long day, VillageLevel level) {
		return roll(seed, day, level, 0);
	}

	/** Como {@link #roll(long, long, VillageLevel)} mais {@code extra} missoes da taverna (entrega e cacada alternadas). */
	public static List<Bounty> roll(long seed, long day, VillageLevel level, int extra) {
		Random random = new Random(seed * 0x9E3779B97F4A7C15L + day * 0xC2B2AE3D27D4EB4FL + level.ordinal());
		List<Bounty> out = new ArrayList<>();
		Bounty.Kind third = level.ordinal() >= 1 ? Bounty.Kind.EXPLORE : Bounty.Kind.DELIVER;
		List<Bounty.Kind> kinds = new ArrayList<>(List.of(Bounty.Kind.DELIVER, Bounty.Kind.HUNT, third));
		for (int i = 0; i < Math.min(extra, MAX_PER_DAY - PER_DAY); i++) {
			kinds.add(i % 2 == 0 ? Bounty.Kind.HUNT : Bounty.Kind.DELIVER);
		}
		for (Bounty.Kind kind : kinds) {
			List<Entry> options = new ArrayList<>();
			for (Entry entry : ENTRIES) {
				if (entry.kind == kind && entry.minLevel <= level.ordinal() && out.stream().noneMatch(b -> b.target().equals(entry.target))) {
					options.add(entry);
				}
			}
			Entry entry = options.get(random.nextInt(options.size()));
			int count = entry.min + random.nextInt(entry.max - entry.min + 1);
			// esmeraldas crescem um pouco com o tamanho do pedido e com o nivel
			double scale = (double) count / Math.max(1, entry.max);
			int emeralds = Math.max(1, (int) Math.round(entry.emeralds * (0.7 + 0.3 * scale)) + level.ordinal());
			List<Reward> rewards = REWARDS.stream().filter(r -> r.minLevel <= level.ordinal()).toList();
			// recompensas do nivel atual sao mais provaveis
			Reward reward = rewards.get(Math.max(random.nextInt(rewards.size()), random.nextInt(rewards.size())));
			int rewardCount = reward.min + random.nextInt(reward.max - reward.min + 1);
			int prosperity = 10 + entry.minLevel * 3 + (kind == Bounty.Kind.EXPLORE ? 5 : 0);
			out.add(new Bounty(kind, entry.target, count, emeralds, reward.item, rewardCount, prosperity));
		}
		return out;
	}
}
