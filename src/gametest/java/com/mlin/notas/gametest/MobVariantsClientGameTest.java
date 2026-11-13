package com.mlin.notas.gametest;

import com.mlin.notas.variants.MobVariant;
import com.mlin.notas.variants.MobVariantsServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Game test de cliente das variantes de bioma: as 6 variantes lado a lado (textura recolorida) e, para comparar, os
 * mobs vanilla na mesma posicao. Tambem confere que o attachment chegou ao cliente.
 */
@SuppressWarnings("UnstableApiUsage")
public class MobVariantsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("gamerule advance_time false");
			singleplayer.getServer().runCommand("difficulty easy");

			BlockPos center = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos base = player.blockPosition();
				ServerLevel level = player.level();
				// teto para os mortos-vivos nao pegarem fogo ao sol
				for (int x = -9; x <= 9; x++) {
					for (int z = 2; z <= 10; z++) {
						level.setBlockAndUpdate(base.offset(x, 5, z), Blocks.SMOOTH_STONE.defaultBlockState());
					}
				}
				return base.offset(0, 0, 6);
			});

			spawnRow(singleplayer, center, true);
			context.waitFor(mc -> mc.level != null && countClientVariants(mc.level.entitiesForRendering()) == MobVariant.values().length, 200);
			context.getInput().lookAt(center.above());
			context.waitTicks(20);
			context.takeScreenshot("mlinnotas-mob-variants");
			// mirando o Creeper de Cristal: nome da variante no HUD WAILA e acima da cabeca
			// chega perto (alcance de entidade = 3 blocos)
			singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
					.teleportTo(center.getX() + 0.5 + (MobVariant.CRYSTAL_CREEPER.ordinal() - (MobVariant.values().length - 1) / 2.0) * 2.4, center.getY(), center.getZ() - 2.0));
			context.waitTicks(10);
			context.runOnClient(mc -> {
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof net.minecraft.world.entity.monster.Creeper) {
						mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, e.position().add(0, 1.0, 0));
					}
				}
			});
			context.waitFor(mc -> mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult, 100);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-mob-variants-name");

			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				List<Entity> toRemove = new ArrayList<>();
				level.getAllEntities().forEach(e -> {
					if (e instanceof Mob) {
						toRemove.add(e);
					}
				});
				toRemove.forEach(Entity::discard);
			});
			spawnRow(singleplayer, center, false);
			context.waitTicks(20);
			context.takeScreenshot("mlinnotas-mob-variants-vanilla");
		}
	}

	private static int countClientVariants(Iterable<Entity> entities) {
		AtomicInteger count = new AtomicInteger();
		entities.forEach(e -> {
			if (e.getAttached(MobVariantsServer.VARIANT) != null) {
				count.incrementAndGet();
			}
		});
		return count.get();
	}

	private static void spawnRow(TestSingleplayerContext singleplayer, BlockPos center, boolean variants) {
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			MobVariant[] all = MobVariant.values();
			for (int i = 0; i < all.length; i++) {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(all[i].baseType()));
				Mob mob = (Mob) type.create(level, EntitySpawnReason.COMMAND);
				double x = center.getX() + 0.5 + (i - (all.length - 1) / 2.0) * 2.4;
				mob.snapTo(x, center.getY(), center.getZ() + 0.5, 180.0F, 0.0F);
				mob.setYHeadRot(180.0F);
				mob.setYBodyRot(180.0F);
				mob.setNoAi(true);
				mob.setPersistenceRequired();
				if (variants) {
					MobVariantsServer.apply(mob, all[i]);
				}
				level.addFreshEntity(mob);
			}
		});
	}
}
