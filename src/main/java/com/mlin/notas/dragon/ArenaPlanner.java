package com.mlin.notas.dragon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Planeja a arena do dragao na ilha central do End (logica pura). Somada ao terreno e aos pilares vanilla, sem mexer no
 * que a luta precisa: o portal de saida (raio 5 em volta de 0,0) fica livre, nada entra no quadrado de cada pilar de
 * obsidiana (que o vanilla limpa e refaz quando o dragao renasce) e as colunas dos pontos de voo do dragao nao ganham
 * altura.
 *
 * <p>Desenho:
 * <ul>
 * <li><b>Santuario do portal</b> (raio 5 a 15): piso radial de tijolos do End com 8 raios de purpur, anel escuro de
 * tijolos do vazio em volta do portal, borda de purpur e vazio; pedra gasta e musgo do End espalhados.</li>
 * <li><b>Colunata</b> (raio 12): 12 colunas de purpur, as inteiras ligadas por vergas e com haste do End, as quebradas com
 * entulho e o fuste caido no chao. Vaos nos 4 pontos cardeais.</li>
 * <li><b>Estrados</b> nos pontos cardeais (raio 10): o do norte e o <b>estrado do tesouro</b> (lanternas de alma; o bau
 * aparece ali quando o dragao morre), os outros tem obeliscos de tijolo do vazio com obsidiana chorona e lampada.</li>
 * <li><b>Vias processionais</b>: uma por pilar, do santuario ate a base, com bordas escuras, faixas de purpur e postes
 * com lanterna de alma.</li>
 * <li><b>Base de cada pilar</b>: soco de tijolos com degrau, contrafortes de purpur nos 4 cantos (obsidiana chorona,
 * lampada e haste) e <b>colares</b> de purpur abracando a obsidiana a cada 10 blocos; cristais etereos no pe.</li>
 * <li><b>Muralha da arena</b> (raio 24): anel baixo de tijolos com ameias, trechos desmoronados e portais com
 * pilares e lampada onde as vias passam.</li>
 * <li><b>Ruinas</b> entre as vias (dentro e fora da muralha): muros desmoronando, arcos partidos, colunas caidas e afloramentos de
 * cristal.</li>
 * </ul>
 *
 * <p>Referencias de desenho (forma, camadas e paleta; nada copiado): YUNG's Better End Island (pilares e portal
 * central redesenhados como arquitetura, com estrado proprio), a "End Arena" e o "End Dragon Arena &amp; Boss Arena" da
 * BuiltByBit (aneis de arena em camadas com cara de templo antigo, estatua/obeliscos), o mapa "The End Battleground"
 * (ilha flutuante com escultura de dragao) e o mundo "End Island Transformation" do CurseForge (pilares decorados so com
 * blocos vanilla: purpur, tijolos do End, obsidiana).
 */
public final class ArenaPlanner {
	/** Sem chao nesta coluna (vazio). */
	public static final int VOID = Integer.MIN_VALUE;
	/** Raio em volta de 0,0 que fica intocado (o portal vanilla ocupa raio 3,5). */
	public static final double PODIUM_CLEAR = 5.0;
	public static final double PLAZA_RADIUS = 15.5;
	public static final int COLONNADE_RADIUS = 12;
	public static final int DAIS_RADIUS = 10;
	public static final int HOARD_X = 0;
	public static final int HOARD_Z = -DAIS_RADIUS;
	/** Nas colunas dos pontos de voo do dragao nada passa desta altura acima do chao. */
	public static final int NODE_MAX_HEIGHT = 2;
	/** Distancia das ruinas ao eixo de uma via. */
	private static final double RUIN_PATH_CLEARANCE = 3.5;
	/** Raio da muralha baixa que fecha a arena. */
	public static final double RING_RADIUS = 24.0;

	/** Um pilar de obsidiana vanilla (centro, raio e altura do topo; {@code guarded} = tem gaiola). */
	public record Spike(int x, int z, int radius, int height, boolean guarded) {
		/** Dentro do quadrado que o vanilla limpa/refaz ao gerar o pilar. */
		public boolean inSquare(int px, int pz) {
			return Math.abs(px - x) <= radius && Math.abs(pz - z) <= radius;
		}

		/** Bloco de obsidiana do pilar (mesma conta do EndSpikeFeature). */
		public boolean obsidian(int px, int py, int pz) {
			int dx = px - x;
			int dz = pz - z;
			return dx * dx + dz * dz <= radius * radius + 1 && py < height;
		}

		public int chebyshev(int px, int pz) {
			return Math.max(Math.abs(px - x), Math.abs(pz - z));
		}
	}

	/** Altura do chao (y do bloco do topo) ou {@link #VOID}. */
	@FunctionalInterface
	public interface Ground {
		int y(int x, int z);
	}

	private final long seed;
	private final List<Spike> spikes;
	private final Ground ground;
	private final ArenaPlan plan;
	private final Random random;
	/** Colunas ja usadas por estruturas (nao recebem ruinas). */
	private final Set<Long> used = new HashSet<>();

	private ArenaPlanner(long seed, List<Spike> spikes, Ground ground) {
		this.seed = seed;
		this.spikes = spikes;
		this.ground = ground;
		this.plan = new ArenaPlan(spikes);
		this.random = new Random(seed ^ 0x5DEECE66DL);
	}

	public static ArenaPlan plan(long seed, List<Spike> spikes, Ground ground) {
		ArenaPlanner p = new ArenaPlanner(seed, spikes, ground);
		p.plaza();
		p.daises();
		p.colonnade();
		p.outerRing();
		for (Spike s : spikes) {
			p.path(s);
		}
		for (Spike s : spikes) {
			p.pillarBase(s);
		}
		p.ruins();
		p.lowerNodeColumns();
		p.prune();
		return p.plan;
	}

	/** Posicoes dos pilares vanilla (raio 42, 10 pilares; so o tamanho depende da semente). */
	public static int[] spikeCenter(int i) {
		double a = 2.0 * (-Math.PI + Math.PI / 10.0 * i);
		return new int[] {(int) Math.floor(42.0 * Math.cos(a)), (int) Math.floor(42.0 * Math.sin(a))};
	}

	/** Colunas (x, z) dos 24 pontos de voo do dragao (mesma conta do EnderDragon.findClosestNode). */
	public static List<int[]> dragonNodes() {
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i < 24; i++) {
			double r;
			double step;
			int m;
			if (i < 12) {
				r = 60;
				step = Math.PI / 12;
				m = i;
			} else if (i < 20) {
				r = 40;
				step = Math.PI / 8;
				m = i - 12;
			} else {
				r = 20;
				step = Math.PI / 4;
				m = i - 20;
			}
			double a = 2.0 * (-Math.PI + step * m);
			out.add(new int[] {(int) Math.floor(r * Math.cos(a)), (int) Math.floor(r * Math.sin(a))});
		}
		return out;
	}

	/** Coluna de ponto de voo (com 1 bloco de folga para a diferenca de arredondamento do jogo). */
	public static boolean nearNode(int x, int z) {
		for (int[] n : NODES) {
			if (Math.abs(n[0] - x) <= 1 && Math.abs(n[1] - z) <= 1) {
				return true;
			}
		}
		return false;
	}

	private static final List<int[]> NODES = dragonNodes();

	// ---------------------------------------------------------------- utilitarios

	private static long col(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private double noise(int x, int z, int salt) {
		long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L + salt * 0x27D4EB2F165667C5L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Coluna proibida: portal, quadrado de pilar ou sem chao. */
	private boolean blocked(int x, int z) {
		if (x * x + z * z < PODIUM_CLEAR * PODIUM_CLEAR) {
			return true;
		}
		for (Spike s : spikes) {
			if (s.inSquare(x, z)) {
				return true;
			}
		}
		return ground.y(x, z) == VOID;
	}

	private void pave(int x, int z, ArenaPart part) {
		if (blocked(x, z)) {
			return;
		}
		plan.set(x, ground.y(x, z), z, part);
	}

	/** Bloco acima do chao (nunca enterrado). */
	private void put(int x, int y, int z, ArenaPart part) {
		if (blocked(x, z) || y <= ground.y(x, z)) {
			return;
		}
		plan.set(x, y, z, part);
		used.add(col(x, z));
	}

	/** Enche a coluna do chao ate {@code top} com {@code fill} e poe {@code cap} em {@code top}. */
	private void column(int x, int z, int top, ArenaPart fill, ArenaPart cap) {
		int g = ground.y(x, z);
		if (g == VOID) {
			return;
		}
		for (int y = g + 1; y < top; y++) {
			put(x, y, z, fill);
		}
		put(x, top, z, cap);
	}

	private ArenaPart floor(int x, int z) {
		double n = noise(x, z, 1);
		if (n < 0.015) {
			return ArenaPart.PAVE_MOSS;
		}
		return n < 0.08 ? ArenaPart.PAVE_WORN : ArenaPart.PAVE_BRICK;
	}

	// ---------------------------------------------------------------- santuario

	private void plaza() {
		int r = (int) Math.ceil(PLAZA_RADIUS);
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d < PODIUM_CLEAR || d >= PLAZA_RADIUS) {
					continue;
				}
				ArenaPart part;
				if (d < 6.2) {
					part = ArenaPart.PAVE_VOID;
				} else if (d >= 14.4) {
					part = ArenaPart.PAVE_VOID;
				} else if (d >= 13.4) {
					part = ArenaPart.PAVE_PURPUR;
				} else if (d >= 8.6 && d < 9.4) {
					part = ArenaPart.PAVE_VOID;
				} else if (onSpoke(x, z, d)) {
					part = ArenaPart.PAVE_PURPUR;
				} else {
					part = floor(x, z);
				}
				pave(x, z, part);
			}
		}
	}

	/** 8 raios de purpur a 22,5 + k*45 graus (entre os estrados). */
	private static boolean onSpoke(int x, int z, double d) {
		double a = Math.atan2(z, x);
		for (int k = 0; k < 8; k++) {
			double s = Math.PI / 8 + k * Math.PI / 4;
			double rel = a - s;
			if (Math.cos(rel) > 0 && Math.abs(d * Math.sin(rel)) < 0.75) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- estrados

	private void daises() {
		int[][] centers = {{HOARD_X, HOARD_Z}, {DAIS_RADIUS, 0}, {0, DAIS_RADIUS}, {-DAIS_RADIUS, 0}};
		for (int i = 0; i < centers.length; i++) {
			int cx = centers[i][0];
			int cz = centers[i][1];
			int gc = ground.y(cx, cz);
			if (gc == VOID) {
				continue;
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					int g = ground.y(x, z);
					if (g == VOID || g > gc) {
						continue;
					}
					boolean platform = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
					if (platform) {
						column(x, z, gc + 1, ArenaPart.VOID_BRICK, ArenaPart.VOID_BRICK);
					} else if (g == gc) {
						put(x, gc + 1, z, ArenaPart.BRICK_SLAB);
					} else {
						column(x, z, gc + 1, ArenaPart.BRICK, ArenaPart.BRICK_SLAB);
					}
				}
			}
			if (i == 0) {
				// estrado do tesouro: lanternas de alma nos cantos, o bau aparece no centro
				for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
					put(cx + c[0], gc + 2, cz + c[1], ArenaPart.SOUL_LANTERN);
				}
				plan.hoard(cx, gc + 2, cz);
			} else {
				put(cx, gc + 2, cz, ArenaPart.VOID_BRICK);
				put(cx, gc + 3, cz, ArenaPart.CRYING_OBSIDIAN);
				put(cx, gc + 4, cz, ArenaPart.VOID_BRICK);
				put(cx, gc + 5, cz, ArenaPart.LAMP);
				put(cx, gc + 6, cz, ArenaPart.END_ROD);
				for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
					put(cx + c[0], gc + 2, cz + c[1], ArenaPart.CRYSTAL);
				}
			}
		}
	}

	// ---------------------------------------------------------------- colunata

	private void colonnade() {
		int n = 16;
		int[][] pos = new int[n][];
		boolean[] intact = new boolean[n];
		int[] top = new int[n];
		for (int k = 0; k < n; k++) {
			if (k % 4 == 0) {
				continue;
			}
			double a = k * 2 * Math.PI / n;
			int x = (int) Math.round(COLONNADE_RADIUS * Math.cos(a));
			int z = (int) Math.round(COLONNADE_RADIUS * Math.sin(a));
			if (ground.y(x, z) == VOID) {
				continue;
			}
			pos[k] = new int[] {x, z};
			intact[k] = random.nextDouble() < 0.62;
			top[k] = ground.y(x, z) + 6;
		}
		// vergas entre colunas inteiras vizinhas (no mesmo quadrante): as duas sobem ate a mais alta
		boolean[] lintelled = new boolean[n];
		for (int k = 0; k < n; k++) {
			int k2 = (k + 1) % n;
			if (pos[k] == null || pos[k2] == null || !intact[k] || !intact[k2]) {
				continue;
			}
			int t = Math.max(top[k], top[k2]);
			top[k] = Math.max(top[k], t);
			top[k2] = Math.max(top[k2], t);
			lintelled[k] = true;
		}
		// segunda passada: vergas que ficaram mais baixas que o topo final
		for (int k = 0; k < n; k++) {
			if (pos[k] == null) {
				continue;
			}
			int x = pos[k][0];
			int z = pos[k][1];
			int g = ground.y(x, z);
			if (intact[k]) {
				put(x, g + 1, z, ArenaPart.PURPUR);
				for (int y = g + 2; y < top[k]; y++) {
					put(x, y, z, ArenaPart.PURPUR_PILLAR);
				}
				put(x, top[k], z, ArenaPart.PURPUR);
				put(x, top[k] + 1, z, ArenaPart.END_ROD);
			} else {
				int h = 1 + random.nextInt(3);
				put(x, g + 1, z, ArenaPart.PURPUR);
				for (int y = g + 2; y <= g + 1 + h; y++) {
					put(x, y, z, ArenaPart.PURPUR_PILLAR);
				}
				fallenShaft(x, z, k * 2 * Math.PI / n);
			}
		}
		for (int k = 0; k < n; k++) {
			int k2 = (k + 1) % n;
			if (!lintelled[k]) {
				continue;
			}
			int t = Math.max(top[k], top[k2]);
			line(pos[k][0], pos[k][1], pos[k2][0], pos[k2][1], t, ArenaPart.PURPUR_SLAB_TOP);
		}
	}

	/** Linha 4-conexa (cada bloco encosta no anterior pela face), sem os extremos. */
	private void line(int x0, int z0, int x1, int z1, int y, ArenaPart part) {
		int x = x0;
		int z = z0;
		while (x != x1 || z != z1) {
			int ex = Math.abs(x1 - x);
			int ez = Math.abs(z1 - z);
			if (ex >= ez) {
				x += Integer.signum(x1 - x);
			} else {
				z += Integer.signum(z1 - z);
			}
			if (x != x1 || z != z1) {
				if (plan.get(x, y, z) == null) {
					put(x, y, z, part);
				}
			}
		}
	}

	/** Fuste caido para fora da colunata (deitado no chao) + entulho. */
	private void fallenShaft(int x, int z, double angle) {
		int dx = (int) Math.signum(Math.round(Math.cos(angle) * 2));
		int dz = (int) Math.signum(Math.round(Math.sin(angle) * 2));
		boolean alongX = Math.abs(Math.cos(angle)) >= Math.abs(Math.sin(angle));
		int len = 2 + random.nextInt(2);
		for (int i = 2; i < 2 + len; i++) {
			int px = alongX ? x + dx * i : x + (i == 2 ? dx : 0);
			int pz = alongX ? z + (i == 2 ? dz : 0) : z + dz * i;
			int g = ground.y(px, pz);
			if (g != VOID && !blocked(px, pz)) {
				put(px, g + 1, pz, alongX ? ArenaPart.PURPUR_PILLAR_X : ArenaPart.PURPUR_PILLAR_Z);
			}
		}
		int rx = x + (alongX ? dx : 0) + (alongX ? 0 : 1);
		int rz = z + (alongX ? 0 : dz) + (alongX ? 1 : 0);
		int g = ground.y(rx, rz);
		if (g != VOID) {
			put(rx, g + 1, rz, random.nextBoolean() ? ArenaPart.PURPUR_SLAB : ArenaPart.PURPUR);
		}
	}

	// ---------------------------------------------------------------- anel da arena

	/**
	 * Muralha baixa em anel (raio 24) fechando a arena: base de tijolos do vazio, ameias de tijolos do End, trechos
	 * desmoronados com entulho para fora e portais com pilares e lampada onde cada via passa.
	 */
	private void outerRing() {
		int r = (int) Math.ceil(RING_RADIUS + 1);
		double phase = noise(0, 0, 11) * Math.PI * 2;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d < RING_RADIUS - 0.6 || d >= RING_RADIUS + 0.6) {
					continue;
				}
				int g = ground.y(x, z);
				if (g == VOID || blocked(x, z)) {
					continue;
				}
				if (nearPath(x, z, 2.6)) {
					if (!nearPath(x, z, 1.9)) {
						// pilares do portal da via
						column(x, z, g + 4, ArenaPart.PURPUR_PILLAR, ArenaPart.PURPUR);
						put(x, g + 5, z, ArenaPart.LAMP);
						put(x, g + 6, z, ArenaPart.END_ROD);
					}
					continue;
				}
				double a = Math.atan2(z, x);
				// desmoronamento: ruido suave ao longo do anel (0 = intacto, 1 = no chao)
				double wear = 0.5 + 0.3 * Math.sin(a * 3 + phase) + 0.25 * Math.sin(a * 7 - phase * 2) + (noise(x, z, 12) - 0.5) * 0.3;
				int h = wear > 0.85 ? 0 : wear > 0.65 ? 1 : 3;
				boolean merlon = ((int) Math.floor((a + Math.PI) * RING_RADIUS / 2.0)) % 2 == 0;
				if (h == 0) {
					if (noise(x, z, 13) < 0.5) {
						put(x, g + 1, z, ArenaPart.BRICK_SLAB);
					}
					continue;
				}
				put(x, g + 1, z, ArenaPart.VOID_BRICK);
				for (int y = g + 2; y <= g + h; y++) {
					put(x, y, z, ArenaPart.BRICK);
				}
				if (h == 3 && merlon) {
					put(x, g + 4, z, ArenaPart.BRICK_SLAB);
				}
				if (h == 1 && noise(x, z, 15) < 0.4) {
					// entulho caido para fora
					int ox = (int) Math.round(x * (RING_RADIUS + 1.6) / d);
					int oz = (int) Math.round(z * (RING_RADIUS + 1.6) / d);
					int og = ground.y(ox, oz);
					if (og != VOID && !nearPath(ox, oz, 2.6)) {
						put(ox, og + 1, oz, ArenaPart.BRICK_SLAB);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- vias

	private void path(Spike s) {
		double len = Math.sqrt((double) s.x() * s.x() + (double) s.z() * s.z());
		double ux = s.x() / len;
		double uz = s.z() / len;
		double end = len - s.radius() - 4.0;
		for (double t = PLAZA_RADIUS - 0.5; t <= end; t += 0.3) {
			for (double p = -1.5; p <= 1.5; p += 0.3) {
				int x = (int) Math.round(ux * t - uz * p);
				int z = (int) Math.round(uz * t + ux * p);
				ArenaPart part;
				if (Math.abs(p) > 0.95) {
					part = ArenaPart.PAVE_VOID;
				} else if (t % 7.0 < 1.0) {
					part = ArenaPart.PAVE_PURPUR;
				} else {
					part = floor(x, z);
				}
				ArenaPart old = plan.get(x, ground.y(x, z), z);
				if (old == ArenaPart.PAVE_PURPUR && part != ArenaPart.PAVE_PURPUR) {
					continue;
				}
				pave(x, z, part);
			}
		}
		// postes com lanterna dos dois lados, no meio da via
		double t = (PLAZA_RADIUS + end) / 2.0;
		for (int side = -1; side <= 1; side += 2) {
			int x = (int) Math.round(ux * t - uz * 2.6 * side);
			int z = (int) Math.round(uz * t + ux * 2.6 * side);
			int g = ground.y(x, z);
			if (g == VOID || blocked(x, z)) {
				continue;
			}
			put(x, g + 1, z, ArenaPart.BRICK_WALL);
			put(x, g + 2, z, ArenaPart.BRICK_WALL);
			put(x, g + 3, z, ArenaPart.SOUL_LANTERN);
		}
	}

	/** A via do pilar {@code s} passa a menos de {@code clearance} de (x, z)? */
	private boolean nearPath(int x, int z, double clearance) {
		for (Spike s : spikes) {
			double len = Math.sqrt((double) s.x() * s.x() + (double) s.z() * s.z());
			double ux = s.x() / len;
			double uz = s.z() / len;
			double t = x * ux + z * uz;
			double p = -x * uz + z * ux;
			if (t > PLAZA_RADIUS - 2 && t < len && Math.abs(p) < clearance) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- base dos pilares

	private void pillarBase(Spike s) {
		int L = ground.y(s.x(), s.z());
		if (L == VOID) {
			return;
		}
		int R = s.radius();
		for (int dx = -(R + 4); dx <= R + 4; dx++) {
			for (int dz = -(R + 4); dz <= R + 4; dz++) {
				int c = Math.max(Math.abs(dx), Math.abs(dz));
				int x = s.x() + dx;
				int z = s.z() + dz;
				int g = ground.y(x, z);
				if (c <= R || g == VOID || otherSpike(s, x, z)) {
					continue;
				}
				if (c == R + 1) {
					if (g < L + 2) {
						column(x, z, L + 2, ArenaPart.BRICK, ArenaPart.VOID_BRICK);
					}
				} else if (c == R + 2) {
					if (g < L + 1) {
						column(x, z, L + 1, ArenaPart.BRICK, ArenaPart.BRICK);
					}
				} else if (c == R + 3) {
					if (g < L + 1) {
						column(x, z, L + 1, ArenaPart.BRICK, ArenaPart.BRICK_SLAB);
					}
				} else if (c == R + 4) {
					pave(x, z, ArenaPart.PAVE_VOID);
					if (noise(x, z, 7) < 0.12) {
						put(x, g + 1, z, ArenaPart.CRYSTAL);
					}
				}
			}
		}
		// colares de purpur abracando a obsidiana a cada 10 blocos; nervuras nos 4 cantos ligam os colares aos contrafortes
		List<Integer> collars = new ArrayList<>();
		for (int y = L + 13; y <= s.height() - 7; y += 10) {
			collars.add(y);
		}
		int ribTop = collars.isEmpty() ? 0 : collars.getLast();
		// contrafortes nos 4 cantos (2x2), um pouco mais altos nos pilares altos
		int b = 5 + Math.min(3, Math.max(0, (s.height() - L - 20) / 6));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int i = R + 1; i <= R + 2; i++) {
					for (int j = R + 1; j <= R + 2; j++) {
						int x = s.x() + sx * i;
						int z = s.z() + sz * j;
						if (otherSpike(s, x, z)) {
							continue;
						}
						boolean outer = i == R + 2 && j == R + 2;
						boolean rib = i == R + 1 && j == R + 1 && ribTop > 0;
						int top = rib ? ribTop - 1 : L + 2 + b;
						for (int y = L + 3; y <= top; y++) {
							put(x, y, z, y == L + 2 + b / 2 + 1 ? ArenaPart.CRYING_OBSIDIAN : ArenaPart.PURPUR_PILLAR);
						}
						if (outer) {
							put(x, L + 3 + b, z, ArenaPart.LAMP);
							put(x, L + 4 + b, z, ArenaPart.END_ROD);
						} else if (!rib) {
							put(x, L + 3 + b, z, ArenaPart.PURPUR_SLAB);
						}
					}
				}
			}
		}
		for (int y : collars) {
			for (int dx = -(R + 1); dx <= R + 1; dx++) {
				for (int dz = -(R + 1); dz <= R + 1; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != R + 1) {
						continue;
					}
					int x = s.x() + dx;
					int z = s.z() + dz;
					if (otherSpike(s, x, z)) {
						continue;
					}
					boolean corner = Math.abs(dx) == R + 1 && Math.abs(dz) == R + 1;
					if (corner) {
						put(x, y, z, ArenaPart.LAMP);
						if (y == ribTop) {
							put(x, y + 1, z, ArenaPart.END_ROD);
						}
					} else {
						put(x, y, z, ArenaPart.PURPUR);
						put(x, y - 1, z, ArenaPart.PURPUR_SLAB_TOP);
					}
				}
			}
		}
	}

	private boolean otherSpike(Spike self, int x, int z) {
		for (Spike s : spikes) {
			if (s != self && s.chebyshev(x, z) <= s.radius() + 1) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- ruinas

	private void ruins() {
		int n = spikes.size();
		List<double[]> angles = new ArrayList<>();
		for (Spike s : spikes) {
			angles.add(new double[] {Math.atan2(s.z(), s.x())});
		}
		angles.sort((a, b) -> Double.compare(a[0], b[0]));
		for (int i = 0; i < n; i++) {
			double a0 = angles.get(i)[0];
			double a1 = i + 1 < n ? angles.get(i + 1)[0] : angles.getFirst()[0] + 2 * Math.PI;
			double mid = (a0 + a1) / 2;
			int count = 1 + (random.nextDouble() < 0.6 ? 1 : 0);
			for (int j = 0; j < count; j++) {
				double r = random.nextBoolean() ? 17 + random.nextDouble() * 4 : 27 + random.nextDouble() * 8;
				double a = mid + (random.nextDouble() - 0.5) * (a1 - a0) * 0.4;
				int x = (int) Math.round(r * Math.cos(a));
				int z = (int) Math.round(r * Math.sin(a));
				int kind = random.nextInt(4);
				ruin(kind, x, z, a);
			}
		}
	}

	/** Pode por uma ruina com pegada (x0..x1, z0..z1)? Longe das vias, dos pilares e de outras estruturas. */
	private boolean freeArea(int x0, int z0, int x1, int z1) {
		for (int x = Math.min(x0, x1) - 1; x <= Math.max(x0, x1) + 1; x++) {
			for (int z = Math.min(z0, z1) - 1; z <= Math.max(z0, z1) + 1; z++) {
				if (blocked(x, z) || used.contains(col(x, z)) || nearPath(x, z, RUIN_PATH_CLEARANCE)) {
					return false;
				}
				for (Spike s : spikes) {
					if (s.chebyshev(x, z) <= s.radius() + 5) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private void ruin(int kind, int x, int z, double angle) {
		// direcao tangente ao anel, alinhada ao eixo mais proximo
		boolean tangentX = Math.abs(Math.sin(angle)) >= Math.abs(Math.cos(angle));
		int tx = tangentX ? 1 : 0;
		int tz = tangentX ? 0 : 1;
		switch (kind) {
			case 0 -> brokenWall(x, z, tx, tz);
			case 1 -> brokenArch(x, z, tx, tz);
			case 2 -> fallenColumn(x, z, tx, tz);
			default -> crystalOutcrop(x, z);
		}
	}

	private void brokenWall(int x, int z, int tx, int tz) {
		int len = 4 + random.nextInt(4);
		if (!freeArea(x, z, x + tx * (len - 1) + tz * 2, z + tz * (len - 1) + tx * 2)) {
			return;
		}
		int maxH = 3 + random.nextInt(3);
		for (int i = 0; i < len; i++) {
			int px = x + tx * i;
			int pz = z + tz * i;
			int g = ground.y(px, pz);
			int h = Math.max(1, (int) Math.round(maxH * (1.0 - (double) i / len) + noise(px, pz, 3) * 1.6 - 0.6));
			for (int y = g + 1; y <= g + h; y++) {
				put(px, y, pz, noise(px, y, 4) < 0.18 ? ArenaPart.VOID_BRICK : ArenaPart.BRICK);
			}
			if (noise(px, pz, 5) < 0.35) {
				put(px, g + h + 1, pz, ArenaPart.BRICK_SLAB);
			}
		}
		// retorno curto na ponta alta (canto do comodo) e entulho na ponta baixa
		for (int i = 1; i <= 2; i++) {
			int px = x + tz * i;
			int pz = z + tx * i;
			int g = ground.y(px, pz);
			int h = Math.max(1, maxH - i);
			for (int y = g + 1; y <= g + h; y++) {
				put(px, y, pz, ArenaPart.BRICK);
			}
		}
		int ex = x + tx * len + tz;
		int ez = z + tz * len + tx;
		int g = ground.y(ex, ez);
		if (g != VOID && !nearPath(ex, ez, 2.5)) {
			put(ex, g + 1, ez, ArenaPart.BRICK_SLAB);
		}
	}

	private void brokenArch(int x, int z, int tx, int tz) {
		int span = 4;
		if (!freeArea(x, z, x + tx * span, z + tz * span)) {
			return;
		}
		int ga = ground.y(x, z);
		int gb = ground.y(x + tx * span, z + tz * span);
		boolean broken = random.nextDouble() < 0.4;
		int top = Math.max(ga, gb) + 5;
		column(x, z, top, ArenaPart.BRICK, ArenaPart.PURPUR);
		if (broken) {
			column(x + tx * span, z + tz * span, gb + 2, ArenaPart.BRICK, ArenaPart.BRICK_SLAB);
			// pedaco da verga ainda preso na coluna inteira
			put(x + tx, top, z + tz, ArenaPart.PURPUR_SLAB_TOP);
			int rx = x + tx * 2 + tz;
			int rz = z + tz * 2 + tx;
			int g = ground.y(rx, rz);
			if (g != VOID) {
				put(rx, g + 1, rz, ArenaPart.PURPUR);
				put(rx + tx, g + 1, rz + tz, ArenaPart.PURPUR_SLAB);
			}
		} else {
			column(x + tx * span, z + tz * span, top, ArenaPart.BRICK, ArenaPart.PURPUR);
			for (int i = 1; i < span; i++) {
				put(x + tx * i, top, z + tz * i, ArenaPart.PURPUR_SLAB_TOP);
			}
			put(x + tx * 2, top + 1, z + tz * 2, ArenaPart.END_ROD);
		}
	}

	private void fallenColumn(int x, int z, int tx, int tz) {
		int len = 3 + random.nextInt(3);
		if (!freeArea(x, z, x + tx * (len + 2), z + tz * (len + 2))) {
			return;
		}
		int g = ground.y(x, z);
		put(x, g + 1, z, ArenaPart.PURPUR);
		put(x, g + 2, z, ArenaPart.PURPUR_PILLAR);
		if (random.nextBoolean()) {
			put(x, g + 3, z, ArenaPart.PURPUR_PILLAR);
		}
		ArenaPart lying = tx != 0 ? ArenaPart.PURPUR_PILLAR_X : ArenaPart.PURPUR_PILLAR_Z;
		for (int i = 2; i < 2 + len; i++) {
			int px = x + tx * i;
			int pz = z + tz * i;
			put(px, ground.y(px, pz) + 1, pz, lying);
		}
		int cx = x + tx * (len + 2);
		int cz = z + tz * (len + 2);
		put(cx, ground.y(cx, cz) + 1, cz, ArenaPart.PURPUR);
	}

	private void crystalOutcrop(int x, int z) {
		if (!freeArea(x - 1, z - 1, x + 1, z + 1)) {
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int px = x + dx;
				int pz = z + dz;
				int g = ground.y(px, pz);
				double n = noise(px, pz, 6);
				boolean center = dx == 0 && dz == 0;
				if (center || n < 0.45) {
					int h = center ? 2 + random.nextInt(2) : 1;
					for (int y = g + 1; y <= g + h; y++) {
						put(px, y, pz, ArenaPart.CRYSTAL_BLOCK);
					}
					put(px, g + h + 1, pz, ArenaPart.CRYSTAL);
				} else if (n < 0.8) {
					put(px, g + 1, pz, ArenaPart.CRYSTAL);
				}
			}
		}
	}

	// ---------------------------------------------------------------- limpeza final

	/** Nada alto nas colunas dos pontos de voo do dragao (o caminho dele depende do heightmap ali). */
	private void lowerNodeColumns() {
		List<long[]> drop = new ArrayList<>();
		for (Map.Entry<Long, ArenaPart> e : plan.blocks().entrySet()) {
			long k = e.getKey();
			int x = ArenaPlan.keyX(k);
			int y = ArenaPlan.keyY(k);
			int z = ArenaPlan.keyZ(k);
			if (nearNode(x, z) && y > ground.y(x, z) + NODE_MAX_HEIGHT) {
				drop.add(new long[] {x, y, z});
			}
		}
		for (long[] d : drop) {
			plan.remove((int) d[0], (int) d[1], (int) d[2]);
		}
	}

	/**
	 * Tira o que ficaria solto no ar: blocos solidos precisam se ligar (pela face) a algo apoiado no chao ou na
	 * obsidiana de um pilar; decoracoes precisam de bloco solido ou chao embaixo.
	 */
	private void prune() {
		Set<Long> reached = new HashSet<>();
		ArrayDeque<Long> queue = new ArrayDeque<>();
		for (Map.Entry<Long, ArenaPart> e : plan.blocks().entrySet()) {
			if (!e.getValue().solid()) {
				continue;
			}
			long k = e.getKey();
			int x = ArenaPlan.keyX(k);
			int y = ArenaPlan.keyY(k);
			int z = ArenaPlan.keyZ(k);
			if (y <= ground.y(x, z) + 1 || touchesObsidian(x, y, z)) {
				reached.add(k);
				queue.add(k);
			}
		}
		int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			long k = queue.poll();
			int x = ArenaPlan.keyX(k);
			int y = ArenaPlan.keyY(k);
			int z = ArenaPlan.keyZ(k);
			for (int[] d : dirs) {
				long n = ArenaPlan.key(x + d[0], y + d[1], z + d[2]);
				ArenaPart p = plan.blocks().get(n);
				if (p != null && p.solid() && reached.add(n)) {
					queue.add(n);
				}
			}
		}
		List<Long> remove = new ArrayList<>();
		for (Map.Entry<Long, ArenaPart> e : plan.blocks().entrySet()) {
			long k = e.getKey();
			if (e.getValue().solid() && !reached.contains(k)) {
				remove.add(k);
			}
		}
		for (long k : remove) {
			plan.remove(ArenaPlan.keyX(k), ArenaPlan.keyY(k), ArenaPlan.keyZ(k));
		}
		remove.clear();
		for (Map.Entry<Long, ArenaPart> e : plan.blocks().entrySet()) {
			long k = e.getKey();
			if (e.getValue().solid()) {
				continue;
			}
			int x = ArenaPlan.keyX(k);
			int y = ArenaPlan.keyY(k);
			int z = ArenaPlan.keyZ(k);
			ArenaPart below = plan.get(x, y - 1, z);
			if (!(y - 1 == ground.y(x, z) || below != null && below.solid())) {
				remove.add(k);
			}
		}
		for (long k : remove) {
			plan.remove(ArenaPlan.keyX(k), ArenaPlan.keyY(k), ArenaPlan.keyZ(k));
		}
	}

	private boolean touchesObsidian(int x, int y, int z) {
		for (Spike s : spikes) {
			if (s.obsidian(x + 1, y, z) || s.obsidian(x - 1, y, z) || s.obsidian(x, y, z + 1) || s.obsidian(x, y, z - 1)) {
				return true;
			}
		}
		return false;
	}
}
