package com.mlin.notas.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mlin.notas.client.dynlight.DynamicLightsClient;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Luz dinamica nos blocos: soma a luz das fontes dinamicas nas coordenadas de lightmap que a malha dos chunks (e os
 * block entities) usam. Nao ha evento da Fabric para isso. Blocos solidos ficam de fora (a face usa a luz do vizinho).
 */
@Mixin(LightCoordsUtil.class)
public abstract class LightCoordsUtilMixin {
	@ModifyReturnValue(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
			at = @At("RETURN"))
	private static int mlinnotas$dynamicLight(int original, LightCoordsUtil.BrightnessGetter getter, BlockAndLightGetter level, BlockState state,
			BlockPos pos) {
		if (state.isSolidRender()) {
			return original;
		}
		return DynamicLightsClient.applyToPacked(pos, original);
	}
}
