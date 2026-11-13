package com.mlin.notas.minimap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Lado servidor do minimapa: teleporte a partir do mapa. So para quem pode usar /tp
 * (operador nivel 2 ou singleplayer com cheats).
 */
public final class MinimapServer {
	private MinimapServer() {
	}

	public static boolean canTeleport(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** Devolve true se teleportou; caso contrario avisa o jogador e devolve false. */
	public static boolean teleport(ServerPlayer player, double x, double y, double z) {
		if (!canTeleport(player)) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.teleport.denied"));
			return false;
		}
		double clampedY = Math.max(player.level().getMinY() + 1, Math.min(player.level().getMaxY() - 1, y));
		player.teleportTo(x, clampedY, z);
		player.sendOverlayMessage(Component.translatable("message.mlinnotas.teleport.done", (int) Math.floor(x), (int) Math.floor(clampedY), (int) Math.floor(z)));
		return true;
	}
}
