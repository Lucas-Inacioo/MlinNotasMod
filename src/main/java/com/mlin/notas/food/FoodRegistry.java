package com.mlin.notas.food;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registro da cozinha: culturas (plantadas e selvagens), sementes, comidas, as tres estacoes (Panela, Tabua de Corte,
 * Barril de Conserva), tipos de receita, o efeito Bem Alimentado, a marca de colheita e o historico de refeicoes.
 */
public final class FoodRegistry {
	// --- dados ---
	/** Estacao (numero absoluto) em que o produto perecivel foi colhido. */
	public static final DataComponentType<Long> HARVEST_SEASON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MlinNotas.id("harvest_season"), DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

	/** Ultimas comidas do jogador (ids), para a variedade; sincronizado so com o proprio jogador (dica do item). */
	public static final AttachmentType<List<String>> FOOD_HISTORY = AttachmentRegistry.create(MlinNotas.id("food_history"),
			b -> b.persistent(Codec.STRING.listOf()).copyOnDeath()
					.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, MlinNotas.id("heat_sources"));
	public static final TagKey<Item> CUTTABLE = TagKey.create(Registries.ITEM, MlinNotas.id("cuttable"));

	// --- receitas ---
	public static final RecipeType<CookingPotRecipe> COOKING_TYPE = recipeType("cooking");
	public static final RecipeType<CuttingRecipe> CUTTING_TYPE = recipeType("cutting");
	public static final RecipeType<FermentingRecipe> FERMENTING_TYPE = recipeType("fermenting");

	// --- efeito ---
	public static final Holder<MobEffect> WELL_FED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, MlinNotas.id("well_fed"),
			new WellFedEffect().addAttributeModifier(Attributes.MAX_HEALTH, MlinNotas.id("effect.well_fed"),
					FoodRules.WELL_FED_MAX_HEALTH_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE));

	// --- culturas ---
	private static final Map<String, Block> CROP_BLOCKS = new LinkedHashMap<>();
	private static final Map<String, Block> WILD_BLOCKS = new LinkedHashMap<>();
	private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

	static {
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			CROP_BLOCKS.put(crop.name(), Blocks.register(ModBlocks.key(crop.blockName()), p -> new ModCropBlock(p, () -> item(crop.seed())),
					BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP)
							.pushReaction(PushReaction.POPPED)));
			WILD_BLOCKS.put(crop.name(), Blocks.register(ModBlocks.key(crop.wildName()), WildCropBlock::new,
					BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS)
							.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)));
		}
	}

	// --- estacoes ---
	public static final Block COOKING_POT = Blocks.register(ModBlocks.key("cooking_pot"), CookingPotBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.LANTERN).requiresCorrectToolForDrops().noOcclusion());
	public static final Block CUTTING_BOARD = Blocks.register(ModBlocks.key("cutting_board"), CuttingBoardBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
	public static final Block PRESERVING_BARREL = Blocks.register(ModBlocks.key("preserving_barrel"), PreservingBarrelBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());

	public static final BlockEntityType<CookingPotBlockEntity> COOKING_POT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MlinNotas.id("cooking_pot"), new BlockEntityType<>(CookingPotBlockEntity::new, Set.of(COOKING_POT)));
	public static final BlockEntityType<PreservingBarrelBlockEntity> PRESERVING_BARREL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MlinNotas.id("preserving_barrel"), new BlockEntityType<>(PreservingBarrelBlockEntity::new, Set.of(PRESERVING_BARREL)));
	public static final MenuType<CookingPotMenu> COOKING_POT_MENU = Registry.register(BuiltInRegistries.MENU, MlinNotas.id("cooking_pot"),
			new MenuType<>(CookingPotMenu::new, FeatureFlags.VANILLA_SET));

	// --- itens (ordem da aba criativa) ---
	public static final Item COOKING_POT_ITEM = ModItems.register("cooking_pot", p -> new BlockItem(COOKING_POT, p.useBlockDescriptionPrefix()),
			station("cooking_pot"));
	public static final Item CUTTING_BOARD_ITEM = ModItems.register("cutting_board", p -> new BlockItem(CUTTING_BOARD, p.useBlockDescriptionPrefix()),
			station("cutting_board"));
	public static final Item PRESERVING_BARREL_ITEM = ModItems.register("preserving_barrel",
			p -> new BlockItem(PRESERVING_BARREL, p.useBlockDescriptionPrefix()), station("preserving_barrel"));

	static {
		// sementes separadas
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			if (crop.hasSeparateSeed()) {
				Block block = CROP_BLOCKS.get(crop.name());
				ITEMS.put(crop.seed(), ModItems.register(crop.seed(), p -> new BlockItem(block, p.useItemDescriptionPrefix()),
						new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW)));
			}
		}
		// produtos e comidas
		for (FoodCatalog.Food food : FoodCatalog.FOODS) {
			FoodCatalog.Crop planted = plantedBy(food.id());
			Item.Properties properties = properties(food);
			Item item;
			if (planted != null) {
				Block block = CROP_BLOCKS.get(planted.name());
				item = ModItems.register(food.id(), p -> new BlockItem(block, p.useItemDescriptionPrefix()), properties);
			} else {
				item = ModItems.register(food.id(), p -> new FoodItem(p, food), properties);
			}
			ITEMS.put(food.id(), item);
		}
	}

	private FoodRegistry() {
	}

	/** Propriedades de uma estacao: dica (lore) explicando o uso. */
	private static Item.Properties station(String id) {
		return new Item.Properties().component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
				List.of(net.minecraft.network.chat.Component.translatable("block.mlinnotas." + id + ".tooltip")
						.withStyle(net.minecraft.ChatFormatting.GRAY))));
	}

	private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeType<T> recipeType(String name) {
		return Registry.register(BuiltInRegistries.RECIPE_TYPE, MlinNotas.id(name), new RecipeType<T>() {
			@Override
			public String toString() {
				return name;
			}
		});
	}

	/** Cultura plantada pelo proprio produto (cebola, alho, arroz, feijao), ou null. */
	private static FoodCatalog.Crop plantedBy(String itemId) {
		for (FoodCatalog.Crop crop : FoodCatalog.CROPS) {
			if (!crop.hasSeparateSeed() && crop.seed().equals(itemId)) {
				return crop;
			}
		}
		return null;
	}

	private static Item.Properties properties(FoodCatalog.Food food) {
		Item.Properties properties = new Item.Properties().stacksTo(food.stack());
		if (food.kind() == FoodCatalog.Kind.PRODUCE || food.kind() == FoodCatalog.Kind.INGREDIENT && plantedBy(food.id()) != null) {
			properties.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM);
		}
		if (!food.edible()) {
			return properties;
		}
		FoodProperties nutrition = new FoodProperties.Builder().nutrition(food.nutrition()).saturationModifier(food.saturation()).build();
		Consumable.Builder consumable = Consumables.defaultFood();
		if (food.kind() == FoodCatalog.Kind.SNACK && food.nutrition() <= 3) {
			consumable.consumeSeconds(1.0F);
		}
		int wellFed = FoodRules.wellFedTicks(food.kind());
		if (wellFed > 0) {
			consumable.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(WELL_FED, wellFed, FoodRules.wellFedAmplifier(food.kind()))));
		}
		properties.food(nutrition, consumable.build());
		switch (food.container()) {
			case BOWL -> properties.usingConvertsTo(Items.BOWL);
			case BOTTLE -> properties.usingConvertsTo(Items.GLASS_BOTTLE).craftRemainder(Items.GLASS_BOTTLE);
			case NONE -> {
			}
		}
		return properties;
	}

	/** Item do mod pelo nome (semente, produto ou comida); null se nao existir. */
	public static Item item(String name) {
		return ITEMS.get(name);
	}

	public static Block crop(String name) {
		return CROP_BLOCKS.get(name);
	}

	public static Block wild(String name) {
		return WILD_BLOCKS.get(name);
	}

	public static List<Block> cropBlocks() {
		return new ArrayList<>(CROP_BLOCKS.values());
	}

	public static List<Block> wildBlocks() {
		return new ArrayList<>(WILD_BLOCKS.values());
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MlinNotas.id("cooking"), CookingPotRecipe.SERIALIZER);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MlinNotas.id("cutting"), CuttingRecipe.SERIALIZER);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MlinNotas.id("fermenting"), FermentingRecipe.SERIALIZER);
		FoodServer.init();
	}
}
