package com.mlin.notas.gametest;

import com.mlin.notas.client.screen.SelectiveEnchantScreen;
import com.mlin.notas.client.screen.BigChestScreen;
import com.mlin.notas.chest.BigChestBlockEntity;
import com.mlin.notas.chest.BigChestMenu;
import com.mlin.notas.chest.ChestTier;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.end.CitadelPiece;
import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.registry.ModStructures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import com.mlin.notas.enchanting.SelectiveEnchantMenu;
import com.mlin.notas.enchanting.SelectiveEnchanting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Blocks;
import com.mlin.notas.client.GammaBright;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.client.minimap.MinimapOptionsScreen;
import com.mlin.notas.client.minimap.WorldMapScreen;
import com.mlin.notas.client.recipes.RecipeScreen;
import com.mlin.notas.client.recipes.RecipeViewerClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Game test de cliente: abre o jogo de verdade (precisa de janela/GPU) e tira screenshots das telas do mod.
 * Rodar com: gradlew runClientGameTest. Screenshots em build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class MlinNotasClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("mlinnotas-singleplayer");

			// Baus grandes animados na frente do jogador (o console do servidor nao tem posicao: execute at @p)
			singleplayer.getServer().runCommand("execute at @p run setblock ~2 ~ ~3 mlinnotas:iron_chest[facing=north]");
			singleplayer.getServer().runCommand("execute at @p run setblock ~ ~ ~3 mlinnotas:gold_chest[facing=north]");
			singleplayer.getServer().runCommand("execute at @p run setblock ~-2 ~ ~3 mlinnotas:diamond_chest[facing=north]");
			context.getInput().lookAt(0, 30);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-big-chests");

			// Entidades para conferir os icones do minimapa (vaca, zumbi, aldeao, item no chao)
			singleplayer.getServer().runCommand("execute at @p run summon minecraft:cow ~6 ~ ~-4 {NoAI:1b}");
			singleplayer.getServer().runCommand("execute at @p run summon minecraft:zombie ~-6 ~ ~-6 {NoAI:1b,Fire:-1s,PersistenceRequired:1b}");
			singleplayer.getServer().runCommand("execute at @p run summon minecraft:villager ~8 ~ ~6 {NoAI:1b}");
			singleplayer.getServer().runCommand("execute at @p run summon minecraft:item ~-4 ~ ~8 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767s,Age:-32768s}");
			singleplayer.getServer().runCommand("time set noon");
			context.waitTicks(20);
			context.takeScreenshot("mlinnotas-minimap-entities");

			// Mesa de encantamento com escolha: anel de estantes, espada e lapis no menu, 30 niveis
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos table = player.blockPosition().offset(0, 0, 3);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
							level.setBlockAndUpdate(table.offset(dx, 0, dz), Blocks.BOOKSHELF.defaultBlockState());
							level.setBlockAndUpdate(table.offset(dx, 1, dz), Blocks.BOOKSHELF.defaultBlockState());
						}
					}
				}
				level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
				player.giveExperienceLevels(30);
				SelectiveEnchanting.open(player, ContainerLevelAccess.create(level, table));
				player.containerMenu.slots.get(SelectiveEnchantMenu.ITEM_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
				player.containerMenu.slots.get(SelectiveEnchantMenu.LAPIS_SLOT).set(new ItemStack(Items.LAPIS_LAZULI, 32));
				player.containerMenu.broadcastChanges();
			});
			context.waitForScreen(SelectiveEnchantScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-selective-enchant");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			// Gammabright ligado
			context.getInput().pressKey(ModKeys.GAMMABRIGHT);
			context.waitTicks(5);
			if (!context.computeOnClient(mc -> GammaBright.isEnabled())) {
				throw new IllegalStateException("gammabright deveria estar ligado apos a tecla");
			}
			context.takeScreenshot("mlinnotas-gammabright");
			context.getInput().pressKey(ModKeys.GAMMABRIGHT);

			// Mapa grande do minimapa (tecla M) e tela de opcoes
			context.getInput().pressKey(ModKeys.MAP);
			context.waitForScreen(WorldMapScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-world-map");
			context.clickScreenButton("gui.mlinnotas.map.options");
			context.waitForScreen(MinimapOptionsScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-minimap-options");
			context.clickScreenButton("gui.done");
			context.waitForScreen(WorldMapScreen.class);
			context.clickScreenButton("gui.done");
			context.waitForScreen(null);

			// Receitas recebidas do servidor integrado
			context.waitFor(mc -> RecipeViewerClient.index().size() > 0, 200);

			// Inventario com a lista de itens do visualizador
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-inventory-item-list");

			// Tela de receitas do bau, aberta por codigo (mesmo caminho do clique/tecla R)
			context.runOnClient(mc -> {
				if (!RecipeViewerClient.openRecipes(new ItemStack(Items.CHEST), mc.gui.screen())) {
					throw new IllegalStateException("bau deveria ter receita no visualizador");
				}
			});
			context.waitForScreen(RecipeScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-recipe-chest");
			context.clickScreenButton("gui.mlinnotas.recipes.close");
			context.waitForScreen(InventoryScreen.class);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(null);

			// Escada de 12 niveis: suportes com as armaduras e espadas novas, minerios novos ao lado
			String[] tiers = {"cobalt", "infernite", "endite", "astralite", "celestium"};
			for (int i = 0; i < tiers.length; i++) {
				String t = tiers[i];
				singleplayer.getServer().runCommand("execute at @p run summon minecraft:armor_stand ~" + (i * 2 - 4) + " ~ ~-8 {Rotation:[0f,0f],ShowArms:1b,NoGravity:1b,"
						+ "equipment:{head:{id:\"mlinnotas:" + t + "_helmet\"},chest:{id:\"mlinnotas:" + t + "_chestplate\"},legs:{id:\"mlinnotas:" + t + "_leggings\"},"
						+ "feet:{id:\"mlinnotas:" + t + "_boots\"},mainhand:{id:\"mlinnotas:" + t + "_sword\"}}}");
			}
			String[] ores = {"cobalt_ore", "deepslate_cobalt_ore", "infernite_ore", "endite_ore", "astralite_ore"};
			for (int i = 0; i < ores.length; i++) {
				singleplayer.getServer().runCommand("execute at @p run setblock ~" + (i * 2 - 4) + " ~1 ~-11 mlinnotas:" + ores[i]);
			}
			context.getInput().lookAt(180, 15);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-tier-armor");

			// Cidadela do End inteira a 45 blocos ao norte (flutuando), criaturas do End na frente do jogador
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos origin = player.blockPosition().offset(0, 10, -45);
				CitadelPiece piece = new CitadelPiece(20261113L, origin);
				piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(1),
						piece.getBoundingBox(), ChunkPos.containing(origin), origin);
				BlockPos front = player.blockPosition().offset(0, 0, -5);
				ModEntities.VOID_JELLY.spawn(level, front.offset(-3, 2, 0), EntitySpawnReason.COMMAND);
				ModEntities.CRYSTAL_MITE.spawn(level, front.offset(0, 0, 0), EntitySpawnReason.COMMAND);
				ModEntities.PURPUR_SENTINEL.spawn(level, front.offset(3, 0, -1), EntitySpawnReason.COMMAND);
				for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(front).inflate(6))) {
					mob.setNoAi(true);
				}
			});
			singleplayer.getServer().runCommand("time set noon");
			context.getInput().lookAt(180, -5);
			context.waitTicks(40);
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("mlinnotas-end-citadel");
			// dentro do cofre (ultimo andar da fortaleza): elitro na parede, cabeca de dragao, baus
			singleplayer.getServer().runCommand("execute at @p run tp @p ~ ~31 ~-42 180 10");
			context.waitTicks(20);
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("mlinnotas-end-citadel-vault");
			singleplayer.getServer().runCommand("execute at @p run tp @p ~ ~-31 ~42");
			context.waitTicks(10);

			// Bau de celestio (243 slots, 27 linhas): janela de 6 linhas com barra de rolagem
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(3, 0, -3);
				player.level().setBlockAndUpdate(pos, ModBlocks.chest(ChestTier.CELESTIUM).defaultBlockState());
				BigChestBlockEntity chest = (BigChestBlockEntity) player.level().getBlockEntity(pos);
				chest.setItem(0, new ItemStack(ModItems.CELESTIUM_INGOT, 12));
				chest.setItem(10, new ItemStack(Items.DIAMOND, 30));
				chest.setItem(240, new ItemStack(Items.EMERALD, 7));
				chest.setItem(242, new ItemStack(Items.NETHER_STAR));
				player.openMenu(chest);
			});
			context.waitForScreen(BigChestScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-celestium-chest");
			context.runOnClient(mc -> {
				BigChestScreen screen = (BigChestScreen) mc.gui.screen();
				for (int i = 0; i < 30; i++) {
					screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-celestium-chest-scrolled");
			context.runOnClient(mc -> {
				BigChestMenu menu = ((BigChestScreen) mc.gui.screen()).getMenu();
				if (menu.scrollRow() != ChestTier.CELESTIUM.maxScroll() || !menu.slots.get(53).getItem().is(Items.NETHER_STAR)) {
					throw new IllegalStateException("rolagem deveria mostrar o ultimo slot (estrela do Nether), linha " + menu.scrollRow());
				}
			});
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(null);
		}

		// Mundo normal (End de verdade): a cidadela tem que nascer no End externo e a End City vanilla nao
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false).create()) {
			real.getConnection().waitForChunksRender();
			BlockPos citadel = real.getServer().computeOnServer(server -> {
				ServerLevel end = server.getLevel(net.minecraft.world.level.Level.END);
				var structures = end.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
				var generator = end.getChunkSource().getGenerator();
				BlockPos from = new BlockPos(1500, 64, 0);
				var vanilla = generator.findNearestMapStructure(end, net.minecraft.core.HolderSet.direct(structures.getOrThrow(
						net.minecraft.world.level.levelgen.structure.BuiltinStructures.END_CITY)), from, 50, false);
				if (vanilla != null) {
					throw new IllegalStateException("End City vanilla ainda gera em " + vanilla.getFirst());
				}
				var found = generator.findNearestMapStructure(end, net.minecraft.core.HolderSet.direct(structures.getOrThrow(ModStructures.END_CITADEL)),
						from, 100, false);
				if (found == null) {
					throw new IllegalStateException("nenhuma cidadela no End externo");
				}
				return found.getFirst();
			});
			real.getServer().runCommand("gamemode creative @p");
			real.getServer().runCommand("execute in minecraft:the_end run tp @p " + citadel.getX() + " 105 " + (citadel.getZ() + 50) + " 180 25");
			real.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			});
			context.waitTicks(100);
			real.getConnection().waitForChunksRender();
			context.waitTicks(40);
			context.takeScreenshot("mlinnotas-end-citadel-generated");
		}
	}
}
