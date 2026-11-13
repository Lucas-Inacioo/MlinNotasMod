package com.mlin.notas.waystone;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;
import java.util.Map;

/**
 * Pedras de teleporte ativadas (do mundo todo, compartilhadas) e ultima pedra de cada jogador, salvas no
 * armazenamento de dados do Overworld ({@code data/mlinnotas/waystones.dat}).
 */
public class WaystoneData extends SavedData {
	private static final Codec<WaystoneInfo> INFO_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(WaystoneInfo::dimension),
			Codec.INT.fieldOf("x").forGetter(WaystoneInfo::x),
			Codec.INT.fieldOf("y").forGetter(WaystoneInfo::y),
			Codec.INT.fieldOf("z").forGetter(WaystoneInfo::z),
			Codec.STRING.fieldOf("name").forGetter(WaystoneInfo::name)
	).apply(i, WaystoneInfo::new));

	public static final Codec<WaystoneData> CODEC = RecordCodecBuilder.create(i -> i.group(
			INFO_CODEC.listOf().optionalFieldOf("waystones", List.of()).forGetter(d -> d.registry.all()),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("last_used", Map.of()).forGetter(d -> d.registry.lastUsedMap())
	).apply(i, (list, last) -> new WaystoneData(new WaystoneRegistry(list, last))));

	// "remainder" no datafixer: nenhum conserto e aplicado aos nossos dados
	public static final SavedDataType<WaystoneData> TYPE = new SavedDataType<>(MlinNotas.id("waystones"), WaystoneData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final WaystoneRegistry registry;

	public WaystoneData() {
		this(new WaystoneRegistry());
	}

	private WaystoneData(WaystoneRegistry registry) {
		this.registry = registry;
	}

	public static WaystoneData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Leitura; para mudar use os metodos abaixo (que marcam para salvar). */
	public WaystoneRegistry registry() {
		return registry;
	}

	public boolean activate(WaystoneInfo info) {
		boolean changed = registry.activate(info);
		setDirty();
		return changed;
	}

	public boolean rename(String dimension, int x, int y, int z, String name) {
		boolean changed = registry.rename(dimension, x, y, z, name);
		setDirty();
		return changed;
	}

	public boolean remove(String dimension, int x, int y, int z) {
		boolean changed = registry.remove(dimension, x, y, z);
		setDirty();
		return changed;
	}

	public void setLastUsed(String player, WaystoneInfo info) {
		registry.setLastUsed(player, info);
		setDirty();
	}
}
