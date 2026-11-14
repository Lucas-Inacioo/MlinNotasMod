package com.mlin.notas.grave;

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
import net.minecraft.world.level.material.PushReaction;

import java.util.Set;

/**
 * Lapides: bloco {@code mlinnotas:gravestone} (com block entity), item (so criativo/decoracao; sem receita) e o
 * lado servidor ({@link GraveServer}). Resistente a explosao (3.600.000), sem drop proprio (o conteudo e o que
 * importa), nao empurravel e nas tags {@code wither_immune}/{@code dragon_immune}.
 */
public final class GraveRegistry {
	public static final Block GRAVE = Blocks.register(ModBlocks.key("gravestone"), GraveBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(1.5F, 3600000.0F).sound(SoundType.STONE).noLootTable().noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE));
	public static final Item GRAVE_ITEM = ModItems.register("gravestone", p -> new BlockItem(GRAVE, p.useBlockDescriptionPrefix()),
			new Item.Properties().component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
					net.minecraft.network.chat.Component.translatable("block.mlinnotas.gravestone.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY)))));
	public static final BlockEntityType<GraveBlockEntity> GRAVE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MlinNotas.id("gravestone"), new BlockEntityType<>(GraveBlockEntity::new, Set.of(GRAVE)));

	private GraveRegistry() {
	}

	public static void init() {
		GraveServer.init();
	}
}
