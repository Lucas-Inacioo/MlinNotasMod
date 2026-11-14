package com.mlin.notas.village;

import java.util.List;
import java.util.Random;

/**
 * Plantas das construcoes da vila (logica pura), em coordenadas locais de {@link Canvas} (frente/porta no lado +z).
 *
 * <p>Referencias de desenho (escritas do zero, nada copiado): enxaimel medieval com 1 fiada de pedra na base, postes
 * de tronco a cada 3 blocos, viga no topo e reboco claro entre as vigas (guia "How to Build a Medieval House" do
 * Instructables e o tutorial de casas medievais do Playgama); telhado de duas aguas com inclinacao de 1 degrau por
 * bloco, beiral de 1 bloco e oitao com janelinha (lista de casas medievais do Switchblade Gaming e as casas de
 * enxaimel de dois andares do Shulkr); poco de praca com aro de pedra, 4 postes e telhado piramidal (Medieval Town
 * Well do GrabCraft); barracas de mercado com toldo de la listrada sobre postes de cerca e balcao (mercados medievais
 * da Sportskeeda); estalagem de piso terreo de pedra com andar de madeira, lareira e quartos em cima, e forja aberta
 * na frente com chamine sobre o alto-forno (cidades medievais Andreth e Gelwenton do Planet Minecraft); vilas em
 * niveis aldeia/vila/cidade por bioma (ChoiceTheorem's Overhauled Village).
 */
public final class Buildings {
	private static final VPart[] HOUSE_JOBS = {VPart.FLETCHING, VPart.CARTOGRAPHY, VPart.LOOM, VPart.SMOKER, VPart.BARREL, VPart.CAULDRON,
			VPart.BREWING, VPart.STONECUTTER};

	private Buildings() {
	}

	/** Planta do projeto (a {@code variant} escolhe entre modelos; a semente muda detalhes). */
	public static Canvas plan(VillageProject project, VillageStyle style, long seed) {
		Random random = new Random(seed);
		return switch (project) {
			case HOUSE -> random.nextInt(3) == 0 ? townhouse(style, random) : cottage(style, random);
			case FARM -> farm(style, random);
			case MARKET -> market(style, random);
			case SMITHY -> smithy(style, random);
			case INN -> inn(style, random);
			case LIBRARY -> library(style, random);
			case PLAZA -> well(style, random);
			case TOWN_HALL -> BuildingPlans.townHall(style, random, 1);
			default -> {
				if (CivicPlans.handles(project)) {
					yield CivicPlans.plan(project, style, seed, 1);
				}
				throw new IllegalArgumentException("projeto sem planta: " + project);
			}
		};
	}

	// ---------------------------------------------------------------- pecas comuns

	/** Assoalho: borda de alicerce e miolo de tabuas, na camada do chao. */
	static void floor(Canvas c, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				c.set(x, 0, z, edge ? VPart.FOUNDATION : VPart.FLOOR);
			}
		}
	}

	/** Quintal de grama em todo o lote (o que nao for construcao). */
	static void yard(Canvas c) {
		for (int x = 0; x < c.w(); x++) {
			for (int z = 0; z < c.d(); z++) {
				c.setIfAbsent(x, 0, z, VPart.SOIL);
			}
		}
	}

	/**
	 * Paredes do retangulo de y0 a y1. Enxaimel: cantos e postes de tronco a cada 3 blocos, reboco entre eles, viga
	 * deitada no topo e (se {@code plinth}) uma fiada de pedra na base. {@code stone}: alvenaria de pedra com cantos
	 * de pedra de destaque. No deserto, tudo arenito liso com cantos de arenito lapidado.
	 */
	static void walls(Canvas c, VillageStyle style, int x0, int z0, int x1, int z1, int y0, int y1, boolean stone, boolean plinth) {
		boolean desert = style.flatRoof();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean xEdge = z == z0 || z == z1;
				boolean zEdge = x == x0 || x == x1;
				if (!xEdge && !zEdge) {
					continue;
				}
				boolean corner = xEdge && zEdge;
				int along = xEdge ? x - x0 : z - z0;
				boolean post = corner || along % 3 == 0;
				Facing axis = xEdge ? Facing.EAST : Facing.SOUTH;
				for (int y = y0; y <= y1; y++) {
					VPart part;
					Facing dir = null;
					if (desert) {
						part = corner || y == y1 ? VPart.BRICK : (post && !stone ? VPart.BRICK : VPart.PLASTER);
					} else if (stone) {
						if (corner) {
							part = VPart.BRICK;
						} else if (y == y1) {
							part = VPart.BEAM;
							dir = axis;
						} else {
							part = Math.floorMod(x * 7 + y * 13 + z * 5, 5) == 0 ? VPart.STONE_ALT : VPart.STONE;
						}
					} else if (corner) {
						part = VPart.LOG;
					} else if (y == y1) {
						part = VPart.BEAM;
						dir = axis;
					} else if (plinth && y == y0) {
						part = VPart.STONE;
					} else if (post) {
						part = VPart.LOG;
					} else {
						part = VPart.PLASTER;
					}
					c.set(x, y, z, part, dir);
				}
			}
		}
	}

	/** Janela na parede que olha para {@code out}, com venezianas dos lados de fora. */
	static void window(Canvas c, int x, int y, int z, Facing out, boolean shutters) {
		c.set(x, y, z, VPart.WINDOW, out.alongX() ? Facing.SOUTH : Facing.EAST);
		if (shutters) {
			Facing side = out.rotate(1);
			c.set(x + out.dx + side.dx, y, z + out.dz + side.dz, VPart.SHUTTER, out);
			c.set(x + out.dx - side.dx, y, z + out.dz - side.dz, VPart.SHUTTER, out);
		}
	}

	static void door(Canvas c, int x, int y, int z, Facing out) {
		c.set(x, y, z, VPart.DOOR, out);
		c.set(x, y + 1, z, VPart.DOOR_TOP, out);
	}

	/** Cama: pe em (x, y, z), cabeceira para o lado {@code head}. */
	static void bed(Canvas c, int x, int y, int z, Facing head) {
		c.set(x, y, z, VPart.BED, head);
		c.set(x + head.dx, y, z + head.dz, VPart.BED_HEAD, head);
	}

	/**
	 * Telhado sobre as paredes (x0..x1, z0..z1) com topo em {@code baseY}. Duas aguas com beiral de 1 bloco e cumeeira
	 * ao longo de x ({@code alongX}) ou z; oitoes fechados com reboco e janelinha. No deserto, laje plana com parapeito.
	 */
	static void roof(Canvas c, VillageStyle style, int x0, int z0, int x1, int z1, int baseY, boolean alongX) {
		if (style.flatRoof()) {
			for (int x = x0 + 1; x < x1; x++) {
				for (int z = z0 + 1; z < z1; z++) {
					c.set(x, baseY, z, VPart.ROOF_BLOCK);
				}
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					if (edge) {
						c.set(x, baseY + 1, z, corner ? VPart.BRICK : VPart.ROOF_SLAB);
					}
				}
			}
			return;
		}
		// a = coordenada ao longo da cumeeira, b = atravessando (as aguas descem para os lados de b)
		int a0 = (alongX ? x0 : z0) - 1;
		int a1 = (alongX ? x1 : z1) + 1;
		int b0 = (alongX ? z0 : x0) - 1;
		int b1 = (alongX ? z1 : x1) + 1;
		int ends0 = alongX ? x0 : z0;
		int ends1 = alongX ? x1 : z1;
		Facing up0 = alongX ? Facing.SOUTH : Facing.EAST;
		for (int i = 0; ; i++) {
			int lo = b0 + i;
			int hi = b1 - i;
			int y = baseY + i;
			if (lo > hi) {
				break;
			}
			for (int a = a0; a <= a1; a++) {
				if (lo == hi) {
					put(c, alongX, a, y, lo, VPart.ROOF_SLAB, null);
				} else {
					put(c, alongX, a, y, lo, VPart.ROOF, up0);
					put(c, alongX, a, y, hi, VPart.ROOF, up0.opposite());
				}
			}
			// oitoes: triangulo de reboco nas duas pontas, com janelinha no meio
			if (i >= 1) {
				for (int b = lo + 1; b <= hi - 1; b++) {
					put(c, alongX, ends0, y, b, VPart.PLASTER, null);
					put(c, alongX, ends1, y, b, VPart.PLASTER, null);
				}
				int mid = (b0 + b1) / 2;
				if (i == 2 && hi - lo >= 4) {
					// vidraca ao longo da parede do oitao
					Facing pane = alongX ? Facing.SOUTH : Facing.EAST;
					put(c, alongX, ends0, y, mid, VPart.WINDOW, pane);
					put(c, alongX, ends1, y, mid, VPart.WINDOW, pane);
				}
			}
			if (lo == hi) {
				break;
			}
		}
	}

	private static void put(Canvas c, boolean alongX, int a, int y, int b, VPart part, Facing dir) {
		if (alongX) {
			c.set(a, y, b, part, dir);
		} else {
			c.set(b, y, a, part, dir);
		}
	}

	/** Maior y de telhado na coluna (para a chamine passar por cima). */
	static int roofTop(Canvas c, int x, int z) {
		int top = 0;
		for (int y = 0; y < 40; y++) {
			VPart p = c.part(x, y, z);
			if (p == VPart.ROOF || p == VPart.ROOF_SLAB || p == VPart.ROOF_BLOCK || p == VPart.ROOF_TOP) {
				top = y;
			}
		}
		return top;
	}

	/** Chamine de pedra de y0 ate {@code extra} blocos acima do telhado daquela coluna, com fogueira (fumaca) no topo. */
	static void chimney(Canvas c, int x, int z, int y0, int extra) {
		int top = Math.max(roofTop(c, x, z), y0) + extra;
		for (int y = y0; y <= top; y++) {
			c.set(x, y, z, VPart.BRICK);
		}
		c.set(x, top + 1, z, VPart.CAMPFIRE);
	}

	static VPart houseJob(Random random) {
		return HOUSE_JOBS[random.nextInt(HOUSE_JOBS.length)];
	}

	// ---------------------------------------------------------------- casas

	/** Chale de enxaimel 7x7 com telhado de duas aguas, chamine externa, venezianas e canteiros. Lote 9x9. */
	static Canvas cottage(VillageStyle style, Random random) {
		Canvas c = new Canvas(9, 9);
		floor(c, 1, 1, 7, 7);
		walls(c, style, 1, 1, 7, 7, 1, 4, false, true);
		// porta com batentes de tronco
		door(c, 4, 1, 7, Facing.SOUTH);
		if (!style.flatRoof()) {
			for (int y = 1; y <= 3; y++) {
				c.set(3, y, 7, VPart.LOG);
				c.set(5, y, 7, VPart.LOG);
			}
		}
		c.set(4, 3, 7, style.flatRoof() ? VPart.BRICK : VPart.BEAM, Facing.EAST);
		window(c, 2, 2, 7, Facing.SOUTH, true);
		window(c, 6, 2, 7, Facing.SOUTH, true);
		window(c, 1, 2, 3, Facing.WEST, false);
		window(c, 1, 2, 5, Facing.WEST, false);
		window(c, 7, 2, 3, Facing.EAST, false);
		window(c, 7, 2, 5, Facing.EAST, false);
		window(c, 5, 2, 1, Facing.NORTH, true);
		roof(c, style, 1, 1, 7, 7, 4, true);
		// chamine encostada na parede de tras
		chimney(c, 2, 0, 0, style.flatRoof() ? 2 : 3);
		// moveis
		bed(c, 2, 1, 3, Facing.NORTH);
		c.set(3, 1, 2, VPart.CRAFTING);
		c.set(6, 1, 2, VPart.CHEST, Facing.SOUTH);
		c.set(6, 1, 3, houseJob(random), Facing.WEST);
		c.set(6, 1, 5, VPart.BARREL);
		c.set(6, 2, 5, VPart.LANTERN);
		c.set(2, 1, 5, VPart.FLOWER_POT);
		for (int x = 3; x <= 5; x++) {
			for (int z = 4; z <= 5; z++) {
				c.set(x, 1, z, VPart.CARPET);
			}
		}
		// canteiros na frente
		c.set(2, 1, 8, VPart.FLOWER);
		c.set(6, 1, 8, VPart.FLOWER);
		c.set(1, 1, 8, VPart.PLANT);
		c.set(7, 1, 8, VPart.PLANT);
		yard(c);
		c.set(4, 0, 8, VPart.PATH);
		c.doorX(4);
		c.spawn(4, 1, 4);
		return c;
	}

	/** Sobrado: terreo de pedra, andar de enxaimel, escada de mao, duas camas em cima. Lote 9x11. */
	static Canvas townhouse(VillageStyle style, Random random) {
		Canvas c = new Canvas(9, 11);
		floor(c, 1, 1, 7, 9);
		walls(c, style, 1, 1, 7, 9, 1, 4, true, false);
		// piso do andar de cima
		for (int x = 1; x <= 7; x++) {
			for (int z = 1; z <= 9; z++) {
				boolean edge = x == 1 || x == 7 || z == 1 || z == 9;
				boolean corner = (x == 1 || x == 7) && (z == 1 || z == 9);
				c.set(x, 5, z, corner ? (style.flatRoof() ? VPart.BRICK : VPart.LOG) : edge ? VPart.BEAM : VPart.FLOOR,
						edge && !corner ? (z == 1 || z == 9 ? Facing.EAST : Facing.SOUTH) : null);
			}
		}
		walls(c, style, 1, 1, 7, 9, 6, 8, false, false);
		door(c, 4, 1, 9, Facing.SOUTH);
		window(c, 2, 2, 9, Facing.SOUTH, false);
		window(c, 6, 2, 9, Facing.SOUTH, false);
		window(c, 1, 2, 3, Facing.WEST, false);
		window(c, 1, 2, 7, Facing.WEST, false);
		window(c, 7, 2, 3, Facing.EAST, false);
		window(c, 7, 2, 7, Facing.EAST, false);
		window(c, 2, 7, 9, Facing.SOUTH, true);
		window(c, 6, 7, 9, Facing.SOUTH, true);
		window(c, 1, 7, 3, Facing.WEST, false);
		window(c, 1, 7, 6, Facing.WEST, false);
		window(c, 7, 7, 3, Facing.EAST, false);
		window(c, 7, 7, 6, Facing.EAST, false);
		roof(c, style, 1, 1, 7, 9, 8, false);
		// escada de mao no canto do fundo, atravessando o piso
		for (int y = 1; y <= 5; y++) {
			c.set(6, y, 2, VPart.LADDER, Facing.SOUTH);
		}
		// terreo: cozinha e mesa
		c.set(2, 1, 2, VPart.FURNACE, Facing.SOUTH);
		c.set(3, 1, 2, VPart.CRAFTING);
		c.set(4, 1, 2, VPart.BARREL);
		c.set(2, 1, 6, houseJob(random), Facing.EAST);
		c.set(5, 1, 5, VPart.WOOD_SLAB_TOP);
		c.set(5, 2, 5, VPart.LANTERN);
		c.set(5, 1, 4, VPart.WOOD_STAIRS, Facing.NORTH);
		c.set(5, 1, 6, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.set(2, 1, 8, VPart.FLOWER_POT);
		// andar de cima: quarto
		bed(c, 2, 6, 3, Facing.NORTH);
		bed(c, 2, 6, 7, Facing.SOUTH);
		c.set(6, 6, 8, VPart.CHEST, Facing.NORTH);
		c.set(6, 6, 5, VPart.BARREL);
		c.set(6, 7, 5, VPart.LANTERN);
		for (int z = 4; z <= 6; z++) {
			c.set(4, 6, z, VPart.CARPET);
			c.set(3, 6, z, VPart.CARPET);
		}
		c.set(2, 1, 10, VPart.FLOWER);
		c.set(6, 1, 10, VPart.FLOWER);
		c.set(1, 1, 10, VPart.PLANT);
		yard(c);
		c.set(4, 0, 10, VPart.PATH);
		c.doorX(4);
		c.spawn(3, 1, 4);
		return c;
	}

	// ---------------------------------------------------------------- fazenda, praca, mercado

	/** Plantacao cercada: dois canteiros com canal de agua no meio, passarela, espantalho, composteira e feno. 11x9. */
	static Canvas farm(VillageStyle style, Random random) {
		Canvas c = new Canvas(11, 9);
		VPart[] crops = {VPart.CROP_WHEAT, VPart.CROP_CARROT, VPart.CROP_POTATO, VPart.CROP_BEET};
		VPart left = crops[random.nextInt(crops.length)];
		VPart right = crops[random.nextInt(crops.length)];
		VPart far = crops[random.nextInt(crops.length)];
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 8; z++) {
				boolean edge = x == 0 || x == 10 || z == 0 || z == 8;
				if (edge) {
					c.set(x, 0, z, VPart.SOIL);
					c.set(x, 1, z, VPart.FENCE);
				} else if (x == 5) {
					c.set(x, 0, z, z == 4 ? VPart.PLANKS : VPart.PATH);
				} else if (z == 4) {
					c.set(x, 0, z, VPart.WATER);
				} else {
					c.set(x, 0, z, VPart.FARMLAND);
					VPart crop = x < 5 ? (z < 4 ? far : left) : right;
					c.set(x, 1, z, crop);
				}
			}
		}
		c.set(5, 1, 8, VPart.FENCE_GATE, Facing.SOUTH);
		c.set(4, 2, 8, VPart.LANTERN);
		c.set(6, 2, 8, VPart.LANTERN);
		// composteira e feno nos cantos; espantalho no canteiro
		c.set(1, 0, 1, VPart.SOIL);
		c.set(1, 1, 1, VPart.COMPOSTER);
		c.set(9, 0, 1, VPart.SOIL);
		c.set(9, 1, 1, VPart.HAY);
		c.set(9, 0, 2, VPart.SOIL);
		c.set(9, 1, 2, VPart.HAY, Facing.SOUTH);
		c.set(9, 2, 1, VPart.HAY, Facing.EAST);
		c.set(2, 0, 6, VPart.SOIL);
		c.set(2, 1, 6, VPart.FENCE);
		c.set(2, 2, 6, VPart.HAY);
		c.set(2, 3, 6, VPart.PUMPKIN, Facing.SOUTH);
		c.doorX(5);
		c.spawn(5, 1, 6);
		return c;
	}

	/** Poco da praca: aro de pedra, agua funda, 4 postes e telhado piramidal com lanterna pendurada. 5x5. */
	static Canvas well(VillageStyle style, Random random) {
		Canvas c = new Canvas(5, 5);
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				c.set(x, -3, z, VPart.STONE);
				if (edge) {
					c.set(x, -2, z, VPart.STONE);
					c.set(x, -1, z, VPart.STONE);
					c.set(x, 0, z, VPart.FOUNDATION);
					c.set(x, 1, z, corner ? VPart.LOG : VPart.STONE_SLAB);
					if (corner) {
						c.set(x, 2, z, VPart.FENCE);
						c.set(x, 3, z, VPart.FENCE);
					}
				} else {
					c.set(x, -2, z, VPart.WATER);
					c.set(x, -1, z, VPart.WATER);
					c.set(x, 0, z, VPart.WATER);
				}
				// telhado piramidal
				if (edge) {
					Facing in = corner ? (z == 0 ? Facing.SOUTH : Facing.NORTH)
							: z == 0 ? Facing.SOUTH : z == 4 ? Facing.NORTH : x == 0 ? Facing.EAST : Facing.WEST;
					c.set(x, 4, z, VPart.ROOF, in);
				} else {
					c.set(x, 4, z, VPart.ROOF_BLOCK);
					boolean ring = x != 2 || z != 2;
					if (ring) {
						Facing in = z == 1 ? Facing.SOUTH : z == 3 ? Facing.NORTH : x == 1 ? Facing.EAST : Facing.WEST;
						c.set(x, 5, z, VPart.ROOF, in);
					}
				}
			}
		}
		c.set(2, 5, 2, VPart.ROOF_BLOCK);
		c.set(2, 6, 2, VPart.ROOF_SLAB);
		c.set(2, 3, 2, VPart.LANTERN_HANGING);
		c.doorX(2);
		return c;
	}

	/** Fogueira do acampamento com bancos de tronco, caixotes e lanterna. 7x7. */
	public static Canvas campfire(VillageStyle style) {
		Canvas c = new Canvas(7, 7);
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				int dx = x - 3;
				int dz = z - 3;
				if (dx * dx + dz * dz <= 5) {
					c.set(x, 0, z, (dx + dz) % 2 == 0 ? VPart.GRAVEL : VPart.PATH);
				}
			}
		}
		c.set(3, 0, 3, VPart.STONE);
		c.set(3, 1, 3, VPart.CAMPFIRE);
		for (int i = 2; i <= 4; i++) {
			c.set(i, 1, 0, VPart.LOG, Facing.EAST);
			c.set(i, 1, 6, VPart.LOG, Facing.EAST);
			c.set(0, 1, i, VPart.LOG, Facing.SOUTH);
		}
		c.set(6, 1, 5, VPart.BARREL);
		c.set(6, 2, 5, VPart.LANTERN);
		c.set(6, 1, 4, VPart.HAY);
		c.set(5, 1, 6, VPart.CRAFTING);
		yard(c);
		c.doorX(3);
		return c;
	}

	/** Mercado: tres barracas com toldo de la listrada, balcao e oficio; caixotes e feno na frente. 11x9. */
	static Canvas market(VillageStyle style, Random random) {
		Canvas c = new Canvas(11, 9);
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 8; z++) {
				c.set(x, 0, z, Math.floorMod(x * 3 + z * 5 + (int) random.nextInt(2), 7) == 0 ? VPart.GRAVEL : VPart.PATH);
			}
		}
		VPart[] jobs = {VPart.STONECUTTER, VPart.LOOM, random.nextBoolean() ? VPart.CARTOGRAPHY : VPart.FLETCHING};
		for (int i = 0; i < 3; i++) {
			int xs = 4 * i;
			for (int[] p : new int[][] {{xs, 1}, {xs + 2, 1}, {xs, 3}, {xs + 2, 3}}) {
				c.set(p[0], 1, p[1], VPart.FENCE);
				c.set(p[0], 2, p[1], VPart.FENCE);
			}
			for (int x = xs; x <= xs + 2; x++) {
				for (int z = 1; z <= 4; z++) {
					c.set(x, 3, z, (x - xs) % 2 == 0 ? VPart.WOOL : VPart.WOOL_ALT);
				}
			}
			c.set(xs + 1, 1, 1, jobs[i], Facing.SOUTH);
			c.set(xs + 1, 1, 3, VPart.WOOD_SLAB_TOP);
			c.set(xs + 1, 2, 3, VPart.FLOWER_POT);
			c.set(xs + 1, 2, 2, VPart.LANTERN_HANGING);
		}
		c.set(0, 1, 6, VPart.BARREL);
		c.set(0, 2, 6, VPart.LANTERN);
		c.set(0, 1, 7, VPart.BARREL);
		c.set(10, 1, 6, VPart.HAY);
		c.set(10, 1, 7, VPart.HAY);
		c.set(10, 2, 7, VPart.HAY);
		c.set(3, 1, 7, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.set(7, 1, 7, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.doorX(5);
		c.spawn(3, 1, 5);
		c.spawn(7, 1, 5);
		return c;
	}

	// ---------------------------------------------------------------- forja, estalagem, biblioteca

	/** Forja de pedra aberta na frente: alto-forno com chamine, bigorna, mesa de ferraria, rebolo, lava. 9x10. */
	static Canvas smithy(VillageStyle style, Random random) {
		Canvas c = new Canvas(9, 10);
		for (int x = 1; x <= 7; x++) {
			for (int z = 1; z <= 8; z++) {
				boolean edge = x == 1 || x == 7 || z == 1 || z == 8;
				c.set(x, 0, z, edge ? VPart.FOUNDATION : (Math.floorMod(x + z, 3) == 0 ? VPart.PAVING_ALT : VPart.PAVING));
			}
		}
		walls(c, style, 1, 1, 7, 8, 1, 4, true, false);
		// frente aberta entre dois esteios de tronco
		for (int x = 2; x <= 6; x++) {
			for (int y = 1; y <= 3; y++) {
				c.remove(x, y, 8);
			}
			c.set(x, 0, 8, VPart.PAVING);
		}
		for (int y = 1; y <= 3; y++) {
			c.set(1, y, 8, style.flatRoof() ? VPart.BRICK : VPart.LOG);
			c.set(7, y, 8, style.flatRoof() ? VPart.BRICK : VPart.LOG);
		}
		window(c, 1, 2, 4, Facing.WEST, false);
		window(c, 7, 2, 4, Facing.EAST, false);
		roof(c, style, 1, 1, 7, 8, 4, true);
		c.set(2, 1, 2, VPart.BLAST_FURNACE, Facing.SOUTH);
		chimney(c, 2, 2, 2, style.flatRoof() ? 2 : 1);
		c.set(3, 1, 2, VPart.LAVA_CAULDRON);
		c.set(4, 1, 2, VPart.CHEST, Facing.SOUTH);
		c.set(6, 1, 2, VPart.SMITHING_TABLE);
		c.set(4, 1, 4, VPart.ANVIL, Facing.EAST);
		c.set(6, 1, 5, VPart.GRINDSTONE, Facing.WEST);
		c.set(2, 1, 6, VPart.BARREL);
		c.set(2, 2, 6, VPart.LANTERN);
		c.set(6, 1, 7, VPart.CAULDRON);
		c.set(6, 1, 3, VPart.FURNACE, Facing.WEST);
		yard(c);
		c.set(4, 0, 9, VPart.PATH);
		c.doorX(4);
		c.spawn(4, 1, 6);
		c.golem(4, 1, 7);
		return c;
	}

	/** Estalagem: terreo de pedra com balcao, mesas e lareira; andar de enxaimel com quatro camas. 11x13. */
	static Canvas inn(VillageStyle style, Random random) {
		Canvas c = new Canvas(11, 13);
		floor(c, 1, 1, 9, 11);
		walls(c, style, 1, 1, 9, 11, 1, 4, true, false);
		for (int x = 1; x <= 9; x++) {
			for (int z = 1; z <= 11; z++) {
				boolean edge = x == 1 || x == 9 || z == 1 || z == 11;
				boolean corner = (x == 1 || x == 9) && (z == 1 || z == 11);
				c.set(x, 5, z, corner ? (style.flatRoof() ? VPart.BRICK : VPart.LOG) : edge ? VPart.BEAM : VPart.FLOOR,
						edge && !corner ? (z == 1 || z == 11 ? Facing.EAST : Facing.SOUTH) : null);
			}
		}
		walls(c, style, 1, 1, 9, 11, 6, 9, false, false);
		door(c, 5, 1, 11, Facing.SOUTH);
		for (int y = 2; y <= 3; y++) {
			window(c, 3, y, 11, Facing.SOUTH, false);
			window(c, 7, y, 11, Facing.SOUTH, false);
			window(c, 1, y, 4, Facing.WEST, false);
			window(c, 1, y, 8, Facing.WEST, false);
			window(c, 9, y, 4, Facing.EAST, false);
			window(c, 9, y, 8, Facing.EAST, false);
		}
		window(c, 3, 7, 11, Facing.SOUTH, true);
		window(c, 6, 7, 11, Facing.SOUTH, true);
		for (int z : new int[] {3, 6, 9}) {
			window(c, 1, 7, z, Facing.WEST, false);
			window(c, 9, 7, z, Facing.EAST, false);
		}
		roof(c, style, 1, 1, 9, 11, 9, false);
		// lareira na parede leste, com chamine atravessando o andar e o telhado
		c.set(8, 1, 5, VPart.BRICK);
		c.set(8, 1, 7, VPart.BRICK);
		c.set(8, 1, 6, VPart.CAMPFIRE);
		for (int z = 5; z <= 7; z++) {
			c.set(8, 2, z, VPart.BRICK);
		}
		chimney(c, 8, 6, 3, style.flatRoof() ? 2 : 1);
		// balcao com barris atras
		for (int x = 2; x <= 5; x++) {
			c.set(x, 1, 2, VPart.BARREL);
			c.set(x, 1, 4, VPart.WOOD_SLAB_TOP);
		}
		c.set(6, 1, 4, VPart.WOOD_SLAB_TOP);
		c.set(6, 1, 2, VPart.BREWING);
		c.set(3, 2, 4, VPart.LANTERN);
		c.set(5, 2, 2, VPart.FLOWER_POT);
		// mesas com cadeiras
		for (int x : new int[] {3, 6}) {
			c.set(x, 1, 8, VPart.WOOD_SLAB_TOP);
			c.set(x, 2, 8, VPart.LANTERN);
			c.set(x, 1, 7, VPart.WOOD_STAIRS, Facing.NORTH);
			c.set(x, 1, 9, VPart.WOOD_STAIRS, Facing.SOUTH);
		}
		// escada de mao junto a frente
		for (int y = 1; y <= 5; y++) {
			c.set(2, y, 10, VPart.LADDER, Facing.NORTH);
		}
		// quartos
		bed(c, 2, 6, 3, Facing.NORTH);
		bed(c, 2, 6, 7, Facing.NORTH);
		bed(c, 8, 6, 3, Facing.NORTH);
		bed(c, 8, 6, 9, Facing.NORTH);
		c.set(5, 6, 2, VPart.CHEST, Facing.SOUTH);
		c.set(4, 6, 2, VPart.BARREL);
		c.set(4, 7, 2, VPart.LANTERN);
		for (int z = 4; z <= 9; z++) {
			c.set(5, 6, z, VPart.CARPET_ALT);
		}
		// lanternas penduradas em bracos de viga dos dois lados da porta
		for (int x : new int[] {4, 6}) {
			c.set(x, 4, 12, VPart.BEAM, Facing.SOUTH);
			c.set(x, 3, 12, VPart.LANTERN_HANGING);
		}
		c.set(2, 1, 12, VPart.FLOWER);
		c.set(8, 1, 12, VPart.FLOWER);
		yard(c);
		c.set(5, 0, 12, VPart.PATH);
		c.doorX(5);
		c.spawn(5, 1, 6);
		c.spawn(5, 6, 6);
		return c;
	}

	/** Biblioteca de pedra com janelas altas, estantes entre as janelas, atris, mesa de leitura e tapete. 9x11. */
	static Canvas library(VillageStyle style, Random random) {
		Canvas c = new Canvas(9, 11);
		floor(c, 1, 1, 7, 9);
		walls(c, style, 1, 1, 7, 9, 1, 6, true, false);
		door(c, 4, 1, 9, Facing.SOUTH);
		c.set(4, 3, 9, VPart.STONE_STAIRS_TOP, Facing.NORTH);
		for (int y = 2; y <= 3; y++) {
			window(c, 2, y, 9, Facing.SOUTH, false);
			window(c, 6, y, 9, Facing.SOUTH, false);
		}
		for (int z : new int[] {3, 5, 7}) {
			for (int y = 2; y <= 4; y++) {
				window(c, 1, y, z, Facing.WEST, false);
				window(c, 7, y, z, Facing.EAST, false);
			}
		}
		window(c, 4, 4, 1, Facing.NORTH, false);
		roof(c, style, 1, 1, 7, 9, 6, false);
		for (int z : new int[] {2, 4, 6, 8}) {
			for (int y = 1; y <= 3; y++) {
				c.set(2, y, z, VPart.BOOKSHELF);
				c.set(6, y, z, VPart.BOOKSHELF);
			}
			c.set(2, 4, z, VPart.LANTERN);
			c.set(6, 4, z, VPart.LANTERN);
		}
		for (int y = 1; y <= 2; y++) {
			c.set(3, y, 2, VPart.BOOKSHELF);
			c.set(5, y, 2, VPart.BOOKSHELF);
		}
		c.set(4, 1, 3, VPart.LECTERN, Facing.SOUTH);
		c.set(3, 1, 5, VPart.LECTERN, Facing.EAST);
		c.set(4, 1, 6, VPart.WOOD_SLAB_TOP);
		c.set(4, 2, 6, VPart.FLOWER_POT);
		c.set(5, 1, 6, VPart.WOOD_STAIRS, Facing.EAST);
		for (int z = 7; z <= 8; z++) {
			c.set(4, 1, z, VPart.CARPET_ALT);
		}
		c.set(2, 1, 10, VPart.FLOWER);
		c.set(6, 1, 10, VPart.FLOWER);
		yard(c);
		c.set(4, 0, 10, VPart.PATH);
		c.doorX(4);
		c.spawn(4, 1, 8);
		return c;
	}

	/** Todas as plantas de um projeto (para os testes passarem por cada variante). */
	public static List<Canvas> variants(VillageProject project, VillageStyle style) {
		Random random = new Random(1);
		return switch (project) {
			case HOUSE -> List.of(cottage(style, random), townhouse(style, random));
			default -> List.of(plan(project, style, 1));
		};
	}
}
