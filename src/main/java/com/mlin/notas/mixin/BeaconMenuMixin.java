package com.mlin.notas.mixin;

import com.mlin.notas.beacon.BeaconMenuTier;
import com.mlin.notas.beacon.BeaconPowers;
import com.mlin.notas.beacon.BeaconTierSlot;
import com.mlin.notas.beacon.BeaconTiers;
import com.mlin.notas.registry.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Menu do sinalizador: um data slot extra leva o metal da piramide ao cliente (a tela mostra o nivel e libera os
 * poderes extras) e a escolha de Garimpo/Voo so vale se o metal da piramide permitir.
 */
@Mixin(BeaconMenu.class)
public abstract class BeaconMenuMixin extends AbstractContainerMenu implements BeaconMenuTier {
	@Shadow
	@Final
	private ContainerLevelAccess access;

	@Shadow
	public abstract int getLevels();

	@Unique
	private DataSlot mlinnotas$tierSlot;

	protected BeaconMenuMixin(MenuType<?> menuType, int containerId) {
		super(menuType, containerId);
	}

	@Inject(method = "<init>(ILnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
			at = @At("TAIL"))
	private void mlinnotas$addTierSlot(int containerId, Container inventory, ContainerData beaconData, ContainerLevelAccess access, CallbackInfo ci) {
		this.mlinnotas$tierSlot = this.addDataSlot(access == ContainerLevelAccess.NULL ? DataSlot.standalone() : new BeaconTierSlot(access, this::getLevels));
	}

	@Override
	public int mlinnotas$tierCode() {
		return this.mlinnotas$tierSlot == null ? 0 : this.mlinnotas$tierSlot.get();
	}

	@Inject(method = "updateEffects", at = @At("HEAD"), cancellable = true)
	private void mlinnotas$checkExtraTier(Optional<Holder<MobEffect>> primary, Optional<Holder<MobEffect>> secondary, CallbackInfoReturnable<Boolean> cir) {
		BeaconTiers.Extra extra = ModEffects.extraOf(secondary.orElse(null));
		if (extra == null) {
			return;
		}
		int levels = this.getLevels();
		boolean unlocked = this.access.evaluate((level, pos) -> BeaconTiers.unlocks(extra, BeaconPowers.pyramidTier(level, pos, levels), levels), false);
		if (!unlocked) {
			cir.setReturnValue(false);
		}
	}
}
