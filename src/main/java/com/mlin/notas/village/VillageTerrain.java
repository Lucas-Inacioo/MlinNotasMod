package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.skills.SkillServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lado servidor da terraplenagem do plano diretor: preco de cada anel lido do mundo (o que o jogador acertou a mao
 * baixa o preco e quarteirao inteiro na altura ja conta como pronto), entregas, a obra que aplaina o anel coluna por
 * coluna no mesmo ritmo das construcoes (cada coluna e lida do mundo na hora; so blocos naturais sao trocados;
 * arvores nascidas no mundo caem e, nos arredores, mudas sao replantadas; estacas nos cantos dos lotes no fim), a
 * conferencia do plato antes de cada bloco das obras e o gancho das casas de jogador (cama + porta).
 */
public final class VillageTerrain {
	/** Blocos solidos por tick (o mesmo das obras). */
	public static final int BLOCKS_PER_TICK = VillageBuilder.BLOCKS_PER_TICK;
	/** Colunas olhadas por tick no maximo (colunas ja prontas sao baratas). */
	public static final int COLUMNS_PER_TICK = 48;
	/** A cada quantos ticks uma obra parada confere de novo o plato. */
	public static final int PAUSE_RECHECK = 100;

	private static final Map<String, List<Long>> COLUMNS = new HashMap<>();

	private VillageTerrain() {
	}

	static void clearCache() {
		COLUMNS.clear();
	}

	private static List<Long> columns(VillageState village, MasterPlan plan, int ring) {
		return COLUMNS.computeIfAbsent(village.key() + "#" + ring + "#" + System.identityHashCode(plan), k -> plan.columns(ring));
	}

	/** Estacas: cantos dos lotes do anel (x, z). */
	static List<int[]> stakes(MasterPlan plan, int ring) {
		List<int[]> out = new ArrayList<>();
		for (MasterPlan.Parcel p : plan.parcels()) {
			MasterPlan.Block block = plan.block(p.block());
			if (block != null && block.ring() == ring && !p.retired()) {
				out.addAll(p.corners());
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- preco e situacao

	/** Anel que a vila pode pagar agora (aberto no nivel ou o proximo). */
	public static boolean fundable(VillageState village, int ring) {
		return ring >= 1 && ring <= Math.min(MasterPlan.RINGS, village.level().rings() + 1);
	}

	/**
	 * Le o terreno dos aneis que a vila pode pagar: preco de cada um, quarteiroes ja na altura (prontos), lotes onde
	 * apareceu construcao de jogador, casas de jogador; anel todo pronto a mao vira pronto. Devolve o nivel novo se a
	 * vila subiu.
	 */
	public static VillageLevel refresh(ServerLevel level, VillageState village) {
		MasterPlan plan = village.layout() == null ? null : village.layout().master();
		if (plan == null) {
			return null;
		}
		WorldTerrain terrain = new WorldTerrain(level, VillageServer.center(village));
		VillageLayout layout = village.layout();
		VillageData data = VillageData.get(level.getServer());
		VillageLevel up = null;
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			RingWork work = village.ring(ring);
			if (!fundable(village, ring) || work.done() || data.terrainJob(village.key()) != null && data.terrainJob(village.key()).ring == ring) {
				continue;
			}
			// quarteirao inteiro na altura conta como pronto
			Map<Integer, Boolean> ready = new HashMap<>();
			for (long col : columns(village, plan, ring)) {
				int x = VillageLayout.colX(col);
				int z = VillageLayout.colZ(col);
				MasterPlan.Block block = plan.blockAt(x, z);
				if (block == null || terrain.playerBuilt(x, z)) {
					continue;
				}
				boolean at = terrain.bed(x, z) == block.target() && terrain.water(x, z) == 0;
				ready.merge(block.id(), at, Boolean::logicalAnd);
			}
			ready.forEach((id, ok) -> {
				if (ok) {
					plan.block(id).setPrepared(true);
				}
			});
			work.setCost(RingCost.of(plan, ring, terrain, village.stockpile() + CraftsRules.quarryFillCredit(VillageBuildings.level(village, CraftsBuildings.QUARRY))));
			if (work.state() == RingWork.State.OPEN && work.emeralds() == 0 && work.columns() > 0 && work.ready() >= work.columns()) {
				work.setState(RingWork.State.DONE);
				plan.setRingPrepared(ring);
				VillageLevel u = village.recompute();
				up = u != null ? u : up;
			}
		}
		refreshPlayerLots(level, village, plan, terrain, layout);
		VillageLevel h = village.setPlayerHouses(countPlayerHouses(level, village, plan, terrain));
		data.changed();
		return h != null ? h : up;
	}

	/** Lote do plano onde apareceu construcao de jogador vira "lote do jogador" (a vila nunca mexe nele). */
	static void refreshPlayerLots(ServerLevel level, VillageState village, MasterPlan plan, WorldTerrain terrain, VillageLayout layout) {
		int open = Math.min(MasterPlan.RINGS, village.level().rings() + 1);
		for (MasterPlan.Parcel p : plan.parcels()) {
			MasterPlan.Block block = plan.block(p.block());
			if (block == null || block.ring() > open || p.retired()) {
				continue;
			}
			boolean taken = false;
			VillageData.Job job = VillageData.get(level.getServer()).job(village.key());
			for (VillageLayout.Lot lot : layout.lots()) {
				if (lot.parcel() == p.id() && (lot.built() || job != null && job.lot == lot.id())) {
					taken = true;
					break;
				}
			}
			if (taken) {
				// obra da vila (pronta ou em andamento) no lote: nao e do jogador
				p.setPlayer(false);
				continue;
			}
			VillageState.Rect r = p.rect();
			boolean found = false;
			search:
			for (int x = r.x0(); x <= r.x1(); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					if (!plan.known(x, z) && terrain.playerBuilt(x, z)) {
						found = true;
						if (!p.player()) {
							WorldTerrain.Inspection in = terrain.inspect(x, z);
							MlinNotas.LOGGER.debug("[{}] lote {} de {} virou lote do jogador: {} em {}", MlinNotas.MOD_ID, p.id(), village.name(),
									in.block() == null ? "?" : level.getBlockState(in.block()), in.block());
						}
						break search;
					}
				}
			}
			// o jogador tirou o que tinha construido: o lote volta para a vila
			p.setPlayer(found);
		}
	}

	/** Areas e lotes de jogador nos aneis abertos com cama e porta: cada um conta como uma casa da vila. */
	static int countPlayerHouses(ServerLevel level, VillageState village, MasterPlan plan, WorldTerrain terrain) {
		List<VillageState.Rect> areas = new ArrayList<>();
		int open = village.level().rings();
		for (VillageState.Rect r : plan.playerAreas()) {
			int ring = plan.ring((r.x0() + r.x1()) / 2, (r.z0() + r.z1()) / 2);
			if (ring >= 1 && ring <= open) {
				areas.add(r);
			}
		}
		for (MasterPlan.Parcel p : plan.parcels()) {
			MasterPlan.Block block = plan.block(p.block());
			if (p.player() && block != null && block.ring() <= open) {
				areas.add(p.rect());
			}
		}
		// lote vazio da vila (fase 6): com cama e porta dentro o jogador fez a propria casa e ela conta como casa da vila
		for (VillageLayout.Lot lot : village.layout() == null ? List.<VillageLayout.Lot>of() : village.layout().lots()) {
			if (lot.built() && CraftsBuildings.EMPTY_LOT.equals(lot.kind())) {
				areas.add(lot.rect());
			}
		}
		int houses = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (VillageState.Rect r : areas) {
			int base = terrain.ground((r.x0() + r.x1()) / 2, (r.z0() + r.z1()) / 2);
			boolean bed = false;
			boolean door = false;
			for (int x = r.x0(); x <= r.x1() && !(bed && door); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					for (int y = base - 6; y <= base + 12; y++) {
						pos.set(village.x() + x, y, village.z() + z);
						if (!level.isLoaded(pos)) {
							continue;
						}
						BlockState s = level.getBlockState(pos);
						bed |= s.getBlock() instanceof BedBlock;
						door |= s.getBlock() instanceof DoorBlock;
					}
				}
			}
			if (bed && door) {
				houses++;
			}
		}
		return houses;
	}

	// ---------------------------------------------------------------- entrega

	/** Entrega para a terraplenagem do anel o que o jogador tiver do que falta. */
	public static boolean deposit(ServerPlayer player, VillageState village, int ring) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		if (!village.planned() || !fundable(village, ring)) {
			notify(player, Component.translatable("message.mlinnotas.village.terrain.locked", ring));
			return false;
		}
		RingWork work = village.ring(ring);
		if (work.state() != RingWork.State.OPEN) {
			notify(player, Component.translatable("message.mlinnotas.village.terrain.already", ring));
			return false;
		}
		refresh(level, village);
		if (work.state() == RingWork.State.DONE) {
			notify(player, Component.translatable("message.mlinnotas.village.terrain.ready_by_hand", ring));
			VillageServer.sync(server, village);
			return true;
		}
		Map<String, Integer> available = new LinkedHashMap<>();
		for (VillageCost cost : work.costs()) {
			available.put(cost.id(), ContainerHelper.clearOrCountMatchingItems(player.getInventory(), SkillServer.matcher(cost.id()), Integer.MAX_VALUE,
					true));
		}
		Map<String, Integer> taken = work.deposit(available);
		Map<String, Integer> items = new LinkedHashMap<>();
		taken.forEach((id, n) -> VillagePlanning.take(player, id, n).forEach((item, k) -> items.merge(item, k, Integer::sum)));
		work.recordPaid(items);
		if (work.paidOff()) {
			work.setState(RingWork.State.QUEUED);
			server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.terrain.funded", player.getDisplayName(),
					ring, village.name()).withStyle(ChatFormatting.GOLD), false);
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		} else if (taken.isEmpty()) {
			notify(player, Component.translatable("message.mlinnotas.village.terrain.nothing", ring));
		} else {
			notify(player, Component.translatable("message.mlinnotas.village.terrain.deposited", ring));
		}
		VillageData.get(server).changed();
		VillageServer.sync(server, village);
		return !taken.isEmpty() || work.paidOff();
	}

	private static void notify(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendOverlayMessage(message);
		}
	}

	// ---------------------------------------------------------------- obra

	/** Avanca as obras de terraplenagem e comeca a proxima paga. */
	static void tick(MinecraftServer server, VillageData data, long now) {
		for (VillageData.TerrainJob job : List.copyOf(data.terrainJobs())) {
			VillageState village = data.village(job.village);
			ServerLevel level = village == null ? null : VillageServer.levelOf(server, village);
			MasterPlan plan = village == null || village.layout() == null ? null : village.layout().master();
			if (level == null || plan == null) {
				data.endTerrainJob(job.village);
				continue;
			}
			if (step(level, village, plan, job, false)) {
				finish(server, level, village, plan, job);
			} else if (now % 20 == 0) {
				VillageServer.sync(server, village);
			}
			data.changed();
		}
		if (now % 20 == 0) {
			for (VillageState village : List.copyOf(data.villages())) {
				if (data.terrainJob(village.key()) != null || !village.planned()) {
					continue;
				}
				for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
					if (village.ring(ring).state() == RingWork.State.QUEUED) {
						ServerLevel level = VillageServer.levelOf(server, village);
						if (level != null && level.isLoaded(VillageServer.center(village))) {
							data.startTerrainJob(new VillageData.TerrainJob(village.key(), ring, 0));
							server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.terrain.started", village.name(),
									ring).withStyle(ChatFormatting.YELLOW), false);
						}
						break;
					}
				}
			}
		}
	}

	/**
	 * Aplaina colunas ate gastar o ritmo do tick ({@code instant}: tudo de uma vez). Para se a coluna estiver num chunk
	 * descarregado. Devolve true quando acabou (colunas e estacas).
	 */
	static boolean step(ServerLevel level, VillageState village, MasterPlan plan, VillageData.TerrainJob job, boolean instant) {
		List<Long> columns = columns(village, plan, job.ring);
		List<int[]> stakes = stakes(plan, job.ring);
		VillagePalette palette = VillagePalette.of(village.style());
		WorldTerrain natural = new WorldTerrain(level, VillageServer.center(village), instant);
		int budget = BLOCKS_PER_TICK;
		int looked = 0;
		RingWork work = village.ring(job.ring);
		while (job.index < columns.size() + stakes.size() && (instant || (budget > 0 && looked < COLUMNS_PER_TICK))) {
			if (job.index < columns.size()) {
				long col = columns.get(job.index);
				int x = VillageLayout.colX(col);
				int z = VillageLayout.colZ(col);
				BlockPos at = VillageBuilder.world(village, x, 0, z);
				if (!level.isLoaded(at)) {
					return false;
				}
				budget -= column(level, village, plan, x, z, palette, natural, instant, job.index);
				looked++;
			} else {
				int[] c = stakes.get(job.index - columns.size());
				stake(level, village, plan, c[0], c[1], palette);
			}
			job.index++;
			work.setProgress(Math.min(job.index, columns.size()), columns.size());
		}
		return job.index >= columns.size() + stakes.size();
	}

	/** Aplaina uma coluna; devolve quantos blocos solidos colocou. */
	static int column(ServerLevel level, VillageState village, MasterPlan plan, int x, int z, VillagePalette palette, WorldTerrain natural, boolean trees,
			int salt) {
		int wx = village.x() + x;
		int wz = village.z() + z;
		int target = plan.plannedHeight(x, z);
		if (target == Integer.MIN_VALUE) {
			return 0;
		}
		// arvores nascidas no mundo em cima da coluna caem inteiras (troncos e folhas soltas)
		int[] s = WorldTerrain.scan(level, wx, wz, trees);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = s[0] + 1; y <= s[0] + WorldTerrain.CLEARANCE; y++) {
			pos.set(wx, y, wz);
			BlockState here = level.getBlockState(pos);
			if (here.is(BlockTags.LOGS) && (trees || WorldTerrain.naturalTree(level, pos) || WorldTerrain.fallenLog(level, pos))) {
				VillageBuilder.fellTree(level, pos.immutable());
				s = WorldTerrain.scan(level, wx, wz, trees);
				break;
			}
			if (mushroom(here)) {
				fellMushroom(level, pos.immutable());
				s = WorldTerrain.scan(level, wx, wz, trees);
				break;
			}
		}
		boolean street = plan.ground(x, z);
		if (!street && s[1] == 0 && s[2] == 0) {
			// algo de jogador (ou de outra estrutura) na coluna: nunca mexe
			return 0;
		}
		List<Placement> placements = TerrainWork.column(x, z, s[0], s[2], target, street, (nx, nz) -> {
			int h = plan.plannedHeight(nx, nz);
			return h != Integer.MIN_VALUE ? h : natural.bed(nx, nz);
		}, TerrainWork.sapling(plan, x, z));
		int solids = 0;
		for (Placement p : placements) {
			BlockPos w = VillageBuilder.world(village, p.x(), p.y(), p.z());
			if (VillageBuilder.place(level, palette, w, p, salt, trees)) {
				solids++;
				if (solids <= 2) {
					VillageBuilder.effects(level, w, salt + p.y());
				}
			}
		}
		return solids;
	}

	private static boolean mushroom(BlockState state) {
		return state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK);
	}

	/** Cogumelo gigante inteiro (caule e chapeu ligados) some, para o chapeu nao ficar boiando. */
	static void fellMushroom(ServerLevel level, BlockPos start) {
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty() && seen.size() < 400) {
			BlockPos p = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				if (Math.abs(n.getX() - start.getX()) <= 6 && Math.abs(n.getZ() - start.getZ()) <= 6 && !seen.contains(n)
						&& mushroom(level.getBlockState(n))) {
					BlockPos copy = n.immutable();
					seen.add(copy);
					queue.add(copy);
				}
			}
		}
		for (BlockPos p : seen) {
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Estaca de cerca no canto de um lote ainda vazio, em cima do plato. */
	static void stake(ServerLevel level, VillageState village, MasterPlan plan, int x, int z, VillagePalette palette) {
		MasterPlan.Parcel p = plan.parcelAt(x, z);
		MasterPlan.Block block = p == null ? null : plan.block(p.block());
		if (block == null || p.player()) {
			return;
		}
		for (VillageLayout.Lot lot : village.layout().lots()) {
			if (lot.parcel() == p.id() && lot.built()) {
				return;
			}
		}
		BlockPos ground = VillageBuilder.world(village, x, block.target(), z);
		if (!level.isLoaded(ground) || level.getBlockState(ground).isAir() || !level.getBlockState(ground.above()).isAir()) {
			return;
		}
		level.setBlock(ground.above(), palette.fence.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** Tira as estacas dos cantos do lote (antes da obra comecar: a planta pode ocupar o canto). */
	public static void removeStakes(ServerLevel level, VillageState village, MasterPlan plan, MasterPlan.Parcel p) {
		MasterPlan.Block block = plan.block(p.block());
		if (block == null) {
			return;
		}
		for (int[] c : p.corners()) {
			BlockPos pos = VillageBuilder.world(village, c[0], block.target() + 1, c[1]);
			if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof FenceBlock) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	/** Anel aplainado: quarteiroes prontos, estoque de terra, mensagem e nivel. */
	static void finish(MinecraftServer server, ServerLevel level, VillageState village, MasterPlan plan, VillageData.TerrainJob job) {
		VillageData data = VillageData.get(server);
		data.endTerrainJob(village.key());
		RingWork work = village.ring(job.ring);
		plan.setRingPrepared(job.ring);
		work.setState(RingWork.State.DONE);
		village.setStockpile(Math.max(0, village.stockpile() + work.cut() - work.filled()));
		COLUMNS.keySet().removeIf(k -> k.startsWith(village.key() + "#" + job.ring + "#"));
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.terrain.done", village.name(), job.ring)
				.withStyle(ChatFormatting.GREEN), false);
		level.playSound(null, VillageServer.center(village), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5F, 1.0F);
		VillageLevel up = village.recompute();
		data.changed();
		if (up != null) {
			VillageServer.levelUp(server, village, up);
		}
		VillageServer.clearWaiting(village);
		VillageServer.sync(server, village);
	}

	/** Aplaina o anel inteiro na hora, de graca (vila inicial, testes, comando). */
	public static void prepareNow(ServerLevel level, VillageState village, int ring, boolean trees) {
		MasterPlan plan = village.layout() == null ? null : village.layout().master();
		if (plan == null) {
			return;
		}
		VillageData.TerrainJob job = new VillageData.TerrainJob(village.key(), ring, 0);
		step(level, village, plan, job, true);
		RingWork work = village.ring(ring);
		plan.setRingPrepared(ring);
		work.setState(RingWork.State.DONE);
		COLUMNS.keySet().removeIf(k -> k.startsWith(village.key() + "#" + ring + "#"));
		village.recompute();
		VillageData.get(level.getServer()).changed();
		MlinNotas.LOGGER.debug("[{}] anel {} de {} aplainado", MlinNotas.MOD_ID, ring, village.name());
	}

	/** Aplaina na hora todos os aneis abertos no nivel atual que ainda nao estao prontos (testes, comandos). */
	public static void prepareOpenRings(ServerLevel level, VillageState village, boolean trees) {
		VillagePlanning.ensureLayout(level, village);
		for (int ring = 1; ring <= village.level().rings(); ring++) {
			if (!village.ring(ring).done()) {
				prepareNow(level, village, ring, trees);
			}
		}
		VillagePlanning.grow(level, village);
	}

	// ---------------------------------------------------------------- conferencia do plato

	/**
	 * A coluna (x, z relativos) ainda esta no plato: sem agua, sem nada de jogador e o chao a no maximo 1 da altura
	 * (o acerto fino da obra cuida de 1). Mais que isso a obra para em vez de construir torto.
	 */
	public static boolean plateauOk(ServerLevel level, VillageState village, int x, int z, int target) {
		int[] s = WorldTerrain.scan(level, village.x() + x, village.z() + z, false);
		return s[2] == 0 && s[1] == 1 && Math.abs(s[0] - target) <= 1;
	}
}
