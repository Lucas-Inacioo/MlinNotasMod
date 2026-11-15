package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Terrenos do jogador (Vilas 3.0 fase 5, logica pura): "o terreno da casa e uma area pre-gerada, vazia, onde o jogador
 * constroi sem sair dos limites, e o terreno se adapta para pertencer a vila".
 *
 * <p>Um terreno e um {@link MasterPlan.Parcel lote do plano} com a marca {@link MasterPlan.Parcel#plot()}: a vila nunca
 * constroi, aplaina nem decora dentro dele (todo o codigo que ja respeitava "lote do jogador" vale; quarteirao e rua
 * em volta seguem normais) e, com cama e porta dentro, ele conta como casa da vila.
 * <ul>
 * <li>{@link #reserve}: mundo novo. Recorta {@link #SIZE} (L, 19x13) em quarteiroes dos aneis 2 e 3, o mais perto da
 * praca, com frente para a rua e em lados diferentes da vila; como o quarteirao e um plato, o terreno ja nasce plano na
 * altura certa e com o acesso do lote ate a rua (celulas do quarteirao, ate {@link BlockSubdivider#ACCESS_MAX}).</li>
 * <li>{@link #add}: vila que ja existe. Registra um retangulo qualquer (a casa do jogador com folga) como terreno: os
 * lotes livres que ele cobre saem do plano, as celulas de quarteirao viram area recortada e o campo de alturas e refeito
 * com o terreno pinado ({@link FieldPins#plot}).</li>
 * </ul>
 */
public final class PlotPlanner {
	/** Tamanho dos terrenos da vila nova. */
	public static final SizeClass SIZE = SizeClass.L;
	/** Quantos terrenos a vila inicial nova reserva. */
	public static final int COUNT = 2;
	/** Menor lado de um terreno registrado a mao. */
	public static final int MIN_SIDE = 4;

	private PlotPlanner() {
	}

	/** Por que um terreno nao pode ser registrado. */
	public enum Problem {
		TOO_SMALL, STREET, LOT, PLOT, OUTSIDE_FIELD
	}

	/** Resultado de {@link #add}: o terreno novo ou o problema (com a coluna que atrapalha). */
	public record Added(MasterPlan.Parcel plot, Problem problem, int x, int z, List<MasterPlan.Parcel> retired) {
	}

	/**
	 * Reserva {@code count} terrenos de {@link #SIZE} num plano novo (antes de qualquer lote de obra): aneis 2 e 3, o mais
	 * perto da praca, cada um num lado diferente. Devolve os terrenos (menos que {@code count} se o plano nao comportar).
	 */
	public static List<MasterPlan.Parcel> reserve(MasterPlan plan, int count) {
		List<MasterPlan.Parcel> out = new ArrayList<>();
		int[][] bounds = BlockSubdivider.blockBounds(plan);
		boolean[] keep = BlockSubdivider.emptyTaken(plan);
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (!p.retired() && p.player()) {
				BlockSubdivider.occupy(plan, keep, p.rect());
			}
		}
		for (int k = 0; k < count; k++) {
			BlockSubdivider.Carving best = null;
			double bestScore = Double.MAX_VALUE;
			for (MasterPlan.Block block : plan.blocks()) {
				if (block.ring() != 2 && block.ring() != 3 || block.zone() == null) {
					continue;
				}
				BlockSubdivider.Carving c = BlockSubdivider.carve(plan, block, bounds[block.id()], SIZE, keep);
				if (c == null) {
					continue;
				}
				double cx = (c.rect().x0() + c.rect().x1()) / 2.0;
				double cz = (c.rect().z0() + c.rect().z1()) / 2.0;
				double score = Math.hypot(cx, cz) + (block.ring() == 3 ? 40 : 0);
				for (MasterPlan.Parcel other : out) {
					double ox = (other.rect().x0() + other.rect().x1()) / 2.0;
					double oz = (other.rect().z0() + other.rect().z1()) / 2.0;
					double cos = (cx * ox + cz * oz) / Math.max(1, Math.hypot(cx, cz) * Math.hypot(ox, oz));
					// lados opostos da vila: o mais perto de 180 graus ganha
					score += (cos + 1) * 30;
				}
				if (score < bestScore) {
					bestScore = score;
					best = c;
				}
			}
			if (best == null) {
				break;
			}
			List<MasterPlan.Parcel> retired = new ArrayList<>();
			MasterPlan.Parcel made = BlockSubdivider.applyCarving(plan, best, keep, Set.of(), retired);
			made.setPlot(true);
			out.add(made);
			BlockSubdivider.occupy(plan, keep, made.rect());
		}
		return out;
	}

	/** Chao dos terrenos reservados = plato do quarteirao (depois de o campo estar resolvido e escrito no plano). */
	public static void settleFloors(MasterPlan plan) {
		for (MasterPlan.Parcel p : plan.plots()) {
			MasterPlan.Block b = plan.block(p.block());
			if (b != null && p.floor() == Integer.MIN_VALUE) {
				p.setFloor(b.target());
			}
		}
	}

	/**
	 * Registra {@code rect} (relativo ao centro da vila) como terreno do jogador com chao em {@code floor}. {@code
	 * reserved}: lotes do plano ja ocupados por obra (pronta, em andamento ou reservada). Nao muda nada se houver problema.
	 */
	public static Added add(MasterPlan plan, VillageState.Rect rect, int floor, Set<Integer> reserved, List<VillageState.Rect> lotRects) {
		if (rect.x1() - rect.x0() + 1 < MIN_SIDE || rect.z1() - rect.z0() + 1 < MIN_SIDE) {
			return new Added(null, Problem.TOO_SMALL, rect.x0(), rect.z0(), List.of());
		}
		for (VillageState.Rect lot : lotRects) {
			if (lot.overlaps(rect, 0)) {
				return new Added(null, Problem.LOT, Math.max(lot.x0(), rect.x0()), Math.max(lot.z0(), rect.z0()), List.of());
			}
		}
		for (int x = rect.x0(); x <= rect.x1(); x++) {
			for (int z = rect.z0(); z <= rect.z1(); z++) {
				int c = plan.code(x, z);
				if (c == MasterPlan.PLAZA || c == MasterPlan.LAKE || c >= MasterPlan.STREET && c < MasterPlan.BLOCK) {
					return new Added(null, Problem.STREET, x, z, List.of());
				}
			}
		}
		List<MasterPlan.Parcel> retired = new ArrayList<>();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.retired() || !p.rect().overlaps(rect, 1)) {
				continue;
			}
			if (p.plot() && p.rect().overlaps(rect, 0)) {
				return new Added(null, Problem.PLOT, Math.max(p.rect().x0(), rect.x0()), Math.max(p.rect().z0(), rect.z0()), List.of());
			}
			if (p.plot() || reserved.contains(p.id())) {
				if (p.rect().overlaps(rect, 0)) {
					return new Added(null, Problem.LOT, Math.max(p.rect().x0(), rect.x0()), Math.max(p.rect().z0(), rect.z0()), List.of());
				}
				continue;
			}
			retired.add(p);
		}
		for (MasterPlan.Parcel p : retired) {
			plan.retireParcel(p.id());
		}
		plan.carveCells(rect);
		MasterPlan.Parcel made = new MasterPlan.Parcel(plan.parcels().size(), nearestBlock(plan, rect), rect, front(plan, rect), SIZE,
				zoneAt(plan, rect), false);
		made.setPlot(true);
		made.setFloor(floor);
		plan.addParcel(made);
		return new Added(made, null, 0, 0, retired);
	}

	/** Tira o terreno (o lote some do plano; as celulas recortadas continuam recortadas ate o plano ser refeito). */
	public static boolean remove(MasterPlan plan, int parcelId) {
		MasterPlan.Parcel p = plan.parcel(parcelId);
		if (p == null || !p.plot() || p.retired()) {
			return false;
		}
		plan.retireParcel(parcelId);
		return true;
	}

	private static int nearestBlock(MasterPlan plan, VillageState.Rect r) {
		int cx = (r.x0() + r.x1()) / 2;
		int cz = (r.z0() + r.z1()) / 2;
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for (MasterPlan.Block b : plan.blocks()) {
			double d = Math.hypot(b.cx() - cx, b.cz() - cz);
			if (d < bestDist && d < 40) {
				bestDist = d;
				best = b.id();
			}
		}
		return best;
	}

	private static Zone zoneAt(MasterPlan plan, VillageState.Rect r) {
		MasterPlan.Block b = plan.block(nearestBlock(plan, r));
		return b != null && b.zone() != null ? b.zone() : Zone.DISTRICT;
	}

	/** Lado do terreno que fica mais perto do centro da vila (so para a placa e o desenho). */
	private static Facing front(MasterPlan plan, VillageState.Rect r) {
		double cx = (r.x0() + r.x1()) / 2.0;
		double cz = (r.z0() + r.z1()) / 2.0;
		if (Math.abs(cx) > Math.abs(cz)) {
			return cx > 0 ? Facing.WEST : Facing.EAST;
		}
		return cz > 0 ? Facing.NORTH : Facing.SOUTH;
	}

	/** Posicao da placa (coluna x, z) perto do canto da frente do terreno. */
	public static int[] signSpot(MasterPlan.Parcel p) {
		VillageState.Rect r = p.rect();
		return switch (p.front()) {
			case SOUTH -> new int[] {r.x0() + 1, r.z1()};
			case NORTH -> new int[] {r.x1() - 1, r.z0()};
			case EAST -> new int[] {r.x1(), r.z1() - 1};
			default -> new int[] {r.x0(), r.z0() + 1};
		};
	}

	/**
	 * Caminho do terreno ate a rua mais perto pelo plano (colunas de quarteirao/praca sem terreno, ate rua): lista de
	 * colunas {x, z} do retangulo (borda) ate a primeira celula de rua. Vazio se nao houver caminho ate {@code max} passos.
	 * Serve de teste (e de dica) do "tem acesso a pe": a altura ja e garantida pelo plato/rampa do campo.
	 */
	public static List<int[]> streetPath(MasterPlan plan, MasterPlan.Parcel p, int max) {
		VillageState.Rect r = p.rect();
		int size = 2 * plan.extent() + 1;
		int[] parent = new int[size * size];
		java.util.Arrays.fill(parent, -2);
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				if (plan.in(x, z)) {
					parent[(x + plan.extent()) * size + z + plan.extent()] = -1;
					queue.add(new int[] {x, z, 0});
				}
			}
		}
		while (!queue.isEmpty()) {
			int[] cur = queue.poll();
			if (cur[2] >= max) {
				continue;
			}
			for (Facing f : Facing.values()) {
				int nx = cur[0] + f.dx;
				int nz = cur[1] + f.dz;
				if (!plan.in(nx, nz)) {
					continue;
				}
				int i = (nx + plan.extent()) * size + nz + plan.extent();
				if (parent[i] != -2) {
					continue;
				}
				if (plan.street(nx, nz) || plan.plaza(nx, nz)) {
					List<int[]> path = new ArrayList<>();
					path.add(new int[] {nx, nz});
					int px = cur[0];
					int pz = cur[1];
					while (!r.contains(px, pz)) {
						path.add(new int[] {px, pz});
						int pi = parent[(px + plan.extent()) * size + pz + plan.extent()];
						px = pi / size - plan.extent();
						pz = pi % size - plan.extent();
					}
					java.util.Collections.reverse(path);
					return path;
				}
				if (plan.code(nx, nz) >= MasterPlan.BLOCK || plan.code(nx, nz) == MasterPlan.CARVED) {
					parent[i] = (cur[0] + plan.extent()) * size + cur[1] + plan.extent();
					queue.add(new int[] {nx, nz, cur[2] + 1});
				}
			}
		}
		return List.of();
	}
}
