package com.mlin.notas.village;

import java.util.Random;

/**
 * Plantas das construcoes de oficios e servicos (Vilas 2.0, fase 6, logica pura), em coordenadas de {@link Canvas} como
 * {@link Buildings}: pedreira, alfaiataria, curtume, flecharia, encantador, taverna, jardim, igreja e lote vazio. Cada
 * nivel cabe na classe de lote do tipo ({@link LotRules}); o nivel I e a base e os seguintes so acrescentam
 * (ou trocam o corpo por um maior), para a melhoria no lugar reaproveitar o terreno.
 *
 * <p>Referencias de desenho (escritas do zero, nada copiado): pedreira com frente de rocha em terracos, guindaste de
 * viga com lanterna, pilhas de blocos cortados e galpao de apoio (pedreiras medievais e "Medieval Quarry" do Planet
 * Minecraft, mais as pedreiras de pedra da lista "Minecraft Quarry Build Ideas" do Sportskeeda); alfaiataria com teares,
 * toldo listrado e varal de la tingida (lojas de tecelao das cidades medievais Andreth e Gelwenton, Planet Minecraft);
 * curtume com tinas sob cobertura e varais de couro afastado da casa, como os curtumes de Marrakech e Fez e o
 * "Medieval Tannery" do Planet Minecraft; campo de tiro com alvos sobre fardos de feno atras de uma linha de tiro
 * coberta (campos de arquearia dos "Archery Range" do Minecraft Maps); casa do encantador com anel de estantes em volta
 * da mesa, alas de leitura e torres de estudo que crescem a cada nivel (torres de mago do "Wizard Tower" do Planet
 * Minecraft e a sala de encantamento vanilla de 15 estantes); taverna de balcao com barris, lareira, quartos em cima e
 * jardim de mesas (tavernas medievais do Shulkr e do Switchblade Gaming); jardim pequeno com lago, ponte e caramanchao
 * (jardins "Japanese Garden" e "Mini Park" de galerias do Planet Minecraft); igreja de nave alta com contrafortes,
 * vitrais, rosacea, campanario separado e cemiterio cercado (igrejas medievais do Playgama e "Village Church" do
 * Planet Minecraft); lote vazio demarcado por estacas (terrenos a venda das cidades do Cities: Skylines e do
 * MineColonies).
 */
public final class CraftsPlans {
	private CraftsPlans() {
	}

	/** Este modulo desenha o projeto. */
	public static boolean handles(VillageProject project) {
		return switch (project) {
			case QUARRY, TAILOR, TANNERY, FLETCHER, ENCHANTER, TAVERN, GARDEN, CHURCH, EMPTY_LOT -> true;
			default -> false;
		};
	}

	/** Planta do projeto no nivel (I = a base). */
	public static Canvas plan(VillageProject project, VillageStyle style, long seed, int level) {
		return BodyDressing.with(() -> build(project, style, seed, level));
	}

	private static Canvas build(VillageProject project, VillageStyle style, long seed, int level) {
		int lv = Math.max(1, Math.min(BuildingTypes.maxLevel(project.id()), level));
		Random random = new Random(seed ^ 0x6A09E667F3BCC908L);
		return switch (project) {
			case QUARRY -> quarry(style, random, lv);
			case TAILOR -> tailor(style, random, lv);
			case TANNERY -> tannery(style, random, lv);
			case FLETCHER -> fletcher(style, random, lv);
			case ENCHANTER -> enchanter(style, random, lv);
			case TAVERN -> tavern(style, random, lv);
			case GARDEN -> garden(style, random, lv);
			case CHURCH -> church(style, random, lv);
			case EMPTY_LOT -> emptyLot(style, random);
			default -> throw new IllegalArgumentException("projeto fora de CraftsPlans: " + project);
		};
	}

	// ---------------------------------------------------------------- pecas

	static void ground(Canvas c, int x0, int z0, int x1, int z1, VPart part) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, part);
			}
		}
	}

	static void post(Canvas c, int x, int z, int height) {
		for (int y = 1; y <= height; y++) {
			c.set(x, y, z, VPart.FENCE);
		}
	}

	static void column(Canvas c, int x, int z, int height, VPart part) {
		for (int y = 1; y <= height; y++) {
			c.set(x, y, z, part);
		}
	}

	/** Mesa de fora: tampo de laje sobre a terra, com banquinhos de escada em volta. */
	static void table(Canvas c, int x, int z, boolean lantern) {
		c.set(x, 1, z, VPart.WOOD_SLAB_TOP);
		if (lantern) {
			c.set(x, 2, z, VPart.LANTERN);
		}
		c.set(x - 1, 1, z, VPart.WOOD_STAIRS, Facing.WEST);
		c.set(x + 1, 1, z, VPart.WOOD_STAIRS, Facing.EAST);
	}

	/** Cobertura de lajes sobre postes nos quatro cantos do retangulo (postes de {@code height}, laje em height+1). */
	static void canopy(Canvas c, int x0, int z0, int x1, int z1, int height, VPart roof) {
		for (int[] p : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			post(c, p[0], p[1], height);
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, height + 1, z, roof);
			}
		}
	}

	/** Cobertura listrada (toldo de la) sobre postes. */
	static void awning(Canvas c, int x0, int z0, int x1, int z1, int height, boolean alongX) {
		for (int[] p : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			post(c, p[0], p[1], height);
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int stripe = alongX ? x - x0 : z - z0;
				c.set(x, height + 1, z, stripe % 2 == 0 ? VPart.WOOL : VPart.WOOL_ALT);
			}
		}
	}

	/** Caminho ate a porta e quintal; canteiros na frente so onde o chao e terra. */
	static void yard(Canvas c, int doorX, boolean flowers) {
		int z = c.d() - 1;
		if (flowers) {
			for (int x = 1; x < c.w() - 1; x++) {
				VPart below = c.part(x, 0, z);
				if (Math.abs(x - doorX) >= 2 && x % 2 == 0 && (below == null || below == VPart.SOIL)) {
					c.setIfAbsent(x, 1, z, VPart.FLOWER);
				}
			}
		}
		Buildings.yard(c);
		c.set(doorX, 0, z, VPart.PATH);
		c.doorX(doorX);
	}

	// ---------------------------------------------------------------- pedreira

	/**
	 * Pedreira (M 15x13, I-III). Frente de rocha em terracos no fundo a esquerda (cada degrau sobe 1: da para subir a
	 * pe), cabana de pedra do pedreiro a direita com cortador de pedras e o bau que a pedreira enche, pilhas de blocos
	 * cortados na frente. II: guindaste de viga com lanterna sobre a frente e telheiro sobre as pilhas. III: terracos
	 * mais altos, arco de pedra no portao e cortadores ao ar livre.
	 */
	static Canvas quarry(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		ground(c, 0, 0, 14, 12, VPart.GRAVEL);
		int hMax = level >= 3 ? 6 : level == 2 ? 5 : 4;
		for (int z = 0; z <= 8; z++) {
			int h = (8 - z) * hMax / 8;
			for (int x = 1; x <= 6; x++) {
				for (int y = 1; y <= h; y++) {
					c.set(x, y, z, (x * 5 + y * 3 + z) % 7 == 0 ? VPart.STONE_ALT : VPart.ROUGH_STONE);
				}
			}
		}
		// blocos soltos ao pe dos terracos
		c.set(7, 1, 7, VPart.ROUGH_STONE);
		c.set(0, 1, 5, VPart.ROUGH_STONE);
		// cabana
		BuildingPlans.body(c, style, 8, 5, 13, 10, 1, true, true);
		c.set(9, 1, 6, VPart.STONECUTTER, Facing.SOUTH);
		c.set(12, 1, 6, VPart.CHEST, Facing.SOUTH);
		BuildingPlans.lamp(c, 9, 1, 9);
		c.set(12, 1, 9, VPart.BARREL);
		c.set(11, 1, 6, VPart.BARREL);
		// pilhas de blocos cortados
		for (int x = 1; x <= 2; x++) {
			c.set(x, 1, 10, VPart.STONE);
			c.set(x, 2, 10, VPart.STONE);
		}
		c.set(3, 1, 10, VPart.BRICK);
		c.set(1, 1, 11, VPart.STONE_SLAB);
		c.set(2, 1, 11, VPart.STONE_SLAB);
		c.set(3, 1, 11, VPart.STONE_ALT);
		c.set(5, 1, 11, VPart.BARREL);
		if (level >= 2) {
			// guindaste: poste alto com braco de viga sobre a frente de rocha e lanterna pendurada
			column(c, 7, 2, 6, VPart.LOG);
			for (int x = 3; x <= 6; x++) {
				c.set(x, 6, 2, VPart.BEAM, Facing.EAST);
			}
			c.set(7, 6, 2, VPart.BEAM, Facing.EAST);
			c.set(4, 5, 2, VPart.LANTERN_HANGING);
			// telheiro sobre as pilhas
			canopy(c, 0, 10, 5, 12, 3, VPart.ROOF_SLAB);
		}
		if (level >= 3) {
			// arco de pedra no portao e cortadores ao ar livre
			for (int x : new int[] {9, 11}) {
				column(c, x, 12, 3, VPart.BRICK);
			}
			for (int x = 9; x <= 11; x++) {
				c.set(x, 4, 12, VPart.BRICK);
			}
			c.set(10, 3, 12, VPart.LANTERN_HANGING);
			c.set(7, 1, 8, VPart.STONECUTTER, Facing.EAST);
			c.set(14, 1, 11, VPart.STONECUTTER, Facing.WEST);
			c.set(14, 1, 12, VPart.STONE);
			c.set(14, 2, 12, VPart.STONE);
		}
		c.spawn(10, 1, 8);
		yard(c, 10, false);
		return c;
	}

	// ---------------------------------------------------------------- alfaiataria

	/**
	 * Alfaiataria (M 15x13, I-II). Casa de enxaimel 11x9 com teares, balcao e tapetes listrados no chao; toldo de la
	 * listrado e varal de la tingida na frente. II: sobrado com loft de mais teares e estandartes de la na fachada.
	 */
	static Canvas tailor(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		BuildingPlans.body(c, style, 2, 2, 12, 10, level >= 2 ? 2 : 1, true, false);
		// tapetes listrados no chao
		for (int x = 3; x <= 11; x++) {
			if (x == 7) {
				continue;
			}
			for (int z = 3; z <= 9; z++) {
				c.set(x, 1, z, x % 4 == 3 ? VPart.CARPET_ACCENT : x % 4 == 1 ? VPart.CARPET : VPart.CARPET_ALT);
			}
		}
		for (int x : new int[] {3, 5, 9}) {
			if (x <= 5 || level >= 2) {
				c.set(x, 1, 3, VPart.LOOM, Facing.SOUTH);
			}
		}
		// fardos de la
		for (int z = 6; z <= 9; z++) {
			c.set(11, 1, z, z % 2 == 0 ? VPart.WOOL : VPart.WOOL_ACCENT);
		}
		c.set(11, 2, 7, VPart.WOOL_ALT);
		c.set(11, 2, 8, VPart.WOOL_ACCENT);
		// balcao
		for (int x = 3; x <= 5; x++) {
			c.set(x, 1, 8, VPart.WOOD_SLAB_TOP);
		}
		c.set(3, 2, 8, VPart.LANTERN);
		// manequim
		c.set(9, 1, 8, VPart.FENCE);
		c.set(9, 2, 8, VPart.PUMPKIN, Facing.SOUTH);
		// toldo e balcao de fora
		awning(c, 2, 11, 6, 12, 2, true);
		for (int x = 3; x <= 5; x++) {
			c.set(x, 1, 11, VPart.WOOD_SLAB_TOP);
		}
		c.set(4, 2, 11, VPart.FLOWER_POT);
		c.set(5, 1, 12, VPart.BARREL);
		// varal de la tingida
		post(c, 10, 12, 2);
		post(c, 13, 12, 2);
		c.set(11, 2, 12, VPart.FENCE);
		c.set(12, 2, 12, VPart.FENCE);
		c.set(11, 1, 12, VPart.WOOL_ACCENT);
		c.set(12, 1, 12, VPart.WOOL);
		if (level >= 2) {
			// loft: mais teares, fardos e bau; estandartes de la na fachada
			c.set(3, 6, 3, VPart.LOOM, Facing.SOUTH);
			c.set(5, 6, 3, VPart.LOOM, Facing.SOUTH);
			c.set(11, 6, 4, VPart.CHEST, Facing.WEST);
			c.set(11, 6, 8, VPart.WOOL_ACCENT);
			c.set(11, 6, 9, VPart.WOOL);
			BuildingPlans.lamp(c, 9, 6, 9);
			for (int x : new int[] {3, 11}) {
				for (int y = 6; y <= 8; y++) {
					c.set(x, y, 11, y == 8 ? VPart.WOOL_ALT : VPart.WOOL_ACCENT);
				}
			}
		}
		c.spawn(7, 1, 7);
		yard(c, 7, true);
		return c;
	}

	// ---------------------------------------------------------------- curtume

	/**
	 * Curtume (M 15x13, I-II). Cabana de pedra do curtidor (caldeirao, barris, bau) a esquerda e, ao lado, o patio de
	 * tinas sobre piso de pedra com varal de couro. II: segunda tina, cobertura de lajes sobre as tinas, segundo varal e
	 * fogueira de fervura.
	 */
	static Canvas tannery(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		ground(c, 0, 0, 14, 12, VPart.PATH);
		ground(c, 9, 2, 14, 8, VPart.PAVING);
		BuildingPlans.body(c, style, 2, 3, 8, 9, 1, true, true);
		c.set(3, 1, 4, VPart.CAULDRON);
		c.set(7, 1, 4, VPart.BARREL);
		c.set(7, 1, 5, VPart.BARREL);
		c.set(3, 1, 8, VPart.CHEST, Facing.EAST);
		BuildingPlans.lamp(c, 7, 1, 8);
		// patio das tinas
		c.set(10, 1, 5, VPart.CAULDRON);
		c.set(11, 1, 5, VPart.TANNING_VAT);
		c.set(12, 1, 5, level >= 2 ? VPart.TANNING_VAT : VPart.BARREL);
		c.set(13, 1, 5, VPart.BARREL);
		c.set(13, 2, 5, VPart.LANTERN);
		c.set(10, 1, 6, VPart.CAULDRON);
		c.set(12, 1, 6, VPart.BARREL);
		// varal de couro: postes, trave e couro estendido por cima
		rack(c, 10, 13, 1);
		if (level >= 2) {
			rack(c, 10, 13, 10);
			canopy(c, 10, 3, 13, 7, 3, VPart.ROOF_SLAB);
			// fogueira de fervura atras da cabana
			c.set(1, 1, 10, VPart.STONE_WALL);
			c.set(1, 1, 11, VPart.CAULDRON);
			c.set(0, 1, 11, VPart.STONE_WALL);
			c.set(2, 1, 11, VPart.STONE_WALL);
			c.set(1, 1, 12, VPart.STONE_WALL);
		}
		c.set(10, 1, 9, VPart.HAY);
		c.set(5, 1, 11, VPart.BARREL);
		c.spawn(5, 1, 7);
		yard(c, 5, false);
		return c;
	}

	private static void rack(Canvas c, int x0, int x1, int z) {
		post(c, x0, z, 2);
		post(c, x1, z, 2);
		for (int x = x0 + 1; x < x1; x++) {
			c.set(x, 2, z, VPart.FENCE);
		}
		for (int x = x0; x <= x1; x++) {
			c.set(x, 3, z, VPart.CARPET);
		}
	}

	// ---------------------------------------------------------------- flecharia

	/**
	 * Flecharia (M 15x13, I-III). Cabana 7x7 do flecheiro (mesa de flecharia, bancada, bau) a esquerda e, a direita, o
	 * campo de tiro: linha de tiro com cerca e alvos sobre fardos de feno no fundo. II: sobrado (deposito no andar de
	 * cima), mais dois alvos e cobertura sobre a linha de tiro. III: cinco alvos com muro de contencao e lanternas.
	 */
	static Canvas fletcher(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		ground(c, 9, 0, 13, 9, VPart.PATH);
		BuildingPlans.body(c, style, 1, 3, 7, 9, level >= 2 ? 2 : 1, true, false);
		c.set(2, 1, 4, VPart.FLETCHING);
		c.set(3, 1, 4, VPart.CRAFTING);
		c.set(6, 1, 5, VPart.CHEST, Facing.WEST);
		BuildingPlans.lamp(c, 6, 1, 8);
		c.set(2, 1, 8, VPart.FENCE);
		c.set(3, 1, 8, VPart.FENCE);
		c.set(2, 2, 8, VPart.HAY);
		if (level >= 2) {
			c.set(2, 6, 4, VPart.BARREL);
			c.set(3, 6, 4, VPart.BARREL);
			c.set(6, 6, 5, VPart.HAY);
			c.set(6, 6, 6, VPart.HAY);
		}
		// campo de tiro: alvos no fundo (z=1) sobre fardos
		int[] targets = level >= 3 ? new int[] {9, 10, 11, 12, 13} : level == 2 ? new int[] {9, 10, 12, 13} : new int[] {10, 12};
		for (int x : targets) {
			c.set(x, 1, 1, VPart.HAY);
			c.set(x, 2, 1, VPart.TARGET);
		}
		if (level >= 3) {
			for (int x = 9; x <= 13; x++) {
				c.set(x, 1, 0, VPart.BRICK);
				c.set(x, 2, 0, VPart.BRICK);
			}
			c.set(11, 3, 0, VPart.LANTERN);
		}
		// linha de tiro
		for (int x = 9; x <= 13; x++) {
			if (x != 11) {
				c.set(x, 1, 8, VPart.FENCE);
			}
		}
		ground(c, 10, 6, 12, 7, VPart.PLANKS);
		if (level >= 2) {
			for (int[] p : new int[][] {{9, 6}, {13, 6}, {9, 8}, {13, 8}}) {
				post(c, p[0], p[1], 3);
			}
			c.set(9, 1, 8, VPart.FENCE);
			c.set(13, 1, 8, VPart.FENCE);
			for (int x = 9; x <= 13; x++) {
				for (int z = 6; z <= 8; z++) {
					c.set(x, 4, z, VPart.ROOF_SLAB);
				}
			}
			c.set(11, 3, 7, VPart.LANTERN_HANGING);
		}
		if (level >= 3) {
			c.set(10, 3, 7, VPart.LANTERN_HANGING);
			c.set(12, 3, 7, VPart.LANTERN_HANGING);
			c.set(8, 1, 10, VPart.WOOD_STAIRS, Facing.EAST);
			c.set(8, 1, 11, VPart.BARREL);
			c.set(8, 2, 11, VPart.LANTERN);
		}
		c.spawn(4, 1, 7);
		yard(c, 4, true);
		return c;
	}

	// ---------------------------------------------------------------- encantador

	/**
	 * Casa do Encantador (L 19x13, I-V). Salao de pedra 11x9 com a mesa de encantamento no centro do anel de estantes
	 * (parcial no I, completo no II, de duas alturas no III), atril do bibliotecario, bau de lapis e tapete. II: pavilhoes
	 * de alquimia dos lados. III: andar de cima (galeria de estantes). IV: torre de estudo com cupula de vidro.
	 * V: segunda torre e postes de lanterna na frente.
	 */
	static Canvas enchanter(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(19, 13);
		ground(c, 4, 11, 14, 12, VPart.PAVING);
		BuildingPlans.body(c, style, 4, 2, 14, 10, level >= 3 ? 2 : 1, true, true);
		// tapete roxo no salao
		for (int x = 5; x <= 13; x++) {
			for (int z = 3; z <= 9; z++) {
				c.set(x, 1, z, (x + z) % 2 == 0 ? VPart.CARPET_ACCENT : VPart.CARPET_ALT);
			}
		}
		// mesa e anel de estantes (distancia 2; o lado da porta fica aberto no meio)
		int tx = 9;
		int tz = 5;
		c.set(tx, 1, tz, VPart.ENCHANTING_TABLE);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) != 2 && Math.abs(dz) != 2) {
					continue;
				}
				if (dx == 0 && dz == 2) {
					c.remove(tx, 1, tz + 2);
					continue;
				}
				boolean back = dz <= 0;
				if (level == 1 && !back) {
					c.remove(tx + dx, 1, tz + dz);
					continue;
				}
				c.set(tx + dx, 1, tz + dz, VPart.BOOKSHELF);
				if (level >= 3 || (level == 2 && back)) {
					c.set(tx + dx, 2, tz + dz, VPart.BOOKSHELF);
				}
			}
		}
		c.set(5, 1, 4, VPart.LECTERN, Facing.EAST);
		c.set(13, 1, 4, VPart.CHEST, Facing.WEST);
		c.set(5, 1, 8, VPart.BOOKSHELF);
		c.set(5, 2, 8, VPart.BOOKSHELF);
		c.set(13, 1, 8, VPart.BOOKSHELF);
		c.set(13, 2, 8, VPart.BOOKSHELF);
		BuildingPlans.lamp(c, 5, 1, 9);
		BuildingPlans.lamp(c, 13, 1, 9);
		if (level >= 2) {
			// pavilhoes de alquimia: cobertura de lajes, caldeirao e suporte de pocoes
			canopy(c, 0, 4, 2, 7, 3, VPart.ROOF_SLAB);
			c.set(1, 1, 5, VPart.BREWING);
			c.set(1, 1, 6, VPart.CAULDRON);
			c.set(0, 1, 5, VPart.BARREL);
			canopy(c, 16, 4, 18, 7, 3, VPart.ROOF_SLAB);
			c.set(17, 1, 5, VPart.BREWING);
			c.set(17, 1, 6, VPart.CAULDRON);
			c.set(18, 1, 5, VPart.BARREL);
			c.set(1, 3, 4, VPart.LANTERN_HANGING);
			c.set(17, 3, 4, VPart.LANTERN_HANGING);
		}
		if (level >= 3) {
			// andar de cima: galeria de estantes junto da parede do fundo
			for (int x = 5; x <= 12; x++) {
				c.set(x, 6, 3, VPart.BOOKSHELF);
				c.set(x, 7, 3, VPart.BOOKSHELF);
			}
			BuildingPlans.lamp(c, 9, 6, 9);
			c.set(13, 6, 6, VPart.LECTERN, Facing.WEST);
		}
		if (level >= 4) {
			tower(c, 16, 1);
		}
		if (level >= 5) {
			tower(c, 0, 1);
			for (int x : new int[] {3, 15}) {
				post(c, x, 12, 3);
				c.set(x, 4, 12, VPart.LANTERN);
			}
		}
		c.spawn(9, 1, 8);
		yard(c, 9, false);
		return c;
	}

	/** Torre de estudo 3x3 em (x0, z0): pedra com cintas de viga, janelas estreitas, lanterna de vidro e cupula de escadas. */
	private static void tower(Canvas c, int x0, int z0) {
		int x1 = x0 + 2;
		int z1 = z0 + 2;
		int cx = x0 + 1;
		int cz = z0 + 1;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, VPart.FOUNDATION);
			}
		}
		for (int y = 1; y <= 10; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (x == cx && z == cz) {
						continue;
					}
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					boolean belt = y == 4 || y == 8;
					Facing axis = z == z0 || z == z1 ? Facing.EAST : Facing.SOUTH;
					if (y >= 9) {
						c.set(x, y, z, corner ? VPart.BRICK : VPart.WINDOW, axis);
					} else {
						c.set(x, y, z, corner ? VPart.BRICK : belt ? VPart.BEAM : VPart.STONE, belt ? axis : null);
					}
				}
			}
		}
		// janelas estreitas
		for (int y : new int[] {2, 6}) {
			c.set(cx, y, z1, VPart.WINDOW, Facing.EAST);
			c.set(x1, y, cz, VPart.WINDOW, Facing.SOUTH);
		}
		// o miolo e oco, com uma escada de mao encostada na parede de tras
		for (int y = 1; y <= 9; y++) {
			c.set(cx, y, cz, VPart.LADDER, Facing.SOUTH);
		}
		// cupula: anel de escadas virado para dentro, agulha e lanterna
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x == cx && z == cz) {
					c.set(x, 11, z, VPart.BRICK);
				} else {
					Facing f = x == x0 && z != z0 && z != z1 ? Facing.EAST : x == x1 && z != z0 && z != z1 ? Facing.WEST
							: z == z0 ? Facing.SOUTH : Facing.NORTH;
					c.set(x, 11, z, VPart.STONE_STAIRS, f);
				}
			}
		}
		c.set(cx, 12, cz, VPart.BRICK);
		c.set(cx, 13, cz, VPart.LANTERN);
	}

	// ---------------------------------------------------------------- taverna

	/**
	 * Taverna (M 15x13, I-III). Salao de enxaimel 13x9 com balcao e barris, banquinhos, mesas, lareira e mural de
	 * avisos; lanternas em postes na frente. II: sobrado com quartos de hospedes (camas e baus) em cima. III: jardim de
	 * mesas na frente e pilha de barris de cerveja.
	 */
	static Canvas tavern(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(15, 13);
		BuildingPlans.body(c, style, 1, 2, 13, 10, level >= 2 ? 2 : 1, true, false);
		// balcao com barris atras e banquinhos de costas para o salao
		for (int x = 2; x <= 6; x++) {
			c.set(x, 1, 5, VPart.WOOD_SLAB_TOP);
			c.set(x, 1, 3, VPart.BARREL);
		}
		c.set(2, 2, 3, VPart.LANTERN);
		c.set(5, 2, 5, VPart.FLOWER_POT);
		for (int x = 2; x <= 5; x++) {
			c.set(x, 1, 6, VPart.WOOD_STAIRS, Facing.SOUTH);
		}
		// lareira
		c.set(12, 1, 5, VPart.BRICK);
		c.set(12, 1, 7, VPart.BRICK);
		c.set(12, 1, 6, VPart.CAMPFIRE);
		for (int z = 5; z <= 7; z++) {
			c.set(12, 2, z, VPart.BRICK);
		}
		// mesas do salao
		table(c, 9, 5, true);
		table(c, 9, 8, false);
		table(c, 4, 8, false);
		c.set(11, 1, 9, VPart.CARTOGRAPHY);
		c.set(10, 1, 9, VPart.BARREL);
		c.set(2, 1, 9, VPart.CHEST, Facing.EAST);
		// lanternas nos postes da frente
		for (int x : new int[] {3, 11}) {
			post(c, x, 11, 2);
			c.set(x, 3, 11, VPart.LANTERN);
		}
		if (level >= 2) {
			Buildings.bed(c, 2, 6, 5, Facing.EAST);
			Buildings.bed(c, 2, 6, 8, Facing.EAST);
			Buildings.bed(c, 11, 6, 5, Facing.WEST);
			c.set(6, 6, 3, VPart.CHEST, Facing.SOUTH);
			c.set(8, 6, 3, VPart.CHEST, Facing.SOUTH);
			BuildingPlans.lamp(c, 7, 6, 9);
			for (int x = 4; x <= 10; x++) {
				c.set(x, 6, 6, VPart.CARPET_ACCENT);
			}
		}
		if (level >= 3) {
			// jardim de mesas na frente e pilha de barris de cerveja
			table(c, 2, 12, true);
			table(c, 12, 12, true);
			c.set(14, 1, 3, VPart.BARREL);
			c.set(14, 2, 3, VPart.BARREL);
			c.set(14, 1, 4, VPart.BARREL);
			c.set(0, 1, 3, VPart.BARREL);
			c.set(0, 2, 3, VPart.BARREL);
		}
		c.spawn(7, 1, 7);
		yard(c, 7, true);
		return c;
	}

	// ---------------------------------------------------------------- jardim

	/**
	 * Jardim (T 7x7, I-III). Lago de 3x3 cercado de flores da estacao e bancos. II: ponte de tabuas sobre o lago com
	 * postes de lanterna. III: caramanchao de entrada com trepadeira de folhas e lanternas.
	 */
	static Canvas garden(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(7, 7);
		ground(c, 0, 0, 6, 6, VPart.SOIL);
		ground(c, 2, 2, 4, 4, VPart.WATER);
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				boolean ring = x == 1 || x == 5 || z == 1 || z == 5;
				if (ring && !(x == 3 && z == 5)) {
					c.set(x, 1, z, VPart.FLOWER);
				}
			}
		}
		c.set(0, 1, 2, VPart.WOOD_STAIRS, Facing.WEST);
		c.set(0, 1, 4, VPart.WOOD_STAIRS, Facing.WEST);
		c.set(6, 1, 2, VPart.WOOD_STAIRS, Facing.EAST);
		c.set(6, 1, 4, VPart.WOOD_STAIRS, Facing.EAST);
		if (level >= 2) {
			for (int x = 1; x <= 5; x++) {
				c.set(x, 0, 3, VPart.PLANKS);
				c.remove(x, 1, 3);
			}
			for (int x : new int[] {0, 6}) {
				c.set(x, 0, 3, VPart.PLANKS);
				post(c, x, 3, 1);
				c.set(x, 2, 3, VPart.LANTERN);
			}
		}
		if (level >= 3) {
			for (int x : new int[] {2, 4}) {
				post(c, x, 6, 3);
			}
			for (int x = 2; x <= 4; x++) {
				c.set(x, 4, 6, VPart.HEDGE);
			}
			c.set(3, 3, 6, VPart.LANTERN_HANGING);
		}
		c.spawn(3, 1, 5);
		c.doorX(3);
		c.set(3, 0, 6, VPart.PATH);
		c.set(3, 0, 5, VPart.PATH);
		return c;
	}

	// ---------------------------------------------------------------- igreja

	/**
	 * Igreja (L 19x13, I-III). Nave alta de pedra 9x11 com contrafortes, janelas altas, rosacea, altar de cerimonia em
	 * estrado (o altar abencoa), bancos e tapete; campanario de pedra separado a direita com o sino. II: segunda fileira
	 * de bancos, candelabros e campanario mais alto. III: cemiterio cercado a esquerda com lapides e arvores, e portico
	 * na frente.
	 */
	static Canvas church(VillageStyle style, Random random, int level) {
		Canvas c = new Canvas(19, 13);
		int x0 = 6;
		int x1 = 14;
		int z0 = 1;
		int z1 = 11;
		int mid = (x0 + x1) / 2;
		ground(c, 5, 12, 15, 12, VPart.PAVING);
		Buildings.floor(c, x0, z0, x1, z1);
		Buildings.walls(c, style, x0, z0, x1, z1, 1, 6, true, false);
		// janelas altas (tres vidracas empilhadas) e contrafortes entre elas
		for (int z = z0 + 2; z < z1 - 1; z += 3) {
			for (int y = 2; y <= 4; y++) {
				c.set(x0, y, z, VPart.WINDOW, Facing.SOUTH);
				c.set(x1, y, z, VPart.WINDOW, Facing.SOUTH);
			}
			for (int y = 1; y <= 5; y++) {
				c.set(x0 - 1, y, z + 1, VPart.BRICK);
				c.set(x1 + 1, y, z + 1, VPart.BRICK);
			}
		}
		// rosacea no fundo e porta na frente
		for (int y = 3; y <= 5; y++) {
			for (int x = mid - 1; x <= mid + 1; x++) {
				if (y == 4 || x == mid) {
					c.set(x, y, z0, VPart.WINDOW, Facing.EAST);
				}
			}
		}
		for (int y = 1; y <= 3; y++) {
			c.set(mid - 1, y, z1, VPart.BRICK);
			c.set(mid + 1, y, z1, VPart.BRICK);
		}
		Buildings.door(c, mid, 1, z1, Facing.SOUTH);
		c.set(mid, 3, z1, VPart.BRICK);
		Buildings.roof(c, style, x0, z0, x1, z1, 6, false);
		// estrado do altar: degraus (sobem para o fundo), plataforma de pedra, altar e velas
		for (int x = mid - 2; x <= mid + 2; x++) {
			c.set(x, 1, 3, VPart.STONE_STAIRS, Facing.NORTH);
			c.set(x, 1, 2, VPart.BRICK);
		}
		c.set(mid, 2, 2, VPart.BREWING);
		c.set(mid - 1, 2, 2, VPart.LANTERN);
		c.set(mid + 1, 2, 2, VPart.LANTERN);
		c.set(mid - 2, 2, 2, VPart.FLOWER_POT);
		c.set(mid + 2, 2, 2, VPart.FLOWER_POT);
		// tapete e bancos (o encosto fica para a porta: o fiel olha o altar)
		for (int z = 4; z <= 10; z++) {
			c.set(mid, 1, z, VPart.CARPET_ACCENT);
		}
		int[] benchZ = level >= 2 ? new int[] {5, 6, 8, 9} : new int[] {5, 8};
		for (int bz : benchZ) {
			for (int x : new int[] {mid - 3, mid - 2, mid + 2, mid + 3}) {
				c.set(x, 1, bz, VPart.WOOD_STAIRS, Facing.SOUTH);
			}
		}
		for (int x : new int[] {x0 + 1, x1 - 1}) {
			BuildingPlans.lamp(c, x, 1, 3);
		}
		if (level >= 2) {
			for (int x : new int[] {mid - 1, mid + 1}) {
				post(c, x, 7, 2);
				c.set(x, 3, 7, VPart.LANTERN);
			}
		}
		belltower(c, 16, 6, level >= 2 ? 12 : 10);
		for (int x = 15; x <= 16; x++) {
			c.set(x, 0, 11, VPart.PAVING);
		}
		if (level >= 3) {
			// cemiterio cercado, com portao para a rua e para o adro
			ground(c, 0, 2, 4, 11, VPart.SOIL);
			for (int z = 2; z <= 11; z++) {
				c.set(0, 1, z, VPart.FENCE);
				c.set(4, 1, z, VPart.FENCE);
			}
			for (int x = 0; x <= 4; x++) {
				c.set(x, 1, 2, VPart.FENCE);
				c.set(x, 1, 11, VPart.FENCE);
			}
			c.set(4, 1, 6, VPart.FENCE_GATE, Facing.EAST);
			c.set(2, 1, 11, VPart.FENCE_GATE, Facing.SOUTH);
			for (int[] g : new int[][] {{1, 4}, {3, 4}, {1, 8}, {3, 8}}) {
				c.set(g[0], 1, g[1], VPart.TOMBSTONE);
				c.set(g[0], 1, g[1] + 1, VPart.FLOWER);
			}
			c.set(2, 1, 5, VPart.HEDGE);
			c.set(2, 2, 5, VPart.HEDGE);
			c.set(2, 1, 8, VPart.PLANT);
			// portico na frente
			for (int x : new int[] {mid - 2, mid + 2}) {
				column(c, x, 12, 4, VPart.BRICK);
			}
			for (int x = mid - 2; x <= mid + 2; x++) {
				c.set(x, 5, 12, VPart.STONE_SLAB);
			}
			c.set(mid, 4, 12, VPart.LANTERN_HANGING);
		}
		c.spawn(mid, 1, 8);
		yard(c, mid, false);
		return c;
	}

	/** Campanario de pedra 3x3: paredes cheias ate o piso do sino, quatro pilares abertos, sino e telhado de escadas. */
	private static void belltower(Canvas c, int x0, int z0, int height) {
		int x1 = x0 + 2;
		int z1 = z0 + 2;
		int floorY = height - 4;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, 0, z, VPart.FOUNDATION);
			}
		}
		for (int y = 1; y <= floorY; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					if (edge) {
						c.set(x, y, z, corner ? VPart.BRICK : VPart.STONE);
					}
				}
			}
		}
		c.set(x0 + 1, 3, z1, VPart.WINDOW, Facing.EAST);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, floorY, z, VPart.BRICK);
			}
		}
		// pedestal e sino no meio, pilares nos cantos
		c.set(x0 + 1, floorY + 1, z0 + 1, VPart.BRICK);
		c.set(x0 + 1, floorY + 2, z0 + 1, VPart.BELL_FLOOR, Facing.EAST);
		for (int[] p : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			for (int y = floorY + 1; y <= floorY + 3; y++) {
				c.set(p[0], y, p[1], VPart.BRICK);
			}
		}
		// telhado dentro da planta: anel de escadas virado para dentro sobre os pilares, agulha e lanterna
		int top = floorY + 4;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x == x0 + 1 && z == z0 + 1) {
					c.set(x, top, z, VPart.BRICK);
				} else {
					Facing f = z == z0 ? Facing.SOUTH : z == z1 ? Facing.NORTH : x == x0 ? Facing.EAST : Facing.WEST;
					c.set(x, top, z, VPart.STONE_STAIRS, f);
				}
			}
		}
		c.set(x0 + 1, top + 1, z0 + 1, VPart.BRICK);
		c.set(x0 + 1, top + 2, z0 + 1, VPart.LANTERN);
	}

	// ---------------------------------------------------------------- lote vazio

	/**
	 * Lote vazio (S 11x11): patamar plano de terra com estacas nos cantos e nos meios, e um caminho ate o meio. A vila
	 * nunca mexe no que o jogador construir aqui; com cama e porta dentro conta como casa.
	 */
	static Canvas emptyLot(VillageStyle style, Random random) {
		Canvas c = new Canvas(11, 11);
		ground(c, 0, 0, 10, 10, VPart.SOIL);
		for (int[] p : new int[][] {{0, 0}, {10, 0}, {0, 10}, {10, 10}, {5, 0}, {0, 5}, {10, 5}}) {
			post(c, p[0], p[1], 2);
			c.set(p[0], 3, p[1], VPart.LANTERN);
		}
		for (int z = 6; z <= 10; z++) {
			c.set(5, 0, z, VPart.PATH);
		}
		c.doorX(5);
		c.spawn(5, 1, 5);
		return c;
	}
}
