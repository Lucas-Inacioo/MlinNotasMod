package com.mlin.notas.end;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Sentinela de Purpur: construto que guarda a Cidadela do End. Lento e resistente; de perto da um golpe que joga
 * o alvo para cima, de longe atira projeteis de shulker (levitacao). Nao some quando o jogador se afasta.
 */
public class PurpurSentinel extends Monster {
	private static final byte EVENT_ATTACK = 4;
	private int attackAnimationTick;

	public PurpurSentinel(EntityType<? extends PurpurSentinel> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new ShootBulletGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (attackAnimationTick > 0) {
			attackAnimationTick--;
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		attackAnimationTick = 10;
		level.broadcastEntityEvent(this, EVENT_ATTACK);
		boolean hurt = super.doHurtTarget(level, target);
		if (hurt) {
			double resistance = target instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0;
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.5 * Math.max(0.0, 1.0 - resistance), 0.0));
		}
		this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.2F);
		return hurt;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_ATTACK) {
			attackAnimationTick = 10;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Ticks restantes da animacao de golpe (o renderer usa o modelo de golem). */
	public int getAttackAnimationTick() {
		return attackAnimationTick;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SHULKER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	/** Atira um projetil de shulker quando o alvo esta visivel e a mais de 3 blocos. Roda junto com andar/atacar. */
	static class ShootBulletGoal extends Goal {
		private final PurpurSentinel sentinel;
		private int cooldown;

		ShootBulletGoal(PurpurSentinel sentinel) {
			this.sentinel = sentinel;
			this.setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = sentinel.getTarget();
			return target != null && target.isAlive() && sentinel.distanceToSqr(target) > 9.0 && sentinel.distanceToSqr(target) < 400.0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = sentinel.getTarget();
			if (--cooldown > 0 || target == null || !sentinel.getSensing().hasLineOfSight(target)) {
				return;
			}
			cooldown = 50 + sentinel.getRandom().nextInt(30);
			sentinel.level().addFreshEntity(new ShulkerBullet(sentinel.level(), sentinel, target, Direction.Axis.Y));
			sentinel.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (sentinel.getRandom().nextFloat() - sentinel.getRandom().nextFloat()) * 0.2F + 1.0F);
		}
	}
}
