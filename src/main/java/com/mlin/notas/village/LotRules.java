package com.mlin.notas.village;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Que lote cada tipo de construcao usa (logica pura): a classe de tamanho no nivel maximo da construcao e as zonas
 * onde ela pode ficar, na ordem de preferencia. Tipos rurais ({@code rural}) aceitam o fundo dos bairros (ou o centro,
 * no comeco) enquanto os arredores ainda nao abriram.
 *
 * <p>Fases seguintes registram os tipos novos aqui com {@link #register}: por exemplo
 * {@code LotRules.register("mill", SizeClass.L, true, Zone.OUTSKIRTS)}. Tipo sem regra usa {@link #DEFAULT}.
 */
public final class LotRules {
	public record Rule(String kind, SizeClass size, boolean rural, List<Zone> zones) {
		public boolean allows(Zone zone) {
			return zones.contains(zone);
		}

		/** Posicao da zona na preferencia (menor = melhor; fora = grande). */
		public int preference(Zone zone) {
			int i = zones.indexOf(zone);
			return i < 0 ? 99 : i;
		}
	}

	public static final Rule DEFAULT = new Rule("", SizeClass.S, false, List.of(Zone.DISTRICT, Zone.CENTER));
	private static final Map<String, Rule> RULES = new LinkedHashMap<>();

	static {
		register(VillageLayout.CAMPFIRE, SizeClass.S, false, Zone.CENTER);
		register("house", SizeClass.S, false, Zone.DISTRICT, Zone.CENTER, Zone.OUTSKIRTS);
		register("farm", SizeClass.S, true, Zone.OUTSKIRTS, Zone.DISTRICT);
		register("smithy", SizeClass.M, false, Zone.DISTRICT, Zone.CENTER);
		register("market", SizeClass.M, false, Zone.CENTER, Zone.DISTRICT);
		register("inn", SizeClass.M, false, Zone.CENTER, Zone.DISTRICT);
		register("library", SizeClass.M, false, Zone.CENTER, Zone.DISTRICT);
		register(BuildingTypes.TOWN_HALL, SizeClass.M, false, Zone.CENTER, Zone.DISTRICT);
		CraftsBuildings.registerLots();
	}

	private LotRules() {
	}

	/** Registra (ou troca) a regra de um tipo. */
	public static synchronized void register(String kind, SizeClass size, boolean rural, Zone... zones) {
		RULES.put(kind, new Rule(kind, size, rural, List.of(zones)));
	}

	public static Rule of(String kind) {
		Rule rule = RULES.get(kind);
		return rule == null ? DEFAULT : rule;
	}

	public static Map<String, Rule> all() {
		return Map.copyOf(RULES);
	}

	/**
	 * O tipo aceita um lote da zona {@code zone} agora? {@code outskirtsOpen}/{@code districtOpen}: os aneis dessas zonas
	 * ja abriram. Rural sem arredores abertos aceita o bairro; sem bairro aceita o centro.
	 */
	public static boolean accepts(Rule rule, Zone zone, boolean districtOpen, boolean outskirtsOpen) {
		if (rule.allows(zone)) {
			return true;
		}
		if (rule.rural() && !outskirtsOpen) {
			return zone == Zone.DISTRICT || (zone == Zone.CENTER && !districtOpen);
		}
		return false;
	}
}
