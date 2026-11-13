package com.mlin.notas.client;

import com.mlin.notas.end.ElevatorPayload;
import com.mlin.notas.end.Elevators;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Detecta pulo/agachar em cima de um Elevador de Purpur (borda da tecla: um aperto = um andar) e pede ao servidor
 * para teleportar ({@link ElevatorPayload}).
 */
public final class ElevatorClient {
	private static boolean lastJump;
	private static boolean lastSneak;

	private ElevatorClient() {
	}

	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null) {
			lastJump = false;
			lastSneak = false;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown();
		boolean sneak = minecraft.options.keyShift.isDown();
		boolean onElevator = player.onGround() && Elevators.elevatorBelow(player.level(), player.getX(), player.getY(), player.getZ()) != null;
		if (onElevator && ClientPlayNetworking.canSend(ElevatorPayload.TYPE)) {
			if (jump && !lastJump) {
				ClientPlayNetworking.send(new ElevatorPayload(true));
			} else if (sneak && !lastSneak) {
				ClientPlayNetworking.send(new ElevatorPayload(false));
			}
		}
		lastJump = jump;
		lastSneak = sneak;
	}
}
