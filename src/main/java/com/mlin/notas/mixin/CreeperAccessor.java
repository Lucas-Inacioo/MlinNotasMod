package com.mlin.notas.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raio da explosao do creeper (privado na vanilla); usado pelo Creeper de Cristal ({@code variants/}). */
@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("explosionRadius")
	void mlinnotas$setExplosionRadius(int radius);

	@Accessor("explosionRadius")
	int mlinnotas$getExplosionRadius();
}
