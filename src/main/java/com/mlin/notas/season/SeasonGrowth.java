package com.mlin.notas.season;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Ponte entre o random tick das culturas (mixin {@code SeasonalGrowthMixin}) e {@link CropSeasons}. So o Overworld tem
 * estacoes. O teste de estufa (ate 16 blocos acima) so roda quando a estacao atrapalha (multiplicador &lt; 1).
 */
public final class SeasonGrowth {
	/** Altura maxima procurada acima da cultura para achar o teto. */
	public static final int ROOF_SEARCH = 16;

	private static volatile Map<Block, CropSeasons.Profile> profiles;
	private static boolean bonusRunning;

	private SeasonGrowth() {
	}

	public static CropSeasons.Profile profile(Block block) {
		Map<Block, CropSeasons.Profile> map = profiles;
		if (map == null) {
			map = new IdentityHashMap<>();
			for (Block b : BuiltInRegistries.BLOCK) {
				CropSeasons.Profile p = CropSeasons.profile(BuiltInRegistries.BLOCK.getKey(b).toString());
				if (p != null) {
					map.put(b, p);
				}
			}
			profiles = map;
		}
		return map.get(block);
	}

	/** Clima sazonal no lugar; null = sem estacoes (fora do Overworld ou desligadas). */
	public static SeasonClimate climate(Level level, BlockPos pos) {
		if (SeasonState.current() == null || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		return SeasonBiomes.climate(level.getBiome(pos).value());
	}

	/** Multiplicador de crescimento agora; -1 = nao e sazonal aqui. */
	public static float multiplier(Level level, BlockPos pos, Block block) {
		SeasonDate date = SeasonState.current();
		if (date == null) {
			return -1;
		}
		CropSeasons.Profile profile = profile(block);
		if (profile == null) {
			return -1;
		}
		SeasonClimate climate = climate(level, pos);
		if (climate == null) {
			return -1;
		}
		float m = CropSeasons.seasonal(profile, date, climate);
		if (m < 1.0F && greenhouse(level, pos, block)) {
			return 1.0F;
		}
		return m;
	}

	/** Estufa: teto de vidro, ou teto qualquer com luz de bloco forte na cultura. */
	public static boolean greenhouse(Level level, BlockPos pos, Block crop) {
		boolean covered = false;
		boolean glass = false;
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 1; i <= ROOF_SEARCH; i++) {
			cursor.move(0, 1, 0);
			BlockState above = level.getBlockState(cursor);
			if (above.isAir() || above.is(crop)) {
				continue;
			}
			covered = true;
			glass = above.is(ConventionalBlockTags.GLASS_BLOCKS) || above.is(ConventionalBlockTags.GLASS_PANES);
			break;
		}
		return CropSeasons.greenhouse(covered, glass, level.getBrightness(LightLayer.BLOCK, pos));
	}

	/** HEAD do random tick: true = pula este tick (estacao ruim). */
	public static boolean skipTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (bonusRunning || SeasonState.current() == null) {
			return false;
		}
		float m = multiplier(level, pos, state.getBlock());
		return m >= 0 && !CropSeasons.allowTick(m, random.nextDouble());
	}

	/** TAIL do random tick: estacao boa pode dar um tick extra. */
	public static void afterTick(ServerLevel level, BlockPos pos, RandomSource random) {
		if (bonusRunning || SeasonState.current() == null) {
			return;
		}
		BlockState now = level.getBlockState(pos);
		float m = multiplier(level, pos, now.getBlock());
		if (m > 1.0F && CropSeasons.bonusTick(m, random.nextDouble())) {
			bonusRunning = true;
			try {
				now.randomTick(level, pos, random);
			} finally {
				bonusRunning = false;
			}
		}
	}
}
