package com.mlin.notas.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Colosso Igneo (Picos de Infernita, Nether). Dorme ajoelhado no trono da forja e acorda quando alguem chega a 16
 * blocos (ou o acerta). <b>Onda de fogo</b> que se espalha em anel a partir dele, <b>chuva de meteoros</b> de magma
 * sobre os jogadores. Fase 2: <b>pocas de lava</b> temporarias brotam sob os jogadores (avisadas por particulas).
 */
public class IgneousColossus extends BossMob {
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(IgneousColossus.class, EntityDataSerializers.BOOLEAN);
	public static final int ANIM_WAVE = 0;
	public static final int ANIM_METEOR = 1;
	public static final int ANIM_LAVA = 2;
	public static final int ANIM_PUNCH = 3;
	public static final int WAVE_TICKS = 20;
	public static final double WAVE_RADIUS = 11.0;
	/** Quanto tempo a poca de lava fica (ticks). */
	public static final int LAVA_TICKS = 100;
	private static final int LAVA_WARNING = 16;

	private int waveTick = -1;
	private final Set<Integer> waveHit = new HashSet<>();
	/** Pocas de lava: posicao, tick do jogo em que nasce e em que some. */
	private final List<long[]> lavaPools = new ArrayList<>();

	public IgneousColossus(EntityType<? extends IgneousColossus> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BossKind.IGNEOUS_COLOSSUS.baseHealth())
				.add(Attributes.ARMOR, 14.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.ATTACK_DAMAGE, 15.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DORMANT, false);
	}

	@Override
	public boolean dormant() {
		return this.entityData.get(DATA_DORMANT);
	}

	public void setDormant(boolean dormant) {
		this.entityData.set(DATA_DORMANT, dormant);
		bossEvent.setVisible(!dormant);
	}

	@Override
	public BossKind kind() {
		return BossKind.IGNEOUS_COLOSSUS;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	@Override
	protected AttackScheduler createScheduler() {
		return new AttackScheduler(40, List.of(
				new AttackScheduler.Attack("fire_wave", 100, 1, 0, 12, 3),
				new AttackScheduler.Attack("meteor", 90, 1, 4, 32, 3),
				new AttackScheduler.Attack("lava_pool", 160, 2, 0, 24, 2)));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	/** Acorda: animacao de surgimento, vida escalada, barra visivel e aviso. */
	public void wake(ServerLevel level) {
		if (!dormant()) {
			return;
		}
		setDormant(false);
		startSpawnAnimation();
		prepareForFight(level);
		for (ServerPlayer player : playersAround(level, BossRules.PLAYER_SCAN_RADIUS)) {
			tell(player, Component.translatable("message.mlinnotas.boss.awakens", getDisplayName()));
		}
	}

	/** Acorda se algum dos jogadores (sobrevivencia/aventura) estiver dentro do raio. */
	public boolean checkWake(ServerLevel level, List<? extends Player> players) {
		for (Player player : players) {
			if (!player.isCreative() && !player.isSpectator() && BossRules.shouldWake(player.distanceToSqr(this), kind().wakeRadius())) {
				wake(level);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (dormant() && source.getEntity() instanceof Player) {
			wake(level);
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected void serverTick(ServerLevel level) {
		bossEvent.setVisible(!dormant());
		if (dormant()) {
			if (tickCount % 10 == 0) {
				checkWake(level, playersAround(level, kind().wakeRadius()));
			}
			if (tickCount % 20 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.7, getZ(), 2, 0.4, 0.2, 0.4, 0.01);
			}
			return;
		}
		if (waveTick >= 0) {
			fireWave(level);
		}
		tickLavaPools(level);
	}

	@Override
	protected void performAttack(ServerLevel level, String id, LivingEntity target) {
		switch (id) {
			case "fire_wave" -> {
				animate(level, ANIM_WAVE);
				waveTick = 0;
				waveHit.clear();
				playSound(SoundEvents.BLAZE_SHOOT, 3.0F, 0.5F);
			}
			case "meteor" -> {
				animate(level, ANIM_METEOR);
				playSound(SoundEvents.GHAST_SHOOT, 3.0F, 0.5F);
				for (ServerPlayer player : playersAround(level, 32)) {
					int count = phase() >= 2 ? 3 : 2;
					for (int i = 0; i < count; i++) {
						double ox = (rng.nextDouble() - 0.5) * 6;
						double oz = (rng.nextDouble() - 0.5) * 6;
						BossProjectile meteor = BossProjectile.shoot(BossRegistry.MAGMA_METEOR, this, player.getX() + ox, player.getY() + 14 + i * 3,
								player.getZ() + oz);
						meteor.setDeltaMovement(-ox * 0.04, -0.5, -oz * 0.04);
						level.addFreshEntity(meteor);
					}
				}
			}
			case "lava_pool" -> {
				animate(level, ANIM_LAVA);
				playSound(SoundEvents.LAVA_AMBIENT, 3.0F, 0.6F);
				long now = level.getGameTime();
				for (ServerPlayer player : playersAround(level, 24)) {
					BlockPos feet = player.blockPosition();
					addPool(level, feet, now);
					addPool(level, feet.offset(rng.nextInt(3) - 1, 0, rng.nextInt(3) - 1), now);
				}
			}
			default -> {
			}
		}
	}

	private void addPool(ServerLevel level, BlockPos pos, long now) {
		// a lava ocupa o bloco dos pes, sobre chao firme
		BlockPos at = pos;
		for (int i = 0; i < 3 && level.getBlockState(at.below()).isAir(); i++) {
			at = at.below();
		}
		if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
			lavaPools.add(new long[] {at.asLong(), now + LAVA_WARNING, now + LAVA_WARNING + LAVA_TICKS});
		}
	}

	/** Avisa, cria e depois remove as pocas de lava. */
	private void tickLavaPools(ServerLevel level) {
		long now = level.getGameTime();
		lavaPools.removeIf(pool -> {
			BlockPos pos = BlockPos.of(pool[0]);
			if (now < pool[1]) {
				level.sendParticles(ParticleTypes.FALLING_LAVA, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
				return false;
			}
			if (now == pool[1] && level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
				return false;
			}
			if (now >= pool[2]) {
				clearLava(level, pos);
				return true;
			}
			return false;
		});
	}

	private static void clearLava(ServerLevel level, BlockPos pos) {
		if (level.getFluidState(pos).isSourceOfType(Fluids.LAVA)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Lava ainda no mundo (para os testes). */
	public int activeLavaPools() {
		return lavaPools.size();
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel level) {
			for (long[] pool : lavaPools) {
				clearLava(level, BlockPos.of(pool[0]));
			}
			lavaPools.clear();
		}
		super.remove(reason);
	}

	/** Anel de fogo crescendo; queima quem estiver na faixa (uma vez por onda). */
	private void fireWave(ServerLevel level) {
		double radius = BossRules.waveRadius(waveTick, WAVE_TICKS, WAVE_RADIUS);
		int points = Math.max(8, (int) (radius * 8));
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2 / points;
			level.sendParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * radius, getY() + 0.3, getZ() + Math.sin(a) * radius, 1, 0.05, 0.15, 0.05, 0.01);
		}
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(WAVE_RADIUS + 1, 2, WAVE_RADIUS + 1),
				e -> e != this && !(e instanceof BossMob))) {
			double dist = Math.sqrt(near.distanceToSqr(getX(), near.getY(), getZ()));
			if (BossRules.inRing(dist, radius, 1.6) && Math.abs(near.getY() - getY()) < 2.5 && waveHit.add(near.getId())) {
				near.hurtServer(level, damageSources().mobAttack(this), phase() >= 2 ? 10.0F : 8.0F);
				near.igniteForSeconds(4.0F);
			}
		}
		if (++waveTick > WAVE_TICKS) {
			waveTick = -1;
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		animate(level, ANIM_PUNCH);
		boolean hurt = super.doHurtTarget(level, target);
		if (hurt) {
			target.igniteForSeconds(3.0F);
		}
		playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.5F);
		return hurt;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Dormant", dormant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setDormant(input.getBooleanOr("Dormant", false));
	}

	@Override
	protected ParticleOptions themeParticle() {
		return ParticleTypes.FLAME;
	}

	@Override
	protected SoundEvent roarSound() {
		return SoundEvents.RAVAGER_ROAR;
	}

	@Override
	protected Item dropItem() {
		return BossRegistry.COLOSSUS_EMBER;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return dormant() ? null : SoundEvents.BLAZE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}
}
