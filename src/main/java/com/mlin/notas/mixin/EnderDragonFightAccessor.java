package com.mlin.notas.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Barra de chefe da luta do dragao (privada na vanilla); o rework mostra a fase nela ({@code dragon/DragonBoss}). */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
	@Accessor("dragonEvent")
	ServerBossEvent mlinnotas$getDragonEvent();
}
