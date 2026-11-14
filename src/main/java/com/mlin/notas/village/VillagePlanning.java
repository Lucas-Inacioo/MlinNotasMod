package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.skills.SkillServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Lado servidor do plano da vila: calcula/estende o plano, manda o contorno para os jogadores (ao fundar, ao subir de
 * nivel e pelo botao "Mostrar area"), aceita a escolha de lugar do jogador, cancela obras da fila e demole obras
 * prontas, sempre devolvendo os itens.
 */
public final class VillagePlanning {
	/** Segundos que o contorno fica no mundo depois de fundar/subir de nivel. */
	public static final int AUTO_SECONDS = 30;
	/** Segundos do contorno pedido pelo botao (ate desligar). */
	public static final int TOGGLE_SECONDS = 600;
	public static final int MAX_OBSTACLES = 400;
	/** Jogadores ate esta distancia do quadro veem o contorno automatico. */
	public static final int SHOW_RANGE = 96;

	private VillagePlanning() {
	}

	// ---------------------------------------------------------------- plano

	/** Plano da vila (calcula se ainda nao existe: vila nova ou de antes do plano). */
	public static VillageLayout ensureLayout(ServerLevel level, VillageState village) {
		if (village.layout() == null || village.layout().master() == null) {
			WorldTerrain terrain = new WorldTerrain(level, VillageServer.center(village));
			boolean migrate = village.layout() != null;
			if (migrate) {
				// vila de antes do plano diretor: as obras prontas ficam, o resto passa para o plano novo
				village.layout().migrate(village, terrain);
			} else {
				village.setLayout(VillageLayout.create(village, terrain));
			}
			VillageLayout layout = village.layout();
			VillageData.get(level.getServer()).changed();
			MlinNotas.LOGGER.info("[{}] plano diretor da vila {}{}: {} quarteiroes, {} lotes do plano, {} reservados, {} postes", MlinNotas.MOD_ID,
					village.name(), migrate ? " (plano antigo convertido)" : "", layout.master().blocks().size(), layout.master().parcels().size(),
					layout.lots().size(), layout.lamps().size());
			VillageTerrain.refresh(level, village);
		}
		if (village.layout().master() != null && village.layout().master().version() < MasterPlan.VERSION) {
			upgradePlan(level, village, village.layout());
		}
		if (village.layout().needsRural(village.level())) {
			// vila que ja era Cidade antes do anel rural: o plano ganha os campos fora da muralha externa
			VillageLayout layout = village.layout();
			int before = layout.master().parcels().size();
			layout.extend(village, new WorldTerrain(level, VillageServer.center(village)), village.level());
			VillageData.get(level.getServer()).changed();
			MlinNotas.LOGGER.info("[{}] vila {} ganhou o anel rural: {} lotes novos no plano, {} quarteiroes no total", MlinNotas.MOD_ID, village.name(),
					layout.master().parcels().size() - before, layout.master().blocks().size());
			level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.rural_open", village.name())
					.withStyle(net.minecraft.ChatFormatting.GOLD), false);
		}
		clearRetired(level, village);
		return village.layout();
	}

	/**
	 * Plano salvo antes da subdivisao densa: refaz so os quarteiroes sem lote reservado e preenche o espaco que sobrou
	 * nos outros ({@link VillageLayout#densifyMaster}); as estacas dos lotes aposentados saem do mundo (carregando o
	 * chunk se preciso, para nao sobrar cerca solta que pareceria construcao de jogador).
	 */
	static void upgradePlan(ServerLevel level, VillageState village, VillageLayout layout) {
		List<MasterPlan.Parcel> retired = layout.densifyMaster();
		MasterPlan plan = layout.master();
		int removed = 0;
		for (MasterPlan.Parcel p : retired) {
			MasterPlan.Block block = plan.block(p.block());
			if (block == null) {
				continue;
			}
			for (int[] c : p.corners()) {
				BlockPos pos = VillageBuilder.world(village, c[0], block.target() + 1, c[1]);
				level.getChunk(pos);
				if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
					level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
					removed++;
				}
			}
		}
		VillageData.get(level.getServer()).changed();
		MlinNotas.LOGGER.info("[{}] plano da vila {} passou para a subdivisao densa: {} lotes aposentados, {} estacas tiradas, {} lotes no plano", MlinNotas.MOD_ID,
				village.name(), retired.size(), removed, plan.parcels().size());
	}

	/**
	 * Lotes livres do plano aposentados por recortes sob demanda ({@link VillageLayout#drainRetired}): as estacas da
	 * terraplenagem nos cantos e o que as obras publicas puseram neles saem do mundo (carregando o chunk se preciso, para
	 * nao sobrar cerca solta que pareceria construcao de jogador).
	 */
	public static void clearRetired(ServerLevel level, VillageState village) {
		VillageLayout layout = village.layout();
		if (layout == null || layout.master() == null) {
			return;
		}
		List<MasterPlan.Parcel> retired = layout.drainRetired();
		if (retired.isEmpty()) {
			return;
		}
		int removed = removeCornerStakes(level, village, layout.master(), retired);
		for (MasterPlan.Parcel p : retired) {
			com.mlin.notas.village.works.VillageWorks.releaseArea(level, village, p.rect());
		}
		VillageData.get(level.getServer()).changed();
		MlinNotas.LOGGER.debug("[{}] vila {}: {} lotes livres recortados, {} estacas tiradas", MlinNotas.MOD_ID, village.name(), retired.size(), removed);
	}

	private static int removeCornerStakes(ServerLevel level, VillageState village, MasterPlan plan, List<MasterPlan.Parcel> parcels) {
		int removed = 0;
		for (MasterPlan.Parcel p : parcels) {
			MasterPlan.Block block = plan.block(p.block());
			if (block == null) {
				continue;
			}
			for (int[] c : p.corners()) {
				// a quina pode ter virado lote novo: so tira a estaca se ela nao for canto de um lote vivo
				MasterPlan.Parcel now = plan.parcelAt(c[0], c[1]);
				if (now != null && now.corners().stream().anyMatch(k -> k[0] == c[0] && k[1] == c[1])) {
					continue;
				}
				BlockPos pos = VillageBuilder.world(village, c[0], block.target() + 1, c[1]);
				level.getChunk(pos);
				if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
					level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
					removed++;
				}
			}
		}
		return removed;
	}

	/** A vila subiu de nivel: o plano abre o proximo anel (lotes novos mais longe, os antigos ficam). */
	public static void grow(ServerLevel level, VillageState village) {
		VillageLayout layout = ensureLayout(level, village);
		layout.extend(village, new WorldTerrain(level, VillageServer.center(village)), village.level());
		clearRetired(level, village);
		VillageTerrain.refresh(level, village);
		VillageData.get(level.getServer()).changed();
	}

	// ---------------------------------------------------------------- contorno

	/** Manda o contorno para todo jogador perto do quadro, com mensagem explicando. */
	public static void showNearby(ServerLevel level, VillageState village, int seconds, Component message) {
		BlockPos center = VillageServer.center(village);
		VillagePayloads.Overlay overlay = null;
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(center) > (double) SHOW_RANGE * SHOW_RANGE) {
				continue;
			}
			if (overlay == null) {
				overlay = overlay(level, village, seconds);
			}
			send(player, overlay);
			if (message != null && player.connection != null) {
				player.sendSystemMessage(message);
			}
		}
	}

	public static void show(ServerPlayer player, VillageState village, int seconds) {
		send(player, overlay(player.level(), village, seconds));
	}

	private static void send(ServerPlayer player, VillagePayloads.Overlay overlay) {
		if (player.connection != null && ServerPlayNetworking.canSend(player, VillagePayloads.Overlay.TYPE)) {
			ServerPlayNetworking.send(player, overlay);
		}
	}

	/** Situacao de cada lote e os blocos que atrapalham, lidos do mundo agora. */
	public static VillagePayloads.Overlay overlay(ServerLevel level, VillageState village, int seconds) {
		VillageLayout layout = ensureLayout(level, village);
		WorldTerrain terrain = new WorldTerrain(level, VillageServer.center(village));
		VillageData.Job job = VillageData.get(level.getServer()).job(village.key());
		List<VillagePayloads.LotInfo> infos = new ArrayList<>();
		List<Long> obstacles = new ArrayList<>();
		List<Integer> kinds = new ArrayList<>();
		MasterPlan master = layout.master();
		for (VillageLayout.Lot lot : layout.lots()) {
			VillageState.Rect r = lot.rect();
			MasterPlan.Parcel parcel = master == null ? null : master.parcel(lot.parcel());
			MasterPlan.Block block = parcel == null ? null : master.block(parcel.block());
			int ground = block != null ? block.target() : TerrainPrep.target(terrain, r);
			VillagePayloads.LotStatus status;
			if (job != null && job.lot == lot.id()) {
				status = job.paused() ? VillagePayloads.LotStatus.PAUSED : VillagePayloads.LotStatus.BUILDING;
				ground = job.ground;
				if (job.paused() && obstacles.size() < MAX_OBSTACLES) {
					int px = VillageLayout.colX(job.pausedAt);
					int pz = VillageLayout.colZ(job.pausedAt);
					obstacles.add(VillageBuilder.world(village, px, terrain.ground(px, pz), pz).asLong());
					kinds.add(WorldTerrain.Obstacle.PLAYER.ordinal());
				}
			} else if (lot.built()) {
				status = VillagePayloads.LotStatus.BUILT;
			} else if (parcel != null && parcel.player()) {
				status = VillagePayloads.LotStatus.PLAYER;
			} else if (block != null && !block.prepared()) {
				status = VillagePayloads.LotStatus.UNPREPARED;
			} else {
				status = inspectLot(terrain, lot, obstacles, kinds, master);
				if (block != null && status == VillagePayloads.LotStatus.STEEP) {
					status = VillagePayloads.LotStatus.FREE;
				}
			}
			infos.add(new VillagePayloads.LotInfo(lot.id(), status.ordinal(), ground));
		}
		if (master != null && master.rural() && village.level().ordinal() >= VillageProject.OUTER_WALL.minLevel().ordinal() && village.built(VillageProject.OUTER_WALL) == 0) {
			// muralha externa: vaos onde ha construcao de jogador ou agua funda na linha (a obra deixa o trecho aberto)
			int n = 0;
			for (int[] b : OuterWallPlanner.breaches(master, terrain)) {
				if (obstacles.size() >= MAX_OBSTACLES) {
					break;
				}
				if (n++ % 2 == 0) {
					obstacles.add(VillageBuilder.world(village, b[0], terrain.ground(b[0], b[1]), b[1]).asLong());
					kinds.add((b[2] == 1 ? WorldTerrain.Obstacle.WATER : WorldTerrain.Obstacle.PLAYER).ordinal());
				}
			}
		}
		int rings = village.level().rings() | (village.ringsDone() << 4);
		return new VillagePayloads.Overlay(village.key(), VillageServer.center(village), layout, infos, obstacles, kinds, seconds, rings);
	}

	/** Pior obstaculo do lote; anota os blocos a destacar. */
	static VillagePayloads.LotStatus inspectLot(WorldTerrain terrain, VillageLayout.Lot lot, List<Long> obstacles, List<Integer> kinds, MasterPlan master) {
		VillageState.Rect r = lot.rect();
		boolean tree = false;
		boolean player = false;
		boolean water = false;
		for (int x = r.x0() - 1; x <= r.x1() + 1; x++) {
			for (int z = r.z0() - 1; z <= r.z1() + 1; z++) {
				boolean inside = r.contains(x, z);
				if (master != null && (master.street(x, z) || master.parcelAt(x, z) != null && master.known(x, z))) {
					// rua do plano e estacas da terraplenagem sao da vila
					continue;
				}
				WorldTerrain.Inspection in = terrain.inspect(x, z);
				if (in.obstacle() == WorldTerrain.Obstacle.NONE || (!inside && in.obstacle() != WorldTerrain.Obstacle.TREE)) {
					continue;
				}
				switch (in.obstacle()) {
					case TREE -> tree = true;
					case PLAYER -> player = true;
					case WATER -> water = true;
					default -> {
					}
				}
				if (obstacles.size() < MAX_OBSTACLES) {
					obstacles.add(in.block().asLong());
					kinds.add(in.obstacle().ordinal());
				}
			}
		}
		if (player) {
			return VillagePayloads.LotStatus.BLOCKED;
		}
		if (water) {
			return VillagePayloads.LotStatus.WATER;
		}
		if (VillageLayout.terrain(terrain, r.x0(), r.z0(), lot.w(), lot.d()) == VillageLayout.Problem.STEEP) {
			return VillagePayloads.LotStatus.STEEP;
		}
		return tree ? VillagePayloads.LotStatus.CLEAR : VillagePayloads.LotStatus.FREE;
	}

	// ---------------------------------------------------------------- escolha de lugar

	/** O jogador escolheu um lote ja planejado ({@code arg} = id). */
	public static boolean pick(ServerPlayer player, VillageState village, String arg) {
		VillageLayout layout = ensureLayout(player.level(), village);
		int id;
		try {
			id = Integer.parseInt(arg.trim());
		} catch (NumberFormatException e) {
			return false;
		}
		VillageLayout.Lot lot = layout.lot(id);
		if (lot == null || lot.built() || !layout.prefer(id)) {
			notify(player, Component.translatable("message.mlinnotas.village.place_invalid").withStyle(ChatFormatting.RED));
			return false;
		}
		VillageData.get(player.level().getServer()).changed();
		notify(player, Component.translatable("message.mlinnotas.village.place_picked", projectName(lot.kind())).withStyle(ChatFormatting.GREEN));
		show(player, village, AUTO_SECONDS);
		VillageServer.sync(player.level().getServer(), village);
		return true;
	}

	/** O jogador escolheu um lugar novo ({@code arg} = tipo;x;z;giro, x/z do canto relativos ao quadro). */
	public static VillageLayout.Problem place(ServerPlayer player, VillageState village, String arg) {
		String[] parts = arg.split(";");
		if (parts.length != 4) {
			return VillageLayout.Problem.OVERLAP;
		}
		String kind = parts[0];
		int x;
		int z;
		int rot;
		try {
			x = Integer.parseInt(parts[1]);
			z = Integer.parseInt(parts[2]);
			rot = Integer.parseInt(parts[3]);
		} catch (NumberFormatException e) {
			return VillageLayout.Problem.OVERLAP;
		}
		ServerLevel level = player.level();
		VillageLayout layout = ensureLayout(level, village);
		VillageLayout.Problem problem = layout.relocate(kind, x, z, rot, new WorldTerrain(level, VillageServer.center(village)));
		if (problem != null) {
			notify(player, Component.translatable("message.mlinnotas.village.place_problem." + problem.name().toLowerCase(java.util.Locale.ROOT))
					.withStyle(ChatFormatting.RED));
		} else {
			VillageData.get(level.getServer()).changed();
			notify(player, Component.translatable("message.mlinnotas.village.place_moved", projectName(kind)).withStyle(ChatFormatting.GREEN));
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
			VillageServer.sync(level.getServer(), village);
		}
		show(player, village, AUTO_SECONDS);
		return problem;
	}

	private static Component projectName(String kind) {
		VillageProject project = VillageProject.byId(kind);
		return project == null ? Component.translatable("village.mlinnotas.project.campfire") : VillageServer.projectName(project);
	}

	private static void notify(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendSystemMessage(message);
		}
	}

	// ---------------------------------------------------------------- cancelar e demolir

	/** Item que representa uma tag na devolucao (o primeiro da tag). */
	static String representative(String costId) {
		if (!costId.startsWith("#")) {
			return costId;
		}
		TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(costId.substring(1)));
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
			return BuiltInRegistries.ITEM.getKey(holder.value()).toString();
		}
		return null;
	}

	/** Entrega itens ao jogador (o que nao couber cai no quadro). */
	static void give(ServerPlayer player, BlockPos board, Map<String, Integer> items) {
		items.forEach((id, count) -> {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
			if (item == null || item == Items.AIR) {
				return;
			}
			int left = count;
			while (left > 0) {
				int n = Math.min(left, item.getDefaultMaxStackSize());
				ItemStack stack = new ItemStack(item, n);
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					Block.popResource(player.level(), board.above(), stack);
				}
				left -= n;
			}
		});
	}

	/**
	 * Tira do inventario ate {@code count} itens que casam com o custo e devolve o que tirou (id do item -> quantidade),
	 * para a devolucao ser dos mesmos itens.
	 */
	public static Map<String, Integer> take(ServerPlayer player, String costId, int count) {
		Predicate<ItemStack> matcher = SkillServer.matcher(costId);
		Map<String, Integer> out = new LinkedHashMap<>();
		Inventory inventory = player.getInventory();
		int left = count;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!matcher.test(stack)) {
				continue;
			}
			int n = Math.min(left, stack.getCount());
			out.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), n, Integer::sum);
			stack.shrink(n);
			left -= n;
		}
		inventory.setChanged();
		return out;
	}

	/** Cancela a obra {@code index} da fila (nao a que esta sendo construida) e devolve tudo o que foi pago. */
	public static boolean cancel(ServerPlayer player, VillageState village, BlockPos board, int index) {
		MinecraftServer server = player.level().getServer();
		VillageData data = VillageData.get(server);
		if (index == 0 && data.job(village.key()) != null) {
			notify(player, Component.translatable("message.mlinnotas.village.cancel_building").withStyle(ChatFormatting.RED));
			return false;
		}
		Map.Entry<VillageProject, Map<String, Integer>> entry = village.cancelQueued(index);
		if (entry == null) {
			return false;
		}
		Map<String, Integer> refund = Refunds.of(entry.getKey(), entry.getValue(), 1.0, VillagePlanning::representative);
		give(player, board, refund);
		data.changed();
		VillageServer.clearWaiting(village);
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.cancelled", player.getDisplayName(),
				VillageServer.projectName(entry.getKey()), village.name(), Refunds.total(refund)).withStyle(ChatFormatting.YELLOW), false);
		VillageServer.sync(server, village);
		return true;
	}

	/**
	 * Demole a obra do lote {@code lotId}: tira os blocos que a vila colocou (so os que ainda estao la), devolve o chao,
	 * libera o lote, conta uma obra a menos e devolve metade do custo. Aldeoes ficam.
	 */
	public static boolean demolish(ServerPlayer player, VillageState village, BlockPos board, int lotId) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		VillageData data = VillageData.get(server);
		VillageLayout layout = ensureLayout(level, village);
		VillageLayout.Lot lot = layout.lot(lotId);
		VillageData.Job job = data.job(village.key());
		if (lot == null || !lot.built() || (job != null && job.lot == lotId)) {
			return false;
		}
		VillageData.BuildRecord record = data.record(village.key(), lotId);
		if (record == null || lot.legacy()) {
			notify(player, Component.translatable("message.mlinnotas.village.demolish_legacy").withStyle(ChatFormatting.RED));
			return false;
		}
		int removed = VillageBuilder.demolish(level, village, record);
		data.removeRecord(village.key(), lotId);
		// nivel e melhoria aberta do lote somem (o que foi pago na melhoria volta inteiro)
		Map<String, Integer> upgradePaid = village.buildingsState().cancel(lotId);
		if (upgradePaid != null) {
			give(player, board, upgradePaid);
		}
		village.buildingsState().forget(lotId);
		lot.setBuilt(false);
		lot.setPreferred(false);
		VillageProject project = lot.project();
		Map<String, Integer> refund = Map.of();
		if (project != null) {
			village.unbuild(project);
			refund = Refunds.of(project, lot.paid(), Refunds.DEMOLISH_FRACTION, VillagePlanning::representative);
			give(player, board, refund);
		}
		lot.setPaid(Map.of());
		village.removeLot(lot.rect());
		data.changed();
		level.playSound(null, VillageServer.center(village), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.2F);
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.demolished", player.getDisplayName(),
				projectName(lot.kind()), village.name(), removed, Refunds.total(refund)).withStyle(ChatFormatting.YELLOW), false);
		VillageServer.sync(server, village);
		return true;
	}

	/** Semente estavel para os sorteios do plano. */
	static Random random(VillageState village, long salt) {
		return new Random(village.seed() ^ salt);
	}
}
