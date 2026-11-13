package com.mlin.notas.chest;

import com.mlin.notas.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Conteudo do bau grande. O tamanho vem do {@link ChestTier} do bloco.
 */
public class BigChestBlockEntity extends RandomizableContainerBlockEntity {
	private final ChestTier tier;
	private NonNullList<ItemStack> items;
	private @Nullable Component customName;

	public BigChestBlockEntity(BlockPos worldPosition, BlockState blockState) {
		this(worldPosition, blockState, tierOf(blockState));
	}

	public BigChestBlockEntity(BlockPos worldPosition, BlockState blockState, ChestTier tier) {
		super(ModBlockEntities.BIG_CHEST, worldPosition, blockState);
		this.tier = tier;
		this.items = NonNullList.withSize(tier.size(), ItemStack.EMPTY);
	}

	private static ChestTier tierOf(BlockState state) {
		return state.getBlock() instanceof BigChestBlock chest ? chest.tier() : ChestTier.IRON;
	}

	public ChestTier tier() {
		return tier;
	}

	@Override
	public int getContainerSize() {
		return tier.size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	/** Copia publica do conteudo (para upgrades e testes). */
	public NonNullList<ItemStack> copyItems() {
		NonNullList<ItemStack> copy = NonNullList.withSize(items.size(), ItemStack.EMPTY);
		for (int i = 0; i < items.size(); i++) {
			copy.set(i, items.get(i).copy());
		}
		return copy;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.mlinnotas." + tier.blockName());
	}

	@Override
	public @Nullable Component getCustomName() {
		return customName;
	}

	public void setCustomName(@Nullable Component name) {
		this.customName = name;
		setChanged();
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new BigChestMenu(tier, containerId, inventory, this);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items);
		}
		output.storeNullable("CustomName", ComponentSerialization.CODEC, customName);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!this.tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
		this.customName = parseCustomNameSafe(input, "CustomName");
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		this.customName = components.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, customName);
	}
}
