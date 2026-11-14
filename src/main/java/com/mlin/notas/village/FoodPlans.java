package com.mlin.notas.village;

import java.util.Random;

/**
 * Plantas das construcoes de producao e comida (Vilas 2.0, fase 5; logica pura, coordenadas de {@link Canvas}): celeiro,
 * moinho de vento, padaria, pomar, curral, apiario, acougue, vinicola e serraria. Toda planta de um tipo tem o mesmo
 * tamanho de tela em todos os niveis (a classe do lote em {@link FoodBuildings}), entao melhorar nunca transborda. A
 * convencao dos baus: o primeiro {@link VPart#CHEST} da planta (ordem de insercao) e o bau de trabalho da construcao,
 * onde o servidor le e poe itens ({@code FoodBuildingsServer}).
 *
 * <p>Referencias de desenho (escritas do zero, nada copiado): moinho de vento de torre de pedra com capuz em piramide e
 * quatro velas de cerca e la (Planet Minecraft "Medieval Windmill" e os moinhos do Medieval Village do Minecraft
 * Maps); celeiro de enxaimel com porta larga, sotao de feno e abrigo lateral (Medieval Granary / Barn do Planet
 * Minecraft, celeiros de Alsacia e da Normandia); padaria de esquina com forno de tijolo e toldo listrado na frente
 * (Medieval Bakery do Planet Minecraft, padarias da lista da Sportskeeda); pomar cercado em fileiras com barraca de
 * ferramentas e prensa de cidra (Medieval Orchard do Minecraft Maps); curral de estacas com abrigo de feno, cochos e
 * celeiro de fundos; apiario de jardim com colmeias em fileiras e casa do mel (Beekeeper's Garden do Planet
 * Minecraft); acougue de pedra e madeira com toldo e defumadores (acougues da lista de casas medievais do Switchblade
 * Gaming); vinicola de parreirais em latada com adega de pedra e terraco de degustacao (Medieval Vineyard e Winery
 * do Planet Minecraft); serraria aberta na frente com pilhas de toras, bancada de serra e secador de tabuas (Medieval
 * Sawmill do Planet Minecraft).
 */
public final class FoodPlans {
	private FoodPlans() {
	}

	public static boolean handles(VillageProject project) {
		return switch (project) {
			case GRANARY, MILL, BAKERY, ORCHARD, PEN, APIARY, BUTCHER, WINERY, SAWMILL -> true;
			default -> false;
		};
	}

	/** Planta do tipo no nivel (1..maximo do tipo). */
	public static Canvas plan(VillageProject project, VillageStyle style, long seed, int level) {
		int lv = Math.max(1, Math.min(BuildingTypes.maxLevel(project.id()), level));
		Random random = new Random(seed ^ 0x51F0_0D5EL ^ ((long) project.ordinal() << 40));
		return switch (project) {
			case GRANARY -> granary(style, random, lv);
			case MILL -> mill(style, random, lv);
			case BAKERY -> bakery(style, random, lv);
			case ORCHARD -> orchard(style, random, lv);
			case PEN -> pen(style, random, lv);
			case APIARY -> apiary(style, random, lv);
			case BUTCHER -> butcher(style, random, lv);
			case WINERY -> winery(style, random, lv);
			case SAWMILL -> sawmill(style, random, lv);
			default -> throw new IllegalArgumentException("sem planta de producao: " + project);
		};
	}

	// ---------------------------------------------------------------- pecas

	/** Caminho de terra da porta ate a frente do lote e grama no resto. */
	private static void finish(Canvas c, int door, int zFrom) {
		for (int z = zFrom; z < c.d(); z++) {
			c.set(door, 0, z, VPart.PATH);
		}
		for (int x = 1; x < c.w() - 1; x++) {
			if (Math.abs(x - door) >= 3 && x % 3 == 0) {
				c.setIfAbsent(x, 1, c.d() - 1, VPart.FLOWER);
			}
		}
		Buildings.yard(c);
		c.doorX(door);
	}

	private static void column(Canvas c, int x, int z, int y0, int y1, VPart part) {
		for (int y = y0; y <= y1; y++) {
			c.set(x, y, z, part);
		}
	}

	/** Cerca em volta do retangulo (no chao de grama), com portao no meio de um lado ({@code gate}: x do portao no lado z1). */
	private static void fenceRing(Canvas c, int x0, int z0, int x1, int z1, int gateX) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x == x0 || x == x1 || z == z0 || z == z1) {
					c.set(x, 0, z, VPart.SOIL);
					c.set(x, 1, z, VPart.FENCE);
				}
			}
		}
		c.set(gateX, 1, z1, VPart.FENCE_GATE, Facing.SOUTH);
		c.set(gateX, 0, z1, VPart.PATH);
	}

	/** Telhado de laje sobre estacas (abrigo aberto): estacas nos cantos, laje em y = {@code top}. */
	private static void canopy(Canvas c, int x0, int z0, int x1, int z1, int top) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, top, z, VPart.ROOF_SLAB);
			}
		}
		for (int[] p : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			column(c, p[0], p[1], 1, top - 1, VPart.LOG);
		}
	}

	/** Toldo listrado de la na frente da loja: laje de la em y=3 sobre cercas. */
	private static void awning(Canvas c, int xc, int half, int zFront, int y) {
		for (int x = xc - half; x <= xc + half; x++) {
			for (int z = zFront; z <= zFront + 1; z++) {
				c.set(x, y, z, (x + z) % 2 == 0 ? VPart.WOOL : VPart.WOOL_ALT);
			}
		}
		for (int s : new int[] {-half, half}) {
			column(c, xc + s, zFront + 1, 1, y - 1, VPart.FENCE);
		}
		c.set(xc - half + 1, y - 1, zFront + 1, VPart.LANTERN_HANGING);
		c.set(xc + half - 1, y - 1, zFront + 1, VPart.LANTERN_HANGING);
	}

	/** Telhado de piramide: anel de degraus por camada e miolo cheio para apoiar a camada de cima. */
	static void pyramid(Canvas c, int x0, int z0, int x1, int z1, int baseY) {
		int lx = x0 - 1;
		int hx = x1 + 1;
		int lz = z0 - 1;
		int hz = z1 + 1;
		for (int i = 0; lx <= hx && lz <= hz; i++, lx++, hx--, lz++, hz--) {
			int y = baseY + i;
			if (lx == hx || lz == hz) {
				for (int x = lx; x <= hx; x++) {
					for (int z = lz; z <= hz; z++) {
						c.set(x, y, z, VPart.ROOF_SLAB);
					}
				}
				break;
			}
			for (int x = lx; x <= hx; x++) {
				for (int z = lz; z <= hz; z++) {
					boolean north = z == lz;
					boolean south = z == hz;
					boolean west = x == lx;
					boolean east = x == hx;
					if (north) {
						c.set(x, y, z, VPart.ROOF, Facing.SOUTH);
					} else if (south) {
						c.set(x, y, z, VPart.ROOF, Facing.NORTH);
					} else if (west) {
						c.set(x, y, z, VPart.ROOF, Facing.EAST);
					} else if (east) {
						c.set(x, y, z, VPart.ROOF, Facing.WEST);
					} else {
						c.set(x, y, z, VPart.ROOF_BLOCK);
					}
				}
			}
		}
	}

	/** Sacos de la/farinha empilhados (parte SACK). */
	private static void sacks(Canvas c, int x, int z, int height) {
		for (int y = 1; y <= height; y++) {
			c.set(x, y, z, VPart.SACK);
		}
	}

	// ---------------------------------------------------------------- celeiro

	/**
	 * Celeiro 15x13: galpao de enxaimel 11x9 com a porta no meio, fardos de feno e barris na frente; bau de estoque,
	 * barris e feno por dentro. II: sotao com feno e segundo bau. III: base de pedra, segundo bau no terreo e abrigo
	 * lateral de laje com feno e barris.
	 */
	static Canvas granary(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		int x0 = 2;
		int x1 = 12;
		int z0 = 1;
		int z1 = 9;
		int mid = 7;
		int floors = level >= 2 ? 2 : 1;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, true, level >= 3);
		c.set(x0 + 1, 1, z0 + 1, VPart.CHEST, Facing.SOUTH);
		for (int x = x0 + 2; x <= x0 + 4; x++) {
			c.set(x, 1, z0 + 1, VPart.BARREL);
		}
		for (int x = x1 - 4; x <= x1 - 2; x++) {
			c.set(x, 1, z0 + 1, VPart.HAY, Facing.EAST);
			c.set(x, 2, z0 + 1, VPart.HAY, Facing.EAST);
		}
		sacks(c, x0 + 1, z0 + 3, 2);
		sacks(c, x0 + 1, z0 + 4, 1);
		c.set(x0 + 1, 1, z0 + 6, VPart.BARREL);
		BuildingPlans.lamp(c, x1 - 1, 1, z0 + 6);
		if (floors == 2) {
			int y = BuildingPlans.floorY(1) + 1;
			c.set(x0 + 1, y, z0 + 1, VPart.CHEST, Facing.SOUTH);
			for (int x = x0 + 2; x <= x1 - 3; x++) {
				c.set(x, y, z0 + 1, VPart.HAY, Facing.EAST);
				if (x % 2 == 0) {
					c.set(x, y + 1, z0 + 1, VPart.HAY, Facing.EAST);
				}
			}
			for (int x = x0 + 2; x <= x0 + 6; x++) {
				c.set(x, y, z0 + 3, VPart.HAY, Facing.EAST);
			}
			c.set(x1 - 2, y, z1 - 1, VPart.BARREL);
			c.set(x1 - 3, y, z1 - 1, VPart.BARREL);
			BuildingPlans.lamp(c, x0 + 1, y, z1 - 1);
		}
		if (level >= 3) {
			c.set(x1 - 1, 1, z0 + 3, VPart.CHEST, Facing.WEST);
			c.set(x1 - 1, 2, z0 + 3, VPart.BARREL);
			// abrigo lateral de laje no lado leste, encostado na parede
			for (int z = 2; z <= 8; z++) {
				c.set(13, 4, z, VPart.ROOF_SLAB);
				c.set(14, 4, z, VPart.ROOF_SLAB);
			}
			for (int z : new int[] {2, 5, 8}) {
				column(c, 14, z, 1, 3, VPart.LOG);
			}
			for (int z = 2; z <= 8; z++) {
				c.set(13, 0, z, VPart.PAVING);
				c.set(14, 0, z, VPart.PAVING_ALT);
			}
			for (int z = 3; z <= 4; z++) {
				c.set(13, 1, z, VPart.HAY, Facing.SOUTH);
				c.set(13, 2, z, VPart.HAY, Facing.SOUTH);
			}
			c.set(13, 1, 6, VPart.BARREL);
			c.set(13, 1, 7, VPart.BARREL);
			c.set(13, 2, 7, VPart.BARREL);
		}
		// frente: fardos, barris e lampiao; o carrinho de mao (cerca + feno) ao lado da porta
		c.set(4, 1, 10, VPart.HAY, Facing.EAST);
		c.set(5, 1, 10, VPart.HAY, Facing.EAST);
		c.set(4, 2, 10, VPart.HAY, Facing.EAST);
		c.set(10, 1, 10, VPart.BARREL);
		c.set(11, 1, 10, VPart.BARREL);
		c.set(11, 2, 10, VPart.BARREL);
		for (int z = z1 + 1; z <= 11; z++) {
			c.set(mid - 2, 0, z, VPart.PATH);
			c.set(mid + 2, 0, z, VPart.PATH);
		}
		finish(c, mid, z1 + 1);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}

	// ---------------------------------------------------------------- moinho

	/**
	 * Moinho de vento 15x13: torre quadrada de pedra 7x7 com 2 (I) ou 3 (II-III) andares e capuz de piramide; as quatro
	 * velas (cerca + la) giram em torno de um cubo de tronco na frente, acima da porta. Mos de pedra (esmeril) no
	 * terreo, bau de trabalho, sacos de farinha. II e III ganham um anexo de deposito com segundo bau; III tem velas
	 * maiores, tres mos e uma carroca.
	 */
	static Canvas mill(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		int x0 = 4;
		int x1 = 10;
		int z0 = 2;
		int z1 = 8;
		int mid = 7;
		int floors = level >= 2 ? 3 : 2;
		Buildings.floor(c, x0, z0, x1, z1);
		for (int k = 0; k < floors; k++) {
			int yb = k == 0 ? 1 : 4 * k + 2;
			int yt = 4 * k + 4;
			if (k >= 1) {
				BuildingPlans.storeyFloor(c, style, x0, z0, x1, z1, 4 * k + 1);
			}
			Buildings.walls(c, style, x0, z0, x1, z1, yb, yt, true, false);
			int wy = k == 0 ? 2 : 4 * k + 3;
			if (k > 0) {
				Buildings.window(c, mid - 2, wy, z1, Facing.SOUTH, true);
				Buildings.window(c, mid + 2, wy, z1, Facing.SOUTH, true);
			}
			Buildings.window(c, x0, wy, mid + 0, Facing.WEST, k > 0);
			Buildings.window(c, x1, wy, mid + 0, Facing.EAST, k > 0);
			Buildings.window(c, mid, wy, z0, Facing.NORTH, false);
		}
		Buildings.door(c, mid, 1, z1, Facing.SOUTH);
		for (int y = 1; y <= 2; y++) {
			c.set(mid - 1, y, z1, VPart.BRICK);
			c.set(mid + 1, y, z1, VPart.BRICK);
		}
		int top = 4 * floors;
		for (int y = 1; y <= 4 * (floors - 1) + 1; y++) {
			c.set(x1 - 1, y, z1 - 1, VPart.LADDER, Facing.NORTH);
		}
		pyramid(c, x0, z0, x1, z1, top);
		// mos, bau e sacos no terreo
		int stones = level;
		for (int i = 0; i < stones; i++) {
			c.set(x0 + 1 + i * 2, 1, z0 + 1, VPart.GRINDSTONE, Facing.SOUTH);
		}
		c.set(x1 - 1, 1, z0 + 2, VPart.CHEST, Facing.WEST);
		sacks(c, x0 + 1, z0 + 3, 2);
		sacks(c, x0 + 1, z0 + 4, 1);
		sacks(c, x1 - 1, z0 + 4, 1);
		BuildingPlans.lamp(c, x1 - 1, 1, z0 + 5);
		// andares de cima: sacas e barris
		for (int k = 1; k < floors; k++) {
			int y = 4 * k + 2;
			sacks2(c, x0 + 1, y, z0 + 1);
			c.set(x0 + 2, y, z0 + 1, VPart.BARREL);
			c.set(x1 - 1, y, z0 + 1, VPart.BARREL);
			c.set(mid, y, z0 + 3, VPart.CRAFTING);
			BuildingPlans.lamp(c, x0 + 1, y, z1 - 1);
		}
		// velas: cubo de tronco atravessando a parede da frente, braços de cerca e pas de la em cada braço
		int hubY = 4 * floors - 2;
		int len = 3 + level;
		int sz = z1 + 2;
		c.set(mid, hubY, z1 + 1, VPart.LOG, Facing.SOUTH);
		c.set(mid, hubY, sz, VPart.LOG, Facing.SOUTH);
		for (int k = 1; k <= len; k++) {
			c.set(mid + k, hubY, sz, VPart.FENCE);
			c.set(mid - k, hubY, sz, VPart.FENCE);
			c.set(mid, hubY + k, sz, VPart.FENCE);
			c.set(mid, hubY - k, sz, VPart.FENCE);
		}
		for (int k = 2; k <= len; k++) {
			VPart cloth = k % 2 == 0 ? VPart.WOOL : VPart.WOOL_ALT;
			c.set(mid + k, hubY + 1, sz, cloth);
			c.set(mid - 1, hubY + k, sz, cloth);
			c.set(mid - k, hubY - 1, sz, cloth);
			c.set(mid + 1, hubY - k, sz, cloth);
			if (k >= 3 && level >= 3) {
				c.set(mid + k, hubY + 2, sz, cloth);
				c.set(mid - 2, hubY + k, sz, cloth);
				c.set(mid - k, hubY - 2, sz, cloth);
				c.set(mid + 2, hubY - k, sz, cloth);
			}
		}
		if (level >= 2) {
			// anexo de deposito encostado na parede leste
			for (int z = 3; z <= 7; z++) {
				c.set(11, 4, z, VPart.ROOF_SLAB);
				c.set(12, 4, z, VPart.ROOF_SLAB);
				c.set(11, 0, z, VPart.PAVING);
				c.set(12, 0, z, VPart.PAVING_ALT);
			}
			for (int z : new int[] {3, 7}) {
				column(c, 12, z, 1, 3, VPart.LOG);
			}
			c.set(11, 1, 3, VPart.CHEST, Facing.WEST);
			c.set(11, 1, 4, VPart.BARREL);
			sacks(c, 12, 4, 2);
			sacks(c, 12, 5, 1);
			c.set(11, 1, 6, VPart.HAY, Facing.EAST);
			c.set(12, 1, 6, VPart.HAY, Facing.EAST);
		}
		if (level >= 3) {
			// carroca de feno ao lado do moinho
			for (int x = 1; x <= 3; x++) {
				c.set(x, 0, 9, VPart.PLANKS);
				c.set(x, 1, 9, VPart.HAY, Facing.EAST);
			}
			c.set(1, 1, 8, VPart.FENCE);
			c.set(3, 1, 8, VPart.FENCE);
			c.set(0, 1, 9, VPart.FENCE);
		}
		finish(c, mid, z1 + 1);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}

	/** Pilha de sacos de farinha de 2 de altura sobre o piso de um andar de cima (y = pes). */
	private static void sacks2(Canvas c, int x, int y, int z) {
		c.set(x, y, z, VPart.SACK);
		c.set(x, y + 1, z, VPart.SACK);
	}

	// ---------------------------------------------------------------- padaria

	/**
	 * Padaria 11x11: loja 9x7 com forno de tijolo e chamine no fundo (dois defumadores e uma fornalha), balcao de
	 * tabuas com barris de pao, bau de trabalho e sacos de farinha; toldo listrado na frente. II: andar de cima com a
	 * cama do padeiro e mais um forno. III: forno de rua de tijolo no flanco leste e mesas no patio da frente.
	 */
	static Canvas bakery(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(11, 11);
		int x0 = 1;
		int x1 = 9;
		int z0 = 1;
		int z1 = 7;
		int mid = 5;
		int floors = level >= 2 ? 2 : 1;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, true, true);
		// forno de tijolo no fundo
		for (int x = x0 + 1; x <= x0 + 5; x++) {
			c.set(x, 1, z0 + 1, VPart.BRICK);
			c.set(x, 2, z0 + 1, VPart.BRICK);
		}
		c.set(x0 + 1, 1, z0 + 2, VPart.SMOKER, Facing.SOUTH);
		c.set(x0 + 3, 1, z0 + 2, VPart.FURNACE, Facing.SOUTH);
		c.set(x0 + 5, 1, z0 + 2, VPart.SMOKER, Facing.SOUTH);
		Buildings.chimney(c, x0 + 3, z0 + 1, 3, style.flatRoof() ? 2 : 1);
		c.set(x1 - 1, 1, z0 + 3, VPart.CHEST, Facing.WEST);
		sacks(c, x1 - 1, z0 + 4, 2);
		sacks(c, x1 - 2, z0 + 4, 1);
		// balcao
		for (int x = x0 + 1; x <= mid - 2; x++) {
			c.set(x, 1, z1 - 3, VPart.WOOD_SLAB_TOP);
		}
		c.set(x0 + 1, 1, z1 - 3, VPart.BARREL);
		c.set(x0 + 3, 1, z1 - 3, VPart.BARREL);
		c.set(x0 + 2, 2, z1 - 3, VPart.LANTERN);
		c.set(x1 - 1, 1, z1 - 2, VPart.CRAFTING);
		if (floors == 2) {
			int y = BuildingPlans.floorY(1) + 1;
			Buildings.bed(c, x0 + 1, y, z0 + 2, Facing.NORTH);
			c.set(x1 - 1, y, z0 + 3, VPart.CHEST, Facing.WEST);
			c.set(x0 + 4, y, z0 + 1, VPart.FURNACE, Facing.SOUTH);
			c.set(x0 + 3, y, z0 + 1, VPart.SMOKER, Facing.SOUTH);
			c.set(mid, y, z0 + 4, VPart.WOOD_SLAB_TOP);
			c.set(mid, y + 1, z0 + 4, VPart.LANTERN);
			sacks2(c, x1 - 1, y, z1 - 1);
		}
		awning(c, mid, 3, z1 + 1, 3);
		c.set(mid - 2, 1, z1 + 1, VPart.BARREL);
		c.set(mid + 2, 1, z1 + 1, VPart.BARREL);
		if (level >= 3) {
			// forno de rua de tijolo com cupula no flanco leste
			for (int z = 3; z <= 4; z++) {
				c.set(10, 0, z, VPart.PAVING);
				c.set(10, 1, z, VPart.BRICK);
				c.set(10, 2, z, VPart.BRICK);
			}
			c.set(10, 3, 3, VPart.STONE_SLAB);
			c.set(10, 3, 4, VPart.STONE_SLAB);
			c.set(10, 3, 2, VPart.BRICK);
			c.set(10, 0, 2, VPart.PAVING);
			c.set(10, 1, 2, VPart.BRICK);
			c.set(10, 2, 2, VPart.BRICK);
			c.set(10, 4, 2, VPart.CAMPFIRE);
			// mesas no patio da frente
			for (int[] t : new int[][] {{1, 9}, {9, 9}}) {
				c.set(t[0], 0, t[1], VPart.PAVING);
				c.set(t[0], 1, t[1], VPart.FENCE);
				c.set(t[0], 2, t[1], VPart.WOOD_SLAB_TOP);
				int side = t[0] == 1 ? 1 : -1;
				c.set(t[0] + side, 0, t[1], VPart.PAVING_ALT);
				c.set(t[0] + side, 1, t[1], VPart.WOOD_STAIRS, side == 1 ? Facing.WEST : Facing.EAST);
			}
		}
		finish(c, mid, z1 + 1);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}

	// ---------------------------------------------------------------- pomar

	/** Posicoes das macieiras (x, z) em ordem de melhoria. */
	private static final int[][] TREES = {{3, 3}, {11, 3}, {3, 7}, {11, 7}, {7, 3}, {7, 7}};

	private static void tree(Canvas c, int x, int z) {
		c.set(x, 0, z, VPart.SOIL);
		column(c, x, z, 1, 4, VPart.LOG);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					c.set(x + dx, 3, z + dz, VPart.FOLIAGE);
				}
				c.set(x + dx, 4, z + dz, VPart.FOLIAGE);
			}
		}
		c.set(x, 5, z, VPart.FOLIAGE);
		c.set(x - 1, 5, z, VPart.FOLIAGE);
		c.set(x + 1, 5, z, VPart.FOLIAGE);
		c.set(x, 5, z - 1, VPart.FOLIAGE);
		c.set(x, 5, z + 1, VPart.FOLIAGE);
	}

	/**
	 * Pomar 15x13: cercado de estacas com portao, macieiras em fileiras (4, 6 no II), alameda de terra entre elas,
	 * arbustos de frutas doces na frente e uma barraca de ferramentas com bau. III: a barraca vira prensa de cidra
	 * (barris, caldeirao, bancada) e ha um caramanchao de entrada.
	 */
	static Canvas orchard(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		fenceRing(c, 0, 0, 14, 12, 7);
		for (int x = 1; x <= 13; x++) {
			c.set(x, 0, 5, VPart.PATH);
		}
		for (int z = 8; z <= 11; z++) {
			c.set(7, 0, z, VPart.PATH);
		}
		for (int z = 6; z <= 7; z++) {
			c.set(7, 0, z, VPart.PATH);
		}
		int trees = level == 1 ? 4 : 6;
		for (int i = 0; i < trees; i++) {
			tree(c, TREES[i][0], TREES[i][1]);
		}
		// flores e arbustos
		for (int x = 1; x <= 5; x++) {
			if (x != 3) {
				c.set(x, 0, 10, VPart.SOIL);
				c.set(x, 1, 10, VPart.BERRY_BUSH);
			}
		}
		for (int x = 2; x <= 12; x += 2) {
			c.setIfAbsent(x, 1, 1, VPart.FLOWER);
		}
		// barraca de ferramentas
		canopy(c, 9, 9, 13, 11, 4);
		for (int x = 9; x <= 13; x++) {
			for (int z = 9; z <= 11; z++) {
				c.set(x, 0, z, VPart.PLANKS);
			}
		}
		c.set(13, 1, 9, VPart.CHEST, Facing.SOUTH);
		c.set(12, 1, 9, VPart.BARREL);
		c.set(11, 1, 9, VPart.COMPOSTER);
		c.set(13, 1, 11, VPart.BARREL);
		c.set(11, 3, 10, VPart.LANTERN_HANGING);
		if (level >= 3) {
			c.set(12, 1, 11, VPart.CAULDRON);
			c.set(11, 1, 11, VPart.BARREL);
			c.set(10, 1, 11, VPart.BARREL);
			c.set(10, 2, 11, VPart.BARREL);
			c.set(9, 1, 10, VPart.WOOD_SLAB_TOP);
			// caramanchao de entrada: dois postes e viga com folhagem
			for (int x : new int[] {6, 8}) {
				column(c, x, 12, 1, 3, VPart.LOG);
			}
			for (int x = 6; x <= 8; x++) {
				c.set(x, 4, 12, VPart.BEAM, Facing.EAST);
			}
			c.set(7, 5, 12, VPart.FOLIAGE);
			c.set(6, 5, 12, VPart.FOLIAGE);
			c.set(8, 5, 12, VPart.FOLIAGE);
		}
		c.doorX(7);
		c.spawn(7, 1, 9);
		return c;
	}

	// ---------------------------------------------------------------- curral

	/**
	 * Curral 15x13: cercado de estacas com portao, abrigo de feno (laje sobre troncos) com o bau de trabalho, cocho de
	 * agua e fardos de feno. II: segundo cocho, comedouro de cerca e feno e canteiro de grama alta. III: celeiro de
	 * enxaimel no fundo com bau e estantes de feno, e o abrigo some.
	 */
	static Canvas pen(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		fenceRing(c, 0, 0, 14, 12, 7);
		for (int z = 8; z <= 11; z++) {
			c.set(7, 0, z, VPart.PATH);
		}
		if (level < 3) {
			canopy(c, 1, 1, 6, 3, 4);
			for (int x = 1; x <= 6; x++) {
				c.set(x, 0, 1, VPart.PLANKS);
				c.set(x, 0, 2, VPart.PLANKS);
				c.set(x, 0, 3, VPart.PLANKS);
			}
			c.set(5, 1, 2, VPart.CHEST, Facing.SOUTH);
			c.set(2, 1, 1, VPart.HAY, Facing.EAST);
			c.set(3, 1, 1, VPart.HAY, Facing.EAST);
			c.set(2, 2, 1, VPart.HAY, Facing.EAST);
			c.set(6, 1, 1, VPart.BARREL);
			c.set(4, 3, 3, VPart.LANTERN_HANGING);
		} else {
			int x0 = 2;
			int x1 = 12;
			int z0 = 1;
			int z1 = 5;
			BuildingPlans.body(c, style, x0, z0, x1, z1, 1, true, false);
			c.set(x0 + 1, 1, z0 + 1, VPart.CHEST, Facing.SOUTH);
			for (int x = x0 + 3; x <= x1 - 1; x++) {
				c.set(x, 1, z0 + 1, VPart.HAY, Facing.EAST);
				c.set(x, 2, z0 + 1, VPart.HAY, Facing.EAST);
			}
			c.set(x0 + 1, 1, z0 + 2, VPart.BARREL);
			c.set(x0 + 1, 1, z0 + 3, VPart.CAULDRON);
			BuildingPlans.lamp(c, x1 - 1, 1, z1 - 1);
		}
		c.set(10, 1, 3, VPart.TROUGH);
		c.set(11, 1, 3, VPart.TROUGH);
		if (level >= 3) {
			c.remove(10, 1, 3);
			c.remove(11, 1, 3);
			c.set(10, 1, 7, VPart.TROUGH);
			c.set(11, 1, 7, VPart.TROUGH);
		}
		c.set(12, 1, 9, VPart.HAY, Facing.EAST);
		c.set(12, 1, 10, VPart.HAY, Facing.EAST);
		c.set(12, 2, 10, VPart.HAY, Facing.EAST);
		if (level >= 2) {
			// comedouro de cerca com feno e segundo cocho
			for (int x = 2; x <= 5; x++) {
				c.set(x, 1, 9, VPart.FENCE);
			}
			c.set(3, 1, 10, VPart.HAY, Facing.EAST);
			c.set(4, 1, 10, VPart.HAY, Facing.EAST);
			c.set(2, 1, 6 + (level >= 3 ? 1 : 0), VPart.TROUGH);
			c.set(3, 1, 6 + (level >= 3 ? 1 : 0), VPart.TROUGH);
			for (int x = 9; x <= 13; x++) {
				c.setIfAbsent(x, 1, 6 + (level >= 3 ? 2 : 0), VPart.PLANT);
			}
		}
		c.doorX(7);
		c.spawn(7, 1, 10);
		return c;
	}

	// ---------------------------------------------------------------- apiario

	/**
	 * Apiario 11x11: jardim de flores com colmeias em fileiras (4 no I, 8 no II) e a casa do mel 7x5 no fundo, com bau,
	 * tonel de mel (caldeirao), barris e defumador de abelhas. II: segunda fileira de colmeias, arco de flores na
	 * entrada e bancada de extracao.
	 */
	static Canvas apiary(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(11, 11);
		int x0 = 2;
		int x1 = 8;
		int z0 = 1;
		int z1 = 5;
		int mid = 5;
		BuildingPlans.body(c, style, x0, z0, x1, z1, 1, true, false);
		c.set(x0 + 1, 1, z0 + 1, VPart.CHEST, Facing.SOUTH);
		c.set(x0 + 2, 1, z0 + 1, VPart.BARREL);
		c.set(x1 - 1, 1, z0 + 1, VPart.CAULDRON);
		c.set(x1 - 2, 1, z0 + 1, VPart.SMOKER, Facing.SOUTH);
		c.set(x0 + 1, 1, z1 - 1, VPart.BARREL);
		BuildingPlans.lamp(c, x1 - 1, 1, z1 - 1);
		if (level >= 2) {
			c.set(x0 + 2, 1, z1 - 1, VPart.CRAFTING);
			c.set(x1 - 1, 1, z0 + 2, VPart.BARREL);
		}
		// jardim
		for (int z = 7; z <= 9; z++) {
			for (int x = 1; x <= 9; x++) {
				if (x == mid) {
					c.set(x, 0, z, VPart.PATH);
				} else if ((x * 7 + z * 3) % 4 != 0) {
					c.set(x, 0, z, VPart.SOIL);
					c.setIfAbsent(x, 1, z, VPart.FLOWER);
				}
			}
		}
		for (int x : new int[] {1, 3, 7, 9}) {
			c.set(x, 0, 7, VPart.SOIL);
			c.set(x, 1, 7, VPart.BEEHIVE, Facing.SOUTH);
		}
		if (level >= 2) {
			for (int x : new int[] {2, 4, 6, 8}) {
				c.set(x, 0, 9, VPart.SOIL);
				c.set(x, 1, 9, VPart.BEEHIVE, Facing.SOUTH);
			}
			// arco de flores na entrada
			for (int x : new int[] {4, 6}) {
				column(c, x, 10, 1, 3, VPart.FENCE);
			}
			for (int x = 4; x <= 6; x++) {
				c.set(x, 4, 10, VPart.FOLIAGE);
			}
		}
		// postes de lanterna na frente
		for (int x : new int[] {2, 8}) {
			column(c, x, 10, 1, 2, VPart.FENCE);
			c.set(x, 3, 10, VPart.LANTERN);
		}
		finish(c, mid, z1 + 1);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}

	// ---------------------------------------------------------------- acougue

	/**
	 * Acougue 11x11: loja de pedra e enxaimel 9x7 com defumadores no fundo (2 no I, 3 no II), bau de trabalho, cepo de
	 * tronco, balcao e barris de salmoura; toldo vermelho e branco na frente. II: andar de cura com mais defumadores,
	 * barris e caldeirao.
	 */
	static Canvas butcher(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(11, 11);
		int x0 = 1;
		int x1 = 9;
		int z0 = 1;
		int z1 = 7;
		int mid = 5;
		int floors = level >= 2 ? 2 : 1;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, true, true);
		int smokers = level >= 2 ? 3 : 2;
		for (int i = 0; i < smokers; i++) {
			c.set(x0 + 1 + i, 1, z0 + 1, VPart.SMOKER, Facing.SOUTH);
		}
		c.set(x1 - 1, 1, z0 + 3, VPart.CHEST, Facing.WEST);
		c.set(x1 - 2, 1, z0 + 1, VPart.BARREL);
		c.set(x1 - 1, 1, z0 + 4, VPart.CAULDRON);
		c.set(x0 + 1, 1, z0 + 3, VPart.LOG);
		c.set(x0 + 1, 2, z0 + 3, VPart.WOOD_SLAB_TOP);
		for (int x = x0 + 1; x <= mid - 2; x++) {
			c.set(x, 1, z1 - 2, VPart.WOOD_SLAB_TOP);
		}
		c.set(x0 + 1, 1, z1 - 2, VPart.BARREL);
		c.set(x0 + 2, 2, z1 - 2, VPart.LANTERN);
		if (floors == 2) {
			int y = BuildingPlans.floorY(1) + 1;
			for (int i = 0; i < 4; i++) {
				c.set(x0 + 1 + i, y, z0 + 1, i % 2 == 0 ? VPart.SMOKER : VPart.BARREL, i % 2 == 0 ? Facing.SOUTH : null);
			}
			c.set(x1 - 1, y, z0 + 3, VPart.CHEST, Facing.WEST);
			c.set(x1 - 1, y, z0 + 4, VPart.CAULDRON);
			BuildingPlans.lamp(c, x0 + 1, y, z1 - 1);
		}
		awning(c, mid, 2, z1 + 1, 3);
		c.set(mid - 3, 1, z1 + 1, VPart.BARREL);
		c.set(mid + 3, 1, z1 + 1, VPart.BARREL);
		c.set(mid + 3, 2, z1 + 1, VPart.BARREL);
		finish(c, mid, z1 + 1);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}

	// ---------------------------------------------------------------- vinicola

	private static void trellisRow(Canvas c, int x, int z0, int z1) {
		for (int z = z0; z <= z1; z++) {
			c.set(x, 0, z, VPart.FARMLAND);
			c.set(x, 1, z, VPart.GRAPE_VINE);
			c.set(x, 3, z, VPart.FENCE);
			if (z % 2 == 0) {
				c.set(x, 4, z, VPart.FOLIAGE);
			}
		}
		for (int z : new int[] {z0, (z0 + z1) / 2, z1}) {
			c.set(x, 2, z, VPart.FENCE);
			c.set(x, 0, z, VPart.SOIL);
			c.set(x, 1, z, VPart.LOG);
			c.set(x, 2, z, VPart.LOG);
			c.set(x, 3, z, VPart.LOG);
		}
	}

	/**
	 * Vinicola 19x13: parreirais em latada (2 fileiras no I, 3 no II-III; videiras de uva sobre canteiro, postes e
	 * trilho de cerca com folhagem) e a adega de pedra 7x8 com barris de vinho, prensa (caldeirao), bau de trabalho e
	 * alambique (II). II: andar de degustacao. III: terraco de pedra na frente com mesas e barris empilhados.
	 */
	static Canvas winery(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(19, 13);
		int rows = level >= 2 ? 3 : 2;
		int[] rowX = rows == 3 ? new int[] {2, 5, 8} : new int[] {3, 7};
		for (int x : rowX) {
			trellisRow(c, x, 2, 10);
		}
		for (int z = 1; z <= 11; z++) {
			c.set(1, 0, z, VPart.SOIL);
		}
		int x0 = 11;
		int x1 = 17;
		int z0 = 2;
		int z1 = 9;
		int mid = 14;
		int floors = level >= 2 ? 2 : 1;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, false, true);
		c.set(x0 + 1, 1, z0 + 1, VPart.CHEST, Facing.SOUTH);
		c.set(x1 - 1, 1, z0 + 2, VPart.CAULDRON);
		int barrels = level == 1 ? 4 : level == 2 ? 8 : 12;
		int placed = 0;
		for (int z = z0 + 3; z <= z1 - 3 && placed < barrels; z++) {
			for (int tier = 1; tier <= (level >= 3 ? 2 : 1) && placed < barrels; tier++) {
				c.set(x0 + 1, tier, z, VPart.BARREL);
				placed++;
				if (placed < barrels) {
					c.set(x1 - 1, tier, z, VPart.BARREL);
					placed++;
				}
			}
		}
		c.set(x0 + 2, 1, z0 + 1, VPart.BARREL);
		BuildingPlans.lamp(c, x1 - 1, 1, z1 - 1);
		if (floors == 2) {
			int y = BuildingPlans.floorY(1) + 1;
			c.set(x0 + 1, y, z0 + 1, VPart.BREWING);
			c.set(x0 + 2, y, z0 + 1, VPart.BARREL);
			c.set(x1 - 1, y, z0 + 2, VPart.CHEST, Facing.WEST);
			for (int x = x0 + 2; x <= x1 - 2; x++) {
				c.set(x, y, z0 + 4, VPart.WOOD_SLAB_TOP);
			}
			c.set(x0 + 2, y, z0 + 5, VPart.WOOD_STAIRS, Facing.NORTH);
			c.set(x1 - 2, y, z0 + 5, VPart.WOOD_STAIRS, Facing.NORTH);
			c.set(mid, y + 1, z0 + 4, VPart.LANTERN);
			BuildingPlans.lamp(c, x1 - 1, y, z1 - 1);
		}
		if (level >= 3) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z1 + 1; z <= z1 + 2; z++) {
					c.set(x, 0, z, (x + z) % 2 == 0 ? VPart.PAVING : VPart.PAVING_ALT);
				}
			}
			for (int[] t : new int[][] {{x0 + 1, z1 + 2}, {x1 - 1, z1 + 2}}) {
				c.set(t[0], 1, t[1], VPart.FENCE);
				c.set(t[0], 2, t[1], VPart.WOOD_SLAB_TOP);
			}
			c.set(x0, 1, z1 + 1, VPart.BARREL);
			c.set(x0, 2, z1 + 1, VPart.BARREL);
			c.set(x1, 1, z1 + 1, VPart.BARREL);
		}
		c.set(mid - 2, 0, z1 + 1, VPart.PATH);
		for (int z = z1 + 1; z < c.d(); z++) {
			c.set(mid, 0, z, VPart.PATH);
		}
		for (int x = 9; x <= 14; x++) {
			c.set(x, 0, 11, VPart.PATH);
		}
		finish(c, mid, z1 + 1);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}

	// ---------------------------------------------------------------- serraria

	/**
	 * Serraria 15x13: galpao 11x8 aberto na frente (estacas e viga na frente, tres paredes), bancada de serra
	 * (cortador de pedra), pilhas de toras e de tabuas, mesa de trabalho e bau; toras empilhadas no patio. II: segunda
	 * bancada e secador de tabuas (cerca + laje) no patio. III: terceira bancada, guindaste de viga e pilhas altas.
	 */
	static Canvas sawmill(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		int x0 = 2;
		int x1 = 12;
		int z0 = 1;
		int z1 = 8;
		int mid = 7;
		BuildingPlans.body(c, style, x0, z0, x1, z1, 1, true, false);
		// frente aberta: fica so a ossatura (estacas nos cantos e a cada 3)
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int y = 1; y <= 3; y++) {
				c.remove(x, y, z1);
			}
			c.set(x, 0, z1, VPart.PAVING);
			if ((x - x0) % 3 == 0 && x != mid) {
				column(c, x, z1, 1, 3, VPart.LOG);
			}
		}
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				c.set(x, 0, z, (x + z) % 3 == 0 ? VPart.PAVING_ALT : VPart.FLOOR);
			}
		}
		for (int i = 0; i < Math.min(3, level); i++) {
			c.set(mid - 3 + i * 3, 1, z0 + 3, VPart.STONECUTTER, Facing.SOUTH);
		}
		// pilhas de toras (deitadas) e de tabuas junto das paredes
		for (int x = x0 + 1; x <= x0 + 3; x++) {
			c.set(x, 1, z0 + 1, VPart.LOG, Facing.EAST);
			c.set(x, 2, z0 + 1, VPart.LOG, Facing.EAST);
			if (level >= 3) {
				c.set(x, 3, z0 + 1, VPart.LOG, Facing.EAST);
			}
		}
		for (int x = x1 - 3; x <= x1 - 2; x++) {
			c.set(x, 1, z0 + 1, VPart.PLANKS);
			c.set(x, 2, z0 + 1, VPart.PLANKS);
		}
		c.set(x1 - 1, 1, z0 + 1, VPart.CHEST, Facing.SOUTH);
		c.set(x1 - 1, 1, z0 + 4, VPart.CRAFTING);
		c.set(x0 + 1, 1, z0 + 5, VPart.BARREL);
		c.set(x0 + 1, 2, z0 + 5, VPart.LANTERN);
		// patio: toras empilhadas a direita, secador de tabuas a esquerda
		for (int z = 3; z <= 5; z++) {
			for (int h = 1; h <= (level >= 3 ? 3 : 2); h++) {
				c.set(13, h, z, VPart.LOG, Facing.SOUTH);
				if (h == 1) {
					c.set(14, h, z, VPart.LOG, Facing.SOUTH);
				}
			}
			c.set(13, 0, z, VPart.SOIL);
			c.set(14, 0, z, VPart.SOIL);
		}
		if (level >= 2) {
			for (int z = 3; z <= 6; z++) {
				c.set(0, 0, z, VPart.SOIL);
				c.set(1, 0, z, VPart.SOIL);
				column(c, 0, z, 1, 2, VPart.FENCE);
				if (z % 2 == 1) {
					c.set(1, 1, z, VPart.PLANKS);
					c.set(1, 2, z, VPart.PLANKS);
				}
			}
			for (int z = 3; z <= 6; z++) {
				c.set(0, 3, z, VPart.WOOD_SLAB);
			}
		}
		if (level >= 3) {
			// guindaste: poste de tronco com braco de viga e corrente de cerca
			column(c, 13, 7, 1, 5, VPart.LOG);
			c.set(13, 0, 7, VPart.SOIL);
			for (int x = 10; x <= 13; x++) {
				c.set(x, 6, 7, VPart.BEAM, Facing.EAST);
			}
			c.set(13, 6, 7, VPart.LOG);
			c.set(10, 5, 7, VPart.FENCE);
			c.set(10, 4, 7, VPart.FENCE);
			c.set(10, 3, 7, VPart.LOG, Facing.SOUTH);
			c.set(10, 0, 7, VPart.SOIL);
			c.set(10, 1, 7, VPart.LOG, Facing.SOUTH);
			c.set(10, 2, 7, VPart.LOG, Facing.SOUTH);
			c.set(12, 5, 7, VPart.LANTERN_HANGING);
		}
		finish(c, mid, z1 + 1);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}
}
