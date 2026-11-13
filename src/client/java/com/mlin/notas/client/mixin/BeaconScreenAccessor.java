package com.mlin.notas.client.mixin;

import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Poderes escolhidos na tela do sinalizador (o botao de confirmar envia estes dois campos). */
@Mixin(BeaconScreen.class)
public interface BeaconScreenAccessor {
	@Accessor("primary")
	Holder<MobEffect> mlinnotas$primary();

	@Accessor("primary")
	void mlinnotas$setPrimary(Holder<MobEffect> effect);

	@Accessor("secondary")
	Holder<MobEffect> mlinnotas$secondary();

	@Accessor("secondary")
	void mlinnotas$setSecondary(Holder<MobEffect> effect);
}
