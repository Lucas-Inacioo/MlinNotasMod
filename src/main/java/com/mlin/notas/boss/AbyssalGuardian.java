package com.mlin.notas.boss;

import com.mlin.notas.registry.ModSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Guardiao das Profundezas (Deep Dark, altar com 4 fragmentos de eco). Cego como o warden, mas com outra forma:
 * <b>pulso sonico</b> em linha reta que ignora armadura e <b>escuridao</b> em quem estiver perto. Fase 2:
 * <b>tentaculos de sculk</b> brotam sob os jogadores, prendem (lentidao forte) e ferem.
 */
public class AbyssalGuardian extends BossMob {
	public static final int ANIM_PULSE = 0;
	public static final int ANIM_ROAR = 1;
	public static final int ANIM_TENDRILS = 2;
	public static final int ANIM_PUNCH = 3;

	private int pulseDelay;
	private @Nullable LivingEntity pulseTarget;

	public AbyssalGuardian(EntityType<? extends AbyssalGuardian> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BossKind.ABYSSAL_GUARDIAN.baseHealth())
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ATTACK_DAMAGE, 13.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.STEP_HEIGHT, 1.2);
	}

	@Override
	public BossKind kind() {
		return BossKind.ABYSSAL_GUARDIAN;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.GREEN;
	}

	@Override
	protected AttackScheduler createScheduler() {
		return new AttackScheduler(35, List.of(
				new AttackScheduler.Attack("pulse", 70, 1, 3, 20, 3),
				new AttackScheduler.Attack("darkness", 220, 1, 0, 24, 1),
				new AttackScheduler.Attack("tendrils", 120, 2, 0, 14, 3)));
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
			case "pulse" -> {
				animate(level, ANIM_PULSE);
				pulseTarget = target;
				pulseDelay = 14;
				playSound(ModSounds.ABYSSAL_GUARDIAN_PULSE_CHARGE, 3.0F, 1.0F);
			}
			case "darkness" -> {
				animate(level, ANIM_ROAR);
				playSound(ModSounds.ABYSSAL_GUARDIAN_ROAR, 3.0F, 1.1F);
				for (ServerPlayer player : playersAround(level, 24)) {
					player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0), this);
				}
			}
			case "tendrils" -> {
				animate(level, ANIM_TENDRILS);
				playSound(ModSounds.ABYSSAL_GUARDIAN_TENDRILS, 3.0F, 1.0F);
				for (ServerPlayer player : playersAround(level, 14)) {
					tendrils(level, player);
				}
			}
			default -> {
			}
		}
	}

	/** Tentaculos de sculk: coluna de particulas nos pes, prende (lentidao V por 3 s) e fere. */
	private void tendrils(ServerLevel level, LivingEntity victim) {
		for (int i = 0; i < 6; i++) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY() + i * 0.35, victim.getZ(), 4, 0.35, 0.1, 0.35, 0.01);
		}
		level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, victim.getX(), victim.getY() + 0.1, victim.getZ(), 20, 0.8, 0.05, 0.8, 0.02);
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4), this);
		victim.hurtServer(level, damageSources().mobAttack(this), 5.0F);
		victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
		victim.needsSync = true;
		victim.syncVelocity = true;
	}

	@Override
	protected void serverTick(ServerLevel level) {
		if (pulseDelay > 0 && --pulseDelay == 0 && pulseTarget != null) {
			LivingEntity target = pulseTarget;
			pulseTarget = null;
			if (!target.isAlive() || distanceToSqr(target) > 22 * 22) {
				return;
			}
			Vec3 from = position().add(0, getBbHeight() * 0.75, 0);
			Vec3 to = target.getEyePosition();
			Vec3 step = to.subtract(from).normalize();
			double length = from.distanceTo(to);
			for (double d = 1; d < length; d += 1.0) {
				Vec3 p = from.add(step.scale(d));
				level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			playSound(ModSounds.ABYSSAL_GUARDIAN_PULSE, 3.0F, 1.0F);
			if (hasLineOfSight(target)) {
				target.hurtServer(level, damageSources().sonicBoom(this), phase() >= 2 ? 12.0F : 10.0F);
				CobaltGolem.fling(target, step.x * 1.2, 0.3, step.z * 1.2);
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		animate(level, ANIM_PUNCH);
		boolean hurt = super.doHurtTarget(level, target);
		playSound(ModSounds.ABYSSAL_GUARDIAN_ATTACK, 1.5F, 1.0F);
		return hurt;
	}

	@Override
	protected ParticleOptions themeParticle() {
		return ParticleTypes.SCULK_SOUL;
	}

	@Override
	protected SoundEvent roarSound() {
		return ModSounds.ABYSSAL_GUARDIAN_ROAR;
	}

	@Override
	protected Item dropItem() {
		return BossRegistry.ABYSSAL_CORE;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.ABYSSAL_GUARDIAN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.ABYSSAL_GUARDIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.ABYSSAL_GUARDIAN_DEATH;
	}

}
