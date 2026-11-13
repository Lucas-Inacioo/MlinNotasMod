package com.mlin.notas.boss;

import com.mlin.notas.registry.ModEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Arauto Astral (Cidadela do End, altar com 4 Nucleos de Sentinela). Voa em volta do alvo, atira <b>raios estelares</b>
 * que perseguem e <b>se teleporta</b> para perto do alvo. Fase 2: <b>invoca Sentinelas de Purpur</b> (no maximo 4 por
 * perto) e atira mais raios por vez.
 */
public class AstralHerald extends BossMob {
	public static final int ANIM_CAST = 0;
	public static final int ANIM_BLINK = 1;
	public static final int ANIM_SUMMON = 2;
	public static final int MAX_SENTINELS = 4;

	public AstralHerald(EntityType<? extends AstralHerald> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 20, true);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BossKind.ASTRAL_HERALD.baseHealth())
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.MOVEMENT_SPEED, 0.45)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	public BossKind kind() {
		return BossKind.ASTRAL_HERALD;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	@Override
	protected AttackScheduler createScheduler() {
		return new AttackScheduler(30, List.of(
				new AttackScheduler.Attack("star_bolt", 50, 1, 0, 40, 4),
				new AttackScheduler.Attack("teleport", 140, 1, 0, 48, 2),
				new AttackScheduler.Attack("summon", 300, 2, 0, 48, 2)));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(2, new HoverGoal(this));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void performAttack(ServerLevel level, String id, LivingEntity target) {
		switch (id) {
			case "star_bolt" -> {
				animate(level, ANIM_CAST);
				playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.5F, 1.4F);
				int count = phase() >= 2 ? 5 : 3;
				for (int i = 0; i < count; i++) {
					double a = getYRot() * Math.PI / 180 + (i - (count - 1) / 2.0) * 0.6;
					BossProjectile bolt = BossProjectile.shoot(BossRegistry.STAR_BOLT, this, getX() - Math.sin(a) * 1.2, getY() + getBbHeight() * 0.7,
							getZ() + Math.cos(a) * 1.2);
					bolt.setDeltaMovement(-Math.sin(a) * 0.4, 0.3, Math.cos(a) * 0.4);
					bolt.setHomingTarget(target);
					level.addFreshEntity(bolt);
				}
			}
			case "teleport" -> blink(level, target);
			case "summon" -> {
				animate(level, ANIM_SUMMON);
				playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 0.8F);
				int alive = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(24), e -> e.getType() == ModEntities.PURPUR_SENTINEL).size();
				for (int i = 0; i < 2 && alive < MAX_SENTINELS; i++, alive++) {
					Mob sentinel = ModEntities.PURPUR_SENTINEL.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (sentinel == null) {
						continue;
					}
					Vec3 spot = findSpot(level, target.position(), 4, sentinel.getBoundingBox().getXsize(), sentinel.getBbHeight());
					if (spot == null) {
						continue;
					}
					sentinel.setPos(spot);
					sentinel.setTarget(target);
					level.addFreshEntity(sentinel);
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1, spot.z, 30, 0.4, 0.8, 0.4, 0.05);
				}
			}
			default -> {
			}
		}
	}

	/** Some e reaparece perto do alvo, onde caiba. */
	private void blink(ServerLevel level, LivingEntity target) {
		Vec3 spot = findSpot(level, target.position().add(0, 2, 0), 6, getBbWidth(), getBbHeight());
		if (spot == null) {
			return;
		}
		animate(level, ANIM_BLINK);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 40, 0.5, 1.0, 0.5, 0.1);
		teleportTo(spot.x, spot.y, spot.z);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1, spot.z, 40, 0.5, 1.0, 0.5, 0.1);
		playSound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.7F);
	}

	/** Posicao livre (caixa w x h sem colisao) a ate {@code radius} blocos de {@code around}, ou null. */
	private Vec3 findSpot(ServerLevel level, Vec3 around, double radius, double w, double h) {
		for (int i = 0; i < 24; i++) {
			double a = rng.nextDouble() * Math.PI * 2;
			double r = 2 + rng.nextDouble() * (radius - 2);
			Vec3 p = new Vec3(around.x + Math.cos(a) * r, Math.floor(around.y) + rng.nextInt(3) - 1, around.z + Math.sin(a) * r);
			AABB box = new AABB(p.x - w / 2, p.y, p.z - w / 2, p.x + w / 2, p.y + h, p.z + w / 2);
			if (level.noCollision(box) && !level.containsAnyLiquid(box)) {
				return p;
			}
		}
		return null;
	}

	@Override
	protected ParticleOptions themeParticle() {
		return ParticleTypes.END_ROD;
	}

	@Override
	protected SoundEvent roarSound() {
		return SoundEvents.ENDER_DRAGON_GROWL;
	}

	@Override
	protected Item dropItem() {
		return BossRegistry.ASTRAL_FRAGMENT;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() && tickCount % 3 == 0) {
			level().addParticle(ParticleTypes.END_ROD, getRandomX(0.6), getY() + 0.2, getRandomZ(0.6), 0, -0.04, 0);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.EVOKER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SHULKER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.EVOKER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F;
	}

	/** Paira em volta do alvo (3 a 5 blocos acima, 4 a 7 de distancia), trocando de ponto de tempos em tempos. */
	static class HoverGoal extends Goal {
		private final AstralHerald herald;
		private int repick;

		HoverGoal(AstralHerald herald) {
			this.herald = herald;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = herald.getTarget();
			if (target != null) {
				herald.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			if (--repick > 0 && herald.getMoveControl().hasWanted()) {
				return;
			}
			repick = 30 + herald.rng.nextInt(30);
			Vec3 center = target != null ? target.position() : herald.position();
			double a = herald.rng.nextDouble() * Math.PI * 2;
			double r = target != null ? 4 + herald.rng.nextDouble() * 3 : herald.rng.nextDouble() * 4;
			double y = center.y + (target != null ? 3 + herald.rng.nextDouble() * 2 : herald.rng.nextDouble() * 2 - 1);
			herald.getMoveControl().setWantedPosition(center.x + Math.cos(a) * r, y, center.z + Math.sin(a) * r, 1.0);
		}
	}
}
