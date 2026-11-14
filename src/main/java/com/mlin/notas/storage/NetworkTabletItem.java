package com.mlin.notas.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** Item do {@link NetworkTablet}: Shift+botao direito num terminal vincula, botao direito abre de longe. */
public class NetworkTabletItem extends Item {
	public NetworkTabletItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isSecondaryUseActive()
				|| !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof StorageTerminalBlock)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			NetworkTablet.link(serverPlayer, context.getItemInHand(), context.getClickedPos());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			NetworkTablet.open(serverPlayer, player.getItemInHand(hand));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		TabletLink link = stack.get(NetworkTablet.LINK);
		if (link == null) {
			tooltip.accept(Component.translatable("item.mlinnotas.network_tablet.unlinked").withStyle(ChatFormatting.GRAY));
			return;
		}
		BlockPos pos = link.terminal().pos();
		Component terminal = Component.translatable(link.crafting() ? "block.mlinnotas.crafting_terminal" : "block.mlinnotas.storage_terminal");
		tooltip.accept(Component.translatable("item.mlinnotas.network_tablet.linked", terminal).withStyle(ChatFormatting.AQUA));
		tooltip.accept(Component.translatable("item.mlinnotas.network_tablet.position", pos.getX(), pos.getY(), pos.getZ(),
				link.terminal().dimension().identifier().toString()).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.mlinnotas.network_tablet.usage").withStyle(ChatFormatting.DARK_GRAY));
	}
}
