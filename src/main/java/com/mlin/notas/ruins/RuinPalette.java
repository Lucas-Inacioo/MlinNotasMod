package com.mlin.notas.ruins;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Blocos de cada {@link RuinStyle} (ponte com o jogo da paleta por bioma). A parede mistura cinco pedras
 * (principal, secundaria, musgosa, rachada e de destaque) no plano; aqui so se diz quais sao.
 */
public final class RuinPalette {
	/** Pedras: alicerce, principal, secundaria, musgosa, rachada, destaque, piso. */
	public Block foundation, wall, wallAlt, mossy, cracked, accent, paving;
	/** Lajes, degraus e muro de pedra. */
	public Block slab, stairs, stoneWall;
	/** Chao: terra batida, terra com grama (onde nascem plantas) e o que enche por baixo dela. */
	public Block ground, soil, subsoil;
	/** Madeira. */
	public Block log, planks, woodSlab, woodStairs, fence, trapdoor;
	/** La das barracas (duas cores) e tapete da lona. */
	public Block tent, tentAlt, carpet;
	/** Vegetacao: folhas, planta rasteira, flor, vaso com planta; {@code null} = nao ha (deserto). */
	public Block leaves, plant, flower, pottedPlant, moss, vine;
	public Block altar;

	private RuinPalette() {
	}

	private RuinPalette stone(Block foundation, Block wall, Block wallAlt, Block mossy, Block cracked, Block accent, Block paving) {
		this.foundation = foundation;
		this.wall = wall;
		this.wallAlt = wallAlt;
		this.mossy = mossy;
		this.cracked = cracked;
		this.accent = accent;
		this.paving = paving;
		this.altar = accent;
		return this;
	}

	private RuinPalette shapes(Block slab, Block stairs, Block stoneWall) {
		this.slab = slab;
		this.stairs = stairs;
		this.stoneWall = stoneWall;
		return this;
	}

	private RuinPalette earth(Block ground, Block soil, Block subsoil) {
		this.ground = ground;
		this.soil = soil;
		this.subsoil = subsoil;
		return this;
	}

	private RuinPalette wood(Block log, Block planks, Block woodSlab, Block woodStairs, Block fence, Block trapdoor) {
		this.log = log;
		this.planks = planks;
		this.woodSlab = woodSlab;
		this.woodStairs = woodStairs;
		this.fence = fence;
		this.trapdoor = trapdoor;
		return this;
	}

	private RuinPalette cloth(Block tent, Block tentAlt, Block carpet) {
		this.tent = tent;
		this.tentAlt = tentAlt;
		this.carpet = carpet;
		return this;
	}

	private RuinPalette green(Block leaves, Block plant, Block flower, Block pottedPlant, Block moss, Block vine) {
		this.leaves = leaves;
		this.plant = plant;
		this.flower = flower;
		this.pottedPlant = pottedPlant;
		this.moss = moss;
		this.vine = vine;
		return this;
	}

	public static RuinPalette of(RuinStyle style) {
		RuinPalette p = new RuinPalette();
		return switch (style) {
			case MOSSY -> p.stone(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
							Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS)
					.shapes(Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_COBBLESTONE_WALL)
					.earth(Blocks.DIRT_PATH, Blocks.GRASS_BLOCK, Blocks.DIRT)
					.wood(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_TRAPDOOR)
					.cloth(Blocks.WOOL.white(), Blocks.WOOL.red(), Blocks.CARPET.white())
					.green(Blocks.OAK_LEAVES, Blocks.SHORT_GRASS, Blocks.POPPY, Blocks.POTTED_RED_TULIP, Blocks.MOSS_CARPET, Blocks.VINE);
			case SPRUCE -> p.stone(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.STONE, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS,
							Blocks.STONE_BRICKS, Blocks.STONE_BRICKS)
					.shapes(Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_WALL)
					.earth(Blocks.DIRT_PATH, Blocks.PODZOL, Blocks.DIRT)
					.wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_TRAPDOOR)
					.cloth(Blocks.WOOL.brown(), Blocks.WOOL.green(), Blocks.CARPET.brown())
					.green(Blocks.SPRUCE_LEAVES, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.POTTED_FERN, Blocks.MOSS_CARPET, Blocks.VINE);
			case SANDSTONE -> p.stone(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE,
							Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE)
					.shapes(Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_WALL)
					.earth(Blocks.COARSE_DIRT, Blocks.SAND, Blocks.SANDSTONE)
					.wood(Blocks.STRIPPED_OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_TRAPDOOR)
					.cloth(Blocks.WOOL.yellow(), Blocks.WOOL.orange(), Blocks.CARPET.yellow())
					.green(null, Blocks.DEAD_BUSH, Blocks.DEAD_BUSH, Blocks.POTTED_DEAD_BUSH, null, null);
			case RED_SANDSTONE -> p.stone(Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
							Blocks.TERRACOTTA, Blocks.CHISELED_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE)
					.shapes(Blocks.RED_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_WALL)
					.earth(Blocks.COARSE_DIRT, Blocks.RED_SAND, Blocks.RED_SANDSTONE)
					.wood(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_FENCE,
							Blocks.DARK_OAK_TRAPDOOR)
					.cloth(Blocks.WOOL.orange(), Blocks.WOOL.white(), Blocks.CARPET.orange())
					.green(null, Blocks.DEAD_BUSH, Blocks.DEAD_BUSH, Blocks.POTTED_DEAD_BUSH, null, null);
			case ACACIA -> p.stone(Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.TERRACOTTA,
							Blocks.POLISHED_GRANITE, Blocks.STONE_BRICKS)
					.shapes(Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_WALL)
					.earth(Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT)
					.wood(Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_SLAB, Blocks.ACACIA_STAIRS, Blocks.ACACIA_FENCE, Blocks.ACACIA_TRAPDOOR)
					.cloth(Blocks.WOOL.orange(), Blocks.WOOL.yellow(), Blocks.CARPET.orange())
					.green(Blocks.ACACIA_LEAVES, Blocks.SHORT_GRASS, Blocks.ORANGE_TULIP, Blocks.POTTED_DEAD_BUSH, Blocks.MOSS_CARPET, Blocks.VINE);
			case DARK -> p.stone(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.ANDESITE, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
							Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS)
					.shapes(Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_WALL)
					.earth(Blocks.DIRT_PATH, Blocks.GRASS_BLOCK, Blocks.DIRT)
					.wood(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_FENCE,
							Blocks.DARK_OAK_TRAPDOOR)
					.cloth(Blocks.WOOL.gray(), Blocks.WOOL.black(), Blocks.CARPET.gray())
					.green(Blocks.DARK_OAK_LEAVES, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.POTTED_FERN, Blocks.MOSS_CARPET, Blocks.VINE);
			case DEEPSLATE -> p.stone(Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.MOSSY_COBBLESTONE,
							Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES)
					.shapes(Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.COBBLED_DEEPSLATE_WALL)
					.earth(Blocks.GRAVEL, Blocks.GRASS_BLOCK, Blocks.DIRT)
					.wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_TRAPDOOR)
					.cloth(Blocks.WOOL.brown(), Blocks.WOOL.lightGray(), Blocks.CARPET.brown())
					.green(Blocks.SPRUCE_LEAVES, Blocks.SHORT_GRASS, Blocks.CORNFLOWER, Blocks.POTTED_FERN, Blocks.MOSS_CARPET, Blocks.VINE);
		};
	}
}
