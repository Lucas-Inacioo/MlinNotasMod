package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tipos de construcao com nivel proprio (logica pura): nivel maximo, quantos moradores cada nivel abriga, quantos
 * trabalhadores a construcao precisa para funcionar e quanto cada nivel contribui para cada necessidade da vila.
 *
 * <p>Fases seguintes registram as construcoes novas aqui, uma linha por tipo, por exemplo:
 * <pre>
 * BuildingTypes.register(BuildingTypes.type("mill").maxLevel(3).workers(1).need(VillageNeeds.Need.FOOD, 6, 10, 14));
 * </pre>
 * e o lote em {@link LotRules#register}. Tipo sem registro nao tem nivel (fica no I) nem conta nas necessidades.
 *
 * <p>A Prefeitura limita o nivel de todas as outras ({@link #cap}); o teto dela vem do nivel da vila
 * ({@link #townHallCap}). Inspirado no nivel da prefeitura do MineColonies.
 */
public final class BuildingTypes {
	public static final String TOWN_HALL = "town_hall";
	/** Multiplicador do custo base por nivel alvo (indice = nivel): II 1,5x, III 2,5x, IV 4x, V 6x. */
	private static final double[] COST_MULT = {0, 1, 1.5, 2.5, 4, 6};

	/** Um tipo de construcao com nivel. {@code residents}/{@code needs}: indice 0 = nivel I. */
	public record Type(String kind, int maxLevel, int workers, int[] residents, Map<VillageNeeds.Need, int[]> needs) {
		public int residents(int level) {
			return at(residents, level);
		}

		/** Contribuicao do nivel para a necessidade (0 se nao contribui). */
		public int need(VillageNeeds.Need need, int level) {
			int[] values = needs.get(need);
			return values == null ? 0 : at(values, level);
		}

		private static int at(int[] values, int level) {
			if (values.length == 0 || level <= 0) {
				return 0;
			}
			return values[Math.min(values.length, level) - 1];
		}
	}

	/** Montador de {@link Type}. */
	public static final class Builder {
		private final String kind;
		private int maxLevel = 1;
		private int workers;
		private int[] residents = new int[0];
		private final Map<VillageNeeds.Need, int[]> needs = new EnumMap<>(VillageNeeds.Need.class);

		private Builder(String kind) {
			this.kind = kind;
		}

		public Builder maxLevel(int maxLevel) {
			this.maxLevel = Math.max(1, Math.min(5, maxLevel));
			return this;
		}

		/** Moradores livres que a construcao ocupa para funcionar. */
		public Builder workers(int workers) {
			this.workers = Math.max(0, workers);
			return this;
		}

		/** Moradores que a construcao abriga em cada nivel (I, II, ...). */
		public Builder residents(int... perLevel) {
			this.residents = perLevel.clone();
			return this;
		}

		/** Pontos da necessidade em cada nivel (I, II, ...); o ultimo valor vale para os niveis acima. */
		public Builder need(VillageNeeds.Need need, int... perLevel) {
			needs.put(need, perLevel.clone());
			return this;
		}

		public Type build() {
			return new Type(kind, maxLevel, workers, residents, Map.copyOf(needs));
		}
	}

	private static final Map<String, Type> TYPES = new LinkedHashMap<>();

	static {
		register(type("house").maxLevel(3).residents(2, 3, 4).need(VillageNeeds.Need.HOUSING, 2, 3, 4));
		register(type("farm").maxLevel(3).workers(1).need(VillageNeeds.Need.FOOD, 5, 8, 12));
		register(type("smithy").maxLevel(5).workers(1).need(VillageNeeds.Need.SAFETY, 10, 15, 20, 25, 30)
				.need(VillageNeeds.Need.COMMERCE, 5, 8, 10, 12, 15));
		register(type("market").maxLevel(3).workers(1).need(VillageNeeds.Need.COMMERCE, 35, 60, 90));
		register(type("inn").maxLevel(3).workers(1).need(VillageNeeds.Need.HOUSING, 2, 3, 4).need(VillageNeeds.Need.LEISURE, 20, 30, 40));
		register(type("library").maxLevel(3).workers(1).need(VillageNeeds.Need.LEISURE, 20, 30, 40));
		register(type(TOWN_HALL).maxLevel(5).need(VillageNeeds.Need.SAFETY, 10, 12, 15, 18, 20).need(VillageNeeds.Need.LEISURE, 5, 8, 10, 12, 15));
		FoodBuildings.registerTypes();
	}

	private BuildingTypes() {
	}

	public static Builder type(String kind) {
		return new Builder(kind);
	}

	public static synchronized void register(Builder builder) {
		Type type = builder.build();
		TYPES.put(type.kind(), type);
	}

	/** Tipo registrado ou null. */
	public static Type of(String kind) {
		return TYPES.get(kind);
	}

	public static List<Type> all() {
		return List.copyOf(TYPES.values());
	}

	public static int maxLevel(String kind) {
		Type type = of(kind);
		return type == null ? 1 : type.maxLevel();
	}

	public static int workers(String kind) {
		Type type = of(kind);
		return type == null ? 0 : type.workers();
	}

	public static int residents(String kind, int level) {
		Type type = of(kind);
		return type == null ? 0 : type.residents(level);
	}

	/** Nivel maximo da Prefeitura no nivel da vila: Acampamento 0, Aldeia I, Vila II, Cidade III, Capital IV, Metropole+ V. */
	public static int townHallCap(VillageLevel level) {
		return Math.min(5, level.ordinal());
	}

	/**
	 * Nivel maximo do tipo agora: a Prefeitura pelo nivel da vila; as outras pela Prefeitura (sem Prefeitura, nivel I)
	 * e pelo maximo do tipo.
	 */
	public static int cap(String kind, VillageLevel villageLevel, int townHallLevel) {
		if (TOWN_HALL.equals(kind)) {
			return Math.min(maxLevel(kind), townHallCap(villageLevel));
		}
		return Math.min(maxLevel(kind), Math.max(1, townHallLevel));
	}

	/** Multiplicador do custo base para chegar ao nivel {@code level}. */
	public static double costMultiplier(int level) {
		return COST_MULT[Math.max(1, Math.min(5, level))];
	}

	/** Materiais da faixa do nivel alvo: ferro e tijolos no II-III, cobalto no IV, cobalto e diamante no V. */
	static List<VillageCost> tierExtras(int level) {
		return switch (level) {
			case 2 -> List.of(VillageCost.of("minecraft:iron_ingot", 8), VillageCost.of("minecraft:bricks", 16));
			case 3 -> List.of(VillageCost.of("minecraft:iron_ingot", 16), VillageCost.of("minecraft:bricks", 32));
			case 4 -> List.of(VillageCost.of("mlinnotas:cobalt_ingot", 12), VillageCost.of("minecraft:iron_ingot", 16));
			case 5 -> List.of(VillageCost.of("mlinnotas:cobalt_ingot", 24), VillageCost.of("minecraft:diamond", 6));
			default -> List.of();
		};
	}

	/**
	 * Custo de melhorar o projeto para {@code level} (II..V): o custo base vezes o multiplicador (arredondado para
	 * cima) mais os materiais da faixa do nivel. Item que ja esta no custo base soma na mesma linha.
	 */
	public static List<VillageCost> upgradeCost(VillageProject project, int level) {
		Map<String, Integer> out = new LinkedHashMap<>();
		double mult = costMultiplier(level);
		for (VillageCost cost : project.costs()) {
			out.merge(cost.id(), (int) Math.ceil(cost.count() * mult - 1e-9), Integer::sum);
		}
		for (VillageCost cost : tierExtras(level)) {
			out.merge(cost.id(), cost.count(), Integer::sum);
		}
		List<VillageCost> list = new ArrayList<>();
		out.forEach((id, n) -> list.add(VillageCost.of(id, n)));
		return list;
	}

	/** Aldeoes que chegam ao terminar a melhoria (mais moradores = mais aldeoes). */
	public static int newResidents(String kind, int fromLevel, int toLevel) {
		return Math.max(0, residents(kind, toLevel) - residents(kind, fromLevel));
	}
}
