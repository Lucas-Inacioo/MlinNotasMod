package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.end.EndBiomes;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Game test de cliente do End: elevador com pulo/agachar de verdade (teclas do jogo), criaturas com os modelos
 * proprios e biomas grandes num End gerado de verdade (com screenshot de cada bioma).
 */
@SuppressWarnings("UnstableApiUsage")
public class EndClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			elevator(context, world);
			creatures(context, world);
		}
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false).create()) {
			real.getConnection().waitForChunksRender();
			biomes(context, real);
		}
	}

	/** Dois elevadores na mesma coluna: pular no de baixo leva ao de cima, agachar volta. */
	private static void elevator(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos low = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition().offset(6, 0, 6);
			for (int y = 0; y <= 9; y++) {
				level.setBlockAndUpdate(pos.above(y), Blocks.AIR.defaultBlockState());
			}
			level.setBlockAndUpdate(pos, ModBlocks.PURPUR_ELEVATOR.defaultBlockState());
			level.setBlockAndUpdate(pos.above(5), ModBlocks.PURPUR_ELEVATOR.defaultBlockState());
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
			return pos;
		});
		waitOrExplain(context, mc -> mc.player != null && mc.player.onGround() && Math.abs(mc.player.getY() - (low.getY() + 1)) < 0.01, 100, "parado no elevador de baixo y=" + (low.getY() + 1));
		context.waitTicks(5);
		context.getInput().holdKeyFor(options -> options.keyJump, 3);
		waitOrExplain(context, mc -> Math.abs(mc.player.getY() - (low.getY() + 6)) < 0.3, 60, "subir para y=" + (low.getY() + 6));
		context.waitFor(mc -> mc.player.onGround(), 60);
		double up = context.computeOnClient(mc -> mc.player.getY());
		if (Math.abs(up - (low.getY() + 6)) > 0.01) {
			throw new IllegalStateException("pular no elevador deveria levar ao de cima (y " + (low.getY() + 6) + "), ficou em " + up);
		}
		context.getInput().lookAt(0, 60);
		context.waitTicks(5);
		context.takeScreenshot("mlinnotas-elevator-top");
		context.waitTicks(5);
		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		waitOrExplain(context, mc -> Math.abs(mc.player.getY() - (low.getY() + 1)) < 0.3, 60, "descer para y=" + (low.getY() + 1));
		context.waitFor(mc -> mc.player.onGround(), 60);
		double down = context.computeOnClient(mc -> mc.player.getY());
		if (Math.abs(down - (low.getY() + 1)) > 0.01) {
			throw new IllegalStateException("agachar no elevador deveria descer (y " + (low.getY() + 1) + "), ficou em " + down);
		}
		MlinNotas.LOGGER.info("[EndClientGameTest] elevador: subiu para y={} e desceu para y={}", up, down);
	}

	private static void waitOrExplain(ClientGameTestContext context, java.util.function.Predicate<net.minecraft.client.Minecraft> condition, int ticks, String what) {
		try {
			context.waitFor(condition, ticks);
		} catch (AssertionError e) {
			String state = context.computeOnClient(mc -> mc.player == null ? "sem jogador" : "jogador em " + mc.player.position() + " noChao=" + mc.player.onGround()
					+ " voando=" + mc.player.getAbilities().flying + " bloco abaixo=" + mc.level.getBlockState(mc.player.blockPosition().below()));
			context.takeScreenshot("mlinnotas-elevator-fail");
			throw new IllegalStateException("elevador: esperava " + what + ", " + state, e);
		}
	}

	/** As 4 criaturas paradas na frente do jogador, de perto. */
	private static void creatures(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("time set noon");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			int sx = player.getBlockX() + 20;
			int sz = player.getBlockZ();
			BlockPos stand = new BlockPos(sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz), sz);
			for (int y = 0; y <= 6; y++) {
				for (int dx = -4; dx <= 4; dx++) {
					for (int dz = -8; dz <= 2; dz++) {
						level.setBlockAndUpdate(stand.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}
			player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), 180.0F, 15.0F, true);
			BlockPos front = stand.offset(0, 0, -5);
			ModEntities.PURPUR_SENTINEL.spawn(level, front.offset(-3, 0, 0), EntitySpawnReason.COMMAND);
			ModEntities.VOID_JELLY.spawn(level, front.offset(-1, 1, 1), EntitySpawnReason.COMMAND);
			ModEntities.CRYSTAL_MITE.spawn(level, front.offset(1, 0, 2), EntitySpawnReason.COMMAND);
			ModEntities.CHORUS_HOPPER.spawn(level, front.offset(3, 0, 1), EntitySpawnReason.COMMAND);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(front).inflate(5))) {
				mob.setNoAi(true);
				mob.setYRot(0.0F);
				mob.setYHeadRot(0.0F);
				mob.yBodyRot = 0.0F;
			}
		});
		context.waitTicks(30);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("mlinnotas-end-creatures");
		world.getServer().runCommand("execute at @p run tp @p ~ ~ ~-1.5 180 20");
		context.waitTicks(10);
		context.takeScreenshot("mlinnotas-end-creatures-close");
	}

	private static final List<ResourceKey<Biome>> OURS = List.of(EndBiomes.MOSS_FIELDS, EndBiomes.CRYSTAL_GROVE, EndBiomes.CHORUS_FOREST,
			EndBiomes.VOID_WASTES);

	/** Familia do bioma para medir trechos: null = beirada/vazio (nao conta). */
	private static String family(Holder<Biome> biome) {
		if (biome.is(EndBiomes.MOSS_FIELDS)) {
			return "moss";
		} else if (biome.is(EndBiomes.CRYSTAL_GROVE)) {
			return "crystal";
		} else if (biome.is(EndBiomes.CHORUS_FOREST)) {
			return "chorus";
		} else if (biome.is(Biomes.END_HIGHLANDS) || biome.is(Biomes.END_MIDLANDS)) {
			return "vanilla";
		}
		return null;
	}

	private static void biomes(ClientGameTestContext context, TestSingleplayerContext real) {
		// 1) amostra numa linha do End externo: trechos longos do mesmo bioma
		Map<ResourceKey<Biome>, BlockPos> candidates = real.getServer().computeOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			BiomeResolver resolver = end.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(end.getChunkSource().randomState());
			int runs = 0;
			int counted = 0;
			String last = null;
			Map<String, Integer> seen = new LinkedHashMap<>();
			for (int x = 1200; x < 13200; x += 16) {
				String family = family(resolver.getNoiseBiome(x >> 2, 16, 2400 >> 2));
				if (family == null) {
					continue;
				}
				counted++;
				seen.merge(family, 1, Integer::sum);
				if (!family.equals(last)) {
					runs++;
					last = family;
				}
			}
			double meanRun = counted * 16.0 / Math.max(1, runs);
			MlinNotas.LOGGER.info("[EndClientGameTest] linha z=2400: {} amostras em ilha, {} trechos, media {} blocos, familias {}", counted, runs,
					meanRun, seen);
			// a semente do mundo e aleatoria e o vazio entre ilhas corta os trechos: medias reais variam de ~130 a ~220.
			// O bug antigo dava 16-32 blocos; o tamanho das regioes em si e conferido no EndBiomeLayoutTest.
			if (counted < 50 || meanRun < 96.0) {
				throw new IllegalStateException("biomas do End externo deveriam vir em trechos longos: media " + meanRun + " blocos em " + runs
						+ " trechos");
			}
			if (seen.size() < 3) {
				throw new IllegalStateException("poucas familias de bioma na linha: " + seen);
			}
			// 2) ponto bem no meio de cada bioma novo (mais vizinhos do mesmo bioma num raio de 48)
			Map<ResourceKey<Biome>, BlockPos> best = new LinkedHashMap<>();
			Map<ResourceKey<Biome>, Integer> score = new LinkedHashMap<>();
			for (int x = 1600; x <= 8000; x += 96) {
				for (int z = -4000; z <= 4000; z += 96) {
					Holder<Biome> biome = resolver.getNoiseBiome(x >> 2, 16, z >> 2);
					for (ResourceKey<Biome> key : OURS) {
						if (!biome.is(key)) {
							continue;
						}
						int same = 0;
						for (int dx = -48; dx <= 48; dx += 16) {
							for (int dz = -48; dz <= 48; dz += 16) {
								if (resolver.getNoiseBiome((x + dx) >> 2, 16, (z + dz) >> 2).is(key)) {
									same++;
								}
							}
						}
						if (same > score.getOrDefault(key, -1)) {
							score.put(key, same);
							best.put(key, new BlockPos(x, 64, z));
						}
					}
				}
			}
			MlinNotas.LOGGER.info("[EndClientGameTest] centros dos biomas: {} (vizinhos {})", best, score);
			return best;
		});
		for (ResourceKey<Biome> key : OURS) {
			if (!candidates.containsKey(key)) {
				throw new IllegalStateException("bioma " + key.identifier() + " nao apareceu no End externo");
			}
		}

		real.getServer().runCommand("gamemode creative @p");
		for (ResourceKey<Biome> key : OURS) {
			BlockPos pos = candidates.get(key);
			real.getServer().runCommand("execute in minecraft:the_end run tp @p " + pos.getX() + " 120 " + pos.getZ() + " 0 40");
			real.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			});
			context.waitTicks(60);
			real.getConnection().waitForChunksRender();
			// desce ate ~14 blocos acima do chao e olha para frente e para baixo
			int ground = real.getServer().computeOnServer(server -> {
				ServerLevel end = server.getLevel(Level.END);
				int top = 0;
				for (int dx = -16; dx <= 16; dx += 4) {
					for (int dz = 0; dz <= 32; dz += 4) {
						top = Math.max(top, end.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() + dx, pos.getZ() + dz));
					}
				}
				return top;
			});
			int eye = Math.max(ground, 40) + 14;
			real.getServer().runCommand("execute in minecraft:the_end run tp @p " + pos.getX() + " " + eye + " " + (pos.getZ() - 6) + " 0 35");
			context.waitTicks(40);
			real.getConnection().waitForChunksRender();
			context.waitTicks(20);
			String name = key.identifier().getPath();
			context.takeScreenshot("mlinnotas-biome-" + name);
			Map<String, Integer> found = real.getServer().computeOnServer(server -> countDecor(server.getLevel(Level.END), pos));
			MlinNotas.LOGGER.info("[EndClientGameTest] {} em {}: chao y={}, decoracao {}", name, pos, ground, found);
		}
	}

	/** Conta os blocos de decoracao dos biomas num raio de 40 blocos (so para o log). */
	private static Map<String, Integer> countDecor(ServerLevel end, BlockPos center) {
		Map<String, Block> blocks = new LinkedHashMap<>();
		blocks.put("void_log", ModBlocks.VOID_LOG);
		blocks.put("void_leaves", ModBlocks.VOID_LEAVES);
		blocks.put("void_lily", ModBlocks.VOID_LILY);
		blocks.put("water", Blocks.WATER);
		blocks.put("ether_crystal", ModBlocks.ETHER_CRYSTAL_BLOCK);
		blocks.put("calcite", Blocks.CALCITE);
		blocks.put("void_basalt", ModBlocks.VOID_BASALT);
		blocks.put("bone", Blocks.BONE_BLOCK);
		blocks.put("chorus_plant", Blocks.CHORUS_PLANT);
		blocks.put("end_mushroom", ModBlocks.END_MUSHROOM);
		Map<String, Integer> counts = new LinkedHashMap<>();
		List<BlockPos> loaded = new ArrayList<>();
		for (int dx = -40; dx <= 40; dx++) {
			for (int dz = -40; dz <= 40; dz++) {
				BlockPos column = center.offset(dx, 0, dz);
				if (end.hasChunkAt(column)) {
					loaded.add(column);
				}
			}
		}
		for (BlockPos column : loaded) {
			for (int y = 20; y < 110; y++) {
				var state = end.getBlockState(column.atY(y));
				for (Map.Entry<String, Block> entry : blocks.entrySet()) {
					if (state.is(entry.getValue())) {
						counts.merge(entry.getKey(), 1, Integer::sum);
					}
				}
			}
		}
		return counts;
	}
}
