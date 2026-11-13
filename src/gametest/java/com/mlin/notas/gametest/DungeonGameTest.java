package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.dungeon.DungeonBosses;
import com.mlin.notas.dungeon.DungeonPiece;
import com.mlin.notas.dungeon.DungeonPlan;
import com.mlin.notas.dungeon.DungeonRegistry;
import com.mlin.notas.dungeon.DungeonTheme;
import com.mlin.notas.util.Pos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Game tests das masmorras: registro, portao que abre com a chave, mini-chefe que solta a chave e o cofre configurado. */
public class DungeonGameTest {
	@GameTest(environment = "mlinnotas-gametest:dungeons")
	public void dungeonStructuresAreRegisteredInOneSet(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (DungeonTheme theme : DungeonTheme.values()) {
			helper.assertTrue(structures.get(DungeonRegistry.structureKey(theme)).isPresent(), "estrutura mlinnotas:" + theme.id() + " registrada");
		}
		StructureSet set = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, MlinNotas.id("dungeons"))).value();
		helper.assertTrue(set.structures().size() == DungeonTheme.values().length, "um structure set com os 4 temas");
		helper.succeed();
	}

	private static BlockHitResult hit(GameTestHelper helper, BlockPos rel) {
		BlockPos abs = helper.absolutePos(rel);
		return new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
	}

	@GameTest(environment = "mlinnotas-gametest:dungeons")
	public void gateOpensOnlyWithTheKeyAndKeepsIt(GameTestHelper helper) {
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(x, y, 4, DungeonRegistry.GATE);
			}
		}
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerLevel level = helper.getLevel();
		BlockPos center = new BlockPos(3, 2, 4);
		// sem a chave: nada acontece
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.DIRT));
		level.getBlockState(helper.absolutePos(center)).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, center));
		helper.assertBlockPresent(DungeonRegistry.GATE, center);
		// com a chave: o portao inteiro some e a chave continua na mao
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DungeonRegistry.KEY, 2));
		level.getBlockState(helper.absolutePos(center)).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, center));
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.assertBlockNotPresent(DungeonRegistry.GATE, new BlockPos(x, y, 4));
			}
		}
		helper.assertTrue(player.getMainHandItem().is(DungeonRegistry.KEY) && player.getMainHandItem().getCount() == 2, "a chave nao e gasta no portao");
		helper.succeed();
	}

	@GameTest(maxTicks = 60, environment = "mlinnotas-gametest:dungeons")
	public void miniBossesAreReinforcedAndDropTheKey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		int i = 0;
		for (DungeonTheme theme : DungeonTheme.values()) {
			BlockPos at = helper.absolutePos(new BlockPos(1 + 2 * i++, 1, 3));
			Mob boss = DungeonBosses.create(level, theme, at);
			helper.assertTrue(boss != null, "mini-chefe de " + theme);
			level.addFreshEntity(boss);
			helper.assertTrue(boss.getMaxHealth() >= DungeonBosses.HEALTH, theme + ": vida reforcada " + boss.getMaxHealth());
			helper.assertTrue(DungeonBosses.themeOf(boss) == theme, theme + ": marcado com o tema");
			helper.assertTrue(boss.hasCustomName() && boss.isPersistenceRequired(), theme + ": nome e nao some");
			helper.assertTrue(!boss.getMainHandItem().isEmpty(), theme + ": armado");
			helper.assertTrue(DungeonBosses.tracked(boss), theme + ": barra de chefe");
			boss.kill(level);
			List<ItemEntity> keys = level.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(3), e -> e.getItem().is(DungeonRegistry.KEY));
			helper.assertTrue(!keys.isEmpty(), theme + ": soltou a chave");
			keys.forEach(ItemEntity::discard);
		}
		helper.assertTrue(DungeonBosses.keysFor(0) == 1 && DungeonBosses.keysFor(2) == 2 && DungeonBosses.keysFor(9) == 4, "chaves por jogador");
		helper.succeed();
	}

	@GameTest(environment = "mlinnotas-gametest:dungeons")
	public void vaultTakesTheDungeonKeyAndChestsGetLoot(GameTestHelper helper) {
		long seed = 42L;
		DungeonTheme theme = DungeonTheme.CRYPT;
		DungeonPiece probe = new DungeonPiece(theme, seed, BlockPos.ZERO);
		Pos vault = probe.plan().marker(DungeonPlan.MARKER_VAULT);
		BlockPos target = helper.absolutePos(new BlockPos(4, 3, 4));
		BlockPos origin = target.offset(-vault.x(), -vault.y(), -vault.z());
		DungeonPiece piece = new DungeonPiece(theme, seed, origin);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(7, 7, 7)));
		piece.postProcess(helper.getLevel(), helper.getLevel().structureManager(), helper.getLevel().getChunkSource().getGenerator(),
				RandomSource.create(seed), box, ChunkPos.containing(origin), origin);
		helper.assertBlockPresent(Blocks.VAULT, new BlockPos(4, 3, 4));
		VaultBlockEntity entity = (VaultBlockEntity) helper.getLevel().getBlockEntity(target);
		helper.assertTrue(entity != null && entity.getConfig().keyItem().is(DungeonRegistry.KEY), "cofre abre com a Chave da Masmorra");
		helper.assertTrue(entity.getConfig().lootTable().identifier().equals(MlinNotas.id("chests/" + DungeonPlan.LOOT_VAULT)), "loot do cofre");
		helper.succeed();
	}
}
