package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.ruins.RuinKind;
import com.mlin.notas.ruins.RuinPiece;
import com.mlin.notas.ruins.RuinPlan;
import com.mlin.notas.ruins.RuinStyle;
import com.mlin.notas.util.Pos;
import com.mlin.notas.waystone.WaystoneBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import com.mlin.notas.ruins.RuinPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Game tests das ruinas de superficie: registro, alicerce ate o chao, loot dos baus e pedra de teleporte. */
public class RuinsGameTest {
	/** Chao de pedra em y=0 e ar acima (a ruina e montada 2 blocos acima, para o alicerce ter que descer). */
	private static void stoneFloor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y < 8; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	/** Monta so a parte da ruina que cai na area do teste, com {@code marker} do plano em (4, 3, 4). */
	private static RuinPiece build(GameTestHelper helper, RuinKind kind, RuinStyle style, long seed, String marker) {
		RuinPiece probe = new RuinPiece(kind, style, seed, BlockPos.ZERO);
		return build(helper, kind, style, seed, marker == null ? new Pos(0, 0, 0) : probe.plan().marker(marker));
	}

	/** Monta a parte da ruina em volta do ponto {@code m} do plano, que fica em (4, 3, 4). */
	private static RuinPiece build(GameTestHelper helper, RuinKind kind, RuinStyle style, long seed, Pos m) {
		BlockPos target = helper.absolutePos(new BlockPos(4, 3, 4));
		BlockPos origin = target.offset(-m.x(), -m.y(), -m.z());
		RuinPiece piece = new RuinPiece(kind, style, seed, origin);
		BlockPos min = helper.absolutePos(BlockPos.ZERO);
		BlockPos max = helper.absolutePos(new BlockPos(7, 7, 7));
		BoundingBox box = BoundingBox.fromCorners(min, max);
		piece.postProcess(helper.getLevel(), helper.getLevel().structureManager(), helper.getLevel().getChunkSource().getGenerator(),
				RandomSource.create(seed), box, ChunkPos.containing(origin), origin);
		return piece;
	}

	@GameTest
	public void ruinStructuresAreRegisteredInOneSet(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (RuinKind kind : RuinKind.values()) {
			helper.assertTrue(structures.get(ResourceKey.create(Registries.STRUCTURE, MlinNotas.id(kind.id()))).isPresent(),
					"estrutura mlinnotas:" + kind.id() + " registrada");
		}
		StructureSet set = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, MlinNotas.id("ruins"))).value();
		helper.assertTrue(set.structures().size() == RuinKind.values().length, "um structure set com as " + RuinKind.values().length + " ruinas");
		helper.succeed();
	}

	@GameTest
	public void campSitsOnTheGroundWithUnlitCampfire(GameTestHelper helper) {
		stoneFloor(helper);
		RuinPiece piece = build(helper, RuinKind.CAMP, RuinStyle.SPRUCE, 77L, (String) null);
		// fogueira no centro (4,3,4); a camada de baixo (y=2) e terra/alicerce e o alicerce desce ate a pedra (y=1)
		helper.assertBlockPresent(Blocks.CAMPFIRE, 4, 3, 4);
		helper.assertTrue(!helper.getBlockState(new BlockPos(4, 3, 4)).getValue(CampfireBlock.LIT), "fogueira apagada");
		helper.assertTrue(!helper.getBlockState(new BlockPos(4, 2, 4)).isAir(), "chao do acampamento");
		helper.assertBlockPresent(Blocks.COBBLESTONE, 4, 1, 4);
		helper.assertBlockPresent(Blocks.STONE, 4, 0, 4);
		// em toda coluna com chao do acampamento nao sobra ar entre ele e a pedra
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				if (!helper.getBlockState(new BlockPos(x, 2, z)).isAir() && helper.getBlockState(new BlockPos(x, 2, z)).isSolid()) {
					helper.assertTrue(!helper.getBlockState(new BlockPos(x, 1, z)).isAir(), "alicerce em " + x + ",1," + z);
				}
			}
		}
		CompoundTag tag = piece.createTag(StructurePieceSerializationContext.fromLevel(helper.getLevel()));
		RuinPiece loaded = new RuinPiece(tag);
		helper.assertTrue(loaded.kind() == RuinKind.CAMP && loaded.style() == RuinStyle.SPRUCE && loaded.origin().equals(piece.origin()),
				"tipo, estilo e origem salvos");
		helper.assertTrue(loaded.plan().blocks().equals(piece.plan().blocks()), "mesma semente, mesmo plano");
		helper.succeed();
	}

	@GameTest
	public void ruinChestsGetEarlyGameLoot(GameTestHelper helper) {
		stoneFloor(helper);
		build(helper, RuinKind.CAMP, RuinStyle.MOSSY, 5L, RuinPlan.MARKER_CHEST);
		helper.assertBlockPresent(Blocks.CHEST, 4, 3, 4);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 4), ChestBlockEntity.class);
		helper.assertTrue(RuinPiece.SUPPLIES.equals(chest.getLootTable()), "bau com a loot das ruinas, achou " + chest.getLootTable());
		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(RuinPiece.SUPPLIES) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
				"loot table mlinnotas:chests/ruin_supplies carregada");
		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(RuinPiece.MINE_LOOT) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
				"loot table mlinnotas:chests/ruin_mine carregada");
		helper.succeed();
	}

	private static Direction direction(RuinPlan.Dir dir) {
		return switch (dir) {
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case EAST -> Direction.EAST;
			case WEST -> Direction.WEST;
		};
	}

	@GameTest
	public void mineHasAMinecartOnItsRails(GameTestHelper helper) {
		stoneFloor(helper);
		RuinPlan plan = new RuinPiece(RuinKind.MINE, RuinStyle.DEEPSLATE, 21L, BlockPos.ZERO).plan();
		Pos cart = plan.positionsOf(RuinPart.MINECART).getFirst();
		build(helper, RuinKind.MINE, RuinStyle.DEEPSLATE, 21L, cart);
		helper.assertBlockPresent(Blocks.RAIL, 4, 3, 4);
		List<Minecart> carts = helper.getLevel().getEntitiesOfClass(Minecart.class, new AABB(helper.absolutePos(new BlockPos(4, 3, 4))).inflate(1));
		helper.assertTrue(carts.size() == 1, "um carrinho em cima do trilho, achou " + carts.size());
		carts.forEach(Entity::discard);
		helper.succeed();
	}

	@GameTest
	public void wellRoofStairsFaceLikeThePlan(GameTestHelper helper) {
		stoneFloor(helper);
		RuinPlan plan = new RuinPiece(RuinKind.WELL, RuinStyle.SANDSTONE, 8L, BlockPos.ZERO).plan();
		Pos step = plan.positionsOf(RuinPart.WOOD_STAIRS).stream().filter(p -> p.y() >= 4).findFirst().orElseThrow();
		build(helper, RuinKind.WELL, RuinStyle.SANDSTONE, 8L, step);
		BlockState state = helper.getBlockState(new BlockPos(4, 3, 4));
		helper.assertTrue(state.is(Blocks.OAK_STAIRS), "degrau de carvalho no telhado do poco do deserto, achou " + state);
		Direction expected = direction(plan.facing(step.x(), step.y(), step.z()));
		helper.assertTrue(state.getValue(StairBlock.FACING) == expected, "degrau virado para " + expected + ", ficou " + state);
		helper.succeed();
	}

	@GameTest
	public void campFencesConnectToEachOther(GameTestHelper helper) {
		stoneFloor(helper);
		RuinPlan plan = new RuinPiece(RuinKind.CAMP, RuinStyle.SPRUCE, 13L, BlockPos.ZERO).plan();
		for (Pos fence : plan.positionsOf(RuinPart.FENCE)) {
			for (RuinPlan.Dir d : RuinPlan.Dir.values()) {
				if (fence.y() == 0 && plan.get(fence.x() + d.dx, 0, fence.z() + d.dz) == RuinPart.FENCE) {
					build(helper, RuinKind.CAMP, RuinStyle.SPRUCE, 13L, fence);
					BlockState state = helper.getBlockState(new BlockPos(4, 3, 4));
					helper.assertTrue(state.is(Blocks.SPRUCE_FENCE), "cerca de abeto, achou " + state);
					helper.assertTrue(state.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(direction(d))),
							"cerca ligada a vizinha (" + d + "): " + state);
					helper.succeed();
					return;
				}
			}
		}
		helper.fail("nenhuma cerca com vizinha no acampamento");
	}

	@GameTest
	public void watchtowerHasAWaystoneOnItsFoundation(GameTestHelper helper) {
		stoneFloor(helper);
		build(helper, RuinKind.WATCHTOWER, RuinStyle.MOSSY, 11L, RuinPlan.MARKER_WAYSTONE);
		helper.assertBlockPresent(ModBlocks.WAYSTONE, 4, 3, 4);
		helper.assertBlockPresent(ModBlocks.WAYSTONE, 4, 4, 4);
		helper.assertTrue(helper.getBlockState(new BlockPos(4, 4, 4)).getValue(WaystoneBlock.HALF) == DoubleBlockHalf.UPPER, "metade de cima");
		helper.assertBlockPresent(Blocks.COBBLESTONE, 4, 2, 4);
		helper.assertBlockPresent(Blocks.COBBLESTONE, 4, 1, 4);
		helper.succeed();
	}
}
