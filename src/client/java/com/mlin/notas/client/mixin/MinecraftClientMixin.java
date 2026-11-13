package com.mlin.notas.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exemplo de mixin de cliente. Injetado no inicio de Minecraft.run().
 */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {
	@Inject(at = @At("HEAD"), method = "run")
	private void mlinnotas$onRun(CallbackInfo info) {
	}
}
