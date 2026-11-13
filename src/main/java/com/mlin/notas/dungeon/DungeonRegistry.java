package com.mlin.notas.dungeon;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

/**
 * Masmorras medias: estrutura {@code mlinnotas:dungeon} (um JSON por tema, structure set {@code mlinnotas:dungeons}),
 * peca, Chave da Masmorra, Portao Selado e os mini-chefes ({@link DungeonBosses}).
 */
public final class DungeonRegistry {
	public static final StructureType<DungeonStructure> DUNGEON = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, MlinNotas.id("dungeon"),
			() -> DungeonStructure.CODEC);
	public static final StructurePieceType DUNGEON_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, MlinNotas.id("dungeon"),
			(StructurePieceType.ContextlessType) DungeonPiece::new);

	public static final Block GATE = Blocks.register(ModBlocks.key("dungeon_gate"), DungeonGateBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL).lightLevel(state -> 3)
			.pushReaction(PushReaction.IMMOVEABLE));
	public static final Item GATE_ITEM = ModItems.register("dungeon_gate", p -> new BlockItem(GATE, p.useBlockDescriptionPrefix()),
			new Item.Properties().rarity(Rarity.RARE).component(DataComponents.LORE, lore("block.mlinnotas.dungeon_gate")));
	/** Chave da Masmorra: abre o Portao Selado (sem gastar) e o cofre da camara final (gasta, um premio por jogador). */
	public static final Item KEY = ModItems.register("dungeon_key", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
			.component(DataComponents.LORE, lore("item.mlinnotas.dungeon_key")));

	private DungeonRegistry() {
	}

	private static ItemLore lore(String prefix) {
		return new ItemLore(List.of(Component.translatable(prefix + ".tooltip").withStyle(ChatFormatting.GRAY)));
	}

	public static ResourceKey<Structure> structureKey(DungeonTheme theme) {
		return ResourceKey.create(Registries.STRUCTURE, MlinNotas.id(theme.id()));
	}

	public static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, MlinNotas.id("chests/" + name));
	}

	public static void init() {
		DungeonBosses.init();
	}
}
