package com.mlin.notas.smithing;

import com.mlin.notas.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Moldes de ferraria da cadeia pos-netherite ({@link SmithingChain}): Molde Infernal (Colosso Igneo), Molde do Fim
 * (Dragao do Ender), Molde Astral (Arauto Astral) e Molde Celestial (Wither). Textos do tooltip em
 * {@code item.mlinnotas.smithing_template.<id>.*}.
 */
public final class SmithingTemplates {
	private static final Map<SmithingChain, Item> ITEMS = new EnumMap<>(SmithingChain.class);

	private static final List<Identifier> BASE_ICONS = List.of(slot("helmet"), slot("sword"), slot("chestplate"), slot("pickaxe"), slot("leggings"),
			slot("axe"), slot("boots"), slot("hoe"), slot("shovel"), slot("spear"));
	private static final List<Identifier> ADDITION_ICONS = List.of(slot("ingot"));

	static {
		for (SmithingChain step : SmithingChain.values()) {
			String key = "item.mlinnotas.smithing_template." + step.id();
			ITEMS.put(step, ModItems.register(step.templateId(), p -> new SmithingTemplateItem(
					Component.translatable(key + ".applies_to").withStyle(net.minecraft.ChatFormatting.BLUE),
					Component.translatable(key + ".ingredients").withStyle(net.minecraft.ChatFormatting.BLUE),
					Component.translatable(key + ".base_slot_description"),
					Component.translatable(key + ".additions_slot_description"),
					BASE_ICONS, ADDITION_ICONS, p), new Item.Properties().rarity(step == SmithingChain.CELESTIAL ? Rarity.EPIC : Rarity.RARE)
					.fireResistant().component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
							Component.translatable("item.mlinnotas." + step.templateId() + ".tooltip").withStyle(net.minecraft.ChatFormatting.GRAY))))));
		}
	}

	private SmithingTemplates() {
	}

	private static Identifier slot(String name) {
		return Identifier.withDefaultNamespace("container/slot/" + name);
	}

	public static Item item(SmithingChain step) {
		return ITEMS.get(step);
	}

	/** Carrega a classe (registra os itens). */
	public static void init() {
	}
}
