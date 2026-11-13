package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Cabana da bruxa refeita: casa de abeto sobre palafitas de carvalho que descem ate o fundo do brejo, varanda com
 * guarda-corpo e escada, porta aberta, telhado de duas aguas com beiral, cumeeira de lajes e chamine de pedra com
 * fogueira acesa (fumaca). Dentro: caldeirao com agua, suporte de pocoes, mesa de trabalho, barris com vasos de
 * cogumelo, prateleira de potes, bau de ingredientes ({@code mlinnotas:chests/witch_hut}) e lanterna de alma
 * pendurada. Fora: trepadeiras nas paredes, horta de cogumelos e aboboras, lama. A bruxa e o gato preto nascem com a
 * estrutura (e continuam pelos spawn_overrides vanilla). Frente em -z. Logica pura.
 *
 * <p>Referencias (so forma; nada copiado): a cabana vanilla (abeto sobre troncos de carvalho, caldeirao, vaso com
 * cogumelo) e as cabanas de bruxa de builds de pantano do Planet Minecraft (varanda, chamine torta, telhado com beiral
 * e trepadeiras).
 */
public final class SwampHutPlanner extends PlanBuilder {
	public static final String CHEST = "mlinnotas:chests/witch_hut";
	public static final String MARKER_CAULDRON = "cauldron";
	static final int FLOOR = 2;

	private SwampHutPlanner(Random random) {
		super(new VPlan(false), random);
	}

	public static VPlan plan(long seed, int variant) {
		SwampHutPlanner b = new SwampHutPlanner(new Random(seed * 3301L + 13));
		b.build();
		return b.p;
	}

	private void build() {
		stilts();
		platform();
		walls();
		roof();
		interior();
		porch();
		yard();
		p.removeFloating();
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -10);
	}

	private void stilts() {
		for (int x : new int[] {-3, 0, 3}) {
			for (int z : new int[] {-6, -3, 0, 4}) {
				p.set(x, -1, z, s("oak_log", "axis=y"), VPlan.FILL_DOWN);
				for (int y = 0; y < FLOOR; y++) {
					set(x, y, z, s("oak_log", "axis=y"));
				}
			}
		}
	}

	private void platform() {
		for (int x = -3; x <= 3; x++) {
			for (int z = -6; z <= 4; z++) {
				set(x, FLOOR, z, z < -3 ? "minecraft:spruce_planks" : (chance(0.85) ? "minecraft:spruce_planks" : "minecraft:oak_planks"));
			}
		}
	}

	private void walls() {
		int y0 = FLOOR + 1;
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 4; z++) {
				boolean edge = Math.abs(x) == 3 || z == -3 || z == 4;
				for (int y = y0; y <= y0 + 2; y++) {
					if (!edge) {
						set(x, y, z, VPlan.AIR);
					} else if (Math.abs(x) == 3 && (z == -3 || z == 4)) {
						set(x, y, z, s("oak_log", "axis=y"));
					} else {
						set(x, y, z, chance(0.9) ? "minecraft:spruce_planks" : "minecraft:mossy_cobblestone");
					}
				}
			}
		}
		// janelas de cerca
		set(-3, y0 + 1, 0, s("spruce_fence"));
		set(3, y0 + 1, 1, s("spruce_fence"));
		set(-2, y0 + 1, 4, s("spruce_fence"));
		// porta aberta
		set(0, y0, -3, s("spruce_door", "facing=north", "half=lower", "hinge=left", "open=true"));
		set(0, y0 + 1, -3, s("spruce_door", "facing=north", "half=upper", "hinge=left", "open=true"));
		// trepadeiras nas paredes de fora
		for (int z = -2; z <= 3; z++) {
			for (int side : new int[] {-1, 1}) {
				if (chance(0.35)) {
					int h = between(1, 3);
					for (int y = y0 + 3 - h; y <= y0 + 2; y++) {
						set(side * 4, y, z, s("vine", side < 0 ? "east=true" : "west=true"));
					}
				}
			}
		}
	}

	private void roof() {
		int base = FLOOR + 4;
		for (int k = 0; k <= 4; k++) {
			int y = base + k;
			int xl = -4 + k;
			int xr = 4 - k;
			for (int z = -4; z <= 5; z++) {
				if (xl < xr) {
					set(xl, y, z, s("spruce_stairs", "facing=east"));
					set(xr, y, z, s("spruce_stairs", "facing=west"));
					if (z == -3 || z == 4) {
						for (int x = xl + 1; x < xr; x++) {
							set(x, y, z, "minecraft:spruce_planks");
						}
					}
				} else {
					set(xl, y, z, s("spruce_slab"));
				}
			}
		}
		// beiral de baixo (degraus de cabeca para baixo nas pontas)
		for (int x = -3; x <= 3; x++) {
			set(x, base, -4, x == 0 ? s("spruce_slab", "type=top") : p.get(x, base, -4) == null ? s("spruce_slab", "type=top") : p.get(x, base, -4));
		}
		// chamine com fogueira acesa
		for (int y = FLOOR + 1; y <= base + 4; y++) {
			set(2, y, 3, chance(0.3) ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone");
		}
		set(2, base + 5, 3, s("campfire", "lit=true", "signal_fire=false"));
		// musgo no telhado
		for (int i = 0; i < 6; i++) {
			int z = between(-4, 5);
			int k = between(0, 2);
			set(-4 + k, FLOOR + 4 + k, z, "minecraft:moss_block");
		}
	}

	private void interior() {
		int y = FLOOR + 1;
		set(1, y, 3, s("water_cauldron", "level=" + between(1, 3)));
		p.mark(MARKER_CAULDRON, 1, y, 3);
		set(-2, y, 3, s("brewing_stand"));
		set(-2, y, -2, s("crafting_table"));
		set(-2, y, 1, s("barrel", "facing=up"));
		set(-2, y + 1, 1, s("potted_red_mushroom"));
		set(-2, y, 2, s("barrel", "facing=up"));
		set(-2, y + 1, 2, s("potted_brown_mushroom"));
		p.loot(2, y, 0, s("chest", "facing=west"), CHEST);
		set(2, y, -2, "minecraft:spruce_planks");
		set(2, y + 1, -2, s("decorated_pot", "facing=west"));
		set(2, y, 1, s("spruce_stairs", "facing=east"));
		set(0, y + 2, 1, s("soul_lantern", "hanging=true"));
		for (int cy = y + 3; cy <= FLOOR + 7; cy++) {
			set(0, cy, 1, s("iron_chain", "axis=y"));
		}
		p.entity("minecraft:witch", 0.5, y, 0.5, "");
	}

	private void porch() {
		int y = FLOOR + 1;
		for (int x = -3; x <= 3; x++) {
			for (int z = -6; z <= -4; z++) {
				boolean rail = Math.abs(x) == 3 || z == -6;
				if (rail && !(x == 0 && z == -6)) {
					set(x, y, z, Math.abs(x) == 3 && z == -6 ? s("oak_log", "axis=y") : s("spruce_fence"));
				}
			}
		}
		set(3, y + 1, -6, s("oak_log", "axis=y"));
		set(3, y + 2, -6, s("lantern"));
		set(-3, y + 1, -6, s("oak_log", "axis=y"));
		// escada ate o chao
		set(0, 1, -7, s("spruce_stairs", "facing=south"));
		set(0, 0, -7, s("oak_log", "axis=y"));
		set(0, 0, -8, s("spruce_stairs", "facing=south"));
		set(-2, y, -5, s("barrel", "facing=up"));
		p.entity("minecraft:cat", 1.5, y, -4.5, "black_cat");
	}

	private void yard() {
		for (int x = -8; x <= 8; x++) {
			for (int z = -10; z <= 9; z++) {
				if (p.get(x, 0, z) != null || p.get(x, -1, z) != null || (Math.abs(x) <= 1 && z < -6)) {
					continue;
				}
				double roll = r.nextDouble();
				if (roll < 0.06) {
					set(x, 0, z, chance(0.5) ? "minecraft:red_mushroom" : "minecraft:brown_mushroom");
				} else if (roll < 0.075) {
					set(x, 0, z, chance(0.7) ? s("pumpkin") : s("carved_pumpkin", "facing=" + pick(N, S, E, W)));
				} else if (roll < 0.2) {
					set(x, -1, z, "minecraft:mud");
				}
			}
		}
	}
}
