package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.PlotPlanner;
import com.mlin.notas.village.VillageStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Terrenos do jogador (Vilas 3.0 fase 5): nenhuma obra publica poe bloco dentro de um terreno. */
class PlotWorksTest {
	@Test
	void noPublicWorkPlacesABlockInsideAPlayerPlot() {
		for (int t = 0; t < WorksTestSupport.TERRAINS.size(); t++) {
			MasterPlan plan = WorksTestSupport.plan(t, 11 + t * 7);
			List<MasterPlan.Parcel> plots = PlotPlanner.reserve(plan, PlotPlanner.COUNT);
			assertEquals(PlotPlanner.COUNT, plots.size(), "terreno " + t + ": dois terrenos");
			plots.forEach(MasterPlan.Parcel::player);
			WorkContext ctx = WorksTestSupport.ctx(plan, VillageStyle.PLAINS, 11 + t * 7);
			List<Blueprint> all = new ArrayList<>();
			for (int stage = 1; stage <= 5; stage++) {
				all.add(StreetWorks.plan(ctx, stage));
				all.add(PlazaWorks.plan(ctx, stage));
			}
			for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
				all.add(ParcelWorks.stakes(ctx, ring));
				all.add(BridgeWorks.plan(ctx, ring));
			}
			all.add(ParcelWorks.gardens(ctx));
			all.add(ParcelWorks.rural(ctx));
			all.add(ParcelWorks.trees(ctx));
			all.add(ParcelWorks.decor(ctx));
			all.add(WallWorks.palisade(ctx));
			all.add(WallWorks.woodenGates(ctx));
			all.add(WallWorks.battlements(ctx));
			all.add(CrownWorks.hedge(ctx));
			all.add(CrownWorks.lamps(ctx));
			all.add(CrownWorks.promenade(ctx));
			all.add(CrownWorks.barbican(ctx));
			all.add(FacadeWorks.townhouses(ctx));
			int blocks = 0;
			for (Blueprint bp : all) {
				for (WBlock b : bp.ordered()) {
					blocks++;
					assertFalse(plan.insidePlot(b.x(), b.z()), "obra publica dentro do terreno em " + b.x() + "," + b.z() + " " + b.state());
				}
			}
			assertFalse(blocks == 0, "as obras planejaram alguma coisa");
		}
	}
}
