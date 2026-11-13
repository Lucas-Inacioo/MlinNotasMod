package com.mlin.notas.end;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado do {@link CitadelPlanner}: blocos por posicao relativa (origem = centro da ilha, y=0 = topo da ilha),
 * criaturas/entidades a colocar e baus. Logica pura.
 */
public final class CitadelPlan {
	/** Entidade a criar. {@code dirX/dirZ}: para onde ela olha (moldura) ou de qual parede ela sai (shulker). */
	public record Spawn(Kind kind, int x, int y, int z, int dirX, int dirZ) {
	}

	public enum Kind {
		SHULKER,
		SENTINEL,
		ELYTRA_FRAME
	}

	/** Uma torre: centro, meia largura e numero de andares. */
	public record Tower(int x, int z, int half, int floors) {
		public int roofY() {
			return floors * CitadelPlanner.FLOOR_HEIGHT;
		}
	}

	private final Map<Long, CitadelPart> blocks = new HashMap<>();
	private final List<Spawn> spawns = new ArrayList<>();
	private final List<Tower> towers = new ArrayList<>();
	private final int radius;
	private int minY;
	private int maxY;

	CitadelPlan(int radius) {
		this.radius = radius;
	}

	public static long key(int x, int y, int z) {
		return ((long) (x + 512) << 32) | ((long) (y + 512) << 16) | (z + 512);
	}

	public static int keyX(long key) {
		return (int) (key >>> 32) - 512;
	}

	public static int keyY(long key) {
		return (int) ((key >>> 16) & 0xFFFF) - 512;
	}

	public static int keyZ(long key) {
		return (int) (key & 0xFFFF) - 512;
	}

	void set(int x, int y, int z, CitadelPart part) {
		blocks.put(key(x, y, z), part);
		minY = Math.min(minY, y);
		maxY = Math.max(maxY, y);
	}

	void spawn(Spawn spawn) {
		spawns.add(spawn);
	}

	void tower(Tower tower) {
		towers.add(tower);
	}

	public CitadelPart get(int x, int y, int z) {
		return blocks.get(key(x, y, z));
	}

	public Map<Long, CitadelPart> blocks() {
		return Collections.unmodifiableMap(blocks);
	}

	public List<Spawn> spawns() {
		return Collections.unmodifiableList(spawns);
	}

	/** A primeira e a fortaleza central; as outras sao as torres-satelite. */
	public List<Tower> towers() {
		return Collections.unmodifiableList(towers);
	}

	/** Raio da ilha. */
	public int radius() {
		return radius;
	}

	public int minY() {
		return minY;
	}

	public int maxY() {
		return maxY;
	}

	public long count(Kind kind) {
		return spawns.stream().filter(s -> s.kind() == kind).count();
	}

	public long count(CitadelPart part) {
		return blocks.values().stream().filter(p -> p == part).count();
	}
}
