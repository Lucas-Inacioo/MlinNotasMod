package com.mlin.notas.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Invocacao de chefe num altar: confere oferenda e se ja tem um chefe ativo, consome e faz o chefe surgir. */
public final class BossSummoning {
	public enum Result {
		SUMMONED,
		MISSING_OFFERING,
		BUSY,
		NO_ROOM
	}

	private BossSummoning() {
	}

	public static Result trySummon(ServerLevel level, BlockPos altar, Player player, BossKind kind) {
		EntityType<? extends BossMob> type = BossRegistry.type(kind);
		AABB area = new AABB(altar).inflate(BossRules.ALTAR_BUSY_RADIUS);
		if (!level.getEntities(type, area, e -> e.isAlive()).isEmpty()) {
			return Result.BUSY;
		}
		Item offering = BossRegistry.offeringItem(kind);
		boolean free = player.hasInfiniteMaterials();
		if (!free && player.getInventory().countItem(offering) < kind.offeringCount()) {
			return Result.MISSING_OFFERING;
		}
		BossMob boss = type.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) {
			return Result.NO_ROOM;
		}
		Vec3 spot = spawnSpot(level, altar, boss);
		if (spot == null) {
			return Result.NO_ROOM;
		}
		if (!free) {
			ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.is(offering), kind.offeringCount(), false);
		}
		float yaw = (float) (Math.atan2(player.getZ() - spot.z, player.getX() - spot.x) * 180 / Math.PI) - 90.0F;
		boss.snapTo(spot, yaw, 0.0F);
		boss.setYHeadRot(yaw);
		boss.yBodyRot = yaw;
		boss.startSpawnAnimation();
		level.addFreshEntity(boss);
		boss.prepareForFight(level);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, spot.x, spot.y + 1, spot.z, 1, 0, 0, 0, 0);
		for (ServerPlayer near : boss.playersAround(level, BossRules.PLAYER_SCAN_RADIUS)) {
			BossMob.tell(near, Component.translatable("message.mlinnotas.boss.summoned", boss.getDisplayName()));
		}
		return Result.SUMMONED;
	}

	/** Em cima do altar se couber; senao o lugar livre mais perto em volta (ate 6 blocos). */
	static @Nullable Vec3 spawnSpot(ServerLevel level, BlockPos altar, BossMob boss) {
		for (int r = 0; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = 1; dy >= -2; dy--) {
						Vec3 p = new Vec3(altar.getX() + dx + 0.5, altar.getY() + dy, altar.getZ() + dz + 0.5);
						if (level.noCollision(boss, boss.getType().getDimensions().makeBoundingBox(p))
								&& !level.getBlockState(BlockPos.containing(p).below()).isAir()) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}
}
