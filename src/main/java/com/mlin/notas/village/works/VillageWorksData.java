package com.mlin.notas.village.works;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Obras publicas de cada vila, salvas a parte das vilas ({@code data/mlinnotas/village_works.dat}): fila de obras com o
 * progresso, categorias desligadas no quadro, o ultimo chefe derrotado (estatua da praca), as grades de ferro dos portoes
 * e o registro dos blocos que as obras colocaram ({@link Ownership}). Arquivo novo: vila sem entrada comeca vazia.
 */
public class VillageWorksData extends SavedData {
	/** Uma obra da fila: estado 0 = na fila, 1 = em andamento, 2 = pronta, 3 = substituida (palicada trocada pela muralha de pedra). */
	public static final class Entry {
		public final String id;
		public int state;
		public int index;
		public int total;
		/** Aneis aplainados quando a obra terminou (bits): anel novo pronto reabre a obra. */
		public int ringMask;

		public Entry(String id, int state, int index, int total, int ringMask) {
			this.id = id;
			this.state = state;
			this.index = index;
			this.total = total;
			this.ringMask = ringMask;
		}
	}

	/** Grade de ferro de um portao: barras (x, y, z), alavanca, estado e ate quando fica aberta por ordem do jogador. */
	public static final class GateState {
		public final int[] bars;
		public final int[] lever;
		public final String leverState;
		public boolean closed;

		public GateState(int[] bars, int[] lever, String leverState, boolean closed) {
			this.bars = bars;
			this.lever = lever;
			this.leverState = leverState;
			this.closed = closed;
		}
	}

	/** Tudo de uma vila. */
	public static final class State {
		public int lastLevel = -1;
		public int disabled;
		public final List<Entry> entries = new ArrayList<>();
		public Ownership owned = new Ownership();
		public final List<GateState> gates = new ArrayList<>();

		public Entry entry(String id) {
			for (Entry e : entries) {
				if (e.id.equals(id)) {
					return e;
				}
			}
			return null;
		}

		public boolean enabled(WorkCategory category) {
			return (disabled & category.bit()) == 0;
		}
	}

	private static com.mojang.serialization.MapCodec<IntStream> ints(String name) {
		return Codec.INT_STREAM.optionalFieldOf(name).xmap(o -> o.orElseGet(IntStream::empty), Optional::of);
	}

	private static com.mojang.serialization.MapCodec<LongStream> longs(String name) {
		return Codec.LONG_STREAM.optionalFieldOf(name).xmap(o -> o.orElseGet(LongStream::empty), Optional::of);
	}

	private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(e -> e.id),
			Codec.INT.optionalFieldOf("state", 0).forGetter(e -> e.state),
			Codec.INT.optionalFieldOf("index", 0).forGetter(e -> e.index),
			Codec.INT.optionalFieldOf("total", 0).forGetter(e -> e.total),
			Codec.INT.optionalFieldOf("ring_mask", 0).forGetter(e -> e.ringMask)
	).apply(i, Entry::new));

	private static final Codec<GateState> GATE_CODEC = RecordCodecBuilder.create(i -> i.group(
			ints("bars").forGetter(g -> IntStream.of(g.bars)),
			ints("lever").forGetter(g -> IntStream.of(g.lever)),
			Codec.STRING.optionalFieldOf("lever_state", "").forGetter(g -> g.leverState),
			Codec.BOOL.optionalFieldOf("closed", false).forGetter(g -> g.closed)
	).apply(i, (bars, lever, leverState, closed) -> new GateState(bars.toArray(), lever.toArray(), leverState, closed)));


	private static final Codec<State> STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("last_level", -1).forGetter(s -> s.lastLevel),
			Codec.INT.optionalFieldOf("disabled", 0).forGetter(s -> s.disabled),
			ENTRY_CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(s -> s.entries),
			longs("owned_keys").forGetter(s -> LongStream.of(s.owned.keys())),
			ints("owned_blocks").forGetter(s -> IntStream.of(s.owned.blockIndexes(s.owned.keys()))),
			ints("owned_works").forGetter(s -> IntStream.of(s.owned.workIndexes(s.owned.keys()))),
			Codec.STRING.listOf().optionalFieldOf("owned_palette", List.of()).forGetter(s -> s.owned.palette()),
			Codec.STRING.listOf().optionalFieldOf("owned_work_ids", List.of()).forGetter(s -> s.owned.workIds()),
			GATE_CODEC.listOf().optionalFieldOf("gates", List.of()).forGetter(s -> s.gates)
	).apply(i, (lastLevel, disabled, entries, keys, blocks, works, palette, workIds, gates) -> {
		State s = new State();
		s.lastLevel = lastLevel;
		s.disabled = disabled;
		s.entries.addAll(entries);
		s.owned = Ownership.restore(keys.toArray(), blocks.toArray(), works.toArray(), palette, workIds);
		s.gates.addAll(gates);
		return s;
	}));

	public static final Codec<VillageWorksData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, STATE_CODEC).optionalFieldOf("villages", Map.of()).forGetter(d -> d.states),
			Codec.STRING.optionalFieldOf("last_boss", "").forGetter(d -> d.lastBoss)
	).apply(i, (states, lastBoss) -> {
		VillageWorksData d = new VillageWorksData(states);
		d.lastBoss = lastBoss;
		return d;
	}));

	public static final SavedDataType<VillageWorksData> TYPE = new SavedDataType<>(MlinNotas.id("village_works"), VillageWorksData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, State> states = new LinkedHashMap<>();
	/** Ultimo chefe derrotado no mundo (id do {@code BossKind}, ender_dragon ou wither): a estatua da praca. */
	public String lastBoss = "";

	public VillageWorksData() {
	}

	private VillageWorksData(Map<String, State> states) {
		this.states.putAll(states);
	}

	public static VillageWorksData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public State state(String village) {
		return states.computeIfAbsent(village, k -> new State());
	}

	public State peek(String village) {
		return states.get(village);
	}

	public void forget(String village) {
		states.remove(village);
		setDirty();
	}

	public void changed() {
		setDirty();
	}
}
