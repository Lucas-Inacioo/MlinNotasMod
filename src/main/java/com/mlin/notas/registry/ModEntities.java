package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.end.CrystalMite;
import com.mlin.notas.end.PurpurSentinel;
import com.mlin.notas.end.VoidJelly;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

/** Criaturas do mod (End). Renderers em MlinNotasClient. */
public final class ModEntities {
	public static final EntityType<PurpurSentinel> PURPUR_SENTINEL = register("purpur_sentinel",
			EntityType.Builder.of(PurpurSentinel::new, MobCategory.MONSTER).sized(1.05F, 2.0F).clientTrackingRange(10));
	public static final EntityType<VoidJelly> VOID_JELLY = register("void_jelly",
			EntityType.Builder.of(VoidJelly::new, MobCategory.CREATURE).sized(0.8F, 0.8F).eyeHeight(0.4F).clientTrackingRange(8));
	public static final EntityType<CrystalMite> CRYSTAL_MITE = register("crystal_mite",
			EntityType.Builder.of(CrystalMite::new, MobCategory.MONSTER).sized(0.5F, 0.35F).eyeHeight(0.15F).clientTrackingRange(8));
	public static final EntityType<com.mlin.notas.end.ChorusHopper> CHORUS_HOPPER = register("chorus_hopper",
			EntityType.Builder.of(com.mlin.notas.end.ChorusHopper::new, MobCategory.CREATURE).sized(0.6F, 0.7F).eyeHeight(0.45F).clientTrackingRange(8));

	public static final EntityType<com.mlin.notas.village.civic.VillageGuard> VILLAGE_GUARD = register("village_guard",
			EntityType.Builder.of(com.mlin.notas.village.civic.VillageGuard::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10));

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MlinNotas.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(PURPUR_SENTINEL, PurpurSentinel.createAttributes());
		FabricDefaultAttributeRegistry.register(VOID_JELLY, VoidJelly.createAttributes());
		FabricDefaultAttributeRegistry.register(CRYSTAL_MITE, CrystalMite.createAttributes());
		FabricDefaultAttributeRegistry.register(CHORUS_HOPPER, com.mlin.notas.end.ChorusHopper.createAttributes());
		FabricDefaultAttributeRegistry.register(VILLAGE_GUARD, com.mlin.notas.village.civic.VillageGuard.createAttributes());
		SpawnPlacements.register(CHORUS_HOPPER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				com.mlin.notas.end.ChorusHopper::checkSpawnRules);
		SpawnPlacements.register(VOID_JELLY, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, VoidJelly::checkSpawnRules);
		SpawnPlacements.register(CRYSTAL_MITE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				Monster::checkAnyLightMonsterSpawnRules);
	}
}
