package com.mlin.notas.storage;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Menu do terminal. Os itens da rede nao sao slots: a lista e desenhada pela tela a partir do que o servidor
 * sincroniza ({@link TerminalPayloads.Sync}, so as mudancas, a cada {@link #SYNC_INTERVAL} ticks ou logo depois de
 * uma acao) e os cliques viram {@link TerminalPayloads.Action}. Slots de verdade: inventario do jogador e, na
 * variante de crafting, a grade 3x3 + resultado (a grade se reabastece da rede depois de cada craft).
 */
public class TerminalMenu extends AbstractContainerMenu {
	public static final int COLUMNS = 9;
	public static final int ROWS = 6;
	public static final int SLOT = 18;
	/** Largura do painel principal e da faixa de crafting a esquerda (so na variante de crafting). */
	public static final int MAIN_WIDTH = 195;
	public static final int CRAFT_WIDTH = 80;
	public static final int GRID_X = 9;
	public static final int GRID_Y = 18;
	public static final int STATUS_Y = 130;
	public static final int INV_Y = 144;
	public static final int HOTBAR_Y = INV_Y + 58;
	public static final int HEIGHT = HOTBAR_Y + 24;
	public static final int CRAFT_GRID_X = 9;
	public static final int CRAFT_GRID_Y = 30;
	public static final int RESULT_X = 27;
	public static final int RESULT_Y = 110;
	public static final int SYNC_INTERVAL = 10;
	/** Mudancas por pacote (o resto vai em outro pacote no mesmo tick). */
	private static final int CHANGES_PER_PACKET = 256;

	/** Dados de abertura: posicao do terminal, se e o de crafting e se foi aberto de longe pelo Tablet de Rede. */
	public record Data(BlockPos pos, boolean crafting, boolean remote) {
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Data::pos,
				ByteBufCodecs.BOOL, Data::crafting,
				ByteBufCodecs.BOOL, Data::remote,
				Data::new);

		public Data(BlockPos pos, boolean crafting) {
			this(pos, crafting, false);
		}
	}

	private final Player player;
	private final Data data;
	private final int offsetX;
	private final CraftingContainer craftSlots;
	private final ResultContainer resultSlots = new ResultContainer();
	private final int inventoryStart;

	// servidor
	private final DeltaTracker<ItemKey> tracker = new DeltaTracker<>();
	private int syncTimer;
	private boolean dirty = true;
	private List<Integer> lastStats = List.of();
	/** Chunks seguras por ticket enquanto o menu remoto esta aberto (soltas no {@link #removed}). */
	private final java.util.Set<Long> ticketedChunks = new java.util.HashSet<>();

	// cliente: numero -> item/quantidade; version muda a cada sincronizacao (a tela refaz a lista)
	private final Map<Integer, ItemStack> clientItems = new HashMap<>();
	private final Map<Integer, Long> clientCounts = new HashMap<>();
	private int clientVersion;
	private int clientUnits;
	private int clientContainers;
	private int clientFreeTypes;
	private boolean clientTruncated;

	public TerminalMenu(int containerId, Inventory inventory, Data data) {
		super(StorageRegistry.TERMINAL_MENU, containerId);
		this.player = inventory.player;
		this.data = data;
		this.offsetX = data.crafting() ? CRAFT_WIDTH : 0;
		this.craftSlots = new TransientCraftingContainer(this, 3, 3);
		if (data.crafting()) {
			this.addSlot(new RefillingResultSlot(player, craftSlots, resultSlots, 0, RESULT_X, RESULT_Y));
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					this.addSlot(new Slot(craftSlots, col + row * 3, CRAFT_GRID_X + col * SLOT, CRAFT_GRID_Y + row * SLOT));
				}
			}
		}
		this.inventoryStart = this.slots.size();
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, offsetX + GRID_X + col * SLOT, INV_Y + row * SLOT));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, offsetX + GRID_X + col * SLOT, HOTBAR_Y));
		}
	}

	public Data data() {
		return data;
	}

	public boolean crafting() {
		return data.crafting();
	}

	/** Deslocamento do painel principal (largura da faixa de crafting, ou 0). */
	public int offsetX() {
		return offsetX;
	}

	public int inventoryStart() {
		return inventoryStart;
	}

	@Override
	public boolean stillValid(Player player) {
		if (data.remote()) {
			// de longe: o tablet vinculado continua no inventario e o terminal existe (chunk segura por ticket)
			return NetworkTablet.hasTabletFor(player, data.pos())
					&& player.level().getBlockState(data.pos()).getBlock() instanceof StorageTerminalBlock;
		}
		return player.level().getBlockState(data.pos()).getBlock() instanceof StorageTerminalBlock
				&& player.distanceToSqr(Vec3.atCenterOf(data.pos())) <= 64.0;
	}

	/** Aberto de longe pelo Tablet de Rede. */
	public boolean remote() {
		return data.remote();
	}

	private StorageNetwork network() {
		return StorageNetwork.find(player.level(), data.pos());
	}

	// --- sincronizacao (servidor) ---

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null && (dirty || ++syncTimer >= SYNC_INTERVAL)) {
			syncTimer = 0;
			dirty = false;
			sync(serverPlayer);
		}
	}

	/** Numero de sincronizacao de um tipo ja enviado (o que o cliente manda de volta nos cliques), ou -1. */
	public int syncIdOf(ItemStack stack) {
		return stack.isEmpty() ? -1 : tracker.find(ItemKey.of(stack));
	}

	/** Forca o reenvio de tudo na proxima atualizacao. */
	public void markDirty() {
		dirty = true;
	}

	private void sync(ServerPlayer serverPlayer) {
		StorageNetwork network = network();
		if (data.remote() && serverPlayer.level() instanceof ServerLevel level) {
			NetworkTablet.keepLoaded(level, data.pos(), network.graph(), ticketedChunks);
		}
		List<DeltaTracker.Change<ItemKey>> changes = tracker.update(network.totals());
		int freeTypes = 0;
		for (StorageUnitBlockEntity unit : network.units()) {
			freeTypes += unit.cell().maxTypes() - unit.cell().typeCount();
		}
		List<Integer> stats = List.of(network.units().size(), network.containerCount(), network.graph().truncated() ? 1 : 0, freeTypes);
		if (changes.isEmpty() && stats.equals(lastStats) || !ServerPlayNetworking.canSend(serverPlayer, TerminalPayloads.Sync.TYPE)) {
			return;
		}
		lastStats = stats;
		int start = 0;
		do {
			int end = Math.min(changes.size(), start + CHANGES_PER_PACKET);
			List<TerminalPayloads.Change> chunk = new ArrayList<>(end - start);
			for (DeltaTracker.Change<ItemKey> change : changes.subList(start, end)) {
				chunk.add(new TerminalPayloads.Change(change.id(), change.key() == null ? ItemStack.EMPTY : change.key().template(), change.count()));
			}
			ServerPlayNetworking.send(serverPlayer, new TerminalPayloads.Sync(containerId, chunk, stats.get(0), stats.get(1), stats.get(2) == 1, freeTypes));
			start = end;
		} while (start < changes.size());
	}

	// --- sincronizacao (cliente) ---

	public void applySync(TerminalPayloads.Sync sync) {
		for (TerminalPayloads.Change change : sync.changes()) {
			if (!change.stack().isEmpty()) {
				clientItems.put(change.id(), change.stack());
			}
			if (change.count() <= 0) {
				clientCounts.remove(change.id());
			} else {
				clientCounts.put(change.id(), change.count());
			}
		}
		clientUnits = sync.units();
		clientContainers = sync.containers();
		clientTruncated = sync.truncated();
		clientFreeTypes = sync.freeTypes();
		clientVersion++;
	}

	public Map<Integer, ItemStack> clientItems() {
		return clientItems;
	}

	public Map<Integer, Long> clientCounts() {
		return clientCounts;
	}

	public int clientVersion() {
		return clientVersion;
	}

	public int clientUnits() {
		return clientUnits;
	}

	public int clientContainers() {
		return clientContainers;
	}

	public int clientFreeTypes() {
		return clientFreeTypes;
	}

	public boolean clientTruncated() {
		return clientTruncated;
	}

	// --- acoes (servidor) ---

	/** Clique na lista do terminal, validado no servidor. */
	public void handleAction(ServerPlayer player, int action, int id) {
		ItemKey key = id < 0 ? null : tracker.keyOf(id);
		ItemStack carried = getCarried();
		StorageNetwork network = network();
		switch (action) {
			case TerminalPayloads.Action.LEFT -> {
				if (!carried.isEmpty()) {
					setCarried(network.insert(carried));
				} else if (key != null) {
					setCarried(network.extract(key, key.maxStackSize()));
				}
			}
			case TerminalPayloads.Action.RIGHT -> {
				if (!carried.isEmpty()) {
					ItemStack rest = network.insert(carried.copyWithCount(1));
					if (rest.isEmpty()) {
						carried.shrink(1);
						setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
					}
				} else if (key != null) {
					long available = network.storage().count(key);
					int half = (int) ((Math.min(available, key.maxStackSize()) + 1) / 2);
					setCarried(network.extract(key, half));
				}
			}
			case TerminalPayloads.Action.SHIFT -> {
				if (key != null) {
					ItemStack taken = network.extract(key, key.maxStackSize());
					if (!taken.isEmpty()) {
						player.getInventory().add(taken);
						if (!taken.isEmpty()) {
							ItemStack rest = network.insert(taken);
							if (!rest.isEmpty()) {
								player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
							}
						}
					}
				}
			}
			case TerminalPayloads.Action.CLEAR_GRID -> {
				if (data.crafting()) {
					for (int i = 0; i < craftSlots.getContainerSize(); i++) {
						ItemStack stack = craftSlots.getItem(i);
						if (!stack.isEmpty()) {
							ItemStack rest = network.insert(stack);
							craftSlots.setItem(i, ItemStack.EMPTY);
							if (!rest.isEmpty()) {
								player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
							}
						}
					}
				}
			}
			default -> {
			}
		}
		markDirty();
		broadcastChanges();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		if (data.crafting() && slotIndex == 0) {
			// resultado: igual a mesa de crafting (o laco do clique repete enquanto der para fazer)
			ItemStack stack = slot.getItem();
			ItemStack clicked = stack.copy();
			stack.getItem().onCraftedBy(stack, player);
			if (!this.moveItemStackTo(stack, inventoryStart, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, clicked);
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
			if (stack.getCount() == clicked.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, stack);
			player.drop(stack, false, Prediction.PREDICTED);
			return clicked;
		}
		if (player.level().isClientSide()) {
			// a rede so existe no servidor: ele move e reenvia os slots
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack rest = network().insert(stack);
		if (rest.getCount() != stack.getCount()) {
			slot.setByPlayer(rest);
			markDirty();
		} else if (slotIndex < inventoryStart && !this.moveItemStackTo(stack, inventoryStart, this.slots.size(), false)) {
			// grade de crafting com a rede cheia: vai para o inventario
			return ItemStack.EMPTY;
		} else if (slotIndex < inventoryStart) {
			slot.setChanged();
		}
		// nunca repete: a pilha inteira ja foi tratada
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return target.container != resultSlots && super.canTakeItemForPickAll(carried, target);
	}

	// --- crafting ---

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (data.crafting() && container == craftSlots && player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel level) {
			CraftingInput input = craftSlots.asCraftInput();
			ItemStack result = ItemStack.EMPTY;
			Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
			if (recipe.isPresent() && resultSlots.setRecipeUsed(serverPlayer, recipe.get())) {
				ItemStack assembled = recipe.get().value().assemble(input);
				if (assembled.isItemEnabled(level.enabledFeatures())) {
					result = assembled;
				}
			}
			resultSlots.setItem(0, result);
			setRemoteSlot(0, result);
			if (serverPlayer.connection != null) {
				serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
			}
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (data.remote() && player.level() instanceof ServerLevel level) {
			NetworkTablet.release(level, ticketedChunks);
		}
		if (data.crafting() && player instanceof ServerPlayer) {
			StorageNetwork network = network();
			for (int i = 0; i < craftSlots.getContainerSize(); i++) {
				ItemStack stack = craftSlots.removeItemNoUpdate(i);
				if (!stack.isEmpty()) {
					ItemStack rest = network.insert(stack);
					if (!rest.isEmpty()) {
						player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
					}
				}
			}
		}
	}

	/** Resultado que, depois de cada craft, repoe da rede o ingrediente que acabou em cada casa da grade. */
	private class RefillingResultSlot extends ResultSlot {
		RefillingResultSlot(Player player, CraftingContainer craftSlots, Container resultSlots, int id, int x, int y) {
			super(player, craftSlots, resultSlots, id, x, y);
		}

		@Override
		public void onTake(Player player, ItemStack carried) {
			ItemStack[] before = new ItemStack[craftSlots.getContainerSize()];
			for (int i = 0; i < before.length; i++) {
				before[i] = craftSlots.getItem(i).copy();
			}
			super.onTake(player, carried);
			if (player.level().isClientSide()) {
				return;
			}
			StorageNetwork network = null;
			for (int i = 0; i < before.length; i++) {
				if (!before[i].isEmpty() && craftSlots.getItem(i).isEmpty()) {
					if (network == null) {
						network = network();
					}
					ItemStack refill = network.extract(ItemKey.of(before[i]), 1);
					if (!refill.isEmpty()) {
						craftSlots.setItem(i, refill);
					}
				}
			}
			if (network != null) {
				markDirty();
			}
		}
	}
}
