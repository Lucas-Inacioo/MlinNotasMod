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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Uma ruina inteira numa peca so. Guarda tipo, estilo, semente e origem; o plano ({@link RuinPlanner}) e refeito
 * quando preciso e cada {@link #postProcess} coloca so o que cai no chunk sendo gerado. Os blocos da camada y=-1
 * descem ate o chao (alicerce), para nada ficar flutuando em terreno irregular.
 */
public class RuinPiece extends StructurePiece {
	/** Quanto o alicerce pode descer abaixo da camada y=-1. */
	public static final int FOUNDATION_DEPTH = 12;
	public static final ResourceKey<LootTable> SUPPLIES = ResourceKey.create(Registries.LOOT_TABLE, MlinNotas.id("chests/ruin_supplies"));
	public static final ResourceKey<LootTable> MINE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, MlinNotas.id("chests/ruin_mine"));

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
			BlockState state = state(palette, part, plan.facing(rx, ry, rz), plan.ironVein(), pos.getY() < 0);
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			if (part == RuinPart.CHEST || part == RuinPart.BARREL) {
				RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), kind == RuinKind.MINE ? MINE_LOOT : SUPPLIES);
			}
			if (ry == -1 && state.isSolid()) {
				fillBelow(level, pos.immutable(), palette.foundation());
			}
		}
	}

	/** Alicerce: desce ate achar chao firme (ar, plantas e liquidos sao substituidos). */
	private static void fillBelow(WorldGenLevel level, BlockPos top, BlockState foundation) {
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
			level.setBlock(pos, foundation, Block.UPDATE_CLIENTS);
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

	/** Bloco de cada papel do plano na paleta do estilo. */
	public static BlockState state(RuinPalette palette, RuinPart part, RuinPlan.@Nullable Dir dir, boolean ironVein, boolean deep) {
		return switch (part) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case FOUNDATION -> palette.foundation();
			case PAVING -> palette.paving();
			case GROUND -> palette.ground();
			case FLOOR, PLANKS -> palette.planks();
			case WALL -> palette.wall();
			case WALL_MOSSY -> palette.mossy();
			case WALL_CRACKED -> palette.cracked();
			case BEAM -> palette.log();
			case SLAB -> palette.slab();
			case WOOD_SLAB -> palette.woodSlab();
			case FENCE -> palette.fence();
			case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, direction(dir, Direction.SOUTH));
			case CHEST -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, direction(dir, Direction.SOUTH));
			case BARREL -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, direction(dir, Direction.UP));
			case CAMPFIRE -> Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
			case TENT -> palette.tent();
			case ALTAR -> palette.altar();
			case LECTERN -> Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, direction(dir, Direction.SOUTH));
			// provisorio: o Golem de Cobalto (chefe futuro) vai ser invocado aqui
			case BOSS_ALTAR -> Blocks.LODESTONE.defaultBlockState();
			case ORE -> ironVein
					? (deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).defaultBlockState()
					: (deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE).defaultBlockState();
			case TORCH -> Blocks.TORCH.defaultBlockState();
			case COBWEB -> Blocks.COBWEB.defaultBlockState();
			case WAYSTONE -> ModBlocks.WAYSTONE.defaultBlockState();
			case WAYSTONE_TOP -> ModBlocks.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.HALF,
					net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
		};
	}
}
