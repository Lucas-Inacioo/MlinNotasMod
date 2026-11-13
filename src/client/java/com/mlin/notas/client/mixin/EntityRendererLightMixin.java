package com.mlin.notas.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mlin.notas.client.dynlight.DynamicLightsClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Luz dinamica nas entidades (inclusive a mao em primeira pessoa): soma a luz das fontes perto na luz de bloco. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLightMixin {
	@ModifyReturnValue(method = "getBlockLightLevel", at = @At("RETURN"))
	private int mlinnotas$dynamicLight(int original, Entity entity, BlockPos pos) {
		return DynamicLightsClient.blockLevel(pos, original);
	}
}
