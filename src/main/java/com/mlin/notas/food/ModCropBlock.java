package com.mlin.notas.food;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

import java.util.function.Supplier;

/**
 * Cultura da cozinha (alface, tomate, milho...): um {@link CropBlock} vanilla de 8 estagios com a propria semente.
 * Cresce por estacao pelo mixin das culturas ({@code SeasonalGrowthMixin}) com o perfil de {@link FoodCatalog}.
 */
public class ModCropBlock extends CropBlock {
	private final Supplier<ItemLike> seed;

	public ModCropBlock(Properties properties, Supplier<ItemLike> seed) {
		super(properties);
		this.seed = seed;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return seed.get();
	}
}
