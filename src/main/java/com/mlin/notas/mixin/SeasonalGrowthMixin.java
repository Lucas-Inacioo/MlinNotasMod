package com.mlin.notas.mixin;

import com.mlin.notas.season.SeasonGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Estacoes: crescimento das culturas por estacao. Fica so nos {@code randomTick} das plantas (nao no random tick de
 * todo bloco), entao o custo e zero para grama, folhas etc. Estacao ruim pula o tick; estacao boa pode dar um extra.
 * A Fabric nao tem evento de crescimento por random tick.
 */
@Mixin({CropBlock.class, StemBlock.class, SweetBerryBushBlock.class, SugarCaneBlock.class, SaplingBlock.class, PitcherCropBlock.class})
public abstract class SeasonalGrowthMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$seasonSkip(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (SeasonGrowth.skipTick(state, level, pos, random)) {
			ci.cancel();
		}
	}

	@Inject(method = "randomTick", at = @At("TAIL"))
	private void mlinnotas$seasonBonus(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		SeasonGrowth.afterTick(level, pos, random);
	}
}
