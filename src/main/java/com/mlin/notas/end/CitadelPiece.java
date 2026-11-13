package com.mlin.notas.end;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A Cidadela do End inteira numa peca so. Guarda so a semente e a origem; o plano ({@link CitadelPlanner}) e
 * refeito quando preciso e cada chamada de {@link #postProcess} coloca apenas o que cai no chunk sendo gerado.
 */
public class CitadelPiece extends StructurePiece {
	/** Fundo maximo da ilha e altura maxima da cidadela (folga incluida). */
	private static final int DEPTH = 22;
	private static final int HEIGHT = 32;

	private final long seed;
	private final BlockPos origin;
	private @Nullable CitadelPlan plan;

	public CitadelPiece(long seed, BlockPos origin) {
		super(ModStructures.CITADEL_PIECE, 0, box(origin));
		this.seed = seed;
		this.origin = origin;
	}

	public CitadelPiece(CompoundTag tag) {
		super(ModStructures.CITADEL_PIECE, tag);
		this.seed = tag.getLongOr("Seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0));
	}

	private static BoundingBox box(BlockPos origin) {
		int r = CitadelPlanner.RADIUS + 1;
		return new BoundingBox(origin.getX() - r, origin.getY() - DEPTH, origin.getZ() - r, origin.getX() + r, origin.getY() + HEIGHT, origin.getZ() + r);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Seed", seed);
		tag.putInt("OX", origin.getX());
		tag.putInt("OY", origin.getY());
		tag.putInt("OZ", origin.getZ());
	}

	public CitadelPlan plan() {
		if (plan == null) {
			plan = CitadelPlanner.plan(seed);
		}
		return plan;
	}

	public BlockPos origin() {
		return origin;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		CitadelPlan plan = plan();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		// limpa o terreno natural acima da ilha (a cidadela fica num patio aberto)
		for (int x = chunkBB.minX(); x <= chunkBB.maxX(); x++) {
			for (int z = chunkBB.minZ(); z <= chunkBB.maxZ(); z++) {
				int rx = x - origin.getX();
				int rz = z - origin.getZ();
				if (plan.get(rx, 0, rz) == null) {
					continue;
				}
				for (int ry = 1; ry <= plan.maxY(); ry++) {
					pos.set(x, origin.getY() + ry, z);
					if (plan.get(rx, ry, rz) == null && chunkBB.isInside(pos) && !level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		for (Map.Entry<Long, CitadelPart> entry : plan.blocks().entrySet()) {
			long key = entry.getKey();
			pos.set(origin.getX() + CitadelPlan.keyX(key), origin.getY() + CitadelPlan.keyY(key), origin.getZ() + CitadelPlan.keyZ(key));
			if (!chunkBB.isInside(pos)) {
				continue;
			}
			level.setBlock(pos, state(entry.getValue()), Block.UPDATE_CLIENTS);
			if (entry.getValue() == CitadelPart.CHEST) {
				RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), BuiltInLootTables.END_CITY_TREASURE);
			}
		}

		for (CitadelPlan.Spawn spawn : plan.spawns()) {
			BlockPos at = origin.offset(spawn.x(), spawn.y(), spawn.z());
			if (chunkBB.isInside(at)) {
				spawn(level, spawn, at);
			}
		}
	}

	/** Bloco de cada papel do plano. */
	public static BlockState state(CitadelPart part) {
		return switch (part) {
			case AIR -> Blocks.AIR.defaultBlockState();
			case ISLAND -> Blocks.END_STONE.defaultBlockState();
			case ISLAND_TOP -> ModBlocks.END_MOSS.defaultBlockState();
			case PATH -> Blocks.END_STONE_BRICKS.defaultBlockState();
			case FLOOR, WALL -> Blocks.PURPUR_BLOCK.defaultBlockState();
			case PILLAR -> Blocks.PURPUR_PILLAR.defaultBlockState();
			case TRIM -> ModBlocks.VOID_BRICKS.defaultBlockState();
			case WINDOW -> Blocks.STAINED_GLASS.magenta().defaultBlockState();
			case LAMP -> ModBlocks.PURPUR_LAMP.defaultBlockState();
			case ELEVATOR -> ModBlocks.PURPUR_ELEVATOR.defaultBlockState();
			case RAILING -> Blocks.PURPUR_SLAB.defaultBlockState();
			case BOOKSHELF -> Blocks.BOOKSHELF.defaultBlockState();
			case BREWING_STAND -> Blocks.BREWING_STAND.defaultBlockState();
			case DRAGON_HEAD -> Blocks.DRAGON_HEAD.defaultBlockState();
			case END_ROD -> Blocks.END_ROD.defaultBlockState();
			case PLANT -> ModBlocks.VOID_GRASS.defaultBlockState();
			case CHEST -> Blocks.CHEST.defaultBlockState();
		};
	}

	private static void spawn(WorldGenLevel level, CitadelPlan.Spawn spawn, BlockPos at) {
		switch (spawn.kind()) {
			case SHULKER -> {
				Shulker shulker = EntityTypes.SHULKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (shulker != null) {
					shulker.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
					level.addFreshEntity(shulker);
				}
			}
			case SENTINEL -> {
				Mob sentinel = ModEntities.PURPUR_SENTINEL.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (sentinel != null) {
					sentinel.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
					sentinel.setPersistenceRequired();
					level.addFreshEntity(sentinel);
				}
			}
			case ELYTRA_FRAME -> {
				Direction facing = Direction.getApproximateNearest(spawn.dirX(), 0, spawn.dirZ());
				ItemFrame frame = new ItemFrame(level.getLevel(), at, facing);
				frame.setItem(new ItemStack(Items.ELYTRA), false);
				level.addFreshEntity(frame);
			}
		}
	}
}
