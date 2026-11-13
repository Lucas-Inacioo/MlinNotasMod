package com.mlin.notas.client.mixin;

import com.mlin.notas.client.GammaBright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gammabright: quando ativo, forca o brilho do lightmap ao maximo (equivale ao gamma 1000 dos mods de fullbright).
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void mlinnotas$fullbright(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		if (GammaBright.isEnabled() && renderState.needsUpdate) {
			renderState.brightness = GammaBright.BRIGHTNESS;
		}
	}
}
