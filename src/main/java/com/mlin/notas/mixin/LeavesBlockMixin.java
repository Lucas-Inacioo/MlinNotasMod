package com.mlin.notas.mixin;

import com.mlin.notas.tree.FastLeafDecay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Queda rapida de folhas: so o tick agendado das folhas (o que recalcula a distancia ate o tronco). A Fabric nao tem
 * evento para isso e um evento de quebra de bloco nao pegaria tronco queimado ou explodido. Logica em
 * {@link FastLeafDecay}.
 */
@Mixin(LeavesBlock.class)
public abstract class LeavesBlockMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$fastDecay(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (FastLeafDecay.onTick(state, level, pos, random)) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void mlinnotas$scheduleDecay(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		FastLeafDecay.afterTick(level, pos, random);
	}
}
