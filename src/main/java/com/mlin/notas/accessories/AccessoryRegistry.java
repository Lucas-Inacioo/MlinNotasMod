package com.mlin.notas.accessories;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registro dos itens de acessorio (a partir do {@link AccessoryCatalog}) e do menu. */
public final class AccessoryRegistry {
	private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

	public static final MenuType<AccessoryMenu> MENU = Registry.register(BuiltInRegistries.MENU, MlinNotas.id("accessories"),
			new MenuType<>(AccessoryMenu::new, FeatureFlags.VANILLA_SET));

	static {
		for (AccessoryDef def : AccessoryCatalog.all()) {
			Item.Properties properties = new Item.Properties().stacksTo(1).rarity(rarity(def));
			if (def.kind() == AccessoryKind.RELIC || def.id().startsWith("netherite_")) {
				properties.fireResistant();
			}
			ITEMS.put(def.id(), ModItems.register(def.id(), p -> new AccessoryItem(p, def), properties));
		}
	}

	private AccessoryRegistry() {
	}

	private static Rarity rarity(AccessoryDef def) {
		return switch (def.kind()) {
			case RELIC -> Rarity.EPIC;
			case ARTIFACT -> Rarity.RARE;
			case BASIC -> def.id().startsWith("netherite_") || def.id().startsWith("diamond_") || def.id().startsWith("cobalt_")
					? Rarity.UNCOMMON : Rarity.COMMON;
		};
	}

	/** Item do acessorio pelo id ({@code "life_amulet"}). */
	public static Item item(String id) {
		Item item = ITEMS.get(id);
		if (item == null) {
			throw new IllegalArgumentException("acessorio desconhecido: " + id);
		}
		return item;
	}

	public static Map<String, Item> items() {
		return Collections.unmodifiableMap(ITEMS);
	}

	static void init() {
	}
}
