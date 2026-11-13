package com.mlin.notas.tier;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rebalanceia as ferramentas e armaduras vanilla para a escada de 12 niveis ({@link Tier}): durabilidade,
 * dano, velocidade de mineracao, pontos de armadura e tenacidade. Mexe so nos componentes padrao dos itens
 * (Fabric {@link DefaultItemComponentEvents}), sem mixin.
 */
public final class VanillaRebalance {
	private VanillaRebalance() {
	}

	public static void init() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			for (Tier tier : Tier.values()) {
				if (!tier.vanilla()) {
					continue;
				}
				for (GearKind kind : GearKind.values()) {
					Item item = vanillaItem(kind.itemName(tier));
					if (item != Items.AIR) {
						context.modify(item, builder -> rebalanceTool(builder, tier, kind));
					}
				}
				for (Tier.ArmorPiece piece : Tier.ArmorPiece.values()) {
					Item item = vanillaItem(armorName(tier, piece));
					if (item != Items.AIR) {
						context.modify(item, builder -> {
							builder.set(DataComponents.MAX_DAMAGE, tier.armorDurability(piece));
							builder.set(DataComponents.ATTRIBUTE_MODIFIERS, ModMaterials.armor(tier).createAttributes(ModMaterials.armorType(piece)));
						});
					}
				}
			}
		});
	}

	/** Id da peca de armadura (vanilla ou do mod): leather_helmet, chainmail_boots, golden_leggings, cobalt_chestplate... */
	public static String armorName(Tier tier, Tier.ArmorPiece piece) {
		String prefix = switch (tier) {
			case WOOD -> "leather";
			case STONE -> "chainmail";
			case GOLD -> "golden";
			default -> tier.id();
		};
		return prefix + "_" + piece.name().toLowerCase(Locale.ROOT);
	}

	private static Item vanillaItem(String name) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name));
	}

	/** Material vanilla original (para saber quanto do dano atual vem do material). */
	static ToolMaterial originalMaterial(Tier tier) {
		return switch (tier) {
			case WOOD -> ToolMaterial.WOOD;
			case STONE -> ToolMaterial.STONE;
			case COPPER -> ToolMaterial.COPPER;
			case IRON -> ToolMaterial.IRON;
			case GOLD -> ToolMaterial.GOLD;
			case DIAMOND -> ToolMaterial.DIAMOND;
			case NETHERITE -> ToolMaterial.NETHERITE;
			default -> throw new IllegalArgumentException("nao e vanilla: " + tier);
		};
	}

	private static void rebalanceTool(DataComponentMap.Builder builder, Tier tier, GearKind kind) {
		builder.set(DataComponents.MAX_DAMAGE, tier.toolDurability());
		builder.set(DataComponents.ENCHANTABLE, new Enchantable(tier.enchantability()));

		// dano: troca a parte do material no modificador base (a enxada vanilla anula o bonus de proposito)
		float delta = tier.attackBonus() - originalMaterial(tier).attackDamageBonus();
		ItemAttributeModifiers attributes = builder.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (attributes != null && kind != GearKind.HOE && delta != 0.0F) {
			ItemAttributeModifiers.Builder rebuilt = ItemAttributeModifiers.builder();
			for (ItemAttributeModifiers.Entry entry : attributes.modifiers()) {
				AttributeModifier modifier = entry.modifier();
				if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID)) {
					modifier = new AttributeModifier(modifier.id(), modifier.amount() + delta, modifier.operation());
				}
				rebuilt.add(entry.attribute(), modifier, entry.slot(), entry.display());
			}
			builder.set(DataComponents.ATTRIBUTE_MODIFIERS, rebuilt.build());
		}

		// velocidade de mineracao: so nas regras "minera e dropa" (espadas tem regras fixas, ficam como estao)
		Tool tool = builder.get(DataComponents.TOOL);
		if (tool != null && kind != GearKind.SWORD) {
			List<Tool.Rule> rules = new ArrayList<>();
			for (Tool.Rule rule : tool.rules()) {
				boolean mines = rule.speed().isPresent() && rule.correctForDrops().orElse(false);
				rules.add(mines ? new Tool.Rule(rule.blocks(), Optional.of(tier.miningSpeed()), rule.correctForDrops()) : rule);
			}
			builder.set(DataComponents.TOOL, new Tool(rules, tool.defaultMiningSpeed(), tool.damagePerBlock(), tool.canDestroyBlocksInCreative()));
		}
	}
}
