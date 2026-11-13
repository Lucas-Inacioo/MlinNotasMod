package com.mlin.notas.gametest;

import com.mlin.notas.compass.CompassKind;
import com.mlin.notas.compass.CompassServer;
import com.mlin.notas.compass.CompassTarget;
import com.mlin.notas.compass.Compasses;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Game tests da queda rapida de folhas e da bussola de biomas (lote proprio {@code qol_extras}).
 */
public class ExplorationQolGameTest {
	private static final String ENV = "mlinnotas-gametest:qol_extras";

	/** Tronco de 4 com copa natural: cortado o tronco, as folhas naturais somem em segundos; a colocada fica. */
	@GameTest(environment = ENV, maxTicks = 260)
	public void leavesDecayFastAfterTheTrunkIsCut(GameTestHelper helper) {
		List<BlockPos> natural = new ArrayList<>();
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(3, y, 3, Blocks.OAK_LOG);
		}
		for (int y = 3; y <= 5; y++) {
			int reach = y == 5 ? 1 : 2;
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (y <= 4 && dx == 0 && dz == 0) {
						continue;
					}
					int distance = Math.abs(dx) + Math.abs(dz) + (y == 5 ? 1 : 0);
					BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, distance);
					helper.setBlock(3 + dx, y, 3 + dz, leaf);
					natural.add(new BlockPos(3 + dx, y, 3 + dz));
				}
			}
		}
		// folha colocada pelo jogador em cima da copa e outra solta no canto: nunca caem
		BlockPos placedOnTop = new BlockPos(3, 6, 3);
		BlockPos placedAlone = new BlockPos(0, 1, 0);
		helper.setBlock(placedOnTop, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 2));
		helper.setBlock(placedAlone, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));

		// com o tronco de pe nada cai
		helper.runAfterDelay(30, () -> {
			for (BlockPos pos : natural) {
				helper.assertBlockPresent(Blocks.OAK_LEAVES, pos);
			}
			for (int y = 1; y <= 4; y++) {
				helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(3, y, 3)), false);
			}
			long cutAt = helper.getTick();
			helper.succeedWhen(() -> {
				for (BlockPos pos : natural) {
					helper.assertBlockNotPresent(Blocks.OAK_LEAVES, pos);
				}
				helper.assertBlockPresent(Blocks.OAK_LEAVES, placedOnTop);
				helper.assertBlockPresent(Blocks.OAK_LEAVES, placedAlone);
				if (helper.getTick() - cutAt > 120) {
					helper.fail("folhas demoraram mais de 6 s");
				}
			});
		});
	}

	/** Bussola de biomas: o bioma onde o jogador esta aparece na lista e a busca acha e grava o alvo para a agulha. */
	@GameTest(environment = ENV, maxTicks = 200)
	@SuppressWarnings("removal")
	public void biomeCompassFindsTheBiomeAround(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(helper.absolutePos(new BlockPos(2, 1, 2)).getX(), helper.absolutePos(new BlockPos(2, 1, 2)).getY(),
				helper.absolutePos(new BlockPos(2, 1, 2)).getZ());
		ItemStack compass = new ItemStack(Compasses.BIOME_COMPASS);
		player.setItemInHand(InteractionHand.MAIN_HAND, compass);
		String here = helper.getLevel().getBiome(player.blockPosition()).unwrapKey().orElseThrow().identifier().toString();
		List<String> entries = CompassServer.entries(helper.getLevel(), CompassKind.BIOME);
		helper.assertTrue(entries.contains(here), "bioma " + here + " fora da lista " + entries);
		helper.assertTrue(CompassServer.startSearch(player, compass, CompassKind.BIOME, here), "busca nao comecou");
		helper.assertTrue(compass.get(Compasses.TARGET).status() == CompassTarget.SEARCHING, "bussola deveria estar procurando");
		// estrutura que nao gera aqui nao entra na busca
		ItemStack structures = new ItemStack(Compasses.STRUCTURE_COMPASS);
		helper.assertTrue(!CompassServer.startSearch(player, structures, CompassKind.STRUCTURE, "mlinnotas:no_such_structure")
				&& structures.get(Compasses.TARGET).status() == CompassTarget.NOT_FOUND, "estrutura inexistente deveria falhar na hora");
		helper.succeedWhen(() -> {
			CompassTarget target = compass.get(Compasses.TARGET);
			LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
			helper.assertTrue(target != null && target.status() == CompassTarget.FOUND && tracker != null && tracker.target().isPresent(), "ainda procurando");
			helper.assertTrue(tracker.target().get().dimension().equals(helper.getLevel().dimension()) && !tracker.tracked(), "alvo errado: " + tracker);
			helper.assertTrue(tracker.target().get().pos().distManhattan(player.blockPosition()) <= 64, "bioma onde o jogador esta deveria ser achado perto: " + tracker);
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}
}
