package com.mlin.notas.vstructures;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariants;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Detalhes das entidades das estruturas do mar e pequenas ({@link VPlan.Spawn#tag()}): {@code elder} nasce com a
 * vida cheia, {@code black_cat} e o gato preto da cabana da bruxa e {@code cleric} e o clerigo preso no laboratorio do
 * iglu (com 1 de XP para nao perder a profissao sem a bancada).
 */
final class SeaSpawns {
	private SeaSpawns() {
	}

	static void apply(ServerLevelAccessor level, Mob mob, String tag) {
		switch (tag) {
			case "elder" -> mob.heal(mob.getMaxHealth());
			case "black_cat" -> {
				if (mob instanceof Cat cat) {
					cat.setComponent(DataComponents.CAT_VARIANT, level.registryAccess().lookupOrThrow(Registries.CAT_VARIANT).getOrThrow(CatVariants.ALL_BLACK));
				}
			}
			case "cleric" -> {
				if (mob instanceof Villager villager) {
					villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.CLERIC));
					villager.setVillagerXp(1);
				}
			}
			default -> {
			}
		}
	}
}
