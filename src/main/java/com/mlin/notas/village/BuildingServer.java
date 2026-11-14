package com.mlin.notas.village;

import com.mlin.notas.skills.SkillServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lado servidor das construcoes com nivel (Vilas 2.0, fase 4): entregas e cancelamento de melhorias, a melhoria no
 * lugar (tira so o que a versao anterior colocou e ainda esta la, guarda o conteudo dos baus e poe nos baus da versao
 * nova, constroi aos poucos como as outras obras), as necessidades que rendem prosperidade por dia, a contagem de
 * moradores e golens, o teto de profissao pelo posto de trabalho, o conserto na forja e o quarto na estalagem.
 */
public final class BuildingServer {
	private BuildingServer() {
	}

	private static void notify(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendOverlayMessage(message);
		}
	}

	public static Component kindName(String kind) {
		VillageProject project = VillageProject.byId(kind);
		return project == null ? Component.literal(kind) : VillageServer.projectName(project);
	}

	public static Component levelNumeral(int level) {
		return Component.translatable("village.mlinnotas.building.level." + Math.max(1, Math.min(5, level)));
	}

	// ---------------------------------------------------------------- melhorias

	/** A planta do nivel seguinte cabe no lote (do plano diretor, ou no mesmo retangulo de um lote antigo). */
	public static boolean fits(VillageState village, VillageLayout.Lot lot, int level) {
		Canvas canvas = BuildingPlans.plan(lot.kind(), village.style(), lot.seed(), level);
		VillageLayout layout = village.layout();
		MasterPlan.Parcel parcel = layout.master() == null ? null : layout.master().parcel(lot.parcel());
		if (parcel != null) {
			return VillageLayout.fit(parcel, canvas.w(), canvas.d()) != null;
		}
		return canvas.w() <= lot.cw() && canvas.d() <= lot.cd();
	}

	/** Entrega para melhorar a obra do lote {@code lotId} o que o jogador tiver do que falta. */
	public static BuildingsState.Outcome deposit(ServerPlayer player, VillageState village, int lotId) {
		MinecraftServer server = player.level().getServer();
		VillageLayout layout = village.layout();
		VillageLayout.Lot lot = layout == null ? null : layout.lot(lotId);
		VillageProject project = lot == null ? null : lot.project();
		if (lot == null || !lot.built() || project == null || lot.legacy()) {
			return BuildingsState.Outcome.INVALID;
		}
		BuildingsState b = village.buildingsState();
		int from = b.level(lotId);
		int cap = VillageBuildings.cap(village, project.id());
		if (from < cap && !fits(village, lot, from + 1)) {
			notify(player, Component.translatable("message.mlinnotas.village.upgrade_no_space", kindName(lot.kind())).withStyle(ChatFormatting.RED));
			return BuildingsState.Outcome.INVALID;
		}
		Map<String, Integer> available = new LinkedHashMap<>();
		for (VillageCost cost : BuildingTypes.upgradeCost(project, from + 1)) {
			available.put(cost.id(), ContainerHelper.clearOrCountMatchingItems(player.getInventory(), SkillServer.matcher(cost.id()), Integer.MAX_VALUE,
					true));
		}
		BuildingsState.Deposit result = b.deposit(lotId, project, from, cap, available);
		Component name = Component.translatable("message.mlinnotas.village.upgrade_name", kindName(lot.kind()), levelNumeral(result.toLevel()));
		if (result.accepted()) {
			Map<String, Integer> items = new LinkedHashMap<>();
			result.taken().forEach((id, count) -> VillagePlanning.take(player, id, count).forEach((item, n) -> items.merge(item, n, Integer::sum)));
			b.recordPaid(lotId, items);
			player.getInventory().setChanged();
			VillageData.get(server).changed();
		}
		switch (result.outcome()) {
			case COMPLETE -> {
				server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.upgrade_funded", player.getDisplayName(),
						name, village.name()).withStyle(ChatFormatting.GOLD), false);
				player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			case PARTIAL -> notify(player, Component.translatable("message.mlinnotas.village.deposited", name));
			case NOTHING -> notify(player, Component.translatable("message.mlinnotas.village.nothing", name));
			case CAPPED -> notify(player, Component.translatable(project == VillageProject.TOWN_HALL ? "message.mlinnotas.village.upgrade_capped_hall"
					: "message.mlinnotas.village.upgrade_capped", name).withStyle(ChatFormatting.RED));
			case INVALID -> {
			}
		}
		VillageServer.sync(server, village);
		return result.outcome();
	}

	/** Cancela a melhoria do lote (ainda nao comecada) e devolve tudo o que foi pago. */
	public static boolean cancel(ServerPlayer player, VillageState village, BlockPos board, int lotId) {
		MinecraftServer server = player.level().getServer();
		VillageData data = VillageData.get(server);
		VillageData.Job job = data.job(village.key());
		if (job != null && job.lot == lotId && job.upgradeTo > 0) {
			notify(player, Component.translatable("message.mlinnotas.village.cancel_building").withStyle(ChatFormatting.RED));
			return false;
		}
		Map<String, Integer> paid = village.buildingsState().cancel(lotId);
		if (paid == null) {
			return false;
		}
		VillagePlanning.give(player, board, paid);
		data.changed();
		VillageLayout.Lot lot = village.layout() == null ? null : village.layout().lot(lotId);
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.upgrade_cancelled", player.getDisplayName(),
				lot == null ? Component.empty() : kindName(lot.kind()), village.name(), Refunds.total(paid)).withStyle(ChatFormatting.YELLOW), false);
		VillageServer.sync(server, village);
		return true;
	}

	/**
	 * Comeca a proxima melhoria paga: guarda o conteudo dos baus da versao anterior, tira os blocos que ela colocou e
	 * ainda estao la, encaixa a planta nova no lote e cria a obra (os baus novos ja entram cheios). Devolve a obra ou
	 * null (nada na fila, chunk descarregado, lote sumiu).
	 */
	public static VillageData.Job startUpgrade(MinecraftServer server, VillageState village) {
		BuildingsState b = village.buildingsState();
		int lotId = b.nextQueued();
		if (lotId < 0) {
			return null;
		}
		ServerLevel level = VillageServer.levelOf(server, village);
		if (level == null || !level.isLoaded(VillageServer.center(village))) {
			return null;
		}
		VillageData data = VillageData.get(server);
		VillageLayout layout = VillagePlanning.ensureLayout(level, village);
		VillageLayout.Lot lot = layout.lot(lotId);
		BuildingsState.Upgrade up = b.upgrade(lotId);
		if (lot == null || !lot.built() || lot.project() == null || up == null) {
			// obra sumiu (demolida): devolve nada aqui, so tira da fila
			b.cancel(lotId);
			data.changed();
			return null;
		}
		VillageState.Rect rect = lot.rect();
		for (int x = rect.x0(); x <= rect.x1(); x += 8) {
			for (int z = rect.z0(); z <= rect.z1(); z += 8) {
				if (!level.isLoaded(VillageBuilder.world(village, x, village.y(), z))) {
					return null;
				}
			}
		}
		int from = b.level(lotId);
		int to = up.toLevel();
		Canvas canvas = BuildingPlans.plan(lot.kind(), village.style(), lot.seed(), to);
		// 1) conteudo dos baus da versao anterior
		List<ItemStack> stash = new ArrayList<>();
		VillageData.BuildRecord old = data.record(village.key(), lotId);
		if (old != null) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int i = 0; i < old.size(); i++) {
				pos.set(BlockPos.getX(old.positions[i]), BlockPos.getY(old.positions[i]), BlockPos.getZ(old.positions[i]));
				BlockEntity be = level.getBlockEntity(pos);
				if (be instanceof Container container && BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString().equals(old.block(i))) {
					for (int s = 0; s < container.getContainerSize(); s++) {
						ItemStack stack = container.getItem(s);
						if (!stack.isEmpty()) {
							stash.add(stack.copy());
						}
					}
					container.clearContent();
				}
			}
			// 2) tira o que a versao anterior colocou e ainda esta la (blocos de jogador ficam)
			VillageBuilder.demolish(level, village, old);
			data.removeRecord(village.key(), lotId);
		}
		// 3) planta nova no lote (encaixada de novo no lote do plano diretor)
		if (!layout.resize(lot, canvas.w(), canvas.d(), canvas.doorX())) {
			canvas = BuildingPlans.plan(lot.kind(), village.style(), lot.seed(), from);
		}
		VillagePlanner.Planned planned = VillagePlanner.onLot(lot.project(), lot, village, new WorldTerrain(level, VillageServer.center(village)), canvas);
		int newcomers = BuildingTypes.newResidents(lot.kind(), from, to);
		planned = new VillagePlanner.Planned(planned.project(), planned.lot(), planned.placements(),
				planned.spawns().subList(0, Math.min(newcomers, planned.spawns().size())), List.of(), planned.lotId());
		VillageData.Job job = VillageData.Job.create(village.key(), planned);
		job.upgradeTo = to;
		// 4) baus novos ja no lugar, com o conteudo antigo
		VillagePalette palette = VillagePalette.of(village.style(), job.variant());
		List<BlockPos> chests = new ArrayList<>();
		for (int i = 0; i < job.total(); i++) {
			Placement p = job.placement(i);
			if (p.part() == VPart.CHEST || (p.part() == VPart.BARREL && !stash.isEmpty())) {
				BlockPos pos = VillageBuilder.world(village, p.x(), p.y(), p.z());
				if (VillageBuilder.place(level, palette, pos, p, i)) {
					job.record(pos.asLong(), BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
					chests.add(pos);
				}
			}
		}
		refill(level, chests, stash, VillageBuilder.world(village, rect.x0() + rect.x1() >> 1, planned.lot().groundY() + 1, rect.z0() + rect.z1() >> 1));
		data.startJob(job);
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.upgrading", village.name(),
				kindName(lot.kind()), levelNumeral(to)).withStyle(ChatFormatting.YELLOW), false);
		VillageServer.sync(server, village);
		return job;
	}

	/** Poe o conteudo guardado nos baus novos, na ordem; o que nao couber cai no chao do lote. */
	static void refill(ServerLevel level, List<BlockPos> containers, List<ItemStack> stash, BlockPos fallback) {
		for (ItemStack stack : stash) {
			ItemStack left = stack;
			for (BlockPos pos : containers) {
				if (left.isEmpty()) {
					break;
				}
				if (level.getBlockEntity(pos) instanceof Container container) {
					for (int s = 0; s < container.getContainerSize() && !left.isEmpty(); s++) {
						ItemStack here = container.getItem(s);
						if (here.isEmpty()) {
							container.setItem(s, left.copy());
							left = ItemStack.EMPTY;
						} else if (ItemStack.isSameItemSameComponents(here, left) && here.getCount() < here.getMaxStackSize()) {
							int move = Math.min(left.getCount(), here.getMaxStackSize() - here.getCount());
							here.grow(move);
							left.shrink(move);
						}
					}
					container.setChanged();
				}
			}
			if (!left.isEmpty()) {
				level.addFreshEntity(new ItemEntity(level, fallback.getX() + 0.5, fallback.getY() + 0.5, fallback.getZ() + 0.5, left));
			}
		}
	}

	/** Prosperidade de uma melhoria: metade da obra vezes o nivel alvo. */
	public static int upgradeProsperity(VillageProject project, int toLevel) {
		return (int) Math.ceil(project.prosperity() * toLevel / 2.0);
	}

	/** A melhoria terminou: o lote sobe de nivel, guarda os blocos novos, chegam os moradores novos, soma prosperidade. */
	public static void finishUpgrade(MinecraftServer server, ServerLevel level, VillageState village, VillageData.Job job) {
		VillageData data = VillageData.get(server);
		data.endJob(village.key());
		VillageBuilder.spawnResidents(level, village, job);
		VillageLayout.Lot lot = village.layout() == null ? null : village.layout().lot(job.lot);
		if (lot != null) {
			lot.setBuilt(true);
			data.putRecord(village.key(), lot.id(), job.buildRecord());
		}
		village.buildingsState().finish(job.lot);
		VillageLevel up = village.addProsperity(upgradeProsperity(job.project, job.upgradeTo));
		data.changed();
		level.playSound(null, VillageServer.center(village), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5F, 1.0F);
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.upgraded", village.name(),
				VillageServer.projectName(job.project), levelNumeral(job.upgradeTo), upgradeProsperity(job.project, job.upgradeTo))
				.withStyle(ChatFormatting.GREEN), false);
		if (up != null) {
			VillageServer.levelUp(server, village, up);
		}
		VillageServer.sync(server, village);
	}

	/** Termina na hora toda melhoria paga da vila (testes, comandos). */
	public static void completeUpgrades(MinecraftServer server, VillageState village) {
		ServerLevel level = VillageServer.levelOf(server, village);
		VillageData data = VillageData.get(server);
		for (int guard = 0; guard < 16 && level != null; guard++) {
			VillageData.Job job = data.job(village.key());
			if (job == null) {
				job = startUpgrade(server, village);
				if (job == null) {
					return;
				}
			}
			VillageBuilder.runJob(level, village, job, false);
			if (job.upgradeTo > 0) {
				finishUpgrade(server, level, village, job);
			} else {
				return;
			}
		}
	}

	// ---------------------------------------------------------------- tick: comecar melhorias, necessidades do dia

	public static void tick(MinecraftServer server, VillageData data, long now) {
		if (now % 20 != 0) {
			return;
		}
		long day = VillageServer.day(server);
		for (VillageState village : List.copyOf(data.villages())) {
			if (data.job(village.key()) == null && village.buildingsState().nextQueued() >= 0
					&& (village.queue().isEmpty() || VillageServer.waiting(village))) {
				startUpgrade(server, village);
			}
			if (now % 100 == 0) {
				dailyNeeds(server, village, day);
			}
		}
	}

	/** Conta aldeoes e golens perto do centro (so com o centro carregado). */
	public static void count(ServerLevel level, VillageState village) {
		BlockPos center = VillageServer.center(village);
		if (!level.isLoaded(center)) {
			return;
		}
		int r = village.level().radius() + VillageServer.RESIDENT_MARGIN;
		AABB box = new AABB(center).inflate(r, 48, r);
		int villagers = level.getEntitiesOfClass(Villager.class, box).size();
		int golems = level.getEntitiesOfClass(IronGolem.class, box).size();
		village.buildingsState().setCounts(villagers, golems);
	}

	/**
	 * Uma vez por dia de jogo, com Prefeitura: conta moradores e golens e soma a prosperidade das necessidades (+2
	 * por necessidade acima de 75%, -1 abaixo de 25%; o nivel nunca desce). O primeiro dia so marca a data.
	 */
	public static void dailyNeeds(MinecraftServer server, VillageState village, long day) {
		BuildingsState b = village.buildingsState();
		if (b.needsDay() == day) {
			return;
		}
		ServerLevel level = VillageServer.levelOf(server, village);
		if (level == null || !level.isLoaded(VillageServer.center(village))) {
			return;
		}
		count(level, village);
		long last = b.needsDay();
		b.setNeedsDay(day);
		VillageData.get(server).changed();
		if (last == Long.MIN_VALUE || !VillageBuildings.needsActive(village)) {
			return;
		}
		int delta = VillageNeeds.dailyProsperity(VillageNeeds.values(village));
		if (delta != 0) {
			VillageLevel up = village.addProsperity(delta);
			if (up != null) {
				VillageServer.levelUp(server, village, up);
			}
		}
		VillageServer.sync(server, village);
	}

	/** Aplica um dia de necessidades agora (testes, comando): devolve a prosperidade somada. */
	public static int applyNeedsNow(MinecraftServer server, VillageState village) {
		int before = village.prosperity();
		VillageBuildings.needsActive(village);
		int delta = VillageNeeds.dailyProsperity(VillageNeeds.values(village));
		VillageLevel up = village.addProsperity(delta);
		if (up != null) {
			VillageServer.levelUp(server, village, up);
		}
		VillageData.get(server).changed();
		return village.prosperity() - before;
	}

	// ---------------------------------------------------------------- aldeoes

	/**
	 * Teto de profissao do aldeao: fora de vila fundada o jornaleiro; numa vila, o teto do nivel da vila, e acima de
	 * jornaleiro so se o posto de trabalho estiver numa construcao funcionando no nivel (mestre so no nivel V).
	 */
	public static int professionCap(ServerLevel level, Villager villager) {
		VillageState village = VillageServer.villageAt(level, villager.blockPosition());
		GlobalPos job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (village == null && job != null && job.dimension() == level.dimension()) {
			// posto numa obra dos arredores (alem do raio de moradores): vale a vila dona do plano
			VillageState owner = VillageServer.planVillageAt(level, job.pos());
			if (owner != null && owner.layout() != null) {
				VillageLayout.Lot lot = owner.layout().lotAt(job.pos().getX() - owner.x(), job.pos().getZ() - owner.z());
				village = lot != null && lot.built() ? owner : null;
			}
		}
		if (village == null) {
			return VillageLevel.WILD_TRADE_CAP;
		}
		int lotId = -1;
		GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (site != null && site.dimension() == level.dimension() && village.layout() != null) {
			BlockPos p = site.pos();
			VillageLayout.Lot lot = village.layout().lotAt(p.getX() - village.x(), p.getZ() - village.z());
			if (lot != null && lot.built()) {
				lotId = lot.id();
			}
		}
		return VillageBuildings.professionCap(village, lotId);
	}

	// ---------------------------------------------------------------- mercado: forja e estalagem

	/** Conserto na forja: esmeraldas + material de conserto do item, desconto pelo nivel da forja. */
	public static boolean forgeRepair(ServerPlayer player, VillageState village) {
		ItemStack hand = player.getMainHandItem();
		int smithy = VillageBuildings.level(village, "smithy");
		if (smithy < 1) {
			notify(player, Component.translatable("message.mlinnotas.village.needs_building", kindName("smithy"), levelNumeral(1))
					.withStyle(ChatFormatting.RED));
			return false;
		}
		int[] price = VillageShop.forgeRepair(hand.getDamageValue(), hand.getMaxDamage(), smithy);
		if (!hand.isDamageableItem() || price[0] == 0) {
			notify(player, Component.translatable("message.mlinnotas.village.nothing_to_repair"));
			return false;
		}
		Repairable repairable = hand.get(DataComponents.REPAIRABLE);
		int material = repairable == null ? 0 : price[1];
		int emeralds = price[0] + (repairable == null ? 2 : 0);
		var matcher = repairable == null ? (java.util.function.Predicate<ItemStack>) s -> false : (java.util.function.Predicate<ItemStack>)
				s -> s != hand && !s.isEmpty() && repairable.isValidRepairItem(s);
		int haveEmeralds = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), Integer.MAX_VALUE, true);
		int haveMaterial = material == 0 ? 0 : ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher, Integer.MAX_VALUE, true);
		if (haveEmeralds < emeralds || haveMaterial < material) {
			notify(player, Component.translatable("message.mlinnotas.village.repair_cost", emeralds, material).withStyle(ChatFormatting.RED));
			return false;
		}
		ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), emeralds, false);
		if (material > 0) {
			ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher, material, false);
		}
		hand.setDamageValue(0);
		player.getInventory().setChanged();
		player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
		notify(player, Component.translatable("message.mlinnotas.village.repaired", emeralds, material).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Lugar de dormir da estalagem funcionando de nivel II+ (pes do primeiro morador do andar) ou null. */
	public static BlockPos innRoom(MinecraftServer server, VillageState village) {
		VillageLayout layout = village.layout();
		if (layout == null) {
			return null;
		}
		VillageData data = VillageData.get(server);
		for (VillageBuildings.Building b : VillageBuildings.working(village)) {
			if (!b.kind().equals("inn") || b.level() < 2 || b.lot() < 0) {
				continue;
			}
			VillageLayout.Lot lot = layout.lot(b.lot());
			VillageData.BuildRecord record = data.record(village.key(), b.lot());
			if (lot == null || record == null) {
				continue;
			}
			Canvas canvas = BuildingPlans.plan(lot.kind(), village.style(), lot.seed(), b.level());
			List<int[]> spawns = canvas.spawns();
			int[] s = spawns.get(spawns.size() - 1);
			int[] r = VillagePlanner.rotate(s[0], s[2], canvas.w(), canvas.d(), lot.rot());
			return VillageBuilder.world(village, lot.x() + r[0], record.ground + s[1], lot.z() + r[1]);
		}
		return null;
	}

	/** Quarto na estalagem: o ponto de renascer do jogador passa a ser o andar dos quartos. */
	public static boolean lodge(ServerPlayer player, VillageState village) {
		BlockPos room = innRoom(player.level().getServer(), village);
		if (room == null) {
			notify(player, Component.translatable("message.mlinnotas.village.needs_building", kindName("inn"), levelNumeral(2))
					.withStyle(ChatFormatting.RED));
			return false;
		}
		ServerLevel level = player.level();
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), room, player.getYRot(), 0.0F), true),
				false);
		notify(player, Component.translatable("message.mlinnotas.village.lodged", room.getX(), room.getY(), room.getZ())
				.withStyle(ChatFormatting.GREEN));
		return true;
	}
}
