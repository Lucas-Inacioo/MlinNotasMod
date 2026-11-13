package com.mlin.notas.gametest;

import com.mlin.notas.boss.BossKind;
import com.mlin.notas.boss.BossMob;
import com.mlin.notas.boss.BossRegistry;
import com.mlin.notas.boss.IgneousColossus;
import com.mlin.notas.boss.LairKind;
import com.mlin.notas.boss.LairPiece;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Set;

/**
 * Game test de cliente dos chefes: screenshot de cada chefe com o modelo proprio (e do colosso dormindo) e dos tres
 * covis montados num mundo plano. Screenshots em build/run/clientGameTest/screenshots/mlinnotas-boss-*.
 */
@SuppressWarnings("UnstableApiUsage")
public class BossClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamemode creative @p");
			int i = 0;
			for (BossKind kind : BossKind.values()) {
				boss(context, world, kind, 40 * (++i), false);
			}
			boss(context, world, BossKind.IGNEOUS_COLOSSUS, 40 * (++i), true);
			lair(context, world, LairKind.COBALT_SHRINE, 400, 2, 16, 24, 30.0F);
			lair(context, world, LairKind.COLOSSUS_FORGE, 500, 2, 18, 28, 30.0F);
			lair(context, world, LairKind.ABYSSAL_SANCTUM, 600, 6, 3, 9, 5.0F);
		}
	}

	/** Um chefe parado, de frente para a camera. */
	private static void boss(ClientGameTestContext context, TestSingleplayerContext world, BossKind kind, int dx, boolean dormant) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			int x = player.getBlockX() + dx;
			int z = player.getBlockZ();
			level.getChunk(x >> 4, z >> 4);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
			int distance = kind == BossKind.IGNEOUS_COLOSSUS ? 9 : 7;
			BlockPos stand = ground.offset(0, 0, distance);
			player.teleportTo(level, stand.getX() + 0.5, stand.getY() + (kind == BossKind.ASTRAL_HERALD ? 1.0 : 0.0), stand.getZ() + 0.5, Set.of(),
					180.0F, kind == BossKind.IGNEOUS_COLOSSUS && !dormant ? -8.0F : 0.0F, true);
			BossMob boss = BossRegistry.type(kind).create(level, EntitySpawnReason.COMMAND);
			boss.snapTo(ground.getX() + 0.5, ground.getY() + (kind == BossKind.ASTRAL_HERALD ? 1.0 : 0.0), ground.getZ() + 0.5, 0.0F, 0.0F);
			boss.setYHeadRot(0.0F);
			boss.yBodyRot = 0.0F;
			boss.setNoAi(true);
			if (boss instanceof IgneousColossus colossus) {
				colossus.setDormant(dormant);
			}
			level.addFreshEntity(boss);
		});
		context.waitTicks(30);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("mlinnotas-boss-" + kind.id() + (dormant ? "-dormant" : ""));
	}

	/** Monta o covil inteiro e fotografa de (camX, camY, camZ) relativo a origem, olhando para o norte. */
	private static void lair(ClientGameTestContext context, TestSingleplayerContext world, LairKind kind, int dx, int lift, int camY, int camZ,
			float pitch) {
		BlockPos origin = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			int x = player.getBlockX() + dx;
			int z = player.getBlockZ();
			level.getChunk(x >> 4, z >> 4);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			BlockPos o = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1 + lift, z);
			player.teleportTo(level, o.getX() + 0.5, o.getY() + camY, o.getZ() + camZ + 0.5, Set.of(), 180.0F, pitch, true);
			return o;
		});
		context.waitTicks(10);
		world.getConnection().waitForChunksRender();
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			LairPiece piece = new LairPiece(kind, 7L, origin);
			BoundingBox box = piece.getBoundingBox();
			piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(7L), box,
					ChunkPos.containing(origin), origin);
		});
		context.waitTicks(20);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("mlinnotas-boss-lair-" + kind.id());
	}
}
