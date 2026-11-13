package com.mlin.notas.mixin;

import com.mlin.notas.enchanting.EnchantRules;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bigorna respeita o teto de encantamento do mundo ({@link EnchantRules#miningCap}): um resultado com encantamento
 * acima do que a Mineracao do mundo libera (e acima do que o item da esquerda ja tinha) e recusado. A Fabric nao tem
 * evento para o resultado da bigorna. So no servidor (o cliente recebe o slot vazio); criativo ignora.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;

	protected AnvilMenuMixin(@Nullable MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access,
			ItemCombinerMenuSlotDefinition slots) {
		super(menuType, containerId, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void mlinnotas$capEnchantments(CallbackInfo ci) {
		if (!(this.player instanceof ServerPlayer serverPlayer) || serverPlayer.hasInfiniteMaterials()) {
			return;
		}
		ItemStack result = this.resultSlots.getItem(0);
		if (result.isEmpty()) {
			return;
		}
		int mining = SkillServer.progress(serverPlayer.level().getServer()).level(SkillBranch.MINING);
		ItemEnchantments before = EnchantmentHelper.getEnchantmentsForCrafting(this.inputSlots.getItem(0));
		ItemEnchantments after = EnchantmentHelper.getEnchantmentsForCrafting(result);
		for (Holder<Enchantment> enchantment : after.keySet()) {
			int level = after.getLevel(enchantment);
			if (!EnchantRules.anvilAllows(before.getLevel(enchantment), level, enchantment.value().getMaxLevel(), mining)) {
				this.resultSlots.setItem(0, ItemStack.EMPTY);
				this.cost.set(0);
				this.broadcastChanges();
				if (serverPlayer.connection != null) {
					serverPlayer.sendOverlayMessage(Component.translatable("gui.mlinnotas.enchant.mining_cap",
							Component.translatable("enchantment.level." + level), SkillServer.tierName(EnchantRules.tierForLevel(level)))
							.withStyle(ChatFormatting.RED));
				}
				return;
			}
		}
	}
}
