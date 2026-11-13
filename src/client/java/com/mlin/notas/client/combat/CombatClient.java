package com.mlin.notas.client.combat;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.combat.ComboRules;
import com.mlin.notas.combat.DodgePayload;
import com.mlin.notas.combat.DodgeRules;
import com.mlin.notas.combat.WeaponClass;
import com.mlin.notas.combat.WeaponItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Cliente do "Combate melhor": tecla de esquiva (Alt esquerdo; o impulso e local, o servidor so da a invulnerabilidade
 * e confere recarga/fome) e um HUD discreto abaixo da mira com os passos do combo da arma na mao e a recarga da
 * esquiva. O combo do HUD e previsto no cliente com as mesmas regras do servidor ({@link ComboRules}).
 */
public final class CombatClient {
	public static final KeyMapping DODGE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mlinnotas.dodge", InputConstants.KEY_LALT,
			ModKeys.CATEGORY));

	private static long ticks;
	private static long lastDodge = Long.MIN_VALUE / 2;
	private static ComboRules.State combo;
	private static int comboCooldown = 20;

	private CombatClient() {
	}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			combo = null;
			lastDodge = Long.MIN_VALUE / 2;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ticks++;
			if (client.player == null) {
				return;
			}
			while (DODGE.consumeClick()) {
				tryDodge(client.player);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				ItemStack stack = player.getMainHandItem();
				WeaponClass weaponClass = WeaponItem.classOf(stack);
				if (weaponClass == null) {
					combo = null;
				} else {
					comboCooldown = Math.round(20.0F / weaponClass.attackSpeed());
					combo = ComboRules.next(combo, stack.getItem().getDescriptionId(), ticks, comboCooldown, player.getAttackStrengthScale(0.5F));
				}
			}
			return InteractionResult.PASS;
		});
		HudElementRegistry.addLast(MlinNotas.id("combat"), CombatClient::render);
	}

	/** Tenta esquivar agora (usado tambem pelo client game test). */
	public static boolean tryDodge(LocalPlayer player) {
		if (player.isSpectator() || player.getAbilities().flying || player.isPassenger() || !ClientPlayNetworking.canSend(DodgePayload.TYPE)) {
			return false;
		}
		DodgeRules.Check check = DodgeRules.check(ticks, lastDodge, player.getFoodData().getFoodLevel(), player.onGround(),
				player.getAbilities().instabuild);
		if (check != DodgeRules.Check.OK) {
			return false;
		}
		Vec2 move = player.input.getMoveVector();
		double[] direction = DodgeRules.direction(move.x, move.y, player.getYRot());
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(direction[0] * DodgeRules.HORIZONTAL_SPEED, Math.max(motion.y, DodgeRules.VERTICAL_SPEED),
				direction[1] * DodgeRules.HORIZONTAL_SPEED);
		lastDodge = ticks;
		ClientPlayNetworking.send(new DodgePayload((float) direction[0], (float) direction[1]));
		return true;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
		int y = minecraft.getWindow().getGuiScaledHeight() / 2 + 10;
		boolean holdingWeapon = WeaponItem.classOf(player.getMainHandItem()) != null;
		if (holdingWeapon && ComboRules.alive(combo, ticks, comboCooldown)) {
			// tres losangos: cheios ate o passo atual; o ultimo (golpe final) dourado
			for (int i = 0; i < WeaponClass.COMBO_LENGTH; i++) {
				int x = centerX - 7 + i * 6;
				boolean filled = i <= combo.step();
				boolean finisher = i == WeaponClass.COMBO_LENGTH - 1;
				int color = filled ? (finisher ? 0xFFFFC94A : 0xFFE8E8E8) : 0x60FFFFFF;
				graphics.fill(x + 1, y, x + 2, y + 3, color);
				graphics.fill(x, y + 1, x + 3, y + 2, color);
			}
			y += 5;
		}
		float progress = DodgeRules.cooldownProgress(ticks, lastDodge);
		if (progress < 1.0F) {
			int width = 16;
			graphics.fill(centerX - width / 2, y, centerX + width / 2, y + 1, 0x60000000);
			graphics.fill(centerX - width / 2, y, centerX - width / 2 + Math.round(width * progress), y + 1, 0xC07FD8FF);
		}
	}
}
