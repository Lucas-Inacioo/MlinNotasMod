package com.mlin.notas.food;

import com.mlin.notas.season.SeasonState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;
import java.util.Optional;

/**
 * Barril de Conserva: 9 espacos. O que esta dentro nao envelhece (o frescor da horta para de contar) e as receitas
 * {@link FermentingRecipe} curam a pilha no lugar depois de alguns dias de jogo (queijo fresco -> curado, repolho ->
 * chucrute). O tempo de cada espaco recomeca quando o item do espaco muda.
 */
public class PreservingBarrelBlockEntity extends BaseContainerBlockEntity {
	public static final int SIZE = 9;
	/** O barril confere o conteudo a cada N ticks. */
	public static final int CHECK_TICKS = 20;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int[] progress = new int[SIZE];
	private long lastSeason = Long.MIN_VALUE;
	private int clock;

	public PreservingBarrelBlockEntity(BlockPos pos, BlockState state) {
		super(FoodRegistry.PRESERVING_BARREL_ENTITY, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.mlinnotas.preserving_barrel");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (!ItemStack.isSameItem(items.get(slot), stack)) {
			progress[slot] = 0;
		}
		super.setItem(slot, stack);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		progress = Arrays.copyOf(input.getIntArray("progress").orElse(new int[SIZE]), SIZE);
		lastSeason = input.getLongOr("last_season", Long.MIN_VALUE);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		output.putIntArray("progress", progress);
		output.putLong("last_season", lastSeason);
	}

	/** Ticks de cura ja acumulados no espaco. */
	public int progress(int slot) {
		return progress[slot];
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, PreservingBarrelBlockEntity barrel) {
		if (level instanceof ServerLevel serverLevel && ++barrel.clock >= CHECK_TICKS) {
			barrel.clock = 0;
			barrel.advance(serverLevel, CHECK_TICKS);
		}
	}

	/** Avanca a cura de todos os espacos em {@code ticks} e segura o frescor do que esta dentro. */
	public void advance(ServerLevel level, int ticks) {
		boolean changed = keepFresh();
		for (int slot = 0; slot < SIZE; slot++) {
			ItemStack stack = items.get(slot);
			Optional<RecipeHolder<FermentingRecipe>> recipe = stack.isEmpty() ? Optional.empty()
					: level.recipeAccess().getRecipeFor(FoodRegistry.FERMENTING_TYPE, new SingleRecipeInput(stack), level);
			if (recipe.isEmpty()) {
				progress[slot] = 0;
				continue;
			}
			progress[slot] += ticks;
			if (progress[slot] >= recipe.get().value().ticks()) {
				ItemStack result = recipe.get().value().output().create();
				result.setCount(Math.min(result.getMaxStackSize(), stack.getCount() * result.getCount()));
				items.set(slot, result);
				progress[slot] = 0;
				changed = true;
			}
		}
		if (changed) {
			setChanged();
		}
	}

	/**
	 * O tempo nao passa dentro do barril: quando a estacao avanca, a marca de colheita de cada produto anda junto (a
	 * idade fica parada).
	 */
	private boolean keepFresh() {
		if (SeasonState.current() == null) {
			lastSeason = Long.MIN_VALUE;
			return false;
		}
		return keepFresh(SeasonState.seasonIndex());
	}

	/** Segura o frescor considerando que a estacao atual e {@code now}. Devolve se mudou alguma marca. */
	public boolean keepFresh(long now) {
		long last = lastSeason;
		lastSeason = now;
		if (last == Long.MIN_VALUE || now <= last) {
			return false;
		}
		boolean changed = false;
		for (ItemStack stack : items) {
			Long harvested = stack.get(FoodRegistry.HARVEST_SEASON);
			if (harvested != null) {
				stack.set(FoodRegistry.HARVEST_SEASON, Math.min(now, harvested + (now - last)));
				changed = true;
			}
		}
		return changed;
	}
}
