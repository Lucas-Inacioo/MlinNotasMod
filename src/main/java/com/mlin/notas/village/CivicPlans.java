package com.mlin.notas.village;

import java.util.Random;

/**
 * Plantas das construcoes civicas e de defesa (Vilas 2.0, fase 7), em coordenadas de {@link Canvas}: Correio, Escola,
 * Torre de vigia, Quartel, Pedra de teleporte da vila, Farol comunitario, Museu e Guilda dos Exploradores. Cada nivel
 * cabe na classe de lote do tipo ({@link LotRules}). Pontos que a funcao da obra procura no mundo ficam em
 * {@link Canvas#mark}: {@code bell}, {@code archer}, {@code guard}, {@code frame} (moldura do Museu: celula de ar + lado
 * para onde olha), {@code pedestal}, {@code board}, {@code mailbox}, {@code waystone} e {@code beacon}.
 *
 * <p>Referencias de desenho (escritas do zero, nada copiado): agencia de correios medieval de pedra com balcao, escaninhos
 * de estantes e caixa de correio na fachada (a "Medieval Post Office" e as casas de comercio do Planet Minecraft e do
 * Minecraft Maps); escola de uma sala com lampiao e sineta no telhado, patio com amarelinha e balanco (modelos de
 * "Little Red Schoolhouse" das galerias de builds); torre de vigia de pedra com andares de madeira, seteiras, mata-cao
 * em balanco e sino sob um telhadinho de quatro aguas (torres de vigia de muralha medieval do Planet Minecraft); quartel
 * com beliches, armeiros e patio de treino com alvos e bonecos (quarteis medievais do Minecraft Maps); piramide em
 * degraus de sinalizador com pilares e lampioes nos cantos (as piramides de beacon das galerias de builds); museu
 * neoclassico de salao central, colunas, tapete e paredes de molduras (museus do Planet Minecraft); alojamento de
 * exploradores com quadro de avisos, mesa de mapas e fogueira (guild halls da comunidade).
 */
public final class CivicPlans {
	private CivicPlans() {
	}

	public static boolean handles(VillageProject project) {
		return switch (project) {
			case POST_OFFICE, SCHOOL, WATCHTOWER, BARRACKS, VILLAGE_WAYSTONE, BEACON, MUSEUM, EXPLORERS_GUILD -> true;
			default -> false;
		};
	}

	public static Canvas plan(VillageProject project, VillageStyle style, long seed, int level) {
		return BodyDressing.with(() -> build(project, style, seed, level));
	}

	private static Canvas build(VillageProject project, VillageStyle style, long seed, int level) {
		Random random = new Random(seed ^ (level * 0x9E3779B97F4A7C15L));
		int max = BuildingTypes.maxLevel(project.id());
		int lv = Math.max(1, Math.min(max, level));
		return switch (project) {
			case POST_OFFICE -> postOffice(style, random, lv);
			case SCHOOL -> school(style, random, lv);
			case WATCHTOWER -> watchtower(style, random, lv);
			case BARRACKS -> CivicHalls.barracks(style, random, lv);
			case VILLAGE_WAYSTONE -> waystone(style, random);
			case BEACON -> beacon(style, random, lv);
			case MUSEUM -> museum(style, random, lv);
			case EXPLORERS_GUILD -> CivicHalls.guild(style, random, lv);
			default -> throw new IllegalArgumentException("projeto sem planta civica: " + project);
		};
	}

	// ---------------------------------------------------------------- pecas

	private static void rect(Canvas c, int x0, int z0, int x1, int z1, int y, VPart part) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, y, z, part);
			}
		}
	}

	private static void column(Canvas c, int x, int z, int y0, int y1, VPart part) {
		for (int y = y0; y <= y1; y++) {
			c.set(x, y, z, part);
		}
	}

	/** Poste de lampiao: duas cercas e a lanterna em cima. */
	private static void lampPost(Canvas c, int x, int z) {
		c.set(x, 1, z, VPart.FENCE);
		c.set(x, 2, z, VPart.FENCE);
		c.set(x, 3, z, VPart.LANTERN);
	}

	/** Placa pendurada em braco de viga dos dois lados da porta (z1 = parede da frente). */
	private static void signArms(Canvas c, int mid, int z1, int dx) {
		for (int x : new int[] {mid - dx, mid + dx}) {
			c.set(x, 4, z1 + 1, VPart.BEAM, Facing.SOUTH);
			c.set(x, 3, z1 + 1, VPart.LANTERN_HANGING);
		}
	}

	// ---------------------------------------------------------------- correio

	/**
	 * Correio I: casa de pedra 9x6 com balcao e caixa de correio no meio dele, escaninhos atras e caixa de rua ao lado da
	 * porta. II: 11x7 de dois andares com sala de triagem em cima. Lote M.
	 */
	static Canvas postOffice(VillageStyle style, Random random, int level) {
		int bw = level >= 2 ? 11 : 9;
		int floors = level >= 2 ? 2 : 1;
		Canvas c = new Canvas(15, 13);
		int x0 = (15 - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = level >= 2 ? 8 : 7;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, true, true);
		int mid = (x0 + x1) / 2;
		// balcao com a caixa de correio no meio, lampioes nas pontas
		for (int x = x0 + 2; x <= x1 - 2; x++) {
			if (x == mid) {
				c.set(x, 1, z0 + 3, VPart.MAILBOX, Facing.SOUTH);
				c.mark("mailbox", x, 1, z0 + 3, Facing.SOUTH);
			} else {
				c.set(x, 1, z0 + 3, VPart.WOOD_SLAB_TOP);
			}
		}
		c.set(x0 + 2, 2, z0 + 3, VPart.LANTERN);
		c.set(x1 - 2, 2, z0 + 3, VPart.LANTERN);
		// escaninhos na parede do fundo: barris embaixo e estantes por cima
		for (int x = x0 + 3; x <= x1 - 3; x++) {
			c.set(x, 1, z0 + 1, VPart.BARREL);
			c.set(x, 2, z0 + 1, VPart.BOOKSHELF);
			c.set(x, 3, z0 + 1, VPart.BOOKSHELF);
		}
		c.set(x0 + 1, 1, z0 + 1, VPart.CARTOGRAPHY);
		c.set(x0 + 1, 1, z0 + 2, VPart.LECTERN, Facing.EAST);
		// sala de espera: banco, flores e tapete ate o balcao
		c.set(x0 + 1, 1, z1 - 1, VPart.WOOD_STAIRS, Facing.EAST);
		c.set(x0 + 1, 1, z1 - 2, VPart.WOOD_STAIRS, Facing.EAST);
		c.set(x1 - 1, 1, z1 - 1, VPart.FLOWER_POT);
		for (int z = z0 + 4; z <= z1 - 1; z++) {
			c.set(mid, 1, z, VPart.CARPET);
		}
		if (floors > 1) {
			// triagem em cima: mesas de barris, mapas e bau de cartas
			int y = BuildingPlans.floorY(1) + 1;
			for (int x = x0 + 2; x <= x1 - 3; x += 2) {
				c.set(x, y, z0 + 1, VPart.BARREL);
				c.set(x, y + 1, z0 + 1, VPart.BARREL);
			}
			for (int x = x0 + 2; x <= x1 - 3; x++) {
				c.set(x, y, z0 + 4, VPart.WOOD_SLAB_TOP);
			}
			c.set(mid, y + 1, z0 + 4, VPart.LANTERN);
			c.set(mid, y, z0 + 3, VPart.WOOD_STAIRS, Facing.SOUTH);
			c.set(mid, y, z0 + 5, VPart.WOOD_STAIRS, Facing.NORTH);
			c.set(x0 + 1, y, z1 - 1, VPart.CHEST, Facing.EAST);
			c.set(x0 + 1, y, z0 + 2, VPart.CARTOGRAPHY);
			Buildings.chimney(c, x0 + 1, z1 - 3, y, 2);
		}
		// caixa de correio de rua ao lado da porta e lampioes
		c.set(mid + 3, 1, z1 + 2, VPart.MAILBOX, Facing.SOUTH);
		c.mark("mailbox", mid + 3, 1, z1 + 2, Facing.SOUTH);
		lampPost(c, mid - 3, z1 + 2);
		signArms(c, mid, z1, 2);
		BuildingPlans.frontYard(c, mid);
		c.spawn(x0 + 1, 1, z0 + 2 + 1);
		return c;
	}

	// ---------------------------------------------------------------- escola

	/**
	 * Escola I: sala de aula 9x7 com atril do professor, estantes, carteiras e sineta de verdade no telhado; patio com
	 * amarelinha e balanco. II: 11x8 de dois andares com biblioteca e berçario em cima. Lote M.
	 */
	static Canvas school(VillageStyle style, Random random, int level) {
		int bw = level >= 2 ? 11 : 9;
		int floors = level >= 2 ? 2 : 1;
		Canvas c = new Canvas(15, 13);
		int x0 = (15 - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = level >= 2 ? 8 : 7;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, true, false);
		int mid = (x0 + x1) / 2;
		// professor: atril, mesa de mapas e estantes
		c.set(mid, 1, z0 + 1, VPart.LECTERN, Facing.SOUTH);
		c.set(mid - 2, 1, z0 + 1, VPart.CARTOGRAPHY);
		for (int y = 1; y <= 2; y++) {
			c.set(x0 + 1, y, z0 + 1, VPart.BOOKSHELF);
			c.set(x0 + 2, y, z0 + 1, VPart.BOOKSHELF);
		}
		c.set(mid + 2, 1, z0 + 1, VPart.BARREL);
		c.set(mid + 2, 2, z0 + 1, VPart.LANTERN);
		// carteiras: mesinha de laje com a cadeira atras, fileiras dos dois lados do corredor
		int rows = level >= 2 ? 2 : 1;
		for (int r = 0; r < rows; r++) {
			int zd = z0 + 3 + 2 * r;
			for (int x : new int[] {x0 + 2, x0 + 4, x1 - 4, x1 - 2}) {
				if (Math.abs(x - mid) < 2) {
					continue;
				}
				c.set(x, 1, zd, VPart.WOOD_SLAB_TOP);
				c.set(x, 1, zd + 1, VPart.WOOD_STAIRS, Facing.NORTH);
			}
		}
		c.set(x1 - 1, 1, z1 - 1, VPart.CHEST, Facing.WEST);
		if (floors > 1) {
			int y = BuildingPlans.floorY(1) + 1;
			// biblioteca e bercario
			for (int x = x0 + 1; x <= x1 - 3; x++) {
				c.set(x, y, z0 + 1, VPart.BOOKSHELF);
				c.set(x, y + 1, z0 + 1, VPart.BOOKSHELF);
			}
			Buildings.bed(c, x0 + 2, y, z0 + 4, Facing.NORTH);
			Buildings.bed(c, x0 + 4, y, z0 + 4, Facing.NORTH);
			Buildings.bed(c, x0 + 6, y, z0 + 4, Facing.NORTH);
			c.set(x0 + 1, y, z1 - 1, VPart.LECTERN, Facing.EAST);
			c.set(mid, y, z1 - 2, VPart.CARPET_ALT);
			c.set(mid + 1, y, z1 - 2, VPart.CARPET_ALT);
			BuildingPlans.lamp(c, x1 - 1, y, z1 - 1);
		}
		BuildingPlans.cupola(c, mid, (z0 + z1) / 2);
		c.mark("bell", mid, Buildings.roofTop(c, mid, (z0 + z1) / 2) + 3, (z0 + z1) / 2, null);
		// patio: amarelinha na frente, balanco de lado e cerca viva
		for (int i = 0; i < 4 && z1 + 2 + i < c.d() - 1; i++) {
			c.set(mid, 0, z1 + 2 + i, i % 2 == 0 ? VPart.PAVING : VPart.PAVING_ALT);
		}
		if (z1 + 4 < c.d() - 1) {
			c.set(mid - 1, 0, z1 + 4, VPart.PAVING_ALT);
			c.set(mid + 1, 0, z1 + 4, VPart.PAVING);
		}
		int sz = z1 + 3;
		for (int x : new int[] {x1 - 2, x1}) {
			column(c, x, sz, 1, 3, VPart.FENCE);
		}
		c.set(x1 - 1, 3, sz, VPart.BEAM, Facing.EAST);
		c.set(x1 - 2, 3, sz, VPart.BEAM, Facing.EAST);
		c.set(x1, 3, sz, VPart.BEAM, Facing.EAST);
		c.set(x1 - 1, 2, sz, VPart.TRAPDOOR_TOP);
		c.set(x0 + 1, 1, z1 + 3, VPart.WOOD_STAIRS, Facing.EAST);
		c.set(x0 + 1, 1, z1 + 4, VPart.WOOD_STAIRS, Facing.EAST);
		c.set(x0 + 3, 1, z1 + 3, VPart.FLOWER_POT);
		lampPost(c, mid - 3, z1 + 2);
		BuildingPlans.frontYard(c, mid);
		c.spawn(mid - 1, 1, z0 + 2);
		return c;
	}

	// ---------------------------------------------------------------- torre de vigia

	/**
	 * Torre de vigia: base de pedra 5x5 (I) ou 7x7 (II-III) em 3-4 andares com ladeira de mao, seteiras e faixas de viga
	 * entre os andares; no topo, plataforma com ameias e um quadro de postes e viga que segura o sino (de verdade), com
	 * telhado de quatro aguas a partir do II e, no III, mata-cao em balanco e quatro postos de arqueiro. Lote S (11x11).
	 */
	static Canvas watchtower(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(11, 11);
		int half = level >= 2 ? 3 : 2;
		int cx = 5;
		int cz = 5;
		int x0 = cx - half;
		int x1 = cx + half;
		int z0 = cz - half;
		int z1 = cz + half;
		int top = level >= 2 ? 16 : 12;
		boolean desert = style.flatRoof();
		// base: alicerce e assoalho
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				c.set(x, 0, z, edge ? VPart.FOUNDATION : VPart.FLOOR);
			}
		}
		// fuste de pedra com cunhais, faixas de viga nos andares e seteiras
		for (int y = 1; y < top; y++) {
			boolean band = y % 4 == 0;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean xEdge = z == z0 || z == z1;
					boolean zEdge = x == x0 || x == x1;
					if (!xEdge && !zEdge) {
						continue;
					}
					boolean corner = xEdge && zEdge;
					VPart part;
					if (corner) {
						part = VPart.BRICK;
					} else if (band) {
						part = VPart.BEAM;
					} else {
						part = Math.floorMod(x * 7 + y * 13 + z * 5, 4) == 0 ? VPart.STONE_ALT : VPart.STONE;
					}
					c.set(x, y, z, part, band && !corner ? (xEdge ? Facing.EAST : Facing.SOUTH) : null);
				}
			}
		}
		// seteiras no meio das quatro faces, entre as faixas
		for (int y = 2; y < top; y += 4) {
			if (y + 1 >= top) {
				break;
			}
			c.set(cx, y + 1, z0, VPart.WINDOW, Facing.EAST);
			c.set(cx, y + 1, z1, VPart.WINDOW, Facing.EAST);
			c.set(x0, y + 1, cz, VPart.WINDOW, Facing.SOUTH);
			c.set(x1, y + 1, cz, VPart.WINDOW, Facing.SOUTH);
		}
		// porta e degraus
		Buildings.door(c, cx, 1, z1, Facing.SOUTH);
		c.set(cx, 0, z1 + 1, VPart.PAVING);
		c.set(cx - 1, 0, z1 + 1, VPart.PAVING_ALT);
		c.set(cx + 1, 0, z1 + 1, VPart.PAVING_ALT);
		c.set(cx - 1, 1, z1 + 1, VPart.FENCE);
		c.set(cx - 1, 2, z1 + 1, VPart.LANTERN);
		c.set(cx + 1, 1, z1 + 1, VPart.FENCE);
		c.set(cx + 1, 2, z1 + 1, VPart.LANTERN);
		// andares de madeira com buraco da escada (canto do fundo a direita)
		int lx = x1 - 1;
		int lz = z0 + 1;
		for (int y = 5; y < top; y += 4) {
			for (int x = x0 + 1; x < x1; x++) {
				for (int z = z0 + 1; z < z1; z++) {
					if (x != lx || z != lz) {
						c.set(x, y, z, VPart.FLOOR);
					}
				}
			}
		}
		// mobilia: deposito e beliche no terreo, mesa e mapas nos andares
		c.set(x0 + 1, 1, z0 + 1, VPart.BARREL);
		c.set(x0 + 1, 2, z0 + 1, VPart.LANTERN);
		c.set(x0 + 1, 1, z0 + 2, VPart.CHEST, Facing.EAST);
		Buildings.bed(c, x0 + 1, 6, z1 - 1, Facing.NORTH);
		c.set(x1 - 1, 6, z1 - 1, VPart.CRAFTING);
		c.set(x0 + 1, 6, z0 + 1, VPart.CARTOGRAPHY);
		if (top > 12) {
			c.set(x0 + 1, 10, z0 + 1, VPart.FLETCHING);
			c.set(x0 + 1, 10, z0 + 2, VPart.BARREL);
			c.set(x1 - 1, 10, z1 - 1, VPart.CHEST, Facing.WEST);
			c.set(x0 + 1, 11, z0 + 1, VPart.LANTERN);
		}
		// escada de mao do terreo ate a plataforma
		for (int y = 1; y <= top; y++) {
			c.set(lx, y, lz, VPart.LADDER, Facing.SOUTH);
		}
		// plataforma
		int reach = level >= 3 ? 1 : 0;
		for (int x = x0 - reach; x <= x1 + reach; x++) {
			for (int z = z0 - reach; z <= z1 + reach; z++) {
				boolean outer = x < x0 || x > x1 || z < z0 || z > z1;
				if (x == lx && z == lz) {
					continue;
				}
				c.set(x, top, z, outer ? (corner(x, z, x0 - reach, x1 + reach, z0 - reach, z1 + reach) ? VPart.BRICK : VPart.STONE_SLAB) : VPart.FLOOR);
			}
		}
		if (reach > 0) {
			// mata-cao: misulas de degrau virado para baixo sob o balanco
			for (int x = x0; x <= x1; x++) {
				c.set(x, top - 1, z0 - 1, VPart.STONE_STAIRS_TOP, Facing.SOUTH);
				c.set(x, top - 1, z1 + 1, VPart.STONE_STAIRS_TOP, Facing.NORTH);
			}
			for (int z = z0; z <= z1; z++) {
				c.set(x0 - 1, top - 1, z, VPart.STONE_STAIRS_TOP, Facing.EAST);
				c.set(x1 + 1, top - 1, z, VPart.STONE_STAIRS_TOP, Facing.WEST);
			}
		}
		// ameias no contorno da plataforma
		int ax0 = x0 - reach;
		int ax1 = x1 + reach;
		int az0 = z0 - reach;
		int az1 = z1 + reach;
		for (int x = ax0; x <= ax1; x++) {
			for (int z = az0; z <= az1; z++) {
				boolean edge = x == ax0 || x == ax1 || z == az0 || z == az1;
				if (!edge) {
					continue;
				}
				boolean merlon = corner(x, z, ax0, ax1, az0, az1) || (x + z) % 2 == 0;
				c.set(x, top + 1, z, merlon ? VPart.BRICK : VPart.STONE_SLAB);
			}
		}
		// quadro do sino: dois postes, viga e o sino pendurado no meio
		column(c, cx - 1, cz, top + 1, top + 3, VPart.FENCE);
		column(c, cx + 1, cz, top + 1, top + 3, VPart.FENCE);
		for (int x = cx - 1; x <= cx + 1; x++) {
			c.set(x, top + 3, cz, VPart.BEAM, Facing.EAST);
		}
		c.set(cx, top + 2, cz, VPart.BELL);
		c.mark("bell", cx, top + 2, cz, null);
		if (level >= 2 && !desert) {
			// telhadinho de quatro aguas sobre o quadro do sino
			for (int x = cx - 2; x <= cx + 2; x++) {
				for (int z = cz - 1; z <= cz + 1; z++) {
					Facing in = z == cz - 1 ? Facing.SOUTH : z == cz + 1 ? Facing.NORTH : x < cx ? Facing.EAST : Facing.WEST;
					boolean edge = Math.abs(x - cx) == 2 || z != cz;
					c.set(x, top + 4, z, edge ? VPart.ROOF : VPart.ROOF_BLOCK, edge ? in : null);
				}
			}
			c.set(cx - 1, top + 5, cz, VPart.ROOF, Facing.EAST);
			c.set(cx + 1, top + 5, cz, VPart.ROOF, Facing.WEST);
			c.set(cx, top + 5, cz, VPart.ROOF_BLOCK);
			c.set(cx, top + 6, cz, VPart.FENCE);
		} else if (level >= 2) {
			for (int x = cx - 1; x <= cx + 1; x++) {
				c.set(x, top + 4, cz, VPart.ROOF_SLAB);
			}
		}
		// postos de arqueiro nos quatro cantos da plataforma (III) e lampioes
		if (level >= 3) {
			int[][] posts = {{ax0 + 1, az0 + 1}, {ax1 - 1, az0 + 1}, {ax0 + 1, az1 - 1}, {ax1 - 1, az1 - 1}};
			for (int[] p : posts) {
				c.mark("archer", p[0], top + 1, p[1], null);
			}
			c.set(ax0 + 1, top + 1, cz, VPart.BARREL);
			c.set(ax1 - 1, top + 1, cz, VPart.HAY);
			c.set(ax0 + 1, top + 2, cz, VPart.LANTERN);
		}
		BuildingPlans.frontYard(c, cx);
		c.doorX(cx);
		return c;
	}

	private static boolean corner(int x, int z, int x0, int x1, int z0, int z1) {
		return (x == x0 || x == x1) && (z == z0 || z == z1);
	}

	// ---------------------------------------------------------------- pedra de teleporte da vila

	/** Pedra de teleporte da vila: pracinha calcada 7x7 com a pedra no meio, bancos nos lados e lampioes nos cantos. Lote T. */
	static Canvas waystone(VillageStyle style, Random random) {
		Canvas c = new Canvas(7, 7);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean ring = x == 0 || x == 6 || z == 0 || z == 6;
				boolean mid = Math.abs(x - 3) <= 1 && Math.abs(z - 3) <= 1;
				c.set(x, 0, z, ring ? VPart.PAVING_ALT : mid ? VPart.BRICK : VPart.PAVING);
			}
		}
		c.set(3, 1, 3, VPart.WAYSTONE);
		c.set(3, 2, 3, VPart.WAYSTONE_TOP);
		c.mark("waystone", 3, 1, 3, null);
		for (int[] p : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			c.set(p[0], 1, p[1], VPart.STONE_WALL);
			c.set(p[0], 2, p[1], VPart.LANTERN);
		}
		c.set(1, 1, 3, VPart.STONE_STAIRS, Facing.EAST);
		c.set(5, 1, 3, VPart.STONE_STAIRS, Facing.WEST);
		c.set(3, 1, 1, VPart.STONE_STAIRS, Facing.SOUTH);
		c.set(2, 1, 5, VPart.FLOWER_POT);
		c.set(4, 1, 5, VPart.FLOWER_POT);
		c.doorX(3);
		return c;
	}

	// ---------------------------------------------------------------- farol comunitario

	/**
	 * Farol comunitario: piramide em degraus de blocos de metal (1 a 4 camadas; metal do nivel: ferro, ouro, diamante,
	 * netherite, celestio) com o sinalizador no topo, pilares com lampioes nos cantos do patio e degraus de pedra nos
	 * quatro lados; no V, obelisco de cantos mais alto e braseiros. Lote S (11x11).
	 */
	static Canvas beacon(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(11, 11);
		int layers = Math.min(4, level);
		VPart metal = switch (level) {
			case 1 -> VPart.METAL_1;
			case 2 -> VPart.METAL_2;
			case 3 -> VPart.METAL_3;
			case 4 -> VPart.METAL_4;
			default -> VPart.METAL_5;
		};
		int cx = 5;
		int cz = 5;
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 11; z++) {
				boolean ring = x == 0 || x == 10 || z == 0 || z == 10;
				c.set(x, 0, z, ring ? VPart.BRICK : ((x + z) % 2 == 0 ? VPart.PAVING : VPart.PAVING_ALT));
			}
		}
		for (int i = 0; i < layers; i++) {
			int y = 1 + i;
			int h = layers - i;
			for (int x = cx - h; x <= cx + h; x++) {
				for (int z = cz - h; z <= cz + h; z++) {
					c.set(x, y, z, metal);
				}
			}
		}
		c.set(cx, layers + 1, cz, VPart.BEACON);
		c.mark("beacon", cx, layers + 1, cz, null);
		// pilares de canto com lampiao (mais altos no V)
		int ph = level >= 5 ? 4 : 2;
		for (int[] p : new int[][] {{0, 0}, {10, 0}, {0, 10}, {10, 10}}) {
			column(c, p[0], p[1], 1, ph, VPart.STONE_WALL);
			c.set(p[0], 1, p[1], VPart.BRICK);
			c.set(p[0], ph + 1, p[1], VPart.LANTERN);
		}
		// degraus de pedra em volta da base (meio das quatro faces)
		int h = layers;
		c.set(cx, 1, cz + h + 1, VPart.STONE_STAIRS, Facing.NORTH);
		c.set(cx, 1, cz - h - 1, VPart.STONE_STAIRS, Facing.SOUTH);
		c.set(cx + h + 1, 1, cz, VPart.STONE_STAIRS, Facing.WEST);
		c.set(cx - h - 1, 1, cz, VPart.STONE_STAIRS, Facing.EAST);
		if (level <= 2) {
			c.set(2, 1, 8, VPart.FLOWER_POT);
			c.set(8, 1, 8, VPart.FLOWER_POT);
		}
		c.doorX(cx);
		return c;
	}

	// ---------------------------------------------------------------- museu

	/**
	 * Museu I: salao 13x8 de pedra com tapete, pedestal de doacao no centro, colunas, estantes do catalogo e molduras nas
	 * paredes. II: 15 de largura e galeria no andar de cima. III: 17 de largura com portico de colunas na entrada. Lote L.
	 */
	static Canvas museum(VillageStyle style, Random random, int level) {
		int bw = level >= 3 ? 17 : level >= 2 ? 15 : 13;
		int floors = level >= 2 ? 2 : 1;
		Canvas c = new Canvas(19, 13);
		int x0 = (19 - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = 8;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, true, true);
		int mid = (x0 + x1) / 2;
		// tapete ate o pedestal, colunas e vitrines
		for (int z = z0 + 5; z <= z1 - 1; z++) {
			c.set(mid, 1, z, VPart.CARPET_ALT);
		}
		c.set(mid, 1, z0 + 4, VPart.PEDESTAL, Facing.SOUTH);
		c.mark("pedestal", mid, 1, z0 + 4, Facing.SOUTH);
		c.set(mid - 1, 1, z0 + 4, VPart.STONE_SLAB_TOP);
		c.set(mid + 1, 1, z0 + 4, VPart.STONE_SLAB_TOP);
		c.set(mid - 1, 2, z0 + 4, VPart.LANTERN);
		c.set(mid + 1, 2, z0 + 4, VPart.LANTERN);
		for (int dx : new int[] {-4, 4}) {
			for (int z : new int[] {z0 + 3, z0 + 6}) {
				column(c, mid + dx, z, 1, 3, VPart.STONE_WALL);
				c.set(mid + dx, 1, z, VPart.BRICK);
				c.set(mid + dx, 4, z, VPart.BRICK);
			}
		}
		for (int dx : new int[] {-2, 2}) {
			c.set(mid + dx, 1, z0 + 7, VPart.STONE);
			c.set(mid + dx, 2, z0 + 7, VPart.WINDOW, Facing.EAST);
		}
		// curador: atril e estantes do catalogo
		c.set(x0 + 1, 1, z0 + 2, VPart.LECTERN, Facing.EAST);
		for (int y = 1; y <= 2; y++) {
			c.set(x0 + 1, y, z0 + 3, VPart.BOOKSHELF);
		}
		c.set(x1 - 1, 1, z1 - 1, VPart.BARREL);
		c.set(x1 - 1, 2, z1 - 1, VPart.LANTERN);
		frames(c, x0, z0, x1, z1, 2, 3);
		if (floors > 1) {
			int y = BuildingPlans.floorY(1) + 1;
			for (int x = x0 + 2; x <= x1 - 3; x++) {
				c.set(x, y, z0 + 4, VPart.STONE_SLAB_TOP);
				if ((x - x0) % 2 == 0) {
					c.set(x, y + 1, z0 + 4, VPart.LANTERN);
				}
			}
			c.set(x0 + 1, y, z1 - 1, VPart.BOOKSHELF);
			c.set(x0 + 1, y + 1, z1 - 1, VPart.BOOKSHELF);
			c.set(x1 - 1, y, z1 - 1, VPart.LECTERN, Facing.WEST);
			for (int z = z0 + 5; z <= z1 - 1; z++) {
				c.set(mid, y, z, VPart.CARPET_ALT);
			}
			frames(c, x0, z0, x1, z1, y, y + 1);
		}
		if (level >= 3) {
			// portico: lajes e colunas de pedra sobre a escadaria
			for (int x = mid - 3; x <= mid + 3; x++) {
				for (int z = z1 + 1; z <= z1 + 2; z++) {
					c.set(x, 0, z, VPart.PAVING_ALT);
					c.set(x, 4, z, VPart.STONE_SLAB);
				}
			}
			for (int x : new int[] {mid - 3, mid - 1, mid + 1, mid + 3}) {
				column(c, x, z1 + 2, 1, 3, VPart.STONE_WALL);
				c.set(x, 1, z1 + 2, VPart.BRICK);
				c.set(x, 4, z1 + 2, VPart.BRICK);
			}
		}
		for (int z = z1 + (level >= 3 ? 3 : 1); z < c.d(); z++) {
			c.set(mid, 0, z, VPart.PAVING);
		}
		lampPost(c, mid - 4, c.d() - 2);
		lampPost(c, mid + 4, c.d() - 2);
		BuildingPlans.frontYard(c, mid);
		c.spawn(x0 + 2, 1, z0 + 2);
		return c;
	}

	/** Molduras nas quatro paredes interiores entre as alturas {@code yLow} e {@code yHigh} (so em celula livre). */
	private static void frames(Canvas c, int x0, int z0, int x1, int z1, int yLow, int yHigh) {
		for (int y = yLow; y <= yHigh; y++) {
			for (int x = x0 + 1; x < x1; x++) {
				addFrame(c, x, y, z0 + 1, Facing.SOUTH);
				addFrame(c, x, y, z1 - 1, Facing.NORTH);
			}
			for (int z = z0 + 2; z < z1 - 1; z++) {
				addFrame(c, x0 + 1, y, z, Facing.EAST);
				addFrame(c, x1 - 1, y, z, Facing.WEST);
			}
		}
	}

	/** Moldura na celula livre {@code (x, y, z)} presa a um bloco inteiro atras (nunca janela, porta ou estante). */
	private static void addFrame(Canvas c, int x, int y, int z, Facing facing) {
		if (c.get(x, y, z) != null) {
			return;
		}
		VPart behind = c.part(x - facing.dx, y, z - facing.dz);
		if (behind == null || !behind.solid() || behind == VPart.WINDOW || behind == VPart.BOOKSHELF || behind == VPart.IRON_BARS) {
			return;
		}
		c.mark("frame", x, y, z, facing);
	}
}
