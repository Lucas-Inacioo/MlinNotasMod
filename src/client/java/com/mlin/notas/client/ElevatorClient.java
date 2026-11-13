package com.mlin.notas.client;

import com.mlin.notas.end.ElevatorPayload;
import com.mlin.notas.end.Elevators;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Detecta pulo/agachar em cima de um Elevador de Purpur (borda da tecla: um aperto = um andar) e pede ao servidor
 * para teleportar ({@link ElevatorPayload}).
 * <p>
 * Roda no fim do tick do cliente, depois do jogador ja ter processado o pulo: nesse momento ele ja saiu do chao e o
 * bloco logo abaixo dos pes e ar. Por isso vale "estava no elevador no tick anterior" (ou ainda esta) e o
 * {@link Elevators#elevatorBelow} procura alguns blocos abaixo.
 */
public final class ElevatorClient {
	private static boolean lastJump;
	private static boolean lastSneak;
	private static boolean wasOnElevator;

	private ElevatorClient() {
	}

	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null) {
			lastJump = false;
			lastSneak = false;
			wasOnElevator = false;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown();
		boolean sneak = minecraft.options.keyShift.isDown();
		boolean nearElevator = Elevators.elevatorBelow(player.level(), player.getX(), player.getY(), player.getZ()) != null;
		boolean onElevator = nearElevator && player.onGround();
		if ((onElevator || (wasOnElevator && nearElevator)) && ClientPlayNetworking.canSend(ElevatorPayload.TYPE)) {
			if (jump && !lastJump) {
				ClientPlayNetworking.send(new ElevatorPayload(true));
			} else if (sneak && !lastSneak) {
				ClientPlayNetworking.send(new ElevatorPayload(false));
			}
		}
		lastJump = jump;
		lastSneak = sneak;
		wasOnElevator = onElevator;
	}
}
