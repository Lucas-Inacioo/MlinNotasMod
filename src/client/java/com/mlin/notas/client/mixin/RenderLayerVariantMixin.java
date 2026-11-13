package com.mlin.notas.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mlin.notas.client.variants.MobVariantTextures;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Variantes de bioma: troca tambem a textura das camadas extras desenhadas por {@code renderColoredCutoutModel}
 * (ex.: a "roupa" de alga do afogado) quando a variante tem a textura recolorida correspondente.
 */
@Mixin(RenderLayer.class)
abstract class RenderLayerVariantMixin {
	@ModifyVariable(method = "renderColoredCutoutModel", at = @At("HEAD"), argsOnly = true)
	private static Identifier mlinnotas$variantLayerTexture(Identifier texture, @Local(argsOnly = true) LivingEntityRenderState state) {
		return MobVariantTextures.textureFor(((FabricRenderState) state).getData(MobVariantTextures.VARIANT), texture);
	}
}
