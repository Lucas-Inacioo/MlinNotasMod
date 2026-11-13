package com.mlin.notas.vstructures;

import java.util.Random;

/** Ferramentas comuns dos planners (logica pura): caixas, cascas, degraus, sorteios. */
abstract class PlanBuilder {
	static final String N = "north";
	static final String S = "south";
	static final String E = "east";
	static final String W = "west";

	final VPlan p;
	final Random r;

	PlanBuilder(VPlan plan, Random random) {
		this.p = plan;
		this.r = random;
	}

	static String s(String id, String... props) {
		return VPlan.s(id, props);
	}

	static String opposite(String dir) {
		return switch (dir) {
			case N -> S;
			case S -> N;
			case E -> W;
			default -> E;
		};
	}

	/** Gira 90 graus no sentido horario. */
	static String cw(String dir) {
		return switch (dir) {
			case N -> E;
			case E -> S;
			case S -> W;
			default -> N;
		};
	}

	static int dx(String dir) {
		return dir.equals(E) ? 1 : dir.equals(W) ? -1 : 0;
	}

	static int dz(String dir) {
		return dir.equals(S) ? 1 : dir.equals(N) ? -1 : 0;
	}

	static String stairs(String id, String facing) {
		return s(id, "facing=" + facing);
	}

	static String stairsTop(String id, String facing) {
		return s(id, "facing=" + facing, "half=top");
	}

	static String slab(String id) {
		return s(id);
	}

	static String slabTop(String id) {
		return s(id, "type=top");
	}

	static String axis(String id, String axis) {
		return s(id, "axis=" + axis);
	}

	static String wallTorch(String facing) {
		return s("wall_torch", "facing=" + facing);
	}

	static String ladder(String facing) {
		return s("ladder", "facing=" + facing);
	}

	boolean chance(double p) {
		return r.nextDouble() < p;
	}

	int between(int min, int max) {
		return min + r.nextInt(max - min + 1);
	}

	@SafeVarargs
	final <T> T pick(T... options) {
		return options[r.nextInt(options.length)];
	}

	void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
		p.fill(x1, y1, z1, x2, y2, z2, state);
	}

	void air(int x1, int y1, int z1, int x2, int y2, int z2) {
		p.air(x1, y1, z1, x2, y2, z2);
	}

	void set(int x, int y, int z, String state) {
		p.set(x, y, z, state);
	}

	boolean empty(int x, int y, int z) {
		String s = p.get(x, y, z);
		return s == null || BlockTraits.isAir(s);
	}

	boolean isAirAt(int x, int y, int z) {
		String s = p.get(x, y, z);
		return s != null && BlockTraits.isAir(s);
	}

	/**
	 * Sala: casca de {@code wall} (so onde ainda nao ha nada no plano, para nao fechar salas vizinhas) em volta da
	 * caixa, que vira ar.
	 */
	void room(int x1, int y1, int z1, int x2, int y2, int z2, String wall) {
		for (int x = x1 - 1; x <= x2 + 1; x++) {
			for (int y = y1 - 1; y <= y2 + 1; y++) {
				for (int z = z1 - 1; z <= z2 + 1; z++) {
					if (p.get(x, y, z) == null) {
						p.set(x, y, z, wall);
					}
				}
			}
		}
		air(x1, y1, z1, x2, y2, z2);
	}

	/** Como {@link #room}, mas a casca sorteia entre os estados dados (pedra gasta). */
	void roomMixed(int x1, int y1, int z1, int x2, int y2, int z2, String... walls) {
		for (int x = x1 - 1; x <= x2 + 1; x++) {
			for (int y = y1 - 1; y <= y2 + 1; y++) {
				for (int z = z1 - 1; z <= z2 + 1; z++) {
					if (p.get(x, y, z) == null) {
						p.set(x, y, z, walls[r.nextInt(walls.length)]);
					}
				}
			}
		}
		air(x1, y1, z1, x2, y2, z2);
	}

	/** Troca so os blocos solidos do plano que estao na caixa (revestimento de paredes ja escavadas). */
	void reclad(int x1, int y1, int z1, int x2, int y2, int z2, String... states) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					String here = p.get(x, y, z);
					if (here != null && !BlockTraits.isAir(here)) {
						p.set(x, y, z, states[r.nextInt(states.length)]);
					}
				}
			}
		}
	}
}
