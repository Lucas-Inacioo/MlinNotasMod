package com.mlin.notas.village.civic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Regras das expedicoes da Guilda dos Exploradores (logica pura): destinos, quanto dura a viagem (1 a 3 dias de jogo,
 * menos com equipamento melhor e comida), o que o explorador traz (tabela por destino, ferramenta, arma e nivel de
 * Mineracao do mundo) e o desgaste do equipamento. Minerio acima do nivel de Mineracao do mundo (e acima da ferramenta)
 * nunca volta, e nenhum item de chefe entra nas tabelas.
 */
public final class ExpeditionRules {
	public enum Destination {
		MINE, HUNT, FOREST, FISHING, NETHER, END;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** So abre depois que um jogador visitou a dimensao. */
		public boolean needsVisit() {
			return this == NETHER || this == END;
		}

		public static Destination byId(String id) {
			for (Destination d : values()) {
				if (d.id().equals(id)) {
					return d;
				}
			}
			return null;
		}
	}

	/** Equipamento do explorador: nivel da ferramenta e da arma (-1 = nada), nivel de cada peca de armadura (-1 = vazio), comida. */
	public record Gear(int tool, int weapon, int[] armor, int food) {
		public static final Gear NONE = new Gear(-1, -1, new int[] {-1, -1, -1, -1}, 0);

		/** Soma dos niveis (+1 por peca, para madeira contar mais que nada). */
		public int score() {
			int s = (tool + 1) + (weapon + 1);
			for (int a : armor) {
				s += a + 1;
			}
			return s;
		}

		public boolean fed() {
			return food > 0;
		}
	}

	/** Uma linha da tabela: item, quantidade, peso, nivel de minerio (-1 = nao e minerio) e nivel de arma exigido (-1 = nenhum). */
	public record Entry(String item, int min, int max, int weight, int oreTier, int weaponTier) {
	}

	public record Drop(String item, int count) {
	}

	/** Encaixes de explorador simultaneos = nivel da Guilda. */
	public static int maxExplorers(int guildLevel) {
		return Math.max(1, guildLevel);
	}

	/** Dias de viagem (1..3): 3 sem equipamento, 2 com equipamento razoavel, 1 com equipamento completo e comida. */
	public static int durationDays(Gear gear) {
		int score = gear.score() + (gear.fed() ? 4 : 0);
		if (score >= 36) {
			return 1;
		}
		if (score >= 14) {
			return 2;
		}
		return 3;
	}

	/** Multiplicador das quantidades (1,0 sem equipamento ate ~2,6 com tudo no celestio). */
	public static double lootMultiplier(Gear gear) {
		double m = 1.0 + Math.min(66, gear.score()) / 66.0 * 1.5;
		return gear.fed() ? m + 0.1 : m;
	}

	/** Sorteios de uma viagem: 3 mais 2 por nivel da Guilda. */
	public static int rolls(int guildLevel) {
		return 3 + 2 * Math.max(1, guildLevel);
	}

	/** Maior nivel de minerio que volta: o menor entre a Mineracao do mundo e a ferramenta (sem ferramenta = pedra). */
	public static int oreCap(int miningLevel, int toolTier) {
		return Math.min(miningLevel, Math.max(1, toolTier));
	}

	/** Desgaste de cada peca de equipamento por viagem (a peca nunca quebra: sobra 1 de durabilidade). */
	public static int wearPerTrip(int days) {
		return 10 + 6 * days;
	}

	/** Comidas gastas na viagem. */
	public static int foodUsed(int days) {
		return days;
	}

	private static Entry e(String item, int min, int max, int weight) {
		return new Entry(item, min, max, weight, -1, -1);
	}

	private static Entry ore(String item, int min, int max, int weight, int tier) {
		return new Entry(item, min, max, weight, tier, -1);
	}

	private static Entry rare(String item, int min, int max, int weight, int weaponTier) {
		return new Entry(item, min, max, weight, -1, weaponTier);
	}

	private static final Map<Destination, List<Entry>> TABLES = new LinkedHashMap<>();

	static {
		TABLES.put(Destination.MINE, List.of(e("minecraft:cobblestone", 8, 24, 40), ore("minecraft:coal", 2, 6, 30, 1),
				ore("minecraft:raw_copper", 2, 5, 20, 2), ore("minecraft:raw_iron", 2, 4, 20, 3), ore("minecraft:lapis_lazuli", 2, 6, 10, 3),
				ore("minecraft:raw_gold", 1, 3, 10, 4), ore("minecraft:redstone", 3, 8, 10, 4), ore("mlinnotas:raw_cobalt", 1, 3, 6, 5),
				ore("minecraft:diamond", 1, 2, 4, 6), ore("minecraft:emerald", 1, 2, 4, 6)));
		TABLES.put(Destination.HUNT, List.of(e("minecraft:rotten_flesh", 2, 6, 30), e("minecraft:bone", 2, 5, 25), e("minecraft:string", 2, 4, 20),
				e("minecraft:arrow", 3, 8, 15), e("minecraft:leather", 1, 3, 15), e("minecraft:beef", 2, 4, 15), e("minecraft:gunpowder", 1, 4, 12),
				e("minecraft:spider_eye", 1, 2, 10), rare("minecraft:ender_pearl", 1, 1, 3, 3), rare("minecraft:slime_ball", 1, 3, 6, 1)));
		TABLES.put(Destination.FOREST, List.of(e("minecraft:oak_log", 8, 24, 30), e("minecraft:birch_log", 6, 16, 20), e("minecraft:spruce_log", 6, 16, 20),
				e("minecraft:stick", 4, 10, 25), e("minecraft:apple", 1, 3, 15), e("minecraft:sweet_berries", 2, 6, 15), e("minecraft:oak_sapling", 1, 3, 15),
				e("minecraft:honey_bottle", 1, 1, 4), e("minecraft:mushroom_stem", 1, 3, 5)));
		TABLES.put(Destination.FISHING, List.of(e("minecraft:cod", 1, 4, 30), e("minecraft:salmon", 1, 3, 25), e("minecraft:tropical_fish", 1, 2, 8),
				e("minecraft:pufferfish", 1, 1, 6), e("minecraft:ink_sac", 1, 3, 10), e("minecraft:lily_pad", 1, 2, 8),
				e("minecraft:name_tag", 1, 1, 2), e("minecraft:nautilus_shell", 1, 1, 1)));
		TABLES.put(Destination.NETHER, List.of(e("minecraft:netherrack", 8, 20, 25), e("minecraft:nether_wart", 2, 6, 20), e("minecraft:quartz", 3, 8, 20),
				e("minecraft:glowstone_dust", 2, 6, 15), e("minecraft:gold_nugget", 4, 12, 15), e("minecraft:magma_cream", 1, 2, 8),
				rare("minecraft:blaze_rod", 1, 2, 8, 3), ore("minecraft:netherite_scrap", 1, 1, 2, 7), ore("mlinnotas:raw_infernite", 1, 2, 3, 8)));
		TABLES.put(Destination.END, List.of(e("minecraft:end_stone", 8, 16, 25), e("minecraft:chorus_fruit", 2, 6, 20), e("minecraft:purpur_block", 4, 12, 15),
				e("minecraft:popped_chorus_fruit", 2, 6, 10), rare("minecraft:ender_pearl", 1, 3, 12, 3), ore("mlinnotas:raw_endite", 1, 2, 4, 9),
				ore("mlinnotas:raw_astralite", 1, 2, 2, 10)));
	}

	private ExpeditionRules() {
	}

	/** Tabela crua do destino. */
	public static List<Entry> table(Destination destination) {
		return TABLES.get(destination);
	}

	/** Linhas que podem sair agora (minerio dentro do teto, arma suficiente). */
	public static List<Entry> eligible(Destination destination, Gear gear, int miningLevel) {
		int cap = oreCap(miningLevel, gear.tool());
		List<Entry> out = new ArrayList<>();
		for (Entry entry : TABLES.get(destination)) {
			if (entry.oreTier() >= 0 && entry.oreTier() > cap) {
				continue;
			}
			if (entry.weaponTier() >= 0 && gear.weapon() < entry.weaponTier()) {
				continue;
			}
			out.add(entry);
		}
		return out;
	}

	/** Sorteia o que o explorador traz (itens iguais somados). */
	public static List<Drop> roll(Destination destination, Gear gear, int miningLevel, int guildLevel, Random random) {
		List<Entry> entries = eligible(destination, gear, miningLevel);
		int total = entries.stream().mapToInt(Entry::weight).sum();
		double mult = lootMultiplier(gear);
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < rolls(guildLevel) && total > 0; i++) {
			int pick = random.nextInt(total);
			for (Entry entry : entries) {
				pick -= entry.weight();
				if (pick < 0) {
					int base = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
					out.merge(entry.item(), Math.max(1, (int) Math.round(base * mult)), Integer::sum);
					break;
				}
			}
		}
		List<Drop> drops = new ArrayList<>();
		out.forEach((item, n) -> drops.add(new Drop(item, n)));
		return drops;
	}

	/** Destinos que a Guilda oferece: os do Overworld sempre; Nether e End depois de visitados. */
	public static List<Destination> destinations(Set<String> visitedDimensions) {
		List<Destination> out = new ArrayList<>();
		for (Destination d : Destination.values()) {
			if (d == Destination.NETHER && !visitedDimensions.contains("minecraft:the_nether")) {
				continue;
			}
			if (d == Destination.END && !visitedDimensions.contains("minecraft:the_end")) {
				continue;
			}
			out.add(d);
		}
		return out;
	}
}
