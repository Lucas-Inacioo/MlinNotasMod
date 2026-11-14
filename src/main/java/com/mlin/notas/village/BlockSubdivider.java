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
			case RuralRing.RING -> new SizeClass[] {SizeClass.L, SizeClass.M};
			default -> new SizeClass[] {};
		};
	}

	static SizeClass[] mix(int ring) {
		return switch (ring) {
			case 1 -> new SizeClass[] {SizeClass.S};
			case 2 -> new SizeClass[] {SizeClass.M, SizeClass.S, SizeClass.S, SizeClass.L};
			case 3 -> new SizeClass[] {SizeClass.S, SizeClass.S, SizeClass.S, SizeClass.M};
			case 5 -> new SizeClass[] {SizeClass.L, SizeClass.M, SizeClass.M, SizeClass.L};
			case 6 -> new SizeClass[] {SizeClass.WL};
			// anel rural: campos e galpoes medios, um grande de vez em quando (vinicola), um pequeno (apiario)
			case RuralRing.RING -> new SizeClass[] {SizeClass.M, SizeClass.M, SizeClass.L, SizeClass.S, SizeClass.M};
			default -> new SizeClass[] {};
		};
	}

	/** Distancia maxima da porta ate a rua (o acesso e um caminho de ate 4 celulas) e da metade da frente. */
	static final int ACCESS_MAX = 4;
	static final int NEAR_MAX = 6;

	private record Candidate(int x0, int z0, int w, int d, Facing front, double score) {
	}

	/** Subdivisao densa de um plano novo (todos os quarteiroes). */
	static List<MasterPlan.Parcel> subdivide(MasterPlan plan, long seed) {
		int extent = plan.extent();
		int size = 2 * extent + 1;
		boolean[] taken = new boolean[size * size];
		List<MasterPlan.Parcel> out = new ArrayList<>();
		for (MasterPlan.Block block : plan.blocks()) {
			fillBlock(plan, block, seed, taken, out, 0);
		}
		return out;
	}

	/**
	 * Lotes dos quarteiroes novos (id a partir de {@code firstBlock}) de um plano que ganhou o anel rural: os lotes que
	 * ja existem ficam como estao e os novos tem ids depois deles.
	 */
	static List<MasterPlan.Parcel> subdivideFrom(MasterPlan plan, long seed, int firstBlock) {
		boolean[] taken = emptyTaken(plan);
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (!p.retired()) {
				occupy(plan, taken, p.rect());
			}
		}
		List<MasterPlan.Parcel> out = new ArrayList<>();
		for (MasterPlan.Block block : plan.blocks()) {
			if (block.id() >= firstBlock) {
				fillBlock(plan, block, seed, taken, out, plan.parcels().size());
			}
		}
		return out;
	}

	/** O que {@link #densify} mudou: lotes aposentados (com o retangulo antigo, para tirar as estacas) e quantos novos. */
	record Densified(List<MasterPlan.Parcel> retired, int added) {
	}

	/**
	 * Refaz a subdivisao de um plano salvo antes da versao densa. Quarteirao sem nenhum lote reservado (obra em
	 * andamento, pronta ou lote de jogador) e subdividido de novo (os lotes velhos viram {@link MasterPlan.Parcel#retired
	 * aposentados}); quarteirao com lote reservado so ganha lotes novos no espaco que sobrou, sem tocar nos antigos.
	 * Deterministico pela semente do plano.
	 */
	static Densified densify(MasterPlan plan, java.util.Set<Integer> reserved, java.util.Collection<int[]> keepFree) {
		int extent = plan.extent();
		int size = 2 * extent + 1;
		boolean[] taken = new boolean[size * size];
		List<MasterPlan.Parcel> retired = new ArrayList<>();
		for (int[] c : keepFree) {
			mark(plan, taken, c[0], c[1], 1, 1);
		}
		int added = 0;
		for (MasterPlan.Block block : plan.blocks()) {
			List<MasterPlan.Parcel> mine = new ArrayList<>();
			boolean used = false;
			for (MasterPlan.Parcel p : plan.parcels()) {
				if (p.block() == block.id() && !p.retired()) {
					mine.add(p);
					used |= reserved.contains(p.id()) || p.player();
				}
			}
			if (!used) {
				for (MasterPlan.Parcel p : mine) {
					retired.add(p);
					plan.retireParcel(p.id());
				}
			} else {
				for (MasterPlan.Parcel p : mine) {
					VillageState.Rect r = p.rect();
					mark(plan, taken, r.x0(), r.z0(), r.x1() - r.x0() + 1, r.z1() - r.z0() + 1);
				}
			}
			List<MasterPlan.Parcel> out = new ArrayList<>();
			fillBlock(plan, block, plan.seed(), taken, out, plan.parcels().size());
			for (MasterPlan.Parcel p : out) {
				plan.addParcel(p);
				added++;
			}
		}
		plan.setVersion(MasterPlan.VERSION);
		return new Densified(retired, added);
	}

	private static void fillBlock(MasterPlan plan, MasterPlan.Block block, long seed, boolean[] taken, List<MasterPlan.Parcel> out, int firstId) {
		fillBlock(plan, block, seed, taken, out, firstId, true);
	}

	/** Preenche o quarteirao com lotes novos (ids a partir de {@code firstId + out.size()}); {@code taken} ja marca o que existe. */
	private static void fillBlock(MasterPlan plan, MasterPlan.Block block, long seed, boolean[] taken, List<MasterPlan.Parcel> out, int firstId,
			boolean cornerLots) {
		Zone zone = block.zone();
		if (zone == null || block.cells() < 30) {
			return;
		}
		Random random = new Random(seed ^ 0xB10C5L ^ (block.id() * 0x9E3779B97F4A7C15L));
		int extent = plan.extent();
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
		for (SizeClass cls : cornerLots ? corners(block.ring()) : new SizeClass[0]) {
			for (int n = 0; n < 2; n++) {
				Candidate c = best(plan, sat, cls, true);
				if (c == null) {
					break;
				}
				out.add(take(plan, c, cls, zone, block, firstId + out.size(), taken, sat));
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
			out.add(take(plan, c, used, zone, block, firstId + out.size(), taken, sat));
			turn++;
		}
		// sobras (cantos chanfrados, esquinas largas): lotes minusculos
		if (block.ring() != 6 && block.ring() != RuralRing.RING) {
			for (int guard = 0; guard < 32; guard++) {
				Candidate c = best(plan, sat, SizeClass.T, false);
				if (c == null) {
					break;
				}
				out.add(take(plan, c, SizeClass.T, zone, block, firstId + out.size(), taken, sat));
			}
		}
	}

	// ---------------------------------------------------------------- lote sob demanda

	/** Lugar achado para um lote novo dentro de um quarteirao (ainda nao aplicado ao plano). */
	record Carving(MasterPlan.Block block, SizeClass size, VillageState.Rect rect, Facing front, double score) {
	}

	/** Retangulo que envolve cada quarteirao ({x0, z0, x1, z1} por id), numa passada so pela grade. */
	static int[][] blockBounds(MasterPlan plan) {
		int[][] out = new int[plan.blocks().size()][];
		int extent = plan.extent();
		for (int x = -extent; x <= extent; x++) {
			for (int z = -extent; z <= extent; z++) {
				int id = plan.code(x, z) - MasterPlan.BLOCK;
				if (id < 0 || id >= out.length) {
					continue;
				}
				int[] b = out[id];
				if (b == null) {
					out[id] = new int[] {x, z, x, z};
				} else {
					b[0] = Math.min(b[0], x);
					b[1] = Math.min(b[1], z);
					b[2] = Math.max(b[2], x);
					b[3] = Math.max(b[3], z);
				}
			}
		}
		return out;
	}

	/** Grade de ocupacao vazia do tamanho do plano, para {@link #occupy}. */
	static boolean[] emptyTaken(MasterPlan plan) {
		int size = 2 * plan.extent() + 1;
		return new boolean[size * size];
	}

	/** Marca o retangulo (com 1 de folga em volta) como ocupado. */
	static void occupy(MasterPlan plan, boolean[] taken, VillageState.Rect r) {
		mark(plan, taken, r.x0(), r.z0(), r.x1() - r.x0() + 1, r.z1() - r.z0() + 1);
	}

	/**
	 * Melhor lugar para um lote da classe {@code cls} no espaco livre do quarteirao ({@code taken} marca o que nao pode
	 * mudar: lotes reservados/prontos/de jogador, caminhos de porta e postes feitos). Lotes do plano ainda livres NAO
	 * entram em {@code taken}: o lugar pode juntar dois lotes pequenos vizinhos ou partir um grande. null = nao cabe.
	 */
	static Carving carve(MasterPlan plan, MasterPlan.Block block, int[] bounds, SizeClass cls, boolean[] taken) {
		if (bounds == null || block.zone() == null) {
			return null;
		}
		Sat sat = new Sat(plan, block.id(), taken, bounds[0], bounds[1], bounds[2], bounds[3]);
		Candidate c = best(plan, sat, cls, false, true);
		if (c == null) {
			return null;
		}
		c = grow(sat, c);
		return new Carving(block, cls, new VillageState.Rect(c.x0, c.z0, c.x0 + c.w - 1, c.z0 + c.d - 1), c.front, c.score);
	}

	/**
	 * Aplica o recorte: os lotes livres do quarteirao que o lote novo cobre (com a folga de 1) sao aposentados, o lote
	 * novo entra no plano e a sobra do quarteirao e subdividida de novo (sem lotes de esquina; quem ja existe fica).
	 * {@code keep} marca o que nao pode mudar (o mesmo {@code taken} do {@link #carve}). Devolve o lote novo; os
	 * aposentados vao para {@code retired}.
	 */
	static MasterPlan.Parcel applyCarving(MasterPlan plan, Carving carving, boolean[] keep, java.util.Set<Integer> fixedParcels,
			List<MasterPlan.Parcel> retired) {
		MasterPlan.Block block = carving.block();
		for (MasterPlan.Parcel p : new ArrayList<>(plan.parcels())) {
			if (p.block() == block.id() && !p.retired() && !fixedParcels.contains(p.id()) && !p.player() && p.rect().overlaps(carving.rect(), 1)) {
				retired.add(p);
				plan.retireParcel(p.id());
			}
		}
		MasterPlan.Parcel made = new MasterPlan.Parcel(plan.parcels().size(), block.id(), carving.rect(), carving.front(), carving.size(), block.zone(),
				false);
		plan.addParcel(made);
		boolean[] taken = keep.clone();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (!p.retired()) {
				occupy(plan, taken, p.rect());
			}
		}
		List<MasterPlan.Parcel> out = new ArrayList<>();
		fillBlock(plan, block, plan.seed() ^ ((long) plan.parcels().size() * 0x2545F4914F6CDD1DL), taken, out, plan.parcels().size(), false);
		for (MasterPlan.Parcel p : out) {
			plan.addParcel(p);
		}
		return made;
	}

	private static void mark(MasterPlan plan, boolean[] taken, int x0, int z0, int w, int d) {
		int extent = plan.extent();
		int size = 2 * extent + 1;
		for (int x = x0 - 1; x <= x0 + w; x++) {
			for (int z = z0 - 1; z <= z0 + d; z++) {
				if (plan.in(x, z)) {
					taken[(x + extent) * size + (z + extent)] = true;
				}
			}
		}
	}

	/** Sobra menor que isso (nao cabe nem um lote minusculo) e engolida pelo lote vizinho. */
	static final int GROW_LIMIT = 7;

	/**
	 * Cresce o lote para o fundo e para os lados quando a sobra livre que ficaria depois dele e menor que um lote
	 * minusculo (esse resto so viraria quintal): "mescla com o vizinho". O fundo e os lados ficam livres ate a rua ou
	 * o proximo lote; a frente nunca muda.
	 */
	private static Candidate grow(Sat sat, Candidate c) {
		int x0 = c.x0;
		int z0 = c.z0;
		int x1 = c.x0 + c.w - 1;
		int z1 = c.z0 + c.d - 1;
		Facing back = c.front.opposite();
		for (Facing f : new Facing[] {back, c.front.rotate(1), c.front.rotate(3)}) {
			int k = 0;
			for (int n = 1; n <= GROW_LIMIT; n++) {
				boolean free = switch (f) {
					case NORTH -> sat.all(x0, z0 - n, x1, z0 - n);
					case SOUTH -> sat.all(x0, z1 + n, x1, z1 + n);
					case WEST -> sat.all(x0 - n, z0, x0 - n, z1);
					case EAST -> sat.all(x1 + n, z0, x1 + n, z1);
				};
				if (!free) {
					break;
				}
				k = n;
			}
			if (k >= GROW_LIMIT) {
				continue;
			}
			switch (f) {
				case NORTH -> z0 -= k;
				case SOUTH -> z1 += k;
				case WEST -> x0 -= k;
				case EAST -> x1 += k;
			}
		}
		return new Candidate(x0, z0, x1 - x0 + 1, z1 - z0 + 1, c.front, c.score);
	}

	private static MasterPlan.Parcel take(MasterPlan plan, Candidate c, SizeClass cls, Zone zone, MasterPlan.Block block, int id, boolean[] taken,
			Sat sat) {
		c = grow(sat, c);
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
		return best(plan, sat, cls, corner, false);
	}

	/**
	 * {@code packing}: recorte sob demanda; penaliza deixar do lado do lote uma sobra de frente que nao cabe nem um lote
	 * pequeno (de 1 a {@link SizeClass#S} de largura): essas sobras viram retalhos que nenhuma obra usa.
	 */
	private static Candidate best(MasterPlan plan, Sat sat, SizeClass cls, boolean corner, boolean packing) {
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
					if (packing) {
						score -= sliver(sat, x0, z0, w, d, front.rotate(1)) + sliver(sat, x0, z0, w, d, front.rotate(3));
					}
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

	/**
	 * Sobra livre ao lado do lote, na direcao {@code side} (medida na fileira do meio do fundo): 0 se o lote encosta;
	 * a largura da sobra se ela nao comporta um lote pequeno (retalho perdido); 0 se ainda cabe um.
	 */
	private static double sliver(Sat sat, int x0, int z0, int w, int d, Facing side) {
		int cx = x0 + w / 2;
		int cz = z0 + d / 2;
		int x = switch (side) {
			case EAST -> x0 + w;
			case WEST -> x0 - 1;
			default -> cx;
		};
		int z = switch (side) {
			case SOUTH -> z0 + d;
			case NORTH -> z0 - 1;
			default -> cz;
		};
		int run = 0;
		while (run <= SizeClass.S.width() && sat.free(x, z)) {
			run++;
			x += side.dx;
			z += side.dz;
		}
		return run > 0 && run <= SizeClass.S.width() ? run : 0;
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
		int doorDist = streetAhead(plan, door[0], door[1], f, ACCESS_MAX);
		if (doorDist == 0) {
			return -1;
		}
		int near = 0;
		for (int[] c : edge) {
			if (streetAhead(plan, c[0], c[1], f, NEAR_MAX) > 0) {
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
			if (ax < x0 || az < z0 || bx > x1 || bz > z1) {
				return false;
			}
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
