package com.mlin.notas.quests;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Progresso do livro de quests do mundo (compartilhado), salvo no Overworld ({@code data/mlinnotas/quests.dat}): quests
 * completas, progresso das tarefas, resgates por jogador e quem ja ganhou o livro.
 */
public class QuestData extends SavedData {
	public static final Codec<QuestData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("completed", List.of()).forGetter(d -> d.progress.completedList()),
			Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).optionalFieldOf("tasks", Map.of()).forGetter(d -> d.progress.taskMap()),
			Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("claimed", Map.of()).forGetter(d -> d.progress.claimedMap()),
			Codec.STRING.listOf().optionalFieldOf("book_given", List.of()).forGetter(d -> List.copyOf(d.bookGiven))
	).apply(i, (completed, tasks, claimed, given) -> new QuestData(QuestProgress.of(completed, tasks, claimed), given)));

	public static final SavedDataType<QuestData> TYPE = new SavedDataType<>(MlinNotas.id("quests"), QuestData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final QuestProgress progress;
	private final Set<String> bookGiven;

	public QuestData() {
		this(new QuestProgress(), List.of());
	}

	private QuestData(QuestProgress progress, List<String> bookGiven) {
		this.progress = progress;
		this.bookGiven = new LinkedHashSet<>(bookGiven);
	}

	public static QuestData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Progresso vivo; quem mudar chama {@link #setDirty()}. */
	public QuestProgress progress() {
		return progress;
	}

	/** Marca que {@code player} ja ganhou o livro; true na primeira vez. */
	public boolean markBookGiven(String player) {
		boolean added = bookGiven.add(player);
		if (added) {
			setDirty();
		}
		return added;
	}

	public void reset() {
		progress.clear();
		setDirty();
	}
}
