package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.backpack.BackpackItem;
import com.mlin.notas.backpack.BackpackUpgradeItem;
import com.mlin.notas.backpack.BackpackUpgradeKind;
import com.mlin.notas.chest.ChestTier;
import com.mlin.notas.chest.ChestUpgradeItem;
import com.mlin.notas.furnace.FurnaceUpgradeItem;
import com.mlin.notas.tablet.CraftingTabletItem;
import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.ModMaterials;
import com.mlin.notas.tier.Tier;
import com.mlin.notas.tier.VanillaRebalance;
import com.mlin.notas.wand.BuildingWandItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Itens do mod. A ordem de registro e a ordem na aba criativa.
 */
public final class ModItems {
	private ModItems() {
	}

	/** Todos os itens registrados, na ordem, para a aba criativa. */
	public static final List<Item> ALL = new ArrayList<>();

	private static final Map<Tier, Item> RAW = new EnumMap<>(Tier.class);
	private static final Map<Tier, Item> INGOTS = new EnumMap<>(Tier.class);
	private static final Map<Tier, Map<GearKind, Item>> GEAR = new EnumMap<>(Tier.class);
	private static final Map<Tier, Map<Tier.ArmorPiece, Item>> ARMOR = new EnumMap<>(Tier.class);
	private static final Map<Tier, Item> WANDS = new EnumMap<>(Tier.class);
	private static final Map<Tier, Item> FURNACE_UPGRADES = new EnumMap<>(Tier.class);
	private static final Map<ChestTier, Item> CHESTS = new EnumMap<>(ChestTier.class);
	private static final Map<ChestTier, Item> CHEST_UPGRADES = new EnumMap<>(ChestTier.class);

	/** Ordem das pecas na aba criativa (como no inventario vanilla). */
	private static final Tier.ArmorPiece[] ARMOR_ORDER = {
			Tier.ArmorPiece.HELMET, Tier.ArmorPiece.CHESTPLATE, Tier.ArmorPiece.LEGGINGS, Tier.ArmorPiece.BOOTS};

	static {
		// materiais novos: minerio bruto -> lingote (celestio e liga, so lingote)
		for (Tier tier : Tier.values()) {
			if (tier.hasOre()) {
				RAW.put(tier, register("raw_" + tier.id(), Item::new, materialProperties(tier)));
			}
			if (tier.modGear()) {
				INGOTS.put(tier, register(tier.id() + "_ingot", Item::new, materialProperties(tier)));
			}
		}
		for (Block ore : ModBlocks.ores()) {
			registerBlock(ore, BuiltInRegistries.BLOCK.getKey(ore).getPath(), new Item.Properties());
		}
		// blocos de metal e de minerio bruto
		for (Tier tier : Tier.values()) {
			if (ModBlocks.storage(tier) != null) {
				registerBlock(ModBlocks.storage(tier), tier.id() + "_block", materialProperties(tier));
			}
			if (ModBlocks.rawStorage(tier) != null) {
				registerBlock(ModBlocks.rawStorage(tier), "raw_" + tier.id() + "_block", materialProperties(tier));
			}
		}
		// ferramentas e armaduras dos niveis novos
		for (Tier tier : Tier.values()) {
			if (tier.modGear()) {
				registerGear(tier);
			}
		}
		for (Tier tier : Tier.values()) {
			String name = tier == Tier.WOOD ? "wooden_wand" : tier.id() + "_wand";
			Item.Properties properties = new Item.Properties().stacksTo(1).durability(tier.wandDurability());
			if (tier.fireResistant()) {
				properties.fireResistant();
			}
			WANDS.put(tier, register(name, p -> new BuildingWandItem(p, tier.wandBlocks()), properties));
		}
	}

	public static final Item COBALT_INGOT = ingot(Tier.COBALT);
	public static final Item CELESTIUM_INGOT = ingot(Tier.CELESTIUM);

	public static final Item WOODEN_WAND = wand(Tier.WOOD);
	public static final Item STONE_WAND = wand(Tier.STONE);
	public static final Item IRON_WAND = wand(Tier.IRON);
	public static final Item DIAMOND_WAND = wand(Tier.DIAMOND);

	public static final Item CRAFTING_TABLET = register("crafting_tablet", CraftingTabletItem::new, new Item.Properties().stacksTo(1));

	static {
		for (Tier tier : Tier.values()) {
			if (tier != Tier.WOOD) {
				FURNACE_UPGRADES.put(tier, register("furnace_upgrade_" + tier.id(), p -> new FurnaceUpgradeItem(p, tier), new Item.Properties().stacksTo(16)));
			}
		}
		for (ChestTier tier : ChestTier.values()) {
			CHESTS.put(tier, registerBlock(ModBlocks.chest(tier), tier.blockName(), materialProperties(tier.material())));
			CHEST_UPGRADES.put(tier, register(tier.upgradeName(), p -> new ChestUpgradeItem(p, tier), new Item.Properties().stacksTo(16)));
		}
	}

	public static final Item FURNACE_UPGRADE_IRON = furnaceUpgrade(Tier.IRON);
	public static final Item FURNACE_UPGRADE_GOLD = furnaceUpgrade(Tier.GOLD);
	public static final Item FURNACE_UPGRADE_DIAMOND = furnaceUpgrade(Tier.DIAMOND);

	public static final Item IRON_CHEST = chest(ChestTier.IRON);
	public static final Item GOLD_CHEST = chest(ChestTier.GOLD);
	public static final Item DIAMOND_CHEST = chest(ChestTier.DIAMOND);
	public static final Item CHEST_UPGRADE_IRON = chestUpgrade(ChestTier.IRON);
	public static final Item CHEST_UPGRADE_GOLD = chestUpgrade(ChestTier.GOLD);
	public static final Item CHEST_UPGRADE_DIAMOND = chestUpgrade(ChestTier.DIAMOND);

	public static final Item BACKPACK = register("backpack", BackpackItem::new, new Item.Properties().stacksTo(1));
	public static final Item BACKPACK_UPGRADE_SIZE = registerBackpackUpgrade(BackpackUpgradeKind.SIZE);
	public static final Item BACKPACK_UPGRADE_CRAFTING = registerBackpackUpgrade(BackpackUpgradeKind.CRAFTING);
	public static final Item BACKPACK_UPGRADE_FURNACE = registerBackpackUpgrade(BackpackUpgradeKind.FURNACE);
	public static final Item BACKPACK_UPGRADE_WATER = registerBackpackUpgrade(BackpackUpgradeKind.WATER);
	public static final Item BACKPACK_UPGRADE_LAVA = registerBackpackUpgrade(BackpackUpgradeKind.LAVA);

	// End: blocos dos biomas e da cidadela, drops das criaturas e ovos
	static {
		for (Block block : ModBlocks.endBlocks()) {
			String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
			if (block == ModBlocks.PURPUR_ELEVATOR) {
				register(name, p -> new BlockItem(block, p.useBlockDescriptionPrefix()) {
					@Override
					public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
						tooltip.accept(Component.translatable("block.mlinnotas.purpur_elevator.tooltip").withStyle(ChatFormatting.GRAY));
					}
				}, new Item.Properties());
			} else {
				registerBlock(block, name, new Item.Properties());
			}
		}
	}

	public static final Item ETHER_CRYSTAL_SHARD = register("ether_crystal_shard", Item::new, new Item.Properties());
	public static final Item SENTINEL_CORE = register("sentinel_core", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	/** Geleia do Vazio: comida que da queda lenta (ajuda a atravessar as ilhas do End). */
	public static final Item VOID_JELLY = register("void_jelly", Item::new, new Item.Properties().food(
			new FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).alwaysEdible().build(),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600))).build()));
	public static final Item PURPUR_SENTINEL_SPAWN_EGG = register("purpur_sentinel_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.PURPUR_SENTINEL));
	public static final Item VOID_JELLY_SPAWN_EGG = register("void_jelly_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.VOID_JELLY));
	public static final Item CRYSTAL_MITE_SPAWN_EGG = register("crystal_mite_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.CRYSTAL_MITE));
	public static final Item CHORUS_HOPPER_SPAWN_EGG = register("chorus_hopper_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.CHORUS_HOPPER));

	// pedras de teleporte (waystone/)
	public static final Item WAYSTONE = register("waystone", p -> new BlockItem(ModBlocks.WAYSTONE, p.useBlockDescriptionPrefix()) {
		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			tooltip.accept(Component.translatable("block.mlinnotas.waystone.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item RETURN_SCROLL = register("return_scroll", com.mlin.notas.waystone.ReturnScrollItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	// quadro de missoes da vila (village/)
	public static final Item VILLAGE_BOARD = register("village_board", p -> new BlockItem(ModBlocks.VILLAGE_BOARD, p.useBlockDescriptionPrefix()) {
		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			tooltip.accept(Component.translatable("block.mlinnotas.village_board.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}, new Item.Properties());

	public static ResourceKey<Item> key(String name) {
		return ResourceKey.create(Registries.ITEM, MlinNotas.id(name));
	}

	public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = key(name);
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		ALL.add(item);
		return item;
	}

	private static Item registerBlock(Block block, String name, Item.Properties properties) {
		return register(name, p -> new BlockItem(block, p.useBlockDescriptionPrefix()), properties);
	}

	private static Item registerBackpackUpgrade(BackpackUpgradeKind kind) {
		return register(kind.itemName(), p -> new BackpackUpgradeItem(p, kind), new Item.Properties().stacksTo(16));
	}

	/** Raridade cresce com o nivel; do netherite para cima o item nao queima. */
	private static Item.Properties materialProperties(Tier tier) {
		Item.Properties properties = new Item.Properties();
		if (tier.fireResistant()) {
			properties.fireResistant();
		}
		if (tier == Tier.CELESTIUM) {
			properties.rarity(Rarity.EPIC);
		} else if (tier.ordinal() >= Tier.ENDITE.ordinal()) {
			properties.rarity(Rarity.RARE);
		} else if (tier == Tier.INFERNITE) {
			properties.rarity(Rarity.UNCOMMON);
		}
		return properties;
	}

	private static void registerGear(Tier tier) {
		ToolMaterial material = ModMaterials.tool(tier);
		Map<GearKind, Item> gear = new EnumMap<>(GearKind.class);
		for (GearKind kind : GearKind.values()) {
			Item.Properties properties = materialProperties(tier);
			switch (kind) {
				case SWORD -> properties.sword(material, kind.attackDamageBaseline(), kind.attackSpeedBaseline());
				case SHOVEL -> properties.shovel(material, kind.attackDamageBaseline(), kind.attackSpeedBaseline());
				case PICKAXE -> properties.pickaxe(material, kind.attackDamageBaseline(), kind.attackSpeedBaseline());
				case AXE -> properties.axe(material, kind.attackDamageBaseline(), kind.attackSpeedBaseline());
				// enxada: dano total 1, como as enxadas vanilla de diamante/netherite
				case HOE -> properties.hoe(material, -tier.attackBonus(), 0.0F);
				// lanca: mesmos tempos da lanca de netherite
				case SPEAR -> properties.spear(material, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F);
			}
			gear.put(kind, register(kind.itemName(tier), Item::new, properties));
		}
		GEAR.put(tier, gear);
		Map<Tier.ArmorPiece, Item> armor = new EnumMap<>(Tier.ArmorPiece.class);
		for (Tier.ArmorPiece piece : ARMOR_ORDER) {
			Item.Properties properties = materialProperties(tier).humanoidArmor(ModMaterials.armor(tier), ModMaterials.armorType(piece));
			armor.put(piece, register(VanillaRebalance.armorName(tier, piece), Item::new, properties));
		}
		ARMOR.put(tier, armor);
	}

	public static Item raw(Tier tier) {
		return RAW.get(tier);
	}

	public static Item ingot(Tier tier) {
		return INGOTS.get(tier);
	}

	/** Ferramenta/arma de um nivel novo (null para niveis vanilla). */
	public static Item gear(Tier tier, GearKind kind) {
		Map<GearKind, Item> map = GEAR.get(tier);
		return map == null ? null : map.get(kind);
	}

	/** Peca de armadura de um nivel novo (null para niveis vanilla). */
	public static Item armor(Tier tier, Tier.ArmorPiece piece) {
		Map<Tier.ArmorPiece, Item> map = ARMOR.get(tier);
		return map == null ? null : map.get(piece);
	}

	public static Item wand(Tier tier) {
		return WANDS.get(tier);
	}

	/** Upgrade de fornalha do nivel (null para madeira, que e a fornalha sem upgrade). */
	public static Item furnaceUpgrade(Tier tier) {
		return FURNACE_UPGRADES.get(tier);
	}

	public static Item chest(ChestTier tier) {
		return CHESTS.get(tier);
	}

	public static Item chestUpgrade(ChestTier tier) {
		return CHEST_UPGRADES.get(tier);
	}

	/** Forca o carregamento da classe (e o registro dos campos estaticos). */
	public static void init() {
	}
}
