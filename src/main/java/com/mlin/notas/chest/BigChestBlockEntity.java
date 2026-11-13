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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Conteudo do bau grande. O tamanho vem do {@link ChestTier} do bloco.
 */
public class BigChestBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
	/** Evento de bloco que sincroniza o numero de jogadores com o bau aberto (igual ao bau vanilla). */
	private static final int EVENT_SET_OPEN_COUNT = 1;

	private final ChestLidController lidController = new ChestLidController();
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playSound(level, pos, SoundEvents.CHEST_OPEN);
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playSound(level, pos, SoundEvents.CHEST_CLOSE);
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
			level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, current);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof BigChestMenu menu && menu.getContainer() == BigChestBlockEntity.this;
		}
	};
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

	// --- animacao da tampa (mesma logica do ChestBlockEntity vanilla) ---

	/** Ticker de cliente: avanca a animacao da tampa. */
	public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, BigChestBlockEntity entity) {
		entity.lidController.tickLid();
	}

	@Override
	public float getOpenNess(float partialTicks) {
		return lidController.getOpenness(partialTicks);
	}

	@Override
	public boolean triggerEvent(int type, int data) {
		if (type == EVENT_SET_OPEN_COUNT) {
			lidController.shouldBeOpen(data > 0);
			return true;
		}
		return super.triggerEvent(type, data);
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (!this.remove && !user.getLivingEntity().isSpectator()) {
			openersCounter.incrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState(), user.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (!this.remove && !user.getLivingEntity().isSpectator()) {
			openersCounter.decrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState());
		}
	}

	@Override
	public List<ContainerUser> getEntitiesWithContainerOpen() {
		return openersCounter.getEntitiesWithContainerOpen(getLevel(), getBlockPos());
	}

	/** Quantos jogadores estao com o bau aberto (controla a tampa). */
	public int openerCount() {
		return openersCounter.getOpenerCount();
	}

	/** Chamado pelo tick agendado do bloco: fecha a tampa se quem abriu foi embora. */
	public void recheckOpen() {
		if (!this.remove) {
			openersCounter.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
		}
	}

	private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.5F,
				level.getRandom().nextFloat() * 0.1F + 0.9F);
	}
}
