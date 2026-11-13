package com.mlin.notas.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Metodos privados do aldeao usados pelos testes das vilas (teto de nivel e desconto). */
@Mixin(Villager.class)
public interface VillagerAccessor {
	@Invoker("shouldIncreaseLevel")
	boolean mlinnotas$shouldIncreaseLevel();

	@Invoker("updateSpecialPrices")
	void mlinnotas$updateSpecialPrices(Player player);
}
