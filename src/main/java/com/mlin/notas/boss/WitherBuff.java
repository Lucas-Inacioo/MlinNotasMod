package com.mlin.notas.boss;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;

/**
 * Wither reforcado (sem mixin): ao nascer ganha {@link BossRules#WITHER_HEALTH} de vida (+50% por jogador alem de 2 a
 * ate {@link BossRules#PLAYER_SCAN_RADIUS} blocos), uma vez por wither (marca persistente). Ele nasce carregando
 * (invulneravel), entao a vida ja comeca cheia. O loot do wither (Molde Celestial) esta em
 * {@code data/minecraft/loot_table/entities/wither.json}.
 */
public final class WitherBuff {
	public static final AttachmentType<Boolean> SCALED = AttachmentRegistry.create(MlinNotas.id("wither_scaled"), b -> b.persistent(Codec.BOOL));

	private WitherBuff() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof WitherBoss wither && !wither.hasAttached(SCALED)) {
				apply(level, wither);
			}
		});
	}

	public static void apply(ServerLevel level, WitherBoss wither) {
		AttributeInstance max = wither.getAttribute(Attributes.MAX_HEALTH);
		if (max == null) {
			return;
		}
		int players = level.getEntitiesOfClass(Player.class, wither.getBoundingBox().inflate(BossRules.PLAYER_SCAN_RADIUS),
				p -> !p.isSpectator() && p.isAlive()).size();
		double fraction = wither.getInvulnerableTicks() > 0 ? 1.0 : wither.getHealth() / wither.getMaxHealth();
		max.setBaseValue(BossRules.scaledHealth(BossRules.WITHER_HEALTH, players));
		wither.setHealth((float) (wither.getMaxHealth() * fraction));
		wither.setAttached(SCALED, true);
	}
}
