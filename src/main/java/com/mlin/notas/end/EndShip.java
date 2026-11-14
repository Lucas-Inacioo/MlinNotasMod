package com.mlin.notas.end;

import com.mlin.notas.vstructures.VPlan;

import static com.mlin.notas.end.CitadelPlanner.BRICKS;
import static com.mlin.notas.end.CitadelPlanner.BRICK_WALL;
import static com.mlin.notas.end.CitadelPlanner.CRYSTAL;
import static com.mlin.notas.end.CitadelPlanner.GLASS;
import static com.mlin.notas.end.CitadelPlanner.LAMP;
import static com.mlin.notas.end.CitadelPlanner.PANE;
import static com.mlin.notas.end.CitadelPlanner.PILLAR;
import static com.mlin.notas.end.CitadelPlanner.PURPUR;
import static com.mlin.notas.end.CitadelPlanner.PURPUR_SLAB;
import static com.mlin.notas.end.CitadelPlanner.PURPUR_STAIRS;
import static com.mlin.notas.end.CitadelPlanner.VOID_BRICKS;
import static com.mlin.notas.end.CitadelPlanner.VOID_LOG;
import static com.mlin.notas.end.CitadelPlanner.VOID_PLANKS;
import static com.mlin.notas.end.CitadelPlanner.dir;
import static com.mlin.notas.end.CitadelPlanner.s;

/**
 * A Nave do End refeita, atracada no cais da cidadela (logica pura, escreve no {@link CitadelPlan}). Coordenadas
 * locais: {@code u} ao longo do casco (popa -14 .. proa 16, gurupés ate 21), {@code v} de bordo a bordo (negativo = lado
 * do cais), {@code y} absoluto. Casco curvo de purpura com faixa de tijolos do vazio e vigias, convés de tabuas do vazio,
 * tres mastros com velas magenta/roxas bojudas e cesto de gávea, castelo de popa com a cabine do tesouro (elitro na
 * parede da popa, dois baus, suporte de pocao), porao com bau e shulkers por uma escotilha com escada de mao, cabeca de
 * dragao na proa e "propulsores" de cristal etereo embaixo do casco.
 */
final class EndShip {
	static final int STERN = -14;
	static final int BOW = 16;
	static final int CASTLE_FRONT = -6;

	private final CitadelPlan p;
	private final int dx;
	private final int dz;
	private final int dist;
	private final int deck;

	private EndShip(CitadelPlan plan, int dx, int dz, int dist, int deckY) {
		this.p = plan;
		this.dx = dx;
		this.dz = dz;
		this.dist = dist;
		this.deck = deckY;
	}

	/** Constroi a nave com o centro a {@code dist} do centro da ilha na direcao (dx, dz); o lado {@code v<0} encosta no cais. */
	static void build(CitadelPlan plan, int dx, int dz, int dist, int deckY) {
		new EndShip(plan, dx, dz, dist, deckY).build();
	}

	// --- quadro local -> plano ---

	private int x(int u, int v) {
		return dx * (dist + v) + Math.abs(dz) * u;
	}

	private int z(int u, int v) {
		return dz * (dist + v) + Math.abs(dx) * u;
	}

	/** Direcao (texto) de um vetor local (du, dv). */
	private String face(int du, int dv) {
		return dir(dx * dv + Math.abs(dz) * du, dz * dv + Math.abs(dx) * du);
	}

	private String vAxis() {
		return dx != 0 ? "x" : "z";
	}

	private String uAxis() {
		return dx != 0 ? "z" : "x";
	}

	private void set(int u, int v, int y, String state) {
		p.set(x(u, v), y, z(u, v), state);
	}

	private void remove(int u, int v, int y) {
		p.remove(x(u, v), y, z(u, v));
	}

	private boolean has(int u, int v, int y) {
		return p.get(x(u, v), y, z(u, v)) != null;
	}

	// --- forma do casco ---

	/** Meia largura do convés em {@code u} (-1 fora da nave). */
	static int width(int u) {
		if (u < STERN || u > BOW) {
			return -1;
		}
		if (u > 7) {
			double t = (u - 7) / 9.0;
			return (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - t * t)));
		}
		return u >= -11 ? 5 : 4;
	}

	static int depth(int u) {
		if (u > 6) {
			return Math.max(2, 6 - (u - 5) / 2);
		}
		return u < -9 ? Math.max(4, 6 - (-9 - u)) : 6;
	}

	private static final int[] DROP = {0, 0, 1, 1, 2, 3, 5};

	/** Meia largura da camada {@code j} abaixo do convés (-1 se nao existe). */
	static int layer(int u, int j) {
		int w = width(u);
		if (w < 0 || j > depth(u)) {
			return -1;
		}
		return w - DROP[Math.min(j, DROP.length - 1)];
	}

	private static boolean inHull(int u, int v, int j) {
		int w = layer(u, j);
		return w >= 0 && Math.abs(v) <= w;
	}

	private static boolean holdRegion(int u) {
		return u >= -5 && u <= 8;
	}

	private void build() {
		hull();
		deckAndBulwark();
		sternCastle();
		masts();
		bow();
		hold();
		engines();
		p.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.SENTINEL, x(5, 2), deck + 1, z(5, 2), 0, 0));
		p.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.SHULKER, x(12, 0), deck + 2, z(12, 0), 0, 0));
		p.mark("ship_deck", x(0, 0), deck + 1, z(0, 0));
	}

	private void hull() {
		for (int u = STERN; u <= BOW; u++) {
			for (int j = 1; j <= depth(u); j++) {
				int w = layer(u, j);
				if (w < 0) {
					continue;
				}
				int y = deck - j;
				for (int v = -w; v <= w; v++) {
					boolean shell = Math.abs(v) == w || j == depth(u) || !inHull(u + 1, v, j) || !inHull(u - 1, v, j) || !inHull(u, v, j + 1);
					boolean hold = holdRegion(u) && j <= 3 && !shell;
					if (hold) {
						set(u, v, y, VPlan.AIR);
						continue;
					}
					String block;
					if (!shell) {
						block = PURPUR;
					} else if (j == depth(u) && Math.abs(v) < w) {
						block = BRICKS;
					} else if (j == 3) {
						block = VOID_BRICKS;
					} else if (j == 2 && Math.abs(v) == w && holdRegion(u) && Math.floorMod(u, 4) == 0) {
						block = GLASS;
					} else if (v != 0 && Math.abs(v) == w && j < depth(u) && !inHull(u, v, j + 1)) {
						block = CitadelPlanner.stairsTop(PURPUR_STAIRS, face(0, -Integer.signum(v)));
					} else {
						block = PURPUR;
					}
					set(u, v, y, block);
				}
			}
		}
		// leme
		for (int y = deck - 5; y <= deck - 1; y++) {
			set(STERN - 1, 0, y, PURPUR);
		}
		// barbatanas da popa
		for (int u = STERN + 1; u <= STERN + 4; u++) {
			int w = layer(u, 2);
			for (int side : new int[] {-1, 1}) {
				for (int k = 1; k <= (u <= STERN + 2 ? 2 : 1); k++) {
					set(u, side * (w + k), deck - 2, s(PURPUR_SLAB, "type=top"));
				}
			}
		}
	}

	private boolean deckCell(int u, int v) {
		int w = width(u);
		return w >= 0 && Math.abs(v) <= w;
	}

	private void deckAndBulwark() {
		for (int u = STERN; u <= BOW; u++) {
			int w = width(u);
			for (int v = -w; v <= w; v++) {
				boolean edge = !deckCell(u + 1, v) || !deckCell(u - 1, v) || !deckCell(u, v + 1) || !deckCell(u, v - 1);
				boolean lamp = (u == -3 || u == 5) && Math.abs(v) == 3;
				set(u, v, deck, edge ? PURPUR : lamp ? LAMP : Math.floorMod(u, 6) == 0 ? PURPUR : VOID_PLANKS);
				for (int y = deck + 1; y <= deck + 3; y++) {
					set(u, v, y, VPlan.AIR);
				}
				boolean gangway = v < 0 && u >= -1 && u <= 1 && Math.abs(v) == w;
				if (edge && !gangway && u > CASTLE_FRONT) {
					boolean post = Math.floorMod(u, 4) == 0 || u == BOW;
					set(u, v, deck + 1, post ? PILLAR : s(PURPUR_SLAB, "type=bottom"));
					if (post) {
						set(u, v, deck + 2, CitadelPlanner.rod("up"));
					} else {
						remove(u, v, deck + 2);
					}
				}
			}
		}
	}

	private void sternCastle() {
		int roof = deck + 5;
		for (int u = STERN; u <= CASTLE_FRONT; u++) {
			int w = width(u);
			for (int v = -w; v <= w; v++) {
				boolean wall = Math.abs(v) == w || u == STERN || u == CASTLE_FRONT;
				for (int y = deck + 1; y < roof; y++) {
					if (!wall) {
						set(u, v, y, VPlan.AIR);
						continue;
					}
					int level = y - deck;
					String block = PURPUR;
					boolean corner = (Math.abs(v) == w && (u == STERN || u == CASTLE_FRONT));
					if (corner) {
						block = PILLAR;
					} else if (level >= 2 && level <= 3 && ((Math.abs(v) == w && (u == -12 || u == -9)) || ((u == STERN || u == CASTLE_FRONT) && Math.abs(v) == 2))) {
						block = PANE;
					} else if (level == 4) {
						block = VOID_BRICKS;
					}
					set(u, v, y, block);
				}
				boolean edge = Math.abs(v) == w || u == STERN || u == CASTLE_FRONT;
				set(u, v, roof, edge ? VOID_BRICKS : ((u == -10 || u == -8) && Math.abs(v) == 2) ? LAMP : PURPUR);
				boolean entry = u == CASTLE_FRONT && (v == 3 || v == 4);
				if (edge && !entry) {
					set(u, v, roof + 1, (u + v & 1) == 0 ? PURPUR : BRICK_WALL);
				} else {
					set(u, v, roof + 1, VPlan.AIR);
					set(u, v, roof + 2, VPlan.AIR);
				}
			}
		}
		// porta da cabine
		set(CASTLE_FRONT, 0, deck + 1, VPlan.AIR);
		set(CASTLE_FRONT, 0, deck + 2, VPlan.AIR);
		set(CASTLE_FRONT, 0, deck + 3, CitadelPlanner.stairsTop(PURPUR_STAIRS, face(1, 0)));
		set(CASTLE_FRONT + 1, -1, deck + 3, CitadelPlanner.banner("magenta", face(1, 0)));
		set(CASTLE_FRONT + 1, 1, deck + 3, CitadelPlanner.banner("purple", face(1, 0)));
		// escada ate o tombadilho (lado de fora do cais)
		for (int i = 0; i < 5; i++) {
			int u = -1 - i;
			for (int v = 3; v <= 4; v++) {
				for (int y = deck + 1; y < deck + 1 + i; y++) {
					set(u, v, y, PURPUR);
				}
				set(u, v, deck + 1 + i, CitadelPlanner.stairs(PURPUR_STAIRS, face(-1, 0)));
			}
		}
		// cabine do tesouro
		int y = deck + 1;
		for (int u = STERN + 1; u <= CASTLE_FRONT - 1; u++) {
			set(u, 0, y, "minecraft:magenta_carpet");
		}
		p.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.ELYTRA_FRAME, x(STERN + 1, 0), deck + 2, z(STERN + 1, 0), dx * 0 + Math.abs(dz), Math.abs(dx)));
		p.mark("elytra", x(STERN + 1, 0), deck + 2, z(STERN + 1, 0));
		set(STERN + 1, -1, deck + 3, CitadelPlanner.rod(face(1, 0)));
		set(STERN + 1, 1, deck + 3, CitadelPlanner.rod(face(1, 0)));
		p.chest(x(STERN + 1, -2), y, z(STERN + 1, -2), s("minecraft:chest", "facing=" + face(1, 0)));
		p.chest(x(STERN + 1, 2), y, z(STERN + 1, 2), s("minecraft:chest", "facing=" + face(1, 0)));
		set(CASTLE_FRONT - 1, 3, y, VOID_PLANKS);
		set(CASTLE_FRONT - 1, 3, y + 1, "minecraft:brewing_stand");
		set(CASTLE_FRONT - 1, -3, y, "minecraft:barrel[facing=up]");
		set(-9, -3, y, s("minecraft:purple_bed", "facing=" + face(1, 0), "part=head"));
		set(-10, -3, y, s("minecraft:purple_bed", "facing=" + face(1, 0), "part=foot"));
		p.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.SHULKER, x(-9, 2), y, z(-9, 2), 0, 0));
		// lanternas da popa e shulker no tombadilho
		for (int v : new int[] {-3, 3}) {
			set(STERN + 1, v, roof + 1, PILLAR);
			set(STERN + 1, v, roof + 2, LAMP);
			set(STERN + 1, v, roof + 3, CitadelPlanner.rod("up"));
		}
		p.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.SHULKER, x(-9, -2), roof + 1, z(-9, -2), 0, 0));
	}

	private void masts() {
		mast(3, deck + 1, deck + 19, new int[][] {{deck + 14, 5, deck + 13, deck + 9}, {deck + 8, 5, deck + 7, deck + 3}});
		mast(10, deck + 1, deck + 13, new int[][] {{deck + 11, 4, deck + 10, deck + 4}});
		mast(-10, deck + 6, deck + 14, new int[][] {{deck + 12, 3, deck + 11, deck + 8}});
		// cesto da gavea
		int nest = deck + 16;
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (du != 0 || dv != 0) {
					set(3 + du, dv, nest, s(PURPUR_SLAB, "type=top"));
					set(3 + du, dv, nest + 1, BRICK_WALL);
				}
			}
		}
		set(2, 0, deck + 18, CitadelPlanner.banner("magenta", face(-1, 0)));
		// mastro atravessa o convés ate o porao
		for (int y = deck - 3; y <= deck; y++) {
			set(3, 0, y, s(VOID_LOG, "axis=y"));
		}
	}

	/** Mastro com vergas {@code {y da verga, meia largura, topo da vela, base da vela}} e velas bojudas listradas. */
	private void mast(int u, int from, int to, int[][] yards) {
		for (int y = from; y <= to; y++) {
			set(u, 0, y, s(VOID_LOG, "axis=y"));
		}
		set(u, 0, to + 1, CitadelPlanner.rod("up"));
		for (int[] yard : yards) {
			int yy = yard[0];
			int half = yard[1];
			for (int v = -half; v <= half; v++) {
				if (v != 0) {
					set(u, v, yy, s(VOID_LOG, "axis=" + vAxis()));
				}
			}
			set(u, -half - 1, yy, CitadelPlanner.rod(face(0, -1)));
			set(u, half + 1, yy, CitadelPlanner.rod(face(0, 1)));
			int top = yard[2];
			int bottom = yard[3];
			for (int y = top; y >= bottom; y--) {
				int row = top - y;
				boolean inner = y < top && y > bottom;
				for (int v = -(half - 1); v <= half - 1; v++) {
					int bu = inner && Math.abs(v) <= half - 2 ? 1 : 0;
					if (bu == 0 && v == 0) {
						continue;
					}
					String wool = row % 3 == 1 ? "minecraft:purple_wool" : "minecraft:magenta_wool";
					if (bu == 1 && v == 0 && y == (top + bottom) / 2) {
						wool = "minecraft:black_wool";
					}
					set(u + bu, v, y, wool);
				}
			}
		}
	}

	private void bow() {
		for (int y = deck - 2; y <= deck + 2; y++) {
			set(BOW, 0, y, PILLAR);
		}
		set(BOW + 1, 0, deck + 1, s("minecraft:dragon_wall_head", "facing=" + face(1, 0)));
		for (int u = BOW + 1; u <= BOW + 4; u++) {
			set(u, 0, deck + 2, s(PILLAR, "axis=" + uAxis()));
		}
		set(BOW + 5, 0, deck + 2, CitadelPlanner.rod(face(1, 0)));
		// castelo de proa baixo
		for (int u = 12; u <= BOW - 1; u++) {
			int w = width(u);
			for (int v = -w + 1; v <= w - 1; v++) {
				if (!has(u, v, deck + 1) || p.get(x(u, v), deck + 1, z(u, v)).equals(VPlan.AIR)) {
					set(u, v, deck + 1, s(PURPUR_SLAB, "type=bottom"));
				}
			}
		}
	}

	private void hold() {
		// escotilha com escada de mao
		int hu = 6;
		for (int y = deck - 3; y <= deck - 1; y++) {
			set(hu, 0, y, PILLAR);
		}
		for (int y = deck - 3; y <= deck; y++) {
			set(hu, -1, y, s("minecraft:ladder", "facing=" + face(0, -1)));
		}
		for (int y = deck + 1; y <= deck + 2; y++) {
			remove(hu, -1, y);
		}
		set(hu - 1, -1, deck + 1, VPlan.AIR);
		int y = deck - 3;
		p.chest(x(-4, 2), y, z(-4, 2), s("minecraft:chest", "facing=" + face(0, -1)));
		set(-4, -2, y, "minecraft:barrel[facing=up]");
		set(-5, -1, y, "minecraft:barrel[facing=up]");
		set(8, 2, y, "minecraft:brewing_stand");
		set(8, -2, y, "minecraft:brewing_stand");
		set(-5, 2, y, "minecraft:decorated_pot");
		p.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.SHULKER, x(1, 2), y, z(1, 2), 0, 0));
		p.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.SHULKER, x(-2, -2), y, z(-2, -2), 0, 0));
	}

	private void engines() {
		for (int u : new int[] {-8, 0, 7}) {
			int bottom = deck - depth(u);
			set(u, 0, bottom, CRYSTAL);
			set(u, 0, bottom - 1, CitadelPlanner.rod("down"));
		}
		for (int v : new int[] {-2, 2}) {
			set(STERN - 1, v, deck - 2, CitadelPlanner.rod(face(-1, 0)));
		}
	}
}
