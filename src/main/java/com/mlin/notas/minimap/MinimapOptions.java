package com.mlin.notas.minimap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Opcoes do minimapa, com defaults e serializacao JSON. Logica pura (sem Minecraft).
 */
public final class MinimapOptions {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final int[] SIZES = {96, 128, 160, 192};
	public static final float[] ZOOMS = {0.5F, 1.0F, 2.0F, 4.0F};

	public enum CaveMode {
		AUTO, ON, OFF;

		public CaveMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public boolean enabled = true;
	public CaveMode caveMode = CaveMode.AUTO;
	public boolean showPlayers = true;
	public boolean showHostiles = true;
	public boolean showPassives = true;
	public boolean showItems = false;
	public boolean showVillagers = true;
	public boolean showWaypoints = true;
	public boolean showCoordinates = true;
	/** Tamanho do quadrado na tela, em pixels de GUI. */
	public int size = 128;
	/** Pixels de tela por bloco. */
	public float zoom = 1.0F;

	public MinimapOptions copy() {
		return fromJson(toJson());
	}

	/** Quantos blocos cabem no minimapa com o zoom atual. */
	public int visibleBlocks() {
		return Math.max(1, Math.round(size / zoom));
	}

	public void nextSize() {
		size = SIZES[(indexOf(SIZES, size) + 1) % SIZES.length];
	}

	public void nextZoom() {
		zoom = ZOOMS[(indexOf(ZOOMS, zoom) + 1) % ZOOMS.length];
	}

	public void zoomIn() {
		int i = indexOf(ZOOMS, zoom);
		zoom = ZOOMS[Math.min(ZOOMS.length - 1, i + 1)];
	}

	public void zoomOut() {
		int i = indexOf(ZOOMS, zoom);
		zoom = ZOOMS[Math.max(0, i - 1)];
	}

	private static int indexOf(int[] values, int value) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == value) {
				return i;
			}
		}
		return 1;
	}

	private static int indexOf(float[] values, float value) {
		for (int i = 0; i < values.length; i++) {
			if (Math.abs(values[i] - value) < 0.001F) {
				return i;
			}
		}
		return 1;
	}

	public String toJson() {
		JsonObject obj = new JsonObject();
		obj.addProperty("enabled", enabled);
		obj.addProperty("caveMode", caveMode.name());
		obj.addProperty("showPlayers", showPlayers);
		obj.addProperty("showHostiles", showHostiles);
		obj.addProperty("showPassives", showPassives);
		obj.addProperty("showItems", showItems);
		obj.addProperty("showVillagers", showVillagers);
		obj.addProperty("showWaypoints", showWaypoints);
		obj.addProperty("showCoordinates", showCoordinates);
		obj.addProperty("size", size);
		obj.addProperty("zoom", zoom);
		return GSON.toJson(obj);
	}

	/** Campos ausentes ou invalidos mantem o default. */
	public static MinimapOptions fromJson(String json) {
		MinimapOptions options = new MinimapOptions();
		if (json == null || json.isBlank()) {
			return options;
		}
		JsonObject obj;
		try {
			obj = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			return options;
		}
		options.enabled = bool(obj, "enabled", options.enabled);
		options.showPlayers = bool(obj, "showPlayers", options.showPlayers);
		options.showHostiles = bool(obj, "showHostiles", options.showHostiles);
		options.showPassives = bool(obj, "showPassives", options.showPassives);
		options.showItems = bool(obj, "showItems", options.showItems);
		options.showVillagers = bool(obj, "showVillagers", options.showVillagers);
		options.showWaypoints = bool(obj, "showWaypoints", options.showWaypoints);
		options.showCoordinates = bool(obj, "showCoordinates", options.showCoordinates);
		if (obj.has("caveMode")) {
			try {
				options.caveMode = CaveMode.valueOf(obj.get("caveMode").getAsString());
			} catch (RuntimeException ignored) {
			}
		}
		if (obj.has("size")) {
			int size = obj.get("size").getAsInt();
			if (indexOf(SIZES, size) >= 0 && size == SIZES[indexOf(SIZES, size)]) {
				options.size = size;
			}
		}
		if (obj.has("zoom")) {
			float zoom = obj.get("zoom").getAsFloat();
			if (Math.abs(ZOOMS[indexOf(ZOOMS, zoom)] - zoom) < 0.001F) {
				options.zoom = zoom;
			}
		}
		return options;
	}

	private static boolean bool(JsonObject obj, String key, boolean def) {
		return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : def;
	}
}
