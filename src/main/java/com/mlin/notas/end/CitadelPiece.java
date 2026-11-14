package com.mlin.notas.end;

import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Cidadela do End inteira numa peca so. Guarda so a semente e a origem; o plano ({@link CitadelPlanner}) e refeito
 * quando preciso (com cache) e indexado por chunk, entao cada {@link #postProcess} coloca apenas o que cai no chunk
 * sendo gerado. O terreno natural acima da ilha da cidadela e limpo (a cidadela fica num patio aberto).
 */
public class CitadelPiece extends StructurePiece {
	private static final Map<Long, CitadelPlan> PLANS = Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, CitadelPlan> eldest) {
			return size() > 6;
		}
	});

	private final long seed;
	private final BlockPos origin;
	private volatile @Nullable Map<Long, List<Long>> byChunk;
	private volatile @Nullable Map<Long, List<int[]>> clearColumns;

	public CitadelPiece(long seed, BlockPos origin) {
		super(ModStructures.CITADEL_PIECE, 0, box(planFor(seed), origin));
		this.seed = seed;
		this.origin = origin;
	}

	public CitadelPiece(CompoundTag tag) {
		super(ModStructures.CITADEL_PIECE, tag);
		this.seed = tag.getLongOr("Seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0));
	}

	private static BoundingBox box(CitadelPlan plan, BlockPos origin) {
		int[] b = plan.bounds();
		return new BoundingBox(origin.getX() + b[0], origin.getY() + b[1], origin.getZ() + b[2], origin.getX() + b[3], origin.getY() + b[4],
				origin.getZ() + b[5]);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Seed", seed);
		tag.putInt("OX", origin.getX());
		tag.putInt("OY", origin.getY());
		tag.putInt("OZ", origin.getZ());
	}

	public static CitadelPlan planFor(long seed) {
		CitadelPlan plan = PLANS.get(seed);
		if (plan == null) {
			plan = CitadelPlanner.plan(seed);
			PLANS.put(seed, plan);
		}
		return plan;
	}

	public CitadelPlan plan() {
		return planFor(seed);
	}

	public BlockPos origin() {
		return origin;
	}

	public BlockPos world(int x, int y, int z) {
		return origin.offset(x, y, z);
	}

	private static long chunkOf(int x, int z) {
		return ChunkPos.pack(x >> 4, z >> 4);
	}

	private Map<Long, List<Long>> byChunk() {
		if (byChunk == null) {
			Map<Long, List<Long>> out = new HashMap<>();
			Map<Long, List<int[]>> clear = new HashMap<>();
			CitadelPlan plan = plan();
			int maxY = plan.maxY();
			for (long k : plan.blocks().keySet()) {
				int x = CitadelPlan.keyX(k);
				int y = CitadelPlan.keyY(k);
				int z = CitadelPlan.keyZ(k);
				out.computeIfAbsent(chunkOf(origin.getX() + x, origin.getZ() + z), c -> new ArrayList<>()).add(k);
				// colunas da ilha principal: limpa o que houver de terreno acima do topo
				boolean islandTop = (y == 0 || y == CitadelPlanner.TERRACE_Y) && x * x + z * z <= (CitadelPlanner.RADIUS + 2) * (CitadelPlanner.RADIUS + 2)
						&& plan.get(x, y - 1, z) != null;
				if (islandTop) {
					clear.computeIfAbsent(chunkOf(origin.getX() + x, origin.getZ() + z), c -> new ArrayList<>()).add(new int[] {x, y + 1, z, maxY});
				}
			}
			clearColumns = clear;
			byChunk = out;
		}
		return byChunk;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		for (int cx = chunkBB.minX() >> 4; cx <= chunkBB.maxX() >> 4; cx++) {
			for (int cz = chunkBB.minZ() >> 4; cz <= chunkBB.maxZ() >> 4; cz++) {
				placeChunk(level, random, chunkBB, ChunkPos.pack(cx, cz));
			}
		}
		CitadelPlan plan = plan();
		for (CitadelPlan.Spawn spawn : plan.spawns()) {
			BlockPos at = origin.offset(spawn.x(), spawn.y(), spawn.z());
			if (chunkBB.isInside(at)) {
				spawn(level, spawn, at);
			}
		}
	}

	private void placeChunk(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, long chunk) {
		CitadelPlan plan = plan();
		Map<Long, List<Long>> index = byChunk();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<int[]> columns = clearColumns.get(chunk);
		if (columns != null) {
			for (int[] c : columns) {
				for (int y = c[1]; y <= c[3]; y++) {
					if (plan.get(c[0], y, c[2]) != null) {
						continue;
					}
					pos.set(origin.getX() + c[0], origin.getY() + y, origin.getZ() + c[2]);
					if (chunkBB.isInside(pos) && !level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		List<Long> keys = index.get(chunk);
		if (keys != null) {
			List<BlockPos> connect = new ArrayList<>();
			for (long k : keys) {
				pos.set(origin.getX() + CitadelPlan.keyX(k), origin.getY() + CitadelPlan.keyY(k), origin.getZ() + CitadelPlan.keyZ(k));
				if (!chunkBB.isInside(pos)) {
					continue;
				}
				BlockState state = VanillaRemakePiece.parse(plan.blocks().get(k));
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
				String table = plan.lootTables().get(k);
				if (table != null) {
					RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(),
							ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table)));
				}
				Block block = state.getBlock();
				if (block instanceof StairBlock || block instanceof WallBlock || block instanceof CrossCollisionBlock) {
					connect.add(pos.immutable());
				}
			}
			for (BlockPos p : connect) {
				BlockState placed = level.getBlockState(p);
				BlockState updated = Block.updateFromNeighbourShapes(placed, level, p);
				if (updated != placed && !updated.isAir()) {
					level.setBlock(p, updated, Block.UPDATE_CLIENTS);
				}
			}
		}
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
