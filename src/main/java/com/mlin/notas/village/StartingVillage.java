package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Vila inicial: num mundo novo (gamerule {@code mlinnotas:starting_village}, ligada por padrao; so em mundos com
 * terreno normal), o spawn vai para um Acampamento perto do spawn vanilla, num lugar plano e seco de bioma aberto: o
 * quadro, uma fogueira com bancos, duas casas, uma plantacao e tres aldeoes. Cada jogador e levado ate o quadro na
 * primeira vez que entra.
 */
public final class StartingVillage {
	/** Raio da busca de lugar em volta do spawn vanilla. */
	public static final int SEARCH_RADIUS = 240;
	/** Raio do acampamento (um pouco mais folgado que o do nivel, para caber mesmo com arvores e desnivel). */
	public static final int CAMP_RADIUS = 28;

	private StartingVillage() {
	}

	static void onServerStarted(MinecraftServer server) {
		VillageData data = VillageData.get(server);
		if (data.starterChecked()) {
			return;
		}
		data.setStarterChecked();
		ServerLevel level = server.overworld();
		if (!server.getGameRules().get(VillageServer.STARTING_VILLAGE) || level.getGameTime() > 200
				|| !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)) {
			return;
		}
		try {
			VillageState village = create(level, level.getRespawnData().pos());
			if (village != null) {
				MlinNotas.LOGGER.info("[{}] acampamento inicial {} em {}, {}, {}", MlinNotas.MOD_ID, village.name(), village.x(), village.y(), village.z());
			}
		} catch (RuntimeException e) {
			MlinNotas.LOGGER.error("[{}] falha ao criar o acampamento inicial", MlinNotas.MOD_ID, e);
		}
	}

	/** Funda e constroi o acampamento perto de {@code near} e move o spawn do mundo para a praca. */
	public static VillageState create(ServerLevel level, BlockPos near) {
		int[] site = findSite(level, near);
		BlockPos board = boardSpot(level, site[0], site[1]);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState().setValue(VillageBoardBlock.FACING, Direction.SOUTH), board,
				3);
		VillageState village = VillageServer.create(level, board, true);
		// clareira em volta do quadro
		VillageBuilder.clearTrees(level, village, -9, -9, 9, 9, board.getY() - 4, board.getY() + 16, true);
		Random random = new Random(village.seed());
		VillagePlanner.Planned fire = VillagePlanner.building(null, Buildings.campfire(village.style()), village, new WorldTerrain(level, board, true),
				random, 3, 1);
		if (fire != null) {
			village.addLot(fire.lot().rect());
			VillageBuilder.clearSite(level, village, fire, true);
			VillageBuilder.buildNow(level, village, fire, true);
		}
		for (VillageProject project : List.of(VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.FARM)) {
			VillagePlanner.Planned planned = VillagePlanner.plan(project, village, new WorldTerrain(level, board, true), random.nextLong(), CAMP_RADIUS);
			if (planned == null) {
				continue;
			}
			if (planned.lot() != null) {
				village.addLot(planned.lot().rect());
			}
			VillageBuilder.clearSite(level, village, planned, true);
			VillageBuilder.buildNow(level, village, planned, true);
			village.addBuilt(project);
			for (int[] s : planned.spawns()) {
				EntityTypes.VILLAGER.spawn(level, VillageBuilder.world(village, s[0], s[1], s[2]), EntitySpawnReason.MOB_SUMMONED);
			}
		}
		VillageData.get(level.getServer()).changed();
		BlockPos spawn = spawnSpot(level, board);
		level.setRespawnData(new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD, spawn), 180.0F, 0.0F));
		return village;
	}

	/** Lugar livre na frente do quadro (sul), no chao. */
	static BlockPos spawnSpot(ServerLevel level, BlockPos board) {
		level.getChunk(board.getX() >> 4, board.getZ() >> 4);
		for (int dz = 2; dz <= 5; dz++) {
			for (int dx : new int[] {0, 1, -1, 2, -2}) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, board.offset(dx, 0, dz));
				if (Math.abs(top.getY() - board.getY()) <= 2 && level.getBlockState(top).isAir() && level.getBlockState(top.above()).isAir()) {
					return top;
				}
			}
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, board.offset(0, 0, 3));
	}

	/** Coluna livre mais perto do ponto escolhido para por o quadro. */
	private static BlockPos boardSpot(ServerLevel level, int x, int z) {
		WorldTerrain terrain = new WorldTerrain(level, new BlockPos(x, 0, z));
		for (int r = 0; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if ((Math.abs(dx) == r || Math.abs(dz) == r) && terrain.free(dx, dz) && terrain.free(dx, dz + 1)) {
						return new BlockPos(x + dx, terrain.ground(dx, dz) + 1, z + dz);
					}
				}
			}
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/**
	 * Melhor centro perto de {@code near} pelo gerador (sem gerar chunks): seco, acima do mar, pouco desnivel num
	 * quadrado de 42 blocos e bioma aberto. Devolve {x, z}.
	 */
	static int[] findSite(ServerLevel level, BlockPos near) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		double best = Double.MAX_VALUE;
		int[] site = {near.getX(), near.getZ()};
		for (int ring = 0; ring * 24 <= SEARCH_RADIUS; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int x = near.getX() + i * 24;
					int z = near.getZ() + j * 24;
					double score = score(level, generator, random, x, z) + ring * 0.4;
					if (score < best) {
						best = score;
						site = new int[] {x, z};
					}
				}
			}
			if (best < 3 + ring * 0.4) {
				break;
			}
		}
		return site;
	}

	private static double score(ServerLevel level, ChunkGenerator generator, RandomState random, int x, int z) {
		int centerY = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
		// bioma no centro e nos quatro cantos: o pior manda (evita uma ponta de planicie no meio da floresta)
		double penalty = 0;
		for (int[] s : new int[][] {{0, 0}, {-20, -20}, {20, -20}, {-20, 20}, {20, 20}}) {
			Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x + s[0]), QuartPos.fromBlock(centerY), QuartPos.fromBlock(z + s[1]));
			penalty = Math.max(penalty, biomePenalty(biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("")));
			if (penalty >= 1000) {
				return Double.MAX_VALUE;
			}
		}
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int dx = -21; dx <= 21; dx += 7) {
			for (int dz = -21; dz <= 21; dz += 7) {
				int surface = generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, level, random);
				int floor = generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
				if (floor < surface || surface - 1 < level.getSeaLevel()) {
					return Double.MAX_VALUE;
				}
				min = Math.min(min, surface);
				max = Math.max(max, surface);
			}
		}
		return penalty + (max - min) * 1.5;
	}

	/** Biomas abertos sao melhores; oceano, rio e praia nunca. */
	static double biomePenalty(String id) {
		if (id.contains("ocean") || id.contains("river") || id.contains("beach") || id.contains("shore") || id.contains("mushroom")) {
			return 1000;
		}
		if (id.equals("plains") || id.equals("sunflower_plains") || id.equals("meadow") || id.contains("savanna") || id.equals("desert")
				|| id.equals("snowy_plains")) {
			return 0;
		}
		if (id.contains("forest") || id.contains("taiga") || id.contains("cherry")) {
			return 9;
		}
		return 15;
	}

	/** Primeira entrada de cada jogador: leva ate a frente do quadro do acampamento. */
	static void greet(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(VillageServer.GREETED))) {
			return;
		}
		player.setAttached(VillageServer.GREETED, true);
		MinecraftServer server = player.level().getServer();
		VillageState starter = null;
		for (VillageState village : VillageData.get(server).villages()) {
			if (village.starter()) {
				starter = village;
			}
		}
		if (starter == null || player.level().dimension() != Level.OVERWORLD || !starter.dimension().equals(VillageServer.dimension(server.overworld()))) {
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos spot = spawnSpot(level, VillageServer.center(starter));
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
		if (player.connection != null) {
			player.sendSystemMessage(Component.translatable("message.mlinnotas.village.welcome", starter.name()).withStyle(ChatFormatting.GOLD));
		}
	}
}
