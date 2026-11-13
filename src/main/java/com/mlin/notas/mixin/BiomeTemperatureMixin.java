package com.mlin.notas.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mlin.notas.season.SeasonBiomes;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Estacoes: soma o deslocamento sazonal a temperatura dos biomas temperados depois do cache vanilla (o cache guarda o
 * valor sem estacao). Assim chuva vira neve, a agua congela e a neve se acumula no inverno, no servidor (precipitacao,
 * geracao de chunks) e no cliente (chuva desenhada como neve). Sem mixin nao ha como mudar o clima de um bioma em
 * tempo de jogo.
 */
@Mixin(Biome.class)
public abstract class BiomeTemperatureMixin {
	@ModifyReturnValue(method = "getTemperature", at = @At("RETURN"))
	private float mlinnotas$seasonalTemperature(float temperature) {
		float offset = SeasonBiomes.temperatureOffset((Biome) (Object) this);
		return offset == 0.0F ? temperature : temperature + offset;
	}
}
