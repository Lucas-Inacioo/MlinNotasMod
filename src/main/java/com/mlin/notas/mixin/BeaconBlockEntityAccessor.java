package com.mlin.notas.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/** Conjunto de efeitos aceitos pelo sinalizador (filtro do menu e do NBT); o mod soma Garimpo e Voo. */
@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
	@Accessor("VALID_EFFECTS")
	static Set<Holder<MobEffect>> mlinnotas$validEffects() {
		throw new AssertionError();
	}
}
