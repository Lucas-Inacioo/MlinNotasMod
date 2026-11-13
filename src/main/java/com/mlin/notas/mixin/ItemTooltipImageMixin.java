package com.mlin.notas.mixin;

import com.mlin.notas.waila.ContainerTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Caixa de shulker no inventario mostra a grade de conteudo no tooltip. A Fabric API nao tem evento para a imagem
 * do tooltip ({@code Item#getTooltipImage}) e o item da shulker e um {@link BlockItem} vanilla, entao so por mixin.
 */
@Mixin(Item.class)
public abstract class ItemTooltipImageMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$shulkerContents(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if ((Object) this instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
			Optional<TooltipComponent> image = ContainerTooltip.of(stack);
			if (image.isPresent()) {
				cir.setReturnValue(image);
			}
		}
	}
}
