package com.mlin.notas.boss;

import com.mlin.notas.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
	/** Dano em area do meteoro do vazio (definido pelo dragao conforme a fase). */
	private float areaDamage = 8.0F;

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

	private boolean voidMeteor() {
		return getType() == BossRegistry.VOID_METEOR;
	}

	public void setAreaDamage(float damage) {
		this.areaDamage = damage;
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
		if (getType() == BossRegistry.VOID_METEOR) {
			return Items.CRYING_OBSIDIAN;
		}
		if (getType() == BossRegistry.STAR_BOLT) {
			return Items.NETHER_STAR;
		}
		return Items.COBBLED_DEEPSLATE;
	}

	@Override
	protected double getDefaultGravity() {
		return star() ? 0.0 : meteor() ? 0.06 : voidMeteor() ? 0.05 : 0.04;
	}

	@Override
	public void tick() {
		super.tick();
		Level level = level();
		if (level.isClientSide()) {
			level.addParticle(star() ? ParticleTypes.END_ROD : voidMeteor() ? ParticleTypes.REVERSE_PORTAL : meteor() ? ParticleTypes.FLAME : ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
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
		if (!(level() instanceof ServerLevel level) || hit == getOwner() || hit instanceof BossMob || voidMeteor()) {
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
			if (voidMeteor()) {
				voidImpact(level);
			} else 			if (meteor()) {
				// estilhaca: dano em area pequena, sem quebrar blocos
				level.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 12, 0.6, 0.3, 0.6, 0.1);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 6, 0.6, 0.3, 0.6, 0.02);
				playSound(ModSounds.MAGMA_METEOR_IMPACT, 2.0F, 1.0F);
				for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5), e -> !(e instanceof BossMob))) {
					near.hurtServer(level, damageSources().onFire(), 4.0F);
					near.igniteForSeconds(3.0F);
				}
			} else if (star()) {
				level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 14, 0.3, 0.3, 0.3, 0.15);
				playSound(ModSounds.STAR_BOLT_IMPACT, 1.0F, 1.0F);
			} else {
				level.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 8, 0.3, 0.3, 0.3, 0.05);
				playSound(ModSounds.COBALT_ROCK_IMPACT, 1.5F, 1.0F);
			}
			discard();
		}
	}

	/** O meteoro do vazio so acerta jogadores (passa pelo dragao e pelos adds). */
	@Override
	protected boolean canHitEntity(Entity entity) {
		if (voidMeteor()) {
			return entity instanceof net.minecraft.world.entity.player.Player && super.canHitEntity(entity);
		}
		return super.canHitEntity(entity);
	}

	/** Impacto do meteoro do vazio: dano em area so nos jogadores (cheio no centro, metade na borda), sem quebrar blocos. */
	private void voidImpact(ServerLevel level) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.3, getZ(), 40, 1.2, 0.4, 1.2, 0.15);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 2, 0.5, 0.2, 0.5, 0);
		playSound(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.8F);
		double r = com.mlin.notas.dragon.DragonRules.METEOR_RADIUS;
		for (net.minecraft.world.entity.player.Player player : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, getBoundingBox().inflate(r + 1),
				p -> p.isAlive() && !p.isSpectator())) {
			float damage = com.mlin.notas.dragon.DragonRules.meteorFalloff(Math.sqrt(player.distanceToSqr(position())), areaDamage);
			if (damage > 0) {
				player.hurtServer(level, damageSources().indirectMagic(this, getOwner()), damage);
			}
		}
	}
}
