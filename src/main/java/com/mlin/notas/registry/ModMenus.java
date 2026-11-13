package com.mlin.notas.registry;

import com.mlin.notas.enchanting.SelectiveEnchantMenu;
import com.mlin.notas.MlinNotas;
import com.mlin.notas.backpack.BackpackMenu;
import com.mlin.notas.chest.BigChestMenu;
import com.mlin.notas.chest.ChestTier;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import java.util.EnumMap;
import java.util.Map;

/** Tipos de menu (container) do mod. As telas correspondentes sao registradas em MlinNotasClient. */
public final class ModMenus {
	private static final Map<ChestTier, MenuType<BigChestMenu>> CHESTS = new EnumMap<>(ChestTier.class);

	static {
		for (ChestTier tier : ChestTier.values()) {
			registerChest(tier);
		}
	}

	public static final ExtendedMenuType<BackpackMenu, BackpackMenu.Data> BACKPACK = Registry.register(
			BuiltInRegistries.MENU, MlinNotas.id("backpack"),
			new ExtendedMenuType<>(BackpackMenu::new, BackpackMenu.Data.STREAM_CODEC));

	public static final MenuType<SelectiveEnchantMenu> SELECTIVE_ENCHANT = Registry.register(
			BuiltInRegistries.MENU, MlinNotas.id("selective_enchant"),
			new MenuType<>(SelectiveEnchantMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	private static MenuType<BigChestMenu> registerChest(ChestTier tier) {
		MenuType<BigChestMenu> type = new MenuType<>((id, inventory) -> new BigChestMenu(tier, id, inventory), FeatureFlags.VANILLA_SET);
		Registry.register(BuiltInRegistries.MENU, MlinNotas.id(tier.blockName()), type);
		CHESTS.put(tier, type);
		return type;
	}

	public static MenuType<BigChestMenu> forTier(ChestTier tier) {
		return CHESTS.get(tier);
	}

	public static void init() {
	}
}
