package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.doors.DoubleDoors;
import com.mlin.notas.recipes.RecipeOverlap;
import com.mlin.notas.salvage.SalvageRecipe;
import com.mlin.notas.salvage.SalvageRules;
import com.mlin.notas.sorting.QuickStackServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Game tests do lote de qualidade de vida 2: conflito de receitas, portas duplas, carne podre, guardar rapido e
 * reciclagem de equipamento na fornalha.
 */
public class Qol2GameTest {
	private static final String ENV = "mlinnotas-gametest:qol2";

	// ------------------------------------------------------------------ conflito de receitas

	private record Entry(String id, List<RecipeOverlap.Grid> grids, String result) {
	}

	private static Set<Integer> items(Ingredient ingredient) {
		Set<Integer> out = new HashSet<>();
		ingredient.items().forEach(holder -> out.add(BuiltInRegistries.ITEM.getId(holder.value())));
		return out;
	}

	private static String resultOf(Recipe<?> recipe) {
		ItemStack stack = ItemStack.EMPTY;
		if (recipe instanceof ShapedRecipe shaped) {
			stack = shaped.assemble(CraftingInput.EMPTY);
		} else if (recipe instanceof ShapelessRecipe shapeless) {
			stack = shapeless.assemble(CraftingInput.EMPTY);
		}
		return stack.isEmpty() ? "?" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount();
	}

	/** Formas de casar uma receita de crafting; vazio = receita especial (sem forma fixa), ignorada. */
	private static List<RecipeOverlap.Grid> grids(Recipe<?> recipe) {
		if (recipe.isSpecial()) {
			return List.of();
		}
		if (recipe instanceof ShapedRecipe shaped) {
			List<Set<Integer>> cells = new ArrayList<>();
			for (Optional<Ingredient> ingredient : shaped.getIngredients()) {
				cells.add(ingredient.map(Qol2GameTest::items).orElse(Set.of()));
			}
			return List.of(RecipeOverlap.Grid.shaped(shaped.getWidth(), shaped.getHeight(), cells));
		}
		if (recipe instanceof TransmuteRecipe transmute) {
			// [entrada, material x max]: uma variante para cada quantidade de material
			List<Ingredient> ingredients = transmute.placementInfo().ingredients();
			List<RecipeOverlap.Grid> out = new ArrayList<>();
			for (int materials = 1; materials < ingredients.size(); materials++) {
				List<Set<Integer>> cells = new ArrayList<>();
				for (int i = 0; i <= materials; i++) {
					cells.add(items(ingredients.get(i)));
				}
				out.add(RecipeOverlap.Grid.shapeless(cells));
			}
			return out;
		}
		if (recipe instanceof ShapelessRecipe shapeless) {
			List<Set<Integer>> cells = new ArrayList<>();
			for (Ingredient ingredient : shapeless.placementInfo().ingredients()) {
				cells.add(items(ingredient));
			}
			return List.of(RecipeOverlap.Grid.shapeless(cells));
		}
		return List.of();
	}

	/**
	 * Nenhuma receita de crafting do mod pode casar com a mesma grade que outra receita (vanilla ou do mod) com
	 * resultado diferente: a que carregar primeiro esconderia a outra (ex.: a varinha de madeira tapava a vanilla).
	 */
	@GameTest(environment = ENV, maxTicks = 200)
	public void noCraftingRecipeConflicts(GameTestHelper helper) {
		List<Entry> all = new ArrayList<>();
		for (RecipeHolder<?> holder : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			Recipe<?> recipe = holder.value();
			if (recipe.getType() != RecipeType.CRAFTING) {
				continue;
			}
			List<RecipeOverlap.Grid> grids = grids(recipe);
			if (!grids.isEmpty()) {
				all.add(new Entry(holder.id().identifier().toString(), grids, resultOf(recipe)));
			}
		}
		helper.assertTrue(all.size() > 500, "deveria achar centenas de receitas de crafting, achou " + all.size());
		List<String> conflicts = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			Entry a = all.get(i);
			for (int j = i + 1; j < all.size(); j++) {
				Entry b = all.get(j);
				boolean modA = a.id().startsWith(MlinNotas.MOD_ID + ":");
				boolean modB = b.id().startsWith(MlinNotas.MOD_ID + ":");
				if (!modA && !modB || a.result().equals(b.result())) {
					continue;
				}
				if (overlap(a, b)) {
					conflicts.add(a.id() + " (" + a.result() + ") x " + b.id() + " (" + b.result() + ")");
				}
			}
		}
		if (!conflicts.isEmpty()) {
			conflicts.forEach(c -> MlinNotas.LOGGER.error("Conflito de receita: {}", c));
			helper.fail(conflicts.size() + " conflitos de receita: " + String.join("; ", conflicts));
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ portas duplas

	private static void door(GameTestHelper helper, BlockPos pos, Block block, DoorHingeSide hinge, boolean open) {
		BlockState lower = block.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HINGE, hinge)
				.setValue(DoorBlock.OPEN, open).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		helper.setBlock(pos, lower);
		helper.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static boolean isOpen(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos).getValue(DoorBlock.OPEN);
	}

	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		BlockPos pos = helper.absolutePos(relative);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
		player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
	}

	@GameTest(environment = ENV)
	public void doubleDoorsOpenTogether(GameTestHelper helper) {
		// porta da esquerda (dobradica a esquerda, olhando para o norte) e a parceira a leste
		BlockPos left = new BlockPos(2, 1, 2);
		BlockPos right = new BlockPos(3, 1, 2);
		door(helper, left, Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		door(helper, right, Blocks.SPRUCE_DOOR, DoorHingeSide.RIGHT, false);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 4.5)));

		click(helper, player, left);
		helper.assertTrue(isOpen(helper, left) && isOpen(helper, right), "as duas portas abrem");
		click(helper, player, right.above()); // metade de cima da outra
		helper.assertTrue(!isOpen(helper, left) && !isOpen(helper, right), "as duas fecham");

		// agachado: so a clicada
		player.setShiftKeyDown(true);
		click(helper, player, left);
		helper.assertTrue(isOpen(helper, left) && !isOpen(helper, right), "agachado abre so uma");
		player.setShiftKeyDown(false);
		// estados diferentes antes do clique: nao formam par
		click(helper, player, left);
		helper.assertTrue(!isOpen(helper, left) && !isOpen(helper, right), "fecha so a aberta");
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void doubleDoorsIgnoreMismatchedAndIron(GameTestHelper helper) {
		// mesma dobradica: nao e par
		door(helper, new BlockPos(1, 1, 1), Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		door(helper, new BlockPos(2, 1, 1), Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		// porta de ferro do lado de uma de madeira: nao abre junto
		door(helper, new BlockPos(1, 1, 4), Blocks.OAK_DOOR, DoorHingeSide.LEFT, false);
		door(helper, new BlockPos(2, 1, 4), Blocks.IRON_DOOR, DoorHingeSide.RIGHT, false);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(1.5, 1, 6.5)));
		click(helper, player, new BlockPos(1, 1, 1));
		helper.assertTrue(isOpen(helper, new BlockPos(1, 1, 1)) && !isOpen(helper, new BlockPos(2, 1, 1)), "mesma dobradica");
		click(helper, player, new BlockPos(1, 1, 4));
		helper.assertTrue(isOpen(helper, new BlockPos(1, 1, 4)) && !isOpen(helper, new BlockPos(2, 1, 4)), "ferro fica");
		helper.assertTrue(DoubleDoors.partner(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 4)),
				helper.getBlockState(new BlockPos(1, 1, 4))) == null, "ferro nao e parceira");
		helper.succeed();
	}

	// ------------------------------------------------------------------ carne podre

	@GameTest(environment = ENV, maxTicks = 300)
	public void rottenFleshSmeltsIntoLeather(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SingleRecipeInput input = new SingleRecipeInput(new ItemStack(Items.ROTTEN_FLESH));
		for (RecipeType<? extends AbstractCookingRecipe> type : List.of(RecipeType.SMELTING, RecipeType.SMOKING)) {
			var recipe = level.recipeAccess().getRecipeFor(type, input, level).orElseThrow(() -> new IllegalStateException("sem receita " + type));
			helper.assertTrue(recipe.value().assemble(input).is(Items.LEATHER), "couro em " + type);
			helper.assertTrue(recipe.value().cookingTime() >= 200, "devagar como fundicao normal em " + type);
		}
		helper.setBlock(1, 1, 1, Blocks.FURNACE);
		AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(new BlockPos(1, 1, 1), AbstractFurnaceBlockEntity.class);
		furnace.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 2));
		furnace.setItem(1, new ItemStack(Items.COAL));
		helper.succeedWhen(() -> helper.assertTrue(furnace.getItem(2).is(Items.LEATHER), "couro na saida"));
	}

	// ------------------------------------------------------------------ guardar rapido

	@SuppressWarnings("removal")
	@GameTest(environment = ENV)
	public void quickStackMovesKnownKindsFromMainInventory(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(1, 1, 1), ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 60));
		chest.setItem(5, new ItemStack(Items.OAK_LOG, 1));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
			player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
			Inventory inv = player.getInventory();
			inv.clearContent();
			inv.setItem(0, new ItemStack(Items.COBBLESTONE, 64)); // hotbar: fica
			inv.setItem(9, new ItemStack(Items.COBBLESTONE, 20));
			inv.setItem(10, new ItemStack(Items.SAND, 10)); // nao existe no bau: fica
			inv.setItem(20, new ItemStack(Items.OAK_LOG, 7));
			inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.OAK_LOG, 5)); // mao secundaria: fica
			player.openMenu(chest);
			helper.assertTrue(player.containerMenu instanceof ChestMenu, "bau aberto");
			int moved = QuickStackServer.quickStack(player);
			helper.assertTrue(moved == 27, "deveria guardar 20 pedregulhos e 7 toras, guardou " + moved);
			helper.assertTrue(chest.getItem(0).getCount() == 64, "completa a pilha de 60");
			int cobble = 0;
			int logs = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				cobble += s.is(Items.COBBLESTONE) ? s.getCount() : 0;
				logs += s.is(Items.OAK_LOG) ? s.getCount() : 0;
			}
			helper.assertTrue(cobble == 80 && logs == 8, "bau com 80 pedregulhos e 8 toras: " + cobble + "/" + logs);
			helper.assertTrue(inv.getItem(0).getCount() == 64, "hotbar intacta");
			helper.assertTrue(inv.getItem(9).isEmpty() && inv.getItem(20).isEmpty(), "inventario principal esvaziado");
			helper.assertTrue(inv.getItem(10).is(Items.SAND), "areia fica");
			helper.assertTrue(inv.getItem(Inventory.SLOT_OFFHAND).getCount() == 5, "mao secundaria intacta");
			player.closeContainer();
			helper.assertTrue(QuickStackServer.quickStack(player) == 0, "sem bau aberto nao faz nada");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ reciclagem

	/** Toda peca reconhecida pelo SalvageRules tem receita no lugar certo (alto-forno sempre, fornalha se nao for so do alto-forno). */
	@GameTest(environment = ENV, maxTicks = 100)
	public void salvageRecipesCoverEveryPiece(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int checked = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			var piece = SalvageRules.piece(id);
			if (piece.isEmpty()) {
				continue;
			}
			ItemStack stack = new ItemStack(item);
			SingleRecipeInput input = new SingleRecipeInput(stack);
			ItemStack expected = SalvageRecipe.salvage(stack);
			Optional<? extends RecipeHolder<?>> blasting = level.recipeAccess().getRecipeFor(RecipeType.BLASTING, input, level);
			Optional<? extends RecipeHolder<?>> smelting = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
			if (expected.isEmpty()) {
				helper.assertTrue(blasting.isEmpty() && smelting.isEmpty(), id + " nao devolve nada: nao deveria entrar");
				continue;
			}
			helper.assertTrue(blasting.isPresent() && blasting.get().value() instanceof SalvageRecipe, id + " sem reciclagem no alto-forno");
			helper.assertTrue(ItemStack.matches(((SalvageRecipe) blasting.get().value()).assemble(input), expected), id + " resultado errado");
			if (piece.get().material().blastOnly()) {
				helper.assertTrue(smelting.isEmpty(), id + " so no alto-forno");
			} else {
				helper.assertTrue(smelting.isPresent() && smelting.get().value() instanceof SalvageRecipe, id + " sem reciclagem na fornalha");
			}
			checked++;
		}
		helper.assertTrue(checked > 150, "deveria conferir centenas de pecas, conferiu " + checked);
		// diamante desgastado nao entra; diamante novo vai pelo alto-forno
		ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
		worn.setDamageValue(100);
		helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.BLASTING, new SingleRecipeInput(worn), level).isEmpty(), "espada gasta");
		// o visualizador mostra a reciclagem como alto-forno, com o exemplo do JSON
		var view = com.mlin.notas.recipes.RecipeExporter.export(level.getServer().getRecipeManager().byKey(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, MlinNotas.id("salvage_diamond_from_blasting")))
				.orElseThrow());
		helper.assertTrue(view != null && view.kind() == com.mlin.notas.recipes.RecipeKind.BLASTING
				&& view.output() == BuiltInRegistries.ITEM.getId(Items.DIAMOND), "visualizador");
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 400)
	public void furnaceSalvagesWornIronAndEnchantedXp(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.FURNACE);
		helper.setBlock(4, 1, 1, Blocks.BLAST_FURNACE);
		AbstractFurnaceBlockEntity furnace = helper.getBlockEntity(new BlockPos(1, 1, 1), AbstractFurnaceBlockEntity.class);
		AbstractFurnaceBlockEntity blast = helper.getBlockEntity(new BlockPos(4, 1, 1), AbstractFurnaceBlockEntity.class);
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.setDamageValue(pick.getMaxDamage() / 2);
		var enchantments = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		pick.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 3);
		furnace.setItem(0, pick);
		furnace.setItem(1, new ItemStack(Items.COAL));
		blast.setItem(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
		blast.setItem(1, new ItemStack(Items.COAL));
		helper.succeedWhen(() -> {
			helper.assertTrue(blast.getItem(2).is(Items.DIAMOND) && blast.getItem(2).getCount() == 4, "4 diamantes do peitoral novo");
			// 3 * 0,5 * 0,5 = 0,75 lingote = 6 pepitas
			helper.assertTrue(furnace.getItem(2).is(Items.IRON_NUGGET) && furnace.getItem(2).getCount() == 6,
					"6 pepitas da picareta pela metade, saiu " + furnace.getItem(2));
			List<ExperienceOrb> orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class,
					new AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(3));
			helper.assertTrue(!orbs.isEmpty(), "XP extra do encantamento");
		});
	}

	private static boolean overlap(Entry a, Entry b) {
		for (RecipeOverlap.Grid x : a.grids()) {
			for (RecipeOverlap.Grid y : b.grids()) {
				if (RecipeOverlap.overlaps(x, y)) {
					return true;
				}
			}
		}
		return false;
	}
}
