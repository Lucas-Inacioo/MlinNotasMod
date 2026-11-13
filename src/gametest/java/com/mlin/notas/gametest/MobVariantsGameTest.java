package com.mlin.notas.gametest;

import com.mlin.notas.mixin.CreeperAccessor;
import com.mlin.notas.variants.MobVariant;
import com.mlin.notas.variants.MobVariantsServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Game tests das variantes de bioma: sorteio no spawn natural em cada bioma (via /fillbiome), efeito no golpe,
 * drop extra e a explosao com estilhacos do Creeper de Cristal.
 */
public class MobVariantsGameTest {
	private static void fillBiome(GameTestHelper helper, String biome) {
		BlockPos a = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos b = helper.absolutePos(new BlockPos(7, 7, 7));
		MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"fillbiome " + Math.min(a.getX(), b.getX()) + " " + a.getY() + " " + Math.min(a.getZ(), b.getZ()) + " "
						+ Math.max(a.getX(), b.getX()) + " " + b.getY() + " " + Math.max(a.getZ(), b.getZ()) + " " + biome);
		String actual = helper.getLevel().getBiome(helper.absolutePos(new BlockPos(3, 2, 3))).unwrapKey()
				.map(k -> k.identifier().toString()).orElse("?");
		if (!actual.equals(biome)) {
			throw new IllegalStateException("fillbiome falhou: " + actual + " != " + biome);
		}
	}

	private static <T extends Mob> T spawnWithReason(GameTestHelper helper, EntityType<T> type, EntitySpawnReason reason) {
		ServerLevel level = helper.getLevel();
		T mob = type.create(level, reason);
		Vec3 pos = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		mob.snapTo(pos.x, pos.y, pos.z, 0, 0);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	@SuppressWarnings("unchecked")
	private static EntityType<? extends Mob> baseType(MobVariant variant) {
		return (EntityType<? extends Mob>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(variant.baseType()));
	}

	private static Mob variantMob(GameTestHelper helper, MobVariant variant) {
		Mob mob = spawnWithReason(helper, baseType(variant), EntitySpawnReason.COMMAND);
		mob.setNoAi(true);
		MobVariantsServer.apply(mob, variant);
		return mob;
	}

	/** Spawna 15 mobs NATURAL no bioma: com 60% de chance, pelo menos um vira variante; COMMAND nunca. */
	private static void assignedInBiome(GameTestHelper helper, MobVariant variant, String biome) {
		fillBiome(helper, biome);
		int variants = 0;
		for (int i = 0; i < 15; i++) {
			Mob mob = spawnWithReason(helper, baseType(variant), EntitySpawnReason.NATURAL);
			if (MobVariantsServer.variantOf(mob).orElse(null) == variant) {
				variants++;
				if (!(mob.getCustomName() != null && mob.getCustomName().getContents() instanceof TranslatableContents t
						&& t.getKey().equals(variant.translationKey()))) {
					throw new IllegalStateException("nome da variante ausente: " + mob.getCustomName());
				}
			}
		}
		if (variants == 0 || variants == 15) {
			throw new IllegalStateException(variant + " em " + biome + ": " + variants + "/15 variantes (esperado ~60%)");
		}
		Mob command = spawnWithReason(helper, baseType(variant), EntitySpawnReason.COMMAND);
		if (MobVariantsServer.variantOf(command).isPresent()) {
			throw new IllegalStateException("spawn por comando nao deveria virar variante");
		}
		helper.succeed();
	}

	@GameTest
	public void mossyZombieInJungle(GameTestHelper helper) {
		assignedInBiome(helper, MobVariant.MOSSY_ZOMBIE, "minecraft:jungle");
	}

	@GameTest
	public void snowSpiderInIceSpikes(GameTestHelper helper) {
		assignedInBiome(helper, MobVariant.SNOW_SPIDER, "minecraft:ice_spikes");
	}

	@GameTest
	public void blazingSkeletonInDesert(GameTestHelper helper) {
		assignedInBiome(helper, MobVariant.BLAZING_SKELETON, "minecraft:desert");
	}

	@GameTest
	public void crystalCreeperInDripstoneCaves(GameTestHelper helper) {
		assignedInBiome(helper, MobVariant.CRYSTAL_CREEPER, "minecraft:dripstone_caves");
	}

	@GameTest
	public void mangroveDrownedInMangroveSwamp(GameTestHelper helper) {
		assignedInBiome(helper, MobVariant.MANGROVE_DROWNED, "minecraft:mangrove_swamp");
	}

	@GameTest
	public void ashenWitherSkeletonInBasaltDeltas(GameTestHelper helper) {
		assignedInBiome(helper, MobVariant.ASHEN_WITHER_SKELETON, "minecraft:basalt_deltas");
	}

	@GameTest
	public void normalBiomeGivesNoVariant(GameTestHelper helper) {
		fillBiome(helper, "minecraft:plains");
		for (int i = 0; i < 10; i++) {
			Mob mob = spawnWithReason(helper, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL);
			if (MobVariantsServer.variantOf(mob).isPresent()) {
				throw new IllegalStateException("zumbi na planicie virou variante");
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ efeito no golpe

	private static Pig target(GameTestHelper helper) {
		Pig pig = spawnWithReason(helper, EntityTypes.PIG, EntitySpawnReason.COMMAND);
		pig.setNoAi(true);
		return pig;
	}

	private static void hitWithEffect(GameTestHelper helper, MobVariant variant, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		Mob attacker = variantMob(helper, variant);
		Pig pig = target(helper);
		pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
		if (!pig.hasEffect(effect)) {
			throw new IllegalStateException(variant + " nao aplicou " + effect.getRegisteredName());
		}
		helper.succeed();
	}

	@GameTest
	public void mossyZombiePoisons(GameTestHelper helper) {
		hitWithEffect(helper, MobVariant.MOSSY_ZOMBIE, MobEffects.POISON);
	}

	@GameTest
	public void snowSpiderSlows(GameTestHelper helper) {
		hitWithEffect(helper, MobVariant.SNOW_SPIDER, MobEffects.SLOWNESS);
	}

	@GameTest
	public void ashenWitherSkeletonBlinds(GameTestHelper helper) {
		hitWithEffect(helper, MobVariant.ASHEN_WITHER_SKELETON, MobEffects.BLINDNESS);
	}

	@GameTest
	public void blazingSkeletonArrowSetsFire(GameTestHelper helper) {
		Mob skeleton = variantMob(helper, MobVariant.BLAZING_SKELETON);
		Pig pig = target(helper);
		ServerLevel level = helper.getLevel();
		Arrow arrow = new Arrow(level, pig.getX(), pig.getY() + 1, pig.getZ(), new ItemStack(Items.ARROW), null);
		pig.hurtServer(level, level.damageSources().arrow(arrow, skeleton), 1.0F);
		if (!pig.isOnFire()) {
			throw new IllegalStateException("flecha do Esqueleto Ardente nao incendiou o alvo");
		}
		// esqueleto normal nao incendeia
		Mob plain = spawnWithReason(helper, EntityTypes.SKELETON, EntitySpawnReason.COMMAND);
		Pig other = target(helper);
		other.hurtServer(level, level.damageSources().arrow(arrow, plain), 1.0F);
		if (other.isOnFire()) {
			throw new IllegalStateException("esqueleto comum incendiou o alvo");
		}
		helper.succeed();
	}

	@GameTest
	public void mangroveDrownedPullsTarget(GameTestHelper helper) {
		Mob drowned = variantMob(helper, MobVariant.MANGROVE_DROWNED);
		drowned.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 3.5)));
		Pig pig = target(helper);
		pig.snapTo(helper.absoluteVec(new Vec3(5.5, 2, 3.5)));
		pig.setDeltaMovement(Vec3.ZERO);
		pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(drowned), 1.0F);
		Vec3 toward = drowned.position().subtract(pig.position()).normalize();
		Vec3 motion = pig.getDeltaMovement();
		// o knockback vanilla empurra para longe; o puxao tem que vencer
		if (motion.x * toward.x + motion.z * toward.z <= 0.1) {
			throw new IllegalStateException("Afogado do Mangue nao puxou o alvo: " + motion);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ drop extra

	private static void dropsOnDeath(GameTestHelper helper, MobVariant variant, Item expected) {
		Mob mob = variantMob(helper, variant);
		mob.kill(helper.getLevel());
		helper.succeedWhen(() -> helper.assertItemEntityPresent(expected));
	}

	@GameTest
	public void mossyZombieDropsMoss(GameTestHelper helper) {
		dropsOnDeath(helper, MobVariant.MOSSY_ZOMBIE, Items.MOSS_BLOCK);
	}

	@GameTest
	public void snowSpiderDropsSnowAndIce(GameTestHelper helper) {
		Mob mob = variantMob(helper, MobVariant.SNOW_SPIDER);
		mob.kill(helper.getLevel());
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(Items.SNOWBALL);
			helper.assertItemEntityPresent(Items.ICE);
		});
	}

	@GameTest
	public void crystalCreeperDropsShard(GameTestHelper helper) {
		dropsOnDeath(helper, MobVariant.CRYSTAL_CREEPER, Items.AMETHYST_SHARD);
	}

	@GameTest
	public void mangroveDrownedDropsPropagule(GameTestHelper helper) {
		dropsOnDeath(helper, MobVariant.MANGROVE_DROWNED, Items.MANGROVE_PROPAGULE);
	}

	@GameTest
	public void ashenWitherSkeletonDropsAsh(GameTestHelper helper) {
		Item ash = MobVariantsServer.ashItem();
		if (ash == Items.AIR) {
			throw new IllegalStateException("item de cinza invalido");
		}
		dropsOnDeath(helper, MobVariant.ASHEN_WITHER_SKELETON, ash);
	}

	@GameTest
	public void blazingSkeletonBlazePowderIsRare(GameTestHelper helper) {
		int hits = 0;
		for (int i = 0; i < 400; i++) {
			List<ItemStack> drops = MobVariantsServer.extraDrops(MobVariant.BLAZING_SKELETON, helper.getLevel().getRandom());
			if (drops.stream().anyMatch(s -> s.is(Items.BLAZE_POWDER))) {
				hits++;
			}
		}
		if (hits < 40 || hits > 180) {
			throw new IllegalStateException("po de blaze em " + hits + "/400 (esperado ~25%)");
		}
		helper.succeed();
	}

	@GameTest
	public void plainZombieHasNoExtraDrop(GameTestHelper helper) {
		Mob mob = spawnWithReason(helper, EntityTypes.ZOMBIE, EntitySpawnReason.COMMAND);
		mob.kill(helper.getLevel());
		helper.runAfterDelay(5, () -> {
			helper.assertItemEntityNotPresent(Items.MOSS_BLOCK);
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ creeper de cristal

	@GameTest(maxTicks = 100)
	public void crystalCreeperExplodesIntoShards(GameTestHelper helper) {
		Creeper creeper = (Creeper) variantMob(helper, MobVariant.CRYSTAL_CREEPER);
		int radius = ((CreeperAccessor) creeper).mlinnotas$getExplosionRadius();
		if (radius != MobVariantsServer.CRYSTAL_EXPLOSION_RADIUS) {
			throw new IllegalStateException("raio da explosao " + radius);
		}
		creeper.ignite();
		helper.succeedWhen(() -> {
			helper.assertTrue(creeper.isRemoved(), "creeper ainda nao explodiu");
			helper.assertEntityPresent(EntityTypes.AREA_EFFECT_CLOUD);
		});
	}
}
