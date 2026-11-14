package com.mlin.notas.village;

import java.util.Random;

/**
 * Quartel e Guilda dos Exploradores com identidade propria (rodada de acabamento das Vilas 2.0), em coordenadas de
 * {@link Canvas}. Mesmos lotes (M, 15x13), mesmos pontos marcados ({@code guard}, {@code bell}, {@code board}) e mesmos niveis
 * de {@link CivicPlans}; so o desenho mudou.
 *
 * <p>Quartel: torre de menagem de pedra de telhado plano com ameias, torreoes de canto, seteiras de grade, portao com
 * grade de ferro e vergas de tijolo, estandartes vermelhos e brancos na fachada, patio de parada calcado com postos de
 * guarda, campo de treino com bonecos de feno e alvos, balcao de armas (rack de lanças) e muro de pedra seca. Referencias:
 * quarteis e "keeps" medievais do Planet Minecraft ("Medieval Barracks", "Small Keep") e do Minecraft Maps.
 *
 * <p>Guilda: alojamento de toras de gaveta alta, com varanda coberta de lampioes, sala de mapas (mesas de cartografia, quadro
 * de expedicoes e atril) e, la fora, um globo de blocos sobre pedestal no meio da rosa dos ventos, mastros com bandeiras de
 * expedicao, fogueira com bancos e uma carroca coberta cheia de sacos e barris. Referencias: "guild halls" e
 * "explorer's lodge" do Planet Minecraft, carroças de comerciante e globos de cartografo das galerias de builds.
 * Tudo escrito do zero.
 */
final class CivicHalls {
	private CivicHalls() {
	}

	private static void rect(Canvas c, int x0, int z0, int x1, int z1, int y, VPart part) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, y, z, part);
			}
		}
	}

	private static void lampPost(Canvas c, int x, int z) {
		c.set(x, 1, z, VPart.STONE_WALL);
		c.set(x, 2, z, VPart.STONE_WALL);
		c.set(x, 3, z, VPart.LANTERN);
	}

	// ---------------------------------------------------------------- quartel

	/**
	 * Quartel I: menagem 11x7 de uma laje de pedra com 4 torreoes de canto e ameias, alojamento com beliches, mesa do rancho
	 * e armeiro por dentro, patio de parada e campo de treino. II: 13 de largura e dois andares (arsenal em cima). III: sino
	 * de alarme num belvedere no terraco e mais alvos.
	 */
	static Canvas barracks(VillageStyle style, Random random, int level) {
		int bw = level >= 2 ? 13 : 11;
		int floors = level >= 2 ? 2 : 1;
		Canvas c = new Canvas(15, 13);
		int x0 = (15 - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = 7;
		int mid = (x0 + x1) / 2;
		int deck = 4 * floors;
		Buildings.floor(c, x0, z0, x1, z1);
		// muralha de pedra: cunhais, pilastras a cada 4, fiada de tijolo no pe e faixa embaixo do terraco
		for (int y = 1; y < deck; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean xEdge = z == z0 || z == z1;
					boolean zEdge = x == x0 || x == x1;
					if (!xEdge && !zEdge) {
						continue;
					}
					boolean corner = xEdge && zEdge;
					int along = xEdge ? x - x0 : z - z0;
					VPart part;
					if (corner || y == deck - 1 || y == 1 || along % 4 == 0) {
						part = VPart.BRICK;
					} else {
						part = Math.floorMod(x * 7 + y * 13 + z * 5, 5) == 0 ? VPart.STONE_ALT : VPart.STONE;
					}
					c.set(x, y, z, part);
				}
			}
		}
		// piso de cima (laje de tabuas) com o vao da escada de mao
		if (floors > 1) {
			for (int x = x0 + 1; x < x1; x++) {
				for (int z = z0 + 1; z < z1; z++) {
					if (!(x == x1 - 1 && z == z0 + 1)) {
						c.set(x, 5, z, VPart.FLOOR);
					}
				}
			}
			for (int y = 1; y <= 5; y++) {
				c.set(x1 - 1, y, z0 + 1, VPart.LADDER, Facing.SOUTH);
			}
		}
		// terraco: laje do telhado, ameias alternadas, cantoneiras mais altas
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				c.set(x, deck, z, VPart.ROOF_BLOCK);
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (edge && (x + z) % 2 == 0) {
					c.set(x, deck + 1, z, VPart.STONE);
				}
			}
		}
		// torreoes de canto: pilar 2x2 que sai do canto, com seteira e coroa de ameias
		for (int[] t : new int[][] {{x0, z0, -1, -1}, {x1, z0, 1, -1}, {x0, z1, -1, 1}, {x1, z1, 1, 1}}) {
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					int x = t[0] + dx * t[2];
					int z = t[1] + dz * t[3];
					c.set(x, 0, z, VPart.FOUNDATION);
					boolean outer = dx == 1 && dz == 1;
					for (int y = 1; y <= deck + 2; y++) {
						VPart part = outer || y == 1 || y == deck + 2 ? VPart.BRICK : Math.floorMod(x * 7 + y * 13 + z * 5, 5) == 0 ? VPart.STONE_ALT : VPart.STONE;
						c.set(x, y, z, part);
					}
					if (outer || (dx + dz == 1)) {
						c.set(x, deck + 3, z, (x + z) % 2 == 0 ? VPart.STONE_SLAB : VPart.STONE);
					}
				}
			}
			// seteira de grade na face de fora do pilar
			int ox = t[0] + t[2];
			int oz = t[1] + t[3];
			c.set(ox, 3, oz, VPart.IRON_BARS, Facing.SOUTH);
			if (floors > 1) {
				c.set(ox, 7, oz, VPart.IRON_BARS, Facing.SOUTH);
			}
		}
		// seteiras de grade nas laterais e no fundo, e janela larga na frente
		for (int y : floors > 1 ? new int[] {2, 6} : new int[] {2}) {
			for (int z : new int[] {z0 + 2, z1 - 2}) {
				c.set(x0, y, z, VPart.IRON_BARS, Facing.SOUTH);
				c.set(x1, y, z, VPart.IRON_BARS, Facing.SOUTH);
			}
			for (int x : new int[] {mid - 3, mid + 3}) {
				if (x > x0 + 1 && x < x1 - 1) {
					c.set(x, y, z0, VPart.IRON_BARS, Facing.EAST);
					c.set(x, y, z1, VPart.WINDOW, Facing.EAST);
				}
			}
		}
		// portao: jambas e verga de tijolo, porta de madeira e grade em cima
		Buildings.door(c, mid, 1, z1, Facing.SOUTH);
		for (int y = 1; y <= 3; y++) {
			c.set(mid - 1, y, z1, VPart.BRICK);
			c.set(mid + 1, y, z1, VPart.BRICK);
		}
		c.set(mid, 3, z1, VPart.IRON_BARS, Facing.EAST);
		// estandartes de la nas duas faces da fachada
		for (int x : new int[] {mid - 3, mid + 3}) {
			c.set(x, 2, z1 + 1, VPart.WOOL);
			c.set(x, 3, z1 + 1, VPart.WOOL);
			c.set(x, 4, z1 + 1, VPart.WOOL_ALT);
		}
		if (floors > 1) {
			for (int x : new int[] {mid - 3, mid + 3}) {
				c.set(x, 6, z1 + 1, VPart.WOOL);
				c.set(x, 7, z1 + 1, VPart.WOOL);
				c.set(x, 8, z1 + 1, VPart.WOOL_ALT);
			}
		}
		// por dentro: beliches e baus, rancho, armeiro
		for (int x = x0 + 2; x <= x1 - 3; x += 2) {
			Buildings.bed(c, x, 1, z0 + 2, Facing.NORTH);
			c.set(x, 1, z0 + 3, VPart.CHEST, Facing.NORTH);
		}
		c.set(x0 + 1, 1, z0 + 1, VPart.BARREL);
		c.set(x0 + 1, 2, z0 + 1, VPart.LANTERN);
		for (int x = mid - 1; x <= mid + 1; x++) {
			c.set(x, 1, z0 + 4, VPart.WOOD_SLAB_TOP);
		}
		c.set(mid, 2, z0 + 4, VPart.LANTERN);
		c.set(x0 + 1, 1, z1 - 1, VPart.GRINDSTONE, Facing.EAST);
		c.set(x0 + 2, 1, z1 - 1, VPart.FLETCHING);
		c.set(x1 - 3, 1, z1 - 1, VPart.FURNACE, Facing.NORTH);
		c.set(x1 - 2, 1, z1 - 1, VPart.SMOKER, Facing.NORTH);
		c.set(x1 - 1, 1, z1 - 1, VPart.BARREL);
		if (floors > 1) {
			int y = 6;
			for (int x = x0 + 1; x <= x1 - 3; x += 2) {
				c.set(x, y, z0 + 1, VPart.CHEST, Facing.SOUTH);
				c.set(x, y + 1, z0 + 1, VPart.LANTERN);
			}
			for (int x = x0 + 2; x <= x1 - 3; x++) {
				c.set(x, y, z0 + 4, VPart.WOOD_SLAB_TOP);
			}
			c.set(x0 + 1, y, z1 - 1, VPart.ANVIL, Facing.EAST);
			c.set(x0 + 2, y, z1 - 1, VPart.SMITHING_TABLE);
			c.set(x0 + 3, y, z1 - 1, VPart.GRINDSTONE, Facing.NORTH);
			Buildings.bed(c, x1 - 3, y, z1 - 2, Facing.NORTH);
		}
		if (level >= 3) {
			BuildingPlans.cupola(c, mid, (z0 + z1) / 2);
			c.mark("bell", mid, Buildings.roofTop(c, mid, (z0 + z1) / 2) + 3, (z0 + z1) / 2, null);
		}
		// patio de parada: calcamento em xadrez, rampa de entrada e lampioes nas pontas
		rect(c, x0, z1 + 1, x1, z1 + 2, 0, VPart.PAVING);
		for (int x = x0; x <= x1; x++) {
			for (int z = z1 + 1; z <= z1 + 2; z++) {
				if ((x + z) % 2 == 0) {
					c.set(x, 0, z, VPart.PAVING_ALT);
				}
			}
		}
		int[] gx = {mid - 4, mid - 2, mid, mid + 2, mid + 4, mid + 3};
		for (int i = 0; i < 6; i++) {
			int x = Math.max(x0, Math.min(x1, gx[i]));
			c.mark("guard", x, 1, z1 + 2, null);
		}
		lampPost(c, mid - 2, z1 + 1);
		lampPost(c, mid + 2, z1 + 1);
		// campo de treino: bonecos de feno, alvos, balcao de lancas e muro de pedra seca
		int tz = 11;
		for (int x : new int[] {x0 + 1, x0 + 4, x1 - 1, x1 - 4}) {
			c.set(x, 0, tz, VPart.SOIL);
			c.set(x, 1, tz, VPart.FENCE);
			c.set(x, 2, tz, VPart.HAY);
			c.set(x, 3, tz, VPart.PUMPKIN, Facing.NORTH);
		}
		c.set(mid - 1, 1, tz, VPart.HAY);
		c.set(mid - 1, 2, tz, VPart.TARGET);
		c.set(mid + 1, 1, tz, VPart.HAY);
		c.set(mid + 1, 2, tz, VPart.TARGET);
		if (level >= 3) {
			c.set(mid, 1, tz, VPart.HAY);
			c.set(mid, 2, tz, VPart.TARGET);
		}
		// balcao de armas: dois postes e grade entre eles (as lanças), no lado do patio
		int rz = 9;
		for (int x : new int[] {x0 + 1, x1 - 1}) {
			c.set(x, 1, rz, VPart.FENCE);
			c.set(x, 2, rz, VPart.FENCE);
			c.set(x, 3, rz, VPart.FENCE);
		}
		for (int x = x0 + 2; x <= x0 + 3; x++) {
			c.set(x, 2, rz, VPart.IRON_BARS, Facing.EAST);
		}
		for (int x = x1 - 3; x <= x1 - 2; x++) {
			c.set(x, 2, rz, VPart.IRON_BARS, Facing.EAST);
		}
		c.set(x0 + 2, 1, rz, VPart.GRINDSTONE, Facing.NORTH);
		c.set(x1 - 2, 1, rz, VPart.ANVIL, Facing.EAST);
		for (int x = x0; x <= x1; x++) {
			if (Math.abs(x - mid) > 1) {
				c.set(x, 1, 12, VPart.STONE_WALL);
			}
		}
		BuildingPlans.frontYard(c, mid);
		c.spawn(x0 + 1, 1, z0 + 2);
		return c;
	}

	// ---------------------------------------------------------------- guilda dos exploradores

	/**
	 * Guilda I: alojamento 9x7 de toras com varanda coberta, quadro de expedicoes no fundo da sala de mapas, mesas de
	 * cartografia e atril; la fora o globo sobre a rosa dos ventos, mastros com bandeiras, fogueira com bancos e a carroca
	 * coberta. II: 11 de largura, armeiro. III: deposito maior. IV: andar de cima com sala de trofeus. V: mirante com sino
	 * no telhado. Lote M.
	 */
	static Canvas guild(VillageStyle style, Random random, int level) {
		int bw = level >= 2 ? 11 : 9;
		int floors = level >= 4 ? 2 : 1;
		Canvas c = new Canvas(15, 13);
		int x0 = (15 - bw) / 2;
		int x1 = x0 + bw - 1;
		int z0 = 1;
		int z1 = level >= 3 ? 8 : 7;
		BuildingPlans.body(c, style, x0, z0, x1, z1, floors, false, false);
		int mid = (x0 + x1) / 2;
		// varanda coberta: assoalho, postes de tora, balaustrada e telhadinho de laje com lampioes pendurados
		rect(c, x0, z1 + 1, x1, z1 + 2, 0, VPart.FLOOR);
		for (int x : new int[] {x0, mid - 2, mid + 2, x1}) {
			for (int y = 1; y <= 3; y++) {
				c.set(x, y, z1 + 2, VPart.LOG);
			}
		}
		for (int x = x0 + 1; x < x1; x++) {
			if (Math.abs(x - mid) > 2) {
				c.set(x, 1, z1 + 2, VPart.FENCE);
			}
		}
		for (int x = x0; x <= x1; x++) {
			c.set(x, 4, z1 + 2, VPart.BEAM, Facing.EAST);
			c.setIfAbsent(x, 5, z1 + 2, VPart.ROOF_SLAB);
		}
		for (int x : new int[] {mid - 1, mid + 1}) {
			c.set(x, 3, z1 + 2, VPart.LANTERN_HANGING);
		}
		// bandeira de expedicao pendurada na verga da varanda
		c.set(mid - 2, 3, z1 + 1, VPart.WOOL_ACCENT);
		c.set(mid + 2, 3, z1 + 1, VPart.WOOL_ACCENT);
		// sala de mapas: quadro no meio do fundo, mesas de cartografia e atril
		c.set(mid, 1, z0 + 1, VPart.GUILD_BOARD, Facing.SOUTH);
		c.mark("board", mid, 1, z0 + 1, Facing.SOUTH);
		c.set(mid - 2, 1, z0 + 1, VPart.CARTOGRAPHY);
		c.set(mid + 2, 1, z0 + 1, VPart.LECTERN, Facing.SOUTH);
		c.set(mid - 2, 2, z0 + 1, VPart.LANTERN);
		c.set(mid - 1, 1, z0 + 1, VPart.CARPET_ACCENT);
		c.set(mid + 1, 1, z0 + 1, VPart.CARPET_ACCENT);
		c.set(x0 + 1, 1, z0 + 2, VPart.BARREL);
		c.set(x0 + 1, 1, z0 + 3, VPart.CHEST, Facing.EAST);
		c.set(x0 + 1, 2, z0 + 2, VPart.LANTERN);
		c.set(x1 - 1, 1, z0 + 4, VPart.BARREL);
		c.set(x1 - 1, 1, z0 + 5, VPart.BARREL);
		Buildings.bed(c, x1 - 1, 1, z0 + 3, Facing.NORTH);
		// mesa do conselho com mapa (carpete de destaque) e bancos
		for (int x = mid - 1; x <= mid + 1; x++) {
			c.set(x, 1, z0 + 4, VPart.WOOD_SLAB_TOP);
		}
		c.set(mid, 2, z0 + 4, VPart.LANTERN);
		c.set(mid - 1, 1, z0 + 5, VPart.WOOD_STAIRS, Facing.NORTH);
		c.set(mid + 1, 1, z0 + 5, VPart.WOOD_STAIRS, Facing.NORTH);
		if (level >= 2) {
			c.set(x0 + 1, 1, z1 - 1, VPart.ANVIL, Facing.EAST);
			c.set(x0 + 1, 1, z1 - 2, VPart.GRINDSTONE, Facing.EAST);
			c.set(x0 + 1, 1, z1 - 3, VPart.FLETCHING);
		}
		if (level >= 3) {
			c.set(x1 - 1, 1, z1 - 1, VPart.CHEST, Facing.WEST);
			c.set(x1 - 1, 1, z1 - 2, VPart.CHEST, Facing.WEST);
			c.set(x1 - 1, 2, z1 - 2, VPart.BARREL);
		}
		if (floors > 1) {
			int y = BuildingPlans.floorY(1) + 1;
			for (int x = x0 + 1; x <= x1 - 3; x++) {
				c.set(x, y, z0 + 1, VPart.BOOKSHELF);
			}
			c.set(x0 + 1, y, z0 + 2, VPart.PUMPKIN, Facing.SOUTH);
			c.set(mid, y, z0 + 3, VPart.WOOD_SLAB_TOP);
			c.set(mid, y + 1, z0 + 3, VPart.LANTERN);
			c.set(x0 + 1, y, z1 - 1, VPart.CHEST, Facing.EAST);
			c.set(x1 - 3, y, z1 - 1, VPart.CARTOGRAPHY);
			for (int z = z0 + 4; z <= z1 - 2; z++) {
				c.set(mid, y, z, VPart.CARPET_ALT);
			}
		}
		if (level >= 5) {
			BuildingPlans.cupola(c, mid, (z0 + z1) / 2);
			c.mark("bell", mid, Buildings.roofTop(c, mid, (z0 + z1) / 2) + 3, (z0 + z1) / 2, null);
		}
		// globo sobre a rosa dos ventos, no meio do patio
		int rz = Math.min(c.d() - 2, z1 + 5);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (rz + dz < c.d() && rz + dz > z1 + 2) {
					boolean star = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
					c.set(mid + dx, 0, rz + dz, star ? VPart.PAVING_ALT : VPart.PAVING);
				}
			}
		}
		c.set(mid, 0, rz, VPart.BRICK);
		c.set(mid, 1, rz, VPart.STONE_WALL);
		c.set(mid, 2, rz, VPart.STONE_WALL);
		int gy = 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx * dx + dy * dy + dz * dz <= 2) {
						boolean land = Math.floorMod(dx * 3 + dy * 5 + dz * 7 + 2, 4) == 0;
						c.set(mid + dx, gy + dy, rz + dz, land ? VPart.FOLIAGE : VPart.WOOL_ACCENT);
					}
				}
			}
		}
		c.set(mid, gy + 2, rz, VPart.FENCE);
		// mastros com bandeira nos cantos da frente e fogueira com bancos de lado
		for (int x : new int[] {x0 - 1, x1 + 1}) {
			if (x >= 0 && x < c.w()) {
				for (int y = 1; y <= 6; y++) {
					c.set(x, y, z1 + 3, VPart.FENCE);
				}
				int side = x < mid ? 1 : -1;
				if (x + side >= 0 && x + side < c.w()) {
					c.set(x + side, 6, z1 + 3, VPart.WOOL_ACCENT);
					c.set(x + side, 5, z1 + 3, VPart.WOOL_ALT);
				}
			}
		}
		int fx = x0 + 1;
		int fz = z1 + 5 > c.d() - 2 ? c.d() - 2 : z1 + 5;
		c.set(fx, 1, fz, VPart.CAMPFIRE);
		c.set(fx - 1, 1, fz, VPart.WOOD_STAIRS, Facing.EAST);
		c.set(fx, 1, fz - 1, VPart.WOOD_STAIRS, Facing.SOUTH);
		c.set(fx, 1, fz + 1 < c.d() ? fz + 1 : fz - 2, VPart.WOOD_STAIRS, Facing.NORTH);
		// carroca coberta ao lado do alojamento: leito, grades, sacos, barris, aro de lona e as varas
		int wx = c.w() - 1;
		for (int z = 2; z <= 4; z++) {
			c.set(wx, 0, z, VPart.SOIL);
			c.set(wx - 1, 0, z, VPart.SOIL);
			c.set(wx, 1, z, VPart.PLANKS);
			c.set(wx - 1, 1, z, VPart.PLANKS);
			c.set(wx - 1, 2, z, VPart.FENCE);
			c.set(wx, 2, z, VPart.FENCE);
		}
		c.set(wx, 2, 3, VPart.SACK);
		c.set(wx - 1, 2, 2, VPart.BARREL);
		c.set(wx, 2, 4, VPart.BARREL);
		c.set(wx - 1, 2, 3, VPart.SACK);
		for (int z : new int[] {2, 4}) {
			c.set(wx - 1, 3, z, VPart.FENCE);
			c.set(wx, 3, z, VPart.FENCE);
		}
		for (int z = 2; z <= 4; z++) {
			c.set(wx - 1, 4, z, VPart.WOOL_ALT);
			c.set(wx, 4, z, VPart.WOOL_ALT);
		}
		c.set(wx - 1, 3, 3, VPart.WOOL_ALT);
		c.set(wx, 3, 3, VPart.WOOL_ALT);
		c.set(wx - 1, 1, 5, VPart.FENCE);
		c.set(wx, 1, 5, VPart.FENCE);
		lampPost(c, x1 + 1, z1 + 1);
		BuildingPlans.frontYard(c, mid);
		c.spawn(mid + 1, 1, z0 + 3);
		return c;
	}
}
