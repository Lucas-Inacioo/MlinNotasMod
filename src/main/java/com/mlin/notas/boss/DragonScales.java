package com.mlin.notas.boss;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

/**
 * Escamas de Dragao: o dragao vanilla nao tem loot table de verdade, entao quando ele e removido como morto (fim da
 * animacao de morte ou /kill) soltamos 2 a 4 escamas. Na luta do End elas caem em cima do portal de saida; fora dela,
 * onde o dragao estava.
 */
public final class DragonScales {
	private DragonScales() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof EnderDragon dragon && entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
				drop(level, dragon);
			}
		});
	}

	public static int drop(ServerLevel level, EnderDragon dragon) {
		int count = BossRules.dragonScales(level.getRandom().nextInt(3));
		double x;
		double y;
		double z;
		if (dragon.getDragonFight() != null) {
			BlockPos podium = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EnderDragonFight.getPodiumLocation(dragon.getFightOrigin()));
			x = podium.getX() + 0.5;
			y = podium.getY() + 1.0;
			z = podium.getZ() + 0.5;
		} else {
			x = dragon.getX();
			y = dragon.getY();
			z = dragon.getZ();
		}
		ItemEntity item = new ItemEntity(level, x, y, z, new ItemStack(BossRegistry.DRAGON_SCALE, count));
		item.setExtendedLifetime();
		item.setDeltaMovement(0, 0.2, 0);
		level.addFreshEntity(item);
		return count;
	}
}
