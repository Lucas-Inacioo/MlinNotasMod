package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Divide cada quarteirao em lotes com frente para uma rua (logica pura). Primeiro os lotes de esquina (encostados em
 * duas ruas), maiores, para construcoes grandes; depois o resto, numa mistura sorteada de tamanhos da zona. Cada lote
 * e um retangulo dentro do quarteirao com 1 bloco livre entre lotes (passagem lateral); a porta vai no meio da frente
 * e a rua fica a no maximo 2 blocos dela. O que sobra do quarteirao fica de quintal/jardim.
 */
final class BlockSubdivider {
	private BlockSubdivider() {
	}

	/** Classes tentadas nas esquinas e a mistura do resto, por anel. */
	static SizeClass[] corners(int ring) {
		return switch (ring) {
			case 2 -> new SizeClass[] {SizeClass.L, SizeClass.M};
			case 3 -> new SizeClass[] {SizeClass.M};
			case 5 -> new SizeClass[] {SizeClass.XL, SizeClass.L};
			default -> new SizeClass[] {};
		};
	}

	static SizeClass[] mix(int ring) {
		return switch (ring) {
			case 1 -> new SizeClass[] {SizeClass.S};
			case 2 -> new SizeClass[] {SizeClass.M, SizeClass.S, SizeClass.S, SizeClass.L};
			case 3 -> new SizeClass[] {SizeClass.S, SizeClass.S, SizeClass.S, SizeClass.M};
			case 5 -> new SizeClass[] {SizeClass.L, SizeClass.M, SizeClass.M, SizeClass.L};
			case 6 -> new SizeClass[] {SizeClass.W};
			default -> new SizeClass[] {};
		};
	}

	private record Candidate(int x0, int z0, int w, int d, Facing front, double score) {
	}

	static List<MasterPlan.Parcel> subdivide(MasterPlan plan, long seed) {
		Random random = new Random(seed ^ 0xB10C5L);
		int extent = plan.extent();
		int size = 2 * extent + 1;
		boolean[] taken = new boolean[size * size];
		List<MasterPlan.Parcel> out = new ArrayList<>();
		for (MasterPlan.Block block : plan.blocks()) {
			Zone zone = block.zone();
			if (zone == null || block.cells() < 30) {
				continue;
			}
			// caixa do quarteirao
			int bx0 = Integer.MAX_VALUE;
			int bz0 = Integer.MAX_VALUE;
			int bx1 = Integer.MIN_VALUE;
			int bz1 = Integer.MIN_VALUE;
			for (int x = -extent; x <= extent; x++) {
				for (int z = -extent; z <= extent; z++) {
					if (plan.code(x, z) == MasterPlan.BLOCK + block.id()) {
						bx0 = Math.min(bx0, x);
						bz0 = Math.min(bz0, z);
						bx1 = Math.max(bx1, x);
						bz1 = Math.max(bz1, z);
					}
				}
			}
			Sat sat = new Sat(plan, block.id(), taken, bx0, bz0, bx1, bz1);
			for (SizeClass cls : corners(block.ring())) {
				for (int n = 0; n < 2; n++) {
					Candidate c = best(plan, sat, cls, true);
					if (c == null) {
						break;
					}
					out.add(take(plan, c, cls, zone, block, out.size(), taken, sat));
				}
			}
			SizeClass[] mix = mix(block.ring());
			int turn = random.nextInt(Math.max(1, mix.length));
			for (int guard = 0; guard < 64 && mix.length > 0; guard++) {
				Candidate c = null;
				SizeClass used = null;
				for (int k = 0; k < mix.length && c == null; k++) {
					SizeClass cls = mix[(turn + k) % mix.length];
					c = best(plan, sat, cls, false);
					used = cls;
				}
				if (c == null) {
					// nada da mistura coube: tenta o menor
					SizeClass small = block.ring() == 6 ? SizeClass.W : SizeClass.S;
					c = best(plan, sat, small, false);
					used = small;
				}
				if (c == null) {
					break;
				}
				out.add(take(plan, c, used, zone, block, out.size(), taken, sat));
				turn++;
			}
		}
		return out;
	}

	private static MasterPlan.Parcel take(MasterPlan plan, Candidate c, SizeClass cls, Zone zone, MasterPlan.Block block, int id, boolean[] taken,
			Sat sat) {
		int extent = plan.extent();
		int size = 2 * extent + 1;
		for (int x = c.x0 - 1; x <= c.x0 + c.w; x++) {
			for (int z = c.z0 - 1; z <= c.z0 + c.d; z++) {
				if (plan.in(x, z)) {
					taken[(x + extent) * size + (z + extent)] = true;
				}
			}
		}
		sat.rebuild();
		return new MasterPlan.Parcel(id, block.id(), new VillageState.Rect(c.x0, c.z0, c.x0 + c.w - 1, c.z0 + c.d - 1), c.front, cls, zone, false);
	}

	/** Melhor lugar para um lote da classe (null = nao cabe). {@code corner}: so encostado em duas ruas. */
	private static Candidate best(MasterPlan plan, Sat sat, SizeClass cls, boolean corner) {
		Candidate best = null;
		for (Facing front : Facing.values()) {
			int w = front.alongX() ? cls.depth() : cls.width();
			int d = front.alongX() ? cls.width() : cls.depth();
			for (int x0 = sat.x0; x0 + w - 1 <= sat.x1; x0++) {
				for (int z0 = sat.z0; z0 + d - 1 <= sat.z1; z0++) {
					if (!sat.all(x0, z0, x0 + w - 1, z0 + d - 1)) {
						continue;
					}
					double front_ = frontScore(plan, x0, z0, w, d, front);
					if (front_ < 0) {
						continue;
					}
					boolean isCorner = side(plan, x0, z0, w, d, front.rotate(1)) || side(plan, x0, z0, w, d, front.rotate(3));
					if (corner && !isCorner) {
						continue;
					}
					double score = front_ + snug(plan, sat, x0, z0, w, d) * 0.8 + (isCorner ? 2 : 0);
					// desempate estavel: mais perto do centro
					score -= Math.hypot(x0 + w / 2.0, z0 + d / 2.0) * 0.01;
					if (best == null || score > best.score) {
						best = new Candidate(x0, z0, w, d, front, score);
					}
				}
			}
		}
		return best;
	}

	/** Celulas da borda do lote no lado {@code f}, de ponta a ponta. */
	private static int[][] edge(int x0, int z0, int w, int d, Facing f) {
		int n = f.alongX() ? d : w;
		int[][] out = new int[n][];
		for (int i = 0; i < n; i++) {
			out[i] = switch (f) {
				case NORTH -> new int[] {x0 + i, z0};
				case SOUTH -> new int[] {x0 + i, z0 + d - 1};
				case WEST -> new int[] {x0, z0 + i};
				case EAST -> new int[] {x0 + w - 1, z0 + i};
			};
		}
		return out;
	}

	/** Distancia ate a rua andando para {@code f} a partir da celula (0 = nenhuma ate {@code max}). */
	private static int streetAhead(MasterPlan plan, int x, int z, Facing f, int max) {
		for (int k = 1; k <= max; k++) {
			int nx = x + f.dx * k;
			int nz = z + f.dz * k;
			if (plan.ground(nx, nz)) {
				return k;
			}
			int code = plan.code(nx, nz);
			if (code == MasterPlan.OUT || code == MasterPlan.LAKE || code == MasterPlan.CARVED || code == MasterPlan.PLAZA) {
				return 0;
			}
		}
		return 0;
	}

	/** Nota da frente (negativa = nao serve): porta a ate 2 da rua e metade da frente a ate 3. */
	private static double frontScore(MasterPlan plan, int x0, int z0, int w, int d, Facing f) {
		int[][] edge = edge(x0, z0, w, d, f);
		int[] door = edge[edge.length / 2];
		int doorDist = streetAhead(plan, door[0], door[1], f, 2);
		if (doorDist == 0) {
			return -1;
		}
		int near = 0;
		for (int[] c : edge) {
			if (streetAhead(plan, c[0], c[1], f, 3) > 0) {
				near++;
			}
		}
		if (near * 2 < edge.length) {
			return -1;
		}
		return 4.0 * near / edge.length - doorDist;
	}

	private static boolean side(MasterPlan plan, int x0, int z0, int w, int d, Facing f) {
		int[][] edge = edge(x0, z0, w, d, f);
		int near = 0;
		for (int[] c : edge) {
			if (streetAhead(plan, c[0], c[1], f, 2) > 0) {
				near++;
			}
		}
		return near * 2 >= edge.length;
	}

	/** Quantas celulas em volta do lote nao estao livres (encaixe justo deixa menos sobra). */
	private static int snug(MasterPlan plan, Sat sat, int x0, int z0, int w, int d) {
		int n = 0;
		for (int x = x0 - 1; x <= x0 + w; x++) {
			n += sat.free(x, z0 - 1) ? 0 : 1;
			n += sat.free(x, z0 + d) ? 0 : 1;
		}
		for (int z = z0; z < z0 + d; z++) {
			n += sat.free(x0 - 1, z) ? 0 : 1;
			n += sat.free(x0 + w, z) ? 0 : 1;
		}
		return n;
	}

	/** Soma acumulada das celulas livres do quarteirao (teste de retangulo em O(1)). */
	private static final class Sat {
		final MasterPlan plan;
		final int block;
		final boolean[] taken;
		final int x0;
		final int z0;
		final int x1;
		final int z1;
		int[] sum;

		Sat(MasterPlan plan, int block, boolean[] taken, int x0, int z0, int x1, int z1) {
			this.plan = plan;
			this.block = block;
			this.taken = taken;
			this.x0 = x0;
			this.z0 = z0;
			this.x1 = x1;
			this.z1 = z1;
			rebuild();
		}

		boolean free(int x, int z) {
			if (!plan.in(x, z) || plan.code(x, z) != MasterPlan.BLOCK + block) {
				return false;
			}
			int extent = plan.extent();
			return !taken[(x + extent) * (2 * extent + 1) + (z + extent)];
		}

		void rebuild() {
			int w = x1 - x0 + 1;
			int d = z1 - z0 + 1;
			sum = new int[(w + 1) * (d + 1)];
			for (int i = 0; i < w; i++) {
				for (int j = 0; j < d; j++) {
					int v = free(x0 + i, z0 + j) ? 1 : 0;
					sum[(i + 1) * (d + 1) + j + 1] = v + sum[i * (d + 1) + j + 1] + sum[(i + 1) * (d + 1) + j] - sum[i * (d + 1) + j];
				}
			}
		}

		boolean all(int ax, int az, int bx, int bz) {
			int d = z1 - z0 + 1;
			int i0 = ax - x0;
			int j0 = az - z0;
			int i1 = bx - x0 + 1;
			int j1 = bz - z0 + 1;
			int n = sum[i1 * (d + 1) + j1] - sum[i0 * (d + 1) + j1] - sum[i1 * (d + 1) + j0] + sum[i0 * (d + 1) + j0];
			return n == (bx - ax + 1) * (bz - az + 1);
		}
	}
}
