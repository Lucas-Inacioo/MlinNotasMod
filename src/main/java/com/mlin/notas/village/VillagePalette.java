package com.mlin.notas.village;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Blocos de cada {@link VillageStyle} (ponte com o jogo). Paletas pensadas como as das casas medievais de referencia:
 * base de pedra, enxaimel de tronco escuro com reboco claro e telhado escuro; no deserto, arenito e telhado plano.
 */
public final class VillagePalette {
	public Block foundation, stone, stoneAlt, brick, stoneStairs, stoneSlab, stoneWall, paving, pavingAlt;
	public Block soil, subsoil, log, beam, planks, floor, plaster;
	public Block roofStairs, roofSlab, roofBlock, woodStairs, woodSlab, fence, fenceGate, door, trapdoor;
	public Block wool, woolAlt, carpet, carpetAlt, bed, hedge;
	public Block[] flowers;
	public Block plant;

	private VillagePalette() {
	}

	private static VillagePalette base() {
		VillagePalette p = new VillagePalette();
		p.foundation = Blocks.COBBLESTONE;
		p.stone = Blocks.COBBLESTONE;
		p.stoneAlt = Blocks.MOSSY_COBBLESTONE;
		p.brick = Blocks.STONE_BRICKS;
		p.stoneStairs = Blocks.STONE_BRICK_STAIRS;
		p.stoneSlab = Blocks.STONE_BRICK_SLAB;
		p.stoneWall = Blocks.COBBLESTONE_WALL;
		p.paving = Blocks.STONE_BRICKS;
		p.pavingAlt = Blocks.MOSSY_STONE_BRICKS;
		p.soil = Blocks.GRASS_BLOCK;
		p.subsoil = Blocks.DIRT;
		p.log = Blocks.SPRUCE_LOG;
		p.beam = Blocks.STRIPPED_SPRUCE_LOG;
		p.planks = Blocks.SPRUCE_PLANKS;
		p.floor = Blocks.SPRUCE_PLANKS;
		p.plaster = Blocks.DYED_TERRACOTTA.white();
		p.roofStairs = Blocks.DARK_OAK_STAIRS;
		p.roofSlab = Blocks.DARK_OAK_SLAB;
		p.roofBlock = Blocks.DARK_OAK_PLANKS;
		p.woodStairs = Blocks.SPRUCE_STAIRS;
		p.woodSlab = Blocks.SPRUCE_SLAB;
		p.fence = Blocks.SPRUCE_FENCE;
		p.fenceGate = Blocks.SPRUCE_FENCE_GATE;
		p.door = Blocks.SPRUCE_DOOR;
		p.trapdoor = Blocks.SPRUCE_TRAPDOOR;
		p.wool = Blocks.WOOL.red();
		p.woolAlt = Blocks.WOOL.white();
		p.carpet = Blocks.CARPET.brown();
		p.carpetAlt = Blocks.CARPET.red();
		p.bed = Blocks.BED.red();
		p.hedge = Blocks.OAK_LEAVES;
		p.flowers = new Block[] {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY};
		p.plant = Blocks.SHORT_GRASS;
		return p;
	}

	/**
	 * Paleta do estilo com o telhado da variante {@code variant} (cada construcao sorteia um, para a vila nao ficar
	 * toda da mesma cor): madeira escura, madeira clara ou telhas de pedra/tijolo, conforme o bioma.
	 */
	public static VillagePalette of(VillageStyle style, int variant) {
		VillagePalette p = of(style);
		Block[][] roofs = switch (style) {
			case PLAINS, SNOWY -> new Block[][] {{Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS},
					{Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS},
					{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES}};
			case TAIGA -> new Block[][] {{Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS},
					{Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS},
					{Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE}};
			case SAVANNA -> new Block[][] {{Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_PLANKS},
					{Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICKS},
					{Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS}};
			case DESERT -> new Block[][] {{Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE},
					{Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB, Blocks.CUT_SANDSTONE}};
		};
		Block[] roof = roofs[Math.floorMod(variant, roofs.length)];
		p.roofStairs = roof[0];
		p.roofSlab = roof[1];
		p.roofBlock = roof[2];
		return p;
	}

	public static VillagePalette of(VillageStyle style) {
		VillagePalette p = base();
		switch (style) {
			case PLAINS -> {
			}
			case TAIGA -> {
				p.plaster = Blocks.PACKED_MUD;
				p.roofStairs = Blocks.SPRUCE_STAIRS;
				p.roofSlab = Blocks.SPRUCE_SLAB;
				p.roofBlock = Blocks.SPRUCE_PLANKS;
				p.floor = Blocks.DARK_OAK_PLANKS;
				p.log = Blocks.DARK_OAK_LOG;
				p.beam = Blocks.STRIPPED_DARK_OAK_LOG;
				p.paving = Blocks.COBBLESTONE;
				p.pavingAlt = Blocks.MOSSY_COBBLESTONE;
				p.soil = Blocks.PODZOL;
				p.wool = Blocks.WOOL.green();
				p.carpet = Blocks.CARPET.green();
				p.bed = Blocks.BED.green();
				p.hedge = Blocks.SPRUCE_LEAVES;
				p.flowers = new Block[] {Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.CORNFLOWER, Blocks.BLUE_ORCHID};
				p.plant = Blocks.FERN;
			}
			case SNOWY -> {
				p.stone = Blocks.STONE_BRICKS;
				p.stoneAlt = Blocks.CRACKED_STONE_BRICKS;
				p.brick = Blocks.POLISHED_ANDESITE;
				p.paving = Blocks.STONE_BRICKS;
				p.pavingAlt = Blocks.POLISHED_ANDESITE;
				p.wool = Blocks.WOOL.blue();
				p.carpet = Blocks.CARPET.lightBlue();
				p.carpetAlt = Blocks.CARPET.blue();
				p.bed = Blocks.BED.blue();
				p.hedge = Blocks.SPRUCE_LEAVES;
				p.flowers = new Block[] {Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM};
				p.plant = Blocks.FERN;
			}
			case SAVANNA -> {
				p.stoneAlt = Blocks.GRANITE;
				p.brick = Blocks.POLISHED_GRANITE;
				p.stoneStairs = Blocks.POLISHED_GRANITE_STAIRS;
				p.stoneSlab = Blocks.POLISHED_GRANITE_SLAB;
				p.paving = Blocks.POLISHED_GRANITE;
				p.pavingAlt = Blocks.COBBLESTONE;
				p.log = Blocks.ACACIA_LOG;
				p.beam = Blocks.STRIPPED_ACACIA_LOG;
				p.planks = Blocks.ACACIA_PLANKS;
				p.floor = Blocks.ACACIA_PLANKS;
				p.roofStairs = Blocks.ACACIA_STAIRS;
				p.roofSlab = Blocks.ACACIA_SLAB;
				p.roofBlock = Blocks.ACACIA_PLANKS;
				p.woodStairs = Blocks.ACACIA_STAIRS;
				p.woodSlab = Blocks.ACACIA_SLAB;
				p.fence = Blocks.ACACIA_FENCE;
				p.fenceGate = Blocks.ACACIA_FENCE_GATE;
				p.door = Blocks.ACACIA_DOOR;
				p.trapdoor = Blocks.ACACIA_TRAPDOOR;
				p.wool = Blocks.WOOL.orange();
				p.woolAlt = Blocks.WOOL.yellow();
				p.carpet = Blocks.CARPET.orange();
				p.bed = Blocks.BED.orange();
				p.hedge = Blocks.ACACIA_LEAVES;
				p.flowers = new Block[] {Blocks.ORANGE_TULIP, Blocks.DANDELION, Blocks.RED_TULIP};
			}
			case DESERT -> {
				p.foundation = Blocks.SANDSTONE;
				p.stone = Blocks.SMOOTH_SANDSTONE;
				p.stoneAlt = Blocks.SANDSTONE;
				p.brick = Blocks.CUT_SANDSTONE;
				p.stoneStairs = Blocks.SANDSTONE_STAIRS;
				p.stoneSlab = Blocks.SMOOTH_SANDSTONE_SLAB;
				p.stoneWall = Blocks.SANDSTONE_WALL;
				p.paving = Blocks.SMOOTH_SANDSTONE;
				p.pavingAlt = Blocks.CUT_SANDSTONE;
				p.soil = Blocks.SAND;
				p.subsoil = Blocks.SANDSTONE;
				p.log = Blocks.STRIPPED_JUNGLE_LOG;
				p.beam = Blocks.STRIPPED_JUNGLE_LOG;
				p.planks = Blocks.JUNGLE_PLANKS;
				p.floor = Blocks.SMOOTH_SANDSTONE;
				p.plaster = Blocks.SMOOTH_SANDSTONE;
				p.roofStairs = Blocks.SMOOTH_SANDSTONE_STAIRS;
				p.roofSlab = Blocks.SMOOTH_SANDSTONE_SLAB;
				p.roofBlock = Blocks.SMOOTH_SANDSTONE;
				p.woodStairs = Blocks.JUNGLE_STAIRS;
				p.woodSlab = Blocks.JUNGLE_SLAB;
				p.fence = Blocks.JUNGLE_FENCE;
				p.fenceGate = Blocks.JUNGLE_FENCE_GATE;
				p.door = Blocks.JUNGLE_DOOR;
				p.trapdoor = Blocks.JUNGLE_TRAPDOOR;
				p.wool = Blocks.WOOL.cyan();
				p.woolAlt = Blocks.WOOL.yellow();
				p.carpet = Blocks.CARPET.cyan();
				p.carpetAlt = Blocks.CARPET.orange();
				p.bed = Blocks.BED.cyan();
				p.hedge = null;
				p.flowers = new Block[] {Blocks.DEAD_BUSH};
				p.plant = Blocks.DEAD_BUSH;
			}
		}
		applySeason(p);
		return p;
	}

	/** Flores da estacao do mundo (jardim e canteiros das obras novas; sem estacoes ou no deserto, as do estilo). */
	private static void applySeason(VillagePalette p) {
		com.mlin.notas.season.SeasonDate date = com.mlin.notas.season.SeasonState.current();
		if (date == null || p.hedge == null) {
			return;
		}
		p.flowers = switch (date.season()) {
			case SPRING -> new Block[] {Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.AZURE_BLUET, Blocks.LILY_OF_THE_VALLEY};
			case SUMMER -> new Block[] {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY};
			case AUTUMN -> new Block[] {Blocks.ORANGE_TULIP, Blocks.RED_TULIP, Blocks.POPPY, Blocks.DANDELION};
			case WINTER -> new Block[] {Blocks.BLUE_ORCHID, Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM};
		};
	}

	private static Direction direction(@Nullable Facing dir, Direction fallback) {
		if (dir == null) {
			return fallback;
		}
		return switch (dir) {
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case EAST -> Direction.EAST;
			case WEST -> Direction.WEST;
		};
	}

	private static Direction.Axis axis(@Nullable Facing dir) {
		if (dir == null) {
			return Direction.Axis.Y;
		}
		return dir.alongX() ? Direction.Axis.X : Direction.Axis.Z;
	}

	private static BlockState pane(Block block, @Nullable Facing dir) {
		boolean alongX = axis(dir) == Direction.Axis.X;
		return block.defaultBlockState().setValue(CrossCollisionBlock.EAST, alongX).setValue(CrossCollisionBlock.WEST, alongX)
				.setValue(CrossCollisionBlock.NORTH, !alongX).setValue(CrossCollisionBlock.SOUTH, !alongX);
	}

	/** Bloco de cada papel. {@code salt} varia detalhes (flor, idade da planta). Parte sem bloco devolve ar. */
	public BlockState state(VPart part, @Nullable Facing dir, int salt) {
		int pick = Math.floorMod(salt, 1009);
		Direction facing = direction(dir, Direction.SOUTH);
		return switch (part) {
			case AIR, DOOR_TOP, BED_HEAD -> Blocks.AIR.defaultBlockState();
			case FOUNDATION -> foundation.defaultBlockState();
			case STONE -> stone.defaultBlockState();
			case STONE_ALT -> stoneAlt.defaultBlockState();
			case BRICK -> brick.defaultBlockState();
			case STONE_STAIRS -> stoneStairs.defaultBlockState().setValue(StairBlock.FACING, facing);
			case STONE_STAIRS_TOP -> stoneStairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
			case STONE_SLAB -> stoneSlab.defaultBlockState();
			case STONE_SLAB_TOP -> stoneSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case STONE_WALL -> stoneWall.defaultBlockState();
			case PAVING -> paving.defaultBlockState();
			case PAVING_ALT -> pavingAlt.defaultBlockState();
			case PATH -> Blocks.DIRT_PATH.defaultBlockState();
			case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
			case SOIL -> soil.defaultBlockState();
			case FARMLAND -> Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
			case WATER -> Blocks.WATER.defaultBlockState();
			case FLOOR -> floor.defaultBlockState();
			case PLANKS -> planks.defaultBlockState();
			case PLASTER -> plaster.defaultBlockState();
			case LOG -> log.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis(dir));
			case BEAM -> beam.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis(dir));
			case ROOF -> roofStairs.defaultBlockState().setValue(StairBlock.FACING, facing);
			case ROOF_TOP -> roofStairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
			case ROOF_SLAB -> roofSlab.defaultBlockState();
			case ROOF_SLAB_TOP -> roofSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case ROOF_BLOCK -> roofBlock.defaultBlockState();
			case WOOD_STAIRS -> woodStairs.defaultBlockState().setValue(StairBlock.FACING, facing);
			case WOOD_SLAB -> woodSlab.defaultBlockState();
			case WOOD_SLAB_TOP -> woodSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case FENCE -> fence.defaultBlockState();
			case FENCE_GATE -> fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, facing);
			case DOOR -> door.defaultBlockState().setValue(DoorBlock.FACING, facing.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
			case WINDOW -> pane(Blocks.GLASS_PANE, dir);
			case SHUTTER -> trapdoor.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, true);
			case TRAPDOOR_TOP -> trapdoor.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP);
			case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
			case BED -> bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT);
			case CARPET -> carpet.defaultBlockState();
			case CARPET_ALT -> carpetAlt.defaultBlockState();
			case WOOL -> wool.defaultBlockState();
			case WOOL_ALT -> woolAlt.defaultBlockState();
			case CHEST -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
			case BARREL -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
			case CRAFTING -> Blocks.CRAFTING_TABLE.defaultBlockState();
			case FURNACE -> Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case SMOKER -> Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case COMPOSTER -> Blocks.COMPOSTER.defaultBlockState();
			case BLAST_FURNACE -> Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case SMITHING_TABLE -> Blocks.SMITHING_TABLE.defaultBlockState();
			case GRINDSTONE -> Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
					.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
			case LECTERN -> Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case BOOKSHELF -> Blocks.BOOKSHELF.defaultBlockState();
			case LOOM -> Blocks.LOOM.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case STONECUTTER -> Blocks.STONECUTTER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case CARTOGRAPHY -> Blocks.CARTOGRAPHY_TABLE.defaultBlockState();
			case FLETCHING -> Blocks.FLETCHING_TABLE.defaultBlockState();
			case BREWING -> Blocks.BREWING_STAND.defaultBlockState();
			case CAULDRON -> Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3);
			case LAVA_CAULDRON -> Blocks.LAVA_CAULDRON.defaultBlockState();
			case ANVIL -> Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case LANTERN -> Blocks.LANTERN.defaultBlockState();
			case LANTERN_HANGING -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
			case CAMPFIRE -> Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
			case FLOWER -> flowers[pick % flowers.length].defaultBlockState();
			case PLANT -> plant.defaultBlockState();
			case FLOWER_POT -> (pick % 2 == 0 ? Blocks.POTTED_RED_TULIP : Blocks.POTTED_FERN).defaultBlockState();
			case HEDGE -> hedge == null ? Blocks.AIR.defaultBlockState() : hedge.defaultBlockState().trySetValue(LeavesBlock.PERSISTENT, true);
			case HAY -> Blocks.HAY_BLOCK.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis(dir));
			case PUMPKIN -> Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			case CROP_WHEAT -> ((CropBlock) Blocks.WHEAT).getStateForAge(3 + pick % 5);
			case CROP_CARROT -> ((CropBlock) Blocks.CARROTS).getStateForAge(3 + pick % 5);
			case CROP_POTATO -> ((CropBlock) Blocks.POTATOES).getStateForAge(3 + pick % 5);
			case CROP_BEET -> Blocks.BEETROOTS.defaultBlockState().setValue(BeetrootBlock.AGE, 1 + pick % 3);
			case TURF -> (soil == Blocks.SAND || soil == Blocks.RED_SAND || soil == Blocks.SNOW_BLOCK ? soil : Blocks.GRASS_BLOCK).defaultBlockState();
			case EARTH -> subsoil.defaultBlockState();
			case ROCK -> (subsoil == Blocks.SANDSTONE ? Blocks.SANDSTONE : Blocks.STONE).defaultBlockState();
			case RETAIN -> (pick % 5 == 0 ? foundation : brick).defaultBlockState();
			case SAPLING -> sapling();
			case DRAIN -> Blocks.AIR.defaultBlockState();
			case TANNING_VAT -> CraftsRegistry.TANNING_VAT.defaultBlockState();
			case ENCHANTING_TABLE -> Blocks.ENCHANTING_TABLE.defaultBlockState();
			case BELL -> Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.FACING, facing)
					.setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR);
			case TARGET -> Blocks.TARGET.defaultBlockState();
			case WOOL_ACCENT -> (soil == Blocks.SAND ? Blocks.WOOL.yellow() : Blocks.WOOL.blue()).defaultBlockState();
			case CARPET_ACCENT -> (soil == Blocks.SAND ? Blocks.CARPET.yellow() : Blocks.CARPET.blue()).defaultBlockState();
			case ROUGH_STONE -> (soil == Blocks.SAND ? (pick % 2 == 0 ? Blocks.SANDSTONE : Blocks.CUT_SANDSTONE)
					: pick % 3 == 0 ? Blocks.ANDESITE : pick % 3 == 1 ? Blocks.STONE : Blocks.COBBLESTONE).defaultBlockState();
			case TOMBSTONE -> stoneWall.defaultBlockState();
			case STAKE -> fence.defaultBlockState();
		};
	}

	/** Muda da madeira do estilo (areia: nenhuma). */
	private BlockState sapling() {
		if (soil == Blocks.SAND || soil == Blocks.RED_SAND) {
			return Blocks.AIR.defaultBlockState();
		}
		Block sapling = log == Blocks.SPRUCE_LOG ? Blocks.SPRUCE_SAPLING : log == Blocks.ACACIA_LOG ? Blocks.ACACIA_SAPLING
				: log == Blocks.DARK_OAK_LOG ? Blocks.BIRCH_SAPLING : Blocks.OAK_SAPLING;
		return sapling.defaultBlockState();
	}
}
