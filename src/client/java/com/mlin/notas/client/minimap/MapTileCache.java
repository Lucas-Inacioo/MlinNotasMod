package com.mlin.notas.client.minimap;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cache de tiles (16x16 cores) por chunk, separado por dimensao e por modo (superficie/caverna).
 * Mantem uma fila de chunks sujos e re-renderiza alguns por tick, os mais proximos do jogador primeiro.
 */
public final class MapTileCache {
	/** Chunks re-renderizados por tick, no maximo. */
	public static final int CHUNKS_PER_TICK = 4;
	/** A cada tantos ticks os chunks ao redor do jogador sao marcados como sujos (pega mudancas de bloco). */
	public static final int REFRESH_INTERVAL = 20;
	public static final int REFRESH_RADIUS = 2;
	/** No modo caverna o mapa depende do Y do jogador; atualiza mais perto e mais vezes. */
	public static final int CAVE_REFRESH_INTERVAL = 10;
	public static final int CAVE_REFRESH_RADIUS = 3;

	private record Layer(ResourceKey<Level> dimension, boolean cave) {
	}

	private final Map<Layer, Map<Long, int[]>> layers = new HashMap<>();
	private final Set<Long> dirty = new LinkedHashSet<>();
	private Layer current = new Layer(Level.OVERWORLD, false);
	private int tickCounter;
	/** Incrementa a cada tile renderizado; quem compoe texturas usa para saber se precisa recompor. */
	private long version;

	public long version() {
		return version;
	}

	public boolean isCaveMode() {
		return current.cave();
	}

	public ResourceKey<Level> dimension() {
		return current.dimension();
	}

	/** Tile do chunk na camada atual, ou null se ainda nao renderizado. */
	public int[] get(int chunkX, int chunkZ) {
		Map<Long, int[]> tiles = layers.get(current);
		return tiles == null ? null : tiles.get(ChunkPos.pack(chunkX, chunkZ));
	}

	public void markDirty(int chunkX, int chunkZ) {
		dirty.add(ChunkPos.pack(chunkX, chunkZ));
	}

	/** Chamado quando um chunk chega do servidor: ele e o vizinho ao sul (sombreamento) precisam de render. */
	public void onChunkLoaded(LevelChunk chunk) {
		markDirty(chunk.getPos().x(), chunk.getPos().z());
		markDirty(chunk.getPos().x(), chunk.getPos().z() + 1);
	}

	public void onChunkUnloaded(LevelChunk chunk) {
		dirty.remove(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()));
	}

	public void clear() {
		layers.clear();
		dirty.clear();
		version++;
	}

	/** Esquece a camada de caverna (ela depende do Y do jogador e envelhece rapido). */
	public void clearCaveLayers() {
		layers.keySet().removeIf(Layer::cave);
		version++;
	}

	/**
	 * Um passo de atualizacao. Troca de camada (dimensao/modo) quando necessario e renderiza ate
	 * {@link #CHUNKS_PER_TICK} chunks sujos, mais proximos do jogador primeiro.
	 */
	public void tick(ClientLevel level, LocalPlayer player, boolean cave) {
		Layer layer = new Layer(level.dimension(), cave);
		if (!layer.equals(current)) {
			current = layer;
			dirty.clear();
			version++;
			if (!layers.containsKey(layer)) {
				layers.put(layer, new HashMap<>());
			}
			markAround(player, cave ? CAVE_REFRESH_RADIUS : REFRESH_RADIUS + 4);
		}
		Map<Long, int[]> tiles = layers.computeIfAbsent(current, k -> new HashMap<>());

		tickCounter++;
		int interval = cave ? CAVE_REFRESH_INTERVAL : REFRESH_INTERVAL;
		if (tickCounter % interval == 0) {
			markAround(player, cave ? CAVE_REFRESH_RADIUS : REFRESH_RADIUS);
		}
		if (dirty.isEmpty()) {
			return;
		}

		int pcx = player.blockPosition().getX() >> 4;
		int pcz = player.blockPosition().getZ() >> 4;
		List<Long> ordered = new ArrayList<>(dirty);
		ordered.sort((a, b) -> Integer.compare(distance(a, pcx, pcz), distance(b, pcx, pcz)));

		int eyeY = (int) Math.floor(player.getEyeY());
		int done = 0;
		for (Long key : ordered) {
			if (done >= CHUNKS_PER_TICK) {
				break;
			}
			int cx = ChunkPos.getX(key);
			int cz = ChunkPos.getZ(key);
			LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
			dirty.remove(key);
			if (chunk == null || chunk.isEmpty()) {
				continue;
			}
			tiles.put(key, TileRenderer.render(level, chunk, cave, eyeY));
			version++;
			done++;
		}
	}

	private void markAround(LocalPlayer player, int radius) {
		int pcx = player.blockPosition().getX() >> 4;
		int pcz = player.blockPosition().getZ() >> 4;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				markDirty(pcx + dx, pcz + dz);
			}
		}
	}

	private static int distance(long key, int pcx, int pcz) {
		int dx = ChunkPos.getX(key) - pcx;
		int dz = ChunkPos.getZ(key) - pcz;
		return dx * dx + dz * dz;
	}
}
