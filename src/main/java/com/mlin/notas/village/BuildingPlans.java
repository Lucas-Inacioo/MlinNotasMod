package com.mlin.notas.village;

import java.util.List;
import java.util.Random;

/**
 * Plantas das construcoes nos niveis acima do I e da Prefeitura (logica pura), em coordenadas de {@link Canvas} como
 * em {@link Buildings}. Cada nivel cabe na classe de lote do tipo ({@link LotRules}); o nivel I e sempre a planta de
 * {@link Buildings#plan} com a mesma semente (os lotes ja reservados nao mudam).
 *
 * <p>Referencias de desenho (escritas do zero, nada copiado): prefeitura medieval com terreo de pedra, andar de
 * enxaimel e torre do relogio no meio da fachada (lista de prefeituras da Sportskeeda, "Medieval Town Hall" do
 * mc-mod e a torre "Everhollow Clocktower" do Planet Minecraft, que serve de marco da praca); sobrados de tres
 * andares com pavimentos em balanco (casas de enxaimel do Shulkr); oficinas de ferreiro que crescem com mais
 * fornalhas e chamine alta (cidades medievais Andreth e Gelwenton do Planet Minecraft); mercado coberto com duas
 * fileiras de barracas.
 */
public final class BuildingPlans {
	private BuildingPlans() {
	}

	/** Planta do tipo no nivel (I = a de sempre). */
	public static Canvas plan(VillageProject project, VillageStyle style, long seed, int level) {
		if (project == VillageProject.TOWN_HALL) {
			return townHall(style, new Random(seed), Math.max(1, level));
		}
		if (level <= 1) {
			return Buildings.plan(project, style, seed);
		}
		Random random = new Random(seed ^ (level * 0x9E3779B97F4A7C15L));
		return switch (project) {
			case HOUSE -> level == 2 ? houseII(style, random) : houseIII(style, random);
			case FARM -> farm(style, random, level);
			case SMITHY -> smithy(style, random, level);
			case MARKET -> market(style, random, level);
			case INN -> inn(style, random, level);
			case LIBRARY -> library(style, random, level);
			default -> Buildings.plan(project, style, seed);
		};
	}

	/** Planta do tipo do lote no nivel ({@code kind} = id do projeto). */
	public static Canvas plan(String kind, VillageStyle style, long seed, int level) {
		VillageProject project = VillageProject.byId(kind);
		return project == null ? VillageLayout.canvas(kind, style, seed) : plan(project, style, seed, level);
	}

	/** Todas as plantas de todos os niveis do projeto (testes). */
	public static List<Canvas> levels(VillageProject project, VillageStyle style) {
		List<Canvas> out = new java.util.ArrayList<>();
		for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
			for (long seed = 1; seed <= 3; seed++) {
				out.add(plan(project, style, seed, level));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- pecas

	/** Piso de um andar de cima: cantos de tronco, beiradas de viga e assoalho. */
	static void storeyFloor(Canvas c, VillageStyle style, int x0, int z0, int x1, int z1, int y) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				c.set(x, y, z, corner ? (style.flatRoof() ? VPart.BRICK : VPart.LOG) : edge ? VPart.BEAM : VPart.FLOOR,
						edge && !corner ? (z == z0 || z == z1 ? Facing.EAST : Facing.SOUTH) : null);
			}
		}
	}

	/** y do piso do andar {@code k} (0 = terreo, no chao). */
	static int floorY(int k) {
		return k == 0 ? 0 : 4 * k + 1;
	}

	/**
	 * Corpo de {@code floors} andares no retangulo: terreo de pedra (ou enxaimel com fiada de pedra), andares de
	 * enxaimel, janelas, porta no meio da frente (+z), escada de mao no canto do fundo a direita e telhado. Devolve o y
	 * do topo das paredes.
	 */
	static int body(Canvas c, VillageStyle style, int x0, int z0, int x1, int z1, int floors, boolean alongX, boolean stoneGround) {
		Buildings.floor(c, x0, z0, x1, z1);
		int mid = (x0 + x1) / 2;
		for (int k = 0; k < floors; k++) {
			int yb = k == 0 ? 1 : 4 * k + 2;
			int yt = 4 * k + 4;
			if (k >= 1) {
				storeyFloor(c, style, x0, z0, x1, z1, 4 * k + 1);
			}
			Buildings.walls(c, style, x0, z0, x1, z1, yb, yt, k == 0 && stoneGround, k == 0 && !stoneGround);
			int wy = k == 0 ? 2 : 4 * k + 3;
			for (int x = x0 + 1; x < x1; x++) {
				if ((x - x0) % 3 == 2) {
					if (!(k == 0 && Math.abs(x - mid) <= 1)) {
						Buildings.window(c, x, wy, z1, Facing.SOUTH, k > 0);
					}
					Buildings.window(c, x, wy, z0, Facing.NORTH, false);
				}
			}
			for (int z = z0 + 1; z < z1; z++) {
				if ((z - z0) % 3 == 2) {
					Buildings.window(c, x0, wy, z, Facing.WEST, false);
					Buildings.window(c, x1, wy, z, Facing.EAST, false);
				}
			}
		}
		Buildings.door(c, mid, 1, z1, Facing.SOUTH);
		if (!style.flatRoof()) {
			for (int y = 1; y <= 2; y++) {
				c.set(mid - 1, y, z1, stoneGround ? VPart.BRICK : VPart.LOG);
				c.set(mid + 1, y, z1, stoneGround ? VPart.BRICK : VPart.LOG);
			}
		}
		if (floors > 1) {
			for (int y = 1; y <= 4 * (floors - 1) + 1; y++) {
				c.set(x1 - 1, y, z0 + 1, VPart.LADDER, Facing.SOUTH);
			}
		}
		int top = 4 * floors;
		Buildings.roof(c, style, x0, z0, x1, z1, top, alongX);
		return top;
	}

	/** Canteiros na frente e caminho ate a porta. */
	static void frontYard(Canvas c, int doorX) {
		int z = c.d() - 1;
		for (int x = 1; x < c.w() - 1; x++) {
			if (Math.abs(x - doorX) >= 2 && x % 2 == 0) {
				c.setIfAbsent(x, 1, z, VPart.FLOWER);
			}
		}
		Buildings.yard(c);
		c.set(doorX, 0, z, VPart.PATH);
		c.doorX(doorX);
	}

	/** Lanterna de pe sobre um barril. */
	static void lamp(Canvas c, int x, int y, int z) {
		c.set(x, y, z, VPart.BARREL);
		c.set(x, y + 1, z, VPart.LANTERN);
	}

	// ---------------------------------------------------------------- casas

	/** Casa II: sobrado 9x8 de dois andares com tres camas, cozinha e chamine. 11x11. */
	static Canvas houseII(VillageStyle style, Random random) {
		Canvas c = new Canvas(11, 11);
		body(c, style, 1, 1, 9, 8, 2, true, true);
		Buildings.chimney(c, 1, 4, 5, style.flatRoof() ? 2 : 2);
		// terreo: cozinha, mesa e oficio
		c.set(2, 1, 2, VPart.FURNACE, Facing.SOUTH);
		c.set(3, 1, 2, VPart.CRAFTING);
		c.set(4, 1, 2, VPart.BARREL);
		c.set(2, 1, 6, Buildings.houseJob(random), Facing.EAST);
		c.set(6, 1, 4, VPart.WOOD_SLAB_TOP);
		c.set(6, 2, 4, VPart.LANTERN);
		c.set(6, 1, 3, VPart.WOOD_STAIRS, Facing.NORTH);
		c.set(6, 1, 5, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.set(8, 1, 7, VPart.FLOWER_POT);
		// andar: tres camas e bau
		Buildings.bed(c, 2, 6, 3, Facing.NORTH);
		Buildings.bed(c, 4, 6, 3, Facing.NORTH);
		Buildings.bed(c, 2, 6, 6, Facing.SOUTH);
		c.set(6, 6, 7, VPart.CHEST, Facing.NORTH);
		lamp(c, 7, 6, 7);
		for (int x = 4; x <= 6; x++) {
			c.set(x, 6, 5, VPart.CARPET);
		}
		// varanda: postes e lanternas na frente
		for (int x : new int[] {3, 7}) {
			c.set(x, 1, 9, VPart.FENCE);
			c.set(x, 2, 9, VPart.LANTERN);
		}
		frontYard(c, 5);
		c.spawn(5, 1, 5);
		c.spawn(6, 6, 4);
		return c;
	}

	/** Casa III: casarao 9x9 de tres andares com quatro camas, sala e escritorio. 11x11. */
	static Canvas houseIII(VillageStyle style, Random random) {
		Canvas c = new Canvas(11, 11);
		body(c, style, 1, 1, 9, 9, 3, false, true);
		Buildings.chimney(c, 1, 5, 5, 2);
		// terreo: sala com lareira e cozinha
		c.set(2, 1, 4, VPart.BRICK);
		c.set(2, 1, 6, VPart.BRICK);
		c.set(2, 1, 5, VPart.CAMPFIRE);
		c.set(2, 2, 4, VPart.BRICK);
		c.set(2, 2, 5, VPart.BRICK);
		c.set(2, 2, 6, VPart.BRICK);
		c.set(3, 1, 2, VPart.FURNACE, Facing.SOUTH);
		c.set(4, 1, 2, VPart.SMOKER, Facing.SOUTH);
		c.set(5, 1, 2, VPart.CRAFTING);
		c.set(6, 1, 2, VPart.BARREL);
		c.set(6, 1, 6, VPart.WOOD_SLAB_TOP);
		c.set(6, 2, 6, VPart.FLOWER_POT);
		c.set(6, 1, 5, VPart.WOOD_STAIRS, Facing.NORTH);
		c.set(6, 1, 7, VPart.WOOD_STAIRS, Facing.SOUTH);
		for (int z = 4; z <= 7; z++) {
			c.set(4, 1, z, VPart.CARPET_ALT);
		}
		// segundo andar: dois quartos
		Buildings.bed(c, 2, 6, 3, Facing.NORTH);
		Buildings.bed(c, 2, 6, 7, Facing.SOUTH);
		c.set(5, 6, 2, VPart.CHEST, Facing.SOUTH);
		c.set(3, 6, 5, Buildings.houseJob(random), Facing.EAST);
		lamp(c, 6, 6, 8);
		// terceiro andar: mais dois quartos e escritorio
		Buildings.bed(c, 2, 10, 3, Facing.NORTH);
		Buildings.bed(c, 4, 10, 3, Facing.NORTH);
		c.set(2, 10, 8, VPart.LECTERN, Facing.EAST);
		c.set(3, 10, 8, VPart.BOOKSHELF);
		c.set(6, 10, 8, VPart.CHEST, Facing.NORTH);
		c.set(7, 10, 5, VPart.LANTERN);
		for (int x = 4; x <= 6; x++) {
			c.set(x, 10, 6, VPart.CARPET);
		}
		// jardim cercado na frente
		for (int x = 1; x <= 9; x++) {
			if (Math.abs(x - 5) > 1) {
				c.set(x, 1, 10, VPart.HEDGE);
			}
		}
		c.set(4, 1, 10, VPart.FENCE);
		c.set(4, 2, 10, VPart.LANTERN);
		c.set(6, 1, 10, VPart.FENCE);
		c.set(6, 2, 10, VPart.LANTERN);
		frontYard(c, 5);
		c.spawn(5, 1, 5);
		c.spawn(6, 6, 5);
		c.spawn(6, 10, 5);
		return c;
	}

	// ---------------------------------------------------------------- plantacao

	/** Plantacao II/III: 11x11, tres canteiros com dois canais; no III um galpao com bau, composteiras e colmeia de feno. */
	static Canvas farm(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(11, 11);
		VPart[] crops = {VPart.CROP_WHEAT, VPart.CROP_CARROT, VPart.CROP_POTATO, VPart.CROP_BEET};
		int shedZ = level >= 3 ? 3 : 0;
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 10; z++) {
				boolean edge = x == 0 || x == 10 || z == 0 || z == 10;
				if (edge) {
					c.set(x, 0, z, VPart.SOIL);
					c.set(x, 1, z, VPart.FENCE);
				} else if (z <= shedZ) {
					c.set(x, 0, z, VPart.PATH);
				} else if (x == 5) {
					c.set(x, 0, z, z == 4 || z == 7 ? VPart.PLANKS : VPart.PATH);
				} else if (z == 4 || z == 7) {
					c.set(x, 0, z, VPart.WATER);
				} else {
					c.set(x, 0, z, VPart.FARMLAND);
					int bed = z < 4 ? 0 : z < 7 ? 1 : 2;
					c.set(x, 1, z, crops[Math.floorMod(bed * 3 + (x < 5 ? 0 : 1) + random.nextInt(2), crops.length)]);
				}
			}
		}
		c.set(5, 1, 10, VPart.FENCE_GATE, Facing.SOUTH);
		c.set(4, 2, 10, VPart.LANTERN);
		c.set(6, 2, 10, VPart.LANTERN);
		if (level >= 3) {
			// galpao aberto no fundo: postes, telhado de laje, bau, composteiras e feno
			for (int x = 1; x <= 9; x++) {
				for (int z = 1; z <= 2; z++) {
					c.set(x, 0, z, VPart.PLANKS);
				}
				c.set(x, 4, 1, VPart.ROOF_SLAB);
				c.set(x, 4, 2, VPart.ROOF_SLAB);
				c.set(x, 4, 3, VPart.ROOF_SLAB);
			}
			for (int x : new int[] {1, 5, 9}) {
				for (int y = 1; y <= 3; y++) {
					c.set(x, y, 3, VPart.LOG);
				}
			}
			for (int x = 1; x <= 9; x++) {
				for (int y = 1; y <= 3; y++) {
					c.set(x, y, 0, VPart.PLANKS);
				}
			}
			c.set(2, 1, 1, VPart.CHEST, Facing.SOUTH);
			c.set(3, 1, 1, VPart.COMPOSTER);
			c.set(4, 1, 1, VPart.COMPOSTER);
			c.set(6, 1, 1, VPart.HAY);
			c.set(7, 1, 1, VPart.HAY);
			c.set(7, 2, 1, VPart.HAY);
			c.set(8, 1, 1, VPart.BARREL);
			c.set(8, 3, 2, VPart.LANTERN_HANGING);
			c.set(2, 3, 2, VPart.LANTERN_HANGING);
		} else {
			c.set(1, 0, 1, VPart.SOIL);
			c.set(1, 1, 1, VPart.COMPOSTER);
			c.set(9, 0, 1, VPart.SOIL);
			c.set(9, 1, 1, VPart.HAY);
		}
		// espantalho
		c.set(2, 0, 8, VPart.SOIL);
		c.set(2, 1, 8, VPart.FENCE);
		c.set(2, 2, 8, VPart.HAY);
		c.set(2, 3, 8, VPart.PUMPKIN, Facing.SOUTH);
		c.doorX(5);
		c.spawn(5, 1, 9);
		return c;
	}

	// ---------------------------------------------------------------- forja

	/**
	 * Forja II..V: oficina de pedra aberta na frente que cresce (11x10 .. 15x13) com mais bigornas, alto-fornos e
	 * mesas; do III em diante uma sala de armas com bau; do IV um andar de deposito; no V chamine alta e lava.
	 */
	static Canvas smithy(VillageStyle style, Random random, int level) {
		int w = new int[] {9, 9, 11, 13, 15, 15}[level];
		int d = new int[] {10, 10, 10, 11, 12, 13}[level];
		Canvas c = new Canvas(w, d);
		int x0 = 1;
		int z0 = 1;
		int x1 = w - 2;
		int z1 = d - 2;
		int floors = level >= 4 ? 2 : 1;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				c.set(x, 0, z, edge ? VPart.FOUNDATION : (Math.floorMod(x + z, 3) == 0 ? VPart.PAVING_ALT : VPart.PAVING));
			}
		}
		Buildings.walls(c, style, x0, z0, x1, z1, 1, 4, true, false);
		if (floors == 2) {
			storeyFloor(c, style, x0, z0, x1, z1, 5);
			Buildings.walls(c, style, x0, z0, x1, z1, 6, 8, false, false);
			for (int x = x0 + 2; x < x1; x += 3) {
				Buildings.window(c, x, 7, z1, Facing.SOUTH, true);
			}
			for (int y = 1; y <= 5; y++) {
				c.set(x1 - 1, y, z0 + 1, VPart.LADDER, Facing.SOUTH);
			}
			c.set(x0 + 1, 6, z0 + 1, VPart.CHEST, Facing.SOUTH);
			c.set(x0 + 2, 6, z0 + 1, VPart.CHEST, Facing.SOUTH);
			c.set(x0 + 1, 6, z1 - 1, VPart.BARREL);
			c.set(x0 + 2, 6, z1 - 1, VPart.BARREL);
			c.set(x1 - 2, 6, z1 - 1, VPart.LANTERN);
		}
		// frente aberta entre esteios
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int y = 1; y <= 3; y++) {
				c.remove(x, y, z1);
			}
			c.set(x, 0, z1, VPart.PAVING);
		}
		for (int y = 1; y <= 3; y++) {
			c.set(x0, y, z1, style.flatRoof() ? VPart.BRICK : VPart.LOG);
			c.set(x1, y, z1, style.flatRoof() ? VPart.BRICK : VPart.LOG);
		}
		for (int z = z0 + 2; z < z1; z += 3) {
			Buildings.window(c, x0, 2, z, Facing.WEST, false);
			Buildings.window(c, x1, 2, z, Facing.EAST, false);
		}
		int top = floors == 2 ? 8 : 4;
		Buildings.roof(c, style, x0, z0, x1, z1, top, true);
		// fundo: alto-fornos com chamine, lava, mesas
		int furnaces = Math.min(3, (level + 1) / 2);
		for (int i = 0; i < furnaces; i++) {
			c.set(x0 + 1 + i * 3, 1, z0 + 1, VPart.BLAST_FURNACE, Facing.SOUTH);
		}
		Buildings.chimney(c, x0 + 1, z0, 1, level >= 5 ? 5 : style.flatRoof() ? 2 : 1);
		c.set(x0 + 2, 1, z0 + 1, VPart.LAVA_CAULDRON);
		c.set(x0 + 3, 1, z0 + 1, VPart.CHEST, Facing.SOUTH);
		c.set(x1 - 2, 1, z0 + 1, VPart.SMITHING_TABLE);
		// bigornas no meio
		int anvils = Math.min(3, level);
		for (int i = 0; i < anvils; i++) {
			c.set(x0 + 3 + i * 3, 1, z0 + 3, VPart.ANVIL, Facing.EAST);
		}
		c.set(x1 - 1, 1, z0 + 4, VPart.GRINDSTONE, Facing.WEST);
		c.set(x1 - 1, 1, z0 + 3, VPart.FURNACE, Facing.WEST);
		c.set(x0 + 1, 1, z1 - 2, VPart.BARREL);
		c.set(x0 + 1, 2, z1 - 2, VPart.LANTERN);
		c.set(x1 - 1, 1, z1 - 1, VPart.CAULDRON);
		if (level >= 3) {
			// sala de armas: baus e suportes (barris) junto a parede oeste
			c.set(x0 + 1, 1, z0 + 3, VPart.CHEST, Facing.EAST);
			c.set(x0 + 1, 1, z0 + 4, VPart.CHEST, Facing.EAST);
			c.set(x0 + 1, 1, z0 + 5, VPart.SMITHING_TABLE);
		}
		if (level >= 5) {
			c.set(x0 + 6, 1, z0 + 1, VPart.LAVA_CAULDRON);
			c.set(x1 - 3, 1, z0 + 1, VPart.LAVA_CAULDRON);
		}
		Buildings.yard(c);
		int door = (x0 + x1) / 2;
		c.set(door, 0, d - 1, VPart.PATH);
		c.doorX(door);
		c.spawn(door, 1, z1 - 2);
		c.golem(door, 1, z1 - 1);
		return c;
	}

	// ---------------------------------------------------------------- mercado

	/**
	 * Mercado II: quatro barracas 15x9. III: mercado de duas fileiras (quatro na frente, tres no fundo viradas para o
	 * corredor) com postes de lanterna e livreiro. 15x13.
	 */
	static Canvas market(VillageStyle style, Random random, int level) {
		int d = level >= 3 ? 13 : 9;
		Canvas c = new Canvas(15, d);
		for (int x = 0; x < 15; x++) {
			for (int z = 0; z < d; z++) {
				c.set(x, 0, z, Math.floorMod(x * 3 + z * 5 + random.nextInt(2), 7) == 0 ? VPart.GRAVEL : VPart.PATH);
			}
		}
		VPart[] jobs = {VPart.STONECUTTER, VPart.LOOM, VPart.CARTOGRAPHY, VPart.FLETCHING, VPart.SMOKER, VPart.BARREL, VPart.LECTERN};
		for (int i = 0; i < 4; i++) {
			stall(c, 4 * i, d - 8, jobs[i], (i % 2) == 0, Facing.SOUTH);
		}
		if (level >= 3) {
			for (int i = 0; i < 3; i++) {
				stall(c, 2 + 4 * i, 0, jobs[4 + i], (i % 2) == 1, Facing.SOUTH);
			}
			for (int x : new int[] {0, 14}) {
				c.set(x, 1, 6, VPart.FENCE);
				c.set(x, 2, 6, VPart.FENCE);
				c.set(x, 3, 6, VPart.LANTERN);
			}
		}
		c.set(0, 1, d - 2, VPart.BARREL);
		c.set(0, 2, d - 2, VPart.LANTERN);
		c.set(14, 1, d - 2, VPart.HAY);
		c.set(14, 1, d - 3, VPart.HAY);
		c.set(5, 1, d - 2, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.set(9, 1, d - 2, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.doorX(7);
		c.spawn(3, 1, d - 3);
		c.spawn(11, 1, d - 3);
		return c;
	}

	/** Barraca 3x4 com toldo listrado: postes, balcao e oficio no fundo. */
	private static void stall(Canvas c, int xs, int zs, VPart job, boolean alt, Facing front) {
		for (int[] p : new int[][] {{xs, zs + 1}, {xs + 2, zs + 1}, {xs, zs + 3}, {xs + 2, zs + 3}}) {
			c.set(p[0], 1, p[1], VPart.FENCE);
			c.set(p[0], 2, p[1], VPart.FENCE);
		}
		for (int x = xs; x <= xs + 2; x++) {
			for (int z = zs + 1; z <= zs + 4; z++) {
				c.set(x, 3, z, ((x - xs) % 2 == 0) != alt ? VPart.WOOL : VPart.WOOL_ALT);
			}
		}
		c.set(xs + 1, 1, zs + 1, job, front);
		c.set(xs + 1, 1, zs + 3, VPart.WOOD_SLAB_TOP);
		c.set(xs + 1, 2, zs + 3, VPart.FLOWER_POT);
		c.set(xs + 1, 2, zs + 2, VPart.LANTERN_HANGING);
	}

	// ---------------------------------------------------------------- estalagem e biblioteca

	/** Estalagem II: 11x11 de dois andares, seis camas. III: 13x11 de tres andares, dez camas. 15x13. */
	static Canvas inn(VillageStyle style, Random random, int level) {
		int bw = level >= 3 ? 13 : 11;
		int floors = level >= 3 ? 3 : 2;
		Canvas c = new Canvas(15, 13);
		int x0 = (15 - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = 11;
		body(c, style, x0, z0, x1, z1, floors, false, true);
		Buildings.chimney(c, x0, z0 + 5, floorY(1) + 1, 2);
		// terreo: balcao, lareira, mesas
		for (int x = x0 + 1; x <= x0 + 4; x++) {
			c.set(x, 1, z0 + 1, VPart.BARREL);
			c.set(x, 1, z0 + 3, VPart.WOOD_SLAB_TOP);
		}
		c.set(x0 + 5, 1, z0 + 1, VPart.BREWING);
		c.set(x0 + 2, 2, z0 + 3, VPart.LANTERN);
		c.set(x0 + 1, 1, z0 + 5, VPart.BRICK);
		c.set(x0 + 1, 1, z0 + 7, VPart.BRICK);
		c.set(x0 + 1, 1, z0 + 6, VPart.CAMPFIRE);
		for (int z = z0 + 5; z <= z0 + 7; z++) {
			c.set(x0 + 1, 2, z, VPart.BRICK);
		}
		for (int x = x0 + 4; x <= x1 - 2; x += 3) {
			c.set(x, 1, z0 + 7, VPart.WOOD_SLAB_TOP);
			c.set(x, 2, z0 + 7, VPart.LANTERN);
			c.set(x, 1, z0 + 6, VPart.WOOD_STAIRS, Facing.NORTH);
			c.set(x, 1, z0 + 8, VPart.WOOD_STAIRS, Facing.SOUTH);
		}
		// quartos: camas nas duas paredes laterais de cada andar
		for (int k = 1; k < floors; k++) {
			int y = floorY(k) + 1;
			for (int z = z0 + 2; z <= z1 - 2; z += 3) {
				Buildings.bed(c, x0 + 1, y, z, Facing.NORTH);
				if (z + 2 < z1) {
					Buildings.bed(c, x1 - 1 - (z == z0 + 2 ? 1 : 0), y, z + 1, Facing.SOUTH);
				}
			}
			c.set((x0 + x1) / 2, y, z1 - 1, VPart.CHEST, Facing.NORTH);
			for (int z = z0 + 3; z <= z1 - 2; z++) {
				c.set((x0 + x1) / 2, y, z, VPart.CARPET_ALT);
			}
		}
		// placas: bracos de viga com lanterna dos lados da porta
		int mid = (x0 + x1) / 2;
		for (int x : new int[] {mid - 2, mid + 2}) {
			c.set(x, 4, z1 + 1, VPart.BEAM, Facing.SOUTH);
			c.set(x, 3, z1 + 1, VPart.LANTERN_HANGING);
		}
		frontYard(c, mid);
		c.spawn(mid, 1, z0 + 5);
		c.spawn(mid, floorY(1) + 1, z0 + 5);
		return c;
	}

	/** Biblioteca II: 11x11 com estantes de dois andares e galeria. III: 13x11 com sala de leitura em cima. 15x13. */
	static Canvas library(VillageStyle style, Random random, int level) {
		int bw = level >= 3 ? 13 : 11;
		Canvas c = new Canvas(15, 13);
		int x0 = (15 - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = 11;
		body(c, style, x0, z0, x1, z1, 2, false, true);
		int mid = (x0 + x1) / 2;
		// terreo: estantes nas paredes laterais, atris e mesa de leitura
		for (int z = z0 + 1; z <= z1 - 2; z++) {
			if ((z - z0) % 3 != 2) {
				for (int y = 1; y <= 3; y++) {
					c.set(x0 + 1, y, z, VPart.BOOKSHELF);
				}
			}
			if ((z - z0) % 3 != 2 && z > z0 + 2) {
				for (int y = 1; y <= 3; y++) {
					c.set(x1 - 1, y, z, VPart.BOOKSHELF);
				}
			}
		}
		c.set(mid, 1, z0 + 2, VPart.LECTERN, Facing.SOUTH);
		c.set(mid - 2, 1, z0 + 5, VPart.LECTERN, Facing.EAST);
		for (int x = mid - 1; x <= mid + 1; x++) {
			c.set(x, 1, z0 + 6, VPart.WOOD_SLAB_TOP);
		}
		c.set(mid, 2, z0 + 6, VPart.LANTERN);
		c.set(mid - 1, 1, z0 + 7, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.set(mid + 1, 1, z0 + 7, VPart.WOOD_STAIRS, Facing.SOUTH);
		for (int z = z0 + 8; z <= z1 - 1; z++) {
			c.set(mid, 1, z, VPart.CARPET_ALT);
		}
		// andar: mais estantes e mesa de estudo; no III atris de copista
		int y = floorY(1) + 1;
		for (int x = x0 + 1; x <= x1 - 3; x++) {
			if ((x - x0) % 2 == 1) {
				c.set(x, y, z0 + 1, VPart.BOOKSHELF);
				c.set(x, y + 1, z0 + 1, VPart.BOOKSHELF);
			}
		}
		c.set(x0 + 1, y, z1 - 1, VPart.CHEST, Facing.NORTH);
		c.set(mid, y, z0 + 5, VPart.LECTERN, Facing.SOUTH);
		lamp(c, x1 - 1, y, z1 - 1);
		if (level >= 3) {
			c.set(mid - 2, y, z0 + 7, VPart.LECTERN, Facing.SOUTH);
			c.set(mid + 2, y, z0 + 7, VPart.LECTERN, Facing.SOUTH);
			c.set(mid, y, z0 + 8, VPart.CARTOGRAPHY);
		}
		frontYard(c, mid);
		c.spawn(mid, 1, z1 - 2);
		return c;
	}

	// ---------------------------------------------------------------- prefeitura

	/**
	 * Prefeitura I..V. I: salao 9x7 de um andar com atril, mapa e estantes. II: 11x8 com andar de escritorios. III:
	 * 13x8 com portico de colunas. IV: torre do relogio no meio da fachada. V: terceiro andar (arquivo) e torre mais
	 * alta. Cabe no lote medio (15x13).
	 */
	static Canvas townHall(VillageStyle style, Random random, int level) {
		int bw = new int[] {9, 9, 11, 13, 13, 13}[level];
		int bd = new int[] {7, 7, 8, 8, 8, 8}[level];
		int floors = level >= 5 ? 3 : level >= 2 ? 2 : 1;
		boolean tower = level >= 4;
		int front = level >= 3 ? 3 : 2;
		Canvas c = new Canvas(bw + 2, bd + 2 + front);
		int x0 = (c.w() - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = z0 + bd - 1;
		int mid = (x0 + x1) / 2;
		int top = body(c, style, x0, z0, x1, z1, floors, true, true);
		// salao do conselho: mesa comprida com cadeiras, atril e mapa na cabeceira, estantes nas paredes
		int ty = 1;
		for (int x = mid - 2; x <= mid + 2; x++) {
			c.set(x, ty, z0 + 3, VPart.WOOD_SLAB_TOP);
			if (z0 + 2 > z0) {
				c.set(x, ty, z0 + 2, VPart.WOOD_STAIRS, Facing.NORTH);
			}
			if (z0 + 4 < z1 - 1) {
				c.set(x, ty, z0 + 4, VPart.WOOD_STAIRS, Facing.SOUTH);
			}
		}
		c.set(mid, ty + 1, z0 + 3, VPart.LANTERN);
		c.set(mid - 1, 1, z0 + 1, VPart.LECTERN, Facing.SOUTH);
		c.set(mid + 1, 1, z0 + 1, VPart.CARTOGRAPHY);
		c.set(mid, 1, z0 + 1, VPart.BOOKSHELF);
		c.set(mid, 2, z0 + 1, VPart.FLOWER_POT);
		for (int y = 1; y <= 2; y++) {
			c.set(x0 + 1, y, z0 + 1, VPart.BOOKSHELF);
		}
		c.set(x0 + 1, 1, z1 - 1, VPart.CHEST, Facing.EAST);
		c.set(x0 + 1, 1, z1 - 2, VPart.BARREL);
		c.set(x0 + 1, 2, z1 - 2, VPart.LANTERN);
		if (floors >= 2) {
			int y = floorY(1) + 1;
			c.set(x0 + 1, y, z0 + 1, VPart.LECTERN, Facing.SOUTH);
			c.set(x0 + 2, y, z0 + 1, VPart.BOOKSHELF);
			c.set(x0 + 3, y, z0 + 1, VPart.BOOKSHELF);
			c.set(x0 + 1, y, z1 - 1, VPart.CHEST, Facing.NORTH);
			c.set(mid, y, z1 - 2, VPart.WOOD_SLAB_TOP);
			c.set(mid, y + 1, z1 - 2, VPart.LANTERN);
			c.set(mid, y, z1 - 3, VPart.WOOD_STAIRS, Facing.NORTH);
			for (int x = x0 + 2; x <= x1 - 3; x++) {
				c.set(x, y, (z0 + z1) / 2, VPart.CARPET_ALT);
			}
		}
		if (floors >= 3) {
			// arquivo no ultimo andar: estantes, baus e mesa de mapas
			int y = floorY(2) + 1;
			for (int x = x0 + 1; x <= x1 - 3; x++) {
				c.set(x, y, z0 + 1, VPart.BOOKSHELF);
			}
			c.set(x0 + 1, y, z1 - 1, VPart.CHEST, Facing.NORTH);
			c.set(x0 + 2, y, z1 - 1, VPart.CHEST, Facing.NORTH);
			c.set(mid, y, (z0 + z1) / 2, VPart.CARTOGRAPHY);
			c.set(mid + 2, y, (z0 + z1) / 2, VPart.LECTERN, Facing.WEST);
			lamp(c, x1 - 1, y, z1 - 1);
		}
		// escadaria na frente
		for (int x = mid - 1; x <= mid + 1; x++) {
			c.set(x, 0, z1 + 1, VPart.PAVING);
		}
		if (level >= 3) {
			// portico: colunas de pedra e laje sobre a entrada
			for (int x = mid - 2; x <= mid + 2; x++) {
				for (int z = z1 + 1; z <= z1 + 2; z++) {
					c.set(x, 0, z, VPart.PAVING_ALT);
					if (!tower) {
						c.set(x, 4, z, VPart.STONE_SLAB);
					}
				}
			}
			if (!tower) {
				for (int x : new int[] {mid - 2, mid + 2}) {
					for (int y = 1; y <= 3; y++) {
						c.set(x, y, z1 + 2, VPart.STONE_WALL);
					}
					c.set(x, 1, z1 + 2, VPart.BRICK);
					c.set(x, 4, z1 + 2, VPart.BRICK);
				}
			}
		}
		if (tower) {
			clockTower(c, mid, z1, top + 7);
		} else {
			cupola(c, mid, (z0 + z1) / 2);
		}
		// canteiros e postes de lanterna dos lados da escadaria
		for (int x : new int[] {mid - 3, mid + 3}) {
			if (x >= 0 && x < c.w()) {
				c.set(x, 1, c.d() - 2, VPart.FENCE);
				c.set(x, 2, c.d() - 2, VPart.FENCE);
				c.set(x, 3, c.d() - 2, VPart.LANTERN);
			}
		}
		for (int z = z1 + (tower ? 3 : 2); z < c.d(); z++) {
			c.set(mid, 0, z, VPart.PAVING);
		}
		Buildings.yard(c);
		c.doorX(mid);
		return c;
	}

	/**
	 * Torre do relogio 5x3 no meio da fachada ({@code z1} = parede da frente), avancando 2 para a rua: vestibulo com
	 * passagem em arco ate a porta, fuste de pedra com cunhais de destaque, mostrador de vidro, campanario aberto com
	 * lanterna e telhado de quatro aguas com pinaculo.
	 */
	static void clockTower(Canvas c, int mid, int z1, int height) {
		int tz0 = z1;
		int tz1 = z1 + 2;
		int zc = z1 + 1;
		for (int x = mid - 2; x <= mid + 2; x++) {
			for (int z = tz0; z <= tz1; z++) {
				boolean ring = Math.abs(x - mid) == 2 || z == tz0 || z == tz1;
				boolean corner = Math.abs(x - mid) == 2 && (z == tz0 || z == tz1);
				boolean face = (x == mid && z != zc) || (z == zc && Math.abs(x - mid) == 2);
				c.set(x, 0, z, VPart.PAVING);
				for (int y = 1; y <= height; y++) {
					boolean vestibule = y <= 3 && Math.abs(x - mid) <= 1 && z != tz0;
					if (vestibule || (x == mid && z == tz1 && y <= 3)) {
						c.remove(x, y, z);
						continue;
					}
					if (x == mid && z == tz0 && y <= 2) {
						continue;
					}
					if (face && y >= height - 2 && y < height) {
						c.remove(x, y, z);
						continue;
					}
					if (ring || y == 4 || y == height) {
						c.set(x, y, z, corner || y % 4 == 0 || y == height ? VPart.BRICK : VPart.STONE);
					} else {
						c.remove(x, y, z);
					}
				}
			}
		}
		// arco da passagem e mostrador do relogio
		c.set(mid - 1, 3, tz1, VPart.STONE_STAIRS_TOP, Facing.EAST);
		c.set(mid + 1, 3, tz1, VPart.STONE_STAIRS_TOP, Facing.WEST);
		int clock = height - 4;
		// seteiras nas tres faces ate o mostrador
		for (int y = 6; y < clock - 1; y += 4) {
			c.set(mid, y, tz1, VPart.WINDOW, Facing.EAST);
			c.set(mid - 2, y, zc, VPart.WINDOW, Facing.SOUTH);
			c.set(mid + 2, y, zc, VPart.WINDOW, Facing.SOUTH);
		}
		c.set(mid, clock, tz1, VPart.WINDOW, Facing.EAST);
		c.set(mid - 1, clock, tz1, VPart.BRICK);
		c.set(mid + 1, clock, tz1, VPart.BRICK);
		c.set(mid, clock + 1, tz1, VPart.BRICK);
		c.set(mid, clock - 1, tz1, VPart.BRICK);
		c.set(mid, height - 1, zc, VPart.LANTERN_HANGING);
		// telhado de quatro aguas e pinaculo
		for (int x = mid - 2; x <= mid + 2; x++) {
			for (int z = tz0; z <= tz1; z++) {
				boolean edge = Math.abs(x - mid) == 2 || z != zc;
				if (edge) {
					Facing in = z == tz0 ? Facing.SOUTH : z == tz1 ? Facing.NORTH : x < mid ? Facing.EAST : Facing.WEST;
					c.set(x, height + 1, z, VPart.ROOF, in);
				} else {
					c.set(x, height + 1, z, VPart.ROOF_BLOCK);
				}
			}
		}
		c.set(mid - 1, height + 2, zc, VPart.ROOF, Facing.EAST);
		c.set(mid + 1, height + 2, zc, VPart.ROOF, Facing.WEST);
		c.set(mid, height + 2, zc, VPart.ROOF_BLOCK);
		c.set(mid, height + 3, zc, VPart.FENCE);
		c.set(mid, height + 4, zc, VPart.FENCE);
		Buildings.door(c, mid, 1, z1, Facing.SOUTH);
	}

	/** Lanternim na cumeeira (3x3 em pilares de cerca com lanterna pendurada), apoiado no telhado. */
	static void cupola(Canvas c, int mid, int zc) {
		int base = 0;
		for (int x = mid - 1; x <= mid + 1; x++) {
			for (int z = zc - 1; z <= zc + 1; z++) {
				base = Math.max(base, Buildings.roofTop(c, x, z) + 1);
			}
		}
		for (int x = mid - 1; x <= mid + 1; x++) {
			for (int z = zc - 1; z <= zc + 1; z++) {
				for (int y = Buildings.roofTop(c, x, z) + 1; y <= base; y++) {
					c.set(x, y, z, VPart.ROOF_BLOCK);
				}
				boolean corner = x != mid && z != zc;
				if (corner) {
					c.set(x, base + 1, z, VPart.FENCE);
					c.set(x, base + 2, z, VPart.FENCE);
				}
				c.set(x, base + 3, z, VPart.ROOF_SLAB);
			}
		}
		c.set(mid, base + 2, zc, VPart.LANTERN_HANGING);
		c.set(mid, base + 4, zc, VPart.FENCE);
	}
}
