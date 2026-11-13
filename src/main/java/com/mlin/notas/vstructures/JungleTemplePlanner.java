package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Templo da selva refeito: zigurate de 4 andares (31x35, 16 de altura) de pedregulho e tijolos musgosos, com
 * escadaria monumental na frente, terracos com musgo, samambaias e arbustos, trepadeiras nas faces, uma arvore da
 * selva crescendo num terraco e um santuario no topo com crista vazada (estilo Tikal). Dentro: o atrio de 12 blocos de
 * altura com espelho d'agua, oculo coberto de folhas, mezanino com escada e escada de mao ate o terraco; o corredor
 * das flechas vanilla (fio de armadilha + 2 despensers escondidos atras de trepadeiras) desde a porta de baixo; a
 * galeria do bau principal com a segunda armadilha; o quebra-cabeca de 3 alavancas (combinacao: as duas com bloco
 * cinzelado em cima ligadas e a do bloco rachado desligada) que recolhe os pistoes grudentos e abre a sala escondida;
 * e duas salas no mezanino (deposito e sala do idolo com tijolos infestados). Entrada pela frente (-z). Logica pura.
 *
 * <p>Circuito do quebra-cabeca (atras da parede das alavancas): cada alavanca "certa" apaga uma tocha de redstone; a
 * "errada" liga direto a linha. A linha (fio) segura os pistoes estendidos; so sem nenhuma tocha acesa e com a
 * alavanca errada desligada ela fica sem sinal e os pistoes puxam a porta.
 *
 * <p>Referencias (so ideia de forma, camadas e paleta; nada copiado): YUNG's Better Jungle Temples (armadilhas e
 * quebra-cabecas novos), "Mayan/Incan Jungle Temple" e "Tikal Temple Pack" (Planet Minecraft: andares em degraus,
 * crista do telhado, desgaste), "Re-Structured" (Planet Minecraft: salao com agua) e "Dungeons and Taverns Jungle
 * Temple Overhaul" (Modrinth: salas e quebra-cabecas).
 */
public final class JungleTemplePlanner extends PlanBuilder {
	static final String MAIN = "minecraft:chests/jungle_temple";
	static final String DISPENSER = "minecraft:chests/jungle_temple_dispenser";
	static final String STORAGE = "mlinnotas:chests/jungle_storage";
	public static final String MARKER_SECRET_DOOR = "secret_door";
	public static final String MARKER_HIDDEN_CHEST = "hidden_chest";
	public static final String MARKER_MAIN_CHEST = "main_chest";
	/** Alavancas do quebra-cabeca: {@code lever_0..2} (da esquerda para a direita); {@code wrong_lever} = a que fica desligada. */
	public static final String MARKER_LEVER = "lever_";
	public static final String MARKER_WRONG_LEVER = "wrong_lever";
	public static final String MARKER_TRAP_1 = "trap_1";
	public static final String MARKER_TRAP_2 = "trap_2";

	private static final int[] HX = {15, 13, 11, 9};
	private static final int[] HZ = {17, 15, 13, 11};
	private static final int TOP = 15;

	private final boolean bamboo;

	private JungleTemplePlanner(Random random, boolean bamboo) {
		super(new VPlan(false), random);
		this.bamboo = bamboo;
	}

	public static VPlan plan(long seed, int variant) {
		JungleTemplePlanner b = new JungleTemplePlanner(new Random(seed * 104729L + 3), variant == VariantRules.JUNGLE_BAMBOO);
		b.build();
		return b.p;
	}

	private String stone() {
		int roll = r.nextInt(100);
		if (roll < 20) {
			return "minecraft:cobblestone";
		}
		if (roll < 46) {
			return "minecraft:mossy_cobblestone";
		}
		if (roll < 64) {
			return "minecraft:stone_bricks";
		}
		if (roll < 90) {
			return "minecraft:mossy_stone_bricks";
		}
		return "minecraft:cracked_stone_bricks";
	}

	private String brick() {
		int roll = r.nextInt(100);
		return roll < 45 ? "minecraft:mossy_stone_bricks" : roll < 80 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks";
	}

	private void build() {
		body();
		grandStair();
		terraces();
		shrine();
		atrium();
		arrowCorridor();
		treasureGallery();
		puzzle();
		upperRooms();
		tree();
		vines();
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -38);
	}

	private void body() {
		for (int x = -HX[0]; x <= HX[0]; x++) {
			for (int z = -HZ[0]; z <= HZ[0]; z++) {
				p.set(x, -1, z, "minecraft:cobblestone", VPlan.FILL_DOWN);
			}
		}
		for (int k = 0; k < 4; k++) {
			for (int y = 4 * k; y <= 4 * k + 3; y++) {
				for (int x = -HX[k]; x <= HX[k]; x++) {
					for (int z = -HZ[k]; z <= HZ[k]; z++) {
						boolean edge = Math.abs(x) == HX[k] || Math.abs(z) == HZ[k];
						boolean glyph = edge && y == 4 * k + 1 && (x + z) % 4 == 0;
						set(x, y, z, glyph ? "minecraft:chiseled_stone_bricks" : stone());
					}
				}
			}
		}
		// cornija de cada andar (degraus de cabeca para baixo para fora)
		for (int k = 0; k < 4; k++) {
			int y = 4 * k + 3;
			for (int x = -HX[k]; x <= HX[k]; x++) {
				for (int z = -HZ[k]; z <= HZ[k]; z++) {
					if (Math.abs(x) != HX[k] && Math.abs(z) != HZ[k]) {
						continue;
					}
					if (chance(0.12)) {
						continue;
					}
					int ox = Math.abs(x) == HX[k] ? Integer.signum(x) : 0;
					int oz = Math.abs(z) == HZ[k] && ox == 0 ? Integer.signum(z) : 0;
					if (ox == 0 && oz == 0) {
						continue;
					}
					String facing = ox > 0 ? W : ox < 0 ? E : oz > 0 ? N : S;
					if (p.get(x + ox, y, z + oz) == null) {
						set(x + ox, y, z + oz, stairsTop(chance(0.5) ? "minecraft:mossy_stone_brick_stairs" : "minecraft:stone_brick_stairs", facing));
					}
				}
			}
		}
	}

	/** Escadaria monumental da frente, do chao ao terraco do topo, com balaustradas. */
	private void grandStair() {
		for (int h = 1; h <= 16; h++) {
			int z = h - 28;
			for (int x = -3; x <= 3; x++) {
				p.set(x, -1, z, "minecraft:cobblestone", VPlan.FILL_DOWN);
				for (int y = 0; y < h - 1; y++) {
					if (p.get(x, y, z) == null || Math.abs(z) > HZ[0]) {
						set(x, y, z, stone());
					}
				}
				if (Math.abs(x) == 3) {
					set(x, h - 1, z, stone());
					set(x, h, z, s(chance(0.6) ? "mossy_stone_brick_wall" : "cobblestone_wall"));
				} else {
					int roll = r.nextInt(100);
					String id = roll < 45 ? "minecraft:mossy_stone_brick_stairs" : roll < 80 ? "minecraft:stone_brick_stairs" : "minecraft:mossy_cobblestone_stairs";
					set(x, h - 1, z, stairs(id, S));
					for (int y = h; y <= h + 3; y++) {
						if (p.get(x, y, z) != null && y <= TOP) {
							set(x, y, z, VPlan.AIR);
						}
					}
				}
			}
		}
		for (int x = -5; x <= 5; x++) {
			for (int z = -32; z <= -28; z++) {
				for (int y = 0; y <= 4; y++) {
					p.setIfAbsent(x, y, z, VPlan.AIR);
				}
			}
		}
		// praca de pedra na frente da escada, com estelas entalhadas
		for (int x = -9; x <= 9; x++) {
			for (int z = -36; z <= -28; z++) {
				if (Math.abs(x) == 9 && (z == -36 || z == -28)) {
					continue;
				}
				p.set(x, -1, z, chance(0.15) ? "minecraft:mossy_cobblestone" : brick(), VPlan.FILL_DOWN);
				if (chance(0.08) && Math.abs(x) > 2) {
					set(x, 0, z, "minecraft:moss_carpet");
				}
			}
		}
		for (int x : new int[] {-8, 8}) {
			for (int z : new int[] {-34, -30}) {
				int h = chance(0.25) ? 2 : 4;
				for (int y = 0; y < h; y++) {
					set(x, y, z, y == 1 || y == 3 ? "minecraft:chiseled_stone_bricks" : "minecraft:mossy_stone_bricks");
				}
				set(x, h, z, h == 4 ? s("mossy_stone_brick_slab") : s("mossy_stone_brick_stairs", "facing=" + (x < 0 ? E : W)));
				if (chance(0.6)) {
					set(x + (x < 0 ? -1 : 1), 1, z, s("vine", (x < 0 ? E : W) + "=true"));
				}
			}
		}
		// pedestais com braseiros apagados no pe da escada
		for (int x : new int[] {-5, 5}) {
			p.set(x, -1, -27, "minecraft:cobblestone", VPlan.FILL_DOWN);
			set(x, 0, -27, "minecraft:chiseled_stone_bricks");
			set(x, 1, -27, "minecraft:mossy_stone_bricks");
			set(x, 2, -27, s("campfire", "lit=false"));
		}
	}

	/** Terracos: musgo, plantas, arbustos de folhas (e bambu na variante de bambuzal). */
	private void terraces() {
		for (int k = 0; k < 4; k++) {
			int y = 4 * k + 3;
			for (int x = -HX[k]; x <= HX[k]; x++) {
				for (int z = -HZ[k]; z <= HZ[k]; z++) {
					boolean inner = k < 3 && Math.abs(x) <= HX[k + 1] && Math.abs(z) <= HZ[k + 1];
					if (inner || (Math.abs(x) <= 3 && z < 0)) {
						continue;
					}
					int roll = r.nextInt(100);
					if (k == 3) {
						set(x, y, z, roll < 50 ? "minecraft:mossy_stone_bricks" : roll < 85 ? "minecraft:stone_bricks" : "minecraft:mossy_cobblestone");
						continue;
					}
					boolean edge = Math.abs(x) == HX[k] || Math.abs(z) == HZ[k];
					String top = roll < 8 ? "minecraft:moss_block" : roll < 60 ? "minecraft:mossy_cobblestone" : brick();
					set(x, y, z, top);
					if (p.get(x, y + 1, z) != null || (!top.equals("minecraft:moss_block") && !chance(0.6))) {
						continue;
					}
					int plant = r.nextInt(100);
					if (plant < 30) {
						set(x, y + 1, z, "minecraft:moss_carpet");
					} else if (plant < 45 && top.equals("minecraft:moss_block")) {
						set(x, y + 1, z, pick("minecraft:fern", "minecraft:short_grass", "minecraft:fern"));
					} else if (plant < 55 && !edge) {
						set(x, y + 1, z, s("jungle_leaves", "persistent=true"));
						if (chance(0.4)) {
							set(x, y + 2, z, s("jungle_leaves", "persistent=true"));
						}
					} else if (bamboo && plant < 65 && !edge && top.equals("minecraft:moss_block")) {
						int h = between(2, 5);
						for (int i = 1; i <= h; i++) {
							set(x, y + i, z, s("bamboo", "age=0", "leaves=" + (i == h ? "large" : i == h - 1 ? "small" : "none"), "stage=0"));
						}
					}
				}
			}
		}
	}

	private void shrine() {
		int base = TOP + 1;
		for (int x = -4; x <= 4; x++) {
			for (int z = -3; z <= 5; z++) {
				boolean edge = Math.abs(x) == 4 || z == -3 || z == 5;
				for (int y = base; y <= base + 4; y++) {
					if (edge) {
						boolean corner = Math.abs(x) == 4 && (z == -3 || z == 5);
						set(x, y, z, corner ? (y % 2 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:mossy_stone_bricks") : brick());
					} else {
						set(x, y, z, VPlan.AIR);
					}
				}
				set(x, TOP, z, edge ? "minecraft:chiseled_stone_bricks" : (x + z) % 2 == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks");
			}
		}
		for (int x = -5; x <= 5; x++) {
			for (int z = -4; z <= 6; z++) {
				set(x, base + 5, z, s(chance(0.5) ? "mossy_stone_brick_slab" : "stone_brick_slab"));
			}
		}
		fill(-3, base + 5, -2, 3, base + 5, 4, "minecraft:mossy_stone_bricks");
		fill(-3, base + 6, -2, 3, base + 6, 4, "minecraft:stone_bricks");
		// crista vazada do telhado
		for (int x = -3; x <= 3; x++) {
			for (int y = base + 7; y <= base + 10; y++) {
				boolean hole = x % 2 != 0 && y >= base + 8 && y <= base + 9;
				set(x, y, 3, hole ? VPlan.AIR : y == base + 10 ? s("stone_brick_slab") : brick());
			}
		}
		// porta, lintel, braseiros, altar
		air(-1, base, -3, 1, base + 2, -3);
		fill(-1, base + 3, -3, 1, base + 3, -3, "minecraft:chiseled_stone_bricks");
		for (int x : new int[] {-3, 3}) {
			set(x, base, -6, "minecraft:chiseled_stone_bricks");
			set(x, base + 1, -6, s("campfire", "lit=false"));
		}
		set(0, base, 4, "minecraft:chiseled_stone_bricks");
		set(0, base + 1, 4, "minecraft:decorated_pot");
		set(-2, base, 4, s("candle", "candles=3", "lit=false"));
		set(2, base, 4, s("candle", "candles=2", "lit=false"));
		set(-3, base, 4, "minecraft:skeleton_skull");
		set(3, base + 4, 4, "minecraft:cobweb");
		// oculo coberto de folhas sobre o atrio
		fill(-1, TOP, -1, 1, TOP, 1, s("jungle_leaves", "persistent=true"));
	}

	/** Atrio: espelho d'agua, oculo, mezanino com escada e escada de mao ate o terraco. */
	private void atrium() {
		air(-5, 0, -6, 5, 11, 6);
		air(-1, 12, -1, 1, TOP - 1, 1);
		for (int x = -5; x <= 5; x++) {
			for (int z = -6; z <= 6; z++) {
				set(x, -1, z, chance(0.25) ? "minecraft:moss_block" : brick());
			}
		}
		for (int y = 0; y <= 11; y++) {
			for (int i = -6; i <= 6; i++) {
				String face = y == 3 && i % 3 == 0 ? "minecraft:chiseled_stone_bricks" : brick();
				set(-6, y, i, face);
				set(6, y, i, face);
				if (Math.abs(i) <= 5) {
					set(i, y, -7, face);
					set(i, y, 7, face);
				}
			}
		}
		// espelho d'agua
		fill(-2, -2, -2, 2, -2, 2, "minecraft:mossy_cobblestone");
		fill(-1, -1, -1, 1, -1, 1, "minecraft:water");
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) == 2) {
					set(x, -1, z, "minecraft:chiseled_stone_bricks");
					if ((x + z) % 2 == 0) {
						set(x, 0, z, s("mossy_stone_brick_slab"));
					}
				}
			}
		}
		set(1, 0, -1, "minecraft:lily_pad");
		set(-1, 0, 1, "minecraft:lily_pad");
		// pilares e mezanino em y=6
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int y = 0; y <= 5; y++) {
					set(4 * sx, y, 5 * sz, y == 0 || y == 5 ? "minecraft:chiseled_stone_bricks" : "minecraft:mossy_stone_bricks");
				}
			}
		}
		for (int x = -5; x <= 5; x++) {
			for (int z = -6; z <= 6; z++) {
				if (Math.abs(x) >= 4 || Math.abs(z) >= 5) {
					set(x, 6, z, chance(0.3) ? "minecraft:mossy_cobblestone" : "minecraft:mossy_stone_bricks");
					boolean innerEdge = (Math.abs(x) == 4 && Math.abs(z) <= 4) || (Math.abs(z) == 5 && Math.abs(x) <= 4);
					if (innerEdge) {
						set(x, 7, z, s("jungle_fence"));
					}
				}
			}
		}
		// escada do mezanino (encostada na parede oeste) e o vao no piso
		for (int h = 1; h <= 6; h++) {
			int z = 4 - h;
			for (int y = 0; y < h - 1; y++) {
				set(-5, y, z, "minecraft:mossy_stone_bricks");
			}
			set(-5, h - 1, z, stairs("minecraft:mossy_stone_brick_stairs", N));
			set(-5, 6, z, VPlan.AIR);
			set(-4, 7, z, s("jungle_fence"));
		}
		set(-4, 7, 3, s("jungle_fence"));
		set(-4, 7, -3, VPlan.AIR);
		set(-4, 7, -2, s("jungle_fence"));
		// escada de mao do mezanino ao terraco do topo
		for (int y = 7; y <= TOP; y++) {
			set(5, y, 6, ladder(N));
		}
		// trepadeiras caindo do oculo e liquens brilhantes
		for (int y = 4; y <= TOP - 1; y++) {
			if (y >= 12 || chance(0.8)) {
				set(-1, y, 0, s("vine", "west=true"));
			}
			if (y >= 7 && (y >= 12 || chance(0.7))) {
				set(1, y, 1, s("vine", "east=true"));
			}
		}
		for (int i = 0; i < 10; i++) {
			int y = between(8, 11);
			int z = between(-4, 4);
			if (chance(0.5)) {
				set(5, y, z, s("glow_lichen", "east=true"));
			} else {
				set(-5, y, z, s("glow_lichen", "west=true"));
			}
		}
		set(3, 11, -6, "minecraft:cobweb");
		set(-3, 11, 6, "minecraft:cobweb");
	}

	/** Corredor da porta de baixo com a primeira armadilha de flechas. */
	private void arrowCorridor() {
		air(-9, 0, -HZ[0], -9, 2, -HZ[0]);
		air(-10, 0, -16, -8, 2, -1);
		for (int z = -16; z <= -1; z++) {
			set(-9, -1, z, z % 2 == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:cracked_stone_bricks");
		}
		set(-9, 3, -HZ[0], "minecraft:chiseled_stone_bricks");
		tripwireTrap(-10, -8, -12, MARKER_TRAP_1);
		// porta para o atrio
		air(-7, 0, -5, -6, 2, -4);
		set(-8, 2, -16, "minecraft:cobweb");
		set(-10, 2, -6, "minecraft:cobweb");
	}

	/**
	 * Armadilha vanilla atravessando um corredor no eixo z: ganchos nas bordas {@code x0} e {@code x1}, fio no meio e um
	 * despenser com flechas em cada parede (logo acima do bloco que segura o gancho), escondido atras de trepadeira.
	 */
	private void tripwireTrap(int x0, int x1, int z, String marker) {
		set(x0 - 1, 0, z, "minecraft:mossy_stone_bricks");
		set(x1 + 1, 0, z, "minecraft:mossy_stone_bricks");
		set(x0, 0, z, s("tripwire_hook", "facing=east", "attached=true"));
		set(x1, 0, z, s("tripwire_hook", "facing=west", "attached=true"));
		for (int x = x0 + 1; x < x1; x++) {
			set(x, 0, z, s("tripwire", "attached=true", "east=true", "west=true"));
		}
		p.loot(x0 - 1, 1, z, s("dispenser", "facing=east"), DISPENSER);
		p.loot(x1 + 1, 1, z, s("dispenser", "facing=west"), DISPENSER);
		set(x0, 1, z, s("vine", "west=true"));
		set(x1, 1, z, s("vine", "east=true"));
		p.mark(marker, x0 + 1, 0, z);
	}

	/** Galeria do bau principal, com a segunda armadilha de flechas na frente dele. */
	private void treasureGallery() {
		air(-13, 0, -3, -11, 2, 8);
		for (int z = -3; z <= 8; z++) {
			set(-12, -1, z, "minecraft:chiseled_stone_bricks");
			if (z % 3 == 0) {
				set(-14, 1, z, "minecraft:chiseled_stone_bricks");
			}
		}
		tripwireTrap(-13, -11, 4, MARKER_TRAP_2);
		p.loot(-12, 0, 9, s("chest", "facing=north"), MAIN);
		p.mark(MARKER_MAIN_CHEST, -12, 0, 9);
		set(-13, 0, 8, "minecraft:skeleton_skull");
		set(-11, 0, 8, s("candle", "candles=2", "lit=false"));
		set(-12, 2, 8, "minecraft:cobweb");
	}

	/** Quebra-cabeca das 3 alavancas e a sala escondida atras dos pistoes grudentos. */
	private void puzzle() {
		air(-3, 0, 8, 3, 2, 13);
		air(0, 0, 7, 0, 1, 7);
		for (int x = -3; x <= 3; x++) {
			for (int z = 8; z <= 13; z++) {
				set(x, -1, z, (x + z) % 2 == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks");
			}
		}
		int wrong = r.nextInt(3);
		for (int i = 0; i < 3; i++) {
			int x = -2 + 2 * i;
			set(x, 1, 14, "minecraft:stone_bricks");
			set(x, 2, 14, i == wrong ? "minecraft:cracked_stone_bricks" : "minecraft:chiseled_stone_bricks");
			set(x, 1, 13, s("lever", "face=wall", "facing=north", "powered=false"));
			p.mark(MARKER_LEVER + i, x, 1, 13);
			// atras da parede: tocha (alavanca certa) ou fio direto (alavanca errada)
			set(x, 1, 15, i == wrong ? "minecraft:redstone_wire" : s("redstone_wall_torch", "facing=south", "lit=true"));
			set(x - 1, 1, 15, "minecraft:stone");
			set(x + 1, 1, 15, "minecraft:stone");
		}
		p.mark(MARKER_WRONG_LEVER, -2 + 2 * wrong, 1, 13);
		for (int x = -2; x <= 6; x++) {
			set(x, 0, 16, "minecraft:stone");
			set(x, 1, 16, "minecraft:redstone_wire");
		}
		for (int z = 12; z <= 15; z++) {
			set(6, 0, z, "minecraft:stone");
			set(6, 1, z, "minecraft:redstone_wire");
		}
		set(5, 0, 12, "minecraft:stone");
		set(5, 1, 12, "minecraft:redstone_wire");
		// pistoes grudentos estendidos segurando a porta (2 blocos) na parede leste da sala
		for (int y = 0; y <= 1; y++) {
			set(4, y, 12, s("sticky_piston", "facing=north", "extended=true"));
			set(4, y, 11, s("piston_head", "facing=north", "type=sticky", "short=false"));
			set(4, y, 10, "minecraft:mossy_stone_bricks");
		}
		p.mark(MARKER_SECRET_DOOR, 4, 0, 10);
		// sala escondida
		air(5, 0, 8, 7, 2, 11);
		for (int x = 5; x <= 7; x++) {
			for (int z = 8; z <= 11; z++) {
				set(x, -1, z, "minecraft:chiseled_stone_bricks");
			}
		}
		p.loot(7, 0, 9, s("chest", "facing=west"), MAIN);
		p.mark(MARKER_HIDDEN_CHEST, 7, 0, 9);
		set(7, 0, 11, "minecraft:gold_block");
		set(7, 1, 11, "minecraft:skeleton_skull");
		set(5, 2, 8, "minecraft:cobweb");
		// enfeites da sala do quebra-cabeca
		set(-3, 0, 8, "minecraft:decorated_pot");
		set(3, 0, 8, s("candle", "candles=3", "lit=false"));
		set(-3, 2, 13, "minecraft:cobweb");
	}

	/** Salas do mezanino: deposito (leste) e sala do idolo com tijolos infestados (oeste). */
	private void upperRooms() {
		air(7, 7, -4, 10, 9, 4);
		air(6, 7, 0, 6, 8, 0);
		p.loot(10, 7, 0, s("barrel", "facing=west"), STORAGE);
		set(10, 7, -4, s("barrel", "facing=up"));
		set(10, 8, -4, s("barrel", "facing=up"));
		set(9, 7, -4, s("barrel", "facing=up"));
		set(10, 7, 4, "minecraft:decorated_pot");
		set(7, 7, 4, s("potted_bamboo"));
		set(8, 7, -4, s("potted_fern"));
		set(10, 9, 2, "minecraft:cobweb");
		for (int z = -4; z <= 4; z++) {
			set(8, 6, z, "minecraft:jungle_planks");
		}

		air(-10, 7, -4, -7, 9, 4);
		air(-6, 7, -4, -6, 8, -4);
		set(-10, 7, 0, "minecraft:chiseled_stone_bricks");
		set(-10, 8, 0, "minecraft:mossy_stone_bricks");
		set(-10, 9, 0, s("carved_pumpkin", "facing=east"));
		set(-10, 7, -1, "minecraft:emerald_block");
		set(-10, 7, 1, "minecraft:emerald_block");
		set(-9, 7, 0, s("candle", "candles=4", "lit=false"));
		set(-7, 7, 4, "minecraft:decorated_pot");
		set(-10, 7, 4, "minecraft:decorated_pot");
		reclad(-11, 7, -5, -11, 9, 5, "minecraft:mossy_stone_bricks", "minecraft:infested_mossy_stone_bricks", "minecraft:cracked_stone_bricks",
				"minecraft:infested_cracked_stone_bricks");
		set(-7, 9, 4, "minecraft:cobweb");
	}

	/** Arvore da selva crescendo num canto do terraco do segundo andar (com cacau). */
	private void tree() {
		int sx = pick(-1, 1);
		int sz = chance(0.7) ? 1 : -1;
		int tx = sx * 12;
		int tz = sz * 14;
		int base = 8;
		int height = between(6, 8);
		for (int y = base; y < base + height; y++) {
			set(tx, y, tz, axis("jungle_log", "y"));
		}
		set(tx + sx, base + 2, tz, s("cocoa", "facing=" + (sx > 0 ? W : E), "age=2"));
		set(tx, base + 3, tz + sz, s("cocoa", "facing=" + (sz > 0 ? N : S), "age=1"));
		int cy = base + height;
		for (int x = -3; x <= 3; x++) {
			for (int y = -1; y <= 2; y++) {
				for (int z = -3; z <= 3; z++) {
					if (x * x + z * z + y * y * 2 <= 10 && p.get(tx + x, cy + y, tz + z) == null) {
						set(tx + x, cy + y, tz + z, s("jungle_leaves", "persistent=true"));
					}
				}
			}
		}
		set(tx, cy, tz, axis("jungle_log", "y"));
		// arbusto no terraco de baixo
		int bx = -sx * 14;
		int bz = between(-8, 8);
		set(bx, 4, bz, axis("jungle_log", "y"));
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (p.get(bx + x, 5, bz + z) == null) {
					set(bx + x, 5, bz + z, s("jungle_leaves", "persistent=true"));
				}
			}
		}
	}

	/** Trepadeiras descendo pelas faces de cada andar. */
	private void vines() {
		for (int k = 0; k < 4; k++) {
			for (int x = -HX[k]; x <= HX[k]; x++) {
				for (int z = -HZ[k]; z <= HZ[k]; z++) {
					if (Math.abs(x) != HX[k] && Math.abs(z) != HZ[k]) {
						continue;
					}
					int ox = Math.abs(x) == HX[k] ? Integer.signum(x) : 0;
					int oz = Math.abs(z) == HZ[k] && ox == 0 ? Integer.signum(z) : 0;
					if ((ox == 0 && oz == 0) || !chance(0.28)) {
						continue;
					}
					String face = ox > 0 ? W : ox < 0 ? E : oz > 0 ? N : S;
					int len = between(1, 4);
					for (int y = 4 * k + 2; y > 4 * k + 2 - len && y >= 0; y--) {
						if (p.get(x + ox, y, z + oz) != null) {
							break;
						}
						set(x + ox, y, z + oz, s("vine", face + "=true"));
					}
				}
			}
		}
	}
}
