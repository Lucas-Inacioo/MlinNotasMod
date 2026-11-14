package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.MasterPlanTestAccess;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageStyle;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ferramentas dos testes das obras publicas: plano pronto, contexto e conferencia de "nada solto, nada enterrado". */
final class WorksTestSupport {
	static final List<IntBinaryOperator> TERRAINS = MasterPlanTestAccess.terrains();

	private WorksTestSupport() {
	}

	static MasterPlan plan(int terrain, long seed) {
		MasterPlan plan = MasterPlanTestAccess.plan(terrain, seed);
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			plan.setRingPrepared(ring);
		}
		return plan;
	}

	static WorkContext ctx(MasterPlan plan, VillageStyle style, long seed) {
		return new WorkContext(plan, style, seed, MasterPlan.RINGS, (x, z) -> false, Set.of(), List.of(), Set.of(), List.of(), "", null, (x, z) -> Integer.MIN_VALUE);
	}

	/** Cada bloco entra no plano, na altura do chao planejado, e esta ligado ao chao (de cima de uma coluna ou de vizinho ligado). */
	static void assertGrounded(MasterPlan plan, Blueprint bp, String what) {
		Map<Long, WBlock> byKey = new java.util.HashMap<>();
		for (WBlock b : bp.ordered()) {
			byKey.put(b.key(), b);
		}
		Set<Long> grounded = new HashSet<>();
		ArrayDeque<WBlock> queue = new ArrayDeque<>();
		for (WBlock b : bp.ordered()) {
			int h = surface(plan, b.x(), b.z());
			assertTrue(h != Integer.MIN_VALUE, what + ": bloco fora do plano em " + b.x() + "," + b.z());
			if (b.kind() == WBlock.Kind.SURFACE) {
				assertTrue(b.y() == h || b.y() == h + 0, what + ": chao na altura errada em " + b.x() + "," + b.z() + " y=" + b.y() + " h=" + h);
				grounded.add(b.key());
				queue.add(b);
			} else {
				assertTrue(b.y() > h, what + ": bloco enterrado em " + b.x() + "," + b.y() + "," + b.z() + " h=" + h);
				if (b.y() == h + 1) {
					grounded.add(b.key());
					queue.add(b);
				}
			}
		}
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			WBlock b = queue.poll();
			for (int[] d : dirs) {
				WBlock n = byKey.get(WBlock.above(b.x() + d[0], b.y() + d[1], b.z() + d[2], "").key());
				if (n != null && grounded.add(n.key())) {
					queue.add(n);
				}
			}
		}
		for (WBlock b : bp.ordered()) {
			assertTrue(grounded.contains(b.key()), what + ": bloco solto em " + b.x() + "," + b.y() + "," + b.z() + " " + b.state());
		}
	}

	/** Altura do chao planejado da coluna: rua de chao, plato, praca; ponte nao conta (o bloco da ponte e a propria altura). */
	static int surface(MasterPlan plan, int x, int z) {
		return plan.plannedHeight(x, z);
	}

	static void assertOutsideLots(WorkContext ctx, Blueprint bp, String what) {
		for (WBlock b : bp.ordered()) {
			assertTrue(!ctx.insideLot(b.x(), b.z()), what + ": bloco dentro de lote em " + b.x() + "," + b.z());
		}
	}

	static VillageState.Rect rect(int x0, int z0, int x1, int z1) {
		return new VillageState.Rect(x0, z0, x1, z1);
	}
}
