package com.mlin.notas.gametest;

import com.mlin.notas.enchanting.EnchantRules;
import com.mlin.notas.enchanting.SelectiveEnchantMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests do encantamento escolhido na mesa de encantamento.
 */
public class EnchantingGameTest {
	private static final BlockPos TABLE = new BlockPos(3, 1, 3);

	private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** Mesa no centro e o anel de estantes a 2 blocos (com ar entre eles), em 1 ou 2 alturas. */
	private static void buildTable(GameTestHelper helper, boolean bookshelves) {
		helper.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
		if (!bookshelves) {
			return;
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					helper.setBlock(TABLE.offset(dx, 0, dz), Blocks.BOOKSHELF);
					helper.setBlock(TABLE.offset(dx, 1, dz), Blocks.BOOKSHELF);
				}
			}
		}
	}

	private static SelectiveEnchantMenu menu(GameTestHelper helper, ServerPlayer player) {
		return new SelectiveEnchantMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
	}

	@SuppressWarnings("removal")
	@GameTest
	public void enchantingTableOpensSelectiveMenu(GameTestHelper helper) {
		buildTable(helper, false);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			BlockPos abs = helper.absolutePos(TABLE);
			player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
			helper.assertTrue(player.containerMenu instanceof SelectiveEnchantMenu, "a mesa deveria abrir o menu de encantamento escolhido");
			player.closeContainer();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	@GameTest
	public void bookshelvesAreCounted(GameTestHelper helper) {
		buildTable(helper, true);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		SelectiveEnchantMenu menu = menu(helper, player);
		helper.assertTrue(menu.bookshelves() >= EnchantRules.MAX_BOOKSHELVES, "anel completo deveria contar >= 15 estantes, contou " + menu.bookshelves());
		helper.succeed();
	}

	@GameTest
	public void chosenEnchantmentIsAppliedAndCharged(GameTestHelper helper) {
		buildTable(helper, true);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.experienceLevel = 30;
		SelectiveEnchantMenu menu = menu(helper, player);
		menu.slots.get(SelectiveEnchantMenu.ITEM_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
		menu.slots.get(SelectiveEnchantMenu.LAPIS_SLOT).set(new ItemStack(Items.LAPIS_LAZULI, 64));

		EnchantRules.Check check = menu.tryEnchant(player, enchantment(helper, Enchantments.SHARPNESS), 5);
		helper.assertTrue(check == EnchantRules.Check.OK, "Afiacao V deveria aplicar, resultado " + check);
		ItemStack sword = menu.item();
		helper.assertTrue(sword.getEnchantments().getLevel(enchantment(helper, Enchantments.SHARPNESS)) == 5, "espada com Afiacao V");
		int xp = EnchantRules.xpCost(1, 0, 5);
		helper.assertTrue(player.experienceLevel == 30 - xp, "deveria cobrar " + xp + " niveis, sobrou " + player.experienceLevel);
		helper.assertTrue(menu.lapis().getCount() == 64 - 5, "deveria cobrar 5 lapis");

		EnchantRules.Check smite = menu.tryEnchant(player, enchantment(helper, Enchantments.SMITE), 1);
		helper.assertTrue(smite == EnchantRules.Check.INCOMPATIBLE, "Julgamento conflita com Afiacao, resultado " + smite);
		helper.succeed();
	}

	@GameTest
	public void upgradingExistingEnchantmentPaysOnlyNewLevels(GameTestHelper helper) {
		buildTable(helper, true);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.experienceLevel = 30;
		SelectiveEnchantMenu menu = menu(helper, player);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(enchantment(helper, Enchantments.SHARPNESS), 3);
		menu.slots.get(SelectiveEnchantMenu.ITEM_SLOT).set(sword);
		menu.slots.get(SelectiveEnchantMenu.LAPIS_SLOT).set(new ItemStack(Items.LAPIS_LAZULI, 10));

		helper.assertTrue(menu.tryEnchant(player, enchantment(helper, Enchantments.SHARPNESS), 3) == EnchantRules.Check.ALREADY_AT_LEVEL, "nivel igual e recusado");
		helper.assertTrue(menu.tryEnchant(player, enchantment(helper, Enchantments.SHARPNESS), 4) == EnchantRules.Check.OK, "III -> IV");
		helper.assertTrue(player.experienceLevel == 30 - EnchantRules.xpCost(1, 3, 4), "so paga o nivel IV");
		helper.assertTrue(menu.lapis().getCount() == 9, "1 lapis");
		helper.succeed();
	}

	@GameTest
	public void withoutBookshelvesOnlyLevelOne(GameTestHelper helper) {
		buildTable(helper, false);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.experienceLevel = 30;
		SelectiveEnchantMenu menu = menu(helper, player);
		menu.slots.get(SelectiveEnchantMenu.ITEM_SLOT).set(new ItemStack(Items.DIAMOND_PICKAXE));
		menu.slots.get(SelectiveEnchantMenu.LAPIS_SLOT).set(new ItemStack(Items.LAPIS_LAZULI, 10));
		helper.assertTrue(menu.tryEnchant(player, enchantment(helper, Enchantments.EFFICIENCY), 3) == EnchantRules.Check.NEEDS_BOOKSHELVES, "nivel III exige estantes");
		helper.assertTrue(menu.tryEnchant(player, enchantment(helper, Enchantments.EFFICIENCY), 1) == EnchantRules.Check.OK, "nivel I sempre liberado");
		helper.succeed();
	}

	@GameTest
	public void bookBecomesEnchantedBookAndWrongItemIsRefused(GameTestHelper helper) {
		buildTable(helper, true);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.experienceLevel = 30;
		SelectiveEnchantMenu menu = menu(helper, player);
		menu.slots.get(SelectiveEnchantMenu.LAPIS_SLOT).set(new ItemStack(Items.LAPIS_LAZULI, 10));

		menu.slots.get(SelectiveEnchantMenu.ITEM_SLOT).set(new ItemStack(Items.DIAMOND_PICKAXE));
		helper.assertTrue(menu.tryEnchant(player, enchantment(helper, Enchantments.SHARPNESS), 1) == EnchantRules.Check.NOT_APPLICABLE, "afiacao nao serve em picareta");
		helper.assertTrue(menu.tryEnchant(player, enchantment(helper, Enchantments.MENDING), 1) == EnchantRules.Check.NOT_APPLICABLE, "remendo (tesouro) nao sai da mesa");

		menu.slots.get(SelectiveEnchantMenu.ITEM_SLOT).set(new ItemStack(Items.BOOK));
		helper.assertTrue(menu.tryEnchant(player, enchantment(helper, Enchantments.PROTECTION), 2) == EnchantRules.Check.OK, "livro aceita protecao");
		ItemStack book = menu.item();
		helper.assertTrue(book.is(Items.ENCHANTED_BOOK), "vira livro encantado");
		ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		helper.assertTrue(stored.getLevel(enchantment(helper, Enchantments.PROTECTION)) == 2, "livro guarda Protecao II");
		helper.succeed();
	}
}
