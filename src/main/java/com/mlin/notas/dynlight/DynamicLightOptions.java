package com.mlin.notas.dynlight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Opcoes da luz dinamica ({@code config/mlinnotas/dynamic_lights.json}, so cliente). Logica pura.
 */
public final class DynamicLightOptions {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Liga/desliga tudo. */
	public boolean enabled = true;
	/** Itens de luz na mao (sua e de outros jogadores). */
	public boolean heldItems = true;
	/** Outros jogadores tambem iluminam. */
	public boolean otherPlayers = true;
	/** Itens de luz jogados no chao. */
	public boolean droppedItems = true;
	/** Entidades pegando fogo. */
	public boolean burningEntities = true;
	/** Mobs que brilham (blaze, cubo de magma, lula brilhante, lesma de magma). */
	public boolean glowingMobs = true;
	/** Maximo de fontes ao mesmo tempo (as mais perto da camera ganham), 1..64. */
	public int maxSources = 24;

	public String toJson() {
		return GSON.toJson(this);
	}

	/** Le o JSON; campos ausentes ou invalidos ficam no padrao. */
	public static DynamicLightOptions fromJson(String json) {
		DynamicLightOptions options = new DynamicLightOptions();
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			options.enabled = bool(o, "enabled", options.enabled);
			options.heldItems = bool(o, "heldItems", options.heldItems);
			options.otherPlayers = bool(o, "otherPlayers", options.otherPlayers);
			options.droppedItems = bool(o, "droppedItems", options.droppedItems);
			options.burningEntities = bool(o, "burningEntities", options.burningEntities);
			options.glowingMobs = bool(o, "glowingMobs", options.glowingMobs);
			if (o.has("maxSources") && o.get("maxSources").isJsonPrimitive() && o.get("maxSources").getAsJsonPrimitive().isNumber()) {
				options.maxSources = Math.max(1, Math.min(64, o.get("maxSources").getAsInt()));
			}
		} catch (RuntimeException e) {
			return new DynamicLightOptions();
		}
		return options;
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean() ? o.get(key).getAsBoolean() : fallback;
	}
}
