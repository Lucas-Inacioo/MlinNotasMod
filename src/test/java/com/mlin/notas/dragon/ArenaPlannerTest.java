package com.mlin.notas.dragon;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaPlannerTest {
	private static final long[] SEEDS = {0L, 1L, 7L, 42L, -99L, 123456789L, 987654321987L, 31337L};

	/** Pilares como o vanilla gera: posicoes fixas, tamanhos embaralhados pela semente. */
	static List<ArenaPlanner.Spike> spikes(long seed) {
		List<Integer> sizes = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			sizes.add(i);
		}
		Collections.shuffle(sizes, new Random(seed));
		List<ArenaPlanner.Spike> out = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			int[] c = ArenaPlanner.spikeCenter(i);
			int size = sizes.get(i);
			out.add(new ArenaPlanner.Spike(c[0], c[1], 2 + size / 3, 76 + size * 3, size == 1 || size == 2));
		}
		return out;
	}

	/** Ilha em domo (como a central do End) com calombos e beirada no vazio. */
	static ArenaPlanner.Ground dome(long seed) {
		Random r = new Random(seed);
		double bx = r.nextDouble() * 6;
		double bz = r.nextDouble() * 6;
		return (x, z) -> {
			double d2 = (double) x * x + (double) z * z;
			if (d2 > 80 * 80) {
				return ArenaPlanner.VOID;
			}
			return 62 - (int) Math.floor(d2 / 500.0) + (int) Math.round(Math.sin(x / 7.0 + bx) * Math.cos(z / 9.0 + bz));
		};
	}

	private static ArenaPlan plan(long seed) {
		return ArenaPlanner.plan(seed, spikes(seed), dome(seed));
	}

	@Test
	void sameSeedSamePlan() {
		assertEquals(plan(42).blocks(), plan(42).blocks());
	}

	@Test
	void portalAndPillarSquaresStayVanilla() {
		for (long seed : SEEDS) {
			ArenaPlan plan = plan(seed);
			for (long k : plan.blocks().keySet()) {
				int x = ArenaPlan.keyX(k);
				int z = ArenaPlan.keyZ(k);
				assertTrue(x * x + z * z >= ArenaPlanner.PODIUM_CLEAR * ArenaPlanner.PODIUM_CLEAR, "bloco no portal em " + x + "," + z);
				for (ArenaPlanner.Spike s : plan.spikes()) {
					assertTrue(!s.inSquare(x, z), "bloco no quadrado do pilar " + s + " em " + x + "," + z);
				}
			}
		}
	}

	@Test
	void dragonFlightNodesStayLow() {
		for (long seed : SEEDS) {
			ArenaPlan plan = plan(seed);
			ArenaPlanner.Ground ground = dome(seed);
			for (long k : plan.blocks().keySet()) {
				int x = ArenaPlan.keyX(k);
				int z = ArenaPlan.keyZ(k);
				if (ArenaPlanner.nearNode(x, z)) {
					assertTrue(ArenaPlan.keyY(k) <= ground.y(x, z) + ArenaPlanner.NODE_MAX_HEIGHT, "bloco alto no ponto de voo " + x + "," + z);
				}
			}
			// nada no santuario acima de 7 blocos do chao (o dragao pousa no portal)
			for (long k : plan.blocks().keySet()) {
				int x = ArenaPlan.keyX(k);
				int z = ArenaPlan.keyZ(k);
				if (x * x + z * z < 16 * 16) {
					assertTrue(ArenaPlan.keyY(k) <= ground.y(x, z) + 8, "alto demais perto do portal: " + x + "," + ArenaPlan.keyY(k) + "," + z);
				}
			}
		}
	}

	@Test
	void nothingFloats() {
		for (long seed : SEEDS) {
			ArenaPlan plan = plan(seed);
			ArenaPlanner.Ground ground = dome(seed);
			Set<Long> reached = new HashSet<>();
			ArrayDeque<Long> queue = new ArrayDeque<>();
			for (Map.Entry<Long, ArenaPart> e : plan.blocks().entrySet()) {
				long k = e.getKey();
				int x = ArenaPlan.keyX(k);
				int y = ArenaPlan.keyY(k);
				int z = ArenaPlan.keyZ(k);
				assertTrue(y >= ground.y(x, z), "enterrado/abaixo do chao: " + e);
				if (e.getValue().paving()) {
					assertEquals(ground.y(x, z), y, "piso fora do chao");
				}
				if (e.getValue().solid() && (y <= ground.y(x, z) + 1 || touchesObsidian(plan, x, y, z))) {
					reached.add(k);
					queue.add(k);
				}
			}
			while (!queue.isEmpty()) {
				long k = queue.poll();
				int x = ArenaPlan.keyX(k);
				int y = ArenaPlan.keyY(k);
				int z = ArenaPlan.keyZ(k);
				for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
					long n = ArenaPlan.key(x + d[0], y + d[1], z + d[2]);
					ArenaPart p = plan.blocks().get(n);
					if (p != null && p.solid() && reached.add(n)) {
						queue.add(n);
					}
				}
			}
			for (Map.Entry<Long, ArenaPart> e : plan.blocks().entrySet()) {
				long k = e.getKey();
				int x = ArenaPlan.keyX(k);
				int y = ArenaPlan.keyY(k);
				int z = ArenaPlan.keyZ(k);
				if (e.getValue().solid()) {
					assertTrue(reached.contains(k), "solto no ar: " + e.getValue() + " em " + x + "," + y + "," + z);
				} else {
					ArenaPart below = plan.get(x, y - 1, z);
					assertTrue(y - 1 == ground.y(x, z) || below != null && below.solid(), "decoracao sem apoio: " + e.getValue() + " em " + x + "," + y + "," + z);
				}
			}
		}
	}

	private static boolean touchesObsidian(ArenaPlan plan, int x, int y, int z) {
		for (ArenaPlanner.Spike s : plan.spikes()) {
			if (s.obsidian(x + 1, y, z) || s.obsidian(x - 1, y, z) || s.obsidian(x, y, z + 1) || s.obsidian(x, y, z - 1)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void hasAllTheParts() {
		for (long seed : SEEDS) {
			ArenaPlan plan = plan(seed);
			assertTrue(plan.count(ArenaPart.PAVE_PURPUR) > 150, "raios e faixas de purpur");
			assertTrue(plan.count(ArenaPart.PAVE_VOID) > 300, "bordas escuras");
			assertTrue(plan.count(ArenaPart.PURPUR_PILLAR) > 150, "colunas e contrafortes");
			assertTrue(plan.count(ArenaPart.LAMP) >= 30, "lampadas nos contrafortes e obeliscos: " + plan.count(ArenaPart.LAMP));
			assertTrue(plan.count(ArenaPart.SOUL_LANTERN) >= 16, "lanternas das vias e do tesouro");
			assertTrue(plan.count(ArenaPart.CRYING_OBSIDIAN) >= 30, "obsidiana chorona");
			assertTrue(plan.count(ArenaPart.PURPUR_SLAB_TOP) >= 20, "vergas e colares");
			assertTrue(plan.count(ArenaPart.CRYSTAL) >= 15, "cristais");
			long ruins = plan.count(ArenaPart.CRYSTAL_BLOCK) + plan.count(ArenaPart.PURPUR_PILLAR_X) + plan.count(ArenaPart.PURPUR_PILLAR_Z);
			assertTrue(ruins >= 6, "ruinas espalhadas: " + ruins);
			long ring = plan.blocks().entrySet().stream().filter(e -> !e.getValue().paving()).filter(e -> {
				double d = Math.hypot(ArenaPlan.keyX(e.getKey()), ArenaPlan.keyZ(e.getKey()));
				return Math.abs(d - ArenaPlanner.RING_RADIUS) < 0.7;
			}).count();
			assertTrue(ring >= 150, "muralha da arena: " + ring);
			// o tesouro fica no ar logo acima do estrado de tijolos do vazio
			assertEquals(ArenaPart.VOID_BRICK, plan.get(plan.hoardX(), plan.hoardY() - 1, plan.hoardZ()));
			assertEquals(null, plan.get(plan.hoardX(), plan.hoardY(), plan.hoardZ()));
			// cada pilar tem soco
			for (ArenaPlanner.Spike s : plan.spikes()) {
				int around = 0;
				for (long k : plan.blocks().keySet()) {
					if (s.chebyshev(ArenaPlan.keyX(k), ArenaPlan.keyZ(k)) == s.radius() + 1) {
						around++;
					}
				}
				assertTrue(around >= 12, "pilar sem soco: " + s);
			}
		}
	}

	/** Andar a pe pelas colunas (sobe ate 1 bloco, 2 de espaco livre; decoracao no topo nao da para pisar). */
	@Test
	void everythingImportantIsReachableOnFoot() {
		for (long seed : SEEDS) {
			ArenaPlan plan = plan(seed);
			ArenaPlanner.Ground ground = dome(seed);
			Map<Long, Double> stand = new HashMap<>();
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			int sx = 6;
			int sz = 0;
			Double s0 = standHeight(plan, ground, sx, sz);
			assertNotNull(s0);
			stand.put(col(sx, sz), s0);
			queue.add(new int[] {sx, sz});
			while (!queue.isEmpty()) {
				int[] c = queue.poll();
				double h = stand.get(col(c[0], c[1]));
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int x = c[0] + d[0];
					int z = c[1] + d[1];
					if (Math.abs(x) > 70 || Math.abs(z) > 70 || stand.containsKey(col(x, z)) || inSpike(plan, x, z)) {
						continue;
					}
					Double h2 = standHeight(plan, ground, x, z);
					if (h2 != null && h2 - h <= 1.0 && h - h2 <= 3.0) {
						stand.put(col(x, z), h2);
						queue.add(new int[] {x, z});
					}
				}
			}
			assertTrue(stand.containsKey(col(plan.hoardX(), plan.hoardZ() + 1)) || stand.containsKey(col(plan.hoardX(), plan.hoardZ())),
					"estrado do tesouro inalcancavel (semente " + seed + ")");
			for (ArenaPlanner.Spike s : plan.spikes()) {
				boolean reached = false;
				for (long k : stand.keySet()) {
					int x = (int) (k >> 32);
					int z = (int) k;
					if (s.chebyshev(x, z) <= s.radius() + 2) {
						reached = true;
						break;
					}
				}
				assertTrue(reached, "base do pilar " + s + " inalcancavel (semente " + seed + ")");
			}
		}
	}

	private static long col(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static boolean inSpike(ArenaPlan plan, int x, int z) {
		for (ArenaPlanner.Spike s : plan.spikes()) {
			if (s.chebyshev(x, z) <= s.radius()) {
				return true;
			}
		}
		return false;
	}

	/** Altura onde os pes ficam na coluna (pilha continua a partir do chao), ou null se nao da para pisar. */
	private static Double standHeight(ArenaPlan plan, ArenaPlanner.Ground ground, int x, int z) {
		int g = ground.y(x, z);
		if (g == ArenaPlanner.VOID) {
			return null;
		}
		int y = g + 1;
		ArenaPart top = null;
		while (plan.get(x, y, z) != null) {
			top = plan.get(x, y, z);
			y++;
		}
		if (top != null && !top.solid()) {
			return null;
		}
		if (plan.get(x, y + 1, z) != null) {
			return null;
		}
		return top == ArenaPart.BRICK_SLAB || top == ArenaPart.PURPUR_SLAB ? y - 0.5 : (double) y;
	}

	/** Da para mirar em cada cristal de algum ponto do santuario (nada da arena no caminho da flecha). */
	@Test
	void crystalsCanBeShotFromThePlaza() {
		for (long seed : SEEDS) {
			ArenaPlan plan = plan(seed);
			ArenaPlanner.Ground ground = dome(seed);
			for (ArenaPlanner.Spike s : plan.spikes()) {
				boolean visible = false;
				for (int i = 0; i < 16 && !visible; i++) {
					double a = i * Math.PI / 8;
					double ex = 8 * Math.cos(a);
					double ez = 8 * Math.sin(a);
					double ey = ground.y((int) Math.round(ex), (int) Math.round(ez)) + 2.6;
					visible = clear(plan, ex, ey, ez, s.x() + 0.5, s.height() + 1.5, s.z() + 0.5);
				}
				assertTrue(visible, "cristal do pilar " + s + " escondido pela arena");
			}
		}
	}

	private static boolean clear(ArenaPlan plan, double x0, double y0, double z0, double x1, double y1, double z1) {
		double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		for (double t = 0; t < len - 2; t += 0.2) {
			double f = t / len;
			int x = (int) Math.floor(x0 + (x1 - x0) * f);
			int y = (int) Math.floor(y0 + (y1 - y0) * f);
			int z = (int) Math.floor(z0 + (z1 - z0) * f);
			ArenaPart p = plan.get(x, y, z);
			if (p != null && !p.paving()) {
				return false;
			}
		}
		return true;
	}
}
