package com.mlin.notas.village.civic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * Guarda da vila: gerado pelo Quartel (2, 4 ou 6 por nivel). Armadura e arma vem do nivel do Quartel, e ele nunca some.
 * De dia fica perto do posto; a noite patrulha pontos sorteados no anel das ruas e ataca monstros (menos creepers, que
 * ele deixa para os golens) e quem o machucar. O centro e o raio da patrulha sao salvos com a entidade.
 */
public class VillageGuard extends PathfinderMob {
	private int patrolX;
	private int patrolZ;
	private int patrolRadius;

	public VillageGuard(EntityType<? extends VillageGuard> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
	}

	/** Centro e raio da patrulha noturna (centro da vila). */
	public void setPatrol(BlockPos center, int radius) {
		this.patrolX = center.getX();
		this.patrolZ = center.getZ();
		this.patrolRadius = radius;
	}

	public int patrolRadius() {
		return patrolRadius;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 32.0F));
		this.goalSelector.addGoal(3, new PatrolGoal());
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
				(target, level) -> target instanceof Enemy && !(target instanceof Creeper)));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !(target instanceof Player) || getLastHurtByMob() == target;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("patrol_x", patrolX);
		output.putInt("patrol_z", patrolZ);
		output.putInt("patrol_radius", patrolRadius);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		patrolX = input.getIntOr("patrol_x", 0);
		patrolZ = input.getIntOr("patrol_z", 0);
		patrolRadius = input.getIntOr("patrol_radius", 0);
	}

	/** A noite, a cada poucos segundos caminha ate um ponto sorteado do anel das ruas. */
	private final class PatrolGoal extends Goal {
		private int cooldown = 40;

		PatrolGoal() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (patrolRadius <= 0 || getTarget() != null || !CivicRules.patrolNow(level().getOverworldClockTime())) {
				return false;
			}
			if (cooldown > 0) {
				cooldown--;
				return false;
			}
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return !navigation().isDone() && getTarget() == null && CivicRules.patrolNow(level().getOverworldClockTime());
		}

		@Override
		public void start() {
			cooldown = 60 + random.nextInt(80);
			int[] p = CivicRules.patrolPoint(random::nextDouble, Math.min(10, patrolRadius / 2), Math.max(12, patrolRadius - 6));
			// passos curtos ate o ponto: o caminho ate os campos passa pelos portoes da muralha externa
			int[] step = com.mlin.notas.village.RuralRules.patrolStep(getBlockX(), getBlockZ(), patrolX + p[0], patrolZ + p[1]);
			int x = step[0];
			int z = step[1];
			if (level() instanceof ServerLevel serverLevel && serverLevel.isLoaded(new BlockPos(x, 64, z))) {
				BlockPos ground = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
				navigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0.6);
			}
		}

		private net.minecraft.world.entity.ai.navigation.PathNavigation navigation() {
			return VillageGuard.this.getNavigation();
		}
	}
}
