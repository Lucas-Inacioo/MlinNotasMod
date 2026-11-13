package com.mlin.notas.season;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Estado das estacoes do mundo ({@code data/mlinnotas/seasons.dat} no Overworld): deslocamento em dias somado ao dia
 * do relogio (o {@code /season set} muda) e a duracao usada para calcular esse deslocamento (se a gamerule mudar, o
 * deslocamento e refeito para manter a data).
 */
public class SeasonData extends SavedData {
	public static final Codec<SeasonData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("offset_days", 0L).forGetter(d -> d.offsetDays),
			Codec.INT.optionalFieldOf("length_days", SeasonCalendar.DEFAULT_LENGTH).forGetter(d -> d.lengthDays)
	).apply(i, SeasonData::new));

	public static final SavedDataType<SeasonData> TYPE = new SavedDataType<>(MlinNotas.id("seasons"), SeasonData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private long offsetDays;
	private int lengthDays;

	public SeasonData() {
		this(0L, SeasonCalendar.DEFAULT_LENGTH);
	}

	private SeasonData(long offsetDays, int lengthDays) {
		this.offsetDays = offsetDays;
		this.lengthDays = lengthDays;
	}

	public static SeasonData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public long offsetDays() {
		return offsetDays;
	}

	public int lengthDays() {
		return lengthDays;
	}

	public void setOffset(long offsetDays, int lengthDays) {
		if (offsetDays != this.offsetDays || lengthDays != this.lengthDays) {
			this.offsetDays = offsetDays;
			this.lengthDays = lengthDays;
			setDirty();
		}
	}
}
