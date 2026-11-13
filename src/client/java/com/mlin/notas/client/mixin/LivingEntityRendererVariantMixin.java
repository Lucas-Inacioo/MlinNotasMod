package com.mlin.notas.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mlin.notas.client.variants.MobVariantTextures;
import com.mlin.notas.variants.MobVariantsServer;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Variantes de bioma ({@code variants/}): copia o attachment {@code mlinnotas:mob_variant} da entidade para o render
 * state (dados extras da Fabric) e troca a textura do corpo em {@code getRenderType}. A Fabric API nao tem evento de
 * extracao de render state de entidade nem de escolha de textura, por isso o mixin.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererVariantMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void mlinnotas$extractVariant(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		((FabricRenderState) state).setData(MobVariantTextures.VARIANT, entity.getAttached(MobVariantsServer.VARIANT));
	}

	@ModifyExpressionValue(method = "getRenderType", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"))
	private Identifier mlinnotas$variantTexture(Identifier original, @Local(argsOnly = true) LivingEntityRenderState state) {
		return MobVariantTextures.textureFor(((FabricRenderState) state).getData(MobVariantTextures.VARIANT), original);
	}
}
