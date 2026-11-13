package com.mlin.notas.mixin;

import com.mlin.notas.MlinNotas;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exemplo de mixin de servidor. Injetado no inicio de MinecraftServer.loadLevel().
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
	@Inject(at = @At("HEAD"), method = "loadLevel")
	private void mlinnotas$onLoadLevel(CallbackInfo info) {
		MlinNotas.LOGGER.debug("loadLevel iniciado");
	}
}
