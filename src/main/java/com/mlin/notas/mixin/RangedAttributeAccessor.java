package com.mlin.notas.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Teto de um atributo (final e privado na vanilla). O mod sobe o teto de vida maxima (1024 na vanilla) para os chefes
 * com mais de 1024 de vida ({@code boss/BossRules#MAX_HEALTH_CAP}).
 */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {
	@Mutable
	@Accessor("maxValue")
	void mlinnotas$setMaxValue(double maxValue);
}
