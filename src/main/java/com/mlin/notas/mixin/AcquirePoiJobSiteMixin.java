package com.mlin.notas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mlin.notas.village.VillageVillagers;
import com.mojang.datafixers.util.Pair;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dentro de uma vila do mod o aldeao so acha posto de trabalho onde ele vale ({@link com.mlin.notas.village.JobSiteRules}):
 * o filtro entra na propria busca de POIs (antes do {@code limit(5)} do vanilla, senao enfeites mais perto escondem os
 * postos de verdade). Bloco que nao e posto, camas e sinos passam sempre.
 */
@Mixin(AcquirePoi.class)
public abstract class AcquirePoiJobSiteMixin {
	@WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findAllClosestFirstWithType(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/stream/Stream;"))
	private static Stream<Pair<Holder<PoiType>, BlockPos>> mlinnotas$onlyValidJobSites(PoiManager manager, Predicate<Holder<PoiType>> type,
			Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy,
			Operation<Stream<Pair<Holder<PoiType>, BlockPos>>> original, @Local(argsOnly = true) ServerLevel level) {
		return original.call(manager, type, (Predicate<BlockPos>) pos -> filter.test(pos) && VillageVillagers.jobSiteAllowed(level, pos), center, radius, occupancy);
	}
}
