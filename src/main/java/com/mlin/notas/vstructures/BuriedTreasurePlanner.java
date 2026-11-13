package com.mlin.notas.vstructures;

import java.util.Random;

/**
 * Tesouro enterrado refeito, de proposito pequeno (o structure set vanilla gera muitos): o bau
 * {@code buried_treasure} fica exatamente na origem, 3 blocos abaixo da superficie, no bloco (9, 9) do chunk para onde
 * o mapa do tesouro aponta, envolto em areia sobre arenito. Na superficie um "X" de cascalho marca o lugar, com um
 * moledro de pedras (com cranio as vezes), um tronco de madeira trazido pelo mar e uma pa velha (cerca) espetada.
 * Logica pura.
 *
 * <p>Referencia: o "X marca o lugar" dos mapas do tesouro vanilla e os moledros de praia de builds de ilhas piratas.
 */
public final class BuriedTreasurePlanner extends PlanBuilder {
	public static final String TREASURE = "minecraft:chests/buried_treasure";
	public static final int CHEST_Y = -3;

	private BuriedTreasurePlanner(Random random) {
		super(new VPlan(false), random);
	}

	public static VPlan plan(long seed, int variant) {
		BuriedTreasurePlanner b = new BuriedTreasurePlanner(new Random(seed * 131L + 7));
		b.build();
		return b.p;
	}

	private void build() {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = CHEST_Y - 1; y <= -2; y++) {
					set(x, y, z, y == CHEST_Y - 1 ? "minecraft:sandstone" : "minecraft:sand");
				}
			}
		}
		p.loot(0, CHEST_Y, 0, s("chest", "facing=" + pick(N, S, E, W)), TREASURE);
		String mark = chance(0.5) ? "minecraft:gravel" : "minecraft:coarse_dirt";
		for (int d = -2; d <= 2; d++) {
			set(d, -1, d, mark);
			set(d, -1, -d, mark);
		}
		// moledro
		int cx = pick(-4, 4);
		int cz = between(-3, 3);
		set(cx, 0, cz, "minecraft:cobblestone");
		set(cx, 1, cz, "minecraft:mossy_cobblestone");
		if (chance(0.5)) {
			set(cx, 2, cz, s("cobblestone_wall"));
			set(cx, 3, cz, s("skeleton_skull", "rotation=" + r.nextInt(16)));
		} else {
			set(cx, 2, cz, s("cobblestone_slab"));
		}
		set(cx + 1, 0, cz, s("cobblestone_slab"));
		// tronco trazido pelo mar
		int dz = cz > 0 ? -4 : 4;
		boolean alongX = chance(0.5);
		for (int i = 0; i < between(2, 4); i++) {
			set(alongX ? -cx / 2 + i : -cx, 0, alongX ? dz : dz + i, s("stripped_oak_log", "axis=" + (alongX ? "x" : "z")));
		}
		// pa espetada
		set(1, 0, -2, s("spruce_fence"));
		p.mark(VPlan.MARKER_ENTRANCE, 0, 0, -3);
	}
}
