package com.mlin.notas.storage;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rede de armazenamento: Unidades de Armazenamento (uma por {@link StorageTier}), Cabo, Terminal e Terminal de
 * Crafting, o block entity das unidades, o componente com o conteudo, o menu e os pacotes do terminal.
 */
public final class StorageRegistry {
	/** Baus que entram na rede quando encostam num cabo, unidade ou terminal. */
	public static final TagKey<Block> CONNECTABLE = TagKey.create(Registries.BLOCK, MlinNotas.id("storage_connectable"));

	public static final DataComponentType<StoredItems> CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MlinNotas.id("storage_contents"), DataComponentType.<StoredItems>builder()
					.persistent(StoredItems.CODEC)
					.networkSynchronized(StoredItems.STREAM_CODEC)
					.build());

	private static final Map<StorageTier, Block> UNITS = new EnumMap<>(StorageTier.class);
	private static final Map<StorageTier, Item> UNIT_ITEMS = new EnumMap<>(StorageTier.class);

	static {
		for (StorageTier tier : StorageTier.values()) {
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6.0F)
					.sound(SoundType.COPPER);
			Block block = Blocks.register(ModBlocks.key(tier.blockName()), p -> new StorageUnitBlock(p, tier), properties);
			UNITS.put(tier, block);
			Item.Properties itemProperties = new Item.Properties().stacksTo(1);
			if (tier.material().fireResistant()) {
				itemProperties.fireResistant();
			}
			UNIT_ITEMS.put(tier, ModItems.register(tier.blockName(), p -> new StorageUnitItem(block, tier, p.useBlockDescriptionPrefix()),
					itemProperties));
		}
	}

	public static final Block CABLE = Blocks.register(ModBlocks.key("storage_cable"), StorageCableBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GRAY).strength(0.8F).sound(SoundType.COPPER).noOcclusion());
	public static final Block TERMINAL = Blocks.register(ModBlocks.key("storage_terminal"), p -> new StorageTerminalBlock(p, false),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.5F, 6.0F).sound(SoundType.COPPER).lightLevel(state -> 6));
	public static final Block CRAFTING_TERMINAL = Blocks.register(ModBlocks.key("crafting_terminal"), p -> new StorageTerminalBlock(p, true),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.5F, 6.0F).sound(SoundType.COPPER).lightLevel(state -> 6));

	public static final Item CABLE_ITEM = ModItems.register("storage_cable", p -> new BlockItem(CABLE, p.useBlockDescriptionPrefix()), new Item.Properties());
	public static final Item TERMINAL_ITEM = ModItems.register("storage_terminal", p -> new BlockItem(TERMINAL, p.useBlockDescriptionPrefix()),
			new Item.Properties());
	public static final Item CRAFTING_TERMINAL_ITEM = ModItems.register("crafting_terminal",
			p -> new BlockItem(CRAFTING_TERMINAL, p.useBlockDescriptionPrefix()), new Item.Properties());

	public static final BlockEntityType<StorageUnitBlockEntity> UNIT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			MlinNotas.id("storage_unit"), new BlockEntityType<>(StorageUnitBlockEntity::new, Set.copyOf(UNITS.values())));

	public static final ExtendedMenuType<TerminalMenu, TerminalMenu.Data> TERMINAL_MENU = Registry.register(BuiltInRegistries.MENU,
			MlinNotas.id("storage_terminal"), new ExtendedMenuType<>(TerminalMenu::new, TerminalMenu.Data.STREAM_CODEC));

	private StorageRegistry() {
	}

	public static Block unit(StorageTier tier) {
		return UNITS.get(tier);
	}

	public static Item unitItem(StorageTier tier) {
		return UNIT_ITEMS.get(tier);
	}

	public static List<Block> units() {
		return new ArrayList<>(UNITS.values());
	}

	/** O cabo encaixa neste bloco? */
	public static boolean connectsTo(BlockState state) {
		Block block = state.getBlock();
		return block instanceof StorageCableBlock || block instanceof StorageUnitBlock || block instanceof StorageTerminalBlock
				|| state.is(CONNECTABLE);
	}

	/** Unidade nao guarda outra unidade com conteudo (evita aninhar dados sem fim). */
	public static boolean canStoreInUnit(ItemKey key) {
		return !key.template().has(CONTENTS);
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TerminalPayloads.Sync.TYPE, TerminalPayloads.Sync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TerminalPayloads.Action.TYPE, TerminalPayloads.Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TerminalPayloads.Action.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId()
					&& menu.stillValid(context.player())) {
				menu.handleAction(context.player(), payload.action(), payload.id());
			}
		});
	}
}
