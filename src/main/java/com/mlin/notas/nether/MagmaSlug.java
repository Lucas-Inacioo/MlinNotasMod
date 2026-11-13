package com.mlin.notas.nether;

import com.mlin.notas.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lesma de Magma: bicho passivo do Bosque Ardente. Lenta, imune a fogo e lava, solta faiscas de vez em quando e
 * foge quando apanha. Solta creme de magma.
 */
public class MagmaSlug extends PathfinderMob {
	public MagmaSlug(EntityType<? extends MagmaSlug> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.14);
	}

	/** Nasce sobre bloco solido (nao em lava). */
	public static boolean checkSpawnRules(EntityType<MagmaSlug> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return below.isSolidRender() && level.getBlockState(pos).isAir();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 2.2));
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() && random.nextInt(12) == 0) {
			level().addParticle(random.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.LAVA, getRandomX(0.5), getY() + 0.3,
					getRandomZ(0.5), 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.MAGMA_SLUG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.MAGMA_SLUG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.MAGMA_SLUG_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.5F;
	}
}
