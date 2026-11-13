package com.mlin.notas.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere que todo som registrado em {@link ModSounds} tem entrada no {@code sounds.json}, arquivos .ogg (Ogg Vorbis
 * mono) e legenda em pt_br e en_us, e que o {@code sounds.json} nao tem evento que nao foi registrado. Le o fonte do
 * ModSounds em vez de carregar a classe (registrar exige o bootstrap do jogo). Se falhar: rode {@code ./gradlew soundGen}
 * ou alinhe os ids do ModSounds com os de {@code scripts/SoundGen.java}.
 */
class ModSoundsResourcesTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/mlinnotas");
	private static final Path SOURCE = Path.of("src/main/java/com/mlin/notas/registry/ModSounds.java");
	private static final Pattern SOUND = Pattern.compile("\\bsound\\(\"([a-z0-9_.]+)\"\\)");
	private static final Pattern BLOCK_SET = Pattern.compile("\\bblockSounds\\(\"([a-z0-9_]+)\"\\)");

	static Set<String> registeredIds() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
		Set<String> ids = new LinkedHashSet<>();
		Matcher m = SOUND.matcher(source);
		while (m.find()) {
			ids.add(m.group(1));
		}
		Matcher b = BLOCK_SET.matcher(source);
		while (b.find()) {
			for (String kind : new String[] {"break", "step", "place", "hit", "fall"}) {
				ids.add("block." + b.group(1) + "." + kind);
			}
		}
		return ids;
	}

	private static JsonObject json(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void everyRegisteredSoundHasFilesAndSubtitles() throws IOException {
		Set<String> ids = registeredIds();
		assertTrue(ids.size() >= 80, "poucos sons achados no ModSounds: " + ids.size());
		JsonObject sounds = json(ASSETS.resolve("sounds.json"));
		JsonObject pt = json(ASSETS.resolve("lang/pt_br.json"));
		JsonObject en = json(ASSETS.resolve("lang/en_us.json"));
		List<String> missing = new ArrayList<>();
		for (String id : ids) {
			if (!sounds.has(id)) {
				missing.add("sounds.json: " + id);
				continue;
			}
			JsonObject entry = sounds.getAsJsonObject(id);
			String subtitle = entry.has("subtitle") ? entry.get("subtitle").getAsString() : null;
			if (subtitle == null || !subtitle.equals("subtitles.mlinnotas." + id)) {
				missing.add("legenda no sounds.json: " + id);
			} else {
				if (!pt.has(subtitle)) {
					missing.add("pt_br: " + subtitle);
				}
				if (!en.has(subtitle)) {
					missing.add("en_us: " + subtitle);
				}
			}
			if (!entry.has("sounds") || entry.getAsJsonArray("sounds").isEmpty()) {
				missing.add("sem arquivos: " + id);
				continue;
			}
			for (JsonElement sound : entry.getAsJsonArray("sounds")) {
				String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
				if (!name.startsWith("mlinnotas:")) {
					missing.add("arquivo fora do mod em " + id + ": " + name);
					continue;
				}
				Path file = ASSETS.resolve("sounds/" + name.substring("mlinnotas:".length()) + ".ogg");
				String problem = checkOgg(file);
				if (problem != null) {
					missing.add(problem);
				}
			}
		}
		for (String key : sounds.keySet()) {
			if (!ids.contains(key)) {
				missing.add("sounds.json tem evento nao registrado no ModSounds: " + key);
			}
		}
		assertTrue(missing.isEmpty(), "Sons incompletos (rode ./gradlew soundGen):\n" + String.join("\n", missing));
	}

	/** Cabecalho de identificacao Vorbis na primeira pagina Ogg, com 1 canal (som posicional precisa ser mono). */
	static String checkOgg(Path file) throws IOException {
		if (!Files.exists(file)) {
			return "falta " + file;
		}
		byte[] data = Files.readAllBytes(file);
		if (data.length < 64 || data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S') {
			return "nao e Ogg: " + file;
		}
		int packet = 27 + (data[26] & 0xFF);
		String magic = new String(data, packet + 1, 6, StandardCharsets.US_ASCII);
		if (data[packet] != 1 || !magic.equals("vorbis")) {
			return "nao e Vorbis: " + file;
		}
		int channels = data[packet + 11] & 0xFF;
		return channels == 1 ? null : "nao e mono (" + channels + " canais): " + file;
	}
}
