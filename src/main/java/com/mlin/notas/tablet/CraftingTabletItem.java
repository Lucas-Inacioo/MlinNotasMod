package com.mlin.notas.tablet;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Tablet de crafting: mesa de trabalho 3x3 portatil. Abre com o botao direito ou pela tecla configurada,
 * desde que esteja em qualquer lugar do inventario.
 */
public class CraftingTabletItem extends Item {
	public static final Component TITLE = Component.translatable("container.mlinnotas.crafting_tablet");

	public CraftingTabletItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			open(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	/** Abre a mesa de trabalho portatil. O menu usa ContainerLevelAccess.NULL, entao nao exige bloco no mundo. */
	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CraftingMenu(id, inventory), TITLE));
	}

	/** True se o jogador tem um tablet em qualquer slot (inventario, hotbar ou mao secundaria). */
	public static boolean hasTablet(Player player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.getItem() instanceof CraftingTabletItem) {
				return true;
			}
		}
		return player.getOffhandItem().getItem() instanceof CraftingTabletItem;
	}
}
