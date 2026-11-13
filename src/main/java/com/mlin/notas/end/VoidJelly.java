package com.mlin.notas.end;

import com.mlin.notas.registry.ModSounds;
import com.mlin.notas.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Agua-viva do Vazio: criatura passiva que flutua devagar sobre os Campos de Musgo do End, sempre perto de onde
 * nasceu. Brilha no escuro (renderer) e solta Geleia do Vazio (queda lenta ao comer).
 */
public class VoidJelly extends PathfinderMob {
	private @Nullable BlockPos home;
	private @Nullable Vec3 target;

	public VoidJelly(EntityType<? extends VoidJelly> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.1);
	}

	/** So nasce sobre musgo do End. */
	public static boolean checkSpawnRules(EntityType<VoidJelly> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return level.getBlockState(pos.below()).is(ModBlocks.END_MOSS);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (home == null) {
			home = blockPosition();
		}
		if (target == null || random.nextInt(80) == 0 || target.closerThan(position(), 1.0)) {
			// novo ponto: ate 6 blocos da casa, de 2 a 6 blocos acima dela
			target = new Vec3(home.getX() + random.nextInt(13) - 6 + 0.5, home.getY() + 2 + random.nextInt(5), home.getZ() + random.nextInt(13) - 6 + 0.5);
		}
		Vec3 velocity = getDeltaMovement().scale(0.9).add(target.subtract(position()).normalize().scale(0.008));
		if (velocity.length() > 0.08) {
			velocity = velocity.normalize().scale(0.08);
		}
		setDeltaMovement(velocity);
		float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90.0F;
		setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()) * 0.1F);
		yBodyRot = getYRot();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.VOID_JELLY_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return ModSounds.VOID_JELLY_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.VOID_JELLY_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
