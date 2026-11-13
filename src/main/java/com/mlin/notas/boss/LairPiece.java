package com.mlin.notas.boss;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.util.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Um covil de chefe inteiro numa peca so: guarda tipo, semente e origem e refaz o plano ({@link LairPlanner}) quando
 * preciso. Cada {@link #postProcess} coloca so o que cai no chunk sendo gerado; a camada FOUNDATION desce ate o chao
 * (ate {@link #FOUNDATION_DEPTH} blocos). Na Forja, o Colosso Igneo nasce adormecido no trono.
 */
public class LairPiece extends StructurePiece {
	public static final int FOUNDATION_DEPTH = 16;

	private final LairKind kind;
	private final long seed;
	private final BlockPos origin;
	private @Nullable LairPlan plan;

	public LairPiece(LairKind kind, long seed, BlockPos origin) {
		this(kind, seed, origin, LairPlanner.plan(kind, seed));
	}

	private LairPiece(LairKind kind, long seed, BlockPos origin, LairPlan plan) {
		super(BossRegistry.LAIR_PIECE, 0, box(plan, origin));
		this.kind = kind;
		this.seed = seed;
		this.origin = origin;
		this.plan = plan;
	}

	public LairPiece(CompoundTag tag) {
		super(BossRegistry.LAIR_PIECE, tag);
		this.kind = LairKind.byName(tag.getStringOr("Kind", "cobalt_shrine"));
		this.seed = tag.getLongOr("Seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0));
	}

	private static BoundingBox box(LairPlan plan, BlockPos origin) {
		int[] b = plan.bounds();
		return new BoundingBox(origin.getX() + b[0], origin.getY() + b[1] - FOUNDATION_DEPTH, origin.getZ() + b[2],
				origin.getX() + b[3], origin.getY() + b[4], origin.getZ() + b[5]);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Kind", kind.id());
		tag.putLong("Seed", seed);
		tag.putInt("OX", origin.getX());
		tag.putInt("OY", origin.getY());
		tag.putInt("OZ", origin.getZ());
	}

	public LairKind kind() {
		return kind;
	}

	public BlockPos origin() {
		return origin;
	}

	public LairPlan plan() {
		if (plan == null) {
			plan = LairPlanner.plan(kind, seed);
		}
		return plan;
	}

	/** Posicao no mundo de um marcador do plano. */
	public BlockPos at(String marker) {
		Pos p = plan().marker(marker);
		return origin.offset(p.x(), p.y(), p.z());
	}

	public static ResourceKey<LootTable> loot(LairKind kind) {
		return ResourceKey.create(Registries.LOOT_TABLE, MlinNotas.id("chests/" + kind.id()));
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
			ChunkPos chunkPos, BlockPos referencePos) {
		LairPlan plan = plan();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Map.Entry<Long, LairPart> entry : plan.blocks().entrySet()) {
			long key = entry.getKey();
			pos.set(origin.getX() + LairPlan.keyX(key), origin.getY() + LairPlan.keyY(key), origin.getZ() + LairPlan.keyZ(key));
			if (!chunkBB.isInside(pos)) {
				continue;
			}
			LairPart part = entry.getValue();
			BlockState state = state(kind, part);
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			if (part == LairPart.FOUNDATION) {
				BlockPos.MutableBlockPos below = pos.mutable();
				for (int i = 0; i < FOUNDATION_DEPTH; i++) {
					below.move(0, -1, 0);
					BlockState there = level.getBlockState(below);
					if (!chunkBB.isInside(below) || (!there.isAir() && there.getFluidState().isEmpty() && !there.canBeReplaced())) {
						break;
					}
					level.setBlock(below, state, Block.UPDATE_CLIENTS);
				}
			} else if (part == LairPart.CHEST) {
				RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), loot(kind));
			}
		}
		if (kind == LairKind.COLOSSUS_FORGE) {
			BlockPos boss = at(LairPlan.MARKER_BOSS);
			if (chunkBB.isInside(boss)) {
				IgneousColossus colossus = BossRegistry.IGNEOUS_COLOSSUS.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (colossus != null) {
					colossus.snapTo(boss.getX() + 0.5, boss.getY(), boss.getZ() + 0.5, 180.0F, 0.0F);
					colossus.setDormant(true);
					colossus.setPersistenceRequired();
					level.addFreshEntity(colossus);
				}
			}
		}
	}

	/** Bloco de cada papel na paleta do covil. */
	public static BlockState state(LairKind kind, LairPart part) {
		if (part == LairPart.AIR) {
			return Blocks.AIR.defaultBlockState();
		}
		if (part == LairPart.CHEST) {
			return Blocks.CHEST.defaultBlockState();
		}
		return switch (kind) {
			case COBALT_SHRINE -> switch (part) {
				case FOUNDATION -> Blocks.COBBLESTONE.defaultBlockState();
				case FLOOR -> Blocks.STONE_BRICKS.defaultBlockState();
				case FLOOR_ACCENT -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
				case FLOOR_CRACKED -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
				case PATH, RUBBLE -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
				case DECOR -> Blocks.MOSS_BLOCK.defaultBlockState();
				case DAIS -> Blocks.POLISHED_ANDESITE.defaultBlockState();
				case SLAB -> Blocks.STONE_BRICK_SLAB.defaultBlockState();
				case PILLAR -> Blocks.STONE_BRICKS.defaultBlockState();
				case PILLAR_CAP -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
				case WALL_ACCENT -> ModBlocks.COBALT_ORE.defaultBlockState();
				case WALL_LOW -> Blocks.STONE_BRICK_WALL.defaultBlockState();
				case LIGHT -> Blocks.LANTERN.defaultBlockState();
				case ALTAR -> BossRegistry.COBALT_ALTAR.defaultBlockState();
				default -> Blocks.STONE_BRICKS.defaultBlockState();
			};
			case ABYSSAL_SANCTUM -> switch (part) {
				case FOUNDATION -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
				case FLOOR, CEILING -> Blocks.DEEPSLATE_TILES.defaultBlockState();
				case FLOOR_ACCENT, DAIS, PILLAR -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
				case FLOOR_CRACKED -> Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
				case DECOR -> Blocks.SCULK.defaultBlockState();
				case WALL -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
				case WALL_ACCENT, PILLAR_CAP -> Blocks.CHISELED_DEEPSLATE.defaultBlockState();
				case WALL_CRACKED -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
				case SLAB -> Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
				case FRAME -> Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
				case LIGHT -> Blocks.SOUL_LANTERN.defaultBlockState();
				case HANGING_LIGHT -> Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
				case CHAIN -> Blocks.IRON_CHAIN.defaultBlockState();
				case CANDLE -> Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3);
				case SENSOR -> Blocks.SCULK_SENSOR.defaultBlockState();
				case SHRIEKER -> Blocks.SCULK_SHRIEKER.defaultBlockState();
				case ALTAR -> BossRegistry.ABYSSAL_ALTAR.defaultBlockState();
				default -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
			};
			case COLOSSUS_FORGE -> switch (part) {
				case FOUNDATION -> Blocks.BLACKSTONE.defaultBlockState();
				case FLOOR, WALL -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				case FLOOR_ACCENT -> Blocks.SMOOTH_BASALT.defaultBlockState();
				case FLOOR_CRACKED -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				case DECOR, PILLAR_CAP -> Blocks.MAGMA_BLOCK.defaultBlockState();
				case LIQUID -> Blocks.LAVA.defaultBlockState();
				case WALL_ACCENT -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
				case WALL_LOW -> Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
				case PILLAR -> Blocks.POLISHED_BASALT.defaultBlockState();
				case RUBBLE -> Blocks.BASALT.defaultBlockState();
				case DAIS -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
				case SLAB -> Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState();
				case CAMPFIRE -> Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
				case SKULL -> Blocks.SKELETON_SKULL.defaultBlockState();
				default -> Blocks.BLACKSTONE.defaultBlockState();
			};
		};
	}
}
