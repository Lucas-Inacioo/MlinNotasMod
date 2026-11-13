package com.mlin.notas.vstructures;

import com.mlin.notas.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uma estrutura vanilla refeita inteira numa peca so. Guarda tipo, variante, semente, origem, giro e espelho; o plano
 * ({@link VKind#plan}) e refeito quando preciso (com cache) e cada {@link #postProcess} coloca so o que cai no chunk.
 * Bandeiras do plano: {@link VPlan#FILL_DOWN} desce o bloco ate o chao, {@link VPlan#IF_AIR} so preenche vazio,
 * {@link VPlan#IF_SOLID} so troca rocha. Trechos ({@link VPlan#newGroup}) que encostam em liquido sao pulados no chunk.
 */
public class VanillaRemakePiece extends StructurePiece {
	public static final int FILL_DEPTH = 32;
	private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();
	private static final Map<String, VPlan> PLANS = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, VPlan> eldest) {
			return size() > 12;
		}
	});

	private final VKind kind;
	private final int variant;
	private final long seed;
	private final BlockPos origin;
	private final int turns;
	private final boolean mirror;
	private volatile @Nullable Map<Long, List<Long>> byChunk;

	public VanillaRemakePiece(VKind kind, int variant, long seed, BlockPos origin, int turns, boolean mirror) {
		super(ModStructures.VANILLA_REMAKE_PIECE, 0, box(planFor(kind, seed, variant), origin, turns, mirror));
		this.kind = kind;
		this.variant = variant;
		this.seed = seed;
		this.origin = origin;
		this.turns = turns;
		this.mirror = mirror;
	}

	public VanillaRemakePiece(CompoundTag tag) {
		super(ModStructures.VANILLA_REMAKE_PIECE, tag);
		this.kind = VKind.byName(tag.getStringOr("Kind", "desert_pyramid"));
		this.variant = tag.getIntOr("Variant", 0);
		this.seed = tag.getLongOr("Seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 64), tag.getIntOr("OZ", 0));
		this.turns = tag.getIntOr("Turns", 0);
		this.mirror = tag.getBooleanOr("Mirror", false);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Kind", kind.serializedName());
		tag.putInt("Variant", variant);
		tag.putLong("Seed", seed);
		tag.putInt("OX", origin.getX());
		tag.putInt("OY", origin.getY());
		tag.putInt("OZ", origin.getZ());
		tag.putInt("Turns", turns);
		tag.putBoolean("Mirror", mirror);
	}

	public static VPlan planFor(VKind kind, long seed, int variant) {
		String key = kind.name() + "/" + variant + "/" + seed;
		VPlan plan = PLANS.get(key);
		if (plan == null) {
			plan = kind.plan(seed, variant);
			PLANS.put(key, plan);
		}
		return plan;
	}

	public VPlan plan() {
		return planFor(kind, seed, variant);
	}

	public VKind kind() {
		return kind;
	}

	public int variant() {
		return variant;
	}

	public BlockPos origin() {
		return origin;
	}

	// --- giro e espelho ---

	/** Posicao no mundo de um ponto do plano. */
	public BlockPos world(int x, int y, int z) {
		int px = mirror ? -x : x;
		int pz = z;
		for (int i = 0; i < turns; i++) {
			int nx = -pz;
			pz = px;
			px = nx;
		}
		return origin.offset(px, y, pz);
	}

	public BlockPos world(com.mlin.notas.util.Pos p) {
		return world(p.x(), p.y(), p.z());
	}

	/** Ponto do plano (com fracao) no mundo, com o mesmo giro/espelho dos blocos. */
	public double[] worldPoint(double x, double y, double z) {
		double px = mirror ? 1 - x : x;
		double pz = z;
		for (int i = 0; i < turns; i++) {
			double nx = 1 - pz;
			pz = px;
			px = nx;
		}
		return new double[] {origin.getX() + px, origin.getY() + y, origin.getZ() + pz};
	}

	private Rotation rotation() {
		return switch (Math.floorMod(turns, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	public BlockState transform(BlockState state) {
		BlockState out = mirror ? state.mirror(Mirror.FRONT_BACK) : state;
		return out.rotate(rotation());
	}

	private static BoundingBox box(VPlan plan, BlockPos origin, int turns, boolean mirror) {
		int[] b = plan.bounds();
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int x : new int[] {b[0], b[3]}) {
			for (int z : new int[] {b[2], b[5]}) {
				int px = mirror ? -x : x;
				int pz = z;
				for (int i = 0; i < turns; i++) {
					int nx = -pz;
					pz = px;
					px = nx;
				}
				minX = Math.min(minX, px);
				maxX = Math.max(maxX, px);
				minZ = Math.min(minZ, pz);
				maxZ = Math.max(maxZ, pz);
			}
		}
		return new BoundingBox(origin.getX() + minX, origin.getY() + b[1], origin.getZ() + minZ, origin.getX() + maxX, origin.getY() + b[4],
				origin.getZ() + maxZ);
	}

	// --- estados ---

	/** Estado de bloco a partir do texto do plano (com cache). Bloco ou propriedade desconhecidos dao erro. */
	public static BlockState parse(String text) {
		return STATES.computeIfAbsent(text, VanillaRemakePiece::parseUncached);
	}

	private static BlockState parseUncached(String text) {
		int bracket = text.indexOf('[');
		String id = bracket < 0 ? text : text.substring(0, bracket);
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id));
		if (block.isEmpty()) {
			throw new IllegalArgumentException("bloco desconhecido: " + text);
		}
		BlockState state = block.get().defaultBlockState();
		if (bracket >= 0) {
			for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
				int eq = pair.indexOf('=');
				Property<?> property = block.get().getStateDefinition().getProperty(pair.substring(0, eq));
				if (property == null) {
					throw new IllegalArgumentException("propriedade desconhecida em " + text);
				}
				state = with(state, property, pair.substring(eq + 1), text);
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value, String text) {
		return state.setValue(property, property.getValue(value).orElseThrow(() -> new IllegalArgumentException("valor invalido em " + text)));
	}

	// --- colocacao ---

	private Map<Long, List<Long>> byChunk() {
		if (byChunk == null) {
			Map<Long, List<Long>> out = new HashMap<>();
			for (long k : plan().blocks().keySet()) {
				BlockPos p = world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k));
				out.computeIfAbsent(ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4), c -> new ArrayList<>()).add(k);
			}
			byChunk = out;
		}
		return byChunk;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		placeIn(level, random, chunkBB, chunkPos, generator.getSeaLevel());
	}

	/** Coloca o que cai no chunk; {@code seaLevel}: abaixo dele o ar do plano vira agua nas estruturas do mar (testes passam o seu). */
	public void placeIn(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, int seaLevel) {
		VPlan plan = plan();
		List<Long> keys = byChunk().get(ChunkPos.pack(chunkPos.x(), chunkPos.z()));
		if (keys != null) {
			place(level, random, chunkBB, plan, keys, seaLevel);
		}
		for (VPlan.Spawn spawn : plan.entities()) {
			double[] w = worldPoint(spawn.x(), spawn.y(), spawn.z());
			BlockPos at = BlockPos.containing(w[0], w[1], w[2]);
			if (chunkBB.isInside(at)) {
				spawn(level, random, spawn, w, at);
			}
		}
	}

	private void place(WorldGenLevel level, RandomSource random, BoundingBox chunkBB, VPlan plan, List<Long> keys, int seaLevel) {
		Map<Long, Integer> groups = plan.groupMap();
		Set<Integer> wet = new HashSet<>();
		if (!groups.isEmpty()) {
			for (long k : keys) {
				Integer g = groups.get(k);
				if (g != null && !wet.contains(g)) {
					BlockPos p = world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k));
					if (chunkBB.isInside(p) && !level.getFluidState(p).isEmpty()) {
						wet.add(g);
					}
				}
			}
		}
		List<BlockPos> connect = new ArrayList<>();
		Map<Long, Integer> flags = plan.flagMap();
		for (long k : keys) {
			int rx = VPlan.keyX(k);
			int ry = VPlan.keyY(k);
			int rz = VPlan.keyZ(k);
			BlockPos pos = world(rx, ry, rz);
			if (!chunkBB.isInside(pos)) {
				continue;
			}
			Integer g = groups.get(k);
			if (g != null && wet.contains(g)) {
				continue;
			}
			int flag = flags.getOrDefault(k, 0);
			BlockState here = level.getBlockState(pos);
			if ((flag & VPlan.IF_AIR) != 0 && !(here.isAir() || here.canBeReplaced() || !here.getFluidState().isEmpty())) {
				continue;
			}
			if ((flag & VPlan.IF_SOLID) != 0 && (here.isAir() || here.canBeReplaced() || !here.getFluidState().isEmpty())) {
				continue;
			}
			BlockState state = transform(parse(plan.blocks().get(k)));
			if (kind.placement().aquatic()) {
				state = SeaBlocks.forDepth(state, pos.getY() < seaLevel);
				if (state == null) {
					continue;
				}
			}
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			String table = plan.lootTables().get(k);
			if (table != null) {
				RandomizableContainer.setBlockEntityLootTable(level, random, pos, lootKey(table));
			}
			String spawner = plan.spawnerMap().get(k);
			if (spawner != null && level.getBlockEntity(pos) instanceof SpawnerBlockEntity entity) {
				BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(spawner)).ifPresent(type -> entity.setEntityId(type, random));
			}
			String dig = plan.archaeologyMap().get(k);
			if (dig != null) {
				BlockEntity entity = level.getBlockEntity(pos);
				if (entity instanceof BrushableBlockEntity brushable) {
					brushable.setLootTable(lootKey(dig), pos.asLong());
				}
			}
			if ((flag & VPlan.FILL_DOWN) != 0) {
				fillBelow(level, pos, state);
			}
			if ((flag & VPlan.FILL_SOIL) != 0) {
				fillBelow(level, pos, soilFor(state));
			}
			if ("ominous".equals(plan.tagMap().get(k)) && level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
				banner.applyComponentsFromItemStack(Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
			}
			if (connects(state)) {
				connect.add(pos);
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

	private static BlockState soilFor(BlockState top) {
		if (top.is(Blocks.SAND)) {
			return Blocks.SANDSTONE.defaultBlockState();
		}
		if (top.is(Blocks.RED_SAND)) {
			return Blocks.RED_SANDSTONE.defaultBlockState();
		}
		return Blocks.DIRT.defaultBlockState();
	}

	private static ResourceKey<LootTable> lootKey(String table) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table));
	}

	private static boolean connects(BlockState state) {
		Block block = state.getBlock();
		return block instanceof FenceBlock || block instanceof IronBarsBlock || block instanceof WallBlock || block instanceof StairBlock
				|| block instanceof RedstoneWireBlock || block instanceof CrossCollisionBlock;
	}

	/** Desce o bloco ate achar chao firme (ar, plantas e liquidos sao substituidos). */
	private static void fillBelow(WorldGenLevel level, BlockPos top, BlockState fill) {
		BlockPos.MutableBlockPos pos = top.mutable();
		for (int i = 0; i < FILL_DEPTH; i++) {
			pos.move(Direction.DOWN);
			if (pos.getY() <= level.getMinY()) {
				return;
			}
			BlockState here = level.getBlockState(pos);
			if (!here.isAir() && !here.canBeReplaced() && here.getFluidState().isEmpty()) {
				return;
			}
			level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
		}
	}

	private void spawn(WorldGenLevel level, RandomSource random, VPlan.Spawn spawn, double[] w, BlockPos at) {
		Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(spawn.type()));
		if (type.isEmpty()) {
			return;
		}
		Entity entity = type.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (entity == null) {
			return;
		}
		entity.snapTo(w[0], w[1], w[2], random.nextFloat() * 360f, 0f);
		String tag = spawn.tag() == null ? "" : spawn.tag();
		if (entity instanceof MinecartChest cart && tag.startsWith("loot:")) {
			cart.setLootTable(lootKey(tag.substring(5)), random.nextLong());
		}
		if (entity instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
			mob.setPersistenceRequired();
			if (tag.equals("captain") && mob instanceof PatrollingMonster raider) {
				raider.setPatrolLeader(true);
				raider.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
				raider.setDropChance(EquipmentSlot.HEAD, 2.0f);
			}
			SeaSpawns.apply(level, mob, tag);
		}
		level.addFreshEntityWithPassengers(entity);
	}

	/** Usado pelos testes: todos os textos de estado do plano viram blocos validos. */
	public static List<String> invalidStates(VPlan plan) {
		List<String> out = new ArrayList<>();
		for (String s : new HashSet<>(plan.blocks().values())) {
			try {
				parse(s);
			} catch (RuntimeException e) {
				out.add(e.getMessage());
			}
		}
		return out;
	}

	static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
