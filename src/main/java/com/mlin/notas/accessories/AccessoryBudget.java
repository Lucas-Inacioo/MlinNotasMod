package com.mlin.notas.accessories;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orcamento dos acessorios: soma os numeros de tudo que esta equipado, por atributo, e corta no teto do estagio do mundo
 * (dano/reducao/velocidade/vida, ver {@link AccessoryStage}) ou no teto fixo dos outros atributos. Artefatos e reliquias
 * repetidos contam uma vez so. Valores negativos (ex.: a ancora deixa mais lento) nunca sao cortados. Logica pura.
 */
public final class AccessoryBudget {
	private AccessoryBudget() {
	}

	/**
	 * Um atributo somado.
	 *
	 * @param raw     soma de todos os acessorios
	 * @param cap     teto no estagio
	 * @param applied o que vale de verdade ({@code min(raw, cap)})
	 */
	public record Line(AccessoryStat stat, double raw, double cap, double applied) {
		public boolean capped() {
			return raw > cap + 1.0E-9;
		}
	}

	/** Resultado de {@link #compute}: linhas por atributo (so os que algum acessorio tem) e efeitos especiais. */
	public record Totals(AccessoryStage stage, Map<AccessoryStat, Line> lines, Set<AccessoryAbility> abilities) {
		public static final Totals EMPTY = new Totals(AccessoryStage.PRE_GOLEM, Map.of(), Set.of());

		public double applied(AccessoryStat stat) {
			Line line = lines.get(stat);
			return line == null ? 0.0 : line.applied();
		}

		public double raw(AccessoryStat stat) {
			Line line = lines.get(stat);
			return line == null ? 0.0 : line.raw();
		}

		public boolean capped(AccessoryStat stat) {
			Line line = lines.get(stat);
			return line != null && line.capped();
		}

		public boolean has(AccessoryAbility ability) {
			return abilities.contains(ability);
		}
	}

	/** Soma e corta. {@code equipped} pode ter nulls (encaixes vazios). */
	public static Totals compute(List<AccessoryDef> equipped, AccessoryStage stage) {
		Map<AccessoryStat, Double> raw = new EnumMap<>(AccessoryStat.class);
		Set<AccessoryAbility> abilities = EnumSet.noneOf(AccessoryAbility.class);
		Set<String> seenUnique = new HashSet<>();
		for (AccessoryDef def : equipped) {
			if (def == null || def.unique() && !seenUnique.add(def.id())) {
				continue;
			}
			def.stats().forEach((stat, value) -> raw.merge(stat, value, Double::sum));
			abilities.addAll(def.abilities());
		}
		Map<AccessoryStat, Line> lines = new EnumMap<>(AccessoryStat.class);
		raw.forEach((stat, sum) -> {
			double cap = stat.cap(stage);
			lines.put(stat, new Line(stat, sum, cap, Math.min(sum, cap)));
		});
		return new Totals(stage, Collections.unmodifiableMap(lines), Collections.unmodifiableSet(abilities));
	}

	/**
	 * Multiplicador do dano recebido: reducao geral vezes as reducoes especificas do tipo de dano. Nunca chega a zero
	 * (os tetos ficam bem abaixo de 100%).
	 */
	public static double incomingFactor(Totals totals, boolean fire, boolean sonic, boolean magic, boolean blast) {
		double factor = 1.0 - Math.max(0.0, totals.applied(AccessoryStat.DAMAGE_REDUCTION));
		if (fire) {
			factor *= 1.0 - Math.max(0.0, totals.applied(AccessoryStat.FIRE_REDUCTION));
		}
		if (sonic) {
			factor *= 1.0 - Math.max(0.0, totals.applied(AccessoryStat.SONIC_REDUCTION));
		}
		if (magic) {
			factor *= 1.0 - Math.max(0.0, totals.applied(AccessoryStat.MAGIC_REDUCTION));
		}
		if (blast) {
			factor *= 1.0 - Math.max(0.0, totals.applied(AccessoryStat.BLAST_REDUCTION));
		}
		return factor;
	}

	/** Vida devolvida por um golpe que causou {@code damageDealt} (roubo de vida, no maximo 3%). */
	public static double lifesteal(Totals totals, double damageDealt) {
		return Math.max(0.0, damageDealt) * Math.max(0.0, totals.applied(AccessoryStat.LIFESTEAL));
	}

	/** Lista de definicoes a partir de ids (desconhecido ou vazio vira null). */
	public static List<AccessoryDef> defs(List<String> ids) {
		return ids.stream().map(id -> id == null || id.isEmpty() ? null : AccessoryCatalog.byId(id)).toList();
	}
}
