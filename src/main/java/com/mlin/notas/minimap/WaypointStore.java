package com.mlin.notas.minimap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Lista de waypoints de um mundo/servidor, com serializacao JSON. Logica pura (sem Minecraft).
 * A persistencia em disco fica a cargo de quem usa (cliente).
 */
public final class WaypointStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final List<Waypoint> waypoints = new ArrayList<>();

	public List<Waypoint> all() {
		return Collections.unmodifiableList(waypoints);
	}

	/** Waypoints de uma dimensao, na ordem de insercao. */
	public List<Waypoint> inDimension(String dimension) {
		List<Waypoint> result = new ArrayList<>();
		for (Waypoint w : waypoints) {
			if (w.dimension().equals(dimension)) {
				result.add(w);
			}
		}
		return result;
	}

	public int size() {
		return waypoints.size();
	}

	public Waypoint get(int index) {
		return waypoints.get(index);
	}

	public void add(Waypoint waypoint) {
		waypoints.add(waypoint);
	}

	public boolean remove(Waypoint waypoint) {
		return waypoints.remove(waypoint);
	}

	/** Substitui {@code old} por {@code updated} mantendo a posicao na lista. */
	public boolean replace(Waypoint old, Waypoint updated) {
		int index = waypoints.indexOf(old);
		if (index < 0) {
			return false;
		}
		waypoints.set(index, updated);
		return true;
	}

	public Optional<Waypoint> findByName(String name) {
		for (Waypoint w : waypoints) {
			if (w.name().equalsIgnoreCase(name)) {
				return Optional.of(w);
			}
		}
		return Optional.empty();
	}

	public void clear() {
		waypoints.clear();
	}

	public String toJson() {
		JsonArray array = new JsonArray();
		for (Waypoint w : waypoints) {
			JsonObject obj = new JsonObject();
			obj.addProperty("name", w.name());
			obj.addProperty("x", w.x());
			obj.addProperty("y", w.y());
			obj.addProperty("z", w.z());
			obj.addProperty("color", String.format("#%06X", w.color() & 0xFFFFFF));
			obj.addProperty("dimension", w.dimension());
			array.add(obj);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("waypoints", array);
		return GSON.toJson(root);
	}

	/** Le o JSON gerado por {@link #toJson()}. Entradas invalidas sao ignoradas em vez de derrubar tudo. */
	public static WaypointStore fromJson(String json) {
		WaypointStore store = new WaypointStore();
		if (json == null || json.isBlank()) {
			return store;
		}
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonObject() || !root.getAsJsonObject().has("waypoints")) {
			return store;
		}
		for (JsonElement element : root.getAsJsonObject().getAsJsonArray("waypoints")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			try {
				String name = obj.get("name").getAsString();
				int x = obj.get("x").getAsInt();
				int y = obj.get("y").getAsInt();
				int z = obj.get("z").getAsInt();
				int color = parseColor(obj.has("color") ? obj.get("color").getAsString() : null);
				String dimension = obj.has("dimension") ? obj.get("dimension").getAsString() : "minecraft:overworld";
				store.add(new Waypoint(name, x, y, z, color, dimension));
			} catch (RuntimeException ignored) {
				// entrada corrompida: pula
			}
		}
		return store;
	}

	static int parseColor(String text) {
		if (text == null) {
			return Waypoint.PALETTE[0];
		}
		String hex = text.startsWith("#") ? text.substring(1) : text;
		try {
			return 0xFF000000 | (Integer.parseInt(hex, 16) & 0xFFFFFF);
		} catch (NumberFormatException e) {
			return Waypoint.PALETTE[0];
		}
	}
}
