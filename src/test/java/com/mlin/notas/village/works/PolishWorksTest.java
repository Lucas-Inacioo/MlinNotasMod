package com.mlin.notas.village.works;

import com.mlin.notas.village.Facing;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VillageStyle;
import com.mlin.notas.village.Zone;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Esculturas da praca e fachadas do centro (rodada de acabamento): nada solto, nada enterrado, cada estatua distinta. */
class PolishWorksTest {
	private static final List<String> BOSSES = List.of("cobalt_golem", "abyssal_guardian", "igneous_colossus", "astral_herald", "ender_dragon", "wither", "");

	@Test
	void everyStatueIsGroundedHasASignAndLooksDifferent() {
		MasterPlan plan = WorksTestSupport.plan(0, 11);
		java.util.Set<String> shapes = new java.util.HashSet<>();
		for (String boss : BOSSES) {
			for (VillageStyle style : VillageStyle.values()) {
				WorkContext ctx = new WorkContext(plan, style, 9, MasterPlan.RINGS, (x, z) -> false, java.util.Set.of(), List.of(), java.util.Set.of(), List.of(), boss, null,
						(x, z) -> Integer.MIN_VALUE);
				Blueprint bp = PlazaWorks.plan(ctx, 4);
				WorksTestSupport.assertGrounded(plan, bp, "estatua " + boss + " " + style);
				assertTrue(bp.ordered().stream().anyMatch(b -> b.text() != null && b.text()[0].equals("tr:sign.mlinnotas.statue.header")), "placa do pedestal");
				if (style == VillageStyle.PLAINS) {
					StringBuilder sb = new StringBuilder();
					bp.ordered().stream().filter(b -> b.y() > plan.plazaY() + PlazaSculpture.PLINTH).forEach(b -> sb.append(b.state()).append(';'));
					shapes.add(sb.toString());
				}
			}
		}
		assertEquals(BOSSES.size(), shapes.size(), "cada chefe tem a sua silhueta");
	}

	@Test
	void clockTowerHasDialsBellAndSpire() {
		MasterPlan plan = WorksTestSupport.plan(0, 11);
		WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 3);
		Blueprint bp = PlazaWorks.plan(ctx, 5);
		WorksTestSupport.assertGrounded(plan, bp, "torre do relogio");
		long dials = bp.ordered().stream().filter(b -> b.state().equals("minecraft:smooth_quartz")).count();
		assertTrue(dials >= 16, "quatro mostradores: " + dials);
		assertTrue(bp.ordered().stream().anyMatch(b -> b.state().startsWith("minecraft:bell[")), "sino");
		assertTrue(bp.ordered().stream().anyMatch(b -> b.state().equals("minecraft:lightning_rod")), "agulha");
		int max = bp.ordered().stream().mapToInt(WBlock::y).max().orElse(0);
		assertTrue(max - plan.plazaY() >= 17, "altura " + (max - plan.plazaY()));
	}

	/** Sites de teste: um lote medio do centro com a planta girada para a rua. */
	private static List<FacadeWorks.Site> sites(MasterPlan plan, String kind) {
		List<FacadeWorks.Site> out = new ArrayList<>();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.zone() != Zone.CENTER || p.retired() || p.player() || p.size() != com.mlin.notas.village.SizeClass.M) {
				continue;
			}
			int rot = switch (p.front()) {
				case SOUTH -> 0;
				case WEST -> 1;
				case NORTH -> 2;
				case EAST -> 3;
			};
			out.add(new FacadeWorks.Site(kind, 1, 5, p.rect().x0(), p.rect().z0(), rot, p.id()));
		}
		return out;
	}

	@Test
	void facadesAndArcadesStayGrounded() {
		int blocks = 0;
		for (int t = 0; t < WorksTestSupport.TERRAINS.size(); t++) {
			MasterPlan plan = WorksTestSupport.plan(t, 11 + t * 7);
			WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 4);
			Blueprint arcades = FacadeWorks.arcades(ctx, sites(plan, "market"));
			WorksTestSupport.assertGrounded(plan, arcades, "arcadas terreno " + t);
			blocks += arcades.size();
			assertFalse(plan.parcels().isEmpty());
			// as casas do tipo "house" cabem em lote pequeno; as fachadas usam qualquer lote do centro com a planta no lugar
			Blueprint facades = FacadeWorks.facades(ctx, sites(plan, "house"));
			WorksTestSupport.assertGrounded(plan, facades, "fachadas terreno " + t);
			blocks += facades.size();
		}
		assertTrue(blocks > 30, "as fachadas fazem alguma coisa: " + blocks);
	}

	@Test
	void townhousesFillVacantCentreLotsAndAreGrounded() {
		int blocks = 0;
		for (int t = 0; t < WorksTestSupport.TERRAINS.size(); t++) {
			MasterPlan plan = WorksTestSupport.plan(t, 11 + t * 7);
			for (VillageStyle style : VillageStyle.values()) {
				WorkContext ctx = WorksTestSupport.ctx(plan, style, 4);
				Blueprint bp = FacadeWorks.townhouses(ctx);
				WorksTestSupport.assertGrounded(plan, bp, "sobrados " + style + " terreno " + t);
				blocks += bp.size();
				for (WBlock b : bp.ordered()) {
					MasterPlan.Parcel p = plan.parcelAt(b.x(), b.z());
					assertTrue(p == null || p.zone() == Zone.CENTER, "sobrado fora do centro em " + b.x() + "," + b.z());
				}
			}
		}
		assertTrue(blocks > 500, "ha sobrados: " + blocks);
	}

	@Test
	void sidesMapToFacings() {
		assertEquals(Facing.WEST, FacadeWorks.front(1));
		assertEquals(Facing.SOUTH, FacadeWorks.front(0));
		assertEquals(Facing.EAST, FacadeWorks.front(7));
	}
}
