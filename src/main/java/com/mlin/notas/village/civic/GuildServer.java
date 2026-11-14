package com.mlin.notas.village.civic;

import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Lado servidor da Guilda dos Exploradores. Agachado + botao direito no quadro troca o destino (e arma a viagem); botao
 * direito abre o equipamento do explorador (ferramenta, arma, quatro pecas de armadura e comida na primeira fileira, o que
 * voltou nas outras duas) e ao fechar, com destino armado, um aldeao da vila parte levando o equipamento. Volta em 1 a 3
 * dias de jogo com o que as regras ({@link ExpeditionRules}) sortearam e o equipamento gasto; o aldeao e guardado inteiro
 * (comercio e nome) e reaparece junto do quadro.
 */
public final class GuildServer {
	/** Primeira fileira: ferramenta, arma, capacete, peitoral, calca, botas, comida. */
	public static final int GEAR_SLOTS = 7;
	private static final int RETURN_SLOTS = 18;
	private static final Map<UUID, ExpeditionRules.Destination> SELECTED = new HashMap<>();

	private GuildServer() {
	}

	public static void clear() {
		SELECTED.clear();
	}

	public static ExpeditionRules.Destination selected(UUID player) {
		return SELECTED.getOrDefault(player, ExpeditionRules.Destination.MINE);
	}

	public static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		VillageState village = CivicSites.villageAt(server, level, pos, 8);
		CivicSites.Site site = null;
		if (village != null) {
			for (CivicSites.Site s : CivicSites.working(server, village, CivicBuildings.GUILD)) {
				if (s.lot().rect().contains(pos.getX() - village.x(), pos.getZ() - village.z())) {
					site = s;
				}
			}
		}
		if (site == null) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.civic.guild.inactive").withStyle(ChatFormatting.RED));
			return;
		}
		List<ExpeditionRules.Destination> options = ExpeditionRules.destinations(CivicData.get(server).visited());
		if (player.isShiftKeyDown()) {
			ExpeditionRules.Destination current = selected(player.getUUID());
			ExpeditionRules.Destination next = options.get((Math.max(0, options.indexOf(current)) + 1) % options.size());
			SELECTED.put(player.getUUID(), next);
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.civic.guild.dest", Component.translatable("guild.mlinnotas.dest." + next.id()))
					.withStyle(ChatFormatting.GREEN));
			return;
		}
		open(player, village, site);
	}

	private static boolean armed(UUID player) {
		return SELECTED.containsKey(player);
	}

	public static void open(ServerPlayer player, VillageState village, CivicSites.Site site) {
		MinecraftServer server = player.level().getServer();
		GuildContainer container = new GuildContainer(server, village, site, player);
		ExpeditionRules.Destination dest = armed(player.getUUID()) ? selected(player.getUUID()) : null;
		Component title = dest == null ? Component.translatable("container.mlinnotas.guild.idle")
				: Component.translatable("container.mlinnotas.guild", Component.translatable("guild.mlinnotas.dest." + dest.id()));
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GuildMenu(id, inventory, container), title));
	}

	/** Primeira fileira: equipamento. Nas outras duas: o que as expedicoes trouxeram. */
	static final class GuildContainer extends SimpleContainer {
		final MinecraftServer server;
		final VillageState village;
		final CivicSites.Site site;
		final ServerPlayer owner;

		GuildContainer(MinecraftServer server, VillageState village, CivicSites.Site site, ServerPlayer owner) {
			super(27);
			this.server = server;
			this.village = village;
			this.site = site;
			this.owner = owner;
			CivicData data = CivicData.get(server);
			List<ItemStack> returns = data.guildReturns(village.key());
			for (int i = 9; i < 27 && !returns.isEmpty(); i++) {
				setItem(i, returns.remove(0));
			}
			data.touch();
		}

		void finish(Player player) {
			CivicData data = CivicData.get(server);
			List<ItemStack> gearRow = new ArrayList<>();
			for (int i = 0; i < 9; i++) {
				ItemStack stack = removeItemNoUpdate(i);
				if (!stack.isEmpty()) {
					gearRow.add(stack);
				}
			}
			for (int i = 9; i < 27; i++) {
				ItemStack stack = removeItemNoUpdate(i);
				if (!stack.isEmpty()) {
					data.guildReturns(village.key()).add(stack);
				}
			}
			data.touch();
			if (player instanceof ServerPlayer sp && armed(sp.getUUID())) {
				ExpeditionRules.Destination dest = selected(sp.getUUID());
				if (depart(sp, village, site, dest, gearRow)) {
					SELECTED.remove(sp.getUUID());
					return;
				}
			}
			for (ItemStack stack : gearRow) {
				player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
	}

	static final class GuildMenu extends ChestMenu {
		private final GuildContainer guild;

		GuildMenu(int id, Inventory inventory, GuildContainer container) {
			super(MenuType.GENERIC_9x3, id, inventory, (Container) container, 3);
			this.guild = container;
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (!player.level().isClientSide()) {
				guild.finish(player);
			}
		}
	}

	/** Equipamento lido da primeira fileira (qualquer encaixe serve, pelo tipo de cada item). */
	public static ExpeditionRules.Gear gearOf(List<ItemStack> stacks) {
		int tool = -1;
		int weapon = -1;
		int[] armor = {-1, -1, -1, -1};
		int food = 0;
		for (ItemStack stack : stacks) {
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			var gear = GearTiers.parse(id);
			if (gear.isPresent()) {
				switch (gear.get().kind()) {
					case TOOL -> tool = Math.max(tool, gear.get().tier());
					case WEAPON -> weapon = Math.max(weapon, gear.get().tier());
					case HELMET -> armor[0] = Math.max(armor[0], gear.get().tier());
					case CHESTPLATE -> armor[1] = Math.max(armor[1], gear.get().tier());
					case LEGGINGS -> armor[2] = Math.max(armor[2], gear.get().tier());
					case BOOTS -> armor[3] = Math.max(armor[3], gear.get().tier());
				}
			} else if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
				food += stack.getCount();
			}
		}
		return new ExpeditionRules.Gear(tool, weapon, armor, food);
	}

	/** Expedicoes em andamento da vila. */
	public static int active(MinecraftServer server, VillageState village) {
		int n = 0;
		for (CivicData.Expedition e : CivicData.get(server).expeditions()) {
			if (e.village().equals(village.key())) {
				n++;
			}
		}
		return n;
	}

	/** Manda um aldeao. Devolve false (e nada muda) se nao ha encaixe ou aldeao livre; o equipamento volta ao jogador. */
	public static boolean depart(ServerPlayer player, VillageState village, CivicSites.Site site, ExpeditionRules.Destination dest, List<ItemStack> gearRow) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		if (active(server, village) >= ExpeditionRules.maxExplorers(site.level())) {
			player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.guild.full", site.level()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (dest.needsVisit() && !CivicData.get(server).visited().contains(dest == ExpeditionRules.Destination.NETHER ? "minecraft:the_nether" : "minecraft:the_end")) {
			return false;
		}
		Villager villager = pickVillager(level, village, site);
		if (villager == null) {
			player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.guild.no_villager").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ExpeditionRules.Gear gear = gearOf(gearRow);
		int days = ExpeditionRules.durationDays(gear);
		int mining = SkillServer.progress(server).level(SkillBranch.MINING);
		List<ExpeditionRules.Drop> drops = ExpeditionRules.roll(dest, gear, mining, site.level(), new Random(level.getRandom().nextLong()));
		List<ItemStack> back = new ArrayList<>();
		// comida gasta, o resto do equipamento volta gasto junto com o explorador
		int foodLeft = ExpeditionRules.foodUsed(days);
		for (ItemStack stack : gearRow) {
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if (GearTiers.parse(id).isPresent()) {
				if (stack.isDamageableItem()) {
					stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + ExpeditionRules.wearPerTrip(days)));
				}
				back.add(stack);
			} else if (stack.has(net.minecraft.core.component.DataComponents.FOOD) && foodLeft > 0) {
				int eaten = Math.min(foodLeft, stack.getCount());
				stack.shrink(eaten);
				foodLeft -= eaten;
				if (!stack.isEmpty()) {
					back.add(stack);
				}
			} else {
				back.add(stack);
			}
		}
		for (ExpeditionRules.Drop drop : drops) {
			ItemStack total = new ItemStack(MuseumServer.item(drop.item()), 1);
			if (total.isEmpty()) {
				continue;
			}
			int left = drop.count();
			while (left > 0) {
				int n = Math.min(left, total.getMaxStackSize());
				back.add(new ItemStack(total.getItem(), n));
				left -= n;
			}
		}
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		villager.save(out);
		String name = villager.hasCustomName() ? villager.getCustomName().getString() : villager.getDisplayName().getString();
		long returnAt = PostServer.now(server) + days * PostRules.DAY;
		CivicData data = CivicData.get(server);
		data.expeditions().add(new CivicData.Expedition(village.key(), dest.id(), returnAt, name, out.buildResult(), back));
		data.touch();
		villager.discard();
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, villager.getX(), villager.getY() + 1, villager.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.guild.departed", name,
				Component.translatable("guild.mlinnotas.dest." + dest.id()), days).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Aldeao adulto e livre mais perto do quadro (dentro do raio da vila). */
	static Villager pickVillager(ServerLevel level, VillageState village, CivicSites.Site site) {
		BlockPos center = site.world(new int[] {site.canvas().w() / 2, 1, site.canvas().d() / 2, -1});
		double radius = village.level().radius() + VillageServer.RESIDENT_MARGIN;
		AABB box = new AABB(village.x() - radius, level.getMinY(), village.z() - radius, village.x() + radius, level.getMaxY(), village.z() + radius);
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
			if (v.isBaby() || !v.isAlive() || v.getTradingPlayer() != null || v.isSleeping()) {
				continue;
			}
			double d = v.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
			if (d < bestD) {
				best = v;
				bestD = d;
			}
		}
		return best;
	}

	/** Volta quem ja completou a viagem (so com a chunk do quadro carregada). */
	public static void tick(MinecraftServer server) {
		CivicData data = CivicData.get(server);
		if (data.expeditions().isEmpty()) {
			return;
		}
		long now = PostServer.now(server);
		Iterator<CivicData.Expedition> it = data.expeditions().iterator();
		boolean changed = false;
		while (it.hasNext()) {
			CivicData.Expedition e = it.next();
			if (e.returnAt() > now) {
				continue;
			}
			VillageState village = null;
			for (VillageState v : VillageData.get(server).villages()) {
				if (v.key().equals(e.village())) {
					village = v;
				}
			}
			if (village == null) {
				// vila removida: o aldeao nao volta, mas o que ele trouxe nao se perde (fica para o primeiro dono de Guilda)
				it.remove();
				changed = true;
				continue;
			}
			ServerLevel level = VillageServer.levelOf(server, village);
			BlockPos spot = returnSpot(server, village);
			if (level == null || spot == null || !level.isLoaded(spot)) {
				continue;
			}
			it.remove();
			changed = true;
			data.guildReturns(village.key()).addAll(CivicData.nonEmpty(e.loot()));
			restore(level, e, spot);
			Component message = Component.translatable("message.mlinnotas.civic.guild.returned", e.villagerName(), village.name()).withStyle(ChatFormatting.GOLD);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (CivicSites.villageAt(server, p.level(), p.blockPosition(), 0) == village) {
					p.sendSystemMessage(message);
				}
			}
		}
		if (changed) {
			data.touch();
		}
	}

	/** Em frente ao quadro da Guilda (ou ao centro da planta). */
	static BlockPos returnSpot(MinecraftServer server, VillageState village) {
		List<CivicSites.Site> sites = CivicSites.built(server, village, CivicBuildings.GUILD);
		if (sites.isEmpty()) {
			return null;
		}
		CivicSites.Site site = sites.get(0);
		List<int[]> boards = site.canvas().marks("board");
		int[] spot = boards.isEmpty() ? new int[] {site.canvas().w() / 2, 1, site.canvas().d() / 2, -1}
				: new int[] {boards.get(0)[0], boards.get(0)[1], boards.get(0)[2] + 2, -1};
		return site.world(spot);
	}

	private static void restore(ServerLevel level, CivicData.Expedition e, BlockPos spot) {
		Entity entity = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), e.villager()), level,
				EntitySpawnReason.LOAD, loaded -> loaded);
		if (entity == null) {
			entity = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		}
		if (entity != null) {
			entity.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, entity.getYRot(), 0.0F);
			entity.setUUID(UUID.randomUUID());
			level.addFreshEntity(entity);
		}
	}
}
