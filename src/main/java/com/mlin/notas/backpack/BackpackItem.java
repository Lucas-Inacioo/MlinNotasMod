package com.mlin.notas.backpack;

import com.mlin.notas.registry.ModDataComponents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Mochila: botao direito (ou tecla B) abre; Shift + botao direito usa os tanques. Upgrades sao aplicados
 * arrastando o item de upgrade sobre a mochila no inventario e clicando com o botao direito.
 */
public class BackpackItem extends Item {
	public BackpackItem(Properties properties) {
		super(properties);
	}

	public static BackpackUpgrades upgrades(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.BACKPACK_UPGRADES, BackpackUpgrades.DEFAULT);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
		return BackpackFluids.placeFluid(context.getLevel(), player, context.getItemInHand(), hit);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSecondaryUseActive()) {
			return BackpackFluids.collectOrToggle(level, player, stack);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
			open(serverPlayer, slot);
		}
		return InteractionResult.SUCCESS;
	}

	/** Expoe o raycast protegido de Item para as classes do pacote. */
	static BlockHitResult povHitResult(Level level, Player player, ClipContext.Fluid fluid) {
		return getPlayerPOVHitResult(level, player, fluid);
	}

	/** Indice do inventario (0..35 ou mao secundaria) da primeira mochila, ou -1. */
	public static int findBackpackSlot(Player player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (inventory.getItem(i).getItem() instanceof BackpackItem) {
				return i;
			}
		}
		return inventory.getItem(Inventory.SLOT_OFFHAND).getItem() instanceof BackpackItem ? Inventory.SLOT_OFFHAND : -1;
	}

	/** Abre a mochila que esta no slot informado do inventario do jogador. */
	public static void open(ServerPlayer player, int inventorySlot) {
		ItemStack stack = player.getInventory().getItem(inventorySlot);
		if (!(stack.getItem() instanceof BackpackItem)) {
			return;
		}
		BackpackMenu.Data data = new BackpackMenu.Data(inventorySlot, upgrades(stack));
		Component title = stack.getHoverName();
		player.openMenu(new ExtendedMenuProvider<BackpackMenu.Data>() {
			@Override
			public BackpackMenu.Data getScreenOpeningData(ServerPlayer serverPlayer) {
				return data;
			}

			@Override
			public Component getDisplayName() {
				return title;
			}

			@Override
			public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new BackpackMenu(containerId, inventory, data);
			}
		});
	}

	/** Aplica um upgrade a mochila. Devolve true se instalou. */
	public static boolean applyUpgrade(ItemStack backpack, BackpackUpgradeKind kind) {
		BackpackUpgrades current = upgrades(backpack);
		if (!current.canApply(kind)) {
			return false;
		}
		backpack.set(ModDataComponents.BACKPACK_UPGRADES, current.apply(kind));
		return true;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem) {
		if (clickAction != ClickAction.SECONDARY || !(other.getItem() instanceof BackpackUpgradeItem upgrade)) {
			return false;
		}
		if (applyUpgrade(self, upgrade.kind())) {
			other.shrink(1);
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.backpack.upgrade_applied",
					Component.translatable("item.mlinnotas." + upgrade.kind().itemName())));
		} else {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.backpack.upgrade_already"));
		}
		return true;
	}

	@Override
	public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		BackpackSmelter.tick(itemStack, level);
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		BackpackUpgrades u = upgrades(itemStack);
		int used = (int) itemStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
		tooltip.accept(Component.translatable("item.mlinnotas.backpack.tooltip.slots", used, u.slotCount()));
		if (u.crafting()) {
			tooltip.accept(Component.translatable("item.mlinnotas.backpack.tooltip.crafting"));
		}
		if (u.furnace()) {
			tooltip.accept(Component.translatable(u.smelting().isLit() ? "item.mlinnotas.backpack.tooltip.furnace.lit" : "item.mlinnotas.backpack.tooltip.furnace"));
		}
		if (u.water()) {
			tooltip.accept(Component.translatable(u.waterFilled() ? "item.mlinnotas.backpack.tooltip.water.filled" : "item.mlinnotas.backpack.tooltip.water.empty"));
		}
		if (u.lava()) {
			tooltip.accept(Component.translatable("item.mlinnotas.backpack.tooltip.lava", u.lavaCharges()));
		}
		if (u.water() && u.lava()) {
			tooltip.accept(Component.translatable(u.lavaSelected() ? "message.mlinnotas.backpack.mode.lava" : "message.mlinnotas.backpack.mode.water"));
		}
	}
}
