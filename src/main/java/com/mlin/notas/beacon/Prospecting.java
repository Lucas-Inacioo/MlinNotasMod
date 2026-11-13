package com.mlin.notas.beacon;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModEffects;
import com.mlin.notas.tier.OreYield;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Poder "Garimpo" do sinalizador: quem tem o efeito ganha {@link BeaconTiers#PROSPECTING_CHANCE} de chance de um
 * minerio bruto/gema extra ao quebrar um minerio (tag {@code mlinnotas:prospecting_ores} = {@code #c:ores}). Nada
 * extra com toque suave (o drop vira o proprio bloco) ou ferramenta errada. A conta fica em {@link OreYield}, que
 * aceita outras fontes de chance (ramo de Prospeccao da arvore de habilidades, no futuro).
 */
public final class Prospecting {
	public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, MlinNotas.id("prospecting_ores"));

	private Prospecting() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register(Prospecting::afterBreak);
	}

	/** Fontes de chance extra do jogador. */
	public static List<OreYield.Source> sources(Player player) {
		List<OreYield.Source> sources = new ArrayList<>();
		if (player.hasEffect(ModEffects.PROSPECTING)) {
			sources.add(new OreYield.Source("beacon_prospecting", BeaconTiers.PROSPECTING_CHANCE));
		}
		return sources;
	}

	/** Item extra (vazio se nao ganhou) ao quebrar {@code state} com {@code tool}. */
	public static ItemStack extraDrop(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity, Player player, ItemStack tool,
			DoubleSupplier random) {
		if (!state.is(ORES) || !player.hasCorrectToolForDrops(state)) {
			return ItemStack.EMPTY;
		}
		List<OreYield.Source> sources = sources(player);
		if (sources.isEmpty()) {
			return ItemStack.EMPTY;
		}
		for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, player, tool)) {
			if (!drop.isEmpty() && !(drop.getItem() instanceof BlockItem)) {
				int extra = OreYield.rollExtra(drop.getCount(), sources, random);
				return extra > 0 ? drop.copyWithCount(extra) : ItemStack.EMPTY;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || player.isCreative() || !player.hasEffect(ModEffects.PROSPECTING)) {
			return;
		}
		ItemStack extra = extraDrop(serverLevel, pos, state, blockEntity, player, player.getMainHandItem(), serverLevel.getRandom()::nextDouble);
		if (!extra.isEmpty()) {
			Block.popResource(level, pos, extra);
		}
	}
}
