package com.mlin.notas.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Projeteis dos chefes, desenhados como item: pedra de cobalto (Golem; cai em arco), meteoro de magma (Colosso; cai do
 * alto, queima) e raio estelar (Arauto; sem gravidade, persegue o alvo por alguns segundos). O tipo de entidade diz
 * qual e qual.
 */
public class BossProjectile extends ThrowableItemProjectile {
	private @Nullable LivingEntity homingTarget;

	public BossProjectile(EntityType<? extends BossProjectile> type, Level level) {
		super(type, level);
	}

	public static BossProjectile shoot(EntityType<BossProjectile> type, LivingEntity owner, double x, double y, double z) {
		BossProjectile projectile = new BossProjectile(type, owner.level());
		projectile.setOwner(owner);
		projectile.setPos(x, y, z);
		projectile.setItem(new ItemStack(projectile.getDefaultItem()));
		return projectile;
	}

	private boolean star() {
		return getType() == BossRegistry.STAR_BOLT;
	}

	private boolean meteor() {
		return getType() == BossRegistry.MAGMA_METEOR;
	}

	public void setHomingTarget(@Nullable LivingEntity target) {
		this.homingTarget = target;
	}

	@Override
	protected Item getDefaultItem() {
		if (getType() == BossRegistry.MAGMA_METEOR) {
			return Items.MAGMA_BLOCK;
		}
		if (getType() == BossRegistry.STAR_BOLT) {
			return Items.NETHER_STAR;
		}
		return Items.COBBLED_DEEPSLATE;
	}

	@Override
	protected double getDefaultGravity() {
		return star() ? 0.0 : meteor() ? 0.06 : 0.04;
	}

	@Override
	public void tick() {
		super.tick();
		Level level = level();
		if (level.isClientSide()) {
			level.addParticle(star() ? ParticleTypes.END_ROD : meteor() ? ParticleTypes.FLAME : ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
			return;
		}
		if (star()) {
			if (homingTarget != null && homingTarget.isAlive() && tickCount < 80) {
				Vec3 want = homingTarget.getBoundingBox().getCenter().subtract(position()).normalize().scale(0.55);
				setDeltaMovement(getDeltaMovement().scale(0.85).add(want.scale(0.15)).normalize().scale(0.55));
			}
			if (tickCount > 120) {
				discard();
			}
		} else if (tickCount > 200) {
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		Entity hit = hitResult.getEntity();
		if (!(level() instanceof ServerLevel level) || hit == getOwner() || hit instanceof BossMob) {
			return;
		}
		float damage = star() ? 7.0F : meteor() ? 9.0F : 10.0F;
		if (star()) {
			hit.hurtServer(level, damageSources().indirectMagic(this, getOwner()), damage);
		} else {
			hit.hurtServer(level, damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), damage);
			if (meteor()) {
				hit.igniteForSeconds(5.0F);
			}
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (level() instanceof ServerLevel level) {
			if (meteor()) {
				// estilhaca: dano em area pequena, sem quebrar blocos
				level.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 12, 0.6, 0.3, 0.6, 0.1);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 6, 0.6, 0.3, 0.6, 0.02);
				playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.3F);
				for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5), e -> !(e instanceof BossMob))) {
					near.hurtServer(level, damageSources().onFire(), 4.0F);
					near.igniteForSeconds(3.0F);
				}
			} else if (star()) {
				level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 14, 0.3, 0.3, 0.3, 0.15);
				playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.6F);
			} else {
				level.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 8, 0.3, 0.3, 0.3, 0.05);
				playSound(SoundEvents.STONE_BREAK, 1.2F, 0.7F);
			}
			discard();
		}
	}
}
