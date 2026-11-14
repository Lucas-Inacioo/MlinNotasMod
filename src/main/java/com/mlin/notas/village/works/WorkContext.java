package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.Terrain;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageStyle;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * O que o planejador de obras publicas ve da vila (logica pura): o plano diretor com as alturas, o estilo, a semente, os
 * aneis abertos, os lotes que ja tem obra (nunca mexidos), as colunas a deixar livres (postes, caminhos de porta), o
 * que atrapalha em cima do chao ({@code blocked}: bloco de jogador ou de obra paga) e, para obras de fora da vila,
 * o terreno e os destinos.
 *
 * @param blocked          coluna (x, z) com bloco de fora acima do chao que a obra nao pode trocar
 * @param reservedParcels  lotes do plano com obra reservada, pronta ou de jogador
 * @param lotRects         retangulos das obras prontas ou reservadas (relativos ao quadro)
 * @param keepClear        colunas (chave {@link com.mlin.notas.village.VillageLayout#col}) que ficam livres
 * @param destinations     destinos das estradas externas ({x, z, tipo, nome})
 * @param lastBoss         id do ultimo chefe derrotado ("" = nenhum)
 * @param columnTop        topo (y) do bloco nao-ar mais alto da coluna, lido do mundo (muralha de pedra, torres); Integer.MIN_VALUE = nada
 */
public record WorkContext(MasterPlan plan, VillageStyle style, long seed, int openRings, BiPredicate<Integer, Integer> blocked,
		Set<Integer> reservedParcels, List<VillageState.Rect> lotRects, Set<Long> keepClear, List<Destination> destinations, String lastBoss,
		Terrain terrain, java.util.function.BiFunction<Integer, Integer, Integer> columnTop) {
	public record Destination(int x, int z, String kind, String name) {
	}

	public Mats mats() {
		return Mats.of(style);
	}

	/** Altura do chao planejado na coluna (Integer.MIN_VALUE onde o plano nao mexe). */
	public int h(int x, int z) {
		return plan.plannedHeight(x, z);
	}

	/** Anel de terraplenagem da coluna ja esta aplainado (a obra so mexe em chao pronto). */
	public boolean ready(int x, int z) {
		int ring = plan.ring(x, z);
		return ring >= 1 && plan.ringPrepared(ring);
	}

	public boolean free(int x, int z) {
		return !blocked.test(x, z) && !keepClear.contains(com.mlin.notas.village.VillageLayout.col(x, z));
	}

	public boolean insideLot(int x, int z) {
		for (VillageState.Rect r : lotRects) {
			if (r.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Coluna de quarteirao sem lote do plano em cima: quintal, calcada, sobra. */
	public boolean yard(int x, int z) {
		MasterPlan.Block b = plan.blockAt(x, z);
		return b != null && plan.parcelAt(x, z) == null;
	}

	/** Pseudo-aleatorio estavel (0..n-1) por coluna e sal. */
	public int roll(int x, int z, int salt, int n) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (int) Math.floorMod(h, (long) n);
	}
}
