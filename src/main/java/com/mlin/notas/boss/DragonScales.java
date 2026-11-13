package com.mlin.notas.boss;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.dragon.DragonHoard;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
 * Escamas de Dragao, Molde do Fim e tesouro: o dragao vanilla nao tem loot table de verdade, entao quando ele e removido como morto
 * (fim da animacao de morte ou /kill) soltamos as escamas e o Molde do Fim e, na luta do End, o bau do tesouro ({@link DragonHoard}).
 * A primeira morte da luta no mundo da 2 a 4 escamas e o tesouro completo; quando ele e reinvocado, 1 a 2 escamas e
 * um bau pequeno (marca {@link #FIRST_KILL_DONE} no nivel do End). Na luta as escamas caem em cima do portal de saida;
 * fora dela, onde o dragao estava.
 */
public final class DragonScales {
	/** A luta do End ja teve a primeira morte (o tesouro completo ja saiu). */
	public static final AttachmentType<Boolean> FIRST_KILL_DONE = AttachmentRegistry.create(MlinNotas.id("dragon_first_kill_done"),
			b -> b.persistent(Codec.BOOL));

	private DragonScales() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof EnderDragon dragon && entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
				killed(level, dragon);
			}
		});
	}

	/** Dragao morto: escamas e, na luta do End, o tesouro (completo so na primeira vez). */
	public static void killed(ServerLevel level, EnderDragon dragon) {
		boolean fight = dragon.getDragonFight() != null;
		boolean first = fight && !Boolean.TRUE.equals(level.getAttached(FIRST_KILL_DONE));
		drop(level, dragon, first);
		if (fight) {
			DragonHoard.place(level, first);
			level.setAttached(FIRST_KILL_DONE, true);
		}
	}

	public static int drop(ServerLevel level, EnderDragon dragon, boolean firstKill) {
		int count = BossRules.dragonScales(level.getRandom().nextInt(3), firstKill);
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
		for (ItemStack stack : new ItemStack[] {new ItemStack(BossRegistry.DRAGON_SCALE, count),
				new ItemStack(com.mlin.notas.smithing.SmithingTemplates.item(com.mlin.notas.smithing.SmithingChain.END))}) {
			ItemEntity item = new ItemEntity(level, x, y, z, stack);
			item.setExtendedLifetime();
			item.setDeltaMovement(0, 0.2, 0);
			level.addFreshEntity(item);
		}
		return count;
	}
}
