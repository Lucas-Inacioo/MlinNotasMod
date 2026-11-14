package com.mlin.notas.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Formato salvo de {@link BuildingsState} (campo opcional {@code buildings} da vila em {@code villages.dat}): niveis
 * (pares lote/nivel), melhorias, fila e contagens. Vila antiga nao tem o campo: tudo no nivel I.
 */
public final class BuildingsCodec {
	private static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);

	private BuildingsCodec() {
	}

	private static com.mojang.serialization.MapCodec<IntStream> ints(String name) {
		return Codec.INT_STREAM.optionalFieldOf(name).xmap(o -> o.orElseGet(IntStream::empty), Optional::of);
	}

	public static final Codec<BuildingsState.Upgrade> UPGRADE_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("lot").forGetter(BuildingsState.Upgrade::lot),
			Codec.INT.fieldOf("to").forGetter(BuildingsState.Upgrade::toLevel),
			INT_MAP.optionalFieldOf("deposits", Map.of()).forGetter(BuildingsState.Upgrade::deposits),
			INT_MAP.optionalFieldOf("paid", Map.of()).forGetter(BuildingsState.Upgrade::paid),
			Codec.BOOL.optionalFieldOf("queued", false).forGetter(BuildingsState.Upgrade::queued)
	).apply(i, (lot, to, deposits, paid, queued) -> {
		BuildingsState.Upgrade up = new BuildingsState.Upgrade(lot, to);
		up.restore(deposits, paid, queued);
		return up;
	}));

	public static final Codec<BuildingsState> CODEC = RecordCodecBuilder.create(i -> i.group(
			ints("levels").forGetter(b -> b.levels().entrySet().stream().flatMapToInt(e -> IntStream.of(e.getKey(), e.getValue()))),
			UPGRADE_CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(b -> List.copyOf(b.upgrades().values())),
			ints("queue").forGetter(b -> b.queue().stream().mapToInt(Integer::intValue)),
			Codec.INT.optionalFieldOf("population", 0).forGetter(BuildingsState::population),
			Codec.INT.optionalFieldOf("golems", 0).forGetter(BuildingsState::golems),
			Codec.LONG.optionalFieldOf("needs_day", Long.MIN_VALUE).forGetter(BuildingsState::needsDay)
	).apply(i, (levels, upgrades, queue, population, golems, needsDay) -> {
		int[] flat = levels.toArray();
		Map<Integer, Integer> map = new LinkedHashMap<>();
		for (int k = 0; k + 1 < flat.length; k += 2) {
			map.put(flat[k], flat[k + 1]);
		}
		List<Integer> q = new ArrayList<>();
		for (int lot : queue.toArray()) {
			q.add(lot);
		}
		BuildingsState b = new BuildingsState();
		b.restore(map, upgrades, q, population, golems, needsDay);
		return b;
	}));

	/** Copia o estado lido para o da vila. */
	static void copy(BuildingsState from, BuildingsState to) {
		to.restore(from.levels(), List.copyOf(from.upgrades().values()), from.queue(), from.population(), from.golems(), from.needsDay());
	}
}
