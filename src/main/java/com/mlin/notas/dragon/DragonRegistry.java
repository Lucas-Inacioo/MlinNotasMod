package com.mlin.notas.dragon;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Registro do rework do dragao: feature da arena (so na ilha central) e a luta ({@link DragonBoss}). */
public final class DragonRegistry {
	public static final ResourceKey<PlacedFeature> ARENA = ResourceKey.create(Registries.PLACED_FEATURE, MlinNotas.id("dragon_arena"));

	private DragonRegistry() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, MlinNotas.id("dragon_arena"), DragonArenaFeature.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.THE_END), GenerationStep.Decoration.SURFACE_STRUCTURES, ARENA);
		DragonBoss.init();
		RespawnExplosionGuard.init();
	}
}
