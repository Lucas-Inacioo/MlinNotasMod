package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Set;

/** Registro dos blocos das construcoes de oficios (Vilas 2.0, fase 6): a tina de curtir do curtume. */
public final class CraftsRegistry {
	public static final Block TANNING_VAT = Blocks.register(ModBlocks.key("tanning_vat"), TanningVatBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
	public static final BlockEntityType<TanningVatBlockEntity> TANNING_VAT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MlinNotas.id("tanning_vat"), new BlockEntityType<>(TanningVatBlockEntity::new, Set.of(TANNING_VAT)));
	public static final Item TANNING_VAT_ITEM = ModItems.register("tanning_vat", p -> new BlockItem(TANNING_VAT, p.useBlockDescriptionPrefix()),
			new Item.Properties().component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
					List.of(net.minecraft.network.chat.Component.translatable("block.mlinnotas.tanning_vat.tooltip")
							.withStyle(net.minecraft.ChatFormatting.GRAY)))));

	private CraftsRegistry() {
	}

	/** Carrega a classe (registra os blocos). */
	public static void init() {
	}
}
