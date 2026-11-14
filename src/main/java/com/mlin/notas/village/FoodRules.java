package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numeros das funcoes das construcoes de producao e comida (logica pura, Vilas 2.0 fase 5): bonus do celeiro,
 * rendimento do moinho, receitas automaticas da padaria, da vinicola e do acougue, limite de animais do curral,
 * producao do apiario, tabuas da serraria. O lado servidor ({@code FoodBuildingsServer}) so le estes numeros e mexe
 * nos baus e no mundo. Os niveis vem de {@link VillageBuildings#level}.
 */
public final class FoodRules {
	private FoodRules() {
	}

	/** Uma receita automatica: consome {@code in} do bau, poe {@code out} e devolve {@code back} (baldes, garrafas). */
	public record Craft(String id, int minLevel, Map<String, Integer> in, String out, int outCount, Map<String, Integer> back) {
		public boolean possible(Map<String, Integer> stock) {
			return in.entrySet().stream().allMatch(e -> stock.getOrDefault(e.getKey(), 0) >= e.getValue());
		}
	}

	private static Map<String, Integer> m(Object... kv) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			out.put((String) kv[i], (Integer) kv[i + 1]);
		}
		return out;
	}

	private static Craft craft(String id, int level, Map<String, Integer> in, String out, int count) {
		return new Craft(id, level, in, out, count, Map.of());
	}

	private static int clamp3(int level) {
		return Math.max(1, Math.min(3, level));
	}

	// ---------------------------------------------------------------- celeiro

	/** Bonus de colheita das plantacoes perto do celeiro: +10% por nivel. */
	public static int granaryBonusPercent(int granaryLevel) {
		return 10 * Math.max(0, Math.min(3, granaryLevel));
	}

	/** Colheita extra: {@code count} itens, bonus em %, {@code roll} em [0,1). Devolve quantos itens a mais. */
	public static int bonusItems(int count, int percent, double roll) {
		double exact = count * percent / 100.0;
		int whole = (int) Math.floor(exact);
		return whole + (roll < exact - whole ? 1 : 0);
	}

	/** Raio (blocos a partir do celeiro) em que as plantacoes ganham o bonus. */
	public static final int GRANARY_RADIUS = 56;

	/** O que a plantacao poe no celeiro por ciclo (3/5/8), ja com o bonus do celeiro. */
	public static int farmOutput(int farmLevel, int granaryLevel) {
		int base = new int[] {3, 5, 8}[clamp3(farmLevel) - 1];
		return base + bonusItems(base, granaryBonusPercent(granaryLevel), 0.5);
	}

	/** Pomar: macas por ciclo 2/3/5. */
	public static int orchardOutput(int level) {
		return new int[] {2, 3, 5}[clamp3(level) - 1];
	}

	/** Curral: carne crua por ciclo 1/2/3. */
	public static int penOutput(int level) {
		return new int[] {1, 2, 3}[clamp3(level) - 1];
	}

	/** Padaria: pao levado do bau da padaria para o celeiro por ciclo (2/4/6). */
	public static int bakeryTransfer(int level) {
		return 2 * clamp3(level);
	}

	/** Culturas que a plantacao entrega, uma por dia de jogo. */
	public static final List<String> FARM_CROPS = List.of("minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot");

	public static String farmCrop(long day) {
		return FARM_CROPS.get((int) Math.floorMod(day, FARM_CROPS.size()));
	}

	// ---------------------------------------------------------------- moinho

	/** Quanto trigo o moinho mo por ciclo (16/32/48). */
	public static int millCapacity(int level) {
		return 16 * clamp3(level);
	}

	/** Farinha de um lote de 4 trigos: 5/6/7 (a tabua de corte da 1 para 1). */
	public static int flourPer4(int level) {
		return 4 + clamp3(level);
	}

	// ---------------------------------------------------------------- receitas automaticas

	private static final String FLOUR = "mlinnotas:flour";

	/** Padaria: o forno assa o que houver de farinha e ingredientes no bau. */
	public static final List<Craft> BAKERY = List.of(
			craft("strawberry_pie", 3, m(FLOUR, 1, "mlinnotas:strawberry", 2, "minecraft:sugar", 1, "minecraft:egg", 1), "mlinnotas:strawberry_pie", 2),
			craft("pumpkin_pie", 3, m(FLOUR, 1, "minecraft:pumpkin", 1, "minecraft:sugar", 1, "minecraft:egg", 1), "minecraft:pumpkin_pie", 2),
			new Craft("cake", 3, m(FLOUR, 3, "minecraft:sugar", 2, "minecraft:egg", 1, "minecraft:milk_bucket", 3), "minecraft:cake", 1,
					m("minecraft:bucket", 3)),
			craft("cheese_bread", 2, m(FLOUR, 2, "minecraft:egg", 1, "mlinnotas:fresh_cheese", 1), "mlinnotas:cheese_bread", 3),
			craft("cookies", 2, m(FLOUR, 2, "minecraft:cocoa_beans", 1, "minecraft:sugar", 1), "minecraft:cookie", 8),
			craft("corn_bread", 1, m("mlinnotas:cornmeal", 2), "mlinnotas:corn_bread", 2),
			craft("bread", 1, m(FLOUR, 3), "minecraft:bread", 4));

	/** Moinho: 4 trigos em 5/6/7 farinhas. */
	public static List<Craft> mill(int level) {
		return List.of(craft("flour", 1, m("minecraft:wheat", 4), FLOUR, flourPer4(level)));
	}

	/** Vinicola: vinho (I), cerveja (II), cidra (III); cada nivel faz mais lotes por ciclo. */
	public static final List<Craft> WINERY = List.of(
			craft("wine", 1, m("mlinnotas:grapes", 4), "mlinnotas:wine", 3),
			craft("beer", 2, m("minecraft:wheat", 4), "mlinnotas:beer", 3),
			craft("cider", 3, m("minecraft:apple", 4), "mlinnotas:cider", 3));

	/** Acougue II: seca a carne cozida em charque (3 charques para 2 carnes). */
	public static final List<Craft> BUTCHER = List.of(
			craft("jerky_beef", 2, m("minecraft:cooked_beef", 2), "mlinnotas:charque", 3),
			craft("jerky_pork", 2, m("minecraft:cooked_porkchop", 2), "mlinnotas:charque", 3),
			craft("jerky_mutton", 2, m("minecraft:cooked_mutton", 2), "mlinnotas:charque", 3));

	/** Lotes por ciclo de cada oficina: niveis I/II/III = 1/2/3 (acougue 2/4; moinho mo a capacidade inteira). */
	public static int batches(String kind, int level) {
		return switch (kind) {
			case "butcher" -> 2 * Math.max(1, Math.min(2, level));
			case "mill" -> millCapacity(level) / 4;
			case "bakery", "winery" -> 2 * clamp3(level);
			default -> clamp3(level);
		};
	}

	/** Resultado de {@link #run}: o que saiu e quantos lotes rodaram. */
	public record Result(Map<String, Integer> produced, int batches) {
	}

	/**
	 * Roda as receitas permitidas pelo nivel sobre o estoque ({@code stock} muda no lugar: ingredientes saem, produtos e
	 * devolucoes entram): no maximo {@code maxBatches} lotes no total, cada receita na ordem da lista enquanto der.
	 */
	public static Result run(Map<String, Integer> stock, List<Craft> crafts, int level, int maxBatches) {
		Map<String, Integer> produced = new LinkedHashMap<>();
		int done = 0;
		for (Craft craft : crafts) {
			if (craft.minLevel() > level) {
				continue;
			}
			while (done < maxBatches && craft.possible(stock)) {
				craft.in().forEach((id, n) -> stock.merge(id, -n, Integer::sum));
				stock.merge(craft.out(), craft.outCount(), Integer::sum);
				craft.back().forEach((id, n) -> stock.merge(id, n, Integer::sum));
				produced.merge(craft.out(), craft.outCount(), Integer::sum);
				done++;
			}
		}
		stock.values().removeIf(n -> n <= 0);
		return new Result(produced, done);
	}

	// ---------------------------------------------------------------- curral

	/** Animais de cada especie (vaca, ovelha, porco) que o curral segura: 4/6/8. */
	public static int penCap(int level) {
		return new int[] {4, 6, 8}[clamp3(level) - 1];
	}

	/** Nasce um filhote da especie? Precisa de 2 adultos e estar abaixo do limite. */
	public static boolean canBreed(int adults, int total, int cap) {
		return adults >= 2 && total < cap;
	}

	/** La por ciclo: 1 por ovelha adulta; leite: 1 balde a cada 2 vacas adultas (arredonda para cima). */
	public static int woolOutput(int sheep) {
		return Math.max(0, sheep);
	}

	public static int milkOutput(int cows) {
		return (Math.max(0, cows) + 1) / 2;
	}

	// ---------------------------------------------------------------- apiario

	/** Colmeias da planta: 4 no I, 8 no II. */
	public static int hives(int level) {
		return level >= 2 ? 8 : 4;
	}

	/** Favos por ciclo (1 por 2 colmeias; primavera e verao mais; no inverno as abelhas dormem). {@code season}: ordinal da estacao ou null. */
	public static int honeycomb(int level, Integer season) {
		int base = hives(level) / 2;
		if (season == null || season == 2) {
			return base;
		}
		return season == 3 ? 0 : base + base / 2;
	}

	/** Fator da colheita automatica pela estacao (ordinal; null = estacoes desligadas): inverno 0,5, outono 1,2. */
	public static double seasonFactor(Integer season) {
		if (season == null) {
			return 1.0;
		}
		return switch (season) {
			case 2 -> 1.2;
			case 3 -> 0.5;
			default -> 1.0;
		};
	}

	// ---------------------------------------------------------------- serraria

	/** Bancada: tabuas por tronco (o vanilla da 4). */
	public static final int PLANKS_PER_LOG = 6;

	public static int planks(int logs) {
		return Math.max(0, logs) * PLANKS_PER_LOG;
	}

	/** Troncos que a bancada serra por uso: nivel I 16, II 32, III a pilha toda (64). */
	public static int benchLimit(int level) {
		return level >= 3 ? 64 : 16 * Math.max(1, level);
	}

	// ---------------------------------------------------------------- pomar

	/** Intervalo (ticks) para uma arvore dar fruta de novo: um dia de jogo, menos no nivel mais alto. */
	public static long orchardRegrow(int level) {
		return level >= 3 ? 16000L : 24000L;
	}

	/** Frutas por colheita: macas 2/3/4; na estacao 1 (verao) e 2 (outono) mais uma fruta da estacao. */
	public static List<String> orchardFruit(int level, int season) {
		List<String> out = new ArrayList<>();
		int apples = 1 + clamp3(level);
		for (int i = 0; i < apples; i++) {
			out.add("minecraft:apple");
		}
		if (season == 1) {
			out.add("minecraft:sweet_berries");
		} else if (season == 2) {
			out.add("mlinnotas:grapes");
		}
		return out;
	}
}
