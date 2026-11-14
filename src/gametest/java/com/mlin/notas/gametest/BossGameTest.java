package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.boss.BossKind;
import com.mlin.notas.boss.BossMob;
import com.mlin.notas.boss.BossRegistry;
import com.mlin.notas.boss.BossRules;
import com.mlin.notas.boss.BossSummoning;
import com.mlin.notas.boss.IgneousColossus;
import com.mlin.notas.boss.LairKind;
import com.mlin.notas.boss.LairPiece;
import com.mlin.notas.boss.LairPlan;
import com.mlin.notas.end.CitadelPlanner;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import com.mlin.notas.util.Pos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Game tests dos chefes: altares (consomem a oferenda, recusam sem ela ou com o chefe vivo), vida escalada, fase 2,
 * item garantido na morte, escamas do dragao, despertar do colosso e as pecas dos covis.
 */
public class BossGameTest {
	private static final BossKind[] ALTAR_BOSSES = {BossKind.COBALT_GOLEM, BossKind.ABYSSAL_GUARDIAN, BossKind.ASTRAL_HERALD};

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static net.minecraft.world.level.block.Block altarBlock(BossKind kind) {
		return switch (kind) {
			case COBALT_GOLEM -> BossRegistry.COBALT_ALTAR;
			case ABYSSAL_GUARDIAN -> BossRegistry.ABYSSAL_ALTAR;
			default -> BossRegistry.ASTRAL_ALTAR;
		};
	}

	// os testes de altar rodam no Nether: o altar recusa se ha outro chefe do mesmo tipo a 48 blocos, e outros testes
	// (fase 2, morte) criam chefes no Overworld ao mesmo tempo
	private static List<? extends BossMob> bosses(GameTestHelper helper, BossKind kind) {
		return helper.getLevel().getEntities(BossRegistry.type(kind), new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16), e -> e.isAlive());
	}

	@GameTest
	public void offeringItemsMatchTheKinds(GameTestHelper helper) {
		for (BossKind kind : ALTAR_BOSSES) {
			Item item = BossRegistry.offeringItem(kind);
			helper.assertTrue(BuiltInRegistries.ITEM.getKey(item).toString().equals(kind.offering()), kind + " oferenda " + item);
			helper.assertTrue(BossRegistry.dropItem(kind) != null, "drop de " + kind);
		}
		helper.assertTrue(VanillaRemakePiece.parse(CitadelPlanner.ALTAR).is(BossRegistry.ASTRAL_ALTAR), "cofre da cidadela usa o altar astral");
		helper.succeed();
	}

	@GameTest(maxTicks = 60, dimension = "minecraft:the_nether")
	public void altarsRefuseWithoutOfferingThenSummonAndConsume(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos altarRel = new BlockPos(1, 1, 1);
		for (BossKind kind : ALTAR_BOSSES) {
			helper.setBlock(altarRel, altarBlock(kind));
			BlockPos altar = helper.absolutePos(altarRel);
			Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
			player.setPos(altar.getX() + 3.5, altar.getY(), altar.getZ() + 0.5);
			player.getInventory().add(new ItemStack(BossRegistry.offeringItem(kind), kind.offeringCount() - 1));
			if (kind.extraOffering() != null) {
				player.getInventory().add(new ItemStack(BossRegistry.extraOfferingItem(kind), kind.extraOfferingCount()));
			}
			helper.assertTrue(BossSummoning.trySummon(level, altar, player, kind) == BossSummoning.Result.MISSING_OFFERING, kind + ": recusa com 3");
			helper.assertTrue(bosses(helper, kind).isEmpty(), kind + ": nada invocado");
			helper.assertTrue(player.getInventory().countItem(BossRegistry.offeringItem(kind)) == kind.offeringCount() - 1, "nada consumido");

			player.getInventory().add(new ItemStack(BossRegistry.offeringItem(kind), 2));
			BossSummoning.Result result = BossSummoning.trySummon(level, altar, player, kind);
			helper.assertTrue(result == BossSummoning.Result.SUMMONED, kind + ": invoca com 5, deu " + result);
			helper.assertTrue(player.getInventory().countItem(BossRegistry.offeringItem(kind)) == 1, kind + ": consumiu 4");
			List<? extends BossMob> found = bosses(helper, kind);
			helper.assertTrue(found.size() == 1, kind + ": um chefe, achou " + found.size());
			BossMob boss = found.getFirst();
			helper.assertTrue(boss.spawning() && boss.scaled(), kind + ": surgindo e com vida ajustada");
			helper.assertTrue(boss.isPersistenceRequired(), kind + ": nao some");
			helper.assertTrue(BossSummoning.trySummon(level, altar, player, kind) == BossSummoning.Result.BUSY, kind + ": um de cada vez");
			boss.discard();
		}
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void altarBlockRightClickSummons(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(2, 1, 2, BossRegistry.COBALT_ALTAR);
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		BlockPos altar = helper.absolutePos(new BlockPos(2, 1, 2));
		player.setPos(altar.getX() + 3.5, altar.getY(), altar.getZ() + 0.5);
		player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK, 4));
		helper.getLevel().getBlockState(altar).useWithoutItem(helper.getLevel(), player,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(altar), net.minecraft.core.Direction.UP, altar, false));
		helper.assertTrue(bosses(helper, BossKind.COBALT_GOLEM).size() == 1, "golem invocado pelo clique");
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_BLOCK) == 0, "4 blocos de ferro gastos");
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(BossRegistry.COBALT_ALTAR), "altar continua");
		bosses(helper, BossKind.COBALT_GOLEM).forEach(BossMob::discard);
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void healthScalesWithExtraPlayers(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		List<ServerPlayer> players = List.of(helper.makeMockServerPlayerInLevel(), helper.makeMockServerPlayerInLevel(), helper.makeMockServerPlayerInLevel());
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		for (ServerPlayer p : players) {
			p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		}
		BossMob golem = helper.spawn(BossRegistry.COBALT_GOLEM, new BlockPos(4, 1, 4));
		golem.prepareForFight(level);
		double expected = BossRules.scaledHealth(BossKind.COBALT_GOLEM.baseHealth(), 3);
		boolean ok = Math.abs(golem.getMaxHealth() - expected) < 0.01 && golem.getHealth() == golem.getMaxHealth();
		golem.discard();
		for (ServerPlayer p : players) {
			level.getServer().getPlayerList().remove(p);
		}
		helper.assertTrue(ok, "3 jogadores = vida x1,5 (" + expected + "), deu " + golem.getMaxHealth());
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void entersPhaseTwoBelowHalfHealth(GameTestHelper helper) {
		floor(helper);
		BossMob guardian = helper.spawn(BossRegistry.ABYSSAL_GUARDIAN, new BlockPos(4, 1, 4));
		helper.assertTrue(guardian.phase() == 1, "comeca na fase 1");
		helper.runAfterDelay(2, () -> guardian.setHealth(guardian.getMaxHealth() * 0.51F));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(guardian.phase() == 1, "51% ainda fase 1");
			guardian.setHealth(guardian.getMaxHealth() * 0.49F);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(guardian.phase() == 2, "49% = fase 2");
			guardian.discard();
		});
	}

	@GameTest(maxTicks = 40)
	public void everyBossDropsItsItemOnDeath(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		for (BossKind kind : BossKind.values()) {
			BossMob boss = helper.spawn(BossRegistry.type(kind), new BlockPos(4, 1, 4));
			boss.kill(level);
			Item drop = BossRegistry.dropItem(kind);
			List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(4), e -> e.getItem().is(drop));
			helper.assertTrue(!items.isEmpty(), kind + " soltou " + drop);
			LootTable table = level.getServer().reloadableRegistries().getLootTable(boss.getLootTable().orElseThrow());
			helper.assertTrue(table != LootTable.EMPTY, kind + " tem loot table extra");
			items.forEach(ItemEntity::discard);
		}
		helper.succeed();
	}

	@GameTest
	public void dragonDropsScalesWhenKilled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EnderDragon dragon = EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.COMMAND);
		BlockPos at = helper.absolutePos(new BlockPos(4, 4, 4));
		dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		level.addFreshEntity(dragon);
		dragon.kill(level);
		int scales = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6), e -> e.getItem().is(BossRegistry.DRAGON_SCALE)).stream()
				.mapToInt(e -> e.getItem().getCount()).sum();
		// fora da luta do End nao e a "primeira morte": 1 a 2 escamas (e o Molde do Fim)
		helper.assertTrue(scales >= 1 && scales <= 2, "1 a 2 escamas, deu " + scales);
		helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6),
				e -> e.getItem().is(com.mlin.notas.smithing.SmithingTemplates.item(com.mlin.notas.smithing.SmithingChain.END))).isEmpty(), "Molde do Fim");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void colossusOnlyWakesWithTheForgeKey(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		IgneousColossus colossus = helper.spawn(BossRegistry.IGNEOUS_COLOSSUS, new BlockPos(4, 1, 4));
		colossus.setDormant(true);
		Player near = helper.makeMockServerPlayer(GameType.SURVIVAL);
		near.setPos(colossus.getX() + 3, colossus.getY(), colossus.getZ());
		// apanhar nao acorda nem machuca
		float before = colossus.getHealth();
		colossus.hurtServer(level, level.damageSources().playerAttack(near), 10.0F);
		helper.assertTrue(colossus.dormant() && colossus.getHealth() == before, "adormecido nao apanha nem acorda");

		IgneousColossus throne = helper.spawn(BossRegistry.IGNEOUS_COLOSSUS, new BlockPos(1, 1, 1));
		throne.setDormant(true);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(colossus.dormant() && throne.dormant(), "jogador perto por 1 s nao acorda ninguem");
			// chave no proprio colosso: acorda e gasta
			ItemStack key = new ItemStack(BossRegistry.FORGE_KEY, 2);
			near.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, key);
			key.interactLivingEntity(near, colossus, net.minecraft.world.InteractionHand.MAIN_HAND);
			helper.assertTrue(!colossus.dormant() && colossus.spawning() && colossus.scaled(), "a chave acorda com a animacao de surgimento");
			helper.assertTrue(key.getCount() == 1, "chave gasta");
			// chave no trono (bloco perto do outro colosso)
			BlockPos block = helper.absolutePos(new BlockPos(1, 0, 2));
			net.minecraft.world.InteractionResult used = key.useOn(new net.minecraft.world.item.context.UseOnContext(near,
					net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(block),
					net.minecraft.core.Direction.UP, block, false)));
			helper.assertTrue(used.consumesAction() && !throne.dormant() && key.isEmpty(), "chave no trono acorda o colosso, deu " + used);
			colossus.discard();
			throne.discard();
			helper.succeed();
		});
	}

	@GameTest
	public void witherGetsBossHealthAndRespawnBlocksDoNotExplode(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var wither = EntityTypes.WITHER.create(level, EntitySpawnReason.TRIGGERED);
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		wither.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		wither.makeInvulnerable();
		level.addFreshEntity(wither);
		boolean ok = wither.getMaxHealth() == (float) BossRules.WITHER_HEALTH && wither.getHealth() == wither.getMaxHealth()
				&& wither.hasAttached(com.mlin.notas.boss.WitherBuff.SCALED);
		String got = wither.getHealth() + "/" + wither.getMaxHealth();
		wither.discard();
		helper.assertTrue(ok, "wither com " + BossRules.WITHER_HEALTH + " de vida cheia, deu " + got);
		// cama e ancora: o guarda do End so recusa o uso que explodiria
		var guard = com.mlin.notas.dragon.RespawnExplosionGuard.class;
		helper.assertTrue(com.mlin.notas.dragon.RespawnExplosionGuard.wouldExplode(Blocks.BED.red().defaultBlockState(), ItemStack.EMPTY), "cama explode");
		var anchor = Blocks.RESPAWN_ANCHOR.defaultBlockState();
		helper.assertTrue(!com.mlin.notas.dragon.RespawnExplosionGuard.wouldExplode(anchor, ItemStack.EMPTY), "ancora vazia nao explode");
		var charged = anchor.setValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE, 2);
		helper.assertTrue(com.mlin.notas.dragon.RespawnExplosionGuard.wouldExplode(charged, ItemStack.EMPTY), "ancora carregada explode");
		helper.assertTrue(!com.mlin.notas.dragon.RespawnExplosionGuard.wouldExplode(charged, new ItemStack(net.minecraft.world.item.Items.GLOWSTONE)),
				"recarregar com pedra luminosa e permitido");
		helper.assertTrue(guard != null && !com.mlin.notas.dragon.RespawnExplosionGuard.wouldExplode(Blocks.STONE.defaultBlockState(), ItemStack.EMPTY), "pedra");
		helper.succeed();
	}

	@GameTest
	public void forgeKeyIsCraftedAndDoesNothingAwayFromAColossus(GameTestHelper helper) {
		floor(helper);
		var recipe = helper.getLevel().getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(Registries.RECIPE, MlinNotas.id("forge_key")));
		helper.assertTrue(recipe.isPresent(), "receita da Chave da Forja");
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack key = new ItemStack(BossRegistry.FORGE_KEY);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, key);
		BlockPos block = helper.absolutePos(new BlockPos(3, 0, 3));
		net.minecraft.world.InteractionResult used = key.useOn(new net.minecraft.world.item.context.UseOnContext(player,
				net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(block),
				net.minecraft.core.Direction.UP, block, false)));
		helper.assertTrue(!used.consumesAction() && key.getCount() == 1, "sem colosso por perto a chave nao e gasta");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void heraldAltarAlsoNeedsADragonScale(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(5, 1, 5, BossRegistry.ASTRAL_ALTAR);
		BlockPos altar = helper.absolutePos(new BlockPos(5, 1, 5));
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(altar.getX() + 3.5, altar.getY(), altar.getZ() + 0.5);
		player.getInventory().add(new ItemStack(BossRegistry.offeringItem(BossKind.ASTRAL_HERALD), 4));
		helper.assertTrue(BossSummoning.trySummon(level, altar, player, BossKind.ASTRAL_HERALD) == BossSummoning.Result.MISSING_OFFERING,
				"4 nucleos sem escama: recusa");
		helper.assertTrue(player.getInventory().countItem(BossRegistry.offeringItem(BossKind.ASTRAL_HERALD)) == 4, "nada gasto");
		player.getInventory().add(new ItemStack(BossRegistry.DRAGON_SCALE, 2));
		// pode haver outro arauto do teste de altares no Nether: so confere o consumo se invocou
		BossSummoning.Result result = BossSummoning.trySummon(level, altar, player, BossKind.ASTRAL_HERALD);
		if (result == BossSummoning.Result.SUMMONED) {
			helper.assertTrue(player.getInventory().countItem(BossRegistry.DRAGON_SCALE) == 1, "gasta 1 escama");
			helper.assertTrue(player.getInventory().countItem(BossRegistry.offeringItem(BossKind.ASTRAL_HERALD)) == 0, "gasta os 4 nucleos");
		} else {
			helper.assertTrue(result == BossSummoning.Result.BUSY, "com escama so recusa se ja houver arauto, deu " + result);
		}
		level.getEntities(BossRegistry.ASTRAL_HERALD, new AABB(altar).inflate(8), e -> true).forEach(BossMob::discard);
		helper.succeed();
	}

	/** Monta so a parte do covil que cai na area do teste, com o marcador em (4, 3, 4). */
	private static LairPiece build(GameTestHelper helper, LairKind kind, long seed, String marker) {
		LairPiece probe = new LairPiece(kind, seed, BlockPos.ZERO);
		Pos m = probe.plan().marker(marker);
		BlockPos origin = helper.absolutePos(new BlockPos(4, 3, 4)).offset(-m.x(), -m.y(), -m.z());
		LairPiece piece = new LairPiece(kind, seed, origin);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(7, 7, 7)));
		piece.postProcess(helper.getLevel(), helper.getLevel().structureManager(), helper.getLevel().getChunkSource().getGenerator(),
				RandomSource.create(seed), box, ChunkPos.containing(origin), origin);
		return piece;
	}

	@GameTest
	public void shrineAndSanctumPlaceTheirAltars(GameTestHelper helper) {
		build(helper, LairKind.COBALT_SHRINE, 3L, LairPlan.MARKER_ALTAR);
		helper.assertBlockPresent(BossRegistry.COBALT_ALTAR, 4, 3, 4);
		helper.assertBlockPresent(Blocks.POLISHED_ANDESITE, 4, 2, 4);
		helper.assertTrue(helper.getBlockState(new BlockPos(4, 4, 4)).isAir(), "ar em cima do altar");
		build(helper, LairKind.ABYSSAL_SANCTUM, 3L, LairPlan.MARKER_ALTAR);
		helper.assertBlockPresent(BossRegistry.ABYSSAL_ALTAR, 4, 3, 4);
		helper.assertBlockPresent(Blocks.CANDLE, 6, 3, 4);
		helper.succeed();
	}

	@GameTest
	public void lairChestsHaveTheirLoot(GameTestHelper helper) {
		for (LairKind kind : LairKind.values()) {
			build(helper, kind, 9L, LairPlan.MARKER_CHEST);
			// o anel de lava da forja cai na area: tira para nao escorrer para fora do teste
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(7, 7, 7))) {
				if (!helper.getBlockState(p).getFluidState().isEmpty()) {
					helper.setBlock(p, Blocks.STONE);
				}
			}
			helper.assertBlockPresent(Blocks.CHEST, 4, 3, 4);
			ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(4, 3, 4), ChestBlockEntity.class);
			helper.assertTrue(LairPiece.loot(kind).equals(chest.getLootTable()), kind + " loot " + chest.getLootTable());
			helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(LairPiece.loot(kind)) != LootTable.EMPTY,
					"loot table " + LairPiece.loot(kind).identifier() + " carregada");
			chest.clearContent();
			helper.setBlock(4, 3, 4, Blocks.AIR);
		}
		helper.succeed();
	}

	@GameTest
	public void forgeThroneHoldsASleepingColossus(GameTestHelper helper) {
		build(helper, LairKind.COLOSSUS_FORGE, 4L, LairPlan.MARKER_BOSS);
		helper.assertBlockPresent(Blocks.POLISHED_BLACKSTONE, 4, 2, 4);
		List<IgneousColossus> found = helper.getLevel().getEntitiesOfClass(IgneousColossus.class, new AABB(helper.absolutePos(new BlockPos(4, 3, 4))).inflate(2));
		helper.assertTrue(found.size() == 1, "um colosso no trono, achou " + found.size());
		helper.assertTrue(found.getFirst().dormant() && found.getFirst().isPersistenceRequired(), "dormindo e permanente");
		found.forEach(IgneousColossus::discard);
		helper.succeed();
	}

	@GameTest
	public void lairStructuresAreRegistered(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var sets = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
		for (LairKind kind : LairKind.values()) {
			helper.assertTrue(structures.get(BossRegistry.lairKey(kind)).isPresent(), "estrutura " + kind.id());
			helper.assertTrue(sets.get(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET, MlinNotas.id(kind.id()))).isPresent(),
					"structure set " + kind.id());
		}
		helper.succeed();
	}
}
