package com.mlin.notas.client.minimap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Textura dinamica quadrada (1 pixel = 1 bloco) composta a partir do {@link MapTileCache},
 * centrada num bloco. Recomposta so quando o centro muda ou quando o cache tem tiles novos.
 */
public final class MapTexture {
	private final Identifier id;
	private final int size;
	private final DynamicTexture texture;
	private int centerX = Integer.MIN_VALUE;
	private int centerZ = Integer.MIN_VALUE;
	private long version = -1;
	private boolean registered;

	public MapTexture(Identifier id, int size) {
		this.id = id;
		this.size = size;
		this.texture = new DynamicTexture(id.toString(), size, size, true);
	}

	public Identifier id() {
		return id;
	}

	public int size() {
		return size;
	}

	public int centerX() {
		return centerX;
	}

	public int centerZ() {
		return centerZ;
	}

	/** Bloco X do pixel 0. */
	public int originX() {
		return centerX - size / 2;
	}

	/** Bloco Z do pixel 0. */
	public int originZ() {
		return centerZ - size / 2;
	}

	/** Garante que a textura esta registrada no TextureManager (precisa de contexto de render). */
	public void ensureRegistered() {
		if (!registered) {
			Minecraft.getInstance().getTextureManager().register(id, texture);
			registered = true;
		}
	}

	/** Recompoe se o centro ou o cache mudaram. Devolve true se recompos. */
	public boolean update(MapTileCache cache, int newCenterX, int newCenterZ, boolean force) {
		if (!force && newCenterX == centerX && newCenterZ == centerZ && cache.version() == version) {
			return false;
		}
		centerX = newCenterX;
		centerZ = newCenterZ;
		version = cache.version();
		compose(cache);
		return true;
	}

	private void compose(MapTileCache cache) {
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		int originX = originX();
		int originZ = originZ();
		int firstChunkX = originX >> 4;
		int lastChunkX = (originX + size - 1) >> 4;
		int firstChunkZ = originZ >> 4;
		int lastChunkZ = (originZ + size - 1) >> 4;
		for (int cz = firstChunkZ; cz <= lastChunkZ; cz++) {
			for (int cx = firstChunkX; cx <= lastChunkX; cx++) {
				int[] tile = cache.get(cx, cz);
				int chunkOriginX = cx << 4;
				int chunkOriginZ = cz << 4;
				for (int lz = 0; lz < 16; lz++) {
					int py = chunkOriginZ + lz - originZ;
					if (py < 0 || py >= size) {
						continue;
					}
					for (int lx = 0; lx < 16; lx++) {
						int px = chunkOriginX + lx - originX;
						if (px < 0 || px >= size) {
							continue;
						}
						int color = tile == null ? 0 : tile[lz * 16 + lx];
						image.setPixel(px, py, color);
					}
				}
			}
		}
		texture.upload();
	}

	public void close() {
		texture.close();
	}
}
