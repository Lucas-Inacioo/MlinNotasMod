package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Vila inicial: num mundo novo (gamerule {@code mlinnotas:starting_village}, ligada por padrao; so em mundos com
 * terreno normal), o spawn vai para um Acampamento perto do spawn vanilla, num lugar plano e seco de bioma aberto: o
 * quadro, uma fogueira com bancos, duas casas, uma plantacao e tres aldeoes. Cada jogador e levado ate o quadro na
 * primeira vez que entra.
 *
 * <p>O lugar e escolhido no {@link ServerLevelEvents#LOAD} do Overworld, antes do spawn vanilla gerar qualquer chunk;
 * dali em diante as vilas vanilla que comecariam perto ({@link #SUPPRESS_RADIUS}) nao sao geradas (mixin no inicio de
 * estrutura) e o quadro do worldgen nao aparece perto. Num mundo antigo sem vila inicial (reparo), a busca evita os
 * chunks que ja existem em disco (com folga de {@link #PROTECT_MARGIN}); num mundo ja muito explorado, a vila vai
 * para longe (200+ blocos) de qualquer vila vanilla.
 */
public final class StartingVillage {
	/** Raio da busca de lugar em volta do spawn vanilla. */
	public static final int SEARCH_RADIUS = 240;
	/** Raio do acampamento (um pouco mais folgado que o do nivel, para caber mesmo com arvores e desnivel). */
	public static final int CAMP_RADIUS = 28;
	/** Inicio de vila vanilla a menos disso do centro da vila inicial nao e gerado (raio da Capital + 40 + pecas). */
	public static final int SUPPRESS_RADIUS = VillageLevel.CAPITAL.radius() + 40 + 80;
	/** Quadro do worldgen nunca a menos disso da vila inicial. */
	public static final int BOARD_RADIUS = 160;
	/** Folga em volta de chunks ja existentes (mundo antigo) onde a vila nao planeja nada. */
	public static final int PROTECT_MARGIN = 12;
	/** Distancia minima de uma vila vanilla quando o lugar nao foi escolhido cedo. */
	public static final int FALLBACK_DISTANCE = 200;
	/** Mundo com mais chunks que isso em volta do spawn e "jogado": vai pelo caminho de longe das vilas. */
	private static final int PLAYED_CHUNKS = 256;

	/** Centro (x, z) da vila inicial do servidor atual (lido pelo mixin de estruturas e pelo quadro do worldgen). */
	private static volatile int[] site;

	private StartingVillage() {
	}

	static void init() {
		ServerLevelEvents.LOAD.register(StartingVillage::onLevelLoad);
		ServerLifecycleEvents.SERVER_STARTED.register(StartingVillage::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> site = null);
	}

	/** Centro da vila inicial (x, z) ou null. */
	public static int[] site() {
		int[] s = site;
		return s == null ? null : s.clone();
	}

	/** Teste/servidor: muda o centro usado para suprimir vilas vanilla. */
	public static void setSite(int[] s) {
		site = s == null ? null : s.clone();
	}

	/** Uma vila vanilla que comeca no chunk (cx, cz) chegaria perto da vila inicial? */
	public static boolean suppresses(int chunkX, int chunkZ) {
		int[] s = site;
		if (s == null) {
			return false;
		}
		long dx = (chunkX << 4) + 8 - s[0];
		long dz = (chunkZ << 4) + 8 - s[1];
		return dx * dx + dz * dz <= (long) SUPPRESS_RADIUS * SUPPRESS_RADIUS;
	}

	/** Quadro do worldgen em {@code pos} ficaria perto demais da vila inicial? */
	public static boolean nearSite(BlockPos pos) {
		int[] s = site;
		if (s == null) {
			return false;
		}
		long dx = pos.getX() - s[0];
		long dz = pos.getZ() - s[1];
		return dx * dx + dz * dz <= (long) BOARD_RADIUS * BOARD_RADIUS;
	}

	static VillageState starter(VillageData data) {
		for (VillageState village : data.villages()) {
			if (village.starter()) {
				return village;
			}
		}
		return null;
	}

	private static boolean enabled(MinecraftServer server, ServerLevel level) {
		return server.getGameRules().get(VillageServer.STARTING_VILLAGE) && level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator;
	}

	/**
	 * Overworld acabou de ser criado (antes do spawn gerar chunks): escolhe o lugar da vila inicial pelo gerador e liga
	 * a supressao das vilas vanilla perto. Vila inicial que ja existe continua protegida.
	 */
	static void onLevelLoad(MinecraftServer server, ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		site = null;
		try {
			VillageData data = VillageData.get(server);
			int[] stored = data.starterSite();
			VillageState starter = starter(data);
			if (stored != null) {
				site = stored;
				return;
			}
			if (starter != null) {
				site = new int[] {starter.x(), starter.z()};
				return;
			}
			if (data.starterChecked() || !enabled(server, level)) {
				return;
			}
			boolean fresh = !server.getWorldData().overworldData().isInitialized();
			BlockPos near;
			List<VillageState.Rect> protect = List.of();
			if (fresh) {
				ChunkPos origin = level.getChunkSource().getGenerator().getOrigin(level.getChunkSource().randomState());
				near = new BlockPos(origin.getMinBlockX() + 8, 0, origin.getMinBlockZ() + 8);
			} else {
				near = level.getRespawnData().pos();
				List<ChunkPos> existing = existingChunks(server, near, SEARCH_RADIUS + 64);
				if (existing.size() > PLAYED_CHUNKS) {
					// mundo ja explorado: o lugar sai depois, longe das vilas vanilla que ja existem
					MlinNotas.LOGGER.info("[{}] mundo antigo com {} chunks perto do spawn: vila inicial longe das vilas vanilla", MlinNotas.MOD_ID,
							existing.size());
					return;
				}
				protect = protectedAreas(existing);
			}
			int[] chosen = findSite(level, near, protect);
			data.setStarterSite(chosen[0], chosen[1], protect);
			site = chosen;
			MlinNotas.LOGGER.info("[{}] lugar da vila inicial escolhido em {}, {} ({} areas protegidas)", MlinNotas.MOD_ID, chosen[0], chosen[1],
					protect.size());
		} catch (RuntimeException e) {
			MlinNotas.LOGGER.error("[{}] falha ao escolher o lugar da vila inicial", MlinNotas.MOD_ID, e);
		}
	}

	static void onServerStarted(MinecraftServer server) {
		VillageData data = VillageData.get(server);
		if (data.starterChecked()) {
			return;
		}
		data.setStarterChecked();
		ServerLevel level = server.overworld();
		if (!enabled(server, level) || starter(data) != null) {
			return;
		}
		try {
			VillageState village = createAtSite(level, data);
			if (village != null) {
				MlinNotas.LOGGER.info("[{}] acampamento inicial {} em {}, {}, {}", MlinNotas.MOD_ID, village.name(), village.x(), village.y(), village.z());
			}
		} catch (RuntimeException e) {
			MlinNotas.LOGGER.error("[{}] falha ao criar o acampamento inicial", MlinNotas.MOD_ID, e);
		}
	}

	/** Cria a vila no lugar escolhido cedo, ou (sem lugar) num lugar longe das vilas vanilla perto do spawn. */
	static VillageState createAtSite(ServerLevel level, VillageData data) {
		int[] chosen = data.starterSite();
		List<VillageState.Rect> protect = data.starterProtect();
		if (chosen == null) {
			chosen = findSafeSite(level, level.getRespawnData().pos());
			data.setStarterSite(chosen[0], chosen[1], List.of());
			site = chosen;
		}
		return build(level, chosen[0], chosen[1], protect);
	}

	/**
	 * Reparo pelo comando: tira o registro da vila inicial, marca para procurar de novo e cria agora (num mundo ja
	 * gerado, longe das vilas vanilla e de construcoes de jogador).
	 */
	public static VillageState restart(MinecraftServer server) {
		VillageData data = VillageData.get(server);
		for (VillageState village : List.copyOf(data.villages())) {
			if (village.starter()) {
				data.remove(village.key());
			}
		}
		data.resetStarter();
		site = null;
		ServerLevel level = server.overworld();
		data.setStarterChecked();
		int[] chosen = findSafeSite(level, level.getRespawnData().pos());
		data.setStarterSite(chosen[0], chosen[1], List.of());
		site = chosen;
		return build(level, chosen[0], chosen[1], List.of());
	}

	/** Funda e constroi o acampamento perto de {@code near} e move o spawn do mundo para a praca (testes). */
	public static VillageState create(ServerLevel level, BlockPos near) {
		int[] chosen = findSite(level, near, List.of());
		return build(level, chosen[0], chosen[1], List.of());
	}

	/**
	 * Constroi o acampamento com centro em (x, z): quadro, plano da vila (com as areas protegidas), fogueira, duas
	 * casas e a plantacao nos lotes do plano, ja com o terreno preparado; move o spawn para a frente do quadro.
	 */
	static VillageState build(ServerLevel level, int x, int z, List<VillageState.Rect> protect) {
		BlockPos board = boardSpot(level, x, z);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState().setValue(VillageBoardBlock.FACING, Direction.SOUTH), board,
				3);
		VillageState village = VillageServer.create(level, board, true);
		// clareira em volta do quadro
		VillageBuilder.clearTrees(level, village, -9, -9, 9, 9, board.getY() - 4, board.getY() + 16, true);
		WorldTerrain terrain = new WorldTerrain(level, board, true);
		VillageLayout layout = new VillageLayout();
		for (VillageState.Rect r : protect) {
			layout.addProtected(new VillageState.Rect(r.x0() - board.getX(), r.z0() - board.getZ(), r.x1() - board.getX(), r.z1() - board.getZ()));
		}
		village.setLayout(layout);
		layout.ensureRadius(VillageLevel.CAMP.radius());
		Random random = new Random(village.seed());
		VillageData data = VillageData.get(level.getServer());
		VillageLayout.Lot fire = layout.addNear(VillageLayout.CAMPFIRE, village.style(), village.seed(), terrain, random, 0);
		layout.extend(village, terrain, VillageLevel.CAMP);
		List<VillageLayout.Lot> lots = new ArrayList<>();
		if (fire != null) {
			lots.add(fire);
		}
		for (VillageProject project : List.of(VillageProject.HOUSE, VillageProject.FARM)) {
			lots.addAll(layout.lotsOf(project.id()));
		}
		for (VillageLayout.Lot lot : lots) {
			VillageProject project = lot.project();
			VillagePlanner.Planned planned = VillagePlanner.onLot(project, lot, village, new WorldTerrain(level, board, true));
			village.addLot(lot.rect());
			VillageBuilder.clearSite(level, village, planned, true);
			VillageData.Job job = VillageData.Job.create(village.key(), planned);
			VillageBuilder.runJob(level, village, job, true);
			lot.setBuilt(true);
			data.putRecord(village.key(), lot.id(), job.buildRecord());
			if (project != null) {
				village.addBuilt(project);
			}
			for (int[] s : planned.spawns()) {
				EntityTypes.VILLAGER.spawn(level, VillageBuilder.world(village, s[0], s[1], s[2]), EntitySpawnReason.MOB_SUMMONED);
			}
		}
		data.changed();
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

	/** Candidato a centro com a nota (menor = melhor). */
	private record Candidate(int x, int z, double score) {
	}

	/**
	 * Melhor centro perto de {@code near} pelo gerador (sem gerar chunks): seco, acima do mar, pouco desnivel num
	 * quadrado de 42 blocos, bioma aberto e longe das areas protegidas. Devolve {x, z}.
	 */
	static int[] findSite(ServerLevel level, BlockPos near, List<VillageState.Rect> protect) {
		List<Candidate> list = candidates(level, near, protect, true);
		return list.isEmpty() ? new int[] {near.getX(), near.getZ()} : new int[] {list.getFirst().x, list.getFirst().z};
	}

	/**
	 * Como {@link #findSite} num mundo ja gerado: o lugar fica a {@link #FALLBACK_DISTANCE}+ de qualquer vila vanilla e
	 * com o centro quase todo de chao natural (sem construcoes de jogador).
	 */
	static int[] findSafeSite(ServerLevel level, BlockPos near) {
		List<Candidate> list = candidates(level, near, List.of(), false);
		int tried = 0;
		for (Candidate c : list) {
			if (tried++ >= 24) {
				break;
			}
			BlockPos pos = new BlockPos(c.x, level.getSeaLevel(), c.z);
			BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, pos, FALLBACK_DISTANCE / 16 + 1, false);
			if (village != null && Math.hypot(village.getX() - c.x, village.getZ() - c.z) < FALLBACK_DISTANCE) {
				continue;
			}
			if (naturalRatio(level, c.x, c.z, 16) < 0.9) {
				continue;
			}
			return new int[] {c.x, c.z};
		}
		return list.isEmpty() ? new int[] {near.getX(), near.getZ()} : new int[] {list.getFirst().x, list.getFirst().z};
	}

	/** Fracao das colunas num raio que sao chao natural livre (le o mundo de verdade). */
	private static double naturalRatio(ServerLevel level, int x, int z, int r) {
		WorldTerrain terrain = new WorldTerrain(level, new BlockPos(x, 0, z));
		int free = 0;
		int total = 0;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				total++;
				if (terrain.free(dx, dz)) {
					free++;
				}
			}
		}
		return total == 0 ? 0 : (double) free / total;
	}

	private static List<Candidate> candidates(ServerLevel level, BlockPos near, List<VillageState.Rect> protect, boolean stopEarly) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		List<Candidate> out = new ArrayList<>();
		double best = Double.MAX_VALUE;
		for (int ring = 0; ring * 24 <= SEARCH_RADIUS; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int x = near.getX() + i * 24;
					int z = near.getZ() + j * 24;
					if (tooClose(protect, x, z)) {
						continue;
					}
					double score = score(level, generator, random, x, z);
					if (score == Double.MAX_VALUE) {
						continue;
					}
					score += ring * 0.4;
					out.add(new Candidate(x, z, score));
					best = Math.min(best, score);
				}
			}
			if (stopEarly && best < 3 + ring * 0.4) {
				break;
			}
		}
		out.sort(Comparator.comparingDouble(Candidate::score));
		return out;
	}

	/** Centro perto demais de uma area protegida (a praca e o primeiro anel de lotes ficam fora dela). */
	private static boolean tooClose(List<VillageState.Rect> protect, int x, int z) {
		int margin = VillagePlanner.PLAZA_RADIUS + 11;
		for (VillageState.Rect r : protect) {
			int dx = Math.max(Math.max(r.x0() - x, x - r.x1()), 0);
			int dz = Math.max(Math.max(r.z0() - z, z - r.z1()), 0);
			if (dx * dx + dz * dz < margin * margin) {
				return true;
			}
		}
		return false;
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

	// ---------------------------------------------------------------- chunks ja existentes (mundo antigo)

	/**
	 * Chunks do Overworld que ja existem em disco a ate {@code radius} de {@code near} (le so o cabecalho dos arquivos
	 * de regiao: entrada diferente de zero = chunk salvo).
	 */
	static List<ChunkPos> existingChunks(MinecraftServer server, BlockPos near, int radius) {
		Path dir = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT)).resolve("region");
		List<ChunkPos> out = new ArrayList<>();
		int c0x = (near.getX() - radius) >> 4;
		int c1x = (near.getX() + radius) >> 4;
		int c0z = (near.getZ() - radius) >> 4;
		int c1z = (near.getZ() + radius) >> 4;
		for (int rx = c0x >> 5; rx <= c1x >> 5; rx++) {
			for (int rz = c0z >> 5; rz <= c1z >> 5; rz++) {
				Path file = dir.resolve("r." + rx + "." + rz + ".mca");
				if (!Files.isRegularFile(file)) {
					continue;
				}
				try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
					if (raf.length() < 4096) {
						continue;
					}
					for (int i = 0; i < 1024; i++) {
						int entry = raf.readInt();
						if (entry == 0) {
							continue;
						}
						int cx = (rx << 5) + (i & 31);
						int cz = (rz << 5) + (i >> 5);
						if (cx >= c0x && cx <= c1x && cz >= c0z && cz <= c1z) {
							out.add(new ChunkPos(cx, cz));
						}
					}
				} catch (IOException e) {
					MlinNotas.LOGGER.warn("[{}] nao deu para ler {}", MlinNotas.MOD_ID, file, e);
				}
			}
		}
		return out;
	}

	/** Cada chunk existente vira um retangulo (absoluto) com {@link #PROTECT_MARGIN} de folga. */
	static List<VillageState.Rect> protectedAreas(List<ChunkPos> chunks) {
		List<VillageState.Rect> out = new ArrayList<>();
		for (ChunkPos c : chunks) {
			out.add(new VillageState.Rect(c.getMinBlockX() - PROTECT_MARGIN, c.getMinBlockZ() - PROTECT_MARGIN, c.getMaxBlockX() + PROTECT_MARGIN,
					c.getMaxBlockZ() + PROTECT_MARGIN));
		}
		return out;
	}

	// ---------------------------------------------------------------- jogadores

	/** Primeira entrada de cada jogador: leva ate a frente do quadro do acampamento e mostra o plano da vila. */
	static void greet(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(VillageServer.GREETED))) {
			return;
		}
		player.setAttached(VillageServer.GREETED, true);
		MinecraftServer server = player.level().getServer();
		VillageState starter = starter(VillageData.get(server));
		if (starter == null || player.level().dimension() != Level.OVERWORLD || !starter.dimension().equals(VillageServer.dimension(server.overworld()))) {
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos spot = spawnSpot(level, VillageServer.center(starter));
		player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
		if (player.connection != null) {
			player.sendSystemMessage(Component.translatable("message.mlinnotas.village.welcome", starter.name()).withStyle(ChatFormatting.GOLD));
			VillagePlanning.show(player, starter, VillagePlanning.AUTO_SECONDS);
			player.sendSystemMessage(Component.translatable("message.mlinnotas.village.area_shown", VillagePlanning.AUTO_SECONDS)
					.withStyle(ChatFormatting.AQUA));
		}
	}
}
