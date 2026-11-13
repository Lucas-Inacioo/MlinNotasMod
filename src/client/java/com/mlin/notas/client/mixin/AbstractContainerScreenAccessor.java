package com.mlin.notas.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Expoe a posicao e o tamanho da moldura das telas de container para posicionar widgets do mod. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int mlinnotas$leftPos();

	@Accessor("topPos")
	int mlinnotas$topPos();

	@Accessor("imageWidth")
	int mlinnotas$imageWidth();

	@Accessor("imageHeight")
	int mlinnotas$imageHeight();

	@Accessor("hoveredSlot")
	net.minecraft.world.inventory.Slot mlinnotas$hoveredSlot();
}
