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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;

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
 * estrutura). Esta e a unica vila que o mod gerencia: mundo sem vila inicial (antigo) nao ganha uma sozinho; so o
 * comando de operador {@code /village admin restart_starter} cria uma (num mundo ja gerado).
 */
public final class StartingVillage {
	/** Raio da busca de lugar em volta do spawn vanilla. */
	public static final int SEARCH_RADIUS = 240;
	/** Raio do acampamento (um pouco mais folgado que o do nivel, para caber mesmo com arvores e desnivel). */
	public static final int CAMP_RADIUS = 28;
	/** Inicio de vila vanilla a menos disso do centro da vila inicial nao e gerado (raio da Capital + 40 + pecas). */
	public static final int SUPPRESS_RADIUS = VillageLevel.CROWN_CITY.radius() + 40 + 80;

	/** Centro (x, z) da vila inicial do servidor atual (lido pelo mixin de estruturas). */
	private static volatile int[] site;

	private StartingVillage() {
	}

	static void init() {
		com.mlin.notas.village.gen.VillageGen.init();
		ServerLevelEvents.LOAD.register(StartingVillage::onLevelLoad);
		ServerLifecycleEvents.SERVER_STARTED.register(StartingVillage::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			site = null;
			VillageFieldServer.clear();
		});
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
		VillageFieldServer.clear();
		try {
			VillageData data = VillageData.get(server);
			int[] stored = data.starterSite();
			VillageState starter = starter(data);
			// campo de alturas ja calculado: fica disponivel para a geracao antes de qualquer chunk
			VillageFieldServer.setActive(starter != null ? data.field(starter.key()) : data.field(VillageData.PENDING_FIELD));
			if (starter != null) {
				// Vilas 3.0 fase 2: vila antiga sem campo (ou com campo menor que o plano) calcula aqui, antes do spawn gerar
				// chunks, para nenhum chunk novo nascer sem a mistura; o SERVER_STARTED so repete se isto falhar
				try {
					VillageFieldServer.ensureField(level, starter);
				} catch (RuntimeException e) {
					MlinNotas.LOGGER.error("[{}] falha ao calcular o campo de alturas no carregamento do Overworld", MlinNotas.MOD_ID, e);
				}
			}
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
			if (!fresh) {
				// mundo antigo sem vila inicial: o mod nao cria uma sozinho (so /village admin restart_starter)
				return;
			}
			ChunkPos origin = level.getChunkSource().getGenerator().getOrigin(level.getChunkSource().randomState());
			BlockPos near = new BlockPos(origin.getMinBlockX() + 8, 0, origin.getMinBlockZ() + 8);
			int[] chosen = findSite(level, near);
			data.setStarterSite(chosen[0], chosen[1]);
			site = chosen;
			MlinNotas.LOGGER.info("[{}] lugar da vila inicial escolhido em {}, {}", MlinNotas.MOD_ID, chosen[0], chosen[1]);
			// Vilas 3.0: plano diretor e campo de alturas pelo gerador, antes de o spawn gerar chunks
			VillageFieldServer.Prepared prep = VillageFieldServer.prepareStarter(level, chosen[0], chosen[1]);
			data.putField(VillageData.PENDING_FIELD, prep.field());
			VillageFieldServer.setActive(prep.field());
		} catch (RuntimeException e) {
			MlinNotas.LOGGER.error("[{}] falha ao escolher o lugar da vila inicial", MlinNotas.MOD_ID, e);
		}
	}

	static void onServerStarted(MinecraftServer server) {
		VillageData data = VillageData.get(server);
		VillageState existing = starter(data);
		if (existing != null) {
			// vila inicial de antes das Vilas 3.0 (ou que ganhou o anel rural): campo de alturas com o que ja existe pinado
			try {
				VillageFieldServer.ensureField(server.overworld(), existing);
			} catch (RuntimeException e) {
				MlinNotas.LOGGER.error("[{}] falha ao calcular o campo de alturas da vila inicial", MlinNotas.MOD_ID, e);
			}
		}
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

	/** Cria a vila no lugar escolhido cedo (sem lugar escolhido, nao cria nada). */
	static VillageState createAtSite(ServerLevel level, VillageData data) {
		int[] chosen = data.starterSite();
		return chosen == null ? null : build(level, chosen[0], chosen[1]);
	}

	/**
	 * Reparo pelo comando: tira o registro da vila inicial, marca para procurar de novo e cria agora (num mundo ja
	 * gerado, num lugar de chao natural, sem construcoes de jogador).
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
		int[] chosen = findNaturalSite(level, level.getRespawnData().pos());
		data.setStarterSite(chosen[0], chosen[1]);
		site = chosen;
		return buildLegacy(level, chosen[0], chosen[1]);
	}

	/** Funda e constroi o acampamento perto de {@code near} e move o spawn do mundo para a praca (testes). */
	public static VillageState create(ServerLevel level, BlockPos near) {
		int[] chosen = findSite(level, near);
		return build(level, chosen[0], chosen[1]);
	}

	/**
	 * Constroi o acampamento com centro em (x, z): quadro, plano da vila, fogueira, duas
	 * casas e a plantacao nos lotes do plano, ja com o terreno preparado; move o spawn para a frente do quadro.
	 */
	static VillageState build(ServerLevel level, int x, int z) {
		// Vilas 3.0: o plano diretor (ja com o anel rural) e o campo de alturas saem do gerador antes dos chunks; o quadro
		// fica exatamente no centro do campo, em cima da praca
		VillageFieldServer.Prepared prep = VillageFieldServer.prepareStarter(level, x, z);
		BlockPos board = new BlockPos(x, prep.plazaY() + 1, z);
		VillageState village = VillageServer.create(level, board, true);
		if (village.seed() != prep.seed()) {
			MlinNotas.LOGGER.warn("[{}] semente da vila inicial diferente da do plano pre-calculado", MlinNotas.MOD_ID);
		}
		VillageLayout layout = new VillageLayout();
		village.setLayout(layout);
		layout.ensureRadius(VillageLevel.CAMP.radius());
		layout.setMaster(prep.plan());
		VillageData data = VillageData.get(level.getServer());
		data.putField(village.key(), prep.field());
		data.removeField(VillageData.PENDING_FIELD);
		VillageFieldServer.setActive(prep.field());
		// clareira em volta do quadro e os aneis do Acampamento ja aplainados (presente do mundo novo: arvores cortadas). O
		// plano veio do gerador: os chunks dos aneis ainda nao existem e a terraplenagem pula coluna de chunk descarregado
		int reach = StreetPlanner.RING_STREETS[Math.min(VillageLevel.CAMP.rings(), StreetPlanner.RING_STREETS.length) - 1] + 6;
		for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
			for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		VillageBuilder.clearTrees(level, village, -9, -9, 9, 9, board.getY() - 4, board.getY() + 16, true);
		for (int ring = 1; ring <= VillageLevel.CAMP.rings(); ring++) {
			VillageTerrain.prepareNow(level, village, ring, true);
		}
		placeBoard(level, board);
		return finishCamp(level, village, board, layout);
	}

	/** Quadro na praca ja aplainada (o que sobrou de natural no lugar sai). */
	private static void placeBoard(ServerLevel level, BlockPos board) {
		for (BlockPos p : new BlockPos[] {board, board.above()}) {
			if (!level.getBlockState(p).isAir() && WorldTerrain.replaceable(level.getBlockState(p), true)) {
				level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			}
		}
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState().setValue(VillageBoardBlock.FACING, Direction.SOUTH), board,
				3);
	}

	/**
	 * Vila inicial num mundo ja gerado (reparo pelo comando {@code restart_starter}): plano diretor lido do mundo de agora,
	 * como antes das Vilas 3.0 (respeita o que o jogador construiu); o campo de alturas sai depois, com o que ja existe
	 * pinado.
	 */
	static VillageState buildLegacy(ServerLevel level, int x, int z) {
		BlockPos board = boardSpot(level, x, z);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState().setValue(VillageBoardBlock.FACING, Direction.SOUTH), board,
				3);
		VillageState village = VillageServer.create(level, board, true);
		// clareira em volta do quadro
		VillageBuilder.clearTrees(level, village, -9, -9, 9, 9, board.getY() - 4, board.getY() + 16, true);
		WorldTerrain terrain = new WorldTerrain(level, board, true);
		VillageLayout layout = new VillageLayout();
		village.setLayout(layout);
		layout.ensureRadius(VillageLevel.CAMP.radius());
		// plano diretor e os aneis do Acampamento ja aplainados (presente do mundo novo: arvores cortadas)
		layout.planMaster(village, terrain);
		for (int ring = 1; ring <= VillageLevel.CAMP.rings(); ring++) {
			VillageTerrain.prepareNow(level, village, ring, true);
		}
		VillageState out = finishCamp(level, village, board, layout);
		VillageFieldServer.ensureField(level, out);
		return out;
	}

	/** Fogueira, duas casas e a plantacao nos lotes do plano; spawn na frente do quadro. */
	private static VillageState finishCamp(ServerLevel level, VillageState village, BlockPos board, VillageLayout layout) {
		WorldTerrain terrain = new WorldTerrain(level, board, true);
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
	 * quadrado de 42 blocos, bioma aberto. Devolve {x, z}.
	 */
	static int[] findSite(ServerLevel level, BlockPos near) {
		List<Candidate> list = candidates(level, near, true);
		return list.isEmpty() ? new int[] {near.getX(), near.getZ()} : new int[] {list.getFirst().x, list.getFirst().z};
	}

	/**
	 * Como {@link #findSite} num mundo ja gerado (so o reparo de operador): o centro e quase todo de chao natural (sem construcoes de jogador).
	 */
	static int[] findNaturalSite(ServerLevel level, BlockPos near) {
		List<Candidate> list = candidates(level, near, false);
		int tried = 0;
		for (Candidate c : list) {
			if (tried++ >= 24) {
				break;
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

	private static List<Candidate> candidates(ServerLevel level, BlockPos near, boolean stopEarly) {
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
					double score = com.mlin.notas.village.gen.VillageGen.rawDouble(() -> score(level, generator, random, x, z));
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
