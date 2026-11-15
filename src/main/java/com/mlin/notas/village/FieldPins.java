package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pinos do campo de alturas (logica pura): colunas com altura fixa que o {@link HeightFieldBuilder} respeita exatamente,
 * com rampa de no maximo 1 em volta. Coordenadas relativas ao centro da vila; altura = Y do topo do chao (convencao do
 * {@link HeightField}).
 *
 * <ul>
 * <li>{@link #pin}/{@link #pinRect}: o que ja existe e nao pode mudar (obras prontas, aneis ja aplainados, areas de
 * jogador numa vila antiga).</li>
 * <li>{@link #plot}: lote do jogador (fase 5; na fase 6 a casa de Kalanros x -61..-54 z 78..85, chao 94): retangulo
 * plano na altura dada, com o campo nivelado em volta e um caminho andavel (rampa de no maximo 1) ate a rua mais perto
 * ({@link HeightField.Kind#PATH} no campo). So lotes dentro do plano diretor ganham caminho.</li>
 * </ul>
 * Pino repetido na mesma coluna: vale o ultimo.
 */
public final class FieldPins {
	/** Lote do jogador: retangulo (relativo) e altura do chao. */
	public record Plot(VillageState.Rect rect, int height) {
	}

	private final Map<Long, Integer> pins = new HashMap<>();
	private final List<Plot> plots = new ArrayList<>();

	public static FieldPins none() {
		return new FieldPins();
	}

	public FieldPins pin(int x, int z, int height) {
		pins.put(VillageLayout.col(x, z), height);
		return this;
	}

	public FieldPins pinRect(VillageState.Rect rect, int height) {
		for (int x = rect.x0(); x <= rect.x1(); x++) {
			for (int z = rect.z0(); z <= rect.z1(); z++) {
				pin(x, z, height);
			}
		}
		return this;
	}

	/** Lote do jogador: pino em todo o retangulo + caminho ate a rua. */
	public FieldPins plot(VillageState.Rect rect, int height) {
		plots.add(new Plot(rect, height));
		return pinRect(rect, height);
	}

	/** Altura do pino na coluna, ou null. */
	public Integer at(int x, int z) {
		return pins.get(VillageLayout.col(x, z));
	}

	public Map<Long, Integer> all() {
		return Collections.unmodifiableMap(pins);
	}

	public List<Plot> plots() {
		return Collections.unmodifiableList(plots);
	}

	public boolean isEmpty() {
		return pins.isEmpty();
	}

	public int size() {
		return pins.size();
	}

}
