package com.mlin.notas.gametest;

import com.mlin.notas.food.CookingPotBlockEntity;
import com.mlin.notas.food.CuttingBoardBlock;
import com.mlin.notas.food.FoodRegistry;
import com.mlin.notas.food.FoodRules;
import com.mlin.notas.food.FoodServer;
import com.mlin.notas.food.PreservingBarrelBlockEntity;
import com.mlin.notas.recipes.RecipeExporter;
import com.mlin.notas.recipes.RecipeKind;
import com.mlin.notas.recipes.RecipeView;
import com.mlin.notas.season.Season;
import com.mlin.notas.season.SeasonDate;
import com.mlin.notas.season.SeasonState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Cozinha no servidor. Lote {@code food_summer} (verao, poe e volta a estacao por comando): tomate amadurece ao ar
 * livre, a panela cozinha com fogo embaixo (e nao sem), a tabua corta, o barril cura e segura o frescor, comer repetido
 * rende menos e tomate da estacao rende mais, baus de vila trazem sementes e o visualizador ve as receitas novas.
 * Lote {@code food_winter}: tomate nao cresce ao ar livre, repolho sim (devagar) e a estufa salva o tomate.
 */
public class FoodGameTest {
	private static final String SUMMER = "mlinnotas-gametest:food_summer";
	private static final String WINTER = "mlinnotas-gametest:food_winter";
	private static final int TICKS = 90;

	// ------------------------------------------------------------ culturas

	@GameTest(environment = SUMMER, skyAccess = true)
	public void tomatoRipensInSummer(GameTestHelper helper) {
		requireSeason(Season.SUMMER);
		fillBiome(helper, "minecraft:plains");
		BlockPos crop = farm(helper, 2, 2, FoodRegistry.crop("tomato"));
		int age = grow(helper, crop, 11L);
		if (age < CropBlock.MAX_AGE) {
			throw new IllegalStateException("tomate no verao deveria amadurecer, ficou na idade " + age);
		}
		helper.succeed();
	}

	@GameTest(environment = WINTER, skyAccess = true)
	public void tomatoStopsOutdoorsInWinter(GameTestHelper helper) {
		requireSeason(Season.WINTER);
		fillBiome(helper, "minecraft:plains");
		BlockPos crop = farm(helper, 2, 2, FoodRegistry.crop("tomato"));
		int age = grow(helper, crop, 11L);
		if (age != 0) {
			throw new IllegalStateException("tomate no inverno ao ar livre nao deveria crescer, chegou na idade " + age);
		}
		helper.succeed();
	}

	@GameTest(environment = WINTER, skyAccess = true)
	public void cabbageKeepsGrowingInWinter(GameTestHelper helper) {
		requireSeason(Season.WINTER);
		fillBiome(helper, "minecraft:plains");
		BlockPos crop = farm(helper, 2, 2, FoodRegistry.crop("cabbage"));
		int age = grow(helper, crop, 11L);
		if (age < 2) {
			throw new IllegalStateException("repolho resiste ao inverno e deveria crescer um pouco, ficou na idade " + age);
		}
		helper.succeed();
	}

	@GameTest(environment = WINTER, skyAccess = true)
	public void greenhouseSavesTomatoInWinter(GameTestHelper helper) {
		requireSeason(Season.WINTER);
		fillBiome(helper, "minecraft:plains");
		BlockPos crop = farm(helper, 2, 2, FoodRegistry.crop("tomato"));
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(x, 5, z, Blocks.GLASS);
			}
		}
		int age = grow(helper, crop, 11L);
		if (age < CropBlock.MAX_AGE) {
			throw new IllegalStateException("tomate na estufa deveria amadurecer no inverno, ficou na idade " + age);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------ estacoes de cozinha

	@GameTest(environment = SUMMER, maxTicks = 400)
	public void potCooksOnlyOverHeat(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.CAMPFIRE);
		helper.setBlock(1, 2, 1, FoodRegistry.COOKING_POT);
		helper.setBlock(5, 1, 5, Blocks.STONE);
		helper.setBlock(5, 2, 5, FoodRegistry.COOKING_POT);
		CookingPotBlockEntity hot = (CookingPotBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1), CookingPotBlockEntity.class);
		CookingPotBlockEntity cold = (CookingPotBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 5), CookingPotBlockEntity.class);
		for (CookingPotBlockEntity pot : List.of(hot, cold)) {
			pot.setItem(0, new ItemStack(FoodRegistry.item("rice")));
			pot.setItem(1, new ItemStack(FoodRegistry.item("beans")));
			pot.setItem(2, new ItemStack(FoodRegistry.item("onion")));
			pot.setItem(3, new ItemStack(FoodRegistry.item("garlic")));
			pot.setItem(CookingPotBlockEntity.CONTAINER_SLOT, new ItemStack(Items.BOWL, 4));
		}
		helper.succeedWhen(() -> {
			ItemStack out = hot.getItem(CookingPotBlockEntity.OUTPUT_SLOT);
			helper.assertTrue(out.is(FoodRegistry.item("rice_and_beans")) && out.getCount() == 1, "uma tigela de arroz com feijao: " + out);
			helper.assertTrue(hot.getItem(CookingPotBlockEntity.CONTAINER_SLOT).getCount() == 3, "gastou uma tigela");
			helper.assertTrue(hot.getItem(0).isEmpty(), "gastou o arroz");
			helper.assertTrue(cold.getItem(CookingPotBlockEntity.OUTPUT_SLOT).isEmpty(), "sem fogo nao cozinha");
			helper.assertTrue(!CookingPotBlockEntity.isHeated(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5))), "pedra nao e calor");
		});
	}

	@GameTest(environment = SUMMER, maxTicks = 300)
	public void potGivesBucketBackForCheese(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.MAGMA_BLOCK);
		helper.setBlock(1, 2, 1, FoodRegistry.COOKING_POT);
		CookingPotBlockEntity pot = (CookingPotBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1), CookingPotBlockEntity.class);
		pot.setItem(0, new ItemStack(Items.MILK_BUCKET));
		helper.succeedWhen(() -> {
			ItemStack out = pot.getItem(CookingPotBlockEntity.OUTPUT_SLOT);
			helper.assertTrue(out.is(FoodRegistry.item("fresh_cheese")) && out.getCount() == 2, "dois queijos frescos: " + out);
			helper.assertTrue(pot.getItem(0).is(Items.BUCKET), "o balde volta para o espaco: " + pot.getItem(0));
		});
	}

	@GameTest(environment = SUMMER)
	public void cuttingBoardCutsWithoutTools(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, FoodRegistry.CUTTING_BOARD);
		ServerLevel level = helper.getLevel();
		BlockPos board = helper.absolutePos(new BlockPos(2, 1, 2));
		ItemStack wheat = new ItemStack(Items.WHEAT, 3);
		int cut = CuttingBoardBlock.cut(level, board, wheat, 64, null);
		helper.assertTrue(cut == 3 && wheat.isEmpty(), "cortou as 3 de trigo");
		ItemStack corn = new ItemStack(FoodRegistry.item("corn"), 2);
		CuttingBoardBlock.cut(level, board, corn, 1, null);
		helper.assertTrue(corn.getCount() == 1, "corta um por vez");
		helper.assertTrue(CuttingBoardBlock.cut(level, board, new ItemStack(Items.DIAMOND), 1, null) == 0, "diamante nao se corta");
		helper.assertTrue(count(helper, FoodRegistry.item("flour")) == 3, "3 farinhas");
		helper.assertTrue(count(helper, FoodRegistry.item("cornmeal")) == 2, "milho da 2 fubas");
		helper.succeed();
	}

	@GameTest(environment = SUMMER)
	public void barrelCuresAndKeepsFresh(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, FoodRegistry.PRESERVING_BARREL);
		PreservingBarrelBlockEntity barrel = (PreservingBarrelBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2), PreservingBarrelBlockEntity.class);
		ServerLevel level = helper.getLevel();
		barrel.setItem(0, new ItemStack(FoodRegistry.item("fresh_cheese"), 4));
		barrel.setItem(1, new ItemStack(FoodRegistry.item("cabbage"), 2));
		ItemStack tomato = new ItemStack(FoodRegistry.item("tomato"), 5);
		tomato.set(FoodRegistry.HARVEST_SEASON, 100L);
		barrel.setItem(2, tomato);
		barrel.advance(level, FoodRules.fermentTicks(1.0F));
		helper.assertTrue(barrel.getItem(1).is(FoodRegistry.item("sauerkraut")) && barrel.getItem(1).getCount() == 2, "repolho virou chucrute em 1 dia");
		helper.assertTrue(barrel.getItem(0).is(FoodRegistry.item("fresh_cheese")), "queijo ainda nao curou");
		barrel.advance(level, FoodRules.fermentTicks(1.0F));
		helper.assertTrue(barrel.getItem(0).is(FoodRegistry.item("aged_cheese")) && barrel.getItem(0).getCount() == 4, "queijo curado em 2 dias");
		helper.assertTrue(barrel.getItem(2).is(FoodRegistry.item("tomato")), "tomate nao fermenta");
		// a estacao avanca 3 vezes enquanto o tomate esta no barril: a idade fica parada
		barrel.keepFresh(100L);
		barrel.keepFresh(103L);
		Long stamp = barrel.getItem(2).get(FoodRegistry.HARVEST_SEASON);
		helper.assertTrue(stamp != null && stamp == 103L, "marca andou junto com a estacao: " + stamp);
		helper.assertTrue(FoodRules.freshness(stamp, 103L) == FoodRules.Freshness.FRESH, "continua fresco");
		helper.succeed();
	}

	// ------------------------------------------------------------ comer

	@GameTest(environment = SUMMER)
	public void repetitiveFoodFillsLess(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		int[] gains = new int[6];
		for (int i = 0; i < gains.length; i++) {
			player.getFoodData().setFoodLevel(0);
			ItemStack bread = new ItemStack(Items.BREAD);
			bread.finishUsingItem(helper.getLevel(), player);
			gains[i] = player.getFoodData().getFoodLevel();
		}
		// pao = 5: tres vezes cheio, depois 75% (4), e a partir da sexta 50% (3)
		helper.assertTrue(gains[0] == 5 && gains[2] == 5, "primeiras refeicoes rendem tudo: " + java.util.Arrays.toString(gains));
		helper.assertTrue(gains[3] == 4 && gains[4] == 4, "repetido rende 75%: " + java.util.Arrays.toString(gains));
		helper.assertTrue(gains[5] == 3, "muito repetido rende 50%: " + java.util.Arrays.toString(gains));
		helper.succeed();
	}

	@GameTest(environment = SUMMER, skyAccess = true)
	public void inSeasonTomatoFillsMoreAndMealGivesWellFed(GameTestHelper helper) {
		requireSeason(Season.SUMMER);
		fillBiome(helper, "minecraft:plains");
		ServerPlayer player = mockConnected(helper);
		BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		player.getFoodData().setFoodLevel(0);
		ItemStack tomato = new ItemStack(FoodRegistry.item("tomato"));
		tomato.set(FoodRegistry.HARVEST_SEASON, SeasonState.seasonIndex());
		tomato.finishUsingItem(helper.getLevel(), player);
		helper.assertTrue(player.getFoodData().getFoodLevel() == 3 + FoodRules.IN_SEASON_NUTRITION,
				"tomate da estacao rende +1: " + player.getFoodData().getFoodLevel());

		player.getFoodData().setFoodLevel(0);
		ItemStack old = new ItemStack(FoodRegistry.item("tomato"));
		old.set(FoodRegistry.HARVEST_SEASON, SeasonState.seasonIndex() - 3);
		old.finishUsingItem(helper.getLevel(), player);
		helper.assertTrue(player.getFoodData().getFoodLevel() == 2, "tomate passado rende metade (3 -> 2): " + player.getFoodData().getFoodLevel());

		ItemStack meal = new ItemStack(FoodRegistry.item("feijoada"));
		ItemStack left = meal.finishUsingItem(helper.getLevel(), player);
		helper.assertTrue(left.is(Items.BOWL), "devolve a tigela: " + left);
		var effect = player.getEffect(FoodRegistry.WELL_FED);
		helper.assertTrue(effect != null && effect.getAmplifier() == 1, "feijoada da Bem Alimentado II");
		helper.assertTrue(player.getMaxHealth() >= 20.0F + 2 * FoodRules.WELL_FED_MAX_HEALTH_PER_LEVEL - 0.01F, "vida maxima extra: " + player.getMaxHealth());
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// ------------------------------------------------------------ onde achar e receitas

	@GameTest(environment = SUMMER)
	public void villageHouseChestHasGardenSeeds(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2), ChestBlockEntity.class);
		chest.setLootTable(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, 42L);
		chest.unpackLootTable(null);
		List<Item> garden = FoodRegistry.cropBlocks().stream().map(b -> b.asItem()).toList();
		boolean found = false;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if (id.startsWith("mlinnotas:") && (id.endsWith("_seeds") || List.of("mlinnotas:onion", "mlinnotas:garlic", "mlinnotas:rice",
					"mlinnotas:beans").contains(id))) {
				found = true;
			}
		}
		helper.assertTrue(found, "bau de casa de vila deveria ter sementes da horta " + garden.size());
		helper.assertTrue(FoodServer.grassSeeds().size() == 6, "seis sementes caem da grama");
		helper.succeed();
	}

	@GameTest(environment = SUMMER)
	public void recipeViewerSeesKitchenRecipes(GameTestHelper helper) {
		List<RecipeView> views = RecipeExporter.export(helper.getLevel().getServer().getRecipeManager());
		RecipeView pot = find(views, "mlinnotas:cooking/rice_and_beans");
		helper.assertTrue(pot != null && pot.kind() == RecipeKind.COOKING_POT && pot.width() == 1 && pot.inputs().size() == 5,
				"panela com 4 ingredientes + tigela: " + pot);
		RecipeView cut = find(views, "mlinnotas:cutting/flour");
		helper.assertTrue(cut != null && cut.kind() == RecipeKind.CUTTING, "tabua: " + cut);
		RecipeView ferment = find(views, "mlinnotas:fermenting/aged_cheese");
		helper.assertTrue(ferment != null && ferment.kind() == RecipeKind.FERMENTING && ferment.cookTicks() == FoodRules.fermentTicks(2.0F),
				"barril: " + ferment);
		helper.succeed();
	}

	// ------------------------------------------------------------ utilitarios

	/** Jogador com conexao falsa (efeitos mandam pacote) em modo sobrevivencia. */
	@SuppressWarnings("removal")
	private static ServerPlayer mockConnected(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static RecipeView find(List<RecipeView> views, String id) {
		return views.stream().filter(v -> v.id().equals(id)).findFirst().orElse(null);
	}

	private static int count(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
		int n = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				n += entity.getItem().getCount();
			}
		}
		return n;
	}

	private static void requireSeason(Season season) {
		SeasonDate date = SeasonState.current();
		if (date == null || date.season() != season) {
			throw new IllegalStateException("o ambiente deveria ter posto " + season + ", esta em " + date);
		}
	}

	private static void fillBiome(GameTestHelper helper, String biome) {
		BlockPos a = helper.absolutePos(new BlockPos(0, 0, 0));
		BlockPos b = helper.absolutePos(new BlockPos(7, 7, 7));
		net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"fillbiome " + Math.min(a.getX(), b.getX()) + " " + a.getY() + " " + Math.min(a.getZ(), b.getZ()) + " "
						+ Math.max(a.getX(), b.getX()) + " " + b.getY() + " " + Math.max(a.getZ(), b.getZ()) + " " + biome);
	}

	private static BlockPos farm(GameTestHelper helper, int x, int z, Block crop) {
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(x + dx, 1, z + dz, farmland);
			}
		}
		helper.setBlock(x, 2, z, crop.defaultBlockState());
		return helper.absolutePos(new BlockPos(x, 2, z));
	}

	private static int grow(GameTestHelper helper, BlockPos crop, long seed) {
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(seed);
		Block block = level.getBlockState(crop).getBlock();
		for (int i = 0; i < TICKS; i++) {
			level.getBlockState(crop).randomTick(level, crop, random);
		}
		BlockState state = level.getBlockState(crop);
		if (!state.is(block)) {
			throw new IllegalStateException("a planta sumiu: " + state);
		}
		return state.getValue(CropBlock.AGE);
	}
}
