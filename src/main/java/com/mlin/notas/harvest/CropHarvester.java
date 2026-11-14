package com.mlin.notas.harvest;

import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Botao direito numa planta madura colhe e replanta (consumindo uma semente dos proprios drops).
 * Com enxada colhe uma area conforme o nivel ({@link HarvestRules#radius}); a enxada perde 1 de durabilidade
 * por planta. Plantas imaturas seguem o comportamento normal (farinha de osso etc.).
 */
public final class CropHarvester {
	private CropHarvester() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(CropHarvester::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!isMatureCrop(level.getBlockState(pos))) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			harvest(serverLevel, player, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** Planta colhivel e madura. */
	public static boolean isMatureCrop(BlockState state) {
		if (!HarvestRules.isHarvestable(blockId(state))) {
			return false;
		}
		IntegerProperty age = ageProperty(state);
		return age != null && HarvestRules.isMature(state.getValue(age), maxAge(age));
	}

	/**
	 * Colhe a planta em {@code center} e, se o jogador segura uma enxada, as plantas maduras em volta (mesma altura).
	 * Devolve quantas plantas foram colhidas.
	 */
	public static int harvest(ServerLevel level, Player player, BlockPos center) {
		ItemStack tool = player.getMainHandItem();
		Tier hoe = HarvestRules.hoeTier(BuiltInRegistries.ITEM.getKey(tool.getItem()).toString());
		if (hoe != null && com.mlin.notas.gating.ToolLocks.refuse(player, tool)) {
			hoe = null; // enxada travada pela Mineracao do mundo: colhe so a planta clicada, como a mao
		}
		int radius = HarvestRules.radius(hoe);
		List<BlockPos> targets = new ArrayList<>();
		targets.add(center);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx != 0 || dz != 0) {
					targets.add(center.offset(dx, 0, dz));
				}
			}
		}
		int harvested = 0;
		for (BlockPos pos : targets) {
			BlockState state = level.getBlockState(pos);
			if (!isMatureCrop(state)) {
				continue;
			}
			harvestOne(level, player, pos, state, tool);
			harvested++;
			if (hoe != null) {
				tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
				if (tool.isEmpty()) {
					break;
				}
			}
		}
		return harvested;
	}

	private static void harvestOne(ServerLevel level, Player player, BlockPos pos, BlockState state, ItemStack tool) {
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, player, tool));
		List<String> ids = new ArrayList<>(drops.size());
		for (ItemStack drop : drops) {
			ids.add(BuiltInRegistries.ITEM.getKey(drop.getItem()).toString());
		}
		int seed = HarvestRules.seedIndex(ids, HarvestRules.seedFor(blockId(state)));
		level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
		if (seed >= 0) {
			drops.get(seed).shrink(1);
			IntegerProperty age = ageProperty(state);
			level.setBlock(pos, state.setValue(age, 0), Block.UPDATE_ALL);
		} else {
			level.removeBlock(pos, false);
		}
		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) {
				Block.popResource(level, pos, drop);
			}
		}
	}

	private static String blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	/** Propriedade "age" do estado (trigo AGE_7, beterraba AGE_3, cacau AGE_2...), ou null. */
	public static IntegerProperty ageProperty(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty integer && property.getName().equals("age")) {
				return integer;
			}
		}
		return null;
	}

	public static int maxAge(IntegerProperty age) {
		int max = 0;
		for (int value : age.getPossibleValues()) {
			max = Math.max(max, value);
		}
		return max;
	}
}
