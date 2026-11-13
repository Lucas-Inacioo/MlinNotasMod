package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.MansionPlanner;
import com.mlin.notas.vstructures.StrongholdPlanner;
import com.mlin.notas.vstructures.VKind;
import com.mlin.notas.vstructures.VPlan;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Game tests da fortaleza e da mansao refeitas (lote proprio): o portal do End acende com 12 olhos, o gerador e de
 * tracas, a porta de ferro abre com o botao, e a mansao nasce com vindicadores, evocadores e allays presas.
 */
public class SmainGameTest {
	private static final String ENV = "mlinnotas-gametest:smain";

	@GameTest(environment = ENV)
	public void strongholdAndMansionUseTheRemakeType(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String id : new String[] {"stronghold", "mansion"}) {
			var holder = structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id)));
			Structure s = holder.value();
			helper.assertTrue(s.type() == ModStructures.VANILLA_REMAKE, "minecraft:" + id + " usa o tipo refeito, achou " + s.type());
			if (id.equals("stronghold")) {
				helper.assertTrue(holder.is(StructureTags.EYE_OF_ENDER_LOCATED), "o olho do fim continua apontando para a fortaleza");
			}
		}
		var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("mlinnotas", "chests/stronghold_trap"));
		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "loot das armadilhas");
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void strongholdPortalLightsWithTwelveEyes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VanillaRemakePiece piece = VanillaStructuresGameTest.placeWhole(level, VKind.STRONGHOLD, 0, 11L, new BlockPos(34000, 0, 30000));
		VPlan plan = piece.plan();
		Pos spawnerAt = plan.marker(StrongholdPlanner.MARKER_PORTAL_SPAWNER);
		BlockPos spawner = piece.world(spawnerAt);
		helper.assertTrue(level.getBlockEntity(spawner) instanceof SpawnerBlockEntity be
				&& be.getSpawner().getOrCreateDisplayEntity(level, spawner) instanceof Entity e && e.getType() == EntityTypes.SILVERFISH,
				"gerador de tracas na sala do portal");
		long library = plan.lootTables().entrySet().stream().filter(e -> e.getValue().equals(StrongholdPlanner.LIBRARY))
				.filter(e -> level.getBlockEntity(piece.world(VPlan.keyX(e.getKey()), VPlan.keyY(e.getKey()), VPlan.keyZ(e.getKey())))
						instanceof ChestBlockEntity c && c.getLootTable() != null).count();
		helper.assertTrue(library >= 4, "baus da biblioteca com loot: " + library);
		int frames = 0;
		for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
			if (!VPlan.id(e.getValue()).equals("end_portal_frame")) {
				continue;
			}
			BlockPos pos = piece.world(VPlan.keyX(e.getKey()), VPlan.keyY(e.getKey()), VPlan.keyZ(e.getKey()));
			BlockState state = level.getBlockState(pos);
			helper.assertTrue(state.is(Blocks.END_PORTAL_FRAME), "moldura em " + pos + ": " + state);
			frames++;
			if (!state.getValue(EndPortalFrameBlock.HAS_EYE)) {
				UseOnContext use = new UseOnContext(level, null, InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE),
						new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
				Items.ENDER_EYE.useOn(use);
			}
		}
		helper.assertTrue(frames == 12, "12 molduras, achou " + frames);
		BlockPos center = piece.world(plan.marker(StrongholdPlanner.MARKER_PORTAL));
		int portal = 0;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				portal += level.getBlockState(center.offset(x, 0, z)).is(Blocks.END_PORTAL) ? 1 : 0;
			}
		}
		int lit = portal;
		VanillaStructuresGameTest.release(level, piece);
		helper.assertTrue(lit == 9, "os 12 olhos acendem o portal (blocos de portal: " + lit + ")");
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void strongholdIronDoorOpensWithItsButton(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VanillaRemakePiece piece = VanillaStructuresGameTest.placeWhole(level, VKind.STRONGHOLD, 0, 23L, new BlockPos(34400, 0, 30000));
		BlockPos button = null;
		BlockPos door = null;
		for (Map.Entry<Long, String> e : piece.plan().blocks().entrySet()) {
			if (!VPlan.id(e.getValue()).equals("stone_button")) {
				continue;
			}
			BlockPos b = piece.world(VPlan.keyX(e.getKey()), VPlan.keyY(e.getKey()), VPlan.keyZ(e.getKey()));
			for (BlockPos near : BlockPos.betweenClosed(b.offset(-2, -2, -2), b.offset(2, 1, 2))) {
				if (level.getBlockState(near).is(Blocks.IRON_DOOR)) {
					button = b;
					door = near.immutable();
					break;
				}
			}
			if (button != null) {
				break;
			}
		}
		helper.assertTrue(button != null, "nenhum botao ao lado de porta de ferro");
		BlockState state = level.getBlockState(button);
		helper.assertTrue(state.getBlock() instanceof ButtonBlock, "botao: " + state);
		helper.assertTrue(!level.getBlockState(door).getValue(DoorBlock.OPEN), "a porta comeca fechada");
		((ButtonBlock) state.getBlock()).press(state, level, button, null);
		BlockPos doorAt = door;
		helper.runAfterDelay(2, () -> {
			boolean open = level.getBlockState(doorAt).getValue(DoorBlock.OPEN);
			VanillaStructuresGameTest.release(level, piece);
			helper.assertTrue(open, "o botao abre a porta de ferro em " + doorAt);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void mansionSpawnsIllagersAndCapturedAllays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VanillaRemakePiece piece = VanillaStructuresGameTest.placeWhole(level, VKind.WOODLAND_MANSION, 0, 7L, new BlockPos(34800, -40, 30000));
		helper.runAtTickTime(20, () -> {
			AABB area = AABB.of(piece.getBoundingBox()).inflate(2);
			int vindicators = level.getEntitiesOfClass(Vindicator.class, area).size();
			int evokers = level.getEntitiesOfClass(Evoker.class, area).size();
			int allays = level.getEntitiesOfClass(Allay.class, area).size();
			boolean persistent = level.getEntitiesOfClass(Vindicator.class, area).stream().allMatch(v -> v.isPersistenceRequired());
			long chests = piece.plan().lootTables().keySet().stream().filter(k -> level.getBlockEntity(piece.world(VPlan.keyX(k), VPlan.keyY(k),
					VPlan.keyZ(k))) instanceof ChestBlockEntity c && c.getLootTable() != null
					&& c.getLootTable().identifier().getPath().equals("chests/woodland_mansion")).count();
			BlockPos wall = piece.world(piece.plan().marker(MansionPlanner.MARKER_SECRET_WALL + 0));
			boolean secret = level.getBlockState(wall).is(Blocks.BOOKSHELF);
			VanillaStructuresGameTest.release(level, piece);
			helper.assertTrue(vindicators >= 7, "vindicadores: " + vindicators);
			helper.assertTrue(evokers >= 1, "evocadores: " + evokers);
			helper.assertTrue(allays >= 1, "allays presas: " + allays);
			helper.assertTrue(persistent, "os vindicadores nao somem");
			helper.assertTrue(chests >= 8, "baus com loot da mansao: " + chests);
			helper.assertTrue(secret, "parede de estantes do cofre");
			helper.succeed();
		});
	}
}
