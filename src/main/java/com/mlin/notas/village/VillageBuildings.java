package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consultas sobre as construcoes prontas da vila (logica pura): lista com o nivel de cada uma, Prefeitura, teto de
 * nivel, pontos de obra das melhorias, moradores e trabalhadores (quem fica parado por falta de morador livre) e o
 * nivel de funcao de cada tipo (o melhor nivel entre as que estao funcionando).
 *
 * <p>Funcoes por nivel das fases seguintes leem {@link #level(VillageState, String)}: 0 = a vila nao tem o tipo
 * funcionando, senao o nivel da melhor (ex.: {@code level(v, "smithy") >= 2} libera ferramentas de cobalto).
 */
public final class VillageBuildings {
	/** Uma obra pronta: lote (negativo = obra antiga sem lote), tipo e nivel. */
	public record Building(int lot, String kind, int level) {
	}

	private VillageBuildings() {
	}

	/** Obras prontas com lote, mais as antigas sem lote (nivel I) dos tipos que ocupam lote. */
	public static List<Building> all(VillageState state) {
		List<Building> out = new ArrayList<>();
		Map<String, Integer> inLots = new LinkedHashMap<>();
		if (state.layout() != null) {
			for (VillageLayout.Lot lot : state.layout().lots()) {
				if (lot.built() && lot.project() != null) {
					out.add(new Building(lot.id(), lot.kind(), state.buildingsState().level(lot.id())));
					inLots.merge(lot.kind(), 1, Integer::sum);
				}
			}
		}
		int synthetic = -1;
		for (VillageProject project : VillageLayout.LOT_PROJECTS) {
			int orphans = state.built(project) - inLots.getOrDefault(project.id(), 0);
			for (int i = 0; i < orphans; i++) {
				out.add(new Building(synthetic--, project.id(), 1));
			}
		}
		out.sort(Comparator.comparingInt(b -> b.lot() < 0 ? Integer.MIN_VALUE - b.lot() : b.lot()));
		return out;
	}

	/** Moradores que as casas (e outros tipos com moradia) abrigam. */
	public static int residents(VillageState state) {
		int total = 0;
		for (Building b : all(state)) {
			total += BuildingTypes.residents(b.kind(), b.level());
		}
		return total;
	}

	/** Trabalhadores que as construcoes pedem. */
	public static int workersNeeded(VillageState state) {
		int total = 0;
		for (Building b : all(state)) {
			total += BuildingTypes.workers(b.kind());
		}
		return total;
	}

	/**
	 * Lotes parados: cada construcao de oficio ocupa moradores livres na ordem em que foi feita; as que sobram sem
	 * morador ficam paradas (nao funcionam nem contam nas necessidades).
	 */
	public static Set<Integer> idle(VillageState state) {
		return idle(all(state), residents(state));
	}

	public static Set<Integer> idle(List<Building> buildings, int residents) {
		Set<Integer> out = new HashSet<>();
		int free = residents;
		for (Building b : buildings) {
			int need = BuildingTypes.workers(b.kind());
			if (need == 0) {
				continue;
			}
			if (free >= need) {
				free -= need;
			} else {
				out.add(b.lot());
			}
		}
		return out;
	}

	/** Obras funcionando (com trabalhador ou que nao precisam de um). */
	public static List<Building> working(VillageState state) {
		List<Building> all = all(state);
		Set<Integer> idle = idle(all, residents(state));
		List<Building> out = new ArrayList<>();
		for (Building b : all) {
			if (!idle.contains(b.lot())) {
				out.add(b);
			}
		}
		return out;
	}

	/** Melhor nivel do tipo entre as obras funcionando (0 = nenhuma). */
	public static int level(VillageState state, String kind) {
		int best = 0;
		for (Building b : working(state)) {
			if (b.kind().equals(kind)) {
				best = Math.max(best, b.level());
			}
		}
		return best;
	}

	/** Melhor nivel do tipo entre as obras prontas, paradas ou nao (0 = nenhuma). */
	public static int bestLevel(VillageState state, String kind) {
		int best = 0;
		for (Building b : all(state)) {
			if (b.kind().equals(kind)) {
				best = Math.max(best, b.level());
			}
		}
		return best;
	}

	/** Melhor nivel de cada tipo (para as exigencias de nivel da vila, como {@code town_hall:2}). */
	public static Map<String, Integer> bestLevels(VillageState state) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Building b : all(state)) {
			out.merge(b.kind(), b.level(), Math::max);
		}
		return out;
	}

	public static int townHallLevel(VillageState state) {
		return bestLevel(state, BuildingTypes.TOWN_HALL);
	}

	/** Nivel maximo do tipo agora (Prefeitura e nivel da vila). */
	public static int cap(VillageState state, String kind) {
		return BuildingTypes.cap(kind, state.level(), townHallLevel(state));
	}

	/** Pontos de obra das melhorias: cada nivel acima do I vale 1. */
	public static int upgradePoints(VillageState state) {
		int total = 0;
		for (Building b : all(state)) {
			total += Math.max(0, b.level() - 1);
		}
		return total;
	}

	/** Necessidades valem (painel, prosperidade diaria) a partir da Prefeitura I. */
	public static boolean needsActive(VillageState state) {
		return townHallLevel(state) >= 1;
	}

	/**
	 * Teto de nivel de profissao de um aldeao cujo posto de trabalho fica no lote {@code lot} (-1 = fora de qualquer
	 * obra da vila): o teto da vila, e acima de jornaleiro so com a obra funcionando no nivel (IV = experiente; mestre
	 * so com a obra no V).
	 */
	public static int professionCap(VillageState state, int lot) {
		int villageCap = state.level().tradeCap();
		int building = 0;
		if (lot >= 0) {
			for (Building b : working(state)) {
				if (b.lot() == lot) {
					building = b.level();
				}
			}
		}
		return professionCap(villageCap, building);
	}

	/** Regra pura do teto: {@code building} = nivel da obra funcionando do posto (0 = nenhuma). */
	public static int professionCap(int villageCap, int building) {
		return Math.min(villageCap, Math.max(VillageLevel.WILD_TRADE_CAP, building));
	}
}
