package com.mlin.notas.gametest;

import com.mlin.notas.storage.ItemKey;
import com.mlin.notas.storage.NetworkTablet;
import com.mlin.notas.storage.StorageCableBlock;
import com.mlin.notas.storage.StorageNetwork;
import com.mlin.notas.storage.StorageRegistry;
import com.mlin.notas.storage.StorageTier;
import com.mlin.notas.storage.TabletLink;
import com.mlin.notas.storage.TerminalMenu;
import com.mlin.notas.storage.TerminalPayloads;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Tablet de Rede: vincula num terminal de crafting longe (3000 blocos), abre de longe com a grade 3x3, tira um item
 * pela rede, segura a chunk carregada enquanto o menu esta aberto e apaga o vinculo quando o terminal some.
 */
public class NetworkTabletGameTest {
	private static final String ENV = "mlinnotas-gametest:network_tablet";

	/** Terminal de crafting - cabo - unidade de cobre com 40 diamantes, numa chunk longe do jogador. */
	private static BlockPos buildFarNetwork(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos terminal = helper.absolutePos(new BlockPos(3000, 1, 0));
		level.setBlock(terminal.below(), Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(terminal, StorageRegistry.CRAFTING_TERMINAL.defaultBlockState(), 3);
		level.setBlock(terminal.east(), StorageRegistry.CABLE.defaultBlockState(), 3);
		level.setBlock(terminal.east(2), StorageRegistry.unit(StorageTier.COPPER).defaultBlockState(), 3);
		level.setBlock(terminal.east(), StorageCableBlock.withConnections(level, terminal.east(), StorageRegistry.CABLE.defaultBlockState()), 3);
		StorageNetwork.find(level, terminal).insert(new ItemStack(Items.DIAMOND, 40));
		return terminal;
	}

	@SuppressWarnings("removal")
	@GameTest(environment = ENV, maxTicks = 200)
	public void tabletOpensFarTerminalAndKeepsItLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos terminal = buildFarNetwork(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos near = helper.absolutePos(new BlockPos(1, 1, 1));
		player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		ItemStack tablet = new ItemStack(NetworkTablet.ITEM);
		player.setItemInHand(InteractionHand.MAIN_HAND, tablet);

		// sem vinculo nao abre
		helper.assertTrue(!NetworkTablet.open(player, tablet), "tablet sem vinculo nao abre");
		helper.assertTrue(NetworkTablet.link(player, tablet, terminal), "vinculou");
		TabletLink link = tablet.get(NetworkTablet.LINK);
		helper.assertTrue(link != null && link.crafting() && link.terminal().pos().equals(terminal)
				&& link.terminal().dimension().equals(level.dimension()), "vinculo com posicao, dimensao e crafting");

		helper.assertTrue(NetworkTablet.open(player, tablet), "abriu de 3000 blocos");
		helper.assertTrue(player.containerMenu instanceof TerminalMenu, "menu do terminal");
		TerminalMenu menu = (TerminalMenu) player.containerMenu;
		helper.assertTrue(menu.remote() && menu.crafting(), "remoto e com a grade de crafting");
		helper.assertTrue(menu.stillValid(player), "valido longe com o tablet no inventario");
		menu.broadcastChanges();
		int diamond = menu.syncIdOf(new ItemStack(Items.DIAMOND));
		helper.assertTrue(diamond > 0, "diamantes sincronizados");
		menu.handleAction(player, TerminalPayloads.Action.SHIFT, diamond);
		helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 40, "tirou os 40 diamantes de longe");
		helper.assertTrue(StorageNetwork.find(level, terminal).storage().count(ItemKey.of(new ItemStack(Items.DIAMOND))) == 0, "rede vazia");

		int chunkX = terminal.getX() >> 4;
		int chunkZ = terminal.getZ() >> 4;
		helper.onEachTick(() -> {
			if (player.containerMenu == menu) {
				menu.markDirty();
				menu.broadcastChanges(); // o jogador falso nao e "tickado" pela conexao: renova os tickets a mao
			}
		});
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(level.getChunkSource().hasChunk(chunkX, chunkZ), "chunk do terminal segue carregada com o menu aberto");
			helper.assertTrue(menu.stillValid(player), "ainda valido");
			// sem o tablet no inventario o menu deixa de valer
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.assertTrue(!menu.stillValid(player), "sem o tablet nao vale");
			player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
			player.closeContainer();

			// terminal quebrado: abrir apaga o vinculo
			level.setBlock(terminal, Blocks.AIR.defaultBlockState(), 3);
			helper.assertTrue(!NetworkTablet.open(player, tablet), "terminal sumiu: nao abre");
			helper.assertTrue(!tablet.has(NetworkTablet.LINK), "vinculo apagado");
			helper.getLevel().getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}
}
