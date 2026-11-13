package com.mlin.notas.mixin;

import com.mlin.notas.season.SeasonWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Estacoes: degelo da neve/gelo sazonal na mesma coluna que o vanilla sorteia para neve/gelo (barato e incremental).
 * O vanilla nao derrete neve nunca; a Fabric nao tem evento para o tick de precipitacao.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelPrecipitationMixin {
	@Inject(method = "tickPrecipitation", at = @At("TAIL"))
	private void mlinnotas$seasonalMelt(BlockPos pos, CallbackInfo ci) {
		SeasonWeather.melt((ServerLevel) (Object) this, pos);
	}
}
