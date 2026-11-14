package com.mlin.notas.village.works;

import com.mlin.notas.village.VillageStyle;

/**
 * Blocos das obras publicas por estilo da vila (ids de bloco em texto, logica pura). Planicie e savana usam pedra
 * comum (savana com acacia), taiga e neve com abeto, deserto com arenito.
 */
public record Mats(String wood, String log, String leaves, String planks, String fence, String slab, String stairs, String stairsStone,
		String gravel, String cobble, String bricks, String smooth, String accent, String wall, String lawn, String trapdoor) {
	public static Mats of(VillageStyle style) {
		return switch (style) {
			case DESERT -> new Mats("acacia", "minecraft:acacia_log", "minecraft:acacia_leaves", "minecraft:acacia_planks", "minecraft:acacia_fence",
					"minecraft:acacia_slab", "minecraft:acacia_stairs", "minecraft:sandstone_stairs", "minecraft:coarse_dirt", "minecraft:sandstone",
					"minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone", "minecraft:sandstone_wall",
					"minecraft:coarse_dirt", "minecraft:acacia_trapdoor");
			case SAVANNA -> new Mats("acacia", "minecraft:acacia_log", "minecraft:acacia_leaves", "minecraft:acacia_planks", "minecraft:acacia_fence",
					"minecraft:acacia_slab", "minecraft:acacia_stairs", "minecraft:stone_brick_stairs", "minecraft:gravel", "minecraft:cobblestone",
					"minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_wall",
					"minecraft:grass_block", "minecraft:acacia_trapdoor");
			case TAIGA, SNOWY -> new Mats("spruce", "minecraft:spruce_log", "minecraft:spruce_leaves", "minecraft:spruce_planks", "minecraft:spruce_fence",
					"minecraft:spruce_slab", "minecraft:spruce_stairs", "minecraft:stone_brick_stairs", "minecraft:gravel", "minecraft:cobblestone",
					"minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_wall",
					"minecraft:grass_block", "minecraft:spruce_trapdoor");
			default -> new Mats("oak", "minecraft:oak_log", "minecraft:oak_leaves", "minecraft:oak_planks", "minecraft:oak_fence", "minecraft:oak_slab",
					"minecraft:oak_stairs", "minecraft:stone_brick_stairs", "minecraft:gravel", "minecraft:cobblestone", "minecraft:stone_bricks",
					"minecraft:polished_andesite", "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_wall", "minecraft:grass_block",
					"minecraft:oak_trapdoor");
		};
	}

	/** Folha que nao some sozinha. */
	public String leavesKeep() {
		return leaves + "[persistent=true]";
	}

	public String stairs(String facing, boolean top) {
		return stairs + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
	}

	public String stoneStairs(String facing, boolean top) {
		return stairsStone + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
	}

	/** Laje de pedra do estilo (tijolo de pedra; corte de arenito no deserto). */
	public String brickSlab() {
		return (wood.equals("acacia") && bricks.contains("sandstone") ? "minecraft:cut_sandstone_slab" : "minecraft:stone_brick_slab") + "[type=bottom]";
	}

	public String slabBottom() {
		return slab + "[type=bottom]";
	}

	public static String lantern(boolean hanging) {
		return "minecraft:lantern[hanging=" + hanging + "]";
	}

	/** Cerca em pe. */
	public String post() {
		return fence;
	}

	/** Estado de sinal de estacao: placa em pe ({@code rotation} 0..15). */
	public String sign(int rotation) {
		return "minecraft:" + wood + "_sign[rotation=" + Math.floorMod(rotation, 16) + "]";
	}

	public String wallSign(String facing) {
		return "minecraft:" + wood + "_wall_sign[facing=" + facing + "]";
	}
}
