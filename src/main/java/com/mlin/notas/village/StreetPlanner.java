package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ruas do plano diretor (logica pura): aneis octogonais (quadrado de cantos chanfrados, so trechos retos a 0 e 45
 * graus), avenidas radiais de 5 de largura que desviam pelo relevo com degraus a 45 graus, travessas entre aneis
 * quando a frente do quarteirao passa de {@link #MAX_FRONTAGE} e o contorno em volta de areas de jogador (a rua se
 * abre em volta, como uma rotatoria). Tudo rasterizado numa grade de celulas centrada no quadro.
 *
 * <p>Os aneis de rua ficam em {@link #RING_STREETS}: o de 48 corre logo por dentro da muralha de pedra (meia-largura
 * 50) e o de 67 deixa a faixa da muralha externa (meia-largura 74) para as torres. Entre dois aneis cabe um lote de
 * 13 de fundo, entao todo lote tem frente para uma rua e a planta inteira cabe com a porta para ela.
 *
 * <p>Referencias de forma: cidades medievais concentricas (Nordlingen, Palmanova) e as "rings and radials" dos
 * planejadores de cidade; escrito do zero.
 */
public final class StreetPlanner {
	/** Meia-largura (apotema nos eixos) dos aneis de rua. */
	public static final int[] RING_STREETS = {14, 31, 48, 67};
	/** Meia-largura da borda do plano (muralha externa em 75-76). */
	public static final int OUTER = 74;
	/** Corte dos cantos: |x| + |z| <= DIAGONAL * meia-largura (igual a muralha de pedra: 84/50). */
	public static final double DIAGONAL = 1.68;
	public static final int AVENUE_WIDTH = 5;
	public static final int STREET_WIDTH = 3;
	public static final int MAX_FRONTAGE = 40;
	/** Raio da praca (celulas da praca nao sao rua nem quarteirao). */
	public static final double PLAZA = VillagePlanner.PLAZA_RADIUS + 1.5;

	public static final int AVENUE = 1;
	public static final int RING = 2;
	public static final int LANE = 3;
	public static final int LOOP = 4;

	private static final double SQRT2 = Math.sqrt(2);
	/** Normais dos 8 lados: +x, +x+z, +z, -x+z, -x, -x-z, -z, +x-z. */
	static final int[][] SIDES = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

	private StreetPlanner() {
	}

	/** Octogono irregular: apotema de cada lado (lados diagonais ja divididos por raiz de 2). */
	public record Octagon(double[] apothem) {
		/** Octogono com meia-largura {@code half} e deslocamento {@code delta[i]} por lado (pode ser null). */
		public static Octagon of(int half, int[] delta) {
			double[] a = new double[8];
			for (int i = 0; i < 8; i++) {
				int d = delta == null ? 0 : delta[i];
				a[i] = i % 2 == 0 ? half + d : (Math.round(DIAGONAL * half) + d) / SQRT2;
			}
			return new Octagon(a);
		}

		/** Distancia (com sinal) ate a borda: negativa dentro. */
		public double f(double x, double z) {
			double best = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < 8; i++) {
				double n = SIDES[i][0] * x + SIDES[i][1] * z;
				if (i % 2 == 1) {
					n /= SQRT2;
				}
				best = Math.max(best, n - apothem[i]);
			}
			return best;
		}

		/** Lado ativo (o que define a borda) no ponto. */
		public int side(double x, double z) {
			double best = Double.NEGATIVE_INFINITY;
			int side = 0;
			for (int i = 0; i < 8; i++) {
				double n = SIDES[i][0] * x + SIDES[i][1] * z;
				if (i % 2 == 1) {
					n /= SQRT2;
				}
				if (n - apothem[i] > best) {
					best = n - apothem[i];
					side = i;
				}
			}
			return side;
		}

		/** Ponto da borda na direcao do angulo (raio a partir do centro). */
		public double[] along(double angle) {
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			double lo = 0;
			double hi = 200;
			for (int i = 0; i < 40; i++) {
				double mid = (lo + hi) / 2;
				if (f(dx * mid, dz * mid) < 0) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			return new double[] {dx * lo, dz * lo};
		}
	}

	/** Rede de ruas rasterizada e o que veio dela. */
	public static final class Result {
		public final int extent;
		public final int size;
		/** Tipo de rua por celula (0 = nenhuma). */
		public final byte[] kind;
		public final Octagon[] rings;
		public final Octagon outer;
		/** Avenidas: lista de pontos (x, z) da linha central de cada uma. */
		public final List<double[][]> avenues = new ArrayList<>();

		Result(int extent, Octagon[] rings, Octagon outer) {
			this.extent = extent;
			this.size = 2 * extent + 1;
			this.kind = new byte[size * size];
			this.rings = rings;
			this.outer = outer;
		}

		public int idx(int x, int z) {
			return (x + extent) * size + (z + extent);
		}

		public boolean in(int x, int z) {
			return Math.abs(x) <= extent && Math.abs(z) <= extent;
		}

		public int kind(int x, int z) {
			return in(x, z) ? kind[idx(x, z)] : 0;
		}

		void mark(int x, int z, int k) {
			if (in(x, z) && inside(x, z) && x * x + z * z > PLAZA * PLAZA) {
				int i = idx(x, z);
				if (kind[i] == 0 || k == AVENUE) {
					kind[i] = (byte) k;
				}
			}
		}

		/** Dentro da borda do plano. */
		public boolean inside(int x, int z) {
			return outer.f(x, z) <= 0.5;
		}

		/**
		 * Faixa (anel de lotes) da celula: 0 dentro do primeiro anel, 1 entre o 1o e o 2o, ... 4 fora do ultimo. Celula
		 * na rua de um anel conta como a faixa de dentro dele.
		 */
		public int row(int x, int z) {
			int row = 0;
			for (Octagon ring : rings) {
				if (ring.f(x, z) > 1.5) {
					row++;
				}
			}
			return row;
		}

		/** Anel do terreno (1..6) da celula: faixa, com a faixa da muralha de pedra separada (anel 4). */
		public int terrainRing(int x, int z) {
			if (!in(x, z) || !inside(x, z)) {
				return 0;
			}
			int row = 0;
			int onStreet = -1;
			for (int k = 0; k < rings.length; k++) {
				double f = rings[k].f(x, z);
				if (f > 1.5) {
					row++;
				} else if (f > -1.5 && onStreet < 0) {
					onStreet = k;
				}
			}
			if (onStreet >= 0) {
				// rua do anel: 14 -> 1, 31 -> 2, 48 -> 4 (com a faixa da muralha), 67 -> 5
				return new int[] {1, 2, 4, 5}[onStreet];
			}
			return switch (row) {
				case 0 -> 1;
				case 1 -> 2;
				case 2 -> 3;
				case 3 -> rings[2].f(x, z) <= 4.5 ? 4 : 5;
				default -> 6;
			};
		}
	}

	/** Leituras do terreno ja amostradas na grade (fundo, agua, obstaculos). */
	public interface Field {
		int bed(int x, int z);

		int water(int x, int z);

		/** Celula proibida para rua (area de jogador, obra antiga, area protegida). */
		boolean blocked(int x, int z);
	}

	/**
	 * Planeja as ruas. {@code avenues}: 4 nos eixos e ate 2 diagonais ({@code diagonals}); {@code straightUntil}: as
	 * avenidas dos eixos ficam retas (no eixo) ate esse raio (vila antiga: depois do ultimo lote existente).
	 */
	public static Result plan(long seed, Field field, int diagonals, int straightUntil) {
		Random random = new Random(seed ^ 0x57EE7L);
		int extent = OUTER + 2;
		Octagon outer = Octagon.of(OUTER, null);
		Octagon[] rings = new Octagon[RING_STREETS.length];
		int[][] deltas = ringDeltas(field, random, extent);
		for (int k = 0; k < rings.length; k++) {
			rings[k] = Octagon.of(RING_STREETS[k], deltas[k]);
		}
		Result r = new Result(extent, rings, outer);
		// aneis
		for (int x = -extent; x <= extent; x++) {
			for (int z = -extent; z <= extent; z++) {
				for (Octagon ring : rings) {
					double f = ring.f(x, z);
					if (f > -1.5 && f <= 1.5) {
						r.mark(x, z, RING);
					}
				}
			}
		}
		// avenidas dos eixos (com desvio pelo relevo) e diagonais
		for (int a = 0; a < 4; a++) {
			int ux = SIDES[a * 2][0];
			int uz = SIDES[a * 2][1];
			int[] offsets = avenueOffsets(field, random, ux, uz, straightUntil);
			double[][] line = axisLine(ux, uz, offsets);
			r.avenues.add(line);
			raster(r, line, AVENUE_WIDTH / 2.0, AVENUE);
		}
		for (int d : diagonalChoice(field, random, diagonals, rings)) {
			int sx = SIDES[d][0];
			int sz = SIDES[d][1];
			double[][] line = {{sx * 6.0, sz * 6.0}, {sx * 66.0, sz * 66.0}};
			r.avenues.add(line);
			raster(r, line, AVENUE_WIDTH / 2.0, AVENUE);
		}
		lanes(r, random);
		return r;
	}

	/** Desvio de cada lado de cada anel: puxado pelo relevo, agua e obstaculos, com o fundo dos lotes garantido. */
	static int[][] ringDeltas(Field field, Random random, int extent) {
		int[][] out = new int[RING_STREETS.length][8];
		int[][] range = {{-1, 1}, {-1, 1}, {0, 0}, {0, 2}};
		for (int k = 0; k < RING_STREETS.length; k++) {
			for (int side = 0; side < 8; side++) {
				double best = Double.MAX_VALUE;
				for (int d = range[k][0]; d <= range[k][1]; d++) {
					if (k == 1 && d < out[0][side] - 1) {
						continue;
					}
					int[] delta = new int[8];
					delta[side] = d;
					Octagon o = Octagon.of(RING_STREETS[k], delta);
					double cost = Math.abs(d) * 2.0 + random.nextDouble() * 0.5;
					int step = 2;
					for (int x = -extent; x <= extent; x += step) {
						for (int z = -extent; z <= extent; z += step) {
							double f = o.f(x, z);
							if (f > -1.5 && f <= 1.5 && o.side(x, z) == side) {
								cost += cellCost(field, x, z);
							}
						}
					}
					if (cost < best) {
						best = cost;
						out[k][side] = d;
					}
				}
			}
		}
		return out;
	}

	/** Custo de passar rua pela celula: declive, agua e obstaculo. */
	static double cellCost(Field field, int x, int z) {
		double cost = Math.abs(field.bed(x + 1, z) - field.bed(x - 1, z)) + Math.abs(field.bed(x, z + 1) - field.bed(x, z - 1));
		int water = field.water(x, z);
		if (water > 2) {
			cost += 30;
		} else if (water > 0) {
			cost += 4;
		}
		if (field.blocked(x, z)) {
			cost += 40;
		}
		return cost;
	}

	/** Pontos de controle das avenidas (raio) e o desvio lateral em cada um. */
	static final int[] AVENUE_STOPS = {6, 14, 31, 48, 67, 76};

	/**
	 * Desvio lateral da avenida em cada parada: sai reta da praca e passa reta pelo portao da muralha (48); entre essas
	 * paradas o relevo escolhe de -3 a 3 (no maximo ~20 graus).
	 */
	static int[] avenueOffsets(Field field, Random random, int ux, int uz, int straightUntil) {
		int[] best = new int[AVENUE_STOPS.length];
		double bestCost = Double.MAX_VALUE;
		for (int c1 = -3; c1 <= 3; c1++) {
			for (int c3 = -3; c3 <= 3; c3++) {
				if (straightUntil >= 31 && c1 != 0) {
					continue;
				}
				if (straightUntil >= 67 && c3 != 0) {
					continue;
				}
				int[] offsets = {0, 0, c1, 0, c3, c3};
				double[][] line = axisLine(ux, uz, offsets);
				double cost = (Math.abs(c1) + Math.abs(c3)) * 1.5 + random.nextDouble() * 0.6;
				for (double[] p : sample(line, 1.0)) {
					int x = (int) Math.round(p[0]);
					int z = (int) Math.round(p[1]);
					cost += cellCost(field, x, z);
					for (int w = -2; w <= 2; w += 2) {
						cost += field.blocked(x - uz * w, z + ux * w) ? 40 : 0;
					}
				}
				if (cost < bestCost) {
					bestCost = cost;
					best = offsets;
				}
			}
		}
		return best;
	}

	/**
	 * Linha central de uma avenida no eixo (ux, uz): reta em cada trecho, e logo depois de cruzar um anel desvia a 45
	 * graus ate o desvio do proximo trecho (curva feita de trechos retos com canto chanfrado).
	 */
	static double[][] axisLine(int ux, int uz, int[] offsets) {
		List<double[]> pts = new ArrayList<>();
		// lateral = (-uz, ux)
		double t = AVENUE_STOPS[0];
		double o = offsets[0];
		pts.add(world(ux, uz, t, o));
		for (int i = 1; i < AVENUE_STOPS.length; i++) {
			// depois da muralha de pedra (48 -> portao em 50) a avenida segue reta ate 53: a rua do portao fica plana e reta por
			// 3 blocos dos dois lados da muralha (Vilas 3.0, {@link HeightFieldBuilder#GATE_HALF})
			double start = AVENUE_STOPS[i - 1] + (i - 1 == 0 ? 0 : AVENUE_STOPS[i - 1] == 48 ? 5 : 3);
			double target = offsets[i];
			if (target != o && start < AVENUE_STOPS[i]) {
				pts.add(world(ux, uz, start, o));
				double jog = Math.abs(target - o);
				pts.add(world(ux, uz, start + jog, target));
				o = target;
			}
			pts.add(world(ux, uz, AVENUE_STOPS[i], o));
		}
		return pts.toArray(new double[0][]);
	}

	private static double[] world(int ux, int uz, double t, double o) {
		return new double[] {ux * t - uz * o, uz * t + ux * o};
	}

	/** Pontos a cada {@code step} ao longo da linha. */
	static List<double[]> sample(double[][] line, double step) {
		List<double[]> out = new ArrayList<>();
		for (int i = 0; i + 1 < line.length; i++) {
			double[] a = line[i];
			double[] b = line[i + 1];
			double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
			int n = Math.max(1, (int) Math.ceil(len / step));
			for (int k = 0; k < n; k++) {
				double s = (double) k / n;
				out.add(new double[] {a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s});
			}
		}
		out.add(line[line.length - 1]);
		return out;
	}

	/** Diagonais (indices de lado 1, 3, 5, 7) com menos declive, ate {@code count}. */
	static List<Integer> diagonalChoice(Field field, Random random, int count, Octagon[] rings) {
		List<double[]> scored = new ArrayList<>();
		for (int d = 1; d < 8; d += 2) {
			double[][] line = {{SIDES[d][0] * 6.0, SIDES[d][1] * 6.0}, {SIDES[d][0] * 66.0, SIDES[d][1] * 66.0}};
			double cost = random.nextDouble() * 3;
			for (double[] p : sample(line, 1.5)) {
				cost += cellCost(field, (int) Math.round(p[0]), (int) Math.round(p[1]));
			}
			scored.add(new double[] {d, cost});
		}
		scored.sort((a, b) -> Double.compare(a[1], b[1]));
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < Math.min(count, scored.size()); i++) {
			out.add((int) scored.get(i)[0]);
		}
		return out;
	}

	/** Marca as celulas a ate {@code half} da linha. */
	static void raster(Result r, double[][] line, double half, int kind) {
		for (int i = 0; i + 1 < line.length; i++) {
			double[] a = line[i];
			double[] b = line[i + 1];
			int x0 = (int) Math.floor(Math.min(a[0], b[0]) - half - 1);
			int x1 = (int) Math.ceil(Math.max(a[0], b[0]) + half + 1);
			int z0 = (int) Math.floor(Math.min(a[1], b[1]) - half - 1);
			int z1 = (int) Math.ceil(Math.max(a[1], b[1]) + half + 1);
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (segmentDistance(x, z, a, b) <= half + 1e-6) {
						r.mark(x, z, kind);
					}
				}
			}
		}
	}

	static double segmentDistance(double px, double pz, double[] a, double[] b) {
		double dx = b[0] - a[0];
		double dz = b[1] - a[1];
		double len2 = dx * dx + dz * dz;
		double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - a[0]) * dx + (pz - a[1]) * dz) / len2));
		double cx = a[0] + dx * t;
		double cz = a[1] + dz * t;
		return Math.hypot(px - cx, pz - cz);
	}

	/**
	 * Travessas: em cada faixa, a frente ao longo do anel de fora entre duas avenidas e dividida em trechos de ate
	 * {@link #MAX_FRONTAGE}; cada divisao vira uma rua de 3 perpendicular ao lado, do anel de fora ate o de dentro (na
	 * faixa dos arredores, ate a faixa da muralha).
	 */
	static void lanes(Result r, Random random) {
		int steps = 720;
		for (int k = 1; k < r.rings.length; k++) {
			Octagon outerRing = r.rings[k];
			// pontos do anel e onde as avenidas cruzam
			double[][] pts = new double[steps][];
			boolean[] avenue = new boolean[steps];
			for (int i = 0; i < steps; i++) {
				pts[i] = outerRing.along(2 * Math.PI * i / steps);
				int x = (int) Math.round(pts[i][0]);
				int z = (int) Math.round(pts[i][1]);
				avenue[i] = r.kind(x, z) == AVENUE;
			}
			int first = -1;
			for (int i = 0; i < steps; i++) {
				if (avenue[i] && !avenue[(i + steps - 1) % steps]) {
					first = i;
					break;
				}
			}
			if (first < 0) {
				continue;
			}
			// trechos entre avenidas
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
				int n = (int) Math.ceil(len / MAX_FRONTAGE) - 1;
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
					lane(r, k, pts[(first + s) % steps]);
				}
			}
		}
	}

	/** Uma travessa a partir do ponto {@code p} do anel {@code k}, perpendicular ao lado, ate o anel de dentro. */
	static void lane(Result r, int k, double[] p) {
		Octagon ring = r.rings[k];
		int side = ring.side(p[0], p[1]);
		double nx = SIDES[side][0];
		double nz = SIDES[side][1];
		double len = Math.hypot(nx, nz);
		nx /= len;
		nz /= len;
		// encaixa o ponto na grade (lados diagonais: celula inteira)
		double sx = Math.round(p[0]);
		double sz = Math.round(p[1]);
		double[] end = null;
		for (double t = 2; t < 40; t += 0.5) {
			double x = sx - nx * t;
			double z = sz - nz * t;
			boolean stop = k == 3 ? r.rings[2].f(x, z) <= 4.5 : r.rings[k - 1].f(x, z) <= 0;
			if (stop) {
				end = new double[] {x, z};
				break;
			}
		}
		if (end == null) {
			return;
		}
		raster(r, new double[][] {{sx, sz}, end}, STREET_WIDTH / 2.0, LANE);
	}

	/**
	 * Contorno de uma area proibida que a rua cruzava: tira a rua de dentro (com 1 de folga) e abre uma faixa de 3 em
	 * volta, com os cantos chanfrados. Devolve true se a area cortava alguma rua.
	 */
	public static boolean loop(Result r, VillageState.Rect area, java.util.function.BiPredicate<Integer, Integer> forbidden) {
		VillageState.Rect in = new VillageState.Rect(area.x0() - 1, area.z0() - 1, area.x1() + 1, area.z1() + 1);
		boolean cut = false;
		for (int x = in.x0(); x <= in.x1(); x++) {
			for (int z = in.z0(); z <= in.z1(); z++) {
				if (r.in(x, z) && r.kind[r.idx(x, z)] != 0) {
					r.kind[r.idx(x, z)] = 0;
					cut = true;
				}
			}
		}
		if (!cut) {
			return false;
		}
		for (int x = in.x0() - 3; x <= in.x1() + 3; x++) {
			for (int z = in.z0() - 3; z <= in.z1() + 3; z++) {
				int dx = Math.max(Math.max(in.x0() - x, x - in.x1()), 0);
				int dz = Math.max(Math.max(in.z0() - z, z - in.z1()), 0);
				int d = Math.max(dx, dz);
				if (d >= 1 && d <= 3 && dx + dz <= 4 && !forbidden.test(x, z)) {
					r.mark(x, z, LOOP);
				}
			}
		}
		return true;
	}
}
