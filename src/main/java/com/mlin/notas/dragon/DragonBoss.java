package com.mlin.notas.dragon;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.boss.AttackScheduler;
import com.mlin.notas.boss.BossProjectile;
import com.mlin.notas.boss.BossRegistry;
import com.mlin.notas.boss.BossRules;
import com.mlin.notas.end.CrystalMite;
import com.mlin.notas.end.PurpurSentinel;
import com.mlin.notas.mixin.EnderDragonFightAccessor;
import com.mlin.notas.registry.ModEntities;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Luta reformulada contra o Dragao do Ender (ponte com o jogo; regras em {@link DragonRules}). Roda a cada tick do End
 * para cada dragao vivo, sem mixin no dragao: escala a vida pelos jogadores na arena, troca de fase (com cristais que
 * renascem na fase 2 e furia na fase 3), mostra a fase na barra de chefe vanilla e executa os ataques sorteados pelo
 * {@link AttackScheduler}. Todo ataque avisa antes de doer: meteoros marcam o chao, o mergulho marca o circulo da onda.
 */
public final class DragonBoss {
	/** Numero de jogadores para o qual a vida ja foi escalada (0 = ainda nao). */
	public static final AttachmentType<Integer> SCALED_FOR = AttachmentRegistry.create(MlinNotas.id("dragon_scaled_for"),
			b -> b.persistent(Codec.INT));
	/** Ultima fase anunciada (para nao repetir a troca de fase ao recarregar o mundo). */
	public static final AttachmentType<Integer> PHASE = AttachmentRegistry.create(MlinNotas.id("dragon_phase"), b -> b.persistent(Codec.INT));
	/** Nuvem de sopro ja aumentada pela furia. */
	public static final AttachmentType<Boolean> RAGE_CLOUD = AttachmentRegistry.create(MlinNotas.id("dragon_rage_cloud"),
			b -> b.persistent(Codec.BOOL));

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Random RNG = new Random();
	/** Particula dos avisos no chao (sopro do dragao: rosa, grande, facil de ver). */
	private static final net.minecraft.core.particles.ParticleOptions WARNING = net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);

	/** Meteoro marcado: cai do ceu em {@code spawnAt}. */
	record Meteor(double x, double y, double z, long spawnAt, float damage) {
	}

	/** Estado em memoria de um dragao (ataques em andamento). */
	static final class State {
		final AttackScheduler scheduler = DragonRules.scheduler();
		final List<Meteor> meteors = new ArrayList<>();
		@Nullable Vec3 diveTarget;
		long diveStart;
		boolean diveCharging;
		@Nullable Vec3 waveCenter;
		int waveTick;
		int wavePhase;
		final Set<Integer> waveHit = new HashSet<>();
	}

	private DragonBoss() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.END) {
				return;
			}
			for (EnderDragon dragon : List.copyOf(level.getDragons())) {
				tick(level, dragon);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof EnderDragon dragon) {
				STATES.remove(dragon.getUUID());
				if (entity.getRemovalReason() == Entity.RemovalReason.KILLED && dragon.getDragonFight() != null) {
					DragonHoard.place(level);
				}
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AreaEffectCloud cloud && cloud.getOwner() instanceof EnderDragon dragon && phase(dragon) >= 3
					&& !cloud.hasAttached(RAGE_CLOUD)) {
				cloud.setAttached(RAGE_CLOUD, true);
				cloud.setRadius(cloud.getRadius() * DragonRules.RAGE_BREATH_SCALE);
				cloud.setDuration((int) (cloud.getDuration() * DragonRules.RAGE_BREATH_SCALE));
			}
		});
	}

	public static int phase(EnderDragon dragon) {
		return DragonRules.phase(dragon.getHealth(), dragon.getMaxHealth());
	}

	static State state(EnderDragon dragon) {
		return STATES.computeIfAbsent(dragon.getUUID(), u -> new State());
	}

	private static boolean dying(EnderDragon dragon) {
		return !dragon.isAlive() || dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING;
	}

	/** Centro da arena: a origem da luta (0,0) ou, fora da luta, o proprio dragao. */
	private static BlockPos center(EnderDragon dragon) {
		return dragon.getDragonFight() != null ? dragon.getFightOrigin() : dragon.blockPosition();
	}

	/** Jogadores lutando: no End, vivos, nem espectador nem criativo, ate 150 blocos (na horizontal) da arena. */
	public static List<ServerPlayer> fighters(ServerLevel level, EnderDragon dragon) {
		BlockPos c = center(dragon);
		double r = DragonRules.PLAYER_SCAN_RADIUS;
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - c.getX();
			double dz = p.getZ() - c.getZ();
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && dx * dx + dz * dz <= r * r) {
				out.add(p);
			}
		}
		return out;
	}

	/** Jogadores contados para a vida (inclui criativo; so tira espectador). */
	private static int challengers(ServerLevel level, EnderDragon dragon) {
		BlockPos c = center(dragon);
		double r = DragonRules.PLAYER_SCAN_RADIUS;
		int n = 0;
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - c.getX();
			double dz = p.getZ() - c.getZ();
			if (p.isAlive() && !p.isSpectator() && dx * dx + dz * dz <= r * r) {
				n++;
			}
		}
		return n;
	}

	public static void tick(ServerLevel level, EnderDragon dragon) {
		if (dying(dragon)) {
			State st = STATES.get(dragon.getUUID());
			if (st != null) {
				st.meteors.clear();
				st.diveTarget = null;
			}
			return;
		}
		State st = state(dragon);
		scale(level, dragon, challengers(level, dragon));
		int phase = phase(dragon);
		int announced = dragon.getAttachedOrElse(PHASE, 1);
		if (phase > announced) {
			dragon.setAttached(PHASE, phase);
			if (phase >= 2 && announced < 2) {
				enterPhaseTwo(level, dragon);
			}
			if (phase >= 3) {
				enterPhaseThree(level, dragon);
			}
		}
		if (level.getGameTime() % 10 == 0) {
			updateBar(dragon, phase);
		}
		List<ServerPlayer> players = fighters(level, dragon);
		tickMeteors(level, dragon, st);
		tickDive(level, dragon, st, phase);
		tickWave(level, dragon, st);
		st.scheduler.tick();
		if (players.isEmpty() || dragon.isNoAi()) {
			return;
		}
		double nearest = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			nearest = Math.min(nearest, p.distanceTo(dragon));
		}
		st.scheduler.next(phase, nearest, RNG).ifPresent(a -> perform(level, dragon, a.id(), phase, players));
	}

	// ---------------------------------------------------------------- vida e fases

	/** Escala a vida para {@code players} jogadores (so para cima), mantendo a fracao atual. */
	public static void scale(ServerLevel level, EnderDragon dragon, int players) {
		int scaledFor = dragon.getAttachedOrElse(SCALED_FOR, 0);
		if (players <= 0 || !DragonRules.shouldRescale(scaledFor, players)) {
			return;
		}
		AttributeInstance max = dragon.getAttribute(Attributes.MAX_HEALTH);
		if (max == null) {
			return;
		}
		double oldMax = dragon.getMaxHealth();
		double newMax = DragonRules.maxHealth(players);
		double health = DragonRules.rescaledHealth(dragon.getHealth(), oldMax, newMax);
		max.setBaseValue(newMax);
		dragon.setHealth((float) health);
		dragon.setAttached(SCALED_FOR, Math.max(DragonRules.BASE_PLAYERS, players));
		for (ServerPlayer p : fighters(level, dragon)) {
			tell(p, Component.translatable("message.mlinnotas.dragon.health", players, (int) newMax));
		}
	}

	private static void updateBar(EnderDragon dragon, int phase) {
		EnderDragonFight fight = dragon.getDragonFight();
		if (fight == null) {
			return;
		}
		ServerBossEvent bar = ((EnderDragonFightAccessor) fight).mlinnotas$getDragonEvent();
		bar.setName(Component.translatable("boss.mlinnotas.dragon.phase", dragon.getDisplayName(), phase));
		bar.setColor(switch (phase) {
			case 1 -> BossEvent.BossBarColor.PINK;
			case 2 -> BossEvent.BossBarColor.PURPLE;
			default -> BossEvent.BossBarColor.RED;
		});
		bar.setDarkenScreen(phase >= 3);
	}

	/** Fase 2: rugido e cristais novos nos pilares mais baixos. */
	static void enterPhaseTwo(ServerLevel level, EnderDragon dragon) {
		roar(level, dragon);
		int regrown = regrowCrystals(level, dragon);
		MlinNotas.LOGGER.info("[DragonBoss] fase 2: {} cristais renasceram", regrown);
		for (ServerPlayer p : fighters(level, dragon)) {
			tell(p, Component.translatable("message.mlinnotas.dragon.phase_two"));
		}
	}

	static void enterPhaseThree(ServerLevel level, EnderDragon dragon) {
		roar(level, dragon);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, dragon.getX(), dragon.getY() + 2, dragon.getZ(), 120, 4, 2, 4, 0.3);
		for (ServerPlayer p : fighters(level, dragon)) {
			tell(p, Component.translatable("message.mlinnotas.dragon.phase_three"));
		}
	}

	private static void roar(ServerLevel level, EnderDragon dragon) {
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 0.7F);
	}

	/** Recoloca cristais nos pilares escolhidos por {@link DragonRules#regrowTargets}. Devolve quantos. */
	public static int regrowCrystals(ServerLevel level, EnderDragon dragon) {
		if (dragon.getDragonFight() == null) {
			return 0;
		}
		List<DragonRules.Pillar> pillars = new ArrayList<>();
		for (EndSpikeFeature.EndSpike s : EndSpikeFeature.getSpikesForLevel(level)) {
			boolean has = !level.getEntitiesOfClass(EndCrystal.class, s.getTopBoundingBox()).isEmpty();
			pillars.add(new DragonRules.Pillar(s.getCenterX(), s.getCenterZ(), s.getRadius(), s.getHeight(), s.isGuarded(), has));
		}
		List<DragonRules.Pillar> targets = DragonRules.regrowTargets(pillars, DragonRules.REGROWN_CRYSTALS);
		for (DragonRules.Pillar p : targets) {
			BlockPos top = new BlockPos(p.x(), p.height(), p.z());
			if (!level.getBlockState(top.below()).is(Blocks.OBSIDIAN) && !level.getBlockState(top.below()).is(Blocks.BEDROCK)) {
				continue;
			}
			level.setBlockAndUpdate(top, Blocks.BEDROCK.defaultBlockState());
			level.setBlockAndUpdate(top.above(), BaseFireBlock.getState(level, top.above()));
			EndCrystal crystal = net.minecraft.world.entity.EntityTypes.END_CRYSTAL.create(level, EntitySpawnReason.EVENT);
			if (crystal == null) {
				continue;
			}
			crystal.snapTo(p.x() + 0.5, p.height() + 1, p.z() + 0.5, RNG.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(crystal);
			for (int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.x() + p.radius() + 2, p.z()); y < p.height() + 2; y += 2) {
				level.sendParticles(ParticleTypes.END_ROD, p.x() + 0.5, y, p.z() + 0.5, 4, p.radius() * 0.6, 0.5, p.radius() * 0.6, 0.02);
			}
			level.playSound(null, top, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 6.0F, 0.6F);
		}
		return targets.size();
	}

	// ---------------------------------------------------------------- ataques

	/** Executa o ataque {@code id} (tambem usado pelos testes para forcar um ataque). */
	public static void perform(ServerLevel level, EnderDragon dragon, String id, int phase, List<ServerPlayer> players) {
		if (players.isEmpty()) {
			return;
		}
		switch (id) {
			case DragonRules.METEORS -> meteors(level, dragon, phase, players);
			case DragonRules.MITES -> adds(level, dragon, ModEntities.CRYSTAL_MITE, CrystalMite.class, DragonRules.MAX_MITES, 0, players);
			case DragonRules.SENTINELS -> adds(level, dragon, ModEntities.PURPUR_SENTINEL, PurpurSentinel.class, DragonRules.MAX_SENTINELS, 2, players);
			case DragonRules.DIVE -> {
				if (!dive(level, dragon, players)) {
					meteors(level, dragon, phase, players);
				}
			}
			case DragonRules.BREATH_VOLLEY -> breathVolley(level, dragon, players);
			default -> {
			}
		}
	}

	/** Chao (primeiro bloco de ar) na coluna de (x, z). */
	private static double groundY(ServerLevel level, double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
	}

	/** Topo do primeiro bloco solido abaixo de {@code fromY} (ate 12 blocos); sem nada, o chao da coluna. */
	public static double groundBelow(ServerLevel level, double x, double fromY, double z) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, fromY, z).mutable();
		for (int i = 0; i < 12; i++) {
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return pos.getY() + level.getBlockState(pos).getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y);
			}
			pos.move(0, -1, 0);
		}
		return groundY(level, x, z);
	}

	static void meteors(ServerLevel level, EnderDragon dragon, int phase, List<ServerPlayer> players) {
		roar(level, dragon);
		for (ServerPlayer p : players) {
			int n = DragonRules.meteorsPerPlayer(phase);
			for (int i = 0; i < n; i++) {
				double x = p.getX();
				double z = p.getZ();
				if (i > 0) {
					double a = RNG.nextDouble() * Math.PI * 2;
					double d = 1.5 + RNG.nextDouble() * (DragonRules.METEOR_SPREAD - 1.5);
					x += Math.cos(a) * d;
					z += Math.sin(a) * d;
				}
				markMeteor(level, dragon, x, p.getY() + 2, z, phase, DragonRules.METEOR_WARNING + i * 6L);
			}
		}
	}

	/** Marca um meteoro em (x, z): aviso no chao por {@code delay} ticks, depois ele cai do ceu. */
	public static void markMeteor(ServerLevel level, EnderDragon dragon, double x, double z, int phase, long delay) {
		markMeteor(level, dragon, x, groundY(level, x, z), z, phase, delay);
	}

	/** Igual, mas o chao e o primeiro bloco abaixo de {@code fromY} (jogador embaixo de um teto ou arco). */
	public static void markMeteor(ServerLevel level, EnderDragon dragon, double x, double fromY, double z, int phase, long delay) {
		state(dragon).meteors.add(new Meteor(x, groundBelow(level, x, fromY, z), z, level.getGameTime() + delay, DragonRules.meteorDamage(phase)));
	}

	/** Um tick dos ataques em andamento (meteoros marcados, mergulho e onda), sem sortear ataque novo. Para os testes. */
	public static void tickAttacks(ServerLevel level, EnderDragon dragon) {
		State st = state(dragon);
		tickMeteors(level, dragon, st);
		tickDive(level, dragon, st, phase(dragon));
		tickWave(level, dragon, st);
	}

	private static void tickMeteors(ServerLevel level, EnderDragon dragon, State st) {
		long now = level.getGameTime();
		st.meteors.removeIf(m -> {
			if (now < m.spawnAt()) {
				if (now % 3 == 0) {
					ring(level, m.x(), m.y() + 0.15, m.z(), DragonRules.METEOR_RADIUS, WARNING);
					ring(level, m.x(), m.y() + 0.15, m.z(), 1.0, ParticleTypes.END_ROD);
					level.sendParticles(ParticleTypes.END_ROD, m.x(), m.y() + 0.5 + RNG.nextDouble() * 6, m.z(), 1, 0.05, 0.3, 0.05, 0.0);
				}
				return false;
			}
			BossProjectile meteor = BossProjectile.shoot(BossRegistry.VOID_METEOR, dragon, m.x(), m.y() + DragonRules.METEOR_HEIGHT, m.z());
			meteor.setAreaDamage(m.damage());
			meteor.setDeltaMovement(0, -0.7, 0);
			level.addFreshEntity(meteor);
			return true;
		});
	}

	/** Meteoros ainda so marcados no chao (para os testes). */
	public static int pendingMeteors(EnderDragon dragon) {
		State st = STATES.get(dragon.getUUID());
		return st == null ? 0 : st.meteors.size();
	}

	private static void ring(ServerLevel level, double x, double y, double z, double radius, net.minecraft.core.particles.ParticleOptions particle) {
		int points = Math.max(10, (int) (radius * 8));
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			level.sendParticles(particle, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** Adds brotam do chao a 4-7 blocos de cada jogador (ate o limite de vivos perto da arena). */
	static <T extends Mob> int adds(ServerLevel level, EnderDragon dragon, EntityType<T> type, Class<T> cls, int max, int lift, List<ServerPlayer> players) {
		BlockPos c = center(dragon);
		int alive = level.getEntitiesOfClass(cls, new AABB(c).inflate(DragonRules.PLAYER_SCAN_RADIUS, 128, DragonRules.PLAYER_SCAN_RADIUS)).size();
		int n = DragonRules.addsToSpawn(players.size(), alive, max);
		int spawned = 0;
		for (int i = 0; i < n; i++) {
			ServerPlayer target = players.get(i % players.size());
			double a = RNG.nextDouble() * Math.PI * 2;
			double d = 4 + RNG.nextDouble() * 3;
			double x = target.getX() + Math.cos(a) * d;
			double z = target.getZ() + Math.sin(a) * d;
			double y = groundBelow(level, x, target.getY() + 3, z);
			if (y <= level.getMinY() + 1) {
				continue;
			}
			BlockPos pos = BlockPos.containing(x, y + lift, z);
			T mob = type.spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
			if (mob != null) {
				mob.setTarget(target);
				mob.setPersistenceRequired();
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 30, 0.5, 0.6, 0.5, 0.1);
				level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 10, 0.4, 0.8, 0.4, 0.05);
				spawned++;
			}
		}
		if (spawned > 0) {
			level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 6.0F, 1.3F);
		}
		return spawned;
	}

	/** Comeca o mergulho num jogador (so voando em padrao/rasante). Devolve false se nao deu para mergulhar agora. */
	static boolean dive(ServerLevel level, EnderDragon dragon, List<ServerPlayer> players) {
		State st = state(dragon);
		var current = dragon.getPhaseManager().getCurrentPhase().getPhase();
		if (st.diveTarget != null || st.waveCenter != null
				|| current != EnderDragonPhase.HOLDING_PATTERN && current != EnderDragonPhase.STRAFE_PLAYER) {
			return false;
		}
		ServerPlayer target = players.get(RNG.nextInt(players.size()));
		startDive(level, dragon, new Vec3(target.getX(), groundBelow(level, target.getX(), target.getY() + 0.5, target.getZ()), target.getZ()));
		return true;
	}

	/** Marca o alvo e, depois do aviso, poe o dragao em carga ate ele. */
	public static void startDive(ServerLevel level, EnderDragon dragon, Vec3 target) {
		State st = state(dragon);
		st.diveTarget = target;
		st.diveStart = level.getGameTime();
		st.diveCharging = false;
		roar(level, dragon);
		for (ServerPlayer p : fighters(level, dragon)) {
			if (p.connection != null) {
				p.sendOverlayMessage(Component.translatable("message.mlinnotas.dragon.dive"));
			}
		}
	}

	private static void tickDive(ServerLevel level, EnderDragon dragon, State st, int phase) {
		Vec3 t = st.diveTarget;
		if (t == null) {
			return;
		}
		long age = level.getGameTime() - st.diveStart;
		if (age % 2 == 0) {
			ring(level, t.x, t.y + 0.15, t.z, DragonRules.SHOCKWAVE_RADIUS, WARNING);
			ring(level, t.x, t.y + 0.9, t.z, DragonRules.SHOCKWAVE_RADIUS, WARNING);
			level.sendParticles(ParticleTypes.END_ROD, t.x, t.y + 1 + RNG.nextDouble() * 10, t.z, 3, 0.1, 1.5, 0.1, 0.0);
			ring(level, t.x, t.y + 0.15, t.z, 1.5, ParticleTypes.END_ROD);
		}
		if (!st.diveCharging) {
			if (age >= DragonRules.DIVE_WINDUP) {
				st.diveCharging = true;
				if (!dragon.isNoAi()) {
					dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
					dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(t.add(0, 2, 0));
				}
			}
			return;
		}
		boolean arrived = dragon.position().distanceTo(t) < DragonRules.DIVE_ARRIVE
				|| dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.CHARGING_PLAYER
				|| age > DragonRules.DIVE_WINDUP + DragonRules.DIVE_TIMEOUT;
		if (arrived) {
			st.diveTarget = null;
			startWave(level, dragon, t, phase);
		}
	}

	/** Onda de choque a partir de {@code center} (chao). */
	public static void startWave(ServerLevel level, EnderDragon dragon, Vec3 center, int phase) {
		State st = state(dragon);
		st.waveCenter = center;
		st.waveTick = 0;
		st.wavePhase = phase;
		st.waveHit.clear();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.6F);
	}

	private static void tickWave(ServerLevel level, EnderDragon dragon, State st) {
		Vec3 c = st.waveCenter;
		if (c == null) {
			return;
		}
		double radius = BossRules.waveRadius(st.waveTick, DragonRules.SHOCKWAVE_TICKS, DragonRules.SHOCKWAVE_RADIUS);
		ring(level, c.x, c.y + 0.2, c.z, Math.max(0.5, radius), ParticleTypes.REVERSE_PORTAL);
		ring(level, c.x, c.y + 0.1, c.z, Math.max(0.5, radius), ParticleTypes.CLOUD);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(DragonRules.SHOCKWAVE_RADIUS + 2, 4, DragonRules.SHOCKWAVE_RADIUS + 2),
				p -> p.isAlive() && !p.isSpectator())) {
			double dx = p.getX() - c.x;
			double dz = p.getZ() - c.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			double above = p.onGround() ? 0.0 : p.getY() - groundBelow(level, p.getX(), p.getY(), p.getZ());
			if (DragonRules.shockwaveHits(dist, radius, above) && st.waveHit.add(p.getId())) {
				p.hurtServer(level, dragon.damageSources().mobAttack(dragon), DragonRules.shockwaveDamage(st.wavePhase));
				double len = Math.max(0.1, dist);
				p.push(dx / len * 0.9, 0.6, dz / len * 0.9);
			}
		}
		if (++st.waveTick > DragonRules.SHOCKWAVE_TICKS) {
			st.waveCenter = null;
		}
	}

	/** Onda de choque em andamento (para os testes). */
	public static boolean waveActive(EnderDragon dragon) {
		State st = STATES.get(dragon.getUUID());
		return st != null && (st.waveCenter != null || st.diveTarget != null);
	}

	/** Fase 3: tres bolas de fogo do dragao de uma vez, nos jogadores (espalhadas). */
	static void breathVolley(ServerLevel level, EnderDragon dragon, List<ServerPlayer> players) {
		double sx = dragon.head.getX();
		double sy = dragon.head.getY(0.5) + 0.5;
		double sz = dragon.head.getZ();
		level.levelEvent(null, 1017, dragon.blockPosition(), 0);
		for (int i = 0; i < 3; i++) {
			ServerPlayer target = players.get(RNG.nextInt(players.size()));
			double tx = target.getX() + (i == 0 ? 0 : (RNG.nextDouble() - 0.5) * 8);
			double tz = target.getZ() + (i == 0 ? 0 : (RNG.nextDouble() - 0.5) * 8);
			Vec3 dir = new Vec3(tx - sx, target.getY(0.5) - sy, tz - sz).normalize();
			DragonFireball ball = new DragonFireball(level, dragon, dir);
			ball.snapTo(sx, sy, sz, 0.0F, 0.0F);
			level.addFreshEntity(ball);
		}
	}

	private static void tell(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendSystemMessage(message);
		}
	}
}
