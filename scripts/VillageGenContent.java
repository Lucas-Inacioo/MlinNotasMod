import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Vilas 3.0 fase 2: o terreno da vila inicial sai do gerador. Para cada predefinicao do Overworld (normal, biomas grandes,
 * amplificado) copia do jar vanilla a densidade final e a superficie preliminar para
 * {@code mlinnotas:village/<preset>/...} (as cruas) e sobrescreve as do Minecraft com os embrulhos do mod:
 * <ul>
 * <li>{@code minecraft:<preset>/final_density} = {@code mlinnotas:village_blend} (densidade crua + superficie crua);</li>
 * <li>{@code minecraft:<preset>/preliminary_surface_level} (aquifero) e {@code chunk_surface_level} (regras de
 * superficie) = {@code mlinnotas:village_surface}.</li>
 * </ul>
 * Fora do campo da vila os embrulhos devolvem o valor vanilla sem mudanca. Embrulhar as funcoes registradas (e nao o
 * {@code noise_settings/overworld.json}) evita copiar a regra de superficie inteira. Jar: argumento de sistema
 * {@code mlinnotas.clientJar} ou {@code ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar}; sem jar, mantem os
 * arquivos que ja existem. Chamado por ContentGen.main.
 */
class VillageGenContent {
	static final String[] PRESETS = {"overworld", "overworld_large_biomes", "overworld_amplified"};

	static void generate() throws IOException {
		Path jar = Path.of(System.getProperty("mlinnotas.clientJar",
				Path.of(System.getProperty("user.home"), ".gradle", "caches", "fabric-loom", "26.3", "minecraft-client.jar").toString()));
		if (!Files.exists(jar)) {
			System.out.println("VillageGenContent: jar do cliente nao achado (" + jar + "), mantendo os arquivos de densidade da vila");
			return;
		}
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			for (String preset : PRESETS) {
				String raw = "mlinnotas:village/" + preset + "/";
				ContentGen.write("data/mlinnotas/worldgen/density_function/village/" + preset + "/final_density.json",
						read(zip, "data/minecraft/worldgen/density_function/" + preset + "/final_density.json"));
				ContentGen.write("data/mlinnotas/worldgen/density_function/village/" + preset + "/preliminary_surface_level.json",
						read(zip, "data/minecraft/worldgen/density_function/" + preset + "/preliminary_surface_level.json"));
				String chunk = read(zip, "data/minecraft/worldgen/density_function/" + preset + "/chunk_surface_level.json");
				String vanillaPre = "\"minecraft:" + preset + "/preliminary_surface_level\"";
				if (!chunk.contains(vanillaPre)) {
					throw new IllegalStateException("chunk_surface_level de " + preset + " nao aponta para a superficie preliminar: " + chunk);
				}
				ContentGen.write("data/mlinnotas/worldgen/density_function/village/" + preset + "/chunk_surface_level.json",
						chunk.replace(vanillaPre, "\"" + raw + "preliminary_surface_level\""));
				String base = "data/minecraft/worldgen/density_function/" + preset + "/";
				ContentGen.write(base + "final_density.json",
						"{\"type\":\"mlinnotas:village_blend\",\"input\":\"" + raw + "final_density\",\"natural_surface\":\"" + raw + "chunk_surface_level\"}");
				ContentGen.write(base + "preliminary_surface_level.json",
						"{\"type\":\"mlinnotas:village_surface\",\"input\":\"" + raw + "preliminary_surface_level\"}");
				ContentGen.write(base + "chunk_surface_level.json", "{\"type\":\"mlinnotas:village_surface\",\"input\":\"" + raw + "chunk_surface_level\"}");
			}
		}
	}

	/** Le o JSON do jar compactado numa linha (o ContentGen.write indenta de novo). */
	private static String read(ZipFile zip, String entry) throws IOException {
		ZipEntry e = zip.getEntry(entry);
		if (e == null) {
			throw new IOException("nao achei " + entry + " no jar");
		}
		String text = new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8);
		StringBuilder out = new StringBuilder();
		boolean inString = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
				inString = !inString;
			}
			if (!inString && Character.isWhitespace(c)) {
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
