package com.mlin.notas.accessories;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.boss.AbyssalGuardian;
import com.mlin.notas.dungeon.DungeonBosses;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lado servidor dos acessorios: abre o menu, aplica os bonus como modificadores transientes
 * {@code mlinnotas:accessory_*} (cortados pelo teto do estagio do mundo), efeitos especiais (pulo duplo, escalada,
 * ima, lanterna, sol, espinhos, gelo, murchar, roubo de vida, sempre no maximo 1 por segundo), reducao de dano
 * (chamada pelo mixin {@code LivingEntityAccessoryMixin}), drop na morte e os pools extras de loot.
 */
public final class AccessoryServer {
	/** Como cada atributo somado vira modificador. */
	private record AttributeSpec(Holder<Attribute> attribute, AttributeModifier.Operation operation, double sign) {
	}

	private static final Map<AccessoryStat, AttributeSpec> ATTRIBUTES = new EnumMap<>(AccessoryStat.class);

	static {
		ATTRIBUTES.put(AccessoryStat.DAMAGE, new AttributeSpec(Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 1));
		ATTRIBUTES.put(AccessoryStat.SPEED, new AttributeSpec(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 1));
		ATTRIBUTES.put(AccessoryStat.MAX_HEALTH, new AttributeSpec(Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, 1));
		ATTRIBUTES.put(AccessoryStat.KNOCKBACK_RESISTANCE, new AttributeSpec(Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE, 1));
		ATTRIBUTES.put(AccessoryStat.FALL_REDUCTION, new AttributeSpec(Attributes.FALL_DAMAGE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, -1));
		ATTRIBUTES.put(AccessoryStat.MINING_SPEED, new AttributeSpec(Attributes.BLOCK_BREAK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 1));
		ATTRIBUTES.put(AccessoryStat.ATTACK_SPEED, new AttributeSpec(Attributes.ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 1));
		ATTRIBUTES.put(AccessoryStat.LUCK, new AttributeSpec(Attributes.LUCK, AttributeModifier.Operation.ADD_VALUE, 1));
		ATTRIBUTES.put(AccessoryStat.FIRE_REDUCTION, new AttributeSpec(Attributes.BURNING_TIME, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, -1));
		ATTRIBUTES.put(AccessoryStat.STEP_HEIGHT, new AttributeSpec(Attributes.STEP_HEIGHT, AttributeModifier.Operation.ADD_VALUE, 1));
		ATTRIBUTES.put(AccessoryStat.OXYGEN, new AttributeSpec(Attributes.OXYGEN_BONUS, AttributeModifier.Operation.ADD_VALUE, 1));
		ATTRIBUTES.put(AccessoryStat.SWIM_SPEED, new AttributeSpec(Attributes.WATER_MOVEMENT_EFFICIENCY, AttributeModifier.Operation.ADD_VALUE, 1));
	}

	/** Visao noturna da lanterna: 15 s, uma vez a cada 60 s. */
	public static final int LANTERN_DURATION = 300;
	public static final int LANTERN_RECHARGE = 1200;
	/** Sol: meio coracao a cada 5 s. */
	public static final int SUNLIGHT_INTERVAL = 100;
	public static final float THORNS_DAMAGE = 2.0F;
	public static final double MAGNET_RANGE = 6.0;

	/** Estado em memoria por jogador. */
	private static final class State {
		AccessoryBudget.Totals totals = AccessoryBudget.Totals.EMPTY;
		final Map<AccessoryStat, Double> appliedModifiers = new EnumMap<>(AccessoryStat.class);
		final ProcCooldowns procs = new ProcCooldowns();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private AccessoryServer() {
	}

	public static Identifier modifierId(AccessoryStat stat) {
		return MlinNotas.id("accessory_" + stat.id());
	}

	public static void init() {
		AccessoryRegistry.init();
		// registra o attachment ja na inicializacao: a sincronizacao so vale para tipos que o cliente conhece ao conectar
		Objects.requireNonNull(AccessoryData.EQUIPPED);
		PayloadTypeRegistry.serverboundPlay().register(AccessoryPayloads.Open.TYPE, AccessoryPayloads.Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AccessoryPayloads.DoubleJump.TYPE, AccessoryPayloads.DoubleJump.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AccessoryPayloads.Open.TYPE, (payload, context) -> open(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(AccessoryPayloads.DoubleJump.TYPE, (payload, context) -> doubleJump(context.player()));

		ServerPlayerEvents.JOIN.register(AccessoryServer::refresh);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			STATES.remove(oldPlayer.getUUID());
			refresh(newPlayer);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(AccessoryServer::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(AccessoryServer::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(AccessoryServer::afterDeath);
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			ArtifactLoot.Pool pool = ArtifactLoot.poolFor(key.identifier().toString());
			if (pool == null) {
				return;
			}
			LootPool.Builder extra = LootPool.lootPool();
			if (pool.chance() < 1.0) {
				extra.when(LootItemRandomChanceCondition.randomChance((float) pool.chance()));
			}
			for (String id : pool.items()) {
				extra.add(LootItem.lootTableItem(AccessoryRegistry.item(id)));
			}
			builder.withPool(extra);
		});
	}

	// ---- estagio e totais ----

	public static AccessoryStage stage(MinecraftServer server) {
		return AccessoryStage.fromMiningLevel(SkillServer.progress(server).level(SkillBranch.MINING));
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), uuid -> new State());
	}

	/** Totais ja cortados do jogador (calcula se ainda nao tiver). */
	public static AccessoryBudget.Totals totals(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state == null) {
			refresh(player);
			state = state(player);
		}
		return state.totals;
	}

	/** Recalcula a soma com o estagio atual e atualiza os modificadores que mudaram. */
	public static void refresh(ServerPlayer player) {
		State state = state(player);
		AccessoryBudget.Totals totals = AccessoryBudget.compute(AccessoryData.defs(player), stage(player.level().getServer()));
		state.totals = totals;
		for (Map.Entry<AccessoryStat, AttributeSpec> entry : ATTRIBUTES.entrySet()) {
			AccessoryStat stat = entry.getKey();
			double amount = totals.applied(stat) * entry.getValue().sign();
			Double previous = state.appliedModifiers.get(stat);
			if (previous != null && previous == amount) {
				continue;
			}
			AttributeInstance instance = player.getAttribute(entry.getValue().attribute());
			if (instance == null) {
				continue;
			}
			if (amount == 0) {
				instance.removeModifier(modifierId(stat));
			} else {
				instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId(stat), amount, entry.getValue().operation()));
			}
			state.appliedModifiers.put(stat, amount);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Multiplicador do dano recebido pelo jogador (mixin no calculo de dano). */
	public static double incomingFactor(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1.0;
		}
		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		boolean sonic = source.is(DamageTypes.SONIC_BOOM)
				|| source.getEntity() instanceof AbyssalGuardian && source.is(DamageTypeTags.BYPASSES_ARMOR);
		boolean magic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.DRAGON_BREATH);
		boolean blast = source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL);
		return AccessoryBudget.incomingFactor(totals(player), fire, sonic, magic, blast);
	}

	public static ProcCooldowns procs(ServerPlayer player) {
		return state(player).procs;
	}

	// ---- equipar ----

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AccessoryMenu(id, inventory, new AccessoryContainer(p)),
				Component.translatable("container.mlinnotas.accessories")));
	}

	/** Botao direito com o acessorio: equipa no primeiro encaixe livre do tipo. */
	public static boolean equipFromHand(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (equip(player, stack)) {
			player.setItemInHand(hand, ItemStack.EMPTY);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
			return true;
		}
		if (player.connection != null) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.accessories.no_slot"));
		}
		return false;
	}

	/** Poe uma copia de {@code stack} no primeiro encaixe livre (nao mexe na pilha original). */
	public static boolean equip(ServerPlayer player, ItemStack stack) {
		AccessoryDef def = AccessoryData.def(stack);
		if (def == null) {
			return false;
		}
		int free = AccessorySlots.firstFree(AccessoryData.defs(player), def);
		if (free < 0) {
			return false;
		}
		AccessoryData.set(player, free, stack.copyWithCount(1));
		refresh(player);
		return true;
	}

	/** Quantos acessorios equipados casam com {@code matcher} (quests "tenha X" contam o que esta equipado). */
	public static int countEquipped(ServerPlayer player, java.util.function.Predicate<ItemStack> matcher) {
		int count = 0;
		for (ItemStack stack : AccessoryData.get(player)) {
			if (!stack.isEmpty() && matcher.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	// ---- tick: efeitos continuos ----

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (tick % 20 == 0) {
				refresh(player);
			}
			AccessoryBudget.Totals totals = totals(player);
			if (totals.abilities().isEmpty() || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			if (totals.has(AccessoryAbility.CLIMB) && touchingWall(player)) {
				player.resetFallDistance();
			}
			if (totals.has(AccessoryAbility.MAGNET) && tick % 4 == 0 && !player.isShiftKeyDown()) {
				magnet(player);
			}
			if (tick % 20 == 0) {
				if (totals.has(AccessoryAbility.LANTERN)) {
					lantern(player, tick);
				}
				if (totals.has(AccessoryAbility.SUNLIGHT)) {
					sunlight(player, tick);
				}
			}
		}
	}

	/** Encostado numa parede (lados do corpo, sem contar o chao). */
	public static boolean touchingWall(ServerPlayer player) {
		AABB box = player.getBoundingBox();
		AABB sides = new AABB(box.minX - 0.08, box.minY + 0.1, box.minZ - 0.08, box.maxX + 0.08, box.maxY - 0.1, box.maxZ + 0.08);
		return !player.level().noCollision(player, sides);
	}

	private static void magnet(ServerPlayer player) {
		AABB area = player.getBoundingBox().inflate(MAGNET_RANGE);
		Vec3 target = player.position().add(0, 0.5, 0);
		for (Entity entity : player.level().getEntitiesOfClass(Entity.class, area,
				e -> e.isAlive() && (e instanceof ItemEntity item && !item.hasPickUpDelay() || e instanceof ExperienceOrb))) {
			Vec3 delta = target.subtract(entity.position());
			if (delta.lengthSqr() < 0.5 || delta.lengthSqr() > MAGNET_RANGE * MAGNET_RANGE) {
				continue;
			}
			entity.setDeltaMovement(delta.normalize().scale(0.35));
			entity.needsSync = true;
		}
	}

	private static void lantern(ServerPlayer player, long tick) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		if (level.canSeeSky(pos) || level.getMaxLocalRawBrightness(pos) >= 5) {
			return;
		}
		MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
		if (current != null && current.getDuration() > 20) {
			return;
		}
		if (procs(player).tryProc("lantern", tick, LANTERN_RECHARGE)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LANTERN_DURATION, 0, true, false, true));
		}
	}

	private static void sunlight(ServerPlayer player, long tick) {
		ServerLevel level = player.level();
		if (player.getHealth() >= player.getMaxHealth() || !level.isBrightOutside() || !level.canSeeSky(player.blockPosition().above())) {
			return;
		}
		if (procs(player).tryProc("sunlight", tick, SUNLIGHT_INTERVAL)) {
			player.heal(1.0F);
		}
	}

	private static void doubleJump(ServerPlayer player) {
		if (player.onGround() || player.isSpectator() || !totals(player).has(AccessoryAbility.DOUBLE_JUMP)) {
			return;
		}
		if (!procs(player).tryProc("double_jump", player.level().getServer().getTickCount(), 10)) {
			return;
		}
		player.resetFallDistance();
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 0.8F, 1.3F);
	}

	// ---- combate ----

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity.level() instanceof ServerLevel level) || blocked) {
			return;
		}
		long tick = level.getServer().getTickCount();
		// espinhos: quem bate corpo a corpo leva dano
		if (entity instanceof ServerPlayer victim && source.getEntity() instanceof LivingEntity attacker && attacker != victim
				&& source.getDirectEntity() == attacker && !source.is(DamageTypes.THORNS) && totals(victim).has(AccessoryAbility.THORNS)
				&& procs(victim).tryProc(AccessoryAbility.THORNS.id(), tick)) {
			attacker.hurtServer(level, victim.damageSources().thorns(victim), THORNS_DAMAGE);
		}
		// golpes corpo a corpo do jogador
		if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && entity != attacker && damageTaken > 0
				&& !source.is(DamageTypes.THORNS)) {
			AccessoryBudget.Totals totals = totals(attacker);
			ProcCooldowns procs = procs(attacker);
			double heal = AccessoryBudget.lifesteal(totals, damageTaken);
			if (heal > 0 && procs.tryProc("lifesteal", tick)) {
				attacker.heal((float) heal);
			}
			if (totals.has(AccessoryAbility.FROST) && procs.tryProc(AccessoryAbility.FROST.id(), tick)) {
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), attacker);
				level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY(0.5), entity.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
			}
			if (totals.has(AccessoryAbility.WITHER_TOUCH) && procs.tryProc(AccessoryAbility.WITHER_TOUCH.id(), tick)) {
				entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), attacker);
			}
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof ServerPlayer player) {
			if (!level.getGameRules().get(GameRules.KEEP_INVENTORY) && !player.isSpectator()) {
				dropAll(player);
			}
			return;
		}
		if (entity.hasAttached(DungeonBosses.BOSS) && level.getRandom().nextDouble() < ArtifactLoot.CHAMPION_SEAL_CHANCE) {
			entity.spawnAtLocation(level, new ItemStack(AccessoryRegistry.item("champion_seal")));
		}
	}

	/** Solta tudo que esta equipado (morte sem keepInventory) e esvazia os encaixes. */
	public static void dropAll(ServerPlayer player) {
		List<ItemStack> equipped = AccessoryData.get(player);
		for (ItemStack stack : equipped) {
			if (!stack.isEmpty()) {
				ItemEntity drop = player.createItemStackToDrop(stack.copy(), true, false);
				if (drop != null) {
					player.level().addFreshEntity(drop);
				}
			}
		}
		AccessoryData.clear(player);
		refresh(player);
	}
}
