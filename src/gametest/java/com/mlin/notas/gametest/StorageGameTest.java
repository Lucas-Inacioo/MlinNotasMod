package com.mlin.notas.gametest;

import com.mlin.notas.storage.ItemKey;
import com.mlin.notas.storage.StorageCableBlock;
import com.mlin.notas.storage.StorageNetwork;
import com.mlin.notas.storage.StorageRegistry;
import com.mlin.notas.storage.StorageTerminalBlock;
import com.mlin.notas.storage.StorageTier;
import com.mlin.notas.storage.StorageUnitBlockEntity;
import com.mlin.notas.storage.StoredItems;
import com.mlin.notas.storage.TerminalMenu;
import com.mlin.notas.storage.TerminalPayloads;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Rede de armazenamento no mundo: terminal + cabos + unidade + bau, insercao/extracao pela rede e pelo menu do
 * terminal, unidade quebrada levando o conteudo e o terminal de crafting reabastecendo a grade.
 */
public class StorageGameTest {
	private static final String ENV = "mlinnotas-gametest:storage";

	/** Terminal (0,1,0) - cabos (1..2,1,0) - unidade de cobre (3,1,0); bau em cima do segundo cabo. */
	private static BlockPos buildNetwork(GameTestHelper helper) {
		helper.setBlock(0, 1, 0, StorageRegistry.TERMINAL);
		helper.setBlock(1, 1, 0, StorageRegistry.CABLE);
		helper.setBlock(2, 1, 0, StorageRegistry.CABLE);
		helper.setBlock(3, 1, 0, StorageRegistry.unit(StorageTier.COPPER));
		helper.setBlock(2, 2, 0, Blocks.CHEST);
		// setBlock nao passa pelo getStateForPlacement: encaixa os cabos como se tivessem sido colocados por ultimo
		for (int x = 1; x <= 2; x++) {
			BlockPos pos = helper.absolutePos(new BlockPos(x, 1, 0));
			helper.getLevel().setBlock(pos, StorageCableBlock.withConnections(helper.getLevel(), pos, StorageRegistry.CABLE.defaultBlockState()), 3);
		}
		return helper.absolutePos(new BlockPos(0, 1, 0));
	}

	@GameTest(environment = ENV)
	public void networkFillsUnitThenChestAndDrainsChestFirst(GameTestHelper helper) {
		BlockPos terminal = buildNetwork(helper);
		StorageNetwork network = StorageNetwork.find(helper.getLevel(), terminal);
		helper.assertTrue(network.units().size() == 1, "uma unidade na rede");
		helper.assertTrue(network.containerCount() == 1, "um bau na rede");
		helper.assertTrue(network.graph().cables() == 2, "dois cabos");

		ItemStack rest = network.insert(new ItemStack(Items.COBBLESTONE, 64));
		helper.assertTrue(rest.isEmpty(), "64 pedregulhos cabem");
		for (int i = 0; i < 23; i++) {
			network.insert(new ItemStack(Items.COBBLESTONE, 64));
		}
		// 24 * 64 = 1536: 1024 na unidade (limite do cobre), 512 no bau
		StorageUnitBlockEntity unit = helper.getBlockEntity(new BlockPos(3, 1, 0), StorageUnitBlockEntity.class);
		ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 2, 0), ChestBlockEntity.class);
		ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
		helper.assertTrue(unit.cell().count(cobble) == 1024, "unidade cheia de pedregulho: " + unit.cell().count(cobble));
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 512, "resto no bau: " + chest.countItem(Items.COBBLESTONE));
		helper.assertTrue(network.totals().get(cobble) == 1536, "total somado");

		// tirar 600: esvazia o bau (512) antes e tira 88 da unidade
		ItemStack taken = network.extract(cobble, 600);
		helper.assertTrue(taken.getCount() == 600 && taken.is(Items.COBBLESTONE), "tirou 600 (pilha maior que 64 so na memoria)");
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "bau esvaziado primeiro");
		helper.assertTrue(unit.cell().count(cobble) == 936, "unidade com 936");

		// cabo encaixou nos vizinhos
		helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 0)).getValue(PipeBlock.WEST), "cabo liga no terminal");
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 0)).getValue(PipeBlock.UP), "cabo liga no bau");
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 0)).getValue(PipeBlock.EAST), "cabo liga na unidade");
		helper.assertTrue(!helper.getBlockState(new BlockPos(2, 1, 0)).getValue(PipeBlock.NORTH), "cabo nao liga no ar");
		helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 0)).getBlock() instanceof StorageCableBlock, "e cabo");
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest(environment = ENV)
	public void terminalMenuTakesAndStores(GameTestHelper helper) {
		BlockPos terminal = buildNetwork(helper);
		StorageNetwork.find(helper.getLevel(), terminal).insert(new ItemStack(Items.IRON_INGOT, 64));
		StorageNetwork.find(helper.getLevel(), terminal).insert(new ItemStack(Items.IRON_INGOT, 36));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.teleportTo(terminal.getX() + 0.5, terminal.getY() + 1, terminal.getZ() + 2.5);
			StorageTerminalBlock.open(player, terminal, false);
			helper.assertTrue(player.containerMenu instanceof TerminalMenu, "menu do terminal aberto");
			TerminalMenu menu = (TerminalMenu) player.containerMenu;
			helper.assertTrue(menu.stillValid(player), "perto do terminal");
			menu.broadcastChanges();
			int iron = menu.syncIdOf(new ItemStack(Items.IRON_INGOT));
			helper.assertTrue(iron > 0, "ferro sincronizado com numero");

			// esquerdo: pega uma pilha no cursor
			menu.handleAction(player, TerminalPayloads.Action.LEFT, iron);
			helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 64, "pilha de 64 no cursor");
			ItemKey key = ItemKey.of(new ItemStack(Items.IRON_INGOT));
			helper.assertTrue(StorageNetwork.find(helper.getLevel(), terminal).storage().count(key) == 36, "sobrou 36 na rede");
			// esquerdo com o cursor cheio: guarda tudo de volta
			menu.handleAction(player, TerminalPayloads.Action.LEFT, -1);
			helper.assertTrue(menu.getCarried().isEmpty(), "cursor vazio");
			helper.assertTrue(StorageNetwork.find(helper.getLevel(), terminal).storage().count(key) == 100, "100 de volta");
			// direito: meia pilha
			menu.handleAction(player, TerminalPayloads.Action.RIGHT, iron);
			helper.assertTrue(menu.getCarried().getCount() == 32, "meia pilha: " + menu.getCarried().getCount());
			// direito com cursor: guarda 1
			menu.handleAction(player, TerminalPayloads.Action.RIGHT, -1);
			helper.assertTrue(menu.getCarried().getCount() == 31, "guardou 1");
			menu.handleAction(player, TerminalPayloads.Action.LEFT, -1);
			// shift: uma pilha para o inventario
			menu.handleAction(player, TerminalPayloads.Action.SHIFT, iron);
			helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 64, "64 no inventario");
			helper.assertTrue(StorageNetwork.find(helper.getLevel(), terminal).storage().count(key) == 36, "36 na rede");

			// shift+clique no inventario guarda na rede
			int slot = -1;
			for (int i = menu.inventoryStart(); i < menu.slots.size(); i++) {
				if (menu.slots.get(i).getItem().is(Items.IRON_INGOT)) {
					slot = i;
				}
			}
			helper.assertTrue(slot >= 0, "achou o ferro no inventario");
			menu.clicked(slot, 0, ContainerInput.QUICK_MOVE, player);
			helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 0, "inventario sem ferro");
			helper.assertTrue(StorageNetwork.find(helper.getLevel(), terminal).storage().count(key) == 100, "100 na rede de novo");

			// numero desconhecido nao faz nada
			menu.handleAction(player, TerminalPayloads.Action.LEFT, 9999);
			helper.assertTrue(menu.getCarried().isEmpty(), "numero invalido ignorado");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void brokenUnitKeepsContentsInItem(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, StorageRegistry.unit(StorageTier.IRON).defaultBlockState());
		StorageUnitBlockEntity unit = helper.getBlockEntity(new BlockPos(1, 1, 1), StorageUnitBlockEntity.class);
		helper.assertTrue(unit.tier() == StorageTier.IRON, "nivel ferro");
		unit.cell().insert(ItemKey.of(new ItemStack(Items.DIRT)), 3000, false);
		unit.cell().insert(ItemKey.of(new ItemStack(Items.DIAMOND)), 5, false);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().destroyBlock(pos, true);
		helper.runAfterDelay(2, () -> {
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
			helper.assertTrue(dropped.size() == 1, "so a unidade cai (o conteudo vai dentro): " + dropped.size());
			ItemStack item = dropped.get(0).getItem();
			helper.assertTrue(item.is(StorageRegistry.unitItem(StorageTier.IRON)), "item da unidade de ferro");
			StoredItems contents = item.get(StorageRegistry.CONTENTS);
			helper.assertTrue(contents != null && contents.totalCount() == 3005, "conteudo no item");
			helper.assertTrue(!StorageRegistry.canStoreInUnit(ItemKey.of(item)), "unidade cheia nao entra em outra unidade");
			helper.assertTrue(StorageRegistry.canStoreInUnit(ItemKey.of(new ItemStack(StorageRegistry.unitItem(StorageTier.COPPER)))),
					"unidade vazia entra");

			// colocar de novo devolve o conteudo
			helper.setBlock(4, 1, 4, StorageRegistry.unit(StorageTier.IRON).defaultBlockState().setValue(
					com.mlin.notas.storage.StorageUnitBlock.FACING, Direction.EAST));
			StorageUnitBlockEntity placed = helper.getBlockEntity(new BlockPos(4, 1, 4), StorageUnitBlockEntity.class);
			placed.applyComponentsFromItemStack(item);
			helper.assertTrue(placed.cell().count(ItemKey.of(new ItemStack(Items.DIRT))) == 3000, "terra de volta");
			helper.assertTrue(placed.cell().count(ItemKey.of(new ItemStack(Items.DIAMOND))) == 5, "diamantes de volta");
			dropped.forEach(ItemEntity::discard);
			helper.succeed();
		});
	}

	@SuppressWarnings("removal")
	@GameTest(environment = ENV)
	public void craftingTerminalRefillsGridFromNetwork(GameTestHelper helper) {
		helper.setBlock(0, 1, 0, StorageRegistry.CRAFTING_TERMINAL);
		helper.setBlock(1, 1, 0, StorageRegistry.unit(StorageTier.COPPER));
		BlockPos terminal = helper.absolutePos(new BlockPos(0, 1, 0));
		StorageNetwork.find(helper.getLevel(), terminal).insert(new ItemStack(Items.OAK_PLANKS, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.teleportTo(terminal.getX() + 0.5, terminal.getY() + 1, terminal.getZ() + 2.5);
			StorageTerminalBlock.open(player, terminal, true);
			TerminalMenu menu = (TerminalMenu) player.containerMenu;
			helper.assertTrue(menu.crafting() && menu.inventoryStart() == 10, "resultado + grade 3x3 antes do inventario");
			// tabuas uma em cima da outra = gravetos
			menu.slots.get(1).set(new ItemStack(Items.OAK_PLANKS));
			menu.slots.get(4).set(new ItemStack(Items.OAK_PLANKS));
			helper.assertTrue(menu.slots.get(0).getItem().is(Items.STICK), "resultado: gravetos");
			// Shift+clique no resultado: fabrica enquanto a rede repoe (2 na grade + 10 na rede = 6 crafts = 24 gravetos)
			menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
			helper.assertTrue(player.getInventory().countItem(Items.STICK) == 24, "24 gravetos: " + player.getInventory().countItem(Items.STICK));
			ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
			helper.assertTrue(StorageNetwork.find(helper.getLevel(), terminal).storage().count(planks) == 0, "tabuas da rede usadas");
			helper.assertTrue(menu.slots.get(1).getItem().isEmpty() && menu.slots.get(4).getItem().isEmpty(), "grade vazia no fim");

			// limpar a grade devolve para a rede
			StorageNetwork.find(helper.getLevel(), terminal).insert(new ItemStack(Items.OAK_PLANKS, 3));
			menu.slots.get(5).set(new ItemStack(Items.COBBLESTONE, 7));
			menu.handleAction(player, TerminalPayloads.Action.CLEAR_GRID, -1);
			helper.assertTrue(menu.slots.get(5).getItem().isEmpty(), "grade limpa");
			helper.assertTrue(StorageNetwork.find(helper.getLevel(), terminal).storage().count(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 7,
					"pedregulho foi para a rede");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void doubleChestJoinsWhole(GameTestHelper helper) {
		BlockPos a = new BlockPos(3, 1, 3);
		net.minecraft.world.level.block.state.BlockState left = Blocks.CHEST.defaultBlockState()
				.setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH)
				.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
		Direction toOther = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(left);
		BlockPos b = a.relative(toOther);
		helper.setBlock(a, left);
		helper.setBlock(b, left.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
		BlockPos terminalRel = a.relative(toOther.getOpposite());
		helper.setBlock(terminalRel, StorageRegistry.TERMINAL);
		helper.getBlockEntity(b, ChestBlockEntity.class).setItem(5, new ItemStack(Items.EMERALD, 9));
		StorageNetwork network = StorageNetwork.find(helper.getLevel(), helper.absolutePos(terminalRel));
		helper.assertTrue(network.containerCount() == 2, "as duas metades: " + network.containerCount());
		helper.assertTrue(network.storage().count(ItemKey.of(new ItemStack(Items.EMERALD))) == 9, "ve a metade de longe");
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void upgradeRecipeKeepsContentsAndShowsInViewer(GameTestHelper helper) {
		ItemStack copper = new ItemStack(StorageRegistry.unitItem(StorageTier.COPPER));
		copper.set(StorageRegistry.CONTENTS, StoredItems.of(java.util.Map.of(ItemKey.of(new ItemStack(Items.STONE)), 900L)));
		net.minecraft.world.item.crafting.CraftingInput input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
				List.of(copper, new ItemStack(Items.IRON_BLOCK)));
		var recipe = helper.getLevel().getServer().getRecipeManager()
				.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(recipe.isPresent(), "unidade de cobre + bloco de ferro = receita de melhoria");
		ItemStack result = recipe.get().value().assemble(input);
		helper.assertTrue(result.is(StorageRegistry.unitItem(StorageTier.IRON)), "vira unidade de ferro");
		StoredItems contents = result.get(StorageRegistry.CONTENTS);
		helper.assertTrue(contents != null && contents.totalCount() == 900, "conteudo mantido na melhoria");
		com.mlin.notas.recipes.RecipeView view = com.mlin.notas.recipes.RecipeExporter.export(recipe.get());
		helper.assertTrue(view != null && view.inputs().size() == 2, "visualizador de receitas mostra a melhoria");
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void containersDoNotConductAndStoneBreaksNetwork(GameTestHelper helper) {
		helper.setBlock(0, 1, 0, StorageRegistry.TERMINAL);
		helper.setBlock(1, 1, 0, Blocks.BARREL);
		// barril encostado no barril nao entra (baus nao estendem a rede)
		helper.setBlock(2, 1, 0, Blocks.BARREL);
		// pedra nao liga nada
		helper.setBlock(0, 1, 1, Blocks.STONE);
		helper.setBlock(0, 1, 2, StorageRegistry.unit(StorageTier.COPPER));
		BlockPos terminal = helper.absolutePos(new BlockPos(0, 1, 0));
		StorageNetwork network = StorageNetwork.find(helper.getLevel(), terminal);
		helper.assertTrue(network.containerCount() == 1, "so o barril encostado: " + network.containerCount());
		helper.assertTrue(network.units().isEmpty(), "unidade atras da pedra fica de fora");
		helper.succeed();
	}
}
