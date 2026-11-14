package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Iglu refeito: cupula de neve de 11 blocos com janelas de gelo, tunel de entrada com arco, cupula menor de deposito
 * ao lado, chao de tapete branco com tapete cinza, cama, fornalha, mesa de trabalho, barril e lanterna pendurada. O
 * alcapao fica escondido embaixo do tapete (como no vanilla) e da no poco com escada de mao ate o laboratorio do
 * porao: tijolos de pedra com musgo, suporte de pocoes, caldeirao com agua, bau {@code igloo_chest}, estante, teias,
 * lanternas e duas celas com grade, uma com o aldeao zumbi e a outra com o clerigo. Fora: neve no chao, boneco de neve
 * e pilha de lenha. Entrada em -z. Logica pura.
 *
 * <p>Referencias (so forma; nada copiado): o datapack "New Igloos" do Planet Minecraft (iglu maior com tunel e
 * cupulas ligadas), o "Small Igloo House" do Planet Minecraft e os schematics "igloo top/bottom" do Minecraft Maps
 * (alcapao sob o tapete e laboratorio com celas).
 */
public final class IglooPlanner extends PlanBuilder {
	public static final String CHEST = "minecraft:chests/igloo_chest";
	public static final String MARKER_TRAPDOOR = "trapdoor";
	public static final String MARKER_LAB = "lab";
	static final int R = 5;
	static final int H = 5;

	private final int basement;

	private IglooPlanner(Random random) {
		super(new VPlan(false), random);
		this.basement = -between(8, 12);
	}

	public static VPlan plan(long seed, int variant) {
		IglooPlanner b = new IglooPlanner(new Random(seed * 977L + 31));
		b.build();
		return b.p;
	}

	private static boolean inEllipsoid(double x, double y, double z, double rad, double h) {
		return (x * x + z * z) / (rad * rad) + (y * y) / (h * h) <= 1.0;
	}

	private void dome(int cx, int cz, double rad, double h) {
		int ri = (int) Math.ceil(rad);
		int hi = (int) Math.ceil(h);
		for (int x = -ri; x <= ri; x++) {
			for (int z = -ri; z <= ri; z++) {
				set(cx + x, -1, cz + z, "minecraft:snow_block");
				for (int y = 0; y <= hi; y++) {
					if (!inEllipsoid(x, y, z, rad + 0.3, h + 0.3)) {
						continue;
					}
					boolean inner = inEllipsoid(x, y, z, rad - 0.7, h - 0.7);
					if (inner) {
						if (p.get(cx + x, y, cz + z) == null || !VPlan.id(p.get(cx + x, y, cz + z)).equals("snow_block")) {
							set(cx + x, y, cz + z, VPlan.AIR);
						}
					} else if (p.get(cx + x, y, cz + z) == null) {
						set(cx + x, y, cz + z, "minecraft:snow_block");
					}
				}
			}
		}
	}

	private void build() {
		dome(0, 0, R, H);
		// cupula do deposito, ligada por uma porta
		int side = chance(0.5) ? 1 : -1;
		int sx = side * 7;
		dome(sx, 1, 3, 3);
		for (int y = 0; y <= 1; y++) {
			for (int x = Math.min(side * 4, side * 5); x <= Math.max(side * 4, side * 5); x++) {
				set(x, y, 1, VPlan.AIR);
			}
		}
		// tunel de entrada
		for (int z = -R - 3; z <= -R + 1; z++) {
			for (int x = -1; x <= 1; x++) {
				for (int y = 0; y <= 2; y++) {
					boolean wall = Math.abs(x) == 1 || y == 2;
					String here = p.get(x, y, z);
					if (!wall) {
						set(x, y, z, VPlan.AIR);
					} else if (here == null || !BlockTraits.isAir(here)) {
						set(x, y, z, "minecraft:snow_block");
					}
				}
				if (p.get(x, -1, z) == null) {
					set(x, -1, z, "minecraft:snow_block");
				}
			}
		}
		set(0, 3, -R - 1, s("snow", "layers=2"));
		// janelas de gelo
		set(-R + 1, 2, 0, "minecraft:ice");
		set(R - 1, 2, 0, "minecraft:ice");
		set(0, 2, R - 1, "minecraft:ice");
		furnish(side);
		storage(sx);
		lab();
		outside(side);
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -R - 5);
	}

	private void furnish(int side) {
		for (int x = -R + 1; x <= R - 1; x++) {
			for (int z = -R + 1; z <= R - 1; z++) {
				if (isAirAt(x, 0, z)) {
					set(x, 0, z, Math.abs(x) <= 1 && Math.abs(z - 1) <= 1 ? s("light_gray_carpet") : s("white_carpet"));
				}
			}
		}
		// cama, fornalha, mesa, barril e lanterna
		int bx = -side * 3;
		set(bx, 0, 2, s("red_bed", "facing=south", "part=foot"));
		set(bx, 0, 3, s("red_bed", "facing=south", "part=head"));
		set(side * 3, 0, 2, s("furnace", "facing=" + (side > 0 ? W : E)));
		set(side * 3, 0, 3, s("crafting_table"));
		set(-side * 3, 0, -1, s("barrel", "facing=up"));
		set(0, H - 1, 0, s("lantern", "hanging=true"));
		set(side * 2, 0, 4, s("spruce_trapdoor", "facing=north", "half=bottom"));
		// alcapao escondido sob o tapete
		set(0, -1, 2, s("spruce_trapdoor", "facing=south", "half=top"));
		set(0, 0, 2, s("white_carpet"));
		p.mark(MARKER_TRAPDOOR, 0, -1, 2);
	}

	private void storage(int sx) {
		set(sx, 0, 2, s("barrel", "facing=up"));
		set(sx + 1, 0, 1, s("spruce_log", "axis=y"));
		set(sx - 1, 0, 2, "minecraft:packed_ice");
		set(sx, 0, 0, s("white_carpet"));
		set(sx, 2, 1, s("lantern", "hanging=true"));
	}

	private void lab() {
		int b = basement;
		// poco com escada de mao
		for (int y = b + 3; y <= -2; y++) {
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				set(d[0], y, 2 + d[1], "minecraft:stone_bricks");
			}
			set(0, y, 2, s("ladder", "facing=north"));
		}
		// sala
		for (int x = -5; x <= 5; x++) {
			for (int z = -1; z <= 7; z++) {
				for (int y = b - 1; y <= b + 3; y++) {
					boolean shell = Math.abs(x) == 5 || z == -1 || z == 7 || y == b - 1 || y == b + 3;
					if (shell) {
						set(x, y, z, y == b - 1 ? (chance(0.3) ? "minecraft:polished_andesite" : "minecraft:stone_bricks")
								: chance(0.35) ? "minecraft:mossy_stone_bricks" : chance(0.15) ? "minecraft:cracked_stone_bricks" : "minecraft:stone_bricks");
					} else {
						set(x, y, z, VPlan.AIR);
					}
				}
			}
		}
		for (int y = b; y <= b + 3; y++) {
			set(0, y, 2, s("ladder", "facing=north"));
			if (y < b + 3) {
				set(0, y, 3, "minecraft:stone_bricks");
			}
		}
		// celas com grade
		for (int x = -4; x <= 4; x++) {
			if (Math.abs(x) == 2) {
				for (int z = 5; z <= 6; z++) {
					for (int y = b; y <= b + 2; y++) {
						set(x, y, z, "minecraft:stone_bricks");
					}
				}
			}
			if (Math.abs(x) >= 3) {
				for (int y = b; y <= b + 2; y++) {
					set(x, y, 4, y == b + 2 ? "minecraft:stone_bricks" : s("iron_bars"));
				}
			}
		}
		set(-4, b + 2, 4, "minecraft:stone_bricks");
		p.entity("minecraft:villager", -3.0, b, 5.5, "cleric");
		p.entity("minecraft:zombie_villager", 3.5, b, 5.5, "");
		// bancada do laboratorio
		set(0, b, 6, s("brewing_stand"));
		set(-1, b, 6, s("water_cauldron", "level=3"));
		p.loot(1, b, 6, s("chest", "facing=north"), CHEST);
		p.mark(MARKER_LAB, 0, b, 6);
		set(-4, b, 0, "minecraft:bookshelf");
		set(-4, b + 1, 0, "minecraft:bookshelf");
		set(-4, b, 1, s("lectern", "facing=east"));
		set(4, b, 0, s("barrel", "facing=up"));
		set(4, b + 1, 0, s("flower_pot"));
		set(3, b, 0, s("cobweb"));
		set(-4, b + 2, 2, s("cobweb"));
		set(2, b + 2, 1, s("lantern", "hanging=true"));
		set(-2, b + 2, 1, s("lantern", "hanging=true"));
		set(0, b + 2, 5, s("lantern", "hanging=true"));
	}

	private void outside(int side) {
		for (int x = -R - 6; x <= R + 6; x++) {
			for (int z = -R - 6; z <= R + 6; z++) {
				if (p.get(x, 0, z) == null && p.get(x, -1, z) == null && chance(0.7)) {
					set(x, 0, z, s("snow", "layers=" + between(1, 2)));
				}
			}
		}
		int mx = -side * 5;
		int mz = -R - 3;
		set(mx, 0, mz, "minecraft:snow_block");
		set(mx, 1, mz, "minecraft:snow_block");
		set(mx, 2, mz, s("carved_pumpkin", "facing=north"));
		int wx = side * 4;
		for (int z = -R - 2; z <= -R; z++) {
			set(wx, 0, z, s("spruce_log", "axis=z"));
			if (z != -R) {
				set(wx, 1, z, s("spruce_log", "axis=z"));
			}
		}
		// fogueira apagada na frente
		set(0, 0, -R - 7, s("campfire", "lit=false"));
	}
}
