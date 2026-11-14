package com.mlin.notas.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Unidade de Armazenamento no mundo: guarda o {@link StorageCell} do nivel. O conteudo vai para o item ao quebrar
 * (componente {@code mlinnotas:storage_contents} via loot table {@code copy_components}) e volta ao colocar.
 */
public class StorageUnitBlockEntity extends BlockEntity {
	private final StorageTier tier;
	private final StorageCell<ItemKey> cell;
	private @Nullable Component customName;

	public StorageUnitBlockEntity(BlockPos pos, BlockState state) {
		super(StorageRegistry.UNIT_ENTITY, pos, state);
		this.tier = state.getBlock() instanceof StorageUnitBlock unit ? unit.tier() : StorageTier.COPPER;
		this.cell = new StorageCell<>(tier, StorageRegistry::canStoreInUnit);
		this.cell.setOnChange(this::setChanged);
	}

	public StorageTier tier() {
		return tier;
	}

	public StorageCell<ItemKey> cell() {
		return cell;
	}

	public @Nullable Component getCustomName() {
		return customName;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		cell.load(input.read("items", StoredItems.CODEC).orElse(StoredItems.EMPTY).toMap());
		customName = parseCustomNameSafe(input, "CustomName");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!cell.isEmpty()) {
			output.store("items", StoredItems.CODEC, StoredItems.of(cell.contents()));
		}
		output.storeNullable("CustomName", net.minecraft.network.chat.ComponentSerialization.CODEC, customName);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		customName = components.get(DataComponents.CUSTOM_NAME);
		cell.load(components.getOrDefault(StorageRegistry.CONTENTS, StoredItems.EMPTY).toMap());
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, customName);
		if (!cell.isEmpty()) {
			components.set(StorageRegistry.CONTENTS, StoredItems.of(cell.contents()));
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard("items");
		output.discard("CustomName");
	}
}
