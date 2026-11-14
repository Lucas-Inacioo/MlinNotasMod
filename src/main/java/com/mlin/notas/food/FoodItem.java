package com.mlin.notas.food;

import com.mlin.notas.season.SeasonState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Comida do mod. As pereciveis (horta fresca, saladas, queijo fresco) ganham a marca da estacao em que chegaram ao
 * inventario de alguem ({@code mlinnotas:harvest_season}); dai o frescor conta as estacoes que passaram
 * ({@link FoodRules#freshness}). Com as estacoes desligadas nada e marcado.
 */
public class FoodItem extends Item {
	private final FoodCatalog.Food food;

	public FoodItem(Properties properties, FoodCatalog.Food food) {
		super(properties);
		this.food = food;
	}

	public FoodCatalog.Food food() {
		return food;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (food.perishable() && SeasonState.current() != null && !stack.has(FoodRegistry.HARVEST_SEASON)) {
			stack.set(FoodRegistry.HARVEST_SEASON, SeasonState.seasonIndex());
		}
	}
}
