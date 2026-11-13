package com.mlin.notas.dungeon;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mini-chefes das masmorras: mobs vanilla reforcados (vida, equipamento, nome, barra de chefe pequena) marcados com o
 * attachment persistente {@code mlinnotas:dungeon_boss} (id do tema). Nao somem; ao morrer soltam uma Chave da
 * Masmorra por jogador por perto (1 a 4): a chave abre o portao (sem gastar) e cada jogador gasta a sua no cofre.
 */
public final class DungeonBosses {
	public static final AttachmentType<String> BOSS = AttachmentRegistry.create(MlinNotas.id("dungeon_boss"), b -> b.persistent(Codec.STRING));
	/** Vida pensada para 2 jogadores com equipamento de ferro. */
	public static final double HEALTH = 80.0;
	public static final int BAR_RANGE = 24;
	public static final int KEY_RANGE = 32;

	private static final Map<Mob, ServerBossEvent> BARS = new IdentityHashMap<>();

	private DungeonBosses() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(BOSS)) {
				track(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				ServerBossEvent bar = BARS.remove(mob);
				if (bar != null) {
					bar.removeAllPlayers();
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && mob.hasAttached(BOSS) && mob.level() instanceof ServerLevel level) {
				dropKeys(level, mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 5 == 0 && !BARS.isEmpty()) {
				updateBars();
			}
		});
	}

	public static @Nullable DungeonTheme themeOf(Mob mob) {
		String id = mob.getAttached(BOSS);
		return id == null ? null : DungeonTheme.byId(id);
	}

	public static Component name(DungeonTheme theme) {
		return Component.translatable("entity.mlinnotas.dungeon_boss." + theme.id());
	}

	private static EntityType<? extends Mob> type(DungeonTheme theme) {
		return switch (theme) {
			case CRYPT -> EntityTypes.WITHER_SKELETON;
			case DWARF -> EntityTypes.VINDICATOR;
			case DROWNED -> EntityTypes.DROWNED;
			case SAND -> EntityTypes.HUSK;
		};
	}

	/** Cria o mini-chefe do tema (sem adicionar ao mundo), ja equipado, em {@code pos}. */
	public static @Nullable Mob create(ServerLevel level, DungeonTheme theme, BlockPos pos) {
		Mob mob = type(theme).create(level, EntitySpawnReason.STRUCTURE);
		if (mob == null) {
			return null;
		}
		mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		mob.setYRot(level.getRandom().nextFloat() * 360.0F);
		mob.setAttached(BOSS, theme.id());
		mob.setCustomName(name(theme));
		mob.setPersistenceRequired();
		mob.setHomeTo(pos, 10);
		attribute(mob, Attributes.MAX_HEALTH, HEALTH);
		attribute(mob, Attributes.ARMOR, 6.0);
		attribute(mob, Attributes.KNOCKBACK_RESISTANCE, 0.6);
		attribute(mob, Attributes.FOLLOW_RANGE, 32.0);
		switch (theme) {
			case CRYPT -> {
				equip(mob, EquipmentSlot.MAINHAND, Items.STONE_SWORD);
				equip(mob, EquipmentSlot.HEAD, Items.CHAINMAIL_HELMET);
				equip(mob, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
				attribute(mob, Attributes.ATTACK_DAMAGE, 6.0);
			}
			case DWARF -> {
				equip(mob, EquipmentSlot.MAINHAND, Items.IRON_AXE);
				equip(mob, EquipmentSlot.HEAD, Items.GOLDEN_HELMET);
				equip(mob, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
				attribute(mob, Attributes.ATTACK_DAMAGE, 7.0);
			}
			case DROWNED -> {
				equip(mob, EquipmentSlot.MAINHAND, Items.TRIDENT);
				equip(mob, EquipmentSlot.HEAD, Items.TURTLE_HELMET);
				equip(mob, EquipmentSlot.CHEST, Items.CHAINMAIL_CHESTPLATE);
				attribute(mob, Attributes.ATTACK_DAMAGE, 6.0);
			}
			case SAND -> {
				equip(mob, EquipmentSlot.MAINHAND, Items.GOLDEN_SWORD);
				equip(mob, EquipmentSlot.HEAD, Items.GOLDEN_HELMET);
				equip(mob, EquipmentSlot.CHEST, Items.GOLDEN_CHESTPLATE);
				equip(mob, EquipmentSlot.LEGS, Items.GOLDEN_LEGGINGS);
				attribute(mob, Attributes.ATTACK_DAMAGE, 6.0);
				attribute(mob, Attributes.MOVEMENT_SPEED, 0.26);
			}
		}
		mob.setHealth(mob.getMaxHealth());
		return mob;
	}

	private static void equip(Mob mob, EquipmentSlot slot, net.minecraft.world.item.Item item) {
		mob.setItemSlot(slot, new ItemStack(item));
		mob.setDropChance(slot, 0.0F);
	}

	private static void attribute(Mob mob, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void track(Mob mob) {
		DungeonTheme theme = themeOf(mob);
		if (theme == null || BARS.containsKey(mob)) {
			return;
		}
		BossEvent.BossBarColor color = switch (theme) {
			case CRYPT -> BossEvent.BossBarColor.WHITE;
			case DWARF -> BossEvent.BossBarColor.RED;
			case DROWNED -> BossEvent.BossBarColor.BLUE;
			case SAND -> BossEvent.BossBarColor.YELLOW;
		};
		BARS.put(mob, new ServerBossEvent(Mth.createInsecureUUID(mob.getRandom()), mob.getDisplayName(), color, BossEvent.BossBarOverlay.PROGRESS));
	}

	/** Barra de vida para os jogadores a ate {@link #BAR_RANGE} blocos. */
	private static void updateBars() {
		List<Mob> gone = new ArrayList<>();
		for (Map.Entry<Mob, ServerBossEvent> entry : BARS.entrySet()) {
			Mob mob = entry.getKey();
			ServerBossEvent bar = entry.getValue();
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				bar.removeAllPlayers();
				gone.add(mob);
				continue;
			}
			bar.setProgress(Mth.clamp(mob.getHealth() / mob.getMaxHealth(), 0.0F, 1.0F));
			for (ServerPlayer player : level.players()) {
				boolean near = !player.isSpectator() && player.distanceToSqr(mob) < BAR_RANGE * BAR_RANGE;
				if (near && !bar.getPlayers().contains(player)) {
					bar.addPlayer(player);
				} else if (!near && bar.getPlayers().contains(player)) {
					bar.removePlayer(player);
				}
			}
			for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
				if (player.level() != level) {
					bar.removePlayer(player);
				}
			}
		}
		gone.forEach(BARS::remove);
	}

	/** Quantas chaves o mini-chefe solta: uma por jogador por perto, de 1 a 4. */
	public static int keysFor(int playersNear) {
		return Mth.clamp(playersNear, 1, 4);
	}

	private static void dropKeys(ServerLevel level, Mob mob) {
		int near = 0;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(mob) < KEY_RANGE * KEY_RANGE) {
				near++;
			}
		}
		int keys = keysFor(near);
		mob.spawnAtLocation(level, new ItemStack(DungeonRegistry.KEY, keys));
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(mob) < KEY_RANGE * KEY_RANGE && player.connection != null) {
				player.sendSystemMessage(Component.translatable("message.mlinnotas.dungeon.boss_defeated", mob.getDisplayName(), keys));
			}
		}
		ServerBossEvent bar = BARS.remove(mob);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** Para testes: o mob esta sendo acompanhado (barra de chefe). */
	public static boolean tracked(Mob mob) {
		return BARS.containsKey(mob);
	}

}
