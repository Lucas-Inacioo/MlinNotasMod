package com.mlin.notas.backpack;

import com.mlin.notas.registry.ModDataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

import java.util.Optional;

/**
 * Upgrade de fornalha da mochila: a cada tick no inventario, funde o primeiro item fundivel usando o
 * primeiro combustivel encontrado no conteudo. Progresso e combustivel ficam em {@link SmeltingProgress}.
 */
public final class BackpackSmelter {
	/** Quando a mochila esta ociosa (sem fogo) so procura trabalho a cada N ticks. */
	private static final int IDLE_SCAN_INTERVAL = 10;

	private BackpackSmelter() {
	}

	/** Tick normal: quando ociosa, so procura trabalho a cada {@link #IDLE_SCAN_INTERVAL} ticks. */
	public static void tick(ItemStack backpack, ServerLevel level) {
		tick(backpack, level, level.getGameTime() % IDLE_SCAN_INTERVAL == 0);
	}

	/**
	 * @param scanNow se false e a mochila estiver ociosa (sem fogo e sem progresso), nao faz nada neste tick
	 */
	public static void tick(ItemStack backpack, ServerLevel level, boolean scanNow) {
		BackpackUpgrades upgrades = backpack.getOrDefault(ModDataComponents.BACKPACK_UPGRADES, BackpackUpgrades.DEFAULT);
		if (!upgrades.furnace()) {
			return;
		}
		SmeltingProgress progress = upgrades.smelting();
		if (!progress.isLit() && progress.cookProgress() == 0 && !scanNow) {
			return;
		}
		NonNullList<ItemStack> items = NonNullList.withSize(upgrades.slotCount(), ItemStack.EMPTY);
		backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);

		int inputSlot = -1;
		RecipeHolder<? extends AbstractCookingRecipe> recipe = null;
		for (int i = 0; i < items.size() && inputSlot < 0; i++) {
			ItemStack candidate = items.get(i);
			if (candidate.isEmpty()) {
				continue;
			}
			Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> found =
					level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(candidate), level);
			// a mochila nao recicla equipamento guardado nela (salvage/): so funde o resto
			if (found.isPresent() && !(found.get().value() instanceof com.mlin.notas.salvage.SalvageRecipe)) {
				inputSlot = i;
				recipe = found.get();
			}
		}
		boolean hasInput = inputSlot >= 0;
		ItemStack result = hasInput ? recipe.value().assemble(new SingleRecipeInput(items.get(inputSlot))) : ItemStack.EMPTY;
		int outputSlot = hasInput ? findOutputSlot(items, result, inputSlot) : -1;
		boolean canOutput = hasInput && !result.isEmpty() && outputSlot >= 0;
		int cookTime = hasInput ? recipe.value().cookingTime() : SmeltingProgress.DEFAULT_COOK_TIME;

		int fuelSlot = -1;
		int burnTime = 0;
		if (!progress.isLit() && hasInput && canOutput) {
			for (int i = 0; i < items.size(); i++) {
				if (i == inputSlot || items.get(i).isEmpty()) {
					continue;
				}
				int time = burnTime(level, items.get(i));
				if (time > 0) {
					fuelSlot = i;
					burnTime = time;
					break;
				}
			}
		}

		SmeltingProgress.Step step = progress.tick(hasInput, canOutput, burnTime, cookTime);
		boolean itemsChanged = false;
		if (step.consumeFuel() && fuelSlot >= 0) {
			ItemStack fuel = items.get(fuelSlot);
			ItemStackTemplate remainder = fuel.getItem().getCraftingRemainder();
			fuel.shrink(1);
			if (remainder != null) {
				ItemStack rest = remainder.create();
				if (fuel.isEmpty()) {
					items.set(fuelSlot, rest);
				} else {
					insert(items, rest);
				}
			}
			itemsChanged = true;
		}
		if (step.finished()) {
			items.get(inputSlot).shrink(1);
			ItemStack target = items.get(outputSlot);
			if (target.isEmpty()) {
				items.set(outputSlot, result.copy());
			} else {
				target.grow(result.getCount());
			}
			itemsChanged = true;
		}
		if (itemsChanged) {
			backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		}
		if (!step.next().equals(progress)) {
			backpack.set(ModDataComponents.BACKPACK_UPGRADES, upgrades.withSmelting(step.next()));
		}
	}

	/** Slot onde o resultado cabe: pilha igual com espaco, senao o primeiro vazio (nunca o slot de entrada). */
	static int findOutputSlot(NonNullList<ItemStack> items, ItemStack result, int inputSlot) {
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (i != inputSlot && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, result)
					&& stack.getCount() + result.getCount() <= stack.getMaxStackSize()) {
				return i;
			}
		}
		for (int i = 0; i < items.size(); i++) {
			if (i != inputSlot && items.get(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	private static void insert(NonNullList<ItemStack> items, ItemStack stack) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack);
				return;
			}
		}
	}

	/** Duracao do combustivel em ticks (0 se nao for combustivel). */
	public static int burnTime(ServerLevel level, ItemStack stack) {
		if (!stack.has(DataComponents.COOKING_FUEL)) {
			return 0;
		}
		LootContext context = new LootContext.Builder(new LootParams.Builder(level).create(LootContextParamSets.EMPTY)).create(Optional.empty());
		return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
	}
}
