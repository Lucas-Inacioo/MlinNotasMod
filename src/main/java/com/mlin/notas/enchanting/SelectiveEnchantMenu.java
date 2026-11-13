package com.mlin.notas.enchanting;

import com.mlin.notas.registry.ModMenus;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;

import java.util.Optional;

/**
 * Menu da mesa de encantamento com escolha: um slot para o item, um para lapis, e o jogador escolhe
 * qual encantamento e qual nivel aplicar (pedido via {@link EnchantRequestPayload}).
 * As estantes ao redor (contadas como na vanilla) limitam o nivel maximo; o custo esta em {@link EnchantRules}.
 */
public class SelectiveEnchantMenu extends AbstractContainerMenu {
	public static final int ITEM_SLOT = 0;
	public static final int LAPIS_SLOT = 1;
	public static final int ITEM_X = 20;
	public static final int ITEM_Y = 20;
	public static final int LAPIS_X = 20;
	public static final int LAPIS_Y = 42;
	/** Slot do reagente tematico do encantamento ({@link EnchantReagents}). */
	public static final int REAGENT_SLOT = 2;
	public static final int REAGENT_X = 20;
	public static final int REAGENT_Y = 64;
	public static final int INVENTORY_X = 57;
	public static final int INVENTORY_Y = 104;

	private final Container enchantSlots = new SimpleContainer(3) {
		@Override
		public void setChanged() {
			super.setChanged();
			SelectiveEnchantMenu.this.slotsChanged(this);
		}
	};
	private final ContainerLevelAccess access;
	private final DataSlot bookshelves = DataSlot.standalone();
	/** Nivel de Mineracao do mundo (teto dos encantamentos, {@link EnchantRules#miningCap}). */
	private final DataSlot miningLevel = DataSlot.standalone();

	/** Construtor do cliente (o tipo de menu cria o menu sem acesso ao mundo). */
	public SelectiveEnchantMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public SelectiveEnchantMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(ModMenus.SELECTIVE_ENCHANT, containerId);
		this.access = access;
		this.addSlot(new Slot(enchantSlots, ITEM_SLOT, ITEM_X, ITEM_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return EnchantOptions.canHold(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new Slot(enchantSlots, LAPIS_SLOT, LAPIS_X, LAPIS_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.LAPIS_LAZULI);
			}
		});
		this.addSlot(new Slot(enchantSlots, REAGENT_SLOT, REAGENT_X, REAGENT_Y));
		this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
		this.addDataSlot(bookshelves);
		this.bookshelves.set(access.evaluate(SelectiveEnchantMenu::countBookshelves, 0));
		this.addDataSlot(miningLevel);
		this.miningLevel.set(access.evaluate((level, pos) -> worldMiningLevel(level), 1));
	}

	/** Estantes validas ao redor da mesa (mesma regra da vanilla, incluindo o espaco livre entre mesa e estante). */
	public static int countBookshelves(Level level, BlockPos pos) {
		int count = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			if (EnchantingTableBlock.isValidBookShelf(level, pos, offset)) {
				count++;
			}
		}
		return count;
	}

	public int bookshelves() {
		return bookshelves.get();
	}

	/** Nivel de Mineracao do mundo (1 = Madeira .. 12 = Celestio), sincronizado com o cliente. */
	public int miningLevel() {
		return Math.max(1, miningLevel.get());
	}

	static int worldMiningLevel(Level level) {
		return level.getServer() == null ? 1 : com.mlin.notas.skills.SkillServer.progress(level.getServer()).level(com.mlin.notas.skills.SkillBranch.MINING);
	}

	public ItemStack item() {
		return enchantSlots.getItem(ITEM_SLOT);
	}

	public ItemStack lapis() {
		return enchantSlots.getItem(LAPIS_SLOT);
	}

	public ItemStack reagentStack() {
		return enchantSlots.getItem(REAGENT_SLOT);
	}

	/** Quanto do reagente {@code reagent} esta no slot de reagente (0 se o item e outro). */
	public int reagentCount(EnchantReagents.Reagent reagent) {
		ItemStack stack = reagentStack();
		return !stack.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(reagent.item())
				? stack.getCount() : 0;
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		access.execute((level, pos) -> {
			bookshelves.set(countBookshelves(level, pos));
			miningLevel.set(worldMiningLevel(level));
		});
		broadcastChanges();
	}

	/** Valida e aplica o encantamento pedido. Roda no servidor. */
	public EnchantRules.Check tryEnchant(ServerPlayer player, Holder<Enchantment> enchantment, int level) {
		ItemStack stack = item();
		Optional<EnchantOptions.Option> option = EnchantOptions.find(player.level().registryAccess(), stack, enchantment);
		int current = option.map(EnchantOptions.Option::currentLevel).orElse(0);
		EnchantRules.Check check = EnchantRules.check(
				EnchantOptions.canHold(stack),
				option.isPresent(),
				option.map(EnchantOptions.Option::compatible).orElse(false),
				current,
				level,
				option.map(EnchantOptions.Option::maxLevel).orElse(0),
				option.map(EnchantOptions.Option::anvilCost).orElse(1),
				bookshelves(),
				worldMiningLevel(player.level()),
				player.experienceLevel,
				lapis().getCount(),
				option.map(o -> o.reagent().base()).orElse(1),
				option.map(o -> reagentCount(o.reagent())).orElse(0),
				player.hasInfiniteMaterials());
		if (check != EnchantRules.Check.OK) {
			return check;
		}

		int xp = EnchantRules.xpCost(option.get().anvilCost(), current, level);
		int lapisCost = EnchantRules.lapisCost(current, level);
		ItemStack result = stack;
		if (stack.is(Items.BOOK)) {
			result = stack.transmuteCopy(Items.ENCHANTED_BOOK);
		}
		result.enchant(enchantment, level);
		enchantSlots.setItem(ITEM_SLOT, result);
		if (!player.hasInfiniteMaterials()) {
			player.onEnchantmentPerformed(result, xp);
			lapis().shrink(lapisCost);
			if (lapis().isEmpty()) {
				enchantSlots.setItem(LAPIS_SLOT, ItemStack.EMPTY);
			}
			reagentStack().shrink(EnchantReagents.cost(option.get().reagent().base(), current, level));
			if (reagentStack().isEmpty()) {
				enchantSlots.setItem(REAGENT_SLOT, ItemStack.EMPTY);
			}
		}
		player.awardStat(Stats.ENCHANT_ITEM);
		CriteriaTriggers.ENCHANTED_ITEM.trigger(player, result, xp);
		access.execute((world, pos) -> world.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F,
				world.getRandom().nextFloat() * 0.1F + 0.9F));
		enchantSlots.setChanged();
		return check;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> clearContainer(player, enchantSlots));
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Blocks.ENCHANTING_TABLE);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.size() > index ? this.slots.get(index) : null;
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int inventoryStart = 3;
		int inventoryEnd = this.slots.size();
		if (index < inventoryStart) {
			if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(Items.LAPIS_LAZULI)) {
			if (!moveItemStackTo(stack, LAPIS_SLOT, LAPIS_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (EnchantOptions.canHold(stack) && !this.slots.get(ITEM_SLOT).hasItem()) {
			this.slots.get(ITEM_SLOT).setByPlayer(stack.split(1));
		} else if (!moveItemStackTo(stack, REAGENT_SLOT, REAGENT_SLOT + 1, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}
}
