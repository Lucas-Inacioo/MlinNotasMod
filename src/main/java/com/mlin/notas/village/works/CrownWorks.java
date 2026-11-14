package com.mlin.notas.village.works;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.OuterWallPlanner;
import com.mlin.notas.village.RuralRing;
import com.mlin.notas.village.VillagePlanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Obras publicas da fase 8 das Vilas 2.0 (logica pura): a cerca viva do anel rural com porteiras onde as avenidas saem e
 * postes de lanterna ao longo das ruas dos campos (Cidade, com o anel rural aplainado), o passeio do centro historico
 * em cima da muralha de pedra (Metropole: lanternas e vasos de flor nas ameias) e as barbacas na frente dos portoes
 * dos eixos da muralha externa (Cidade da Coroa: duas torres e um arco por fora).
 *
 * <p>Referencias de forma: sebes ("hedgerows") com porteiras de madeira dos campos ingleses, os postes de lanterna das
 * estradas rurais das cidades do Planet Minecraft, o passeio sobre as muralhas de Lucca e as barbacas de Carcassonne e
 * da Porta de Sao Jorge; escrito do zero.
 */
public final class CrownWorks {
	/** Distancia minima entre dois postes do anel rural. */
	public static final int LAMP_SPACING = 10;

	private CrownWorks() {
	}

	// ---------------------------------------------------------------- anel rural

	/** Cerca viva de 2 de altura na borda do anel rural; onde uma avenida sai, dois mouroes de tronco com lanterna. */
	public static Blueprint hedge(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		if (!plan.rural()) {
			return bp;
		}
		Mats m = ctx.mats();
		int e = RuralRing.EDGE;
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (!RuralRing.hedgeLine(x, z) || plan.street(x, z) || !ctx.ready(x, z) || plan.insidePlayerArea(x, z)) {
					continue;
				}
				if (ctx.terrain().water(x, z) > 0) {
					continue;
				}
				int g = ctx.terrain().ground(x, z);
				bp.needRing(RuralRing.RING);
				boolean post = false;
				for (int dx = -1; dx <= 1 && !post; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (plan.street(x + dx, z + dz)) {
							post = true;
							break;
						}
					}
				}
				if (post) {
					// mourao da porteira
					for (int y = 1; y <= 3; y++) {
						bp.above(x, g + y, z, m.log());
					}
					bp.above(x, g + 4, z, Mats.lantern(false));
				} else {
					bp.above(x, g + 1, z, m.leavesKeep());
					bp.above(x, g + 2, z, m.leavesKeep());
				}
			}
		}
		return bp;
	}

	/** Postes de lanterna (2 cercas e lanterna) nas sobras dos quarteiroes rurais ao lado das ruas, a cada ~10 blocos. */
	public static Blueprint lamps(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		if (!plan.rural()) {
			return bp;
		}
		Mats m = ctx.mats();
		for (int[] c : lampSpots(plan)) {
			int x = c[0];
			int z = c[1];
			if (!ctx.ready(x, z) || !ctx.free(x, z) || ctx.insideLot(x, z)) {
				continue;
			}
			int g = plan.plannedHeight(x, z);
			bp.needRing(RuralRing.RING);
			bp.above(x, g + 1, z, m.post());
			bp.above(x, g + 2, z, m.post());
			bp.above(x, g + 3, z, Mats.lantern(false));
		}
		return bp;
	}

	/** Lugares dos postes do anel rural: sobra de quarteirao rural encostada numa rua, espacados, do centro para fora. */
	public static List<int[]> lampSpots(MasterPlan plan) {
		List<int[]> candidates = new ArrayList<>();
		int e = plan.extent();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				MasterPlan.Block b = plan.blockAt(x, z);
				if (b == null || b.ring() != RuralRing.RING || plan.parcelAt(x, z) != null) {
					continue;
				}
				boolean byStreet = false;
				for (com.mlin.notas.village.Facing f : com.mlin.notas.village.Facing.values()) {
					byStreet |= plan.ground(x + f.dx, z + f.dz);
				}
				if (byStreet) {
					candidates.add(new int[] {x, z});
				}
			}
		}
		candidates.sort(Comparator.<int[]>comparingInt(c -> c[0] * c[0] + c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		List<int[]> out = new ArrayList<>();
		for (int[] c : candidates) {
			boolean far = true;
			for (int[] p : out) {
				int dx = p[0] - c[0];
				int dz = p[1] - c[1];
				if (dx * dx + dz * dz < LAMP_SPACING * LAMP_SPACING) {
					far = false;
					break;
				}
			}
			if (far) {
				out.add(c);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- centro historico

	/**
	 * Passeio sobre a muralha de pedra (centro historico): lanterna num mourao a cada ~9 celulas e vaso de flor em
	 * parte das ameias. So entra onde ha ar em cima (ou bloco das obras publicas, as ameias).
	 */
	public static Blueprint promenade(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		Mats m = ctx.mats();
		int h = VillagePlanner.WALL_HALF;
		String[] flowers = {"minecraft:potted_red_tulip", "minecraft:potted_cornflower", "minecraft:potted_oxeye_daisy", "minecraft:potted_azure_bluet"};
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!VillagePlanner.wallCell(x, z) || !ctx.free(x, z)) {
					continue;
				}
				int top = ctx.columnTop().apply(x, z);
				if (top == Integer.MIN_VALUE) {
					continue;
				}
				int k = Math.floorMod(x * 3 + z * 5, 9);
				if (k == 0) {
					bp.above(x, top + 1, z, m.post());
					bp.above(x, top + 2, z, Mats.lantern(false));
				} else if (k == 4 || k == 7) {
					bp.above(x, top + 1, z, flowers[ctx.roll(x, z, 83, flowers.length)]);
				}
			}
		}
		return bp;
	}

	// ---------------------------------------------------------------- barbacas da muralha externa

	/**
	 * Barbaca na frente de cada portao de eixo da muralha externa: duas torres 3x3 dos lados da avenida, por fora da
	 * muralha, ligadas por um arco com lanterna pendurada sobre a avenida.
	 */
	public static Blueprint barbican(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		if (!plan.rural()) {
			return bp;
		}
		Mats m = ctx.mats();
		for (OuterWallPlanner.Gate gate : OuterWallPlanner.gates(plan, ctx.terrain())) {
			if (!gate.axis()) {
				continue;
			}
			int dx = gate.dx();
			int dz = gate.dz();
			int lx = -dz;
			int lz = dx;
			int cx = gate.center()[0];
			int cz = gate.center()[1];
			int base = gate.base();
			int top = base + OuterWallPlanner.HEIGHT + 3;
			for (int side : new int[] {-1, 1}) {
				for (int k = 1; k <= 3; k++) {
					for (int w = 3; w <= 5; w++) {
						int x = cx + dx * k + lx * w * side;
						int z = cz + dz * k + lz * w * side;
						if (!RuralRing.inBand(x, z) || plan.street(x, z) || plan.insidePlayerArea(x, z) || ctx.terrain().water(x, z) > 0) {
							continue;
						}
						int g = ctx.terrain().ground(x, z);
						boolean edge = k != 2 || w != 4;
						for (int y = g + 1; y <= top; y++) {
							boolean slit = !edge ? false : (k == 2 || w == 4) && (y == base + 4 || y == base + 8);
							bp.above(x, y, z, slit ? "minecraft:iron_bars" : y == top || y == g + 1 ? m.bricks() : m.cobble());
						}
						if (edge && Math.floorMod(k + w, 2) == 0) {
							bp.above(x, top + 1, z, m.bricks());
						}
						if (!edge) {
							bp.above(x, top + 1, z, Mats.lantern(false));
						}
					}
				}
			}
			// arco sobre a avenida na ponta de fora
			int k = 3;
			for (int w = -2; w <= 2; w++) {
				int x = cx + dx * k + lx * w;
				int z = cz + dz * k + lz * w;
				for (int y = base + 5; y <= base + 7; y++) {
					bp.above(x, y, z, y == base + 5 ? m.bricks() : m.cobble());
				}
				bp.above(x, base + 8, z, Math.floorMod(w, 2) == 0 ? m.bricks() : m.brickSlab());
				if (w == 0) {
					bp.above(x, base + 4, z, Mats.lantern(true));
				}
			}
		}
		return bp;
	}
}
