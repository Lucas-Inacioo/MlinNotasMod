package com.mlin.notas.storage;

import com.mlin.notas.util.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rede de armazenamento montada a partir de um bloco (ponte entre o mundo e {@link NetworkGraph} /
 * {@link NetworkStorage}). E barata de montar; quem usa (o terminal) monta de novo a cada atualizacao, entao nunca
 * guarda referencia para bloco quebrado.
 */
public final class StorageNetwork {
	private final NetworkGraph.Result graph;
	private final NetworkStorage<ItemKey> storage;
	private final List<StorageUnitBlockEntity> units;
	private final int containerCount;

	private StorageNetwork(NetworkGraph.Result graph, List<StorageUnitBlockEntity> units, List<StorageTarget<ItemKey>> targets, int containers) {
		this.graph = graph;
		this.units = units;
		this.storage = new NetworkStorage<>(targets);
		this.containerCount = containers;
	}

	/** Rede que passa por {@code start} (vazia se ali nao ha cabo, unidade nem terminal). */
	public static StorageNetwork find(Level level, BlockPos start) {
		NetworkGraph.Result graph = NetworkGraph.discover(toPos(start), pos -> kindAt(level, toBlockPos(pos)));
		List<StorageUnitBlockEntity> units = new ArrayList<>();
		List<StorageTarget<ItemKey>> targets = new ArrayList<>();
		for (Pos pos : graph.units()) {
			if (level.getBlockEntity(toBlockPos(pos)) instanceof StorageUnitBlockEntity unit) {
				units.add(unit);
				targets.add(unit.cell());
			}
		}
		// baus duplos: a outra metade entra junto, mesmo sem encostar na rede
		Set<BlockPos> containerPositions = new LinkedHashSet<>();
		for (Pos pos : graph.containers()) {
			BlockPos blockPos = toBlockPos(pos);
			containerPositions.add(blockPos);
			BlockState state = level.getBlockState(blockPos);
			if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				BlockPos other = blockPos.relative(ChestBlock.getConnectedDirection(state));
				if (kindAt(level, other) == NetworkGraph.NodeKind.CONTAINER) {
					containerPositions.add(other);
				}
			}
		}
		int containers = 0;
		for (BlockPos pos : containerPositions) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			// container com loot ainda nao gerado: mexer nele geraria o loot (e revelaria o conteudo)
			if (blockEntity instanceof Container container
					&& !(blockEntity instanceof RandomizableContainer randomizable && randomizable.getLootTable() != null)) {
				targets.add(new ContainerTarget(container));
				containers++;
			}
		}
		return new StorageNetwork(graph, List.copyOf(units), targets, containers);
	}

	public static NetworkGraph.NodeKind kindAt(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return NetworkGraph.NodeKind.NONE;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof StorageCableBlock) {
			return NetworkGraph.NodeKind.CABLE;
		}
		if (state.getBlock() instanceof StorageUnitBlock) {
			return NetworkGraph.NodeKind.UNIT;
		}
		if (state.getBlock() instanceof StorageTerminalBlock) {
			return NetworkGraph.NodeKind.TERMINAL;
		}
		if (state.is(StorageRegistry.CONNECTABLE) && level.getBlockEntity(pos) instanceof Container) {
			return NetworkGraph.NodeKind.CONTAINER;
		}
		return NetworkGraph.NodeKind.NONE;
	}

	public NetworkStorage<ItemKey> storage() {
		return storage;
	}

	public NetworkGraph.Result graph() {
		return graph;
	}

	public List<StorageUnitBlockEntity> units() {
		return units;
	}

	public int containerCount() {
		return containerCount;
	}

	public boolean isEmpty() {
		return storage.targets().isEmpty();
	}

	public Map<ItemKey, Long> totals() {
		return storage.totals();
	}

	/** Guarda a pilha (a pilha passada nao e alterada); devolve o que nao coube. */
	public ItemStack insert(ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		long moved = storage.insert(ItemKey.of(stack), stack.getCount(), false);
		return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) moved);
	}

	/** Tira ate {@code amount} do tipo; devolve a pilha tirada (pode vir menor ou vazia). */
	public ItemStack extract(ItemKey key, int amount) {
		if (amount <= 0) {
			return ItemStack.EMPTY;
		}
		long moved = storage.extract(key, amount, false);
		return moved <= 0 ? ItemStack.EMPTY : key.toStack((int) moved);
	}

	private static Pos toPos(BlockPos pos) {
		return new Pos(pos.getX(), pos.getY(), pos.getZ());
	}

	private static BlockPos toBlockPos(Pos pos) {
		return new BlockPos(pos.x(), pos.y(), pos.z());
	}
}
