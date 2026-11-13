package com.mlin.notas.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mlin.notas.season.SeasonBiomes;
import com.mlin.notas.season.SeasonDate;
import com.mlin.notas.season.SeasonState;
import com.mlin.notas.season.SeasonVisuals;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Estacoes: cor da grama e da folhagem dos biomas temperados por fase (primavera viva, outono laranja, inverno
 * cinza). Os resolvedores de cor do cliente ({@code BiomeColors}) chamam estes metodos ao montar os chunks; o
 * {@code SeasonClient} refaz os chunks quando a fase muda. A Fabric so permite trocar cor por bloco, nao por bioma.
 */
@Mixin(Biome.class)
public abstract class BiomeSeasonColorMixin {
	@ModifyReturnValue(method = "getGrassColor", at = @At("RETURN"))
	private int mlinnotas$seasonalGrass(int color) {
		SeasonDate date = SeasonState.current();
		return date != null && SeasonBiomes.temperate((Biome) (Object) this) ? SeasonVisuals.grassColor(color, date) : color;
	}

	@ModifyReturnValue(method = "getFoliageColor", at = @At("RETURN"))
	private int mlinnotas$seasonalFoliage(int color) {
		SeasonDate date = SeasonState.current();
		return date != null && SeasonBiomes.temperate((Biome) (Object) this) ? SeasonVisuals.foliageColor(color, date) : color;
	}
}
