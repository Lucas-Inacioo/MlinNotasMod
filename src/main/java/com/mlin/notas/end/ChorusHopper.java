package com.mlin.notas.end;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Saltador de Coro: bichinho passivo da Floresta de Coro. Anda aos pulinhos (cada passo e um salto), foge quando
 * apanha e as vezes se teleporta como quem come uma fruta do coro. Solta frutas do coro.
 */
public class ChorusHopper extends PathfinderMob {
	private int hopCooldown;

	public ChorusHopper(EntityType<? extends ChorusHopper> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.28);
	}

	/** Nasce em pedra do End (o chao da Floresta de Coro). */
	public static boolean checkSpawnRules(EntityType<ChorusHopper> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return level.getBlockState(pos.below()).is(BlockTags.SUPPORTS_CHORUS_PLANT);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (hopCooldown > 0) {
			hopCooldown--;
		}
		// andando no chao: transforma o passo num pulinho
		if (!level().isClientSide() && onGround() && hopCooldown == 0 && getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
			setDeltaMovement(getDeltaMovement().multiply(1.6, 1.0, 1.6).add(0.0, 0.42, 0.0));
			hopCooldown = 8 + random.nextInt(6);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive() && random.nextInt(3) == 0) {
			double x = getX() + (random.nextDouble() - 0.5) * 12.0;
			double y = getY() + random.nextInt(5) - 2;
			double z = getZ() + (random.nextDouble() - 0.5) * 12.0;
			if (randomTeleport(x, y, z, true, state -> !state.getFluidState().isEmpty())) {
				playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.4F);
			}
		}
		return hurt;
	}

	/** Pulando (no ar) ou nao: o renderer usa para esticar/achatar o corpo. */
	public boolean isHopping() {
		return !onGround();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CHORUS_FLOWER_GROW;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RABBIT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CHORUS_FLOWER_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}
}
