package com.mlin.notas.ruins;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.waystone.WaystoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Uma ruina inteira numa peca so. Guarda tipo, estilo, semente e origem; o plano ({@link RuinPlanner}) e refeito
 * quando preciso e cada {@link #postProcess} coloca so o que cai no chunk sendo gerado. Os blocos da camada y=-1
 * descem ate o chao (alicerce de pedra, ou terra sob o chao de terra), para nada ficar flutuando em terreno irregular.
 * Cercas, vidros, grades, muros e degraus sao ligados aos vizinhos numa segunda passada.
 */
public class RuinPiece extends StructurePiece {
	/** Quanto o alicerce pode descer abaixo da camada y=-1. */
	public static final int FOUNDATION_DEPTH = 12;
	public static final ResourceKey<LootTable> SUPPLIES = ResourceKey.create(Registries.LOOT_TABLE, MlinNotas.id("chests/ruin_supplies"));
	public static final ResourceKey<LootTable> MINE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, MlinNotas.id("chests/ruin_mine"));
	private static final Block[] GLASS = {Blocks.STAINED_GLASS_PANE.red(), Blocks.STAINED_GLASS_PANE.blue(), Blocks.STAINED_GLASS_PANE.yellow(),
			Blocks.STAINED_GLASS_PANE.purple(), Blocks.STAINED_GLASS_PANE.green(), Blocks.STAINED_GLASS_PANE.lightBlue()};

	private final RuinKind kind;
	private final RuinStyle style;
	private final long seed;
	private final BlockPos origin;
	private @Nullable RuinPlan plan;

	public RuinPiece(RuinKind kind, RuinStyle style, long seed, BlockPos origin) {
		this(kind, style, seed, origin, RuinPlanner.plan(kind, seed));
	}

	private RuinPiece(RuinKind kind, RuinStyle style, long seed, BlockPos origin, RuinPlan plan) {
		super(ModStructures.RUIN_PIECE, 0, box(plan, origin));
		this.kind = kind;
		this.style = style;
		this.seed = seed;
		this.origin = origin;
		this.plan = plan;
	}

	public RuinPiece(CompoundTag tag) {
		super(ModStructures.RUIN_PIECE, tag);
		this.kind = RuinKind.byName(tag.getStringOr("Kind", "watchtower"));
		this.style = RuinStyle.byOrdinal(tag.getIntOr("Style", 0));
		this.seed = tag.getLongOr("Seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0));
	}

	private static BoundingBox box(RuinPlan plan, BlockPos origin) {
		int[] b = plan.bounds();
		return new BoundingBox(origin.getX() + b[0], origin.getY() + b[1] - FOUNDATION_DEPTH, origin.getZ() + b[2],
				origin.getX() + b[3], origin.getY() + b[4], origin.getZ() + b[5]);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Kind", kind.serializedName());
		tag.putInt("Style", style.ordinal());
		tag.putLong("Seed", seed);
		tag.putInt("OX", origin.getX());
		tag.putInt("OY", origin.getY());
		tag.putInt("OZ", origin.getZ());
	}

	public RuinPlan plan() {
		if (plan == null) {
			plan = RuinPlanner.plan(kind, seed);
		}
		return plan;
	}

	public RuinKind kind() {
		return kind;
	}

	public RuinStyle style() {
		return style;
	}

	public BlockPos origin() {
		return origin;
	}

	/** Posicao no mundo de um ponto do plano. */
	public BlockPos world(com.mlin.notas.util.Pos p) {
		return origin.offset(p.x(), p.y(), p.z());
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		RuinPlan plan = plan();
		RuinPalette palette = RuinPalette.of(style);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<BlockPos> connect = new ArrayList<>();
		for (Map.Entry<Long, RuinPart> entry : plan.blocks().entrySet()) {
			long key = entry.getKey();
			int rx = RuinPlan.keyX(key);
			int ry = RuinPlan.keyY(key);
			int rz = RuinPlan.keyZ(key);
			pos.set(origin.getX() + rx, origin.getY() + ry, origin.getZ() + rz);
			if (!chunkBB.isInside(pos)) {
				continue;
			}
			RuinPart part = entry.getValue();
			if (part == RuinPart.WAYSTONE_TOP) {
				continue;
			}
			if (part == RuinPart.WAYSTONE) {
				WaystoneBlock.placeAt(level, ModBlocks.WAYSTONE.defaultBlockState(), pos.immutable(), Block.UPDATE_CLIENTS);
				continue;
			}
			BlockState state = state(palette, part, plan.facing(rx, ry, rz), plan.ironVein(), pos.getY() < 0, rx * 31 + ry * 7 + rz * 13);
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			if (part == RuinPart.CHEST || part == RuinPart.BARREL) {
				RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), kind == RuinKind.MINE ? MINE_LOOT : SUPPLIES);
			}
			if (part == RuinPart.MINECART) {
				Minecart cart = EntityTypes.MINECART.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (cart != null) {
					cart.setPos(pos.getX() + 0.5, pos.getY() + 0.0625, pos.getZ() + 0.5);
					level.addFreshEntity(cart);
				}
			}
			if (connects(state)) {
				connect.add(pos.immutable());
			}
			if (ry == -1 && state.isSolid()) {
				boolean earth = part == RuinPart.SOIL || part == RuinPart.GROUND || part == RuinPart.GRAVEL;
				fillBelow(level, pos.immutable(), earth ? palette.subsoil.defaultBlockState() : palette.foundation.defaultBlockState());
			}
		}
		// cercas, vidros, grades, muros e degraus se ligam aos vizinhos ja colocados
		for (BlockPos p : connect) {
			BlockState placed = level.getBlockState(p);
			BlockState updated = Block.updateFromNeighbourShapes(placed, level, p);
			if (updated != placed && !updated.isAir()) {
				level.setBlock(p, updated, Block.UPDATE_CLIENTS);
			}
		}
	}

	private static boolean connects(BlockState state) {
		Block block = state.getBlock();
		return block instanceof FenceBlock || block instanceof IronBarsBlock || block instanceof WallBlock || block instanceof StairBlock;
	}

	/** Alicerce: desce ate achar chao firme (ar, plantas e liquidos sao substituidos). */
	private static void fillBelow(WorldGenLevel level, BlockPos top, BlockState fill) {
		BlockPos.MutableBlockPos pos = top.mutable();
		for (int i = 0; i < FOUNDATION_DEPTH; i++) {
			pos.move(Direction.DOWN);
			if (pos.getY() < level.getMinY()) {
				return;
			}
			BlockState here = level.getBlockState(pos);
			if (!here.isAir() && !here.canBeReplaced() && here.getFluidState().isEmpty()) {
				return;
			}
			level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
		}
	}

	private static Direction direction(RuinPlan.@Nullable Dir dir, Direction fallback) {
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

	private static Direction.Axis axis(RuinPlan.@Nullable Dir dir) {
		if (dir == null) {
			return Direction.Axis.Y;
		}
		return dir == RuinPlan.Dir.EAST || dir == RuinPlan.Dir.WEST ? Direction.Axis.X : Direction.Axis.Z;
	}

	private static BlockState or(@Nullable Block block) {
		return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
	}

	/** Painel ou grade ligado dos dois lados ao longo do eixo da parede. */
	private static BlockState pane(Block block, RuinPlan.@Nullable Dir dir) {
		boolean alongX = axis(dir) == Direction.Axis.X;
		return block.defaultBlockState().setValue(CrossCollisionBlock.EAST, alongX).setValue(CrossCollisionBlock.WEST, alongX)
				.setValue(CrossCollisionBlock.NORTH, !alongX).setValue(CrossCollisionBlock.SOUTH, !alongX);
	}

	/**
	 * Bloco de cada papel do plano na paleta do estilo. {@code salt} varia detalhes que nao estao no plano (cor do
	 * vitral, numero de velas).
	 */
	public static BlockState state(RuinPalette palette, RuinPart part, RuinPlan.@Nullable Dir dir, boolean ironVein, boolean deep, int salt) {
		int pick = Math.floorMod(salt, 1009);
		return switch (part) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case FOUNDATION -> palette.foundation.defaultBlockState();
			case PAVING -> palette.paving.defaultBlockState();
			case GROUND -> palette.ground.defaultBlockState();
			case SOIL -> palette.soil.defaultBlockState();
			case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
			case FLOOR, PLANKS -> palette.planks.defaultBlockState();
			case WALL -> palette.wall.defaultBlockState();
			case WALL_ALT -> palette.wallAlt.defaultBlockState();
			case WALL_MOSSY -> palette.mossy.defaultBlockState();
			case WALL_CRACKED -> palette.cracked.defaultBlockState();
			case ACCENT -> palette.accent.defaultBlockState();
			case BEAM -> palette.log.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis(dir));
			case SLAB -> palette.slab.defaultBlockState();
			case SLAB_TOP -> palette.slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case STAIRS -> palette.stairs.defaultBlockState().setValue(StairBlock.FACING, direction(dir, Direction.NORTH));
			case STAIRS_TOP -> palette.stairs.defaultBlockState().setValue(StairBlock.FACING, direction(dir, Direction.NORTH))
					.setValue(StairBlock.HALF, Half.TOP);
			case STONE_WALL -> palette.stoneWall.defaultBlockState();
			case WOOD_SLAB -> palette.woodSlab.defaultBlockState();
			case WOOD_SLAB_TOP -> palette.woodSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
			case WOOD_STAIRS -> palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, direction(dir, Direction.NORTH));
			case WOOD_STAIRS_TOP -> palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, direction(dir, Direction.NORTH))
					.setValue(StairBlock.HALF, Half.TOP);
			case FENCE -> palette.fence.defaultBlockState();
			case TRAPDOOR -> palette.trapdoor.defaultBlockState().setValue(TrapDoorBlock.FACING, direction(dir, Direction.NORTH))
					.setValue(TrapDoorBlock.OPEN, true);
			case TRAPDOOR_TOP -> palette.trapdoor.defaultBlockState().setValue(TrapDoorBlock.FACING, direction(dir, Direction.NORTH))
					.setValue(TrapDoorBlock.HALF, Half.TOP);
			case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, direction(dir, Direction.SOUTH));
			case CHEST -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, direction(dir, Direction.SOUTH));
			case BARREL, CRATE -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, direction(dir, Direction.UP));
			case CAMPFIRE -> Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
			case TENT -> palette.tent.defaultBlockState();
			case TENT_ALT -> palette.tentAlt.defaultBlockState();
			case CARPET -> palette.carpet.defaultBlockState();
			case RUNNER -> Blocks.CARPET.red().defaultBlockState();
			case ALTAR -> palette.altar.defaultBlockState();
			case LECTERN -> Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, direction(dir, Direction.SOUTH));
			// provisorio: o Golem de Cobalto (chefe futuro) vai ser invocado aqui
			case BOSS_ALTAR -> com.mlin.notas.boss.BossRegistry.COBALT_ALTAR.defaultBlockState();
			case ORE -> ironVein
					? (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).defaultBlockState()
					: (deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE).defaultBlockState();
			case TORCH -> Blocks.TORCH.defaultBlockState();
			case LANTERN -> Blocks.LANTERN.defaultBlockState();
			case LANTERN_HANGING -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
			case CHAIN -> Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis(dir));
			case COBWEB -> Blocks.COBWEB.defaultBlockState();
			case VINE -> palette.vine == null ? Blocks.AIR.defaultBlockState()
					: palette.vine.defaultBlockState().setValue(VineBlock.getPropertyForFace(direction(dir, Direction.NORTH)), true);
			case LEAVES -> palette.leaves == null ? Blocks.AIR.defaultBlockState()
					: palette.leaves.defaultBlockState().trySetValue(LeavesBlock.PERSISTENT, true);
			case MOSS_CARPET -> or(palette.moss);
			case PLANT -> palette.plant.defaultBlockState();
			case FLOWER -> palette.flower.defaultBlockState();
			case FLOWER_POT -> pick % 3 == 0 ? Blocks.FLOWER_POT.defaultBlockState() : palette.pottedPlant.defaultBlockState();
			case CANDLE -> Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + pick % 3).setValue(CandleBlock.LIT, false);
			case BUTTON -> Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR)
					.setValue(ButtonBlock.FACING, direction(dir, Direction.NORTH));
			case HAY -> Blocks.HAY_BLOCK.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis(dir));
			case CAULDRON -> Blocks.CAULDRON.defaultBlockState();
			case CRAFTING_TABLE -> Blocks.CRAFTING_TABLE.defaultBlockState();
			case ANVIL -> Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, direction(dir, Direction.NORTH));
			case DECORATED_POT -> Blocks.DECORATED_POT.defaultBlockState();
			case GLASS_PANE -> pane(GLASS[pick % GLASS.length], dir);
			case IRON_BARS -> pane(Blocks.IRON_BARS, dir);
			case BELL -> Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR)
					.setValue(BellBlock.FACING, direction(dir, Direction.NORTH));
			case WATER -> Blocks.WATER.defaultBlockState();
			case RAIL, MINECART -> Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,
					axis(dir) == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
			case RAIL_UP -> Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, switch (direction(dir, Direction.NORTH)) {
				case SOUTH -> RailShape.ASCENDING_SOUTH;
				case EAST -> RailShape.ASCENDING_EAST;
				case WEST -> RailShape.ASCENDING_WEST;
				default -> RailShape.ASCENDING_NORTH;
			});
			case WAYSTONE -> ModBlocks.WAYSTONE.defaultBlockState();
			case WAYSTONE_TOP -> ModBlocks.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.HALF, DoubleBlockHalf.UPPER);
		};
	}
}
