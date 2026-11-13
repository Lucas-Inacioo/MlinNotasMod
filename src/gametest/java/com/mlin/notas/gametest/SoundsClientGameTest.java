package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Game test de cliente dos sons sintetizados: todo evento {@code mlinnotas:*} registrado foi carregado do
 * {@code sounds.json} com legenda, e todo {@code .ogg} do mod decodifica no decodificador do proprio jogo (JOrbis)
 * como mono com amostras.
 */
@SuppressWarnings("UnstableApiUsage")
public class SoundsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> problems = context.computeOnClient(mc -> {
			List<String> out = new ArrayList<>();
			int events = 0;
			for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
				if (!id.getNamespace().equals(MlinNotas.MOD_ID)) {
					continue;
				}
				events++;
				WeighedSoundEvents loaded = mc.getSoundManager().getSoundEvent(id);
				if (loaded == null) {
					out.add("evento sem sons.json: " + id);
				} else if (loaded.getSubtitle() == null) {
					out.add("evento sem legenda: " + id);
				}
			}
			if (events < 80) {
				out.add("poucos eventos registrados: " + events);
			}
			Map<Identifier, Resource> files = mc.getResourceManager().listResources("sounds",
					path -> path.getNamespace().equals(MlinNotas.MOD_ID) && path.getPath().endsWith(".ogg"));
			if (files.size() < 150) {
				out.add("poucos arquivos .ogg: " + files.size());
			}
			for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
				try (InputStream in = file.getValue().open(); JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
					int channels = stream.getFormat().getChannels();
					ByteBuffer pcm = stream.readAll();
					if (channels != 1) {
						out.add(file.getKey() + ": " + channels + " canais (sons posicionais precisam ser mono)");
					}
					if (pcm.remaining() < 2000) {
						out.add(file.getKey() + ": quase vazio (" + pcm.remaining() + " bytes)");
					}
				} catch (Exception e) {
					out.add(file.getKey() + ": falhou ao decodificar: " + e);
				}
			}
			MlinNotas.LOGGER.info("[SoundsClientGameTest] {} eventos, {} arquivos .ogg decodificados", events, files.size());
			return out;
		});
		if (!problems.isEmpty()) {
			throw new AssertionError("Sons com problema:\n" + String.join("\n", problems));
		}
	}
}
