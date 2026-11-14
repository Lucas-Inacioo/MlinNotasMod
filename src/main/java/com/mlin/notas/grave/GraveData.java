package com.mlin.notas.grave;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

/**
 * Lapides existentes (do mundo todo), salvas no armazenamento do Overworld ({@code data/mlinnotas/graves.dat}). O
 * conteudo fica no block entity; aqui so o resumo que o minimapa precisa mesmo com a chunk descarregada.
 */
public class GraveData extends SavedData {
	static final Codec<GraveInfo> INFO_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("owner").forGetter(GraveInfo::owner),
			Codec.STRING.optionalFieldOf("owner_name", "?").forGetter(GraveInfo::ownerName),
			Codec.STRING.fieldOf("dimension").forGetter(GraveInfo::dimension),
			Codec.INT.fieldOf("x").forGetter(GraveInfo::x),
			Codec.INT.fieldOf("y").forGetter(GraveInfo::y),
			Codec.INT.fieldOf("z").forGetter(GraveInfo::z),
			Codec.INT.optionalFieldOf("items", 0).forGetter(GraveInfo::items),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(GraveInfo::xp),
			Codec.LONG.optionalFieldOf("day", 1L).forGetter(GraveInfo::day)
	).apply(i, GraveInfo::new));

	public static final Codec<GraveData> CODEC = RecordCodecBuilder.create(i -> i.group(
			INFO_CODEC.listOf().optionalFieldOf("graves", List.of()).forGetter(d -> d.index.all())
	).apply(i, list -> new GraveData(new GraveIndex(list))));

	public static final SavedDataType<GraveData> TYPE = new SavedDataType<>(MlinNotas.id("graves"), GraveData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final GraveIndex index;

	public GraveData() {
		this(new GraveIndex());
	}

	private GraveData(GraveIndex index) {
		this.index = index;
	}

	public static GraveData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Leitura; para mudar use os metodos abaixo (que marcam para salvar). */
	public GraveIndex index() {
		return index;
	}

	public void put(GraveInfo info) {
		index.put(info);
		setDirty();
	}

	public boolean remove(String dimension, int x, int y, int z) {
		boolean removed = index.remove(dimension, x, y, z);
		if (removed) {
			setDirty();
		}
		return removed;
	}
}
