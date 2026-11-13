package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.DesertPyramidPlanner;
import com.mlin.notas.vstructures.JungleTemplePlanner;
import com.mlin.notas.vstructures.OutpostPlanner;
import com.mlin.notas.vstructures.VKind;
import com.mlin.notas.vstructures.VPlan;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Game tests das estruturas vanilla refeitas. Cada estrutura e montada inteira longe da area dos testes (lote
 * proprio), com os chunks forcados para a redstone e os pistoes funcionarem.
 */
public class VanillaStructuresGameTest {
	private static final String ENV = "mlinnotas-gametest:vstructures";

	/** Monta a peca inteira, chunk por chunk, como o gerador de mundo faz. */
	static VanillaRemakePiece placeWhole(ServerLevel level, VKind kind, int variant, long seed, BlockPos origin) {
		VanillaRemakePiece piece = new VanillaRemakePiece(kind, variant, seed, origin, 0, false);
		BoundingBox box = piece.getBoundingBox();
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz);
				BoundingBox chunkBB = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
				piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(seed + cx * 31L + cz),
						chunkBB, new ChunkPos(cx, cz), origin);
			}
		}
		return piece;
	}

	static void release(ServerLevel level, VanillaRemakePiece piece) {
		BoundingBox box = piece.getBoundingBox();
		for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.of(box).inflate(4), e -> !(e instanceof Player))) {
			e.discard();
		}
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
	}

	private static boolean lootIs(ServerLevel level, BlockPos pos, String path) {
		return level.getBlockEntity(pos) instanceof ChestBlockEntity c && c.getLootTable() != null && c.getLootTable().identifier().getPath().equals(path);
	}

	@GameTest(environment = ENV)
	public void vanillaStructuresUseTheRemakeType(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String id : new String[] {"desert_pyramid", "jungle_pyramid", "pillager_outpost", "mineshaft", "mineshaft_mesa"}) {
			Structure s = structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id))).value();
			helper.assertTrue(s.type() == ModStructures.VANILLA_REMAKE, "minecraft:" + id + " usa o tipo refeito, achou " + s.type());
		}
		for (String table : new String[] {"desert_tomb", "desert_storage", "jungle_storage", "outpost_armory", "outpost_supplies", "mineshaft_workshop"}) {
			var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("mlinnotas", "chests/" + table));
			helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "loot mlinnotas:chests/" + table);
		}
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void everyPlannedBlockStateExists(GameTestHelper helper) {
		for (VKind kind : VKind.values()) {
			for (int variant = 0; variant <= 6; variant++) {
				for (long seed : new long[] {1L, 42L, -9L}) {
					List<String> bad = VanillaRemakePiece.invalidStates(kind.plan(seed, variant));
					helper.assertTrue(bad.isEmpty(), kind + "/" + variant + ": " + bad);
				}
			}
		}
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 400)
	public void jungleLeverPuzzleOpensTheSecretDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VanillaRemakePiece piece = placeWhole(level, VKind.JUNGLE_TEMPLE, 0, 77L, new BlockPos(30000, -50, 30000));
		VPlan plan = piece.plan();
		BlockPos door = piece.world(plan.marker(JungleTemplePlanner.MARKER_SECRET_DOOR));
		BlockPos piston = door.south(2);
		Pos wrongPos = plan.marker(JungleTemplePlanner.MARKER_WRONG_LEVER);
		BlockPos wrong = piece.world(wrongPos);
		helper.assertTrue(level.getBlockState(door).is(Blocks.MOSSY_STONE_BRICKS), "porta fechada no comeco: " + level.getBlockState(door));
		helper.assertTrue(level.getBlockState(piston).is(Blocks.STICKY_PISTON) && level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED),
				"pistao estendido: " + level.getBlockState(piston));
		helper.assertTrue(level.getBlockState(piece.world(plan.marker(JungleTemplePlanner.MARKER_HIDDEN_CHEST))).is(Blocks.CHEST), "bau escondido");
		// so a alavanca errada: continua fechada
		pull(level, wrong);
		helper.runAtTickTime(20, () -> {
			helper.assertTrue(level.getBlockState(door).is(Blocks.MOSSY_STONE_BRICKS), "a alavanca errada nao abre: " + level.getBlockState(door));
			pull(level, wrong);
			for (int i = 0; i < 3; i++) {
				BlockPos lever = piece.world(plan.marker(JungleTemplePlanner.MARKER_LEVER + i));
				if (!lever.equals(wrong)) {
					pull(level, lever);
				}
			}
		});
		helper.runAtTickTime(60, () -> {
			BlockState here = level.getBlockState(door);
			BlockState p = level.getBlockState(piston);
			boolean open = here.isAir() && p.is(Blocks.STICKY_PISTON) && !p.getValue(PistonBaseBlock.EXTENDED);
			release(level, piece);
			helper.assertTrue(open, "a combinacao certa abre a porta: porta=" + here + " pistao=" + p);
			helper.succeed();
		});
	}

	private static void pull(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof LeverBlock lever) {
			lever.pull(state, level, pos, null);
		}
	}

	@GameTest(environment = ENV, maxTicks = 300)
	public void desertPressurePlateLightsTheTnt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VanillaRemakePiece piece = placeWhole(level, VKind.DESERT_PYRAMID, 0, 5L, new BlockPos(30400, -40, 30000));
		BlockPos plate = piece.world(piece.plan().marker(DesertPyramidPlanner.MARKER_PLATE));
		helper.assertTrue(level.getBlockState(plate).is(Blocks.STONE_PRESSURE_PLATE), "placa: " + level.getBlockState(plate));
		helper.assertTrue(level.getBlockState(plate.below(2)).is(Blocks.TNT), "TNT embaixo");
		long chests = piece.plan().lootTables().keySet().stream()
				.filter(k -> lootIs(level, piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)), "chests/desert_pyramid")).count();
		helper.assertTrue(chests == 4, "4 baus com loot vanilla, achou " + chests);
		var pig = EntityTypes.PIG.create(level, EntitySpawnReason.TRIGGERED);
		pig.snapTo(plate.getX() + 0.5, plate.getY(), plate.getZ() + 0.5, 0, 0);
		level.addFreshEntity(pig);
		helper.succeedWhen(() -> {
			List<PrimedTnt> primed = level.getEntitiesOfClass(PrimedTnt.class, new AABB(plate).inflate(4));
			helper.assertTrue(!primed.isEmpty(), "a placa acende o TNT");
			release(level, piece);
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void outpostSpawnsCaptainGolemAndOminousBanners(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VanillaRemakePiece piece = placeWhole(level, VKind.PILLAGER_OUTPOST, 0, 9L, new BlockPos(30800, -55, 30000));
		helper.runAtTickTime(20, () -> {
			AABB area = AABB.of(piece.getBoundingBox()).inflate(2);
			List<Pillager> pillagers = level.getEntitiesOfClass(Pillager.class, area);
			boolean captain = pillagers.stream().anyMatch(p -> p.isPatrolLeader() && !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
			boolean golem = !level.getEntitiesOfClass(IronGolem.class, area).isEmpty();
			long ominous = piece.plan().tagMap().keySet().stream().filter(k -> level.getBlockEntity(piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)))
					instanceof BannerBlockEntity b && !b.getPatterns().layers().isEmpty()).count();
			boolean topLoot = lootIs(level, piece.world(piece.plan().marker(OutpostPlanner.MARKER_TOP_CHEST)), "chests/pillager_outpost");
			int count = pillagers.size();
			release(level, piece);
			helper.assertTrue(count >= 5, "saqueadores no posto: " + count);
			helper.assertTrue(captain, "capitao com estandarte");
			helper.assertTrue(golem, "golem de ferro na jaula");
			helper.assertTrue(ominous >= 8, "estandartes de saqueador com padrao: " + ominous);
			helper.assertTrue(topLoot, "bau vanilla no topo da torre");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void mineshaftHasChestMinecartsWithVanillaLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VanillaRemakePiece piece = placeWhole(level, VKind.MINESHAFT, 0, 3L, new BlockPos(31400, -30, 30000));
		helper.runAtTickTime(20, () -> {
			List<MinecartChest> carts = level.getEntitiesOfClass(MinecartChest.class, AABB.of(piece.getBoundingBox()).inflate(2));
			boolean loot = carts.stream().anyMatch(c -> c.getContainerLootTable() != null
					&& c.getContainerLootTable().identifier().getPath().equals("chests/abandoned_mineshaft"));
			long expected = piece.plan().entities().stream().filter(s -> s.tag().startsWith("loot:")).count();
			release(level, piece);
			helper.assertTrue(carts.size() == expected, "carrinhos com bau: " + carts.size() + " de " + expected);
			helper.assertTrue(loot, "loot vanilla da mina");
			helper.succeed();
		});
	}
}
