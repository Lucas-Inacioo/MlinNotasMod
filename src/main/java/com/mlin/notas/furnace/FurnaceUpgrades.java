package com.mlin.notas.furnace;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.tier.Tier;
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
	/** Id do material do upgrade (ver {@link Tier}). */
	public static final AttachmentType<String> UPGRADE = AttachmentRegistry.create(MlinNotas.id("furnace_upgrade"),
			builder -> builder.persistent(Codec.STRING));
	/** Formato antigo (1 ferro, 2 ouro, 3 diamante), so lido para converter fornalhas de mundos antigos. */
	public static final AttachmentType<Integer> LEGACY_TIER = AttachmentRegistry.create(MlinNotas.id("furnace_tier"),
			builder -> builder.persistent(Codec.INT));

	private FurnaceUpgrades() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(FurnaceUpgrades::onUseBlock);
		PlayerBlockBreakEvents.BEFORE.register(FurnaceUpgrades::dropUpgradeOnBreak);
	}

	/** Texto do multiplicador sem casas inuteis: 2, 1.25, 32. */
	public static String formatMultiplier(float multiplier) {
		return multiplier == (int) multiplier ? Integer.toString((int) multiplier) : Float.toString(multiplier);
	}

	/** Nivel do upgrade da fornalha; {@link Tier#WOOD} = sem upgrade. Converte o formato antigo (1 ferro, 2 ouro, 3 diamante). */
	public static Tier tierOf(BlockEntity blockEntity) {
		AttachmentTarget target = (AttachmentTarget) blockEntity;
		String id = target.getAttached(UPGRADE);
		Tier tier = id == null ? null : Tier.byId(id);
		if (tier != null) {
			return tier;
		}
		Integer legacy = target.getAttached(LEGACY_TIER);
		if (legacy != null) {
			return switch (legacy) {
				case 1 -> Tier.IRON;
				case 2 -> Tier.GOLD;
				case 3 -> Tier.DIAMOND;
				default -> Tier.WOOD;
			};
		}
		return Tier.WOOD;
	}

	public static void setTier(BlockEntity blockEntity, Tier tier) {
		AttachmentTarget target = (AttachmentTarget) blockEntity;
		target.removeAttached(LEGACY_TIER);
		if (tier == Tier.WOOD) {
			target.removeAttached(UPGRADE);
		} else {
			target.setAttached(UPGRADE, tier.id());
		}
		blockEntity.setChanged();
	}

	public static Item itemForTier(Tier tier) {
		return ModItems.furnaceUpgrade(tier);
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
		Tier current = tierOf(furnace);
		if (upgrade.tier().ordinal() <= current.ordinal()) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.furnace_upgrade.already"));
			return false;
		}
		if (!level.isClientSide()) {
			if (current != Tier.WOOD) {
				Item previous = itemForTier(current);
				if (previous != null) {
					Block.popResource(level, pos.above(), new ItemStack(previous));
				}
			}
			setTier(furnace, upgrade.tier());
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.furnace_upgrade.applied", formatMultiplier(upgrade.tier().furnaceSpeed())));
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
			Tier tier = tierOf(blockEntity);
			Item item = itemForTier(tier);
			if (item != null) {
				Block.popResource(level, pos, new ItemStack(item));
				setTier(blockEntity, Tier.WOOD);
			}
		}
		return true;
	}
}
