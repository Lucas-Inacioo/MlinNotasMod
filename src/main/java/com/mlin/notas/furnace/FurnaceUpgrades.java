package com.mlin.notas.furnace;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModItems;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Nivel de upgrade guardado na block entity da fornalha (Fabric attachment persistente).
 * O mixin {@code AbstractFurnaceBlockEntityMixin} le o nivel para multiplicar a velocidade.
 */
public final class FurnaceUpgrades {
	public static final AttachmentType<Integer> TIER = AttachmentRegistry.create(MlinNotas.id("furnace_tier"),
			builder -> builder.persistent(Codec.INT));

	private FurnaceUpgrades() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(FurnaceUpgrades::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(FurnaceUpgrades::dropUpgradeOnBreak);
	}

	/** Multiplicador de velocidade do nivel: 1 -> 2x, 2 -> 4x, 3 -> 8x. */
	public static float speedMultiplier(int tier) {
		return tier <= 0 ? 1.0F : (float) (1 << tier);
	}

	public static int tierOf(BlockEntity blockEntity) {
		Integer tier = ((AttachmentTarget) blockEntity).getAttached(TIER);
		return tier == null ? 0 : tier;
	}

	public static void setTier(BlockEntity blockEntity, int tier) {
		if (tier <= 0) {
			((AttachmentTarget) blockEntity).removeAttached(TIER);
		} else {
			((AttachmentTarget) blockEntity).setAttached(TIER, tier);
		}
		blockEntity.setChanged();
	}

	public static Item itemForTier(int tier) {
		return switch (tier) {
			case 1 -> ModItems.FURNACE_UPGRADE_IRON;
			case 2 -> ModItems.FURNACE_UPGRADE_GOLD;
			case 3 -> ModItems.FURNACE_UPGRADE_DIAMOND;
			default -> null;
		};
	}

	/**
	 * Aplica o upgrade da mao na fornalha. Devolve true se aplicou (consome o item e devolve o upgrade anterior).
	 */
	public static boolean apply(Level level, BlockPos pos, Player player, ItemStack stack) {
		if (!(stack.getItem() instanceof FurnaceUpgradeItem upgrade)) {
			return false;
		}
		if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
			return false;
		}
		int current = tierOf(furnace);
		if (upgrade.tier() <= current) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.furnace_upgrade.already"));
			return false;
		}
		if (!level.isClientSide()) {
			if (current > 0) {
				Item previous = itemForTier(current);
				if (previous != null) {
					Block.popResource(level, pos.above(), new ItemStack(previous));
				}
			}
			setTier(furnace, upgrade.tier());
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.furnace_upgrade.applied", (int) speedMultiplier(upgrade.tier())));
		}
		return true;
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof FurnaceUpgradeItem)) {
			return InteractionResult.PASS;
		}
		return apply(level, hit.getBlockPos(), player, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	private static boolean dropUpgradeOnBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (blockEntity instanceof AbstractFurnaceBlockEntity && !level.isClientSide()) {
			int tier = tierOf(blockEntity);
			Item item = itemForTier(tier);
			if (item != null) {
				Block.popResource(level, pos, new ItemStack(item));
				setTier(blockEntity, 0);
			}
		}
		return true;
	}
}
