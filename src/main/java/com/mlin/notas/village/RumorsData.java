package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Boatos ja contados pela taverna (chunks das estruturas reveladas), salvos em {@code data/mlinnotas/rumors_told.dat}
 * para o mesmo lugar nao ser vendido de novo depois de reiniciar. Arquivo novo: mundo antigo comeca vazio (campo
 * opcional, lista ausente = nenhum boato contado).
 */
public class RumorsData extends SavedData {
	public static final Codec<RumorsData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.listOf().optionalFieldOf("told", List.of()).forGetter(d -> List.copyOf(d.told))
	).apply(i, told -> {
		RumorsData d = new RumorsData();
		d.told.addAll(told);
		return d;
	}));

	public static final SavedDataType<RumorsData> TYPE = new SavedDataType<>(MlinNotas.id("rumors_told"), RumorsData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Set<Long> told = new LinkedHashSet<>();

	public static RumorsData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isTold(long chunkKey) {
		return told.contains(chunkKey);
	}

	public void tell(long chunkKey) {
		if (told.add(chunkKey)) {
			setDirty();
		}
	}

	public int size() {
		return told.size();
	}
}
