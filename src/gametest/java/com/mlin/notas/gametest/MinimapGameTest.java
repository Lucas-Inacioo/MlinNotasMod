package com.mlin.notas.gametest;

import com.mlin.notas.minimap.MinimapServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * Game tests do minimapa (lado servidor): teleporte a partir do mapa.
 * O jogador mock e promovido a operador com PlayerList.op antes do teste.
 */
public class MinimapGameTest {
	@SuppressWarnings("removal")
	@GameTest(maxTicks = 60)
	public void teleportMovesOperatorPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.getLevel().getServer().getPlayerList().op(player.nameAndId(), java.util.Optional.of(LevelBasedPermissionSet.OWNER), java.util.Optional.empty());
		BlockPos target = helper.absolutePos(new BlockPos(3, 2, 3));
		try {
			helper.assertTrue(MinimapServer.canTeleport(player), "jogador operador pode teleportar");
			boolean moved = MinimapServer.teleport(player, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
			helper.assertTrue(moved, "teleporte deveria ser aceito");
			helper.assertTrue(player.blockPosition().equals(target), "jogador deveria estar em " + target + ", esta em " + player.blockPosition());
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void teleportClampsYToWorldLimits(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.getLevel().getServer().getPlayerList().op(player.nameAndId(), java.util.Optional.of(LevelBasedPermissionSet.OWNER), java.util.Optional.empty());
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
		try {
			MinimapServer.teleport(player, base.getX() + 0.5, 100000, base.getZ() + 0.5);
			helper.assertTrue(player.blockPosition().getY() < helper.getLevel().getMaxY(), "Y deveria ser limitado ao topo do mundo");
			MinimapServer.teleport(player, base.getX() + 0.5, -100000, base.getZ() + 0.5);
			helper.assertTrue(player.blockPosition().getY() > helper.getLevel().getMinY(), "Y deveria ser limitado ao fundo do mundo");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}
}
