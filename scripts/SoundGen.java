import com.github.axet.jvorbis.spi.file.EncoderFileFormatType;
import com.github.axet.jvorbis.spi.file.OggVorbisAudioFileFormatType;
import com.github.axet.jvorbis.spi.file.OggVorbisAudioFileWriter;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Sintetiza todos os sons do mod (nao ha gravacao nem arte manual), como o TexGen faz com as texturas.
 *
 * <p>Cada evento e uma receita de DSP: osciladores com envelope de frequencia, ruido filtrado (biquads variando no
 * tempo), sintese modal (parciais inarmonicas que decaem: cristal, metal, sinos), graos de ruido (cinza, entulho),
 * bolhas (seno que sobe enquanto decai), vozes de criatura (fonte dente-de-serra com jitter e sub-harmonico passando
 * por 3 formantes), saturacao tanh e um reverb Freeverb mono. Cada variante usa uma semente derivada do id do evento,
 * entao rodar de novo gera os mesmos arquivos.
 *
 * <p>Saida (mono, 44,1 kHz, Ogg Vorbis VBR q0.4 pelo encoder Java {@code com.tianscar.javasound:javasound-vorbis},
 * porta do libvorbis da Xiph; LGPL-2.0 + BSD da Xiph; so e usado aqui, nao entra no jar do mod):
 * {@code sounds/<categoria>/<nome>/<evento>N.ogg}, {@code sounds.json} e as legendas
 * {@code subtitles.mlinnotas.*} mescladas em {@code lang/pt_br.json} e {@code lang/en_us.json}.
 *
 * <p>Uso: {@code ./gradlew soundGen} (ou {@code java -cp <jar do javasound-vorbis> scripts/SoundGen.java
 * src/main/resources/assets/mlinnotas}). Ao criar um evento aqui, registre o mesmo id em {@code registry/ModSounds};
 * o {@code ModSoundsResourcesTest} aponta o que faltar.
 */
public class SoundGen {
	static final int SR = 44100;
	static final float QUALITY = 0.4F;
	static final double PEAK = 0.708; // -3 dBFS

	static Path root;
	static final Map<String, List<String>> SOUNDS = new LinkedHashMap<>();
	static final Map<String, String> EN = new LinkedHashMap<>();
	static final Map<String, String> PT = new LinkedHashMap<>();
	static int files;

	// ------------------------------------------------------------------ main

	public static void main(String[] args) throws IOException {
		root = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/mlinnotas");
		Path soundsDir = root.resolve("sounds");
		if (Files.isDirectory(soundsDir)) {
			try (Stream<Path> walk = Files.walk(soundsDir)) {
				for (Path p : walk.filter(p -> p.toString().endsWith(".ogg")).toList()) {
					Files.delete(p);
				}
			}
		}
		blocks();
		endCreatures();
		netherCreatures();
		cobaltGolem();
		abyssalGuardian();
		igneousColossus();
		astralHerald();
		ambient();
		writeSoundsJson();
		mergeLang("lang/pt_br.json", PT);
		mergeLang("lang/en_us.json", EN);
		System.out.println("SoundGen: " + SOUNDS.size() + " eventos, " + files + " arquivos .ogg em " + soundsDir);
	}

	// ------------------------------------------------------------------ eventos

	interface Synth {
		double[] make(Rnd r);
	}

	/** Gera {@code variants} arquivos para o evento (cada um com sua semente). */
	static void event(String id, int variants, String en, String pt, Synth synth) throws IOException {
		List<String> refs = new ArrayList<>();
		for (int v = 1; v <= variants; v++) {
			Rnd r = new Rnd(id.hashCode() * 7919L + v * 104729L);
			double[] x = finish(synth.make(r));
			String path = id.replace('.', '/') + v;
			Path out = root.resolve("sounds/" + path + ".ogg");
			Files.createDirectories(out.getParent());
			writeOgg(x, out, id.hashCode() + v);
			files++;
			refs.add("\"mlinnotas:" + path + "\"");
		}
		SOUNDS.put(id, refs);
		subtitle(id, en, pt);
	}

	/** Evento que reaproveita os arquivos de outro, com outro tom/volume. */
	static void alias(String id, String source, double pitch, double volume, String en, String pt) {
		List<String> refs = new ArrayList<>();
		for (String ref : SOUNDS.get(source)) {
			String name = ref.substring(1, ref.length() - 1);
			refs.add(String.format(java.util.Locale.ROOT, "{\"name\": \"%s\", \"pitch\": %.2f, \"volume\": %.2f}", name, pitch, volume));
		}
		SOUNDS.put(id, refs);
		subtitle(id, en, pt);
	}

	static void subtitle(String id, String en, String pt) {
		EN.put("subtitles.mlinnotas." + id, en);
		PT.put("subtitles.mlinnotas." + id, pt);
	}

	/** Conjunto de sons de bloco: quebrar/colocar usam os arquivos de break, passo/golpe/queda os de step. */
	static void blockSet(String name, String enName, String ptName, boolean ptFeminine, Synth breakSynth, Synth stepSynth)
			throws IOException {
		String b = "block." + name + ".";
		String o = ptFeminine ? "a" : "o";
		event(b + "break", 3, enName + " breaks", ptName + " quebra", breakSynth);
		event(b + "step", 4, "Footsteps on " + lowerFirst(enName), "Passos em " + lowerFirst(ptName), stepSynth);
		alias(b + "place", b + "break", 1.1, 0.9, enName + " placed", ptName + " colocad" + o);
		alias(b + "hit", b + "step", 1.0, 0.8, enName + " hit", ptName + " golpead" + o);
		alias(b + "fall", b + "step", 0.9, 1.0, "Something falls on " + lowerFirst(enName), "Algo cai em " + lowerFirst(ptName));
	}

	/** Primeira letra minuscula, menos em "End" (nome proprio). */
	static String lowerFirst(String s) {
		return s.startsWith("End ") ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	// ------------------------------------------------------------------ blocos

	static void blocks() throws IOException {
		blockSet("ether_crystal", "Ether crystal", "Cristal etéreo", false, r -> {
			double[] x = buf(1.3);
			add(x, burst(0.06, r, Filt.HP, c(3200), 0.7, 0.0005, 0.007), 0, 0.5);
			add(x, chime(r, r.range(1250, 1800), 0.55, 1.4), 0, 1.0);
			int shards = 6 + r.nextInt(5);
			for (int i = 0; i < shards; i++) {
				add(x, chime(r, r.range(2300, 6800), r.range(0.05, 0.25), 0.8), r.range(0.0, 0.24), r.range(0.15, 0.5));
			}
			add(x, grains(0.3, r, 30, 4000, 10000, 0.001, 0.004, expDecay(0.08)), 0.01, 0.25);
			return reverb(x, 0.55, 0.4, 0.25);
		}, r -> {
			double[] x = buf(0.45);
			add(x, burst(0.03, r, Filt.HP, c(4000), 0.7, 0.0003, 0.004), 0, 0.3);
			add(x, chime(r, r.range(2000, 3300), r.range(0.08, 0.14), 1.0), 0, 0.9);
			add(x, chime(r, r.range(3500, 6000), 0.06, 0.7), r.range(0.01, 0.05), 0.3);
			return reverb(x, 0.1, 0.6, 0.08);
		});
		// purpur e tijolos do vazio: pedra com ressonancia de salao
		blockSet("resonant_stone", "Resonant stone", "Pedra ressonante", true, r -> reverb(stoneBreak(r, 0.4), 0.6, 0.35, 0.28),
				r -> reverb(stoneStep(r, 0.35), 0.15, 0.6, 0.08));
		blockSet("end_moss", "End moss", "Musgo do End", false, r -> {
			double[] x = buf(0.5);
			add(x, mul(lp(noise(0.5, r), lin(0, 1500, 0.3, 450), 0.8), ad(0.004, 0.09)), 0, 1.0);
			add(x, grains(0.28, r, 26, 350, 1500, 0.003, 0.012, expDecay(0.1)), 0, 0.7);
			add(x, kick(0.18, r.range(110, 140), 55, 0.02, 0.05), 0, 0.45);
			return x;
		}, r -> {
			double[] x = buf(0.25);
			add(x, mul(lp(noise(0.25, r), c(r.range(700, 1000)), 0.7), ad(0.003, 0.035)), 0, 1.0);
			add(x, grains(0.12, r, 8, 300, 1200, 0.003, 0.01, expDecay(0.05)), 0, 0.5);
			add(x, kick(0.12, 120, 60, 0.015, 0.03), 0, 0.3);
			return x;
		});
		blockSet("void_ash", "Void ash", "Cinza do Vazio", true, r -> {
			double[] x = buf(0.6);
			add(x, grains(0.5, r, 240, 1500, 7500, 0.0015, 0.007, ad(0.01, 0.14)), 0, 1.0);
			add(x, mul(lp(noise(0.5, r), c(2600), 0.7), ad(0.006, 0.11)), 0, 0.35);
			add(x, kick(0.15, 100, 55, 0.02, 0.04), 0, 0.2);
			return x;
		}, r -> {
			double[] x = buf(0.28);
			add(x, grains(0.2, r, 70, 1200, 6500, 0.0015, 0.006, ad(0.005, 0.05)), 0, 1.0);
			add(x, mul(lp(noise(0.2, r), c(2200), 0.7), ad(0.004, 0.04)), 0, 0.3);
			return x;
		});
		// blocos dos metais novos: placa metalica com batimento etereo
		blockSet("alloy", "Alloy block", "Bloco de liga", false, r -> {
			double[] x = buf(1.2);
			add(x, burst(0.03, r, Filt.HP, c(2500), 0.7, 0.0003, 0.004), 0, 0.6);
			add(x, plate(r, r.range(320, 460), 0.55, 1.0), 0, 1.0);
			add(x, kick(0.2, 190, 90, 0.012, 0.05), 0, 0.45);
			add(x, grains(0.15, r, 12, 2000, 6000, 0.001, 0.004, expDecay(0.05)), 0.005, 0.2);
			return reverb(x, 0.35, 0.5, 0.14);
		}, r -> {
			double[] x = buf(0.4);
			add(x, burst(0.02, r, Filt.HP, c(3000), 0.7, 0.0003, 0.003), 0, 0.5);
			add(x, plate(r, r.range(520, 720), 0.11, 1.0), 0, 0.8);
			add(x, kick(0.1, 220, 110, 0.01, 0.025), 0, 0.35);
			return x;
		});
		// minerios do End (endita, astralita): pedra seca com faiscas cristalinas
		blockSet("glint_ore", "Glinting ore", "Minério cintilante", false, r -> {
			double[] x = buf(0.9);
			add(x, stoneBreak(r, 0.12), 0, 1.0);
			int n = 3 + r.nextInt(3);
			for (int i = 0; i < n; i++) {
				add(x, chime(r, r.range(3000, 7000), r.range(0.06, 0.16), 0.7), r.range(0.0, 0.3), r.range(0.12, 0.25));
			}
			return reverb(x, 0.35, 0.5, 0.12);
		}, r -> {
			double[] x = buf(0.35);
			add(x, stoneStep(r, 0.08), 0, 1.0);
			add(x, chime(r, r.range(3500, 6500), 0.07, 0.7), r.range(0.0, 0.04), 0.12);
			return x;
		});

		// elevador de purpur: whoosh subindo/descendo com brilho
		event("block.purpur_elevator.up", 2, "Elevator whooshes up", "Elevador sobe", r -> elevator(r, true));
		event("block.purpur_elevator.down", 2, "Elevator whooshes down", "Elevador desce", r -> elevator(r, false));

		// pedra de teleporte
		event("block.waystone.activate", 2, "Waystone awakens", "Pedra de teleporte desperta", r -> {
			double dur = 2.6;
			double[] x = buf(dur);
			double base = r.range(96, 112);
			double[] ratios = {1, 2, 3.01, 4.03, 5.1};
			double[] amps = {1, 0.55, 0.3, 0.15, 0.08};
			for (int i = 0; i < ratios.length; i++) {
				double f = base * ratios[i];
				F glide = t -> f * (1 + 0.05 * Math.min(1, t / 0.9));
				add(x, sine(dur, glide, lin(0, 0, 0.7, 1, 1.8, 0.7, dur, 0)), 0, amps[i] * 0.5);
				add(x, sine(dur, t -> glide.at(t) + 0.7, lin(0, 0, 0.7, 1, 1.8, 0.7, dur, 0)), 0, amps[i] * 0.35);
			}
			add(x, mul(bp(noise(1.0, r), expGlide(350, 2400, 0.85), 1.6), lin(0, 0, 0.75, 1, 0.85, 0)), 0, 0.35);
			double strike = 0.8;
			double bell = r.range(500, 620);
			add(x, chime(r, bell, 1.0, 1.2), strike, 0.9);
			add(x, chime(r, bell * 1.5, 0.8, 1.2), strike + 0.12, 0.5);
			add(x, chime(r, bell * 2.0, 0.7, 1.2), strike + 0.24, 0.4);
			add(x, grains(0.8, r, 40, 4000, 9000, 0.001, 0.003, expDecay(0.3)), strike, 0.15);
			return reverb(x, 0.85, 0.3, 0.45);
		});
		event("block.waystone.travel", 3, "Waystone whisks someone away", "Pedra de teleporte leva alguém", r -> {
			double dur = 1.4;
			double[] x = buf(dur);
			double swirl = r.range(2.2, 3.4);
			double[] wind = mul(bp(noise(dur, r), t -> 700 + 2200 * Math.abs(Math.sin(Math.PI * t * swirl + 0.3)), 1.4),
					lin(0, 0, 0.25, 1, 0.7, 0.6, 1.2, 0));
			add(x, flanger(wind, 0.001, 0.007, 1.6), 0, 0.7);
			add(x, sine(1.0, expGlide(r.range(480, 600), 110, 0.7), ad(0.04, 0.35)), 0.1, 0.6);
			for (int i = 0; i < 5; i++) {
				add(x, chime(r, r.range(2500, 6000), r.range(0.1, 0.3), 1.0), r.range(0.0, 0.5), 0.15);
			}
			return reverb(x, 0.75, 0.35, 0.35);
		});

		// altar de chefe: subida de ruido e acorde grave ate um estrondo
		event("block.boss_altar.summon", 2, "Altar summons a boss", "Altar invoca um chefe", r -> {
			double dur = 3.6;
			double[] x = buf(dur);
			double swell = 1.3;
			add(x, mul(bp(noise(swell, r), expGlide(160, 1400, swell), 1.2), lin(0, 0, swell * 0.9, 1, swell, 0)), 0, 0.5);
			double[] cluster = {55, 58.3, 82.4, 87.3};
			for (double f : cluster) {
				add(x, saw(swell, t -> f * (1 + 0.12 * t / swell), lin(0, 0, swell * 0.9, 1, swell, 0)), 0, 0.12);
			}
			double[] boom = buf(2.3);
			add(boom, kick(2.3, 75, 28, 0.09, 0.55), 0, 1.0);
			add(boom, mul(lp(noise(2.3, r), lin(0, 900, 1.0, 120), 0.7), ad(0.004, 0.4)), 0, 0.9);
			add(boom, burst(0.2, r, Filt.BP, c(1200), 0.8, 0.0005, 0.03), 0, 0.5);
			add(boom, sine(2.3, c(41), ad(0.05, 0.9)), 0, 0.4);
			add(x, drive(boom, 2.5), swell, 1.0);
			add(x, grains(1.2, r, 60, 600, 3000, 0.003, 0.012, expDecay(0.35)), swell + 0.02, 0.25);
			return reverb(x, 0.9, 0.3, 0.45);
		});
	}

	static double[] stoneBreak(Rnd r, double resonance) {
		double[] x = buf(0.9);
		add(x, burst(0.12, r, Filt.BP, c(r.range(700, 1300)), 0.9, 0.0005, 0.03), 0, 1.0);
		add(x, kick(0.25, r.range(140, 180), 70, 0.02, 0.06), 0, 0.6);
		add(x, grains(0.38, r, 40, 800, 3500, 0.003, 0.012, expDecay(0.12)), 0.01, 0.55);
		double base = r.range(190, 260);
		add(x, modal(0.9, new double[] {base, base * 1.47, base * 2.09, base * 2.56, base * 4.1},
				new double[] {1, 0.7, 0.45, 0.3, 0.12}, new double[] {0.35, 0.28, 0.2, 0.15, 0.1}, r), 0, resonance);
		return x;
	}

	static double[] stoneStep(Rnd r, double resonance) {
		double[] x = buf(0.3);
		add(x, burst(0.06, r, Filt.BP, c(r.range(900, 1500)), 1.2, 0.0004, 0.015), 0, 1.0);
		add(x, kick(0.12, 220, 120, 0.01, 0.03), 0, 0.4);
		double base = r.range(300, 420);
		add(x, modal(0.3, new double[] {base, base * 1.53, base * 2.2}, new double[] {1, 0.6, 0.3}, new double[] {0.1, 0.07, 0.05}, r), 0,
				resonance);
		return x;
	}

	static double[] elevator(Rnd r, boolean up) {
		double dur = 0.9;
		double[] x = buf(dur);
		F center = up ? expGlide(250, 3600, 0.75) : expGlide(3600, 250, 0.75);
		add(x, mul(bp(noise(dur, r), center, 2.2), up ? lin(0, 0, 0.3, 1, 0.75, 0.3, dur, 0) : lin(0, 0, 0.08, 1, 0.5, 0.5, dur, 0)), 0, 1.0);
		F tone = up ? expGlide(r.range(170, 200), 760, 0.6) : expGlide(r.range(700, 780), 160, 0.6);
		add(x, fm(0.8, tone, 1.5, lin(0, 1.2, 0.8, 0.2), lin(0, 0, 0.12, 1, 0.55, 0.7, 0.8, 0)), 0, 0.28);
		double sparkAt = up ? 0.45 : 0.0;
		for (int i = 0; i < 4; i++) {
			add(x, chime(r, r.range(3000, 6500), r.range(0.08, 0.2), 1.0), sparkAt + r.range(0, 0.25), 0.14);
		}
		return reverb(x, 0.5, 0.4, 0.3);
	}

	// ------------------------------------------------------------------ criaturas do End

	static void endCreatures() throws IOException {
		// Sentinela de Purpur: construto oco que zune
		event("entity.purpur_sentinel.ambient", 3, "Purpur Sentinel hums", "Sentinela de Purpur zune", r -> {
			double dur = 1.5;
			double[] x = buf(dur);
			double base = r.range(105, 140);
			F trem = t -> 0.65 + 0.35 * Math.sin(2 * Math.PI * 3.1 * t);
			for (double[] p : new double[][] {{1, 1}, {2.01, 0.5}, {3.7, 0.25}, {5.2, 0.12}}) {
				add(x, mul(sine(dur, c(base * p[0]), lin(0, 0, 0.3, 1, 1.0, 0.8, dur, 0)), trem), 0, p[1] * 0.6);
			}
			add(x, mul(bp(noise(dur, r), c(base * 6), 3), lin(0, 0, 0.3, 1, dur, 0)), 0, 0.15);
			add(x, stoneStep(r, 0.3), r.range(0.0, 0.2), 0.35);
			return reverb(x, 0.5, 0.4, 0.25);
		});
		event("entity.purpur_sentinel.hurt", 3, "Purpur Sentinel cracks", "Sentinela de Purpur racha", r -> {
			double[] x = buf(0.6);
			add(x, stoneBreak(r, 0.25), 0, 0.8);
			add(x, plate(r, r.range(600, 800), 0.12, 0.8), 0, 0.4);
			add(x, sine(0.4, expGlide(r.range(260, 300), 120, 0.3), ad(0.005, 0.12)), 0, 0.4);
			return x;
		});
		event("entity.purpur_sentinel.death", 2, "Purpur Sentinel collapses", "Sentinela de Purpur desmorona", r -> {
			double dur = 2.0;
			double[] x = buf(dur);
			F trem = t -> 0.6 + 0.4 * Math.sin(2 * Math.PI * (3 + 14 * t) * t);
			add(x, mul(sine(1.6, expGlide(r.range(220, 250), 38, 1.4), lin(0, 1, 1.6, 0)), trem), 0, 0.6);
			add(x, mul(sine(1.6, expGlide(r.range(440, 500), 76, 1.4), lin(0, 1, 1.2, 0)), trem), 0, 0.25);
			add(x, stoneBreak(r, 0.3), 0, 0.8);
			add(x, grains(1.2, r, 90, 500, 3000, 0.004, 0.015, lin(0, 1, 0.4, 0.8, 1.2, 0)), 0.1, 0.5);
			add(x, stoneStep(r, 0.2), r.range(0.5, 0.8), 0.6);
			return reverb(x, 0.6, 0.35, 0.3);
		});
		event("entity.purpur_sentinel.attack", 2, "Purpur Sentinel strikes", "Sentinela de Purpur golpeia", r -> {
			double[] x = buf(0.6);
			add(x, mul(bp(noise(0.3, r), expGlide(400, 1600, 0.2), 1.5), lin(0, 0, 0.15, 1, 0.22, 0)), 0, 0.6);
			add(x, kick(0.35, 150, 50, 0.02, 0.08), 0.18, 1.0);
			add(x, stoneStep(r, 0.3), 0.18, 0.6);
			return x;
		});
		event("entity.purpur_sentinel.shoot", 2, "Purpur Sentinel shoots", "Sentinela de Purpur atira", r -> {
			double[] x = buf(0.6);
			add(x, fm(0.5, expGlide(r.range(300, 360), 900, 0.12), 2.41, lin(0, 3, 0.3, 0.3), ad(0.004, 0.12)), 0, 0.8);
			add(x, burst(0.2, r, Filt.BP, c(1800), 1.0, 0.002, 0.04), 0, 0.4);
			return reverb(x, 0.45, 0.4, 0.25);
		});

		// Agua-viva do Vazio: vidro mole e bolhas
		event("entity.void_jelly.ambient", 4, "Void Jelly warbles", "Água-viva do Vazio ondula", r -> {
			double dur = 1.3;
			double[] x = buf(dur);
			double f = r.range(520, 760);
			add(x, fm(dur, t -> f * (1 + 0.02 * Math.sin(2 * Math.PI * 4.5 * t)), 1.41, lin(0, 0.8, dur, 0.2), lin(0, 0, 0.35, 1, dur, 0)), 0,
					0.5);
			add(x, fm(dur, c(f * 1.5), 2.76, c(0.5), lin(0, 0, 0.5, 0.6, dur, 0)), 0, 0.2);
			add(x, bubbles(dur, r, 5, 500, 1300, 0.04, 0.09), 0, 0.5);
			return reverb(x, 0.6, 0.4, 0.35);
		});
		event("entity.void_jelly.hurt", 3, "Void Jelly squelches", "Água-viva do Vazio estremece", r -> {
			double[] x = buf(0.5);
			add(x, mul(bp(noise(0.4, r), t -> 1500 * Math.pow(0.25, t / 0.25) * (1 + 0.3 * Math.sin(2 * Math.PI * 22 * t)), 4), ad(0.005, 0.1)), 0,
					1.0);
			add(x, chime(r, r.range(1400, 2000), 0.15, 1.0), 0, 0.35);
			add(x, bubbles(0.3, r, 3, 400, 900, 0.03, 0.06), 0, 0.5);
			return reverb(x, 0.4, 0.4, 0.2);
		});
		event("entity.void_jelly.death", 2, "Void Jelly dissolves", "Água-viva do Vazio se desfaz", r -> {
			double dur = 1.6;
			double[] x = buf(dur);
			add(x, bubbles(1.2, r, 14, 400, 1600, 0.03, 0.09), 0, 0.6);
			double f = r.range(900, 1100);
			add(x, fm(1.4, expGlide(f, f * 0.4, 1.2), 1.41, lin(0, 1.5, 1.4, 0), lin(0, 0, 0.05, 1, 1.4, 0)), 0, 0.35);
			add(x, grains(1.0, r, 30, 3000, 8000, 0.001, 0.004, lin(0, 1, 1.0, 0)), 0, 0.15);
			return reverb(x, 0.7, 0.35, 0.4);
		});

		// Acaro Cristalino: estalinhos de cristal
		event("entity.crystal_mite.ambient", 4, "Crystal Mite chitters", "Ácaro Cristalino estala", r -> {
			double[] x = buf(0.7);
			int clicks = 8 + r.nextInt(9);
			double t = 0;
			for (int i = 0; i < clicks && t < 0.6; i++) {
				add(x, burst(0.02, r, Filt.BP, c(r.range(2500, 5000)), 2, 0.0002, 0.003), t, r.range(0.4, 1.0));
				add(x, chime(r, r.range(3500, 7000), 0.02, 0.6), t, r.range(0.2, 0.5));
				t += r.range(0.022, 0.045);
			}
			return x;
		});
		event("entity.crystal_mite.hurt", 3, "Crystal Mite squeaks", "Ácaro Cristalino guincha", r -> {
			double[] x = buf(0.4);
			add(x, voice(0.25, r, expGlide(r.range(850, 1000), 600, 0.2), vowel("I", 1.1).f1(), vowel("I", 1.1).f2(), vowel("I", 1.1).f3(), 0.2, 0.03, 0,
					ad(0.008, 0.08)), 0, 0.8);
			add(x, burst(0.1, r, Filt.HP, c(3000), 0.7, 0.0003, 0.01), 0, 0.5);
			add(x, chime(r, r.range(3000, 5000), 0.08, 0.7), 0, 0.3);
			return x;
		});
		event("entity.crystal_mite.death", 2, "Crystal Mite shatters", "Ácaro Cristalino se estilhaca", r -> {
			double[] x = buf(1.0);
			add(x, voice(0.35, r, expGlide(950, 380, 0.3), vowel("I", 1.1).f1(), vowel("E", 1.1).f2(), vowel("I", 1.1).f3(), 0.2, 0.04, 0,
					ad(0.008, 0.12)), 0, 0.6);
			for (int i = 0; i < 9; i++) {
				add(x, chime(r, r.range(2200, 7000), r.range(0.05, 0.2), 0.8), r.range(0.02, 0.25), r.range(0.15, 0.4));
			}
			add(x, grains(0.3, r, 40, 4000, 10000, 0.001, 0.004, expDecay(0.1)), 0.02, 0.3);
			return reverb(x, 0.4, 0.5, 0.2);
		});
		event("entity.crystal_mite.bite", 2, "Crystal Mite bites", "Ácaro Cristalino morde", r -> {
			double[] x = buf(0.3);
			for (int i = 0; i < 2; i++) {
				add(x, burst(0.03, r, Filt.BP, c(r.range(1800, 2600)), 1.5, 0.0002, 0.005), i * 0.05, 1.0);
				add(x, chime(r, r.range(2800, 4500), 0.05, 0.7), i * 0.05, 0.35);
			}
			return x;
		});

		// Saltador de Coro: bichinho oco e saltitante
		event("entity.chorus_hopper.ambient", 4, "Chorus Hopper chirps", "Saltador de Coro pia", r -> {
			double[] x = buf(0.45);
			double f = r.range(480, 620);
			Formants v = vowel("U", 1.3).to(vowel("I", 1.3));
			add(x, voice(0.22, r, expGlide(f, f * 1.5, 0.15), v.f1t(0.22), v.f2t(0.22), v.f3t(0.22), 0.15, 0.01, 0, ad(0.01, 0.07)), 0, 0.7);
			add(x, sine(0.12, expGlide(f * 0.6, f * 0.5, 0.1), ad(0.002, 0.03)), 0, 0.35);
			return reverb(x, 0.3, 0.5, 0.12);
		});
		event("entity.chorus_hopper.hurt", 3, "Chorus Hopper squeaks", "Saltador de Coro guincha", r -> {
			double[] x = buf(0.35);
			Formants v = vowel("I", 1.3).to(vowel("E", 1.3));
			add(x, voice(0.22, r, expGlide(r.range(900, 1050), 520, 0.18), v.f1t(0.22), v.f2t(0.22), v.f3t(0.22), 0.2, 0.03, 0,
					ad(0.005, 0.08)), 0, 1.0);
			return x;
		});
		event("entity.chorus_hopper.death", 2, "Chorus Hopper dies", "Saltador de Coro morre", r -> {
			double[] x = buf(0.9);
			Formants v = vowel("E", 1.3).to(vowel("U", 1.3));
			add(x, voice(0.45, r, expGlide(900, 300, 0.4), v.f1t(0.45), v.f2t(0.45), v.f3t(0.45), 0.2, 0.04, 0, ad(0.005, 0.15)), 0, 0.8);
			add(x, sine(0.15, expGlide(500, 900, 0.08), ad(0.002, 0.04)), 0.4, 0.4);
			for (int i = 0; i < 4; i++) {
				add(x, chime(r, r.range(2500, 5000), 0.15, 1.0), 0.4 + r.range(0, 0.2), 0.12);
			}
			return reverb(x, 0.4, 0.5, 0.2);
		});
		event("entity.chorus_hopper.teleport", 2, "Chorus Hopper blinks away", "Saltador de Coro some", r -> {
			double[] x = buf(0.7);
			add(x, fm(0.35, expGlide(r.range(250, 320), 1400, 0.25), 1.5, lin(0, 2, 0.35, 0), lin(0, 0, 0.2, 1, 0.35, 0)), 0, 0.6);
			add(x, mul(bp(noise(0.4, r), expGlide(500, 4000, 0.3), 2), lin(0, 0, 0.28, 1, 0.34, 0)), 0, 0.5);
			for (int i = 0; i < 3; i++) {
				add(x, chime(r, r.range(3000, 6000), 0.12, 1.0), 0.3 + r.range(0, 0.1), 0.15);
			}
			return reverb(x, 0.5, 0.4, 0.3);
		});
	}

	// ------------------------------------------------------------------ Nether

	static void netherCreatures() throws IOException {
		event("entity.magma_slug.ambient", 4, "Magma Slug gurgles", "Lesma de Magma borbulha", r -> {
			double dur = 1.0;
			double[] x = buf(dur);
			add(x, mul(lp(noise(dur, r), c(600), 0.8), lin(0, 0, 0.1, 1, 0.7, 0.6, dur, 0)), 0, 0.35);
			add(x, bubbles(dur, r, 6, 150, 380, 0.05, 0.12), 0, 0.9);
			add(x, crackle(dur, r, 14, lin(0, 1, dur, 0.5)), 0, 0.35);
			Formants v = vowel("O", 0.55);
			add(x, voice(0.6, r, t -> 70 + 10 * Math.sin(2 * Math.PI * 1.5 * t), v.f1(), v.f2(), v.f3(), 0.4, 0.15, 0.3,
					lin(0, 0, 0.15, 1, 0.6, 0)), 0.1, 0.25);
			return x;
		});
		event("entity.magma_slug.hurt", 3, "Magma Slug sizzles", "Lesma de Magma chia", r -> {
			double[] x = buf(0.5);
			add(x, sizzle(0.45, r, ad(0.003, 0.12)), 0, 0.7);
			add(x, bubbles(0.3, r, 3, 200, 450, 0.04, 0.08), 0, 0.8);
			add(x, kick(0.12, 140, 70, 0.02, 0.04), 0, 0.3);
			return x;
		});
		event("entity.magma_slug.death", 2, "Magma Slug fizzles out", "Lesma de Magma se apaga", r -> {
			double dur = 1.6;
			double[] x = buf(dur);
			add(x, sizzle(dur, r, lin(0, 0, 0.02, 1, 0.4, 0.6, dur, 0)), 0, 0.6);
			add(x, bubbles(1.0, r, 10, 120, 400, 0.05, 0.12), 0, 0.8);
			add(x, mul(lp(noise(dur, r), lin(0, 800, dur, 200), 0.7), lin(0, 1, dur, 0)), 0, 0.3);
			return x;
		});
	}

	// ------------------------------------------------------------------ chefes

	static void cobaltGolem() throws IOException {
		String g = "entity.cobalt_golem.";
		event(g + "ambient", 3, "Cobalt Golem grinds", "Golem de Cobalto range", r -> {
			double dur = 1.8;
			double[] x = buf(dur);
			add(x, mul(rumble(dur, r, 220), t -> 0.6 + 0.4 * Math.sin(2 * Math.PI * 1.3 * t + 1)), 0, 1.0);
			add(x, mul(bp(saw(dur, t -> 48 + 6 * Math.sin(2 * Math.PI * 0.7 * t), c(1)), t -> 700 + 250 * Math.sin(2 * Math.PI * 0.9 * t),
					8), lin(0, 0, 0.4, 1, 1.4, 0.8, dur, 0)), 0, 0.35);
			add(x, plate(r, r.range(150, 200), 0.3, 0.6), r.range(0.3, 1.0), 0.25);
			return applyFades(x, 0.15, 0.4);
		});
		event(g + "hurt", 3, "Cobalt Golem clangs", "Golem de Cobalto retine", r -> {
			double[] x = buf(0.9);
			add(x, plate(r, r.range(120, 175), 0.25, 1.0), 0, 0.9);
			add(x, stoneBreak(r, 0.1), 0, 0.6);
			add(x, kick(0.3, 120, 45, 0.02, 0.08), 0, 0.6);
			return reverb(x, 0.4, 0.5, 0.15);
		});
		event(g + "death", 2, "Cobalt Golem collapses", "Golem de Cobalto desaba", r -> {
			double dur = 3.6;
			double[] x = buf(dur);
			add(x, metalRoar(r, 1.6, 0.7), 0, 0.6);
			double t = 0.6;
			for (int i = 0; i < 4; i++) {
				add(x, plate(r, r.range(90, 160), 0.6, 1.0), t, 0.7 - i * 0.12);
				add(x, kick(0.6, 110, 32, 0.04, 0.15), t, 0.8 - i * 0.12);
				t += r.range(0.22, 0.4);
			}
			add(x, grains(2.0, r, 160, 400, 3000, 0.004, 0.015, lin(0, 0, 0.3, 1, 2.0, 0)), 0.6, 0.45);
			add(x, rumble(2.6, r, 160), 0.6, 0.5);
			return applyFades(reverb(x, 0.75, 0.35, 0.35), 0.01, 0.6);
		});
		event(g + "roar", 2, "Cobalt Golem roars", "Golem de Cobalto ruge", r -> reverb(metalRoar(r, 2.0, 1.0), 0.75, 0.35, 0.3));
		event(g + "attack", 3, "Cobalt Golem punches", "Golem de Cobalto soca", r -> {
			double[] x = buf(0.7);
			add(x, kick(0.6, r.range(95, 115), 38, 0.025, 0.12), 0, 1.0);
			add(x, mul(lp(noise(0.4, r), c(900), 0.7), ad(0.002, 0.05)), 0, 0.6);
			add(x, plate(r, r.range(200, 260), 0.25, 0.8), 0, 0.3);
			return drive(x, 1.5);
		});
		event(g + "slam_windup", 2, "Cobalt Golem winds up", "Golem de Cobalto prepara a pancada", r -> {
			double dur = 0.8;
			double[] x = buf(dur);
			add(x, mul(bp(saw(dur, expGlide(55, 90, dur), c(1)), expGlide(200, 900, dur), 5), lin(0, 0, 0.6, 1, dur, 0.6)), 0, 0.8);
			add(x, mul(rumble(dur, r, 300), lin(0, 0.2, dur, 1)), 0, 0.7);
			return applyFades(x, 0.02, 0.15);
		});
		event(g + "slam", 2, "Cobalt Golem slams the ground", "Golem de Cobalto bate no chão", r -> {
			double dur = 2.2;
			double[] x = buf(dur);
			add(x, kick(dur, 70, 26, 0.06, 0.4), 0, 1.0);
			add(x, mul(lp(noise(dur, r), expGlide(2500, 120, 0.6), 0.7), ad(0.002, 0.28)), 0, 0.9);
			add(x, grains(1.5, r, 150, 500, 4000, 0.003, 0.014, expDecay(0.45)), 0.03, 0.5);
			add(x, plate(r, r.range(110, 140), 0.5, 0.7), 0, 0.3);
			return reverb(drive(x, 2.2), 0.8, 0.35, 0.3);
		});
		alias(g + "crash", g + "slam", 1.25, 0.8, "Cobalt Golem crashes into a wall", "Golem de Cobalto bate na parede");
		event(g + "throw", 2, "Cobalt Golem hurls a rock", "Golem de Cobalto arremessa uma pedra", r -> {
			double[] x = buf(0.7);
			add(x, mul(bp(noise(0.6, r), lin(0, 350, 0.25, 1500, 0.6, 500), 1.2), lin(0, 0, 0.22, 1, 0.6, 0)), 0, 0.9);
			Formants v = vowel("A", 0.55).to(vowel("U", 0.55));
			add(x, voice(0.3, r, expGlide(80, 60, 0.3), v.f1t(0.3), v.f2t(0.3), v.f3t(0.3), 0.4, 0.08, 0.4, ad(0.02, 0.08)), 0.12, 0.5);
			return x;
		});
		event(g + "charge", 2, "Cobalt Golem charges", "Golem de Cobalto investe", r -> {
			double dur = 1.6;
			double[] x = buf(dur);
			add(x, metalRoar(r, 0.9, 0.8), 0, 0.6);
			for (int i = 0; i < 5; i++) {
				add(x, kick(0.35, 90, 38, 0.02, 0.07), 0.2 + i * 0.26, 0.8);
				add(x, burst(0.1, r, Filt.LP, c(700), 0.7, 0.001, 0.03), 0.2 + i * 0.26, 0.4);
			}
			return applyFades(drive(x, 1.4), 0.01, 0.1);
		});
		event("entity.cobalt_rock.impact", 3, "Rock shatters", "Pedra se espatifa", r -> {
			double[] x = stoneBreak(r, 0.05);
			add(x, kick(0.3, 120, 40, 0.02, 0.07), 0, 0.7);
			return x;
		});
	}

	/** Rugido metalico grave: voz com sub-harmonico, saturada, com parciais de placa. */
	static double[] metalRoar(Rnd r, double dur, double size) {
		double[] x = buf(dur);
		double f = r.range(52, 66);
		Formants v = vowel("A", 0.5 + 0.1 * size).to(vowel("O", 0.5));
		add(x, voice(dur, r, t -> f * (1 + 0.25 * Math.sin(Math.PI * Math.min(1, t / dur))) * (1 + 0.02 * Math.sin(2 * Math.PI * 7 * t)),
				v.f1t(dur), v.f2t(dur), v.f3t(dur), 0.35, 0.12, 0.6, lin(0, 0, 0.12, 1, dur * 0.7, 0.8, dur, 0)), 0, 1.0);
		double[] ring = mul(voice(dur, r, c(f * 2), c(900), c(1700), c(2900), 0.2, 0.05, 0, lin(0, 0, 0.2, 1, dur, 0)),
				t -> Math.sin(2 * Math.PI * 431 * t));
		add(x, ring, 0, 0.25);
		add(x, mul(rumble(dur, r, 180), lin(0, 0, 0.2, 1, dur, 0)), 0, 0.5);
		return drive(x, 2.0);
	}

	static void abyssalGuardian() throws IOException {
		String g = "entity.abyssal_guardian.";
		event(g + "ambient", 3, "Abyssal Guardian throbs", "Guardião das Profundezas pulsa", r -> {
			double dur = 2.2;
			double[] x = buf(dur);
			double start = r.range(0.05, 0.2);
			for (int beat = 0; beat < 2; beat++) {
				double t0 = start + beat * r.range(0.95, 1.1);
				add(x, kick(0.4, 70, 38, 0.03, 0.1), t0, 1.0);
				add(x, kick(0.4, 60, 34, 0.03, 0.08), t0 + 0.22, 0.7);
			}
			add(x, mul(sine(dur, c(r.range(30, 36)), lin(0, 0, 0.5, 1, dur, 0)), c(1)), 0, 0.3);
			add(x, crackle(dur, r, 9, c(0.6)), 0, 0.3);
			add(x, mul(bp(noise(dur, r), c(500), 6), t -> 0.5 + 0.5 * Math.sin(2 * Math.PI * 0.8 * t)), 0, 0.12);
			return applyFades(reverb(x, 0.85, 0.3, 0.35), 0.05, 0.4);
		});
		event(g + "hurt", 3, "Abyssal Guardian groans", "Guardião das Profundezas geme", r -> {
			double[] x = buf(0.9);
			Formants v = vowel("U", 0.6).to(vowel("O", 0.6));
			add(x, voice(0.55, r, expGlide(r.range(85, 100), 60, 0.5), v.f1t(0.55), v.f2t(0.55), v.f3t(0.55), 0.35, 0.12, 0.5,
					ad(0.015, 0.18)), 0, 1.0);
			add(x, crackle(0.5, r, 50, expDecay(0.15)), 0, 0.5);
			add(x, mul(bp(noise(0.4, r), c(900), 2), ad(0.003, 0.06)), 0, 0.4);
			return reverb(drive(x, 1.6), 0.3, 0.5, 0.15);
		});
		event(g + "death", 1, "Abyssal Guardian dies", "Guardião das Profundezas morre", r -> {
			double dur = 4.0;
			double[] x = buf(dur);
			Formants v = vowel("O", 0.6).to(vowel("U", 0.55));
			add(x, voice(2.6, r, expGlide(110, 38, 2.4), v.f1t(2.6), v.f2t(2.6), v.f3t(2.6), 0.4, 0.1, 0.6, lin(0, 0, 0.2, 1, 1.6, 0.6, 2.6, 0)), 0,
					0.9);
			double t = 0.4;
			double gap = 0.6;
			for (int i = 0; i < 4; i++) {
				add(x, kick(0.4, 65, 34, 0.03, 0.1), t, 0.8 - i * 0.15);
				t += gap;
				gap *= 1.35;
			}
			add(x, crackle(3.0, r, 30, lin(0, 1, 3.0, 0)), 0, 0.3);
			return applyFades(reverb(drive(x, 1.5), 0.9, 0.3, 0.45), 0.01, 0.8);
		});
		event(g + "roar", 2, "Abyssal Guardian roars", "Guardião das Profundezas ruge", r -> {
			double dur = 2.6;
			double[] x = buf(dur);
			double f = r.range(44, 56);
			Formants v = vowel("A", 0.55).to(vowel("O", 0.5));
			add(x, voice(dur, r, t -> f * (1 + 0.3 * Math.sin(Math.PI * Math.min(1, t / dur))), v.f1t(dur), v.f2t(dur), v.f3t(dur), 0.45, 0.15, 0.7,
					lin(0, 0, 0.25, 1, 1.8, 0.8, dur, 0)), 0, 1.0);
			add(x, mul(bp(noise(dur, r), c(380), 0.8), lin(0, 0, 0.3, 1, dur, 0)), 0, 0.4);
			add(x, crackle(dur, r, 25, c(0.7)), 0, 0.25);
			return reverb(drive(x, 2.2), 0.92, 0.25, 0.45);
		});
		event(g + "attack", 2, "Abyssal Guardian strikes", "Guardião das Profundezas golpeia", r -> {
			double[] x = buf(0.7);
			add(x, kick(0.6, 90, 35, 0.025, 0.12), 0, 1.0);
			add(x, mul(lp(noise(0.4, r), c(1200), 1.5), ad(0.002, 0.06)), 0, 0.6);
			add(x, crackle(0.4, r, 60, expDecay(0.1)), 0, 0.4);
			return drive(x, 1.6);
		});
		event(g + "pulse_charge", 2, "Abyssal Guardian charges a pulse", "Guardião das Profundezas carrega um pulso", r -> {
			double dur = 0.8;
			double[] x = buf(dur);
			F trem = t -> 0.55 + 0.45 * Math.sin(2 * Math.PI * (6 * t + 18 * t * t));
			add(x, mul(fm(dur, expGlide(70, 380, dur), 1.5, lin(0, 0.5, dur, 3), lin(0, 0, dur * 0.85, 1, dur, 0.6)), trem), 0, 0.8);
			add(x, crackle(dur, r, 60, lin(0, 0, dur, 1)), 0, 0.4);
			return applyFades(x, 0.02, 0.02);
		});
		event(g + "pulse", 2, "Abyssal Guardian unleashes a pulse", "Guardião das Profundezas solta um pulso", r -> {
			double dur = 1.6;
			double[] x = buf(dur);
			add(x, kick(1.2, 110, 30, 0.04, 0.3), 0, 1.0);
			double[] blast = mul(noise(1.0, r), ad(0.002, 0.15));
			add(x, comb(blast, 1.0 / r.range(160, 200), 0.85), 0, 0.5);
			add(x, mul(bp(noise(1.0, r), expGlide(3000, 300, 0.6), 1.2), ad(0.002, 0.2)), 0, 0.5);
			return reverb(drive(x, 1.8), 0.85, 0.3, 0.4);
		});
		event(g + "tendrils", 2, "Sculk tendrils burst out", "Tentáculos de sculk irrompem", r -> {
			double dur = 1.4;
			double[] x = buf(dur);
			add(x, crackle(dur, r, 120, lin(0, 0, 0.15, 1, dur, 0)), 0, 0.6);
			add(x, mul(bp(noise(dur, r), t -> 400 + 300 * Math.sin(2 * Math.PI * 5 * t), 3), lin(0, 0, 0.1, 1, dur, 0)), 0, 0.5);
			for (double ratio : new double[] {1, 1.06, 1.5}) {
				double f = r.range(560, 640) * ratio;
				add(x, sine(dur, t -> f * (1 + 0.5 * t / dur) * (1 + 0.01 * Math.sin(2 * Math.PI * 6 * t)), lin(0, 0, 0.4, 1, dur, 0)), 0, 0.12);
			}
			add(x, kick(0.4, 80, 40, 0.02, 0.08), 0, 0.5);
			return reverb(x, 0.8, 0.35, 0.35);
		});
	}

	static void igneousColossus() throws IOException {
		String g = "entity.igneous_colossus.";
		event(g + "ambient", 3, "Igneous Colossus breathes fire", "Colosso Ígneo respira fogo", r -> {
			double dur = 2.0;
			double[] x = buf(dur);
			add(x, fire(dur, r, lin(0, 0, 0.5, 1, 1.2, 0.9, dur, 0)), 0, 1.0);
			add(x, mul(rumble(dur, r, 140), lin(0, 0, 0.5, 1, dur, 0)), 0, 0.5);
			return applyFades(x, 0.1, 0.3);
		});
		event(g + "hurt", 3, "Igneous Colossus cracks", "Colosso Ígneo racha", r -> {
			double[] x = buf(0.8);
			add(x, stoneBreak(r, 0.08), 0, 0.9);
			add(x, sizzle(0.6, r, ad(0.01, 0.2)), 0, 0.5);
			Formants v = vowel("O", 0.5);
			add(x, voice(0.4, r, expGlide(75, 55, 0.4), v.f1(), v.f2(), v.f3(), 0.5, 0.15, 0.5, ad(0.02, 0.12)), 0, 0.6);
			return drive(x, 1.4);
		});
		event(g + "death", 1, "Igneous Colossus crumbles", "Colosso Ígneo desmorona", r -> {
			double dur = 4.0;
			double[] x = buf(dur);
			double t = 0;
			for (int i = 0; i < 5; i++) {
				add(x, stoneBreak(r, 0.05), t, 0.8);
				add(x, kick(0.5, 90, 30, 0.03, 0.15), t, 0.7);
				t += r.range(0.25, 0.45);
			}
			add(x, fire(3.0, r, lin(0, 1, 3.0, 0)), 0, 0.6);
			add(x, sizzle(3.6, r, lin(0, 0, 1.2, 1, 3.6, 0)), 0, 0.35);
			add(x, rumble(3.0, r, 150), 0, 0.5);
			return applyFades(reverb(x, 0.8, 0.35, 0.35), 0.01, 0.8);
		});
		event(g + "roar", 2, "Igneous Colossus roars", "Colosso Ígneo ruge", r -> {
			double dur = 2.4;
			double[] x = buf(dur);
			double f = r.range(48, 60);
			Formants v = vowel("A", 0.55).to(vowel("O", 0.5));
			add(x, voice(dur, r, t -> f * (1 + 0.25 * Math.sin(Math.PI * Math.min(1, t / dur))), v.f1t(dur), v.f2t(dur), v.f3t(dur), 0.55, 0.15, 0.6,
					lin(0, 0, 0.2, 1, 1.6, 0.8, dur, 0)), 0, 1.0);
			add(x, fire(dur, r, lin(0, 0, 0.3, 1, dur, 0)), 0, 0.6);
			return reverb(drive(x, 2.2), 0.75, 0.35, 0.3);
		});
		event(g + "attack", 2, "Igneous Colossus punches", "Colosso Ígneo soca", r -> {
			double[] x = buf(0.8);
			add(x, kick(0.6, 100, 34, 0.025, 0.12), 0, 1.0);
			add(x, burst(0.12, r, Filt.BP, c(900), 0.9, 0.0005, 0.03), 0, 0.5);
			add(x, fire(0.6, r, ad(0.01, 0.15)), 0, 0.6);
			return drive(x, 1.5);
		});
		event(g + "fire_wave", 2, "Fire wave spreads", "Onda de fogo se espalha", r -> {
			double dur = 1.8;
			double[] x = buf(dur);
			add(x, kick(0.8, 80, 30, 0.04, 0.2), 0, 0.8);
			add(x, mul(bp(noise(dur, r), expGlide(250, 1800, 1.2), 0.9), lin(0, 0, 0.08, 1, 1.0, 0.6, dur, 0)), 0, 0.7);
			add(x, fire(dur, r, lin(0, 0, 0.1, 1, dur, 0)), 0, 0.8);
			return reverb(x, 0.6, 0.4, 0.2);
		});
		event(g + "meteor", 2, "Igneous Colossus calls meteors", "Colosso Ígneo invoca meteoros", r -> {
			double dur = 1.6;
			double[] x = buf(dur);
			add(x, mul(rumble(dur, r, 200), lin(0, 0, 0.5, 1, dur, 0)), 0, 0.8);
			add(x, mul(bp(noise(dur, r), expGlide(300, 1600, 1.3), 6), lin(0, 0, 1.2, 1, dur, 0)), 0, 0.5);
			add(x, sine(dur, expGlide(260, 900, 1.4), lin(0, 0, 1.2, 1, dur, 0)), 0, 0.12);
			add(x, crackle(dur, r, 30, lin(0, 0, dur, 1)), 0, 0.3);
			return reverb(x, 0.7, 0.35, 0.3);
		});
		event(g + "lava_pool", 2, "Lava bubbles up", "Lava borbulha", r -> {
			double dur = 1.6;
			double[] x = buf(dur);
			add(x, bubbles(dur, r, 14, 90, 260, 0.06, 0.16), 0, 1.0);
			add(x, mul(lp(noise(dur, r), c(400), 0.8), lin(0, 0, 0.2, 1, dur, 0)), 0, 0.4);
			add(x, sizzle(dur, r, lin(0, 0, 0.5, 0.6, dur, 0)), 0, 0.25);
			return applyFades(x, 0.02, 0.3);
		});
		event("entity.magma_meteor.impact", 3, "Meteor explodes", "Meteoro explode", r -> {
			double dur = 1.4;
			double[] x = buf(dur);
			add(x, kick(dur, 90, 30, 0.04, 0.25), 0, 1.0);
			add(x, mul(lp(noise(dur, r), expGlide(4000, 200, 0.5), 0.7), ad(0.001, 0.2)), 0, 0.9);
			add(x, crackle(dur, r, 60, expDecay(0.4)), 0, 0.5);
			add(x, sizzle(dur, r, lin(0, 0, 0.1, 0.6, dur, 0)), 0, 0.3);
			return reverb(drive(x, 2.0), 0.6, 0.4, 0.2);
		});
	}

	static void astralHerald() throws IOException {
		String g = "entity.astral_herald.";
		event(g + "ambient", 3, "Astral Herald chants", "Arauto Astral entoa", r -> {
			double dur = 2.4;
			double[] x = buf(dur);
			double base = r.range(180, 230);
			double[] chord = {1, 1.189, 1.498, 2.0};
			for (double ratio : chord) {
				double f = base * ratio * (1 + r.range(-0.004, 0.004));
				Formants v = vowel("O", 1.0).to(vowel("A", 1.0));
				double vib = r.range(4.5, 5.5);
				add(x, voice(dur, r, t -> f * (1 + 0.008 * Math.sin(2 * Math.PI * vib * t)), v.f1t(dur), v.f2t(dur), v.f3t(dur), 0.25, 0.005, 0,
						lin(0, 0, 0.7, 1, 1.6, 0.8, dur, 0)), 0, 0.3);
			}
			for (int i = 0; i < 3; i++) {
				add(x, chime(r, r.range(1800, 4000), 0.6, 1.0), r.range(0.3, 1.5), 0.08);
			}
			return applyFades(reverb(x, 0.9, 0.3, 0.5), 0.1, 0.5);
		});
		event(g + "hurt", 3, "Astral Herald shrieks", "Arauto Astral grita", r -> {
			double[] x = buf(0.7);
			add(x, fm(0.45, expGlide(r.range(900, 1100), 350, 0.35), 1.41, lin(0, 4, 0.45, 1), ad(0.004, 0.12)), 0, 0.6);
			add(x, chime(r, r.range(1500, 2500), 0.3, 1.3), 0, 0.5);
			add(x, burst(0.08, r, Filt.HP, c(3500), 0.7, 0.0003, 0.008), 0, 0.4);
			return reverb(x, 0.35, 0.5, 0.2);
		});
		event(g + "death", 1, "Astral Herald fades into stars", "Arauto Astral se desfaz em estrelas", r -> {
			double dur = 4.2;
			double[] x = buf(dur);
			double base = 220;
			for (double ratio : new double[] {1, 1.189, 1.498, 2.0, 2.378}) {
				double f = base * ratio;
				Formants v = vowel("A", 1.0).to(vowel("U", 1.0));
				add(x, voice(3.2, r, t -> f * Math.pow(0.5, Math.min(1, t / 3.0)), v.f1t(3.2), v.f2t(3.2), v.f3t(3.2), 0.3, 0.01, 0,
						lin(0, 0, 0.3, 1, 2.0, 0.6, 3.2, 0)), 0, 0.22);
			}
			add(x, grains(3.5, r, 260, 3000, 10000, 0.002, 0.01, lin(0, 0, 1.0, 1, 3.5, 0)), 0.2, 0.25);
			for (int i = 0; i < 10; i++) {
				add(x, chime(r, r.range(1500, 6000), r.range(0.3, 0.8), 1.0), r.range(0.5, 3.0), 0.1);
			}
			return applyFades(reverb(x, 0.95, 0.25, 0.55), 0.01, 0.8);
		});
		event(g + "roar", 2, "Astral Herald roars", "Arauto Astral ruge", r -> {
			double dur = 2.6;
			double[] x = buf(dur);
			double f = r.range(66, 78);
			Formants v = vowel("A", 0.65).to(vowel("O", 0.6));
			add(x, voice(dur, r, t -> f * (1 + 0.2 * Math.sin(Math.PI * Math.min(1, t / dur))), v.f1t(dur), v.f2t(dur), v.f3t(dur), 0.35, 0.1, 0.5,
					lin(0, 0, 0.3, 1, 1.8, 0.8, dur, 0)), 0, 0.8);
			for (double ratio : new double[] {4, 4.24, 6.0, 8.5}) {
				add(x, sine(dur, c(f * ratio), lin(0, 0, 0.6, 1, dur, 0)), 0, 0.06);
			}
			add(x, mul(hp(noise(dur, r), c(3000), 0.7), lin(0, 0, 1.0, 1, 1.3, 0.3, dur, 0)), 0, 0.15);
			return reverb(drive(x, 1.8), 0.92, 0.25, 0.45);
		});
		event(g + "cast", 2, "Astral Herald casts star bolts", "Arauto Astral lança raios estelares", r -> {
			double dur = 1.0;
			double[] x = buf(dur);
			double f = r.range(700, 850);
			double[] steps = {1, 1.26, 1.5, 1.89, 2.52};
			for (int i = 0; i < steps.length; i++) {
				add(x, chime(r, f * steps[i], 0.35, 1.0), i * 0.06, 0.4);
			}
			add(x, mul(bp(noise(0.6, r), expGlide(600, 5000, 0.4), 1.5), lin(0, 0, 0.3, 1, 0.45, 0)), 0, 0.5);
			return reverb(x, 0.75, 0.3, 0.4);
		});
		event(g + "teleport", 2, "Astral Herald blinks", "Arauto Astral se teleporta", r -> {
			double dur = 1.0;
			double[] x = buf(dur);
			add(x, mul(bp(noise(0.45, r), expGlide(400, 4500, 0.4), 1.8), lin(0, 0, 0.38, 1, 0.45, 0)), 0, 0.6);
			add(x, fm(0.6, expGlide(r.range(1300, 1500), 180, 0.4), 1.5, lin(0, 3, 0.6, 0), ad(0.005, 0.18)), 0.4, 0.6);
			for (int i = 0; i < 4; i++) {
				add(x, chime(r, r.range(2500, 6500), 0.2, 1.0), 0.4 + r.range(0, 0.15), 0.12);
			}
			return reverb(x, 0.8, 0.3, 0.4);
		});
		event(g + "summon", 2, "Astral Herald summons Sentinels", "Arauto Astral invoca Sentinelas", r -> {
			double dur = 2.4;
			double[] x = buf(dur);
			double base = r.range(98, 110);
			for (double ratio : new double[] {1, 1.06, 1.5, 2.12}) {
				double f = base * ratio;
				add(x, saw(dur, t -> f * (1 + 0.002 * Math.sin(2 * Math.PI * 0.5 * t)), lin(0, 0, 1.2, 1, 1.6, 0.6, dur, 0)), 0, 0.1);
			}
			x = lp(x, lin(0, 300, 1.2, 2200, dur, 600), 0.9);
			add(x, mul(bp(noise(1.2, r), expGlide(300, 3000, 1.2), 1.2), lin(0, 0, 1.15, 1, 1.2, 0)), 0, 0.4);
			add(x, chime(r, base * 4, 1.0, 1.2), 1.2, 0.6);
			add(x, kick(0.8, 80, 35, 0.03, 0.2), 1.2, 0.6);
			return reverb(x, 0.9, 0.3, 0.45);
		});
		event("entity.star_bolt.impact", 3, "Star bolt bursts", "Raio estelar estoura", r -> {
			double[] x = buf(0.8);
			add(x, burst(0.05, r, Filt.HP, c(3000), 0.7, 0.0003, 0.006), 0, 0.5);
			for (int i = 0; i < 7; i++) {
				add(x, chime(r, r.range(2000, 7500), r.range(0.06, 0.25), 1.0), r.range(0, 0.06), r.range(0.15, 0.4));
			}
			add(x, fm(0.3, expGlide(1800, 700, 0.2), 1.41, c(1.5), ad(0.002, 0.07)), 0, 0.3);
			return reverb(x, 0.6, 0.35, 0.3);
		});
	}

	// ------------------------------------------------------------------ ambiente (estacoes)

	/**
	 * Sons de ambiente tocados pelo cliente ({@code client/ambient/AmbientClient}): loops sem emenda (vento de
	 * montanha, vento seco do deserto, rio, grilos) e avulsos (passaros, gotas e ronco de caverna, sapos).
	 */
	static void ambient() throws IOException {
		loopEvent("ambient.wind.mountain", "Wind howls", "Vento uiva", 14, r -> {
			double dur = 15.5;
			double[] gust = smoothNoise(dur, r, 0.25);
			double[] sweep = smoothNoise(dur, r, 0.12);
			double[] sweep2 = smoothNoise(dur, r, 0.17);
			double[] x = buf(dur);
			F gustEnv = t -> 0.55 + 0.45 * at(gust, t);
			add(x, mul(lp(brown(dur, r), c(260), 0.7), gustEnv), 0, 1.6);
			add(x, mul(bp(noise(dur, r), t -> 520 + 260 * at(sweep, t), 6), gustEnv), 0, 0.9);
			add(x, mul(bp(noise(dur, r), t -> 880 + 380 * at(sweep2, t), 9), t -> 0.3 + 0.7 * Math.max(0, at(gust, t))), 0, 0.55);
			add(x, mul(hp(noise(dur, r), c(2500), 0.7), t -> 0.08 + 0.12 * Math.max(0, at(gust, t))), 0, 1.0);
			return x;
		});
		loopEvent("ambient.desert.wind", "Dry wind hisses", "Vento seco sopra", 14, r -> {
			double dur = 15.5;
			double[] gust = smoothNoise(dur, r, 0.3);
			double[] sweep = smoothNoise(dur, r, 0.2);
			double[] x = buf(dur);
			F gustEnv = t -> 0.5 + 0.5 * at(gust, t);
			add(x, mul(bp(noise(dur, r), t -> 2400 + 900 * at(sweep, t), 1.2), gustEnv), 0, 0.7);
			add(x, mul(lp(noise(dur, r), c(500), 0.7), gustEnv), 0, 0.5);
			add(x, grains(dur, r, (int) (dur * 70), 3000, 9000, 0.001, 0.004, t -> 0.3 + 0.7 * Math.max(0, at(gust, t))), 0, 0.35);
			return x;
		});
		loopEvent("ambient.river", "Water flows", "Água corre", 12, r -> {
			double dur = 13.5;
			double[] babble = smoothNoise(dur, r, 7);
			double[] x = buf(dur);
			add(x, lp(brown(dur, r), c(700), 0.7), 0, 2.2);
			add(x, mul(bp(noise(dur, r), c(1100), 1.2), t -> 0.35 + 0.25 * at(babble, t)), 0, 0.6);
			add(x, mul(bp(noise(dur, r), c(2600), 2.0), t -> 0.15 + 0.15 * at(babble, t)), 0, 0.4);
			add(x, bubbles(dur, r, (int) (dur * 22), 350, 1400, 0.012, 0.05), 0, 0.22);
			return x;
		});
		loopEvent("ambient.forest.crickets", "Crickets chirp", "Grilos cricrilam", 10, r -> {
			double dur = 11.5;
			double[] x = buf(dur);
			for (int k = 0; k < 4; k++) {
				double carrier = r.range(4200, 5200);
				double period = r.range(0.45, 0.8);
				int pulses = 2 + r.nextInt(3);
				double gain = k == 0 ? 1.0 : r.range(0.25, 0.6);
				double cutoff = k == 0 ? 9000 : r.range(5000, 7000);
				double[] voice = buf(dur);
				for (double t = r.range(0, period); t < dur; t += period * r.range(0.9, 1.1)) {
					if (r.nextDouble() < 0.12) {
						continue;
					}
					for (int p = 0; p < pulses; p++) {
						double f = carrier * r.range(0.995, 1.005);
						add(voice, sine(0.018, c(f), ad(0.003, 0.006)), t + p * 0.032, r.range(0.7, 1.0));
					}
				}
				add(x, lp(voice, c(cutoff), 0.7), 0, gain);
			}
			add(x, lp(brown(dur, r), c(300), 0.7), 0, 0.15);
			return reverb(x, 0.3, 0.6, 0.12);
		});
		event("ambient.forest.birds", 6, "Birds sing", "Pássaros cantam", r -> {
			double[] x = buf(2.2);
			double base = r.range(2400, 4200);
			int notes = 3 + r.nextInt(6);
			double t = 0;
			int style = r.nextInt(3);
			for (int i = 0; i < notes && t < 1.7; i++) {
				double len = r.range(0.05, 0.16);
				double f0 = base * r.range(0.8, 1.3);
				double f1 = style == 0 ? f0 * r.range(1.2, 1.6) : style == 1 ? f0 * r.range(0.6, 0.8) : f0 * r.range(0.9, 1.1);
				double wobble = r.range(25, 60);
				F freq = tt -> f0 + (f1 - f0) * Math.min(1, tt / len) + (style == 2 ? 300 * Math.sin(2 * Math.PI * wobble * tt) : 0);
				add(x, fm(len, freq, 2.0, c(0.25), ad(0.008, len * 0.4)), t, r.range(0.6, 1.0));
				t += len + r.range(0.02, 0.12);
			}
			return reverb(lp(x, c(9000), 0.7), 0.35, 0.5, 0.18);
		});
		event("ambient.cave.drip", 4, "Water drips", "Água pinga", r -> {
			double[] x = buf(0.4);
			double f = r.range(900, 2000);
			add(x, sine(0.06, t -> f * (1 + 3.5 * t / 0.06), ad(0.001, 0.012)), 0, 1.0);
			add(x, burst(0.01, r, Filt.HP, c(3000), 0.7, 0.0002, 0.002), 0, 0.2);
			return reverb(x, 0.9, 0.3, 0.45);
		});
		event("ambient.cave.rumble", 2, "Cave rumbles", "Caverna ressoa", r -> {
			double dur = 3.2;
			double[] x = buf(dur);
			add(x, mul(rumble(dur, r, 90), lin(0, 0, 0.8, 1, 2.0, 0.6, dur, 0)), 0, 2.5);
			add(x, mul(bp(noise(dur, r), c(r.range(120, 200)), 3), lin(0, 0, 0.6, 0.5, dur, 0)), 0, 0.5);
			add(x, grains(0.6, r, 30, 300, 1500, 0.004, 0.015, expDecay(0.3)), r.range(0.8, 1.4), 0.15);
			return reverb(x, 0.95, 0.6, 0.5);
		});
		event("ambient.swamp.frog", 4, "Frog croaks", "Sapo coaxa", r -> {
			double[] x = buf(0.9);
			double f0 = r.range(95, 170);
			int pulses = 1 + r.nextInt(3);
			Formants vowel = vowel(r.nextDouble() < 0.5 ? "O" : "A", r.range(0.55, 0.75));
			for (int p = 0; p < pulses; p++) {
				double len = r.range(0.07, 0.14);
				add(x, voice(len, r, expGlide(f0 * 1.1, f0 * 0.9, len), vowel.f1(), vowel.f2(), vowel.f3(), 0.1, 0.12, 0.6, ad(0.01, len * 0.5)),
						p * r.range(0.11, 0.17), 1.0);
			}
			return reverb(drive(x, 1.6), 0.4, 0.5, 0.15);
		});
	}

	/** Valor do array de controle no tempo {@code t}. */
	static double at(double[] control, double t) {
		return control[Math.min(control.length - 1, Math.max(0, (int) (t * SR)))];
	}

	/**
	 * Evento de loop sem emenda: a sintese gera {@code seconds} + 1,5 s; o trecho final e misturado (potencia
	 * constante) no comeco, entao o ultimo sample emenda com o primeiro. Sem corte de silencio nem fades.
	 */
	static void loopEvent(String id, String en, String pt, double seconds, Synth synth) throws IOException {
		Rnd r = new Rnd(id.hashCode() * 7919L + 104729L);
		double[] raw = hp(synth.make(r), c(25), 0.7);
		int len = n(seconds);
		int xf = n(1.5);
		if (raw.length < len + xf) {
			throw new IllegalStateException("loop curto demais: " + id);
		}
		double[] out = new double[len];
		for (int i = 0; i < len; i++) {
			if (i < xf) {
				double k = (double) i / xf;
				out[i] = raw[i] * Math.sqrt(k) + raw[len + i] * Math.sqrt(1 - k);
			} else {
				out[i] = raw[i];
			}
		}
		double scale = PEAK / peak(out);
		for (int i = 0; i < len; i++) {
			out[i] *= scale;
		}
		String path = id.replace('.', '/') + "1";
		Path file = root.resolve("sounds/" + path + ".ogg");
		Files.createDirectories(file.getParent());
		writeOgg(out, file, id.hashCode() + 1);
		files++;
		SOUNDS.put(id, List.of("\"mlinnotas:" + path + "\""));
		subtitle(id, en, pt);
	}

	// ------------------------------------------------------------------ texturas sonoras compostas

	/** Barra de cristal: parciais inarmonicas (barra livre) com leve batimento. */
	static double[] chime(Rnd r, double f0, double tau, double brightness) {
		double[] ratios = {1, 2.756, 5.404, 8.933};
		double[] freqs = new double[ratios.length];
		double[] amps = new double[ratios.length];
		double[] taus = new double[ratios.length];
		for (int i = 0; i < ratios.length; i++) {
			freqs[i] = f0 * ratios[i] * (1 + r.range(-0.01, 0.01));
			amps[i] = Math.pow(0.5 / brightness, i) * r.range(0.7, 1.0);
			taus[i] = tau / (1 + i * 0.8);
		}
		return modal(Math.min(3.0, tau * 6 + 0.02), freqs, amps, taus, r);
	}

	/** Placa metalica (modos de placa circular). */
	static double[] plate(Rnd r, double f0, double tau, double brightness) {
		double[] ratios = {1, 1.59, 2.14, 2.30, 2.65, 2.92, 3.16, 3.50, 4.15};
		double[] freqs = new double[ratios.length];
		double[] amps = new double[ratios.length];
		double[] taus = new double[ratios.length];
		for (int i = 0; i < ratios.length; i++) {
			freqs[i] = f0 * ratios[i] * (1 + r.range(-0.015, 0.015));
			amps[i] = r.range(0.4, 1.0) * Math.pow(0.85 / brightness, i);
			taus[i] = tau * r.range(0.5, 1.2) / (1 + i * 0.25);
		}
		return modal(Math.min(3.0, tau * 6 + 0.02), freqs, amps, taus, r);
	}

	/** Fogo: ruido grave tremulando + estalos. */
	static double[] fire(double dur, Rnd r, F amp) {
		double[] x = buf(dur);
		double[] smooth = smoothNoise(dur, r, 12);
		add(x, mul(lp(noise(dur, r), c(900), 0.7), t -> 0.6 + 0.4 * smooth[Math.min(smooth.length - 1, (int) (t * SR))]), 0, 1.0);
		add(x, mul(bp(noise(dur, r), c(250), 0.8), c(0.6)), 0, 1.0);
		add(x, crackle(dur, r, 25, c(1)), 0, 0.5);
		return mul(x, amp);
	}

	static double[] sizzle(double dur, Rnd r, F amp) {
		double[] x = mul(hp(noise(dur, r), c(4500), 0.7), t -> 0.7 + 0.3 * Math.sin(2 * Math.PI * 17 * t));
		add(x, crackle(dur, r, 80, c(0.5)), 0, 0.6);
		return mul(x, amp);
	}

	static double[] rumble(double dur, Rnd r, double cutoff) {
		return lp(lp(brown(dur, r), c(cutoff), 0.7), c(cutoff), 0.7);
	}

	/** Estalos esparsos (fogo, sculk): cliques curtos de ruido agudo, {@code rate} por segundo. */
	static double[] crackle(double dur, Rnd r, double rate, F amp) {
		double[] x = buf(dur);
		int count = (int) (rate * dur);
		for (int i = 0; i < count; i++) {
			double t = r.range(0, dur);
			add(x, burst(0.02, r, Filt.HP, c(r.range(1500, 5000)), 0.7, 0.0001, r.range(0.0008, 0.003)), t, amp.at(t) * r.range(0.2, 1.0));
		}
		return x;
	}

	/** Bolhas: seno curto cuja frequencia sobe enquanto decai. */
	static double[] bubbles(double dur, Rnd r, int count, double fLo, double fHi, double lenLo, double lenHi) {
		double[] x = buf(dur);
		for (int i = 0; i < count; i++) {
			double len = r.range(lenLo, lenHi);
			double f = r.logRange(fLo, fHi);
			double[] b = sine(len, t -> f * (1 + 2.5 * t / len), ad(0.002, len * 0.3));
			add(x, b, r.range(0, Math.max(0.001, dur - len)), r.range(0.4, 1.0));
		}
		return x;
	}

	/** Graos de ruido filtrado (cinza, entulho, faiscas). */
	static double[] grains(double dur, Rnd r, int count, double fLo, double fHi, double lenLo, double lenHi, F density) {
		double[] x = buf(dur + lenHi);
		for (int i = 0; i < count; i++) {
			double t;
			int guard = 0;
			do {
				t = r.range(0, dur);
			} while (r.nextDouble() > density.at(t) && ++guard < 50);
			double len = r.range(lenLo, lenHi);
			double[] g = bp(noise(len, r), c(r.logRange(fLo, fHi)), r.range(1.5, 4));
			for (int k = 0; k < g.length; k++) {
				g[k] *= 0.5 - 0.5 * Math.cos(2 * Math.PI * k / g.length);
			}
			add(x, g, t, r.range(0.3, 1.0) * Math.max(0.2, density.at(t)));
		}
		return x;
	}

	/** Pancada grave: seno cuja frequencia cai de f0 para f1. */
	static double[] kick(double dur, double f0, double f1, double glideTau, double decayTau) {
		return sine(dur, t -> f1 + (f0 - f1) * Math.exp(-t / glideTau), t -> Math.min(1, t / 0.002) * Math.exp(-t / decayTau));
	}

	/** Rajada de ruido filtrado com ataque e decaimento exponencial. */
	static double[] burst(double dur, Rnd r, Filt type, F f, double q, double attack, double tau) {
		return mul(filter(noise(dur, r), type, f, q), ad(attack, tau));
	}

	// ------------------------------------------------------------------ voz

	record Formants(F f1, F f2, F f3, Formants target) {
		Formants to(Formants other) {
			return new Formants(f1, f2, f3, other);
		}

		F f1t(double dur) {
			return morph(f1, target == null ? f1 : target.f1, dur);
		}

		F f2t(double dur) {
			return morph(f2, target == null ? f2 : target.f2, dur);
		}

		F f3t(double dur) {
			return morph(f3, target == null ? f3 : target.f3, dur);
		}

		static F morph(F a, F b, double dur) {
			return t -> {
				double k = Math.min(1, t / dur);
				return a.at(t) * (1 - k) + b.at(t) * k;
			};
		}
	}

	/** Formantes de uma vogal, escalados (menor que 1 = garganta maior). */
	static Formants vowel(String v, double scale) {
		double[] f = switch (v) {
			case "A" -> new double[] {800, 1150, 2900};
			case "O" -> new double[] {450, 800, 2830};
			case "U" -> new double[] {325, 700, 2700};
			case "E" -> new double[] {400, 1700, 2600};
			default -> new double[] {300, 2200, 3000};
		};
		return new Formants(c(f[0] * scale), c(f[1] * scale), c(f[2] * scale), null);
	}

	/**
	 * Voz de criatura: dente-de-serra com jitter (aspereza) e sub-harmonico, misturada com sopro, passando por 3
	 * formantes.
	 */
	static double[] voice(double dur, Rnd r, F f0, F f1, F f2, F f3, double breath, double jitter, double sub, F amp) {
		int n = n(dur);
		double[] src = new double[n];
		double[] jit = smoothNoise(dur, r, 40);
		double phase = 0;
		double subPhase = 0;
		for (int i = 0; i < n; i++) {
			double t = (double) i / SR;
			double f = Math.max(20, f0.at(t) * (1 + jitter * jit[i]));
			double dt = f / SR;
			phase += dt;
			if (phase >= 1) {
				phase -= 1;
			}
			subPhase += dt / 2;
			if (subPhase >= 1) {
				subPhase -= 1;
			}
			double s = 2 * phase - 1 - polyBlep(phase, dt);
			double sb = Math.sin(2 * Math.PI * subPhase);
			src[i] = (1 - breath) * (s + sub * sb) + breath * r.gauss() * 0.6;
		}
		double[] a = bp(src, f1, Math.max(1.5, f1.at(0) / 110.0));
		double[] b = bp(src, f2, Math.max(1.5, f2.at(0) / 150.0));
		double[] c3 = bp(src, f3, Math.max(1.5, f3.at(0) / 220.0));
		double[] body = lp(src, c(250), 0.7);
		double[] out = new double[n];
		int fade = Math.min(n, Math.max(n(0.02), n / 5));
		for (int i = 0; i < n; i++) {
			double t = (double) i / SR;
			int left = n - 1 - i;
			double tail = left < fade ? 0.5 - 0.5 * Math.cos(Math.PI * left / fade) : 1.0;
			out[i] = (a[i] + 0.55 * b[i] + 0.3 * c3[i] + 0.25 * body[i]) * amp.at(t) * tail;
		}
		return out;
	}

	// ------------------------------------------------------------------ DSP basico

	@FunctionalInterface
	interface F {
		double at(double t);
	}

	enum Filt {
		LP, HP, BP
	}

	static F c(double v) {
		return t -> v;
	}

	/** Linha por pontos (t0, v0, t1, v1, ...), constante fora do intervalo. */
	static F lin(double... p) {
		return t -> {
			if (t <= p[0]) {
				return p[1];
			}
			for (int i = 2; i < p.length; i += 2) {
				if (t <= p[i]) {
					double k = (t - p[i - 2]) / (p[i] - p[i - 2]);
					return p[i - 1] + (p[i + 1] - p[i - 1]) * k;
				}
			}
			return p[p.length - 1];
		};
	}

	static F expGlide(double from, double to, double dur) {
		return t -> from * Math.pow(to / from, Math.min(1, t / dur));
	}

	static F expDecay(double tau) {
		return t -> Math.exp(-t / tau);
	}

	/** Ataque em meio-cosseno e decaimento exponencial. */
	static F ad(double attack, double tau) {
		return t -> t < attack ? 0.5 - 0.5 * Math.cos(Math.PI * t / attack) : Math.exp(-(t - attack) / tau);
	}

	static int n(double sec) {
		return Math.max(1, (int) Math.round(sec * SR));
	}

	static double[] buf(double sec) {
		return new double[n(sec)];
	}

	static double[] noise(double dur, Rnd r) {
		double[] x = buf(dur);
		for (int i = 0; i < x.length; i++) {
			x[i] = r.nextDouble() * 2 - 1;
		}
		return x;
	}

	static double[] brown(double dur, Rnd r) {
		double[] x = buf(dur);
		double v = 0;
		for (int i = 0; i < x.length; i++) {
			v = v * 0.995 + (r.nextDouble() * 2 - 1) * 0.1;
			x[i] = v;
		}
		return x;
	}

	/** Ruido suave (-1..1) com cerca de {@code rate} variacoes por segundo. */
	static double[] smoothNoise(double dur, Rnd r, double rate) {
		int n = n(dur);
		double[] x = new double[n];
		int step = Math.max(1, (int) (SR / rate));
		double a = r.range(-1, 1);
		double b = r.range(-1, 1);
		for (int i = 0; i < n; i++) {
			if (i % step == 0) {
				a = b;
				b = r.range(-1, 1);
			}
			double k = (i % step) / (double) step;
			double s = 0.5 - 0.5 * Math.cos(Math.PI * k);
			x[i] = a + (b - a) * s;
		}
		return x;
	}

	static double[] sine(double dur, F freq, F amp) {
		double[] x = buf(dur);
		double phase = 0;
		for (int i = 0; i < x.length; i++) {
			double t = (double) i / SR;
			x[i] = Math.sin(phase) * amp.at(t);
			phase += 2 * Math.PI * freq.at(t) / SR;
		}
		return x;
	}

	/** FM de 2 operadores: portadora freq, moduladora freq*ratio, indice variavel. */
	static double[] fm(double dur, F freq, double ratio, F index, F amp) {
		double[] x = buf(dur);
		double pc = 0;
		double pm = 0;
		for (int i = 0; i < x.length; i++) {
			double t = (double) i / SR;
			double f = freq.at(t);
			x[i] = Math.sin(pc + index.at(t) * Math.sin(pm)) * amp.at(t);
			pc += 2 * Math.PI * f / SR;
			pm += 2 * Math.PI * f * ratio / SR;
		}
		return x;
	}

	static double polyBlep(double t, double dt) {
		if (t < dt) {
			t /= dt;
			return t + t - t * t - 1;
		}
		if (t > 1 - dt) {
			t = (t - 1) / dt;
			return t * t + t + t + 1;
		}
		return 0;
	}

	static double[] saw(double dur, F freq, F amp) {
		double[] x = buf(dur);
		double phase = 0;
		for (int i = 0; i < x.length; i++) {
			double t = (double) i / SR;
			double dt = freq.at(t) / SR;
			x[i] = (2 * phase - 1 - polyBlep(phase, dt)) * amp.at(t);
			phase += dt;
			if (phase >= 1) {
				phase -= 1;
			}
		}
		return x;
	}

	/** Soma de senos amortecidos (sintese modal) com fase aleatoria e um parceiro desafinado para batimento. */
	static double[] modal(double dur, double[] freqs, double[] amps, double[] taus, Rnd r) {
		double[] x = buf(dur);
		for (int m = 0; m < freqs.length; m++) {
			double f = freqs[m];
			if (f >= SR * 0.45) {
				continue;
			}
			double p = r.range(0, 2 * Math.PI);
			double detune = 1 + r.range(0.001, 0.004);
			for (int i = 0; i < x.length; i++) {
				double t = (double) i / SR;
				double env = Math.exp(-t / taus[m]);
				if (env < 1.0E-4) {
					break;
				}
				x[i] += amps[m] * env * (Math.sin(2 * Math.PI * f * t + p) + 0.35 * Math.sin(2 * Math.PI * f * detune * t + p));
			}
		}
		return x;
	}

	/** Biquad (RBJ) com frequencia variando no tempo (coeficientes recalculados a cada 16 amostras). */
	static double[] filter(double[] in, Filt type, F freq, double q) {
		double[] out = new double[in.length];
		double b0 = 0, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
		double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
		for (int i = 0; i < in.length; i++) {
			if (i % 16 == 0) {
				double f = Math.max(20, Math.min(SR * 0.45, freq.at((double) i / SR)));
				double w = 2 * Math.PI * f / SR;
				double cs = Math.cos(w);
				double alpha = Math.sin(w) / (2 * q);
				double a0 = 1 + alpha;
				switch (type) {
					case LP -> {
						b0 = (1 - cs) / 2;
						b1 = 1 - cs;
						b2 = (1 - cs) / 2;
					}
					case HP -> {
						b0 = (1 + cs) / 2;
						b1 = -(1 + cs);
						b2 = (1 + cs) / 2;
					}
					case BP -> {
						b0 = alpha;
						b1 = 0;
						b2 = -alpha;
					}
				}
				b0 /= a0;
				b1 /= a0;
				b2 /= a0;
				a1 = -2 * cs / a0;
				a2 = (1 - alpha) / a0;
			}
			double x = in[i];
			double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
			x2 = x1;
			x1 = x;
			y2 = y1;
			y1 = y;
			out[i] = y;
		}
		return out;
	}

	static double[] lp(double[] x, F f, double q) {
		return filter(x, Filt.LP, f, q);
	}

	static double[] hp(double[] x, F f, double q) {
		return filter(x, Filt.HP, f, q);
	}

	static double[] bp(double[] x, F f, double q) {
		return filter(x, Filt.BP, f, q);
	}

	static double[] mul(double[] x, F env) {
		double[] out = new double[x.length];
		for (int i = 0; i < x.length; i++) {
			out[i] = x[i] * env.at((double) i / SR);
		}
		return out;
	}

	/**
	 * Soma {@code src * gain} em {@code dst} a partir de {@code at} segundos (o que passar do fim e cortado). Os ultimos
	 * 5 ms da fonte sao suavizados para um corte no fim do buffer nunca virar clique.
	 */
	static void add(double[] dst, double[] src, double at, double gain) {
		int off = (int) Math.round(at * SR);
		int end = Math.min(src.length, dst.length - off);
		int taper = Math.max(1, Math.min(end, 220));
		for (int i = 0; i < end; i++) {
			if (off + i >= 0) {
				int left = end - 1 - i;
				double w = left < taper ? 0.5 - 0.5 * Math.cos(Math.PI * left / taper) : 1.0;
				dst[off + i] += src[i] * gain * w;
			}
		}
	}

	static double[] drive(double[] x, double k) {
		double peak = peak(x);
		if (peak == 0) {
			return x;
		}
		double[] out = new double[x.length];
		double norm = Math.tanh(k);
		for (int i = 0; i < x.length; i++) {
			out[i] = Math.tanh(k * x[i] / peak) / norm * peak;
		}
		return out;
	}

	/** Filtro pente com realimentacao (ressonancia metalica/oca). */
	static double[] comb(double[] x, double delaySec, double feedback) {
		int d = Math.max(1, (int) (delaySec * SR));
		double[] out = new double[x.length];
		for (int i = 0; i < x.length; i++) {
			out[i] = x[i] + (i >= d ? feedback * out[i - d] : 0);
		}
		return out;
	}

	/** Flanger: soma com copia atrasada por um atraso que oscila entre min e max. */
	static double[] flanger(double[] x, double min, double max, double rate) {
		double[] out = new double[x.length];
		for (int i = 0; i < x.length; i++) {
			double t = (double) i / SR;
			double d = (min + (max - min) * (0.5 + 0.5 * Math.sin(2 * Math.PI * rate * t))) * SR;
			double pos = i - d;
			int p = (int) Math.floor(pos);
			double k = pos - p;
			double delayed = p >= 0 && p + 1 < x.length ? x[p] * (1 - k) + x[p + 1] * k : 0;
			out[i] = 0.6 * x[i] + 0.6 * delayed;
		}
		return out;
	}

	/** Reverb Freeverb mono (8 pentes amortecidos + 4 passa-tudo); acrescenta a cauda. */
	static double[] reverb(double[] x, double room, double damp, double wet) {
		int[] combs = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
		int[] alls = {556, 441, 341, 225};
		double feedback = 0.7 + 0.28 * room;
		int tail = (int) (SR * (0.3 + 2.2 * room));
		int n = x.length + tail;
		double[] in = new double[n];
		System.arraycopy(x, 0, in, 0, x.length);
		double[] acc = new double[n];
		for (int len : combs) {
			double[] line = new double[len];
			int idx = 0;
			double store = 0;
			for (int i = 0; i < n; i++) {
				double out = line[idx];
				store = out * (1 - damp) + store * damp;
				line[idx] = in[i] * 0.015 + store * feedback;
				idx = (idx + 1) % len;
				acc[i] += out;
			}
		}
		for (int len : alls) {
			double[] line = new double[len];
			int idx = 0;
			for (int i = 0; i < n; i++) {
				double buffered = line[idx];
				double out = -acc[i] + buffered;
				line[idx] = acc[i] + buffered * 0.5;
				idx = (idx + 1) % len;
				acc[i] = out;
			}
		}
		double[] result = new double[n];
		for (int i = 0; i < n; i++) {
			result[i] = in[i] + acc[i] * wet * 3.0;
		}
		return result;
	}

	static double[] applyFades(double[] x, double in, double out) {
		int ni = Math.min(x.length, n(in));
		int no = Math.min(x.length, n(out));
		for (int i = 0; i < ni; i++) {
			x[i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / ni);
		}
		for (int i = 0; i < no; i++) {
			x[x.length - 1 - i] *= 0.5 - 0.5 * Math.cos(Math.PI * i / no);
		}
		return x;
	}

	static double peak(double[] x) {
		double p = 0;
		for (double v : x) {
			p = Math.max(p, Math.abs(v));
		}
		return p;
	}

	/** Tira DC, corta o silencio do fim, normaliza para -3 dBFS e suaviza as pontas (sem clique). */
	static double[] finish(double[] x) {
		double[] y = hp(x, c(25), 0.7);
		double p = peak(y);
		if (p == 0) {
			throw new IllegalStateException("som vazio");
		}
		int end = y.length;
		while (end > SR / 20 && Math.abs(y[end - 1]) < p * 0.0015) {
			end--;
		}
		end = Math.min(y.length, end + n(0.01));
		double[] out = java.util.Arrays.copyOf(y, end);
		applyFades(out, 0.0015, 0.012);
		double scale = PEAK / peak(out);
		for (int i = 0; i < out.length; i++) {
			out[i] *= scale;
		}
		return out;
	}

	// ------------------------------------------------------------------ saida

	static void writeOgg(double[] x, Path out, int serial) throws IOException {
		byte[] pcm = new byte[x.length * 2];
		Rnd dither = new Rnd(serial);
		for (int i = 0; i < x.length; i++) {
			double d = (dither.nextDouble() - dither.nextDouble());
			int s = (int) Math.round(x[i] * 32767 + d);
			s = Math.max(-32768, Math.min(32767, s));
			pcm[2 * i] = (byte) s;
			pcm[2 * i + 1] = (byte) (s >> 8);
		}
		AudioFormat format = new AudioFormat(SR, 16, 1, true, false);
		AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), format, x.length);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new OggVorbisAudioFileWriter().write(ais, new EncoderFileFormatType(OggVorbisAudioFileFormatType.OGG_VORBIS.toString(),
				OggVorbisAudioFileFormatType.OGG_VORBIS.getExtension(), EncoderFileFormatType.VBR, QUALITY), bytes);
		byte[] ogg = bytes.toByteArray();
		fixSerial(ogg, serial);
		Files.write(out, ogg);
	}

	/** O encoder sorteia o numero de serie do fluxo Ogg; fixa um valor por arquivo (e refaz o CRC) para a saida ser reprodutivel. */
	static void fixSerial(byte[] ogg, int serial) {
		int pos = 0;
		while (pos + 27 <= ogg.length) {
			if (ogg[pos] != 'O' || ogg[pos + 1] != 'g' || ogg[pos + 2] != 'g' || ogg[pos + 3] != 'S') {
				throw new IllegalStateException("pagina Ogg invalida em " + pos);
			}
			int segments = ogg[pos + 26] & 0xFF;
			int len = 27 + segments;
			for (int i = 0; i < segments; i++) {
				len += ogg[pos + 27 + i] & 0xFF;
			}
			for (int i = 0; i < 4; i++) {
				ogg[pos + 14 + i] = (byte) (serial >>> (8 * i));
				ogg[pos + 22 + i] = 0;
			}
			int crc = 0;
			for (int i = pos; i < pos + len; i++) {
				crc = (crc << 8) ^ CRC[((crc >>> 24) ^ ogg[i]) & 0xFF];
			}
			for (int i = 0; i < 4; i++) {
				ogg[pos + 22 + i] = (byte) (crc >>> (8 * i));
			}
			pos += len;
		}
	}

	static final int[] CRC = new int[256];

	static {
		for (int i = 0; i < 256; i++) {
			int r = i << 24;
			for (int k = 0; k < 8; k++) {
				r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04C11DB7 : r << 1;
			}
			CRC[i] = r;
		}
	}

	static void writeSoundsJson() throws IOException {
		StringBuilder out = new StringBuilder("{\n");
		int i = 0;
		for (Map.Entry<String, List<String>> e : SOUNDS.entrySet()) {
			out.append("  \"").append(e.getKey()).append("\": {\n    \"subtitle\": \"subtitles.mlinnotas.").append(e.getKey())
					.append("\",\n    \"sounds\": [\n");
			List<String> refs = e.getValue();
			for (int k = 0; k < refs.size(); k++) {
				out.append("      ").append(refs.get(k)).append(k + 1 < refs.size() ? ",\n" : "\n");
			}
			out.append("    ]\n  }").append(++i < SOUNDS.size() ? ",\n" : "\n");
		}
		out.append("}\n");
		Files.writeString(root.resolve("sounds.json"), out.toString(), StandardCharsets.UTF_8);
	}

	static final Pattern LANG_LINE = Pattern.compile("^\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,?\\s*$");

	/** Mescla as legendas no lang existente (mesmo formato do ContentGen); remove legendas antigas que nao existem mais. */
	static void mergeLang(String relative, Map<String, String> generated) throws IOException {
		Path path = root.resolve(relative);
		Map<String, String> entries = new LinkedHashMap<>();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			Matcher m = LANG_LINE.matcher(line);
			if (m.matches() && !(m.group(1).startsWith("subtitles.mlinnotas.") && !generated.containsKey(m.group(1)))) {
				entries.put(m.group(1), m.group(2));
			}
		}
		for (Map.Entry<String, String> entry : generated.entrySet()) {
			entries.put(entry.getKey(), entry.getValue().replace("\\", "\\\\").replace("\"", "\\\""));
		}
		StringBuilder out = new StringBuilder("{\n");
		int i = 0;
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			out.append("  \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append('"');
			out.append(++i < entries.size() ? ",\n" : "\n");
		}
		out.append("}\n");
		Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
	}

	/** Gerador com semente fixa. */
	static final class Rnd {
		private final SplittableRandom random;

		Rnd(long seed) {
			random = new SplittableRandom(seed);
		}

		double nextDouble() {
			return random.nextDouble();
		}

		int nextInt(int bound) {
			return random.nextInt(bound);
		}

		double range(double a, double b) {
			return a == b ? a : a + random.nextDouble() * (b - a);
		}

		double logRange(double a, double b) {
			return Math.exp(range(Math.log(a), Math.log(b)));
		}

		double gauss() {
			double u = Math.max(1.0E-12, random.nextDouble());
			return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * random.nextDouble());
		}
	}
}
