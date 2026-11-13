package com.mlin.notas.gametest;

import com.mlin.notas.client.food.CookingPotScreen;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.client.recipes.RecipeScreen;
import com.mlin.notas.client.recipes.RecipeViewerClient;
import com.mlin.notas.food.CookingPotBlockEntity;
import com.mlin.notas.food.FoodCatalog;
import com.mlin.notas.food.FoodRegistry;
import com.mlin.notas.season.SeasonState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Game test de cliente da cozinha: uma horta com as 10 culturas (3 idades) e as formas selvagens, as tres estacoes
 * (panela cozinhando sobre a fogueira, tabua, barril), a tela da panela, a dica de um tomate da estacao e de uma
 * feijoada, e a receita da panela no visualizador. Screenshots {@code mlinnotas-food-*}.
 */
@SuppressWarnings("UnstableApiUsage")
public class FoodClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("season set summer mid");
			world.getServer().runCommand("gamemode spectator @a");
			context.waitFor(mc -> SeasonState.current() != null, 100);

			BlockPos origin = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos base = player.blockPosition().offset(0, -1, -8);
				buildGarden(player.level(), base);
				buildStations(player.level(), base.offset(16, 0, 0));
				return base;
			});
			context.waitTicks(20);

			// horta vista de cima, de frente para o sul
			tp(world, origin.getX() + 5.5, origin.getY() + 7.5, origin.getZ() + 9.5, 180, 38);
			context.waitTicks(30);
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-food-field");

			// formas selvagens de perto
			tp(world, origin.getX() + 5.5, origin.getY() + 3.0, origin.getZ() - 8.5, 0, 25);
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("mlinnotas-food-wild");

			// estacoes: a panela precisa estar cozinhando (lit) para soltar vapor
			context.waitFor(mc -> mc.level != null && mc.level.getBlockState(origin.offset(17, 2, 1)).getOptionalValue(
					com.mlin.notas.food.CookingPotBlock.LIT).orElse(false), 200);
			tp(world, origin.getX() + 18.5, origin.getY() + 3.6, origin.getZ() + 4.5, 180, 30);
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("mlinnotas-food-stations");

			// tela da panela no meio do cozimento
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (player.level().getBlockEntity(origin.offset(17, 2, 1)) instanceof CookingPotBlockEntity pot) {
					player.openMenu(pot);
				}
			});
			context.waitForScreen(CookingPotScreen.class);
			context.waitTicks(40);
			context.takeScreenshot("mlinnotas-food-pot-screen");
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitForScreen(null);

			// dicas: tomate da estacao e feijoada (Bem Alimentado)
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getInventory().clearContent();
				ItemStack tomato = new ItemStack(FoodRegistry.item("tomato"), 12);
				tomato.set(FoodRegistry.HARVEST_SEASON, SeasonState.seasonIndex());
				player.getInventory().setItem(0, tomato);
				player.getInventory().setItem(1, new ItemStack(FoodRegistry.item("feijoada"), 2));
				ItemStack old = new ItemStack(FoodRegistry.item("lettuce"), 5);
				old.set(FoodRegistry.HARVEST_SEASON, SeasonState.seasonIndex() - 2);
				player.getInventory().setItem(2, old);
				player.getInventory().setItem(3, new ItemStack(FoodRegistry.item("cabbage_seeds"), 8));
				// historico: comeu pao 4 vezes (repetitivo)
				player.setAttached(FoodRegistry.FOOD_HISTORY, List.of("minecraft:bread", "minecraft:bread", "minecraft:bread", "minecraft:bread"));
				player.getInventory().setItem(4, new ItemStack(Items.BREAD, 6));
			});
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			for (int slot = 0; slot < 5; slot++) {
				hoverHotbar(context, slot);
				context.waitTicks(5);
				context.takeScreenshot("mlinnotas-food-tooltip-" + slot);
			}

			// receita da panela no visualizador
			context.waitFor(mc -> RecipeViewerClient.index().size() > 0, 200);
			context.runOnClient(mc -> {
				if (!RecipeViewerClient.openRecipes(new ItemStack(FoodRegistry.item("feijoada")), mc.gui.screen())) {
					throw new IllegalStateException("feijoada deveria ter receita no visualizador");
				}
			});
			context.waitForScreen(RecipeScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-food-recipe-pot");
			context.runOnClient(mc -> {
				if (!RecipeViewerClient.openRecipes(new ItemStack(FoodRegistry.item("aged_cheese")), mc.gui.screen())) {
					throw new IllegalStateException("queijo curado deveria ter receita no visualizador");
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-food-recipe-barrel");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitForScreen(null);
			world.getServer().runCommand("season reset");
		}
	}

	/** Canteiro de 10 colunas (uma por cultura) x 3 linhas (madura, meio, nova) com agua no meio, e as selvagens na frente. */
	private static void buildGarden(ServerLevel level, BlockPos base) {
		List<FoodCatalog.Crop> crops = FoodCatalog.CROPS;
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
		for (int x = -2; x < crops.size() + 2; x++) {
			for (int z = -6; z <= 5; z++) {
				level.setBlock(base.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 1; y <= 6; y++) {
					level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		int[] ages = {CropBlock.MAX_AGE, 4, 1};
		for (int i = 0; i < crops.size(); i++) {
			Block crop = FoodRegistry.crop(crops.get(i).name());
			for (int row = 0; row < ages.length; row++) {
				BlockPos soil = base.offset(i, 0, row);
				level.setBlock(soil, farmland, Block.UPDATE_CLIENTS);
				level.setBlock(soil.above(), crop.defaultBlockState().setValue(CropBlock.AGE, ages[row]), Block.UPDATE_CLIENTS);
			}
			level.setBlock(base.offset(i, 0, 3), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
			// forma selvagem na grama, mais a frente
			level.setBlock(base.offset(i, 1, -4), FoodRegistry.wild(crops.get(i).name()).defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Bancada: fogueira + panela cozinhando, tabua com trigo ao lado, barril de conserva. */
	private static void buildStations(ServerLevel level, BlockPos base) {
		for (int x = -1; x <= 5; x++) {
			for (int z = -2; z <= 4; z++) {
				level.setBlock(base.offset(x, 0, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 1; y <= 4; y++) {
					level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		level.setBlock(base.offset(1, 1, 1), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(base.offset(1, 2, 1), FoodRegistry.COOKING_POT.defaultBlockState().setValue(com.mlin.notas.food.CookingPotBlock.FACING, Direction.SOUTH),
				Block.UPDATE_ALL);
		if (level.getBlockEntity(base.offset(1, 2, 1)) instanceof CookingPotBlockEntity pot) {
			// estoque grande para continuar cozinhando durante o teste
			pot.setItem(0, new ItemStack(FoodRegistry.item("beans"), 16));
			pot.setItem(1, new ItemStack(FoodRegistry.item("beans"), 16));
			pot.setItem(2, new ItemStack(Items.PORKCHOP, 16));
			pot.setItem(3, new ItemStack(FoodRegistry.item("garlic"), 16));
			pot.setItem(4, new ItemStack(FoodRegistry.item("onion"), 16));
			pot.setItem(CookingPotBlockEntity.CONTAINER_SLOT, new ItemStack(Items.BOWL, 16));
		}
		level.setBlock(base.offset(3, 1, 1), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(base.offset(3, 2, 1), FoodRegistry.CUTTING_BOARD.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(base.offset(5, 1, 1), FoodRegistry.PRESERVING_BARREL.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(base.offset(-1, 1, 1), Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
	}

	private static void hoverHotbar(ClientGameTestContext context, int slot) {
		double[] pos = context.computeOnClient(mc -> {
			AbstractContainerScreenAccessor frame = (AbstractContainerScreenAccessor) mc.gui.screen();
			int scale = mc.getWindow().getGuiScale();
			return new double[] {(frame.mlinnotas$leftPos() + 8 + slot * 18 + 8) * scale, (frame.mlinnotas$topPos() + 142 + 8) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void tp(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}
}
