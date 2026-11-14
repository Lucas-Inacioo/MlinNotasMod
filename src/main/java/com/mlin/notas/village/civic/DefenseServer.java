package com.mlin.notas.village.civic;

import com.mlin.notas.grave.GraveContents;
import com.mlin.notas.grave.GravePlacement;
import com.mlin.notas.grave.GraveServer;
import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.village.BuildingServer;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.waystone.WaystoneCost;
import com.mlin.notas.waystone.WaystoneInfo;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Funcoes de defesa e cuidado da vila: Escola (filhotes crescem mais rapido, novatos ganham experiencia), Torre de vigia
 * (sino que avisa de saqueadores, monstros no minimapa a noite, arqueiros no nivel III), Quartel (guardas), Farol
 * comunitario (efeitos para todos dentro da muralha), Pedra de teleporte da vila (viagem pela metade) e o Seguro de morte
 * da Estalagem II (renasce na estalagem, itens numa lapide ao lado).
 */
public final class DefenseServer {
	private static final Map<String, Long> RANG = new HashMap<>();
	private static final Map<String, Integer> ARCHER_COOLDOWN = new HashMap<>();
	private static Set<String> lastKeys = Set.of();

	private DefenseServer() {
	}

	public static void clear() {
		RANG.clear();
		ARCHER_COOLDOWN.clear();
		lastKeys = Set.of();
	}

	/** Roda a cada tique do servidor, com cada funcao no seu ritmo. */
	public static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		if (t % 20 != 0) {
			return;
		}
		for (VillageState village : new ArrayList<>(VillageData.get(server).villages())) {
			ServerLevel level = VillageServer.levelOf(server, village);
			if (level == null || !level.isLoaded(VillageServer.center(village))) {
				continue;
			}
			school(level, village, t);
			tower(server, level, village, t);
			if (t % 100 == 0) {
				guards(server, level, village);
			}
			if (t % 80 == 0) {
				beacon(server, level, village);
			}
		}
		if (t % 200 == 0) {
			syncWaystones(server, false);
		}
	}

	private static AABB villageBox(ServerLevel level, VillageState village, int margin) {
		double r = village.level().radius() + margin;
		return new AABB(village.x() - r, level.getMinY(), village.z() - r, village.x() + r, level.getMaxY(), village.z() + r);
	}

	// ---------------------------------------------------------------- escola

	public static void school(ServerLevel level, VillageState village, long t) {
		int school = VillageBuildings.level(village, CivicBuildings.SCHOOL);
		if (school <= 0) {
			return;
		}
		int extra = CivicRules.babyExtraTicksPerSecond(school);
		int interval = CivicRules.noviceXpInterval(school);
		for (Villager v : level.getEntitiesOfClass(Villager.class, villageBox(level, village, VillageServer.RESIDENT_MARGIN))) {
			if (v.isBaby()) {
				v.setAge(Math.min(0, v.getAge() + extra));
			} else if (interval > 0 && t % interval < 20 && v.getVillagerData().level() <= 1 && v.getVillagerXp() < CivicRules.noviceXpCap()) {
				v.setVillagerXp(v.getVillagerXp() + 1);
			}
		}
	}

	// ---------------------------------------------------------------- torre de vigia

	public static void tower(MinecraftServer server, ServerLevel level, VillageState village, long t) {
		List<CivicSites.Site> towers = CivicSites.working(server, village, CivicBuildings.WATCHTOWER);
		if (towers.isEmpty()) {
			return;
		}
		int best = 0;
		for (CivicSites.Site site : towers) {
			best = Math.max(best, site.level());
		}
		// sino: um toque por ataque de saqueadores perto da vila
		BlockPos center = VillageServer.center(village);
		Raid raid = level.getRaids().getNearbyRaid(center, village.level().radius() * village.level().radius());
		boolean near = raid != null && raid.isActive();
		long raidKey = raid == null ? 0 : System.identityHashCode(raid);
		boolean alreadyRang = raid != null && RANG.getOrDefault(village.key(), -1L) == raidKey;
		if (CivicRules.shouldRing(near, alreadyRang)) {
			RANG.put(village.key(), raidKey);
			ring(level, village, towers);
		} else if (!near) {
			RANG.remove(village.key());
		}
		// monstros a noite (minimapa dos jogadores dentro do raio) e arqueiros
		boolean night = CivicRules.towerMarksMonsters(best, level.getOverworldClockTime());
		List<LivingEntity> hostiles = new ArrayList<>();
		if (night || best >= 3) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, villageBox(level, village, 0), e -> e instanceof Enemy && e.isAlive())) {
				hostiles.add(e);
			}
		}
		if (night && t % 40 == 0) {
			List<Integer> coords = new ArrayList<>();
			for (LivingEntity e : hostiles) {
				if (coords.size() < 400) {
					coords.add(e.getBlockX());
					coords.add(e.getBlockZ());
				}
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.level() == level && village.distanceSq(p.getBlockX(), p.getBlockZ()) <= (long) village.level().radius() * village.level().radius()
						&& ServerPlayNetworking.canSend(p, CivicPayloads.Hostiles.TYPE)) {
					ServerPlayNetworking.send(p, new CivicPayloads.Hostiles(coords));
				}
			}
		}
		if (best >= 3) {
			archers(level, village, towers, hostiles, t);
		}
	}

	private static void ring(ServerLevel level, VillageState village, List<CivicSites.Site> towers) {
		for (CivicSites.Site site : towers) {
			for (BlockPos bell : site.marks("bell")) {
				level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.8F);
			}
		}
		Component warning = Component.translatable("message.mlinnotas.civic.tower.raid", village.name()).withStyle(ChatFormatting.RED);
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			if (p.level() == level && village.distanceSq(p.getBlockX(), p.getBlockZ()) <= (long) (village.level().radius() + 32) * (village.level().radius() + 32)) {
				p.sendSystemMessage(warning);
				p.sendOverlayMessage(warning);
			}
		}
	}

	/** Cada posto de arqueiro atira no monstro mais perto que enxerga, a cada 2 s. */
	private static void archers(ServerLevel level, VillageState village, List<CivicSites.Site> towers, List<LivingEntity> hostiles, long t) {
		if (hostiles.isEmpty()) {
			return;
		}
		for (CivicSites.Site site : towers) {
			if (site.level() < 3) {
				continue;
			}
			double range = CivicRules.archerRange(site.level());
			for (BlockPos post : site.marks("archer")) {
				String key = village.key() + "@" + post.asLong();
				int cooldown = ARCHER_COOLDOWN.getOrDefault(key, 0);
				if (cooldown > 0) {
					ARCHER_COOLDOWN.put(key, Math.max(0, cooldown - 20));
					continue;
				}
				Vec3 eye = Vec3.atCenterOf(post).add(0, 0.6, 0);
				LivingEntity target = null;
				double bestD = range * range;
				for (LivingEntity h : hostiles) {
					double d = h.distanceToSqr(eye);
					if (d < bestD && h.isAlive() && level.clip(new net.minecraft.world.level.ClipContext(eye, h.getEyePosition(),
							net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
							net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
						bestD = d;
						target = h;
					}
				}
				if (target != null) {
					shoot(level, eye, target);
					ARCHER_COOLDOWN.put(key, CivicRules.archerCooldown());
				}
			}
		}
	}

	static void shoot(ServerLevel level, Vec3 from, LivingEntity target) {
		Arrow arrow = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
		arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
		arrow.setBaseDamage(CivicRules.arrowDamage());
		Vec3 to = target.getEyePosition().subtract(0, 0.2, 0);
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double dy = to.y - from.y + Math.sqrt(dx * dx + dz * dz) * 0.12;
		arrow.shoot(dx, dy, dz, 1.8F, 2.0F);
		level.addFreshEntity(arrow);
		level.playSound(null, BlockPos.containing(from), SoundEvents.SKELETON_SHOOT, SoundSource.BLOCKS, 0.8F, 1.0F);
	}

	// ---------------------------------------------------------------- quartel

	public static void guards(MinecraftServer server, ServerLevel level, VillageState village) {
		List<CivicSites.Site> barracks = CivicSites.working(server, village, CivicBuildings.BARRACKS);
		int best = 0;
		CivicSites.Site site = null;
		for (CivicSites.Site s : barracks) {
			if (s.level() > best) {
				best = s.level();
				site = s;
			}
		}
		AABB box = villageBox(level, village, VillageServer.RESIDENT_MARGIN);
		List<VillageGuard> present = level.getEntitiesOfClass(VillageGuard.class, box);
		int want = CivicRules.guards(best);
		for (int i = present.size() - 1; i >= want; i--) {
			present.remove(i).discard();
		}
		if (site == null) {
			return;
		}
		List<BlockPos> posts = site.marks("guard");
		for (int i = present.size(); i < want && !posts.isEmpty(); i++) {
			BlockPos post = posts.get(i % posts.size());
			if (!level.isLoaded(post)) {
				continue;
			}
			VillageGuard guard = ModEntities.VILLAGE_GUARD.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (guard == null) {
				continue;
			}
			guard.snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			guard.setPatrol(VillageServer.center(village), village.level().radius());
			guard.setHomeTo(post, 10);
			equip(guard, best);
			guard.setCustomName(Component.translatable("entity.mlinnotas.village_guard"));
			level.addFreshEntity(guard);
		}
		// equipamento acompanha o nivel do quartel
		for (VillageGuard g : present) {
			equip(g, best);
		}
	}

	static void equip(VillageGuard guard, int level) {
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			set(guard, slots[i], CivicRules.guardArmor(level, i));
		}
		set(guard, EquipmentSlot.MAINHAND, CivicRules.guardWeapon(level));
	}

	private static void set(VillageGuard guard, EquipmentSlot slot, String id) {
		ItemStack stack = new ItemStack(MuseumServer.item(id));
		if (!stack.isEmpty() && !ItemStack.isSameItem(stack, guard.getItemBySlot(slot))) {
			guard.setItemSlot(slot, stack);
			guard.setDropChance(slot, 0.0F);
		}
	}

	// ---------------------------------------------------------------- farol comunitario

	public static void beacon(MinecraftServer server, ServerLevel level, VillageState village) {
		int best = 0;
		CivicSites.Site site = null;
		for (CivicSites.Site s : CivicSites.working(server, village, CivicBuildings.BEACON)) {
			if (s.level() > best) {
				best = s.level();
				site = s;
			}
		}
		if (site == null) {
			return;
		}
		List<CivicRules.Effect> effects = CivicRules.beaconEffects(best);
		int range = CivicRules.beaconRange(village.level().radius(), best);
		for (ServerPlayer p : level.players()) {
			if (village.distanceSq(p.getBlockX(), p.getBlockZ()) > (long) range * range) {
				continue;
			}
			for (CivicRules.Effect effect : effects) {
				Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect.id())).orElse(null);
				if (holder != null) {
					p.addEffect(new MobEffectInstance(holder, CivicRules.beaconDuration(), effect.amplifier(), true, false, true));
				}
			}
		}
	}

	// ---------------------------------------------------------------- pedras de teleporte das vilas

	/** Atualiza as chaves das pedras de vila (servidor e jogadores); {@code force} manda mesmo sem mudanca. */
	public static void syncWaystones(MinecraftServer server, boolean force) {
		Set<String> keys = new HashSet<>();
		for (VillageState village : VillageData.get(server).villages()) {
			ServerLevel level = VillageServer.levelOf(server, village);
			if (level == null) {
				continue;
			}
			for (CivicSites.Site site : CivicSites.built(server, village, CivicBuildings.WAYSTONE)) {
				for (BlockPos pos : site.marks("waystone")) {
					keys.add(WaystoneInfo.key(VillageServer.dimension(level), pos.getX(), pos.getY(), pos.getZ()));
				}
			}
		}
		if (!force && keys.equals(lastKeys)) {
			return;
		}
		lastKeys = keys;
		WaystoneCost.setVillageKeys(keys);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			sendWaystones(p);
		}
	}

	public static void sendWaystones(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, CivicPayloads.VillageWaystones.TYPE)) {
			ServerPlayNetworking.send(player, new CivicPayloads.VillageWaystones(new ArrayList<>(lastKeys)));
		}
	}

	// ---------------------------------------------------------------- refugio

	/** Monstro de nascimento natural dentro de uma construcao da vila de muralha completa: nao nasce. */
	public static boolean refugeBlocks(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		VillageState village = CivicSites.villageAt(server, level, pos, 0);
		if (village == null || village.built(com.mlin.notas.village.VillageProject.WALL) < 1 || village.layout() == null) {
			return false;
		}
		int rx = pos.getX() - village.x();
		int rz = pos.getZ() - village.z();
		for (var lot : village.layout().lots()) {
			if (lot.built() && lot.rect().contains(rx, rz)) {
				return CivicRules.refugeBlocksSpawn(true, true, true);
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- seguro de morte

	/**
	 * Gancho da morte ({@code GraveHooks.CLAIM_DEATH_ITEMS}): com Estalagem II funcionando na vila e a morte perto dela, o
	 * jogador renasce no quarto da estalagem e os itens vao para uma lapide ao lado (o "achados e perdidos" da estalagem),
	 * que fica la ate ser recuperada: nada se perde e nao some depois de um dia. Sem seguro, deixa a lapide normal.
	 */
	public static boolean insure(ServerPlayer player, ServerLevel level, BlockPos deathPos, GraveContents contents) {
		MinecraftServer server = level.getServer();
		VillageState village = CivicSites.villageAt(server, level, deathPos, 24);
		if (village == null) {
			return false;
		}
		double distance = Math.sqrt(village.distanceSq(deathPos.getX(), deathPos.getZ()));
		int inn = VillageBuildings.level(village, "inn");
		if (!CivicRules.insured(inn, distance, village.level().radius())) {
			return false;
		}
		BlockPos room = BuildingServer.innRoom(server, village);
		if (room == null) {
			return false;
		}
		player.setRespawnPosition(new net.minecraft.server.level.ServerPlayer.RespawnConfig(
				net.minecraft.world.level.storage.LevelData.RespawnData.of(level.dimension(), room, player.getYRot(), 0.0F), true), false);
		if (!contents.isEmpty()) {
			int minY = level.getMinY();
			int maxY = Math.min(level.getMaxY(), minY + level.dimensionType().logicalHeight() - 1);
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			GravePlacement.Result spot = GravePlacement.find((x, y, z) -> GraveServer.cell(level, cursor.set(x, y, z)), room.getX(), room.getY(), room.getZ(),
					minY, maxY, null);
			if (spot != null) {
				BlockPos pos = new BlockPos(spot.pos().x(), spot.pos().y(), spot.pos().z());
				GraveServer.place(level, pos, player.getDirection().getOpposite(), player.getUUID(), player.getName().getString(), contents);
				for (int i = 0; i < com.mlin.notas.grave.GraveRules.SIZE; i++) {
					contents.take(i);
				}
				contents.setXp(0);
				player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.insurance", village.name(), pos.getX(), pos.getY(), pos.getZ())
						.withStyle(ChatFormatting.AQUA));
				return true;
			}
		}
		player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.insurance_respawn", village.name()).withStyle(ChatFormatting.AQUA));
		return false;
	}
}
