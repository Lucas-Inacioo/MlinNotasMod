package com.mlin.notas.dungeon;

import com.mlin.notas.util.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uma masmorra inteira numa peca so. Guarda tema, semente e origem; o plano ({@link DungeonPlanner}) e refeito quando
 * preciso e cada {@link #postProcess} coloca so o que cai no chunk sendo gerado: blocos, loot dos baus, geradores,
 * flechas dos ejetores, configuracao do cofre (chave e loot por jogador) e o mini-chefe. Blocos solidos na camada
 * y=-1 (patio e predio) descem ate o chao; o subsolo ja vem fechado pelo plano.
 */
public class DungeonPiece extends StructurePiece {
	public static final int FOUNDATION_DEPTH = 12;
	public static final int TRAP_ARROWS = 9;

	private final DungeonTheme theme;
	private final long seed;
	private final BlockPos origin;
	private @Nullable DungeonPlan plan;

	public DungeonPiece(DungeonTheme theme, long seed, BlockPos origin) {
		this(theme, seed, origin, DungeonPlanner.plan(theme, seed));
	}

	DungeonPiece(DungeonTheme theme, long seed, BlockPos origin, DungeonPlan plan) {
		super(DungeonRegistry.DUNGEON_PIECE, 0, box(plan, origin));
		this.theme = theme;
		this.seed = seed;
		this.origin = origin;
		this.plan = plan;
	}

	public DungeonPiece(CompoundTag tag) {
		super(DungeonRegistry.DUNGEON_PIECE, tag);
		this.theme = DungeonTheme.byId(tag.getStringOr("Theme", "forgotten_crypt"));
		this.seed = tag.getLongOr("Seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0));
	}

	private static BoundingBox box(DungeonPlan plan, BlockPos origin) {
		int[] b = plan.bounds();
		return new BoundingBox(origin.getX() + b[0], origin.getY() + Math.min(b[1], -1 - FOUNDATION_DEPTH), origin.getZ() + b[2],
				origin.getX() + b[3], origin.getY() + b[4], origin.getZ() + b[5]);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Theme", theme.id());
		tag.putLong("Seed", seed);
		tag.putInt("OX", origin.getX());
		tag.putInt("OY", origin.getY());
		tag.putInt("OZ", origin.getZ());
	}

	public DungeonPlan plan() {
		if (plan == null) {
			plan = DungeonPlanner.plan(theme, seed);
		}
		return plan;
	}

	public DungeonTheme theme() {
		return theme;
	}

	public BlockPos origin() {
		return origin;
	}

	/** Posicao no mundo de um ponto do plano. */
	public BlockPos world(Pos p) {
		return origin.offset(p.x(), p.y(), p.z());
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		DungeonPlan plan = plan();
		DungeonPalette palette = DungeonPalette.of(theme);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<BlockPos> connect = new ArrayList<>();
		for (Map.Entry<Long, DungeonPart> entry : plan.blocks().entrySet()) {
			long key = entry.getKey();
			int rx = DungeonPlan.keyX(key);
			int ry = DungeonPlan.keyY(key);
			int rz = DungeonPlan.keyZ(key);
			pos.set(origin.getX() + rx, origin.getY() + ry, origin.getZ() + rz);
			if (!chunkBB.isInside(pos)) {
				continue;
			}
			DungeonPart part = entry.getValue();
			BlockState state = part == DungeonPart.REDSTONE ? wire(plan, rx, ry, rz)
					: palette.state(part, plan.facing(rx, ry, rz), rx * 31 + ry * 7 + rz * 13 + (int) (seed & 0xFF));
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			BlockPos at = pos.immutable();
			switch (part) {
				case CHEST -> RandomizableContainer.setBlockEntityLootTable(level, random, at, DungeonRegistry.loot(plan.chests().get(key)));
				case BARREL -> {
					if (Math.floorMod(rx * 7 + rz * 3 + ry, 3) == 0) {
						RandomizableContainer.setBlockEntityLootTable(level, random, at, DungeonRegistry.loot(DungeonPlan.LOOT_COMMON));
					}
				}
				case SPAWNER -> spawner(level, at, plan.spawners().get(key), random);
				case VAULT -> {
					if (level.getBlockEntity(at) instanceof VaultBlockEntity vault) {
						vault.setConfig(new VaultConfig(DungeonRegistry.loot(DungeonPlan.LOOT_VAULT), 4.0, 4.5, new ItemStack(DungeonRegistry.KEY),
								Optional.empty()));
						vault.setChanged();
					}
				}
				case DISPENSER -> {
					if (level.getBlockEntity(at) instanceof DispenserBlockEntity dispenser) {
						dispenser.setItem(0, new ItemStack(Items.ARROW, TRAP_ARROWS));
					}
				}
				case SUSPICIOUS -> {
					if (level.getBlockEntity(at) instanceof BrushableBlockEntity brush) {
						brush.setLootTable(switch (theme) {
							case SAND -> BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY;
							case DROWNED -> BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY;
							default -> BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON;
						}, random.nextLong());
					}
				}
				default -> {
				}
			}
			if (connects(state)) {
				connect.add(at);
			}
			if (ry == -1 && state.isSolid()) {
				fillBelow(level, at, palette.state(DungeonPart.WALL, null, rx * 17 + rz));
			}
		}
		for (BlockPos p : connect) {
			BlockState placed = level.getBlockState(p);
			BlockState updated = Block.updateFromNeighbourShapes(placed, level, p);
			if (updated != placed && !updated.isAir()) {
				level.setBlock(p, updated, Block.UPDATE_CLIENTS);
			}
		}
		Pos boss = plan.marker(DungeonPlan.MARKER_BOSS);
		if (boss != null && chunkBB.isInside(world(boss))) {
			Mob mob = DungeonBosses.create(level.getLevel(), theme, world(boss));
			if (mob != null) {
				level.addFreshEntity(mob);
			}
		}
	}

	private static void spawner(WorldGenLevel level, BlockPos pos, @Nullable String mob, RandomSource random) {
		if (mob == null || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
			return;
		}
		Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(mob));
		type.ifPresent(t -> spawner.setEntityId(t, random));
	}

	/**
	 * Fio de redstone ja ligado aos vizinhos do plano (fio, placa do enigma e a porta de ferro), sem depender da ordem
	 * de geracao dos chunks. Um fio com uma ligacao so ganha o lado oposto (aponta em linha reta).
	 */
	static BlockState wire(DungeonPlan plan, int x, int y, int z) {
		BlockState state = net.minecraft.world.level.block.Blocks.REDSTONE_WIRE.defaultBlockState();
		int links = 0;
		Direction single = null;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			DungeonPart n = plan.get(x + d.getStepX(), y, z + d.getStepZ());
			boolean link = n == DungeonPart.REDSTONE || n == DungeonPart.PUZZLE_PLATE || n == DungeonPart.IRON_DOOR;
			state = state.setValue(RedstoneWireBlock.PROPERTY_BY_DIRECTION.get(d), link ? RedstoneSide.SIDE : RedstoneSide.NONE);
			if (link) {
				links++;
				single = d;
			}
		}
		if (links == 1) {
			state = state.setValue(RedstoneWireBlock.PROPERTY_BY_DIRECTION.get(single.getOpposite()), RedstoneSide.SIDE);
		}
		return state;
	}

	private static boolean connects(BlockState state) {
		Block block = state.getBlock();
		return block instanceof FenceBlock || block instanceof IronBarsBlock || block instanceof WallBlock || block instanceof StairBlock;
	}

	/** Alicerce do patio e do predio: desce ate achar chao firme (ar, plantas e liquidos sao substituidos). */
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

}
