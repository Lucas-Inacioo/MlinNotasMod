package com.mlin.notas.village;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Anel rural fora da muralha externa (logica pura; Vilas 2.0 fase 8). O plano diretor guardado vai ate a meia-largura 74
 * e ficava sem lote para tudo (~25 lotes num terreno de verdade): plantacoes, moinho, pomar, curral, apiario, pedreira,
 * curtume, vinicola e serraria passam para um anel de campos entre a muralha externa e uma cerca viva, com quarteiroes
 * mais fundos e as avenidas continuando pelos portoes.
 *
 * <p>Faixas a partir do centro (meia-largura do octogono, mesmo corte de cantos do plano): ate 74 o plano de sempre
 * (nunca muda); 75-79 a faixa da muralha externa ({@link MasterPlan#WALL_BAND}: muralha em 75, torres e barbacas para
 * fora ate 79; so as avenidas atravessam, pelos portoes); 80-110 o anel rural (anel de terraplenagem
 * {@link #RING}, zona {@link Zone#RURAL}) com uma rua de anel em 95 (duas fileiras de quarteiroes de 14 de fundo) e
 * travessas a cada {@link StreetPlanner#MAX_FRONTAGE} de frente; 111-112 a borda ({@link MasterPlan#RURAL_EDGE}: cerca
 * viva com porteiras onde as avenidas saem).
 *
 * <p>A expansao ({@link #expand}) e feita uma vez, quando a vila chega na Cidade: a grade cresce, as celulas de antes sao
 * copiadas como estao, os quarteiroes e lotes novos ganham ids depois dos antigos e a terraplenagem nova so mexe no
 * anel novo (quarteiroes e ruas antigos ficam parados na altura que tinham). Construcao de jogador vira recorte com a
 * rua em volta, como no resto do plano.
 *
 * <p>Referencias de forma: campos abertos em faixas fora das muralhas de cidades medievais (Carcassonne, Avila, os
 * "open fields" ingleses) e as fazendas fora da muralha das cidades de servidor do Planet Minecraft (Medieval Kingdom of
 * Arendal, Castle town of Aldmere); escrito do zero.
 */
public final class RuralRing {
	/** Anel de terraplenagem do anel rural. */
	public static final int RING = 7;
	/** O anel rural abre com este numero de aneis comuns abertos (5 = arredores, Cidade). */
	public static final int OPENS_WITH = 5;
	/** Nivel em que a vila ganha o anel rural. */
	public static final VillageLevel OPENS = VillageLevel.TOWN;
	/** Linha da muralha externa (meia-largura; 149x149 por dentro = meia-largura 74). */
	public static final int WALL = 75;
	/** Ultima meia-largura da faixa da muralha (torres e barbacas). */
	public static final int BAND = 79;
	/** Rua de anel dos campos. */
	public static final int ROAD = 95;
	/** Ultima meia-largura de quarteirao rural. */
	public static final int HALF = 110;
	/** Ultima meia-largura da borda (cerca viva em {@code HALF + 1}). */
	public static final int EDGE = 112;
	/** Meia-largura da grade do plano com o anel rural. */
	public static final int EXTENT = EDGE + 2;

	private static final StreetPlanner.Octagon OLD = StreetPlanner.Octagon.of(StreetPlanner.OUTER, null);
	private static final StreetPlanner.Octagon BAND_OCT = StreetPlanner.Octagon.of(BAND, null);
	private static final StreetPlanner.Octagon ROAD_OCT = StreetPlanner.Octagon.of(ROAD, null);
	private static final StreetPlanner.Octagon HALF_OCT = StreetPlanner.Octagon.of(HALF, null);
	private static final StreetPlanner.Octagon EDGE_OCT = StreetPlanner.Octagon.of(EDGE, null);
	private static final StreetPlanner.Octagon WALL_OCT = StreetPlanner.Octagon.of(WALL, null);

	private RuralRing() {
	}

	/** Celula fora do plano de antes (meia-largura 74). */
	public static boolean outsideOld(int x, int z) {
		return OLD.f(x, z) > 0.5;
	}

	/** Celula da faixa da muralha externa (75..79). */
	public static boolean inBand(int x, int z) {
		return outsideOld(x, z) && BAND_OCT.f(x, z) <= 0.5;
	}

	/** Celula da linha da muralha externa (a borda do octogono de meia-largura 75). */
	public static boolean wallLine(int x, int z) {
		return outsideOld(x, z) && WALL_OCT.f(x, z) <= 0.5;
	}

	/** Celula dos campos (80..110). */
	public static boolean inFields(int x, int z) {
		return BAND_OCT.f(x, z) > 0.5 && HALF_OCT.f(x, z) <= 0.5;
	}

	/** Celula da borda (111..112): cerca viva. */
	public static boolean inEdge(int x, int z) {
		return HALF_OCT.f(x, z) > 0.5 && EDGE_OCT.f(x, z) <= 0.5;
	}

	/** Linha da cerca viva (a primeira celula da borda). */
	public static boolean hedgeLine(int x, int z) {
		return inEdge(x, z) && HALF_OCT.f(x, z) <= 1.5;
	}

	/** Dentro da area da vila com o anel rural (ate a borda). */
	public static boolean insideRural(int x, int z) {
		return EDGE_OCT.f(x, z) <= 0.5;
	}

	/** Dentro da muralha externa (o lado de dentro da linha da muralha). */
	public static boolean insideWalls(int x, int z) {
		return !outsideOld(x, z);
	}

	/**
	 * Plano com o anel rural: copia o plano {@code old} (meia-largura {@link StreetPlanner#OUTER}) numa grade de meia-
	 * largura {@link #EXTENT} e planeja a faixa da muralha e os campos com o terreno de agora. Se {@code old} ja tem o
	 * anel rural, devolve ele mesmo.
	 */
	public static MasterPlan expand(MasterPlan old, Terrain terrain) {
		if (old.rural()) {
			return old;
		}
		int e = EXTENT;
		int size = 2 * e + 1;
		int[] grid = new int[size * size];
		byte[] rings = new byte[size * size];
		int[] heights = new int[size * size];
		int[] bed = new int[size * size];
		int[] water = new int[size * size];
		boolean[] fixedStreet = new boolean[size * size];
		boolean[] player = new boolean[size * size];
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				int i = (x + e) * size + (z + e);
				if (!outsideOld(x, z)) {
					int c = old.code(x, z);
					grid[i] = c;
					rings[i] = (byte) old.ring(x, z);
					if (old.ground(x, z)) {
						heights[i] = old.streetHeight(x, z);
						fixedStreet[i] = true;
					}
					int h = old.plannedHeight(x, z);
					bed[i] = h == Integer.MIN_VALUE ? old.plazaY() : h;
					continue;
				}
				if (!insideRural(x, z)) {
					grid[i] = MasterPlan.OUT;
					bed[i] = old.plazaY();
					continue;
				}
				water[i] = terrain.water(x, z);
				bed[i] = terrain.ground(x, z) - water[i];
				player[i] = !inBand(x, z) && terrain.playerBuilt(x, z);
			}
		}
		boolean[] sparse = new boolean[size * size];
		List<VillageState.Rect> areas = MasterPlan.clusters(player, e, sparse);
		StreetPlanner.Result r = new StreetPlanner.Result(e, new StreetPlanner.Octagon[] {ROAD_OCT}, EDGE_OCT);
		// rua do anel dos campos
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				double f = ROAD_OCT.f(x, z);
				if (f > -1.5 && f <= 1.5 && inFields(x, z)) {
					r.mark(x, z, StreetPlanner.RING);
				}
			}
		}
		// avenidas: continuam retas a partir de onde saem do plano antigo
		for (double[][] line : exits(old)) {
			StreetPlanner.raster(r, line, StreetPlanner.AVENUE_WIDTH / 2.0, StreetPlanner.AVENUE);
		}
		lanes(r, new Random(old.seed() ^ 0x2A7A1L));
		for (VillageState.Rect area : areas) {
			StreetPlanner.loop(r, area, (x, z) -> !inFields(x, z) || otherArea(areas, area, x, z));
		}
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				int i = (x + e) * size + (z + e);
				if (!outsideOld(x, z) || !insideRural(x, z)) {
					continue;
				}
				int kind = r.kind(x, z);
				boolean deep = water[i] > MasterPlan.SHALLOW;
				if (inBand(x, z) || inEdge(x, z)) {
					if (kind == StreetPlanner.AVENUE) {
						grid[i] = (deep ? MasterPlan.BRIDGE : MasterPlan.STREET) + kind;
						rings[i] = RING;
					} else {
						grid[i] = inBand(x, z) ? MasterPlan.WALL_BAND : MasterPlan.RURAL_EDGE;
						rings[i] = (byte) (inBand(x, z) ? 0 : RING);
					}
					continue;
				}
				rings[i] = RING;
				if (kind != 0) {
					grid[i] = (deep ? MasterPlan.BRIDGE : MasterPlan.STREET) + kind;
				} else if (MasterPlan.insideGrown(areas, x, z, 1) || sparse[i]) {
					grid[i] = MasterPlan.CARVED;
				} else if (deep) {
					grid[i] = MasterPlan.LAKE;
				} else {
					grid[i] = -1;
				}
			}
		}
		int firstBlock = old.blocks().size();
		List<MasterPlan.Block> blocks = new ArrayList<>(old.blocks());
		blocks.addAll(MasterPlan.label(grid, rings, bed, e, firstBlock));
		List<VillageState.Rect> playerAreas = new ArrayList<>(old.playerAreas());
		playerAreas.addAll(areas);
		MasterPlan plan = new MasterPlan(old.seed(), e, old.plazaY(), grid, rings, heights, blocks, old.parcels(), playerAreas, old.fixedAreas());
		plan.setVersion(old.version());
		PlateauGrading.grade(plan, bed, firstBlock, fixedStreet);
		for (MasterPlan.Parcel p : BlockSubdivider.subdivideFrom(plan, old.seed(), firstBlock)) {
			plan.addParcel(p);
		}
		return plan;
	}

	private static boolean otherArea(List<VillageState.Rect> areas, VillageState.Rect self, int x, int z) {
		for (VillageState.Rect a : areas) {
			if (a != self && a.x0() - 1 <= x && x <= a.x1() + 1 && a.z0() - 1 <= z && z <= a.z1() + 1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Linhas das avenidas que continuam para fora: cada grupo de celulas de avenida na ultima fileira do plano antigo
	 * vira uma reta na direcao dele (eixo ou diagonal) ate alem da borda.
	 */
	static List<double[][]> exits(MasterPlan old) {
		int e = old.extent();
		List<int[]> cells = new ArrayList<>();
		java.util.Set<Long> set = new java.util.HashSet<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				double f = OLD.f(x, z);
				if (f > -0.5 && f <= 0.5 && old.streetKind(x, z) == StreetPlanner.AVENUE) {
					cells.add(new int[] {x, z});
					set.add(VillageLayout.col(x, z));
				}
			}
		}
		List<double[][]> out = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (int[] start : cells) {
			if (!seen.add(VillageLayout.col(start[0], start[1]))) {
				continue;
			}
			List<int[]> group = new ArrayList<>();
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				int[] c = queue.poll();
				group.add(c);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						long k = VillageLayout.col(c[0] + dx, c[1] + dz);
						if (set.contains(k) && seen.add(k)) {
							queue.add(new int[] {c[0] + dx, c[1] + dz});
						}
					}
				}
			}
			double cx = 0;
			double cz = 0;
			for (int[] c : group) {
				cx += c[0];
				cz += c[1];
			}
			cx /= group.size();
			cz /= group.size();
			double ax = Math.abs(cx);
			double az = Math.abs(cz);
			int dx = ax > 2 * az ? (int) Math.signum(cx) : az > 2 * ax ? 0 : (int) Math.signum(cx);
			int dz = az > 2 * ax ? (int) Math.signum(cz) : ax > 2 * az ? 0 : (int) Math.signum(cz);
			if (dz == 0) {
				cz = Math.round(cz);
			} else if (dx == 0) {
				cx = Math.round(cx);
			}
			double reach = EXTENT + 4;
			out.add(new double[][] {{cx - dx * 2.0, cz - dz * 2.0}, {cx + dx * reach, cz + dz * reach}});
		}
		return out;
	}

	/**
	 * Travessas dos campos: a frente ao longo da rua do anel entre duas avenidas e dividida em trechos de ate
	 * {@link StreetPlanner#MAX_FRONTAGE}; cada divisao vira uma rua de 3 perpendicular ao lado, atravessando as duas
	 * fileiras (da faixa da muralha ate a borda).
	 */
	static void lanes(StreetPlanner.Result r, Random random) {
		int steps = 720;
		double[][] pts = new double[steps][];
		boolean[] avenue = new boolean[steps];
		for (int i = 0; i < steps; i++) {
			pts[i] = ROAD_OCT.along(2 * Math.PI * i / steps);
			avenue[i] = r.kind((int) Math.round(pts[i][0]), (int) Math.round(pts[i][1])) == StreetPlanner.AVENUE;
		}
		int first = -1;
		for (int i = 0; i < steps; i++) {
			if (avenue[i] && !avenue[(i + steps - 1) % steps]) {
				first = i;
				break;
			}
		}
		if (first < 0) {
			first = 0;
		}
		List<int[]> runs = new ArrayList<>();
		int start = -1;
		for (int j = 0; j <= steps; j++) {
			boolean av = j == steps || avenue[(first + j) % steps];
			if (!av && start < 0) {
				start = j;
			} else if (av && start >= 0) {
				runs.add(new int[] {start, j});
				start = -1;
			}
		}
		for (int[] run : runs) {
			double len = 0;
			for (int s = run[0]; s < run[1]; s++) {
				double[] p = pts[(first + s) % steps];
				double[] q = pts[(first + s + 1) % steps];
				len += Math.hypot(q[0] - p[0], q[1] - p[1]);
			}
			int n = (int) Math.ceil(len / StreetPlanner.MAX_FRONTAGE) - 1;
			for (int j = 1; j <= n; j++) {
				double want = len * j / (n + 1) + (random.nextDouble() - 0.5) * 6;
				double acc = 0;
				int s = run[0];
				while (s < run[1] && acc < want) {
					double[] p = pts[(first + s) % steps];
					double[] q = pts[(first + s + 1) % steps];
					acc += Math.hypot(q[0] - p[0], q[1] - p[1]);
					s++;
				}
				double[] p = pts[(first + s) % steps];
				int side = ROAD_OCT.side(p[0], p[1]);
				double nx = StreetPlanner.SIDES[side][0];
				double nz = StreetPlanner.SIDES[side][1];
				double norm = Math.hypot(nx, nz);
				nx /= norm;
				nz /= norm;
				double sx = Math.round(p[0]);
				double sz = Math.round(p[1]);
				double reach = HALF - ROAD + 2;
				StreetPlanner.raster(r, new double[][] {{sx - nx * reach, sz - nz * reach}, {sx + nx * reach, sz + nz * reach}},
						StreetPlanner.STREET_WIDTH / 2.0, StreetPlanner.LANE);
			}
		}
	}

	// ---------------------------------------------------------------- regras de lote

	/** Tipos que vao para o anel rural (preferem os campos; antes disso, os arredores e o fundo dos bairros). */
	public static final List<String> RURAL_KINDS = List.of("farm", "mill", "orchard", "pen", "apiary", "winery", "sawmill", "quarry", "tannery");

	/** Regras de lote dos tipos rurais: o anel rural primeiro (chamado no fim do bloco estatico de {@link LotRules}). */
	static void registerLots() {
		for (String kind : RURAL_KINDS) {
			LotRules.Rule old = LotRules.of(kind);
			List<Zone> zones = new ArrayList<>();
			zones.add(Zone.RURAL);
			for (Zone z : old.zones()) {
				if (z != Zone.RURAL) {
					zones.add(z);
				}
			}
			LotRules.register(kind, old.size(), true, zones.toArray(new Zone[0]));
		}
	}
}
