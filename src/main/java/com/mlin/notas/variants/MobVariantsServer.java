package com.mlin.notas.variants;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.mixin.CreeperAccessor;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ponte das variantes de bioma com o jogo: attachment persistente e sincronizado ({@code mlinnotas:mob_variant}),
 * sorteio ao nascer (evento {@code ENTITY_LOAD} da Fabric com o motivo do spawn), efeito no golpe, drop extra e a
 * explosao com estilhacos do Creeper de Cristal.
 */
public final class MobVariantsServer {
	public static final AttachmentType<String> VARIANT = AttachmentRegistry.create(MlinNotas.id("mob_variant"),
			b -> b.persistent(Codec.STRING).syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/** Raio da explosao do Creeper de Cristal (vanilla = 3). */
	public static final int CRYSTAL_EXPLOSION_RADIUS = 2;

	private MobVariantsServer() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && !((EntityLoadData) entity).isLoadedFromDisk() && variantOf(mob).isEmpty()) {
				EntitySpawnReason reason = ((EntityLoadData) entity).spawnReason();
				String biome = level.getBiome(mob.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("");
				String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
				MobVariants.pick(type, biome, reason == null ? null : reason.name(), level.getRandom().nextDouble())
						.ifPresent(v -> apply(mob, v));
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && taken > 0 && source.getEntity() instanceof Mob attacker) {
				variantOf(attacker).ifPresent(v -> onHit(v, attacker, entity));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && mob.level() instanceof ServerLevel level && !mob.isBaby()
					&& level.getGameRules().get(GameRules.MOB_DROPS)) {
				variantOf(mob).ifPresent(v -> extraDrops(v, level.getRandom()).forEach(stack -> mob.spawnAtLocation(level, stack)));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			// creeper que explodiu: removido com DISCARDED depois de inchar ate o fim
			if (entity instanceof Creeper creeper && entity.getRemovalReason() == Entity.RemovalReason.DISCARDED
					&& creeper.getSwelling(1.0F) >= 1.0F && level.getDifficulty() != Difficulty.PEACEFUL
					&& variantOf(creeper).orElse(null) == MobVariant.CRYSTAL_CREEPER) {
				spawnShards(level, creeper.position(), creeper);
			}
		});
	}

	public static Optional<MobVariant> variantOf(Entity entity) {
		String id = entity.getAttached(VARIANT);
		return id == null ? Optional.empty() : MobVariant.byId(id);
	}

	/** Transforma o mob na variante: attachment, nome visivel (HUD e mira) e ajustes de atributo. */
	public static void apply(Mob mob, MobVariant variant) {
		mob.setAttached(VARIANT, variant.id());
		if (!mob.hasCustomName()) {
			mob.setCustomName(Component.translatable(variant.translationKey()));
		}
		if (variant == MobVariant.CRYSTAL_CREEPER && mob instanceof Creeper creeper) {
			((CreeperAccessor) creeper).mlinnotas$setExplosionRadius(CRYSTAL_EXPLOSION_RADIUS);
		}
	}

	/** Efeito aplicado no alvo quando a variante acerta (corpo a corpo ou flecha). */
	public static void onHit(MobVariant variant, Mob attacker, LivingEntity target) {
		switch (variant) {
			case MOSSY_ZOMBIE -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100), attacker);
			case SNOW_SPIDER -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), attacker);
			case BLAZING_SKELETON -> target.igniteForSeconds(4.0F);
			case ASHEN_WITHER_SKELETON -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80), attacker);
			case MANGROVE_DROWNED -> {
				Vec3 pull = attacker.position().subtract(target.position());
				if (pull.lengthSqr() > 1.0E-4) {
					Vec3 dir = pull.normalize().scale(0.8);
					target.push(dir.x, 0.15, dir.z);
				}
			}
			case CRYSTAL_CREEPER -> {
				// creeper nao bate corpo a corpo; o efeito dele e a explosao
			}
		}
	}

	/** Drops extras da variante. */
	public static List<ItemStack> extraDrops(MobVariant variant, RandomSource random) {
		List<ItemStack> drops = new ArrayList<>();
		switch (variant) {
			case MOSSY_ZOMBIE -> drops.add(new ItemStack(Items.MOSS_BLOCK));
			case SNOW_SPIDER -> {
				drops.add(new ItemStack(Items.SNOWBALL, 1 + random.nextInt(3)));
				drops.add(new ItemStack(Items.ICE));
			}
			case BLAZING_SKELETON -> {
				if (random.nextFloat() < 0.25F) {
					drops.add(new ItemStack(Items.BLAZE_POWDER));
				}
			}
			case CRYSTAL_CREEPER -> drops.add(new ItemStack(Items.AMETHYST_SHARD, 1 + random.nextInt(2)));
			case MANGROVE_DROWNED -> drops.add(new ItemStack(Items.MANGROVE_PROPAGULE));
			case ASHEN_WITHER_SKELETON -> drops.add(new ItemStack(ashItem(), 1 + random.nextInt(2)));
		}
		return drops;
	}

	/** {@code mlinnotas:void_ash} se existir como item, senao farinha de osso. */
	public static Item ashItem() {
		return BuiltInRegistries.ITEM.getOptional(MlinNotas.id("void_ash")).orElse(Items.BONE_MEAL);
	}

	/** Estilhacos: nuvem curta de dano instantaneo que encolhe rapido, com particulas de ametista. */
	public static AreaEffectCloud spawnShards(ServerLevel level, Vec3 pos, @Nullable LivingEntity owner) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, pos.x, pos.y, pos.z);
		cloud.setOwner(owner);
		cloud.setRadius(3.0F);
		cloud.setRadiusOnUse(-0.5F);
		cloud.setWaitTime(0);
		cloud.setDuration(30);
		cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
		cloud.setCustomParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState()));
		cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 0));
		level.addFreshEntity(cloud);
		return cloud;
	}
}
