package com.mlin.notas.combat;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado servidor do "Combate melhor", sem mixin:
 * <ul>
 *   <li>{@link AttackEntityCallback} (inicio de {@code Player.attack}): avanca o combo ({@link ComboRules}), soma o
 *   multiplicador do golpe final / ataque pelas costas como modificador transitorio de dano do jogador e tira parte
 *   da armadura do alvo (martelo);</li>
 *   <li>{@code AFTER_DAMAGE} do alvo: tira os modificadores e aplica o arco (montante, glaive), a onda de choque
 *   (martelo), a estocada (rapieira) e o efeito da arma de chefe ({@link BossWeapon});</li>
 *   <li>esquiva ({@link DodgePayload}): invulnerabilidade curta via {@code ALLOW_DAMAGE}.</li>
 * </ul>
 * Os modificadores que sobrarem (golpe que nao causou dano) saem no fim do tick.
 */
public final class CombatServer {
	private static final Identifier HIT_MODIFIER = MlinNotas.id("combat_hit");
	private static final Identifier PIERCE_MODIFIER = MlinNotas.id("combat_pierce");

	/** Estado de combate de um jogador. */
	private static final class PlayerState {
		ComboRules.State combo;
		long lastDodge = Long.MIN_VALUE / 2;
		long lastProc = Long.MIN_VALUE / 2;
		Pending pending;
	}

	/** Golpe em andamento (entre o inicio do ataque e o dano no alvo). */
	private record Pending(ServerPlayer player, LivingEntity target, WeaponClass weaponClass, BossWeapon bossWeapon, boolean charged,
			boolean finisher, boolean behind, LivingEntity pierced) {
	}

	private static final Map<UUID, PlayerState> STATES = new HashMap<>();
	/** Evita que os golpes extras (arco, onda, efeito) disparem efeitos de novo. */
	private static boolean applyingExtra;

	private CombatServer() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(DodgePayload.TYPE, DodgePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DodgePayload.TYPE, (payload, context) -> dodge(context.player(), payload.dx(), payload.dz()));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (player instanceof ServerPlayer serverPlayer && !player.isSpectator()) {
				beforeAttack(serverPlayer, entity);
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatServer::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && dodging(player, source)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (PlayerState state : STATES.values()) {
				clear(state);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			PlayerState state = STATES.remove(player.getUUID());
			if (state != null) {
				clear(state);
			}
		});
	}

	private static PlayerState state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}

	/** Zera combo, recargas e esquiva de um jogador (game tests). */
	public static void reset(Player player) {
		PlayerState state = STATES.remove(player.getUUID());
		if (state != null) {
			clear(state);
		}
	}

	/** Passo do combo do ultimo golpe do jogador (-1 = fraco/nenhum). */
	public static int comboStep(Player player) {
		PlayerState state = STATES.get(player.getUUID());
		return state == null || state.combo == null ? -1 : state.combo.step();
	}

	// ---------------------------------------------------------------- ataque

	private static void beforeAttack(ServerPlayer player, Entity entity) {
		if (applyingExtra) {
			return;
		}
		PlayerState state = state(player);
		clear(state);
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof WeaponItem weapon)) {
			state.combo = null;
			return;
		}
		LivingEntity target = living(entity);
		if (target == null) {
			return;
		}
		WeaponClass weaponClass = weapon.weaponClass();
		long now = now(player);
		float charge = player.getAttackStrengthScale(0.5F);
		int cooldown = Math.round(20.0F / weaponClass.attackSpeed());
		state.combo = ComboRules.next(state.combo, stack.getItem().getDescriptionId(), now, cooldown, charge);
		boolean charged = charge >= ComboRules.FULL_CHARGE;
		boolean finisher = charged && state.combo.finisher();
		boolean behind = charged && weaponClass.backstabMultiplier() > 1.0F
				&& ArcGeometry.behind(target.yBodyRot, target.getX(), target.getZ(), player.getX(), player.getZ());

		float multiplier = WeaponStats.hitMultiplier(weaponClass, finisher, behind);
		if (multiplier != 1.0F) {
			AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (damage != null) {
				damage.removeModifier(HIT_MODIFIER);
				damage.addTransientModifier(new AttributeModifier(HIT_MODIFIER, multiplier - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		LivingEntity pierced = null;
		if (weaponClass.armorPierce() > 0.0F && charged) {
			pierce(target, weaponClass.armorPierce());
			pierced = target;
		}
		// golpes rapidos (adaga) nao esbarram nos 10 ticks de invulnerabilidade do alvo
		if (charged && weaponClass.attackSpeed() > 2.0F && target.damageCooldownTime > 10) {
			target.damageCooldownTime = 10;
		}
		state.pending = new Pending(player, target, weaponClass, weapon.bossWeapon(), charged, finisher, behind, pierced);
	}

	private static LivingEntity living(Entity entity) {
		if (entity instanceof EnderDragonPart part) {
			return part.parentMob;
		}
		return entity instanceof LivingEntity living ? living : null;
	}

	private static void pierce(LivingEntity target, float fraction) {
		for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
			AttributeInstance instance = target.getAttribute(attribute);
			if (instance != null) {
				instance.removeModifier(PIERCE_MODIFIER);
				instance.addTransientModifier(new AttributeModifier(PIERCE_MODIFIER, -fraction, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	/** Tira os modificadores transitorios do golpe pendente (dano do jogador, armadura do alvo). */
	private static void clear(PlayerState state) {
		Pending pending = state.pending;
		state.pending = null;
		if (pending == null) {
			return;
		}
		AttributeInstance damage = pending.player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.removeModifier(HIT_MODIFIER);
		}
		if (pending.pierced != null) {
			for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
				AttributeInstance instance = pending.pierced.getAttribute(attribute);
				if (instance != null) {
					instance.removeModifier(PIERCE_MODIFIER);
				}
			}
		}
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (applyingExtra || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player
				|| !source.is(DamageTypes.PLAYER_ATTACK)) {
			return;
		}
		PlayerState state = STATES.get(player.getUUID());
		if (state == null || state.pending == null || state.pending.target != entity) {
			return;
		}
		Pending hit = state.pending;
		clear(state);
		ServerLevel level = player.level();
		applyingExtra = true;
		try {
			feedback(level, player, hit);
			if (hit.charged) {
				extraHits(level, player, hit, baseDamage);
				if (hit.bossWeapon != null) {
					long now = now(player);
					if (hit.bossWeapon.proc() == BossWeapon.Proc.FIRE_WAVE) {
						hit.target.igniteForSeconds(4.0F);
					}
					if (hit.bossWeapon.canProc(now, state.lastProc, true, hit.finisher, hit.behind)) {
						state.lastProc = now;
						proc(level, player, hit, baseDamage);
					}
				}
			}
		} finally {
			applyingExtra = false;
		}
	}

	/** Som e particulas do golpe: o golpe final e mais grave e solta faiscas. */
	private static void feedback(ServerLevel level, ServerPlayer player, Pending hit) {
		if (hit.finisher) {
			sound(level, player.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.CRIT, hit.target.getX(), hit.target.getY(0.6), hit.target.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
		}
		if (hit.behind) {
			sound(level, player.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.4F);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, hit.target.getX(), hit.target.getY(0.6), hit.target.getZ(), 10, 0.25, 0.3, 0.25, 0.2);
		}
	}

	private static void extraHits(ServerLevel level, ServerPlayer player, Pending hit, float baseDamage) {
		WeaponClass weaponClass = hit.weaponClass;
		if (weaponClass.sweeps()) {
			double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			for (LivingEntity other : nearby(level, player, player.getBoundingBox().inflate(reach + 1.0, 1.0, reach + 1.0), hit.target)) {
				if (ArcGeometry.inArc(player.getX(), player.getY(), player.getZ(), player.getYRot(), other.getX(), other.getY(), other.getZ(),
						other.getBbWidth() / 2.0, reach, weaponClass.arcDegrees())) {
					splash(level, player, other, baseDamage * weaponClass.splashRatio(), 0.4F);
				}
			}
			sweepParticles(level, player, weaponClass.arcDegrees(), Math.min(reach, 3.5));
			sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, hit.finisher ? 0.7F : 0.9F);
		}
		if (weaponClass.shockwave()) {
			boolean quake = hit.bossWeapon != null && hit.bossWeapon.proc() == BossWeapon.Proc.QUAKE;
			if (!quake || !hit.bossWeapon.canProc(now(player), state(player).lastProc, true, hit.finisher, hit.behind)) {
				shockwave(level, player, hit.target, weaponClass.shockwaveRadius(), baseDamage * weaponClass.splashRatio(), false);
			}
		}
		if (weaponClass.thrustPierces() && hit.finisher) {
			for (LivingEntity other : nearby(level, player, hit.target.getBoundingBox().inflate(2.5, 1.0, 2.5), hit.target)) {
				if (ArcGeometry.behindInLine(player.getX(), player.getZ(), player.getYRot(), hit.target.getX(), hit.target.getZ(), other.getX(),
						other.getZ(), 2.5, 0.9)) {
					splash(level, player, other, baseDamage * weaponClass.splashRatio(), 0.2F);
					level.sendParticles(ParticleTypes.CRIT, other.getX(), other.getY(0.6), other.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
					break;
				}
			}
		}
	}

	private static void shockwave(ServerLevel level, ServerPlayer player, LivingEntity center, float radius, float damage, boolean heavy) {
		for (LivingEntity other : nearby(level, player, center.getBoundingBox().inflate(radius + 1.0, 1.0, radius + 1.0), center)) {
			if (ArcGeometry.inRadius(center.getX(), center.getY(), center.getZ(), other.getX(), other.getY(), other.getZ(), other.getBbWidth() / 2.0,
					radius)) {
				splash(level, player, other, damage, heavy ? 0.7F : 0.4F);
				if (heavy) {
					other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), player);
					other.push(0.0, 0.35, 0.0);
				}
			}
		}
		if (heavy) {
			center.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), player);
		}
		ring(level, center.position(), radius, heavy ? ParticleTypes.EXPLOSION : ParticleTypes.CLOUD, heavy ? 10 : 18);
		sound(level, center.position(), heavy ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND, 1.0F, heavy ? 0.8F : 1.0F);
	}

	private static void proc(ServerLevel level, ServerPlayer player, Pending hit, float baseDamage) {
		BossWeapon weapon = hit.bossWeapon;
		LivingEntity target = hit.target;
		switch (weapon.proc()) {
			case QUAKE -> shockwave(level, player, target, weapon.procRadius(), baseDamage * weapon.procDamage(), true);
			case SCULK_BIND -> {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3), player);
				for (LivingEntity other : nearby(level, player, target.getBoundingBox().inflate(weapon.procRadius()), null)) {
					other.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), player);
				}
				level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY(0.5), target.getZ(), 14, 0.4, 0.5, 0.4, 0.03);
				ring(level, target.position(), weapon.procRadius(), ParticleTypes.SCULK_CHARGE_POP, 24);
				sound(level, target.position(), SoundEvents.SCULK_CLICKING, 1.2F, 0.8F);
			}
			case FIRE_WAVE -> {
				for (LivingEntity other : nearby(level, player, target.getBoundingBox().inflate(weapon.procRadius()), null)) {
					if (other == target || ArcGeometry.inRadius(target.getX(), target.getY(), target.getZ(), other.getX(), other.getY(), other.getZ(),
							other.getBbWidth() / 2.0, weapon.procRadius())) {
						splash(level, player, other, weapon.procDamage(), 0.3F, player.damageSources().indirectMagic(player, player));
						other.igniteForSeconds(3.0F);
					}
				}
				ring(level, target.position(), weapon.procRadius(), ParticleTypes.FLAME, 28);
				sound(level, target.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 0.7F);
			}
			case VOID_BURST -> {
				for (LivingEntity other : nearby(level, player, target.getBoundingBox().inflate(weapon.procRadius()), null)) {
					splash(level, player, other, weapon.procDamage(), 0.1F, player.damageSources().indirectMagic(player, player));
				}
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(0.5), target.getZ(), 30, 0.6, 0.5, 0.6, 0.05);
				sound(level, target.position(), SoundEvents.ENDER_DRAGON_SHOOT, 0.7F, 1.4F);
			}
			case STAR_BOLT -> {
				LivingEntity bolt = target;
				double best = Double.MAX_VALUE;
				for (LivingEntity other : nearby(level, player, player.getBoundingBox().inflate(weapon.procRadius()), target)) {
					double distance = other.distanceToSqr(player);
					if (other instanceof Enemy && distance < best && player.hasLineOfSight(other)) {
						best = distance;
						bolt = other;
					}
				}
				splash(level, player, bolt, weapon.procDamage(), 0.2F, player.damageSources().indirectMagic(player, player));
				Vec3 from = player.getEyePosition();
				Vec3 to = bolt.position().add(0.0, bolt.getBbHeight() * 0.6, 0.0);
				int steps = Math.max(4, (int) (from.distanceTo(to) * 3.0));
				for (int i = 0; i <= steps; i++) {
					Vec3 point = from.lerp(to, i / (double) steps);
					level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0);
				}
				sound(level, bolt.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.6F);
			}
		}
	}

	/** Entidades vivas que podem levar golpe extra: sem o jogador, outros jogadores, aliados, aldeoes e pets dele. */
	private static List<LivingEntity> nearby(ServerLevel level, ServerPlayer player, AABB box, LivingEntity except) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (other == player || other == except || !other.isAlive() || other instanceof Player || other instanceof Npc
					|| player.isAlliedTo(other) || (other instanceof ArmorStand stand && stand.isMarker())
					|| (other instanceof OwnableEntity pet && pet.getOwner() == player)) {
				continue;
			}
			out.add(other);
		}
		return out;
	}

	private static void splash(ServerLevel level, ServerPlayer player, LivingEntity other, float damage, float knockback) {
		splash(level, player, other, damage, knockback, player.damageSources().playerAttack(player));
	}

	private static void splash(ServerLevel level, ServerPlayer player, LivingEntity other, float damage, float knockback, DamageSource source) {
		if (damage <= 0.0F) {
			return;
		}
		if (other.hurtServer(level, source, damage) && knockback > 0.0F) {
			other.knockback(knockback, Mth.sin(player.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(player.getYRot() * Mth.DEG_TO_RAD), source, damage);
		}
	}

	private static void sweepParticles(ServerLevel level, ServerPlayer player, float arcDegrees, double distance) {
		int count = arcDegrees >= 140.0F ? 5 : 3;
		for (int i = 0; i < count; i++) {
			float yaw = player.getYRot() - arcDegrees / 2.0F + arcDegrees * (i + 0.5F) / count;
			double[] look = ArcGeometry.look(yaw);
			double d = distance * 0.7;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look[0] * d, player.getY(0.5), player.getZ() + look[1] * d, 0, look[0], 0.0,
					look[1], 0.0);
		}
	}

	private static void ring(ServerLevel level, Vec3 center, double radius, ParticleOptions particle, int count) {
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0 * i / count;
			level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + 0.2, center.z + Math.sin(angle) * radius, 1, 0.05, 0.05,
					0.05, 0.01);
		}
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	// ---------------------------------------------------------------- esquiva

	/** Pedido de esquiva do cliente: valida as regras, gasta fome e abre a janela de invulnerabilidade. */
	public static boolean dodge(ServerPlayer player, float dx, float dz) {
		if (player.isSpectator() || !Float.isFinite(dx) || !Float.isFinite(dz)) {
			return false;
		}
		PlayerState state = state(player);
		long now = now(player);
		// quando o pacote chega o cliente ja saiu do chao: o servidor confere so recarga e fome
		DodgeRules.Check check = DodgeRules.check(now, state.lastDodge, player.getFoodData().getFoodLevel(), true,
				player.getAbilities().instabuild);
		if (check != DodgeRules.Check.OK) {
			return false;
		}
		state.lastDodge = now;
		player.causeFoodExhaustion(DodgeRules.EXHAUSTION);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
		sound(level, player.position(), SoundEvents.BREEZE_JUMP, 0.6F, 1.3F);
		return true;
	}

	/** Esta nos ticks de invulnerabilidade da esquiva (dano que ignora invulnerabilidade, como /kill, passa). */
	public static boolean dodging(ServerPlayer player, DamageSource source) {
		PlayerState state = STATES.get(player.getUUID());
		if (state == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return DodgeRules.invulnerable(now(player), state.lastDodge);
	}
}
