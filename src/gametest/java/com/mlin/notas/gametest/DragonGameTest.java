package com.mlin.notas.gametest;

import com.mlin.notas.boss.BossProjectile;
import com.mlin.notas.dragon.DragonBoss;
import com.mlin.notas.dragon.DragonRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests da luta reformulada do dragao: vida escalada, meteoro do vazio (aviso, queda e dano) e onda de choque do
 * mergulho (acerta quem esta no chao, quem esta no ar escapa). Dragao parado (sem IA) no mundo de teste.
 */
public class DragonGameTest {
	private static EnderDragon dragon(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EnderDragon dragon = EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.COMMAND);
		BlockPos at = helper.absolutePos(new BlockPos(4, 12, 4));
		dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		dragon.setNoAi(true);
		level.addFreshEntity(dragon);
		return dragon;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.END_STONE);
			}
		}
	}

	@SuppressWarnings("removal")
	private static ServerPlayer survivor(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		// o jogador falso fica invulneravel ate o "cliente" avisar que carregou
		p.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		p.teleportTo(o.getX() + x, o.getY() + y, o.getZ() + z);
		return p;
	}

	private static String debug(GameTestHelper helper, ServerPlayer p) {
		return "pos=" + p.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + " modo=" + p.gameMode() + " abil=" + p.getAbilities().invulnerable
				+ " loaded=" + p.connection.hasClientLoaded() + " dif=" + helper.getLevel().getDifficulty() + " vida=" + p.getHealth() + " chao="
				+ (helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ()) - helper.absolutePos(BlockPos.ZERO).getY());
	}

	private static void cleanup(GameTestHelper helper, EnderDragon dragon, ServerPlayer... players) {
		dragon.discard();
		for (ServerPlayer p : players) {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	@GameTest
	public void healthScalesOnlyUpWithPlayers(GameTestHelper helper) {
		EnderDragon dragon = dragon(helper);
		ServerLevel level = helper.getLevel();
		DragonBoss.scale(level, dragon, 2);
		helper.assertTrue(dragon.getMaxHealth() == 400.0F && dragon.getHealth() == 400.0F, "2 jogadores: 400 cheio, deu " + dragon.getHealth() + "/" + dragon.getMaxHealth());
		dragon.setHealth(200.0F);
		DragonBoss.scale(level, dragon, 3);
		helper.assertTrue(dragon.getMaxHealth() == 600.0F && Math.abs(dragon.getHealth() - 300.0F) < 0.01, "3 jogadores: 600 e mantem metade, deu "
				+ dragon.getHealth() + "/" + dragon.getMaxHealth());
		DragonBoss.scale(level, dragon, 1);
		helper.assertTrue(dragon.getMaxHealth() == 600.0F, "jogador que sai nao diminui a vida");
		dragon.setHealth(250.0F);
		helper.assertTrue(DragonBoss.phase(dragon) == 2, "abaixo da metade = fase 2");
		dragon.setHealth(100.0F);
		helper.assertTrue(DragonBoss.phase(dragon) == 3, "abaixo de 20% = fase 3");
		dragon.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 160)
	public void voidMeteorWarnsThenHitsThePlayer(GameTestHelper helper) {
		floor(helper);
		EnderDragon dragon = dragon(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer target = survivor(helper, 2.5, 1, 2.5);
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		// abre o teto do lote de teste em cima do alvo (o meteoro cai de 16 blocos)
		for (int y = 2; y <= DragonRules.METEOR_HEIGHT + 3; y++) {
			for (int dx = 1; dx <= 3; dx++) {
				for (int dz = 1; dz <= 3; dz++) {
					level.setBlockAndUpdate(o.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		DragonBoss.markMeteor(level, dragon, o.getX() + 2.5, o.getY() + 2, o.getZ() + 2.5, 1, DragonRules.METEOR_WARNING);
		helper.assertTrue(DragonBoss.pendingMeteors(dragon) == 1, "meteoro marcado no chao");
		helper.onEachTick(() -> DragonBoss.tickAttacks(level, dragon));
		helper.runAfterDelay(DragonRules.METEOR_WARNING - 5, () -> {
			helper.assertTrue(target.getHealth() == target.getMaxHealth(), "nada de dano durante o aviso");
			helper.assertTrue(level.getEntitiesOfClass(BossProjectile.class, new AABB(o).inflate(20)).isEmpty(), "meteoro ainda nao caiu");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(DragonBoss.pendingMeteors(dragon) == 0, "meteoro saiu do ceu");
			helper.assertTrue(target.getHealth() < target.getMaxHealth(), "jogador atingido pelo meteoro: " + debug(helper, target));
			cleanup(helper, dragon, target);
		});
	}

	@GameTest(maxTicks = 60)
	public void shockwaveHitsGroundedPlayersOnly(GameTestHelper helper) {
		floor(helper);
		EnderDragon dragon = dragon(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer grounded = survivor(helper, 4.5, 1, 1.5);
		ServerPlayer jumping = survivor(helper, 1.5, 3.5, 4.5);
		jumping.setNoGravity(true);
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		DragonBoss.startWave(level, dragon, new Vec3(o.getX() + 1.5, o.getY() + 1, o.getZ() + 1.5), 2);
		helper.onEachTick(() -> DragonBoss.tickAttacks(level, dragon));
		helper.runAfterDelay(DragonRules.SHOCKWAVE_TICKS + 3, () -> {
			helper.assertTrue(grounded.getHealth() < grounded.getMaxHealth(), "a onda acerta quem esta no chao: " + debug(helper, grounded));
			helper.assertTrue(jumping.getHealth() == jumping.getMaxHealth(), "quem pula escapa da onda");
			cleanup(helper, dragon, grounded, jumping);
			helper.succeed();
		});
	}

}
