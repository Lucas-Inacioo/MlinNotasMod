package com.mlin.notas.tier;

import com.mlin.notas.MlinNotas;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ponte entre a escada {@link Tier} e os materiais do Minecraft: {@link ToolMaterial} e {@link ArmorMaterial}
 * de cada nivel (os vanilla com os valores rebalanceados) e as tags de mineracao.
 */
public final class ModMaterials {
	/** Blocos que exigem picareta de cobalto ou melhor (minerio de diamante). */
	public static final TagKey<Block> NEEDS_COBALT_TOOL = blockTag("needs_cobalt_tool");
	/** Blocos que exigem picareta de netherite ou melhor (minerio de infernita). */
	public static final TagKey<Block> NEEDS_NETHERITE_TOOL = blockTag("needs_netherite_tool");
	/** Blocos que exigem picareta de infernita ou melhor (minerio de endita). */
	public static final TagKey<Block> NEEDS_INFERNITE_TOOL = blockTag("needs_infernite_tool");
	/** Blocos que exigem picareta de endita ou melhor (minerio de astralita). */
	public static final TagKey<Block> NEEDS_ENDITE_TOOL = blockTag("needs_endite_tool");

	private static final Map<Tier, ToolMaterial> TOOLS = new EnumMap<>(Tier.class);
	private static final Map<Tier, ArmorMaterial> ARMOR = new EnumMap<>(Tier.class);

	static {
		for (Tier tier : Tier.values()) {
			TOOLS.put(tier, new ToolMaterial(incorrectForTool(tier), tier.toolDurability(), tier.miningSpeed(), tier.attackBonus(),
					tier.enchantability(), toolRepairTag(tier)));
			ARMOR.put(tier, new ArmorMaterial(tier.armorDurabilityMultiplier(),
					ArmorMaterials.makeDefense(tier.defense(Tier.ArmorPiece.BOOTS), tier.defense(Tier.ArmorPiece.LEGGINGS),
							tier.defense(Tier.ArmorPiece.CHESTPLATE), tier.defense(Tier.ArmorPiece.HELMET), 0),
					tier.enchantability(), equipSound(tier), tier.toughness(), tier.knockbackResistance(), armorRepairTag(tier),
					equipmentAsset(tier)));
		}
	}

	private ModMaterials() {
	}

	public static ToolMaterial tool(Tier tier) {
		return TOOLS.get(tier);
	}

	public static ArmorMaterial armor(Tier tier) {
		return ARMOR.get(tier);
	}

	public static ArmorType armorType(Tier.ArmorPiece piece) {
		return switch (piece) {
			case BOOTS -> ArmorType.BOOTS;
			case LEGGINGS -> ArmorType.LEGGINGS;
			case CHESTPLATE -> ArmorType.CHESTPLATE;
			case HELMET -> ArmorType.HELMET;
		};
	}

	/** Tag de blocos que uma ferramenta deste nivel nao consegue quebrar com drop. */
	public static TagKey<Block> incorrectForTool(Tier tier) {
		return switch (tier) {
			case WOOD -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
			case STONE -> BlockTags.INCORRECT_FOR_STONE_TOOL;
			case COPPER -> BlockTags.INCORRECT_FOR_COPPER_TOOL;
			case IRON -> BlockTags.INCORRECT_FOR_IRON_TOOL;
			case GOLD -> BlockTags.INCORRECT_FOR_GOLD_TOOL;
			case DIAMOND -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
			case NETHERITE -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
			default -> blockTag("incorrect_for_" + tier.id() + "_tool");
		};
	}

	private static TagKey<Item> toolRepairTag(Tier tier) {
		return switch (tier) {
			case WOOD -> ItemTags.WOODEN_TOOL_MATERIALS;
			case STONE -> ItemTags.STONE_TOOL_MATERIALS;
			case COPPER -> ItemTags.COPPER_TOOL_MATERIALS;
			case IRON -> ItemTags.IRON_TOOL_MATERIALS;
			case GOLD -> ItemTags.GOLD_TOOL_MATERIALS;
			case DIAMOND -> ItemTags.DIAMOND_TOOL_MATERIALS;
			case NETHERITE -> ItemTags.NETHERITE_TOOL_MATERIALS;
			default -> itemTag(tier.id() + "_tool_materials");
		};
	}

	private static TagKey<Item> armorRepairTag(Tier tier) {
		return switch (tier) {
			case WOOD -> ItemTags.REPAIRS_LEATHER_ARMOR;
			case STONE -> ItemTags.REPAIRS_CHAIN_ARMOR;
			case COPPER -> ItemTags.REPAIRS_COPPER_ARMOR;
			case IRON -> ItemTags.REPAIRS_IRON_ARMOR;
			case GOLD -> ItemTags.REPAIRS_GOLD_ARMOR;
			case DIAMOND -> ItemTags.REPAIRS_DIAMOND_ARMOR;
			case NETHERITE -> ItemTags.REPAIRS_NETHERITE_ARMOR;
			default -> itemTag("repairs_" + tier.id() + "_armor");
		};
	}

	private static Holder<SoundEvent> equipSound(Tier tier) {
		return switch (tier) {
			case WOOD -> SoundEvents.ARMOR_EQUIP_LEATHER;
			case STONE -> SoundEvents.ARMOR_EQUIP_CHAIN;
			case COPPER -> SoundEvents.ARMOR_EQUIP_COPPER;
			case GOLD -> SoundEvents.ARMOR_EQUIP_GOLD;
			case DIAMOND -> SoundEvents.ARMOR_EQUIP_DIAMOND;
			case IRON, COBALT -> SoundEvents.ARMOR_EQUIP_IRON;
			default -> SoundEvents.ARMOR_EQUIP_NETHERITE;
		};
	}

	private static ResourceKey<EquipmentAsset> equipmentAsset(Tier tier) {
		return switch (tier) {
			case WOOD -> EquipmentAssets.LEATHER;
			case STONE -> EquipmentAssets.CHAINMAIL;
			case COPPER -> EquipmentAssets.COPPER;
			case IRON -> EquipmentAssets.IRON;
			case GOLD -> EquipmentAssets.GOLD;
			case DIAMOND -> EquipmentAssets.DIAMOND;
			case NETHERITE -> EquipmentAssets.NETHERITE;
			default -> ResourceKey.create(EquipmentAssets.ROOT_ID, MlinNotas.id(tier.id()));
		};
	}

	private static TagKey<Block> blockTag(String name) {
		return TagKey.create(Registries.BLOCK, MlinNotas.id(name));
	}

	private static TagKey<Item> itemTag(String name) {
		return TagKey.create(Registries.ITEM, MlinNotas.id(name));
	}
}
