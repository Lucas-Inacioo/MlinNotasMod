package com.mlin.notas.dragon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado do {@link ArenaPlanner}: blocos em coordenadas absolutas do mundo (o portal de saida fica em 0,0), pilares
 * de obsidiana considerados e a posicao do tesouro. Logica pura.
 */
public final class ArenaPlan {
	private final Map<Long, ArenaPart> blocks = new LinkedHashMap<>();
	private final List<ArenaPlanner.Spike> spikes;
	private int hoardX;
	private int hoardY;
	private int hoardZ;

	ArenaPlan(List<ArenaPlanner.Spike> spikes) {
		this.spikes = List.copyOf(spikes);
	}

	public static long key(int x, int y, int z) {
		return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096);
	}

	public static int keyX(long key) {
		return (int) (key >>> 40) - 4096;
	}

	public static int keyY(long key) {
		return (int) ((key >>> 20) & 0xFFFFF) - 4096;
	}

	public static int keyZ(long key) {
		return (int) (key & 0xFFFFF) - 4096;
	}

	void set(int x, int y, int z, ArenaPart part) {
		blocks.put(key(x, y, z), part);
	}

	void remove(int x, int y, int z) {
		blocks.remove(key(x, y, z));
	}

	void hoard(int x, int y, int z) {
		hoardX = x;
		hoardY = y;
		hoardZ = z;
	}

	public ArenaPart get(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	public Map<Long, ArenaPart> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	public List<ArenaPlanner.Spike> spikes() {
		return spikes;
	}

	/** Onde o bau do tesouro aparece quando o dragao morre (o ar em cima do estrado norte). */
	public int hoardX() {
		return hoardX;
	}

	public int hoardY() {
		return hoardY;
	}

	public int hoardZ() {
		return hoardZ;
	}

	public long count(ArenaPart part) {
		return blocks.values().stream().filter(p -> p == part).count();
	}
}
