package com.mlin.notas.boss;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Golem de Cobalto (Overworld, altar com 4 blocos de ferro). Lento e pesado: soco, <b>pancada no chao</b> (anel que
 * empurra e fere quem estiver a ate 6 blocos) e <b>arremesso de pedra</b> de longe. Fase 2: <b>investida</b> em linha
 * reta que atropela e arremessa.
 */
public class CobaltGolem extends BossMob {
	public static final int ANIM_SLAM = 0;
	public static final int ANIM_THROW = 1;
	public static final int ANIM_CHARGE = 2;
	public static final int ANIM_PUNCH = 3;
	public static final double SLAM_RADIUS = 6.0;

	private int slamDelay;
	private int chargeTicks;
	private Vec3 chargeDir = Vec3.ZERO;
	private final Set<Integer> chargeHit = new HashSet<>();

	public CobaltGolem(EntityType<? extends CobaltGolem> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BossKind.COBALT_GOLEM.baseHealth())
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ATTACK_DAMAGE, 11.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	public BossKind kind() {
		return BossKind.COBALT_GOLEM;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	@Override
	protected AttackScheduler createScheduler() {
		return new AttackScheduler(40, List.of(
				new AttackScheduler.Attack("slam", 90, 1, 0, SLAM_RADIUS, 3),
				new AttackScheduler.Attack("rock", 70, 1, 5, 28, 2),
				new AttackScheduler.Attack("charge", 140, 2, 4, 22, 3)));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void performAttack(ServerLevel level, String id, LivingEntity target) {
		switch (id) {
			case "slam" -> {
				animate(level, ANIM_SLAM);
				slamDelay = 10;
				playSound(SoundEvents.IRON_GOLEM_REPAIR, 2.0F, 0.5F);
			}
			case "rock" -> {
				animate(level, ANIM_THROW);
				throwRock(target, 0.0);
				if (phase() >= 2) {
					throwRock(target, 2.5);
				}
			}
			case "charge" -> {
				animate(level, ANIM_CHARGE);
				Vec3 d = target.position().subtract(position());
				chargeDir = new Vec3(d.x, 0, d.z).normalize();
				chargeTicks = 24;
				chargeHit.clear();
				getNavigation().stop();
				playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.7F);
			}
			default -> {
			}
		}
	}

	private void throwRock(LivingEntity target, double spread) {
		BossProjectile rock = BossProjectile.shoot(BossRegistry.COBALT_ROCK, this, getX(), getY() + getBbHeight() * 0.85, getZ());
		double dx = target.getX() - rock.getX() + (rng.nextDouble() - 0.5) * spread * 2;
		double dz = target.getZ() - rock.getZ() + (rng.nextDouble() - 0.5) * spread * 2;
		double dy = target.getY(0.5) - rock.getY();
		double dist = Math.sqrt(dx * dx + dz * dz);
		rock.shoot(dx, dy + dist * 0.18, dz, 1.25F, 1.5F);
		level().addFreshEntity(rock);
		playSound(SoundEvents.SNOW_GOLEM_SHOOT, 2.0F, 0.4F);
	}

	@Override
	protected void serverTick(ServerLevel level) {
		if (slamDelay > 0 && --slamDelay == 0) {
			slam(level);
		}
		if (chargeTicks > 0) {
			chargeTicks--;
			setDeltaMovement(chargeDir.x * 0.85, getDeltaMovement().y, chargeDir.z * 0.85);
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 3, 0.6, 0.1, 0.6, 0.01);
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), e -> e != this && !(e instanceof BossMob))) {
				if (chargeHit.add(hit.getId())) {
					strike(level, hit, 13.0F);
					fling(hit, chargeDir.x * 1.6, 0.7, chargeDir.z * 1.6);
				}
			}
			if (horizontalCollision) {
				chargeTicks = 0;
				playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.8F);
			}
		}
	}

	/** Pancada no chao: anel de particulas e empurrao para fora em quem estiver no raio. */
	private void slam(ServerLevel level) {
		playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.6F);
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
		for (int i = 0; i < 32; i++) {
			double a = i * Math.PI * 2 / 32;
			for (double r = 1.5; r <= SLAM_RADIUS; r += 1.5) {
				level.sendParticles(dust, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.1);
			}
		}
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SLAM_RADIUS, 2.0, SLAM_RADIUS),
				e -> e != this && !(e instanceof BossMob) && e.onGround())) {
			double dist = Math.sqrt(near.distanceToSqr(getX(), near.getY(), getZ()));
			double push = BossRules.slamKnockback(dist, SLAM_RADIUS + 0.5, 1.8);
			if (push <= 0) {
				continue;
			}
			strike(level, near, phase() >= 2 ? 12.0F : 9.0F);
			Vec3 out = new Vec3(near.getX() - getX(), 0, near.getZ() - getZ());
			out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
			fling(near, out.x * push, 0.55, out.z * push);
		}
	}

	/** Lanca uma entidade (sincroniza a velocidade com o cliente dos jogadores). */
	static void fling(Entity entity, double vx, double vy, double vz) {
		double resist = entity instanceof LivingEntity l ? Math.max(0.0, 1.0 - l.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) : 1.0;
		entity.setDeltaMovement(entity.getDeltaMovement().add(vx * resist, vy * resist, vz * resist));
		entity.needsSync = true;
		entity.syncVelocity = true;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		animate(level, ANIM_PUNCH);
		boolean hurt = super.doHurtTarget(level, target);
		if (hurt) {
			fling(target, 0, 0.45, 0);
		}
		playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.6F);
		return hurt;
	}

	@Override
	protected ParticleOptions themeParticle() {
		return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LAPIS_BLOCK.defaultBlockState());
	}

	@Override
	protected SoundEvent roarSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected Item dropItem() {
		return BossRegistry.COBALT_HEART;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.IRON_GOLEM_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}
}
