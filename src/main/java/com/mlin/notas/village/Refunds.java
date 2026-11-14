package com.mlin.notas.village;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Contas das devolucoes (logica pura). Cancelar uma obra da fila devolve tudo o que foi pago; demolir uma obra pronta
 * devolve {@link #DEMOLISH_FRACTION} (arredondado para baixo). Quando os itens de verdade nao foram registrados (obra
 * de antes do registro), a devolucao sai do custo do projeto, com um item representativo para cada tag.
 */
public final class Refunds {
	public static final double DEMOLISH_FRACTION = 0.5;

	private Refunds() {
	}

	/** Itens a devolver: {@code fraction} de {@code paid}; vazio = do custo do projeto ({@code representative}: tag -> item). */
	public static Map<String, Integer> of(VillageProject project, Map<String, Integer> paid, double fraction, Function<String, String> representative) {
		Map<String, Integer> source = new LinkedHashMap<>();
		if (paid.isEmpty()) {
			for (VillageCost cost : project.costs()) {
				String item = cost.isTag() ? representative.apply(cost.id()) : cost.id();
				if (item != null) {
					source.merge(item, cost.count(), Integer::sum);
				}
			}
		} else {
			source.putAll(paid);
		}
		Map<String, Integer> out = new LinkedHashMap<>();
		source.forEach((id, n) -> {
			int give = (int) Math.floor(n * fraction + 1e-9);
			if (give > 0) {
				out.put(id, give);
			}
		});
		return out;
	}

	/** Total de itens (para mensagens). */
	public static int total(Map<String, Integer> items) {
		return items.values().stream().mapToInt(Integer::intValue).sum();
	}
}
