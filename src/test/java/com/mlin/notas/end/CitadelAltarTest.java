package com.mlin.notas.end;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O altar do Arauto Astral no cofre da cidadela: um so, no andar do cofre da fortaleza, alcancavel sem construir. */
class CitadelAltarTest {
	@Test
	void vaultHasOneReachableAstralAltar() {
		for (long seed : CitadelPlannerTest.SEEDS) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			List<Pos> altars = plan.positionsOf("astral_altar");
			assertEquals(1, altars.size(), "seed " + seed);
			Pos altar = altars.getFirst();
			assertEquals(CitadelPlanner.VAULT_Y, altar.y(), "no andar do cofre");
			assertTrue(Math.abs(altar.x()) < CitadelPlanner.KEEP_HALF && Math.abs(altar.z()) < CitadelPlanner.KEEP_HALF, "dentro da fortaleza");
			Set<Long> reach = CitadelPlannerTest.reach(plan);
			assertTrue(CitadelPlan.usableFrom(reach, altar.x(), altar.y(), altar.z()), "seed " + seed + ": altar inalcancavel");
			// cabe o arauto (1 x 2,4) em cima do altar
			for (int dy = 1; dy <= 3; dy++) {
				assertTrue(!plan.solidAt(altar.x(), altar.y() + dy, altar.z()), "seed " + seed + ": sem espaco em cima do altar");
			}
		}
	}
}
