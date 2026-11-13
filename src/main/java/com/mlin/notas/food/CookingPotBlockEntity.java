package com.mlin.notas.food;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Panela: 6 espacos de ingrediente, 1 de recipiente e 1 de saida. Cozinha sozinha enquanto houver fonte de calor
 * embaixo (fogueira acesa, fogo, magma, lava ou fornalha/defumador acesos; tag {@code mlinnotas:heat_sources}) e uma
 * receita {@link CookingPotRecipe} casar. Funil: em cima entra ingrediente, dos lados recipiente, embaixo sai o prato.
 */
public class CookingPotBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	public static final int INGREDIENTS = CookingPotRecipe.MAX_INGREDIENTS;
	public static final int CONTAINER_SLOT = 6;
	public static final int OUTPUT_SLOT = 7;
	public static final int SIZE = 8;
	private static final int[] TOP_SLOTS = {0, 1, 2, 3, 4, 5};
	private static final int[] SIDE_SLOTS = {CONTAINER_SLOT};
	private static final int[] BOTTOM_SLOTS = {OUTPUT_SLOT};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;
	private int total;
	private boolean heated;
	private boolean contentsChanged = true;
	private RecipeHolder<CookingPotRecipe> recipe;

	/** Progresso, tempo total e calor para a tela. */
	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> total;
				case 2 -> heated ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> progress = value;
				case 1 -> total = value;
				case 2 -> heated = value != 0;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return 3;
		}
	};

	public CookingPotBlockEntity(BlockPos pos, BlockState state) {
		super(FoodRegistry.COOKING_POT_ENTITY, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.mlinnotas.cooking_pot");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
		contentsChanged = true;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new CookingPotMenu(containerId, inventory, this, data);
	}

	@Override
	public void setChanged() {
		contentsChanged = true;
		super.setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		progress = input.getIntOr("progress", 0);
		total = input.getIntOr("total", 0);
		contentsChanged = true;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
		output.putInt("progress", progress);
		output.putInt("total", total);
	}

	public boolean heated() {
		return heated;
	}

	public int progress() {
		return progress;
	}

	/** A fonte de calor embaixo esta ativa (blocos com LIT precisam estar acesos). */
	public static boolean isHeated(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (!below.is(FoodRegistry.HEAT_SOURCES)) {
			return false;
		}
		return !below.hasProperty(BlockStateProperties.LIT) || below.getValue(BlockStateProperties.LIT);
	}

	List<ItemStack> ingredientStacks() {
		return new ArrayList<>(items.subList(0, INGREDIENTS));
	}

	private Optional<RecipeHolder<CookingPotRecipe>> findRecipe(ServerLevel level) {
		if (contentsChanged) {
			contentsChanged = false;
			CookingPotRecipe.Input input = new CookingPotRecipe.Input(ingredientStacks(), items.get(CONTAINER_SLOT));
			recipe = level.recipeAccess().getRecipeFor(FoodRegistry.COOKING_TYPE, input, level, recipe).orElse(null);
		}
		return Optional.ofNullable(recipe);
	}

	private boolean canOutput(ItemStack result) {
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			return true;
		}
		return ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		pot.heated = isHeated(level, pos);
		boolean cooking = false;
		if (pot.heated) {
			RecipeHolder<CookingPotRecipe> holder = pot.findRecipe(serverLevel).orElse(null);
			if (holder != null) {
				ItemStack result = holder.value().result().create();
				if (pot.canOutput(result)) {
					cooking = true;
					pot.total = holder.value().cookingTime();
					pot.progress++;
					if (pot.progress >= pot.total) {
						pot.progress = 0;
						pot.cook(serverLevel, pos, holder.value(), result);
					}
				}
			}
		}
		if (!cooking && pot.progress > 0) {
			pot.progress = 0;
		}
		if (state.getValue(CookingPotBlock.LIT) != cooking) {
			level.setBlock(pos, state.setValue(CookingPotBlock.LIT, cooking), Block.UPDATE_ALL);
		}
	}

	/** Consome um de cada ingrediente (devolvendo baldes/garrafas) e o recipiente, e poe o prato na saida. */
	private void cook(ServerLevel level, BlockPos pos, CookingPotRecipe recipe, ItemStack result) {
		for (int slot = 0; slot < INGREDIENTS; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
			stack.shrink(1);
			if (remainder != null) {
				ItemStack back = remainder.create();
				if (stack.isEmpty()) {
					items.set(slot, back);
				} else {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, back);
				}
			}
		}
		if (recipe.container().isPresent()) {
			items.get(CONTAINER_SLOT).shrink(1);
		}
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, result.copy());
		} else {
			out.grow(result.getCount());
		}
		int xp = (int) Math.floor(recipe.experience());
		if (level.getRandom().nextFloat() < recipe.experience() - xp) {
			xp++;
		}
		if (xp > 0) {
			ExperienceOrb.award(level, Vec3.atCenterOf(pos).add(0, 0.6, 0), xp);
		}
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot != OUTPUT_SLOT;
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return switch (direction) {
			case UP -> TOP_SLOTS;
			case DOWN -> BOTTOM_SLOTS;
			default -> SIDE_SLOTS;
		};
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return slot != OUTPUT_SLOT;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return slot == OUTPUT_SLOT && direction == Direction.DOWN;
	}
}
