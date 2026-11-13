package com.mlin.notas.skills;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Map;

/**
 * Arvore de habilidades do mundo (compartilhada por todos os jogadores), salva no armazenamento do Overworld
 * ({@code data/mlinnotas/skills.dat}).
 */
public class SkillData extends SavedData {
	private static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);

	public static final Codec<SkillData> CODEC = RecordCodecBuilder.create(i -> i.group(
			INT_MAP.optionalFieldOf("levels", Map.of()).forGetter(d -> d.progress.levelsById()),
			Codec.unboundedMap(Codec.STRING, INT_MAP).optionalFieldOf("item_deposits", Map.of()).forGetter(d -> d.progress.itemDeposits()),
			INT_MAP.optionalFieldOf("xp_deposits", Map.of()).forGetter(d -> d.progress.xpDeposits())
	).apply(i, (levels, items, xp) -> new SkillData(SkillProgress.of(levels, items, xp))));

	public static final SavedDataType<SkillData> TYPE = new SavedDataType<>(MlinNotas.id("skills"), SkillData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final SkillProgress progress;

	public SkillData() {
		this(new SkillProgress());
	}

	private SkillData(SkillProgress progress) {
		this.progress = progress;
	}

	public static SkillData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Leitura; para mudar use {@link #deposit} ou {@link #setLevel}. */
	public SkillProgress progress() {
		return progress;
	}

	public SkillProgress.Deposit deposit(SkillNode node, Map<String, Integer> available, int xpLevels) {
		SkillProgress.Deposit result = progress.deposit(node, available, xpLevels);
		if (result.accepted()) {
			setDirty();
		}
		return result;
	}

	public void setLevel(SkillBranch branch, int level) {
		progress.setLevel(branch, level);
		setDirty();
	}

	public void reset() {
		progress.clear();
		setDirty();
	}
}
