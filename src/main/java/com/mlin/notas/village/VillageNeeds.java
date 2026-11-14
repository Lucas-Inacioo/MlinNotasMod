package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Necessidades da vila (logica pura): moradia, comida, seguranca, luz, lazer e fe, comercio. Cada uma vai de 0 a 100%
 * comparando o que as construcoes (que estao funcionando, com trabalhador) oferecem com a demanda, que cresce com a
 * populacao. Acima de 75% a necessidade da +2 de prosperidade por dia; abaixo de 25% tira 1 (o nivel da vila nunca
 * desce). Da Cidade em diante, subir de nivel exige todas acima de 50%.
 *
 * <p>Fontes sao uma lista aberta: as construcoes contribuem pelo registro em {@link BuildingTypes} (pontos por nivel)
 * e fontes que nao sao construcao com lote (golens, postes, praca, casas de jogador; celeiro e igreja nas fases
 * seguintes) entram por {@link #register(Need, Provider)}.
 */
public final class VillageNeeds {
	public enum Need {
		HOUSING, FOOD, SAFETY, LIGHT, LEISURE, COMMERCE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Acima disso a necessidade da prosperidade todo dia. */
	public static final int GOOD = 75;
	/** Abaixo disso tira prosperidade todo dia. */
	public static final int BAD = 25;
	/** Da Cidade em diante, toda necessidade precisa passar disso para subir de nivel. */
	public static final int LEVEL_UP = 50;
	public static final int GOOD_BONUS = 2;
	public static final int BAD_PENALTY = 1;

	/** Situacao lida pelas fontes: construcoes funcionando, obras sem lote prontas e o que foi contado no mundo. */
	public record Input(int population, List<VillageBuildings.Building> working, Map<VillageProject, Integer> built, int golems,
			int streetLamps, int playerHouses) {
		public int built(VillageProject project) {
			return built.getOrDefault(project, 0);
		}
	}

	/** Uma fonte de pontos para uma necessidade. */
	@FunctionalInterface
	public interface Provider {
		int points(Input input);
	}

	private static final Map<Need, List<Provider>> PROVIDERS = new EnumMap<>(Need.class);

	static {
		register(Need.HOUSING, in -> 2 * in.playerHouses());
		register(Need.SAFETY, in -> Math.min(60, 15 * in.golems()));
		register(Need.SAFETY, in -> 40 * Math.min(1, in.built(VillageProject.WALL)));
		register(Need.SAFETY, in -> 10 * in.built(VillageProject.LAMPS));
		register(Need.LIGHT, in -> 25 * in.built(VillageProject.LAMPS));
		register(Need.LIGHT, in -> 3 * in.streetLamps());
		register(Need.LIGHT, in -> 15 * Math.min(1, in.built(VillageProject.PLAZA)));
		register(Need.LEISURE, in -> 25 * Math.min(1, in.built(VillageProject.PLAZA)));
	}

	private VillageNeeds() {
	}

	/** Soma uma fonte a necessidade (fases seguintes: celeiro na comida, igreja no lazer e fe...). */
	public static synchronized void register(Need need, Provider provider) {
		PROVIDERS.computeIfAbsent(need, n -> new ArrayList<>()).add(provider);
	}

	/** Demanda (pontos para 100%) com {@code population} moradores. */
	public static int demand(Need need, int population) {
		int p = Math.max(1, population);
		return switch (need) {
			case HOUSING, FOOD -> p;
			case SAFETY -> 30 + 3 * p;
			case LIGHT, LEISURE -> 20 + 4 * p;
			case COMMERCE -> 10 + 4 * p;
		};
	}

	/** Pontos oferecidos para a necessidade. */
	public static int supply(Need need, Input in) {
		int total = 0;
		for (VillageBuildings.Building b : in.working()) {
			BuildingTypes.Type type = BuildingTypes.of(b.kind());
			if (type != null) {
				total += type.need(need, b.level());
			}
		}
		for (Provider provider : PROVIDERS.getOrDefault(need, List.of())) {
			total += Math.max(0, provider.points(in));
		}
		return total;
	}

	/** 0..100%. */
	public static int value(Need need, Input in) {
		return (int) Math.min(100, (long) supply(need, in) * 100 / demand(need, in.population()));
	}

	public static Map<Need, Integer> values(Input in) {
		Map<Need, Integer> out = new EnumMap<>(Need.class);
		for (Need need : Need.values()) {
			out.put(need, value(need, in));
		}
		return out;
	}

	/** Prosperidade do dia: +2 por necessidade acima de 75%, -1 por necessidade abaixo de 25%. */
	public static int dailyProsperity(Map<Need, Integer> values) {
		int out = 0;
		for (int v : values.values()) {
			if (v > GOOD) {
				out += GOOD_BONUS;
			} else if (v < BAD) {
				out -= BAD_PENALTY;
			}
		}
		return out;
	}

	/** Menor valor entre as necessidades. */
	public static int min(Map<Need, Integer> values) {
		return values.values().stream().mapToInt(Integer::intValue).min().orElse(100);
	}

	/** Entrada a partir do estado da vila. */
	public static Input input(VillageState state) {
		int lamps = 0;
		if (state.layout() != null) {
			for (VillageLayout.Lamp lamp : state.layout().lamps()) {
				lamps += lamp.built() ? 1 : 0;
			}
		}
		BuildingsState b = state.buildingsState();
		return new Input(b.population(), VillageBuildings.working(state), state.builtMap(), b.golems(), lamps, state.playerHouses());
	}

	public static Map<Need, Integer> values(VillageState state) {
		return values(input(state));
	}
}
