package com.mlin.notas.village;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Densidade de lotes por anel (antes da subdivisao densa, plano de semente 11 em terreno plano:
 * {@code [0, 0, 8, 11, 0, 15, 48]} lotes nos aneis 1..6, cobertura 0/51/54/0/66/55%) e a passagem de um plano salvo na
 * versao esparsa para a densa sem mexer em lote reservado.
 */
class LotDensityTest {
	static int[] lotsPerRing(MasterPlan plan) {
		int[] lots = new int[7];
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (!p.retired()) {
				lots[plan.block(p.block()).ring()]++;
			}
		}
		return lots;
	}

	static int[] coverage(MasterPlan plan) {
		int[] cells = new int[7];
		int[] covered = new int[7];
		int e = plan.extent();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				MasterPlan.Block b = plan.blockAt(x, z);
				if (b != null) {
					cells[b.ring()]++;
					if (plan.parcelAt(x, z) != null) {
						covered[b.ring()]++;
					}
				}
			}
		}
		int[] out = new int[7];
		for (int r = 1; r <= 6; r++) {
			out[r] = cells[r] == 0 ? 0 : 100 * covered[r] / cells[r];
		}
		return out;
	}

	@Test
	void denserThanTheSparseSubdivision() {
		long seed = 11;
		for (IntBinaryOperator h : MasterPlanTest.TERRAINS) {
			VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(h);
			MasterPlan plan = MasterPlanTest.plan(t, seed);
			int[] lots = lotsPerRing(plan);
			int[] cover = coverage(plan);
			System.out.println("terreno " + MasterPlanTest.TERRAINS.indexOf(h) + " lotes " + java.util.Arrays.toString(lots) + " cobertura% "
					+ java.util.Arrays.toString(cover));
			assertTrue(lots[1] >= 3, "esquinas largas do centro sem lote minusculo: " + lots[1]);
			assertTrue(lots[3] >= 16, "poucos lotes nos bairros: " + lots[3]);
			assertTrue(lots[5] >= 18, "poucos lotes nos arredores: " + lots[5]);
			assertTrue(cover[3] >= 75 && cover[5] >= 75, "cobertura baixa: " + java.util.Arrays.toString(cover));
			assertEquals(MasterPlan.VERSION, plan.version());
			boolean tiny = false;
			for (MasterPlan.Parcel p : plan.parcels()) {
				tiny |= p.size() == SizeClass.T;
			}
			assertTrue(tiny, "nenhum lote minusculo nas sobras");
			seed += 7;
		}
	}

	/** Plano "salvo" na versao esparsa: so alguns lotes do plano atual (reindexados) e a versao 1. */
	static MasterPlan sparse(MasterPlan full) {
		List<MasterPlan.Parcel> old = new ArrayList<>();
		for (MasterPlan.Parcel p : full.parcels()) {
			// poucos lotes por quarteirao e nenhum T: como o plano antigo
			if (p.size() != SizeClass.T && old.stream().filter(q -> q.block() == p.block()).count() < 2) {
				old.add(new MasterPlan.Parcel(old.size(), p.block(), p.rect(), p.front(), p.size(), p.zone(), false));
			}
		}
		MasterPlan plan = MasterPlan.restore(full.seed(), full.extent(), full.plazaY(), full.gridRle(), full.ringRle(), full.streetHeights(), full.blocks(),
				old, full.playerAreas(), full.fixedAreas());
		plan.setVersion(MasterPlan.VERSION_SPARSE);
		return plan;
	}

	@Test
	void savedSparsePlanKeepsReservedLotsAndGetsDenser() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(2));
		MasterPlan full = MasterPlanTest.plan(t, 11);
		MasterPlan plan = sparse(full);
		int before = plan.parcels().size();
		List<MasterPlan.Parcel> snapshot = new ArrayList<>(plan.parcels());
		// reservados: o 1o, o 4o e o 9o lotes; um lote de jogador
		Set<Integer> reserved = Set.of(0, 3, 8);
		plan.parcels().get(5).setPlayer(true);
		int[] keep = {plan.parcels().get(11).rect().x0(), plan.parcels().get(11).rect().z0()};
		BlockSubdivider.Densified d = BlockSubdivider.densify(plan, reserved, List.<int[]>of(keep));
		assertEquals(MasterPlan.VERSION, plan.version());
		assertTrue(d.added() > 0 && !d.retired().isEmpty());
		// reservados e de jogador: identicos
		for (int id : new int[] {0, 3, 8, 5}) {
			MasterPlan.Parcel now = plan.parcels().get(id);
			assertFalse(now.retired(), "lote reservado aposentado: " + id);
			assertEquals(snapshot.get(id).rect(), now.rect());
			assertEquals(snapshot.get(id).size(), now.size());
			assertEquals(snapshot.get(id).front(), now.front());
		}
		// quarteiroes sem reserva: lotes velhos aposentados; com reserva: nenhum aposentado
		Set<Integer> blocksWithReserved = new HashSet<>();
		for (int id : new int[] {0, 3, 8, 5}) {
			blocksWithReserved.add(snapshot.get(id).block());
		}
		for (MasterPlan.Parcel p : snapshot) {
			boolean should = !blocksWithReserved.contains(p.block());
			assertEquals(should, plan.parcels().get(p.id()).retired(), "aposentadoria do lote " + p.id());
			assertEquals(p.id(), plan.parcels().get(p.id()).id());
		}
		int live = (int) plan.parcels().stream().filter(p -> !p.retired()).count();
		assertTrue(live > before + 10, "plano denso tem mais lotes: " + before + " -> " + live);
		System.out.println("plano salvo esparso: " + before + " lotes -> " + live + " vivos (" + plan.parcels().size() + " no indice), aposentados "
				+ d.retired().size() + ", novos " + d.added() + ", por anel " + java.util.Arrays.toString(lotsPerRing(plan)));
		// ids = indice, sem lotes sobrepostos nem fora do quarteirao, frente para a rua
		for (int i = 0; i < plan.parcels().size(); i++) {
			MasterPlan.Parcel p = plan.parcels().get(i);
			assertEquals(i, p.id());
			if (p.retired()) {
				continue;
			}
			VillageState.Rect r = p.rect();
			for (int x = r.x0(); x <= r.x1(); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					assertEquals(MasterPlan.BLOCK + p.block(), plan.code(x, z));
				}
			}
			assertFalse(r.contains(keep[0], keep[1]) && p.id() >= snapshot.size(), "lote novo em cima de celula livre");
			for (MasterPlan.Parcel q : plan.parcels()) {
				if (q != p && !q.retired()) {
					assertFalse(r.overlaps(q.rect(), 0), "lotes sobrepostos " + p.id() + " e " + q.id());
				}
			}
			int[] door = MasterPlanTest.doorCell(p);
			boolean ok = false;
			for (int k = 1; k <= BlockSubdivider.ACCESS_MAX && !ok; k++) {
				ok = plan.ground(door[0] + p.front().dx * k, door[1] + p.front().dz * k);
			}
			assertTrue(ok, "lote " + p.id() + " sem rua na frente");
		}
		// determinismo: de novo a partir do mesmo plano salvo
		MasterPlan again = sparse(full);
		again.parcels().get(5).setPlayer(true);
		BlockSubdivider.densify(again, reserved, List.<int[]>of(keep));
		assertEquals(plan.parcels().size(), again.parcels().size());
		for (int i = 0; i < plan.parcels().size(); i++) {
			assertEquals(plan.parcels().get(i).rect(), again.parcels().get(i).rect());
		}
	}

	@Test
	void savedPlanRoundTripKeepsRetiredLotsAndVersion() {
		VillagePlannerTest.FakeTerrain t = new VillagePlannerTest.FakeTerrain(MasterPlanTest.TERRAINS.get(0));
		MasterPlan plan = sparse(MasterPlanTest.plan(t, 11));
		BlockSubdivider.densify(plan, Set.of(0), List.of());
		MasterPlan back = MasterPlan.restore(plan.seed(), plan.extent(), plan.plazaY(), plan.gridRle(), plan.ringRle(), plan.streetHeights(), plan.blocks(),
				plan.parcels(), plan.playerAreas(), plan.fixedAreas());
		assertEquals(plan.parcels().size(), back.parcels().size());
		for (int i = 0; i < plan.parcels().size(); i++) {
			assertEquals(plan.parcels().get(i).retired(), back.parcels().get(i).retired());
			assertEquals(plan.parcels().get(i).rect(), back.parcels().get(i).rect());
		}
		// aposentado nao serve para nenhuma obra
		VillageLayout layout = new VillageLayout();
		layout.setMaster(back);
		MasterPlan.Parcel retired = back.parcels().stream().filter(MasterPlan.Parcel::retired).findFirst().orElseThrow();
		assertEquals(VillageLayout.Problem.OUTSIDE, layout.parcelProblem("house", retired, 9, 9, 6, -1));
	}
}
