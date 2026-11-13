package com.mlin.notas.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mlin.notas.season.SeasonBiomes;
import com.mlin.notas.season.SeasonDate;
import com.mlin.notas.season.SeasonState;
import com.mlin.notas.season.SeasonVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Estacoes: no outono as copas variam por lugar (manchas amarelas e vermelhas sobre o laranja da estacao). So aqui o
 * resolvedor de cor recebe a posicao; {@code Biome.getFoliageColor()} nao. A Fabric nao tem gancho para a cor por bioma.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTintMixin {
	@WrapOperation(method = "calculateBlockTint", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/ColorResolver;getColor(Lnet/minecraft/world/level/biome/Biome;DD)I"))
	private int mlinnotas$autumnPatches(ColorResolver resolver, Biome biome, double x, double z, Operation<Integer> original) {
		int color = original.call(resolver, biome, x, z);
		if (resolver != BiomeColors.FOLIAGE_COLOR_RESOLVER) {
			return color;
		}
		SeasonDate date = SeasonState.current();
		return date != null && SeasonBiomes.temperate(biome) ? SeasonVisuals.foliageVariation(color, x, z, date) : color;
	}
}
