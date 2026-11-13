package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.furnace.FurnaceUpgradeItem;
import com.mlin.notas.tablet.CraftingTabletItem;
import com.mlin.notas.wand.BuildingWandItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Itens do mod. A ordem de registro e a ordem na aba criativa.
 */
public final class ModItems {
	private ModItems() {
	}

	/** Todos os itens registrados, na ordem, para a aba criativa. */
	public static final List<Item> ALL = new ArrayList<>();

	public static final Item WOODEN_WAND = register("wooden_wand", p -> new BuildingWandItem(p, 9), new Item.Properties().stacksTo(1).durability(128));
	public static final Item STONE_WAND = register("stone_wand", p -> new BuildingWandItem(p, 27), new Item.Properties().stacksTo(1).durability(256));
	public static final Item IRON_WAND = register("iron_wand", p -> new BuildingWandItem(p, 81), new Item.Properties().stacksTo(1).durability(512));
	public static final Item DIAMOND_WAND = register("diamond_wand", p -> new BuildingWandItem(p, 256), new Item.Properties().stacksTo(1).durability(2048));

	public static final Item CRAFTING_TABLET = register("crafting_tablet", CraftingTabletItem::new, new Item.Properties().stacksTo(1));

	public static final Item FURNACE_UPGRADE_IRON = register("furnace_upgrade_iron", p -> new FurnaceUpgradeItem(p, 1), new Item.Properties().stacksTo(16));
	public static final Item FURNACE_UPGRADE_GOLD = register("furnace_upgrade_gold", p -> new FurnaceUpgradeItem(p, 2), new Item.Properties().stacksTo(16));
	public static final Item FURNACE_UPGRADE_DIAMOND = register("furnace_upgrade_diamond", p -> new FurnaceUpgradeItem(p, 3), new Item.Properties().stacksTo(16));

	public static ResourceKey<Item> key(String name) {
		return ResourceKey.create(Registries.ITEM, MlinNotas.id(name));
	}

	public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = key(name);
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		ALL.add(item);
		return item;
	}

	/** Forca o carregamento da classe (e o registro dos campos estaticos). */
	public static void init() {
	}
}
