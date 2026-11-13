package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.ambient.AmbientRules;
import com.mlin.notas.client.ambient.AmbientClient;
import com.mlin.notas.client.season.SeasonClient;
import com.mlin.notas.season.Season;
import com.mlin.notas.season.SeasonDate;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/**
 * Game test de cliente das estacoes e do ambiente num mundo gerado de verdade: a mesma paisagem (planicie olhando
 * para uma floresta) nas 4 estacoes, com a neve do inverno acumulada pelo tick de precipitacao de verdade (chamado em
 * lote numa area em volta da camera, para nao esperar dias de chuva), e vaga-lumes num pantano numa noite de verao.
 */
@SuppressWarnings("UnstableApiUsage")
public class SeasonsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-estacoes")).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			landscape(context, world);
			fireflies(context, world);
			world.getServer().runCommand("season reset");
		}
	}

	private static void landscape(ClientGameTestContext context, TestSingleplayerContext world) {
		double[] camera = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos forest = find(level, Biomes.FOREST, BlockPos.ZERO, 3200);
			BlockPos plains = forest == null ? null : find(level, Biomes.PLAINS, forest, 400);
			if (forest == null) {
				throw new IllegalStateException("nenhuma floresta perto da origem");
			}
			BlockPos eye = plains != null ? plains : forest.offset(-24, 0, -24);
			// camera a 30 blocos da borda da floresta, do lado da planicie
			double dx = forest.getX() - eye.getX();
			double dz = forest.getZ() - eye.getZ();
			double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
			double cx = forest.getX() - dx / len * 30;
			double cz = forest.getZ() - dz / len * 30;
			int ground = level.getChunk(BlockPos.containing(cx, 0, cz)).getHeight(Heightmap.Types.MOTION_BLOCKING, (int) cx & 15, (int) cz & 15);
			int forestGround = level.getChunk(forest).getHeight(Heightmap.Types.MOTION_BLOCKING, forest.getX() & 15, forest.getZ() & 15);
			MlinNotas.LOGGER.info("[SeasonsClientGameTest] floresta {} planicie {} camera {},{} chao {}", forest, plains, cx, cz, ground);
			return new double[] {cx, ground + 9, cz, forest.getX() + 0.5, forestGround - 2, forest.getZ() + 0.5};
		});
		look(world, camera);
		context.waitTicks(60);
		world.getConnection().waitForChunksRender();

		for (Season season : Season.values()) {
			if (season == Season.WINTER) {
				// um inverno de chuva: o tick de precipitacao vanilla (com a temperatura sazonal) em toda coluna perto
				setSeason(context, world, season);
				int columns = world.getServer().computeOnServer(server -> snowAround(server.overworld(), camera, 64));
				MlinNotas.LOGGER.info("[SeasonsClientGameTest] neve: {} colunas com neve", columns);
				if (columns < 200) {
					throw new IllegalStateException("o inverno deveria cobrir a planicie de neve, so " + columns + " colunas");
				}
			} else {
				setSeason(context, world, season);
			}
			context.waitTicks(40);
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("mlinnotas-season-" + season.id());
		}

		// primavera de novo: o degelo pelo mesmo tick de precipitacao
		setSeason(context, world, Season.SPRING);
		int left = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int i = 0; i < 2; i++) {
				snowAround(level, camera, 64);
			}
			return countSnow(level, camera, 64);
		});
		MlinNotas.LOGGER.info("[SeasonsClientGameTest] neve depois do degelo: {} colunas", left);
		if (left > 20) {
			throw new IllegalStateException("a primavera deveria derreter a neve, sobraram " + left + " colunas");
		}
	}

	private static void fireflies(ClientGameTestContext context, TestSingleplayerContext world) {
		double[] camera = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos swamp = find(level, Biomes.SWAMP, BlockPos.ZERO, 6400);
			if (swamp == null) {
				throw new IllegalStateException("nenhum pantano perto da origem");
			}
			// camera um pouco acima do chao, virada para o lado mais baixo (agua/lama aberta) do pantano
			int ground = surface(level, swamp.getX(), swamp.getZ());
			double bestYaw = 0;
			double bestHeight = Double.MAX_VALUE;
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4;
				double sum = 0;
				for (int d = 4; d <= 20; d += 4) {
					sum += surface(level, swamp.getX() + (int) Math.round(Math.cos(a) * d), swamp.getZ() + (int) Math.round(Math.sin(a) * d));
				}
				if (sum < bestHeight) {
					bestHeight = sum;
					bestYaw = a;
				}
			}
			int lookX = swamp.getX() + (int) Math.round(Math.cos(bestYaw) * 12);
			int lookZ = swamp.getZ() + (int) Math.round(Math.sin(bestYaw) * 12);
			MlinNotas.LOGGER.info("[SeasonsClientGameTest] pantano {} chao {} olhando para {},{}", swamp, ground, lookX, lookZ);
			return new double[] {swamp.getX() + 0.5, ground + 1.0, swamp.getZ() + 0.5, lookX + 0.5, surface(level, lookX, lookZ) + 1.0,
					lookZ + 0.5};
		});
		setSeason(context, world, Season.SUMMER);
		world.getServer().runCommand("time set 18000");
		look(world, camera);
		context.waitTicks(60);
		world.getConnection().waitForChunksRender();
		context.waitTicks(260);
		AmbientRules.Env env = context.computeOnClient(mc -> AmbientClient.env());
		if (env == null || !env.swamp() || !env.night()) {
			throw new IllegalStateException("o ambiente deveria ver pantano de noite: " + env);
		}
		if (AmbientRules.particleRate(AmbientRules.Particle.FIREFLIES, env) <= 0) {
			throw new IllegalStateException("vaga-lumes deveriam aparecer: " + env);
		}
		java.util.Set<AmbientRules.Loop> loops = context.computeOnClient(mc -> AmbientClient.activeLoops());
		MlinNotas.LOGGER.info("[SeasonsClientGameTest] pantano a noite: loops {}", loops);
		if (!loops.contains(AmbientRules.Loop.CRICKETS)) {
			throw new IllegalStateException("grilos deveriam tocar numa noite de verao no pantano: " + loops);
		}
		context.takeScreenshot("mlinnotas-season-fireflies");
		// neve natural das montanhas em volta pode existir; no proprio pantano (quente o ano todo fora do inverno) nao
		int swampSnow = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int snow = 0;
			for (int x = (int) camera[0] - 32; x <= camera[0] + 32; x++) {
				for (int z = (int) camera[2] - 32; z <= camera[2] + 32; z++) {
					BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
					if (level.getBiome(top).is(Biomes.SWAMP)
							&& (level.getBlockState(top).is(Blocks.SNOW) || level.getBlockState(top.below()).is(Blocks.SNOW))) {
						snow++;
					}
				}
			}
			return snow;
		});
		String particles = context.computeOnClient(mc -> mc.particleEngine.countParticles());
		MlinNotas.LOGGER.info("[SeasonsClientGameTest] pantano: {} colunas de pantano com neve no verao, {} particulas", swampSnow, particles);
		if (swampSnow > 0) {
			throw new IllegalStateException("nao deveria haver neve no pantano no verao: " + swampSnow);
		}
		world.getServer().runCommand("time set 6000");
	}

	private static void setSeason(ClientGameTestContext context, TestSingleplayerContext world, Season season) {
		world.getServer().runCommand("season set " + season.id() + " mid");
		context.waitFor(mc -> {
			SeasonDate date = SeasonClient.date();
			return date != null && date.season() == season && !SeasonClient.retinting();
		}, 200);
	}

	private static void look(TestSingleplayerContext world, double[] c) {
		double dx = c[3] - c[0];
		double dz = c[5] - c[2];
		double dy = c[4] - (c[1] + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", c[0], c[1], c[2], yaw, pitch));
	}

	private static int surface(ServerLevel level, int x, int z) {
		return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
	}

	private static BlockPos find(ServerLevel level, ResourceKey<Biome> biome, BlockPos origin, int radius) {
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(biome), origin.atY(64), radius, 16, 64);
		return found == null ? null : found.getFirst();
	}

	/** Chama o tick de precipitacao vanilla com chuva em toda coluna do quadrado; devolve quantas ficaram com neve. */
	private static int snowAround(ServerLevel level, double[] camera, int radius) {
		float rain = level.getRainLevel(1.0F);
		level.setRainLevel(1.0F);
		int cx = (int) Math.floor((camera[0] + camera[3]) / 2);
		int cz = (int) Math.floor((camera[2] + camera[5]) / 2);
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				if (level.hasChunk(x >> 4, z >> 4)) {
					level.tickPrecipitation(new BlockPos(x, 0, z));
				}
			}
		}
		level.setRainLevel(rain);
		return countSnow(level, camera, radius);
	}

	private static int countSnow(ServerLevel level, double[] camera, int radius) {
		int cx = (int) Math.floor((camera[0] + camera[3]) / 2);
		int cz = (int) Math.floor((camera[2] + camera[5]) / 2);
		int snow = 0;
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
				if (level.getBlockState(top).is(Blocks.SNOW) || level.getBlockState(top.below()).is(Blocks.SNOW)) {
					snow++;
				}
			}
		}
		return snow;
	}
}
