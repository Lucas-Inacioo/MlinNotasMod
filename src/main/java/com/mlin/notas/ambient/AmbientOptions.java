package com.mlin.notas.ambient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Opcoes do ambiente e do HUD de estacoes ({@code config/mlinnotas/ambient.json}). Logica pura.
 */
public final class AmbientOptions {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Particulas de ambiente (folhas, vaga-lumes, poeira, flocos). */
	public boolean particles = true;
	/** Sons de ambiente (vento, rio, passaros, grilos, cavernas, sapos). */
	public boolean sounds = true;
	/** Multiplicador da quantidade de particulas (0..2). */
	public float particleDensity = 1.0F;
	/** Multiplicador do volume dos sons de ambiente (0..2; tambem respeita o volume "Ambiente" do jogo). */
	public float soundVolume = 1.0F;
	/** Linha da estacao embaixo do minimapa. */
	public boolean seasonHud = true;
	/** Mensagem no chat quando a estacao muda. */
	public boolean seasonMessages = true;

	public String toJson() {
		return GSON.toJson(this);
	}

	/** Le o JSON; campos ausentes ou invalidos ficam no padrao. */
	public static AmbientOptions fromJson(String json) {
		AmbientOptions options = new AmbientOptions();
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			options.particles = bool(o, "particles", options.particles);
			options.sounds = bool(o, "sounds", options.sounds);
			options.seasonHud = bool(o, "seasonHud", options.seasonHud);
			options.seasonMessages = bool(o, "seasonMessages", options.seasonMessages);
			options.particleDensity = clamp(number(o, "particleDensity", options.particleDensity));
			options.soundVolume = clamp(number(o, "soundVolume", options.soundVolume));
		} catch (RuntimeException e) {
			return new AmbientOptions();
		}
		return options;
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean() ? o.get(key).getAsBoolean() : fallback;
	}

	private static float number(JsonObject o, String key, float fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber() ? o.get(key).getAsFloat() : fallback;
	}

	private static float clamp(float v) {
		return Float.isNaN(v) ? 1.0F : Math.max(0.0F, Math.min(2.0F, v));
	}
}
