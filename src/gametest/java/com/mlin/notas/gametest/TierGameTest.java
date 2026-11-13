package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.ModWorldgen;
import com.mlin.notas.tier.Tier;
import com.mlin.notas.wand.BuildingWandItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;

/** Game tests da escada de 12 niveis: rebalanceamento vanilla, requisitos de picareta, minerios e receitas. */
public class TierGameTest {
	private static double sum(Item item, Holder<Attribute> attribute) {
		ItemAttributeModifiers modifiers = new ItemStack(item).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return modifiers.modifiers().stream().filter(e -> e.attribute().is(attribute)).mapToDouble(e -> e.modifier().amount()).sum();
	}

	private static double setArmor(List<Item> set) {
		return set.stream().mapToDouble(item -> sum(item, Attributes.ARMOR)).sum();
	}

	@GameTest
	public void diamondArmorNoLongerFillsTheBar(GameTestHelper helper) {
		double diamond = setArmor(List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS));
		double netherite = setArmor(List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS));
		double iron = setArmor(List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS));
		double celestium = 0;
		for (Tier.ArmorPiece piece : Tier.ArmorPiece.values()) {
			celestium += sum(ModItems.armor(Tier.CELESTIUM, piece), Attributes.ARMOR);
		}
		helper.assertTrue(diamond == Tier.DIAMOND.fullSetDefense(), "diamante deveria somar " + Tier.DIAMOND.fullSetDefense() + ", soma " + diamond);
		helper.assertTrue(diamond < 20, "diamante longe da barra cheia");
		helper.assertTrue(netherite == Tier.NETHERITE.fullSetDefense(), "netherite rebalanceado, soma " + netherite);
		helper.assertTrue(iron == Tier.IRON.fullSetDefense(), "ferro rebalanceado, soma " + iron);
		helper.assertTrue(celestium == 20, "so o celestio enche a barra, soma " + celestium);
		helper.assertTrue(sum(Items.DIAMOND_CHESTPLATE, Attributes.ARMOR_TOUGHNESS) == Tier.DIAMOND.toughness(), "tenacidade do diamante");
		helper.assertTrue(new ItemStack(Items.DIAMOND_CHESTPLATE).getMaxDamage() == Tier.DIAMOND.armorDurability(Tier.ArmorPiece.CHESTPLATE), "durabilidade do peitoral");
		helper.succeed();
	}

	@GameTest
	public void vanillaToolsAreRebalanced(GameTestHelper helper) {
		helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).getMaxDamage() == 1000, "picareta de diamante com 1000 de durabilidade");
		helper.assertTrue(new ItemStack(Items.NETHERITE_SWORD).getMaxDamage() == 1400, "espada de netherite com 1400");
		helper.assertTrue(new ItemStack(Items.GOLDEN_PICKAXE).getMaxDamage() == 400, "ouro virou um nivel de verdade");
		// espada: 3 de base + bonus do material
		helper.assertTrue(sum(Items.NETHERITE_SWORD, Attributes.ATTACK_DAMAGE) == 3 + Tier.NETHERITE.attackBonus(), "dano do netherite");
		helper.assertTrue(sum(Items.GOLDEN_SWORD, Attributes.ATTACK_DAMAGE) == 3 + Tier.GOLD.attackBonus(), "dano do ouro");
		helper.assertTrue(sum(Items.IRON_SWORD, Attributes.ATTACK_DAMAGE) == 3 + Tier.IRON.attackBonus(), "ferro igual");
		float goldSpeed = new ItemStack(Items.GOLDEN_PICKAXE).getDestroySpeed(Blocks.STONE.defaultBlockState());
		helper.assertTrue(goldSpeed == Tier.GOLD.miningSpeed(), "velocidade do ouro " + goldSpeed);
		// itens novos usam a mesma tabela
		helper.assertTrue(new ItemStack(ModItems.gear(Tier.CELESTIUM, GearKind.PICKAXE)).getMaxDamage() == Tier.CELESTIUM.toolDurability(), "celestio");
		helper.assertTrue(sum(ModItems.gear(Tier.CELESTIUM, GearKind.SWORD), Attributes.ATTACK_DAMAGE) == 3 + Tier.CELESTIUM.attackBonus(), "dano celestio");
		helper.succeed();
	}

	private static boolean mines(Item pickaxe, Block block) {
		return new ItemStack(pickaxe).isCorrectToolForDrops(block.defaultBlockState());
	}

	@GameTest
	public void pickaxeRequirementsFollowTheLadder(GameTestHelper helper) {
		Item cobalt = ModItems.gear(Tier.COBALT, GearKind.PICKAXE);
		Item infernite = ModItems.gear(Tier.INFERNITE, GearKind.PICKAXE);
		Item endite = ModItems.gear(Tier.ENDITE, GearKind.PICKAXE);
		helper.assertTrue(mines(Items.IRON_PICKAXE, ModBlocks.COBALT_ORE), "ferro minera cobalto");
		helper.assertTrue(!mines(Items.COPPER_PICKAXE, ModBlocks.COBALT_ORE), "cobre nao minera cobalto");
		helper.assertTrue(!mines(Items.IRON_PICKAXE, Blocks.DIAMOND_ORE), "ferro nao minera mais diamante");
		helper.assertTrue(!mines(Items.GOLDEN_PICKAXE, Blocks.DEEPSLATE_DIAMOND_ORE), "ouro nao minera diamante");
		helper.assertTrue(mines(Items.GOLDEN_PICKAXE, Blocks.IRON_ORE), "ouro agora minera ferro");
		helper.assertTrue(mines(Items.GOLDEN_PICKAXE, ModBlocks.DEEPSLATE_COBALT_ORE), "ouro minera cobalto");
		helper.assertTrue(mines(cobalt, Blocks.DIAMOND_ORE), "cobalto minera diamante");
		helper.assertTrue(!mines(cobalt, Blocks.ANCIENT_DEBRIS), "cobalto nao minera detritos");
		helper.assertTrue(!mines(Items.DIAMOND_PICKAXE, ModBlocks.INFERNITE_ORE), "diamante nao minera infernita");
		helper.assertTrue(mines(Items.NETHERITE_PICKAXE, ModBlocks.INFERNITE_ORE), "netherite minera infernita");
		helper.assertTrue(!mines(Items.NETHERITE_PICKAXE, ModBlocks.ENDITE_ORE), "netherite nao minera endita");
		helper.assertTrue(mines(infernite, ModBlocks.ENDITE_ORE), "infernita minera endita");
		helper.assertTrue(!mines(infernite, ModBlocks.ASTRALITE_ORE), "infernita nao minera astralita");
		helper.assertTrue(mines(endite, ModBlocks.ASTRALITE_ORE), "endita minera astralita");
		helper.succeed();
	}

	@GameTest
	public void oresDropRawMaterial(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		for (Block ore : List.of(ModBlocks.COBALT_ORE, ModBlocks.INFERNITE_ORE, ModBlocks.ENDITE_ORE, ModBlocks.ASTRALITE_ORE)) {
			BlockState state = ore.defaultBlockState();
			List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, null, null, new ItemStack(ModItems.gear(Tier.CELESTIUM, GearKind.PICKAXE)));
			boolean raw = drops.stream().anyMatch(stack -> stack.getItem().toString().contains("raw_"));
			helper.assertTrue(raw, ore + " deveria dropar o minerio bruto, dropou " + drops);
		}
		helper.succeed();
	}

	private static boolean biomeHas(GameTestHelper helper, ResourceKey<Biome> biome, ResourceKey<PlacedFeature> feature) {
		Biome value = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome).value();
		for (HolderSet<PlacedFeature> step : value.getGenerationSettings().features()) {
			for (Holder<PlacedFeature> holder : step) {
				if (holder.is(feature)) {
					return true;
				}
			}
		}
		return false;
	}

	@GameTest
	public void newOresOnlyGenerateInTheirDimension(GameTestHelper helper) {
		helper.assertTrue(biomeHas(helper, Biomes.PLAINS, ModWorldgen.ORE_COBALT), "cobalto no Overworld");
		helper.assertTrue(!biomeHas(helper, Biomes.PLAINS, ModWorldgen.ORE_INFERNITE), "infernita fora do Overworld");
		helper.assertTrue(biomeHas(helper, Biomes.NETHER_WASTES, ModWorldgen.ORE_INFERNITE), "infernita no Nether");
		helper.assertTrue(!biomeHas(helper, Biomes.NETHER_WASTES, ModWorldgen.ORE_ENDITE), "endita fora do Nether");
		helper.assertTrue(biomeHas(helper, Biomes.THE_END, ModWorldgen.ORE_ENDITE), "endita no End");
		helper.assertTrue(!biomeHas(helper, Biomes.THE_END, ModWorldgen.ORE_ASTRALITE), "astralita nao na ilha central");
		helper.assertTrue(biomeHas(helper, Biomes.END_HIGHLANDS, ModWorldgen.ORE_ASTRALITE), "astralita nas ilhas externas");
		helper.succeed();
	}

	@GameTest
	public void recipesForTheNewTiersExist(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		for (String id : List.of("celestium_ingot", "cobalt_pickaxe", "endite_chestplate", "astralite_spear", "cobalt_ingot_from_smelting_raw_cobalt",
				"celestium_chest", "chest_upgrade_stone", "furnace_upgrade_celestium", "netherite_wand")) {
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, MlinNotas.id(id));
			helper.assertTrue(recipes.byKey(key).isPresent(), "receita " + id + " deveria existir");
		}
		helper.succeed();
	}

	@GameTest
	public void everyTierHasWandChestAndFurnaceUpgrade(GameTestHelper helper) {
		int previousBlocks = 0;
		for (Tier tier : Tier.values()) {
			BuildingWandItem wand = (BuildingWandItem) ModItems.wand(tier);
			helper.assertTrue(wand != null, "varinha " + tier);
			helper.assertTrue(wand.maxBlocks() > previousBlocks, "varinha " + tier + " coloca mais blocos que a anterior");
			previousBlocks = wand.maxBlocks();
			if (tier != Tier.WOOD) {
				helper.assertTrue(ModItems.furnaceUpgrade(tier) != null, "upgrade de fornalha " + tier);
			}
		}
		helper.assertTrue(ModBlocks.chests().size() == 11, "11 baus do mod + o vanilla");
		helper.succeed();
	}
}
