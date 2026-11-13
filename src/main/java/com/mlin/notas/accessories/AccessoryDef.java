package com.mlin.notas.accessories;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Definicao de um acessorio: id do item, encaixe, origem, numeros e efeitos especiais. Logica pura.
 *
 * @param bossFor para reliquias, o id do chefe que a solta (vazio nos outros)
 */
public record AccessoryDef(String id, AccessorySlotType slot, AccessoryKind kind, Map<AccessoryStat, Double> stats,
		Set<AccessoryAbility> abilities, String bossFor) {
	public AccessoryDef {
		EnumMap<AccessoryStat, Double> copy = new EnumMap<>(AccessoryStat.class);
		copy.putAll(stats);
		stats = Collections.unmodifiableMap(copy);
		abilities = abilities.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(abilities));
	}

	public double stat(AccessoryStat stat) {
		return stats.getOrDefault(stat, 0.0);
	}

	public boolean has(AccessoryAbility ability) {
		return abilities.contains(ability);
	}

	public boolean unique() {
		return kind.unique();
	}

	static Builder builder(String id, AccessorySlotType slot, AccessoryKind kind) {
		return new Builder(id, slot, kind);
	}

	static final class Builder {
		private final String id;
		private final AccessorySlotType slot;
		private final AccessoryKind kind;
		private final Map<AccessoryStat, Double> stats = new EnumMap<>(AccessoryStat.class);
		private final Set<AccessoryAbility> abilities = EnumSet.noneOf(AccessoryAbility.class);
		private String bossFor = "";

		private Builder(String id, AccessorySlotType slot, AccessoryKind kind) {
			this.id = id;
			this.slot = slot;
			this.kind = kind;
		}

		Builder stat(AccessoryStat stat, double value) {
			stats.put(stat, value);
			return this;
		}

		Builder ability(AccessoryAbility ability) {
			abilities.add(ability);
			return this;
		}

		Builder boss(String boss) {
			this.bossFor = boss;
			return this;
		}

		AccessoryDef build() {
			return new AccessoryDef(id, slot, kind, stats, abilities, bossFor);
		}
	}
}
