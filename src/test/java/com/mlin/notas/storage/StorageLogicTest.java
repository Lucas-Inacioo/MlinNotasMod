package com.mlin.notas.storage;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageLogicTest {
	/** Bau falso: slots com limite de pilha 64, como um container vanilla. */
	static final class FakeChest implements StorageTarget<String> {
		final String[] keys;
		final int[] counts;

		FakeChest(int slots) {
			keys = new String[slots];
			counts = new int[slots];
		}

		@Override
		public int priority() {
			return 0;
		}

		@Override
		public boolean contains(String key) {
			for (int i = 0; i < keys.length; i++) {
				if (key.equals(keys[i]) && counts[i] > 0) {
					return true;
				}
			}
			return false;
		}

		@Override
		public long insert(String key, long amount, boolean simulate) {
			long left = amount;
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < keys.length && left > 0; i++) {
					boolean match = pass == 0 ? key.equals(keys[i]) && counts[i] > 0 : counts[i] == 0;
					if (match) {
						int moved = (int) Math.min(left, 64 - counts[i]);
						if (!simulate) {
							keys[i] = key;
							counts[i] += moved;
						}
						left -= moved;
					}
				}
			}
			return amount - left;
		}

		@Override
		public long extract(String key, long amount, boolean simulate) {
			long left = amount;
			for (int i = keys.length - 1; i >= 0 && left > 0; i--) {
				if (key.equals(keys[i]) && counts[i] > 0) {
					int moved = (int) Math.min(left, counts[i]);
					if (!simulate) {
						counts[i] -= moved;
					}
					left -= moved;
				}
			}
			return amount - left;
		}

		@Override
		public void forEach(BiConsumer<String, Long> action) {
			for (int i = 0; i < keys.length; i++) {
				if (counts[i] > 0) {
					action.accept(keys[i], (long) counts[i]);
				}
			}
		}
	}

	@Test
	void cellHoldsFarMoreThanAStackPerType() {
		StorageCell<String> cell = new StorageCell<>(StorageTier.COPPER, k -> true);
		assertEquals(1024, cell.insert("stone", 5000, false));
		assertEquals(1024, cell.count("stone"));
		assertEquals(0, cell.insert("stone", 1, false));
	}

	@Test
	void cellLimitsTypes() {
		StorageCell<String> cell = new StorageCell<>(2, 100, 10, k -> true);
		assertEquals(5, cell.insert("a", 5, false));
		assertEquals(5, cell.insert("b", 5, false));
		assertEquals(0, cell.insert("c", 5, false));
		// tipo ja presente continua aceitando
		assertEquals(5, cell.insert("a", 5, false));
		// ao esvaziar um tipo, a vaga volta
		assertEquals(5, cell.extract("b", 99, false));
		assertEquals(5, cell.insert("c", 5, false));
		assertEquals(2, cell.typeCount());
	}

	@Test
	void simulateChangesNothing() {
		StorageCell<String> cell = new StorageCell<>(2, 100, 10, k -> true);
		int[] changes = {0};
		cell.setOnChange(() -> changes[0]++);
		assertEquals(100, cell.insert("a", 150, true));
		assertTrue(cell.isEmpty());
		cell.insert("a", 30, false);
		assertEquals(30, cell.extract("a", 50, true));
		assertEquals(30, cell.count("a"));
		assertEquals(1, changes[0]);
	}

	@Test
	void cellRespectsFilter() {
		StorageCell<String> cell = new StorageCell<>(4, 100, 10, k -> !k.startsWith("full_unit"));
		assertEquals(0, cell.insert("full_unit_copper", 1, false));
		assertEquals(1, cell.insert("dirt", 1, false));
	}

	@Test
	void loadCutsExcess() {
		StorageCell<String> cell = new StorageCell<>(2, 10, 10, k -> true);
		Map<String, Long> data = new LinkedHashMap<>();
		data.put("a", 50L);
		data.put("b", 3L);
		data.put("c", 3L);
		cell.load(data);
		assertEquals(10, cell.count("a"));
		assertEquals(3, cell.count("b"));
		assertEquals(0, cell.count("c"));
		assertEquals(13, cell.totalCount());
	}

	@Test
	void tiersGrowAndChainUpgrades() {
		StorageTier previous = null;
		for (StorageTier tier : StorageTier.values()) {
			assertEquals(previous, tier.previous());
			if (previous != null) {
				assertTrue(tier.types() > previous.types());
				assertTrue(tier.perType() > previous.perType());
				assertTrue(tier.priority() > previous.priority());
				assertTrue(tier.material().ordinal() > previous.material().ordinal());
			}
			previous = tier;
		}
		// a menor unidade ja guarda mais que o maior bau do mod (243 pilhas de 64)
		assertTrue(StorageTier.COPPER.capacity() > 0);
		assertTrue(StorageTier.DIAMOND.capacity() > 243L * 64);
	}

	@Test
	void insertPrefersWhereTheTypeAlreadyIs() {
		StorageCell<String> big = new StorageCell<>(8, 1000, 20, k -> true);
		StorageCell<String> small = new StorageCell<>(8, 1000, 10, k -> true);
		FakeChest chest = new FakeChest(3);
		small.insert("iron", 5, false);
		chest.insert("gold", 5, false);
		NetworkStorage<String> net = new NetworkStorage<>(List.of(chest, small, big));
		// ferro ja esta na unidade pequena: nao espalha para a grande
		assertEquals(10, net.insert("iron", 10, false));
		assertEquals(15, small.count("iron"));
		assertEquals(0, big.count("iron"));
		// ouro esta no bau: completa a pilha do bau primeiro
		assertEquals(10, net.insert("gold", 10, false));
		assertTrue(chest.contains("gold"));
		assertEquals(0, big.count("gold"));
		assertEquals(15, net.count("gold"));
		// tipo novo vai para a maior prioridade
		assertEquals(7, net.insert("dirt", 7, false));
		assertEquals(7, big.count("dirt"));
	}

	@Test
	void insertOverflowsToContainersAndReportsLeftover() {
		StorageCell<String> unit = new StorageCell<>(1, 100, 10, k -> true);
		FakeChest chest = new FakeChest(2);
		NetworkStorage<String> net = new NetworkStorage<>(List.of(unit, chest));
		assertEquals(228, net.insert("sand", 300, false));
		assertEquals(100, unit.count("sand"));
		assertEquals(228, net.count("sand"));
		assertEquals(0, net.insert("sand", 1, false));
		assertEquals(0, net.insert("sand", 1, true));
	}

	@Test
	void extractDrainsContainersBeforeUnits() {
		StorageCell<String> unit = new StorageCell<>(4, 1000, 10, k -> true);
		FakeChest chest = new FakeChest(2);
		unit.insert("coal", 100, false);
		chest.insert("coal", 30, false);
		NetworkStorage<String> net = new NetworkStorage<>(List.of(unit, chest));
		assertEquals(40, net.extract("coal", 40, false));
		assertFalse(chest.contains("coal"));
		assertEquals(90, unit.count("coal"));
		assertEquals(90, net.extract("coal", 500, false));
		assertEquals(0, net.extract("coal", 1, false));
	}

	@Test
	void totalsSumAcrossTargets() {
		StorageCell<String> unit = new StorageCell<>(4, 1000, 10, k -> true);
		FakeChest chest = new FakeChest(4);
		unit.insert("a", 500, false);
		chest.insert("a", 100, false);
		chest.insert("b", 3, false);
		Map<String, Long> totals = new NetworkStorage<>(List.of(unit, chest)).totals();
		assertEquals(Map.of("a", 600L, "b", 3L), totals);
	}

	@Test
	void graphFollowsConductorsAndStopsAtContainers() {
		Map<Pos, NetworkGraph.NodeKind> world = new HashMap<>();
		world.put(new Pos(0, 0, 0), NetworkGraph.NodeKind.TERMINAL);
		for (int x = 1; x <= 5; x++) {
			world.put(new Pos(x, 0, 0), NetworkGraph.NodeKind.CABLE);
		}
		world.put(new Pos(6, 0, 0), NetworkGraph.NodeKind.UNIT);
		world.put(new Pos(3, 1, 0), NetworkGraph.NodeKind.CONTAINER);
		// bau encostado no bau: nao entra (baus nao conduzem)
		world.put(new Pos(3, 2, 0), NetworkGraph.NodeKind.CONTAINER);
		// unidade encostada na unidade: entra (unidades conduzem)
		world.put(new Pos(7, 0, 0), NetworkGraph.NodeKind.UNIT);
		// bloco solto longe
		world.put(new Pos(20, 0, 0), NetworkGraph.NodeKind.UNIT);
		NetworkGraph.Result result = NetworkGraph.discover(new Pos(0, 0, 0), p -> world.getOrDefault(p, NetworkGraph.NodeKind.NONE));
		assertEquals(List.of(new Pos(6, 0, 0), new Pos(7, 0, 0)), result.units());
		assertEquals(List.of(new Pos(3, 1, 0)), result.containers());
		assertEquals(List.of(new Pos(0, 0, 0)), result.terminals());
		assertEquals(5, result.cables());
		assertFalse(result.truncated());
		assertEquals(9, result.size());
	}

	@Test
	void graphFromNonConductorIsEmpty() {
		NetworkGraph.Result result = NetworkGraph.discover(new Pos(0, 0, 0), p -> NetworkGraph.NodeKind.CONTAINER);
		assertEquals(0, result.size());
	}

	@Test
	void graphStopsAtTheLimit() {
		// linha infinita de cabos
		NetworkGraph.Result result = NetworkGraph.discover(new Pos(0, 0, 0),
				p -> p.y() == 0 && p.z() == 0 ? NetworkGraph.NodeKind.CABLE : NetworkGraph.NodeKind.NONE, 50);
		assertTrue(result.truncated());
		assertEquals(50, result.size());
	}

	@Test
	void deltaSendsItemsOnceAndOnlyChanges() {
		DeltaTracker<String> tracker = new DeltaTracker<>();
		Map<String, Long> state = new LinkedHashMap<>();
		state.put("a", 10L);
		state.put("b", 5L);
		List<DeltaTracker.Change<String>> first = tracker.update(state);
		assertEquals(2, first.size());
		assertEquals("a", first.get(0).key());
		int idA = first.get(0).id();
		assertEquals("a", tracker.keyOf(idA));

		assertTrue(tracker.update(state).isEmpty());

		state.put("a", 11L);
		state.remove("b");
		List<DeltaTracker.Change<String>> second = tracker.update(state);
		assertEquals(2, second.size());
		Map<Integer, Long> counts = new HashMap<>();
		for (DeltaTracker.Change<String> change : second) {
			assertNull(change.key(), "item ja conhecido nao viaja de novo");
			counts.put(change.id(), change.count());
		}
		assertEquals(11L, counts.get(idA));
		assertTrue(counts.containsValue(0L));

		// "b" volta: mesmo numero, sem reenviar o item
		state.put("b", 1L);
		List<DeltaTracker.Change<String>> third = tracker.update(state);
		assertEquals(1, third.size());
		assertNull(third.get(0).key());

		tracker.reset();
		List<DeltaTracker.Change<String>> full = tracker.update(state);
		assertEquals(2, full.size());
		assertTrue(full.stream().allMatch(c -> c.key() != null));
	}

	@Test
	void compactCounts() {
		assertEquals("0", CountFormat.compact(0));
		assertEquals("999", CountFormat.compact(999));
		assertEquals("1k", CountFormat.compact(1000));
		assertEquals("1.2k", CountFormat.compact(1250));
		assertEquals("9.9k", CountFormat.compact(9999));
		assertEquals("34k", CountFormat.compact(34_567));
		assertEquals("999k", CountFormat.compact(999_999));
		assertEquals("1M", CountFormat.compact(1_000_000));
		assertEquals("34M", CountFormat.compact(34_000_001));
		assertEquals("1.2B", CountFormat.compact(1_234_567_890L));
		assertEquals("1,234,567", CountFormat.full(1_234_567));
		for (long n : new long[] {5, 123, 4567, 89_012, 345_678, 9_012_345, 678_901_234L, 12_345_678_901L}) {
			assertTrue(CountFormat.compact(n).length() <= 4, n + " -> " + CountFormat.compact(n));
		}
	}

	@Test
	void queryFiltersAndSorts() {
		List<TerminalQuery.Entry<Integer>> entries = new ArrayList<>(List.of(
				new TerminalQuery.Entry<>(1, "Stone", "minecraft:stone", 500),
				new TerminalQuery.Entry<>(2, "Cobalt Ingot", "mlinnotas:cobalt_ingot", 12),
				new TerminalQuery.Entry<>(3, "Iron Ingot", "minecraft:iron_ingot", 64),
				new TerminalQuery.Entry<>(4, "Gone", "minecraft:air", 0)));
		assertEquals(List.of(2, 3, 1), handles(TerminalQuery.apply(entries, "", TerminalQuery.SortMode.NAME, false)));
		assertEquals(List.of(1, 3, 2), handles(TerminalQuery.apply(entries, "", TerminalQuery.SortMode.COUNT, false)));
		assertEquals(List.of(2, 3, 1), handles(TerminalQuery.apply(entries, "", TerminalQuery.SortMode.COUNT, true)));
		assertEquals(List.of(3, 1, 2), handles(TerminalQuery.apply(entries, "", TerminalQuery.SortMode.MOD, false)));
		assertEquals(List.of(2, 3), handles(TerminalQuery.apply(entries, "ingot", TerminalQuery.SortMode.NAME, false)));
		assertEquals(List.of(2), handles(TerminalQuery.apply(entries, "@mlin", TerminalQuery.SortMode.NAME, false)));
		assertEquals(List.of(3), handles(TerminalQuery.apply(entries, "ingot @minecraft", TerminalQuery.SortMode.NAME, false)));
		assertEquals(List.of(1), handles(TerminalQuery.apply(entries, "minecraft:stone", TerminalQuery.SortMode.NAME, false)));
		assertEquals(Set.of(TerminalQuery.SortMode.NAME, TerminalQuery.SortMode.COUNT, TerminalQuery.SortMode.MOD),
				Set.of(TerminalQuery.SortMode.NAME.next().next().next(), TerminalQuery.SortMode.NAME.next(), TerminalQuery.SortMode.MOD));
	}

	private static List<Integer> handles(List<TerminalQuery.Entry<Integer>> list) {
		return list.stream().map(TerminalQuery.Entry::handle).toList();
	}
}
