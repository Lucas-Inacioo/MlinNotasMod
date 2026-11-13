package com.mlin.notas.client.minimap;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.minimap.Waypoint;
import com.mlin.notas.minimap.WaypointStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Waypoints do mundo/servidor atual, carregados de {@code config/mlinnotas/waypoints/<chave>.json}.
 */
public final class WaypointManager {
	private WaypointStore store = new WaypointStore();
	private String worldKey;

	public WaypointStore store() {
		return store;
	}

	public String worldKey() {
		return worldKey;
	}

	/** Waypoints da dimensao atual. */
	public List<Waypoint> inDimension(String dimension) {
		return store.inDimension(dimension);
	}

	/** Chamado ao entrar num mundo: descobre a chave e carrega o arquivo. */
	public void load(Minecraft minecraft) {
		worldKey = worldKey(minecraft);
		store = new WaypointStore();
		Path file = file();
		if (file != null && Files.exists(file)) {
			try {
				store = WaypointStore.fromJson(Files.readString(file, StandardCharsets.UTF_8));
			} catch (IOException | RuntimeException e) {
				MlinNotas.LOGGER.warn("Nao consegui ler waypoints de {}", file, e);
			}
		}
	}

	public void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, store.toJson(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MlinNotas.LOGGER.warn("Nao consegui salvar waypoints em {}", file, e);
		}
	}

	public void add(Waypoint waypoint) {
		store.add(waypoint);
		save();
	}

	public void remove(Waypoint waypoint) {
		store.remove(waypoint);
		save();
	}

	public void replace(Waypoint old, Waypoint updated) {
		store.replace(old, updated);
		save();
	}

	public void unload() {
		save();
		store = new WaypointStore();
		worldKey = null;
	}

	private Path file() {
		if (worldKey == null) {
			return null;
		}
		return FabricLoader.getInstance().getConfigDir().resolve("mlinnotas").resolve("waypoints").resolve(worldKey + ".json");
	}

	static String worldKey(Minecraft minecraft) {
		IntegratedServer integrated = minecraft.getSingleplayerServer();
		if (integrated != null) {
			return "sp_" + sanitize(integrated.getWorldData().getLevelName());
		}
		ServerData server = minecraft.getCurrentServer();
		if (server != null && server.ip != null) {
			return "mp_" + sanitize(server.ip);
		}
		return "unknown";
	}

	static String sanitize(String name) {
		String cleaned = name.replaceAll("[^A-Za-z0-9._-]", "_");
		return cleaned.isEmpty() ? "unknown" : cleaned;
	}
}
