package com.mlin.notas.end;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Planeja a Cidadela do End, a cidade que substitui a End City vanilla. Logica pura (sem Minecraft).
 *
 * <ul>
 *   <li>A cidadela traz a propria ilha (raio {@link #RADIUS}), entao tudo fica no mesmo chao: nada de pontes entre ilhas.</li>
 *   <li>Uma fortaleza central de {@link #KEEP_FLOORS} andares (~26 blocos, a End City vanilla passa de 60) e 3 ou 4
 *       torres-satelite de 2 ou 3 andares nos pontos cardeais, ligadas a fortaleza por pontes cobertas no 2o andar
 *       e por caminhos no chao.</li>
 *   <li>Cada torre tem uma coluna de elevadores no centro (um por andar e um no telhado): pular sobe, agachar desce.</li>
 *   <li>Salas: hall com sentinelas, ninho de shulkers no andar das pontes, saque/biblioteca/laboratorio e o cofre no
 *       topo com o elitro, cabeca de dragao e dois baus. Shulkers tambem vigiam as pontes por fora e os telhados.</li>
 * </ul>
 */
public final class CitadelPlanner {
	public static final int FLOOR_HEIGHT = 5;
	public static final int RADIUS = 24;
	public static final int KEEP_HALF = 5;
	public static final int KEEP_FLOORS = 5;
	public static final int TOWER_HALF = 3;
	/** Andar (0 = terreo) em que as pontes chegam. */
	public static final int BRIDGE_FLOOR = 1;
	/** Alcance maximo do elevador (blocos). */
	public static final int ELEVATOR_RANGE = 32;

	/** Norte, leste, sul, oeste (x, z). */
	private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	private enum Room {
		LOOT,
		LIBRARY,
		LAB
	}

	private CitadelPlanner() {
	}

	public static CitadelPlan plan(long seed) {
		Random random = new Random(seed);
		CitadelPlan plan = new CitadelPlan(RADIUS);

		// quais direcoes tem torre-satelite e a que distancia
		List<int[]> directions = new ArrayList<>(List.of(DIRECTIONS));
		Collections.shuffle(directions, random);
		int satellites = 3 + random.nextInt(2);
		List<int[]> sats = new ArrayList<>();
		for (int i = 0; i < satellites; i++) {
			int[] d = directions.get(i);
			int distance = 14 + random.nextInt(3);
			int floors = 2 + random.nextInt(2);
			sats.add(new int[] {d[0], d[1], distance, floors});
		}

		island(plan, seed);
		for (int[] s : sats) {
			path(plan, s[0], s[1], s[2]);
		}

		CitadelPlan.Tower keep = new CitadelPlan.Tower(0, 0, KEEP_HALF, KEEP_FLOORS);
		plan.tower(keep);
		building(plan, keep);
		for (int[] d : DIRECTIONS) {
			door(plan, keep, d[0], d[1], 0);
		}

		for (int[] s : sats) {
			CitadelPlan.Tower tower = new CitadelPlan.Tower(s[0] * s[2], s[1] * s[2], TOWER_HALF, s[3]);
			plan.tower(tower);
			building(plan, tower);
			door(plan, tower, -s[0], -s[1], 0);
			bridge(plan, s[0], s[1], s[2]);
			door(plan, keep, s[0], s[1], BRIDGE_FLOOR * FLOOR_HEIGHT);
			door(plan, tower, -s[0], -s[1], BRIDGE_FLOOR * FLOOR_HEIGHT);
			lampPosts(plan, s[0], s[1], s[2]);
		}

		keepRooms(plan, keep, random);
		for (int i = 0; i < sats.size(); i++) {
			int[] s = sats.get(i);
			satelliteRooms(plan, plan.towers().get(i + 1), -s[0], -s[1], random);
			// shulker vigiando a ponte, preso na parede de fora da fortaleza
			int px = Math.abs(s[1]);
			int pz = Math.abs(s[0]);
			int side = random.nextBoolean() ? 3 : -3;
			plan.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.SHULKER, s[0] * (KEEP_HALF + 1) + px * side,
					BRIDGE_FLOOR * FLOOR_HEIGHT + 4, s[1] * (KEEP_HALF + 1) + pz * side, s[0], s[1]));
		}
		plants(plan, seed);
		return plan;
	}

	private static long hash(long seed, int x, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return h;
	}

	/** Disco plano no topo (y=0) com o fundo arredondado como uma ilha flutuante. */
	private static void island(CitadelPlan plan, long seed) {
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				long h = hash(seed, x, z);
				double distance = Math.sqrt(x * x + z * z) + ((h & 7) / 7.0) * 1.5;
				if (distance > RADIUS) {
					continue;
				}
				plan.set(x, 0, z, CitadelPart.ISLAND_TOP);
				double t = distance / RADIUS;
				int depth = 2 + (int) Math.round(Math.sqrt(Math.max(0.0, 1.0 - t * t)) * 16) + (int) ((h >>> 8) & 1);
				for (int y = -1; y >= -depth; y--) {
					plan.set(x, y, z, CitadelPart.ISLAND);
				}
			}
		}
	}

	/** Caminho de 3 blocos no chao, do centro ate a torre na direcao (dx, dz). */
	private static void path(CitadelPlan plan, int dx, int dz, int distance) {
		int px = Math.abs(dz);
		int pz = Math.abs(dx);
		for (int k = 0; k <= distance; k++) {
			for (int p = -1; p <= 1; p++) {
				plan.set(k * dx + p * px, 0, k * dz + p * pz, CitadelPart.PATH);
			}
		}
	}

	/** Paredes, pisos, telhado com guarda-corpo, janelas, lampadas e a coluna de elevadores. */
	private static void building(CitadelPlan plan, CitadelPlan.Tower t) {
		int half = t.half();
		int top = t.roofY();
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				boolean edgeX = Math.abs(dx) == half;
				boolean edgeZ = Math.abs(dz) == half;
				boolean edge = edgeX || edgeZ;
				boolean corner = edgeX && edgeZ;
				int x = t.x() + dx;
				int z = t.z() + dz;
				for (int y = 0; y <= top; y++) {
					CitadelPart part;
					if (corner) {
						part = CitadelPart.PILLAR;
					} else if (edge) {
						boolean floorLine = y % FLOOR_HEIGHT == 0;
						int along = edgeX ? dz : dx;
						int level = y % FLOOR_HEIGHT;
						boolean window = !floorLine && (level == 2 || level == 3) && along % 3 == 0;
						part = floorLine ? CitadelPart.TRIM : window ? CitadelPart.WINDOW : CitadelPart.WALL;
					} else if (y % FLOOR_HEIGHT == 0) {
						boolean lamp = y < top && Math.abs(dx) == half - 1 && Math.abs(dz) == half - 1;
						part = lamp ? CitadelPart.LAMP : CitadelPart.FLOOR;
					} else {
						part = CitadelPart.AIR;
					}
					plan.set(x, y, z, part);
				}
				if (edge) {
					plan.set(x, top + 1, z, corner ? CitadelPart.PILLAR : CitadelPart.RAILING);
					if (corner) {
						plan.set(x, top + 2, z, CitadelPart.END_ROD);
					}
				}
			}
		}
		for (int f = 0; f <= t.floors(); f++) {
			plan.set(t.x(), f * FLOOR_HEIGHT, t.z(), CitadelPart.ELEVATOR);
		}
	}

	/** Abertura de 3 de largura x 3 de altura na parede do lado (dx, dz), acima do piso em {@code baseY}. */
	private static void door(CitadelPlan plan, CitadelPlan.Tower t, int dx, int dz, int baseY) {
		int px = Math.abs(dz);
		int pz = Math.abs(dx);
		int cx = t.x() + dx * t.half();
		int cz = t.z() + dz * t.half();
		for (int p = -1; p <= 1; p++) {
			for (int y = baseY + 1; y <= baseY + 3; y++) {
				plan.set(cx + p * px, y, cz + p * pz, CitadelPart.AIR);
			}
		}
	}

	/** Ponte coberta de guarda-corpo entre a fortaleza e a torre, no piso do andar {@link #BRIDGE_FLOOR}. */
	private static void bridge(CitadelPlan plan, int dx, int dz, int distance) {
		int px = Math.abs(dz);
		int pz = Math.abs(dx);
		int deckY = BRIDGE_FLOOR * FLOOR_HEIGHT;
		int from = KEEP_HALF + 1;
		int to = distance - TOWER_HALF - 1;
		for (int k = from; k <= to; k++) {
			for (int p = -2; p <= 2; p++) {
				int x = k * dx + p * px;
				int z = k * dz + p * pz;
				plan.set(x, deckY, z, Math.abs(p) == 2 ? CitadelPart.TRIM : CitadelPart.FLOOR);
				if (Math.abs(p) == 2) {
					plan.set(x, deckY + 1, z, CitadelPart.RAILING);
				}
				for (int y = deckY + 1; y <= deckY + 3 && Math.abs(p) < 2; y++) {
					plan.set(x, y, z, CitadelPart.AIR);
				}
			}
		}
		// pilares sob o meio da ponte, fora do caminho
		int middle = (from + to) / 2;
		for (int p = -2; p <= 2; p += 4) {
			for (int y = 1; y < deckY; y++) {
				plan.set(middle * dx + p * px, y, middle * dz + p * pz, CitadelPart.PILLAR);
			}
		}
	}

	/** Postes de luz ao lado do caminho. */
	private static void lampPosts(CitadelPlan plan, int dx, int dz, int distance) {
		int px = Math.abs(dz);
		int pz = Math.abs(dx);
		for (int k = KEEP_HALF + 3; k <= distance - TOWER_HALF - 2; k += 5) {
			for (int p = -3; p <= 3; p += 6) {
				int x = k * dx + p * px;
				int z = k * dz + p * pz;
				if (plan.get(x, 1, z) == null) {
					plan.set(x, 1, z, CitadelPart.PILLAR);
					plan.set(x, 2, z, CitadelPart.END_ROD);
				}
			}
		}
	}

	private static void spawn(CitadelPlan plan, CitadelPlan.Kind kind, CitadelPlan.Tower t, int dx, int y, int dz) {
		plan.spawn(new CitadelPlan.Spawn(kind, t.x() + dx, y, t.z() + dz, 0, 0));
	}

	private static void chest(CitadelPlan plan, CitadelPlan.Tower t, int dx, int y, int dz) {
		plan.set(t.x() + dx, y, t.z() + dz, CitadelPart.CHEST);
	}

	private static void keepRooms(CitadelPlan plan, CitadelPlan.Tower keep, Random random) {
		int in = KEEP_HALF - 1;
		// terreo: hall com duas sentinelas
		spawn(plan, CitadelPlan.Kind.SENTINEL, keep, -2, 1, 2);
		spawn(plan, CitadelPlan.Kind.SENTINEL, keep, 2, 1, -2);

		// andar das pontes: ninho de shulkers em tres cantos e um bau no quarto
		int y = BRIDGE_FLOOR * FLOOR_HEIGHT + 1;
		spawn(plan, CitadelPlan.Kind.SHULKER, keep, in, y, in);
		spawn(plan, CitadelPlan.Kind.SHULKER, keep, -in, y, in);
		spawn(plan, CitadelPlan.Kind.SHULKER, keep, in, y, -in);
		chest(plan, keep, -in, y, -in);

		// andares do meio: saque garantido + biblioteca ou laboratorio, em ordem aleatoria
		List<Room> rooms = new ArrayList<>(List.of(Room.LOOT, random.nextBoolean() ? Room.LIBRARY : Room.LAB));
		Collections.shuffle(rooms, random);
		for (int i = 0; i < rooms.size(); i++) {
			int floorY = (BRIDGE_FLOOR + 1 + i) * FLOOR_HEIGHT + 1;
			switch (rooms.get(i)) {
				case LOOT -> {
					chest(plan, keep, in, floorY, -in);
					chest(plan, keep, -in, floorY, in);
					spawn(plan, CitadelPlan.Kind.SHULKER, keep, in, floorY, in);
				}
				case LIBRARY -> {
					for (int a = -in; a <= in; a++) {
						for (int h = 0; h < 2; h++) {
							if (Math.abs(a) > 1) {
								plan.set(keep.x() + a, floorY + h, keep.z() - in, CitadelPart.BOOKSHELF);
								plan.set(keep.x() + a, floorY + h, keep.z() + in, CitadelPart.BOOKSHELF);
								plan.set(keep.x() - in, floorY + h, keep.z() + a, CitadelPart.BOOKSHELF);
								plan.set(keep.x() + in, floorY + h, keep.z() + a, CitadelPart.BOOKSHELF);
							}
						}
					}
					chest(plan, keep, 2, floorY, -2);
					spawn(plan, CitadelPlan.Kind.SHULKER, keep, -2, floorY, 2);
				}
				case LAB -> {
					plan.set(keep.x() - 3, floorY, keep.z() - 3, CitadelPart.BREWING_STAND);
					plan.set(keep.x() + 3, floorY, keep.z() - 3, CitadelPart.BREWING_STAND);
					chest(plan, keep, 0, floorY, 3);
					spawn(plan, CitadelPlan.Kind.SENTINEL, keep, -2, floorY, 2);
				}
			}
		}

		// cofre no ultimo andar: elitro na parede norte, cabeca de dragao num pedestal, dois baus, dois shulkers
		int vault = (KEEP_FLOORS - 1) * FLOOR_HEIGHT + 1;
		plan.spawn(new CitadelPlan.Spawn(CitadelPlan.Kind.ELYTRA_FRAME, keep.x() + 1, vault + 1, keep.z() - in, 0, 1));
		plan.set(keep.x() - 2, vault, keep.z() - 3, CitadelPart.PILLAR);
		plan.set(keep.x() - 2, vault + 1, keep.z() - 3, CitadelPart.DRAGON_HEAD);
		chest(plan, keep, 3, vault, -in);
		chest(plan, keep, -3, vault, in);
		spawn(plan, CitadelPlan.Kind.SHULKER, keep, in, vault, in);
		spawn(plan, CitadelPlan.Kind.SHULKER, keep, -in, vault, -in);

		// telhado: dois shulkers de vigia
		int roof = keep.roofY() + 1;
		spawn(plan, CitadelPlan.Kind.SHULKER, keep, 3, roof, 3);
		spawn(plan, CitadelPlan.Kind.SHULKER, keep, -3, roof, -3);
	}

	/** Salas da torre-satelite; (doorX, doorZ) aponta para a fortaleza (lado da porta). */
	private static void satelliteRooms(CitadelPlan plan, CitadelPlan.Tower t, int doorX, int doorZ, Random random) {
		int in = TOWER_HALF - 1;
		// cantos do fundo (longe da porta)
		List<int[]> far = new ArrayList<>();
		for (int cx = -in; cx <= in; cx += 2 * in) {
			for (int cz = -in; cz <= in; cz += 2 * in) {
				if (cx * doorX + cz * doorZ < 0) {
					far.add(new int[] {cx, cz});
				}
			}
		}
		spawn(plan, CitadelPlan.Kind.SENTINEL, t, far.get(0)[0] / 2, 1, far.get(0)[1] / 2);

		int bridgeY = BRIDGE_FLOOR * FLOOR_HEIGHT + 1;
		if (random.nextBoolean()) {
			chest(plan, t, far.get(0)[0], bridgeY, far.get(0)[1]);
			spawn(plan, CitadelPlan.Kind.SHULKER, t, far.get(1)[0], bridgeY, far.get(1)[1]);
		} else {
			spawn(plan, CitadelPlan.Kind.SHULKER, t, far.get(0)[0], bridgeY, far.get(0)[1]);
			spawn(plan, CitadelPlan.Kind.SHULKER, t, far.get(1)[0], bridgeY, far.get(1)[1]);
		}
		if (t.floors() > 2) {
			int y = 2 * FLOOR_HEIGHT + 1;
			chest(plan, t, far.get(1)[0], y, far.get(1)[1]);
			spawn(plan, CitadelPlan.Kind.SHULKER, t, far.get(0)[0], y, far.get(0)[1]);
		}
		spawn(plan, CitadelPlan.Kind.SHULKER, t, far.get(0)[0], t.roofY() + 1, far.get(0)[1]);
	}

	/** Plantas no musgo do patio. */
	private static void plants(CitadelPlan plan, long seed) {
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				if (plan.get(x, 0, z) == CitadelPart.ISLAND_TOP && plan.get(x, 1, z) == null && Math.floorMod(hash(seed + 1, x, z), 7) == 0) {
					plan.set(x, 1, z, CitadelPart.PLANT);
				}
			}
		}
	}
}
