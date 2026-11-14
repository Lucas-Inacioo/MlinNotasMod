package com.mlin.notas.village.works;

import java.util.Map;
import java.util.Set;

/**
 * Esculturas da praca (logica pura): estatua de chefe sobre pedestal, torre do relogio com mostradores, coreto e chafariz.
 * Tudo e escrito em "arte de linhas": cada estatua e uma silhueta de ate 7 colunas por 9 linhas (de cima para baixo), com
 * uma legenda de blocos; as letras maiusculas marcadas como "fundas" ganham tres blocos de profundidade (corpo), as demais
 * um so (bracos, asas, espinhos), e a figura olha para o centro da praca.
 *
 * <p>Referencias de forma: estatuas de heroi e de chefe sobre pedestal das galerias de builds do Planet Minecraft
 * ("Statue Pack", "Ender Dragon statue", "Wither statue", "Iron Golem statue"), torres do relogio de praca de cidade
 * europeia pequena (a "Everhollow Clocktower" e as "Clock Tower" do Planet Minecraft: fuste de pedra, caixa do relogio mais
 * larga, belvedere aberto com sino e agulha), coretos vitorianos de planta octogonal com telhado de quatro aguas e
 * chafarizes de bacia dupla de praca de vila. Escritas do zero.
 */
public final class PlazaSculpture {
	private PlazaSculpture() {
	}

	/** Uma silhueta: linhas de cima para baixo, legenda, letras que ganham profundidade e a cor da placa do pedestal. */
	record Art(String[] rows, Map<Character, String> legend, Set<Character> deep) {
		int height() {
			return rows.length;
		}
	}

	private static final String ROD = "minecraft:lightning_rod";

	/** Silhueta de cada chefe (ids do {@code BossKind}, ender_dragon, wither; qualquer outro = heroi de pedra). */
	static Art art(String boss) {
		return switch (boss == null ? "" : boss) {
			case "cobalt_golem" -> new Art(new String[] {
					"..r..",
					"..h..",
					"ATTTA",
					"ATcTA",
					"A.T.A",
					"AFTFA",
					".L.L.",
					".L.L.",
			}, Map.of('r', ROD, 'h', "mlinnotas:cobalt_block", 'T', "mlinnotas:cobalt_block", 'A', "mlinnotas:cobalt_block", 'c', "minecraft:copper_block",
					'F', "minecraft:copper_block", 'L', "minecraft:iron_block"), Set.of('h', 'T', 'L'));
			case "abyssal_guardian" -> new Art(new String[] {
					"..s..",
					".PPP.",
					"sPEPs",
					".PPP.",
					".sts.",
					"..t..",
					"..t..",
			}, Map.of('s', "minecraft:prismarine_wall", 'P', "minecraft:prismarine_bricks", 'E', "minecraft:sea_lantern", 't', "minecraft:dark_prismarine"),
					Set.of('P', 'E'));
			case "igneous_colossus" -> new Art(new String[] {
					"W...W",
					"WHHHW",
					"GBBBG",
					"GBMBG",
					".BMB.",
					".BBB.",
					".B.B.",
					".B.B.",
			}, Map.of('W', "minecraft:blackstone_wall", 'H', "minecraft:polished_blackstone_bricks", 'G', "minecraft:gilded_blackstone",
					'B', "minecraft:blackstone", 'M', "minecraft:magma_block"), Set.of('H', 'B', 'M'));
			case "astral_herald" -> new Art(new String[] {
					"..e..",
					"..A..",
					"WpPpW",
					"WpPpW",
					".WPW.",
					"..P..",
					".PPP.",
					".PPP.",
			}, Map.of('e', "minecraft:end_rod", 'A', "minecraft:amethyst_block", 'W', "minecraft:purple_stained_glass", 'p', "minecraft:purpur_pillar",
					'P', "minecraft:purpur_block"), Set.of('A', 'P'));
			case "ender_dragon" -> new Art(new String[] {
					"....DD.",
					"...hDm.",
					"...DD..",
					"WW.DD..",
					"WWWBBB.",
					".W.BBBD",
					".tBBBD.",
					"ttBBB..",
			}, Map.of('h', "minecraft:end_rod", 'D', "minecraft:obsidian", 'W', "minecraft:magenta_stained_glass", 'm', "minecraft:crying_obsidian",
					'B', "minecraft:crying_obsidian", 't', "minecraft:obsidian"), Set.of('B'));
			case "wither" -> new Art(new String[] {
					".kkk.",
					"ZSSSZ",
					".bSb.",
					"..S..",
					".bSb.",
					"..S..",
					"..S..",
			}, Map.of('k', "SKULL", 'Z', "minecraft:soul_soil", 'S', "minecraft:soul_sand", 'b', "minecraft:bone_block"), Set.of('S'));
			default -> new Art(new String[] {
					"..H.R",
					".sHsR",
					"IBBBR",
					"IBBB.",
					".BBB.",
					".L.L.",
					".L.L.",
			}, Map.of('H', "minecraft:chiseled_stone_bricks", 's', "minecraft:stone_brick_wall", 'I', "minecraft:iron_bars", 'R', "minecraft:iron_bars",
					'B', "minecraft:stone_bricks", 'L', "minecraft:polished_andesite"), Set.of('H', 'B', 'L'));
		};
	}

	/** Altura do pedestal acima do chao da praca (a figura comeca em {@code PLINTH + 1}). */
	static final int PLINTH = 3;

	/** Estatua sobre pedestal escalonado, com placa na frente; {@code f} = +1 olha para +z, -1 para -z. */
	public static void statue(Mats m, Blueprint bp, int cx, int cz, int base, String boss, int f) {
		String front = f > 0 ? "south" : "north";
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				bp.surface(cx + dx, base, cz + dz, m.bricks());
			}
		}
		// degrau de baixo: laje em volta, o meio sobe
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					bp.above(cx, base + 1, cz, m.smooth());
				} else if (dx != 0 && dz != 0) {
					bp.above(cx + dx, base + 1, cz + dz, m.brickSlab());
				} else {
					String face = dx != 0 ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
					bp.above(cx + dx, base + 1, cz + dz, m.stoneStairs(face, false));
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				bp.above(cx + dx, base + 2, cz + dz, m.bricks());
				bp.above(cx + dx, base + 3, cz + dz, dx == 0 && dz == 0 ? m.accent() : m.smooth());
			}
		}
		bp.add(WBlock.sign(cx, base + 2, cz + 2 * f, m.wallSign(front), "tr:sign.mlinnotas.statue.header", "tr:sign.mlinnotas.statue." + boss(boss), "", ""));
		Art art = art(boss);
		int w = art.rows()[0].length();
		int half = w / 2;
		int top = base + PLINTH + art.height();
		for (int r = 0; r < art.height(); r++) {
			String row = art.rows()[r];
			int y = top - r;
			for (int i = 0; i < row.length(); i++) {
				char ch = row.charAt(i);
				if (ch == '.') {
					continue;
				}
				String state = art.legend().get(ch);
				if (state == null) {
					continue;
				}
				if (state.equals("SKULL")) {
					state = "minecraft:wither_skeleton_skull[rotation=" + (f > 0 ? 0 : 8) + "]";
				}
				int depth = art.deep().contains(ch) ? 1 : 0;
				for (int dv = -depth; dv <= depth; dv++) {
					if (state.contains("_skull") && dv != 0) {
						continue;
					}
					bp.above(cx + i - half, y, cz + dv, state);
				}
			}
		}
	}

	private static String boss(String boss) {
		return switch (boss == null ? "" : boss) {
			case "cobalt_golem", "abyssal_guardian", "igneous_colossus", "astral_herald", "ender_dragon", "wither" -> boss;
			default -> "hero";
		};
	}

	// ---------------------------------------------------------------- torre do relogio

	/**
	 * Torre do relogio: base escalonada, fuste de pedra com cantoneiras e seteiras, caixa do relogio 5x5 com um mostrador
	 * 3x3 claro em cada lado (ponteiros em escuro, aro de tijolo), belvedere aberto com o sino pendurado e telhado de
	 * quatro aguas com agulha.
	 */
	public static void clockTower(Mats m, Blueprint bp, int cx, int cz, int base) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				bp.surface(cx + dx, base, cz + dz, m.bricks());
			}
		}
		// soco: cantos de laje
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				boolean corner = dx != 0 && dz != 0;
				bp.above(cx + dx, base + 1, cz + dz, corner ? m.log() : m.bricks());
			}
		}
		// fuste y2..y5
		for (int y = 2; y <= 5; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean corner = dx != 0 && dz != 0;
					boolean slit = !corner && (dx == 0 ^ dz == 0) && y == 4 && (dx != 0 || dz != 0);
					String block = corner ? m.log() : slit ? "minecraft:iron_bars" : (y == 2 || y == 5) ? m.bricks() : m.smooth();
					if (dx == 0 && dz == 0) {
						block = m.bricks();
					}
					bp.above(cx + dx, base + y, cz + dz, block);
				}
			}
		}
		// cornija: anel de degraus invertidos sobre o fuste, ja saindo para a caixa do relogio
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
					bp.above(cx + dx, base + 6, cz + dz, m.bricks());
				} else {
					boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					if (corner) {
						bp.above(cx + dx, base + 6, cz + dz, m.brickSlab().replace("bottom", "top"));
					} else {
						String face = Math.abs(dx) == 2 ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
						bp.above(cx + dx, base + 6, cz + dz, m.stoneStairs(face, true));
					}
				}
			}
		}
		// caixa do relogio y7..y9: 5x5; mostradores 3x3 nas quatro faces
		for (int y = 7; y <= 9; y++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
						bp.above(cx + dx, base + y, cz + dz, m.bricks());
						continue;
					}
					boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					if (corner) {
						bp.above(cx + dx, base + y, cz + dz, m.log());
						continue;
					}
					// parede lisa; o miolo 3x3 de cada face e o mostrador
					bp.above(cx + dx, base + y, cz + dz, dial(m, dx, dz, y - 8));
				}
			}
		}
		// coroa da caixa: laje saliente
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				bp.above(cx + dx, base + 10, cz + dz, corner ? m.log() : m.smooth());
			}
		}
		// belvedere y11..y13: quatro pilares, vao aberto, sino pendurado
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				boolean corner = dx != 0 && dz != 0;
				if (corner) {
					for (int y = 11; y <= 13; y++) {
						bp.above(cx + dx, base + y, cz + dz, m.log());
					}
				} else if (dx != 0 || dz != 0) {
					bp.above(cx + dx, base + 11, cz + dz, m.stoneStairs(dx != 0 ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south"), false));
				}
			}
		}
		bp.above(cx, base + 13, cz, m.bricks());
		bp.above(cx, base + 12, cz, "minecraft:bell[attachment=ceiling,facing=north]");
		// telhado: tres aneis de degraus, laje e agulha
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
					bp.above(cx + dx, base + 14, cz + dz, m.planks());
					continue;
				}
				if (corner) {
					bp.above(cx + dx, base + 14, cz + dz, m.slabBottom());
				} else {
					String face = Math.abs(dx) == 2 ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
					bp.above(cx + dx, base + 14, cz + dz, m.stairs(face, false));
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					bp.above(cx, base + 14, cz, m.planks());
					continue;
				}
				boolean corner = dx != 0 && dz != 0;
				if (corner) {
					bp.above(cx + dx, base + 15, cz + dz, m.slabBottom());
				} else {
					String face = dx != 0 ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
					bp.above(cx + dx, base + 15, cz + dz, m.stairs(face, false));
				}
			}
		}
		bp.above(cx, base + 15, cz, m.planks());
		bp.above(cx, base + 16, cz, m.slabBottom());
		bp.above(cx, base + 17, cz, m.fence());
		bp.above(cx, base + 18, cz, ROD);
	}

	/**
	 * Bloco do mostrador na posicao (dx, dz) da parede do relogio, na altura {@code row} (-1 = baixo, 0 = meio, 1 = cima).
	 * O miolo 3x3 de cada face e quartzo liso; os ponteiros (que marcam tres horas) e o centro sao concreto preto, a aro
	 * em cima e embaixo e de pedra lapidada.
	 */
	static String dial(Mats m, int dx, int dz, int row) {
		int along = Math.abs(dx) == 2 ? dz : dx;
		if (along == 0 && row == 0) {
			return "minecraft:gold_block";
		}
		// marcas das 12, 3, 6 e 9 horas em escuro; os cantos ficam claros
		boolean mark = (along == 0) != (row == 0);
		return mark ? "minecraft:black_concrete" : "minecraft:smooth_quartz";
	}

	// ---------------------------------------------------------------- coreto

	/** Coreto: palco elevado 3x3 com degrau na frente, balaustrada, quatro colunas e telhado de quatro aguas com lanterna. */
	public static void bandstand(Mats m, Blueprint bp, int cx, int cz, int base, int f) {
		String front = f > 0 ? "south" : "north";
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				boolean edge = dx != 0 || dz != 0;
				bp.surface(cx + dx, base, cz + dz, edge ? m.bricks() : m.planks());
				bp.above(cx + dx, base + 1, cz + dz, m.planks());
			}
		}
		// degrau de pedra na frente
		bp.above(cx, base + 1, cz + 2 * f, m.stoneStairs(front, false));
		// colunas nos cantos (tronco), balaustrada nos lados e no fundo, frente aberta
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				for (int y = 2; y <= 4; y++) {
					bp.above(cx + sx, base + y, cz + sz, m.log());
				}
			}
			bp.above(cx + sx, base + 2, cz, m.fence());
		}
		bp.above(cx, base + 2, cz - f, m.fence());
		// banco no fundo e lanterna pendurada no meio
		bp.above(cx, base + 2, cz, m.stairs(f > 0 ? "north" : "south", false));
		bp.above(cx, base + 4, cz, Mats.lantern(true));
		// telhado: beiral de degraus 5x5, anel interno e topo
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
					bp.above(cx + dx, base + 5, cz + dz, m.planks());
					continue;
				}
				if (corner) {
					bp.above(cx + dx, base + 5, cz + dz, m.slabBottom());
				} else {
					String face = Math.abs(dx) == 2 ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
					bp.above(cx + dx, base + 5, cz + dz, m.stairs(face, false));
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				boolean corner = dx != 0 && dz != 0;
				if (dx == 0 && dz == 0) {
					bp.above(cx, base + 6, cz, m.planks());
				} else if (corner) {
					bp.above(cx + dx, base + 6, cz + dz, m.slabBottom());
				} else {
					String face = dx != 0 ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
					bp.above(cx + dx, base + 6, cz + dz, m.stairs(face, false));
				}
			}
		}
		bp.above(cx, base + 7, cz, m.slabBottom());
		bp.above(cx, base + 8, cz, m.fence());
		bp.above(cx, base + 9, cz, Mats.lantern(false));
	}

	// ---------------------------------------------------------------- chafariz

	/** Chafariz de duas bacias: lago 3x3 com coluna central, bacia de cima de laje e jato de vidro azul. */
	public static void fountain(Mats m, Blueprint bp, int cx, int cz, int base) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (dx == 0 && dz == 0) {
					bp.surface(x, base, z, m.bricks());
					bp.above(x, base + 1, z, m.accent());
					bp.above(x, base + 2, z, m.wall());
					bp.above(x, base + 3, z, m.wall());
					continue;
				}
				bp.surface(x, base, z, "minecraft:water");
				if (dx != 0 && dz != 0) {
					bp.above(x, base + 1, z, m.wall());
					bp.above(x, base + 2, z, Mats.lantern(false));
				}
			}
		}
		// bacia de cima: laje larga, jato e lanterna
		for (int[] s : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			bp.above(cx + s[0], base + 4, cz + s[1], m.brickSlab());
		}
		bp.above(cx, base + 4, cz, m.accent());
		bp.above(cx, base + 5, cz, "minecraft:light_blue_stained_glass");
		bp.above(cx, base + 6, cz, "minecraft:light_blue_stained_glass_pane");
		bp.above(cx, base + 7, cz, Mats.lantern(false));
	}
}
