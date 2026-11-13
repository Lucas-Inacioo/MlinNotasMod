package com.mlin.notas.dungeon;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.ruins.RuinPlan.Dir;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Blocos de cada tema: a peca ({@link DungeonPiece}) troca cada {@link DungeonPart} do plano pelo bloco daqui. As
 * paredes misturam 4 variantes por peso (pedra principal, secundaria, musgo/variacao e rachada).
 */
public final class DungeonPalette {
	final Block wall;
	final Block wallAlt;
	final Block wallMossy;
	final Block wallCracked;
	final Block floor;
	final Block floorAlt;
	final Block floorTrim;
	final Block pillar;
	final Block accent;
	final Block log;
	final Block planks;
	final Block stairs;
	final Block slab;
	final Block stoneWall;
	final Block glow;
	final Block treasure;
	final Block sand;
	final Block suspicious;
	final Block[] decor;
	final Block[] plants;
	final Block carpet;
	final Block lantern;
	final Block torch;
	final Block skull;
	final Block workstation;
	final Block puzzlePlate;
	final Block button;

	private DungeonPalette(Block wall, Block wallAlt, Block wallMossy, Block wallCracked, Block floor, Block floorAlt, Block floorTrim,
			Block pillar, Block accent, Block log, Block planks, Block stairs, Block slab, Block stoneWall, Block glow, Block treasure,
			Block sand, Block suspicious, Block[] decor, Block[] plants, Block carpet, Block lantern, Block torch, Block skull,
			Block workstation, Block puzzlePlate, Block button) {
		this.wall = wall;
		this.wallAlt = wallAlt;
		this.wallMossy = wallMossy;
		this.wallCracked = wallCracked;
		this.floor = floor;
		this.floorAlt = floorAlt;
		this.floorTrim = floorTrim;
		this.pillar = pillar;
		this.accent = accent;
		this.log = log;
		this.planks = planks;
		this.stairs = stairs;
		this.slab = slab;
		this.stoneWall = stoneWall;
		this.glow = glow;
		this.treasure = treasure;
		this.sand = sand;
		this.suspicious = suspicious;
		this.decor = decor;
		this.plants = plants;
		this.carpet = carpet;
		this.lantern = lantern;
		this.torch = torch;
		this.skull = skull;
		this.workstation = workstation;
		this.puzzlePlate = puzzlePlate;
		this.button = button;
	}

	public static DungeonPalette of(DungeonTheme theme) {
		return switch (theme) {
			case CRYPT -> new DungeonPalette(Blocks.STONE_BRICKS, Blocks.TUFF_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
					Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF_BRICKS, Blocks.CHISELED_STONE_BRICKS,
					Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL,
					Blocks.PEARLESCENT_FROGLIGHT, Blocks.GOLD_BLOCK, Blocks.COARSE_DIRT, Blocks.SUSPICIOUS_GRAVEL,
					new Block[] {Blocks.BONE_BLOCK}, new Block[] {Blocks.DEAD_BUSH, Blocks.COBWEB}, Blocks.CARPET.red(), Blocks.SOUL_LANTERN,
					Blocks.SOUL_WALL_TORCH, Blocks.SKELETON_SKULL, Blocks.LECTERN, Blocks.DARK_OAK_PRESSURE_PLATE, Blocks.STONE_BUTTON);
			case DWARF -> new DungeonPalette(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.CRACKED_DEEPSLATE_BRICKS,
					Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BASALT, Blocks.CHISELED_DEEPSLATE,
					Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL,
					Blocks.SHROOMLIGHT, Blocks.RAW_GOLD_BLOCK, Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL,
					new Block[] {Blocks.COAL_BLOCK, Blocks.RAW_IRON_BLOCK}, new Block[] {Blocks.BROWN_MUSHROOM}, Blocks.CARPET.red(), Blocks.LANTERN,
					Blocks.WALL_TORCH, Blocks.SKELETON_SKULL, Blocks.BLAST_FURNACE, Blocks.SPRUCE_PRESSURE_PLATE, Blocks.POLISHED_BLACKSTONE_BUTTON);
			case DROWNED -> new DungeonPalette(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE,
					Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SMOOTH_SANDSTONE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN,
					Blocks.DARK_PRISMARINE, Blocks.MANGROVE_PLANKS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB, Blocks.PRISMARINE_WALL,
					Blocks.SEA_LANTERN, Blocks.GOLD_BLOCK, Blocks.SAND, Blocks.SUSPICIOUS_SAND,
					new Block[] {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK},
					new Block[] {Blocks.DEAD_TUBE_CORAL_FAN, Blocks.DEAD_BRAIN_CORAL_FAN, Blocks.DEAD_HORN_CORAL, Blocks.SEA_PICKLE},
					Blocks.CARPET.cyan(), Blocks.LANTERN, Blocks.WALL_TORCH, Blocks.SKELETON_SKULL, Blocks.SMITHING_TABLE, Blocks.MANGROVE_PRESSURE_PLATE,
					Blocks.STONE_BUTTON);
			case SAND -> new DungeonPalette(Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE,
					Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.DYED_TERRACOTTA.orange(), Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
					Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL,
					Blocks.OCHRE_FROGLIGHT, Blocks.GOLD_BLOCK, Blocks.SAND, Blocks.SUSPICIOUS_SAND,
					new Block[] {Blocks.DYED_TERRACOTTA.blue(), Blocks.CHISELED_RED_SANDSTONE}, new Block[] {Blocks.DEAD_BUSH},
					Blocks.CARPET.orange(), Blocks.LANTERN, Blocks.WALL_TORCH, Blocks.SKELETON_SKULL, Blocks.SMITHING_TABLE, Blocks.ACACIA_PRESSURE_PLATE,
					Blocks.STONE_BUTTON);
		};
	}

	static Direction direction(@Nullable Dir dir, Direction fallback) {
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

	private static Direction.Axis axis(@Nullable Dir dir) {
		if (dir == null) {
			return Direction.Axis.Y;
		}
		return dir == Dir.EAST || dir == Dir.WEST ? Direction.Axis.X : Direction.Axis.Z;
	}

	private static BlockState pane(Block block, @Nullable Dir dir) {
		boolean alongX = dir == null || axis(dir) == Direction.Axis.X;
		return block.defaultBlockState().setValue(CrossCollisionBlock.EAST, alongX).setValue(CrossCollisionBlock.WEST, alongX)
				.setValue(CrossCollisionBlock.NORTH, !alongX).setValue(CrossCollisionBlock.SOUTH, !alongX);
	}

	private static BlockState dry(Block block) {
		return block.defaultBlockState().trySetValue(BlockStateProperties.WATERLOGGED, false);
	}

	/** Mistura das paredes: 55% principal, 20% secundaria, 15% variacao, 10% rachada. */
	private Block wallMix(int pick) {
		int p = pick % 100;
		return p < 55 ? wall : p < 75 ? wallAlt : p < 90 ? wallMossy : wallCracked;
	}

	/** Bloco de um papel do plano. {@code salt} varia detalhes (mistura da parede, coral, minerio). */
	public BlockState state(DungeonPart part, @Nullable Dir dir, int salt) {
		int pick = Math.floorMod(salt, 1009);
		Direction facing = direction(dir, Direction.NORTH);
		return switch (part) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case WALL -> wallMix(pick).defaultBlockState();
			case WALL_ALT -> wallAlt.defaultBlockState();
			case WALL_MOSSY -> wallMossy.defaultBlockState();
			case WALL_CRACKED -> wallCracked.defaultBlockState();
			case FLOOR -> (pick % 9 == 0 ? wallCracked : floor).defaultBlockState();
			case FLOOR_ALT -> floorAlt.defaultBlockState();
			case FLOOR_TRIM -> floorTrim.defaultBlockState();
			case PILLAR -> pillar.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
			case ACCENT -> accent.defaultBlockState();
			case BEAM -> log.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis(dir));
			case PLANKS -> planks.defaultBlockState();
			case STAIRS -> stairs.defaultBlockState().setValue(StairBlock.FACING, facing);
			case STAIRS_TOP -> stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP);
			case SLAB -> slab.defaultBlockState();
			case SLAB_TOP -> slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case STONE_WALL -> stoneWall.defaultBlockState();
			case GLOW -> glow.defaultBlockState();
			case TREASURE -> treasure.defaultBlockState();
			case ORE -> switch (pick % 7) {
				case 0, 1, 2 -> Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
				case 3, 4 -> Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
				case 5 -> ModBlocks.DEEPSLATE_COBALT_ORE.defaultBlockState();
				default -> Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
			};
			case SAND -> sand.defaultBlockState();
			case SUSPICIOUS -> suspicious.defaultBlockState();
			case DECOR_BLOCK -> decor[pick % decor.length].defaultBlockState();
			case BOOKSHELF -> (pick % 6 == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF).defaultBlockState()
					.trySetValue(HorizontalDirectionalBlock.FACING, direction(dir, Direction.SOUTH));
			case BARREL -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, pick % 3 == 0 ? facing : Direction.UP);
			case CHEST -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, direction(dir, Direction.SOUTH));
			case VAULT -> Blocks.VAULT.defaultBlockState().setValue(VaultBlock.FACING, direction(dir, Direction.SOUTH));
			case SPAWNER -> Blocks.SPAWNER.defaultBlockState();
			case GATE -> DungeonRegistry.GATE.defaultBlockState();
			case IRON_DOOR -> Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
					.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
			case IRON_DOOR_TOP -> Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
					.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
			case LEVER -> Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, facing);
			case BUTTON -> button.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, facing);
			case TRAP_PLATE -> Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE.defaultBlockState();
			case TRAP_FLOOR -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			case DISPENSER -> Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing);
			case PUZZLE_PLATE -> puzzlePlate.defaultBlockState();
			case REDSTONE -> Blocks.REDSTONE_WIRE.defaultBlockState();
			case IRON_BARS -> pane(Blocks.IRON_BARS, dir);
			case WATER -> Blocks.WATER.defaultBlockState();
			case LAVA -> Blocks.LAVA.defaultBlockState();
			case WALL_TORCH -> torch.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
			case LANTERN -> lantern.defaultBlockState();
			case LANTERN_HANGING -> lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
			case CHAIN -> Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
			case CANDLE -> Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + pick % 4).setValue(CandleBlock.LIT, true);
			case SKULL -> skull.defaultBlockState().setValue(SkullBlock.ROTATION, pick % 16);
			case COBWEB -> Blocks.COBWEB.defaultBlockState();
			case CARPET -> carpet.defaultBlockState();
			case PLANT -> dry(plants[pick % plants.length]);
			case DECORATED_POT -> Blocks.DECORATED_POT.defaultBlockState().trySetValue(HorizontalDirectionalBlock.FACING, facing);
			case ANVIL -> (pick % 2 == 0 ? Blocks.CHIPPED_ANVIL : Blocks.ANVIL).defaultBlockState().setValue(AnvilBlock.FACING, facing.getClockWise());
			case WORKSTATION -> workstation.defaultBlockState().trySetValue(HorizontalDirectionalBlock.FACING, facing);
			case CAMPFIRE -> Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
			case LECTERN -> Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, direction(dir, Direction.SOUTH));
		};
	}
}
