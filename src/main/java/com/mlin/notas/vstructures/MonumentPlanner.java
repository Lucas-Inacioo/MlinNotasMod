package com.mlin.notas.vstructures;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Monumento oceanico refeito: templo de prismarinho de 55x46 sobre pilares, com colunata na frente, terreo em
 * labirinto de 6x5 salas (salas de esponja molhada, de coluna com lanterna do mar, jardim de coral, prateleiras com
 * picles do mar, desabadas e 2 tesourarias com bau), andar de cima com salas ligadas por alcapoes abertos, as duas
 * alas (torres com um guardiao ancestral cada) e o nucleo central de prismarinho escuro com o cubo de 8 blocos de ouro
 * cercado de colunas de lanterna do mar; em cima do nucleo a cobertura com o terceiro guardiao ancestral e a agulha.
 * Telhado com parapeito, torreoes nos cantos, faixas de prismarinho escuro e lanternas do mar nas fachadas. Em volta,
 * recife de coral, algas e capim marinho (so capim e cascalho no mar gelado). Tudo vazio vira agua na colocacao (o
 * monumento fica com o topo 2 blocos abaixo do mar). Os guardioes comuns continuam pelos spawn_overrides vanilla.
 * Frente em -z. Logica pura.
 *
 * <p>Referencias (so forma, camadas e paleta; nada copiado): a replica do monumento do Planet Minecraft
 * ("Ocean Monument", 2014), YUNG's Better Ocean Monuments (alas, salas com tema, labirinto maior) e DnT Ocean Monument
 * Overhaul (monumento como templo com colunata e recife em volta).
 */
public final class MonumentPlanner extends PlanBuilder {
	public static final String MARKER_CORE = "core";
	public static final String MARKER_PENTHOUSE = "penthouse";
	public static final String TREASURY = "mlinnotas:chests/monument_treasury";
	static final int COLS = 6;
	static final int ROWS = 5;
	static final int X0 = -27;
	static final int Z0 = -21;
	static final int X1 = 27;
	static final int Z1 = 24;

	private static final String BRICKS = "minecraft:prismarine_bricks";
	private static final String ROUGH = "minecraft:prismarine";
	private static final String DARK = "minecraft:dark_prismarine";
	private static final String LANTERN = "minecraft:sea_lantern";

	private final boolean frozen;

	private MonumentPlanner(Random random, int variant) {
		super(new VPlan(false), random);
		this.frozen = variant == 1;
	}

	public static VPlan plan(long seed, int variant) {
		MonumentPlanner b = new MonumentPlanner(new Random(seed * 7919L + 3), variant);
		b.build();
		b.p.setWaterLevel(40);
		return b.p;
	}

	static int cx0(int c) {
		return X0 + 9 * c + 1;
	}

	static int cz0(int r) {
		return Z0 + 9 * r + 1;
	}

	private boolean wing(int c, int r) {
		return (c == 0 || c == COLS - 1) && r >= 1 && r <= 3;
	}

	private boolean core(int c, int r) {
		return (c == 2 || c == 3) && (r == 2 || r == 3);
	}

	private String wallBlock() {
		double d = r.nextDouble();
		return d < 0.7 ? BRICKS : d < 0.9 ? ROUGH : DARK;
	}

	private String facade(int y) {
		if (y == 0 || y == 5 || y == 10 || y == 16) {
			return DARK;
		}
		return chance(0.8) ? ROUGH : BRICKS;
	}

	private void build() {
		body();
		groundMaze();
		upperRooms();
		wings();
		coreChamber();
		penthouse();
		roof();
		colonnade();
		legs();
		reef();
		clearing();
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 1, Z0 - 10);
	}

	/** Bloco macico do corpo (terreo + andar de cima), depois escavado. */
	private void body() {
		for (int x = X0; x <= X1; x++) {
			for (int z = Z0; z <= Z1; z++) {
				boolean edge = x == X0 || x == X1 || z == Z0 || z == Z1;
				for (int y = 0; y <= 10; y++) {
					set(x, y, z, edge ? facade(y) : (y == 0 ? (((x + z) & 1) == 0 ? DARK : BRICKS) : wallBlock()));
				}
			}
		}
		// faixas de lanterna do mar nas fachadas, nas linhas das paredes internas
		for (int c = 0; c <= COLS; c++) {
			int x = X0 + 9 * c;
			for (int y : new int[] {3, 8}) {
				set(x, y, Z0, LANTERN);
				set(x, y, Z1, LANTERN);
			}
		}
		for (int r = 0; r <= ROWS; r++) {
			int z = Z0 + 9 * r;
			for (int y : new int[] {3, 8}) {
				set(X0, y, z, LANTERN);
				set(X1, y, z, LANTERN);
			}
		}
	}

	private void carve(int x1, int y1, int z1, int x2, int y2, int z2) {
		air(x1, y1, z1, x2, y2, z2);
	}

	// --- terreo ---

	private void groundMaze() {
		List<int[]> edges = new ArrayList<>();
		boolean[][] seen = new boolean[COLS][ROWS];
		List<int[]> stack = new ArrayList<>();
		int sc = 2 + r.nextInt(2);
		stack.add(new int[] {sc, 0});
		seen[sc][0] = true;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!stack.isEmpty()) {
			int[] cur = stack.get(stack.size() - 1);
			List<int[]> next = new ArrayList<>();
			for (int[] d : dirs) {
				int nc = cur[0] + d[0];
				int nr = cur[1] + d[1];
				if (nc >= 0 && nc < COLS && nr >= 0 && nr < ROWS && !seen[nc][nr]) {
					next.add(new int[] {nc, nr});
				}
			}
			if (next.isEmpty()) {
				stack.remove(stack.size() - 1);
				continue;
			}
			int[] n = next.get(r.nextInt(next.size()));
			seen[n[0]][n[1]] = true;
			edges.add(new int[] {cur[0], cur[1], n[0], n[1]});
			stack.add(n);
		}
		// lacos extras para nao ser um labirinto de uma via so
		for (int c = 0; c < COLS; c++) {
			for (int rr = 0; rr < ROWS; rr++) {
				if (c + 1 < COLS && chance(0.25)) {
					edges.add(new int[] {c, rr, c + 1, rr});
				}
				if (rr + 1 < ROWS && chance(0.25)) {
					edges.add(new int[] {c, rr, c, rr + 1});
				}
			}
		}
		for (int c = 0; c < COLS; c++) {
			for (int rr = 0; rr < ROWS; rr++) {
				carve(cx0(c), 1, cz0(rr), cx0(c) + 7, 4, cz0(rr) + 7);
				set(cx0(c) + 3, 5, cz0(rr) + 3, LANTERN);
				set(cx0(c) + 4, 5, cz0(rr) + 4, LANTERN);
			}
		}
		for (int[] e : edges) {
			door(e[0], e[1], e[2], e[3], 1);
		}
		// salao de entrada: as duas salas da frente do meio viram uma so, porta na fachada
		carve(-1, 1, cz0(0), 1, 4, cz0(0) + 7);
		carve(-1, 1, Z0, 1, 3, Z0);
		for (int y = 1; y <= 3; y++) {
			set(-2, y, Z0 + 1, DARK);
			set(2, y, Z0 + 1, DARK);
		}
		// salas com tema
		List<int[]> cells = new ArrayList<>();
		for (int c = 0; c < COLS; c++) {
			for (int rr = 1; rr < ROWS; rr++) {
				cells.add(new int[] {c, rr});
			}
		}
		java.util.Collections.shuffle(cells, r);
		for (int i = 0; i < cells.size(); i++) {
			int[] cell = cells.get(i);
			int x = cx0(cell[0]);
			int z = cz0(cell[1]);
			switch (i < 2 ? 0 : 1 + (i % 6)) {
				case 0 -> treasury(x, z);
				case 1 -> spongeRoom(x, z, 1, 4);
				case 2 -> pillarRoom(x, z, 1, 4);
				case 3 -> coralRoom(x, z);
				case 4 -> shelfRoom(x, z, 1);
				case 5 -> collapsed(x, z, 1, 4);
				default -> {
				}
			}
		}
	}

	/** Porta de 2 de largura entre duas salas vizinhas no andar que comeca em {@code y}. */
	private void door(int c1, int r1, int c2, int r2, int y) {
		if (c1 != c2) {
			int x = X0 + 9 * Math.max(c1, c2);
			int z = cz0(r1) + 3;
			carve(x, y, z, x, y + 2, z + 1);
		} else {
			int z = Z0 + 9 * Math.max(r1, r2);
			int x = cx0(c1) + 3;
			carve(x, y, z, x + 1, y + 2, z);
		}
	}

	private void treasury(int x, int z) {
		// nicho com bau, ouro e lanternas
		for (int i = 0; i < 8; i++) {
			set(x + i, 1, z + 7, DARK);
		}
		p.loot(x + 3, 2, z + 7, s("chest", "facing=north"), TREASURY);
		if (chance(0.5)) {
			p.loot(x + 4, 2, z + 7, s("chest", "facing=north"), TREASURY);
		}
		set(x + 1, 2, z + 7, LANTERN);
		set(x + 6, 2, z + 7, LANTERN);
		set(x, 1, z, DARK);
		set(x + 7, 1, z, DARK);
		set(x + 3, 1, z + 3, s("sea_pickle", "pickles=" + between(1, 4)));
	}

	private void spongeRoom(int x, int z, int y0, int y1) {
		for (int i = 0; i <= 7; i++) {
			for (int k = 0; k <= 7; k++) {
				if (chance(0.45)) {
					set(x + i, y1, z + k, "minecraft:wet_sponge");
				}
			}
		}
		for (int i = 0; i < 4; i++) {
			set(x + r.nextInt(8), y0 + 1 + r.nextInt(2), z + (chance(0.5) ? -1 : 8), "minecraft:wet_sponge");
		}
	}

	private void pillarRoom(int x, int z, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			String b = y == y0 || y == y1 ? DARK : (y == (y0 + y1) / 2 ? LANTERN : BRICKS);
			fill(x + 3, y, z + 3, x + 4, y, z + 4, b);
		}
	}

	private void coralRoom(int x, int z) {
		String[] blocks = {"tube", "brain", "bubble", "fire", "horn"};
		for (int i = 0; i <= 7; i++) {
			for (int k = 0; k <= 7; k++) {
				if (frozen) {
					if (chance(0.3)) {
						set(x + i, 1, z + k, chance(0.5) ? "minecraft:gravel" : "minecraft:seagrass");
					}
				} else if (chance(0.3)) {
					String coral = pick(blocks);
					set(x + i, 1, z + k, s(coral + "_coral_block"));
					if (chance(0.6)) {
						set(x + i, 2, z + k, chance(0.5) ? s(coral + "_coral") : s(coral + "_coral_fan"));
					}
				} else if (chance(0.25)) {
					set(x + i, 1, z + k, "minecraft:seagrass");
				}
			}
		}
	}

	private void shelfRoom(int x, int z, int y0) {
		for (int i = 0; i <= 7; i += 7) {
			for (int k = 1; k <= 6; k++) {
				set(x + i, y0, z + k, DARK);
				set(x + i, y0 + 1, z + k, s("dark_prismarine_slab", "type=bottom"));
				if (chance(0.3)) {
					set(x + i, y0 + 1, z + k, DARK);
					set(x + i, y0 + 2, z + k, s("sea_pickle", "pickles=" + between(1, 3)));
				}
			}
		}
	}

	private void collapsed(int x, int z, int y0, int y1) {
		for (int i = 0; i <= 7; i++) {
			for (int k = 0; k <= 7; k++) {
				if (chance(0.15)) {
					set(x + i, y1 + 1, z + k, VPlan.AIR);
					if (chance(0.7)) {
						set(x + i, y0, z + k, chance(0.5) ? ROUGH : s("prismarine_slab"));
					}
				}
			}
		}
	}

	// --- andar de cima ---

	private void upperRooms() {
		for (int c = 0; c < COLS; c++) {
			for (int rr = 0; rr < ROWS; rr++) {
				if (wing(c, rr) || core(c, rr)) {
					continue;
				}
				int x = cx0(c);
				int z = cz0(rr);
				carve(x, 6, z, x + 7, 9, z + 7);
				// alcapao (abertura) no piso ligando ao terreo
				carve(x + 3, 5, z + 3, x + 4, 5, z + 4);
				fill(x + 2, 10, z + 2, x + 2, 10, z + 2, LANTERN);
				set(x + 5, 10, z + 5, LANTERN);
				int theme = r.nextInt(4);
				if (theme == 0) {
					spongeRoom(x, z, 6, 9);
				} else if (theme == 1) {
					shelfRoom(x, z, 6);
				} else if (theme == 2) {
					collapsed(x, z, 6, 9);
				}
				for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
					int nc = c + d[0];
					int nr = rr + d[1];
					if (nc < COLS && nr < ROWS && !wing(nc, nr) && !core(nc, nr)) {
						door(c, rr, nc, nr, 6);
					}
				}
			}
		}
	}

	private void wings() {
		for (int side : new int[] {0, COLS - 1}) {
			int xa = X0 + 9 * side;
			int xb = xa + 9;
			int za = Z0 + 9;
			int zb = Z0 + 36;
			for (int x = xa; x <= xb; x++) {
				for (int z = za; z <= zb; z++) {
					boolean edge = x == xa || x == xb || z == za || z == zb;
					for (int y = 10; y <= 16; y++) {
						set(x, y, z, edge ? facade(y) : wallBlock());
					}
				}
			}
			carve(xa + 1, 6, za + 1, xb - 1, 15, zb - 1);
			// colunas e varandas internas
			for (int z = za + 3; z < zb; z += 8) {
				for (int y = 6; y <= 15; y++) {
					set(xa + 1, y, z, y % 3 == 0 ? LANTERN : BRICKS);
					set(xb - 1, y, z, y % 3 == 0 ? LANTERN : BRICKS);
				}
			}
			fill(xa + 1, 11, za + 1, xb - 1, 11, za + 2, s("prismarine_brick_slab", "type=top"));
			fill(xa + 1, 11, zb - 2, xb - 1, 11, zb - 1, s("prismarine_brick_slab", "type=top"));
			// porta para o andar de cima vizinho em cada uma das 3 salas
			int inner = side == 0 ? xb : xa;
			for (int rr = 1; rr <= 3; rr++) {
				carve(inner, 6, cz0(rr) + 3, inner, 8, cz0(rr) + 4);
			}
			// janelas altas
			int outer = side == 0 ? xa : xb;
			for (int z = za + 4; z < zb; z += 6) {
				carve(outer, 12, z, outer, 14, z);
			}
			// telhado em degraus
			for (int x = xa + 1; x <= xb - 1; x++) {
				for (int z = za + 1; z <= zb - 1; z++) {
					set(x, 17, z, x == xa + 1 || x == xb - 1 || z == za + 1 || z == zb - 1 ? s("prismarine_brick_slab") : BRICKS);
				}
			}
			for (int x = xa + 3; x <= xb - 3; x++) {
				for (int z = za + 3; z <= zb - 3; z++) {
					set(x, 18, z, DARK);
				}
			}
			int midZ = (za + zb) / 2;
			set((xa + xb) / 2, 19, midZ, LANTERN);
			set((xa + xb) / 2, 20, midZ, s("prismarine_wall"));
			p.entity("minecraft:elder_guardian", (xa + xb) / 2.0 + 0.5, 9, midZ + 0.5, "elder");
		}
	}

	private void coreChamber() {
		int xa = -9;
		int xb = 9;
		int za = Z0 + 18;
		int zb = Z0 + 36;
		for (int x = xa; x <= xb; x++) {
			for (int z = za; z <= zb; z++) {
				boolean edge = x == xa || x == xb || z == za || z == zb;
				for (int y = 5; y <= 14; y++) {
					if (edge || y == 5 || y == 14 || y >= 10) {
						set(x, y, z, edge && y > 10 ? facade(y) : DARK);
					}
				}
			}
		}
		carve(xa + 1, 6, za + 1, xb - 1, 13, zb - 1);
		// portas: dos lados (salas de cima vizinhas) e da frente
		for (int rr = 2; rr <= 3; rr++) {
			carve(xa, 6, cz0(rr) + 3, xa, 8, cz0(rr) + 4);
			carve(xb, 6, cz0(rr) + 3, xb, 8, cz0(rr) + 4);
		}
		carve(-4, 6, za, -3, 8, za);
		carve(3, 6, za, 4, 8, za);
		// pedestal e o cubo de ouro (8 blocos)
		int cz = (za + zb) / 2;
		fill(-1, 6, cz - 1, 2, 6, cz + 2, DARK);
		fill(0, 7, cz, 1, 8, cz + 1, "minecraft:gold_block");
		set(-1, 7, cz - 1, LANTERN);
		set(2, 7, cz - 1, LANTERN);
		set(-1, 7, cz + 2, LANTERN);
		set(2, 7, cz + 2, LANTERN);
		p.mark(MARKER_CORE, 0, 7, cz);
		// colunas de lanterna do mar nos cantos
		for (int[] c : new int[][] {{xa + 2, za + 2}, {xb - 2, za + 2}, {xa + 2, zb - 2}, {xb - 2, zb - 2}}) {
			for (int y = 6; y <= 13; y++) {
				set(c[0], y, c[1], y % 2 == 0 ? DARK : LANTERN);
			}
		}
		// friso de prismarinho no teto
		for (int x = xa + 1; x <= xb - 1; x++) {
			set(x, 13, za + 1, s("dark_prismarine_stairs", "facing=north", "half=top"));
			set(x, 13, zb - 1, s("dark_prismarine_stairs", "facing=south", "half=top"));
		}
	}

	private void penthouse() {
		int xa = -6;
		int xb = 6;
		int za = Z0 + 21;
		int zb = Z0 + 33;
		for (int x = xa; x <= xb; x++) {
			for (int z = za; z <= zb; z++) {
				boolean edge = x == xa || x == xb || z == za || z == zb;
				for (int y = 14; y <= 20; y++) {
					if (edge || y == 14 || y == 20) {
						set(x, y, z, edge ? facade(y) : (y == 20 ? BRICKS : DARK));
					}
				}
			}
		}
		carve(xa + 1, 15, za + 1, xb - 1, 19, zb - 1);
		// abertura no teto do nucleo (sobe nadando)
		carve(-4, 14, za + 2, -3, 14, za + 3);
		carve(3, 14, zb - 3, 4, 14, zb - 2);
		// janelas e colunas
		for (int z = za + 3; z < zb; z += 3) {
			carve(xa, 17, z, xa, 18, z);
			carve(xb, 17, z, xb, 18, z);
		}
		for (int x = xa + 2; x <= xb - 2; x += 4) {
			carve(x, 17, za, x, 18, za);
		}
		set(0, 19, (za + zb) / 2, LANTERN);
		p.entity("minecraft:elder_guardian", 0.5, 16, (za + zb) / 2.0 + 0.5, "elder");
		p.mark(MARKER_PENTHOUSE, 0, 16, (za + zb) / 2);
		// cobertura em degraus e agulha
		for (int x = xa + 1; x <= xb - 1; x++) {
			for (int z = za + 1; z <= zb - 1; z++) {
				boolean ring = x == xa + 1 || x == xb - 1 || z == za + 1 || z == zb - 1;
				set(x, 21, z, ring ? s("dark_prismarine_slab") : DARK);
			}
		}
		int mz = (za + zb) / 2;
		fill(-1, 22, mz - 1, 1, 22, mz + 1, BRICKS);
		set(0, 23, mz, LANTERN);
		set(0, 24, mz, s("prismarine_wall"));
		set(0, 25, mz, s("prismarine_wall"));
	}

	/** Parapeito no telhado do corpo, lanternas no telhado e torreoes nos cantos. */
	private void roof() {
		for (int x = X0; x <= X1; x++) {
			for (int z = Z0; z <= Z1; z++) {
				boolean edge = x == X0 || x == X1 || z == Z0 || z == Z1;
				if (!edge || p.get(x, 11, z) != null) {
					continue;
				}
				boolean merlon = (x - X0) % 9 == 0 || (z - Z0) % 9 == 0;
				if (merlon) {
					set(x, 11, z, DARK);
				} else if (chance(0.92)) {
					set(x, 11, z, s("prismarine_brick_slab"));
				}
			}
		}
		for (int c = 0; c < COLS; c++) {
			for (int rr = 0; rr < ROWS; rr++) {
				if (!wing(c, rr) && !core(c, rr) && p.get(cx0(c) + 3, 11, cz0(rr) + 3) == null) {
					set(cx0(c) + 3, 11, cz0(rr) + 3, s("prismarine_brick_slab"));
					set(cx0(c) + 4, 10, cz0(rr) + 4, LANTERN);
				}
			}
		}
		for (int x : new int[] {X0, X1 - 1}) {
			for (int z : new int[] {Z0, Z1 - 1}) {
				for (int y = 11; y <= 14; y++) {
					fill(x, y, z, x + 1, y, z + 1, y == 14 ? DARK : BRICKS);
				}
				set(x, 15, z, LANTERN);
				set(x + 1, 15, z + 1, LANTERN);
				set(x, 16, z, s("prismarine_wall"));
				set(x + 1, 16, z + 1, s("prismarine_wall"));
			}
		}
	}

	/** Colunata da entrada na frente da fachada. */
	private void colonnade() {
		int za = Z0 - 7;
		for (int x = -9; x <= 9; x++) {
			for (int z = za; z < Z0; z++) {
				set(x, 0, z, Math.abs(x) == 9 || z == za ? DARK : BRICKS);
			}
		}
		for (int x : new int[] {-8, -4, 4, 8}) {
			for (int z : new int[] {za + 1, za + 4}) {
				for (int y = 1; y <= 6; y++) {
					set(x, y, z, y == 6 ? DARK : y == 3 ? LANTERN : BRICKS);
				}
			}
		}
		for (int x = -9; x <= 9; x++) {
			for (int z = za; z < Z0; z++) {
				boolean edge = Math.abs(x) == 9 || z == za;
				if (edge || chance(0.9)) {
					set(x, 7, z, edge ? s("prismarine_brick_slab", "type=top") : s("dark_prismarine_slab", "type=top"));
				}
			}
		}
		for (int x = -9; x <= 9; x++) {
			set(x, 8, za, s("prismarine_brick_stairs", "facing=south"));
		}
		set(0, 8, za + 2, LANTERN);
	}

	/** Pilares que descem ate o fundo do mar. */
	private void legs() {
		for (int c = 0; c <= COLS; c++) {
			for (int rr = 0; rr <= ROWS; rr++) {
				int x = X0 + 9 * c;
				int z = Z0 + 9 * rr;
				p.set(x, -1, z, BRICKS, VPlan.FILL_DOWN);
				if ((c == 0 || c == COLS) && (rr == 0 || rr == ROWS)) {
					p.set(x + (c == 0 ? 1 : -1), -1, z, BRICKS, VPlan.FILL_DOWN);
					p.set(x, -1, z + (rr == 0 ? 1 : -1), BRICKS, VPlan.FILL_DOWN);
				}
			}
		}
		for (int x : new int[] {-9, -4, 4, 9}) {
			p.set(x, -1, Z0 - 7, BRICKS, VPlan.FILL_DOWN);
		}
	}

	/** Recife em volta: coral, algas, capim marinho e picles do mar (mar gelado: capim, cascalho e entulho). */
	private void reef() {
		String[] corals = {"tube", "brain", "bubble", "fire", "horn"};
		for (int x = X0 - 7; x <= X1 + 7; x++) {
			for (int z = Z0 - 14; z <= Z1 + 7; z++) {
				boolean inside = x >= X0 - 1 && x <= X1 + 1 && z >= Z0 - 8 && z <= Z1 + 1;
				boolean path = Math.abs(x) <= 3 && z < Z0;
				if (!inside && p.get(x, -1, z) == null) {
					p.set(x, -1, z, frozen ? "minecraft:gravel" : "minecraft:sand", VPlan.FILL_DOWN);
				}
				if (inside || path || p.get(x, 0, z) != null) {
					continue;
				}
				double roll = r.nextDouble();
				if (frozen) {
					if (roll < 0.12) {
						set(x, 0, z, chance(0.5) ? "minecraft:gravel" : ROUGH);
					} else if (roll < 0.3) {
						set(x, 0, z, "minecraft:seagrass");
					}
					continue;
				}
				if (roll < 0.08) {
					// toceira de coral
					String coral = pick(corals);
					set(x, 0, z, s(coral + "_coral_block"));
					if (chance(0.5)) {
						set(x, 1, z, s(coral + "_coral_block"));
						set(x, 2, z, chance(0.5) ? s(coral + "_coral_fan") : s(coral + "_coral"));
					} else {
						set(x, 1, z, chance(0.3) ? s("sea_pickle", "pickles=" + between(1, 4)) : s(coral + "_coral_fan"));
					}
				} else if (roll < 0.11) {
					int h = between(3, 11);
					for (int y = 0; y < h; y++) {
						set(x, y, z, y == h - 1 ? s("kelp", "age=" + between(15, 25)) : "minecraft:kelp_plant");
					}
				} else if (roll < 0.3) {
					set(x, 0, z, "minecraft:seagrass");
				}
			}
		}
	}

	/** Agua em volta (corta morro, iceberg e o fundo que sobe no meio do templo e do recife). */
	private void clearing() {
		for (int x = X0 - 7; x <= X1 + 7; x++) {
			for (int z = Z0 - 14; z <= Z1 + 7; z++) {
				boolean body = x >= X0 - 2 && x <= X1 + 2 && z >= Z0 - 9 && z <= Z1 + 2;
				for (int y = 0; y <= (body ? 25 : 18); y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
		}
	}
}
