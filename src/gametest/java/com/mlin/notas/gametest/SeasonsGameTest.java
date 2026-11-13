package com.mlin.notas.gametest;

import com.mlin.notas.season.Season;
import com.mlin.notas.season.SeasonBiomes;
import com.mlin.notas.season.SeasonClimate;
import com.mlin.notas.season.SeasonDate;
import com.mlin.notas.season.SeasonServer;
import com.mlin.notas.season.SeasonState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Estacoes no servidor: trigo quase nao cresce no inverno ao ar livre, cresce normal na estufa (teto de vidro) e rapido
 * na primavera; no inverno a chuva vira neve e a agua congela num bioma temperado, e na primavera derretem. Os
 * ambientes {@code seasons_winter}/{@code seasons_spring} poem a estacao por comando (funcao de setup) e voltam o
 * calendario no teardown; o random tick e chamado direto com semente fixa (deterministico).
 */
public class SeasonsGameTest {
	private static final String WINTER = "mlinnotas-gametest:seasons_winter";
	private static final String SPRING = "mlinnotas-gametest:seasons_spring";
	private static final int TICKS = 90;

	@GameTest(environment = WINTER, skyAccess = true)
	public void winterWheatOutdoorsBarelyGrows(GameTestHelper helper) {
		requireSeason(Season.WINTER);
		fillBiome(helper, "minecraft:plains");
		BlockPos crop = farm(helper, 2, 2);
		requireTemperate(helper, crop);
		int age = grow(helper, crop, 11L);
		if (age > 3) {
			throw new IllegalStateException("trigo no inverno ao ar livre deveria quase parar, chegou na idade " + age);
		}
		helper.succeed();
	}

	@GameTest(environment = WINTER, skyAccess = true)
	public void greenhouseIgnoresWinter(GameTestHelper helper) {
		requireSeason(Season.WINTER);
		fillBiome(helper, "minecraft:plains");
		BlockPos crop = farm(helper, 2, 2);
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(x, 5, z, Blocks.GLASS);
			}
		}
		int age = grow(helper, crop, 11L);
		if (age < 7) {
			throw new IllegalStateException("trigo na estufa deveria crescer normal no inverno, ficou na idade " + age);
		}
		helper.succeed();
	}

	@GameTest(environment = SPRING, skyAccess = true)
	public void springWheatGrows(GameTestHelper helper) {
		requireSeason(Season.SPRING);
		fillBiome(helper, "minecraft:plains");
		BlockPos crop = farm(helper, 2, 2);
		int age = grow(helper, crop, 11L);
		if (age < 7) {
			throw new IllegalStateException("trigo na primavera deveria amadurecer, ficou na idade " + age);
		}
		helper.succeed();
	}

	@GameTest(environment = WINTER, skyAccess = true)
	public void winterSnowsAndFreezesThenSpringMelts(GameTestHelper helper) {
		requireSeason(Season.WINTER);
		fillBiome(helper, "minecraft:plains");
		ServerLevel level = helper.getLevel();
		helper.setBlock(2, 1, 2, Blocks.STONE);
		helper.setBlock(5, 0, 5, Blocks.STONE);
		helper.setBlock(5, 1, 5, Blocks.WATER);
		BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos water = helper.absolutePos(new BlockPos(5, 1, 5));
		requireTemperate(helper, ground);
		float rainBefore = level.getRainLevel(1.0F);
		try {
			level.setRainLevel(1.0F);
			level.tickPrecipitation(ground);
			level.tickPrecipitation(water);
			if (!level.getBlockState(ground.above()).is(Blocks.SNOW)) {
				BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, ground);
				var biome = level.getBiome(top);
				throw new IllegalStateException("nao nevou: topo " + top + " (chao " + ground + "), bioma " + biome.unwrapKey().orElse(null)
						+ ", frio " + biome.value().coldEnoughToSnow(top, level.getSeaLevel()) + ", deveria nevar "
						+ biome.value().shouldSnow(level, top) + ", chuva " + level.isRaining() + ", estado " + SeasonState.current()
						+ ", acumulo " + level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MAX_SNOW_ACCUMULATION_HEIGHT));
			}
			helper.assertBlockPresent(Blocks.ICE, 5, 1, 5);

			// primavera: a mesma chuva derrete em vez de nevar (sincrono: os outros testes do lote nao veem a troca)
			SeasonServer.setSeason(level.getServer(), Season.SPRING, Season.Phase.MID);
			for (int i = 0; i < 3; i++) {
				level.tickPrecipitation(ground);
				level.tickPrecipitation(water);
			}
			helper.assertBlockNotPresent(Blocks.SNOW, 2, 2, 2);
			helper.assertBlockPresent(Blocks.WATER, 5, 1, 5);
		} finally {
			SeasonServer.setSeason(level.getServer(), Season.WINTER, Season.Phase.MID);
			level.setRainLevel(rainBefore);
		}
		helper.succeed();
	}

	/** Os testes caem em qualquer bioma do mundo de teste; planicies e temperado. */
	private static void fillBiome(GameTestHelper helper, String biome) {
		BlockPos a = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos b = helper.absolutePos(new BlockPos(7, 7, 7));
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"fillbiome " + Math.min(a.getX(), b.getX()) + " " + a.getY() + " " + Math.min(a.getZ(), b.getZ()) + " "
						+ Math.max(a.getX(), b.getX()) + " " + b.getY() + " " + Math.max(a.getZ(), b.getZ()) + " " + biome);
	}

	private static void requireSeason(Season season) {
		SeasonDate date = SeasonState.current();
		if (date == null || date.season() != season) {
			throw new IllegalStateException("o ambiente deveria ter posto " + season + ", esta em " + date);
		}
	}

	private static void requireTemperate(GameTestHelper helper, BlockPos pos) {
		var biome = helper.getLevel().getBiome(pos);
		if (SeasonBiomes.climate(biome.value()) != SeasonClimate.TEMPERATE) {
			throw new IllegalStateException("o bioma do teste deveria ser temperado: " + biome.unwrapKey().map(Object::toString).orElse("?"));
		}
	}

	/** Canteiro 3x3 de terra arada molhada com trigo novo no meio; devolve a posicao absoluta do trigo. */
	private static BlockPos farm(GameTestHelper helper, int x, int z) {
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(x + dx, 1, z + dz, farmland);
			}
		}
		helper.setBlock(x, 2, z, Blocks.WHEAT.defaultBlockState());
		return helper.absolutePos(new BlockPos(x, 2, z));
	}

	/** {@value TICKS} random ticks com semente fixa; devolve a idade final. */
	private static int grow(GameTestHelper helper, BlockPos crop, long seed) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(seed);
		for (int i = 0; i < TICKS; i++) {
			level.getBlockState(crop).randomTick(level, crop, random);
		}
		BlockState state = level.getBlockState(crop);
		if (!state.is(Blocks.WHEAT)) {
			throw new IllegalStateException("o trigo sumiu: " + state);
		}
		return state.getValue(CropBlock.AGE);
	}
}
