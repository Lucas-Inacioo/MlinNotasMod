package com.mlin.notas.boss;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Base dos chefes: barra de chefe, vida escalada pelos jogadores perto, 2 fases (fase 2 abaixo de 50%), animacao de
 * surgimento (invulneravel e parado), nunca some, imune a queda/sufocar/afogar, explosao de particulas na morte e o
 * item garantido do {@link BossKind}. Os ataques especiais saem de um {@link AttackScheduler} e cada subclasse executa
 * o seu em {@link #performAttack}.
 */
public abstract class BossMob extends Monster {
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(BossMob.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SPAWN_TICKS = SynchedEntityData.defineId(BossMob.class, EntityDataSerializers.INT);
	/** Eventos de entidade 60..69 = animacao do ataque n. */
	private static final byte EVENT_ANIM = 60;
	public static final int ANIM_TICKS = 20;

	protected final ServerBossEvent bossEvent;
	protected final AttackScheduler scheduler;
	protected final Random rng = new Random();
	private boolean scaled;
	private int animTicks;
	private int animKind;

	protected BossMob(EntityType<? extends BossMob> type, Level level) {
		super(type, level);
		this.xpReward = kind().xp();
		this.bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
		this.scheduler = createScheduler();
		this.setPersistenceRequired();
	}

	public abstract BossKind kind();

	protected abstract BossEvent.BossBarColor barColor();

	protected abstract AttackScheduler createScheduler();

	/** Executa o ataque especial {@code id} (servidor). */
	protected abstract void performAttack(ServerLevel level, String id, LivingEntity target);

	/** Particula usada no surgimento, na troca de fase e na morte. */
	protected abstract ParticleOptions themeParticle();

	protected abstract SoundEvent roarSound();

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_SPAWN_TICKS, 0);
	}

	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	public int spawnTicks() {
		return this.entityData.get(DATA_SPAWN_TICKS);
	}

	public boolean spawning() {
		return spawnTicks() > 0;
	}

	/** Ticks restantes da animacao do ultimo ataque (cliente e servidor). */
	public int animTicks() {
		return animTicks;
	}

	public int animKind() {
		return animKind;
	}

	/** Comeca a animacao de surgimento: parado e invulneravel por {@link BossRules#SPAWN_TICKS}. */
	public void startSpawnAnimation() {
		this.entityData.set(DATA_SPAWN_TICKS, BossRules.SPAWN_TICKS);
	}

	/** Ajusta a vida ao numero de jogadores por perto e enche. Chamado ao invocar/acordar. */
	public void prepareForFight(ServerLevel level) {
		int players = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(BossRules.PLAYER_SCAN_RADIUS),
				p -> !p.isSpectator() && p.isAlive()).size();
		AttributeInstance max = getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(BossRules.scaledHealth(kind().baseHealth(), players));
		}
		setHealth(getMaxHealth());
		scaled = true;
	}

	public boolean scaled() {
		return scaled;
	}

	/** Toca a animacao {@code kind} nos clientes (e no servidor). */
	protected void animate(ServerLevel level, int kind) {
		animTicks = ANIM_TICKS;
		animKind = kind;
		level.broadcastEntityEvent(this, (byte) (EVENT_ANIM + kind));
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id >= EVENT_ANIM && id < EVENT_ANIM + 10) {
			animTicks = ANIM_TICKS;
			animKind = id - EVENT_ANIM;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Parado (sem IA): surgindo ou dormindo. */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || spawning() || dormant();
	}

	/** Chefe adormecido (so o colosso). */
	public boolean dormant() {
		return false;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (animTicks > 0) {
			animTicks--;
		}
		int spawn = spawnTicks();
		if (level() instanceof ServerLevel server) {
			if (spawn > 0) {
				this.entityData.set(DATA_SPAWN_TICKS, spawn - 1);
				if (spawn % 4 == 0) {
					server.sendParticles(themeParticle(), getX(), getY() + getBbHeight() * (1.0 - spawn / (double) BossRules.SPAWN_TICKS),
							getZ(), 12, getBbWidth() * 0.6, 0.2, getBbWidth() * 0.6, 0.02);
				}
				if (spawn == BossRules.SPAWN_TICKS - 1) {
					playSound(roarSound(), 3.0F, 0.8F);
				}
			}
			serverTick(server);
		}
	}

	/** Gancho por tick no servidor (inclusive parado). */
	protected void serverTick(ServerLevel level) {
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!scaled) {
			prepareForFight(level);
		}
		int phase = BossRules.phase(getHealth(), getMaxHealth());
		if (phase != phase()) {
			this.entityData.set(DATA_PHASE, phase);
			if (phase == 2) {
				enterPhaseTwo(level);
			}
		}
		scheduler.tick();
		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			scheduler.next(phase, distanceTo(target), rng).ifPresent(attack -> performAttack(level, attack.id(), target));
		}
		bossEvent.setProgress(getHealth() / getMaxHealth());
	}

	/** Troca para a fase 2: rugido, particulas e barra escura. */
	protected void enterPhaseTwo(ServerLevel level) {
		playSound(roarSound(), 4.0F, 0.6F);
		level.sendParticles(themeParticle(), getX(), getY() + getBbHeight() / 2, getZ(), 60, getBbWidth(), getBbHeight() / 2, getBbWidth(), 0.1);
		bossEvent.setDarkenScreen(true);
		for (ServerPlayer player : playersAround(level, BossRules.PLAYER_SCAN_RADIUS)) {
			tell(player, Component.translatable("message.mlinnotas.boss.phase_two", getDisplayName()));
		}
	}

	/** Jogadores vivos (fora do espectador/criativo) a ate {@code radius} blocos. */
	public List<ServerPlayer> playersAround(ServerLevel level, double radius) {
		return level.getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(radius),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(this) <= radius * radius);
	}

	/** Mensagem no chat (ignora jogadores sem conexao, como os falsos dos game tests). */
	public static void tell(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendSystemMessage(message);
		}
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.isInvulnerableTo(level, source);
		}
		if (spawning() || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_DROWNING)) {
			return true;
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
		if (level().getDifficulty() == Difficulty.PEACEFUL && !getType().isAllowedInPeaceful()) {
			discard();
		} else {
			this.noActionTime = 0;
		}
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		bossEvent.setName(getDisplayName());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Scaled", scaled);
		output.putInt("SpawnTicks", spawnTicks());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		scaled = input.getBooleanOr("Scaled", false);
		this.entityData.set(DATA_SPAWN_TICKS, input.getIntOr("SpawnTicks", 0));
		if (hasCustomName()) {
			bossEvent.setName(getDisplayName());
		}
	}

	/** Item garantido do chefe (vida longa no chao, nao some antes do jogador pegar). */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		ItemEntity drop = spawnAtLocation(level, new ItemStack(dropItem()));
		if (drop != null) {
			drop.setExtendedLifetime();
		}
	}

	protected abstract Item dropItem();

	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (level() instanceof ServerLevel server && deathTime % 3 == 0) {
			server.sendParticles(themeParticle(), getX(), getY() + getBbHeight() / 2, getZ(), 20, getBbWidth() / 2, getBbHeight() / 2,
					getBbWidth() / 2, 0.15);
			if (deathTime == 18) {
				server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + getBbHeight() / 2, getZ(), 1, 0, 0, 0, 0);
			}
		}
	}

	/** Dano do chefe num alvo (fonte = ataque de mob). */
	protected boolean strike(ServerLevel level, LivingEntity target, float damage) {
		return target.hurtServer(level, damageSources().mobAttack(this), damage);
	}
}
