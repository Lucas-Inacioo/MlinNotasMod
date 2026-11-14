package com.mlin.notas.storage;

import com.mlin.notas.util.Pos;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Chunks que o Tablet de Rede segura carregadas enquanto o terminal esta aberto de longe (logica pura). A chunk do
 * terminal vem primeiro; depois as chunks de cada unidade, bau e terminal ja achados na rede, ate {@link #MAX_CHUNKS}.
 * Cada ticket carrega um raio de {@link #RADIUS} chunk em volta, entao os cabos que atravessam a borda tambem entram e
 * a rede "cresce" a cada sincronizacao ate aparecer inteira.
 */
public final class TabletChunks {
	/** Raio (em chunks) carregado em volta de cada chunk com ticket. */
	public static final int RADIUS = 1;
	/** Limite de chunks com ticket por terminal aberto. */
	public static final int MAX_CHUNKS = 32;

	private TabletChunks() {
	}

	/** Chunk (x, z) empacotada num long (mesmo formato do {@code ChunkPos.pack}: x nos 32 bits baixos). */
	public static long pack(int chunkX, int chunkZ) {
		return chunkX & 0xFFFFFFFFL | (chunkZ & 0xFFFFFFFFL) << 32;
	}

	public static int unpackX(long packed) {
		return (int) (packed & 0xFFFFFFFFL);
	}

	public static int unpackZ(long packed) {
		return (int) (packed >>> 32 & 0xFFFFFFFFL);
	}

	/** Chunk de um bloco. */
	public static long chunkOf(Pos pos) {
		return pack(pos.x() >> 4, pos.z() >> 4);
	}

	/** Chunks a segurar: a do terminal e as dos nos da rede, sem repetir, no maximo {@link #MAX_CHUNKS}. */
	public static Set<Long> plan(Pos terminal, Collection<Pos> nodes) {
		Set<Long> out = new LinkedHashSet<>();
		out.add(chunkOf(terminal));
		for (Pos pos : nodes) {
			if (out.size() >= MAX_CHUNKS) {
				break;
			}
			out.add(chunkOf(pos));
		}
		return out;
	}
}
