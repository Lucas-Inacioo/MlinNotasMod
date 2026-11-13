package com.mlin.notas.backpack;

import com.mlin.notas.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Tanques da mochila (upgrades de agua e lava). Tudo acontece agachado (Shift):
 * <ul>
 *   <li>Shift + botao direito num bloco: coloca o fluido selecionado (agua infinita depois de encher; lava gasta 1 carga).</li>
 *   <li>Shift + botao direito numa fonte de agua/lava: enche o tanque de agua / soma 1 carga de lava.</li>
 *   <li>Shift + botao direito no ar: alterna entre agua e lava (se tiver os dois upgrades).</li>
 * </ul>
 */
public final class BackpackFluids {
	private BackpackFluids() {
	}

	/** Shift + clique num bloco (chamado de {@code Item.useOn}). */
	public static InteractionResult placeFluid(Level level, Player player, ItemStack backpack, BlockHitResult hit) {
		BackpackUpgrades upgrades = BackpackItem.upgrades(backpack);
		if (!upgrades.canPlaceSelectedFluid()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BucketItem bucket = (BucketItem) (upgrades.lavaSelected() ? Items.LAVA_BUCKET : Items.WATER_BUCKET);
		if (!bucket.emptyContents(player, level, hit.getBlockPos(), hit)) {
			return InteractionResult.FAIL;
		}
		if (upgrades.lavaSelected()) {
			backpack.set(ModDataComponents.BACKPACK_UPGRADES, upgrades.withLavaCharges(upgrades.lavaCharges() - 1));
		}
		return InteractionResult.SUCCESS;
	}

	/** Shift + botao direito sem acertar bloco solido (chamado de {@code Item.use}). */
	public static InteractionResult collectOrToggle(Level level, Player player, ItemStack backpack) {
		BackpackUpgrades upgrades = BackpackItem.upgrades(backpack);
		if (!upgrades.water() && !upgrades.lava()) {
			return InteractionResult.PASS;
		}
		BlockHitResult hit = BackpackItem.povHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			if (upgrades.water() && upgrades.lava()) {
				if (!level.isClientSide()) {
					boolean lava = !upgrades.lavaSelected();
					backpack.set(ModDataComponents.BACKPACK_UPGRADES, upgrades.withLavaSelected(lava));
					player.sendOverlayMessage(Component.translatable(lava ? "message.mlinnotas.backpack.mode.lava" : "message.mlinnotas.backpack.mode.water"));
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		FluidState fluid = level.getFluidState(pos);
		if (!fluid.isSource()) {
			return placeFluid(level, player, backpack, hit);
		}
		if (fluid.getType().is(FluidTags.WATER) && upgrades.water()) {
			if (!level.isClientSide()) {
				backpack.set(ModDataComponents.BACKPACK_UPGRADES, upgrades.withWaterFilled(true).withLavaSelected(false));
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
				player.sendOverlayMessage(Component.translatable("message.mlinnotas.backpack.water_filled"));
			}
			return InteractionResult.SUCCESS;
		}
		if (fluid.getType().is(FluidTags.LAVA) && upgrades.lava()) {
			if (upgrades.lavaCharges() >= BackpackUpgrades.MAX_LAVA_CHARGES) {
				player.sendOverlayMessage(Component.translatable("message.mlinnotas.backpack.lava_full"));
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof BucketPickup pickup && !pickup.pickupBlock(player, level, pos, state).isEmpty()) {
					int charges = upgrades.lavaCharges() + 1;
					backpack.set(ModDataComponents.BACKPACK_UPGRADES, upgrades.withLavaCharges(charges).withLavaSelected(true));
					level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
					player.sendOverlayMessage(Component.translatable("message.mlinnotas.backpack.lava_charged", charges));
				}
			}
			return InteractionResult.SUCCESS;
		}
		return placeFluid(level, player, backpack, hit);
	}
}
