package com.mlin.notas.season;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Clima sazonal de cada bioma, lido pelos mixins que so recebem o objeto {@link Biome} (temperatura, cores). As tags
 * sao lidas quando carregam (servidor e cliente tem instancias de {@code Biome} diferentes, entao ha um mapa por lado)
 * e a consulta e um {@code IdentityHashMap}.
 */
public final class SeasonBiomes {
	public static final TagKey<Biome> TEMPERATE = TagKey.create(Registries.BIOME, MlinNotas.id("seasonal/temperate"));
	public static final TagKey<Biome> HOT = TagKey.create(Registries.BIOME, MlinNotas.id("seasonal/hot"));

	private static volatile Map<Biome, SeasonClimate> serverSide = Map.of();
	private static volatile Map<Biome, SeasonClimate> clientSide = Map.of();

	private SeasonBiomes() {
	}

	public static void init() {
		CommonLifecycleEvents.TAGS_LOADED.register(SeasonBiomes::classify);
	}

	public static void classify(RegistryAccess registries, boolean client) {
		Map<Biome, SeasonClimate> map = new IdentityHashMap<>();
		registries.lookupOrThrow(Registries.BIOME).listElements().forEach(holder -> {
			SeasonClimate climate = classify(holder);
			if (climate != null) {
				map.put(holder.value(), climate);
			}
		});
		if (client) {
			clientSide = map;
		} else {
			serverSide = map;
		}
		MlinNotas.LOGGER.debug("[estacoes] {} biomas classificados ({})", map.size(), client ? "cliente" : "servidor");
	}

	/** Null = fora do Overworld (sem estacoes). */
	public static SeasonClimate classify(Holder<Biome> holder) {
		if (holder.is(TEMPERATE)) {
			return SeasonClimate.TEMPERATE;
		}
		if (holder.is(HOT)) {
			return SeasonClimate.HOT;
		}
		return holder.is(BiomeTags.IS_OVERWORLD) ? SeasonClimate.OTHER : null;
	}

	/** Clima do bioma; null = fora do Overworld. */
	public static SeasonClimate climate(Biome biome) {
		SeasonClimate climate = serverSide.get(biome);
		return climate != null ? climate : clientSide.get(biome);
	}

	/** Deslocamento de temperatura do bioma agora (so temperados; 0 fora da estacao fria). */
	public static float temperatureOffset(Biome biome) {
		float offset = SeasonState.temperatureOffset();
		if (offset == 0.0F) {
			return 0.0F;
		}
		return climate(biome) == SeasonClimate.TEMPERATE ? offset : 0.0F;
	}

	public static boolean temperate(Biome biome) {
		return climate(biome) == SeasonClimate.TEMPERATE;
	}
}
