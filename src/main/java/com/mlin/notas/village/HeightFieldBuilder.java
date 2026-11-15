package com.mlin.notas.village;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.IntBinaryOperator;

/**
 * Calcula o {@link HeightField} da vila inicial (Vilas 3.0 fase 1, logica pura, determinista: mesma entrada = mesmo
 * campo). Duas entradas:
 * <ul>
 * <li>{@link #forNewVillage}: mundo novo. O plano diretor e criado ja com o anel rural (tamanho final) a partir do chao
 * natural <b>filtrado</b>, e as alturas resolvidas sao gravadas de volta no plano (plano == campo).</li>
 * <li>{@link #forPlan}: vila que ja existe (Kalanros, dados antigos). O plano salvo nao muda (nenhum lote, id ou altura);
 * o que ja esta pronto entra como pino ({@link FieldPins}) e o resto e resolvido em volta.</li>
 * </ul>
 *
 * <h2>Passos</h2>
 * <ol>
 * <li><b>Chao natural</b> {@code N} em toda a grade (meia-largura {@link #EXTENT}), lido do gerador antes de qualquer
 * chunk existir ({@link #coarse}: amostra de 4 em 4 blocos + bilinear, o ruido do Minecraft ja e interpolado nessa
 * escala).</li>
 * <li><b>Chao filtrado</b> {@code I}: media de caixa em 3 passadas de raio {@link #BLUR_RADIUS} (~gaussiana de sigma 16:
 * some o que e menor que um quarteirao e fica a inclinacao geral do relevo) e compressao suave em volta da praca
 * {@code c + A*tanh((L - c)/A)} com amplitude {@link #AMP_INNER} = 10 dentro da muralha de pedra (meia-largura 50) subindo
 * ate {@link #AMP_OUTER} = 16 na muralha externa (75) e nos campos. Por que esses numeros: dentro da muralha cabem ~3
 * quarteiroes do centro ate a borda, cada um a no maximo {@link MasterPlan#MAX_STEP} do vizinho; 10 blocos em 50 e uma
 * subida de 1:5 que ainda parece uma colina de vila (e a muralha de pedra, de 5 de altura, nunca fica mais baixa que a
 * diferenca entre dois aneis); nos arredores e nos campos os quarteiroes sao maiores e mais fundos e 16 deixa o
 * relevo aparecer (vila de encosta) sem montanha dentro do plano. tanh em vez de corte seco: uma montanha vira um
 * morro baixo de topo arredondado, nunca um plato cortado. Nada abaixo de {@code minY} (um acima do mar).</li>
 * <li><b>Plano diretor</b> (so vila nova): {@link MasterPlan#create} + {@link RuralRing#expand} sobre {@code I} sem
 * agua (o campo e o chao; a geracao fecha agua e caverna ate {@link #DEPTH_INSIDE} abaixo).</li>
 * <li><b>Restricoes</b> num grafo de celulas: cada quarteirao, a praca e cada portao sao um no so (plato / rua plana);
 * rua, faixa da muralha externa, borda rural e caminho de lote sao nos por celula. Arestas = diferenca maxima: 4 entre
 * dois platos encostados (2 com a praca), 4 entre platos ligados por ate 6 celulas de rua (como no
 * {@link PlateauGrading}), 1 em todo o resto (rampa de 1 por bloco). Pinos sao nos fixos.</li>
 * <li><b>Solucao</b>: alvo de cada no = mediana de {@code I}; preso ao que os pinos permitem (envelopes dos fixos);
 * altura = media entre o envelope inferior e o superior dos alvos pelas distancias do grafo (Dijkstra). Os dois
 * envelopes cumprem todas as restricoes e a media arredondada para baixo tambem; onde o alvo ja cumpre, fica o alvo.</li>
 * <li><b>Transicao</b> (faixa de {@link #BAND} = 56 em volta do plano): {@code h = N + (V - N) * (1 - g(t))}, com
 * {@code V} = altura da celula do plano mais perto (transformada de distancia exata), {@code t} = distancia/56 e
 * {@code g} = smoothstep misturado com a reta {@code t} quando a diferenca {@code V - N} (suavizada) passa de 37: ate
 * 37 a inclinacao maxima do smoothstep (1,5 * diferenca / 56) fica em 1 bloco por bloco; acima disso a reta espalha a
 * diferenca pela faixa toda (nunca um muro, so uma encosta mais forte que 1). Na borda da faixa {@code h = N}.</li>
 * </ol>
 */
public final class HeightFieldBuilder {
	/** Largura da faixa de transicao em volta do plano diretor. */
	public static final int BAND = 56;
	/** Meia-largura da grade do campo: borda do anel rural + faixa. */
	public static final int EXTENT = RuralRing.EXTENT + BAND;
	/** Raio da media de caixa (3 passadas: sigma ~16). */
	public static final int BLUR_RADIUS = 16;
	/** Amplitude da compressao em volta da praca dentro da muralha de pedra. */
	public static final int AMP_INNER = 10;
	/** Amplitude nos arredores e no anel rural. */
	public static final int AMP_OUTER = 16;
	/** Profundidade fechada (sem caverna nem agua) abaixo da superficie dentro do plano. */
	public static final int DEPTH_INSIDE = 8;
	/** Rua plana de cada lado da linha da muralha nos portoes. */
	public static final int GATE_HALF = 3;
	/** Raio da amostra do chao da praca (mediana). */
	static final int PLAZA_SAMPLE = 6;
	/** Passo da amostra do gerador. */
	public static final int SAMPLE_STEP = 4;
	/** Diferenca (suavizada) entre vila e natural a partir da qual a transicao vira reta. */
	static final double SMOOTH_LIMIT = BAND / 1.5;
	/** Raio da suavizacao da diferenca vila - natural (escolha da curva). */
	static final int DIFF_BLUR = 12;

	private static final int INF = 1 << 28;

	private HeightFieldBuilder() {
	}

	/** Chao natural (Y do topo solido, sem agua) em coordenadas relativas ao centro. */
	@FunctionalInterface
	public interface Natural {
		int ground(int x, int z);
	}

	/** Plano diretor novo e o campo. */
	public record Result(MasterPlan plan, HeightField field) {
	}

	// ---------------------------------------------------------------- entradas

	/**
	 * Amostra o natural na grade inteira chamando {@code sampler} so nos pontos multiplos de {@code step} (relativos) e
	 * interpolando o resto. E o unico caminho de leitura do gerador: a fase 2 troca o sampler por um que ignora a
	 * mistura da vila.
	 */
	public static Natural coarse(IntBinaryOperator sampler, int extent, int step) {
		int lo = -Math.floorDiv(extent + step - 1, step);
		int nodes = 2 * -lo + 1;
		int[] grid = new int[nodes * nodes];
		for (int a = 0; a < nodes; a++) {
			for (int b = 0; b < nodes; b++) {
				grid[a * nodes + b] = sampler.applyAsInt((lo + a) * step, (lo + b) * step);
			}
		}
		int size = 2 * extent + 1;
		int[] out = new int[size * size];
		for (int x = -extent; x <= extent; x++) {
			for (int z = -extent; z <= extent; z++) {
				int ax = Math.floorDiv(x, step) - lo;
				int bz = Math.floorDiv(z, step) - lo;
				double fx = (double) Math.floorMod(x, step) / step;
				double fz = (double) Math.floorMod(z, step) / step;
				int ax1 = Math.min(ax + 1, nodes - 1);
				int bz1 = Math.min(bz + 1, nodes - 1);
				double v = grid[ax * nodes + bz] * (1 - fx) * (1 - fz) + grid[ax1 * nodes + bz] * fx * (1 - fz) + grid[ax * nodes + bz1] * (1 - fx) * fz
						+ grid[ax1 * nodes + bz1] * fx * fz;
				out[(x + extent) * size + (z + extent)] = (int) Math.floor(v + 0.5);
			}
		}
		return (x, z) -> Math.abs(x) <= extent && Math.abs(z) <= extent ? out[(x + extent) * size + (z + extent)]
				: out[(clamp(x, -extent, extent) + extent) * size + (clamp(z, -extent, extent) + extent)];
	}

	/**
	 * Chao da praca de uma vila nova: a media larga do natural (mesma do chao filtrado) no centro, nunca abaixo de
	 * {@code minY}. Com a media (e nao o chao cru do centro) a praca nunca vira um buraco no pe de um morro: ela fica na
	 * altura media do lugar, como os quarteiroes em volta.
	 */
	public static int plazaHeight(Natural natural, int minY) {
		int[] n = grid(natural);
		double[] d = new double[n.length];
		for (int i = 0; i < d.length; i++) {
			d[i] = n[i];
		}
		double[] low = blur(d, size(), BLUR_RADIUS, 3);
		List<Integer> list = new ArrayList<>();
		for (int x = -PLAZA_SAMPLE; x <= PLAZA_SAMPLE; x++) {
			for (int z = -PLAZA_SAMPLE; z <= PLAZA_SAMPLE; z++) {
				if (x * x + z * z <= PLAZA_SAMPLE * PLAZA_SAMPLE) {
					list.add((int) Math.floor(low[fidx(x, z)] + 0.5));
				}
			}
		}
		list.sort(null);
		return Math.max(minY, list.get(list.size() / 2));
	}

	/**
	 * Vila nova: plano diretor do tamanho final (com o anel rural) sobre o chao filtrado e o campo. {@code plazaY} deve
	 * ser {@link #plazaHeight}; {@code protect}: areas (relativas) onde a vila nunca mexe (viram recortes, chao natural).
	 */
	public static Result forNewVillage(long seed, int centerX, int centerZ, int plazaY, Natural natural, int minY, List<VillageState.Rect> protect,
			FieldPins pins) {
		return forNewVillage(seed, centerX, centerZ, plazaY, natural, minY, protect, pins, 0);
	}

	/**
	 * Como {@link #forNewVillage(long, int, int, int, Natural, int, List, FieldPins)} reservando {@code plots} terrenos do
	 * jogador ({@link PlotPlanner}) no plano ja resolvido: o terreno e um lote de quarteirao, entao nasce plano e com
	 * acesso a rua.
	 */
	public static Result forNewVillage(long seed, int centerX, int centerZ, int plazaY, Natural natural, int minY, List<VillageState.Rect> protect,
			FieldPins pins, int plots) {
		int[] n = grid(natural);
		int[] filtered = filtered(n, plazaY, minY);
		Terrain terrain = new Terrain() {
			@Override
			public int ground(int x, int z) {
				return at(filtered, x, z);
			}

			@Override
			public boolean free(int x, int z) {
				return true;
			}
		};
		MasterPlan plan = MasterPlan.create(seed, terrain, plazaY, List.of(), protect, Set.of(), 0);
		plan = RuralRing.expand(plan, terrain);
		HeightField field = solve(plan, centerX, centerZ, n, filtered, minY, pins, true);
		plan.adoptField(field);
		if (plots > 0) {
			PlotPlanner.reserve(plan, plots);
			PlotPlanner.settleFloors(plan);
		}
		return new Result(plan, field);
	}

	/** Vila que ja existe: campo em volta do plano salvo (que nao muda), com o que ja existe pinado. */
	public static HeightField forPlan(MasterPlan plan, int centerX, int centerZ, Natural natural, int minY, FieldPins pins) {
		int[] n = grid(natural);
		int[] filtered = filtered(n, plan.plazaY(), minY);
		return solve(plan, centerX, centerZ, n, filtered, minY, pins, false);
	}

	/** Celula de avenida dentro de {@link #GATE_HALF} da linha de uma muralha (rua plana do portao). */
	public static HeightField.Wall gateWall(MasterPlan plan, int x, int z) {
		if (!plan.ground(x, z) || plan.streetKind(x, z) != StreetPlanner.AVENUE) {
			return null;
		}
		if (Math.abs(STONE.f(x, z)) <= GATE_HALF + 0.5) {
			return HeightField.Wall.STONE;
		}
		if (plan.rural() && Math.abs(OUTER.f(x, z)) <= GATE_HALF + 0.5) {
			return HeightField.Wall.OUTER;
		}
		return null;
	}

	private static final StreetPlanner.Octagon STONE = HeightField.wallOctagon(HeightField.Wall.STONE);
	private static final StreetPlanner.Octagon OUTER = HeightField.wallOctagon(HeightField.Wall.OUTER);

	// ---------------------------------------------------------------- grades

	static int size() {
		return 2 * EXTENT + 1;
	}

	static int fidx(int x, int z) {
		return (x + EXTENT) * size() + (z + EXTENT);
	}

	private static int at(int[] grid, int x, int z) {
		return grid[fidx(clamp(x, -EXTENT, EXTENT), clamp(z, -EXTENT, EXTENT))];
	}

	static int[] grid(Natural natural) {
		int s = size();
		int[] out = new int[s * s];
		for (int x = -EXTENT; x <= EXTENT; x++) {
			for (int z = -EXTENT; z <= EXTENT; z++) {
				out[fidx(x, z)] = natural.ground(x, z);
			}
		}
		return out;
	}

	/** Meia-largura octogonal (mesmo corte de cantos do plano): 50 na muralha de pedra, 75 na externa. */
	static double halfWidth(int x, int z) {
		return Math.max(Math.max(Math.abs(x), Math.abs(z)), (Math.abs(x) + Math.abs(z)) / StreetPlanner.DIAGONAL);
	}

	/** Amplitude da compressao: {@link #AMP_INNER} ate a muralha de pedra, subindo ate {@link #AMP_OUTER} na externa. */
	static double amplitude(int x, int z) {
		double t = (halfWidth(x, z) - VillagePlanner.WALL_HALF) / (RuralRing.WALL - VillagePlanner.WALL_HALF);
		return AMP_INNER + (AMP_OUTER - AMP_INNER) * Math.max(0, Math.min(1, t));
	}

	/** Chao filtrado: media larga + compressao suave em volta de {@code center}, nunca abaixo de {@code minY}. */
	static int[] filtered(int[] natural, int center, int minY) {
		int s = size();
		double[] d = new double[natural.length];
		for (int i = 0; i < d.length; i++) {
			d[i] = natural[i];
		}
		double[] low = blur(d, s, BLUR_RADIUS, 3);
		int[] out = new int[natural.length];
		for (int x = -EXTENT; x <= EXTENT; x++) {
			for (int z = -EXTENT; z <= EXTENT; z++) {
				int i = fidx(x, z);
				double a = amplitude(x, z);
				double v = center + a * Math.tanh((low[i] - center) / a);
				out[i] = Math.max(minY, (int) Math.floor(v + 0.5));
			}
		}
		return out;
	}

	/** Media de caixa separavel, borda repetida, {@code passes} vezes. */
	static double[] blur(double[] in, int size, int r, int passes) {
		double[] cur = in.clone();
		double[] tmp = new double[in.length];
		double w = 2 * r + 1;
		for (int p = 0; p < passes; p++) {
			// ao longo de z
			for (int x = 0; x < size; x++) {
				int row = x * size;
				double sum = 0;
				for (int k = -r; k <= r; k++) {
					sum += cur[row + clamp(k, 0, size - 1)];
				}
				for (int z = 0; z < size; z++) {
					tmp[row + z] = sum / w;
					sum += cur[row + clamp(z + r + 1, 0, size - 1)] - cur[row + clamp(z - r, 0, size - 1)];
				}
			}
			// ao longo de x
			for (int z = 0; z < size; z++) {
				double sum = 0;
				for (int k = -r; k <= r; k++) {
					sum += tmp[clamp(k, 0, size - 1) * size + z];
				}
				for (int x = 0; x < size; x++) {
					cur[x * size + z] = sum / w;
					sum += tmp[clamp(x + r + 1, 0, size - 1) * size + z] - tmp[clamp(x - r, 0, size - 1) * size + z];
				}
			}
		}
		return cur;
	}

	private static int clamp(int v, int lo, int hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	/**
	 * Distancia euclidiana exata ate a celula {@code inside} mais perta (Felzenszwalb-Huttenlocher, separavel);
	 * {@code nearest} (pode ser null) recebe o indice dela. Sem nenhuma celula dentro: infinito.
	 */
	static double[] distance(boolean[] inside, int extent, int[] nearest) {
		int s = 2 * extent + 1;
		long big = (long) s * s * 4L + 1;
		long[] g = new long[s * s];
		int[] nz = new int[s * s];
		for (int x = 0; x < s; x++) {
			int row = x * s;
			int last = -1;
			for (int z = 0; z < s; z++) {
				if (inside[row + z]) {
					last = z;
				}
				g[row + z] = last < 0 ? big : z - last;
				nz[row + z] = last;
			}
			last = -1;
			for (int z = s - 1; z >= 0; z--) {
				if (inside[row + z]) {
					last = z;
				}
				if (last >= 0 && last - z < g[row + z]) {
					g[row + z] = last - z;
					nz[row + z] = last;
				}
			}
		}
		double[] out = new double[s * s];
		int[] v = new int[s];
		double[] bound = new double[s + 1];
		double[] f = new double[s];
		for (int z = 0; z < s; z++) {
			for (int x = 0; x < s; x++) {
				long gv = g[x * s + z];
				f[x] = gv >= big ? Double.POSITIVE_INFINITY : (double) gv * gv;
			}
			int k = -1;
			for (int q = 0; q < s; q++) {
				if (f[q] == Double.POSITIVE_INFINITY) {
					continue;
				}
				double cut = 0;
				while (k >= 0) {
					int p = v[k];
					cut = ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * (q - p));
					if (cut <= bound[k]) {
						k--;
					} else {
						break;
					}
				}
				if (k < 0) {
					k = 0;
					v[0] = q;
					bound[0] = Double.NEGATIVE_INFINITY;
				} else {
					k++;
					v[k] = q;
					bound[k] = cut;
				}
				bound[k + 1] = Double.POSITIVE_INFINITY;
			}
			if (k < 0) {
				for (int x = 0; x < s; x++) {
					out[x * s + z] = Double.POSITIVE_INFINITY;
					if (nearest != null) {
						nearest[x * s + z] = -1;
					}
				}
				continue;
			}
			int kk = 0;
			for (int q = 0; q < s; q++) {
				while (bound[kk + 1] < q) {
					kk++;
				}
				int p = v[kk];
				double d2 = (double) (q - p) * (q - p) + f[p];
				out[q * s + z] = Math.sqrt(d2);
				if (nearest != null) {
					nearest[q * s + z] = p * s + nz[p * s + z];
				}
			}
		}
		return out;
	}

	/** Peso da vila pela distancia ate o plano: 1 dentro, smoothstep ate 0 em {@code band}. */
	static double blend(double dist, int band) {
		if (dist <= 0) {
			return 1;
		}
		double t = dist / band;
		if (t >= 1) {
			return 0;
		}
		return 1 - t * t * (3 - 2 * t);
	}

	/** Curva da transicao: smoothstep, misturado com a reta quando a diferenca passa de {@link #SMOOTH_LIMIT}. */
	static double curve(double t, double diff) {
		double a = Math.abs(diff) <= SMOOTH_LIMIT ? 0 : Math.min(1, (1.5 * Math.abs(diff) - BAND) / (0.5 * Math.abs(diff)));
		double smooth = t * t * (3 - 2 * t);
		return (1 - a) * smooth + a * t;
	}

	// ---------------------------------------------------------------- solucao

	private static final int K_NATURAL = HeightField.Kind.NATURAL.ordinal();
	private static final int K_BAND = HeightField.Kind.BAND.ordinal();
	private static final int K_PLAZA = HeightField.Kind.PLAZA.ordinal();
	private static final int K_BLOCK = HeightField.Kind.BLOCK.ordinal();
	private static final int K_STREET = HeightField.Kind.STREET.ordinal();
	private static final int K_GATE_STONE = HeightField.Kind.GATE_STONE.ordinal();
	private static final int K_GATE_OUTER = HeightField.Kind.GATE_OUTER.ordinal();
	private static final int K_FREE = HeightField.Kind.FREE.ordinal();
	private static final int K_WATER = HeightField.Kind.WATER.ordinal();
	private static final int K_KEEP = HeightField.Kind.KEEP.ordinal();
	private static final int K_PATH = HeightField.Kind.PATH.ordinal();

	private static boolean walkway(int k) {
		return k == K_STREET || k == K_GATE_STONE || k == K_GATE_OUTER;
	}

	static HeightField solve(MasterPlan plan, int centerX, int centerZ, int[] n, int[] filtered, int minY, FieldPins pins, boolean writeBack) {
		int s = size();
		int cells = s * s;
		int[] kind = new int[cells];
		int[] group = new int[cells];
		Arrays.fill(group, -1);
		int blocks = plan.blocks().size();
		int plazaGroup = blocks;
		for (int x = -EXTENT; x <= EXTENT; x++) {
			for (int z = -EXTENT; z <= EXTENT; z++) {
				int i = fidx(x, z);
				int code = plan.code(x, z);
				if (code == MasterPlan.OUT) {
					kind[i] = K_NATURAL;
				} else if (code >= MasterPlan.BLOCK) {
					kind[i] = K_BLOCK;
					group[i] = code - MasterPlan.BLOCK;
				} else if (code == MasterPlan.PLAZA) {
					kind[i] = K_PLAZA;
					group[i] = plazaGroup;
				} else if (code >= MasterPlan.STREET && code < MasterPlan.BRIDGE) {
					HeightField.Wall gate = gateWall(plan, x, z);
					kind[i] = gate == null ? K_STREET : gate == HeightField.Wall.STONE ? K_GATE_STONE : K_GATE_OUTER;
				} else if (code >= MasterPlan.BRIDGE || code == MasterPlan.LAKE) {
					kind[i] = K_WATER;
				} else if (code == MasterPlan.CARVED) {
					kind[i] = K_KEEP;
				} else {
					kind[i] = K_FREE;
				}
			}
		}
		// portoes: cada trecho ligado de avenida em volta da linha da muralha e um no so (rua plana)
		int groups = blocks + 1;
		for (int start = 0; start < cells; start++) {
			if ((kind[start] != K_GATE_STONE && kind[start] != K_GATE_OUTER) || group[start] >= 0) {
				continue;
			}
			int id = groups++;
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(start);
			group[start] = id;
			while (!queue.isEmpty()) {
				int i = queue.poll();
				int x = i / s - EXTENT;
				int z = i % s - EXTENT;
				for (Facing f : Facing.values()) {
					int nx = x + f.dx;
					int nz = z + f.dz;
					if (Math.abs(nx) > EXTENT || Math.abs(nz) > EXTENT) {
						continue;
					}
					int j = fidx(nx, nz);
					if (kind[j] == kind[start] && group[j] < 0) {
						group[j] = id;
						queue.add(j);
					}
				}
			}
		}
		// pinos
		int[] pin = new int[cells];
		Arrays.fill(pin, Integer.MIN_VALUE);
		for (Map.Entry<Long, Integer> e : pins.all().entrySet()) {
			int x = VillageLayout.colX(e.getKey());
			int z = VillageLayout.colZ(e.getKey());
			if (Math.abs(x) <= EXTENT && Math.abs(z) <= EXTENT) {
				pin[fidx(x, z)] = e.getValue();
			}
		}
		for (int i = 0; i < cells; i++) {
			if (pin[i] != Integer.MIN_VALUE && (kind[i] == K_KEEP || kind[i] == K_WATER || kind[i] == K_NATURAL)) {
				// recorte com pino (area de jogador numa vila antiga): entra nas restricoes com a altura de agora
				kind[i] = K_FREE;
			}
		}
		// terreno do jogador (fase 5): o quarteirao em volta deixa de ser plato (celulas soltas) para a rampa de 1 por bloco
		// poder subir/descer do terreno ate a borda do quarteirao
		if (!pins.plots().isEmpty()) {
			java.util.Set<Integer> release = new java.util.HashSet<>();
			for (FieldPins.Plot plot : pins.plots()) {
				VillageState.Rect pr = plot.rect();
				for (int x = Math.max(-EXTENT, pr.x0() - 2); x <= Math.min(EXTENT, pr.x1() + 2); x++) {
					for (int z = Math.max(-EXTENT, pr.z0() - 2); z <= Math.min(EXTENT, pr.z1() + 2); z++) {
						int g = group[fidx(x, z)];
						if (g >= 0 && g < blocks) {
							release.add(g);
						}
					}
				}
			}
			for (int i = 0; i < cells; i++) {
				if (group[i] >= 0 && release.contains(group[i])) {
					group[i] = -1;
				}
			}
		}
		// grupos com pino: inteiro e na mesma altura = grupo fixo; senao as celulas pinadas saem do grupo
		int[] fixedGroup = new int[groups];
		Arrays.fill(fixedGroup, Integer.MIN_VALUE);
		fixedGroup[plazaGroup] = plan.plazaY();
		int[] groupCells = new int[groups];
		int[] groupPinned = new int[groups];
		int[] groupPinValue = new int[groups];
		boolean[] groupMixed = new boolean[groups];
		Arrays.fill(groupPinValue, Integer.MIN_VALUE);
		for (int i = 0; i < cells; i++) {
			int g = group[i];
			if (g < 0) {
				continue;
			}
			groupCells[g]++;
			if (pin[i] != Integer.MIN_VALUE) {
				groupPinned[g]++;
				if (groupPinValue[g] == Integer.MIN_VALUE) {
					groupPinValue[g] = pin[i];
				} else if (groupPinValue[g] != pin[i]) {
					groupMixed[g] = true;
				}
			}
		}
		for (int g = 0; g < groups; g++) {
			if (groupPinned[g] > 0 && groupPinned[g] == groupCells[g] && !groupMixed[g] && (g != plazaGroup || groupPinValue[g] == plan.plazaY())) {
				fixedGroup[g] = groupPinValue[g];
			}
		}
		boolean[] split = new boolean[cells];
		for (int i = 0; i < cells; i++) {
			int g = group[i];
			if (g >= 0 && pin[i] != Integer.MIN_VALUE && fixedGroup[g] == Integer.MIN_VALUE) {
				split[i] = true;
				group[i] = -1;
			} else if (g >= 0 && pin[i] != Integer.MIN_VALUE && fixedGroup[g] != pin[i]) {
				// praca pinada noutra altura: o pino manda na celula
				split[i] = true;
				group[i] = -1;
			}
		}
		// caminhos dos lotes do jogador ate a rua
		for (FieldPins.Plot plot : pins.plots()) {
			path(plot, kind, group, pin, split);
		}
		// nos
		int[] node = new int[cells];
		Arrays.fill(node, -1);
		int nodes = groups;
		for (int i = 0; i < cells; i++) {
			if (group[i] >= 0) {
				node[i] = group[i];
			} else if (kind[i] != K_NATURAL && kind[i] != K_WATER && kind[i] != K_KEEP) {
				node[i] = nodes++;
			}
		}
		int[] fixed = new int[nodes];
		Arrays.fill(fixed, Integer.MIN_VALUE);
		System.arraycopy(fixedGroup, 0, fixed, 0, groups);
		long[] sum = new long[nodes];
		List<List<Integer>> values = new ArrayList<>();
		for (int g = 0; g < groups; g++) {
			values.add(new ArrayList<>());
		}
		int[] desired = new int[nodes];
		for (int i = 0; i < cells; i++) {
			int a = node[i];
			if (a < 0) {
				continue;
			}
			if (a < groups) {
				values.get(a).add(filtered[i]);
			} else {
				desired[a] = filtered[i];
				if (pin[i] != Integer.MIN_VALUE) {
					fixed[a] = pin[i];
				}
			}
		}
		for (int g = 0; g < groups; g++) {
			List<Integer> v = values.get(g);
			if (v.isEmpty()) {
				desired[g] = plan.plazaY();
				continue;
			}
			v.sort(null);
			desired[g] = v.get(v.size() / 2);
		}
		// arestas
		Graph graph = new Graph(nodes);
		Map<Long, Integer> pairLimit = new HashMap<>();
		for (int x = -EXTENT; x <= EXTENT; x++) {
			for (int z = -EXTENT; z <= EXTENT; z++) {
				int i = fidx(x, z);
				if (node[i] < 0) {
					continue;
				}
				for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
					int nx = x + d[0];
					int nz = z + d[1];
					if (nx > EXTENT || nz > EXTENT) {
						continue;
					}
					int j = fidx(nx, nz);
					if (node[j] < 0 || node[j] == node[i]) {
						continue;
					}
					int w = weight(i, j, kind, group, pin, plazaGroup);
					if (node[i] < groups && node[j] < groups) {
						limit(pairLimit, node[i], node[j], w);
					} else {
						graph.add(node[i], node[j], w);
					}
				}
			}
		}
		streetLimits(kind, group, plazaGroup, blocks, pairLimit);
		pairLimit.forEach((k, w) -> graph.add((int) (k >> 32), (int) (long) k, w));
		graph.build();
		// envelopes dos fixos, alvos presos, envelopes dos alvos
		int[] fixLow = new int[nodes];
		int[] fixHigh = new int[nodes];
		for (int a = 0; a < nodes; a++) {
			fixLow[a] = fixed[a] == Integer.MIN_VALUE ? -INF : fixed[a];
			fixHigh[a] = fixed[a] == Integer.MIN_VALUE ? INF : fixed[a];
		}
		fixLow = graph.propagate(fixLow, true);
		fixHigh = graph.propagate(fixHigh, false);
		int[] target = new int[nodes];
		for (int a = 0; a < nodes; a++) {
			if (fixed[a] != Integer.MIN_VALUE) {
				target[a] = fixed[a];
			} else if (fixLow[a] > fixHigh[a]) {
				target[a] = Math.floorDiv(fixLow[a] + fixHigh[a], 2);
			} else {
				target[a] = Math.max(fixLow[a], Math.min(fixHigh[a], desired[a]));
			}
		}
		int[] low = graph.propagate(target.clone(), true);
		int[] high = graph.propagate(target.clone(), false);
		int[] out = new int[nodes];
		for (int a = 0; a < nodes; a++) {
			out[a] = fixed[a] != Integer.MIN_VALUE ? fixed[a] : Math.floorDiv(low[a] + high[a], 2);
		}
		// alturas dentro do plano
		short[] heights = new short[cells];
		boolean[] inside = new boolean[cells];
		for (int i = 0; i < cells; i++) {
			if (kind[i] == K_NATURAL) {
				continue;
			}
			inside[i] = true;
			heights[i] = (short) (node[i] >= 0 ? out[node[i]] : n[i]);
		}
		if (writeBack) {
			for (MasterPlan.Block b : plan.blocks()) {
				if (b.id() < blocks && values.get(b.id()).size() > 0) {
					b.setTarget(out[b.id()]);
				}
			}
			int pe = plan.extent();
			for (int x = -pe; x <= pe; x++) {
				for (int z = -pe; z <= pe; z++) {
					if (plan.ground(x, z)) {
						plan.heights[plan.idx(x, z)] = heights[fidx(x, z)];
					}
				}
			}
		}
		// transicao
		int[] nearest = new int[cells];
		double[] dist = distance(inside, EXTENT, nearest);
		double[] diff = new double[cells];
		for (int i = 0; i < cells; i++) {
			int v = nearest[i] < 0 ? n[i] : heights[nearest[i]];
			diff[i] = v - n[i];
		}
		double[] smoothDiff = blur(diff, s, DIFF_BLUR, 3);
		double[] real = new double[cells];
		for (int i = 0; i < cells; i++) {
			if (inside[i]) {
				real[i] = heights[i];
				continue;
			}
			double t = dist[i] / BAND;
			if (nearest[i] < 0 || t >= 1) {
				heights[i] = (short) n[i];
				real[i] = n[i];
				kind[i] = K_NATURAL;
				continue;
			}
			kind[i] = K_BAND;
			double v = heights[nearest[i]];
			real[i] = n[i] + (v - n[i]) * (1 - curve(t, smoothDiff[i]));
		}
		quantizeBand(heights, real, n, kind, pin);
		byte[] flags = new byte[cells];
		for (int i = 0; i < cells; i++) {
			flags[i] = (byte) (kind[i] | (pin[i] != Integer.MIN_VALUE ? HeightField.pinnedBit() : 0));
		}
		return new HeightField(centerX, centerZ, EXTENT, plan.extent(), BAND, plan.plazaY(), minY, heights, flags);
	}

	/** Diferenca maxima entre duas celulas vizinhas de nos diferentes. */
	private static int weight(int i, int j, int[] kind, int[] group, int[] pin, int plazaGroup) {
		boolean pi = (kind[i] == K_BLOCK || kind[i] == K_PLAZA) && group[i] >= 0;
		boolean pj = (kind[j] == K_BLOCK || kind[j] == K_PLAZA) && group[j] >= 0;
		if (pi && pj) {
			return group[i] == plazaGroup || group[j] == plazaGroup ? 2 : MasterPlan.MAX_STEP;
		}
		if (kind[i] == K_PATH || kind[j] == K_PATH) {
			int other = kind[i] == K_PATH ? j : i;
			int k = kind[other];
			return k == K_PATH || walkway(k) || k == K_PLAZA || pin[other] != Integer.MIN_VALUE ? 1 : MasterPlan.MAX_STEP;
		}
		return 1;
	}

	private static void limit(Map<Long, Integer> limits, int a, int b, int max) {
		long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
		limits.merge(key, max, Math::min);
	}

	/**
	 * Platos ligados por pouca rua (ate 6 celulas entre as frentes) ficam a no maximo {@link MasterPlan#MAX_STEP} (a rampa
	 * de 1 por bloco sozinha deixaria ate 8 numa avenida): quarteiroes vizinhos nunca viram um penhasco.
	 */
	private static void streetLimits(int[] kind, int[] group, int plazaGroup, int blocks, Map<Long, Integer> limits) {
		int s = size();
		int cells = s * s;
		int[][] owners = new int[cells][];
		List<List<Integer>> fronts = new ArrayList<>();
		for (int g = 0; g <= blocks; g++) {
			fronts.add(new ArrayList<>());
		}
		for (int i = 0; i < cells; i++) {
			if (!walkway(kind[i])) {
				continue;
			}
			int x = i / s - EXTENT;
			int z = i % s - EXTENT;
			int[] list = new int[0];
			for (Facing f : Facing.values()) {
				int nx = x + f.dx;
				int nz = z + f.dz;
				if (Math.abs(nx) > EXTENT || Math.abs(nz) > EXTENT) {
					continue;
				}
				int j = fidx(nx, nz);
				int g = group[j];
				if (g >= 0 && g <= blocks && (kind[j] == K_BLOCK || kind[j] == K_PLAZA)) {
					boolean has = false;
					for (int o : list) {
						has |= o == g;
					}
					if (!has) {
						list = Arrays.copyOf(list, list.length + 1);
						list[list.length - 1] = g;
						fronts.get(g).add(i);
					}
				}
			}
			owners[i] = list;
		}
		int[] depth = new int[cells];
		Arrays.fill(depth, -1);
		List<Integer> touched = new ArrayList<>();
		for (int o = 0; o <= blocks; o++) {
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			for (int i : fronts.get(o)) {
				if (depth[i] < 0) {
					depth[i] = 0;
					touched.add(i);
					queue.add(i);
				}
			}
			while (!queue.isEmpty()) {
				int i = queue.poll();
				int d = depth[i];
				for (int other : owners[i]) {
					if (other != o && d >= 3) {
						limit(limits, o, other, Math.min(MasterPlan.MAX_STEP, d + 2));
					}
				}
				if (d >= 6) {
					continue;
				}
				int x = i / s - EXTENT;
				int z = i % s - EXTENT;
				for (Facing f : Facing.values()) {
					int nx = x + f.dx;
					int nz = z + f.dz;
					if (Math.abs(nx) > EXTENT || Math.abs(nz) > EXTENT) {
						continue;
					}
					int j = fidx(nx, nz);
					if (owners[j] != null && depth[j] < 0) {
						depth[j] = d + 1;
						touched.add(j);
						queue.add(j);
					}
				}
			}
			for (int i : touched) {
				depth[i] = -1;
			}
			touched.clear();
		}
	}

	/**
	 * Caminho do lote do jogador ate a rua mais perto: busca em largura a partir do lote pelas celulas do plano (sem
	 * agua, recorte ou outro pino); as celulas do caminho viram {@link HeightField.Kind#PATH} (fora do plato do
	 * quarteirao, rampa de 1 entre elas e ate a rua). Lote encostado na rua nao precisa de caminho.
	 */
	private static void path(FieldPins.Plot plot, int[] kind, int[] group, int[] pin, boolean[] split) {
		int s = size();
		VillageState.Rect r = plot.rect();
		int[] parent = new int[s * s];
		Arrays.fill(parent, -2);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				if (Math.abs(x) <= EXTENT && Math.abs(z) <= EXTENT) {
					int i = fidx(x, z);
					parent[i] = -1;
					queue.add(i);
				}
			}
		}
		int found = -1;
		while (!queue.isEmpty() && found < 0) {
			int i = queue.poll();
			int x = i / s - EXTENT;
			int z = i % s - EXTENT;
			for (Facing f : Facing.values()) {
				int nx = x + f.dx;
				int nz = z + f.dz;
				if (Math.abs(nx) > EXTENT || Math.abs(nz) > EXTENT) {
					continue;
				}
				int j = fidx(nx, nz);
				if (parent[j] != -2) {
					continue;
				}
				int k = kind[j];
				if (walkway(k) || k == K_PLAZA) {
					found = i;
					break;
				}
				if ((k == K_BLOCK || k == K_FREE || k == K_PATH || k == K_BAND || k == K_NATURAL) && pin[j] == Integer.MIN_VALUE) {
					parent[j] = i;
					queue.add(j);
				}
			}
		}
		for (int i = found; i >= 0 && parent[i] != -1; i = parent[i]) {
			kind[i] = K_PATH;
			group[i] = -1;
			split[i] = false;
		}
	}

	/**
	 * Arredonda a transicao sem criar degrau: arredondar cada coluna sozinha somaria a escada do chao natural (degraus de
	 * 1 a cada poucos blocos) com a rampa da mistura e daria degraus de 2 numa encosta de inclinacao menor que 1. Aqui o
	 * limite de cada par vizinho e max(1, degrau natural, inclinacao da mistura suavizada (raio 3) arredondada para cima com folga de 0,1) e a altura inteira
	 * e a do meio entre os envelopes de baixo e de cima do valor arredondado, com o plano, o chao natural de fora e os
	 * pinos da faixa fixos (pino da faixa: exato, rampa de 1 ate ele).
	 */
	private static void quantizeBand(short[] heights, double[] real, int[] n, int[] kind, int[] pin) {
		// inclinacao "de verdade" (sem a escada do natural): a mistura levemente suavizada
		double[] slope = blur(real, 2 * EXTENT + 1, 3, 1);
		int s = size();
		int cells = s * s;
		Graph graph = new Graph(cells);
		for (int x = -EXTENT; x <= EXTENT; x++) {
			for (int z = -EXTENT; z <= EXTENT; z++) {
				int i = fidx(x, z);
				for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
					if (x + d[0] > EXTENT || z + d[1] > EXTENT) {
						continue;
					}
					int j = fidx(x + d[0], z + d[1]);
					if (kind[i] != K_BAND && kind[j] != K_BAND) {
						continue;
					}
					int limit = Math.max(1, Math.abs(n[i] - n[j]));
					limit = Math.max(limit, (int) Math.ceil(Math.abs(slope[i] - slope[j]) - 0.1));
					graph.add(i, j, limit);
				}
			}
		}
		graph.build();
		int[] fixLow = new int[cells];
		int[] fixHigh = new int[cells];
		int[] desired = new int[cells];
		boolean[] fixed = new boolean[cells];
		for (int i = 0; i < cells; i++) {
			boolean band = kind[i] == K_BAND;
			fixed[i] = !band || pin[i] != Integer.MIN_VALUE;
			int v = !band ? heights[i] : pin[i] != Integer.MIN_VALUE ? pin[i] : (int) Math.floor(real[i] + 0.5);
			desired[i] = v;
			fixLow[i] = fixed[i] ? v : -INF;
			fixHigh[i] = fixed[i] ? v : INF;
		}
		fixLow = graph.propagate(fixLow, true);
		fixHigh = graph.propagate(fixHigh, false);
		for (int i = 0; i < cells; i++) {
			if (!fixed[i]) {
				desired[i] = fixLow[i] > fixHigh[i] ? Math.floorDiv(fixLow[i] + fixHigh[i], 2) : Math.max(fixLow[i], Math.min(fixHigh[i], desired[i]));
			}
		}
		int[] low = graph.propagate(desired.clone(), true);
		int[] high = graph.propagate(desired.clone(), false);
		for (int i = 0; i < cells; i++) {
			if (kind[i] == K_BAND) {
				heights[i] = (short) (fixed[i] ? desired[i] : Math.floorDiv(low[i] + high[i], 2));
			}
		}
	}

	/** Grafo com pesos inteiros pequenos (listas compactas). */
	static final class Graph {
		private final int nodes;
		private int[] ea = new int[1024];
		private int[] eb = new int[1024];
		private int[] ew = new int[1024];
		private int edges;
		private int[] start;
		private int[] to;
		private int[] w;

		Graph(int nodes) {
			this.nodes = nodes;
		}

		void add(int a, int b, int weight) {
			if (edges == ea.length) {
				ea = Arrays.copyOf(ea, edges * 2);
				eb = Arrays.copyOf(eb, edges * 2);
				ew = Arrays.copyOf(ew, edges * 2);
			}
			ea[edges] = a;
			eb[edges] = b;
			ew[edges] = weight;
			edges++;
		}

		void build() {
			start = new int[nodes + 1];
			for (int e = 0; e < edges; e++) {
				start[ea[e] + 1]++;
				start[eb[e] + 1]++;
			}
			for (int a = 0; a < nodes; a++) {
				start[a + 1] += start[a];
			}
			to = new int[edges * 2];
			w = new int[edges * 2];
			int[] fill = Arrays.copyOf(start, nodes);
			for (int e = 0; e < edges; e++) {
				to[fill[ea[e]]] = eb[e];
				w[fill[ea[e]]++] = ew[e];
				to[fill[eb[e]]] = ea[e];
				w[fill[eb[e]]++] = ew[e];
			}
		}

		/**
		 * {@code max}: maior funcao abaixo de {@code bound} com |f(a) - f(b)| <= peso (propaga para baixo a partir dos
		 * maiores); senao a menor acima. Valores +-{@link #INF} sao "sem limite".
		 */
		int[] propagate(int[] bound, boolean max) {
			int[] out = bound.clone();
			PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> max ? Long.compare(b[1], a[1]) : Long.compare(a[1], b[1]));
			for (int a = 0; a < nodes; a++) {
				if (Math.abs(out[a]) < INF / 2) {
					queue.add(new long[] {a, out[a]});
				}
			}
			while (!queue.isEmpty()) {
				long[] top = queue.poll();
				int a = (int) top[0];
				if (top[1] != out[a]) {
					continue;
				}
				for (int e = start[a]; e < start[a + 1]; e++) {
					int b = to[e];
					int next = max ? out[a] - w[e] : out[a] + w[e];
					if (max ? next > out[b] : next < out[b]) {
						out[b] = next;
						queue.add(new long[] {b, next});
					}
				}
			}
			return out;
		}
	}
}
