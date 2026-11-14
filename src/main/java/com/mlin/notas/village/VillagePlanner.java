package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Onde e como a vila cresce (logica pura). Procura o lote da proxima construcao em aneis em volta do quadro, com a
 * porta virada para a praca, so em colunas livres ({@link Terrain#free}: chao natural, sem agua e sem nada de
 * jogador), sem encostar em outros lotes (2 blocos de folga para o caminho) e com no maximo 3 blocos de desnivel
 * (aplaina pouco: o que sobra acima e cortado, o que falta embaixo vira alicerce). Gera tambem a praca, os postes, a
 * muralha octogonal com torres e portoes e os caminhos de terra ate a praca.
 *
 * <p>Referencias: a muralha com torres nos cantos e portao em arco segue as vilas muradas do ChoiceTheorem's
 * Overhauled Village e as muralhas medievais do Planet Minecraft (Andreth, Gelwenton); lanternas nas entradas e ao
 * longo das ruas, como no guia de cidade medieval do minecraft.how; praca central com poco e bancos.
 */
public final class VillagePlanner {
	/** Raio da praca em volta do quadro (nenhum lote encosta nela, exceto o poco). */
	public static final int PLAZA_RADIUS = 5;
	/** Meia-largura da muralha (quadrado) e corte dos cantos (|x|+|z|). */
	public static final int WALL_HALF = 50;
	public static final int WALL_DIAGONAL = 84;
	public static final int MAX_SPREAD = 3;

	/** Lote no mundo: canto (x, z) relativo ao centro, tamanho ja girado, giro (0 = frente para +z) e y do chao. */
	public record Lot(int x, int z, int w, int d, int rot, int groundY) {
		public VillageState.Rect rect() {
			return new VillageState.Rect(x, z, x + w - 1, z + d - 1);
		}
	}

	/** Uma obra planejada: blocos em ordem de construcao, onde aparecem aldeoes e golens; {@code lotId} = lote do plano (-1 = nenhum). */
	public record Planned(VillageProject project, Lot lot, List<Placement> placements, List<int[]> spawns, List<int[]> golems, int lotId) {
		public Planned(VillageProject project, Lot lot, List<Placement> placements, List<int[]> spawns, List<int[]> golems) {
			this(project, lot, placements, spawns, golems, -1);
		}
	}

	private VillagePlanner() {
	}

	/** Planeja a obra {@code project} (null = nao cabe agora). {@code seed} varia a planta e o lote. */
	public static Planned plan(VillageProject project, VillageState state, Terrain terrain, long seed) {
		return plan(project, state, terrain, seed, state.level().radius());
	}

	/** Como {@link #plan(VillageProject, VillageState, Terrain, long)} com outro raio maximo (vila inicial). */
	public static Planned plan(VillageProject project, VillageState state, Terrain terrain, long seed, int maxR) {
		Random random = new Random(seed);
		return switch (project) {
			case PLAZA -> plaza(state, terrain, random);
			case LAMPS -> lamps(state, terrain);
			case WALL -> wall(state, terrain);
			default -> {
				VillageLayout layout = layout(state, terrain);
				VillageLayout.Lot lot = layout.buildable(project.id(), terrain);
				if (lot == null && layout.master() == null) {
					// nenhum lote reservado serve agora (jogador construiu em cima, etc.): reserva outro
					VillageLayout.Lot extra = layout.addLot(project.id(), state.style(), state.seed(), terrain, random);
					lot = extra == null ? null : layout.buildable(project.id(), terrain);
				}
				yield lot == null ? null : onLot(project, lot, state, terrain);
			}
		};
	}

	/** Plano da vila (calcula na primeira vez, vila de antes do plano inclusive). */
	public static VillageLayout layout(VillageState state, Terrain terrain) {
		if (state.layout() == null) {
			state.setLayout(VillageLayout.create(state, terrain));
		} else if (state.layout().master() == null) {
			// plano de antes do plano diretor: passa para o plano diretor (obras prontas ficam onde estao)
			state.layout().migrate(state, terrain);
		} else if (state.layout().radius() < state.level().radius()) {
			// nivel mudou sem passar pelo crescimento (comando, teste): o plano acompanha
			state.layout().extend(state, terrain, state.level());
		}
		return state.layout();
	}

	/**
	 * Obra no lote {@code lot} do plano: terreno preparado (corte, aterro, rampa e acesso), a planta na altura alvo e a
	 * rua do lote ate a praca.
	 */
	public static Planned onLot(VillageProject project, VillageLayout.Lot lot, VillageState state, Terrain terrain) {
		return onLot(project, lot, state, terrain, VillageLayout.canvas(lot.kind(), state.style(), lot.seed()));
	}

	/** Como {@link #onLot(VillageProject, VillageLayout.Lot, VillageState, Terrain)} com outra planta (melhoria no lugar). */
	public static Planned onLot(VillageProject project, VillageLayout.Lot lot, VillageState state, Terrain terrain, Canvas canvas) {
		VillageLayout layout = state.layout();
		VillageState.Rect rect = lot.rect();
		List<VillageState.Rect> others = new ArrayList<>();
		for (VillageLayout.Lot other : layout.lots()) {
			if (other != lot) {
				others.add(other.rect());
			}
		}
		others.addAll(layout.protectedAreas());
		java.util.function.LongPredicate locked = key -> {
			int x = VillageLayout.colX(key);
			int z = VillageLayout.colZ(key);
			return insideAny(others, x, z) || x * x + z * z <= PLAZA_RADIUS * PLAZA_RADIUS;
		};
		MasterPlan master = layout.master();
		MasterPlan.Parcel parcel = master == null ? null : master.parcel(lot.parcel());
		if (parcel != null) {
			// plano diretor: o quarteirao ja esta aplainado; so o acerto fino (ate 1 bloco) dentro do lote, sem rampa fora
			int target = master.block(parcel.block()).target();
			TerrainPrep.Result prep = TerrainPrep.prepare(rect, List.of(), terrain, locked, target, 0);
			Lot placed = new Lot(lot.x(), lot.z(), lot.w(), lot.d(), lot.rot(), target);
			List<Placement> out = new ArrayList<>(prep.placements());
			out.addAll(place(canvas, placed));
			// quintal da frente ate a rua: caminho na altura do plato
			for (int[] c : lot.access()) {
				if (master.ground(c[0], c[1]) || rect.contains(c[0], c[1]) || !terrain.free(c[0], c[1])) {
					continue;
				}
				out.add(new Placement(c[0], target, c[1], VPart.PATH, null, false));
				out.add(new Placement(c[0], target + 1, c[1], VPart.AIR, null, false));
				out.add(new Placement(c[0], target + 2, c[1], VPart.AIR, null, false));
			}
			return new Planned(project, placed, sort(dedupe(out)), worldPoints(placed, canvas, canvas.spawns()), worldPoints(placed, canvas,
					canvas.golems()), lot.id());
		}
		TerrainPrep.Result prep = TerrainPrep.prepare(rect, lot.access(), terrain, locked);
		Lot placed = new Lot(lot.x(), lot.z(), lot.w(), lot.d(), lot.rot(), prep.target());
		List<Placement> out = new ArrayList<>(prep.placements());
		out.addAll(place(canvas, placed));
		for (int[] c : layout.routeToPlaza(lot)) {
			int x = c[0];
			int z = c[1];
			if (insideAny(others, x, z) || rect.contains(x, z) || x * x + z * z <= PLAZA_RADIUS * PLAZA_RADIUS || !terrain.free(x, z)) {
				continue;
			}
			int g = prep.ground(terrain, x, z);
			out.add(new Placement(x, g, z, VPart.PATH, null, false));
			out.add(new Placement(x, g + 1, z, VPart.AIR, null, false));
			out.add(new Placement(x, g + 2, z, VPart.AIR, null, false));
		}
		return new Planned(project, placed, sort(dedupe(out)), worldPoints(placed, canvas, canvas.spawns()), worldPoints(placed, canvas,
				canvas.golems()), lot.id());
	}

	/** Planeja uma planta pronta (fogueira do acampamento, testes). */
	public static Planned building(VillageProject project, Canvas canvas, VillageState state, Terrain terrain, Random random, int minR,
			int clearance) {
		return building(project, canvas, state.lots(), state.level().radius(), terrain, random, minR, clearance);
	}

	static Planned building(VillageProject project, Canvas canvas, List<VillageState.Rect> taken, int maxR, Terrain terrain, Random random,
			int minR, int clearance) {
		Lot lot = findLot(canvas, taken, maxR, terrain, random, minR, clearance);
		if (lot == null) {
			return null;
		}
		List<Placement> out = new ArrayList<>(place(canvas, lot));
		List<VillageState.Rect> rects = new ArrayList<>(taken);
		rects.add(lot.rect());
		int[] door = toWorld(lot, canvas, canvas.doorX(), canvas.d());
		out.addAll(path(door[0], door[1], terrain, rects));
		return new Planned(project, lot, sort(out), worldPoints(lot, canvas, canvas.spawns()), worldPoints(lot, canvas, canvas.golems()));
	}

	// ---------------------------------------------------------------- lotes

	/** Giro que deixa a frente (+z local) virada para {@code front}. */
	public static int rotationFor(Facing front) {
		return Math.floorMod(front.ordinal() - Facing.SOUTH.ordinal(), 4);
	}

	/** Posicao local (x, z) da planta dentro do lote girado. */
	public static int[] rotate(int x, int z, int w, int d, int rot) {
		return switch (Math.floorMod(rot, 4)) {
			case 1 -> new int[] {d - 1 - z, x};
			case 2 -> new int[] {w - 1 - x, d - 1 - z};
			case 3 -> new int[] {z, w - 1 - x};
			default -> new int[] {x, z};
		};
	}

	static int[] toWorld(Lot lot, Canvas canvas, int x, int z) {
		int[] r = rotate(x, z, canvas.w(), canvas.d(), lot.rot());
		return new int[] {lot.x() + r[0], lot.z() + r[1]};
	}

	private static List<int[]> worldPoints(Lot lot, Canvas canvas, List<int[]> local) {
		List<int[]> out = new ArrayList<>();
		for (int[] p : local) {
			int[] w = toWorld(lot, canvas, p[0], p[2]);
			out.add(new int[] {w[0], lot.groundY() + p[1], w[1]});
		}
		return out;
	}

	/**
	 * Melhor lote para a planta: aneis de {@code minR} ate {@code maxR}, porta virada para o centro, sem sobrepor
	 * {@code taken} (com folga de 2), longe da praca ({@code clearance}), so colunas livres e desnivel ate
	 * {@link #MAX_SPREAD}. Prefere perto do centro e plano, com um pouco de sorteio para a vila nao ficar em grade.
	 */
	public static Lot findLot(Canvas canvas, List<VillageState.Rect> taken, int maxR, Terrain terrain, Random random, int minR, int clearance) {
		double offset = random.nextDouble();
		Lot best = null;
		double bestScore = Double.MAX_VALUE;
		int span = Math.max(canvas.w(), canvas.d());
		for (int r = minR + span / 2; r <= maxR; r += 2) {
			int n = Math.max(8, (int) (2 * Math.PI * r / 4));
			for (int k = 0; k < n; k++) {
				double angle = 2 * Math.PI * (k + offset) / n;
				double px = r * Math.cos(angle);
				double pz = r * Math.sin(angle);
				Facing front = Facing.toward(-px, -pz);
				int rot = rotationFor(front);
				int w = rot % 2 == 0 ? canvas.w() : canvas.d();
				int d = rot % 2 == 0 ? canvas.d() : canvas.w();
				int x0 = (int) Math.round(px - w / 2.0);
				int z0 = (int) Math.round(pz - d / 2.0);
				Lot lot = check(canvas, x0, z0, w, d, rot, taken, maxR, terrain, clearance);
				if (lot == null) {
					continue;
				}
				int[] heights = heights(terrain, x0, z0, w, d);
				int spread = heights[heights.length - 1] - heights[0];
				double score = r + spread * 3 + random.nextDouble() * 5;
				if (score < bestScore) {
					bestScore = score;
					best = lot;
				}
			}
			// ja achou um bom lote num anel interno: nao precisa ir longe
			if (best != null && bestScore < r - 4) {
				break;
			}
		}
		return best;
	}

	private static int[] heights(Terrain terrain, int x0, int z0, int w, int d) {
		int[] out = new int[w * d];
		int i = 0;
		for (int x = x0; x < x0 + w; x++) {
			for (int z = z0; z < z0 + d; z++) {
				out[i++] = terrain.ground(x, z);
			}
		}
		Arrays.sort(out);
		return out;
	}

	private static Lot check(Canvas canvas, int x0, int z0, int w, int d, int rot, List<VillageState.Rect> taken, int maxR, Terrain terrain,
			int clearance) {
		int x1 = x0 + w - 1;
		int z1 = z0 + d - 1;
		for (int[] corner : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			if (corner[0] * corner[0] + corner[1] * corner[1] > maxR * maxR) {
				return null;
			}
		}
		// ponto do lote mais perto do centro
		int nx = Math.max(x0, Math.min(0, x1));
		int nz = Math.max(z0, Math.min(0, z1));
		if (nx * nx + nz * nz <= clearance * clearance) {
			return null;
		}
		VillageState.Rect rect = new VillageState.Rect(x0, z0, x1, z1);
		for (VillageState.Rect other : taken) {
			if (rect.overlaps(other, 2)) {
				return null;
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!terrain.free(x, z)) {
					return null;
				}
			}
		}
		int[] heights = heights(terrain, x0, z0, w, d);
		if (heights[heights.length - 1] - heights[0] > MAX_SPREAD) {
			return null;
		}
		int groundY = heights[heights.length / 2];
		Lot lot = new Lot(x0, z0, w, d, rot, groundY);
		// a saida da porta precisa dar em chao livre, no maximo 2 abaixo/acima
		int[] out = toWorld(lot, canvas, canvas.doorX(), canvas.d());
		if (!terrain.free(out[0], out[1]) || Math.abs(terrain.ground(out[0], out[1]) - groundY) > 2) {
			return null;
		}
		return lot;
	}

	/** Planta girada e posta no lote, com o espaco acima limpo (so natural) ate 1 acima do telhado. */
	public static List<Placement> place(Canvas canvas, Lot lot) {
		List<Placement> out = new ArrayList<>();
		Set<Long> used = new HashSet<>();
		for (var entry : canvas.cells().entrySet()) {
			long k = entry.getKey();
			int lx = Canvas.keyX(k);
			int ly = Canvas.keyY(k);
			int lz = Canvas.keyZ(k);
			int[] w = toWorld(lot, canvas, lx, lz);
			Canvas.Cell cell = entry.getValue();
			Facing dir = cell.dir() == null ? null : cell.dir().rotate(lot.rot());
			boolean fill = ly <= 0 && (cell.part().fillsStone() || cell.part().fillsDirt());
			out.add(new Placement(w[0], lot.groundY() + ly, w[1], cell.part(), dir, fill));
			used.add(Placement.key(w[0], lot.groundY() + ly, w[1]));
		}
		int top = canvas.height() + 1;
		for (int x = lot.x(); x < lot.x() + lot.w(); x++) {
			for (int z = lot.z(); z < lot.z() + lot.d(); z++) {
				for (int y = 1; y <= top; y++) {
					if (!used.contains(Placement.key(x, lot.groundY() + y, z))) {
						out.add(new Placement(x, lot.groundY() + y, z, VPart.AIR, null, false));
					}
				}
			}
		}
		return out;
	}

	/** Ordem de construcao: limpa de cima para baixo, solidos de baixo para cima, detalhes por ultimo. */
	public static List<Placement> sort(List<Placement> list) {
		List<Placement> out = new ArrayList<>(list);
		out.sort(Comparator.comparingInt(Placement::phase)
				.thenComparingInt(p -> p.part() == VPart.AIR ? -p.y() : p.y())
				.thenComparingInt(p -> p.x() * 31 + p.z()));
		return out;
	}

	/**
	 * Caminho de terra da saida (x, z) ate a borda da praca: anda pelo eixo mais longe do centro (ruas em L), so em
	 * colunas livres fora dos lotes.
	 */
	public static List<Placement> path(int x, int z, Terrain terrain, List<VillageState.Rect> lots) {
		List<Placement> out = new ArrayList<>();
		int cx = x;
		int cz = z;
		for (int step = 0; step < 120 && cx * cx + cz * cz > PLAZA_RADIUS * PLAZA_RADIUS; step++) {
			if (!insideAny(lots, cx, cz) && terrain.free(cx, cz)) {
				out.add(new Placement(cx, terrain.ground(cx, cz), cz, VPart.PATH, null, false));
				out.add(new Placement(cx, terrain.ground(cx, cz) + 1, cz, VPart.AIR, null, false));
			}
			if (Math.abs(cx) >= Math.abs(cz)) {
				cx -= Integer.signum(cx);
			} else {
				cz -= Integer.signum(cz);
			}
		}
		return out;
	}

	private static boolean insideAny(List<VillageState.Rect> lots, int x, int z) {
		for (VillageState.Rect r : lots) {
			if (r.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- praca, postes, muralha

	/** Praca: poco perto do quadro, calcamento em volta, bancos virados para o centro, postes e canteiros. */
	static Planned plaza(VillageState state, Terrain terrain, Random random) {
		Canvas well = Buildings.plan(VillageProject.PLAZA, state.style(), random.nextLong());
		Lot lot = findLot(well, state.lots(), PLAZA_RADIUS + 1, terrain, random, 1, 1);
		List<Placement> out = new ArrayList<>();
		List<VillageState.Rect> rects = new ArrayList<>(state.lots());
		if (lot != null) {
			out.addAll(place(well, lot));
			rects.add(lot.rect());
		}
		int r = PLAZA_RADIUS;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				int d2 = x * x + z * z;
				if (d2 > r * r - 4 || (x == 0 && z == 0) || insideAny(rects, x, z) || !terrain.free(x, z)) {
					continue;
				}
				int g = terrain.ground(x, z);
				int pattern = Math.floorMod(x * 5 + z * 3 + random.nextInt(3), 9);
				VPart part = d2 <= 2 ? VPart.BRICK : pattern == 0 ? VPart.PAVING_ALT : pattern < 3 ? VPart.STONE : VPart.PAVING;
				out.add(new Placement(x, g, z, part, null, false));
				out.add(new Placement(x, g + 1, z, VPart.AIR, null, false));
				out.add(new Placement(x, g + 2, z, VPart.AIR, null, false));
			}
		}
		// bancos, postes e canteiros na borda
		int[][] benches = {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
		for (int[] b : benches) {
			for (int[] p : new int[][] {{b[0], b[1] + Integer.signum(b[1])}, {b[0] + Integer.signum(b[0]), b[1]}}) {
				if (terrain.free(p[0], p[1]) && !insideAny(rects, p[0], p[1])) {
					out.add(new Placement(p[0], terrain.ground(p[0], p[1]) + 1, p[1], VPart.WOOD_STAIRS,
							Facing.toward(p[0], p[1]), false));
				}
			}
		}
		for (int[] p : new int[][] {{5, 0}, {-5, 0}, {0, 5}, {0, -5}}) {
			// os postes ficam do lado das entradas, nao no meio delas
			int px = p[0] == 0 ? 2 : p[0];
			int pz = p[1] == 0 ? 2 : p[1];
			lampPost(out, terrain, rects, px, pz);
		}
		for (int[] p : new int[][] {{4, 2}, {-4, -2}, {2, -4}, {-2, 4}}) {
			if (terrain.free(p[0], p[1]) && !insideAny(rects, p[0], p[1])) {
				int g = terrain.ground(p[0], p[1]);
				out.add(new Placement(p[0], g, p[1], VPart.SOIL, null, false));
				out.add(new Placement(p[0], g + 1, p[1], VPart.FLOWER, null, false));
			}
		}
		return new Planned(VillageProject.PLAZA, lot, sort(dedupe(out)), List.of(), List.of());
	}

	/** Poste: base de muro de pedra, dois de cerca e lanterna em cima. */
	static boolean lampPost(List<Placement> out, Terrain terrain, List<VillageState.Rect> lots, int x, int z) {
		if (!terrain.free(x, z) || insideAny(lots, x, z)) {
			return false;
		}
		int g = terrain.ground(x, z);
		out.add(new Placement(x, g + 1, z, VPart.STONE_WALL, null, false));
		out.add(new Placement(x, g + 2, z, VPart.FENCE, null, false));
		out.add(new Placement(x, g + 3, z, VPart.FENCE, null, false));
		out.add(new Placement(x, g + 4, z, VPart.LANTERN, null, false));
		return true;
	}

	/**
	 * Postes nos lugares do plano que ainda nao tem poste (o nivel novo acrescenta lugares; os de antes ficam): base de
	 * muro, duas cercas e lanterna, so em coluna livre. Marca os lugares usados como construidos.
	 */
	static Planned lamps(VillageState state, Terrain terrain) {
		VillageLayout layout = layout(state, terrain);
		List<Placement> out = new ArrayList<>();
		List<VillageState.Rect> rects = new ArrayList<>(state.lots());
		for (VillageLayout.Lot lot : layout.lots()) {
			rects.add(lot.rect());
		}
		for (VillageLayout.Lamp lamp : layout.lamps()) {
			MasterPlan.Block block = layout.master() == null ? null : layout.master().blockAt(lamp.x(), lamp.z());
			if (!lamp.built() && (layout.master() == null || (block != null && block.prepared())) && lampPost(out, terrain, rects, lamp.x(), lamp.z())) {
				lamp.setBuilt(true);
			}
		}
		if (out.isEmpty()) {
			return null;
		}
		return new Planned(VillageProject.LAMPS, null, sort(out), List.of(), List.of());
	}

	/** Dentro do octogono da muralha (inclui a borda). */
	public static boolean insideWall(int x, int z) {
		return Math.abs(x) <= WALL_HALF && Math.abs(z) <= WALL_HALF && Math.abs(x) + Math.abs(z) <= WALL_DIAGONAL;
	}

	/** Dentro do octogono crescido (ou encolhido, se negativo) de {@code grow} blocos. */
	public static boolean insideWall(int x, int z, int grow) {
		return Math.abs(x) <= WALL_HALF + grow && Math.abs(z) <= WALL_HALF + grow && Math.abs(x) + Math.abs(z) <= WALL_DIAGONAL + grow;
	}

	/** Celula da borda do octogono (ligada em 4 direcoes, sem frestas na diagonal). */
	public static boolean wallCell(int x, int z) {
		if (!insideWall(x, z)) {
			return false;
		}
		return !insideWall(x + 1, z) || !insideWall(x - 1, z) || !insideWall(x, z + 1) || !insideWall(x, z - 1);
	}

	/**
	 * Muralha octogonal seguindo o terreno: 4 de altura com ameias, torres nos 8 cantos e portoes em arco no meio de
	 * cada lado, com rua de terra ate a praca. Colunas que nao estao livres (casas, caminhos, agua) ficam abertas.
	 */
	static Planned wall(VillageState state, Terrain terrain) {
		List<Placement> out = new ArrayList<>();
		int h = WALL_HALF;
		Set<Long> towerCells = new HashSet<>();
		int[][] vertices = {{h, WALL_DIAGONAL - h}, {WALL_DIAGONAL - h, h}, {-h, WALL_DIAGONAL - h}, {-(WALL_DIAGONAL - h), h},
				{h, -(WALL_DIAGONAL - h)}, {WALL_DIAGONAL - h, -h}, {-h, -(WALL_DIAGONAL - h)}, {-(WALL_DIAGONAL - h), -h}};
		for (int[] v : vertices) {
			int vx = v[0] - Integer.signum(v[0]);
			int vz = v[1] - Integer.signum(v[1]);
			tower(out, terrain, vx, vz, towerCells);
		}
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (!wallCell(x, z) || towerCells.contains(Placement.key(x, 0, z))) {
					continue;
				}
				boolean gateZone = (Math.abs(x) <= 2 && Math.abs(z) >= h - 1) || (Math.abs(z) <= 2 && Math.abs(x) >= h - 1);
				if (gateZone) {
					continue;
				}
				if (!terrain.free(x, z)) {
					continue;
				}
				int g = terrain.ground(x, z);
				out.add(new Placement(x, g, z, VPart.FOUNDATION, null, true));
				for (int y = 1; y <= 4; y++) {
					VPart part = y == 1 ? VPart.BRICK : Math.floorMod(x * 7 + z * 11 + y * 3, 6) == 0 ? VPart.STONE_ALT : VPart.STONE;
					out.add(new Placement(x, g + y, z, part, null, false));
				}
				out.add(new Placement(x, g + 5, z, Math.floorMod(x + z, 2) == 0 ? VPart.BRICK : VPart.STONE_SLAB, null, false));
			}
		}
		List<int[]> golems = new ArrayList<>();
		for (Facing side : Facing.values()) {
			gate(out, terrain, side, golems, state.layout() == null || state.layout().master() == null);
		}
		return new Planned(VillageProject.WALL, null, sort(dedupe(out)), List.of(), golems.subList(0, Math.min(1, golems.size())));
	}

	/** Torre 5x5 macica de 7 de altura com ameias e lanterna no topo. */
	private static void tower(List<Placement> out, Terrain terrain, int cx, int cz, Set<Long> cells) {
		int free = 0;
		int base = Integer.MIN_VALUE;
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				if (terrain.free(x, z)) {
					free++;
					base = Math.max(base, terrain.ground(x, z));
				}
			}
		}
		if (free < 20) {
			return;
		}
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				if (!terrain.free(x, z)) {
					continue;
				}
				cells.add(Placement.key(x, 0, z));
				boolean edge = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
				boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
				out.add(new Placement(x, base, z, VPart.FOUNDATION, null, true));
				for (int y = 1; y <= 7; y++) {
					VPart part = corner ? VPart.BRICK : y == 7 && edge ? VPart.BRICK
							: Math.floorMod(x * 3 + y * 5 + z, 7) == 0 ? VPart.STONE_ALT : VPart.STONE;
					out.add(new Placement(x, base + y, z, part, null, false));
				}
				if (edge && (corner || Math.floorMod(x + z, 2) == 0)) {
					out.add(new Placement(x, base + 8, z, VPart.BRICK, null, false));
				}
			}
		}
		if (terrain.free(cx, cz)) {
			out.add(new Placement(cx, base + 8, cz, VPart.LANTERN, null, false));
		}
	}

	/** Portao em arco no meio do lado {@code side}, com pilares, lanterna pendurada e rua ate a praca. */
	private static void gate(List<Placement> out, Terrain terrain, Facing side, List<int[]> golems, boolean road) {
		int h = WALL_HALF;
		int base = Integer.MIN_VALUE;
		for (int t = -2; t <= 2; t++) {
			int x = side.alongX() ? side.dx * h : t;
			int z = side.alongX() ? t : side.dz * h;
			base = Math.max(base, terrain.ground(x, z));
		}
		boolean whole = true;
		for (int t = -2; t <= 2; t++) {
			whole &= terrain.free(side.alongX() ? side.dx * h : t, side.alongX() ? t : side.dz * h);
		}
		for (int t = -2; t <= 2; t++) {
			int x = side.alongX() ? side.dx * h : t;
			int z = side.alongX() ? t : side.dz * h;
			boolean pillar = Math.abs(t) == 2;
			if (!terrain.free(x, z)) {
				// estrada ou construcao no caminho: o portao fica aberto ali
				continue;
			}
			if (!whole) {
				// sem os dois pilares nao ha arco: so a passagem
				out.add(new Placement(x, terrain.ground(x, z), z, VPart.PATH, null, false));
				continue;
			}
			if (pillar) {
				out.add(new Placement(x, terrain.ground(x, z), z, VPart.FOUNDATION, null, true));
				for (int y = terrain.ground(x, z) + 1; y <= base + 7; y++) {
					out.add(new Placement(x, y, z, VPart.BRICK, null, false));
				}
				out.add(new Placement(x, base + 8, z, VPart.BRICK, null, false));
				continue;
			}
			// abertura: limpa ate 3 de altura, arco por cima
			out.add(new Placement(x, terrain.ground(x, z), z, VPart.PATH, null, false));
			for (int y = terrain.ground(x, z) + 1; y <= base + 3; y++) {
				out.add(new Placement(x, y, z, VPart.AIR, null, false));
			}
			Facing toCenter = side.alongX() ? (t < 0 ? Facing.SOUTH : Facing.NORTH) : (t < 0 ? Facing.EAST : Facing.WEST);
			if (t == 0) {
				out.add(new Placement(x, base + 4, z, VPart.LANTERN_HANGING, null, false));
			} else {
				out.add(new Placement(x, base + 4, z, VPart.STONE_STAIRS_TOP, toCenter.opposite(), false));
			}
			for (int y = base + 5; y <= base + 7; y++) {
				out.add(new Placement(x, y, z, VPart.BRICK, null, false));
			}
			if (t == 0) {
				out.add(new Placement(x, base + 8, z, VPart.BRICK, null, false));
			}
		}
		// rua do portao ate a praca
		int gx = side.dx * (h - 2);
		int gz = side.dz * (h - 2);
		if (road) {
			// plano antigo: rua de terra do portao ate a praca (no plano diretor a avenida ja esta la)
			out.addAll(path(gx, gz, terrain, List.of()));
		}
		golems.add(new int[] {gx, terrain.ground(gx, gz) + 1, gz});
	}

	/** Remove posicoes repetidas (fica a ultima). */
	static List<Placement> dedupe(List<Placement> list) {
		java.util.LinkedHashMap<Long, Placement> map = new java.util.LinkedHashMap<>();
		for (Placement p : list) {
			map.remove(p.key());
			map.put(p.key(), p);
		}
		return new ArrayList<>(map.values());
	}
}
